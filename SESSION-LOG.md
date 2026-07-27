# Session log

Add each session summary immediately below this heading so the newest entry remains first.

## 2026-07-27 — Searchable EPUB library and reader polish

- Replaced the opening page with a scrollable private library: the full-size wombat illustration, Add Book to Library button, substring title/author search, and compact book cards all scroll together.
- Library cards open on a short tap. Holding a card for two seconds gives haptic feedback and opens a deliberate Delete/Cancel confirmation; deletion removes only Wombat Reader’s private EPUB copy and saved state, never the original selected file.
- Added Room database version-2 migration for author metadata, case-insensitive title/author search, card ordering by most recently opened, and EPUB metadata extraction with filename/Unknown author fallbacks.
- Fixed library-card text contrast in dark system themes and reduced each card to a compact 64dp height.
- Finished reader controls: centre-tap reveal/hide, progress slider, saved locations, restored reader activity after app switching/minimising, seven built-in fonts including OpenDyslexic, font size, line spacing, paragraph gap, dark mode, and shelf/gear icon controls.
- Confirmed on device: reader restoration, slider, typography settings, paragraph gap, shelf/gear controls, library cards, card open, and two-second delete hold all work.
- Current published commit: `8f7ad82 Fix library delete hold gesture`.

## 2026-07-27 — Reader activity restoration

- Reader saves the active EPUB URI across Android activity recreation, safely replaces Readium’s stale fragment shell, reopens the private library copy, and restores the saved locator.

## 2026-07-27 — Reader controls and private EPUB library foundation

- Added private content-addressed EPUB copies in `filesDir/ebooks`, stable SHA-256 book IDs, Room reading-location persistence, and restoration of the last Readium locator.
- Added DataStore persistence for reader appearance preferences and Room schema export/testing foundations.
- Added a centre-tap controls bar, progress slider, top reader inset, and a fixed footer area so EPUB text does not disappear beneath controls.

## 2026-07-27 — Readium EPUB compatibility and project initialization

- Created the standalone Wombat Reader Android project and published it separately from Wombat Liberates.
- Integrated Readium Kotlin 3.1.2 for offline local EPUB reading and confirmed supplied EPUBs, including images, render successfully.
- Kept uploaded EPUB samples, screenshots, and logs out of version control.
- Wombat Liberates remains a separate project; its EPUB XML declaration formatting issue was fixed independently.
