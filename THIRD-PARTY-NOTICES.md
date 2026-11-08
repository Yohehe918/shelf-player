# Shelf 0.4.2 source reuse

This version is distributed under GPL-3.0-or-later, with the corresponding Shelf source supplied beside the APK. Component MIT / Apache notices remain applicable. Shelf keeps its own name and icon. No upstream API keys, accounts or proprietary brand assets were imported.

## Copied with adaptations

### Just Player Pro

Copyright (C) 2026 Zain Imam. GPL-3.0-or-later.
Source: https://github.com/Zain-Imam/just-player-pro/tree/20bc66f6a3d117273990e0f77e53eacdf2fde118

- `app/src/main/java/com/brouken/player/TrackNames.java` → Shelf `TrackNames.java`.
- `app/src/main/java/com/brouken/player/SubtitleColors.java` → Shelf `SubtitleColors.java`.
- `app/src/test/java/com/brouken/player/TrackNamesTest.java` and `app/src/test/java/android/text/TextUtils.java` → corresponding Shelf tests.

Changes: package names; translated string resources; optional nullability annotations removed; SAMI parser constant replaced with its MIME literal; Media3 unstable API opt-in. Track names describe the selected source format, not proof of HDMI passthrough.

Upstream COPYING text and copyright notice are in `app/src/main/assets/licenses/` and accessible through Settings → 开源许可与版本 0.4.2.

### NOVA MediaLib

Copyright 2017 Archos SA. Apache-2.0.
Source: https://github.com/nova-video-player/aos-MediaLib/blob/fba68119be9b704bfa0cad78f7ca8422df0e5cfb/src/com/archos/mediascraper/ShowUtils.java

The four named-show episode regular expressions and separator definitions were extracted into `EpisodeNames.java`. Shelf adds its own folder-context adapter and title/year parsing in `Catalog.java`. This is a small parser extraction, not a copy of NOVA's full scraper or decoder.

## Ported to Java

### Kinema

Copyright (c) 2026 Basswaves. MIT.
Source: https://github.com/basswaves/kinema/tree/9b61dc4cb28cc018c12eae13bdc712dace67ccd4

- `src/player/resume.ts` → `ResumePolicy.java`: same 30-second / 94% resume bounds and 80% rewatch policy, adapted to milliseconds; remote Trakt fraction omitted.
- `src/metadata/score.ts` → `TitleMatching.java`: `literalTitle`, leading-article normalization, and numbered-part normalization adapted to Java Unicode/regex. Shelf's automatic matcher deliberately uses literal names and refuses ambiguous results; the full Kinema scoring algorithm is not claimed.

Shelf's own `SeriesIndex`, `LibraryStore`, manual match UI, next/previous episode integration and artwork cache implement local SMB equivalents of features reviewed in Kinema. They are integrations written for Shelf, not unchanged copies of its React/Tauri components.

## Reviewed, not copied

- NOVA aos-AVP `5c5d4ec81b47ba59be8f91c9d877b49b0ab1c01e`: reviewed submodule layout, MediaLib scraper and file parsing. NOVA's native ffmpeg/avos output path was not transplanted into Media3.
- Jellyfin Android TV `45c52925661ff51427cea46ebabcd3de656276cc`: reviewed `BrowsingUtils.createNextEpisodesRequest` and detail browsing. Those requests require Jellyfin server IDs and server-side metadata; no Jellyfin client code is present in this SMB-only integration.
- No mpv/FFmpeg binaries, online service credentials or skip-intro services from these projects are bundled.

## Existing dependencies

- AndroidX DynamicAnimation 1.1.0, Apache-2.0: https://developer.android.com/jetpack/androidx/releases/dynamicanimation
- AndroidX Media3 1.11.1, Apache-2.0: https://github.com/androidx/media
- SMBJ 0.14.0, Apache-2.0: https://github.com/hierynomus/smbj
- Google Material icons, Apache-2.0: see MATERIAL-ICONS-LICENSE.txt.
- Test-only JUnit 4.13.2 (EPL-1.0) and org.json 20240303 (public domain) are not in the APK.

Transitive dependencies retain their own licenses and pinned resolution through Gradle. Project source and build instructions accompany this APK. Upstream trademarks and logos are not granted by these code licenses.
