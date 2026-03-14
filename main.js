/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║  AETHER — Audio Engine for Total Harmonic Experience & Rendering ║
 * ║  Main Process (Electron)                                        ║
 * ╠══════════════════════════════════════════════════════════════════╣
 * ║  Creates frameless OLED-black window, system tray, IPC bridge.  ║
 * ║  WebSecurity disabled to allow localhost Navidrome API calls.    ║
 * ║  Background throttling OFF to keep visualizers running smooth.   ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * DEVNOTES:
 * - F12 opens DevTools for debugging API calls
 * - CSP headers are overridden to allow mixed content from Navidrome
 * - Tray icon is generated programmatically (16x16 purple square)
 * - Window state (maximized/normal) is broadcast to renderer via IPC
 * - TODO: Add auto-updater support
 * - TODO: Add media key support (play/pause/next/prev)
 * - TODO: Add MPRIS/Windows media session integration
 */

const { app, BrowserWindow, ipcMain, Tray, Menu, nativeImage, screen, session, dialog } = require('electron');
const path = require('path');
const fs = require('fs');
const net = require('net');

let mainWindow = null;
let tray = null;

function createWindow() {
  const { width: screenW, height: screenH } = screen.getPrimaryDisplay().workAreaSize;

  mainWindow = new BrowserWindow({
    width: Math.min(1400, screenW),
    height: Math.min(900, screenH),
    minWidth: 800,
    minHeight: 600,
    frame: false,
    titleBarStyle: 'hidden',
    backgroundColor: '#000000',
    show: false,
    icon: path.join(__dirname, 'icon.ico'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      webSecurity: false,  // Allow fetch to localhost Navidrome from file:// origin
      webgl: true,
      backgroundThrottling: false,
    },
  });

  // Remove CSP headers that block localhost API calls
  session.defaultSession.webRequest.onHeadersReceived((details, callback) => {
    callback({
      responseHeaders: {
        ...details.responseHeaders,
        'Content-Security-Policy': ["default-src * 'unsafe-inline' 'unsafe-eval' data: blob:"]
      }
    });
  });

  mainWindow.loadFile('player.html');

  // Open DevTools to debug API calls (press F12 to toggle)
  mainWindow.webContents.on('before-input-event', (event, input) => {
    if (input.key === 'F12') mainWindow.webContents.toggleDevTools();
  });

  // Prevent white flash
  mainWindow.once('ready-to-show', () => {
    mainWindow.show();
  });

  mainWindow.on('maximize', () => {
    mainWindow.webContents.send('window-state', 'maximized');
  });

  mainWindow.on('unmaximize', () => {
    mainWindow.webContents.send('window-state', 'normal');
  });

  mainWindow.on('closed', () => {
    mainWindow = null;
  });
}

function createTray() {
  // Create a simple 16x16 tray icon programmatically (purple P on black)
  const size = 16;
  const canvas = Buffer.alloc(size * size * 4);
  // Fill transparent
  for (let i = 0; i < size * size; i++) {
    const offset = i * 4;
    // Simple purple square with dark bg
    const x = i % size;
    const y = Math.floor(i / size);
    if (x >= 2 && x <= 13 && y >= 2 && y <= 13) {
      canvas[offset] = 157;     // R
      canvas[offset + 1] = 78;  // G
      canvas[offset + 2] = 221; // B
      canvas[offset + 3] = 200; // A
    } else {
      canvas[offset + 3] = 0;
    }
  }

  const img = nativeImage.createFromBuffer(canvas, { width: size, height: size });
  tray = new Tray(img);
  tray.setToolTip('AETHER');

  const contextMenu = Menu.buildFromTemplate([
    {
      label: 'Show AETHER',
      click: () => {
        if (mainWindow) {
          mainWindow.show();
          mainWindow.focus();
        }
      },
    },
    { type: 'separator' },
    {
      label: 'Quit',
      click: () => {
        app.quit();
      },
    },
  ]);

  tray.setContextMenu(contextMenu);
  tray.on('double-click', () => {
    if (mainWindow) {
      mainWindow.show();
      mainWindow.focus();
    }
  });
}

// Catch crashes
process.on('uncaughtException', (err) => {
  console.error('UNCAUGHT:', err);
});

// Force Chromium to use the default Windows audio output (fixes silent audio on some systems)
app.commandLine.appendSwitch('disable-features', 'AudioServiceSandbox');
app.commandLine.appendSwitch('autoplay-policy', 'no-user-gesture-required');

app.whenReady().then(() => {
  createWindow();
  createTray();
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});

app.on('activate', () => {
  if (BrowserWindow.getAllWindows().length === 0) createWindow();
});

// ============ IPC HANDLERS — WINDOW CONTROLS ============

ipcMain.on('window-minimize', () => {
  if (mainWindow) mainWindow.minimize();
});

ipcMain.on('window-maximize', () => {
  if (mainWindow) {
    if (mainWindow.isMaximized()) {
      mainWindow.unmaximize();
    } else {
      mainWindow.maximize();
    }
  }
});

ipcMain.on('window-close', () => {
  if (mainWindow) mainWindow.close();
});

ipcMain.handle('window-is-maximized', () => {
  return mainWindow ? mainWindow.isMaximized() : false;
});

// ============ IPC HANDLERS — FOLDER PICKER ============

ipcMain.handle('open-folder-dialog', async (_event, title) => {
  if (!mainWindow) return null;
  const result = await dialog.showOpenDialog(mainWindow, {
    title: title || 'Select Folder',
    properties: ['openDirectory'],
  });
  if (result.canceled || result.filePaths.length === 0) return null;
  return result.filePaths[0];
});

ipcMain.handle('open-files-dialog', async (_event, title, extensions) => {
  if (!mainWindow) return [];
  const result = await dialog.showOpenDialog(mainWindow, {
    title: title || 'Select Files',
    properties: ['openFile', 'multiSelections'],
    filters: extensions ? [{ name: 'Videos', extensions }] : [],
  });
  if (result.canceled) return [];
  return result.filePaths;
});

// Wallpaper Engine: scan workshop folder for video wallpapers with metadata
ipcMain.handle('scan-wallpaper-engine', () => {
  const wpPaths = [
    path.join('C:', 'Program Files (x86)', 'Steam', 'steamapps', 'workshop', 'content', '431960'),
    path.join('D:', 'SteamLibrary', 'steamapps', 'workshop', 'content', '431960'),
    path.join('E:', 'SteamLibrary', 'steamapps', 'workshop', 'content', '431960'),
  ];
  let wpRoot = null;
  for (const p of wpPaths) {
    if (fs.existsSync(p)) { wpRoot = p; break; }
  }
  if (!wpRoot) return [];
  const results = [];
  try {
    const dirs = fs.readdirSync(wpRoot);
    for (const dir of dirs) {
      const dirPath = path.join(wpRoot, dir);
      if (!fs.statSync(dirPath).isDirectory()) continue;
      const files = fs.readdirSync(dirPath);
      const mp4 = files.find(f => f.toLowerCase().endsWith('.mp4'));
      if (!mp4) continue;
      let title = dir;
      const projFile = path.join(dirPath, 'project.json');
      if (fs.existsSync(projFile)) {
        try {
          const proj = JSON.parse(fs.readFileSync(projFile, 'utf-8'));
          if (proj.title) title = proj.title;
        } catch(e) {}
      }
      results.push({
        id: dir,
        title: title,
        path: path.join(dirPath, mp4).replace(/\\/g, '/'),
      });
    }
  } catch(e) {
    console.error('Wallpaper Engine scan error:', e);
  }
  return results;
});

// Electric Sheep: scan folder for video files (runs in main process, no sandbox issue)
ipcMain.handle('scan-sheep-folder', (_event, folderPath) => {
  try {
    if (!fs.existsSync(folderPath)) return [];
    const files = fs.readdirSync(folderPath);
    const videoExts = ['.mp4', '.webm', '.mkv', '.avi', '.mov', '.m4v'];
    return files
      .filter(f => videoExts.includes(path.extname(f).toLowerCase()))
      .map(f => path.join(folderPath, f).replace(/\\/g, '/'));
  } catch (e) {
    console.error('Sheep scan error:', e);
    return [];
  }
});

// ============ DISCORD RICH PRESENCE (Local IPC) ============

// Discord Application ID — user can configure this in settings
let discordClient = null;
let discordConnected = false;
let discordAppId = null;

function encodeDiscordPacket(op, data) {
  const payload = JSON.stringify(data);
  const len = Buffer.byteLength(payload);
  const buf = Buffer.alloc(8 + len);
  buf.writeInt32LE(op, 0);
  buf.writeInt32LE(len, 4);
  buf.write(payload, 8);
  return buf;
}

function parseDiscordPacket(buf) {
  if (buf.length < 8) return null;
  const op = buf.readInt32LE(0);
  const len = buf.readInt32LE(4);
  if (buf.length < 8 + len) return null;
  try {
    const data = JSON.parse(buf.slice(8, 8 + len).toString());
    return { op, data };
  } catch(e) {
    return null;
  }
}

function connectDiscord(appId) {
  return new Promise((resolve) => {
    if (discordClient) {
      try { discordClient.destroy(); } catch(e) {}
      discordClient = null;
    }
    discordConnected = false;
    discordAppId = appId;

    const pipePath = '\\\\.\\pipe\\discord-ipc-0';
    let dataBuffer = Buffer.alloc(0);

    discordClient = net.createConnection(pipePath, () => {
      console.log('[Discord RPC] Connected to pipe, sending handshake...');
      const handshake = encodeDiscordPacket(0, { v: 1, client_id: appId });
      discordClient.write(handshake);
    });

    discordClient.on('data', (chunk) => {
      dataBuffer = Buffer.concat([dataBuffer, chunk]);
      const packet = parseDiscordPacket(dataBuffer);
      if (packet) {
        dataBuffer = dataBuffer.slice(8 + Buffer.byteLength(JSON.stringify(packet.data)));
        if (packet.op === 1 && packet.data.cmd === 'DISPATCH' && packet.data.evt === 'READY') {
          console.log('[Discord RPC] Handshake complete, user:', packet.data.data?.user?.username);
          discordConnected = true;
          resolve({ connected: true });
        }
      }
    });

    discordClient.on('error', (err) => {
      console.warn('[Discord RPC] Connection error:', err.message);
      discordConnected = false;
      discordClient = null;
      resolve({ connected: false });
    });

    discordClient.on('close', () => {
      console.log('[Discord RPC] Connection closed');
      discordConnected = false;
      discordClient = null;
    });

    // Timeout after 5s
    setTimeout(() => {
      if (!discordConnected) {
        resolve({ connected: false });
      }
    }, 5000);
  });
}

function setDiscordActivity(trackData) {
  if (!discordClient || !discordConnected) return { connected: false };

  const nonce = Math.random().toString(36).slice(2, 12);
  const activity = {
    details: trackData.title ? `${trackData.title}` : 'Idle',
    state: trackData.artist ? `by ${trackData.artist}` : undefined,
    timestamps: {},
    assets: {
      large_image: 'aether_logo',
      large_text: trackData.album || 'AETHER',
      small_image: trackData.playing ? 'play' : 'pause',
      small_text: trackData.playing ? 'Playing' : 'Paused'
    }
  };

  if (trackData.playing && trackData.duration > 0) {
    const now = Math.floor(Date.now() / 1000);
    activity.timestamps.start = now - Math.floor(trackData.elapsed || 0);
    activity.timestamps.end = now + Math.floor(trackData.duration - (trackData.elapsed || 0));
  }

  const payload = encodeDiscordPacket(1, {
    cmd: 'SET_ACTIVITY',
    args: { pid: process.pid, activity },
    nonce
  });

  try {
    discordClient.write(payload);
    return { connected: true };
  } catch(e) {
    discordConnected = false;
    return { connected: false };
  }
}

function clearDiscordActivity() {
  if (!discordClient || !discordConnected) return { connected: false };
  const nonce = Math.random().toString(36).slice(2, 12);
  const payload = encodeDiscordPacket(1, {
    cmd: 'SET_ACTIVITY',
    args: { pid: process.pid, activity: null },
    nonce
  });
  try {
    discordClient.write(payload);
    return { connected: true };
  } catch(e) {
    return { connected: false };
  }
}

ipcMain.handle('connect-discord', async (_event, appId) => {
  return await connectDiscord(appId);
});

ipcMain.handle('set-discord-presence', (_event, trackData) => {
  return setDiscordActivity(trackData);
});

ipcMain.handle('clear-discord-presence', () => {
  return clearDiscordActivity();
});

// Clean up Discord connection on app quit
app.on('before-quit', () => {
  if (discordClient) {
    try {
      clearDiscordActivity();
      discordClient.destroy();
    } catch(e) {}
    discordClient = null;
  }
});

// ============ STEM ISOLATION — File System Checks ============

ipcMain.handle('check-stems', (_event, opts) => {
  const { stemsFolder, trackId, title, artist } = opts;
  if (!stemsFolder || !fs.existsSync(stemsFolder)) return { found: false };

  const stemFiles = ['vocals', 'drums', 'bass', 'other'];
  const stemExts = ['.wav', '.flac', '.mp3', '.ogg', '.m4a'];

  // Strategy 1: Check by track ID
  const idFolder = path.join(stemsFolder, trackId);
  if (fs.existsSync(idFolder)) {
    const paths = {};
    let foundAll = true;
    for (const stem of stemFiles) {
      let found = false;
      for (const ext of stemExts) {
        const p = path.join(idFolder, stem + ext);
        if (fs.existsSync(p)) {
          paths[stem] = p.replace(/\\/g, '/');
          found = true;
          break;
        }
      }
      if (!found) foundAll = false;
    }
    if (Object.keys(paths).length >= 2) return { found: true, paths };
  }

  // Strategy 2: Check by "Artist - Title" folder name
  if (artist && title) {
    const safeName = `${artist} - ${title}`.replace(/[<>:"/\\|?*]/g, '_');
    const nameFolder = path.join(stemsFolder, safeName);
    if (fs.existsSync(nameFolder)) {
      const paths = {};
      for (const stem of stemFiles) {
        for (const ext of stemExts) {
          const p = path.join(nameFolder, stem + ext);
          if (fs.existsSync(p)) {
            paths[stem] = p.replace(/\\/g, '/');
            break;
          }
        }
      }
      if (Object.keys(paths).length >= 2) return { found: true, paths };
    }
  }

  // Strategy 3: Check by title only
  if (title) {
    const safeTitle = title.replace(/[<>:"/\\|?*]/g, '_');
    const titleFolder = path.join(stemsFolder, safeTitle);
    if (fs.existsSync(titleFolder)) {
      const paths = {};
      for (const stem of stemFiles) {
        for (const ext of stemExts) {
          const p = path.join(titleFolder, stem + ext);
          if (fs.existsSync(p)) {
            paths[stem] = p.replace(/\\/g, '/');
            break;
          }
        }
      }
      if (Object.keys(paths).length >= 2) return { found: true, paths };
    }
  }

  // Strategy 4: Scan all subdirectories for a match (case-insensitive)
  try {
    const dirs = fs.readdirSync(stemsFolder);
    const lowerTitle = (title || '').toLowerCase();
    const lowerArtist = (artist || '').toLowerCase();
    for (const dir of dirs) {
      const dirLower = dir.toLowerCase();
      if (dirLower.includes(lowerTitle) && lowerTitle.length > 2) {
        const dirPath = path.join(stemsFolder, dir);
        if (!fs.statSync(dirPath).isDirectory()) continue;
        const paths = {};
        for (const stem of stemFiles) {
          for (const ext of stemExts) {
            const p = path.join(dirPath, stem + ext);
            if (fs.existsSync(p)) {
              paths[stem] = p.replace(/\\/g, '/');
              break;
            }
          }
        }
        if (Object.keys(paths).length >= 2) return { found: true, paths };
      }
    }
  } catch(e) {
    console.error('Stem scan error:', e);
  }

  return { found: false };
});

ipcMain.handle('get-stem-path', (_event, opts) => {
  const { stemsFolder, trackId, stem } = opts;
  if (!stemsFolder) return null;
  const stemExts = ['.wav', '.flac', '.mp3', '.ogg', '.m4a'];
  const idFolder = path.join(stemsFolder, trackId);
  if (fs.existsSync(idFolder)) {
    for (const ext of stemExts) {
      const p = path.join(idFolder, stem + ext);
      if (fs.existsSync(p)) return p.replace(/\\/g, '/');
    }
  }
  return null;
});
