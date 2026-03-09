/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║  AETHER — Preload Script (Context Bridge)                       ║
 * ╠══════════════════════════════════════════════════════════════════╣
 * ║  Exposes safe IPC methods to the renderer process.              ║
 * ║  contextIsolation: true — no direct Node access in renderer.    ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * DEVNOTES:
 * - Window controls (min/max/close) use one-way IPC (send)
 * - isMaximized uses two-way IPC (invoke) for return value
 * - scanSheepFolder uses IPC invoke to main process (avoids sandbox issues)
 * - All paths are normalized to forward slashes for cross-platform compat
 */

const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('electronAPI', {
  minimize: () => ipcRenderer.send('window-minimize'),
  maximize: () => ipcRenderer.send('window-maximize'),
  close: () => ipcRenderer.send('window-close'),
  isMaximized: () => ipcRenderer.invoke('window-is-maximized'),
  onWindowState: (callback) => {
    ipcRenderer.on('window-state', (_event, state) => callback(state));
  },
  // Electric Sheep: scan a folder for video files (via main process IPC)
  scanSheepFolder: (folderPath) => ipcRenderer.invoke('scan-sheep-folder', folderPath),
  // Native folder picker dialog
  openFolderDialog: (title) => ipcRenderer.invoke('open-folder-dialog', title),
  // Native file picker dialog (multi-select)
  openFilesDialog: (title, extensions) => ipcRenderer.invoke('open-files-dialog', title, extensions),
  // Wallpaper Engine: scan workshop folder for video wallpapers
  scanWallpaperEngine: () => ipcRenderer.invoke('scan-wallpaper-engine'),
});
