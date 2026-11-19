/** Launcher-Einstellungen (settings.json im Datenordner). */
import os from 'node:os';
import type { Settings } from '../shared/types';
import { paths } from './util/paths';
import { readJson, writeJson } from './util/store';

/** Sinnvoller RAM-Standard: ein Viertel des Systemspeichers, zwischen 2 und 8 GB */
const defaultRam = Math.min(8192, Math.max(2048, Math.floor(os.totalmem() / 1024 / 1024 / 4 / 512) * 512));

export const DEFAULT_SETTINGS: Settings = {
  ramMb: defaultRam,
  javaPath: '',
  jvmArgs: '-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200',
  width: 1280,
  height: 720,
  fullscreen: false,
  afterLaunch: 'minimize',
  language: 'de',
  showConsole: true,
  parallelDownloads: 12,
  selectedInstance: 'divinesmp',
  launchOnStartup: false,
  animations: true,
  particles: true,
  gamePriority: 'normal',
};

let cache: Settings | null = null;

export function getSettings(): Settings {
  if (!cache) cache = { ...DEFAULT_SETTINGS, ...readJson<Partial<Settings>>(paths.settings, {}) };
  return cache;
}

export function saveSettings(patch: Partial<Settings>): Settings {
  cache = { ...getSettings(), ...patch };
  writeJson(paths.settings, cache);
  return cache;
}
