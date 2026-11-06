"""Mimari tarama (grid search): katman sayısı x nöron aralıkları x eğitim
algoritmaları x aktivasyonlar x tekrar. Paralel çalışır, ara sonuçları diske yazar."""
from __future__ import annotations

import csv
import itertools
import os
import time
from concurrent.futures import ProcessPoolExecutor, as_completed

import numpy as np

from . import metrics
from .network import MLP
from .trainers import Dataset, canonical, train

# --------------------------------------------------------------------- #
CRITERIA = {
    "val_mse": ("mse_dogrulama", "min"), "mse_dogrulama": ("mse_dogrulama", "min"),
    "test_mse": ("mse_test", "min"), "mse_test": ("mse_test", "min"),
    "train_mse": ("mse_egitim", "min"), "mse_egitim": ("mse_egitim", "min"),
    "all_mse": ("mse_tum", "min"), "mse_tum": ("mse_tum", "min"),
    "val_r": ("R_dogrulama", "max"), "R_dogrulama": ("R_dogrulama", "max"),
    "all_r": ("R_tum", "max"), "R_tum": ("R_tum", "max"),
}


def parse_range(spec) -> list[int]:
    """[1,100] -> 1..100 ; [10,100,10] -> 10,20,..,100 ; {"liste":[5,8]} ; 7 -> [7]"""
    if isinstance(spec, dict):
        if "liste" in spec:
            return sorted({int(v) for v in spec["liste"]})
        lo, hi, st = spec.get("min", 1), spec.get("max"), spec.get("adim", 1)
        return list(range(int(lo), int(hi) + 1, int(st)))
    if isinstance(spec, (int, np.integer)):
        return [int(spec)]
    spec = list(spec)
    if len(spec) == 2:
        return list(range(int(spec[0]), int(spec[1]) + 1))
    if len(spec) == 3:
        return list(range(int(spec[0]), int(spec[1]) + 1, int(spec[2])))
    raise ValueError(f"Geçersiz nöron aralığı: {spec}")


def build_architectures(layer_ranges, layer_counts=None) -> list[tuple]:
    ranges = [parse_range(r) for r in layer_ranges]
    if not layer_counts:
        layer_counts = [len(ranges)]
    archs = []
    for L in sorted(set(int(c) for c in layer_counts)):
        if L < 1 or L > len(ranges):
            raise ValueError(f"Katman sayısı {L} için {L} adet nöron aralığı tanımlanmalı")
        archs.extend(itertools.product(*ranges[:L]))
    return archs


# --------------------------------------------------------------------- #
_G = {}


def _init_worker(shared):
    _G.clear()
    _G.update(shared)


def _subset_metrics(row, net, theta, Xn, Tn, T_orig, y_scaler, tag, out_names):
    if Xn is None or len(Xn) == 0:
        row[f"mse_{tag}"] = np.nan
        row[f"R_{tag}"] = np.nan
        return
    Yn = net.forward(theta, Xn)
    row[f"mse_{tag}"] = metrics.mse_norm(Tn, Yn)
    a, b = Tn.ravel(), Yn.ravel()
    row[f"R_{tag}"] = float(np.corrcoef(a, b)[0, 1]) if a.size > 1 and np.std(b) > 0 else np.nan
    Y = y_scaler.inverse(Yn)
    pm = metrics.per_output(T_orig, Y)
    for k, o in enumerate(out_names):
        row[f"R2_{tag}[{o}]"] = float(pm["R2"][k])
        row[f"RMSE_{tag}[{o}]"] = float(pm["RMSE"][k])
        row[f"MAPE_{tag}[{o}]"] = float(pm["MAPE_%"][k])


def run_one(job, keep_history=True):
    """Tek bir konfigürasyonu eğitir ve sonuç satırı döndürür."""
    g = _G
    hidden, alg, h_act, rep = job["hidden"], job["alg"], job["act"], job["rep"]
    net = MLP(g["d"], list(hidden), g["m"], hidden_act=h_act, output_act=g["out_act"])
    ent = [int(g["seed"]), int(rep), len(hidden), *map(int, hidden), sum(map(ord, h_act))]
    rng = np.random.default_rng(np.random.SeedSequence(ent))
    theta0 = net.init_params(rng)
    ds = Dataset(g["Xtr"], g["Ttr"], g["Xva"], g["Tva"], g["Xte"], g["Tte"])
    params = dict(g["params"])
    params.update(g["alg_params"].get(alg, {}))
    try:
        res = train(net, theta0, ds, alg, params)
        theta, err = res.theta, ""
    except Exception as e:  # tek bir konfigürasyonun hatası tüm taramayı durdurmasın
        res, theta, err = None, theta0, f"{type(e).__name__}: {e}"
    row = {
        "id": job["id"], "mimari": net.describe(), "gizli_katman_sayisi": len(hidden),
        **{f"n{i + 1}": int(h) for i, h in enumerate(hidden)},
        "algoritma": alg, "aktivasyon": h_act, "tekrar": rep,
        "parametre_sayisi": net.n_params,
        "egitim_ornek/parametre": round(g["Ttr"].shape[0] * g["m"] / net.n_params, 3),
        "epok": res.epochs if res else 0, "en_iyi_epok": res.best_epoch if res else 0,
        "sure_s": round(res.seconds, 3) if res else 0.0,
        "durma_nedeni": res.stop_reason if res else "HATA: " + err,
    }
    if res is not None and "gamma_etkin_parametre" in res.info:
        row["BR_etkin_parametre(gamma)"] = round(res.info["gamma_etkin_parametre"], 2)
    ys, names = g["y_scaler"], g["out_names"]
    _subset_metrics(row, net, theta, g["Xtr"], g["Ttr"], g["Ttr_o"], ys, "egitim", names)
    _subset_metrics(row, net, theta, g["Xva"], g["Tva"], g["Tva_o"], ys, "dogrulama", names)
    _subset_metrics(row, net, theta, g["Xte"], g["Tte"], g["Tte_o"], ys, "test", names)
    _subset_metrics(row, net, theta, g["Xall"], g["Tall"], g["Tall_o"], ys, "tum", names)
    hist = res.history if (res is not None and keep_history) else None
    info = res.info if res is not None else {}
    return row, theta, hist, info


# --------------------------------------------------------------------- #
def _score(row, crit):
    col, mode = crit
    v = row.get(col, np.nan)
    if v is None or not np.isfinite(v):
        return np.inf
    return v if mode == "min" else -v


def grid_search(shared: dict, archs, algorithms, hidden_acts, repeats, n_jobs=1,
                criterion="val_mse", out_csv=None, top_k=10, log=print, progress_every=None):
    """Tüm kombinasyonları çalıştırır. Döndürür: (satırlar, en iyi k sonuç listesi)."""
    crit = CRITERIA.get(criterion)
    if crit is None:
        raise ValueError(f"Geçersiz seçim kriteri '{criterion}'. Geçerli: {list(CRITERIA)}")
    if crit[0].endswith("dogrulama") and shared["Xva"] is None:
        log("UYARI: doğrulama kümesi yok; seçim kriteri eğitim MSE'ye çevrildi.")
        crit = ("mse_egitim", "min")
    algorithms = [canonical(a) for a in algorithms]
    jobs = []
    for hidden, alg, a, rep in itertools.product(archs, algorithms, hidden_acts, range(1, repeats + 1)):
        jobs.append({"id": len(jobs) + 1, "hidden": tuple(hidden), "alg": alg, "act": a, "rep": rep})
    total = len(jobs)
    # Büyük ağları önce çalıştır (yük dengeleme)
    order = sorted(jobs, key=lambda j: -sum(j["hidden"]) * (3 if j["alg"] in ("trainlm", "trainbr") else 1))
    cpu = os.cpu_count() or 1
    if not n_jobs:
        n_jobs = cpu
    elif n_jobs < 0:  # -1 = tüm çekirdekler, -2 = biri hariç ...
        n_jobs = max(1, cpu + 1 + n_jobs)
    n_jobs = min(n_jobs, total) if total else 1
    log(f"Toplam {total} eğitim çalıştırılacak "
        f"({len(archs)} mimari x {len(algorithms)} algoritma x {len(hidden_acts)} aktivasyon x {repeats} tekrar), "
        f"{n_jobs} paralel işlem.")
    progress_every = progress_every or max(1, total // 50)

    rows, top = [], []
    writer, fh = None, None
    t0 = time.perf_counter()
    best_score = np.inf

    def handle(result):
        nonlocal writer, fh, best_score
        row, theta, hist, info = result
        rows.append(row)
        s = _score(row, crit)
        top.append((s, row, theta, hist, info))
        top.sort(key=lambda t: t[0])
        del top[top_k:]
        if s < best_score:
            best_score = s
        if out_csv:
            if writer is None:
                fh = open(out_csv, "w", newline="", encoding="utf-8-sig")
                writer = csv.DictWriter(fh, fieldnames=list(row.keys()), extrasaction="ignore")
                writer.writeheader()
            writer.writerow(row)
            fh.flush()
        n = len(rows)
        if n % progress_every == 0 or n == total:
            el = time.perf_counter() - t0
            eta = el / n * (total - n)
            b = top[0][1]
            log(f"  [{n}/{total}] %{100 * n / total:5.1f}  geçen {el:7.1f}s  kalan ~{eta:7.1f}s  "
                f"| en iyi: {b['mimari']} {b['algoritma']} {crit[0]}={b.get(crit[0], np.nan):.4g}")

    try:
        if n_jobs == 1:
            _init_worker(shared)
            for j in order:
                handle(run_one(j))
        else:
            with ProcessPoolExecutor(max_workers=n_jobs, initializer=_init_worker,
                                     initargs=(shared,)) as ex:
                futs = [ex.submit(run_one, j) for j in order]
                for f in as_completed(futs):
                    handle(f.result())
    finally:
        if fh:
            fh.close()
    rows.sort(key=lambda r: r["id"])
    return rows, top, crit
