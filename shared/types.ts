/**
 * Gemeinsame Typen für Main-Prozess (electron/) und Oberfläche (src/).
 * Nur Typen – nichts hiervon existiert zur Laufzeit.
 */

export type Language = 'de' | 'en';
export type LoaderType = 'vanilla' | 'fabric';
export type ContentType = 'mod' | 'resourcepack' | 'shader';

/** Öffentliche Account-Infos (Tokens bleiben im Main-Prozess) */
export interface AccountInfo {
  uuid: string;
  name: string;
  skinUrl?: string;
}

export interface AccountsState {
  accounts: AccountInfo[];
  activeUuid: string | null;
}

export interface Instance {
  id: string;
  name: string;
  mcVersion: string;
  loader: LoaderType;
  /** Fabric-Loader-Version; leer = neueste stabile */
  loaderVersion?: string;
  /** Schlüssel eines eingebauten Icons (siehe src/components/InstanceIcon.tsx) */
  icon: string;
  created: number;
  lastPlayed?: number;
  /** Standard-Modpack "DeuClient" – kann nicht gelöscht werden */
  isDefault?: boolean;
  /** Gesamte Spielzeit in Millisekunden */
  playTimeMs?: number;
  /** Eigener Arbeitsspeicher für dieses Modpack (MB); leer = globale Einstellung */
  ramMb?: number;
  /** Kurzbeschreibung des Modpacks */
  description?: string;
  /** interne Migrationsversion des Standard-Modpacks */
  divineVersion?: number;
}

export interface WorldInfo {
  folder: string;
  name: string;
  lastPlayed: number;
  sizeMb: number;
  icon?: string;
}

export interface ScreenshotInfo {
  file: string;
  time: number;
  /** Vorschau als data:-URL */
  dataUrl: string;
}

export interface InstanceStats {
  mods: number;
  resourcepacks: number;
  shaders: number;
  worlds: number;
  screenshots: number;
  sizeMb: number;
}

export interface NewInstanceInput {
  name: string;
  mcVersion: string;
  loader: LoaderType;
  icon: string;
}

export interface Settings {
  ramMb: number;
  /** Manuell gewählter Java-Pfad (javaw.exe); leer = automatisch */
  javaPath: string;
  jvmArgs: string;
  width: number;
  height: number;
  fullscreen: boolean;
  afterLaunch: 'keep' | 'minimize' | 'close';
  language: Language;
  showConsole: boolean;
  parallelDownloads: number;
  selectedInstance: string;
  /** Launcher beim Windows-Start starten */
  launchOnStartup: boolean;
  /** Animationen und Uebergaenge in der Oberflaeche */
  animations: boolean;
  /** Partikel im Hintergrund */
  particles: boolean;
  /** Prozess-Prioritaet von Minecraft */
  gamePriority: 'normal' | 'high';
}

export interface SystemInfo {
  totalMemMb: number;
  platform: string;
  appVersion: string;
  dataDir: string;
}

export interface JavaInstall {
  path: string;
  major: number;
  version: string;
  source: string;
}

export interface LaunchOptions {
  instanceId: string;
  /** Direkt mit diesem Server verbinden (Quick-Play) */
  joinServer?: string;
}

export interface ProgressEvent {
  stage: 'auth' | 'version' | 'java' | 'libraries' | 'assets' | 'natives' | 'prepare' | 'launch' | 'running' | 'done' | 'error' | 'download';
  text: string;
  /** 0..1, -1 = unbestimmt */
  percent: number;
  detail?: string;
}

export interface GameState {
  running: boolean;
  instanceId?: string;
}

export interface McVersion {
  id: string;
  type: 'release' | 'snapshot' | 'old_beta' | 'old_alpha';
  releaseTime: string;
}

/* ---------- Modrinth ---------- */

export interface ModrinthSearchParams {
  query: string;
  projectType: ContentType;
  categories: string[];
  loader?: string;
  gameVersion?: string;
  index: 'relevance' | 'downloads' | 'follows' | 'newest' | 'updated';
  offset: number;
  limit: number;
}

export interface ModrinthHit {
  project_id: string;
  slug: string;
  title: string;
  description: string;
  author: string;
  downloads: number;
  follows: number;
  icon_url?: string;
  categories: string[];
  display_categories?: string[];
  project_type: string;
  date_modified: string;
  gallery?: string[];
}

export interface ModrinthSearchResult {
  hits: ModrinthHit[];
  total_hits: number;
  offset: number;
  limit: number;
}

export interface ModrinthCategory {
  name: string;
  project_type: string;
  header: string;
  icon: string;
}

export interface ModrinthProject {
  id: string;
  slug: string;
  title: string;
  description: string;
  body: string;
  icon_url?: string;
  downloads: number;
  followers: number;
  categories: string[];
  project_type: string;
  game_versions: string[];
  loaders: string[];
  gallery: { url: string; title?: string; featured: boolean }[];
  source_url?: string;
  issues_url?: string;
  wiki_url?: string;
  discord_url?: string;
  license?: { id: string; name: string };
  updated: string;
}

export interface ModrinthFile {
  url: string;
  filename: string;
  primary: boolean;
  size: number;
  hashes: { sha1: string; sha512: string };
}

export interface ModrinthVersion {
  id: string;
  project_id: string;
  name: string;
  version_number: string;
  version_type: 'release' | 'beta' | 'alpha';
  game_versions: string[];
  loaders: string[];
  date_published: string;
  downloads: number;
  files: ModrinthFile[];
  dependencies: { version_id?: string | null; project_id?: string | null; file_name?: string | null; dependency_type: 'required' | 'optional' | 'incompatible' | 'embedded' }[];
}

export interface InstalledContent {
  fileName: string;
  enabled: boolean;
  type: ContentType;
  size: number;
  title: string;
  projectId?: string;
  versionId?: string;
  versionNumber?: string;
  iconUrl?: string;
  /** DivineCore – vom Launcher verwaltet, immer aktiv */
  managed?: boolean;
  /** mitgelieferte Divine-Mod (z. B. Glass HUD) – kann deaktiviert, aber nicht gelöscht werden */
  bundled?: boolean;
  description?: string;
  update?: { versionId: string; versionNumber: string };
}

export interface InstallResult {
  installed: string[];
  skipped: string[];
}

/* ---------- Eigene Server ---------- */

export type ServerType = 'paper' | 'vanilla';

export interface ServerConfig {
  id: string;
  name: string;
  type: ServerType;
  mcVersion: string;
  /** Paper-Build-Nummer */
  build?: string;
  ramMb: number;
  port: number;
  maxPlayers: number;
  gamemode: 'survival' | 'creative' | 'adventure' | 'spectator';
  difficulty: 'peaceful' | 'easy' | 'normal' | 'hard';
  motd: string;
  onlineMode: boolean;
  pvp: boolean;
  whitelist: boolean;
  created: number;
  lastStarted?: number;
  /** Port per UPnP im Router freigegeben */
  upnp?: boolean;
  publicIp?: string;
}

export interface NewServerInput {
  name: string;
  type: ServerType;
  mcVersion: string;
  ramMb: number;
  port?: number;
  maxPlayers: number;
  gamemode: ServerConfig['gamemode'];
  difficulty: ServerConfig['difficulty'];
  motd: string;
  onlineMode: boolean;
  pvp: boolean;
  acceptEula: boolean;
}

export interface ServerRuntime {
  id: string;
  running: boolean;
  /** fertig gestartet ("Done") */
  online: boolean;
  players: string[];
  startedAt?: number;
}

export interface ServerAddresses {
  local: string;
  lan: string | null;
  publicIp: string | null;
  upnp: boolean;
}

/* ---------- Shop / eigene Capes ---------- */

export interface CustomCape {
  id: string;
  name: string;
  /** PNG als data:-URL (64×32) */
  dataUrl: string;
  created: number;
}

export interface EquippedCosmetics {
  /** eingebaute ID (z. B. "divine"), "custom:<id>" oder "" */
  cape: string;
  wings: string;
}

/** Kanäle, auf denen der Main-Prozess Ereignisse an die Oberfläche sendet */
/** Zustand des automatischen Updates */
export interface UpdateStatus {
  state: 'idle' | 'checking' | 'downloading' | 'ready' | 'none' | 'error';
  version?: string;
  percent?: number;
  error?: string;
}

export interface EventMap {
  'update:status': UpdateStatus;
  'launch:progress': ProgressEvent;
  'game:state': GameState;
  'game:log': string[];
  'download:progress': ProgressEvent;
  'accounts:changed': AccountsState;
  'instances:changed': Instance[];
  'server:log': { id: string; lines: string[] };
  'server:state': ServerRuntime;
  'server:progress': { id: string; text: string };
}
