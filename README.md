# Lumen

[![Release](https://img.shields.io/github/v/release/tutualican/lumen)](https://github.com/tutualican/lumen/releases/latest)

Galeriden **seçilen** fotoğraf veya videoya GPU üzerinde renk filtresi uygulayan Android
uygulaması. Kamera kullanmaz; yalnızca Android Photo Picker ile seçim yapar.

**APK indir:** [Lumen 1.0.1](https://github.com/tutualican/lumen/releases/download/v1.0.1/Lumen-1.0.1.apk)
(Android 10+, imzalı release)

Filtre kataloğu `kapsamlı_kamera_filtreleri_ve_estetik_rehberi.md` dosyasındaki
26 stilin tamamını kapsar.

## Kalite sözleşmesi

| Konu | Fotoğraf | Video |
|---|---|---|
| Çözünürlük | Orijinalin aynısı, yeniden boyutlandırma yok | Kaynak kare boyutu, ölçekleme yok |
| Kodlama | PNG (gerçek kayıpsız) veya JPEG %100 | H.264/HEVC, kaynaktan yüksek bitrate |
| Ses | — | Kaynaktan **bit-bit kopya** (yeniden encode yok) |
| Renk uzayı | sRGB, ICC/EXIF etiketleri kopyalanır | BT.709 metadata bitstream'e yazılır |
| Yön | EXIF yönü piksellere uygulanır, etiket sıfırlanır | Kaynak döndürme shader'da uygulanır |
| Önizleme | Aynı shader hattı | Aynı shader hattı |

Tek zorunlu taviz: video dışa aktarımı her zaman yeniden encode eder (MediaCodec).
Bunu telafi etmek için bitrate `genişlik × yükseklik × fps × 0.30` formülüyle hesaplanır
ve kaynak bitrate'in 1.6 katının altına inmez (8–150 Mbps aralığında).

Ayrıca çok büyük fotoğraflarda (kaynak + çıktı bitmap'i uygulama belleğine sığmıyorsa)
uygulama çökmez; bunun yerine görüntüyü en yakın sığan boyuta indirir ve sonucu
**açıkça** bildirir ("cihaz belleği sınırı nedeniyle ölçeklendi").

## Filtre kataloğu

**Retro & Analog**
- `fun_saver_98` — 35mm tek kullanımlık film: gren, halasyon, ışık sızıntısı, turuncu tarih damgası
- `y2k_ccd` — kompakt dijital + sert flaş, patlamış ten tonları, mikro piksellenme
- `cinestill_800t` — tungsten gece filmi: turkuaz gölgeler + kırmızı halasyon
- `lomo` — aşırı doygunluk, renk sapması, yoğun vinyet

**Canlı & Modern**
- `vivid` — akıllı doygunlaştırma (ten korunur)
- `teal_orange` — gölge turkuaz / highlight turuncu
- `cyberpunk` — sarı-yeşil bastırma, magenda/cyan yükseltme
- `tropical_pop` — turkuaz mavi, parlak yeşil, yüksek kontrast

**Leica / Fujifilm**
- `leica_authentic`, `leica_vibrant`, `leica_monochrom`, `leica_bw_natural`, `leica_bw_high`
- `fuji_provia`, `fuji_velvia`, `fuji_classic_chrome`, `fuji_astia`, `fuji_classic_neg`

**Lo-Fi & Rüya**
- `vhs_split` — RGB kayması + tarama çizgileri
- `faded_memory` — mat siyahlar, soluk Kodachrome
- `dreamy_bloom` — çok geçişli bloom, buğulu odak
- `midnight_liminal` — lacivert gölge, soluk sarı highlight

**Doğa & Sinema**
- `overcast_moody` — susturulmuş yeşil, sage gölgeler
- `super8` — grain, letterbox, ışık sızıntısı, tarih damgası
- `bleach_bypass` — %60 doygunluk düşüşü + sert kontrast
- `infrared` — kanal rotasyonu ile pembe yaprak / lacivert gök

Her filtrede **Yoğunluk** kaydırıcısı vardır; `FilterParams.atIntensity()` tüm
parametreleri nötr değere doğru doğrusal enterpole eder, böylece %40 gibi ara
değerler de doğal görünür.

## Mimari

```
model/          FilterParams, FilterPreset, FilterCatalog (26 preset)
gl/             EglCore, Framebuffer, Shaders (GLSL ES 3.00), FilterPipeline
export/         PhotoExporter (tile + tam çözünürlük), VideoExporter (MediaCodec)
ui/             FilteredPreviewView (GLSurfaceView), EditorScreen (Compose)
```

Render hattı (tek kare başına):

1. **Grade pass** — tam çözünürlüklü FBO: pozlama, beyaz dengesi, gölge/highlight
   renklendirme, hue rotasyonu, doygunluk/vibrance, kontrast, gamma, fade,
   mono kanal karışımı, kromatik aberasyon, pikselleştirme, unsharp mask
2. **Bloom zinciri** — parlak geçiş + ayrılabilir Gauss (¼ çözünürlük, 2 iterasyon)
3. **Halasyon zinciri** — düşük eşik + kırmızı tint (⅛ çözünürlük)
4. **Composite** — bloom + halasyon + grain + vinyet + ışık sızıntısı + letterbox +
   tarama çizgileri + tarih damgası; istenirse 90/180/270° döndürme

Büyük fotoğraflarda her kare 48 px dolgulu (padding) karolara bölünür; bulanıklık
zincirleri bu dolgu sayesinde karo sınırlarında dikiş bırakmaz. Prosedürel efektler
(vinyet, grain, sızıntı, damga) **global UV** ile hesaplandığı için karolar arasında
konum atlaması olmaz.

## Derleme

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-21.0.12"
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

İmzalı release için proje köküne `keystore.properties` koyun (git'e girmez):

```properties
storeFile=C\:\\path\\to\\lumen-release.jks
storePassword=***
keyAlias=lumen
keyPassword=***
```

Dosya yoksa da derleme çalışır; release APK imzasız üretilir.

APK: `app/build/outputs/apk/release/app-release.apk`

- minSdk 29, targetSdk 36, compileSdk 36
- Kotlin 2.3.21, AGP 8.13.1, Compose Material3
- İzin gerekmez: Photo Picker + MediaStore `IS_PENDING` akışı

## Doğrulama durumu

- Kotlin derlemesi: temiz
- 6 birim testi: geçti (preset kimlikleri, aralık kontrolü, yoğunluk nötrlüğü)
- Tüm GLSL shader'lar `glslangValidator` ile bağımsız olarak derlendi (ES 3.00)
- Cihaz/emülatör testi **yapılmadı**: bu makinede hipervizör (AEHD/WHPX) kurulu
  olmadığı için emülatör başlatılamıyor. İlk çalıştırmada shader derleme hatası
  olursa `FilterPipeline` `IllegalStateException` ile shader log'unu verir.

## Sıradaki adımlar

- `.cube` / 3D-LUT desteği (lightroom presetleri) — mevcut parametrik motorun
  üstüne tek bir `sampler3D` geçişi eklemek yeterli
- Video için canlı filtre önizlemesinde kare zamanına bağlı animasyonlu grain
- Dışa aktarma kuyruğu ve iptal düğmesi (`VideoExporter` zaten `AtomicBoolean` alıyor)
