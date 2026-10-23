# Işıklar — Govee ışık kontrolü (Android)

Govee cihazlarını (H8005A + TV arkası LED) telefondan kontrol eden Kotlin/Jetpack Compose uygulaması.

## Nasıl çalışır
- **Yerel ağ (öncelikli):** Telefon evdeki Wi‑Fi'deyken komutlar Govee LAN API ile UDP üzerinden doğrudan cihaza gider (tarama 239.255.255.250:4001, cevap 4002, komut 4003). İnternet gerekmez, gecikme yok.
- **Bulut yedeği:** Cihaz yerelde cevap vermezse (evden uzak, ya da modelde LAN desteği yok) Govee bulut API'si (`openapi.api.govee.com`) kullanılır.

## Özellikler
- Aç/kapat, parlaklık, renk (hazır renkler + ton kaydırıcı), beyaz ton (2000–9000 K)
- Sahneler: Okuma, Akşam, Film (hazır, tüm cihazlara uygulanır) + "Kaydet" ile ışıkların o anki hâlini cihaz bazında sahne yapma. Silmek için sahneye uzun bas.
- Ana ekran widget'ı: Aç · Kapat · ilk iki sahne · 💡 uygulamayı açar

## Kurulum
1. Govee Home → her cihaz → Ayarlar → **LAN Control**'ü aç. (Seçenek görünmüyorsa cihazı fişten çekip takın, ~30 dk sonra tekrar bakın; model desteklemiyorsa çıkmaz.)
2. (İsteğe bağlı, uzaktan kontrol için) Govee Home → Profil → Ayarlar → **Apply for API Key**; e-postayla gelen anahtarı uygulamada Ayarlar'a gir.
3. APK'yı kur, uygulamayı aç, Wi‑Fi'deyken tarama otomatik başlar.

## Derleme
**GitHub Actions (önerilen):** Bu klasörü bir GitHub reposuna push et. `Actions → APK derle` tamamlanınca **isiklar-apk** artifact'ını indir, zip içindeki `app-debug.apk`'yı telefona kur (bilinmeyen kaynaklara izin ver).

**Android Studio:** Klasörü aç (File → Open). Wrapper yoksa Studio önerir; Gradle 8.14.x seç. Run ▶.

Sürümler: AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01, minSdk 26, targetSdk 35.

## Dosyalar
- `govee/LanClient.kt` — UDP tarama, durum, turn/brightness/colorwc
- `govee/CloudClient.kt` — bulut API (cihaz listesi, durum, kontrol)
- `data/LightController.kt` — yerel/bulut yönlendirme, sahneler
- `data/Store.kt` — API anahtarı, cihaz ve sahne kaydı
- `ui/` — Compose ekranları
- `widget/LightsWidget.kt` — ana ekran widget'ı
