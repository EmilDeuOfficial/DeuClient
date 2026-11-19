/**
 * Vom Launcher verwaltete ("mitgelieferte") Mods:
 *   - DeuCore für jede unterstützte Minecraft-Version (enthält das HUD)
 *   - Module Discord und Scout (für 26.3)
 *
 * Sie liegen im Launcher unter resources/mods/<version>/ (siehe tools/embed-mod.cjs) und werden
 * vor jedem Start in passende Fabric-Modpacks kopiert bzw. aktualisiert.
 * Module darf man deaktivieren (Endung .disabled). Alle mitgelieferten Mods lassen sich löschen;
 * gelöschte Mods werden nicht erneut kopiert, bis man sie im Modpack wiederherstellt.
 */
import fs from 'node:fs';
import path from 'node:path';
import type { Instance } from '../../shared/types';
import { hashFile } from '../util/download';
import { bundledModsDir } from '../util/paths';
import { readJson, writeJson } from '../util/store';
import { instanceDir } from './instances';

export const DIVINECORE_FILE = 'DeuCore.jar';

export interface ManagedMod {
  file: string;
  id: string;
  name: string;
  version: string;
  description?: string;
  /** true = DeuCore (Kernmod, immer aktiv solange vorhanden) */
  core: boolean;
}

interface Manifest {
  divinecore: Record<string, string>;
  versions: Record<string, ManagedMod[]>;
}

let manifestCache: Manifest | null = null;
export function managedManifest(): Manifest {
  manifestCache ??= readJson<Manifest>(path.join(bundledModsDir(), 'manifest.json'), { divinecore: {}, versions: {} });
  return manifestCache;
}

/** Für die Oberfläche: DivineCore-Version + unterstützte Minecraft-Versionen */
export function divineCoreInfo(): { version: string; minecraft: string[] } | null {
  const m = managedManifest();
  const versions = Object.keys(m.divinecore);
  if (!versions.length) return null;
  return { version: versions.map((v) => `${m.divinecore[v]} (${v})`).join(', '), minecraft: versions };
}

export function managedModsFor(inst: Instance): ManagedMod[] {
  if (inst.loader !== 'fabric') return [];
  return managedManifest().versions[inst.mcVersion] ?? [];
}

export function supportsDivineCore(inst: Instance): boolean {
  return managedModsFor(inst).some((m) => m.core);
}

const trackFile = (inst: Instance) => path.join(instanceDir(inst.id), 'divine-managed.json');
const removedFile = (inst: Instance) => path.join(instanceDir(inst.id), 'managed-removed.json');

/** Dateien mitgelieferter Mods, die der Spieler bewusst gelöscht hat */
export function removedManaged(inst: Instance): string[] {
  return readJson<string[]>(removedFile(inst), []);
}

/** Löscht eine mitgelieferte Mod und merkt sich das, damit sie nicht wieder hineinkopiert wird */
export function removeManaged(inst: Instance, fileName: string): void {
  const base = fileName.replace(/\.disabled$/, '');
  const modsDir = path.join(instanceDir(inst.id), 'mods');
  fs.rmSync(path.join(modsDir, base), { force: true });
  fs.rmSync(path.join(modsDir, `${base}.disabled`), { force: true });
  writeJson(removedFile(inst), [...new Set([...removedManaged(inst), base])]);
}

/** Stellt gelöschte mitgelieferte Mods wieder her (file = nur diese, sonst alle) */
export async function restoreManaged(inst: Instance, file?: string): Promise<void> {
  writeJson(removedFile(inst), file ? removedManaged(inst).filter((f) => f !== file) : []);
  await syncDivineCore(inst);
}

/** Kopiert/aktualisiert die mitgelieferten Mods und entfernt nicht mehr passende. Liefert Statusmeldungen fürs Log. */
export async function syncDivineCore(inst: Instance): Promise<string> {
  const modsDir = path.join(instanceDir(inst.id), 'mods');
  fs.mkdirSync(modsDir, { recursive: true });
  const removed = new Set(removedManaged(inst));
  const wanted = managedModsFor(inst).filter((m) => !removed.has(m.file));
  const wantedFiles = new Set(wanted.map((m) => m.file));
  const previous = readJson<string[]>(trackFile(inst), []);

  // Alte/umbenannte Kopien entfernen (z. B. divinecore-1.0.0.jar oder eine ältere Glass-Version)
  for (const f of fs.readdirSync(modsDir)) {
    const base = f.replace(/\.disabled$/, '');
    const staleCore = /^(divinecore|deucore).*\.jar$/i.test(base) && base !== DIVINECORE_FILE;
    const staleManaged = previous.includes(base) && !wantedFiles.has(base);
    if (staleCore || staleManaged) fs.rmSync(path.join(modsDir, f), { force: true });
  }

  const changed: string[] = [];
  for (const mod of wanted) {
    const source = path.join(bundledModsDir(), inst.mcVersion, mod.file);
    if (!fs.existsSync(source)) continue;
    const enabled = path.join(modsDir, mod.file);
    const disabled = `${enabled}.disabled`;
    // DeuCore ist immer aktiv; Module bleiben deaktiviert, wenn der Spieler das so wollte
    if (mod.core) fs.rmSync(disabled, { force: true });
    const target = !mod.core && fs.existsSync(disabled) ? disabled : enabled;
    const [want, have] = await Promise.all([hashFile(source), fs.existsSync(target) ? hashFile(target) : Promise.resolve('')]);
    if (want !== have) {
      fs.copyFileSync(source, target);
      changed.push(mod.name);
    }
  }
  writeJson(trackFile(inst), [...wantedFiles]);

  if (!wanted.length) return removed.size ? 'DeuCore: alle mitgelieferten Mods wurden entfernt' : 'DeuCore: für diese Version nicht verfügbar';
  return changed.length ? `DeuCore aktualisiert: ${changed.join(', ')}` : `DeuCore aktuell (${wanted.map((m) => m.name).join(', ')})`;
}
