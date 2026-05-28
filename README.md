<div align="center">

```
    ___    ________________  __________
   /   |  / ____/_  __/ / / / ____/ __ \
  / /| | / __/   / / / /_/ / __/ / /_/ /
 / ___ |/ /___  / / / __  / /___/ _, _/
/_/  |_/_____/ /_/ /_/ /_/_____/_/ |_|
```

**Audio Engine for Total Harmonic Experience & Rendering**

![v2.1](https://img.shields.io/badge/v2.1-MAY%202026-9D4EDD?style=for-the-badge&labelColor=000000)
![Electron](https://img.shields.io/badge/Electron-Desktop-47848F?style=for-the-badge&logo=electron&logoColor=white)
![Capacitor](https://img.shields.io/badge/Capacitor-Android-119EFF?style=for-the-badge&logo=capacitor&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Media3-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Android Auto](https://img.shields.io/badge/Android-Auto-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Subsonic](https://img.shields.io/badge/Subsonic-API-FF6B00?style=for-the-badge)
![License](https://img.shields.io/badge/License-MIT-00FF41?style=for-the-badge)

**Your music. Your server. Your visualizers. Bit-perfect.**

A premium Navidrome/Subsonic client for desktop *and* Android — 23 beat-reactive OLED visualizers, 5 swappable color skins, AutoEq headphone correction, ReplayGain, gapless playback, Bauer crossfeed, Android Auto with a Tron-style HUD map. Single-file architecture, zero frameworks.

[**Website**](https://emperorbadussy.github.io/aether/) · [**Releases**](https://github.com/EmperorBadussy/aether-android/releases) · [**Skins**](#5-oled-skins) · [**Visualizers**](#23-beat-reactive-visualizers)

</div>

---

## What's New in v2.1

- **Master gain control** — global output trim, +2.6 dB default, slider-tunable
- **5 OLED skins** with live preview — Void, Bloodmoon, Glacier, Toxic, Phantom (Ctrl+Shift+T cycles)
- **AutoEq headphone correction** — 5 built-in presets + paste-your-own ParametricEQ.txt
- **ReplayGain** — track/album modes, peak-clipping protection
- **Bauer-style crossfeed** — tunable intensity, kills planar headphone fatigue
- **Gapless playback** — pre-warms next track 8s before end, near-instant transitions
- **Mini Player mode** — Ctrl+M shrinks to 380×150 floating always-on-top window
- **PURE bypass** — single-button A/B against the entire DSP chain
- **4 new visualizers** — Event Horizon, Mercury, Prism, Attractor
- **Hacker Terminal LRC sync** — terminal feed types out actual synced lyric lines from LRCLIB on time, with progress bar + track HUD
- **Galaxy S26 Ultra polish** — edge-to-edge, punch-hole-aware, 120Hz-tuned, proper safe-area handling

---

## Music Streaming

- **Navidrome / Subsonic API** — stream your entire library from your own server
- **`format=raw` + `maxBitRate=0`** — no transcoding, your FLACs stay FLAC
- **Tailscale / VPN ready** — same client works on LAN or off-network
- **Offline fallback** — local Android MediaStore returned in Subsonic-shaped JSON
- **FLAC, MP3, OGG, AAC, WAV, OPUS** decoded natively

## Audio Engine

```
audio → MediaElementSource → eqFilters[0..9] → analyser → masterGain → destination
                                       ↑                          ↑
                            AutoEq (on-demand)         Crossfeed (on-demand)
                                       ↓
                              ReplayGain (lazy)
```

- **10-band parametric EQ** — 11 presets including Planar Reference, Harman, Diffuse Field, Vocal Forward
- **AutoEq** — load any headphone's measured PEQ correction from `github.com/jaakkopasanen/AutoEq`
- **Bauer crossfeed** — channel-split → lowpass at 700 Hz → 0.27 ms ITD → merge
- **ReplayGain** — parses Subsonic's `replayGain` field, applies pre-DSP with clipping protection
- **Master gain** — final-stage output trim, -6 to +8 dB range
- **PURE bypass** — direct source → destination, skipping every DSP node
- **Output device picker** — `setSinkId` routes AETHER to a specific DAC
- **AudioContext sink pinning** — auto-recovers from stale sink references

## 5 OLED Skins

| Skin | Palette | Character |
|------|---------|-----------|
| **VOID** | Violet / Cyan / Magenta | Default cyberpunk |
| **BLOODMOON** | Crimson / Amber / Molten Orange | Ember-flicker logo, warm radial vignette |
| **GLACIER** | Ice Cyan / Aqua / Silver | Minimalist, sharp 4px radii, dialed-back glow |
| **TOXIC** | Acid Green / Lime / Yellow | Matrix CRT scanlines + chromatic aberration |
| **PHANTOM** | Monochrome White-on-Black | Zero glow, replaced with hairline borders |

Each skin re-maps every accent variable (primary, secondary, tertiary, glow shadows, selection color, surface tints). Live-preview by hovering tile. Click commits. Settings → DISPLAY → SKIN.

## 23 Beat-Reactive Visualizers

Every visualizer is designed for AMOLED — true blacks, vivid neon, maximum contrast.

| # | Visualizer | Description |
|---|------------|-------------|
| 1 | **Fractal Flame** | IFS chaos game with 30K+ iterations per frame |
| 2 | **Void Pulse** | Pulsing core with spiral arms and expanding rings |
| 3 | **Tron Grid** | Outrun landscape with planet and perspective grid |
| 4 | **Particle Field** | 400 particles in orbital spiral galaxy |
| 5 | **Waveform Bars** | Circular frequency EQ with mirror bars |
| 6 | **Aurora Borealis** | 5-layer curtains with 200 twinkling stars |
| 7 | **Cosmic Mandala** | Sacred geometry — nested rings of polygons |
| 8 | **Electric Sheep** | Audio-reactive video overlay (load your own fractals) |
| 9 | **Bio-Genesis** | Bioluminescent organisms with mitochondria and flagella |
| 10 | **Command Deck** | HUD dashboard with frequency gauges and oscilloscope |
| 11 | **Neural Web II** | 80 interconnected nodes with nebula depth background |
| 12 | **DNA Helix II** | Double helix with ambient particles and depth sorting |
| 13 | **Lyric Rain** | Matrix rain that reveals actual song lyrics (LRCLIB API) |
| 14 | **Frequency Mountain** | Synthwave terrain from live FFT — sun, grid, particles |
| 15 | **Chrome Ocean** | Mercury wave grid + horizon glow |
| 16 | **Hyperspace Tunnel** | Perspective ring tunnel with warp particles |
| 17 | **Plasma Reactor** | Containment field with vorticity |
| 18 | **Xenomorph Hive** | Organic biomechanical clusters |
| 19 | **Hacker Terminal** | CRT terminal with **live LRC-sync lyrics**, track HUD, progress bar |
| 20 | **Event Horizon** ⭐ | Black hole with counter-rotating accretion disks + gravitational lensing |
| 21 | **Mercury** ⭐ | Reactive liquid metal pool with floating glow orbs and light shafts |
| 22 | **Prism** ⭐ | Rotating iridescent triangle with 64-bar radial spectrum dispersion |
| 23 | **Attractor** ⭐ | Clifford strange attractor — audio modulates the ODE parameters |

⭐ = new in v2.1

## Android

- **Capacitor** WebView shell wrapping the same `player.html`
- **Media3 MediaLibraryService** with `MediaSession` for lock-screen + notification controls
- **Local library plugin** — Android MediaStore queries returned in Subsonic-shaped JSON (UI doesn't know if it's local or remote)
- **Android Auto** — Car App Service with **Tron-style GPS map**: perspective grid, speed display, now-playing overlay
- **Edge-to-edge** — content extends behind punch-hole + nav bar, status bar transparent over OLED black
- **Adaptive FPS** — 60 charging, 30 battery, 15 low-battery, stops when screen off
- **Galaxy S26 Ultra-tuned** — 2-column skin picker, chunky 44px tap targets, 120 Hz-friendly `will-change`

## Build

### Desktop (Electron)

```bash
npm install
npm start                  # dev
npm run dist               # build Windows NSIS installer
```

### Android (Capacitor + Kotlin)

```bash
cd mobile
./build-www.sh             # copy player.html → www, inject mobile bridge
npx cap sync android       # push to Capacitor project
cd android && ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Requires JDK 17 + Android SDK 34+.

## Stack

| Layer | Tech |
|-------|------|
| **Desktop shell** | Electron 33 (frameless, OLED-black, tray) |
| **Mobile shell** | Capacitor 8 (WebView, native MediaSession bridge) |
| **Android native** | Kotlin + Media3 + ExoPlayer + Car App Service |
| **UI** | Single 21K-line `player.html` — zero frameworks, zero npm runtime deps |
| **Audio** | Web Audio API — BiquadFilter chain, DynamicsCompressor, GainNode pipeline |
| **Fonts** | Orbitron + Rajdhani + JetBrains Mono |
| **Lyrics** | LRCLIB (free, no key) → Subsonic getLyrics fallback |
| **Library** | Subsonic API + Android MediaStore (parallel paths, same shape) |
| **Persistence** | localStorage for all user state (skins, EQ, AutoEq, gain, sink, etc.) |

## License

MIT
