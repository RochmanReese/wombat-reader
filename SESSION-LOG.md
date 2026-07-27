# Session log

Add each session summary immediately below this heading so the newest entry remains first.

## 2026-07-27 — White reader top inset

- Inspected an on-device screenshot and confirmed the dark strip was the Android status bar, with EPUB content beginning immediately below it.
- Added a 32dp white host inset above the navigator, so the first content line has visible breathing room without a grey band.
- `./gradlew :app:assembleDebug :app:testDebugUnitTest` succeeds; on-device confirmation is pending.

## 2026-07-27 — Readium internal page margins

- Replaced the artificial outer top margin with Readium's `EpubPreferences(pageMargins = 1.5)` at navigator creation.
- The setting applies spacing inside reflowable EPUB pages, around text and images, while retaining the compact 56dp controls bar.
- `./gradlew :app:assembleDebug :app:testDebugUnitTest` succeeds. On-device confirmation is pending; Readium flags its preferences API as experimental.

## 2026-07-27 — Refined reader spacing

- Reduced the reader controls bar from 72dp to 56dp after on-device feedback.
- Added a 28dp top margin above the EPUB navigator so the first text lines have breathing room.
- `./gradlew :app:assembleDebug :app:testDebugUnitTest` succeeds; device confirmation of the adjusted spacing is pending.

## 2026-07-27 — Reader footer and invalid-EPUB handling

- Changed the reader screen so the EPUB navigator occupies only the space above a fixed 72dp bottom controls bar; reader text no longer runs beneath the footer.
- Kept Back in the reserved bar and established it as the location for future table of contents, typography, theme, and page-navigation controls.
- Added defensive handling around Readium navigator setup so immediate display failures surface as a readable in-app error rather than closing the activity.
- `./gradlew :app:assembleDebug :app:testDebugUnitTest` succeeds. An on-device test remains needed for the footer spacing and invalid-EPUB error state.

## 2026-07-27 — Readium EPUB compatibility spike

- Replaced the reader scaffold with a working Readium parser and EPUB navigator in `ReaderActivity`.
- Added the required Readium-compatible Kotlin and core-library desugaring configuration; `./gradlew :app:assembleDebug` succeeds.
- Validated the supplied local `swordofk.epub`: its EPUB archive passes integrity checks and contains four image assets (a cover plus three illustrations).
- Added `*.epub` to `.gitignore`; the supplied book remains local and untracked.
- `./gradlew :app:testDebugUnitTest` succeeds, though it currently has no unit-test source files. The remaining Step 2 acceptance is an on-device smoke test: open this EPUB, confirm its text and images render, and confirm an invalid file displays a clear error.

## 2026-07-27 — Project initialization and reader direction

- Created the standalone `wombat-reader` Android project and published its initial GitHub repository.
- Added the Readium Kotlin 3.1.2 dependencies, AndroidX, and required core-library desugaring; the standalone debug build succeeds.
- Added an EPUB document-picker entry point and a `ReaderActivity` scaffold. Readium EPUB parsing/rendering is not implemented yet; this is the remaining work in reader-plan step 2.
- Moved the staged implementation plan into `ebookreader.md` and completed its scope/design step: offline paginated EPUB reading, table of contents, font/theme controls, and saved reading location are the first-release target.
- Wombat Liberates remains a separate project and its build/tests were verified after moving reader work out.
