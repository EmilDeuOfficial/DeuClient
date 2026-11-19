/**
 * Eigene Start-Implementierung für Minecraft (Vanilla & Fabric):
 *   1. Account-Token prüfen/erneuern
 *   2. Version-JSON auflösen (inkl. Fabric)
 *   3. Passendes Java finden oder Temurin herunterladen
 *   4. Client-JAR, Libraries und Assets parallel laden (SHA1-geprüft)
 *   5. Natives entpacken
 *   6. Instanz vorbereiten (DivineCore-Mod)
 *   7. Java-Argumente bauen und Spiel starten
 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawn, type ChildProcess } from 'node:child_process';
import AdmZip from 'adm-zip';
import type { GameState, LaunchOptions, ProgressEvent } from '../../shared/types';
import { getActiveSession } from '../auth/accounts';
import { getSettings } from '../settings';
import { downloadAll, downloadFile, type DownloadTask } from '../util/download';
import { paths } from '../util/paths';
import { readJson } from '../util/store';
import { syncDivineCore } from './divinecore';
import { addPlayTime, getInstance, instanceDir, prepareOptions, updateInstance } from './instances';
import { downloadTemurin, findJavaFor } from './java';
import { resolveVersion, type Argument, type Library, type Rule, type VersionJson } from './versions';

export interface LaunchHooks {
  progress: (e: ProgressEvent) => void;
  log: (lines: string[]) => void;
  state: (s: GameState) => void;
}

const LAUNCHER_VERSION = '1.0.0';
let running: ChildProcess | null = null;
let runningInstance: string | undefined;

export function getGameState(): GameState {
  return { running: !!running, instanceId: runningInstance };
}

export function killGame(): void {
  running?.kill();
}

/* ---------------- Regeln (OS / Features) ---------------- */

const ARCH = process.arch === 'ia32' ? 'x86' : process.arch === 'arm64' ? 'arm64' : 'x86_64';

function ruleMatches(rule: Rule, features: Record<string, boolean>): boolean {
  if (rule.os) {
    if (rule.os.name && rule.os.name !== 'windows') return false;
    if (rule.os.arch && rule.os.arch !== ARCH) return false;
    if (rule.os.version && !new RegExp(rule.os.version).test(os.release())) return false;
  }
  if (rule.features) {
    for (const [k, v] of Object.entries(rule.features)) if (!!features[k] !== v) return false;
  }
  return true;
}

/** Mojang-Regelwerk: ohne Regeln erlaubt; sonst entscheidet die letzte passende Regel */
export function rulesAllow(rules: Rule[] | undefined, features: Record<string, boolean> = {}): boolean {
  if (!rules?.length) return true;
  let allowed = false;
  for (const r of rules) if (ruleMatches(r, features)) allowed = r.action === 'allow';
  return allowed;
}

/* ---------------- Libraries ---------------- */

/** Maven-Koordinate → relativer Pfad ("net.fabricmc:fabric-loader:0.16.9" → net/fabricmc/…/fabric-loader-0.16.9.jar) */
function mavenPath(name: string): string {
  const [coords, ext = 'jar'] = name.split('@');
  const [group, artifact, version, classifier] = coords.split(':');
  return `${group.replace(/\./g, '/')}/${artifact}/${version}/${artifact}-${version}${classifier ? `-${classifier}` : ''}.${ext}`;
}

interface ResolvedLibraries {
  classpath: string[];
  downloads: DownloadTask[];
  /** JARs, aus denen native DLLs entpackt werden müssen */
  natives: { file: string; exclude: string[] }[];
}

/** Welche "natives-windows*"-Klassifizierung zu dieser CPU passt */
const NATIVE_CLASSIFIER = process.arch === 'arm64' ? 'natives-windows-arm64' : process.arch === 'ia32' ? 'natives-windows-x86' : 'natives-windows';

function resolveLibraries(version: VersionJson): ResolvedLibraries {
  const out: ResolvedLibraries = { classpath: [], downloads: [], natives: [] };
  for (const lib of version.libraries as Library[]) {
    if (!rulesAllow(lib.rules)) continue;
    const classifier = lib.name.split(':')[3];

    // Moderne Natives (1.19+): eigene Library mit Klassifizierung "natives-windows…"
    if (classifier?.startsWith('natives-')) {
      if (classifier !== NATIVE_CLASSIFIER) continue;
    }

    // Haupt-Artefakt
    const art = lib.downloads?.artifact;
    if (art?.path) {
      const dest = path.join(paths.libraries, art.path);
      if (art.url) out.downloads.push({ url: art.url, dest, sha1: art.sha1, size: art.size });
      out.classpath.push(dest);
      if (classifier?.startsWith('natives-')) out.natives.push({ file: dest, exclude: ['META-INF/'] });
    } else if (!lib.downloads && !lib.natives) {
      // Fabric-Stil: nur Maven-Name + Repository-URL
      const rel = mavenPath(lib.name);
      const dest = path.join(paths.libraries, rel);
      const base = (lib.url ?? 'https://libraries.minecraft.net/').replace(/\/?$/, '/');
      out.downloads.push({ url: base + rel, dest, sha1: lib.sha1, size: lib.size });
      out.classpath.push(dest);
    }

    // Alte Natives (bis 1.18): "natives"-Map + Klassifizierer-Downloads
    const nativeKey = lib.natives?.windows?.replace('${arch}', process.arch === 'ia32' ? '32' : '64');
    const nat = nativeKey ? lib.downloads?.classifiers?.[nativeKey] : undefined;
    if (nat) {
      const dest = path.join(paths.libraries, nat.path);
      out.downloads.push({ url: nat.url, dest, sha1: nat.sha1, size: nat.size });
      out.natives.push({ file: dest, exclude: lib.extract?.exclude ?? ['META-INF/'] });
    }
  }
  // gleiche Library nicht doppelt auf den Classpath
  out.classpath = [...new Set(out.classpath)];
  return out;
}

function extractNatives(natives: ResolvedLibraries['natives'], dir: string) {
  const marker = path.join(dir, '.divine-extracted');
  if (fs.existsSync(marker)) return;
  fs.mkdirSync(dir, { recursive: true });
  for (const n of natives) {
    const zip = new AdmZip(n.file);
    for (const entry of zip.getEntries()) {
      if (entry.isDirectory) continue;
      if (n.exclude.some((ex) => entry.entryName.startsWith(ex))) continue;
      if (!/\.(dll|so|dylib|jnilib)$/i.test(entry.entryName)) continue;
      fs.writeFileSync(path.join(dir, path.basename(entry.entryName)), entry.getData());
    }
  }
  fs.writeFileSync(marker, new Date().toISOString());
}

/* ---------------- Assets ---------------- */

interface AssetIndex {
  objects: Record<string, { hash: string; size: number }>;
  virtual?: boolean;
  map_to_resources?: boolean;
}

async function prepareAssets(version: VersionJson, gameDir: string, concurrency: number, hooks: LaunchHooks): Promise<string> {
  const indexFile = path.join(paths.assets, 'indexes', `${version.assetIndex.id}.json`);
  await downloadFile({ url: version.assetIndex.url, dest: indexFile, sha1: version.assetIndex.sha1 });
  const index = readJson<AssetIndex>(indexFile, { objects: {} });

  const tasks: DownloadTask[] = Object.values(index.objects).map(({ hash, size }) => ({
    url: `https://resources.download.minecraft.net/${hash.slice(0, 2)}/${hash}`,
    dest: path.join(paths.assets, 'objects', hash.slice(0, 2), hash),
    sha1: hash,
    size,
  }));
  await downloadAll(tasks, concurrency, (done, total) => {
    hooks.progress({ stage: 'assets', text: 'Lade Assets', percent: 0.4 + 0.5 * (done / total), detail: `${done} / ${total}` });
  });

  // Sehr alte Versionen erwarten Assets unter ihrem echten Dateinamen
  if (index.virtual || index.map_to_resources) {
    const target = index.map_to_resources ? path.join(gameDir, 'resources') : path.join(paths.assets, 'virtual', version.assetIndex.id);
    for (const [name, { hash }] of Object.entries(index.objects)) {
      const dest = path.join(target, name);
      if (fs.existsSync(dest)) continue;
      fs.mkdirSync(path.dirname(dest), { recursive: true });
      fs.copyFileSync(path.join(paths.assets, 'objects', hash.slice(0, 2), hash), dest);
    }
    return target;
  }
  return paths.assets;
}

/* ---------------- Argumente ---------------- */

function expandArgs(args: Argument[], vars: Record<string, string>, features: Record<string, boolean>): string[] {
  const out: string[] = [];
  for (const a of args) {
    if (typeof a === 'string') out.push(a);
    else if (rulesAllow(a.rules, features)) out.push(...(Array.isArray(a.value) ? a.value : [a.value]));
  }
  return out.map((s) => s.replace(/\$\{([^}]+)\}/g, (m, key: string) => vars[key] ?? m));
}

/** Teilt "a b 'c d'" in Argumente auf (Anführungszeichen werden respektiert) */
function splitArgs(s: string): string[] {
  return (s.match(/"[^"]*"|'[^']*'|\S+/g) ?? []).map((x) => x.replace(/^["']|["']$/g, ''));
}

/* ---------------- Start ---------------- */

export async function launchGame(opts: LaunchOptions, hooks: LaunchHooks): Promise<void> {
  if (running) throw new Error('Minecraft läuft bereits.');
  const settings = getSettings();
  const inst = getInstance(opts.instanceId);
  const gameDir = instanceDir(inst.id);
  const p = (stage: ProgressEvent['stage'], text: string, percent: number, detail?: string) =>
    hooks.progress({ stage, text, percent, detail });

  // 1. Account
  p('auth', 'Prüfe Microsoft-Account', 0.01);
  const session = await getActiveSession();

  // 2. Version
  p('version', `Lade Minecraft ${inst.mcVersion}${inst.loader === 'fabric' ? ' + Fabric' : ''}`, 0.03);
  const version = await resolveVersion(inst.mcVersion, inst.loader, inst.loaderVersion);

  // 3. Java
  const neededJava = version.javaVersion?.majorVersion ?? 8;
  let javaPath = settings.javaPath && fs.existsSync(settings.javaPath) ? settings.javaPath : '';
  if (!javaPath) {
    p('java', `Suche Java ${neededJava}`, 0.06);
    const found = await findJavaFor(neededJava);
    javaPath = found?.path ?? (await downloadTemurin(neededJava, (text, pct) => p('java', text, 0.06 + 0.09 * Math.max(0, pct)))).path;
  }

  // 4. Client + Libraries
  const clientId = version.clientVersion ?? inst.mcVersion;
  const clientJar = path.join(paths.versions, clientId, `${clientId}.jar`);
  const libs = resolveLibraries(version);
  const libTasks: DownloadTask[] = [
    { url: version.downloads.client.url, dest: clientJar, sha1: version.downloads.client.sha1, size: version.downloads.client.size },
    ...libs.downloads,
  ];
  await downloadAll(libTasks, settings.parallelDownloads, (done, total, file) =>
    p('libraries', 'Lade Libraries', 0.15 + 0.25 * (done / total), `${done} / ${total} · ${file}`),
  );

  // 5. Assets
  const assetsRoot = await prepareAssets(version, gameDir, settings.parallelDownloads, hooks);

  // 6. Natives
  p('natives', 'Entpacke Natives', 0.92);
  const nativesDir = path.join(paths.natives, version.id);
  extractNatives(libs.natives, nativesDir);

  // 7. Instanz vorbereiten
  p('prepare', 'Bereite Instanz vor', 0.95);
  prepareOptions(inst);
  hooks.log([`[DeuClient] ${await syncDivineCore(inst)}`]);

  // 8. Argumente
  const join = opts.joinServer?.trim();
  const features: Record<string, boolean> = {
    has_custom_resolution: !settings.fullscreen,
    is_quick_play_multiplayer: !!join,
  };
  const vars: Record<string, string> = {
    auth_player_name: session.name,
    version_name: version.id,
    game_directory: gameDir,
    assets_root: assetsRoot,
    game_assets: assetsRoot,
    assets_index_name: version.assetIndex.id,
    auth_uuid: session.uuid,
    auth_access_token: session.accessToken,
    auth_session: `token:${session.accessToken}:${session.uuid}`,
    auth_xuid: session.xuid,
    clientid: '',
    user_type: 'msa',
    user_properties: '{}',
    version_type: 'DeuClient',
    resolution_width: String(settings.width),
    resolution_height: String(settings.height),
    natives_directory: nativesDir,
    launcher_name: 'DeuClient',
    launcher_version: LAUNCHER_VERSION,
    classpath: [...libs.classpath, clientJar].join(path.delimiter),
    classpath_separator: path.delimiter,
    library_directory: paths.libraries,
    quickPlayMultiplayer: join ?? '',
  };

  const ram = inst.ramMb || settings.ramMb;
  const jvm = [`-Xmx${ram}M`, `-Xms${Math.min(1024, ram)}M`, ...splitArgs(settings.jvmArgs)];
  if (version.arguments?.jvm) jvm.push(...expandArgs(version.arguments.jvm, vars, features));
  else jvm.push(...expandArgs(['-Djava.library.path=${natives_directory}', '-cp', '${classpath}'], vars, features));

  const game = version.arguments?.game
    ? expandArgs(version.arguments.game, vars, features)
    : expandArgs(splitArgs(version.minecraftArguments ?? ''), vars, features);
  if (settings.fullscreen) game.push('--fullscreen');
  else if (!version.arguments?.game) game.push('--width', vars.resolution_width, '--height', vars.resolution_height);
  // Direkt joinen: ältere Versionen ohne Quick-Play nutzen --server/--port
  if (join && !game.includes('--quickPlayMultiplayer')) {
    const [host, port = '25565'] = join.split(':');
    game.push('--server', host, '--port', port);
  }

  // Nur für automatische Tests: zusätzliche JVM-/Spiel-Argumente per Umgebungsvariable
  if (process.env.DEUCLIENT_TEST_JVM) jvm.push(...splitArgs(process.env.DEUCLIENT_TEST_JVM));
  if (process.env.DEUCLIENT_TEST_GAME) game.push(...splitArgs(process.env.DEUCLIENT_TEST_GAME));
  const args = [...jvm, version.mainClass, ...game];

  // Zugriffstoken niemals ins Log schreiben
  const safe = args.map((a) => (a === session.accessToken ? '********' : a));
  hooks.log([
    `[DeuClient] Starte ${inst.name} (Minecraft ${inst.mcVersion}, ${inst.loader})`,
    `[DeuClient] Java: ${javaPath}`,
    `[DeuClient] Spieler: ${session.name}${join ? ` · verbinde mit ${join}` : ''}`,
    `[DeuClient] Argumente: ${safe.filter((a) => !a.includes(path.delimiter) || a.length < 200).join(' ')}`,
  ]);

  // 9. Start (DEUCLIENT_DRY_RUN=1 → alles vorbereiten, aber Java nicht starten; für Tests)
  if (process.env.DEUCLIENT_DRY_RUN) {
    hooks.log(['[DeuClient] Testlauf: Spiel wird nicht gestartet', `[DeuClient] Main-Klasse: ${version.mainClass}`]);
    p('done', 'Testlauf abgeschlossen', 1);
    return;
  }
  p('launch', 'Starte Minecraft', 0.99);
  const startedAt = Date.now();
  const child = spawn(javaPath, args, {
    cwd: gameDir,
    detached: settings.afterLaunch === 'close',
    windowsHide: false,
    env: { ...process.env, JAVA_TOOL_OPTIONS: undefined },
  });
  running = child;
  runningInstance = inst.id;
  if (settings.gamePriority === 'high' && child.pid) {
    try {
      os.setPriority(child.pid, os.constants.priority.PRIORITY_HIGH);
    } catch {
      // keine Berechtigung: normale Prioritaet
    }
  }
  updateInstance(inst.id, { lastPlayed: Date.now() });
  hooks.state(getGameState());

  // Ausgaben zeilenweise sammeln und gebündelt an das Konsolenfenster schicken
  let pending: string[] = [];
  let partial = '';
  const flush = setInterval(() => {
    if (pending.length) {
      hooks.log(pending);
      pending = [];
    }
  }, 120);
  const onData = (d: Buffer) => {
    const text = partial + d.toString('utf8');
    const lines = text.split(/\r?\n/);
    partial = lines.pop() ?? '';
    pending.push(...lines);
  };
  child.stdout?.on('data', onData);
  child.stderr?.on('data', onData);

  child.on('error', (e) => {
    hooks.log([`[DeuClient] Fehler beim Start: ${e.message}`]);
  });
  child.on('exit', (code) => {
    clearInterval(flush);
    if (partial) pending.push(partial);
    pending.push(`[DeuClient] Minecraft wurde beendet (Code ${code ?? '?'})`);
    hooks.log(pending);
    running = null;
    runningInstance = undefined;
    addPlayTime(inst.id, Date.now() - startedAt);
    hooks.state({ ...getGameState(), running: false });
    hooks.progress({ stage: code === 0 || code === null ? 'done' : 'error', text: code ? `Minecraft abgestürzt (Code ${code})` : 'Spiel beendet', percent: 1 });
  });

  p('running', 'Minecraft läuft', 1);
  if (settings.afterLaunch === 'close') child.unref();
}
