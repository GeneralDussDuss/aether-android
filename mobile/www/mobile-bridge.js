/**
 * AETHER Mobile Bridge
 * Handles mobile-specific interactions:
 * - Swipe gestures on now-playing for prev/next
 * - MediaSession API for lock screen / notification controls
 * - Electron API stubs
 * - Capacitor credential bridge
 */

(function() {
  'use strict';

  // ============ ELECTRON API STUB ============
  // Ensure electronAPI is null so Electron guards degrade gracefully
  if (!window.electronAPI) {
    window.electronAPI = null;
  }

  // ============ SWIPE GESTURES ============
  let touchStartX = 0;
  let touchStartY = 0;
  let touchStartTime = 0;
  const SWIPE_THRESHOLD = 60;
  const SWIPE_TIME_LIMIT = 300;

  function initSwipeGestures() {
    const npBar = document.querySelector('.now-playing');
    if (!npBar) return;

    npBar.addEventListener('touchstart', (e) => {
      touchStartX = e.changedTouches[0].clientX;
      touchStartY = e.changedTouches[0].clientY;
      touchStartTime = Date.now();
    }, { passive: true });

    npBar.addEventListener('touchend', (e) => {
      const dx = e.changedTouches[0].clientX - touchStartX;
      const dy = e.changedTouches[0].clientY - touchStartY;
      const dt = Date.now() - touchStartTime;

      // Only horizontal swipes, within time limit
      if (dt > SWIPE_TIME_LIMIT) return;
      if (Math.abs(dy) > Math.abs(dx)) return;
      if (Math.abs(dx) < SWIPE_THRESHOLD) return;

      if (dx > 0) {
        // Swipe right -> previous
        const prevBtn = document.getElementById('btnPrev');
        if (prevBtn) prevBtn.click();
      } else {
        // Swipe left -> next
        const nextBtn = document.getElementById('btnNext');
        if (nextBtn) nextBtn.click();
      }
    }, { passive: true });
  }

  // ============ MEDIA SESSION API ============
  function updateMediaSession() {
    if (!('mediaSession' in navigator)) return;

    const title = document.getElementById('npTitle')?.textContent || 'AETHER';
    const artist = document.getElementById('npArtist')?.textContent || '';
    const album = document.getElementById('npAlbum')?.textContent || '';

    // Get cover art URL from the now-playing art element
    const artImg = document.querySelector('#npArt img');
    const artwork = artImg ? [
      { src: artImg.src, sizes: '300x300', type: 'image/jpeg' }
    ] : [];

    navigator.mediaSession.metadata = new MediaMetadata({
      title: title === 'No track selected' ? 'AETHER' : title,
      artist: artist === '--' ? '' : artist,
      album: album === '--' ? '' : album,
      artwork: artwork
    });

    navigator.mediaSession.setActionHandler('play', () => {
      document.getElementById('btnPlay')?.click();
    });
    navigator.mediaSession.setActionHandler('pause', () => {
      document.getElementById('btnPlay')?.click();
    });
    navigator.mediaSession.setActionHandler('previoustrack', () => {
      document.getElementById('btnPrev')?.click();
    });
    navigator.mediaSession.setActionHandler('nexttrack', () => {
      document.getElementById('btnNext')?.click();
    });
    navigator.mediaSession.setActionHandler('seekto', (details) => {
      const audio = document.querySelector('audio');
      if (audio && details.seekTime !== undefined) {
        audio.currentTime = details.seekTime;
      }
    });
  }

  // ============ CREDENTIAL BRIDGE ============
  // Push credentials to native SharedPreferences via Capacitor plugin
  function syncCredentialsToNative() {
    try {
      const settings = JSON.parse(localStorage.getItem('aether_settings'));
      if (!settings) return;

      // Store in a format the native Android Auto service can read
      const creds = {
        baseUrl: settings.baseUrl || 'http://100.117.145.79:4533',
        user: settings.user || 'phantom',
        password: settings.password || '052990'
      };

      // Try Capacitor plugin bridge
      if (window.Capacitor?.Plugins?.AetherCredentials) {
        window.Capacitor.Plugins.AetherCredentials.syncCredentials(creds);
      }
    } catch(e) {
      console.warn('Credential sync failed:', e);
    }
  }

  // ============ OBSERVER — Auto-update MediaSession on track change ============
  let mediaSessionHandlersSet = false;
  function observeTrackChanges() {
    const npTitle = document.getElementById('npTitle');
    if (!npTitle) return;

    const observer = new MutationObserver(() => {
      updateMediaSession();
      // Don't sync credentials on every track change — only needed when settings change
    });
    observer.observe(npTitle, { childList: true, characterData: true, subtree: true });
  }

  // ============ INIT ============
  function initMobile() {
    // Wait for DOM
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', setup);
    } else {
      setup();
    }
  }

  function setup() {
    initSwipeGestures();
    observeTrackChanges();
    updateMediaSession();
    killHeavyVisualsOnMobile();
    enableSingleTapPlayback();
    installAudioDiagnostics();
    prepareVizForMobile();
    initBackgroundAudioFix();
    initConnectionMonitor();
    initAdaptiveViz();

    // Ensure volume is up
    const audio = document.querySelector('audio');
    if (audio) { audio.volume = 1.0; audio.muted = false; }

    // Keep screen awake while playing (if available)
    if ('wakeLock' in navigator) {
      let wakeLockSentinel = null;
      document.querySelector('audio')?.addEventListener('play', async () => {
        if (wakeLockSentinel) return;
        try {
          wakeLockSentinel = await navigator.wakeLock.request('screen');
          wakeLockSentinel.addEventListener('release', () => { wakeLockSentinel = null; });
        } catch(e) { wakeLockSentinel = null; }
      });
      document.querySelector('audio')?.addEventListener('pause', () => {
        if (wakeLockSentinel) { wakeLockSentinel.release(); wakeLockSentinel = null; }
      });
    }

    console.log('[AETHER Mobile] Bridge initialized');
  }

  // ============ BACKGROUND AUDIO FIX ============
  // Resume AudioContext when returning from background — Android WebView
  // suspends it aggressively. Also send periodic keep-alive to prevent GC.
  function initBackgroundAudioFix() {
    document.addEventListener('visibilitychange', () => {
      if (!document.hidden) {
        // Returning to foreground — resume audio
        if (typeof audioCtx !== 'undefined' && audioCtx && audioCtx.state === 'suspended') {
          audioCtx.resume().then(() => {
            console.log('[AETHER Mobile] AudioContext resumed from background');
          }).catch(() => {});
        }
        // Re-update MediaSession in case it got stale
        updateMediaSession();
      }
    });

    // Keep-alive: prevent WebView from being garbage collected in background
    // by touching the audio element periodically
    setInterval(() => {
      if (document.hidden) {
        const audio = document.querySelector('audio');
        if (audio && !audio.paused) {
          // Touch the currentTime to keep the process alive
          void audio.currentTime;
        }
      }
    }, 10000);

    console.log('[AETHER Mobile] Background audio fix installed');
  }

  // ============ CONNECTION MONITOR ============
  // Pings Navidrome every 30s, shows status indicator, auto-recovers
  let connectionOnline = true;
  function initConnectionMonitor() {
    // Inject status indicator into the app
    injectConnectionIndicator();

    // Periodic ping — uses direct fetch to Navidrome, NOT the patched SUBSONIC
    // (patched version routes through local plugin when offline, always succeeds)
    setInterval(async () => {
      try {
        if (typeof SUBSONIC === 'undefined' || !SUBSONIC.baseUrl) return;
        const pingUrl = SUBSONIC.baseUrl + '/rest/ping' + SUBSONIC.auth() + '&f=json';
        const resp = await Promise.race([
          fetch(pingUrl, { method: 'GET' }),
          new Promise((_, reject) => setTimeout(() => reject(new Error('timeout')), 5000))
        ]);
        if (resp.ok) {
          if (!connectionOnline) {
            connectionOnline = true;
            updateConnectionIndicator(true);
            if (typeof window.__aetherOnConnectionUp === 'function') window.__aetherOnConnectionUp();
            console.log('[AETHER Mobile] Navidrome connection recovered');
          }
        } else { throw new Error('ping failed: ' + resp.status); }
      } catch(e) {
        if (connectionOnline) {
          connectionOnline = false;
          updateConnectionIndicator(false);
          console.log('[AETHER Mobile] Navidrome connection lost');
        }
      }
    }, 30000);

    // Also listen for network changes
    window.addEventListener('online', () => {
      console.log('[AETHER Mobile] Network online');
      updateConnectionIndicator(null); // unknown until next ping
    });
    window.addEventListener('offline', () => {
      connectionOnline = false;
      updateConnectionIndicator(false);
      console.log('[AETHER Mobile] Network offline');
    });
  }

  function injectConnectionIndicator() {
    let retries = 0;
    const tryInject = () => {
      const sidebar = document.querySelector('.sidebar-header') || document.querySelector('.sidebar');
      if (!sidebar) { if (++retries < 20) setTimeout(tryInject, 500); return; }

      const indicator = document.createElement('div');
      indicator.id = 'aether-conn-status';
      indicator.style.cssText = `
        display: flex; align-items: center; gap: 6px;
        padding: 6px 12px; font-family: var(--font-mono, monospace);
        font-size: 0.6rem; letter-spacing: 0.1em;
        color: var(--purple-muted, #6b5b95); opacity: 0.8;
      `;
      indicator.innerHTML = `
        <span id="conn-dot" style="width:6px;height:6px;border-radius:50%;background:#9d4edd;box-shadow:0 0 6px #9d4edd;"></span>
        <span id="conn-label">NAVIDROME</span>
      `;
      sidebar.insertBefore(indicator, sidebar.firstChild);
    };
    tryInject();
  }

  function updateConnectionIndicator(online) {
    const dot = document.getElementById('conn-dot');
    const label = document.getElementById('conn-label');
    if (!dot || !label) return;

    if (online === true) {
      dot.style.background = '#22c55e';
      dot.style.boxShadow = '0 0 6px #22c55e';
      label.textContent = 'NAVIDROME';
      label.style.color = 'var(--purple-muted, #6b5b95)';
    } else if (online === false) {
      dot.style.background = '#ef4444';
      dot.style.boxShadow = '0 0 6px #ef4444';
      label.textContent = 'OFFLINE';
      label.style.color = '#ef4444';
    } else {
      dot.style.background = '#f59e0b';
      dot.style.boxShadow = '0 0 6px #f59e0b';
      label.textContent = 'RECONNECTING...';
      label.style.color = '#f59e0b';
    }
  }

  // ============ ADAPTIVE VISUALIZER FPS ============
  // Battery-aware: 60fps charging, 30fps battery, 15fps low, stop when screen off
  let vizTargetFps = 30;
  let vizFrameInterval = 1000 / 30;
  let vizLastFrame = 0;

  function initAdaptiveViz() {
    if ('getBattery' in navigator) {
      navigator.getBattery().then(battery => {
        const updateFps = () => {
          if (battery.charging) {
            vizTargetFps = 60;
          } else if (battery.level < 0.15) {
            vizTargetFps = 15;
          } else {
            vizTargetFps = 30;
          }
          vizFrameInterval = 1000 / vizTargetFps;
          console.log('[AETHER Mobile] Viz FPS:', vizTargetFps, 'charging:', battery.charging, 'level:', Math.round(battery.level * 100) + '%');
        };
        updateFps();
        battery.addEventListener('chargingchange', updateFps);
        battery.addEventListener('levelchange', updateFps);
      }).catch(() => {});
    }

    // Stop viz when screen off, restart when screen on
    document.addEventListener('visibilitychange', () => {
      if (document.hidden) {
        // Screen off — kill viz and throttle to save battery
        disableVizThrottle();
        if (typeof window.__killAnimations === 'function') {
          window.__killAnimations();
        }
      } else if (vizStarted) {
        // Screen on — restart viz with throttle
        if (typeof window.__reviveAnimations === 'function') {
          window.__reviveAnimations();
        }
        enableVizThrottle();
        if (typeof drawViz === 'function') requestAnimationFrame(drawViz);
        if (typeof updateAudioAnalysis === 'function') requestAnimationFrame(updateAudioAnalysis);
      }
    });

    // Reduce canvas resolution on mobile for performance
    // Render at 0.5x device pixel ratio, CSS scales up
    const optimizeCanvas = () => {
      // Don't override canvas sizing — let player.html's vizResize handle it
      // at native resolution. The CSS ensures the canvas fills the panel.
    };

    // Hook into vizResize if it exists
    if (typeof vizResize === 'function') {
      const origResize = vizResize;
      window.vizResize = function() {
        origResize();
        optimizeCanvas();
      };
    }

    console.log('[AETHER Mobile] Adaptive viz initialized (target: ' + vizTargetFps + 'fps)');
  }

  // Expose vizFrameInterval for the viz loop to use as a throttle
  window.__aetherMobileVizInterval = () => vizFrameInterval;

  // ============ START MEDIA SERVICE ON CONNECTION ============
  // When Navidrome connection is first established, start the native
  // foreground MediaService so Android Auto / notification controls work.
  function startNativeMediaService() {
    try {
      if (window.Capacitor?.Plugins?.AetherCredentials) {
        // Sync credentials first, which also triggers the service to reload
        syncCredentialsToNative();
        console.log('[AETHER Mobile] Native MediaService credentials synced');
      }
    } catch(e) {
      console.warn('[AETHER Mobile] Failed to start MediaService:', e);
    }
  }

  // Hook into the connection monitor — start service on first successful ping
  let mediaServiceStarted = false;
  const origUpdateConn = window.updateConnectionIndicator;
  window.__aetherOnConnectionUp = function() {
    if (!mediaServiceStarted) {
      mediaServiceStarted = true;
      startNativeMediaService();
    }
  };

  // ============ VIZ FPS THROTTLE FOR MOBILE ============
  // drawViz in player.html self-schedules via requestAnimationFrame(drawViz).
  // We can't wrap it externally without double-loops. Instead, we patch rAF
  // ONLY when viz is active to throttle those self-scheduling calls.
  // Non-viz rAF users (CSS animations, Capacitor) are identified by checking
  // if the callback is one of the known viz functions.
  const _origRAF = window.requestAnimationFrame;
  const _origCAF = window.cancelAnimationFrame;
  let vizThrottleActive = false;

  function enableVizThrottle() {
    if (vizThrottleActive) return;
    vizThrottleActive = true;

    window.requestAnimationFrame = function(callback) {
      // Only throttle known viz callbacks (drawViz, updateAudioAnalysis)
      const fnName = callback.name || '';
      const isVizFn = (typeof drawViz !== 'undefined' && callback === drawViz) ||
                      (typeof updateAudioAnalysis !== 'undefined' && callback === updateAudioAnalysis) ||
                      fnName === 'drawViz' || fnName === 'updateAudioAnalysis';

      if (!isVizFn || vizFrameInterval <= 17) {
        // Not a viz function or running at 60fps — no throttle
        return _origRAF.call(window, callback);
      }

      // Throttle: wrap in a timer-based delay
      return _origRAF.call(window, function(timestamp) {
        if (timestamp - vizLastFrame < vizFrameInterval) {
          // Too soon — reschedule via orig rAF (will check again next frame)
          _origRAF.call(window, function(ts) {
            // Let it through on next native frame
            vizLastFrame = ts;
            callback(ts);
          });
          return;
        }
        vizLastFrame = timestamp;
        callback(timestamp);
      });
    };

    console.log('[AETHER Mobile] Viz rAF throttle enabled');
  }

  function disableVizThrottle() {
    if (!vizThrottleActive) return;
    vizThrottleActive = false;
    window.requestAnimationFrame = _origRAF;
    console.log('[AETHER Mobile] Viz rAF throttle disabled');
  }

  // ============ SINGLE-TAP PLAYBACK ON MOBILE ============
  // Desktop uses dblclick on track rows. On mobile, dblclick doesn't fire
  // reliably from touch events. We convert single taps to dblclick events.
  //
  // CRITICAL AUDIO FIX:
  // initAudioContext() creates a MediaElementSource which captures ALL audio
  // output into the Web Audio graph. If AudioContext is suspended (which it
  // always starts as on mobile), audio.play() produces silence because the
  // output goes into a suspended graph. We MUST await audioCtx.resume()
  // before dispatching dblclick, ensuring the graph is running when audio
  // starts. audio.play() works from synthetic events in Capacitor WebView
  // so the await doesn't break playback.
  let vizStarted = false;
  function enableSingleTapPlayback() {
    document.addEventListener('click', async (e) => {
      const trackRow = e.target.closest('.track-row');
      if (!trackRow) return;
      if (e.target.closest('.context-menu-item') || e.target.closest('button')) return;

      // Init AudioContext in real user gesture (needed for Web Audio + viz)
      if (typeof initAudioContext === 'function') initAudioContext();

      // AWAIT resume — AudioContext MUST be running before audio.play()
      // fires in the dblclick handler, otherwise MediaElementSource
      // captures audio into a suspended graph = silence.
      try {
        if (typeof audioCtx !== 'undefined' && audioCtx && audioCtx.state === 'suspended') {
          await audioCtx.resume();
          console.log('[AETHER Mobile] AudioContext resumed, state:', audioCtx.state);
        }
      } catch(e2) {
        console.warn('[AETHER Mobile] AudioContext resume failed:', e2);
      }

      // Start viz on first track play
      if (!vizStarted) {
        vizStarted = true;
        startVizOnPlay();
      }

      trackRow.dispatchEvent(new MouseEvent('dblclick', { bubbles: true }));
    });

    // Also handle play button taps (for resume/play from now-playing bar)
    document.addEventListener('click', async (e) => {
      if (!e.target.closest('#btnPlay') && !e.target.closest('.play-btn')) return;
      try {
        if (typeof audioCtx !== 'undefined' && audioCtx && audioCtx.state === 'suspended') {
          await audioCtx.resume();
        }
      } catch(e2) { /* ignore */ }
    });

    console.log('[AETHER Mobile] Single-tap playback enabled');
  }

  // ============ KILL HEAVY VISUALS ON MOBILE ============
  // Kill ambient particles + splash (too heavy for phone), but KEEP vizCanvas alive.
  // The early rAF kill switch stops ALL loops on page load. We'll revive just the
  // viz loop when the user plays their first track (see startVizOnPlay).
  function killHeavyVisualsOnMobile() {
    // Kill all rAF loops initially (saves battery during init/library browsing)
    if (typeof window.__killAnimations === 'function') {
      window.__killAnimations();
      console.log('[AETHER Mobile] rAF loops killed (will revive viz on play)');
    }

    // Kill ambient particles and splash — these stay dead
    const ambientCanvas = document.getElementById('ambientParticles');
    if (ambientCanvas) { ambientCanvas.width = 0; ambientCanvas.height = 0; ambientCanvas.style.display = 'none'; }

    const splashCanvas = document.getElementById('splashCanvas');
    if (splashCanvas) { splashCanvas.width = 0; splashCanvas.height = 0; }

    // Hide sheep video
    const sv = document.getElementById('sheepVideo');
    if (sv) { sv.pause(); sv.style.display = 'none'; sv.removeAttribute('src'); }

    // Block beat-reactive CSS vars on mobile — prevents UI flicker from
    // updateAudioAnalysis() setting --beat/--bass/--energy at 60fps which
    // drives box-shadow recalc on 50+ elements. Viz canvas reads from the
    // `bands` object directly, so it still works fine.
    const rootStyle = document.documentElement.style;
    const origSetProp = rootStyle.setProperty.bind(rootStyle);
    const beatVars = new Set(['--beat', '--bass', '--energy', '--mid', '--high']);
    beatVars.forEach(v => origSetProp(v, '0'));
    rootStyle.setProperty = function(prop, val, priority) {
      if (beatVars.has(prop)) return;
      return origSetProp(prop, val, priority);
    };

    console.log('[AETHER Mobile] Heavy visuals killed, beat-reactive CSS blocked');
  }

  // ============ PREPARE VIZ FOR MOBILE ============
  // Lock to scene 0 (FRACTAL FLAME) and ensure panel is visible
  function prepareVizForMobile() {
    // Don't lock to a single scene — let user cycle through all visualizers
    // by tapping the viz panel (handled by player.html's click handler)
    console.log('[AETHER Mobile] Viz prepared (all scenes available)');
  }

  // ============ START VIZ ON FIRST PLAY ============
  // Called once when user first taps a track. Revives rAF and kicks off
  // drawViz + updateAudioAnalysis loops. AudioContext is already initialized
  // and resumed in the click handler.
  function startVizOnPlay() {
    // Revive requestAnimationFrame (was killed on page load)
    if (typeof window.__reviveAnimations === 'function') {
      window.__reviveAnimations();
      console.log('[AETHER Mobile] rAF revived');
    }

    // Resize viz canvas to its panel
    if (typeof vizResize === 'function') vizResize();

    // Lock to fractal flame
    if (typeof currentScene !== 'undefined') currentScene = 0;

    // Enable the rAF throttle — drawViz self-schedules, so our patched rAF
    // intercepts those calls and enforces the target FPS
    enableVizThrottle();

    // Kick-start viz loops (they self-schedule via the now-throttled rAF)
    if (typeof drawViz === 'function') requestAnimationFrame(drawViz);
    if (typeof updateAudioAnalysis === 'function') requestAnimationFrame(updateAudioAnalysis);

    console.log('[AETHER Mobile] Viz started (FRACTAL FLAME, throttled to ' + vizTargetFps + 'fps)');
  }

  // ============ AUDIO DIAGNOSTICS ============
  function installAudioDiagnostics() {
    const audio = document.querySelector('audio');
    if (!audio) return;
    ['loadstart','canplay','playing','stalled','waiting','ended','pause'].forEach(evt => {
      audio.addEventListener(evt, () => {
        console.log('[AETHER Audio]', evt, 'src:', audio.src?.substring(0, 80), 'readyState:', audio.readyState, 'paused:', audio.paused, 'volume:', audio.volume);
      });
    });
    // Error handler with full detail (separate from generic log above)
    audio.addEventListener('error', () => {
      console.error('[AETHER Audio] ERROR code:', audio.error?.code, 'msg:', audio.error?.message, 'src:', audio.src?.substring(0, 80));
    });
    console.log('[AETHER Mobile] Audio diagnostics installed');
  }

  initMobile();
})();

// ============ LOCAL LIBRARY OFFLINE FALLBACK ============
// Monkey-patches SUBSONIC object to route through Capacitor plugin
// when Navidrome is unreachable. Intercepts get(), streamUrl(), coverUrl().
(function() {
  'use strict';

  let isOffline = false;
  let localServerPort = 0;
  let localReady = false;
  let localReadyPromise = null;

  const LocalPlugin = () => window.Capacitor?.Plugins?.AetherLocalLibrary;

  // ── Ensure local subsystem is initialized ──
  async function ensureLocalReady() {
    if (localReady) return true;
    if (localReadyPromise) return localReadyPromise;

    localReadyPromise = (async () => {
      try {
        const plugin = LocalPlugin();
        if (!plugin) {
          console.warn('[AETHER Offline] AetherLocalLibrary plugin not available');
          localReadyPromise = null; // allow retry
          return false;
        }

        // Request permissions
        const perms = await plugin.requestPermissions();
        if (!perms.granted) {
          console.warn('[AETHER Offline] Storage permission denied');
          localReadyPromise = null; // allow retry
          return false;
        }

        // Start file server
        const server = await plugin.startFileServer();
        localServerPort = server.port;
        console.log('[AETHER Offline] File server on port', localServerPort);

        localReady = true;
        return true;
      } catch(e) {
        console.error('[AETHER Offline] Init failed:', e);
        localReadyPromise = null; // allow retry
        return false;
      }
    })();

    return localReadyPromise;
  }

  // ── Route a SUBSONIC.get() call through the local plugin ──
  async function localGet(endpoint, params) {
    const plugin = LocalPlugin();
    if (!plugin) throw new Error('Local plugin unavailable');

    switch (endpoint) {
      case 'getAlbumList2':
        return await plugin.getAlbumList2(params);
      case 'getAlbum':
        return await plugin.getAlbum(params);
      case 'getArtists':
        return await plugin.getArtists(params);
      case 'getArtist':
        return await plugin.getArtist(params);
      case 'getRandomSongs':
        return await plugin.getRandomSongs(params);
      case 'getGenres':
        return await plugin.getGenres(params);
      case 'getSongsByGenre':
        return await plugin.getSongsByGenre(params);
      case 'getSong':
        return await plugin.getSong(params);
      case 'search3':
        return await plugin.search3(params);
      case 'ping':
        return await plugin.ping(params);
      // Endpoints that don't apply offline — return empty success
      case 'startScan':
      case 'scrobble':
      case 'getPlaylists':
        return { status: 'ok', playlists: { playlist: [] } };
      case 'getPlaylist':
        return { status: 'ok', playlist: { entry: [] } };
      case 'createPlaylist':
      case 'updatePlaylist':
      case 'getLyrics':
        return { status: 'ok' };
      default:
        console.warn('[AETHER Offline] Unhandled endpoint:', endpoint);
        return { status: 'ok' };
    }
  }

  // ── Wait for SUBSONIC to be defined, then patch it ──
  function patchSubsonic() {
    if (typeof SUBSONIC === 'undefined') {
      setTimeout(patchSubsonic, 50);
      return;
    }

    const originalGet = SUBSONIC.get.bind(SUBSONIC);
    const originalStreamUrl = SUBSONIC.streamUrl.bind(SUBSONIC);
    const originalCoverUrl = SUBSONIC.coverUrl.bind(SUBSONIC);

    // ── Patched get(): try Navidrome first, fallback to local ──
    SUBSONIC.get = async function(endpoint, params = {}) {
      if (isOffline) {
        await ensureLocalReady();
        return localGet(endpoint, params);
      }

      try {
        return await originalGet(endpoint, params);
      } catch(e) {
        console.warn('[AETHER Offline] Network call failed for', endpoint, '— switching to offline mode');
        isOffline = true;
        const ready = await ensureLocalReady();
        if (!ready) throw e;
        return localGet(endpoint, params);
      }
    };

    // ── Patched streamUrl(): local files served at same-origin /_audio/ path ──
    // Served by AetherWebViewClient.shouldInterceptRequest() — same origin as
    // the WebView (http://localhost), so MediaElementAudioSource works without
    // CORS restrictions. No external file server needed for audio.
    SUBSONIC.streamUrl = function(id) {
      if (typeof id === 'string' && id.startsWith('local_')) {
        const mediaId = id.replace('local_', '');
        const url = `/_audio/${mediaId}`;
        console.log('[AETHER Offline] streamUrl:', url);
        return url;
      }
      return originalStreamUrl(id);
    };

    // ── Patched coverUrl(): local albums go through file server ──
    // Album art still uses the file server (CORS doesn't matter for <img>).
    SUBSONIC.coverUrl = function(id, size = 300) {
      if (typeof id === 'string' && id.startsWith('local_album_')) {
        const albumId = id.replace('local_album_', '');
        return `http://127.0.0.1:${localServerPort}/art/${albumId}?size=${size}`;
      }
      return originalCoverUrl(id, size);
    };

    console.log('[AETHER Offline] SUBSONIC patched with offline fallback');
  }

  // ── MutationObserver: detect CONNECTION FAILED and trigger local library ──
  function observeForConnectionFailure() {
    const dc = document.getElementById('dynamicContent');
    if (!dc) {
      // DOM not ready yet, retry
      setTimeout(observeForConnectionFailure, 100);
      return;
    }

    const observer = new MutationObserver(async (mutations) => {
      // Check if the "CONNECTION FAILED" message appeared
      if (dc.textContent.includes('CONNECTION FAILED')) {
        observer.disconnect();
        console.log('[AETHER Offline] Connection failure detected — loading local library');

        isOffline = true;
        const ready = await ensureLocalReady();
        if (!ready) {
          dc.innerHTML = `
            <div style="padding:40px;text-align:center;">
              <div style="font-family:var(--font-display);color:var(--purple-vivid);font-size:1.2rem;margin-bottom:12px;">OFFLINE MODE UNAVAILABLE</div>
              <div style="font-family:var(--font-mono);color:var(--purple-muted);font-size:0.8rem;">Storage permission required for local playback</div>
            </div>`;
          return;
        }

        // Trigger the library view — it now routes through our patched SUBSONIC
        if (typeof loadLibraryView === 'function') {
          loadLibraryView();
        }
      }
    });

    observer.observe(dc, { childList: true, subtree: true });
  }

  // Start observing and patching
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
      patchSubsonic();
      observeForConnectionFailure();
    });
  } else {
    patchSubsonic();
    observeForConnectionFailure();
  }
})();
