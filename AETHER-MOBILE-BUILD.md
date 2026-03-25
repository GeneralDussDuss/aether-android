# AETHER Mobile — Complete Build Reference

## What This Is
Android APK (Capacitor WebView) + Android Auto Media Service (native Kotlin/ExoPlayer) + Android Auto Navigation (Tron-styled Google Maps). Connects to Navidrome on PC via Tailscale VPN.

---

## Project Structure
```
C:/Users/D/Desktop/AETHER/mobile/
├── package.json                    # Capacitor deps (@capacitor/core, android, cli)
├── capacitor.config.ts             # appId: com.aether.player, webDir: www/, cleartext: true
├── build-www.sh                    # Copies player.html → www/index.html with mobile injections
├── www/
│   ├── index.html                  # Transformed player.html (mobile viewport, CSS/JS injected)
│   ├── mobile-tweaks.css           # 48px touch targets, hide volume slider, safe area insets, no hover
│   └── mobile-bridge.js            # Swipe gestures, MediaSession API, credential bridge, electronAPI stub
└── android/                        # Capacitor-generated Android project
    ├── build.gradle                # Root — needs Kotlin plugin added
    ├── variables.gradle            # minSdk=23, compileSdk=34, targetSdk=34
    └── app/
        ├── build.gradle            # App-level — needs Kotlin, ExoPlayer, Car App, OkHttp, Glide deps
        ├── src/main/
        │   ├── AndroidManifest.xml # Needs: media service, car app service, permissions, cleartext
        │   ├── assets/public/      # Capacitor copies www/ here
        │   ├── java/com/aether/player/
        │   │   ├── MainActivity.java           # Capacitor default (keep as-is)
        │   │   ├── SubsonicClient.kt           # REST client mirroring JS SUBSONIC object
        │   │   ├── AetherMediaService.kt       # MediaLibraryService (Media3) + ExoPlayer
        │   │   ├── AetherCredentialPlugin.kt   # Capacitor plugin: WebView localStorage → SharedPrefs
        │   │   ├── AetherCarAppService.kt      # Car App Library entry point
        │   │   ├── AetherNavigationSession.kt  # Car session
        │   │   ├── AetherMapScreen.kt          # NavigationTemplate + SurfaceCallback + Google Maps
        │   │   └── TronMapStyle.kt             # JSON style constant for maps
        │   └── res/xml/
        │       └── automotive_app_desc.xml     # Declares media + navigation capabilities
```

---

## Source File Reference (player.html)
- **Lines 56-95**: CSS variables (`:root`) — AETHER color system
- **Lines 104-117**: Titlebar CSS
- **Lines 1617-1628**: Titlebar HTML (auto-hidden when no electronAPI)
- **Lines 1720-1755**: Now-playing bar HTML (art, info, transport, seek, volume, EQ)
- **Lines 2064-2108**: MD5 hasher (Joseph Myers)
- **Lines 2110-2139**: SUBSONIC API client object (baseUrl, user, password, auth(), get(), streamUrl(), coverUrl())
- **Lines 2958-3021**: playCurrentTrack() function
- **Lines 4025-4048**: loadSavedSettings() — reads localStorage → SUBSONIC object
- **Lines 8074-8095**: Electron window controls guard (hides titlebar if !electronAPI)
- **Lines 8097-8326**: Init/splash + visualizers

## Subsonic Auth Pattern
```
salt = random 8-char string
token = md5(password + salt)
params: u={user}&t={token}&s={salt}&v=1.16.1&c=Aether&f=json
```
Cover art uses fixed salt `aether_cover` for caching.

## Preload.js IPC Methods to Stub
1. `minimize()` — window min
2. `maximize()` — window max
3. `close()` — window close
4. `isMaximized()` — returns boolean
5. `onWindowState(callback)` — state events
6. `scanSheepFolder(path)` — Electric Sheep video scan
7. `openFolderDialog(title)` — native folder picker
8. `openFilesDialog(title, ext[])` — native file picker

All are already guarded with `if (window.electronAPI && ...)` checks in player.html.

---

## Phase 1: Capacitor WebView APK [DONE]
- ✅ Created `mobile/package.json` with Capacitor 6 deps
- ✅ Created `capacitor.config.ts` (cleartext, http scheme, black background)
- ✅ Created `mobile-tweaks.css` (touch targets, safe areas, hide volume)
- ✅ Created `mobile-bridge.js` (swipe, MediaSession, credential sync, electronAPI stub)
- ✅ Created `build-www.sh` (transforms player.html → www/index.html)
- ✅ Built www/index.html successfully (verified injections at lines 50, 1584, 8326)
- ✅ `npm install` + `npx cap add android` — Android project generated
- ✅ Updated minSdkVersion to 23

### Remaining for Phase 1:
- Add Kotlin plugin to root build.gradle: `classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22"`
- Enable Kotlin in app/build.gradle: `apply plugin: 'kotlin-android'`
- Add compile options to app/build.gradle `android {}` block:
  ```gradle
  compileOptions {
      sourceCompatibility JavaVersion.VERSION_17
      targetCompatibility JavaVersion.VERSION_17
  }
  kotlinOptions {
      jvmTarget = "17"
  }
  ```
- Install JDK 17 and set `JAVA_HOME` (see Environment section)
- Build with `./gradlew assembleDebug`

---

## Phase 2: Android Auto Media Service [TODO]

### SubsonicClient.kt
Mirrors the JS SUBSONIC object in Kotlin using OkHttp:
- MD5 auth via `java.security.MessageDigest` (NOT a port of the JS MD5 — use native Java):
  ```kotlin
  fun md5(input: String): String {
      val md = java.security.MessageDigest.getInstance("MD5")
      return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
  }
  ```
- `get(endpoint, params)` → JSON response
- `streamUrl(id)` → streaming URL string
- `coverUrl(id, size)` → cover art URL string
- `getAlbums()`, `getArtists()`, `getPlaylists()`, `getGenres()`, `getRecentlyAdded()`, `getRandomSongs()`

### AetherMediaService.kt
`MediaLibraryService` (Media3 — NOT legacy MediaBrowserServiceCompat) with:
- Browse tree: ROOT → Albums / Artists / Playlists / Genres / Recently Added / Random Mix → drill into album → tracks
- Media IDs: `__ROOT__`, `__ALBUMS__`, `__ARTISTS__`, `__PLAYLISTS__`, `__GENRES__`, `__RECENT__`, `__RANDOM__`
- Album drill: `album_{id}`, Track play: `track_{id}`
- ExoPlayer for playback (Media3 `ExoPlayer`)
- MediaSession for controls (Media3 `MediaSession`, NOT legacy `MediaSessionCompat`)
- Notification channel: create `"aether_playback"` channel on service start (required API 26+)
- Foreground service type: `mediaPlayback` (declared in manifest, required API 34)
- Scrobbling via `scrobble` endpoint

**Why Media3 not legacy**: Using `MediaLibraryService` + `media3-session` gives unified API, built-in
Android Auto support, and avoids mixing legacy `MediaBrowserServiceCompat` with Media3 ExoPlayer.
The legacy `androidx.media:media` dependency is NOT needed.

### AetherCredentialPlugin.kt
Capacitor plugin (`@CapacitorPlugin`):
- `syncCredentials(call)` — receives {baseUrl, user, password} from JS, stores in SharedPreferences
- Native code reads SharedPreferences on service start

### Dependencies
```gradle
// Media3 (ExoPlayer + Session + Android Auto support — full Media3 stack, no legacy media)
implementation "androidx.media3:media3-exoplayer:1.3.1"
implementation "androidx.media3:media3-session:1.3.1"
implementation "androidx.media3:media3-ui:1.3.1"
// HTTP
implementation "com.squareup.okhttp3:okhttp:4.12.0"
// Image loading (cover art in browse tree)
implementation "com.github.bumptech.glide:glide:4.16.0"
```

---

## Phase 3: Android Auto Navigation (Tron Map) [TODO]

### AetherCarAppService.kt
Entry point for Car App Library. Returns `AetherNavigationSession`.

### AetherNavigationSession.kt
`Session` subclass. Creates `AetherMapScreen`.

### AetherMapScreen.kt
Uses `NavigationTemplate` with `SurfaceCallback`:
- Renders Google Map on car display surface via `MapRenderer` API (Maps SDK 18.2.0+)
- Applies Tron style JSON via `GoogleMapOptions` / `MapStyleOptions`
- Shows current location with custom purple marker
- No turn-by-turn (requires Navigation SDK license)

**Map rendering strategy**: The standard `MapView`/`SupportMapFragment` don't render to arbitrary
surfaces. Use `MapRenderer` (added Maps SDK 18.2.0) which can render to a `Surface` provided by
the Car App Library's `SurfaceCallback`. Alternatively, if `MapRenderer` proves too complex,
downgrade to a simpler approach: pre-render map tiles and draw them on the surface canvas directly.

**Play Store note**: Declaring `category.NAVIGATION` without actual turn-by-turn will get rejected
by Google Play review. This is fine for sideloaded APKs. If Play Store is ever needed, switch to
`MapTemplate` (Car App Library 1.7+) or remove navigation category and just do media.

### TronMapStyle.kt
Google Maps JSON style derived from AETHER CSS vars:

| Map Element | Color | CSS Var |
|------------|-------|---------|
| Base geometry | `#000000` | `--bg-void` |
| Roads | `#362050` | `--purple-dim` |
| Highways | `#4A2D6B` / stroke `#7B2FBE` | `--purple-core` |
| Labels | `#B76EFF` | `--purple-vivid` |
| Water | `#0E6377` / labels `#00DCF5` | `--cyan-muted/bright` |
| Parks | `#0A3D4A` / labels `#00DCF5` | `--cyan-dim/bright` |
| POIs | labels `#FF2D7B` | `--magenta-bright` |

### Dependencies
```gradle
implementation "androidx.car.app:app:1.4.0"
implementation "androidx.car.app:app-projected:1.4.0"
implementation "com.google.android.gms:play-services-maps:18.2.0"
implementation "com.google.android.gms:play-services-location:21.0.1"
```

### Manifest Additions
```xml
<!-- ========== PERMISSIONS (add to <manifest>, outside <application>) ========== -->
<uses-permission android:name="android.permission.INTERNET" />  <!-- already present -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- ========== APPLICATION (add usesCleartextTraffic to existing <application> tag) ========== -->
<!-- android:usesCleartextTraffic="true"  ← REQUIRED for native OkHttp calls to Tailscale IP -->

<!-- ========== SERVICES & METADATA (add inside <application>) ========== -->

<!-- Media Service (Media3 MediaLibraryService) -->
<service android:name=".AetherMediaService"
    android:exported="true"
    android:foregroundServiceType="mediaPlayback">
    <intent-filter>
        <action android:name="androidx.media3.session.MediaLibraryService" />
    </intent-filter>
</service>

<!-- Car App Service -->
<service android:name=".AetherCarAppService" android:exported="true">
    <intent-filter>
        <action android:name="androidx.car.app.CarAppService" />
        <category android:name="androidx.car.app.category.NAVIGATION" />
    </intent-filter>
</service>

<!-- Min Car App API Level (NavigationTemplate requires level 2+) -->
<meta-data android:name="androidx.car.app.minCarAppApiLevel"
    android:value="2" />

<!-- Car App descriptor -->
<meta-data android:name="com.google.android.gms.car.application"
    android:resource="@xml/automotive_app_desc" />

<!-- Google Maps API key -->
<meta-data android:name="com.google.android.geo.API_KEY"
    android:value="YOUR_API_KEY_HERE" />
```

**Why these permissions matter:**
- `FOREGROUND_SERVICE` — required to run any foreground service (API 28+)
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK` — required for `foregroundServiceType="mediaPlayback"` (API 34, our target)
- `ACCESS_FINE/COARSE_LOCATION` — required for Phase 3 map current-location display
- `usesCleartextTraffic` — Capacitor's `cleartext: true` only covers the WebView; native OkHttp needs the manifest flag

### automotive_app_desc.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<automotiveApp>
    <uses name="media" />
    <uses name="navigation" />
</automotiveApp>
```

---

## Phase 4: Build & Test [TODO]

### Build APK
```bash
cd C:/Users/D/Desktop/AETHER/mobile
bash build-www.sh                    # Rebuild www/index.html from player.html
npx cap sync android                 # Sync web assets + plugins to Android
cd android && ./gradlew assembleDebug
```

### Install
```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

### Test Android Auto (DHU)
```bash
# Install DHU
sdkmanager "extras;google;auto"
# Run with phone connected via ADB
desktop-head-unit.exe
```

---

## Environment
- JDK: Needs JDK 17 (Microsoft OpenJDK) — `JAVA_HOME` not set yet
  ```bash
  # Install via winget:
  winget install Microsoft.OpenJDK.17
  # Then set JAVA_HOME (or add to system env vars):
  export JAVA_HOME="/c/Program Files/Microsoft/jdk-17"
  # Verify:
  java -version   # should show 17.x
  ```
- Android SDK: `C:\Users\D\AppData\Local\Android\Sdk` (exists, has build-tools, platform-tools, etc.)
- Kotlin: 1.9.22 (declared via Gradle plugin, no separate install needed)
- Node/npm: Available
- Tailscale: PC needs `tailscale up`, get IP with `tailscale ip -4`

## Cleartext / Network Security
- `capacitor.config.ts`: `server.cleartext: true`, `androidScheme: 'http'` — covers WebView only
- Manifest needs `android:usesCleartextTraffic="true"` on `<application>` — covers native OkHttp (SubsonicClient.kt)
- **Both are required** — Capacitor cleartext does NOT propagate to native HTTP clients
- Subsonic API uses HTTP (not HTTPS) to Tailscale IP

---

## AETHER Color System (for reference)
```css
--bg-void: #000000       --bg-surface: #09090F     --bg-raised: #0F0F1A
--purple-dim: #362050    --purple-muted: #6B42A0    --purple-core: #7B2FBE
--purple-bright: #9D4EDD --purple-vivid: #B76EFF    --purple-hot: #D4A0FF
--cyan-dim: #0A3D4A      --cyan-muted: #0E6377      --cyan-core: #00B4D8
--cyan-bright: #00DCF5   --cyan-vivid: #48F2FF
--magenta-dim: #3D0A2A   --magenta-muted: #9B0054   --magenta-core: #D4147A
--magenta-bright: #FF2D7B --magenta-vivid: #FF6BA8
--font-display: Orbitron  --font-body: Rajdhani      --font-mono: JetBrains Mono
```
