/**
 * HTTP- und Download-Helfer:
 *  - fetchJson mit User-Agent "DeuClient/1.0"
 *  - downloadFile: Streaming-Download mit Hash-Prüfung (SHA1 oder SHA256) und Wiederholungen
 *  - downloadAll: parallele Download-Warteschlange mit Fortschritt
 */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { Readable } from 'node:stream';
import { pipeline } from 'node:stream/promises';

export const USER_AGENT = 'DeuClient/1.0';

export async function fetchJson<T>(url: string, init: RequestInit = {}): Promise<T> {
  const res = await fetch(url, {
    ...init,
    headers: { 'User-Agent': USER_AGENT, Accept: 'application/json', ...(init.headers as Record<string, string> | undefined) },
  });
  if (!res.ok) {
    const body = await res.text().catch(() => '');
    throw new HttpError(res.status, `HTTP ${res.status} bei ${url}${body ? `: ${body.slice(0, 300)}` : ''}`, body);
  }
  return (await res.json()) as T;
}

export class HttpError extends Error {
  constructor(public status: number, message: string, public body: string) {
    super(message);
  }
}

/** Berechnet einen Datei-Hash per Stream (speicherschonend auch für große Dateien). */
export function hashFile(file: string, algorithm: 'sha1' | 'sha256' | 'sha512' = 'sha1'): Promise<string> {
  return new Promise((resolve, reject) => {
    const hash = crypto.createHash(algorithm);
    fs.createReadStream(file)
      .on('data', (c) => hash.update(c))
      .on('error', reject)
      .on('end', () => resolve(hash.digest('hex')));
  });
}

export interface DownloadTask {
  url: string;
  dest: string;
  sha1?: string;
  sha256?: string;
  size?: number;
}

/** Prüft, ob eine vorhandene Datei bereits passt (Größe als Schnellprüfung, sonst Hash). */
async function isValid(task: DownloadTask): Promise<boolean> {
  let stat: fs.Stats;
  try {
    stat = fs.statSync(task.dest);
  } catch {
    return false;
  }
  if (!stat.isFile() || stat.size === 0) return false;
  if (task.size !== undefined) return stat.size === task.size;
  if (task.sha1) return (await hashFile(task.dest, 'sha1')) === task.sha1.toLowerCase();
  if (task.sha256) return (await hashFile(task.dest, 'sha256')) === task.sha256.toLowerCase();
  return true;
}

/** Lädt eine Datei herunter, prüft den Hash und versucht es bei Fehlern bis zu 3-mal erneut. */
export async function downloadFile(task: DownloadTask, retries = 3): Promise<boolean> {
  if (await isValid(task)) return false;
  fs.mkdirSync(path.dirname(task.dest), { recursive: true });
  const tmp = `${task.dest}.part`;
  let lastError: unknown;
  for (let attempt = 1; attempt <= retries; attempt++) {
    try {
      const res = await fetch(task.url, { headers: { 'User-Agent': USER_AGENT } });
      if (!res.ok || !res.body) throw new Error(`HTTP ${res.status}`);
      await pipeline(Readable.fromWeb(res.body as any), fs.createWriteStream(tmp));
      if (task.sha1) {
        const got = await hashFile(tmp, 'sha1');
        if (got !== task.sha1.toLowerCase()) throw new Error(`SHA1 stimmt nicht (${got} ≠ ${task.sha1})`);
      } else if (task.sha256) {
        const got = await hashFile(tmp, 'sha256');
        if (got !== task.sha256.toLowerCase()) throw new Error(`SHA256 stimmt nicht`);
      }
      fs.renameSync(tmp, task.dest);
      return true;
    } catch (e) {
      lastError = e;
      try {
        fs.rmSync(tmp, { force: true });
      } catch {
        /* ignorieren */
      }
      await new Promise((r) => setTimeout(r, 400 * attempt));
    }
  }
  throw new Error(`Download fehlgeschlagen: ${path.basename(task.dest)} (${task.url}) - ${(lastError as Error)?.message}`);
}

/**
 * Lädt viele Dateien parallel. onProgress wird nach jeder fertigen Datei aufgerufen.
 * Doppelte Ziele werden vorher entfernt.
 */
export async function downloadAll(
  tasks: DownloadTask[],
  concurrency: number,
  onProgress?: (done: number, total: number, file: string) => void,
): Promise<void> {
  const unique = [...new Map(tasks.map((t) => [path.resolve(t.dest).toLowerCase(), t])).values()];
  let index = 0;
  let done = 0;
  const errors: Error[] = [];
  const worker = async () => {
    while (index < unique.length && errors.length === 0) {
      const task = unique[index++];
      try {
        await downloadFile(task);
      } catch (e) {
        errors.push(e as Error);
      }
      done++;
      onProgress?.(done, unique.length, path.basename(task.dest));
    }
  };
  await Promise.all(Array.from({ length: Math.max(1, Math.min(concurrency, unique.length)) }, worker));
  if (errors.length) throw errors[0];
}
