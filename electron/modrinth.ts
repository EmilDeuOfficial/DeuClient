/**
 * Modrinth-Integration (offizielle API v2, User-Agent "DeuClient/1.0"):
 *  - Suche mit Filtern, Projekt-Details, Versionen
 *  - Installation inkl. aller benötigten Dependencies
 *  - Installierte Inhalte verwalten (aktivieren/deaktivieren/löschen) und Updates prüfen
 *  - .mrpack-Modpacks importieren
 */
import fs from 'node:fs';
import path from 'node:path';
import AdmZip from 'adm-zip';
import type {
  ContentType, InstalledContent, InstallResult, Instance, ModrinthCategory, ModrinthProject,
  ModrinthSearchParams, ModrinthSearchResult, ModrinthVersion, ProgressEvent,
} from '../shared/types';
import { DEUCORE_FILE, managedModsFor, removeManaged } from './game/deucore';
import { createInstance, getInstance, instanceDir, saveInstance } from './game/instances';
import { downloadAll, downloadFile, fetchJson, hashFile } from './util/download';
import { readJson, writeJson } from './util/store';

const API = 'https://api.modrinth.com/v2';
export const FABRIC_API_ID = 'P7dR8mSH';

const FOLDERS: Record<ContentType, string> = { mod: 'mods', resourcepack: 'resourcepacks', shader: 'shaderpacks' };
const q = (v: unknown) => encodeURIComponent(JSON.stringify(v));

/* ---------------- Metadaten-Index pro Instanz ---------------- */

interface IndexEntry {
  projectId: string;
  versionId: string;
  versionNumber: string;
  title: string;
  iconUrl?: string;
  type: ContentType;
}
type ContentIndex = Record<string, IndexEntry>;

const indexFile = (instId: string) => path.join(instanceDir(instId), 'deu-content.json');
const loadIndex = (instId: string) => readJson<ContentIndex>(indexFile(instId), {});
const saveIndex = (instId: string, idx: ContentIndex) => writeJson(indexFile(instId), idx);

/* ---------------- API ---------------- */

export async function search(p: ModrinthSearchParams): Promise<ModrinthSearchResult> {
  const facets: string[][] = [[`project_type:${p.projectType}`]];
  if (p.gameVersion) facets.push([`versions:${p.gameVersion}`]);
  if (p.loader && p.projectType === 'mod') facets.push([`categories:${p.loader}`]);
  for (const c of p.categories) facets.push([`categories:${c}`]);
  const url = `${API}/search?query=${encodeURIComponent(p.query)}&facets=${q(facets)}&index=${p.index}&offset=${p.offset}&limit=${p.limit}`;
  return fetchJson<ModrinthSearchResult>(url);
}

let categoryCache: ModrinthCategory[] | null = null;
export async function categories(): Promise<ModrinthCategory[]> {
  categoryCache ??= await fetchJson<ModrinthCategory[]>(`${API}/tag/category`);
  return categoryCache;
}

/** Mehrere Projekte auf einmal (für die Extras-Seite) - IDs oder Slugs */
export function projects(ids: string[]): Promise<ModrinthProject[]> {
  return fetchJson<ModrinthProject[]>(`${API}/projects?ids=${q(ids)}`);
}

export function project(id: string): Promise<ModrinthProject> {
  return fetchJson<ModrinthProject>(`${API}/project/${encodeURIComponent(id)}`);
}

export function projectVersions(id: string, loaders?: string[], gameVersions?: string[]): Promise<ModrinthVersion[]> {
  const params = new URLSearchParams();
  if (loaders?.length) params.set('loaders', JSON.stringify(loaders));
  if (gameVersions?.length) params.set('game_versions', JSON.stringify(gameVersions));
  return fetchJson<ModrinthVersion[]>(`${API}/project/${encodeURIComponent(id)}/version?${params}`);
}

function loadersFor(inst: Instance, type: ContentType): string[] | undefined {
  if (type === 'mod') return [inst.loader === 'fabric' ? 'fabric' : 'minecraft'];
  if (type === 'resourcepack') return ['minecraft'];
  return undefined; // Shader: iris/optifine/canvas … - kein Filter
}

/** Beste passende Version eines Projekts für die Instanz (bevorzugt "release") */
async function bestVersion(projectId: string, inst: Instance, type: ContentType): Promise<ModrinthVersion | null> {
  const versions = await projectVersions(projectId, loadersFor(inst, type), [inst.mcVersion]);
  return versions.find((v) => v.version_type === 'release') ?? versions[0] ?? null;
}

/* ---------------- Installation ---------------- */

/**
 * Installiert ein Projekt (optional eine bestimmte Version) und rekursiv alle
 * benötigten Dependencies in die Instanz.
 */
export async function install(
  instanceId: string,
  projectId: string,
  type: ContentType,
  versionId?: string,
  onProgress?: (e: ProgressEvent) => void,
  /** Dependencies, die schon als Datei vorhanden sind und nicht geladen werden sollen */
  provided: string[] = [],
): Promise<InstallResult> {
  const inst = getInstance(instanceId);
  const idx = loadIndex(instanceId);
  const result: InstallResult = { installed: [], skipped: [] };
  const visited = new Set<string>();
  const installedProjects = new Set([...Object.values(idx).map((e) => e.projectId), ...provided]);

  const installOne = async (pid: string, vid: string | undefined, contentType: ContentType, isDependency: boolean) => {
    if (visited.has(pid)) return;
    visited.add(pid);
    // Dependencies, die schon installiert sind, nicht erneut laden
    if (isDependency && installedProjects.has(pid)) {
      result.skipped.push(pid);
      return;
    }

    const version = vid ? await fetchJson<ModrinthVersion>(`${API}/version/${vid}`) : await bestVersion(pid, inst, contentType);
    if (!version) {
      if (isDependency) {
        result.skipped.push(pid);
        return;
      }
      throw new Error(`Keine passende Version für Minecraft ${inst.mcVersion} (${inst.loader}) gefunden.`);
    }
    const proj = await project(pid);
    const file = version.files.find((f) => f.primary) ?? version.files[0];
    const folder = path.join(instanceDir(instanceId), FOLDERS[contentType]);

    onProgress?.({ stage: 'download', text: `Lade ${proj.title}`, percent: -1, detail: file.filename });

    // Ältere Version desselben Projekts entfernen
    for (const [fname, entry] of Object.entries(idx)) {
      if (entry.projectId === pid && fname !== file.filename) {
        fs.rmSync(path.join(folder, fname), { force: true });
        fs.rmSync(path.join(folder, `${fname}.disabled`), { force: true });
        delete idx[fname];
      }
    }

    await downloadFile({ url: file.url, dest: path.join(folder, file.filename), sha1: file.hashes.sha1, size: file.size });
    idx[file.filename] = {
      projectId: pid,
      versionId: version.id,
      versionNumber: version.version_number,
      title: proj.title,
      iconUrl: proj.icon_url,
      type: contentType,
    };
    installedProjects.add(pid);
    result.installed.push(proj.title);

    // Benötigte Dependencies (nur Mods)
    for (const dep of version.dependencies) {
      if (dep.dependency_type !== 'required') continue;
      let depProject = dep.project_id ?? undefined;
      if (!depProject && dep.version_id) depProject = (await fetchJson<ModrinthVersion>(`${API}/version/${dep.version_id}`)).project_id;
      if (depProject) await installOne(depProject, undefined, 'mod', true);
    }
  };

  try {
    await installOne(projectId, versionId, type, false);
  } finally {
    saveIndex(instanceId, idx);
  }
  return result;
}

/* ---------------- Installierte Inhalte ---------------- */

export function listInstalled(instanceId: string, type: ContentType): InstalledContent[] {
  const folder = path.join(instanceDir(instanceId), FOLDERS[type]);
  if (!fs.existsSync(folder)) return [];
  const idx = loadIndex(instanceId);
  const managed = new Map((type === 'mod' ? managedModsFor(getInstance(instanceId)) : []).map((m) => [m.file, m]));
  const exts = type === 'mod' ? /\.jar(\.disabled)?$/i : /\.zip(\.disabled)?$/i;
  return fs
    .readdirSync(folder)
    .filter((f) => exts.test(f))
    .map((f) => {
      const enabled = !f.endsWith('.disabled');
      const base = enabled ? f : f.slice(0, -'.disabled'.length);
      const meta = idx[base];
      const bundledMod = managed.get(base);
      return {
        fileName: f,
        enabled,
        type,
        size: fs.statSync(path.join(folder, f)).size,
        title: bundledMod?.name ?? meta?.title ?? base.replace(/\.(jar|zip)$/i, ''),
        description: bundledMod?.description,
        projectId: meta?.projectId,
        versionId: meta?.versionId,
        iconUrl: meta?.iconUrl,
        versionNumber: bundledMod?.version ?? meta?.versionNumber,
        managed: base === DEUCORE_FILE || !!bundledMod?.core,
        bundled: !!bundledMod && !bundledMod.core,
      } satisfies InstalledContent;
    })
    .sort((a, b) => Number(!!b.managed) - Number(!!a.managed) || Number(!!b.bundled) - Number(!!a.bundled) || a.title.localeCompare(b.title));
}

export function toggle(instanceId: string, type: ContentType, fileName: string): void {
  const folder = path.join(instanceDir(instanceId), FOLDERS[type]);
  if (fileName === DEUCORE_FILE) throw new Error('DeuCore ist der Kern des Clients und bleibt aktiv. Du kannst es stattdessen löschen.');
  const target = fileName.endsWith('.disabled') ? fileName.slice(0, -'.disabled'.length) : `${fileName}.disabled`;
  fs.renameSync(path.join(folder, fileName), path.join(folder, target));
}

export function remove(instanceId: string, type: ContentType, fileName: string): void {
  const base = fileName.replace(/\.disabled$/, '');
  if (type === 'mod' && (base === DEUCORE_FILE || managedModsFor(getInstance(instanceId)).some((m) => m.file === base))) {
    // mitgelieferte Mod: löschen und merken, damit sie nicht wieder hineinkopiert wird
    removeManaged(getInstance(instanceId), base);
    return;
  }
  fs.rmSync(path.join(instanceDir(instanceId), FOLDERS[type], fileName), { force: true });
  const idx = loadIndex(instanceId);
  delete idx[fileName.replace(/\.disabled$/, '')];
  saveIndex(instanceId, idx);
}

/**
 * Prüft Updates über den Datei-Hash (funktioniert auch für manuell hinzugefügte Modrinth-Mods).
 * Liefert die Liste inklusive "update"-Feld bei verfügbarer neuer Version.
 */
export async function checkUpdates(instanceId: string, type: ContentType): Promise<InstalledContent[]> {
  const inst = getInstance(instanceId);
  const items = listInstalled(instanceId, type);
  const folder = path.join(instanceDir(instanceId), FOLDERS[type]);
  const hashes = new Map<string, InstalledContent>();
  for (const it of items) {
    if (it.managed || it.bundled) continue;
    hashes.set(await hashFile(path.join(folder, it.fileName), 'sha1'), it);
  }
  if (!hashes.size) return items;

  const body: Record<string, unknown> = { hashes: [...hashes.keys()], algorithm: 'sha1', game_versions: [inst.mcVersion] };
  const loaders = loadersFor(inst, type);
  if (loaders) body.loaders = loaders;
  const updates = await fetchJson<Record<string, ModrinthVersion>>(`${API}/version_files/update`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });

  // aktuelle Versionen nachschlagen, um den Vergleich sauber zu machen und unbekannte Dateien zu benennen
  const current = await fetchJson<Record<string, ModrinthVersion>>(`${API}/version_files`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ hashes: [...hashes.keys()], algorithm: 'sha1' }),
  }).catch(() => ({}) as Record<string, ModrinthVersion>);

  for (const [hash, it] of hashes) {
    const cur = current[hash];
    if (cur) {
      it.projectId ??= cur.project_id;
      it.versionId ??= cur.id;
      it.versionNumber ??= cur.version_number;
    }
    const up = updates[hash];
    if (up && up.id !== (cur?.id ?? it.versionId)) it.update = { versionId: up.id, versionNumber: up.version_number };
  }
  return items;
}

export async function updateContent(instanceId: string, type: ContentType, fileName: string, projectId: string, versionId: string): Promise<void> {
  const wasEnabled = !fileName.endsWith('.disabled');
  remove(instanceId, type, fileName);
  const res = await install(instanceId, projectId, type, versionId);
  if (!wasEnabled && res.installed.length) {
    const idx = loadIndex(instanceId);
    const fname = Object.entries(idx).find(([, e]) => e.versionId === versionId)?.[0];
    if (fname) toggle(instanceId, type, fname);
  }
}

/** Fabric API automatisch installieren (die meisten Fabric-Mods brauchen sie) */
export async function ensureFabricApi(instanceId: string): Promise<void> {
  const inst = getInstance(instanceId);
  if (inst.loader !== 'fabric') return;
  const idx = loadIndex(instanceId);
  if (Object.values(idx).some((e) => e.projectId === FABRIC_API_ID)) return;
  const modsDir = path.join(instanceDir(instanceId), 'mods');
  if (fs.existsSync(modsDir) && fs.readdirSync(modsDir).some((f) => /^fabric-api/i.test(f))) return;
  await install(instanceId, FABRIC_API_ID, 'mod').catch(() => undefined);
}

/* ---------------- Vorinstallierte Leistungs-Mods ---------------- */

/** In jedem Fabric-Modpack vorinstalliert: Mod-Id (fabric.mod.json) und Modrinth-Projekt */
const PERFORMANCE_MODS = [
  { modId: 'sodium', projectId: 'AANobbMI' },
  { modId: 'lithium', projectId: 'gvQqBUqZ' },
  { modId: 'ferritecore', projectId: 'uXXizFIs' },
  { modId: 'immediatelyfast', projectId: '5ZwdcRci' },
  { modId: 'entityculling', projectId: 'NNAgCjsB' },
];

/** Mod-Ids aller Jars im mods-Ordner (auch deaktivierter) */
function presentModIds(modsDir: string): Set<string> {
  const ids = new Set<string>();
  if (!fs.existsSync(modsDir)) return ids;
  for (const file of fs.readdirSync(modsDir)) {
    if (!/\.jar(\.disabled)?$/i.test(file)) continue;
    try {
      const entry = new AdmZip(path.join(modsDir, file)).getEntry('fabric.mod.json');
      const id = entry && (JSON.parse(entry.getData().toString('utf8')) as { id?: string }).id;
      if (id) ids.add(id);
    } catch {
      // nicht lesbar: am Dateinamen erkennen (z. B. "sodium-fabric-…", aber nicht "sodium-extra-…")
      const hit = PERFORMANCE_MODS.find((m) => new RegExp(`^${m.modId}(-fabric)?[-_]\\d|^${m.modId}-fabric`, 'i').test(file));
      if (hit) ids.add(hit.modId);
    }
  }
  return ids;
}

async function installEssentials(instanceId: string, onProgress?: (e: ProgressEvent) => void): Promise<string[]> {
  await ensureFabricApi(instanceId);
  const inst = getInstance(instanceId);
  if (inst.loader !== 'fabric' || inst.noDeu) return [];
  // Was einmal vorhanden war, wird nicht erneut installiert: so bleibt eine gelöschte Mod gelöscht
  const handledFile = path.join(instanceDir(instanceId), 'deu-preinstalled.json');
  const handled = new Set(readJson<string[]>(handledFile, []));
  const missing = PERFORMANCE_MODS.filter((m) => !handled.has(m.projectId));
  if (!missing.length) return [];

  const modsDir = path.join(instanceDir(instanceId), 'mods');
  const present = presentModIds(modsDir);
  const indexed = new Set(Object.values(loadIndex(instanceId)).map((e) => e.projectId));
  const provided = present.has('fabric-api') ? [FABRIC_API_ID] : [];
  const added: string[] = [];
  for (const mod of missing) {
    if (!present.has(mod.modId) && !indexed.has(mod.projectId)) {
      try {
        added.push(...(await install(instanceId, mod.projectId, 'mod', undefined, onProgress, provided)).installed);
      } catch {
        continue; // offline oder noch keine passende Version: beim nächsten Start erneut versuchen
      }
    }
    handled.add(mod.projectId);
  }
  writeJson(handledFile, [...handled]);
  return added;
}

const essentialQueue = new Map<string, Promise<unknown>>();

/** Fabric API und Leistungs-Mods sicherstellen. Liefert die Namen neu installierter Mods. */
export function ensureEssentials(instanceId: string, onProgress?: (e: ProgressEvent) => void): Promise<string[]> {
  // je Modpack nacheinander: Erstellen und Start schreiben denselben Mod-Index
  const run = (essentialQueue.get(instanceId) ?? Promise.resolve())
    .catch(() => undefined)
    .then(() => installEssentials(instanceId, onProgress));
  essentialQueue.set(instanceId, run);
  return run;
}

/* ---------------- .mrpack-Import ---------------- */

interface MrIndex {
  name: string;
  versionId: string;
  dependencies: Record<string, string>;
  files: { path: string; hashes: { sha1: string; sha512: string }; downloads: string[]; fileSize: number; env?: { client?: string } }[];
}

export async function importMrpack(file: string, onProgress?: (e: ProgressEvent) => void): Promise<Instance> {
  const zip = new AdmZip(file);
  const entry = zip.getEntry('modrinth.index.json');
  if (!entry) throw new Error('Keine gültige .mrpack-Datei (modrinth.index.json fehlt)');
  const index = JSON.parse(entry.getData().toString('utf8')) as MrIndex;

  const mc = index.dependencies.minecraft;
  if (!mc) throw new Error('Modpack gibt keine Minecraft-Version an');
  if (index.dependencies.forge || index.dependencies.neoforge || index.dependencies['quilt-loader']) {
    throw new Error('Nur Fabric- und Vanilla-Modpacks werden unterstützt');
  }
  const fabric = index.dependencies['fabric-loader'];

  const inst = createInstance({ name: index.name, mcVersion: mc, loader: fabric ? 'fabric' : 'vanilla', icon: 'package' });
  if (fabric) saveInstance({ ...inst, loaderVersion: fabric });
  const dir = instanceDir(inst.id);

  // Dateien herunterladen (nur Client-relevante), Pfade gegen Ausbrechen aus dem Ordner absichern
  const tasks = index.files
    .filter((f) => f.env?.client !== 'unsupported')
    .map((f) => {
      const dest = path.resolve(dir, f.path);
      if (!dest.startsWith(path.resolve(dir) + path.sep)) throw new Error(`Ungültiger Pfad im Modpack: ${f.path}`);
      return { url: f.downloads[0], dest, sha1: f.hashes.sha1, size: f.fileSize };
    });
  await downloadAll(tasks, 8, (done, total, name) =>
    onProgress?.({ stage: 'download', text: `Modpack: ${index.name}`, percent: done / total, detail: `${done}/${total} · ${name}` }),
  );

  // overrides/ und client-overrides/ in den Instanzordner entpacken
  for (const e of zip.getEntries()) {
    for (const prefix of ['overrides/', 'client-overrides/']) {
      if (!e.entryName.startsWith(prefix) || e.isDirectory) continue;
      const dest = path.resolve(dir, e.entryName.slice(prefix.length));
      if (!dest.startsWith(path.resolve(dir) + path.sep)) continue;
      fs.mkdirSync(path.dirname(dest), { recursive: true });
      fs.writeFileSync(dest, e.getData());
    }
  }
  onProgress?.({ stage: 'done', text: `${index.name} importiert`, percent: 1 });
  return getInstance(inst.id);
}
