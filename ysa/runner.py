"""Konfigürasyon dosyasından tam akış: veri -> tarama -> en iyi model -> rapor."""
from __future__ import annotations

import copy
import json
import time
from datetime import datetime
from pathlib import Path

import numpy as np
import pandas as pd

from . import analysis, report
from .data import divide, prepare
from .model import Model
from .network import MLP
from .normalization import Scaler
from .search import build_architectures, grid_search

DEFAULT_CONFIG = {
    "veri": {"dosya": None, "sayfa": 0, "girdiler": None, "ciktilar": None,
             "kategorik": [], "bolme_sutunu": None},
    "bolme": {"yontem": "rastgele", "egitim": 0.70, "dogrulama": 0.15, "test": 0.15, "tohum": 42},
    "normalizasyon": {"yontem": "mapminmax", "aralik": [-1, 1], "kaynak": "egitim"},
    "katmanlar": [[1, 20]],
    "katman_sayilari": None,
    "egitim_algoritmalari": ["trainlm"],
    "gizli_aktivasyonlar": ["tansig"],
    "cikis_aktivasyonu": "purelin",
    "tekrar": 3,
    "tohum": 2024,
    "egitim_parametreleri": {"epochs": 1000, "max_fail": 6, "goal": 0.0, "min_grad": 1e-7},
    "algoritma_parametreleri": {},
    "secim_kriteri": "val_mse",
    "paralel_is": -1,
    "en_iyi_k": 10,
    "cikti_klasoru": "ysa_sonuclar",
    "grafikler": True,
    "agirlik_hessiani": True,
}


def _merge(base, over):
    out = copy.deepcopy(base)
    for k, v in (over or {}).items():
        if isinstance(v, dict) and isinstance(out.get(k), dict):
            out[k] = _merge(out[k], v)
        else:
            out[k] = v
    return out


def load_config(path):
    path = Path(path)
    cfg = json.loads(path.read_text(encoding="utf-8"))
    cfg = {k: v for k, v in cfg.items() if not str(k).startswith("_")}  # "_aciklama" alanları
    return _merge(DEFAULT_CONFIG, cfg), path.parent


def run(cfg: dict, base_dir=".", log=print):
    t_start = time.perf_counter()
    if isinstance(cfg.get("normalizasyon"), str):
        cfg["normalizasyon"] = {**DEFAULT_CONFIG["normalizasyon"], "yontem": cfg["normalizasyon"]}
    out_dir = Path(cfg["cikti_klasoru"])
    if not out_dir.is_absolute():
        out_dir = Path(base_dir) / out_dir
    out_dir = out_dir / datetime.now().strftime("calisma_%Y%m%d_%H%M%S")
    out_dir.mkdir(parents=True, exist_ok=True)
    log(f"Çıktı klasörü: {out_dir}")

    # ---------------- Veri
    spec, X, T, df, dropped, split_labels = prepare(cfg["veri"], base_dir)
    if dropped:
        log(f"UYARI: eksik/sayısal olmayan değer içeren {dropped} satır çıkarıldı.")
    in_names, out_names = spec.input_names, spec.output_cols
    log(f"Veri: {X.shape[0]} örnek, {X.shape[1]} girdi {in_names}, {T.shape[1]} çıktı {out_names}")
    tr, va, te = divide(len(X), cfg["bolme"], split_labels)
    log(f"Bölme: eğitim={len(tr)}, doğrulama={len(va)}, test={len(te)}")
    if len(tr) < 2:
        raise ValueError("Eğitim kümesinde en az 2 örnek olmalı")

    nc = cfg["normalizasyon"]
    lo, hi = nc.get("aralik", [-1, 1])
    fit_idx = tr if nc.get("kaynak", "egitim") == "egitim" else np.arange(len(X))
    xs = Scaler(nc["yontem"], lo, hi).fit(X[fit_idx])
    ys = Scaler(nc["yontem"], lo, hi).fit(T[fit_idx])
    Xn, Tn = xs.transform(X), ys.transform(T)

    def sub(A, idx):
        return A[idx] if len(idx) else None

    shared = {
        "d": X.shape[1], "m": T.shape[1], "out_act": cfg["cikis_aktivasyonu"], "seed": cfg["tohum"],
        "Xtr": Xn[tr], "Ttr": Tn[tr], "Xva": sub(Xn, va), "Tva": sub(Tn, va),
        "Xte": sub(Xn, te), "Tte": sub(Tn, te), "Xall": Xn, "Tall": Tn,
        "Ttr_o": T[tr], "Tva_o": sub(T, va), "Tte_o": sub(T, te), "Tall_o": T,
        "y_scaler": ys, "out_names": out_names,
        "params": cfg["egitim_parametreleri"], "alg_params": cfg.get("algoritma_parametreleri") or {},
    }

    # ---------------- Tarama
    archs = build_architectures(cfg["katmanlar"], cfg.get("katman_sayilari"))
    rows, top, crit = grid_search(
        shared, archs, cfg["egitim_algoritmalari"], cfg["gizli_aktivasyonlar"], int(cfg["tekrar"]),
        n_jobs=cfg.get("paralel_is", -1), criterion=cfg["secim_kriteri"],
        out_csv=out_dir / "tum_calistirmalar_canli.csv", top_k=int(cfg.get("en_iyi_k", 10)), log=log)
    results = pd.DataFrame(rows)
    col, mode = crit
    grp = results.groupby(["mimari", "algoritma", "aktivasyon"], sort=False)
    summary = grp.agg(
        tekrar_sayisi=(col, "size"),
        **{f"{col}_ortalama": (col, "mean"), f"{col}_std": (col, "std"),
           f"{col}_en_iyi": (col, "min" if mode == "min" else "max"),
           "mse_test_ortalama": ("mse_test", "mean"), "mse_test_std": ("mse_test", "std"),
           "R_tum_ortalama": ("R_tum", "mean"), "parametre_sayisi": ("parametre_sayisi", "first"),
           "ortalama_sure_s": ("sure_s", "mean")}).reset_index()
    summary = summary.sort_values(f"{col}_ortalama", ascending=(mode == "min")).reset_index(drop=True)
    summary.insert(0, "sira", np.arange(1, len(summary) + 1))
    results_sorted = results.sort_values(col, ascending=(mode == "min")).reset_index(drop=True)
    results_sorted.insert(0, "sira", np.arange(1, len(results_sorted) + 1))

    # ---------------- En iyi modeller
    models_dir = out_dir / "modeller"
    models_dir.mkdir(exist_ok=True)
    best_models = []
    for rank, (_, row, theta, hist, info) in enumerate(top, start=1):
        hidden = [row[f"n{i + 1}"] for i in range(row["gizli_katman_sayisi"])]
        net = MLP(X.shape[1], hidden, T.shape[1], hidden_act=row["aktivasyon"],
                  output_act=cfg["cikis_aktivasyonu"])
        meta = {k: (v if not isinstance(v, (np.floating, np.integer)) else v.item()) for k, v in row.items()}
        meta.update({"siralama": rank, "secim_kriteri": col, "egitim_bilgisi": info})
        mdl = Model(net, theta, xs, ys, in_names, out_names, spec, meta)
        mdl.save(models_dir / f"model_{rank:02d}_{row['mimari']}_{row['algoritma']}.json")
        best_models.append((mdl, row, hist, info))
    best, best_row, best_hist, best_info = best_models[0]
    best.save(out_dir / "en_iyi_model.json")
    (out_dir / "en_iyi_model_tahmin.py").write_text(analysis.standalone_python(best), encoding="utf-8")
    (out_dir / "en_iyi_model_formul.txt").write_text(analysis.formula_text(best), encoding="utf-8")

    subsets = {"egitim": tr, "dogrulama": va, "test": te}
    extra = {}
    if "gamma_etkin_parametre" in best_info:
        extra["BR etkin parametre sayısı (gamma)"] = round(best_info["gamma_etkin_parametre"], 3)
    log("Excel raporu hazırlanıyor...")
    jt, curves, _, _ = report.write_report(
        out_dir / "YSA_Rapor.xlsx", best, results_sorted, summary, best_row, best_hist,
        X, T, subsets, cfg, crit, extra, weight_hessian=cfg.get("agirlik_hessiani", True), log=log)
    if cfg.get("grafikler", True):
        report.make_plots(out_dir / "grafikler", best, X, T, subsets, best_hist, results, crit, jt, curves, log=log)

    # ---------------- Konsol özeti
    log("")
    log("=" * 70)
    log(f"EN İYİ MODEL ({col} {'min' if mode == 'min' else 'max'}): {best_row['mimari']}  "
        f"{best_row['algoritma']}  {best_row['aktivasyon']}  tekrar={best_row['tekrar']}")
    for k in ("mse_egitim", "mse_dogrulama", "mse_test", "R_egitim", "R_dogrulama", "R_test", "R_tum"):
        v = best_row.get(k)
        log(f"   {k:15s}: {v:.6g}" if v is not None and np.isfinite(v) else f"   {k:15s}: -")
    log("   Test kümesi R² (çıktı bazında): " + ", ".join(
        f"{o}={best_row.get(f'R2_test[{o}]', np.nan):.4f}" for o in out_names))
    log("   Sürekli girdilerin göreli katkısı (PaD, %):")
    log(jt["pad_percent"].loc[:, jt["continuous"]].round(1).to_string())
    ce = jt.get("categorical_effects")
    if ce is not None:
        log("   Kategorik girdi etkisi (seviye ortalamasına göre fark):")
        log(ce[["degisken", "seviye"] + [c for c in ce.columns if c.endswith(" fark")]].round(3).to_string(index=False))
    log("=" * 70)
    log(f"Toplam süre: {time.perf_counter() - t_start:.1f} s.  Sonuçlar: {out_dir}")
    with open(out_dir / "kullanilan_konfigurasyon.json", "w", encoding="utf-8") as f:
        json.dump(cfg, f, indent=2, ensure_ascii=False)
    return {"out_dir": out_dir, "best": best, "best_row": best_row, "results": results_sorted,
            "summary": summary, "models": best_models}
