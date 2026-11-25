/** Kurze Anzeige der Version: 1.0.0 wird als 1.0 angezeigt, 1.4.2 bleibt 1.4.2 */
export function shortVersion(v: string): string {
  return v.replace(/^(\d+\.\d+)\.0$/, '$1');
}

export const APP_VERSION = shortVersion(__APP_VERSION__);
