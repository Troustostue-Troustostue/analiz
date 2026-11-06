"""YSA paketi doğruluk testleri:  python -m pytest tests -q"""
import json

import numpy as np
import pytest

from ysa import MLP, Dataset, Model, Scaler, train
from ysa import activations as act
from ysa import analysis
from ysa.search import build_architectures, parse_range

ACTS = ["tansig", "logsig", "purelin", "softplus", "elu", "radbas", "swish"]


def _net(hidden=(5, 3), d=3, m=2, a="tansig", seed=0):
    rng = np.random.default_rng(seed)
    net = MLP(d, list(hidden), m, hidden_act=a)
    th = net.init_params(rng) + 0.1 * rng.standard_normal(net.n_params)
    X = rng.uniform(-1, 1, (7, d))
    return net, th, X


@pytest.mark.parametrize("a", ACTS + ["poslin"])
def test_activation_derivatives(a):
    z = np.linspace(-3, 3, 41) + 0.013
    f, d1, d2 = act.evaluate(a, z)
    h = 1e-5
    fp, d1p, _ = act.evaluate(a, z + h)
    fm, d1m, _ = act.evaluate(a, z - h)
    assert np.allclose(d1, (fp - fm) / (2 * h), atol=1e-6)
    assert np.allclose(d2, (d1p - d1m) / (2 * h), atol=1e-5)


@pytest.mark.parametrize("hidden", [(4,), (5, 3), (3, 4, 2)])
def test_param_jacobian_and_gradient(hidden):
    net, th, X = _net(hidden)
    J, Y = net.jacobian_params(th, X)
    h = 1e-6
    Jn = np.empty_like(J)
    for i in range(net.n_params):
        e = np.zeros(net.n_params); e[i] = h
        Jn[:, i] = ((net.forward(th + e, X) - net.forward(th - e, X)) / (2 * h)).ravel()
    assert np.allclose(J, Jn, atol=1e-7)
    T = np.random.default_rng(1).standard_normal(Y.shape)
    sse, g = net.sse_and_gradient(th, X, T)
    assert np.allclose(g, 2 * J.T @ (Y - T).ravel(), atol=1e-9)


@pytest.mark.parametrize("a", ACTS)
def test_input_jacobian_hessian(a):
    net, th, X = _net((5, 4), a=a)
    Y, Jx, Hx = net.input_derivatives(th, X)
    assert np.allclose(Y, net.forward(th, X))
    h = 1e-5
    d = X.shape[1]
    for i in range(d):
        e = np.zeros(d); e[i] = h
        Jp = net.input_derivatives(th, X + e, hessian=False)[1]
        Jm = net.input_derivatives(th, X - e, hessian=False)[1]
        assert np.allclose(Jx[:, :, i], (net.forward(th, X + e) - net.forward(th, X - e)) / (2 * h), atol=1e-7)
        assert np.allclose(Hx[:, :, :, i], (Jp - Jm) / (2 * h), atol=1e-6)
    assert np.allclose(Hx, np.swapaxes(Hx, 2, 3), atol=1e-10)  # simetri


def test_model_derivatives_original_units():
    rng = np.random.default_rng(3)
    X = rng.uniform([0, 100, 5], [1, 500, 25], (30, 3))
    T = np.c_[X[:, 0] * 50 + X[:, 1] * 0.2, np.sin(X[:, 2] / 5) * 10]
    net, th, _ = _net((6,), d=3, m=2)
    xs, ys = Scaler().fit(X), Scaler().fit(T)
    m = Model(net, th, xs, ys, ["a", "b", "c"], ["y1", "y2"])
    Y, Jx, Hx = m.derivatives(X[:4])
    for i in range(3):
        h = 1e-4 * (X[:, i].max() - X[:, i].min())
        e = np.zeros(3); e[i] = h
        Jn = (m.predict(X[:4] + e) - m.predict(X[:4] - e)) / (2 * h)
        assert np.allclose(Jx[:, :, i], Jn, rtol=1e-5, atol=1e-8)
        Jp = m.derivatives(X[:4] + e, hessian=False)[1]
        Jm = m.derivatives(X[:4] - e, hessian=False)[1]
        assert np.allclose(Hx[:, :, :, i], (Jp - Jm) / (2 * h), rtol=1e-4, atol=1e-8)


def _toy():
    rng = np.random.default_rng(5)
    X = rng.uniform(-1, 1, (120, 2))
    T = np.c_[np.sin(2 * X[:, 0]) + 0.5 * X[:, 1] ** 2, X[:, 0] * X[:, 1]]
    return Dataset(X[:80], T[:80], X[80:100], T[80:100], X[100:], T[100:])


@pytest.mark.parametrize("alg", ["trainlm", "trainbr", "trainscg", "trainbfg", "trainrp", "traingdx", "adam"])
def test_trainers_learn(alg):
    ds = _toy()
    net = MLP(2, [8], 2)
    th0 = net.init_params(np.random.default_rng(0))
    e0 = np.mean((net.forward(th0, ds.Xte) - ds.Tte) ** 2)
    epochs = 300 if alg in ("trainlm", "trainbr", "trainscg", "trainbfg") else 3000
    res = train(net, th0, ds, alg, {"epochs": epochs, "max_fail": 50})
    e1 = np.mean((net.forward(res.theta, ds.Xte) - ds.Tte) ** 2)
    assert e1 < 0.5 * e0
    if alg in ("trainlm", "trainbr"):
        assert e1 < 1e-3


def test_large_net_uses_woodbury_path():
    ds = _toy()
    net = MLP(2, [60, 10], 2)  # P > N*m -> dual çözüm
    assert net.n_params > ds.Ttr.size
    res = train(net, net.init_params(np.random.default_rng(1)), ds, "trainlm", {"epochs": 50})
    assert np.mean((net.forward(res.theta, ds.Xte) - ds.Tte) ** 2) < 0.05


def test_save_load_and_standalone(tmp_path):
    ds = _toy()
    net = MLP(2, [4, 3], 2, hidden_act="logsig")
    res = train(net, net.init_params(np.random.default_rng(2)), ds, "trainlm", {"epochs": 30})
    m = Model(net, res.theta, Scaler().fit(ds.Xtr), Scaler().fit(ds.Ttr), ["x1", "x2"], ["y1", "y2"])
    p = tmp_path / "m.json"
    m.save(p)
    m2 = Model.load(p)
    assert np.allclose(m.predict(ds.Xte), m2.predict(ds.Xte))
    code = analysis.standalone_python(m)
    ns = {}
    exec(code.replace('if __name__ == "__main__":', "if False:"), ns)
    assert np.allclose(ns["tahmin"](ds.Xte), m.predict(ds.Xte))
    assert json.loads(p.read_text(encoding="utf-8"))["sizes"] == [2, 4, 3, 2]


def test_ranges():
    assert parse_range([1, 5]) == [1, 2, 3, 4, 5]
    assert parse_range([10, 30, 10]) == [10, 20, 30]
    assert parse_range({"liste": [8, 3]}) == [3, 8]
    archs = build_architectures([[1, 3], [2, 4]], [1, 2])
    assert len(archs) == 3 + 9 and (1,) in archs and (3, 4) in archs


def test_end_to_end_runner(tmp_path):
    from pathlib import Path
    from ysa.runner import DEFAULT_CONFIG, _merge, run
    data = Path(__file__).resolve().parents[1] / "ornek_veri" / "cumg04_tel_cekme_SENTETIK.xlsx"
    cfg = _merge(DEFAULT_CONFIG, {
        "veri": {"dosya": str(data), "girdiler": "A:E", "ciktilar": "F:I"},
        "katmanlar": [[2, 3], [2, 2]], "katman_sayilari": [1, 2],
        "egitim_algoritmalari": ["trainlm"], "tekrar": 1, "paralel_is": 1,
        "egitim_parametreleri": {"epochs": 20}, "grafikler": False,
        "cikti_klasoru": str(tmp_path)})
    out = run(cfg, log=lambda *a: None)
    assert len(out["results"]) == 2 + 2
    assert (out["out_dir"] / "YSA_Rapor.xlsx").exists()
    assert (out["out_dir"] / "en_iyi_model_tahmin.py").exists()
