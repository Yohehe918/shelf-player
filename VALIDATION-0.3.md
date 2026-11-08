# Shelf 0.3 validation — 2026-11-08

## Build

JDK 17; Android SDK 36; Gradle 8.14.3 / AGP 8.13.2. `assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug` succeeded. 37 JVM tests passed (23 library/parser/resume tests, 7 metadata tests, 7 adapted upstream track-label tests). Android Lint: 0 errors, 9 warnings; warnings are not suppressed.

## Television checks

Xiaomi MiTV-MFTR0 / S Pro Mini LED, Android 14 (SDK 34), HyperOS 3. Tests used a separately installed `local.shelf.player.qa` app and a localhost read-only SMB 2 share forwarded through ADB. Original Shelf preferences and personal NAS media were not used for these fixtures.

- Device instrumentation: 11 assertions/contracts, including real SQLite rollback, source isolation, manual correction precedence, close/reopen persistence, reset, Unicode URI and Media3 format labels. These run against a uniquely named temporary database that is removed afterwards.
- Synthetic H.264/AAC clips (65 seconds, 640×360 at 24 fps), three episodes across two seasons, with matching Chinese SRT subtitles.
- Verified one show card, season browsing, numeric episode ordering, watched/unwatched management and missing-TMDB-token prompt.
- Confirmed SMB video first-frame rendering and Chinese subtitle cues on this TV.
- Opened stream information and subtitle appearance; changed text colour to yellow and size to 30. Preferences carried into subsequent episodes and app updates.
- Selected next episode from S01E01 to S01E02, then across the season boundary to S02E01. Screenshot and details screen confirmed S02E01.
- Adjusted the four-character subtitle appearance button to avoid wrapping/clipping. Playback pause uses play intent so it also works during buffering; explicit media play/pause keys and restart after end are handled.

- Relaunched the app after saving 40 seconds of progress: the continue-watching row and 00:00:40 resume action persisted. Confirmed a rendered video frame at 00:00:40.042 after reopening at 40 seconds. The TV decoder may need several seconds to discard frames before the target after a long-keyframe seek.
- Stopped the fixture SMB server and relaunched: the saved show remained visible with the offline-cache message.
- Preserve known duration when leaving during a buffering seek, so a previously watched title does not lose its rewatch entry.

## Installation

The final `local.shelf.player` APK (version 0.3 / code 3) was installed with an in-place update over 0.2. Its signing certificate matches the prior installed APK (SHA-256 `842bbc473ca4676092ca56e106336fcf9358cacea377ccef429d43eb9d106097`). Final APK device hash matches the delivered APK. Production launch and Settings → version 0.3 were checked; QA apps and the ADB test forwarding rule were removed. No production preference reset was performed. The original app had no saved SMB preference file; fixture settings stayed in the QA app.

## Coverage boundaries

No personal TMDB/OpenSubtitles credentials were supplied. Live metadata search, real download quota/login and online subtitle matching are therefore **not end-to-end verified**. JSON fixtures cover metadata parsing and ambiguity rejection; SQLite tests cover persistence and protection of manual matches. No upstream service key was copied.

The smoke clips do not establish 4K/HDR/Dolby Vision, Atmos/TrueHD, DTS-HD MA/DTS:X decoding or eARC passthrough. Track labels describe source formats. No new native decoder was added. Large NAS performance, unusual anime naming, every subtitle encoding/style, and prolonged playback were not exhaustively tested.

This is a concrete feature integration, not a complete Infuse clone or a full merger of the four reviewed repositories. Exact copied and ported code is listed in THIRD-PARTY-NOTICES.md.
