"""SENTETİK (yapay) CuMg0.4 tel çekme verisi üretir - SADECE algoritmayı denemek içindir.

Buradaki ilişkiler gerçek deney verisi DEĞİLDİR; literatürdeki genel eğilimleri
(birim şekil değiştirme ile pekleşme, Mg ile mukavemet artışı / iletkenlik düşüşü,
hız ve kalıp malzemesinin sürtünme-enerji etkisi) kabaca taklit eder.
Kendi deney verinizi aynı sütun düzeninde Excel'e girip kullanın.
"""
from pathlib import Path

import numpy as np
import pandas as pd

rng = np.random.default_rng(7)
N = 240
mg = rng.uniform(0.30, 0.50, N)                       # ağ.% Mg
pas = rng.integers(6, 15, N)                          # hadde (paso) sayısı
red = rng.uniform(10, 25, N)                          # paso başına ortalama redüksiyon (%)
hiz = rng.uniform(5, 25, N)                           # çekme hızı (m/s)
kalip = rng.choice(["Sert_Metal_WC", "PCD", "Dogal_Elmas"], N)
mu = np.select([kalip == "Sert_Metal_WC", kalip == "PCD"], [0.08, 0.05], 0.04)

eps = -pas * np.log(1 - red / 100)                    # toplam gerçek birim şekil değiştirme
uts = 250 + 330 * (1 - np.exp(-eps / 1.6)) + 260 * (mg - 0.4) - 0.9 * (hiz - 15) + rng.normal(0, 6, N)
hv = 0.30 * uts + 8 + rng.normal(0, 2.5, N)
iacs = 64 - 32 * (mg - 0.4) - 1.6 * eps + 0.05 * (hiz - 15) + rng.normal(0, 0.5, N)
enerji = (40 * eps * (1 + 1.8 * mu / (red / 100) ** 0.5) * (1 + 0.012 * hiz)
          + 0.35 * pas + rng.normal(0, 1.5, N))

df = pd.DataFrame({
    "Mg_orani_agirlik%": mg.round(3), "Hadde_sayisi": pas, "Redüksiyon_%": red.round(2),
    "Cekme_hizi_m/s": hiz.round(2), "Kalip_malzemesi": kalip,
    "Cekme_dayanimi_MPa": uts.round(1), "Sertlik_HV": hv.round(1),
    "Iletkenlik_%IACS": iacs.round(2), "Enerji_kWh/ton": enerji.round(2),
})
out = Path(__file__).with_name("cumg04_tel_cekme_SENTETIK.xlsx")
df.to_excel(out, index=False)
print(f"{len(df)} satır yazıldı: {out}")
