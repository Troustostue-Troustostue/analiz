"""YSA - Yapay Sinir Ağı mimari tarama, eğitim ve girdi-çıktı (Jacobian/Hessian) analiz paketi.

Pamukkale Üniversitesi Makine Mühendisliği - tez/araştırma çalışmaları için.
"""
import os as _os

# Paralel taramada her işlem tek BLAS iş parçacığı kullansın (aşırı abonelik önlenir).
# numpy içe aktarılmadan önce ayarlanmalıdır.
for _v in ("OMP_NUM_THREADS", "OPENBLAS_NUM_THREADS", "MKL_NUM_THREADS"):
    _os.environ.setdefault(_v, "1")

from .network import MLP  # noqa: E402
from .model import Model  # noqa: E402
from .normalization import Scaler  # noqa: E402
from .trainers import ALGORITHMS, Dataset, train  # noqa: E402

__all__ = ["MLP", "Model", "Scaler", "Dataset", "train", "ALGORITHMS"]
__version__ = "1.0.0"
