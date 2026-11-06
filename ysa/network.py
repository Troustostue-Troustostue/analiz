"""Çok katmanlı ileri beslemeli yapay sinir ağı (MLP) çekirdeği.

Tüm parametreler (ağırlık + bias) tek bir vektörde (theta) tutulur. Sıralama:
    [W1 (satır-öncelikli), b1, W2, b2, ..., WL, bL]
W_l boyutu: (n_l, n_{l-1})  -> MATLAB'daki IW/LW matrisleriyle aynı yön
            satır = hedef nöron, sütun = kaynak nöron/girdi

Sağlanan analitik hesaplar:
  * forward                : çıktı
  * gradient               : performans (SSE) gradyanı (geri yayılım)
  * jacobian_params        : dy/dθ  (Levenberg-Marquardt ve Bayes düzenlileştirme için)
  * input_derivatives      : dy/dx (Jacobian) ve d²y/dx² (Hessian), ileri-mod
                             otomatik türev ile TAM olarak (yaklaşıksız)
"""
from __future__ import annotations

import numpy as np

from . import activations as act


class MLP:
    def __init__(self, n_inputs: int, hidden: list[int], n_outputs: int,
                 hidden_act="tansig", output_act="purelin"):
        hidden = [int(h) for h in hidden]
        if any(h < 1 for h in hidden):
            raise ValueError("Her gizli katmanda en az 1 nöron olmalı")
        self.sizes = [int(n_inputs)] + hidden + [int(n_outputs)]
        self.n_layers = len(self.sizes) - 1
        if isinstance(hidden_act, (list, tuple)):
            if len(hidden_act) != len(hidden):
                raise ValueError("Katman başına aktivasyon listesi gizli katman sayısıyla aynı olmalı")
            h_acts = [act.canonical(a) for a in hidden_act]
        else:
            h_acts = [act.canonical(hidden_act)] * len(hidden)
        self.acts = h_acts + [act.canonical(output_act)]

        self.shapes = []
        self._slices = []
        pos = 0
        for l in range(self.n_layers):
            n_out, n_in = self.sizes[l + 1], self.sizes[l]
            w = slice(pos, pos + n_out * n_in)
            pos += n_out * n_in
            b = slice(pos, pos + n_out)
            pos += n_out
            self.shapes.append((n_out, n_in))
            self._slices.append((w, b))
        self.n_params = pos

    # ------------------------------------------------------------------ #
    @property
    def hidden(self):
        return self.sizes[1:-1]

    def describe(self) -> str:
        return "-".join(str(s) for s in self.sizes)

    def unpack(self, theta):
        Ws, bs = [], []
        for (n_out, n_in), (ws, bsl) in zip(self.shapes, self._slices):
            Ws.append(theta[ws].reshape(n_out, n_in))
            bs.append(theta[bsl])
        return Ws, bs

    def pack(self, Ws, bs):
        theta = np.empty(self.n_params)
        for W, b, (ws, bsl) in zip(Ws, bs, self._slices):
            theta[ws] = np.asarray(W).ravel()
            theta[bsl] = np.asarray(b).ravel()
        return theta

    def param_labels(self, input_names=None):
        """Her parametre için okunabilir etiket (ör. 'W1[3,2]', 'b2[1]')."""
        labels = []
        for l, (n_out, n_in) in enumerate(self.shapes, start=1):
            for i in range(n_out):
                for j in range(n_in):
                    labels.append(f"W{l}[{i + 1},{j + 1}]")
            for i in range(n_out):
                labels.append(f"b{l}[{i + 1}]")
        return labels

    # ------------------------------------------------------------------ #
    def init_params(self, rng: np.random.Generator, method="nguyen-widrow"):
        """Ağırlık başlatma.

        tansig/logsig gizli katmanlar için MATLAB'daki gibi Nguyen-Widrow;
        diğerleri için Glorot (Xavier) uniform kullanılır.
        """
        Ws, bs = [], []
        for l, (n_out, n_in) in enumerate(self.shapes):
            a = self.acts[l]
            is_hidden = l < self.n_layers - 1
            if method == "nguyen-widrow" and is_hidden and a in ("tansig", "logsig"):
                beta = 0.7 * n_out ** (1.0 / max(n_in, 1))
                W = rng.uniform(-1.0, 1.0, size=(n_out, n_in))
                norm = np.linalg.norm(W, axis=1, keepdims=True)
                norm[norm == 0] = 1.0
                W = beta * W / norm
                if n_out > 1:
                    sgn = np.sign(W[:, 0])
                    sgn[sgn == 0] = 1.0
                    b = beta * np.linspace(-1.0, 1.0, n_out) * sgn
                else:
                    b = rng.uniform(-beta, beta, size=n_out)
                if a == "logsig":  # logsig için aktif bölge kaydırma
                    W = 4.0 * W
                    b = 4.0 * b
            else:
                lim = np.sqrt(6.0 / (n_in + n_out))
                W = rng.uniform(-lim, lim, size=(n_out, n_in))
                b = rng.uniform(-0.1, 0.1, size=n_out) if is_hidden else np.zeros(n_out)
            Ws.append(W)
            bs.append(b)
        return self.pack(Ws, bs)

    # ------------------------------------------------------------------ #
    def forward(self, theta, X, keep=False):
        """X: (N, d) normalize girdi. Döndürür Y (N, m) [ve ara değerler]."""
        Ws, bs = self.unpack(theta)
        a = X
        A = [X]
        D1 = []
        for l in range(self.n_layers):
            z = a @ Ws[l].T + bs[l]
            if keep:
                a, d1, _ = act.evaluate(self.acts[l], z)
                D1.append(d1)
            else:
                a = act.value(self.acts[l], z)
            A.append(a)
        if keep:
            return a, (Ws, A, D1)
        return a

    def predict(self, theta, X):
        return self.forward(theta, X)

    # ------------------------------------------------------------------ #
    def sse_and_gradient(self, theta, X, T):
        """SSE = Σ (y - t)²  ve  ∂SSE/∂θ  (geri yayılım)."""
        Y, (Ws, A, D1) = self.forward(theta, X, keep=True)
        R = Y - T
        sse = float(np.sum(R * R))
        grad = np.empty(self.n_params)
        delta = 2.0 * R * D1[-1]
        for l in range(self.n_layers - 1, -1, -1):
            ws, bsl = self._slices[l]
            grad[ws] = (delta.T @ A[l]).ravel()
            grad[bsl] = delta.sum(axis=0)
            if l > 0:
                delta = (delta @ Ws[l]) * D1[l - 1]
        return sse, grad

    def jacobian_params(self, theta, X):
        """Ağ çıktısının parametrelere göre Jacobian'ı.

        Döndürür J (N*m, P) ; satır sırası: örnek n, çıktı k -> n*m + k
        ve Y (N, m).
        """
        Y, (Ws, A, D1) = self.forward(theta, X, keep=True)
        N, m = Y.shape
        J = np.empty((N, m, self.n_params))
        for k in range(m):
            delta = np.zeros((N, m))
            delta[:, k] = D1[-1][:, k]
            for l in range(self.n_layers - 1, -1, -1):
                ws, bsl = self._slices[l]
                n_out, n_in = self.shapes[l]
                J[:, k, ws] = (delta[:, :, None] * A[l][:, None, :]).reshape(N, n_out * n_in)
                J[:, k, bsl] = delta
                if l > 0:
                    delta = (delta @ Ws[l]) * D1[l - 1]
        return J.reshape(N * m, self.n_params), Y

    # ------------------------------------------------------------------ #
    def input_derivatives(self, theta, X, hessian=True):
        """Normalize uzayda dy/dx ve d²y/dx² (tam, analitik ileri-mod).

        Döndürür:
            Y  : (N, m)
            Jx : (N, m, d)        Jx[n,k,i]   = ∂y_k/∂x_i
            Hx : (N, m, d, d)     Hx[n,k,i,j] = ∂²y_k/∂x_i∂x_j   (hessian=True ise)
        """
        Ws, bs = self.unpack(theta)
        X = np.asarray(X, float)
        N, d = X.shape
        a = X
        aJ = np.broadcast_to(np.eye(d), (N, d, d)).copy()
        aH = np.zeros((N, d, d, d)) if hessian else None
        for l in range(self.n_layers):
            W = Ws[l]
            z = a @ W.T + bs[l]
            zJ = np.einsum("ij,njd->nid", W, aJ, optimize=True)
            f, f1, f2 = act.evaluate(self.acts[l], z)
            if hessian:
                zH = np.einsum("ij,njde->nide", W, aH, optimize=True)
                aH = (f2[:, :, None, None] * zJ[:, :, :, None] * zJ[:, :, None, :]
                      + f1[:, :, None, None] * zH)
            aJ = f1[:, :, None] * zJ
            a = f
        return (a, aJ, aH) if hessian else (a, aJ)
