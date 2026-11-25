/**
 * DeuClient - Electron-Main-Prozess.
 * Erstellt das rahmenlose Hauptfenster, das Konsolenfenster und registriert alle IPC-Kanäle.
 */
import { app, BrowserWindow, dialog, ipcMain, screen, shell, nativeImage } from 'electron';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import type { EventMap, LaunchOptions, ProgressEvent, Settings } from '../shared/types';
import * as accounts from './auth/accounts';
import { deuCoreInfo, managedModsFor, removedManaged, restoreManaged } from './game/deucore';
import * as instances from './game/instances';
import { findJavaInstalls, downloadTemurin } from './game/java';
import { getGameState, killGame, launchGame } from './game/launch';
import { listFabricGameVersions, listVersions, getManifest } from './game/versions';
import * as modrinth from './modrinth';
import * as servers from './servers';
import * as capes from './capes';
import { fetchJson } from './util/download';
import { friendlyMessage } from './util/errors';
import { getSettings, saveSettings } from './settings';
import { checkForUpdates, getUpdateStatus, initUpdater, installUpdate } from './updater';
import { migrateLegacyData } from './util/migrate';
import { DATA_DIR, ensureBaseDirs, paths } from './util/paths';

const isDev = !!process.env.VITE_DEV_SERVER_URL;
const ICON = path.join(__dirname, '..', '..', isDev ? 'public' : 'dist', 'icon.png');

let mainWindow: BrowserWindow | null = null;
let consoleWindow: BrowserWindow | null = null;

function dirSize(p: string): number {
  let total = 0;
  for (const e of fs.readdirSync(p, { withFileTypes: true })) {
    const full = path.join(p, e.name);
    try {
      total += e.isDirectory() ? dirSize(full) : fs.statSync(full).size;
    } catch {
      // Datei verschwunden
    }
  }
  return total;
}
/** Ringpuffer der Spielausgabe für das Konsolenfenster */
let logBuffer: string[] = [];
let minimizedByLaunch = false;

/* ---------------- Fenster ---------------- */

function loadRenderer(win: BrowserWindow, hash = '') {
  if (isDev) void win.loadURL(`${process.env.VITE_DEV_SERVER_URL}${hash ? `#${hash}` : ''}`);
  else void win.loadFile(path.join(__dirname, '..', '..', 'dist', 'index.html'), { hash });
}

function secureWebContents(win: BrowserWindow) {
  // Links aus der Oberfläche (z. B. Modrinth-Seiten) im Standardbrowser öffnen
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https:\/\//.test(url)) void shell.openExternal(url);
    return { action: 'deny' };
  });
  win.webContents.on('will-navigate', (e, url) => {
    if (!url.startsWith('file://') && !(isDev && url.startsWith(process.env.VITE_DEV_SERVER_URL!))) e.preventDefault();
  });
}

function createMainWindow() {
  // DEUCLIENT_WINDOW=<Breite>x<Hoehe>: nur fuer Tests (Layout bei verschiedenen Fenstergroessen pruefen)
  const [testW, testH] = (process.env.DEUCLIENT_WINDOW ?? '').split('x').map(Number);
  // Fenster nie groesser als der nutzbare Bildschirm (sonst verschwindet unten etwas hinter der Taskleiste)
  const work = screen.getPrimaryDisplay().workAreaSize;
  const startW = Math.min(testW || 1240, work.width);
  const startH = Math.min(testH || 780, work.height);
  mainWindow = new BrowserWindow({
    width: startW,
    height: startH,
    minWidth: Math.min(900, startW),
    minHeight: Math.min(580, startH),
    frame: false, // eigene Titelleiste
    backgroundColor: '#0a0d0b',
    title: 'DeuClient',
    icon: nativeImage.createFromPath(ICON),
    show: false,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      // Testmodus: Fenster ist unsichtbar → Animationen nicht drosseln
      backgroundThrottling: !process.env.DEUCLIENT_SCREENSHOT,
    },
  });
  secureWebContents(mainWindow);
  // Im Screenshot-Testmodus bleibt das Fenster unsichtbar (stiehlt keinen Fokus)
  mainWindow.once('ready-to-show', () => {
    if (!process.env.DEUCLIENT_SCREENSHOT) return mainWindow?.show();
    mainWindow?.setPosition(-5000, 0); // außerhalb des Bildschirms
    mainWindow?.showInactive(); // sichtbar für Chromium, aber ohne Fokus
  });
  mainWindow.on('closed', () => {
    mainWindow = null;
    consoleWindow?.close();
  });
  loadRenderer(mainWindow);
}

function openConsoleWindow() {
  if (consoleWindow) {
    consoleWindow.show();
    consoleWindow.focus();
    return;
  }
  consoleWindow = new BrowserWindow({
    width: 900,
    height: 560,
    minWidth: 560,
    minHeight: 320,
    frame: false,
    backgroundColor: '#050706',
    title: 'DeuClient - Konsole',
    icon: nativeImage.createFromPath(ICON),
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, nodeIntegration: false, sandbox: true },
  });
  secureWebContents(consoleWindow);
  consoleWindow.on('closed', () => (consoleWindow = null));
  loadRenderer(consoleWindow, 'console');
}

/** Ereignis an alle Fenster senden */
function emit<K extends keyof EventMap>(channel: K, payload: EventMap[K]) {
  for (const w of BrowserWindow.getAllWindows()) if (!w.isDestroyed()) w.webContents.send(channel, payload);
}

function pushLog(lines: string[]) {
  logBuffer.push(...lines);
  if (logBuffer.length > 5000) logBuffer = logBuffer.slice(-5000);
  emit('game:log', lines);
}

/* ---------------- IPC ---------------- */

/**
 * Registriert einen IPC-Handler. Fehler werden als { ok:false, error } verpackt,
 * damit die Oberfläche saubere Fehlermeldungen (ohne Electron-Präfix) anzeigen kann.
 */
function handle(channel: string, fn: (...args: any[]) => unknown) {
  ipcMain.handle(channel, async (_e, ...args) => {
    try {
      return { ok: true, data: await fn(...args) };
    } catch (err) {
      console.error(`[IPC ${channel}]`, err);
      return { ok: false, error: friendlyMessage(err) };
    }
  });
}

function registerIpc() {
  // Fenster
  handle('window:minimize', () => BrowserWindow.getFocusedWindow()?.minimize());
  handle('window:maximize', () => {
    const w = BrowserWindow.getFocusedWindow();
    if (w) w.isMaximized() ? w.unmaximize() : w.maximize();
  });
  handle('window:close', () => BrowserWindow.getFocusedWindow()?.close());

  // System
  handle('app:systemInfo', () => ({
    totalMemMb: Math.floor(os.totalmem() / 1024 / 1024),
    platform: `${os.type()} ${os.release()}`,
    appVersion: app.getVersion(),
    dataDir: DATA_DIR,
  }));
  /** "Suche nach Updates": prüft die Erreichbarkeit der Mojang-/Fabric-Server und lädt das Versionsmanifest */
  handle('app:checkOnline', async () => {
    try {
      await getManifest();
      return true;
    } catch {
      return false;
    }
  });
  handle('app:openDataFolder', () => shell.openPath(DATA_DIR));
  handle('shell:openExternal', (url: string) => {
    if (/^https:\/\//.test(url)) return shell.openExternal(url);
  });
  handle('deucore:info', () => deuCoreInfo());

  // Updates
  handle('update:status', () => getUpdateStatus());
  handle('update:check', () => checkForUpdates());
  handle('update:install', () => installUpdate());

  // Einstellungen
  handle('settings:get', () => getSettings());
  handle('settings:save', (patch: Partial<Settings>) => {
    const saved = saveSettings(patch);
    if (patch.launchOnStartup !== undefined && app.isPackaged) app.setLoginItemSettings({ openAtLogin: saved.launchOnStartup });
    return saved;
  });
  handle('app:clearCache', () => {
    // Zwischenspeicher und Logs leeren (Spielstaende, Modpacks und Accounts bleiben unberuehrt)
    let bytes = 0;
    const wipe = (dir: string) => {
      if (!fs.existsSync(dir)) return;
      for (const e of fs.readdirSync(dir)) {
        const p = path.join(dir, e);
        try {
          bytes += fs.statSync(p).isDirectory() ? dirSize(p) : fs.statSync(p).size;
          fs.rmSync(p, { recursive: true, force: true });
        } catch {
          // Datei in Benutzung: ueberspringen
        }
      }
    };
    wipe(paths.cache);
    wipe(paths.logs);
    return Math.round((bytes / 1024 / 1024) * 10) / 10;
  });

  // Accounts
  handle('accounts:get', () => accounts.getAccountsState());
  handle('accounts:refresh', () => accounts.refreshAllQuietly());
  handle('accounts:add', async () => {
    const s = await accounts.addAccount(mainWindow ?? undefined);
    emit('accounts:changed', s);
    return s;
  });
  handle('accounts:select', (uuid: string) => {
    const s = accounts.selectAccount(uuid);
    emit('accounts:changed', s);
    return s;
  });
  handle('accounts:remove', (uuid: string) => {
    const s = accounts.removeAccount(uuid);
    emit('accounts:changed', s);
    return s;
  });

  // Instanzen
  handle('instances:list', () => instances.listInstances());
  handle('instances:ensureDefault', () => instances.ensureDefaultInstance());
  handle('instances:create', async (input) => {
    const inst = instances.createInstance(input);
    emit('instances:changed', instances.listInstances());
    // Fabric API im Hintergrund vorinstallieren
    if (inst.loader === 'fabric') void modrinth.ensureFabricApi(inst.id);
    return inst;
  });
  handle('instances:update', (id: string, patch) => {
    const inst = instances.updateInstance(id, patch);
    emit('instances:changed', instances.listInstances());
    return inst;
  });
  handle('instances:delete', (id: string) => {
    instances.deleteInstance(id);
    emit('instances:changed', instances.listInstances());
  });
  handle('instances:openFolder', (id: string, sub?: string) => instances.openInstanceFolder(id, sub));
  handle('instances:worlds', (id: string) => instances.listWorlds(id));
  handle('instances:screenshots', (id: string) => instances.listScreenshots(id));
  handle('instances:openScreenshot', (id: string, file: string) => instances.openScreenshot(id, file));
  handle('instances:stats', (id: string) => instances.instanceStats(id));
  handle('instances:managedMods', (id: string) => {
    const inst = instances.getInstance(id);
    const removed = removedManaged(inst);
    return managedModsFor(inst).map((m) => ({ ...m, removed: removed.includes(m.file) }));
  });
  handle('instances:restoreManaged', (id: string, file?: string) => restoreManaged(instances.getInstance(id), file));

  // Versionen
  handle('versions:list', (snapshots?: boolean) => listVersions(!!snapshots));
  handle('versions:fabric', () => listFabricGameVersions());

  // Spiel
  handle('game:state', () => getGameState());
  handle('game:kill', () => killGame());
  handle('game:launch', async (opts: LaunchOptions) => {
    const settings = getSettings();
    logBuffer = [];
    if (settings.showConsole) openConsoleWindow();
    const progress = (e: ProgressEvent) => {
      emit('launch:progress', e);
      if (e.stage === 'running') {
        if (settings.afterLaunch === 'minimize' && mainWindow) {
          mainWindow.minimize();
          minimizedByLaunch = true;
        } else if (settings.afterLaunch === 'close') {
          setTimeout(() => app.quit(), 1500);
        }
      }
      if ((e.stage === 'done' || e.stage === 'error') && minimizedByLaunch) {
        mainWindow?.restore();
        minimizedByLaunch = false;
      }
      // Bei Absturz die Konsole zeigen, bei normalem Beenden von Minecraft schliessen
      if (e.stage === 'error') openConsoleWindow();
      else if (e.stage === 'done') consoleWindow?.close();
    };
    try {
      // Fabric API für die Standard-Instanz sicherstellen (DeuClient)
      await modrinth.ensureFabricApi(opts.instanceId).catch(() => undefined);
      await launchGame(opts, { progress, log: pushLog, state: (s) => emit('game:state', s) });
      emit('instances:changed', instances.listInstances());
    } catch (e) {
      const friendly = friendlyMessage(e);
      pushLog([`[DeuClient] FEHLER: ${friendly}`]);
      if (friendly !== (e as Error).message) pushLog([`[DeuClient] Technische Details: ${(e as Error).message}`]);
      if ((e as { accountsChanged?: boolean }).accountsChanged) emit('accounts:changed', accounts.getAccountsState());
      emit('launch:progress', { stage: 'error', text: friendly, percent: 1 });
      throw e;
    }
  });

  // Serverstatus

  // Eigene Server (auf diesem PC gehostet)
  handle('servers:list', () => servers.listServers());
  handle('servers:runtimes', () => servers.allRuntimes());
  handle('servers:create', (input) => servers.createServer(input, (text) => emit('server:progress', { id: '', text })));
  handle('servers:update', (id: string, patch) => servers.updateServer(id, patch));
  handle('servers:delete', (id: string) => servers.deleteServer(id));
  handle('servers:start', (id: string) => servers.startServer(id, (text) => emit('server:progress', { id, text })));
  handle('servers:stop', (id: string) => servers.stopServer(id));
  handle('servers:command', (id: string, cmd: string) => servers.sendCommand(id, cmd));
  handle('servers:log', (id: string) => servers.serverLog(id));
  handle('servers:addresses', (id: string) => servers.addresses(id));
  handle('servers:openFolder', (id: string, sub?: string) => servers.openServerFolder(id, sub));
  handle('servers:upnpOn', (id: string) => servers.enableUpnp(id));
  handle('servers:upnpOff', (id: string) => servers.disableUpnp(id));
  handle('servers:paperVersions', async () => {
    const data = await fetchJson<{ versions: Record<string, string[]> }>('https://fill.papermc.io/v3/projects/paper');
    return Object.values(data.versions).flat().filter((v) => /^\d+(\.\d+)+$/.test(v));
  });

  // Shop: eigene Capes
  handle('capes:list', () => capes.listCapes());
  handle('capes:save', (name: string, png: string, id?: string) => capes.saveCape(name, png, id));
  handle('capes:delete', (id: string) => capes.deleteCape(id));
  handle('capes:equipped', () => capes.getEquipped());
  handle('capes:equip', (sel) => capes.equip(sel));

  // Java
  handle('java:list', () => findJavaInstalls());
  let finishTimer: ReturnType<typeof setTimeout> | undefined;
  const startDownload = () => clearTimeout(finishTimer);
  const finishDownload = () => {
    clearTimeout(finishTimer);
    finishTimer = setTimeout(() => emit('download:progress', { stage: 'idle', text: '', percent: 1 }), 1500);
  };
  handle('java:download', async (major: number) => {
    startDownload();
    try {
      return await downloadTemurin(major, (text, percent) => emit('download:progress', { stage: 'java', text, percent }));
    } finally {
      finishDownload();
    }
  });
  handle('dialog:pickJava', async () => {
    const r = await dialog.showOpenDialog(mainWindow!, {
      title: 'Java auswählen (javaw.exe)',
      filters: [{ name: 'Java', extensions: ['exe'] }],
      properties: ['openFile'],
    });
    return r.canceled ? null : r.filePaths[0];
  });

  // Modrinth
  handle('modrinth:search', (p) => modrinth.search(p));
  handle('modrinth:categories', () => modrinth.categories());
  handle('modrinth:project', (id: string) => modrinth.project(id));
  handle('modrinth:projects', (ids: string[]) => modrinth.projects(ids));
  handle('modrinth:versions', (id: string, loaders?: string[], gv?: string[]) => modrinth.projectVersions(id, loaders, gv));
  handle('modrinth:install', async (instId: string, projectId: string, type, versionId?: string) => {
    startDownload();
    try {
      return await modrinth.install(instId, projectId, type, versionId, (e) => emit('download:progress', e));
    } finally {
      finishDownload();
    }
  });
  handle('modrinth:installed', (instId: string, type) => modrinth.listInstalled(instId, type));
  handle('modrinth:toggle', (instId: string, type, file: string) => modrinth.toggle(instId, type, file));
  handle('modrinth:remove', (instId: string, type, file: string) => modrinth.remove(instId, type, file));
  handle('modrinth:checkUpdates', (instId: string, type) => modrinth.checkUpdates(instId, type));
  handle('modrinth:update', (instId: string, type, file: string, pid: string, vid: string) =>
    modrinth.updateContent(instId, type, file, pid, vid),
  );
  handle('modrinth:importMrpack', async () => {
    const r = await dialog.showOpenDialog(mainWindow!, {
      title: 'Modrinth-Modpack importieren',
      filters: [{ name: 'Modrinth-Modpack', extensions: ['mrpack'] }],
      properties: ['openFile'],
    });
    if (r.canceled) return null;
    startDownload();
    try {
      const inst = await modrinth.importMrpack(r.filePaths[0], (e) => emit('download:progress', e));
      emit('instances:changed', instances.listInstances());
      return inst;
    } finally {
      finishDownload();
    }
  });

  // Konsole
  handle('console:open', () => openConsoleWindow());
  handle('console:getLogs', () => logBuffer);
  handle('console:clear', () => {
    logBuffer = [];
  });
  handle('console:openLogsFolder', () => {
    const id = getGameState().instanceId ?? getSettings().selectedInstance;
    instances.openInstanceFolder(id, 'logs');
  });
}

/* ---------------- App-Lebenszyklus ---------------- */

// Testmodus (DEUCLIENT_SCREENSHOT): eigene Instanz, damit ein laufender Launcher nicht gestört wird
if (!process.env.DEUCLIENT_SCREENSHOT && !app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (mainWindow) {
      if (mainWindow.isMinimized()) mainWindow.restore();
      mainWindow.focus();
    }
  });

  app.setAppUserModelId('eu.deuclient.app'); // eigenes Taskleisten-Icon unter Windows
  app.whenReady().then(() => {
    ensureBaseDirs();
    migrateLegacyData(); // Daten aus der Zeit vor der Umbenennung auf die neuen Namen heben, bevor Einstellungen und Instanzen gelesen werden
    registerIpc();
    servers.setServerEmitter((channel, payload) => emit(channel as keyof EventMap, payload as never));
    createMainWindow();
    if (!process.env.DEUCLIENT_SCREENSHOT) initUpdater((s) => emit('update:status', s));
    if (!process.env.DEUCLIENT_SCREENSHOT) initUpdater((s) => emit('update:status', s));
    if (process.env.DEUCLIENT_SCREENSHOT) runScreenshotTest();
  });
  app.on('window-all-closed', () => app.quit());

  // Laufende eigene Server beim Beenden sauber stoppen (Welt wird gespeichert)
  let stopping = false;
  app.on('before-quit', (e) => {
    if (stopping || !servers.allRuntimes().some((r) => r.running)) return;
    e.preventDefault();
    stopping = true;
    void servers.stopAll().finally(() => app.quit());
  });
}

/**
 * Test-Hilfe (nur aktiv mit Umgebungsvariable DEUCLIENT_SCREENSHOT=<datei.png>):
 * führt optional DEUCLIENT_SCREENSHOT_JS in der Oberfläche aus, speichert einen Screenshot und beendet die App.
 */
function runScreenshotTest() {
  const delay = Number(process.env.DEUCLIENT_SCREENSHOT_DELAY ?? 6000);
  mainWindow?.webContents.on('console-message', (e) => console.log('[renderer]', (e as unknown as { message: string }).message));
  setTimeout(async () => {
    if (!mainWindow) return;
    if (process.env.DEUCLIENT_SCREENSHOT_JS) {
      const result = await mainWindow.webContents.executeJavaScript(process.env.DEUCLIENT_SCREENSHOT_JS).catch((e) => String(e));
      if (result !== undefined) console.log('[test-js]', result);
      await new Promise((r) => setTimeout(r, Number(process.env.DEUCLIENT_SCREENSHOT_WAIT ?? 1500)));
    }
    if (process.env.DEUCLIENT_SCREENSHOT_PROBE) console.log('[probe]', await mainWindow.webContents.executeJavaScript(process.env.DEUCLIENT_SCREENSHOT_PROBE).catch((e) => String(e)));
    const img = await mainWindow.webContents.capturePage();
    require('node:fs').writeFileSync(process.env.DEUCLIENT_SCREENSHOT!, img.toPNG());
    app.quit();
  }, delay);
}

export { paths };
