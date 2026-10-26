# Porting checklist: V1 features → IPTV Player V2

V1 = the original app in `C:\Android Studio\IPTVPlayer` (see its `README.md` / `NOTES.md`).
V2 = this project (StreamVault-based). Compared 2026-10-26 by searching V2's code; a "missing"
item could still exist under a different name, so confirm on device before building it.

## Missing in V2: port these

- [ ] **New-episode tracking ("Catch-Up" in V1)**: mark a series to track, red badge on its card
      when a new episode appears, Track / Clear Badge buttons on the details page, badge clears
      only when cleared or an episode is watched. Needs a new name in V2, because V2 already uses
      "catch-up" for provider replay/archive TV.
- [ ] **Clear watch history for one show**: button on the series details page, with a
      confirmation that says other shows aren't touched. V2 can only clear history for a whole
      provider (`PlaybackHistoryRepository.clearHistoryForProvider`).
- [ ] **In-player search panel**: search live/movies/series while the video keeps playing, then
      switch to the result in place (series → episode list in the panel). V2's player has a
      guide/channel overlay but no catalog search.
- [ ] **Voice search**: mic button next to the search field, plus the remote's mic button
      starting voice input on the search screen. V2 only accepts the system "voice search" intent
      (`ExternalNavigationRequestParser`), with no in-app mic.
- [ ] **Direct URL and Stream API profile types**: one fixed stream URL, or a URL the app calls
      to get a fresh stream URL (plain text or JSON `{"url": ...}`). V2 supports Xtream, M3U,
      Stalker and Jellyfin only.
- [ ] **Teal color scheme**: V1 palette background `#0F766E`, surface `#159188`, accent
      `#5EEAD4`, white text. V2 has an "Ocean" theme, but it is dark navy-teal, not V1's teal.
      Add it as a selectable theme in `core/ui/.../design/AppPalette.kt`.

## Partly there: adapt

- [ ] **OpenSubtitles API key**: V2 only reads the key at build time (`opensubtitles.api.key` in
      `local.properties` → `BuildConfig.OPENSUBTITLES_API_KEY`). V1 let you paste the key (and
      optional username/password) in Settings. Either add your key to `local.properties` (fast)
      or add a Settings field (V1 behavior).
- [ ] **Multi-View with movies and episodes**: V2's multi-view is live channels only; V1 tiles
      could also play movies and episodes.
- [ ] **Search results grouped in collapsible Live / Movies / Series sections**: V2 has global
      search; check on device whether its layout already works for you before changing it.

## Already in V2: keep V2's version

- Xtream and M3U providers (plus Stalker, Jellyfin, local M3U files), fast provider switching
- Favorites, recently watched, continue watching, resume position
- Channel surfing with up/down, plus numeric channel entry
- EPG: full guide grid and a guide overlay during playback (V1 only had now/next text)
- Global search across live, movies, series
- Subtitles: embedded tracks, OpenSubtitles (movies too), local SRT, timing/appearance
- Series details with seasons/episodes, next-episode autoplay, in-player episode switching
- Multi-view for live channels, with audio focus on one tile
- Settings, backup/restore, parental controls, themes, 38 languages

## Release / Downloader

- [ ] Release signing with your own keystore (V1's is in `C:\Android Studio\IPTVPlayer\Keys`)
- [ ] Host the APK and create a Downloader code (V1 README section 2 has the steps)
- [ ] Optional: point the in-app updater (`APP_UPDATES_ENABLED`) at your own release feed

## Rules for every port

- All on-screen text goes in `strings.xml` and must be translated for every locale in
  `app/src/main/res/xml/locales_config.xml` (see `AGENTS.md`).
- Keep the About screen credit to StreamVault / David Nashash and its links (license requirement).
