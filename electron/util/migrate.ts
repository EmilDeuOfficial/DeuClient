/**
 * Einmalige Migration der Launcher-Daten vom frueheren Namen "Divine" auf "Deu".
 * Laeuft bei jedem Start (vor allen anderen Modulen), ist idempotent und fasst nur Altdaten an:
 *   - Instanz-Ordner instances/divinesmp -> instances/deusmp (inkl. id in instance.json und selectedInstance)
 *   - instance.json: noDivine -> noDeu, divineVersion -> deuVersion, Icon "divine" -> "deu"
 *   - divine-managed.json / divine-content.json -> deu-managed.json / deu-content.json
 *   - config/divinecore.json und config/divinecore/ -> config/deucore.json bzw. config/deucore/
 *   - Natives-Marker .divine-extracted -> .deu-extracted
 * Neue Dateien werden nie ueberschrieben, Altdaten nie ohne vollstaendige Kopie geloescht, Fehler werden nur
 * protokolliert (der Start laeuft weiter). Die alten Namen stehen ausschliesslich hier als Migrationsquelle.
 */
import fs from 'node:fs';
import path from 'node:path';
import { paths } from './paths';
import { readJson, writeJson } from './store';

const LEGACY_DEFAULT_ID = 'divinesmp';
const NEW_DEFAULT_ID = 'deusmp';

const log = (msg: string) => console.log(`[Migration] ${msg}`);

/** Entfernt ein Original nach erfolgreicher Kopie; Reste, die sich nicht loeschen lassen, sind harmlos */
function removeOriginal(p: string): void {
  try {
    if (fs.statSync(p).isDirectory()) fs.rmSync(path.join(p, 'instance.json'), { force: true }); // sonst taucht das Modpack doppelt auf
    fs.rmSync(p, { recursive: true, force: true });
  } catch {
    // Reste im alten Ordner bleiben liegen
  }
}

/** Verschiebt Datei oder Ordner nur, wenn das Ziel noch nicht existiert. true = verschoben. */
function moveIfMissing(from: string, to: string): boolean {
  if (!fs.existsSync(from) || fs.existsSync(to)) return false;
  try {
    fs.mkdirSync(path.dirname(to), { recursive: true });
    fs.renameSync(from, to);
  } catch {
    // Umbenennen nicht moeglich (z. B. Datei in Benutzung): erst vollstaendig kopieren, dann das Original entfernen
    const tmp = `${to}.migrating`;
    try {
      fs.rmSync(tmp, { recursive: true, force: true });
      fs.cpSync(from, tmp, { recursive: true });
      fs.renameSync(tmp, to);
    } catch (err) {
      fs.rmSync(tmp, { recursive: true, force: true });
      log(`${path.basename(from)} konnte nicht nach ${path.basename(to)} verschoben werden: ${String(err)}`);
      return false;
    }
    removeOriginal(from);
  }
  log(`${path.basename(from)} -> ${path.basename(to)}`);
  return true;
}

/**
 * Hebt alte Schluessel einer instance.json auf die neuen Namen (noDivine, divineVersion, Icon "divine").
 * Der neue Schluessel gewinnt; der alte wird nur gelesen, wenn der neue fehlt.
 */
export function normalizeLegacyInstance<T extends object>(inst: T): T {
  const o = inst as Record<string, unknown>;
  if (!('noDivine' in o) && !('divineVersion' in o) && o.icon !== 'divine') return inst;
  const out = { ...o };
  if (out.noDeu === undefined && out.noDivine !== undefined) out.noDeu = out.noDivine;
  if (out.deuVersion === undefined && out.divineVersion !== undefined) out.deuVersion = out.divineVersion;
  delete out.noDivine;
  delete out.divineVersion;
  if (out.icon === 'divine') out.icon = 'deu';
  return out as T;
}

function migrateInstanceFolder(dir: string): void {
  const file = path.join(dir, 'instance.json');
  const raw = readJson<Record<string, unknown> | null>(file, null);
  if (raw) {
    let inst = normalizeLegacyInstance(raw);
    // Standard-Instanz: id muss zum umbenannten Ordner passen (auch nach einem unterbrochenen Lauf)
    if (path.basename(dir) === NEW_DEFAULT_ID && inst.id === LEGACY_DEFAULT_ID) inst = { ...inst, id: NEW_DEFAULT_ID };
    if (inst !== raw) {
      writeJson(file, inst);
      log(`instance.json von ${path.basename(dir)} aktualisiert`);
    }
  }
  moveIfMissing(path.join(dir, 'divine-managed.json'), path.join(dir, 'deu-managed.json'));
  moveIfMissing(path.join(dir, 'divine-content.json'), path.join(dir, 'deu-content.json'));
  moveIfMissing(path.join(dir, 'config', 'divinecore.json'), path.join(dir, 'config', 'deucore.json'));
  moveIfMissing(path.join(dir, 'config', 'divinecore'), path.join(dir, 'config', 'deucore'));
}

function subdirs(dir: string): string[] {
  try {
    return fs.readdirSync(dir, { withFileTypes: true }).filter((e) => e.isDirectory()).map((e) => path.join(dir, e.name));
  } catch {
    return [];
  }
}

export function migrateLegacyData(): void {
  try {
    // 1. Standard-Instanz umbenennen, danach alle Instanzen auf die neuen Namen heben
    moveIfMissing(path.join(paths.instances, LEGACY_DEFAULT_ID), path.join(paths.instances, NEW_DEFAULT_ID));
    for (const dir of subdirs(paths.instances)) migrateInstanceFolder(dir);

    // 2. Ausgewaehlte Instanz in den Einstellungen (nur wenn der alte Ordner nicht mehr existiert)
    const settings = readJson<Record<string, unknown> | null>(paths.settings, null);
    if (settings?.selectedInstance === LEGACY_DEFAULT_ID && !fs.existsSync(path.join(paths.instances, LEGACY_DEFAULT_ID, 'instance.json'))) {
      writeJson(paths.settings, { ...settings, selectedInstance: NEW_DEFAULT_ID });
      log(`Einstellung selectedInstance: ${LEGACY_DEFAULT_ID} -> ${NEW_DEFAULT_ID}`);
    }

    // 3. Marker der entpackten Natives
    for (const dir of subdirs(paths.natives)) moveIfMissing(path.join(dir, '.divine-extracted'), path.join(dir, '.deu-extracted'));
  } catch (err) {
    log(`Fehler: ${String(err)}`);
  }
}
