# Changelog
All notable changes to this project will be documented in this file.

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
