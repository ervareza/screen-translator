## [v1.0.6] - 2026-07-08
### Fixed
- Memperbaiki bug Screen Capture yang tidak bekerja di v1.0.5 (bubbles langsung terhapus saat baru muncul karena event overlay memicu pembersihan).
- Memperbaiki bug download AI Model untuk Japanese/Korean dan lainnya (menambahkan `android.permission.INTERNET` di AndroidManifest).
- Menambahkan anti-double click pada tombol FAB Start Service agar dialog Screen Capture stabil muncul di Android 14/15.
