/**
 * Automatische Updates ueber GitHub Releases (electron-updater).
 * Nach dem Start und danach alle paar Stunden wird nach einer neuen Version gesucht,
 * ein Update wird im Hintergrund geladen und beim Beenden installiert (oder sofort per Knopf).
 * Nur in der installierten Version aktiv, nicht in der Entwicklung.
 */
import { app } from 'electron';
import { autoUpdater } from 'electron-updater';
import type { UpdateStatus } from '../shared/types';

let status: UpdateStatus = { state: 'idle' };
let notify: (s: UpdateStatus) => void = () => undefined;

function set(next: UpdateStatus) {
  status = next;
  notify(next);
}

export function getUpdateStatus(): UpdateStatus {
  return status;
}

export async function checkForUpdates(): Promise<UpdateStatus> {
  if (!app.isPackaged) {
    set({ state: 'none', error: 'Updates gibt es nur in der installierten Version.' });
    return status;
  }
  if (status.state === 'downloading' || status.state === 'ready') return status;
  try {
    await autoUpdater.checkForUpdates();
  } catch (e) {
    set({ state: 'error', error: (e as Error).message });
  }
  return status;
}

/** Installiert ein bereits geladenes Update sofort (App startet neu) */
export function installUpdate(): void {
  if (status.state === 'ready') autoUpdater.quitAndInstall(false, true);
}

export function initUpdater(onStatus: (s: UpdateStatus) => void): void {
  notify = onStatus;
  if (!app.isPackaged) return;

  autoUpdater.autoDownload = true;
  autoUpdater.autoInstallOnAppQuit = true;

  autoUpdater.on('checking-for-update', () => set({ state: 'checking' }));
  autoUpdater.on('update-available', (info) => set({ state: 'downloading', version: info.version, percent: 0 }));
  autoUpdater.on('update-not-available', () => set({ state: 'none' }));
  autoUpdater.on('download-progress', (p) => set({ state: 'downloading', version: status.version, percent: Math.round(p.percent) }));
  autoUpdater.on('update-downloaded', (info) => set({ state: 'ready', version: info.version }));
  autoUpdater.on('error', (e) => set({ state: 'error', error: e?.message ?? String(e) }));

  setTimeout(() => void checkForUpdates(), 6000);
  setInterval(() => void checkForUpdates(), 4 * 60 * 60 * 1000);
}
