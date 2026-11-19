/**
 * Eigene Minecraft-Server, die auf diesem PC laufen.
 *
 *   servers/<id>/server.json      Einstellungen des Launchers
 *   servers/<id>/server.jar       Paper oder Vanilla (SHA-geprüft geladen)
 *   servers/<id>/server.properties, eula.txt, world/, plugins/
 *
 * Start/Stopp, Live-Konsole mit Befehlseingabe, Spielerliste, Adressen (lokal/LAN/öffentlich) und
 * optionale Portfreigabe per UPnP.
 */
import fs from 'node:fs';
import path from 'node:path';
import { spawn, type ChildProcess } from 'node:child_process';
import { shell } from 'electron';
import type { NewServerInput, ServerConfig, ServerRuntime } from '../shared/types';
import { downloadFile, fetchJson } from './util/download';
import { bundledModsDir, paths } from './util/paths';
import { readJson, writeJson } from './util/store';
import { downloadTemurin, findJavaFor } from './game/java';
import { getVanillaVersion } from './game/versions';
import { closePort, lanAddress, openPort } from './upnp';

export const serversDir = () => path.join(paths.data, 'servers');
const dirOf = (id: string) => path.join(serversDir(), id);

interface Running {
  proc: ChildProcess;
  log: string[];
  players: Set<string>;
  online: boolean;
  startedAt: number;
}
const running = new Map<string, Running>();

type Emit = (channel: 'server:log' | 'server:state', payload: unknown) => void;
let emit: Emit = () => {};
export function setServerEmitter(e: Emit) {
  emit = e;
}

/* ---------------- Verwaltung ---------------- */

export function listServers(): ServerConfig[] {
  if (!fs.existsSync(serversDir())) return [];
  return fs
    .readdirSync(serversDir())
    .map((d) => readJson<ServerConfig | null>(path.join(dirOf(d), 'server.json'), null))
    .filter((s): s is ServerConfig => !!s)
    .sort((a, b) => b.created - a.created);
}

export function getServer(id: string): ServerConfig {
  const s = readJson<ServerConfig | null>(path.join(dirOf(id), 'server.json'), null);
  if (!s) throw new Error(`Server "${id}" nicht gefunden`);
  return s;
}

function save(s: ServerConfig) {
  writeJson(path.join(dirOf(s.id), 'server.json'), s);
}

export function runtime(id: string): ServerRuntime {
  const r = running.get(id);
  return { id, running: !!r, online: !!r?.online, players: r ? [...r.players] : [], startedAt: r?.startedAt };
}

export function allRuntimes(): ServerRuntime[] {
  return listServers().map((s) => runtime(s.id));
}

export function serverLog(id: string): string[] {
  return running.get(id)?.log ?? readJson<string[]>(path.join(dirOf(id), 'last-log.json'), []);
}

function slug(name: string): string {
  const base = name.toLowerCase().normalize('NFKD').replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') || 'server';
  let id = base;
  for (let i = 2; fs.existsSync(dirOf(id)); i++) id = `${base}-${i}`;
  return id;
}

/** Nächster freier Port ab 25565 (unter den eigenen Servern) */
function nextPort(): number {
  const used = new Set(listServers().map((s) => s.port));
  let p = 25565;
  while (used.has(p)) p++;
  return p;
}

/* ---------------- Erstellen ---------------- */

async function downloadServerJar(cfg: ServerConfig, onProgress: (t: string) => void): Promise<void> {
  const jar = path.join(dirOf(cfg.id), 'server.jar');
  if (cfg.type === 'paper') {
    onProgress(`Lade Paper ${cfg.mcVersion} ...`);
    const build = await fetchJson<{ id: number; downloads: Record<string, { name: string; checksums: { sha256: string }; size: number; url: string }> }>(
      `https://fill.papermc.io/v3/projects/paper/versions/${encodeURIComponent(cfg.mcVersion)}/builds/latest`,
    );
    const dl = build.downloads['server:default'];
    if (!dl) throw new Error(`Für Minecraft ${cfg.mcVersion} gibt es keinen Paper-Build.`);
    await downloadFile({ url: dl.url, dest: jar, sha256: dl.checksums.sha256, size: dl.size });
    cfg.build = String(build.id);
  } else {
    onProgress(`Lade Vanilla-Server ${cfg.mcVersion} ...`);
    const v = (await getVanillaVersion(cfg.mcVersion)) as unknown as { downloads: { server?: { url: string; sha1: string; size: number } } };
    if (!v.downloads.server) throw new Error(`Minecraft ${cfg.mcVersion} hat keinen offiziellen Server.`);
    await downloadFile({ url: v.downloads.server.url, dest: jar, sha1: v.downloads.server.sha1, size: v.downloads.server.size });
  }
}

/** Schreibt/aktualisiert server.properties (vorhandene, unbekannte Schlüssel bleiben erhalten) */
function writeProperties(cfg: ServerConfig) {
  const file = path.join(dirOf(cfg.id), 'server.properties');
  const props = new Map<string, string>();
  if (fs.existsSync(file)) {
    for (const line of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
      const i = line.indexOf('=');
      if (i > 0 && !line.startsWith('#')) props.set(line.slice(0, i), line.slice(i + 1));
    }
  }
  props.set('server-port', String(cfg.port));
  props.set('motd', cfg.motd.replace(/\n/g, '\\n'));
  props.set('max-players', String(cfg.maxPlayers));
  props.set('gamemode', cfg.gamemode);
  props.set('difficulty', cfg.difficulty);
  props.set('online-mode', String(cfg.onlineMode));
  props.set('pvp', String(cfg.pvp));
  props.set('white-list', String(cfg.whitelist));
  const out = ['# Erstellt vom DeuClient', ...[...props].map(([k, v]) => `${k}=${v}`)];
  fs.writeFileSync(file, out.join('\n') + '\n');
}

export async function createServer(input: NewServerInput, onProgress: (t: string) => void): Promise<ServerConfig> {
  if (!input.acceptEula) throw new Error('Bitte die Minecraft-EULA akzeptieren (https://aka.ms/MinecraftEULA).');
  const cfg: ServerConfig = {
    id: slug(input.name),
    name: input.name.trim() || 'Mein Server',
    type: input.type,
    mcVersion: input.mcVersion,
    ramMb: input.ramMb,
    port: input.port || nextPort(),
    maxPlayers: input.maxPlayers,
    gamemode: input.gamemode,
    difficulty: input.difficulty,
    motd: input.motd || `${input.name} – gehostet mit dem DeuClient`,
    onlineMode: input.onlineMode,
    pvp: input.pvp,
    whitelist: false,
    created: Date.now(),
  };
  const dir = dirOf(cfg.id);
  fs.mkdirSync(path.join(dir, 'plugins'), { recursive: true });
  try {
    await downloadServerJar(cfg, onProgress);
    // EULA: der Nutzer hat im Launcher ausdrücklich zugestimmt
    fs.writeFileSync(path.join(dir, 'eula.txt'), `# Zugestimmt im DeuClient am ${new Date().toISOString()}\neula=true\n`);
    writeProperties(cfg);
    save(cfg);
    onProgress('Server erstellt');
    return cfg;
  } catch (e) {
    fs.rmSync(dir, { recursive: true, force: true });
    throw e;
  }
}

export function updateServer(id: string, patch: Partial<ServerConfig>): ServerConfig {
  if (running.has(id)) throw new Error('Bitte den Server zuerst stoppen.');
  const cfg = { ...getServer(id), ...patch, id };
  writeProperties(cfg);
  save(cfg);
  return cfg;
}

export function deleteServer(id: string) {
  if (running.has(id)) throw new Error('Bitte den Server zuerst stoppen.');
  fs.rmSync(dirOf(id), { recursive: true, force: true });
}

export function openServerFolder(id: string, sub?: string) {
  const dir = sub ? path.join(dirOf(id), sub) : dirOf(id);
  fs.mkdirSync(dir, { recursive: true });
  void shell.openPath(dir);
}

/* ---------------- Start / Stopp ---------------- */

const ANSI = /\[[0-9;]*[A-Za-z]/g;

function pushLog(id: string, r: Running, raw: string[]) {
  const lines = raw.map((l) => l.replace(ANSI, ''));
  r.log.push(...lines);
  if (r.log.length > 3000) r.log = r.log.slice(-3000);
  emit('server:log', { id, lines });
}

export async function startServer(id: string, onProgress: (t: string) => void): Promise<void> {
  if (running.has(id)) return;
  const cfg = getServer(id);
  const dir = dirOf(id);
  if (!fs.existsSync(path.join(dir, 'server.jar'))) await downloadServerJar(cfg, onProgress);

  // passendes Java (laut Mojang-Versionsdaten)
  const v = await getVanillaVersion(cfg.mcVersion);
  const major = v.javaVersion?.majorVersion ?? 21;
  onProgress(`Suche Java ${major} ...`);
  const java = (await findJavaFor(major)) ?? (await downloadTemurin(major, (t) => onProgress(t)));
  const javaBin = java.path.replace(/javaw\.exe$/i, 'java.exe');

  // UTF-8 erzwingen, damit Umlaute in der Konsole stimmen
  const args = [`-Xms${Math.min(1024, cfg.ramMb)}M`, `-Xmx${cfg.ramMb}M`, '-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8', '-jar', 'server.jar', 'nogui'];
  const proc = spawn(javaBin, args, { cwd: dir, windowsHide: true });
  const r: Running = { proc, log: [], players: new Set(), online: false, startedAt: Date.now() };
  running.set(id, r);
  pushLog(id, r, [`[DeuClient] Starte ${cfg.name} (${cfg.type} ${cfg.mcVersion}, ${cfg.ramMb} MB RAM, Port ${cfg.port})`]);
  emit('server:state', runtime(id));

  let partial = '';
  const onData = (d: Buffer) => {
    const text = partial + d.toString('utf8');
    const lines = text.split(/\r?\n/);
    partial = lines.pop() ?? '';
    for (const line of lines) {
      if (/Done \([\d.,]+s\)!/.test(line)) {
        r.online = true;
        emit('server:state', runtime(id));
      }
      const joined = /: (\w{2,16}) joined the game/.exec(line);
      const left = /: (\w{2,16}) left the game/.exec(line);
      if (joined) r.players.add(joined[1]);
      if (left) r.players.delete(left[1]);
      if (joined || left) emit('server:state', runtime(id));
    }
    if (lines.length) pushLog(id, r, lines);
  };
  proc.stdout?.on('data', onData);
  proc.stderr?.on('data', onData);
  proc.on('exit', (code) => {
    pushLog(id, r, [`[DeuClient] Server beendet (Code ${code ?? '?'})`]);
    writeJson(path.join(dir, 'last-log.json'), r.log.slice(-500));
    running.delete(id);
    emit('server:state', runtime(id));
  });
  proc.on('error', (e) => pushLog(id, r, [`[DeuClient] Fehler: ${e.message}`]));
  save({ ...cfg, lastStarted: Date.now() });
}

export function sendCommand(id: string, command: string) {
  const r = running.get(id);
  if (!r) throw new Error('Server läuft nicht.');
  const cmd = command.replace(/^\//, '').trim();
  if (!cmd) return;
  pushLog(id, r, [`> ${cmd}`]);
  r.proc.stdin?.write(`${cmd}\n`);
}

/** Sauber stoppen ("stop"), nach 30 s hart beenden */
export function stopServer(id: string): Promise<void> {
  const r = running.get(id);
  if (!r) return Promise.resolve();
  return new Promise((resolve) => {
    const timer = setTimeout(() => r.proc.kill(), 30_000);
    r.proc.once('exit', () => {
      clearTimeout(timer);
      resolve();
    });
    r.proc.stdin?.write('stop\n');
  });
}

export async function stopAll(): Promise<void> {
  await Promise.all([...running.keys()].map((id) => stopServer(id)));
}

/* ---------------- Adressen ---------------- */

export async function addresses(id: string): Promise<{ local: string; lan: string | null; publicIp: string | null; upnp: boolean }> {
  const cfg = getServer(id);
  const port = cfg.port === 25565 ? '' : `:${cfg.port}`;
  const lan = await lanAddress();
  return {
    local: `localhost${port}`,
    lan: lan ? `${lan}${port}` : null,
    publicIp: cfg.publicIp ? `${cfg.publicIp}${port}` : null,
    upnp: !!cfg.upnp,
  };
}

/** Port im Router freigeben (UPnP) – nur auf ausdrücklichen Wunsch des Nutzers */
export async function enableUpnp(id: string): Promise<string> {
  const cfg = getServer(id);
  const ip = await openPort(cfg.port, `DeuClient Server ${cfg.name}`);
  save({ ...cfg, upnp: true, publicIp: ip });
  return ip;
}

export async function disableUpnp(id: string): Promise<void> {
  const cfg = getServer(id);
  try {
    await closePort(cfg.port);
  } finally {
    save({ ...cfg, upnp: false, publicIp: undefined });
  }
}
