"""Girdi-çıktı ilişkisi analizleri.

* Jacobian  (∂y/∂x)  : her örnek noktasında, orijinal birimlerde
* Hessian   (∂²y/∂x²): eğrilik ve girdi-girdi etkileşimleri
* PaD (Partial Derivatives, Gevrey 2003) göreli katkı (%)
* Elastisite / normalize duyarlılık
* Garson ve Olden (bağlantı ağırlıkları) önem yöntemleri
* Tepki eğrileri (bir girdi değişirken diğerleri sabit)
* Ağırlık uzayında Hessian (Gauss-Newton JᵀJ ve tam Hessian) + özdeğerler
* Açık matematiksel formül, Excel formülleriyle hesap sayfası, bağımsız Python tahmin dosyası
"""
from __future__ import annotations

import warnings

import numpy as np
import pandas as pd

from . import activations as act
from .model import Model


# ---------------------------------------------------------------------- #
#  Jacobian / Hessian özetleri
# ---------------------------------------------------------------------- #
def continuous_mask(model: Model) -> np.ndarray:
    """True: sürekli (sayısal) girdi; False: kategorik değişkenin one-hot sütunu.
    One-hot sütunlarına göre türev fiziksel olarak anlamsızdır (girdi yalnız 0/1 olabilir);
    bu sütunlar için türev yerine categorical_effects() kullanılır."""
    mask = np.ones(len(model.input_names), bool)
    spec = model.spec
    if spec is not None and spec.categories:
        cat_names = {f"{c}={v}" for c, vals in spec.categories.items() for v in vals}
        mask = np.array([n not in cat_names for n in model.input_names])
    return mask


def jacobian_tables(model: Model, X):
    """Jacobian özet tabloları (orijinal birimlerde).
    Kategorik (one-hot) sütunlar için normalize duyarlılık/elastisite/PaD hesaplanmaz (NaN)."""
    Y, Jx, Hx = model.derivatives(X, hessian=True)
    cont = continuous_mask(model)
    ins, outs = model.input_names, model.output_names
    mean = pd.DataFrame(Jx.mean(axis=0), index=outs, columns=ins)
    mean_abs = pd.DataFrame(np.abs(Jx).mean(axis=0), index=outs, columns=ins)
    std = pd.DataFrame(Jx.std(axis=0), index=outs, columns=ins)
    # Elastisite: (∂y/∂x)·(x/y) ortalaması -> %1 girdi değişimine % çıktı tepkisi
    with np.errstate(divide="ignore", invalid="ignore"):
        el = Jx * (X[:, None, :] / Y[:, :, None])
        el[~np.isfinite(el)] = np.nan
    el[:, :, ~cont] = np.nan
    with warnings.catch_warnings():
        warnings.simplefilter("ignore", RuntimeWarning)
        elastic = pd.DataFrame(np.nanmean(el, axis=0), index=outs, columns=ins)
    # Aralığa göre normalize duyarlılık: (∂y/∂x)·(xmax-xmin)/(ymax-ymin)
    xr = np.where(model.xs.xmax - model.xs.xmin == 0, 1, model.xs.xmax - model.xs.xmin)
    yr = np.where(model.ys.xmax - model.ys.xmin == 0, 1, model.ys.xmax - model.ys.xmin)
    norm = Jx * xr[None, None, :] / yr[None, :, None]
    norm[:, :, ~cont] = np.nan
    norm_mean = pd.DataFrame(norm.mean(axis=0), index=outs, columns=ins)
    # PaD: SSD_i = Σ_n (∂y/∂x_i)²  (normalize türevle) -> %  (yalnız sürekli girdiler)
    ssd = np.sum(norm ** 2, axis=0)
    tot = np.nansum(ssd, axis=1, keepdims=True)
    pad = pd.DataFrame(100 * ssd / np.where(tot == 0, 1, tot), index=outs, columns=ins)
    # Örnek bazlı uzun tablo
    N, m, d = Jx.shape
    long = pd.DataFrame({
        "ornek": np.repeat(np.arange(1, N + 1), m * d),
        "cikti": np.tile(np.repeat(outs, d), N),
        "girdi": np.tile(ins, N * m),
        "dy_dx": Jx.ravel(),
    })
    hess_mean = {o: pd.DataFrame(Hx[:, k].mean(axis=0), index=ins, columns=ins) for k, o in enumerate(outs)}
    hess_abs = {o: pd.DataFrame(np.abs(Hx[:, k]).mean(axis=0), index=ins, columns=ins) for k, o in enumerate(outs)}
    return {
        "jac_mean": mean, "jac_mean_abs": mean_abs, "jac_std": std, "elasticity": elastic,
        "jac_norm": norm_mean, "pad_percent": pad, "jac_long": long,
        "hess_mean": hess_mean, "hess_abs": hess_abs, "Jx": Jx, "Hx": Hx, "Y": Y,
        "continuous": cont, "categorical_effects": categorical_effects(model, X),
    }


def categorical_effects(model: Model, X):
    """Kategorik girdilerin etkisi (ayrık karşılaştırma, kısmi bağımlılık):
    tüm örneklerde kategori sırayla her seviyeye ayarlanır, ortalama tahmin alınır.
    'fark' sütunları: o seviyenin, seviyelerin ortalamasına göre farkı."""
    spec = model.spec
    if spec is None or not spec.categories:
        return None
    X = np.asarray(X, float)
    rows = []
    for c, vals in spec.categories.items():
        cols = [model.input_names.index(f"{c}={v}") for v in vals]
        preds = []
        for j in range(len(vals)):
            Xc = X.copy()
            Xc[:, cols] = 0.0
            Xc[:, cols[j]] = 1.0
            preds.append(model.predict(Xc).mean(axis=0))
        preds = np.array(preds)
        base = preds.mean(axis=0)
        for j, v in enumerate(vals):
            row = {"degisken": c, "seviye": v}
            for k, o in enumerate(model.output_names):
                row[f"{o} (ort. tahmin)"] = preds[j, k]
            for k, o in enumerate(model.output_names):
                row[f"{o} fark"] = preds[j, k] - base[k]
            rows.append(row)
    return pd.DataFrame(rows)


def jacobian_at_point(model: Model, x):
    """Tek bir çalışma noktasında Jacobian ve Hessian tabloları."""
    _, Jx, Hx = model.derivatives(np.atleast_2d(x), hessian=True)
    J = pd.DataFrame(Jx[0], index=model.output_names, columns=model.input_names)
    H = {o: pd.DataFrame(Hx[0, k], index=model.input_names, columns=model.input_names)
         for k, o in enumerate(model.output_names)}
    return J, H


# ---------------------------------------------------------------------- #
#  Ağırlık tabanlı önem yöntemleri
# ---------------------------------------------------------------------- #
def garson(model: Model):
    """Garson (1991) göreli önem (%). Çok katmanlı ağlarda |W| çarpım zinciri ile genelleştirilmiştir."""
    Ws = [W for W, _ in model.weights]
    if len(Ws) == 2:
        W1, W2 = np.abs(Ws[0]), np.abs(Ws[1])  # (h,d), (m,h)
        c = W1 / W1.sum(axis=1, keepdims=True)  # (h,d)
        imp = np.einsum("kh,hd->kd", W2, c)
    else:
        imp = np.abs(Ws[0])
        imp = imp / imp.sum(axis=1, keepdims=True)
        for W in Ws[1:]:
            imp = np.abs(W) @ imp
    imp = 100 * imp / imp.sum(axis=1, keepdims=True)
    return pd.DataFrame(imp, index=model.output_names, columns=model.input_names)


def olden(model: Model):
    """Olden & Jackson (2002) bağlantı ağırlığı yöntemi (işaretli). Çok katmanda W çarpımı."""
    Ws = [W for W, _ in model.weights]
    P = Ws[0]
    for W in Ws[1:]:
        P = W @ P
    df = pd.DataFrame(P, index=model.output_names, columns=model.input_names)
    rel = 100 * np.abs(P) / np.abs(P).sum(axis=1, keepdims=True)
    return df, pd.DataFrame(rel, index=model.output_names, columns=model.input_names)


# ---------------------------------------------------------------------- #
#  Tepki eğrileri
# ---------------------------------------------------------------------- #
def response_curves(model: Model, X, n_points=50, base="mean"):
    """Her girdi kendi aralığında taranırken diğer girdiler sabit (ortalama/medyan) tutulur."""
    X = np.asarray(X, float)
    ref = X.mean(axis=0) if base == "mean" else np.median(X, axis=0)
    cont = continuous_mask(model)
    if (~cont).any():  # kategorik değişkenlerde referans = en sık seviye (geçerli one-hot)
        for c, vals in model.spec.categories.items():
            cols = [model.input_names.index(f"{c}={v}") for v in vals]
            ref[cols] = 0.0
            ref[cols[int(np.argmax(X[:, cols].sum(axis=0)))]] = 1.0
    rows = []
    for i, name in enumerate(model.input_names):
        if not cont[i]:
            continue
        grid = np.linspace(X[:, i].min(), X[:, i].max(), n_points)
        Xg = np.repeat(ref[None], n_points, axis=0)
        Xg[:, i] = grid
        Y, Jx = model.derivatives(Xg, hessian=False)
        for p in range(n_points):
            row = {"girdi": name, "girdi_degeri": grid[p]}
            for k, o in enumerate(model.output_names):
                row[o] = Y[p, k]
                row[f"d({o})/d({name})"] = Jx[p, k, i]
            rows.append(row)
    return pd.DataFrame(rows), ref


# ---------------------------------------------------------------------- #
#  Ağırlık uzayında Hessian
# ---------------------------------------------------------------------- #
def weight_hessian(model: Model, Xn, Tn, exact=True):
    """Performans fonksiyonunun (normalize MSE) ağırlıklara göre Hessian'ı.

    Gauss-Newton: H ≈ 2 JᵀJ / n   (LM'nin kullandığı yaklaşım)
    Tam         : analitik gradyanın merkezi farkları ile (P adet gradyan)
    """
    net, th = model.net, model.theta
    n = Tn.size
    J, _ = net.jacobian_params(th, Xn)
    H_gn = 2.0 * (J.T @ J) / n
    H_ex = None
    if exact:
        P = net.n_params
        H_ex = np.empty((P, P))
        for i in range(P):
            h = 1e-5 * max(1.0, abs(th[i]))
            tp, tm = th.copy(), th.copy()
            tp[i] += h
            tm[i] -= h
            gp = net.sse_and_gradient(tp, Xn, Tn)[1]
            gm = net.sse_and_gradient(tm, Xn, Tn)[1]
            H_ex[:, i] = (gp - gm) / (2 * h * n)
        H_ex = 0.5 * (H_ex + H_ex.T)
    return H_gn, H_ex, J


def hessian_summary(H):
    ev = np.linalg.eigvalsh(H)
    pos = ev[ev > 1e-12 * max(abs(ev).max(), 1e-300)]
    return {
        "ozdeger_min": float(ev.min()), "ozdeger_max": float(ev.max()),
        "negatif_ozdeger_sayisi": int(np.sum(ev < -1e-10 * max(abs(ev).max(), 1e-300))),
        "kosul_sayisi": float(pos.max() / pos.min()) if pos.size else float("inf"),
        "etkin_rank": int(pos.size), "boyut": int(H.shape[0]),
    }, ev


# ---------------------------------------------------------------------- #
#  Açık formül ve dışa aktarma
# ---------------------------------------------------------------------- #
def _fmt(v):
    return f"{v:+.10g}"


def formula_text(model: Model, expanded=True, max_terms=400) -> str:
    net = model.net
    xs, ys = model.xs, model.ys
    ins, outs = model.input_names, model.output_names
    L = []
    L.append("YAPAY SİNİR AĞI - AÇIK MATEMATİKSEL MODEL")
    L.append("=" * 60)
    L.append(f"Mimari: {net.describe()}  (girdi - gizli katmanlar - çıktı)")
    L.append("Aktivasyonlar: " + ", ".join(f"Katman {i + 1}: {a}" for i, a in enumerate(net.acts)))
    for a in sorted(set(net.acts)):
        L.append("   " + act.FORMULA_TEXT[a])
    L.append(f"Toplam parametre (ağırlık + bias): {net.n_params}")
    L.append("")
    L.append("ADIM 1 - Girdi normalizasyonu (" + xs.method + "):")
    for i, nm in enumerate(ins):
        L.append(f"   xn{i + 1} = ({nm} - {xs.offset[i]:.10g}) * {xs.gain[i]:.10g} + {xs.target:.10g}")
    L.append("")
    L.append("ADIM 2 - Katman hesapları:  a^l = f_l( W^l · a^(l-1) + b^l ),  a^0 = xn")
    prev = [f"xn{i + 1}" for i in range(len(ins))]
    for l, (W, b) in enumerate(model.weights, start=1):
        f = net.acts[l - 1]
        cur = []
        is_out = l == net.n_layers
        for j in range(W.shape[0]):
            name = f"yn{j + 1}" if is_out else f"h{l}_{j + 1}"
            cur.append(name)
            if expanded and W.shape[1] <= max_terms:
                terms = " ".join(f"{_fmt(W[j, i])}*{prev[i]}" for i in range(W.shape[1]))
                L.append(f"   {name} = {f}( {terms} {_fmt(b[j])} )")
            else:
                L.append(f"   {name} = {f}( Σ_i W{l}[{j + 1},i]*{prev[0][:-1]}i + b{l}[{j + 1}] )")
        prev = cur
        L.append("")
    L.append("ADIM 3 - Çıktıların orijinal birime dönüştürülmesi:")
    for k, nm in enumerate(outs):
        L.append(f"   {nm} = (yn{k + 1} - {ys.target:.10g}) / {ys.gain[k]:.10g} + {ys.offset[k]:.10g}")
    return "\n".join(L)


def standalone_python(model: Model) -> str:
    """Sadece numpy gerektiren, ağırlıkları gömülü bağımsız tahmin betiği."""
    Ws, bs = model.net.unpack(model.theta)
    lines = [
        '"""Otomatik üretilmiş YSA tahmin fonksiyonu (sadece numpy gerektirir)."""',
        "import numpy as np",
        "",
        f"GIRDILER = {model.input_names!r}",
        f"CIKTILAR = {model.output_names!r}",
        f"X_OFFSET = np.array({model.xs.offset.tolist()!r})",
        f"X_GAIN = np.array({model.xs.gain.tolist()!r})",
        f"X_TARGET = {model.xs.target!r}",
        f"Y_OFFSET = np.array({model.ys.offset.tolist()!r})",
        f"Y_GAIN = np.array({model.ys.gain.tolist()!r})",
        f"Y_TARGET = {model.ys.target!r}",
        "",
    ]
    for l, (W, b) in enumerate(zip(Ws, bs), start=1):
        lines.append(f"W{l} = np.array({W.tolist()!r})")
        lines.append(f"b{l} = np.array({b.tolist()!r})")
    lines += ["", "", "def tahmin(X):", '    """X: (N, girdi_sayisi) orijinal birimlerde. Döndürür (N, cikti_sayisi)."""',
              "    X = np.atleast_2d(np.asarray(X, dtype=float))",
              "    a = (X - X_OFFSET) * X_GAIN + X_TARGET"]
    for l, f in enumerate(model.net.acts, start=1):
        lines.append(f"    z = a @ W{l}.T + b{l}")
        lines.append(f"    a = {act.NUMPY_CODE[f]}")
    lines += ["    return (a - Y_TARGET) / Y_GAIN + Y_OFFSET", "", "",
              'if __name__ == "__main__":',
              "    ornek = (X_OFFSET + 0.0)  # örnek nokta: her girdinin minimum değeri (mapminmax)",
              "    print(dict(zip(CIKTILAR, tahmin(ornek)[0])))", ""]
    return "\n".join(lines)


def write_excel_calculator(ws, model: Model, example_x=None):
    """openpyxl çalışma sayfasına, ağırlık/biasları ve tüm ara hesapları
    Excel formülleriyle yazar. Kullanıcı girdi hücrelerini değiştirince tahmin
    Excel içinde canlı hesaplanır (ağın tamamen şeffaf doğrulanması)."""
    from openpyxl.styles import Font, PatternFill
    from openpyxl.utils import get_column_letter as CL

    bold = Font(bold=True)
    inp_fill = PatternFill("solid", fgColor="FFF2CC")
    out_fill = PatternFill("solid", fgColor="D9EAD3")
    net, xs, ys = model.net, model.xs, model.ys
    d = len(model.input_names)
    if example_x is None:
        example_x = (xs.xmin + xs.xmax) / 2.0

    ws["A1"] = "YSA Excel Hesap Sayfası - sarı hücrelere girdi yazın, yeşil hücreler tahmindir"
    ws["A1"].font = Font(bold=True, size=12)
    ws["A3"], ws["A4"], ws["A5"], ws["A6"], ws["A7"] = ("Girdi", "Değer (orijinal)", "offset", "gain", "Normalize xn")
    for c in ("A3", "A4", "A5", "A6", "A7"):
        ws[c].font = bold
    for i in range(d):
        col = CL(i + 2)
        ws[f"{col}3"] = model.input_names[i]
        ws[f"{col}4"] = float(example_x[i])
        ws[f"{col}4"].fill = inp_fill
        ws[f"{col}5"] = float(xs.offset[i])
        ws[f"{col}6"] = float(xs.gain[i])
        ws[f"{col}7"] = f"=({col}4-{col}5)*{col}6+({xs.target})"
    # önceki katman aktivasyonları: satır vektörü olarak (row, first_col, n)
    prev_row, prev_c0, prev_n, prev_is_row = 7, 2, d, True
    r = 9
    for l, (W, b) in enumerate(model.weights, start=1):
        f = net.acts[l - 1]
        n_out, n_in = W.shape
        ws.cell(r, 1, f"KATMAN {l}  (aktivasyon: {f})").font = bold
        r += 1
        ws.cell(r, 1, "Nöron").font = bold
        for j in range(n_in):
            ws.cell(r, j + 2, f"W{l}[:,{j + 1}]").font = bold
        ws.cell(r, n_in + 2, f"b{l}").font = bold
        ws.cell(r, n_in + 3, "n = W·a + b").font = bold
        ws.cell(r, n_in + 4, f"a = {f}(n)").font = bold
        r += 1
        first = r
        for i in range(n_out):
            ws.cell(r, 1, f"{l}.{i + 1}")
            for j in range(n_in):
                ws.cell(r, j + 2, float(W[i, j]))
            ws.cell(r, n_in + 2, float(b[i]))
            wr = f"{CL(2)}{r}:{CL(n_in + 1)}{r}"
            if prev_is_row:
                pr = f"${CL(prev_c0)}${prev_row}:${CL(prev_c0 + prev_n - 1)}${prev_row}"
                ws.cell(r, n_in + 3, f"=SUMPRODUCT({wr},{pr})+{CL(n_in + 2)}{r}")
            else:
                pr = f"${CL(prev_c0)}${prev_row}:${CL(prev_c0)}${prev_row + prev_n - 1}"
                ws.cell(r, n_in + 3, f"=MMULT({wr},{pr})+{CL(n_in + 2)}{r}")
            ws.cell(r, n_in + 4, "=" + act.EXCEL_TEMPLATE[f].format(x=f"{CL(n_in + 3)}{r}"))
            r += 1
        prev_row, prev_c0, prev_n, prev_is_row = first, n_in + 4, n_out, False
        r += 1
    ws.cell(r, 1, "ÇIKTILAR (orijinal birim)").font = bold
    r += 1
    for k, nm in enumerate(model.output_names):
        ws.cell(r, 1, nm).font = bold
        cell = f"{CL(prev_c0)}{prev_row + k}"
        ws.cell(r, 2, f"=({cell}-({ys.target}))/{float(ys.gain[k])!r}+{float(ys.offset[k])!r}")
        ws.cell(r, 2).fill = out_fill
        r += 1
    ws.column_dimensions["A"].width = 26
    return r
