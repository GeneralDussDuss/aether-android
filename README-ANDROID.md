```
    ___    ________________  __________
   /   |  / ____/_  __/ / / / ____/ __ \
  / /| | / __/   / / / /_/ / __/ / /_/ /
 / ___ |/ /___  / / / __  / /___/ _, _/
/_/  |_/_____/ /_/ /_/ /_/_____/_/ |_|
                        for Android
```

<p align="center">
  <img src="https://img.shields.io/badge/v1.0.0-ANDROID-9D4EDD?style=for-the-badge&labelColor=000000" alt="v1.0.0" />
  <img src="https://img.shields.io/badge/Capacitor-8-119EFF?style=for-the-badge&logo=capacitor&logoColor=white" alt="Capacitor 8" />
  <img src="https://img.shields.io/badge/Kotlin-Media3-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android" />
  <img src="https://img.shields.io/badge/Subsonic-API-FF6B00?style=for-the-badge" alt="Subsonic" />
  <img src="https://img.shields.io/badge/License-MIT-00FF41?style=for-the-badge" alt="MIT" />
</p>

<p align="center">
  <strong>Your music. Your server. Your visualizers. In your pocket.</strong><br/>
  A premium music player for Android that streams from Navidrome/Subsonic with beat-reactive OLED visualizers, 10-band EQ, and Android Auto support.
</p>

<p align="center">
  <em>OLED-optimized. Beat-reactive. Zero compromise.</em>
</p>

---

## Features

### Music Streaming
- **Navidrome / Subsonic API** — stream your entire library from your own server
- **Tailscale / VPN support** — access your home library from anywhere
- **Offline fallback** — local device library via Android MediaStore when server unreachable
- **FLAC, MP3, OGG, AAC, WAV, OPUS** format support
- **Gapless playback** with crossfade

### 14 Beat-Reactive OLED Visualizers
Every visualizer is designed for AMOLED — true blacks, vivid neon, maximum contrast.

| Visualizer | Description |
|-----------|-------------|
| **Fractal Flame** | IFS fractal algorithm with 30K+ iterations per frame |
| **Void Pulse** | Pulsing core with spiral arms and expanding rings |
| **Tron Grid** | Outrun landscape with planet and perspective grid |
| **Particle Field** | 400 particles in orbital spiral galaxy |
| **Waveform Bars** | Circular frequency EQ with mirror bars |
| **Aurora Borealis** | 5-layer curtains with 200 twinkling stars |
| **Cosmic Mandala** | Sacred geometry — nested rings of polygons |
| **Electric Sheep** | Audio-reactive video overlay (load your own fractals) |
| **Bio-Genesis** | Bioluminescent organisms with mitochondria and flagella |
| **Command Deck** | HUD dashboard with frequency gauges and oscilloscope |
| **Neural Web II** | 80 interconnected nodes with nebula depth background |
| **DNA Helix II** | Double helix with ambient particles and depth sorting |
| **Lyric Rain** | Matrix rain that reveals actual song lyrics (LRCLIB API) |
| **Frequency Mountain** | Synthwave terrain from live FFT — sun, grid, particles |

### Audio Engine
- **10-band parametric EQ** with 10 presets
- **Web Audio API** pipeline: MediaElement → EQ → Compressor → Destination
- **Beat detection** via FFT frequency analysis
- **Master limiter** (DynamicsCompressor prevents clipping)

### Android Auto
- **Media3 MediaLibraryService** — browse Albums, Artists, Playlists, Genres, Recently Added, Random
- **ExoPlayer** native playback for car head unit
- **Tron GPS Map** — cyberpunk navigation HUD with perspective grid, speed display, now-playing overlay
- **Album art** in media notifications

### Mobile Optimizations
- **Adaptive FPS** — 60fps charging, 30fps battery, 15fps low battery, stops when screen off
- **OLED black** backgrounds everywhere (black pixels = off = battery saved)
- **Connection monitor** — Navidrome status indicator, auto-recovery on reconnect
- **Background audio** — keeps playing when screen off or app backgrounded
- **Lock screen controls** — MediaSession API with album art, play/pause/next/prev
- **Swipe gestures** — left/right on now-playing bar for next/prev
- **Touch-optimized** — 48dp minimum touch targets, no hover states

### Interface
- **OLED-optimized** — true #000000 black, AMOLED sweet spot
- **Monochromatic purple palette** — neon purple (#9D4EDD) + cyan (#00DCFF) accents
- **Glassmorphism** — frosted glass panels with backdrop-filter blur
- **Slide-out sidebar** — overlay navigation, not permanent column
- **Bottom-sheet panels** — EQ, queue, settings as mobile-friendly bottom sheets
- **Floating hamburger** — quick access to library navigation

---

## Architecture

```
┌─────────────────────────────────────┐
│  Android (Capacitor 8 WebView)      │
│  ┌───────────────────────────────┐  │
│  │ player.html (17K lines)       │  │  ← The App
│  │ + mobile-bridge.js            │  │  ← Mobile adaptations
│  │ + mobile-tweaks.css (1.3K)    │  │  ← Premium mobile UI
│  └───────────────────────────────┘  │
│  ┌───────────────────────────────┐  │
│  │ Kotlin Native Layer           │  │
│  │ • AetherMediaService (Media3) │  │  ← Android Auto
│  │ • SubsonicClient              │  │  ← REST API
│  │ • AetherMapScreen             │  │  ← Tron GPS HUD
│  │ • AetherLocalLibraryPlugin    │  │  ← Offline fallback
│  │ • AetherFileServer            │  │  ← Local audio serving
│  │ • AetherCredentialPlugin      │  │  ← WebView ↔ Native bridge
│  └───────────────────────────────┘  │
└──────────────┬──────────────────────┘
               │ Subsonic API (REST)
┌──────────────▼──────────────────────┐
│  Navidrome Server                   │
│  (your music, your server)          │
└─────────────────────────────────────┘
```

---

## Getting Started

### Prerequisites
- **Android device** (API 26+ / Android 8.0+, optimized for Galaxy S-series AMOLED)
- **Navidrome server** running on your network ([navidrome.org](https://www.navidrome.org/))
- **Node.js** 18+ and npm (for building)
- **Android Studio** with SDK 35+ (for APK builds)
- **JDK 17** for Gradle

### Quick Start

```bash
# Clone
git clone https://github.com/EmperorBadussy/aether-android.git
cd aether-android

# Configure your Navidrome server
# Edit player.html line ~4387:
#   baseUrl: 'http://your-server-ip:4533'
#   user: 'your_username'
#   password: 'your_password'

# Build mobile web assets
cd mobile
bash build-www.sh

# Sync to Android
npx cap sync android

# Build APK
cd android
./gradlew assembleDebug

# Install
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Remote Access (Tailscale)
For accessing your music library outside your home network:
1. Install [Tailscale](https://tailscale.com/) on your server and phone
2. Use your server's Tailscale IP as the `baseUrl` (e.g., `http://100.x.x.x:4533`)

---

## Project Structure

```
aether-android/
├── player.html                          # THE APP (17K lines)
├── main.js                              # Electron main (desktop)
├── preload.js                           # Electron preload (desktop)
├── mobile/
│   ├── build-www.sh                     # Build pipeline
│   ├── capacitor.config.ts              # Capacitor config
│   ├── www/
│   │   ├── index.html                   # Transformed player.html
│   │   ├── mobile-bridge.js             # Swipe, MediaSession, offline, adaptive viz
│   │   └── mobile-tweaks.css            # 1,313 lines of premium mobile CSS
│   └── android/
│       └── app/src/main/java/com/aether/player/
│           ├── SubsonicClient.kt        # REST client
│           ├── AetherMediaService.kt    # Media3 service (Android Auto)
│           ├── AetherCredentialPlugin.kt # WebView ↔ native bridge
│           ├── AetherLocalLibraryPlugin.kt # MediaStore fallback
│           ├── AetherFileServer.kt      # Local file serving
│           ├── AetherCarAppService.kt   # Android Auto entry
│           ├── AetherNavigationSession.kt # Auto session
│           ├── AetherMapScreen.kt       # Tron GPS HUD (496 lines)
│           └── TronMapStyle.kt          # Google Maps styling
```

---

## Visualizer Gallery

All 14 scenes are OLED-optimized with true black backgrounds and vivid neon accents. Tap the visualizer panel to cycle through scenes.

| | | |
|:---:|:---:|:---:|
| Fractal Flame | Void Pulse | Tron Grid |
| Waveform Bars | Aurora Borealis | Cosmic Mandala |
| Bio-Genesis | Command Deck | Lyric Rain |
| Neural Web II | DNA Helix II | Frequency Mountain |

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **Runtime** | Capacitor 8 WebView |
| **UI** | Vanilla HTML/CSS/JS (17K lines, no frameworks) |
| **Audio** | Web Audio API + MediaElement pipeline |
| **Visualizers** | Canvas 2D, 14 scenes, 30-60fps adaptive |
| **Native** | Kotlin, Media3 ExoPlayer, Car App Library |
| **Maps** | Google Maps SDK + custom Tron styling |
| **Backend** | Navidrome (Subsonic API v1.16.1) |
| **Build** | Gradle + Capacitor CLI |

---

## Legal

```
This software is for personal use with your own music library.
Requires a self-hosted Navidrome or Subsonic-compatible server.
No music is included or distributed with this application.
```

---

## License

MIT License. See [LICENSE](LICENSE) for details.

---

## Credits

Built with **[Claude Code](https://claude.ai/claude-code)** — AI-assisted development from Anthropic.

Part of the **PHANTOM Suite** ecosystem.

---

<p align="center">
  <sub>OLED-optimized. Beat-reactive. Zero compromise.</sub>
</p>
