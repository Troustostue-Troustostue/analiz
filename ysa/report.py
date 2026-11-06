"""Excel raporu, grafikler ve model dosyalarının üretilmesi."""
from __future__ import annotations

from pathlib import Path

import numpy as np
import pandas as pd

from . import analysis, metrics


def _safe_sheet(name, used):
    bad = '[]:*?/\\'
    s = "".join("_" if c in bad else c for c in str(name))[:31]
    base, i = s, 2
    while s in used:
        suf = f"_{i}"
        s = base[: 31 - len(suf)] + suf
        i += 1
    used.add(s)
    return s


def weights_frames(model):
    """Her katman için etiketli ağırlık matrisi ve bias tablosu."""
    frames = []
    prev = list(model.input_names)
    L = model.net.n_layers
    for l, (W, b) in enumerate(model.weights, start=1):
        rows = ([f"Çıktı: {o}" for o in model.output_names] if l == L
                else [f"Katman{l}_Nöron{j + 1}" for j in range(W.shape[0])])
        dfW = pd.DataFrame(W, index=rows, columns=prev)
        dfW[f"BIAS b{l}"] = b
        frames.append((l, dfW))
        prev = rows
    return frames


def write_report(path, model, results_df, summary_df, best_row, history, X, T, subsets,
                 cfg, crit, extra_info=None, weight_hessian=True, log=print):
    """Tüm sonuçları tek bir Excel dosyasına yazar."""
    path = Path(path)
    used = set()
    ins, outs = model.input_names, model.output_names
    Y = model.predict(X)
    jt = analysis.jacobian_tables(model, X)
    curves, ref = analysis.response_curves(model, X)
    gars = analysis.garson(model)
    old_raw, old_rel = analysis.olden(model)

    with pd.ExcelWriter(path, engine="openpyxl") as xw:
        # --- Özet
        info = [
            ("Seçim kriteri", f"{crit[0]} ({'en küçük' if crit[1] == 'min' else 'en büyük'})"),
            ("En iyi mimari (girdi-gizli-çıktı)", best_row["mimari"]),
            ("Eğitim algoritması", best_row["algoritma"]),
            ("Gizli katman aktivasyonu", best_row["aktivasyon"]),
            ("Çıkış aktivasyonu", model.net.acts[-1]),
            ("Tekrar no", best_row["tekrar"]),
            ("Parametre sayısı (ağırlık+bias)", model.net.n_params),
            ("Epok / en iyi epok", f"{best_row['epok']} / {best_row['en_iyi_epok']}"),
            ("Durma nedeni", best_row["durma_nedeni"]),
            ("Normalizasyon", model.xs.method),
            ("Örnek sayısı (eğitim/doğrulama/test)",
             f"{len(subsets['egitim'])}/{len(subsets['dogrulama'])}/{len(subsets['test'])}"),
        ]
        for k in ("mse_egitim", "mse_dogrulama", "mse_test", "mse_tum", "R_egitim", "R_dogrulama", "R_test", "R_tum"):
            info.append((k + " (normalize)", best_row.get(k)))
        for k, v in (extra_info or {}).items():
            info.append((k, v))
        pd.DataFrame(info, columns=["Bilgi", "Değer"]).to_excel(xw, sheet_name=_safe_sheet("Ozet", used), index=False)

        # --- Çıktı bazında başarı (orijinal birim)
        rows = []
        for tag, idx in subsets.items():
            if len(idx) == 0:
                continue
            pm = metrics.per_output(T[idx], Y[idx])
            for k, o in enumerate(outs):
                rows.append({"Küme": tag, "Çıktı": o, **{m: float(v[k]) for m, v in pm.items()}})
        pm = metrics.per_output(T, Y)
        for k, o in enumerate(outs):
            rows.append({"Küme": "tum", "Çıktı": o, **{m: float(v[k]) for m, v in pm.items()}})
        pd.DataFrame(rows).to_excel(xw, sheet_name=_safe_sheet("Basari_Cikti_Bazinda", used), index=False)

        # --- Tarama sonuçları
        results_df.to_excel(xw, sheet_name=_safe_sheet("Tum_Calistirmalar", used), index=False)
        summary_df.to_excel(xw, sheet_name=_safe_sheet("Mimari_Ozet", used), index=False)

        # --- Ağırlıklar ve biaslar
        for l, dfW in weights_frames(model):
            dfW.to_excel(xw, sheet_name=_safe_sheet(f"Agirlik_Bias_Katman{l}", used))
        pd.DataFrame({"parametre": model.net.param_labels(), "deger": model.theta}).to_excel(xw, sheet_name=_safe_sheet("Tum_Parametreler_Vektor", used), index=False)
        norm = pd.DataFrame({
            "degisken": ins + outs, "tur": ["girdi"] * len(ins) + ["çıktı"] * len(outs),
            "min": np.r_[model.xs.xmin, model.ys.xmin], "max": np.r_[model.xs.xmax, model.ys.xmax],
            "offset": np.r_[model.xs.offset, model.ys.offset], "gain": np.r_[model.xs.gain, model.ys.gain],
            "hedef_ymin": [model.xs.target] * len(ins) + [model.ys.target] * len(outs),
        })
        norm.to_excel(xw, sheet_name=_safe_sheet("Normalizasyon", used), index=False)

        # --- Tahminler
        pr = pd.DataFrame(X, columns=ins)
        lab = np.empty(len(X), dtype=object)
        for tag, idx in subsets.items():
            lab[idx] = tag
        pr.insert(0, "kume", lab)
        for k, o in enumerate(outs):
            pr[f"{o} (gerçek)"] = T[:, k]
            pr[f"{o} (YSA)"] = Y[:, k]
            pr[f"{o} hata"] = Y[:, k] - T[:, k]
            with np.errstate(divide="ignore", invalid="ignore"):
                pr[f"{o} hata %"] = 100 * (Y[:, k] - T[:, k]) / T[:, k]
        pr.to_excel(xw, sheet_name=_safe_sheet("Tahminler", used), index=False)

        # --- Jacobian / duyarlılık
        sh = _safe_sheet("Jacobian_Duyarlilik", used)
        r = 0
        blocks = [
            ("Ortalama Jacobian  ∂y/∂x  (orijinal birim: çıktı birimi / girdi birimi)", jt["jac_mean"]),
            ("Ortalama |∂y/∂x|", jt["jac_mean_abs"]),
            ("∂y/∂x standart sapma (yüksekse ilişki doğrusal değil)", jt["jac_std"]),
            ("Aralığa göre normalize duyarlılık  (∂y/∂x)·Δx/Δy  (boyutsuz, karşılaştırılabilir)", jt["jac_norm"]),
            ("Ortalama elastisite (∂y/∂x)·(x/y):  girdide %1 artış -> çıktıda % değişim", jt["elasticity"]),
            ("PaD yöntemi göreli katkı (%)  [Gevrey vd. 2003]", jt["pad_percent"]),
            ("Garson yöntemi göreli önem (%)", gars),
            ("Olden bağlantı ağırlığı (işaretli)", old_raw),
            ("Olden göreli önem (%)", old_rel),
        ]
        for title, df in blocks:
            pd.DataFrame([[title]]).to_excel(xw, sheet_name=sh, startrow=r, index=False, header=False)
            df.to_excel(xw, sheet_name=sh, startrow=r + 1)
            r += len(df) + 4
        ce = jt.get("categorical_effects")
        if ce is not None:
            pd.DataFrame([["Kategorik girdiler: türev yerine seviye karşılaştırması (diğer girdiler gerçek değerlerinde)"]]).to_excel(
                xw, sheet_name=sh, startrow=r, index=False, header=False)
            ce.to_excel(xw, sheet_name=sh, startrow=r + 1, index=False)
            r += len(ce) + 4
        jt["jac_long"].to_excel(xw, sheet_name=_safe_sheet("Jacobian_Ornek_Bazinda", used), index=False)

        # --- Hessian (girdi uzayı)
        sh = _safe_sheet("Hessian_Girdi", used)
        r = 0
        pd.DataFrame([["Ortalama Hessian ∂²y/∂xi∂xj (köşegen: eğrilik; köşegen dışı: girdi etkileşimi)"]]).to_excel(xw, sheet_name=sh, startrow=r, index=False, header=False)
        r += 2
        for o in outs:
            for title, d in ((f"{o}: ortalama Hessian", jt["hess_mean"][o]),
                             (f"{o}: ortalama |Hessian|", jt["hess_abs"][o])):
                pd.DataFrame([[title]]).to_excel(xw, sheet_name=sh, startrow=r, index=False, header=False)
                d.to_excel(xw, sheet_name=sh, startrow=r + 1)
                r += len(d) + 4
        # Referans (ortalama) noktadaki Jacobian/Hessian
        Jp, Hp = analysis.jacobian_at_point(model, ref)
        sh = _safe_sheet("Jacobian_Hessian_OrtNokta", used)
        pd.DataFrame({"girdi": ins, "deger (ortalama nokta)": ref}).to_excel(xw, sheet_name=sh, index=False)
        r = len(ins) + 3
        pd.DataFrame([["Jacobian (ortalama çalışma noktasında)"]]).to_excel(xw, sheet_name=sh, startrow=r, index=False, header=False)
        Jp.to_excel(xw, sheet_name=sh, startrow=r + 1)
        r += len(Jp) + 4
        for o, d in Hp.items():
            pd.DataFrame([[f"Hessian - {o}"]]).to_excel(xw, sheet_name=sh, startrow=r, index=False, header=False)
            d.to_excel(xw, sheet_name=sh, startrow=r + 1)
            r += len(d) + 4

        curves.to_excel(xw, sheet_name=_safe_sheet("Tepki_Egrileri", used), index=False)

        # --- Ağırlık uzayında Hessian
        if weight_hessian:
            Xn, Tn = model.xs.transform(X[subsets["egitim"]]), model.ys.transform(T[subsets["egitim"]])
            exact = model.net.n_params <= 600
            Hgn, Hex, _ = analysis.weight_hessian(model, Xn, Tn, exact=exact)
            sh = _safe_sheet("Hessian_Agirlik", used)
            s_gn, ev_gn = analysis.hessian_summary(Hgn)
            rows = [{"Hessian": "Gauss-Newton 2JᵀJ/n (LM yaklaşımı)", **s_gn}]
            ev_ex = None
            if Hex is not None:
                s_ex, ev_ex = analysis.hessian_summary(Hex)
                rows.append({"Hessian": "Tam Hessian (analitik gradyanın sonlu farkı)", **s_ex})
            pd.DataFrame(rows).to_excel(xw, sheet_name=sh, index=False)
            ev = pd.DataFrame({"ozdeger_GaussNewton": np.sort(ev_gn)[::-1]})
            if ev_ex is not None:
                ev["ozdeger_Tam"] = np.sort(ev_ex)[::-1]
            ev.to_excel(xw, sheet_name=sh, startrow=len(rows) + 3, index=False)
            if model.net.n_params <= 300:
                lab = model.net.param_labels()
                H = Hex if Hex is not None else Hgn
                pd.DataFrame(H, index=lab, columns=lab).to_excel(xw, sheet_name=_safe_sheet("Hessian_Agirlik_Matris", used))

        # --- Eğitim geçmişi
        if history:
            pd.DataFrame({"epok": history["epoch"], "egitim_mse": history["train"],
                          "dogrulama_mse": history["val"], "test_mse": history["test"],
                          "gradyan_normu": history["grad"], "mu/lr/kontrol": history["ctrl"]}).to_excel(xw, sheet_name=_safe_sheet("Egitim_Gecmisi", used), index=False)

        # --- Formül ve Excel hesap sayfası
        txt = analysis.formula_text(model, expanded=True)
        pd.DataFrame({"Açık model formülü": txt.split("\n")}).to_excel(xw, sheet_name=_safe_sheet("Formul", used), index=False)
        pd.DataFrame([{"anahtar": k, "deger": str(v)} for k, v in _flatten(cfg).items()]).to_excel(xw, sheet_name=_safe_sheet("Konfigurasyon", used), index=False)

        wb = xw.book
        ws = wb.create_sheet(_safe_sheet("Excel_Hesap_Makinesi", used))
        analysis.write_excel_calculator(ws, model, example_x=ref)

        for wsn in wb.worksheets:
            if wsn.title in ("Excel_Hesap_Makinesi",):
                continue
            for col in wsn.columns:
                width = max((len(str(c.value)) for c in col[:200] if c.value is not None), default=8)
                wsn.column_dimensions[col[0].column_letter].width = min(max(10, width + 2), 60)
    return jt, curves, gars, old_rel


def _flatten(d, pre=""):
    out = {}
    for k, v in d.items():
        key = f"{pre}{k}"
        if isinstance(v, dict):
            out.update(_flatten(v, key + "."))
        else:
            out[key] = v
    return out


# --------------------------------------------------------------------- #
#  Grafikler
# --------------------------------------------------------------------- #
def make_plots(out_dir, model, X, T, subsets, history, results_df, crit, jt, curves, log=print):
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
    except ImportError:
        log("matplotlib kurulu değil; grafikler atlandı (pip install matplotlib).")
        return []
    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    files = []
    ins, outs = model.input_names, model.output_names
    Y = model.predict(X)
    colors = {"egitim": "#2a6fdb", "dogrulama": "#2e9e5b", "test": "#d9534f"}

    # 1) Regresyon grafikleri
    m = len(outs)
    fig, axes = plt.subplots(1, m, figsize=(4.6 * m, 4.4), squeeze=False)
    for k, o in enumerate(outs):
        ax = axes[0, k]
        for tag, idx in subsets.items():
            if len(idx):
                pm = metrics.per_output(T[idx, k:k + 1], Y[idx, k:k + 1])
                ax.scatter(T[idx, k], Y[idx, k], s=22, alpha=0.8, c=colors.get(tag, "gray"),
                           label=f"{tag} (R={pm['R'][0]:.4f})", edgecolors="none")
        lo, hi = min(T[:, k].min(), Y[:, k].min()), max(T[:, k].max(), Y[:, k].max())
        ax.plot([lo, hi], [lo, hi], "k--", lw=1)
        ax.set_xlabel(f"Gerçek {o}")
        ax.set_ylabel(f"YSA {o}")
        ax.set_title(o)
        ax.legend(fontsize=8)
        ax.grid(alpha=0.3)
    fig.tight_layout()
    files.append(out_dir / "regresyon.png")
    fig.savefig(files[-1], dpi=150)
    plt.close(fig)

    # 2) Eğitim eğrisi
    if history:
        fig, ax = plt.subplots(figsize=(6.5, 4.2))
        ax.semilogy(history["epoch"], history["train"], label="Eğitim", color=colors["egitim"])
        if np.isfinite(history["val"]).any():
            ax.semilogy(history["epoch"], history["val"], label="Doğrulama", color=colors["dogrulama"])
        if np.isfinite(history["test"]).any():
            ax.semilogy(history["epoch"], history["test"], label="Test", color=colors["test"])
        ax.set_xlabel("Epok")
        ax.set_ylabel("MSE (normalize)")
        ax.set_title("Eğitim performansı")
        ax.grid(alpha=0.3, which="both")
        ax.legend()
        fig.tight_layout()
        files.append(out_dir / "egitim_egrisi.png")
        fig.savefig(files[-1], dpi=150)
        plt.close(fig)

    # 3) Tarama sonuçları
    col = crit[0]
    df = results_df.copy()
    agg = "min" if crit[1] == "min" else "max"
    one = df[df["gizli_katman_sayisi"] == 1]
    if len(one):
        g = one.groupby("n1")[col].agg(["mean", agg]).reset_index()
        fig, ax = plt.subplots(figsize=(7, 4.2))
        ax.plot(g["n1"], g["mean"], "o-", ms=3, label="tekrar ortalaması")
        ax.plot(g["n1"], g[agg], "s-", ms=3, label=f"en iyi ({agg})")
        ax.set_xlabel("1. gizli katman nöron sayısı")
        ax.set_ylabel(col)
        if crit[1] == "min":
            ax.set_yscale("log")
        ax.set_title("Tek gizli katman: nöron sayısına göre hata")
        ax.grid(alpha=0.3)
        ax.legend()
        fig.tight_layout()
        files.append(out_dir / "tarama_1katman.png")
        fig.savefig(files[-1], dpi=150)
        plt.close(fig)
    two = df[df["gizli_katman_sayisi"] == 2]
    if len(two):
        pv = two.groupby(["n1", "n2"])[col].agg(agg).unstack("n2")
        fig, ax = plt.subplots(figsize=(7.5, 5.5))
        data = np.log10(pv.to_numpy(float)) if crit[1] == "min" else pv.to_numpy(float)
        im = ax.imshow(data, aspect="auto", origin="lower", cmap="viridis_r" if crit[1] == "min" else "viridis",
                       extent=[pv.columns.min() - 0.5, pv.columns.max() + 0.5, pv.index.min() - 0.5, pv.index.max() + 0.5])
        cb = fig.colorbar(im, ax=ax)
        cb.set_label(("log10 " if crit[1] == "min" else "") + col)
        ax.set_xlabel("2. gizli katman nöron sayısı")
        ax.set_ylabel("1. gizli katman nöron sayısı")
        ax.set_title("İki gizli katman: hata haritası (en iyi tekrar)")
        fig.tight_layout()
        files.append(out_dir / "tarama_2katman_isi_haritasi.png")
        fig.savefig(files[-1], dpi=150)
        plt.close(fig)

    cont = jt["continuous"]
    ins_c = [n for n, c in zip(ins, cont) if c]
    # 4) Duyarlılık (PaD) çubuk grafiği (sürekli girdiler)
    pad = jt["pad_percent"][ins_c]
    ins = ins_c
    fig, ax = plt.subplots(figsize=(max(6, 1.2 * len(ins) * max(1, m) / 2), 4.4))
    w = 0.8 / m
    xpos = np.arange(len(ins))
    for k, o in enumerate(outs):
        ax.bar(xpos + k * w - 0.4 + w / 2, pad.loc[o].to_numpy(), width=w, label=o)
    ax.set_xticks(xpos)
    ax.set_xticklabels(ins, rotation=30, ha="right")
    ax.set_ylabel("Göreli katkı (%)")
    ax.set_title("Girdilerin çıktılara göreli katkısı (PaD - Jacobian tabanlı)")
    ax.legend(fontsize=8)
    ax.grid(alpha=0.3, axis="y")
    fig.tight_layout()
    files.append(out_dir / "duyarlilik_PaD.png")
    fig.savefig(files[-1], dpi=150)
    plt.close(fig)

    # 5) Normalize Jacobian ısı haritası
    J = jt["jac_norm"][ins_c]
    fig, ax = plt.subplots(figsize=(1.3 * len(ins) + 3, 0.7 * m + 2.2))
    v = np.abs(J.to_numpy()).max() or 1
    im = ax.imshow(J.to_numpy(), cmap="RdBu_r", vmin=-v, vmax=v, aspect="auto")
    for i in range(J.shape[0]):
        for j in range(J.shape[1]):
            ax.text(j, i, f"{J.iat[i, j]:.2f}", ha="center", va="center", fontsize=8)
    ax.set_xticks(range(len(ins)))
    ax.set_xticklabels(ins, rotation=30, ha="right")
    ax.set_yticks(range(m))
    ax.set_yticklabels(outs)
    fig.colorbar(im, ax=ax, label="ortalama (∂y/∂x)·Δx/Δy")
    ax.set_title("Normalize Jacobian (işaret: artırınca artar/azalır)")
    fig.tight_layout()
    files.append(out_dir / "jacobian_isi_haritasi.png")
    fig.savefig(files[-1], dpi=150)
    plt.close(fig)

    # 6) Hessian ısı haritaları (çıktı başına)
    fig, axes = plt.subplots(1, m, figsize=(4.2 * m + 1, 4), squeeze=False)
    for k, o in enumerate(outs):
        H = jt["hess_mean"][o].loc[ins_c, ins_c].to_numpy()
        # aralığa göre ölçekle: ∂²y/∂xi∂xj · Δxi·Δxj / Δy
        xr = (model.xs.xmax - model.xs.xmin)[cont]
        yr = (model.ys.xmax - model.ys.xmin)[k] or 1
        Hs = H * np.outer(xr, xr) / yr
        v = np.abs(Hs).max() or 1
        ax = axes[0, k]
        imh = ax.imshow(Hs, cmap="PuOr_r", vmin=-v, vmax=v)
        fig.colorbar(imh, ax=ax, fraction=0.046, pad=0.04)
        ax.set_xticks(range(len(ins)))
        ax.set_yticks(range(len(ins)))
        ax.set_xticklabels(ins, rotation=45, ha="right", fontsize=7)
        ax.set_yticklabels(ins, fontsize=7)
        ax.set_title(f"Hessian (normalize) - {o}", fontsize=9)
    fig.tight_layout()
    files.append(out_dir / "hessian_isi_haritasi.png")
    fig.savefig(files[-1], dpi=150)
    plt.close(fig)

    # 7) Tepki eğrileri
    d = len(ins)
    fig, axes = plt.subplots(m, d, figsize=(2.9 * d + 1, 2.4 * m + 0.6), squeeze=False)
    for i, name in enumerate(ins):
        sub = curves[curves["girdi"] == name]
        for k, o in enumerate(outs):
            ax = axes[k, i]
            ax.plot(sub["girdi_degeri"], sub[o], color="#2a6fdb")
            if k == m - 1:
                ax.set_xlabel(name, fontsize=8)
            if i == 0:
                ax.set_ylabel(o, fontsize=8)
            ax.tick_params(labelsize=7)
            ax.grid(alpha=0.3)
    fig.suptitle("Tepki eğrileri (diğer girdiler ortalama değerde sabit)", fontsize=10)
    fig.tight_layout()
    files.append(out_dir / "tepki_egrileri.png")
    fig.savefig(files[-1], dpi=150)
    plt.close(fig)

    # 8) Kategorik girdilerin etkisi
    ce = jt.get("categorical_effects")
    if ce is not None:
        fig, axes = plt.subplots(1, m, figsize=(4 * m, 3.8), squeeze=False)
        for k, o in enumerate(outs):
            ax = axes[0, k]
            lab = ce["degisken"] + "\n" + ce["seviye"]
            ax.bar(range(len(ce)), ce[f"{o} (ort. tahmin)"], color="#7a5cc4")
            ax.set_xticks(range(len(ce)))
            ax.set_xticklabels(lab, rotation=30, ha="right", fontsize=7)
            lo, hi = ce[f"{o} (ort. tahmin)"].min(), ce[f"{o} (ort. tahmin)"].max()
            pad_ = 0.5 * (hi - lo) + 1e-9 + 0.02 * abs(hi)
            ax.set_ylim(lo - pad_, hi + pad_)
            ax.set_title(o, fontsize=9)
            ax.grid(alpha=0.3, axis="y")
        fig.suptitle("Kategorik girdi seviyelerine göre ortalama tahmin", fontsize=10)
        fig.tight_layout()
        files.append(out_dir / "kategorik_etki.png")
        fig.savefig(files[-1], dpi=150)
        plt.close(fig)
    return files
