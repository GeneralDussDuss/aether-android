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
