# AETHER RIPPER — Dev Notes

## What Is This

Companion app to AETHER. A premium Tidal downloader GUI that wraps `tiddl` (Python CLI) with a full Electron + React desktop app. Downloads feed into `E:\Music` → Navidrome auto-scans → tracks appear in AETHER. Full pipeline.

**Spec file**: `C:\Users\D\Downloads\tiddl-gui-spec.md` (39KB, comprehensive)

## Architecture Summary

```
Electron Shell
├── React Frontend (Vite + Tailwind + Zustand)
│   ├── Search/Browse (tidalapi)
│   ├── Download Queue (tiddl CLI wrapper)
│   ├── Preview Player (30s Tidal clips)
│   └── Settings / Help
├── Python Bridge (phantom_bridge.py)
│   ├── tiddl → download engine (CLI wrapper, DO NOT rewrite)
│   ├── tidalapi → search, browse, metadata, favorites
│   └── JSON stdin/stdout communication with Electron
└── Navidrome Integration (Subsonic API startScan)
```

## Key Decisions To Make Before Starting

### 1. Python Bundling Strategy
This is the #1 deployment risk. Options:
- **Embedded Python** (python-build-standalone): Bundle a portable Python + pip install deps at build time. Cleanest but ~50MB added to installer.
- **PyInstaller**: Compile phantom_bridge.py into a standalone .exe. No Python needed on user machine. But tidalapi/tiddl updates require rebuild.
- **System Python**: Require user to have Python installed. Worst UX, simplest dev.
- **RECOMMENDATION**: PyInstaller for the bridge .exe. Keeps installer self-contained. Can update the .exe independently.

### 2. Auth Token Sharing
tiddl stores auth at `~/.tiddl`. tidalapi has its own session format. Need to spike:
- What format does tiddl use? (check `~/.tiddl/config.json`)
- Can tidalapi consume tiddl's tokens?
- If not, maintain separate session at `~/.tiddl/phantom_session.json`
- MUST solve this before Phase 1 — double login is a dealbreaker UX-wise.

### 3. tiddl Output Parsing
tiddl's stdout output may not be structured enough for progress bars. Options:
- Parse existing text output with regex
- Fork tiddl to add `--json-progress` flag (small PR)
- Monitor output file sizes for progress estimation
- RECOMMENDATION: Start with regex parsing, fork for JSON progress if needed

### 4. React vs Vanilla JS
The spec calls for React + Vite + Tailwind + Zustand. This is the RIGHT call here because:
- Complex state (queue, search, navigation, auth, downloads)
- Many views with shared components
- Zustand is perfect for the download queue reactive updates
- Tailwind + custom CSS for the PHANTOM aesthetic
- This is NOT the same as AETHER (which is a single-purpose player)

## Implementation Order (My Recommendation)

### Sprint 1: Foundation (get something on screen)
1. `npx create-electron-vite@latest aether-ripper -- --template react-ts`
2. Frameless window + custom titlebar
3. PHANTOM design system CSS variables + glassmorphism base
4. Sidebar layout + routing (React Router or Zustand nav state)
5. Placeholder views for all 9 screens

### Sprint 2: Python Bridge
1. Write `phantom_bridge.py` with JSON stdin/stdout protocol
2. Implement `auth_status` and `auth_login` commands
3. Implement `search` command via tidalapi
4. Test from Electron main process via child_process.spawn
5. IPC bridge: main ↔ renderer for search results

### Sprint 3: Core Features
1. Search view (query → results → artist/album/track cards)
2. Artist view (hero + top tracks + albums + discography)
3. Album view (track listing + download button)
4. Single track download via tiddl CLI
5. Basic queue (add → download → complete)

### Sprint 4: Download Engine
1. Queue manager with concurrent downloads (default 3)
2. Progress parsing from tiddl stdout
3. Download history with persistence (localStorage or SQLite)
4. Navidrome auto-scan after album completion
5. Error handling for region-locked / unavailable tracks

### Sprint 5: Polish
1. Preview player (30s clips)
2. Favorites sync
3. Context menus
4. Keyboard shortcuts
5. Help view (first-run onboarding)
6. Hover animations + Framer Motion transitions

## File Locations

- **Spec**: `C:\Users\D\Downloads\tiddl-gui-spec.md`
- **Project dir** (TBD): `C:\Users\D\Desktop\AETHER-RIPPER\` (suggested)
- **tiddl upstream**: https://github.com/oskvr37/tiddl (Apache-2.0)
- **AETHER main app**: `C:\Users\D\Desktop\AETHER\`
- **Navidrome music dir**: `E:\Music`
- **Navidrome server**: `http://localhost:4533`

## Dependencies to Install

```bash
# Node
npm create electron-vite@latest aether-ripper -- --template react-ts
cd aether-ripper
npm install zustand framer-motion lucide-react
npm install -D tailwindcss @tailwindcss/vite

# Python (in python/ subdirectory)
pip install tiddl tidalapi

# System
# ffmpeg must be in PATH or bundled
```

## Design System Quick Reference

```
Backgrounds: #0a0a12 → #0d0d1a → #12121f → #1a1a2e
Purple:      #4c1d95 → #7c3aed → #a78bfa → #c4b5fd
Cyan:        #06b6d4 → #22d3ee → #67e8f9
Magenta:     #d946ef → #e879f9 → #f0abfc
Fonts:       Inter (UI) + JetBrains Mono (stats) + Sora (display)
Glass:       rgba(26,26,46,0.6) + blur(20px) + 1px border rgba(139,92,246,0.15)
```

## Gotchas & Warnings

- tiddl might not have granular enough progress output — test early
- tidalapi is unofficial, can break with Tidal API changes
- Tidal preview URLs have been unreliable recently — need fallback
- Windows path separators everywhere — use path.join(), never hardcode /
- Python subprocess stdio can buffer — use `PYTHONUNBUFFERED=1`
- Electron + Python bridge: handle zombie processes on app quit
- ffmpeg not found = cryptic errors from tiddl — check on startup

## Relationship to AETHER

```
AETHER RIPPER (this app)     →  Downloads to E:\Music
                                      ↓
                              Navidrome scans E:\Music
                                      ↓
                              AETHER connects to Navidrome
                                      ↓
                              User plays music + visualizers + binaural
```

The two apps share:
- Navidrome server config (URL, user, pass)
- PHANTOM/AETHER design language (purple glassmorphism on black)
- The AETHER brand identity

They do NOT share:
- Codebase (React+TS vs vanilla JS)
- Electron config (separate windows, separate processes)
- State (separate localStorage/config)
