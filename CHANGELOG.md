# Changelog
All notable changes to this project will be documented in this file.

## [v1.0.6] - 2026-07-08
### Fixed
- Memperbaiki bug Screen Capture yang tidak bekerja di v1.0.5 (bubbles langsung terhapus saat baru muncul karena event overlay memicu pembersihan).
- Memperbaiki bug download AI Model untuk Japanese/Korean dan lainnya (menambahkan `android.permission.INTERNET` di AndroidManifest).
- Menambahkan anti-double click pada tombol FAB Start Service agar dialog Screen Capture stabil muncul di Android 14/15.

## [v1.0.5] - 2026-07-08
### Added
- FAB Stop button di Main UI bisa di-klik untuk stop service langsung dari aplikasi.
- Translation models sekarang bisa didownload dengan manual di UI AI Models beserta indikator Notifikasi.
- Terjemahan (bubble) akan terhapus otomatis seketika ketika ada aktivitas scroll di layar.

### Changed
- Menu AI Models kini mengecek & mengunduh model **Translation (NLP)**, bukan model OCR (karena OCR otomatis di-download Google Play Services).

## [v1.0.4] - 2026-07-08
### Fixed
- Fix crash (IllegalStateException) saat start service di Android 14 dan Android 15.
- Menambahkan registrasi `MediaProjection.Callback` yang diwajibkan OS terbaru sebelum memulai screen capture.

## [v1.0.3] - 2026-07-08
### Added
- "Stop" action button di Foreground Notification untuk mematikan service langsung dari notifikasi.
### Changed
- Notifikasi di set menjadi `ongoing` (tidak bisa di-swipe away saat service berjalan).
- MainActivity UI sinkron otomatis ketika service di-stop dari notifikasi.

## [v1.0.2] - 2026-07-08
### Fixed
- Crash/mental keluar saat menekan Start Service di Android 14 karena SecurityException pada MediaProjection service yang membutuhkan type dan token valid sebelum pemanggilan startForeground().

## [v1.0.1] - 2026-07-08
### Fixed
- AccessibilityService malfunctioning (lateinit crash dan event flooding)

## [v1.0.0] - 2026-07-08
### Added
- Initial Release
