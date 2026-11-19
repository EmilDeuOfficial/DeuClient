/**
 * Typisierte Brücke zur Preload-API (window.divine).
 * Jede Funktion entspricht einem IPC-Kanal im Main-Prozess (electron/main.ts).
 */
import type {
  AccountsState, ContentType, EventMap, GameState, InstalledContent, InstallResult, Instance, JavaInstall,
  LaunchOptions, McVersion, ModrinthCategory, ModrinthProject, ModrinthSearchParams, ModrinthSearchResult,
  ModrinthVersion, NewInstanceInput, Settings, UpdateStatus, SystemInfo, WorldInfo, ScreenshotInfo, InstanceStats, ServerConfig, ServerRuntime, NewServerInput, ServerAddresses, CustomCape, EquippedCosmetics,
} from '../shared/types';

declare global {
  interface Window {
    divine: {
      invoke: (channel: string, ...args: unknown[]) => Promise<any>;
      on: (channel: string, cb: (payload: any) => void) => () => void;
    };
  }
}

const inv = <T>(channel: string, ...args: unknown[]): Promise<T> => window.divine.invoke(channel, ...args);

export function onEvent<K extends keyof EventMap>(channel: K, cb: (payload: EventMap[K]) => void): () => void {
  return window.divine.on(channel, cb);
}

export const api = {
  window: {
    minimize: () => inv<void>('window:minimize'),
    maximize: () => inv<void>('window:maximize'),
    close: () => inv<void>('window:close'),
  },
  app: {
    systemInfo: () => inv<SystemInfo>('app:systemInfo'),
    checkOnline: () => inv<boolean>('app:checkOnline'),
    openDataFolder: () => inv<void>('app:openDataFolder'),
    clearCache: () => inv<number>('app:clearCache'),
    openExternal: (url: string) => inv<void>('shell:openExternal', url),
    divineCore: () => inv<{ version: string; minecraft: string[] } | null>('divinecore:info'),
  },
  update: {
    status: () => inv<UpdateStatus>('update:status'),
    check: () => inv<UpdateStatus>('update:check'),
    install: () => inv<void>('update:install'),
  },
  settings: {
    get: () => inv<Settings>('settings:get'),
    save: (patch: Partial<Settings>) => inv<Settings>('settings:save', patch),
  },
  accounts: {
    get: () => inv<AccountsState>('accounts:get'),
    refresh: () => inv<AccountsState>('accounts:refresh'),
    add: () => inv<AccountsState>('accounts:add'),
    select: (uuid: string) => inv<AccountsState>('accounts:select', uuid),
    remove: (uuid: string) => inv<AccountsState>('accounts:remove', uuid),
  },
  instances: {
    list: () => inv<Instance[]>('instances:list'),
    ensureDefault: () => inv<Instance>('instances:ensureDefault'),
    create: (input: NewInstanceInput) => inv<Instance>('instances:create', input),
    update: (id: string, patch: Partial<Instance>) => inv<Instance>('instances:update', id, patch),
    remove: (id: string) => inv<void>('instances:delete', id),
    openFolder: (id: string, sub?: string) => inv<void>('instances:openFolder', id, sub),
    worlds: (id: string) => inv<WorldInfo[]>('instances:worlds', id),
    screenshots: (id: string) => inv<ScreenshotInfo[]>('instances:screenshots', id),
    openScreenshot: (id: string, file: string) => inv<void>('instances:openScreenshot', id, file),
    stats: (id: string) => inv<InstanceStats>('instances:stats', id),
    managedMods: (id: string) => inv<{ file: string; id: string; name: string; version: string; description?: string; core: boolean; removed: boolean }[]>('instances:managedMods', id),
    restoreManaged: (id: string, file?: string) => inv<void>('instances:restoreManaged', id, file),
  },
  versions: {
    list: (snapshots = false) => inv<McVersion[]>('versions:list', snapshots),
    fabric: () => inv<string[]>('versions:fabric'),
  },
  game: {
    launch: (opts: LaunchOptions) => inv<void>('game:launch', opts),
    kill: () => inv<void>('game:kill'),
    state: () => inv<GameState>('game:state'),
  },
  java: {
    list: () => inv<JavaInstall[]>('java:list'),
    download: (major: number) => inv<JavaInstall>('java:download', major),
    pick: () => inv<string | null>('dialog:pickJava'),
  },
  modrinth: {
    search: (p: ModrinthSearchParams) => inv<ModrinthSearchResult>('modrinth:search', p),
    categories: () => inv<ModrinthCategory[]>('modrinth:categories'),
    project: (id: string) => inv<ModrinthProject>('modrinth:project', id),
    projects: (ids: string[]) => inv<ModrinthProject[]>('modrinth:projects', ids),
    versions: (id: string, loaders?: string[], gameVersions?: string[]) => inv<ModrinthVersion[]>('modrinth:versions', id, loaders, gameVersions),
    install: (instId: string, projectId: string, type: ContentType, versionId?: string) =>
      inv<InstallResult>('modrinth:install', instId, projectId, type, versionId),
    installed: (instId: string, type: ContentType) => inv<InstalledContent[]>('modrinth:installed', instId, type),
    toggle: (instId: string, type: ContentType, file: string) => inv<void>('modrinth:toggle', instId, type, file),
    remove: (instId: string, type: ContentType, file: string) => inv<void>('modrinth:remove', instId, type, file),
    checkUpdates: (instId: string, type: ContentType) => inv<InstalledContent[]>('modrinth:checkUpdates', instId, type),
    update: (instId: string, type: ContentType, file: string, projectId: string, versionId: string) =>
      inv<void>('modrinth:update', instId, type, file, projectId, versionId),
    importMrpack: () => inv<Instance | null>('modrinth:importMrpack'),
  },
  servers: {
    list: () => inv<ServerConfig[]>('servers:list'),
    runtimes: () => inv<ServerRuntime[]>('servers:runtimes'),
    create: (input: NewServerInput) => inv<ServerConfig>('servers:create', input),
    update: (id: string, patch: Partial<ServerConfig>) => inv<ServerConfig>('servers:update', id, patch),
    remove: (id: string) => inv<void>('servers:delete', id),
    start: (id: string) => inv<void>('servers:start', id),
    stop: (id: string) => inv<void>('servers:stop', id),
    command: (id: string, cmd: string) => inv<void>('servers:command', id, cmd),
    log: (id: string) => inv<string[]>('servers:log', id),
    addresses: (id: string) => inv<ServerAddresses>('servers:addresses', id),
    openFolder: (id: string, sub?: string) => inv<void>('servers:openFolder', id, sub),
    upnpOn: (id: string) => inv<string>('servers:upnpOn', id),
    upnpOff: (id: string) => inv<void>('servers:upnpOff', id),
    paperVersions: () => inv<string[]>('servers:paperVersions'),
  },
  capes: {
    list: () => inv<CustomCape[]>('capes:list'),
    save: (name: string, png: string) => inv<CustomCape>('capes:save', name, png),
    remove: (id: string) => inv<void>('capes:delete', id),
    equipped: () => inv<EquippedCosmetics>('capes:equipped'),
    equip: (sel: EquippedCosmetics) => inv<number>('capes:equip', sel),
  },
  console: {
    open: () => inv<void>('console:open'),
    getLogs: () => inv<string[]>('console:getLogs'),
    clear: () => inv<void>('console:clear'),
    openLogsFolder: () => inv<void>('console:openLogsFolder'),
  },
};
