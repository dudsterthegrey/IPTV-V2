# Porting checklist: V1 features → IPTV Player V2

V1 = the original app in `C:\Android Studio\IPTVPlayer` (see its `README.md` / `NOTES.md`).
V2 = this project (StreamVault-based). Compared 2026-10-26 by searching V2's code; a "missing"
item could still exist under a different name, so confirm on device before building it.

## Missing in V2: port these

- [x] **New-episode tracking ("Catch-Up" in V1)** (done 2026-10-26: "Track new episodes" on the
      series page, red NEW badge on series cards, "Tracked Shows" Home row, `TrackedSeriesRepository`): mark a series to track, red badge on its card
      when a new episode appears, Track / Clear Badge buttons on the details page, badge clears
      only when cleared or an episode is watched. Needs a new name in V2, because V2 already uses
      "catch-up" for provider replay/archive TV.
- [x] **Clear watch history for one show** (done 2026-10-26: `PlaybackHistoryRepository.clearHistoryForSeries`): button on the series details page, with a
      confirmation that says other shows aren't touched. V2 can only clear history for a whole
      provider (`PlaybackHistoryRepository.clearHistoryForProvider`).
- [x] **In-player search panel** (done 2026-10-26: Search button in live and movie/episode
      controls plus the remote's Search key; `player/search/PlayerContentSearchPanel.kt`): search live/movies/series while the video keeps playing, then
      switch to the result in place (series → episode list in the panel). V2's player has a
      guide/channel overlay but no catalog search.
- [x] **Voice search** (done 2026-10-27: mic button on the Search screen and the in-player search
      panel; the remote's Search key opens Search and starts listening; `core/ui/.../VoiceSearch.kt`): mic button next to the search field, plus the remote's mic button
      starting voice input on the search screen. V2 only accepts the system "voice search" intent
      (`ExternalNavigationRequestParser`), with no in-app mic.
- [x] **Direct URL and Stream API profile types** (done 2026-10-27 as a **Saved Streams** tab, not as
      provider types; `presentation/savedstreams/`, `SavedStreamRepository`): one fixed stream URL, or a URL the app calls
      to get a fresh stream URL (plain text or JSON `{"url": ...}`). V2 supports Xtream, M3U,
      Stalker and Jellyfin only.
- [ ] **Teal color scheme** (deferred 2026-10-27: keeping V2's current theme for now; revisit later): V1 palette background `#0F766E`, surface `#159188`, accent
      `#5EEAD4`, white text. V2 has an "Ocean" theme, but it is dark navy-teal, not V1's teal.
      Add it as a selectable theme in `core/ui/.../design/AppPalette.kt`.

## Partly there: adapt

- [x] **OpenSubtitles API key**: Settings > Playback > Subtitles & translation > **API key**.
      The key you paste wins over any built-in key, is stored encrypted, is checked with
      OpenSubtitles before saving (a rejected key is refused; being offline is not), and survives
      signing out of the account. Username/password sign-in was already in V2.
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

## To test on device (after porting, with a real provider)

Built and unit-tested, but not yet tried with real shows. Use the TV emulator (`Television_1080p`).

- [ ] **New-episode tracking**: on a series page press **Track new episodes** → button reads
      "Tracking new episodes" and the show appears in Home's **Tracked Shows** row; pressing again
      removes it.
- [ ] **NEW badge**: real new episodes take days, so ask Claude to lower the show's
      `known_episode_count` and reset `last_checked_at` in the app's `tracked_series` table (app
      stopped). Reopen Home → red NEW badge on the show's poster (Home and Series screens) and a
      **Clear NEW badge** button on its page; pressing it removes the badge. Repeat, then play any
      episode for a few seconds → badge clears by itself.
- [ ] **Clear watch history**: play two episodes of a show about a minute each, press **Clear
      watch history** → confirmation names the show; after **Clear history** a "Watch history
      cleared" message appears and the episodes lose their progress. Another show's progress stays.

- [ ] **In-player search**: while playing anything, open the controls and press **Search** (or
      press the remote's Search key) → a panel opens on the right with the video still playing.
      Type 2+ letters → Live TV / Movies / Series sections, each collapsible. Pick a channel → it
      switches in place with live controls; pick a movie → movie controls; pick a series → its
      episodes show in the panel, pick one → it plays. Back closes the episode list, then the panel.
      Also check Back from the new content still returns to where the player was opened from.

- [ ] **Voice search**: on the Search screen press the mic button next to the search box → the
      speech prompt appears; say a title → it fills the box and searches. Press the remote's
      Search/mic key from Home → Search opens and starts listening. In the in-player search panel
      the mic does the same. On real Fire TV, check the mic button works (some Fire OS builds have
      no speech recognizer: the app should say "Voice search isn't available" instead of crashing).
      Note: on many remotes the dedicated mic button goes straight to Alexa/Google Assistant and
      never reaches the app; the Assistant can still open the app's search by voice.

- [ ] **Saved Streams, Stream API**: add a Stream API entry pointing at a real API and check it
      plays. (Direct URL was verified on the emulator 2026-10-27: added Mux's test stream from the
      Saved Streams tab, it played, Back returned to the list.) Also check Edit, Delete, and that
      the keyboard's Next/Done key moves to the next field in the add/edit dialog.

- [ ] **OpenSubtitles API key**: Settings > Playback > Subtitles & translation > **API key** reads
      "Not set". Paste your key from opensubtitles.com/consumers and press **Save** → row reads
      "Using your key". Play a movie, open subtitles and search online → results appear. Reopen the
      dialog → **Remove my key** returns the row to "Not set". A made-up key should be refused.

## Release / Downloader

- [ ] Release signing with your own keystore (V1's is in `C:\Android Studio\IPTVPlayer\Keys`)
- [ ] Host the APK and create a Downloader code (V1 README section 2 has the steps)
- [ ] Optional: point the in-app updater (`APP_UPDATES_ENABLED`) at your own release feed

## Rules for every port

- All on-screen text goes in `strings.xml` and must be translated for every locale in
  `app/src/main/res/xml/locales_config.xml` (see `AGENTS.md`).
- Keep the About screen credit to StreamVault / David Nashash and its links (license requirement).
