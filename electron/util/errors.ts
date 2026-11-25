/**
 * Verstaendliche Fehlermeldungen: uebersetzt technische Fehler (Node, Electron, HTTP) in einen
 * Satz, den man ohne Vorwissen versteht und der sagt, was zu tun ist.
 * Die technische Originalmeldung bleibt im Log (Konsole) erhalten.
 */

export const MSG_ACCOUNT_UNREADABLE =
  'Deine gespeicherte Anmeldung kann auf diesem PC nicht gelesen werden (der Launcher-Ordner stammt vermutlich von einem anderen Windows-Benutzer oder PC). Der Account wurde entfernt - bitte melde dich neu mit Microsoft an.';
const MSG_OFFLINE = 'Keine Verbindung zum Internet oder zum Server. Prüfe deine Internetverbindung (und ob eine Firewall den DeuClient blockiert) und versuche es erneut.';
const MSG_DISK_FULL = 'Der Speicherplatz ist voll. Mach auf dem Laufwerk Platz frei und versuche es erneut.';
const MSG_FILE_LOCKED = 'Auf eine Datei konnte nicht zugegriffen werden (gesperrt oder keine Berechtigung). Schließe Minecraft und andere Programme, prüfe deinen Virenscanner und versuche es erneut.';
const MSG_RATE_LIMIT = 'Zu viele Anfragen in kurzer Zeit. Warte ein paar Minuten und versuche es dann erneut.';
const MSG_AUTH = 'Die Anmeldung wurde abgelehnt. Melde dich neu mit Microsoft an.';
const MSG_SERVER_DOWN = 'Der Server hat gerade ein Problem. Versuche es in ein paar Minuten erneut.';
const MSG_CORRUPT = 'Eine heruntergeladene Datei ist beschädigt. Versuche es erneut - der Launcher lädt sie dann neu.';
const MSG_NO_JAVA = 'Java wurde nicht gefunden oder konnte nicht gestartet werden. Der Launcher lädt Java normalerweise selbst herunter; prüfe sonst in den Einstellungen den Java-Pfad.';
const MSG_NO_MEMORY = 'Minecraft bekommt nicht genug Arbeitsspeicher. Verringere den Arbeitsspeicher in den Einstellungen oder schließe andere Programme.';

interface Rule {
  test: (raw: string, code: string) => boolean;
  message: string;
}

const RULES: Rule[] = [
  { test: (r) => /decrypting the ciphertext|safeStorage\.decryptString/i.test(r), message: MSG_ACCOUNT_UNREADABLE },
  { test: (_r, c) => ['ENOSPC'].includes(c), message: MSG_DISK_FULL },
  { test: (r, c) => ['EACCES', 'EPERM', 'EBUSY', 'EMFILE', 'ENFILE'].includes(c) || /EACCES|EPERM|EBUSY/.test(r), message: MSG_FILE_LOCKED },
  { test: (r) => /HTTP 429|status.?429|too many requests/i.test(r), message: MSG_RATE_LIMIT },
  { test: (r) => /HTTP (401|403)\b|unauthorized|forbidden/i.test(r), message: MSG_AUTH },
  { test: (r) => /HTTP 5\d\d|status.?5\d\d|bad gateway|service unavailable/i.test(r), message: MSG_SERVER_DOWN },
  {
    test: (r, c) =>
      ['ENOTFOUND', 'EAI_AGAIN', 'ECONNREFUSED', 'ECONNRESET', 'ETIMEDOUT', 'ENETUNREACH', 'EHOSTUNREACH', 'UND_ERR_CONNECT_TIMEOUT'].includes(c) ||
      /fetch failed|getaddrinfo|net::ERR_(INTERNET_DISCONNECTED|NAME_NOT_RESOLVED|CONNECTION|TIMED_OUT|NETWORK)|network error|socket hang up|timed? ?out/i.test(r),
    message: MSG_OFFLINE,
  },
  { test: (r) => /sha1|checksum|hash mismatch|integrity|corrupt/i.test(r), message: MSG_CORRUPT },
  { test: (r, c) => (c === 'ENOENT' && /spawn/i.test(r)) || /spawn .*(java|javaw)/i.test(r), message: MSG_NO_JAVA },
  { test: (r) => /OutOfMemory|Could not reserve enough space|Invalid maximum heap size/i.test(r), message: MSG_NO_MEMORY },
];

/** Liefert eine verstaendliche Meldung; unbekannte Fehler behalten ihren Originaltext */
export function friendlyMessage(err: unknown): string {
  const raw = err instanceof Error ? err.message : String(err);
  const code = String((err as NodeJS.ErrnoException | undefined)?.code ?? '');

  // Download-Fehler: Dateiname behalten, Ursache erklaeren
  const dl = /^Download fehlgeschlagen: (.+?) \(.*?\) - (.*)$/s.exec(raw);
  if (dl) return `Download fehlgeschlagen: ${dl[1]}. ${friendlyMessage(new Error(dl[2]))}`;

  for (const rule of RULES) {
    if (rule.test(raw, code)) return rule.message;
  }
  return raw;
}

/** Fehler, nach dem sich die Account-Liste geaendert hat (Oberflaeche soll neu laden) */
export class AccountUnreadableError extends Error {
  readonly accountsChanged = true;
  constructor() {
    super(MSG_ACCOUNT_UNREADABLE);
  }
}
