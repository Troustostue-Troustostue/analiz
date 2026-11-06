"""Eğitim (öğrenme) algoritmaları.

MATLAB adlandırması ile:
  trainlm  : Levenberg-Marquardt              (Jacobian tabanlı, J'J ≈ Hessian)
  trainbr  : Bayes düzenlileştirmeli LM       (MacKay / Foresee-Hagan)
  trainscg : Ölçeklendirilmiş eşlenik gradyan (Møller)
  trainbfg : BFGS quasi-Newton (ters Hessian yaklaşımı)
  trainrp  : Resilient backpropagation (Rprop)
  traingdx : Momentumlu, uyarlamalı öğrenme oranlı gradyan inişi
  traingd  : Klasik gradyan inişi
  adam     : Adam (tam yığın)

Performans fonksiyonu normalize uzayda MSE'dir. Doğrulama (validation) kümesi
varsa erken durdurma uygulanır ve en düşük doğrulama hatasını veren ağırlıklar
döndürülür (MATLAB davranışı). trainbr doğrulama ile durdurulmaz.
"""
from __future__ import annotations

import time
from dataclasses import dataclass, field

import numpy as np

ALGORITHMS = {
    "trainlm": "Levenberg-Marquardt",
    "trainbr": "Bayes Düzenlileştirme (LM tabanlı)",
    "trainscg": "Ölçeklendirilmiş Eşlenik Gradyan",
    "trainbfg": "BFGS Quasi-Newton",
    "trainrp": "Resilient Backpropagation",
    "traingdx": "Momentum + Uyarlamalı LR Gradyan İnişi",
    "traingd": "Gradyan İnişi",
    "adam": "Adam",
}
_ALIASES = {
    "lm": "trainlm", "levenberg-marquardt": "trainlm", "levenberg": "trainlm",
    "br": "trainbr", "bayes": "trainbr", "bayesian": "trainbr",
    "scg": "trainscg", "bfg": "trainbfg", "bfgs": "trainbfg",
    "rp": "trainrp", "rprop": "trainrp", "gdx": "traingdx", "gd": "traingd",
    "trainadam": "adam",
}

DEFAULTS = {
    "epochs": 1000,        # maksimum iterasyon (epok)
    "max_fail": 6,         # doğrulama hatası art arda kaç kez artarsa dur
    "goal": 0.0,           # hedef eğitim MSE (normalize)
    "min_grad": 1e-7,      # minimum gradyan normu
    "time_limit": None,    # saniye
    # LM / BR
    "mu": 1e-3, "mu_dec": 0.1, "mu_inc": 10.0, "mu_max": 1e10,
    # GD / GDX
    "lr": 0.01, "lr_inc": 1.05, "lr_dec": 0.7, "max_perf_inc": 1.04, "mc": 0.9,
    # Rprop
    "delta0": 0.07, "delt_inc": 1.2, "delt_dec": 0.5, "deltamax": 50.0,
    # SCG
    "sigma": 5e-5, "lambda": 5e-7,
    # Adam
    "adam_lr": 1e-2, "beta1": 0.9, "beta2": 0.999, "eps": 1e-8,
}


def canonical(name: str) -> str:
    key = str(name).strip().lower()
    key = _ALIASES.get(key, key)
    if key not in ALGORITHMS:
        raise ValueError(f"Bilinmeyen eğitim algoritması '{name}'. Geçerli: {', '.join(ALGORITHMS)}")
    return key


@dataclass
class Dataset:
    Xtr: np.ndarray
    Ttr: np.ndarray
    Xval: np.ndarray | None = None
    Tval: np.ndarray | None = None
    Xte: np.ndarray | None = None
    Tte: np.ndarray | None = None

    @staticmethod
    def _ok(X):
        return X is not None and len(X) > 0

    @property
    def has_val(self):
        return self._ok(self.Xval)

    @property
    def has_test(self):
        return self._ok(self.Xte)


@dataclass
class TrainResult:
    theta: np.ndarray
    algorithm: str
    epochs: int
    best_epoch: int
    stop_reason: str
    history: dict = field(default_factory=dict)
    info: dict = field(default_factory=dict)
    seconds: float = 0.0


def _mse(net, theta, X, T):
    R = net.forward(theta, X) - T
    return float(np.mean(R * R))


class _Monitor:
    """Epok kayıtları, erken durdurma ve en iyi ağırlıkların takibi."""

    def __init__(self, net, data: Dataset, p, use_val=True):
        self.net, self.d, self.p = net, data, p
        self.use_val = use_val and data.has_val
        self.hist = {"epoch": [], "train": [], "val": [], "test": [], "grad": [], "ctrl": []}
        self.best_val = np.inf
        self.best_theta = None
        self.best_epoch = 0
        self.fails = 0
        self.t0 = time.perf_counter()
        self.reason = "Maksimum epok sayısına ulaşıldı"

    def record(self, epoch, theta, train_perf, grad_norm, ctrl=np.nan):
        d, net = self.d, self.net
        val = _mse(net, theta, d.Xval, d.Tval) if d.has_val else np.nan
        tst = _mse(net, theta, d.Xte, d.Tte) if d.has_test else np.nan
        h = self.hist
        h["epoch"].append(epoch); h["train"].append(train_perf); h["val"].append(val)
        h["test"].append(tst); h["grad"].append(grad_norm); h["ctrl"].append(ctrl)

        if not np.isfinite(train_perf):
            self.reason = "Sayısal kararsızlık (NaN/Inf)"
            return True
        if self.use_val:
            if val < self.best_val:
                self.best_val, self.best_theta, self.best_epoch, self.fails = val, theta.copy(), epoch, 0
            else:
                self.fails += 1
            if self.fails >= self.p["max_fail"]:
                self.reason = f"Doğrulama hatası art arda {self.p['max_fail']} epok iyileşmedi (erken durdurma)"
                return True
        else:
            self.best_theta, self.best_epoch = theta.copy(), epoch
        if train_perf <= self.p["goal"]:
            self.reason = "Performans hedefine ulaşıldı"
            return True
        if grad_norm < self.p["min_grad"]:
            self.reason = "Minimum gradyana ulaşıldı"
            return True
        tl = self.p.get("time_limit")
        if tl and time.perf_counter() - self.t0 > tl:
            self.reason = "Zaman limiti doldu"
            return True
        return False

    def finish(self, theta):
        return theta.copy() if self.best_theta is None else self.best_theta


# ---------------------------------------------------------------------- #
#   Levenberg-Marquardt ve Bayes Düzenlileştirme
# ---------------------------------------------------------------------- #
class _DampedSolver:
    """(β·JᵀJ + c·I)⁻¹ v çözümü. Parametre sayısı > artık sayısı ise
    Woodbury özdeşliği ile küçük (JJᵀ) sistem çözülür; büyük ağlarda çok hızlıdır."""

    def __init__(self, J, beta=1.0):
        self.J, self.beta = J, beta
        M, P = J.shape
        self.primal = P <= M
        self.G = beta * (J.T @ J) if self.primal else beta * (J @ J.T)

    def solve(self, v, c):
        G = self.G.copy()
        G.flat[:: G.shape[0] + 1] += c
        try:
            if self.primal:
                return np.linalg.solve(G, v)
            u = np.linalg.solve(G, self.J @ v)
            return (v - self.beta * (self.J.T @ u)) / c
        except np.linalg.LinAlgError:
            if self.primal:
                return np.linalg.lstsq(G, v, rcond=None)[0]
            u = np.linalg.lstsq(G, self.J @ v, rcond=None)[0]
            return (v - self.beta * (self.J.T @ u)) / c

    def eigvals(self):
        """JᵀJ'nin sıfırdan farklı olabilecek özdeğerleri (β'sız)."""
        return np.clip(np.linalg.eigvalsh(self.G / self.beta), 0.0, None)


def _train_lm(net, theta, data: Dataset, p, bayes=False):
    X, T = data.Xtr, data.Ttr
    n = T.size
    mon = _Monitor(net, data, p, use_val=not bayes)
    mu = p["mu"]
    alpha, beta = 0.0, 1.0
    gamma = float(net.n_params)

    J, Y = net.jacobian_params(theta, X)
    r = (Y - T).ravel()
    sse, ew = float(r @ r), float(theta @ theta)
    F = beta * sse + alpha * ew
    mon.record(0, theta, sse / n, float(np.linalg.norm(2 * J.T @ r) / n), mu)

    for epoch in range(1, p["epochs"] + 1):
        solver = _DampedSolver(J, beta)
        g = beta * (J.T @ r) + alpha * theta  # ½∇F
        accepted = False
        while mu <= p["mu_max"]:
            step = solver.solve(g, alpha + mu)
            th_new = theta - step
            rn = (net.forward(th_new, X) - T).ravel()
            sse_n, ew_n = float(rn @ rn), float(th_new @ th_new)
            F_n = beta * sse_n + alpha * ew_n
            if np.isfinite(F_n) and F_n < F:
                theta, sse, ew, F = th_new, sse_n, ew_n, F_n
                mu = max(mu * p["mu_dec"], 1e-20)
                accepted = True
                break
            mu *= p["mu_inc"]
        if not accepted:
            mon.reason = "Mu maksimum değere ulaştı (daha fazla iyileşme yok)"
            break

        J, Y = net.jacobian_params(theta, X)
        r = (Y - T).ravel()
        if bayes:
            s = _DampedSolver(J, 1.0).eigvals()
            gamma = float(np.sum(beta * s / (beta * s + alpha))) if alpha > 0 else float(np.sum(s > 1e-12 * max(s.max(), 1e-300)))
            gamma = min(gamma, n - 1e-6)
            alpha = gamma / (2.0 * max(ew, 1e-300))
            beta = (n - gamma) / (2.0 * max(sse, 1e-300))
            F = beta * sse + alpha * ew
        grad_norm = float(np.linalg.norm(2 * J.T @ r) / n)
        if mon.record(epoch, theta, sse / n, grad_norm, mu):
            break
    res_theta = mon.finish(theta)
    info = {"mu_son": mu}
    if bayes:
        info.update({"gamma_etkin_parametre": gamma, "alpha": alpha, "beta": beta,
                     "toplam_parametre": net.n_params})
    return res_theta, mon, info


# ---------------------------------------------------------------------- #
#   Gradyan tabanlı algoritmalar
# ---------------------------------------------------------------------- #
def _fg(net, X, T):
    n = T.size

    def fg(theta):
        sse, g = net.sse_and_gradient(theta, X, T)
        return sse / n, g / n

    return fg


def _train_scg(net, theta, data, p):
    """Møller (1993) ölçeklendirilmiş eşlenik gradyan (Netlab uygulaması temel alınmıştır)."""
    fg = _fg(net, data.Xtr, data.Ttr)
    mon = _Monitor(net, data, p)
    sigma0 = 1e-4
    beta, betamin, betamax = 1.0, 1e-15, 1e100
    fold, gnew = fg(theta)
    mon.record(0, theta, fold, float(np.linalg.norm(gnew)), beta)
    d = -gnew
    success, nsuccess = True, 0
    P = theta.size
    mu = kappa = theta_c = 0.0
    for epoch in range(1, p["epochs"] + 1):
        if success:
            mu = float(d @ gnew)
            if mu >= 0:
                d = -gnew
                mu = float(d @ gnew)
            kappa = float(d @ d)
            if kappa < 1e-300:
                mon.reason = "Arama yönü sıfıra indi"
                break
            sigma = sigma0 / np.sqrt(kappa)
            _, gplus = fg(theta + sigma * d)
            theta_c = float(d @ (gplus - gnew)) / sigma
        delta = theta_c + beta * kappa
        if delta <= 0:
            delta = beta * kappa
            beta = beta - theta_c / kappa
        alpha = -mu / delta
        th_new = theta + alpha * d
        fnew, _ = fg(th_new)
        Delta = 2.0 * (fnew - fold) / (alpha * mu) if alpha * mu != 0 else -1
        if Delta >= 0 and np.isfinite(fnew):
            success = True
            nsuccess += 1
            theta = th_new
            gold = gnew
            fold, gnew = fg(theta)
        else:
            success = False
        if Delta < 0.25:
            beta = min(4.0 * beta, betamax)
        if Delta > 0.75:
            beta = max(0.5 * beta, betamin)
        if nsuccess == P:
            d = -gnew
            nsuccess = 0
        elif success:
            gamma = float((gold - gnew) @ gnew) / mu
            d = gamma * d - gnew
        if beta >= betamax:
            mon.reason = "SCG ölçek parametresi maksimuma ulaştı"
            break
        if mon.record(epoch, theta, fold, float(np.linalg.norm(gnew)), beta):
            break
    return mon.finish(theta), mon, {}


def _train_bfg(net, theta, data, p):
    fg = _fg(net, data.Xtr, data.Ttr)
    mon = _Monitor(net, data, p)
    f, g = fg(theta)
    P = theta.size
    H = np.eye(P)
    mon.record(0, theta, f, float(np.linalg.norm(g)))
    for epoch in range(1, p["epochs"] + 1):
        d = -H @ g
        if g @ d >= 0:  # iniş yönü değilse sıfırla
            H = np.eye(P)
            d = -g
        # Armijo geri izlemeli çizgi araması
        a, c1 = 1.0, 1e-4
        gd = float(g @ d)
        while True:
            th_new = theta + a * d
            f_new, g_new = fg(th_new)
            if np.isfinite(f_new) and f_new <= f + c1 * a * gd:
                break
            a *= 0.5
            if a < 1e-12:
                break
        if a < 1e-12:
            mon.reason = "Çizgi araması ilerleme sağlayamadı"
            break
        s, y = th_new - theta, g_new - g
        sy = float(s @ y)
        if sy > 1e-10:
            rho = 1.0 / sy
            Hy = H @ y
            H = H + (rho * rho * (y @ Hy) + rho) * np.outer(s, s) - rho * (np.outer(Hy, s) + np.outer(s, Hy))
        theta, f, g = th_new, f_new, g_new
        if mon.record(epoch, theta, f, float(np.linalg.norm(g)), a):
            break
    return mon.finish(theta), mon, {}


def _train_rp(net, theta, data, p):
    fg = _fg(net, data.Xtr, data.Ttr)
    mon = _Monitor(net, data, p)
    f, g = fg(theta)
    mon.record(0, theta, f, float(np.linalg.norm(g)))
    delta = np.full_like(theta, p["delta0"])
    g_prev = np.zeros_like(theta)
    for epoch in range(1, p["epochs"] + 1):
        prod = g * g_prev
        delta = np.where(prod > 0, np.minimum(delta * p["delt_inc"], p["deltamax"]),
                         np.where(prod < 0, np.maximum(delta * p["delt_dec"], 1e-6), delta))
        g_eff = np.where(prod < 0, 0.0, g)  # iRprop-
        theta = theta - np.sign(g_eff) * delta
        g_prev = g_eff
        f, g = fg(theta)
        if mon.record(epoch, theta, f, float(np.linalg.norm(g)), float(delta.mean())):
            break
    return mon.finish(theta), mon, {}


def _train_gd(net, theta, data, p, adaptive=True):
    fg = _fg(net, data.Xtr, data.Ttr)
    mon = _Monitor(net, data, p)
    f, g = fg(theta)
    mon.record(0, theta, f, float(np.linalg.norm(g)), p["lr"])
    lr, mc = p["lr"], (p["mc"] if adaptive else 0.0)
    dX = np.zeros_like(theta)
    for epoch in range(1, p["epochs"] + 1):
        dX_new = mc * dX - (1 - mc) * lr * g if adaptive else -lr * g
        th_new = theta + dX_new
        f_new, g_new = fg(th_new)
        if adaptive and (not np.isfinite(f_new) or f_new > f * p["max_perf_inc"]):
            lr *= p["lr_dec"]
            dX = np.zeros_like(theta)
        else:
            if adaptive and f_new < f:
                lr *= p["lr_inc"]
            theta, f, g, dX = th_new, f_new, g_new, dX_new
        if mon.record(epoch, theta, f, float(np.linalg.norm(g)), lr):
            break
    return mon.finish(theta), mon, {"lr_son": lr}


def _train_adam(net, theta, data, p):
    fg = _fg(net, data.Xtr, data.Ttr)
    mon = _Monitor(net, data, p)
    f, g = fg(theta)
    mon.record(0, theta, f, float(np.linalg.norm(g)), p["adam_lr"])
    m = np.zeros_like(theta)
    v = np.zeros_like(theta)
    b1, b2, lr, eps = p["beta1"], p["beta2"], p["adam_lr"], p["eps"]
    for epoch in range(1, p["epochs"] + 1):
        m = b1 * m + (1 - b1) * g
        v = b2 * v + (1 - b2) * g * g
        mh = m / (1 - b1 ** epoch)
        vh = v / (1 - b2 ** epoch)
        theta = theta - lr * mh / (np.sqrt(vh) + eps)
        f, g = fg(theta)
        if mon.record(epoch, theta, f, float(np.linalg.norm(g)), lr):
            break
    return mon.finish(theta), mon, {}


def train(net, theta0, data: Dataset, algorithm="trainlm", params=None) -> TrainResult:
    """Ağı seçilen algoritma ile eğitir."""
    alg = canonical(algorithm)
    p = dict(DEFAULTS)
    if params:
        p.update({k: v for k, v in params.items() if v is not None})
    p["epochs"] = int(p["epochs"])
    p["max_fail"] = int(p["max_fail"])
    t0 = time.perf_counter()
    theta = np.array(theta0, float)
    with np.errstate(over="ignore", invalid="ignore"):
        if alg == "trainlm":
            th, mon, info = _train_lm(net, theta, data, p, bayes=False)
        elif alg == "trainbr":
            th, mon, info = _train_lm(net, theta, data, p, bayes=True)
        elif alg == "trainscg":
            th, mon, info = _train_scg(net, theta, data, p)
        elif alg == "trainbfg":
            th, mon, info = _train_bfg(net, theta, data, p)
        elif alg == "trainrp":
            th, mon, info = _train_rp(net, theta, data, p)
        elif alg == "traingdx":
            th, mon, info = _train_gd(net, theta, data, p, adaptive=True)
        elif alg == "traingd":
            th, mon, info = _train_gd(net, theta, data, p, adaptive=False)
        else:
            th, mon, info = _train_adam(net, theta, data, p)
    hist = {k: np.asarray(v, float) for k, v in mon.hist.items()}
    return TrainResult(theta=th, algorithm=alg, epochs=int(hist["epoch"][-1]),
                       best_epoch=int(mon.best_epoch), stop_reason=mon.reason,
                       history=hist, info=info, seconds=time.perf_counter() - t0)
