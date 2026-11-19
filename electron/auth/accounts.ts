/**
 * Account-Verwaltung: mehrere Microsoft-Accounts speichern, wechseln, entfernen.
 * Refresh- und Access-Tokens werden mit Electron safeStorage (Windows DPAPI) verschlüsselt
 * und niemals an die Oberfläche weitergegeben.
 */
import { safeStorage, BrowserWindow } from 'electron';
import type { AccountInfo, AccountsState } from '../../shared/types';
import { paths } from '../util/paths';
import { readJson, writeJson } from '../util/store';
import { loginInteractive, loginWithRefreshToken, type MinecraftSession } from './microsoft';

interface StoredAccount extends AccountInfo {
  /** verschlüsselter Refresh-Token (base64) */
  refresh: string;
  /** verschlüsselter Minecraft-Token (base64) */
  access: string;
  expiresAt: number;
  xuid: string;
}

interface AccountFile {
  accounts: StoredAccount[];
  activeUuid: string | null;
}

function encrypt(text: string): string {
  if (!safeStorage.isEncryptionAvailable()) throw new Error('Sichere Speicherung (safeStorage) ist nicht verfügbar');
  return safeStorage.encryptString(text).toString('base64');
}

function decrypt(b64: string): string {
  return safeStorage.decryptString(Buffer.from(b64, 'base64'));
}

function load(): AccountFile {
  return readJson<AccountFile>(paths.accounts, { accounts: [], activeUuid: null });
}

function save(file: AccountFile) {
  writeJson(paths.accounts, file);
}

function toStored(s: MinecraftSession): StoredAccount {
  return {
    uuid: s.uuid,
    name: s.name,
    skinUrl: s.skinUrl,
    refresh: encrypt(s.refreshToken),
    access: encrypt(s.accessToken),
    expiresAt: s.expiresAt,
    xuid: s.xuid,
  };
}

export function getAccountsState(): AccountsState {
  const f = load();
  return {
    accounts: f.accounts.map(({ uuid, name, skinUrl }) => ({ uuid, name, skinUrl })),
    activeUuid: f.activeUuid,
  };
}

function upsert(session: MinecraftSession, makeActive: boolean) {
  const f = load();
  const stored = toStored(session);
  const idx = f.accounts.findIndex((a) => a.uuid === stored.uuid);
  if (idx >= 0) f.accounts[idx] = stored;
  else f.accounts.push(stored);
  if (makeActive || !f.activeUuid) f.activeUuid = stored.uuid;
  save(f);
}

export async function addAccount(parent?: BrowserWindow): Promise<AccountsState> {
  const session = await loginInteractive(parent);
  upsert(session, true);
  return getAccountsState();
}

export function selectAccount(uuid: string): AccountsState {
  const f = load();
  if (f.accounts.some((a) => a.uuid === uuid)) f.activeUuid = uuid;
  save(f);
  return getAccountsState();
}

export function removeAccount(uuid: string): AccountsState {
  const f = load();
  f.accounts = f.accounts.filter((a) => a.uuid !== uuid);
  if (f.activeUuid === uuid) f.activeUuid = f.accounts[0]?.uuid ?? null;
  save(f);
  return getAccountsState();
}

/**
 * Liefert eine gültige Spielsitzung für den aktiven Account.
 * Ist der Minecraft-Token (fast) abgelaufen, wird er still per Refresh-Token erneuert.
 */
export async function getActiveSession(): Promise<MinecraftSession> {
  const f = load();
  const acc = f.accounts.find((a) => a.uuid === f.activeUuid);
  if (!acc) throw new Error('Bitte zuerst mit einem Microsoft-Account anmelden.');

  if (acc.expiresAt - Date.now() > 10 * 60 * 1000) {
    return {
      uuid: acc.uuid,
      name: acc.name,
      skinUrl: acc.skinUrl,
      accessToken: decrypt(acc.access),
      expiresAt: acc.expiresAt,
      refreshToken: decrypt(acc.refresh),
      xuid: acc.xuid,
    };
  }
  try {
    const session = await loginWithRefreshToken(decrypt(acc.refresh));
    upsert(session, true);
    return session;
  } catch (e) {
    throw new Error(`Anmeldung für ${acc.name} abgelaufen – bitte Account neu hinzufügen. (${(e as Error).message})`);
  }
}

/** Beim Launcher-Start: Tokens im Hintergrund auffrischen, damit Name/Skin aktuell sind. */
export async function refreshAllQuietly(): Promise<AccountsState> {
  const f = load();
  for (const acc of f.accounts) {
    if (acc.expiresAt - Date.now() > 60 * 60 * 1000) continue;
    try {
      upsert(await loginWithRefreshToken(decrypt(acc.refresh)), false);
    } catch {
      /* offline oder Token ungültig – wird beim Spielstart gemeldet */
    }
  }
  return getAccountsState();
}
