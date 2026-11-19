/**
 * ============================================================
 *  MICROSOFT / AZURE KONFIGURATION
 * ============================================================
 *
 * Trage hier die "Anwendungs-ID (Client)" deiner eigenen Azure-App ein
 * (siehe README → "Azure Client-ID eintragen").
 *
 * Alternativ kann die ID beim Start über die Umgebungsvariable
 * DEUCLIENT_AZURE_CLIENT_ID gesetzt werden.
 *
 * Bleibt das Feld leer, nutzt der Launcher den öffentlichen Microsoft-Live-
 * Client, den auch viele Open-Source-Launcher (z. B. msmc) als Standard verwenden.
 */
export const AZURE_CLIENT_ID: string = process.env.DEUCLIENT_AZURE_CLIENT_ID ?? '';

/** Redirect-URI deiner Azure-App (Plattform "Mobile und Desktop") */
export const AZURE_REDIRECT_URI = 'https://login.microsoftonline.com/common/oauth2/nativeclient';

export interface OAuthConfig {
  clientId: string;
  redirectUri: string;
  scope: string;
  authorizeUrl: string;
  tokenUrl: string;
  /** Azure-Apps brauchen beim Xbox-Login das Präfix "d=" vor dem Token */
  rpsPrefix: string;
}

export function getOAuthConfig(): OAuthConfig {
  if (AZURE_CLIENT_ID.trim()) {
    return {
      clientId: AZURE_CLIENT_ID.trim(),
      redirectUri: AZURE_REDIRECT_URI,
      scope: 'XboxLive.signin offline_access',
      authorizeUrl: 'https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize',
      tokenUrl: 'https://login.microsoftonline.com/consumers/oauth2/v2.0/token',
      rpsPrefix: 'd=',
    };
  }
  // Öffentlicher Live-Client (ohne eigene Azure-Registrierung nutzbar)
  return {
    clientId: '00000000402b5328',
    redirectUri: 'https://login.live.com/oauth20_desktop.srf',
    scope: 'service::user.auth.xboxlive.com::MBI_SSL',
    authorizeUrl: 'https://login.live.com/oauth20_authorize.srf',
    tokenUrl: 'https://login.live.com/oauth20_token.srf',
    rpsPrefix: '',
  };
}
