# Session log

Add each session summary immediately below this heading so the newest entry remains first.

## 2026-07-27 — Progress slider ready for device acceptance

- Added a labelled 0–100% progress slider to the revealed reader controls.
- The slider follows Readium’s overall book progression, pauses updates while dragged, and uses Readium’s progression locator to jump when released.
- Added unit tests for 0%, 50%, 100%, and out-of-range conversion. Debug build, unit tests, and Android test APK build pass.

## 2026-07-27 — Centre-tap reader controls

- Added a centre-screen tap gesture that reveals or hides the reader controls; controls automatically hide after four seconds when revealed.
- Left/right swipe navigation remains handled by Readium and is unaffected.
- Debug build, unit tests, and Android test APK build pass. Manual phone verification passed.

## 2026-07-27 — Private EPUB library import

- Reader now imports each opened EPUB into `filesDir/ebooks/<SHA-256 hash>.epub` and reuses the same copy for duplicate content.
- The content hash is both the private library filename and Room `bookId`; the original selected EPUB remains unchanged.
- Added unit tests for first import and duplicate-copy reuse. Debug build and unit tests pass.
- Manual acceptance passed: opening an EPUB shows the “Added to library” message.

## 2026-07-27 — Stable book identity and reading-location restoration

- Added SHA-256 content-based EPUB identities, so the same EPUB maps to the same Room record when reopened.
- Persisted Readium `Locator` JSON and total progression while reading, with debounced updates plus a final save when the reader pauses.
- Reader startup now restores a saved locator as Readium’s `initialLocator`.
- Added unit coverage for stable/different content IDs. Debug app, unit tests, and Android test APK all build successfully.
- Manual acceptance passed: reopening the same EPUB returns to the saved page.

## 2026-07-27 — Reader persistence foundation

- Added DataStore for global reading appearance: font scale, font family, line spacing, and light/dark theme.
- Added Room (SQLite) version-1 schema for per-book source, title, saved locator, progression, and last-opened time.
- Added model unit tests plus instrumented tests for real DataStore persistence and Room book-location isolation.
- `connectedDebugAndroidTest` passed on a physical Android 15 device, completing the persistence-foundation success check.
- Added `reader-controls-brief.md` with the planned centre-tap controls, progress slider, appearance panel, and location restoration stages.

## 2026-07-27 — White reader top inset

- Inspected an on-device screenshot and confirmed the dark strip was the Android status bar, with EPUB content beginning immediately below it.
- Added a 32dp white host inset above the navigator, so the first content line has visible breathing room without a grey band.

## 2026-07-27 — Readium internal page margins

- Replaced the artificial outer top margin with Readium's `EpubPreferences(pageMargins = 1.5)` at navigator creation.
- The setting applies spacing inside reflowable EPUB pages, around text and images, while retaining the compact 56dp controls bar.

## 2026-07-27 — Reader footer

- Changed the reader screen so the EPUB navigator occupies only the space above a fixed controls bar; reader text no longer runs beneath the footer.
- Kept Back in the reserved bar and established it as the location for future table of contents, typography, theme, and page-navigation controls.

## 2026-07-27 — Readium EPUB compatibility spike

- Replaced the reader scaffold with a working Readium parser and EPUB navigator in `ReaderActivity`.
- Added the Readium Kotlin 3.1.2 dependencies, AndroidX, and required core-library desugaring; the standalone debug build succeeds.
- Validated the supplied local `swordofk.epub`: its EPUB archive passes integrity checks and contains four image assets (a cover plus three illustrations).
- Added `*.epub` to `.gitignore`; supplied books remain local and untracked.

## 2026-07-27 — Project initialization and reader direction

- Created the standalone `wombat-reader` Android project and published its initial GitHub repository.
- Moved the staged implementation plan into `ebookreader.md` and completed its scope/design step: offline paginated EPUB reading, table of contents, font/theme controls, and saved reading location are the first-release target.
- Wombat Liberates remains a separate project and its build/tests were verified after moving reader work out.
