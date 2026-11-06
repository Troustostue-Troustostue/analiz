"""Girdi/çıktı ölçekleme (MATLAB mapminmax ve z-score uyumlu).

Her iki yöntem de doğrusal bir dönüşümdür:  xn = (x - offset) * gain + ymin
Bu sayede türevler (Jacobian/Hessian) normalize uzaydan orijinal birimlere
kesin olarak geri dönüştürülebilir:  dy/dx = (dyn/dxn) * gain_x / gain_y
"""
from __future__ import annotations

import numpy as np


class Scaler:
    def __init__(self, method: str = "mapminmax", ymin: float = -1.0, ymax: float = 1.0):
        method = str(method).lower()
        if method in ("none", "yok", "hicbiri"):
            method = "none"
        if method not in ("mapminmax", "zscore", "none"):
            raise ValueError("normalizasyon 'mapminmax', 'zscore' veya 'none' olmalı")
        self.method = method
        self.ymin = float(ymin)
        self.ymax = float(ymax)
        self.offset = None  # orijinal uzaydaki referans (xmin veya ortalama)
        self.gain = None
        self.target = 0.0  # normalize uzaydaki referans (ymin veya 0)
        self.xmin = None
        self.xmax = None

    def fit(self, X: np.ndarray) -> "Scaler":
        X = np.asarray(X, dtype=float)
        self.xmin = X.min(axis=0)
        self.xmax = X.max(axis=0)
        if self.method == "mapminmax":
            rng = self.xmax - self.xmin
            const = rng <= 1e-12
            gain = np.where(const, 1.0, (self.ymax - self.ymin) / np.where(const, 1.0, rng))
            self.offset = self.xmin.copy()
            self.gain = gain
            self.target = self.ymin
        elif self.method == "zscore":
            mu = X.mean(axis=0)
            sd = X.std(axis=0, ddof=1) if X.shape[0] > 1 else np.ones(X.shape[1])
            sd = np.where(sd <= 1e-12, 1.0, sd)
            self.offset = mu
            self.gain = 1.0 / sd
            self.target = 0.0
        else:
            self.offset = np.zeros(X.shape[1])
            self.gain = np.ones(X.shape[1])
            self.target = 0.0
        return self

    def transform(self, X):
        return (np.asarray(X, dtype=float) - self.offset) * self.gain + self.target

    def inverse(self, Xn):
        return (np.asarray(Xn, dtype=float) - self.target) / self.gain + self.offset

    def to_dict(self):
        return {
            "method": self.method,
            "ymin": self.ymin,
            "ymax": self.ymax,
            "offset": self.offset.tolist(),
            "gain": self.gain.tolist(),
            "target": self.target,
            "xmin": self.xmin.tolist(),
            "xmax": self.xmax.tolist(),
        }

    @classmethod
    def from_dict(cls, d):
        s = cls(d["method"], d.get("ymin", -1.0), d.get("ymax", 1.0))
        s.offset = np.asarray(d["offset"], float)
        s.gain = np.asarray(d["gain"], float)
        s.target = float(d["target"])
        s.xmin = np.asarray(d["xmin"], float)
        s.xmax = np.asarray(d["xmax"], float)
        return s
