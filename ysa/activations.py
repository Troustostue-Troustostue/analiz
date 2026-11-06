"""Aktivasyon (transfer) fonksiyonları.

Her fonksiyon için değer f(z), birinci türev f'(z) ve ikinci türev f''(z)
analitik olarak verilir. İkinci türevler girdi-Hessian matrisinin tam
(yaklaşıksız) hesaplanması için gereklidir.

İsimler MATLAB Neural Network Toolbox ile uyumludur (tansig, logsig, purelin,
poslin, radbas ...) ve yaygın takma adlar da kabul edilir.
"""
from __future__ import annotations

import numpy as np


def _tansig(z):
    a = np.tanh(z)
    d1 = 1.0 - a * a
    d2 = -2.0 * a * d1
    return a, d1, d2


def _logsig(z):
    a = 0.5 * (1.0 + np.tanh(0.5 * z))  # sayısal olarak kararlı sigmoid
    d1 = a * (1.0 - a)
    d2 = d1 * (1.0 - 2.0 * a)
    return a, d1, d2


def _purelin(z):
    return z.copy(), np.ones_like(z), np.zeros_like(z)


def _poslin(z):  # ReLU
    a = np.maximum(z, 0.0)
    d1 = (z > 0).astype(z.dtype)
    return a, d1, np.zeros_like(z)


def _softplus(z):
    a = np.logaddexp(0.0, z)
    s = 0.5 * (1.0 + np.tanh(0.5 * z))
    return a, s, s * (1.0 - s)


def _elu(z):
    e = np.exp(np.minimum(z, 0.0))
    pos = z > 0
    a = np.where(pos, z, e - 1.0)
    d1 = np.where(pos, 1.0, e)
    d2 = np.where(pos, 0.0, e)
    return a, d1, d2


def _radbas(z):
    e = np.exp(-z * z)
    return e, -2.0 * z * e, (4.0 * z * z - 2.0) * e


def _swish(z):
    s = 0.5 * (1.0 + np.tanh(0.5 * z))
    a = z * s
    ds = s * (1.0 - s)
    d1 = s + z * ds
    d2 = 2.0 * ds + z * ds * (1.0 - 2.0 * s)
    return a, d1, d2


_FUNCS = {
    "tansig": _tansig,
    "logsig": _logsig,
    "purelin": _purelin,
    "poslin": _poslin,
    "softplus": _softplus,
    "elu": _elu,
    "radbas": _radbas,
    "swish": _swish,
}

_ALIASES = {
    "tanh": "tansig",
    "sigmoid": "logsig",
    "logistic": "logsig",
    "linear": "purelin",
    "lineer": "purelin",
    "identity": "purelin",
    "relu": "poslin",
    "gaussian": "radbas",
    "silu": "swish",
}

# Okunabilir formül metni (formül çıktısı için)
FORMULA_TEXT = {
    "tansig": "tansig(n) = 2/(1+exp(-2n)) - 1 = tanh(n)",
    "logsig": "logsig(n) = 1/(1+exp(-n))",
    "purelin": "purelin(n) = n",
    "poslin": "poslin(n) = max(0, n)   (ReLU)",
    "softplus": "softplus(n) = ln(1+exp(n))",
    "elu": "elu(n) = n (n>0),  exp(n)-1 (n<=0)",
    "radbas": "radbas(n) = exp(-n^2)",
    "swish": "swish(n) = n/(1+exp(-n))",
}

# Excel formül şablonları ({x} hücre ifadesi ile değiştirilir)
EXCEL_TEMPLATE = {
    "tansig": "TANH({x})",
    "logsig": "1/(1+EXP(-({x})))",
    "purelin": "({x})",
    "poslin": "MAX(0,{x})",
    "softplus": "LN(1+EXP({x}))",
    "elu": "IF(({x})>0,({x}),EXP({x})-1)",
    "radbas": "EXP(-(({x})^2))",
    "swish": "({x})/(1+EXP(-({x})))",
}

# Numpy-only bağımsız tahmin dosyasına gömülen kodlar
NUMPY_CODE = {
    "tansig": "np.tanh(z)",
    "logsig": "1.0/(1.0+np.exp(-z))",
    "purelin": "z",
    "poslin": "np.maximum(z, 0.0)",
    "softplus": "np.logaddexp(0.0, z)",
    "elu": "np.where(z > 0, z, np.exp(np.minimum(z, 0.0)) - 1.0)",
    "radbas": "np.exp(-z*z)",
    "swish": "z/(1.0+np.exp(-z))",
}


def canonical(name: str) -> str:
    """Takma adı standart isme çevirir; bilinmeyen isimde hata verir."""
    key = str(name).strip().lower()
    key = _ALIASES.get(key, key)
    if key not in _FUNCS:
        raise ValueError(
            f"Bilinmeyen aktivasyon fonksiyonu: '{name}'. Geçerli olanlar: "
            + ", ".join(sorted(set(_FUNCS) | set(_ALIASES)))
        )
    return key


def evaluate(name: str, z: np.ndarray):
    """(f, f', f'') üçlüsünü döndürür."""
    return _FUNCS[canonical(name)](z)


def value(name: str, z: np.ndarray) -> np.ndarray:
    return _FUNCS[canonical(name)](z)[0]


def available() -> list[str]:
    return sorted(_FUNCS)
