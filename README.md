# DocScan (Android, Kotlin + Jetpack Compose)

## Cara A - TANPA Android Studio (build di cloud, gratis)
1. Buat akun di github.com, lalu buat repository baru (boleh Private).
2. Klik "uploading an existing file", upload SEMUA isi folder ini
   (termasuk folder .github), lalu Commit.
3. Buka tab "Actions" -> workflow "Build APK" akan jalan otomatis (3-8 menit).
   Jika tidak jalan: pilih "Build APK" -> "Run workflow".
4. Setelah hijau, klik run tersebut -> bagian "Artifacts" -> unduh "DocScan-APK".
5. Ekstrak zip -> kirim file .apk ke HP -> install
   (izinkan "Install dari sumber tidak dikenal").

## Cara B - Android Studio
Buka folder ini, tunggu Gradle sync (JDK 17), lalu Run / Build APK.

Fitur: scan kamera + auto-crop + filter, import galeri, multi-halaman,
ekspor JPG / PNG / PDF / TXT (OCR), bagikan PDF.
