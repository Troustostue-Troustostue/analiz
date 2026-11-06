"""Hata / başarı ölçütleri (orijinal birimlerde, çıktı bazında)."""
from __future__ import annotations

import numpy as np


def per_output(T: np.ndarray, Y: np.ndarray) -> dict:
    """Her çıktı sütunu için ölçütler. T, Y: (N, m) orijinal birimlerde."""
    T = np.atleast_2d(np.asarray(T, float))
    Y = np.atleast_2d(np.asarray(Y, float))
    E = Y - T
    N = T.shape[0]
    mse = np.mean(E ** 2, axis=0)
    rmse = np.sqrt(mse)
    mae = np.mean(np.abs(E), axis=0)
    with np.errstate(divide="ignore", invalid="ignore"):
        ape = np.abs(E) / np.abs(T)
        ape[~np.isfinite(ape)] = np.nan
        mape = 100.0 * np.nanmean(ape, axis=0) if N else np.full(T.shape[1], np.nan)
        ss_res = np.sum(E ** 2, axis=0)
        ss_tot = np.sum((T - T.mean(axis=0)) ** 2, axis=0)
        r2 = 1.0 - ss_res / ss_tot
        tc, yc = T - T.mean(axis=0), Y - Y.mean(axis=0)
        R = np.sum(tc * yc, axis=0) / np.sqrt(np.sum(tc ** 2, axis=0) * np.sum(yc ** 2, axis=0))
    return {"MSE": mse, "RMSE": rmse, "MAE": mae, "MAPE_%": mape, "R2": r2, "R": R,
            "MaxHata": np.max(np.abs(E), axis=0) if N else np.full(T.shape[1], np.nan)}


def mse_norm(Tn, Yn) -> float:
    """Normalize uzayda toplam MSE (farklı birimdeki çıktıları adil karşılaştırır)."""
    if Tn is None or len(Tn) == 0:
        return float("nan")
    return float(np.mean((np.asarray(Yn) - np.asarray(Tn)) ** 2))
