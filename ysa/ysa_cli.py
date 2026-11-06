"""Komut satırı komutları."""
from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np
import pandas as pd


def _cmd_run(a):
    from .runner import load_config, run
    cfg, base = load_config(a.config)
    if a.paralel is not None:
        cfg["paralel_is"] = a.paralel
    run(cfg, base)


def _cmd_predict(a):
    from .data import load_table
    from .model import Model
    m = Model.load(a.model)
    df = load_table(a.veri, a.sayfa)
    df.columns = [str(c).strip() for c in df.columns]
    Y = m.predict_df(df)
    out = df.copy()
    for k, o in enumerate(m.output_names):
        out[f"{o} (YSA tahmini)"] = Y[:, k]
    path = Path(a.cikti or Path(a.veri).with_name(Path(a.veri).stem + "_ysa_tahmin.xlsx"))
    out.to_excel(path, index=False)
    print(f"{len(out)} satır için tahmin yazıldı: {path}")


def _cmd_analyze(a):
    from . import analysis
    from .data import load_table
    from .model import Model
    m = Model.load(a.model)
    df = load_table(a.veri, a.sayfa)
    df.columns = [str(c).strip() for c in df.columns]
    X = m.spec.transform_inputs(df) if m.spec is not None else df[m.input_names].to_numpy(float)
    jt = analysis.jacobian_tables(m, X)
    path = Path(a.cikti or Path(a.veri).with_name(Path(a.veri).stem + "_jacobian_hessian.xlsx"))
    with pd.ExcelWriter(path) as xw:
        jt["jac_mean"].to_excel(xw, sheet_name="Jacobian_ortalama")
        jt["jac_norm"].to_excel(xw, sheet_name="Jacobian_normalize")
        jt["pad_percent"].to_excel(xw, sheet_name="PaD_katki_yuzde")
        jt["jac_long"].to_excel(xw, sheet_name="Jacobian_ornek_bazinda", index=False)
        for i, o in enumerate(m.output_names):
            jt["hess_mean"][o].to_excel(xw, sheet_name=f"Hessian_{i + 1}"[:31])
    print(f"Jacobian/Hessian analizi yazıldı: {path}")


def _cmd_info(a):
    from . import activations, trainers
    print("Eğitim algoritmaları:")
    for k, v in trainers.ALGORITHMS.items():
        print(f"  {k:10s} {v}")
    print("Aktivasyonlar:", ", ".join(activations.available()))
    print("Seçim kriterleri: val_mse, test_mse, train_mse, all_mse, val_r, all_r")


def main(argv=None):
    p = argparse.ArgumentParser(prog="python -m ysa", description="YSA mimari tarama ve analiz aracı")
    s = p.add_subparsers(dest="cmd", required=True)
    r = s.add_parser("calistir", help="Konfigürasyona göre tarama + rapor")
    r.add_argument("config")
    r.add_argument("--paralel", type=int, default=None, help="paralel işlem sayısı (-1: tüm çekirdekler)")
    r.set_defaults(f=_cmd_run)
    t = s.add_parser("tahmin", help="Kayıtlı model ile yeni veri tahmini")
    t.add_argument("model"); t.add_argument("veri")
    t.add_argument("--sayfa", default=0); t.add_argument("-o", "--cikti")
    t.set_defaults(f=_cmd_predict)
    n = s.add_parser("analiz", help="Kayıtlı model ile verilen noktalarda Jacobian/Hessian")
    n.add_argument("model"); n.add_argument("veri")
    n.add_argument("--sayfa", default=0); n.add_argument("-o", "--cikti")
    n.set_defaults(f=_cmd_analyze)
    b = s.add_parser("bilgi", help="Algoritma ve aktivasyon listesi")
    b.set_defaults(f=_cmd_info)
    a = p.parse_args(argv)
    a.f(a)
    return 0
