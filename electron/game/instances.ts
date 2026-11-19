/**
 * Instanz-Verwaltung. Jede Instanz hat einen eigenen Ordner:
 *   instances/<id>/instance.json, mods/, saves/, resourcepacks/, shaderpacks/, config/
 */
import fs from 'node:fs';
import path from 'node:path';
import { shell } from 'electron';
import type { Instance, InstanceStats, NewInstanceInput, ScreenshotInfo, WorldInfo } from '../../shared/types';
import { defaultProfileDir, paths } from '../util/paths';
import { readJson, writeJson } from '../util/store';

/** Standard-Instanz (Fabric, DivineCore und Glass-Mods) */
export const DEFAULT_INSTANCE_ID = 'divinesmp';
export const DEFAULT_MC_VERSION = '26.3';
/** Version des Standard-Modpacks; erhöhen, wenn es migriert werden soll */
const DEFAULT_PACK_VERSION = 2;

const DEFAULT_DESCRIPTION = 'Das DeuClient-Modpack mit DeuCore (HUD, Mod-Menü, Capes) sowie den Modulen Discord und Scout.';

const SUBFOLDERS = ['mods', 'saves', 'resourcepacks', 'shaderpacks', 'config', 'screenshots'];

export function instanceDir(id: string): string {
  return path.join(paths.instances, id);
}

function slugify(name: string): string {
  const base = name.toLowerCase().normalize('NFKD').replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') || 'instanz';
  let id = base;
  for (let i = 2; fs.existsSync(instanceDir(id)); i++) id = `${base}-${i}`;
  return id;
}

export function listInstances(): Instance[] {
  if (!fs.existsSync(paths.instances)) return [];
  return fs
    .readdirSync(paths.instances)
    .map((d) => readJson<Instance | null>(path.join(paths.instances, d, 'instance.json'), null))
    .filter((i): i is Instance => !!i)
    .sort((a, b) => Number(!!b.isDefault) - Number(!!a.isDefault) || (b.lastPlayed ?? b.created) - (a.lastPlayed ?? a.created));
}

export function getInstance(id: string): Instance {
  const inst = readJson<Instance | null>(path.join(instanceDir(id), 'instance.json'), null);
  if (!inst) throw new Error(`Instanz "${id}" nicht gefunden`);
  return inst;
}

export function saveInstance(inst: Instance): Instance {
  const dir = instanceDir(inst.id);
  for (const sub of SUBFOLDERS) fs.mkdirSync(path.join(dir, sub), { recursive: true });
  writeJson(path.join(dir, 'instance.json'), inst);
  return inst;
}

/**
 * Uebernimmt die Standard-Einstellungen (options.txt, Mod-Configs) in ein neues Modpack.
 * Vorhandene Dateien bleiben unberuehrt. Vorlage: resources/default-profile
 */
function applyProfileTemplate(inst: Instance): void {
  const root = defaultProfileDir();
  const dir = instanceDir(inst.id);
  const copyTree = (from: string, to: string) => {
    if (!fs.existsSync(from)) return;
    fs.mkdirSync(to, { recursive: true });
    for (const e of fs.readdirSync(from, { withFileTypes: true })) {
      const src = path.join(from, e.name);
      const dst = path.join(to, e.name);
      if (e.isDirectory()) copyTree(src, dst);
      else if (!fs.existsSync(dst)) fs.copyFileSync(src, dst);
    }
  };
  try {
    const options = path.join(root, 'options.txt');
    const target = path.join(dir, 'options.txt');
    if (fs.existsSync(options) && !fs.existsSync(target)) fs.copyFileSync(options, target);
    if (inst.loader === 'fabric') copyTree(path.join(root, inst.mcVersion), dir);
  } catch {
    // Vorlage nicht lesbar: Modpack startet mit den Minecraft-Standardwerten
  }
}

/**
 * Vor jedem Start: fehlende Standard-Einstellungen aus der Vorlage ergaenzen und das
 * Willkommensmenue (Erzaehler/Barrierefreiheit) abschalten, damit direkt das Hauptmenue erscheint.
 */
export function prepareOptions(inst: Instance): void {
  applyProfileTemplate(inst);
  const file = path.join(instanceDir(inst.id), 'options.txt');
  try {
    if (!fs.existsSync(file)) return;
    const text = fs.readFileSync(file, 'utf8');
    if (/^onboardAccessibility:true\s*$/m.test(text)) fs.writeFileSync(file, text.replace(/^onboardAccessibility:true/m, 'onboardAccessibility:false'));
  } catch {
    // Datei nicht lesbar: Minecraft startet mit seinen Standardwerten
  }
}

export function createInstance(input: NewInstanceInput, id?: string): Instance {
  const name = input.name.trim() || 'Neue Instanz';
  const inst = saveInstance({
    id: id ?? slugify(name),
    name,
    mcVersion: input.mcVersion,
    loader: input.loader,
    icon: input.icon || 'grass',
    created: Date.now(),
  });
  applyProfileTemplate(inst);
  return inst;
}

export function updateInstance(id: string, patch: Partial<Instance>): Instance {
  const inst = getInstance(id);
  // Neue Minecraft-Version oder neuer Loader: Mods passen nicht mehr → sichern statt kaputt starten
  if ((patch.mcVersion && patch.mcVersion !== inst.mcVersion) || (patch.loader && patch.loader !== inst.loader)) {
    backupContent(inst);
    patch = { ...patch, loaderVersion: undefined };
  }
  // ID und Standard-Flag sind unveränderlich
  return saveInstance({ ...inst, ...patch, id: inst.id, isDefault: inst.isDefault });
}

/** Verschiebt Mods + Metadaten nach mods_backup_<alte Version>, damit nichts Inkompatibles geladen wird */
function backupContent(inst: Instance) {
  const dir = instanceDir(inst.id);
  const mods = path.join(dir, 'mods');
  if (fs.existsSync(mods) && fs.readdirSync(mods).length) {
    fs.renameSync(mods, path.join(dir, `mods_backup_${inst.mcVersion}_${Date.now()}`));
  }
  for (const f of ['divine-content.json', 'divine-managed.json']) fs.rmSync(path.join(dir, f), { force: true });
  fs.mkdirSync(mods, { recursive: true });
}

/** Spielzeit nach Spielende gutschreiben */
export function addPlayTime(id: string, ms: number): void {
  try {
    const inst = getInstance(id);
    saveInstance({ ...inst, playTimeMs: (inst.playTimeMs ?? 0) + Math.max(0, ms) });
  } catch {
    /* Modpack wurde inzwischen gelöscht */
  }
}

/* ---------------- Welten, Screenshots, Statistik ---------------- */

function dirSize(p: string): number {
  let total = 0;
  try {
    for (const e of fs.readdirSync(p, { withFileTypes: true })) {
      const full = path.join(p, e.name);
      total += e.isDirectory() ? dirSize(full) : fs.statSync(full).size;
    }
  } catch {
    /* ignorieren */
  }
  return total;
}

export function listWorlds(id: string): WorldInfo[] {
  const saves = path.join(instanceDir(id), 'saves');
  if (!fs.existsSync(saves)) return [];
  return fs
    .readdirSync(saves, { withFileTypes: true })
    .filter((d) => d.isDirectory() && fs.existsSync(path.join(saves, d.name, 'level.dat')))
    .map((d) => {
      const dir = path.join(saves, d.name);
      const iconFile = path.join(dir, 'icon.png');
      return {
        folder: d.name,
        name: d.name,
        lastPlayed: fs.statSync(path.join(dir, 'level.dat')).mtimeMs,
        sizeMb: Math.round((dirSize(dir) / 1024 / 1024) * 10) / 10,
        icon: fs.existsSync(iconFile) ? `data:image/png;base64,${fs.readFileSync(iconFile).toString('base64')}` : undefined,
      };
    })
    .sort((a, b) => b.lastPlayed - a.lastPlayed);
}

export function listScreenshots(id: string, limit = 24): ScreenshotInfo[] {
  const dir = path.join(instanceDir(id), 'screenshots');
  if (!fs.existsSync(dir)) return [];
  return fs
    .readdirSync(dir)
    .filter((f) => /\.png$/i.test(f))
    .map((f) => ({ f, time: fs.statSync(path.join(dir, f)).mtimeMs }))
    .sort((a, b) => b.time - a.time)
    .slice(0, limit)
    .map(({ f, time }) => ({ file: f, time, dataUrl: `data:image/png;base64,${fs.readFileSync(path.join(dir, f)).toString('base64')}` }));
}

export function openScreenshot(id: string, file: string): void {
  const p = path.join(instanceDir(id), 'screenshots', path.basename(file));
  if (fs.existsSync(p)) void shell.openPath(p);
}

export function instanceStats(id: string): InstanceStats {
  const dir = instanceDir(id);
  const count = (sub: string, re: RegExp) => {
    try {
      return fs.readdirSync(path.join(dir, sub)).filter((f) => re.test(f)).length;
    } catch {
      return 0;
    }
  };
  return {
    mods: count('mods', /\.jar$/i),
    resourcepacks: count('resourcepacks', /\.zip$/i),
    shaders: count('shaderpacks', /\.zip$/i),
    worlds: listWorlds(id).length,
    screenshots: count('screenshots', /\.png$/i),
    sizeMb: Math.round(dirSize(dir) / 1024 / 1024),
  };
}

export function deleteInstance(id: string): void {
  const inst = getInstance(id);
  if (inst.isDefault) throw new Error('Die DeuClient-Instanz kann nicht gelöscht werden.');
  fs.rmSync(instanceDir(id), { recursive: true, force: true });
}

export function openInstanceFolder(id: string, sub?: string): void {
  const dir = sub ? path.join(instanceDir(id), sub) : instanceDir(id);
  fs.mkdirSync(dir, { recursive: true });
  void shell.openPath(dir);
}

/** Legt beim ersten Start das Standard-Modpack "DeuClient" (Fabric) an bzw. hebt es auf die aktuelle Version. */
export function ensureDefaultInstance(): Instance {
  const file = path.join(instanceDir(DEFAULT_INSTANCE_ID), 'instance.json');
  if (fs.existsSync(file)) {
    const inst = getInstance(DEFAULT_INSTANCE_ID);
    if ((inst.divineVersion ?? 0) < DEFAULT_PACK_VERSION) {
      // Migration (z. B. 1.21.4 → 26.3): alte Mods werden gesichert
      const migrated = updateInstance(inst.id, { mcVersion: DEFAULT_MC_VERSION, loader: 'fabric' });
      return saveInstance({ ...migrated, divineVersion: DEFAULT_PACK_VERSION, description: DEFAULT_DESCRIPTION });
    }
    // Umbenennung des frueheren Standard-Modpacks "DivineSMP" und aktuelle Beschreibung
    if (inst.name === 'DivineSMP' || inst.description !== DEFAULT_DESCRIPTION) {
      return saveInstance({ ...inst, name: inst.name === 'DivineSMP' ? 'DeuClient' : inst.name, description: DEFAULT_DESCRIPTION });
    }
    return saveInstance(inst); // Unterordner sicherstellen
  }
  const created = saveInstance({
    divineVersion: DEFAULT_PACK_VERSION,
    description: DEFAULT_DESCRIPTION,
    id: DEFAULT_INSTANCE_ID,
    name: 'DeuClient',
    mcVersion: DEFAULT_MC_VERSION,
    loader: 'fabric',
    icon: 'divine',
    created: Date.now(),
    isDefault: true,
  });
  applyProfileTemplate(created);
  return created;
}
