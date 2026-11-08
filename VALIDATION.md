# Shelf 0.4.2 — final audit, 2026-11-09

## Changes and automated checks

- Image requests for the same source and decode size share one in-flight job; waiting views are weak references. Poster/background memory variants are separate while original disk bytes are shared.
- Memory cache is cleared on memory pressure and destruction. Database closes on destruction; late queued work cannot reopen it. Stale player listeners and asynchronous UI completions are ignored after their owner is gone.
- Removed an unused episode regex and hoisted folder regex compilation out of per-file parsing.
- JVM: 37 passed, no failures. Lint: zero errors, nine existing warnings. Warnings remain visible; this is not a claim of a warning-free project.
- Physical TV instrumentation: 11 motion/navigation contracts, 12 SQLite/URI/Media3 contracts (including closed-store protection), plus artwork request coalescing/shared bitmap, wide/poster variant isolation and completed-request cleanup all passed.
- Production credentials remained present after the signed in-place update; no credentials were embedded or published. Version 0.4.2 / code 6.

## End-to-end QA fixture

Generated H.264/AAC video and Chinese SRT played over SMB. Checked series/season/detail navigation, watched toggles, matching setup prompt, stream information, audio/subtitle dialogs, subtitle colour, next episode across seasons, and saving and reopening a 40-second resume position. Playback rendered a video frame after resuming. The original automated HUD script lost its button after the intentional 4.5-second auto-hide; pausing before dialog inspection made the sequence deterministic. A first motion run overlapped launcher UI automation; its layer assertion passed when rerun with exclusive UI ownership. These were test-sequencing failures, not suppressed application crashes.

No new crash entries were present from the audit start through playback/navigation checks. Point-in-time total PSS: launcher 71,174 KB, production Shelf 23,047 KB (empty library/background), QA Shelf 81,316 KB (after fixture playback). These are different states and not before/after benchmarks or proof of no leaks during prolonged use.

The offline check after stopping the fixture server displayed the cached library and an explicit share-unavailable message. QA apps and the temporary reverse port were removed after checks.

## Limits

The personal NAS is not configured. SMB playback checks use a separate QA package and read-only generated fixtures. OpenSubtitles requires separate credentials; lossless Atmos / TrueHD, DTS-HD MA / DTS:X and Dolby Vision/HDR passthrough are not certified by these tests. Existing 0.4 frame timing limitations remain applicable; no unsupported percentage performance improvement or universal 60 fps claim is made.

---

# Shelf 0.4.1 — TMDB device verification, 2026-11-09

The user's Read Access Token was entered through the existing television settings and saved with Android Keystore-backed encryption. A check found no plaintext token in the preferences. No credential appears in this source or APK.

The original `api.themoviedb.org` connection timed out on the TV's resolved IPv6 address. The TMDB alternative `api.tmdb.org` was reachable with normal HTTPS certificate verification; a device request using the encrypted saved token returned the Chinese title 盗梦空间 and a decoded 500×750 poster. The production API base was changed to that endpoint. Router settings, global DNS and TLS verification were not changed.

Actual Shelf OnlineServices automatic title matching (Inception, 2010), manual search, detail lookup and poster download were checked from the TV after the endpoint change. The test helper was temporary and removed afterwards. No private credential was compiled into it. SMB was not configured, so this does not claim a scan of the user's personal library. OpenSubtitles remains untested without its separate credentials.

The 37-test regression suite and Android Lint passed (zero lint errors, nine existing warnings). Build version: 0.4.1 / code 5, same package/signature for an in-place update.

---

# Shelf 0.4 validation — motion update, 2026-11-08

## Scope and references

See MOTION-DESIGN.md for Apple/Android primary references and Shelf-specific tuning. This release changes page/focus/dialog/artwork/playback-control transitions and adds a reduced-motion preference. Library parsing, online services and codecs retain their 0.3 capabilities and boundaries (see VALIDATION-0.3.md).

## Automated and device checks

- JVM regression suite: 37 tests pass.
- Android Lint: zero errors; nine existing compatibility/tool-version/layout warnings.
- On the Xiaomi MiTV-MFTR0 / HyperOS 3 / Android 14: eleven motion/navigation contracts pass, covering focus and scroll restoration, rapid replacement, stale callback safety, settled transforms, reduced-motion mode, an untransformed video layer, a retained loading cover its removal on first-frame readiness, and removal of a released video surface before the exit animation.
- Eleven existing device contracts pass: SQLite transactions/reopen, source isolation, manual metadata precedence, URI handling and Media3 format labels.
- QA used a separate package, localhost read-only SMB fixture and generated H.264/AAC videos with Chinese SRT. No production NAS credentials were changed or fixture preferences imported.

## Visual/performance checks

Remote-control navigation exercised Home → show grid → seasons → Back, repeated page entry/exit, rapid left/right focus changes, and return to the original collection focus. Frame statistics are samples from this TV, not a guarantee of perfect 60 fps in every library. Performance tuning removes the outgoing page and temporary texture layer as soon as the incoming page becomes opaque, avoiding two full-screen composites for the entire spring tail.

A focus-only sample on this TV reported 176 frames, 17 ms median / 18 ms p95, zero missed VSync events; its modern deadline metric still marked 9.66% as janky. A repeated-page sample reported 320 frames, 26 ms median / 48 ms p95, and 6.88% modern deadline misses. These are device diagnostics with different timing definitions, not an FPS measurement. Some navigation frames still exceed a refresh interval, especially during page construction.

The original implementation had hard whole-window replacements; its frame count is not directly comparable to animated transitions. No misleading before/after percentage is claimed. Full-resolution artwork, large collections, other televisions and prolonged sessions may differ.

Video first-frame and Chinese subtitle rendering were checked over the QA SMB share. Playback loading kept the previous page visible; exiting the loading/playback path removed the cover. Remote progress-bar Down now targets the first playback menu, rather than an accidental next-episode action.

## Accessibility and delivery

Settings includes 减少动态效果; Android's disabled-animator state is also respected. Focus remains visible when movement is disabled. Native dialog semantics and video SurfaceView geometry are preserved. Apple private code, proprietary animation assets and system-wide animation settings were not modified.

## Installed build

Shelf 0.4 / versionCode 4 was installed in place over 0.3 under the same `local.shelf.player` package and signing certificate. Production preferences were not reset. QA apps, the temporary SMB service and ADB forwarding rule were removed. The APK and full source are delivered together with GPL/MIT/Apache notices.
