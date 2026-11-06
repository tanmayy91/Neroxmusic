<div align="center">
  <img src="assets/Nexamusic-banner.png" width="1000" alt="NexaMusic banner">
  <h1>NexaMusic</h1>
  <p><strong>An open-source Android music player with a calm, glass-inspired interface.</strong></p>
  <p>
    <a href="https://github.com/tanmayy91/NexaMusic/releases">
      <img src="https://img.shields.io/github/v/release/tanmayy91/NexaMusic?include_prereleases&display_name=tag&style=for-the-badge&logo=android&logoColor=white" alt="View the latest NexaMusic release">
    </a>
    <a href="https://github.com/tanmayy91/NexaMusic/releases">
      <img src="https://img.shields.io/github/downloads/tanmayy91/NexaMusic/total?label=Downloads&style=for-the-badge&logo=github&logoColor=white" alt="Total NexaMusic release asset downloads">
    </a>
  </p>
  <p>
    <a href="https://discord.gg/D8fYQKUEzw">
      <img src="https://img.shields.io/badge/Discord-Join%20the%20Community-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Join the NexaMusic Discord community">
    </a>
  </p>
  <p>
    <img src="https://img.shields.io/badge/License-GPL--3.0-blue?style=flat" alt="GPL-3.0 license">
    <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat&logo=android&logoColor=white" alt="Android 8.0 and later">
  </p>
</div>

NexaMusic brings music discovery, playback, lyrics, and your library together in one Android app. It is built with Kotlin, Jetpack Compose, and Media3, with custom Cupertino-style controls and Liquid Glass surfaces.

## Features

### Playback

- Browse and stream music through NexaMusic's InnerTube client.
- Keep listening in the background with notification, lock-screen, and Android Auto controls.
- Save music for offline listening and manage a local library.
- Build queues and playlists, set a sleep timer, change playback speed, and use the equalizer.
- Use crossfade, Automix, and DJ-style transitions.

### Lyrics and artwork

- Follow synchronized lyrics with word-level highlighting.
- Explore animated artwork and album-inspired colors.
- Browse lyrics from multiple providers, including an opt-in AMLL TTML Database source for word-timed lyrics (disabled by default; coverage varies by track).

### Library and integrations

- Browse local music and network shares, including SMB and WebDAV.
- Import playlists from Spotify.
- Connect Discord Rich Presence, Last.fm, music recognition, and Listen Together.

### NexaMusic interface

- Frosted, refractive glass surfaces for menus, sheets, and floating controls.
- Cupertino-style controls built for NexaMusic's interface and Android interaction patterns.
- Adaptive themes and layouts for phones and wider screens.

## Screenshots

<div align="center">
  <img src="https://raw.githubusercontent.com/tanmayy91/NexaMusic/main/screenshots/HomeUI.png" width="30%" alt="NexaMusic Home screen">
  <img src="https://raw.githubusercontent.com/tanmayy91/NexaMusic/main/screenshots/AlbumUI.png" width="30%" alt="NexaMusic Album screen">
  <img src="https://raw.githubusercontent.com/tanmayy91/NexaMusic/main/screenshots/PlayingUI.png" width="30%" alt="NexaMusic Now Playing screen">
</div>

## Download NexaMusic

**[View all NexaMusic releases and download the latest APK](https://github.com/tanmayy91/NexaMusic/releases)**

Open the Releases page, choose the newest version, and download the APK from its **Assets** section.

The download badge above shows the total number of release-asset downloads reported by GitHub. It counts downloads, not unique people.

NexaMusic is intended to support Android 8.0 (API 26) and later. Install APKs only from sources you trust. Android may ask you to allow installation from the app you used to download an APK.

## Project structure

NexaMusic is a Gradle multi-module Android project. Keep code in the module that owns the capability, and use its existing public APIs when another module needs it.

| Path | Responsibility |
| --- | --- |
| `app/` | Android application, screens, ViewModels, navigation, playback service, persistence, and app wiring. |
| `hig/` | Shared Cupertino-style controls, theme primitives, accessibility helpers, and glass utilities. |
| `innertube/` | InnerTube client and related models. |
| `lyrics/` | Lyrics providers, parsing, and lyrics support. |
| `canvas/` | Animated canvas and catalog features. |
| `artwork/` | Artwork loading, caching, and transitions. |
| `spotify/` | Spotify authentication and playlist import. |
| `integrations/` | Discord, Last.fm, music recognition, and related integrations. |
| `network-storage/` | SMB and WebDAV music sources. |
| `spine/` | Add-on module hosting and lifecycle. |
| `listen-together-server/` | Server component for Listen Together. |

The Android app uses Jetpack Compose for UI, Media3 for playback, Room for the local library, and DataStore for preferences. App modules are wired through the existing Hilt setup. Module names and available features may vary depending on the fork's current code.

## Build locally

### Requirements

- JDK 21
- Android Studio with Android SDK 37 installed
- Android NDK for native components

Clone the repository, open it in Android Studio, and let Gradle sync. To build a debug APK or run unit tests from a terminal, use the tasks supported by the current checkout. For a NexaMusic checkout, these may be:

```bash
./gradlew :app:assembleUniversalFossDebug
./gradlew :app:testUniversalFossDebugUnitTest
```

On Windows, use `gradlew.bat` with the same task names. Confirm that these tasks and build variants exist in the current repository before running them. The `Foss` build omits Google Play services; the `Gms` build includes Google Cast support if those variants are present.

## Contributing

Bug reports, fixes, and feature contributions are welcome. Please check the repository's contributing guide, code of conduct, and security policy if these files are available in the current checkout.

AI coding agents should follow any agent contribution instructions provided in the repository.

## Community and support

- [Join the NexaMusic Discord community](https://discord.gg/D8fYQKUEzw)

## Legal

NexaMusic is an independent project and is not affiliated with, endorsed by, or connected to YouTube, Google, or their affiliates. Some features may use unofficial third-party interfaces that can change or stop working. Users are responsible for following applicable laws and service terms.

NexaMusic is intended to be distributed under the GNU General Public License v3.0, subject to the license and attribution files included in the repository. See `LICENSE` and `NOTICE` if present.

---

Maintained by _**Smelokuhle Mahungela**_
