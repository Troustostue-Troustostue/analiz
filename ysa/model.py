"""Eğitilmiş model: ağ + ağırlıklar + ölçekleyiciler. Orijinal birimlerde tahmin,
Jacobian ve Hessian; JSON olarak kaydet/yükle."""
from __future__ import annotations

import json
from pathlib import Path

import numpy as np

from .network import MLP
from .normalization import Scaler


class Model:
    def __init__(self, net: MLP, theta, x_scaler: Scaler, y_scaler: Scaler,
                 input_names, output_names, spec=None, meta=None):
        self.net = net
        self.theta = np.asarray(theta, float)
        self.xs = x_scaler
        self.ys = y_scaler
        self.input_names = list(input_names)
        self.output_names = list(output_names)
        self.spec = spec  # DataSpec (kategorik kodlama bilgisi) - opsiyonel
        self.meta = dict(meta or {})

    # ---------------------------------------------------------------- #
    @property
    def weights(self):
        """[(W1, b1), (W2, b2), ...] listesi."""
        Ws, bs = self.net.unpack(self.theta)
        return list(zip(Ws, bs))

    def predict(self, X):
        Xn = self.xs.transform(np.atleast_2d(X))
        return self.ys.inverse(self.net.forward(self.theta, Xn))

    def predict_df(self, df):
        """Ham tablo (pandas) üzerinden tahmin; kategorik sütunları otomatik kodlar."""
        if self.spec is None:
            return self.predict(df[self.input_names].to_numpy(float))
        return self.predict(self.spec.transform_inputs(df))

    def derivatives(self, X, hessian=True):
        """ORİJİNAL birimlerde dy/dx ve d²y/dx².

        Döndürür Y (N,m), Jx (N,m,d), [Hx (N,m,d,d)]
        Jx[n,k,i]   = ∂y_k/∂x_i       (ör. MPa / (m/s))
        Hx[n,k,i,j] = ∂²y_k/∂x_i∂x_j
        """
        X = np.atleast_2d(np.asarray(X, float))
        Xn = self.xs.transform(X)
        gx = self.xs.gain
        gy = self.ys.gain
        out = self.net.input_derivatives(self.theta, Xn, hessian=hessian)
        Y = self.ys.inverse(out[0])
        Jx = out[1] * gx[None, None, :] / gy[None, :, None]
        if not hessian:
            return Y, Jx
        Hx = out[2] * (gx[:, None] * gx[None, :])[None, None] / gy[None, :, None, None]
        return Y, Jx, Hx

    # ---------------------------------------------------------------- #
    def to_dict(self):
        Ws, bs = self.net.unpack(self.theta)
        return {
            "format": "ysa-model-v1",
            "sizes": self.net.sizes,
            "activations": self.net.acts,
            "theta": self.theta.tolist(),
            "weights": [W.tolist() for W in Ws],
            "biases": [b.tolist() for b in bs],
            "x_scaler": self.xs.to_dict(),
            "y_scaler": self.ys.to_dict(),
            "input_names": self.input_names,
            "output_names": self.output_names,
            "spec": self.spec.to_dict() if self.spec is not None else None,
            "meta": self.meta,
        }

    def save(self, path):
        Path(path).write_text(json.dumps(self.to_dict(), indent=1, ensure_ascii=False,
                                         default=_json_default), encoding="utf-8")

    @classmethod
    def load(cls, path):
        from .data import DataSpec
        d = json.loads(Path(path).read_text(encoding="utf-8"))
        sizes, acts = d["sizes"], d["activations"]
        net = MLP(sizes[0], sizes[1:-1], sizes[-1], hidden_act=acts[:-1], output_act=acts[-1])
        spec = DataSpec.from_dict(d["spec"]) if d.get("spec") else None
        return cls(net, d["theta"], Scaler.from_dict(d["x_scaler"]), Scaler.from_dict(d["y_scaler"]),
                   d["input_names"], d["output_names"], spec, d.get("meta"))


def _json_default(o):
    if isinstance(o, np.ndarray):
        return o.tolist()
    if isinstance(o, (np.floating, np.integer)):
        return o.item()
    return str(o)
