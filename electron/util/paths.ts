/**
 * Zentrale Pfade des Launchers.
 * Alle Daten liegen unter %APPDATA%\.deuclient – so bleibt die portable .exe sauber.
 */
import { app } from 'electron';
import path from 'node:path';
import fs from 'node:fs';

/**
 * Echter Pfad des Datenordners. Läuft der Launcher in einer Umgebung, die %APPDATA% umleitet
 * (z. B. als MSIX-Paket gestarteter Elternprozess), sieht Java die Dateien unter dem umgeleiteten Pfad.
 * Stimmen Classpath und tatsächlicher Pfad nicht überein, lädt Fabric Mixin doppelt und das Spiel stürzt ab –
 * daher wird der endgültige Pfad aufgelöst.
 */
function resolveDataDir(): string {
  // DEUCLIENT_DATA_DIR: anderer Datenordner (für Tests / portable Nutzung)
  const dir = process.env.DEUCLIENT_DATA_DIR || path.join(app.getPath('appData'), '.deuclient');
  // Migration: Datenordner des frueheren "Divine Launcher" uebernehmen (Accounts, Instanzen, Einstellungen)
  if (!process.env.DEUCLIENT_DATA_DIR && !fs.existsSync(dir)) {
    const legacy = path.join(app.getPath('appData'), '.divinelauncher');
    try {
      if (fs.existsSync(legacy)) fs.renameSync(legacy, dir);
    } catch {
      // Umbenennen nicht moeglich (z. B. Datei in Benutzung): es wird ein neuer, leerer Ordner angelegt
    }
  }
  try {
    fs.mkdirSync(dir, { recursive: true });
    return fs.realpathSync.native(dir);
  } catch {
    return dir;
  }
}

export const DATA_DIR = resolveDataDir();

export const paths = {
  data: DATA_DIR,
  instances: path.join(DATA_DIR, 'instances'),
  /** Gemeinsamer Speicher für Versionen, Libraries und Assets (wird von allen Instanzen genutzt) */
  versions: path.join(DATA_DIR, 'shared', 'versions'),
  libraries: path.join(DATA_DIR, 'shared', 'libraries'),
  assets: path.join(DATA_DIR, 'shared', 'assets'),
  natives: path.join(DATA_DIR, 'shared', 'natives'),
  runtime: path.join(DATA_DIR, 'runtime'),
  cache: path.join(DATA_DIR, 'cache'),
  logs: path.join(DATA_DIR, 'logs'),
  settings: path.join(DATA_DIR, 'settings.json'),
  accounts: path.join(DATA_DIR, 'accounts.json'),
};

/** Pfad zur eingebetteten DivineCore-Mod (im Build unter resources/mods, in der Entwicklung unter launcher/resources/mods) */
export function bundledModsDir(): string {
  if (process.env.DEUCLIENT_BUNDLED_MODS) return process.env.DEUCLIENT_BUNDLED_MODS; // nur für Tests
  return app.isPackaged ? path.join(process.resourcesPath, 'mods') : path.join(app.getAppPath(), 'resources', 'mods');
}

/** Standard-Einstellungen fuer neue Modpacks (options.txt, Mod-Configs) – im Build unter resources/default-profile */
export function defaultProfileDir(): string {
  return app.isPackaged ? path.join(process.resourcesPath, 'default-profile') : path.join(app.getAppPath(), 'resources', 'default-profile');
}

export function ensureBaseDirs(): void {
  for (const p of [paths.instances, paths.versions, paths.libraries, paths.assets, paths.natives, paths.runtime, paths.cache, paths.logs]) {
    fs.mkdirSync(p, { recursive: true });
  }
}
