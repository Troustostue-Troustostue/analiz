# YSA – Yapay Sinir Ağı Mimari Tarama ve Girdi-Çıktı Analiz Aracı

Python (numpy) ile sıfırdan yazılmış, MATLAB Neural Network Toolbox mantığıyla uyumlu bir çok katmanlı
yapay sinir ağı (MLP) aracı. Yaptıkları:

1. Verdiğiniz **nöron aralıklarındaki** (ör. 1. katman 1–100, 2. katman 2–10) **tüm mimarileri**,
   seçtiğiniz **eğitim algoritmalarıyla**, **aktivasyon fonksiyonlarıyla** ve **tekrar sayısıyla** eğitir.
2. Her eğitim için eğitim/doğrulama/test hatalarını (MSE, RMSE, MAE, MAPE, R², R) hesaplayıp tabloya yazar.
3. En iyi ağın **tüm ağırlık ve biaslarını**, açık **matematiksel formülünü** ve **canlı Excel formülleriyle
   çalışan hesap sayfasını** çıkarır.
4. Girdi-çıktı ilişkisini **analitik Jacobian (∂y/∂x)** ve **analitik Hessian (∂²y/∂xᵢ∂xⱼ)** matrisleriyle,
   PaD / Garson / Olden duyarlılık analizleriyle ve tepki eğrileriyle gösterir.

---

## 1. Kurulum

```bash
pip install -r requirements-ysa.txt      # numpy, pandas, openpyxl, matplotlib
```

## 2. Hızlı başlangıç

```bash
python -m ysa calistir ornek_config.json
```

`ornek_veri/cumg04_tel_cekme_SENTETIK.xlsx` dosyası **yapay (sentetik)** bir CuMg0.4 tel çekme veri
setidir; yalnızca aracı denemek içindir, gerçek deney verisi değildir
(`ornek_veri/sentetik_veri_uret.py` ile üretilir). Kendi verinizi aynı düzende (her satır bir deney,
sütunlar girdi ve çıktılar) Excel'e girip konfigürasyondaki `veri` bölümünü değiştirmeniz yeterli.

Diğer komutlar:

```bash
python -m ysa tahmin  ysa_sonuclar/calisma_.../en_iyi_model.json yeni_deneyler.xlsx   # tahmin
python -m ysa analiz  ysa_sonuclar/calisma_.../en_iyi_model.json noktalar.xlsx       # Jacobian/Hessian
python -m ysa bilgi                                                                  # algoritma listesi
python -m pytest tests -q                                                            # doğruluk testleri
```

## 3. Konfigürasyon (JSON)

| Alan | Açıklama | Örnek |
|---|---|---|
| `veri.dosya` | Excel (.xlsx) veya CSV | `"verilerim.xlsx"` |
| `veri.girdiler` / `veri.ciktilar` | Sütun adı, Excel harfi veya aralığı | `["Hiz", "Redüksiyon_%"]`, `"A:E"`, `["F","I"]` |
| `veri.kategorik` | One-hot kodlanacak sütunlar (yazı içeren sütunlar otomatik) | `["Kalip_malzemesi"]` |
| `bolme` | `yontem`: `rastgele` / `blok` / `aralikli` / `sutun`; oranlar; `tohum` | `{"egitim":0.7,"dogrulama":0.15,"test":0.15}` |
| `normalizasyon` | `mapminmax` [-1,1] (MATLAB varsayılanı), `zscore`, `none`; `kaynak`: `egitim` / `tum` | |
| `katmanlar` | Her gizli katman için nöron aralığı: `[min,max]`, `[min,max,adim]`, `{"liste":[...]}` | `[[1,100],[2,10]]` |
| `katman_sayilari` | Denenecek derinlikler (verilmezse tüm katmanlar) | `[1,2]` |
| `egitim_algoritmalari` | `trainlm`, `trainbr`, `trainscg`, `trainbfg`, `trainrp`, `traingdx`, `traingd`, `adam` | `["trainlm","trainbr"]` |
| `gizli_aktivasyonlar` | `tansig`, `logsig`, `purelin`, `poslin`(ReLU), `softplus`, `elu`, `radbas`, `swish` | `["tansig","logsig"]` |
| `cikis_aktivasyonu` | Regresyon için `purelin` önerilir | `"purelin"` |
| `tekrar` | Her konfigürasyon kaç farklı başlangıç ağırlığıyla eğitilsin | `5` |
| `egitim_parametreleri` | `epochs`, `max_fail` (erken durdurma), `goal`, `min_grad`, `time_limit` | |
| `algoritma_parametreleri` | Algoritmaya özel: `mu`, `mu_dec`, `mu_inc`, `lr`, `mc`, `adam_lr` ... | `{"trainlm":{"mu":0.001}}` |
| `secim_kriteri` | `val_mse` (önerilen), `all_mse`, `train_mse`, `test_mse`, `val_r`, `all_r` | |
| `paralel_is` | `-1` = tüm çekirdekler | |

**Sizin örneğiniz** (1. katman 1–100, 2. katman 2–10 nöron, tek ve iki katmanlı tüm ağlar):

```json
"katmanlar": [[1, 100], [2, 10]],
"katman_sayilari": [1, 2],
"egitim_algoritmalari": ["trainlm", "trainbr"],
"tekrar": 5
```

Bu, (100 + 100×9) = 1000 mimari × 2 algoritma × 5 tekrar = **10 000 eğitim** demektir. Ekran ilerlemeyi
ve tahmini kalan süreyi gösterir; sonuçlar `tum_calistirmalar_canli.csv` dosyasına anında yazıldığı için
yarıda kesilse bile kaybolmaz. Kaba aralıkla başlayıp (`[1,100,5]`) iyi bölgeyi daraltmak süre kazandırır.

## 4. Çıktılar (`ysa_sonuclar/calisma_TARIH/`)

| Dosya | İçerik |
|---|---|
| `YSA_Rapor.xlsx` | Tüm sonuçlar (sayfalar aşağıda) |
| `en_iyi_model.json` | Model: mimari, ağırlıklar, biaslar, normalizasyon |
| `en_iyi_model_tahmin.py` | Ağırlıkları gömülü, yalnız numpy gerektiren bağımsız tahmin fonksiyonu |
| `en_iyi_model_formul.txt` | Sayısal katsayılarıyla açık model denklemi |
| `modeller/` | En iyi K modelin her biri |
| `grafikler/` | Regresyon, eğitim eğrisi, tarama haritası, duyarlılık, Jacobian, Hessian, tepki eğrileri |

`YSA_Rapor.xlsx` sayfaları:

- **Ozet**, **Basari_Cikti_Bazinda** – en iyi model ve her çıktı için MSE/RMSE/MAE/MAPE/R²/R (orijinal birim)
- **Tum_Calistirmalar** – her eğitimin satırı (mimari, algoritma, tekrar, parametre sayısı, epok, durma nedeni, tüm hatalar)
- **Mimari_Ozet** – tekrarların ortalaması ± standart sapması; sıralı
- **Agirlik_Bias_KatmanN** – etiketli ağırlık matrisleri (satır = hedef nöron, sütun = kaynak) ve bias sütunu
- **Tum_Parametreler_Vektor**, **Normalizasyon**
- **Tahminler** – her örnek için gerçek / YSA / hata / % hata ve hangi kümede olduğu
- **Jacobian_Duyarlilik** – ortalama ∂y/∂x, |∂y/∂x|, std, normalize duyarlılık, elastisite, PaD %, Garson %, Olden; kategorik girdi etkileri
- **Jacobian_Ornek_Bazinda** – her örnek noktasında ∂yₖ/∂xᵢ
- **Hessian_Girdi**, **Jacobian_Hessian_OrtNokta** – ortalama Hessian ve ortalama çalışma noktasındaki Jacobian/Hessian
- **Tepki_Egrileri** – bir girdi taranırken (diğerleri sabit) çıktı ve türevi
- **Hessian_Agirlik(_Matris)** – performansın ağırlıklara göre Hessian'ı (Gauss-Newton ve tam), özdeğerler, koşul sayısı
- **Egitim_Gecmisi**, **Formul**, **Konfigurasyon**
- **Excel_Hesap_Makinesi** – ağın tamamı Excel formülleriyle; sarı hücrelere girdi yazınca tahmin canlı hesaplanır

## 5. Yöntem

**Ağ.** aˡ = fₗ(Wˡ aˡ⁻¹ + bˡ), a⁰ = normalize girdi. Wˡ boyutu (nₗ × nₗ₋₁) – MATLAB `IW/LW` ile aynı yön.
Başlangıç ağırlıkları Nguyen-Widrow (tansig/logsig), diğerlerinde Glorot. Aynı mimari ve tekrar numarası
tüm algoritmalarda **aynı başlangıç ağırlıklarını** kullanır (adil karşılaştırma); veri bölmesi de tüm
konfigürasyonlarda aynıdır.

**Levenberg-Marquardt (trainlm).** e = y − t, J = ∂y/∂θ (analitik geri yayılım),
Δθ = −(JᵀJ + μI)⁻¹ Jᵀe. Hata azalırsa μ ← μ·0,1, artarsa μ ← μ·10. Parametre sayısı artık sayısından
büyükse Woodbury özdeşliğiyle küçük JJᵀ sistemi çözülür (büyük ağlarda hızlı).

**Bayes düzenlileştirme (trainbr).** F = β·E_D + α·E_W; γ = Σ βλᵢ/(βλᵢ + α) (λᵢ: JᵀJ özdeğerleri),
α = γ / 2E_W, β = (n − γ) / 2E_D (MacKay 1992; Foresee & Hagan 1997). γ “etkin parametre sayısı”
olarak rapora yazılır; toplam parametre sayısından çok küçükse ağ gereğinden büyüktür.

**Erken durdurma.** Doğrulama hatası art arda `max_fail` epok iyileşmezse durulur ve en düşük
doğrulama hatasındaki ağırlıklar döndürülür (trainbr hariç).

**Girdi Jacobian'ı ve Hessian'ı.** İleri-mod otomatik türev ile tam (sonlu fark yaklaşımı değil):

- ∂zˡ/∂x = Wˡ ∂aˡ⁻¹/∂x, ∂aˡ/∂x = f′(zˡ) ⊙ ∂zˡ/∂x
- ∂²aˡ/∂x∂x = f″(zˡ) (∂zˡ/∂x)(∂zˡ/∂x)ᵀ + f′(zˡ) ⊙ Wˡ ∂²aˡ⁻¹/∂x∂x

Normalizasyon doğrusal olduğundan sonuçlar **orijinal birimlere** kesin olarak çevrilir
(ör. MPa / (m/s)). Hessian'ın köşegeni eğriliği (doygunluk, optimum), köşegen dışı terimleri iki girdinin
**etkileşimini** gösterir (ör. hadde sayısı × redüksiyon).

**Duyarlılık ölçütleri.**

- Normalize duyarlılık: (∂y/∂x)·Δx/Δy – boyutsuz; işareti, girdi artınca çıktının arttığını/azaldığını gösterir
- Elastisite: (∂y/∂x)·x/y – girdide %1 değişime çıktının % tepkisi
- PaD (Gevrey vd. 2003): SSDᵢ = Σₙ(∂y/∂xᵢ)² → göreli katkı %
- Garson (1991) ve Olden & Jackson (2002) – yalnız ağırlıklara dayanan karşılaştırma yöntemleri
- Kategorik girdiler (ör. kalıp malzemesi) one-hot kodlanır. 0/1 değişkende türev anlamsız olduğundan
  bunlar için tüm örnekler sırayla her seviyeye ayarlanıp ortalama tahminler karşılaştırılır.

Bütün türevler testlerde sonlu farklarla karşılaştırılır (`tests/test_ysa.py`).

## 6. Tez ve makale için öneriler

- **Seçimi doğrulama hatasıyla yapın**, test kümesini yalnız son raporlama için kullanın
  (`secim_kriteri: val_mse`). Test hatasıyla seçim yapmak sonucu iyimser gösterir.
- **Tekrar sayısını ≥ 5 tutun** ve `Mimari_Ozet`teki ortalama ± std değerlerini raporlayın; tek bir
  şanslı başlangıç ağırlığı yanıltıcı olabilir.
- `egitim_ornek/parametre` sütununa dikkat edin: bu değer 1'in çok altındaysa ağ, veriye göre fazla
  büyüktür. Az veride `trainbr` genellikle daha kararlı sonuç verir.
- Hız, redüksiyon gibi sürekli girdilerin yanında kalıp malzemesi gibi kategorik bir girdi kullanıyorsanız
  `kategorik` listesine ekleyin; 1, 2, 3 gibi sayısal kodlar ağa yapay bir sıralama dayatır.
- Excel_Hesap_Makinesi sayfasıyla jüri/hakem, modelin ağırlık ve biaslarla nasıl tahmin ürettiğini
  Python olmadan doğrulayabilir.

### Kaynaklar

- Hagan, M. T., & Menhaj, M. B. (1994). Training feedforward networks with the Marquardt algorithm. *IEEE TNN*, 5(6).
- MacKay, D. J. C. (1992). Bayesian interpolation. *Neural Computation*, 4(3).
- Foresee, F. D., & Hagan, M. T. (1997). Gauss-Newton approximation to Bayesian learning. *ICNN*.
- Møller, M. F. (1993). A scaled conjugate gradient algorithm for fast supervised learning. *Neural Networks*, 6(4).
- Riedmiller, M., & Braun, H. (1993). A direct adaptive method for faster backpropagation learning: The RPROP algorithm. *ICNN*.
- Nguyen, D., & Widrow, B. (1990). Improving the learning speed of 2-layer neural networks... *IJCNN*.
- Gevrey, M., Dimopoulos, I., & Lek, S. (2003). Review and comparison of methods to study the contribution of variables in ANN models. *Ecological Modelling*, 160(3).
- Garson, G. D. (1991). Interpreting neural-network connection weights. *AI Expert*, 6(4).
- Olden, J. D., & Jackson, D. A. (2002). Illuminating the “black box”. *Ecological Modelling*, 154(1-2).
