"""Veri okuma (Excel/CSV), kategorik kodlama ve eğitim/doğrulama/test bölme."""
from __future__ import annotations

import string
from pathlib import Path

import numpy as np
import pandas as pd


def _col_letter_to_idx(s: str):
    s = s.strip().upper()
    if not s or any(c not in string.ascii_uppercase for c in s):
        return None
    n = 0
    for c in s:
        n = n * 26 + (ord(c) - 64)
    return n - 1


def _resolve_columns(df: pd.DataFrame, spec, what: str) -> list[str]:
    """Sütunları isimle, Excel harfiyle ('B', 'C:F') veya 0-tabanlı indeksle seçer."""
    if spec is None:
        raise ValueError(f"'{what}' sütunları belirtilmeli")
    if isinstance(spec, (str, int)):
        spec = [spec]
    cols = list(df.columns)
    out = []
    for item in spec:
        if isinstance(item, int):
            out.append(cols[item])
            continue
        item = str(item)
        if item in cols:
            out.append(item)
            continue
        if ":" in item:
            a, b = item.split(":", 1)
            ia, ib = _col_letter_to_idx(a), _col_letter_to_idx(b)
            if ia is not None and ib is not None:
                out.extend(cols[ia: ib + 1])
                continue
        idx = _col_letter_to_idx(item)
        if idx is not None and idx < len(cols):
            out.append(cols[idx])
            continue
        raise ValueError(f"{what}: '{item}' sütunu bulunamadı. Mevcut sütunlar: {cols}")
    return out


def load_table(path, sheet=0) -> pd.DataFrame:
    path = Path(path)
    if path.suffix.lower() in (".xlsx", ".xlsm", ".xls"):
        return pd.read_excel(path, sheet_name=sheet)
    if path.suffix.lower() in (".csv", ".txt"):
        df = pd.read_csv(path, sep=None, engine="python")
        return df
    raise ValueError(f"Desteklenmeyen dosya türü: {path.suffix}")


class DataSpec:
    """Ham tabloyu sayısal girdi/çıktı matrislerine dönüştürür.

    Sayısal olmayan (veya 'kategorik' listesinde belirtilen) girdi sütunları
    one-hot kodlanır: örn. Kalip_Malzemesi -> Kalip_Malzemesi=PCD, ...
    """

    def __init__(self, input_cols, output_cols, categorical=None):
        self.input_cols = list(input_cols)
        self.output_cols = list(output_cols)
        self.categorical = list(categorical or [])
        self.categories: dict[str, list] = {}
        self.input_names: list[str] = []

    def fit(self, df: pd.DataFrame):
        self.input_names = []
        for c in self.input_cols:
            if c in self.categorical or not pd.api.types.is_numeric_dtype(df[c]):
                if c not in self.categorical:
                    self.categorical.append(c)
                cats = sorted(df[c].dropna().astype(str).unique().tolist())
                self.categories[c] = cats
                self.input_names.extend(f"{c}={v}" for v in cats)
            else:
                self.input_names.append(c)
        return self

    def transform_inputs(self, df: pd.DataFrame) -> np.ndarray:
        parts = []
        for c in self.input_cols:
            if c in self.categories:
                v = df[c].astype(str).to_numpy()
                parts.append(np.stack([(v == k).astype(float) for k in self.categories[c]], axis=1))
            else:
                parts.append(pd.to_numeric(df[c], errors="coerce").to_numpy(float)[:, None])
        return np.hstack(parts)

    def transform_outputs(self, df: pd.DataFrame) -> np.ndarray:
        return np.column_stack([pd.to_numeric(df[c], errors="coerce").to_numpy(float)
                                for c in self.output_cols])

    def to_dict(self):
        return {"input_cols": self.input_cols, "output_cols": self.output_cols,
                "categorical": self.categorical, "categories": self.categories,
                "input_names": self.input_names}

    @classmethod
    def from_dict(cls, d):
        s = cls(d["input_cols"], d["output_cols"], d.get("categorical"))
        s.categories = {k: list(v) for k, v in d.get("categories", {}).items()}
        s.input_names = list(d["input_names"])
        return s


def prepare(cfg_data: dict, base_dir="."):
    """Konfigürasyondaki 'veri' bölümüne göre veriyi okur."""
    path = Path(cfg_data["dosya"])
    if not path.is_absolute():
        path = Path(base_dir) / path
    df = load_table(path, cfg_data.get("sayfa", 0))
    df.columns = [str(c).strip() for c in df.columns]
    in_cols = _resolve_columns(df, cfg_data.get("girdiler"), "girdiler")
    out_cols = _resolve_columns(df, cfg_data.get("ciktilar"), "ciktilar")
    spec = DataSpec(in_cols, out_cols, cfg_data.get("kategorik")).fit(df)
    X = spec.transform_inputs(df)
    T = spec.transform_outputs(df)
    ok = np.isfinite(X).all(axis=1) & np.isfinite(T).all(axis=1)
    dropped = int((~ok).sum())
    split_col = cfg_data.get("bolme_sutunu")
    split_labels = df[split_col].astype(str).to_numpy()[ok] if split_col else None
    df_ok = df.loc[ok].reset_index(drop=True)
    return spec, X[ok], T[ok], df_ok, dropped, split_labels


def divide(n: int, cfg_div: dict, labels=None):
    """İndeksleri eğitim/doğrulama/test olarak böler.

    yontem = 'rastgele' (MATLAB dividerand), 'blok' (dividerblock: sıralı),
    'aralikli' (divideint) veya 'sutun' (veride 'egitim/dogrulama/test' etiketli sütun).
    Aynı tohum ile tüm mimariler AYNI bölmeyi kullanır -> adil karşılaştırma.
    """
    method = str(cfg_div.get("yontem", "rastgele")).lower()
    tr, va, te = (float(cfg_div.get("egitim", 0.70)), float(cfg_div.get("dogrulama", 0.15)),
                  float(cfg_div.get("test", 0.15)))
    s = tr + va + te
    tr, va, te = tr / s, va / s, te / s
    idx = np.arange(n)
    if method == "sutun":
        if labels is None:
            raise ValueError("bolme yontemi 'sutun' için veri.bolme_sutunu belirtilmeli")
        lab = np.char.lower(labels.astype(str))
        tr_keys = ("egitim", "eğitim", "train", "training", "e")
        va_keys = ("dogrulama", "doğrulama", "val", "validation", "d")
        te_keys = ("test", "t")
        return (idx[np.isin(lab, tr_keys)], idx[np.isin(lab, va_keys)], idx[np.isin(lab, te_keys)])
    n_va = int(round(va * n))
    n_te = int(round(te * n))
    n_tr = n - n_va - n_te
    if method == "rastgele":
        rng = np.random.default_rng(int(cfg_div.get("tohum", 42)))
        p = rng.permutation(n)
        return np.sort(p[:n_tr]), np.sort(p[n_tr:n_tr + n_va]), np.sort(p[n_tr + n_va:])
    if method == "blok":
        return idx[:n_tr], idx[n_tr:n_tr + n_va], idx[n_tr + n_va:]
    if method == "aralikli":
        pattern = np.array(["tr"] * max(int(round(tr * 20)), 1) + ["va"] * int(round(va * 20))
                           + ["te"] * int(round(te * 20)))
        lab = pattern[idx % len(pattern)]
        return idx[lab == "tr"], idx[lab == "va"], idx[lab == "te"]
    raise ValueError(f"Bilinmeyen bölme yöntemi: {method}")
