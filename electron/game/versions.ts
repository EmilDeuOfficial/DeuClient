/**
 * Mojang-Versionsdaten und Fabric-Meta:
 *  - Version-Manifest (Liste aller Versionen)
 *  - Version-JSON (Libraries, Assets, Argumente, Java-Version)
 *  - Fabric-Profil (über meta.fabricmc.net) wird mit der Vanilla-Version zusammengeführt
 */
import fs from 'node:fs';
import path from 'node:path';
import type { McVersion } from '../../shared/types';
import { fetchJson, downloadFile } from '../util/download';
import { paths } from '../util/paths';
import { readJson, writeJson } from '../util/store';

const MANIFEST_URL = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json';
const FABRIC_META = 'https://meta.fabricmc.net/v2';

export type Rule = {
  action: 'allow' | 'disallow';
  os?: { name?: string; arch?: string; version?: string };
  features?: Record<string, boolean>;
};
export type Argument = string | { rules?: Rule[]; value: string | string[] };

export interface Library {
  name: string;
  url?: string;
  sha1?: string;
  size?: number;
  rules?: Rule[];
  natives?: Record<string, string>;
  extract?: { exclude?: string[] };
  downloads?: {
    artifact?: { path: string; url: string; sha1: string; size: number };
    classifiers?: Record<string, { path: string; url: string; sha1: string; size: number }>;
  };
}

export interface VersionJson {
  id: string;
  type: string;
  mainClass: string;
  inheritsFrom?: string;
  minecraftArguments?: string;
  arguments?: { game?: Argument[]; jvm?: Argument[] };
  assetIndex: { id: string; url: string; sha1: string; size: number; totalSize?: number };
  assets: string;
  downloads: { client: { url: string; sha1: string; size: number } };
  libraries: Library[];
  javaVersion?: { component: string; majorVersion: number };
  /** Vanilla-Version, deren client.jar genutzt wird (bei Fabric ≠ id) */
  clientVersion?: string;
}

interface Manifest {
  latest: { release: string; snapshot: string };
  versions: { id: string; type: McVersion['type']; url: string; sha1: string; releaseTime: string }[];
}

let manifestCache: Manifest | null = null;

/** Lädt das Mojang-Versionsmanifest (mit Offline-Fallback aus dem Cache). */
export async function getManifest(): Promise<Manifest> {
  if (manifestCache) return manifestCache;
  const cacheFile = path.join(paths.cache, 'version_manifest_v2.json');
  try {
    manifestCache = await fetchJson<Manifest>(MANIFEST_URL);
    writeJson(cacheFile, manifestCache);
  } catch (e) {
    const cached = readJson<Manifest | null>(cacheFile, null);
    if (!cached) throw e;
    manifestCache = cached;
  }
  return manifestCache;
}

export async function listVersions(includeSnapshots = false): Promise<McVersion[]> {
  const m = await getManifest();
  return m.versions
    .filter((v) => v.type === 'release' || (includeSnapshots && v.type === 'snapshot'))
    .map(({ id, type, releaseTime }) => ({ id, type, releaseTime }));
}

/** Liefert die Vanilla-Version-JSON (wird lokal zwischengespeichert und per SHA1 geprüft). */
export async function getVanillaVersion(id: string): Promise<VersionJson> {
  const file = path.join(paths.versions, id, `${id}.json`);
  try {
    const m = await getManifest();
    const entry = m.versions.find((v) => v.id === id);
    if (!entry) throw new Error(`Minecraft-Version ${id} nicht gefunden`);
    await downloadFile({ url: entry.url, dest: file, sha1: entry.sha1 });
  } catch (e) {
    if (!fs.existsSync(file)) throw e; // offline, aber evtl. gecacht
  }
  return JSON.parse(fs.readFileSync(file, 'utf8')) as VersionJson;
}

/** Fabric: Liste der Minecraft-Versionen, die Fabric unterstützt */
export async function listFabricGameVersions(): Promise<string[]> {
  const list = await fetchJson<{ version: string; stable: boolean }[]>(`${FABRIC_META}/versions/game`);
  return list.filter((v) => v.stable).map((v) => v.version);
}

/** Neueste stabile Fabric-Loader-Version für eine Minecraft-Version */
export async function latestFabricLoader(gameVersion: string): Promise<string> {
  const list = await fetchJson<{ loader: { version: string; stable: boolean } }[]>(
    `${FABRIC_META}/versions/loader/${encodeURIComponent(gameVersion)}`,
  );
  if (!list.length) throw new Error(`Fabric unterstützt Minecraft ${gameVersion} nicht`);
  return (list.find((l) => l.loader.stable) ?? list[0]).loader.version;
}

/** Fabric-Profil laden (inheritsFrom = Vanilla-Version) */
async function getFabricProfile(gameVersion: string, loaderVersion: string): Promise<VersionJson> {
  const id = `fabric-loader-${loaderVersion}-${gameVersion}`;
  const file = path.join(paths.versions, id, `${id}.json`);
  if (!fs.existsSync(file)) {
    const profile = await fetchJson<VersionJson>(
      `${FABRIC_META}/versions/loader/${encodeURIComponent(gameVersion)}/${encodeURIComponent(loaderVersion)}/profile/json`,
    );
    writeJson(file, profile);
  }
  return JSON.parse(fs.readFileSync(file, 'utf8')) as VersionJson;
}

/** "group:artifact[:classifier]" - Schlüssel zum Entfernen doppelter Libraries */
function libKey(name: string): string {
  const [group, artifact, , classifier] = name.split(':');
  return `${group}:${artifact}${classifier ? `:${classifier}` : ''}`;
}

/** Fabric-Profil und Vanilla-Version zu einer startbaren Version zusammenführen */
function mergeVersions(child: VersionJson, parent: VersionJson): VersionJson {
  const childKeys = new Set(child.libraries.map((l) => libKey(l.name)));
  return {
    ...parent,
    id: child.id,
    mainClass: child.mainClass,
    libraries: [...child.libraries, ...parent.libraries.filter((l) => !childKeys.has(libKey(l.name)))],
    arguments: {
      game: [...(parent.arguments?.game ?? []), ...(child.arguments?.game ?? [])],
      jvm: [...(parent.arguments?.jvm ?? []), ...(child.arguments?.jvm ?? [])],
    },
    minecraftArguments: child.minecraftArguments ?? parent.minecraftArguments,
    clientVersion: parent.id,
  };
}

/** Startbare Version für eine Instanz (Vanilla oder Fabric) */
export async function resolveVersion(mcVersion: string, loader: 'vanilla' | 'fabric', loaderVersion?: string): Promise<VersionJson> {
  const vanilla = await getVanillaVersion(mcVersion);
  if (loader === 'vanilla') return { ...vanilla, clientVersion: vanilla.id };
  const lv = loaderVersion || (await latestFabricLoader(mcVersion));
  const fabric = await getFabricProfile(mcVersion, lv);
  return mergeVersions(fabric, vanilla);
}
