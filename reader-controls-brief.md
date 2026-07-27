# Wombat Reader — controls, preferences, and reading-location brief

## Goal

Make Wombat Reader comfortable to use for long reading sessions without cluttering the page:

- Swiping left/right continues to turn pages.
- A centre-screen tap toggles reading controls.
- Revealed controls provide a quick progress slider plus access to reading appearance settings.
- Users can adjust font size, font type, line spacing, and dark mode.
- The app remembers both global reading preferences and the exact place reached in each book.
- All storage remains private to the device and works offline.

## Design decisions

### Interaction model

```text
Reading page
  ├─ swipe left/right ───────────> Readium page navigation
  └─ tap centre ────────────────> Show/hide reader controls
                                    ├─ Back
                                    ├─ Progress slider → jump through book
                                    └─ Aa settings → appearance panel
                                                        ├─ Font size
                                                        ├─ Font type
                                                        ├─ Line spacing
                                                        └─ Dark mode
```

- Do not use a 1.5-second hold for controls. Readium provides reliable tap/drag hooks, while a hold requires fragile page-level JavaScript and can conflict with text selection.
- Controls should auto-hide after a short idle period, except while the user is touching the slider or settings panel.
- In dark mode, the EPUB canvas is black, text is white, and host controls/status bars use matching dark colours.

### Storage model

Use Android-managed private storage, not a hand-written settings file.

| Data | Storage | Why |
|---|---|---|
| Font size, font type, line spacing, theme | Jetpack DataStore Preferences | Small global key/value settings with safe atomic updates. |
| Book ID, source URI/private copy, title, last Readium `Locator`, progress, last opened time | Room database (SQLite) | Structured per-book data that scales into a local library. |

No EPUB text or reading data is sent to a server.

## Implementation and verification checklist

### [x] 1. Add persistent reader data foundations

**Work**

- Add DataStore Preferences and Room dependencies.
- Define `ReaderPreferences` with font size, font family, line spacing, and light/dark theme.
- Define a Room `BookReadingState` entity with a stable book ID, source URI/private path, serialized Readium locator, total progression, title, and last-opened timestamp.
- Add migrations/versioning from the first Room schema onward.

**Success conditions**

- Preferences and per-book state are private to the app and survive process death and app restart.
- A book’s position is associated with its stable ID, not a transient screen instance.
- Database initialization cannot block the main UI thread.

**Tests**

- DataStore unit/instrumented test: write every preference, recreate the repository, and read back the same values.
- Room DAO test: insert, update, and retrieve state by book ID; verify only the intended book changes.
- Migration test from schema version 1 to the current schema.
- Manual test: force-stop and reopen the app; stored values remain.

### [x] 2. Establish stable book identity and restore location

**Work**

- On opening a book, resolve/create a stable book ID using the managed private copy or a content hash plus source metadata.
- Load its saved Readium `Locator` before creating the navigator.
- Observe Readium’s current locator as pages change and debounce writes to Room.
- Save a final locator when the reader pauses/stops.
- Restore the saved locator when the same book is opened again.

**Success conditions**

- Reopening the same EPUB returns the reader to the last page/location rather than the title page.
- Changing books never overwrites another book’s location.
- Rapid page turns do not cause a database write for every animation frame.

**Tests**

- Unit test: locator serialization/deserialization round-trip preserves href and progression data.
- Instrumented test: save a locator, recreate `ReaderActivity`, and verify the navigator receives it as `initialLocator`.
- Manual test: open a book, move to a later chapter, close/force-stop/reopen, and confirm the same page is restored.
- Manual test: alternate between two books and confirm each restores independently.

### [ ] 3. Add centre-tap control visibility

**Work**

- Register a Readium input listener.
- Treat only taps within the centre region as a controls toggle; preserve left/right reading navigation and all swipes.
- Animate the controls bar in/out.
- Auto-hide controls after a short idle timeout; cancel the timeout while the user interacts with controls.
- Keep Back available through Android system Back regardless of visibility.

**Success conditions**

- A centre tap reliably shows/hides controls.
- Swiping left/right keeps its existing page-turn behaviour.
- Controls do not reappear unexpectedly during normal reading.
- Controls remain reachable with TalkBack and hardware navigation.

**Tests**

- Instrumented gesture test: centre tap toggles visible state.
- Instrumented gesture test: edge tap and swipe do not trigger the toggle.
- Instrumented timing test: controls auto-hide after the configured timeout.
- Manual test: read through multiple pages, including image pages, and verify swipe navigation is unchanged.
- Accessibility test: TalkBack can focus and activate every revealed control.

### [ ] 4. Implement the progress slider

**Work**

- Read the navigator’s positions/current locator to calculate total progress.
- Display a labelled `SeekBar` when controls are visible.
- Update the thumb as reading position changes.
- On user release, map slider progress to the nearest valid Readium locator and navigate to it.
- Announce progress accessibly, for example “42 percent through book”.

**Success conditions**

- The slider reflects the current reading location without jumping while the user drags it.
- Releasing the slider navigates near the requested point.
- A user can move quickly from early to late book sections without repeated page swipes.
- Slider navigation works for Wombat Liberates EPUBs and externally supplied reflowable EPUBs.

**Tests**

- Unit test: progression-to-slider and slider-to-nearest-position mapping at 0%, 50%, and 100%.
- Instrumented test: changing the current locator updates the displayed slider value.
- Instrumented test: releasing the slider invokes navigator navigation with the selected locator.
- Manual test: drag to several points in a long book and verify the destination is sensible.
- Accessibility test: slider has a content description, current percentage, and adjustable action support.

### [ ] 5. Implement the Aa appearance panel

**Work**

- Add an Aa button to the revealed controls.
- Open a bottom sheet/panel containing:
  - Font size decrease/increase controls.
  - Font type selector using Readium-supported font families.
  - Line-spacing decrease/increase controls.
  - A light/dark mode toggle.
- Apply changes through Readium `EpubPreferences` immediately.
- Save each change to DataStore and restore it for the next book/app launch.

**Success conditions**

- Each setting changes the currently open reflowable book without reopening it.
- The selected appearance is retained after closing and reopening the app.
- Dark mode changes EPUB background/text and the surrounding reader controls together.
- Settings gracefully remain unavailable/unchanged for EPUB layouts that do not support a preference.

**Tests**

- Unit test: preference mapping produces the expected `EpubPreferences` values.
- Instrumented test: each panel control updates the navigator preference and persists to DataStore.
- Screenshot/manual test: light and dark themes; minimum/default/maximum font sizes; every font; minimum/default/maximum line spacing.
- Manual test: open a book with images and confirm images remain legible in dark mode.
- Accessibility test: all controls have labels, meaningful state descriptions, and adequate touch targets.

### [ ] 6. Integrate appearance and location persistence

**Work**

- Load DataStore preferences and saved book locator before the reader becomes visible.
- Apply preferences to Readium at navigator creation, then restore the locator.
- Save final location on pause and after slider navigation.
- Ensure controls are hidden by default after restoration, leaving the page uncluttered.

**Success conditions**

- Opening a book restores both location and reading appearance without flicker or a visible jump to the title page.
- Restarting the app does not reset user font/theme choices.
- A settings change in one book applies to subsequent books, while reading position remains per-book.

**Tests**

- Instrumented end-to-end test: set dark mode and font size → navigate to later chapter → recreate activity → verify appearance and locator restore.
- Instrumented isolation test: two books maintain distinct locations but share global appearance preferences.
- Manual offline test: disable network, open a local EPUB, change settings, close/reopen, and confirm all state restores.

### [ ] 7. Release readiness

**Work**

- Remove temporary reader debug UI and ensure errors remain understandable.
- Inspect database/storage behaviour for expired or revoked document URIs.
- Update `SESSION-LOG.md` and relevant reader brief checklist items.
- Inspect the final diff for uploaded EPUBs, screenshots, logs, and unrelated files.

**Success conditions**

- Reader controls and persistence work offline on real EPUBs.
- No book text, EPUB sample, screenshot, error log, or local database is committed.
- Build and automated tests pass.

**Tests**

- `./gradlew :app:testDebugUnitTest :app:assembleDebug`.
- Relevant instrumented test task on emulator/device.
- Manual acceptance: open → swipe → centre tap → slider → Aa settings → close/force-stop → reopen at the same page with the same appearance.
- `git diff --check` and `git status --short` before commit.
