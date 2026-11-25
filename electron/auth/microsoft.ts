/**
 * Offizieller Microsoft-Login für Minecraft (Java Edition):
 *
 *   1. Microsoft OAuth2 (Login-Fenster)   → Microsoft Access-/Refresh-Token
 *   2. Xbox Live (user.auth.xboxlive.com) → XBL-Token + User-Hash (uhs)
 *   3. XSTS (xsts.auth.xboxlive.com)      → XSTS-Token für Minecraft
 *   4. Minecraft Services login_with_xbox  → Minecraft Access-Token
 *   5. Profil abrufen                      → UUID, Name, Skin (prüft gleichzeitig den Spielbesitz)
 *
 * Es gibt bewusst KEINEN Offline-/Cracked-Login.
 */
import { BrowserWindow } from 'electron';
import { fetchJson, HttpError } from '../util/download';
import { getOAuthConfig } from './config';

export interface MinecraftSession {
  uuid: string;
  name: string;
  skinUrl?: string;
  accessToken: string;
  /** Zeitpunkt (ms), an dem der Minecraft-Token abläuft */
  expiresAt: number;
  refreshToken: string;
  xuid: string;
}

/** Öffnet das Microsoft-Anmeldefenster und liefert den Authorization-Code. */
export function requestAuthCode(parent?: BrowserWindow): Promise<string> {
  const cfg = getOAuthConfig();
  const url = new URL(cfg.authorizeUrl);
  url.searchParams.set('client_id', cfg.clientId);
  url.searchParams.set('response_type', 'code');
  url.searchParams.set('redirect_uri', cfg.redirectUri);
  url.searchParams.set('scope', cfg.scope);
  url.searchParams.set('prompt', 'select_account');

  return new Promise((resolve, reject) => {
    const win = new BrowserWindow({
      width: 520,
      height: 680,
      parent,
      modal: !!parent,
      title: 'DeuClient - Microsoft-Anmeldung',
      backgroundColor: '#141a16',
      autoHideMenuBar: true,
      webPreferences: {
        // eigene, flüchtige Sitzung → jeder Login startet frisch (mehrere Accounts möglich)
        partition: `msa-${Date.now()}`,
        nodeIntegration: false,
        contextIsolation: true,
      },
    });
    let finished = false;

    const check = (target: string) => {
      if (finished || !target.startsWith(cfg.redirectUri)) return;
      finished = true;
      const u = new URL(target);
      const code = u.searchParams.get('code');
      const error = u.searchParams.get('error_description') ?? u.searchParams.get('error');
      win.close();
      if (code) resolve(code);
      else reject(new Error(error ? `Microsoft-Login abgebrochen: ${error}` : 'Kein Login-Code erhalten'));
    };

    win.webContents.on('will-redirect', (_e, target) => check(target));
    win.webContents.on('will-navigate', (_e, target) => check(target));
    win.webContents.on('did-navigate', (_e, target) => check(target));
    win.webContents.on('did-redirect-navigation', (_e, target) => check(target));
    win.on('closed', () => {
      if (!finished) reject(new Error('Login-Fenster wurde geschlossen'));
    });
    win.loadURL(url.toString());
  });
}

interface MsTokenResponse {
  access_token: string;
  refresh_token: string;
  expires_in: number;
}

async function msToken(params: Record<string, string>): Promise<MsTokenResponse> {
  const cfg = getOAuthConfig();
  const body = new URLSearchParams({ client_id: cfg.clientId, redirect_uri: cfg.redirectUri, scope: cfg.scope, ...params });
  return fetchJson<MsTokenResponse>(cfg.tokenUrl, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
  });
}

/** Erklärt die typischen XSTS-Fehlercodes verständlich */
function xstsError(e: unknown): Error {
  if (e instanceof HttpError && e.status === 401) {
    const code = /"XErr"\s*:\s*(\d+)/.exec(e.body)?.[1];
    const messages: Record<string, string> = {
      '2148916233': 'Dieser Microsoft-Account hat kein Xbox-Profil. Bitte einmal auf minecraft.net anmelden.',
      '2148916235': 'Xbox Live ist in deinem Land nicht verfügbar.',
      '2148916236': 'Für diesen Account ist eine Altersverifizierung (Südkorea) nötig.',
      '2148916237': 'Für diesen Account ist eine Altersverifizierung (Südkorea) nötig.',
      '2148916238': 'Kinder-Account: Der Account muss von einem Erwachsenen zu einer Familie hinzugefügt werden.',
    };
    if (code && messages[code]) return new Error(messages[code]);
  }
  return e as Error;
}

/** Kompletter Token-Austausch Microsoft → Xbox → XSTS → Minecraft → Profil */
async function completeLogin(ms: MsTokenResponse): Promise<MinecraftSession> {
  const cfg = getOAuthConfig();
  const json = { 'Content-Type': 'application/json', Accept: 'application/json' };

  // 2. Xbox Live
  const xbl = await fetchJson<{ Token: string; DisplayClaims: { xui: { uhs: string }[] } }>(
    'https://user.auth.xboxlive.com/user/authenticate',
    {
      method: 'POST',
      headers: json,
      body: JSON.stringify({
        Properties: { AuthMethod: 'RPS', SiteName: 'user.auth.xboxlive.com', RpsTicket: `${cfg.rpsPrefix}${ms.access_token}` },
        RelyingParty: 'http://auth.xboxlive.com',
        TokenType: 'JWT',
      }),
    },
  );
  const uhs = xbl.DisplayClaims.xui[0].uhs;

  // 3. XSTS
  let xsts: { Token: string; DisplayClaims: { xui: { uhs: string; xid?: string }[] } };
  try {
    xsts = await fetchJson('https://xsts.auth.xboxlive.com/xsts/authorize', {
      method: 'POST',
      headers: json,
      body: JSON.stringify({
        Properties: { SandboxId: 'RETAIL', UserTokens: [xbl.Token] },
        RelyingParty: 'rp://api.minecraftservices.com/',
        TokenType: 'JWT',
      }),
    });
  } catch (e) {
    throw xstsError(e);
  }

  // 4. Minecraft
  const mc = await fetchJson<{ access_token: string; expires_in: number }>(
    'https://api.minecraftservices.com/authentication/login_with_xbox',
    { method: 'POST', headers: json, body: JSON.stringify({ identityToken: `XBL3.0 x=${uhs};${xsts.Token}` }) },
  ).catch((e) => {
    if (e instanceof HttpError && e.status === 403) {
      throw new Error('Diese Azure-App ist (noch) nicht für die Minecraft-API freigeschaltet. Siehe README.');
    }
    throw e;
  });

  // 5. Profil (404 = Minecraft nicht gekauft)
  let profile: { id: string; name: string; skins?: { url: string; state: string }[] };
  try {
    profile = await fetchJson('https://api.minecraftservices.com/minecraft/profile', {
      headers: { Authorization: `Bearer ${mc.access_token}` },
    });
  } catch (e) {
    if (e instanceof HttpError && e.status === 404) {
      throw new Error('Dieser Microsoft-Account besitzt kein Minecraft: Java Edition.');
    }
    throw e;
  }

  const skin = profile.skins?.find((s) => s.state === 'ACTIVE') ?? profile.skins?.[0];
  return {
    uuid: profile.id,
    name: profile.name,
    skinUrl: skin?.url?.replace(/^http:/, 'https:'),
    accessToken: mc.access_token,
    expiresAt: Date.now() + mc.expires_in * 1000,
    refreshToken: ms.refresh_token,
    xuid: xsts.DisplayClaims?.xui?.[0]?.xid ?? '0',
  };
}

/** Interaktiver Login über das Microsoft-Fenster */
export async function loginInteractive(parent?: BrowserWindow): Promise<MinecraftSession> {
  const code = await requestAuthCode(parent);
  const ms = await msToken({ code, grant_type: 'authorization_code' });
  return completeLogin(ms);
}

/** Stiller Login mit gespeichertem Refresh-Token */
export async function loginWithRefreshToken(refreshToken: string): Promise<MinecraftSession> {
  const ms = await msToken({ refresh_token: refreshToken, grant_type: 'refresh_token' });
  return completeLogin(ms);
}
