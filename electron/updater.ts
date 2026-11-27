/**
 * Automatische Updates ueber GitHub Releases. An einem Release haengt nur der Installer (keine latest.yml,
 * keine Blockmap): Version, Download-Adresse und SHA-256-Pruefsumme kommen aus der GitHub-API.
 * Nach dem Start und danach alle paar Stunden wird nach einer neuen Version gesucht, ein Update wird im
 * Hintergrund geladen, geprueft und beim Beenden still installiert (oder sofort per Knopf).
 * Nur in der installierten Version aktiv, nicht in der Entwicklung.
 */
import { app } from 'electron';
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import type { UpdateStatus } from '../shared/types';
import { downloadFile, fetchJson, HttpError } from './util/download';
import { friendlyMessage } from './util/errors';
import { paths } from './util/paths';

const REPO = 'EmilDeuOfficial/DeuClient';
const INSTALLER = 'DeuClient-Setup.exe';
const MSG_NO_INSTALLER = 'Das neue Release hat keinen prüfbaren Installer.';

interface Release {
  tag_name: string;
  assets: { name: string; size: number; digest?: string | null; browser_download_url: string }[];
}

let status: UpdateStatus = { state: 'idle' };
let notify: (s: UpdateStatus) => void = () => undefined;
let initialized = false;
/** Gepruefter Installer, der beim Beenden ausgefuehrt wird */
let installer: string | null = null;
let restartAfterInstall = false;

function set(next: UpdateStatus) {
  status = next;
  notify(next);
}

export function getUpdateStatus(): UpdateStatus {
  return status;
}

const updateDir = () => path.join(paths.cache, 'update');

/** "v1.0" oder "1.0.0" -> [1, 0, 0]; null, wenn der Text keine Versionsnummer ist */
function parseVersion(text: string): number[] | null {
  const m = /^v?(\d+)\.(\d+)(?:\.(\d+))?$/.exec(text.trim());
  return m ? [Number(m[1]), Number(m[2]), Number(m[3] ?? 0)] : null;
}

function isNewer(a: number[], b: number[]): boolean {
  const i = a.findIndex((part, n) => part !== b[n]);
  return i >= 0 && a[i] > b[i];
}

/** Neuestes Release ohne Entwuerfe und Vorabversionen; null, wenn es noch keines gibt */
async function latestRelease(): Promise<Release | null> {
  try {
    return await fetchJson<Release>(`https://api.github.com/repos/${REPO}/releases/latest`, {
      headers: { Accept: 'application/vnd.github+json' },
      signal: AbortSignal.timeout(20_000),
    });
  } catch (e) {
    if (e instanceof HttpError && e.status === 404) return null;
    throw e;
  }
}

/** Alte Installer wegraeumen (schlaegt fehl, solange einer noch laeuft: dann beim naechsten Mal) */
function removeDownloads() {
  try {
    fs.rmSync(updateDir(), { recursive: true, force: true });
  } catch {
    /* Datei in Benutzung */
  }
}

async function download(release: Release, version: string): Promise<string> {
  const asset = release.assets.find((a) => a.name === INSTALLER);
  const sha256 = /^sha256:([0-9a-f]{64})$/i.exec(asset?.digest ?? '')?.[1];
  // ohne Pruefsumme oder von einer fremden Adresse wird nichts ausgefuehrt
  if (!asset || !sha256 || !asset.browser_download_url.startsWith(`https://github.com/${REPO}/releases/download/`)) {
    throw new Error(MSG_NO_INSTALLER);
  }
  const dest = path.join(updateDir(), `DeuClient-Setup-${version}.exe`);
  const progress = setInterval(() => {
    try {
      const percent = Math.min(99, Math.round((fs.statSync(`${dest}.part`).size / asset.size) * 100));
      if (percent !== status.percent) set({ state: 'downloading', version, percent });
    } catch {
      /* Download hat noch nicht begonnen oder ist fertig */
    }
  }, 500);
  try {
    // ohne "size": eine schon vorhandene Datei wird so auch ueber die Pruefsumme kontrolliert
    await downloadFile({ url: asset.browser_download_url, dest, sha256 });
  } finally {
    clearInterval(progress);
  }
  return dest;
}

export async function checkForUpdates(): Promise<UpdateStatus> {
  if (!app.isPackaged) {
    set({ state: 'none', error: 'Updates gibt es nur in der installierten Version.' });
    return status;
  }
  if (status.state === 'checking' || status.state === 'downloading' || status.state === 'ready') return status;
  set({ state: 'checking' });
  try {
    const release = await latestRelease();
    const next = release ? parseVersion(release.tag_name) : null;
    const current = parseVersion(app.getVersion());
    if (!release || !next || !current || !isNewer(next, current)) {
      removeDownloads();
      set({ state: 'none' });
      return status;
    }
    const version = next.join('.');
    set({ state: 'downloading', version, percent: 0 });
    installer = await download(release, version);
    set({ state: 'ready', version });
  } catch (e) {
    console.error('[update]', e);
    // GitHub meldet zu viele Anfragen auch mit 403
    const limited = e instanceof HttpError && (e.status === 403 || e.status === 429);
    set({ state: 'error', error: friendlyMessage(limited ? new Error('HTTP 429') : e) });
  }
  return status;
}

/** Startet den Installer losgeloest vom Launcher (still, wie beim bisherigen NSIS-Update) */
function runInstaller(file: string, restart: boolean) {
  const args = ['--updated', '/S', ...(restart ? ['--force-run'] : [])];
  const start = (command: string, commandArgs: string[]) => {
    const child = spawn(command, commandArgs, { detached: true, stdio: 'ignore' });
    child.on('error', (e) => console.error('[update]', e));
    child.unref();
  };
  try {
    start(file, args);
  } catch (e) {
    // Installation fuer alle Benutzer: braucht Administratorrechte
    const code = (e as NodeJS.ErrnoException).code;
    if (code !== 'EACCES' && code !== 'UNKNOWN') return console.error('[update]', e);
    try {
      start(path.join(process.resourcesPath, 'elevate.exe'), [file, ...args]);
    } catch (again) {
      console.error('[update]', again);
    }
  }
}

/** Installiert ein bereits geladenes Update sofort (App startet neu) */
export function installUpdate(): void {
  if (status.state !== 'ready') return;
  restartAfterInstall = true;
  app.quit(); // laufende Server werden vorher gestoppt, installiert wird im "quit"-Ereignis
}

export function initUpdater(onStatus: (s: UpdateStatus) => void): void {
  notify = onStatus;
  if (!app.isPackaged || initialized) return;
  initialized = true;

  app.once('quit', (_event, exitCode) => {
    if (exitCode === 0 && installer && fs.existsSync(installer)) runInstaller(installer, restartAfterInstall);
  });

  setTimeout(() => void checkForUpdates(), 6000);
  setInterval(() => void checkForUpdates(), 4 * 60 * 60 * 1000);
}
