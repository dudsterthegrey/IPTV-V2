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
- [x] **Multi-View with movies and episodes**: while a movie or episode plays, **Split Screen** in
      the player's settings sheet puts it in a Multi-View slot next to live channels. The tile
      starts where you were. Inside Multi-View, **Replace Slot** now has Live TV / Movies / Series
      tabs (Series goes show → episode list). Limits: presets skip movie/episode tiles, and
      Stremio or other remote-only titles can't join.
- [x] **Search results by Live / Movies / Series** (done 2026-10-28, adapted instead of V1's
      collapsible sections): V2's tabs stay. On the All tab each row title shows its count
      ("Movies 42"), and a row with more than 18 results gets a **See all** button that switches
      to that tab's full grid.

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

- [ ] **Multi-View with a movie**: play a movie, stop at a few minutes in, open the player's
      settings → **Split Screen**, pick a slot → the slot card shows the title and "Movie". Add a
      live channel the usual way, then **Launch Split Screen** → the movie tile resumes near where
      you were and plays beside the channel; moving focus switches the audio. Repeat with an
      episode (card shows "S1 E2 · Show name").

- [ ] **Replace Slot with a movie or episode**: in Multi-View press **Replace Slot** → tabs read
      Live TV / Movies / Series. Movies → a category → a movie fills the focused slot. Series → a
      category → a show → its episodes listed "S1 E1 · Title" → pick one. Back steps out one
      level at a time. Works even with no favorites or history.

- [ ] **OpenSubtitles API key**: Settings > Playback > Subtitles & translation > **API key** reads
      "Not set". Paste your key from opensubtitles.com/consumers and press **Save** → row reads
      "Using your key". Play a movie, open subtitles and search online → results appear. Reopen the
      dialog → **Remove my key** returns the row to "Not set". A made-up key should be refused.

- [ ] **Search counts and See all**: search a common word (e.g. "the") on the All tab → row
      titles read "Live TV 120", "Movies 57"… Rows over 18 results have **See all** at the right
      of the title; press it → the matching tab is selected, the full grid shows, and focus lands
      on that tab chip (Down moves into the results). Rows with 18 or fewer have no button.

- [ ] **Sports › Fight / MMA**: open Sports → the filter chips read All / Football / Basketball /
      Motorsport / **Fight / MMA** / Other. Fight / MMA shows only UFC, Bellator, PFL, ONE,
      bare-knuckle, kickboxing and boxing events (e.g. Top Rank, Matchroom); those no longer
      appear under Other.

- [ ] **Guide fills in**: a few hours after setup (or after Settings › Providers › your provider ›
      sync with EPG ticked), the Guide shows programmes on channels that read "No schedule"
      before. Channels whose provider sends no guide ID (24/7, PPV, many "Shows" channels) stay
      empty unless you add an XMLTV source in Settings › TV Guide; Guide › Options › **Hide
      channels with no guide data** removes the empty rows.

- [ ] **Built-in guides (epgshare01 US + UK)**: on a fresh install, after adding a provider,
      Settings › TV Guide lists "epgshare01 US" and "epgshare01 UK" already assigned to it. After
      the next guide refresh, channels such as "USA News 12 Brooklyn", "USA Animal Planet West"
      and "UK TNT Sports 7" show programmes (about 120 more channels on imaxiptv). Channels that
      already had listings keep the provider's own. "UK AMC" stays empty rather than showing the
      US schedule. Delete a built-in source → it doesn't come back on the next launch.

- [ ] **Setup shows it's busy**: add a provider → after **Login** the button shows a spinner and
      the "Syncing data…" box stays up until the import finishes; pressing Login again does
      nothing. Then Home opens on its own.

## Bugs found while testing

- [x] **Xtream setup gives no feedback** (seen 2026-10-28, emulator, imaxiptv): after **Login** the
      screen stayed on the form with no "Syncing data…" box and Login still pressable, while
      the catalog downloaded in the background. Each extra press saved a new provider edit and
      threw away the download in progress ("Provider edit N was superseded before catalog
      commit"), so it never finished. Fixed 2026-10-28 without finding the root cause: the busy
      state now comes from the sync itself (a provider's first import), and repeat submits are
      ignored while it runs.
- [x] **Guide never downloaded** (2026-10-28, imaxiptv): the full guide waited for Movies *and*
      Series indexing, and the server sends broken JSON for some series categories, so Series
      never finished and the guide was put off forever (only 24 of 2,553 linked channels had
      listings). Now the guide waits for Live TV only. Confirmed 2026-10-29: after the overnight
      download 1,777 channels have listings (of 2,642 with a guide ID, 9,815 total).
- [x] **Series import retried a broken category non-stop** (every ~0.7 s, which also kept the
      provider busy). Now a category that fails the same way twice is skipped, and a pass that
      ends with skipped categories waits for the next scheduled refresh instead of retrying.
- [x] **Guide time header overlaps** at the left edge: "6:00 PM" and "6:30 PM" drew on top of each
      other. A half-hour mark before the guide's start was clamped to the edge; now only marks
      inside the window are drawn (row grid lines use the same marks). Checked on the emulator
      2026-10-29.
- [x] **Guide jumped back to the top while scrolling** (2026-10-29, Scheduled only on): a progress
      bar kept appearing over the channels and focus reset to the first row. Two causes: the grid
      re-scrolled to the start whenever the channel count changed (every page loaded while
      scrolling), and database writes re-emitted identical guide requests that restarted the
      whole guide. Now the grid only resets when the first channel changes (new category/filter)
      and identical requests are ignored. Checked on the emulator: 60 rows down to channel 325
      with no reset and no bar.
- [x] **Continue Watching episode "reloaded Home"** (2026-11-02, Fire TV, 2.0.0): the card opened
      the show's page with focus on **Back to library**, so pressing OK to resume went straight
      back to Home. Now an episode card plays that episode (with the Resume / Start Over prompt),
      and the show's page starts with focus on Resume/Play. Checked on the emulator.
- [x] **Next episode was slow** (2026-11-02): for Xtream shows the player waited to reload the
      whole show's details before starting the next episode, which goes to the server once the
      6-hour cache is stale (Naruto: 488 episodes). It now uses the episode it already has and
      refreshes in the background (Stalker still waits, its links expire). On the emulator the
      new stream started 0.45 s after the press; the rest is the provider's server (~3 s to
      answer) and buffering.
- [ ] **Old scheduler tests fail on Windows**: 4 tests in `ProviderSyncWorkSchedulerIntegrationTest`
      fail with "unable to open database file" on the original code too (test environment issue).

## Upstream StreamVault updates

- [x] **1.2.3 + 1.2.4 fixes and TV features** (2026-11-05, 38 commits cherry-picked with `-x`
      from `upstream/master`, base was `b777a112`): text size setting, D-pad VOD scrubbing,
      dropped-VOD retry, live HLS/MPEG-TS stability, guide refresh retries and 2 GiB XMLTV
      limit, accent-insensitive search, Xtream refresh recovery, Downloads remote fixes and
      launcher route, backup section selection, EPG sources from a phone (QR), M3U catch-up,
      subtitle restore after retries, Brazilian sports, hardware-decoder recovery.
      **Skipped:** Stremio (`3e8c6518`, which also holds upstream's Up Next / Watch Next /
      episode calendar / notifications), Trakt device sign-in (`d5f1eaaf`), torrent natives
      (`a3e48562`).
- Database: V2 already used 88 (tracked shows) and 89 (saved streams), so upstream's FTS
  rebuild became **89→90** and its M3U catch-up columns **90→91**. Upgrade checked on the
  emulator with a real v89 database (10,072 channels and history kept).
- Xtream indexing: upstream stops scheduling more categories after any failure; V2 keeps
  going once a known-broken category was skipped (imaxiptv has several), otherwise the rest of
  the catalog would only advance one batch per refresh.
- To take later upstream releases: `git fetch upstream`, cherry-pick new commits since
  `bacaae79`, renumber any new upstream migration after V2's current version.
- [ ] On device: Settings › text size; D-pad left/right scrubs a movie in 10 s steps.
- [x] **Up Next and Recently Added Episodes** (2026-11-12, built natively instead of upstream's
      Stremio-based versions): two Home rows, both on by default and listed in Settings › Home
      rows. **Up Next**: for each show whose last-watched episode was finished, the next
      episode (shows still mid-episode stay in Continue Watching). **Recently Added Episodes**:
      episodes of Tracked Shows, newest first, "S1 E5 · Added Nov 11, 2026". The date is
      Xtream's `added` field (new `episodes.added_at`, migration 91→92); without it, an episode
      that appears after the show was first loaded is dated when the app first saw it, and the
      back catalogue stays undated so it doesn't flood the row. Checked on the emulator with
      test data (imaxiptv was resetting `get_series_info`, so real `added` dates are unverified).
- [ ] On device: finish an episode → Up Next shows the next one; track a show and wait for a new
      episode → it appears in Recently Added Episodes with today's (or the provider's) date.
- Seen once on the emulator: the first launch right after installing the update showed the
  "Setup Provider" welcome card; a relaunch went to Home with everything intact and a clean
  replay of the upgrade did not show it. If it appears, press Back or relaunch, don't add the
  provider again.

## 2.1.0 features (2026-11-15)

- [x] **Updates from the V2 repo**: the checker reads `release/update.json` (versionName,
      versionCode, downloadUrl, sha256, releaseNotes) from dudsterthegrey/IPTV-V2; updates are on.
      Settings › Updates also shows the Downloader code. Publish a new `update.json` with every APK.
- [x] **Built-in keys**: OpenSubtitles and TMDB keys are build defaults in `app/build.gradle.kts`
      (an env var or `local.properties` overrides; a key entered in Settings still wins).
- [x] **Volume boost**: Settings › Audio › Volume boost cycles Off/+3/+6/+10 dB (LoudnessEnhancer
      on the player's audio session; passthrough audio is decoded by the receiver, so not boosted).
- [x] **Typo-tolerant search**: when the exact search finds fewer than 8 per section, a second pass
      matches words within 1–2 typos, prefixes, and joined words ("spiderman").
- [x] **Skip intro**: episode controls › "Intro ends here" saves the point per show; every episode
      then shows Skip intro (intro chapters in the file still work too).
- [x] **IMDb ratings, genres, anime, Top 10** (TMDB + IMDb's ratings file): `TitleMetadataWorker`
      matches titles on TMDB in the background (series first, animation first; ~1 h for series,
      a few hours for 54k movies; pauses during playback), then downloads IMDb's
      `title.ratings.tsv.gz` weekly. IMDb ratings replace provider ratings in the catalog (SQLite
      triggers keep them after refreshes; under 1,000 votes counts as unrated), so "Top rated",
      rating sorts and cards use IMDb. Ratings always show "/10" now.
- [x] **Genres tab**: fixed genres (Action, Comedy, Anime…) for movies and series, best rated first.
      Anime = Animation in Japanese, or a provider "anime" category.
- [x] **Top 10 Today** Home row: TMDB trending (worldwide) or popular on streaming in a country;
      "Change country" opens a list of countries (2.1.3; it used to cycle, which hid the row while
      loading). Up from a poster goes to that button. Only titles the provider has are shown; if a
      country has none, the row stays with a message so another country can be picked.
- [ ] **Sync across devices** (Settings › Backup): sign in with an email/password account on each
      TV; watch progress (movies, episodes) syncs through Firebase REST (Auth + Firestore under
      `users/{uid}/history`). The project id and Web API key are built in (2.1.1).
      Firebase: enable Email/Password and Google sign-in. Rules live in `firebase/firestore.rules`;
      deploy them and the phone page with `firebase deploy --only firestore:rules,hosting` from
      `firebase/`.
- [ ] **Sign in from a phone** (2.1.1), two QR codes in Settings › Backup while signed out:
      A) "Sign in with your phone": the TV's local server (same Wi-Fi) shows an email/password
      form, with Sign in and Create account; B) "Sign in with Google": the phone opens
      `https://iptv-v2-b0b85.firebaseapp.com/?c=<code>` (firebase/public/index.html), signs in with
      Google and leaves its Google token at `pairings/{code}`; the TV polls that document every 2 s
      for 5 minutes, signs in with `accounts:signInWithIdp`, then deletes it. The phone page is in
      English only.
- [ ] **Home shelf filters** (2.1.5): Recently Added Movies and Recently Updated Series have
      Genre, Rating, Country and Year chips (`CatalogShelfFilterBar.kt`). Genres and countries
      toggle several at a time; changes apply and are saved at once (per shelf, in
      `catalog_insights` prefs). Countries come from TMDB (`title_metadata.countries`, migration
      93->94) in the same request that already fetched the IMDb id; titles matched before 2.1.5
      are filled in by the metadata worker. A filtered shelf stays visible with a message when
      nothing matches.
- [ ] **Profiles per provider** (2.1.4): "Who's watching?" (`app/profiles/ViewerProfilePicker.kt`)
      at app start when the active provider has 2+ profiles, and from the profile button in the top
      bar. Each profile has its own progress and watched marks, favorites and custom groups, tracked
      shows and search history. Done by swapping (`data/profile/ViewerProfileRepositoryImpl.kt`):
      live tables hold only the active profile's rows; the others wait in plain-SQL
      `profile_parked_<table>` copies (not Room entities), swapped in one transaction, then the
      movie/episode progress copies are rebuilt. Cloud sync tags items with the profile (lowercase
      name; "" for the primary profile, which keeps the old document ids) and only fetches the
      active profile's. Not yet: backups only include the active profile; Stremio progress is shared.
- [ ] **Sync button in the top bar** (2.1.2): while signed in to sync, every page with the top
      bar (Home, Live TV, Movies, Series, Genres, Search…) shows a small "Sync" button next to the
      close-app icon (`AppSyncTopBarAction.kt`, fed from `MainActivity` through
      `LocalAppSyncShortcut`). It spins while syncing, then shows "Synced" or "Sync failed" for 4 s.
- [ ] On device: volume boost on TV speakers; Skip intro across episodes; Genres › Anime after the
      lookup finishes; IMDb ratings in Top rated; Top 10 country switch; two-device sync.

## Release / Downloader

- [x] Release signing (2026-10-29): V1's keystore passwords were lost, so V2 has its own key,
      `keystore/iptv-v2-release.jks`, with `keystore.properties` (both gitignored). **Back up
      both files together**: every future version must be signed with this key to install as
      an update. Build with `gradlew :app:assembleRelease`; the APK is copied to
      `release/IPTVPlayer-V2-<version>.apk` (gitignored). Raise `versionCode` for each new upload.
      2.0.0 installed and opened on the emulator; provider login on the release build is not
      checked yet (minified builds can break JSON parsing, so try setup first on the Fire TV).
- [x] Host the APK (2026-10-29): committed to `release/` in the public repo
      github.com/dudsterthegrey/IPTV-V2. Direct link for Downloader:
      `https://github.com/dudsterthegrey/IPTV-V2/raw/main/release/IPTVPlayer-V2-2.0.0.apk`
      (checked: downloads without signing in, same SHA-256 as the local build).
- [x] Downloader code **7935478** (short URL aftv.news/7935478, info page aftv.news/7935478+),
      created 2026-10-30 and checked to point at the link above. For later versions, either keep
      the file name and replace the APK (the code keeps working) or edit the code's link.
- [x] Install with the code on the Fire TV and log in to the provider (done by the user,
      2026-11-02, on 2.0.0).
- To reproduce a release-only bug on the emulator, build `:app:assembleMinifiedQa`: R8-shrunk
  like release but with the debug app's ID and key, so it installs over the debug build and
  keeps its provider and history. Reinstall the normal debug build afterwards.
- Updates: raise `versionCode`, build the release, and copy it over
  `release/IPTVPlayer-V2-2.0.0.apk` (the Downloader code points at that file name; the name
  stays 2.0.0, Settings › About shows the real version).
- [ ] Optional: point the in-app updater (`APP_UPDATES_ENABLED`) at your own release feed

## Rules for every port

- All on-screen text goes in `strings.xml` and must be translated for every locale in
  `app/src/main/res/xml/locales_config.xml` (see `AGENTS.md`).
- Keep the About screen credit to StreamVault / David Nashash and its links (license requirement).
