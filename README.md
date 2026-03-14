<p align="center">
  <img src="https://img.shields.io/badge/AETHER-v2.0.0-7B2FBE?style=for-the-badge&labelColor=000000" alt="Version"/>
  <img src="https://img.shields.io/badge/Electron-33-9D4EDD?style=for-the-badge&logo=electron&logoColor=EED4FF&labelColor=000000" alt="Electron"/>
  <img src="https://img.shields.io/badge/Platform-Windows-B76EFF?style=for-the-badge&logo=windows&logoColor=EED4FF&labelColor=000000" alt="Platform"/>
  <img src="https://img.shields.io/badge/License-MIT-D4A0FF?style=for-the-badge&labelColor=000000" alt="License"/>
</p>

<h1 align="center">
  <br/>
  <code>[ AETHER ]</code>
  <br/>
  <sub>Audio Engine for Total Harmonic Experience & Rendering</sub>
  <br/>
</h1>

<p align="center">
  <em>Not a music player — an experience.</em>
</p>

<p align="center">
  <strong>OLED-black. Tron-purple. 17 generative visualizers. AI DJ engine with 9 transitions. Stem isolation. 3D sonic map. Binaural brainwave engine. 140+ features in a single-file Electron app.</strong>
</p>

<p align="center">
  <a href="https://github.com/EmperorBadussy/charon">
    <img src="https://img.shields.io/badge/Companion-CHARON-06B6D4?style=flat-square&labelColor=000000" alt="CHARON"/>
  </a>
  <a href="https://emperorbadussy.github.io/aether/">
    <img src="https://img.shields.io/badge/Website-Live-9D4EDD?style=flat-square&labelColor=000000" alt="Website"/>
  </a>
</p>

---

<p align="center">
  <video src="demo.mp4" autoplay loop muted playsinline controls width="100%"></video>
</p>

---

<br/>

## `> WHAT IS THIS`

AETHER is a desktop music player built from scratch for people who think Spotify's UI peaked in 2014 and everything since has been a lateral move into beige. It connects to your self-hosted **Navidrome/Subsonic** server, streams your own library, and wraps it in an interface designed for OLED screens with a strict monochromatic purple palette on true black.

Every pixel is intentional. Every animation is buttery. Every visualizer is a canvas-rendered generative art piece that reacts to your music in real time.

**16,000+ lines. Single file. Zero frameworks.**

<br/>

## `> FEATURE MAP`

```
 AI DJ ENGINE
  ├─ Dual-deck mixing with 9 transition patterns
  ├─ Essentia.js WASM: float-precision BPM, key detection, danceability
  ├─ Beat-phase alignment with micro-drift correction
  ├─ Camelot wheel harmonic mixing
  ├─ Spectral compatibility scoring (real DFT, 16-band)
  ├─ Vocal clash detection via structure mapping
  ├─ Set journey planning (warmup → building → peak → cooldown → finale)
  ├─ Context-aware transition selection (energy, key, phrase, history)
  ├─ Smart track selection with user behavior learning
  ├─ Master limiter (DynamicsCompressor prevents clipping)
  ├─ Background queue pre-analysis
  └─ 9 transitions: blend, cut, bass swap, echo out, loop tease,
     double drop, backspin, filter sweep, power down

 AUDIO PROCESSING
  ├─ 10-band parametric EQ with 10 presets
  ├─ Vinyl/Lo-Fi warmth engine (noise, hiss, flutter, saturation, rolloff)
  │   └─ 6 presets: Clean, Warm Vinyl, Dusty Record, Cassette, AM Radio, Lo-Fi
  ├─ Stem isolation (vocals/drums/bass/other with solo/mute/volume)
  │   └─ Presets: Full Mix, Karaoke, Instrumental, A Capella, Drums Only
  ├─ Signal path transparency display (real-time processing chain)
  ├─ Hearing profile EQ (built-in audiometric test + compensation)
  ├─ Binaural brainwave engine (see below)
  ├─ Gapless playback with crossfade
  ├─ Beat detection (FFT analysis, band extraction)
  └─ Format support: FLAC, MP3, OGG, AAC, WAV, OPUS

 DISCOVERY & INTELLIGENCE
  ├─ Magic Playlist — type a vibe, get a playlist (NLP keyword matching)
  ├─ AI Auto-Tagger — energy/mood/tempo/danceability/vocal tags from analysis
  ├─ Rediscover Mode — surfaces forgotten favorites (high plays, months ago)
  ├─ Daylist — time-of-day adaptive playlists (7 periods, auto-curated)
  ├─ 3D Sonic Similarity Map — interactive nebula visualization of your library
  │   └─ Energy × BPM × Danceability axes, Camelot key → color
  │   └─ Starfield, nebula clouds, constellation lines, orbit/zoom
  │   └─ Currently playing track pulses, DJ mix path traced in 3D
  ├─ Music DNA Fingerprint — unique circular artwork per track, exportable PNG
  └─ Taste Evolution Timeline — session history with sparklines and vibe classification

 WELLNESS & PRODUCTIVITY
  ├─ Sleep Timer — intelligent fade with Delta binaural crossfade
  ├─ Pomodoro Focus Mode — work/break cycles with Alpha waves + UI dimming
  └─ Weather Ambient — rain/wind/thunder/birds/crickets synthesized from wttr.in

 VISUALIZERS (17 scenes)
  ├─ All Canvas 2D, all 60fps, all beat-reactive
  ├─ Fractal Flame, Void Pulse, Tron Grid, Particle Field, Waveform Bars
  ├─ Plasma Warp, Neural Web, Aurora Borealis, DNA Helix, Cosmic Mandala
  ├─ Electric Sheep (video), Bio-Genesis, Command Deck, Void Pulse II
  ├─ Neural Web II, DNA Helix II, Lyric Rain (LRCLIB lyrics on beats)
  └─ Auto-rotate or pin your favorite

 INTEGRATION
  ├─ Navidrome/Subsonic API (full library streaming)
  ├─ Discord Rich Presence (local IPC pipe, zero dependencies)
  ├─ LRCLIB lyrics (auto-fetch with search fallback)
  ├─ Wallpaper Engine backgrounds (Steam workshop scanner)
  ├─ Electric Sheep video mode
  └─ Essentia.js WASM audio analysis

 LIBRARY
  ├─ Albums, Artists, Genres, Playlists, Search
  ├─ 100k+ track support (paginated loading)
  ├─ Queue panel with track management
  ├─ Shuffle (no-repeat algorithm)
  ├─ Repeat modes (off / all / one)
  ├─ Waveform seek bar (SoundCloud-style)
  └─ Context menus on tracks

 INTERFACE
  ├─ OLED-optimized (true #000000 black)
  ├─ Monochromatic purple palette (12 shades)
  ├─ Glassmorphism overlays
  ├─ Collapsible sidebar
  ├─ Horizontal / Vertical layout modes
  ├─ Full keyboard shortcut system (30+ shortcuts)
  ├─ Toast notifications
  └─ Custom ultra-thin scrollbars
```

<br/>

## `> RHYTHMIC BINAURAL BRAINWAVE LAYERING ENGINE`

**AETHER isn't just a music player — it's a neural entrainment system.**

Built-in binaural beats generator using Web Audio API oscillators. Two sine waves play at slightly different frequencies in each ear — your brain perceives the difference as a "beat" that entrains neural oscillations to specific brainwave states.

### Single Tone Mode

| Preset | Beat Frequency | Base Frequency | Brain State |
|--------|---------------|----------------|-------------|
| **Delta** | 2 Hz | 150 Hz | Deep sleep |
| **Theta** | 6 Hz | 200 Hz | Meditation |
| **Alpha** | 10 Hz | 200 Hz | Relaxation / Focus |
| **Beta** | 20 Hz | 250 Hz | Alertness |
| **Gamma** | 40 Hz | 300 Hz | Peak cognition |

### Rhythm Layer Mode

**Stack multiple binaural brainwave frequencies, each pulsing at a different rhythmic division.**

Delta droning on whole notes. Theta pulsing quarter notes. Gamma flickering on sixteenths. All synchronized to a master BPM clock with sample-accurate scheduling.

```
 RHYTHM ENGINE
  ├─ Lookahead scheduler (sample-accurate Web Audio timing)
  ├─ Per-layer gain envelope (attack → sustain → release)
  ├─ 7 rhythmic divisions (whole, half, quarter, eighth, triplet, 6-tuplet, sixteenth)
  ├─ 5 presets (Deep Meditation, Focus Flow, Lucid Dream, Peak Performance, Shamanic Journey)
  ├─ Independent volume per layer
  ├─ Live parameter changes (no restart needed)
  └─ Mix with music mode (layer binaural over playback)
```

<br/>

## `> AI DJ ENGINE`

Not a crossfader with a timer. A real mixing engine.

```
 ANALYSIS
  ├─ Essentia.js WASM: float-precision BPM (not rounded integers)
  ├─ Key detection + Camelot wheel mapping
  ├─ Danceability scoring
  ├─ 16-band spectral profiling (real DFT with Hann windowing)
  ├─ Structure mapping (intro/buildup/drop/breakdown/outro detection)
  ├─ Vocal section detection via zero-crossing rate
  ├─ Audio buffer caching (no triple-download)
  └─ Background queue pre-analysis

 MIXING INTELLIGENCE
  ├─ Beat-phase alignment (modular arithmetic on beat intervals)
  ├─ Micro-drift correction during all overlapping transitions
  ├─ Spectral compatibility scoring (frequency clash detection)
  ├─ Vocal clash avoidance
  ├─ Set journey: warmup → building → peak → cooldown → finale
  ├─ Energy arc management across last 3 mixes
  ├─ Context-aware transition selection (phrase, energy, key, history)
  ├─ Pattern variety enforcement (tracks usage over last 5 mixes)
  └─ User behavior learning (skip = negative, full play = positive)

 9 TRANSITION PATTERNS
  ├─ Blend — S-curve crossfade with progressive EQ handoff
  ├─ Cut — instant beat-locked switch
  ├─ Bass Swap — low-end handoff on the downbeat
  ├─ Echo Out — delay/feedback chain with high-pass echo tail
  ├─ Loop Tease — tease incoming twice, then commit
  ├─ Double Drop — align both tracks' drops to hit simultaneously
  ├─ Backspin — vinyl backspin simulation with pitch slowdown
  ├─ Filter Sweep — LP filter 20kHz→200Hz with resonance
  └─ Power Down — turntable power-loss (pitch drop + slowdown)
```

<br/>

## `> 3D SONIC SIMILARITY MAP`

Your entire music library visualized as an interactive 3D space.

- **X-axis**: Energy (0-1)
- **Y-axis**: BPM (60-200)
- **Z-axis**: Danceability (0-1)
- **Color**: Camelot key → hue (12 keys mapped to color wheel)
- **Size**: Danceability (bigger = more danceable)

Drag to orbit, scroll to zoom. Twinkling starfield background. Nebula clouds shift as you rotate. Tracks with matching keys form "constellations" connected by faint colored lines. Currently playing track pulses with animated glow. DJ mix history traced as cyan paths through 3D space.

<br/>

## `> TECH STACK`

| Layer | Technology |
|-------|-----------|
| Runtime | Electron 33 |
| Renderer | Vanilla HTML/CSS/JS (single file, 16,000+ lines) |
| Audio | Web Audio API (AudioContext, AnalyserNode, BiquadFilter, WaveShaper, DynamicsCompressor, ConvolverNode, DelayNode) |
| Audio Analysis | Essentia.js WASM (BPM, key, danceability, onset detection) |
| Graphics | Canvas 2D (requestAnimationFrame, perspective projection) |
| Backend | Navidrome (Subsonic API v1.16.1) |
| Lyrics | LRCLIB API (free, no key required) |
| Weather | wttr.in API (no key required) |
| Discord | Local IPC named pipe protocol (zero npm dependencies) |
| Auth | MD5 token authentication (salt + password hash) |
| Build | electron-builder (NSIS installer) |
| State | localStorage (all settings, tags, profiles, sessions) |

<br/>

## `> ARCHITECTURE`

```
 AETHER/
  ├── main.js          # Electron main process — window, tray, IPC, Discord RPC, stem file detection
  ├── preload.js       # Context bridge — safe IPC + filesystem access
  ├── player.html      # THE APP — 16,000+ lines of UI, audio, visualizers, DJ engine, everything
  ├── package.json     # Electron + builder config
  ├── icon.ico         # App icon (purple on black)
  ├── index.html       # Website / landing page (GitHub Pages)
  ├── Launch.vbs       # Silent launcher (no console window)
  └── .gitignore       # Security-first exclusions
```

### Audio Pipeline
```
  AudioElement                    Binaural Engine          Warmth Engine
       │                          ├─ OscL → Pan(-1)┐      ├─ Vinyl noise
  MediaElementSource              ├─ OscR → Pan(+1)┤      ├─ Tape hiss
       │                          └─ GainNode ──────┤      ├─ WaveShaper (saturation)
  BiquadFilters ×10 (EQ)              ↓             │      ├─ LFO delay (flutter)
       │                         RhythmMasterGain──┤      └─ LP filter (rolloff)
  Hearing Compensation EQ                           │           │
       │                                            │           │
       └─── Warmth Engine ─────────────────────────┤           │
                                                    │           │
                                              AnalyserNode ←───┘
                                                    │
                                         DynamicsCompressor (Master Limiter)
                                                    │
                                           AudioContext.destination
                                                    │
                                      ╔══════════════════════════════╗
                                      ║  Beat Detection → Visualizers ║
                                      ║  DJ Engine → Dual Deck Mix    ║
                                      ║  Stem Engine → 4-channel mix  ║
                                      ╚══════════════════════════════╝
```

<br/>

## `> KEYBOARD SHORTCUTS`

| Key | Action |
|-----|--------|
| `Space` | Play / Pause |
| `Ctrl + Right/Left` | Next / Previous Track |
| `Ctrl + Up/Down` | Volume Up / Down |
| `Left / Right` | Seek -5s / +5s |
| `Ctrl + K` | Search |
| `Ctrl + G` | Magic Playlist generator |
| `Ctrl + P` | Pomodoro focus mode |
| `Ctrl + W` | Weather ambient toggle |
| `Ctrl + S` | Stems panel |
| `Ctrl + ,` | Settings |
| `M` | Mute |
| `S` | Shuffle |
| `R` | Repeat |
| `Q` | Queue |
| `V` | Cycle Visualizer |
| `F` | Fullscreen Visualizer |
| `W` | Warmth engine toggle |
| `I` | Signal path display |
| `T` | Sleep timer |
| `D` | DJ Mode |
| `B` | DJ: Blend |
| `C` | DJ: Cut |
| `G` | DJ: Bass Swap |
| `E` | DJ: Echo Out |
| `F` | DJ: Filter Sweep |
| `P` | DJ: Power Down |
| `X` | DJ: Snap crossfader |
| `Z` | DJ: Swap bass |
| `A` | DJ: Toggle auto-mix |
| `?` | DJ: Help overlay |
| `N / P` | Next / Previous Sheep Video |
| `Escape` | Close Overlays |

<br/>

## `> QUICK START`

### Prerequisites
- [Node.js](https://nodejs.org/) 18+
- [Navidrome](https://www.navidrome.org/) running on your network
- Music library configured in Navidrome

### Install & Run
```bash
git clone https://github.com/EmperorBadussy/aether.git
cd aether
npm install
npm start
```

### Configure
1. Launch AETHER
2. Open Settings (gear icon in sidebar)
3. Enter your Navidrome server URL, username, and password
4. Click APPLY — your library loads instantly

### Build Installer
```bash
npm run dist
```
Outputs to `dist/` — NSIS installer for Windows x64.

<br/>

## `> COMPANION APP`

AETHER is one half of a two-app ecosystem:

| | AETHER | CHARON |
|---|--------|--------|
| **Purpose** | Play your library | Build your library |
| **Color** | Tron Purple | Styx Cyan |
| **Backend** | Navidrome (Subsonic API) | Tidal (tidalapi + tiddl) |
| **Repo** | You're here | [EmperorBadussy/charon](https://github.com/EmperorBadussy/charon) |

**AETHER** plays. **CHARON** harvests.

<br/>

## `> LICENSE`

MIT License. Do whatever you want. Credit appreciated but not required.

<br/>

---

<p align="center">
  <sub>Built for OLED. Engineered for audiophiles. Designed from 2077.</sub>
</p>

<p align="center">
  <code>[ AETHER v2.0.0 ]</code>
</p>
