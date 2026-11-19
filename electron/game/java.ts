/**
 * Java-Verwaltung:
 *  - Sucht installierte Java-Versionen (JAVA_HOME, PATH, typische Installationsordner, eigene Runtimes)
 *  - Lädt bei Bedarf die passende Eclipse-Temurin-JRE über die Adoptium-API herunter
 */
import fs from 'node:fs';
import path from 'node:path';
import { execFile } from 'node:child_process';
import AdmZip from 'adm-zip';
import type { JavaInstall } from '../../shared/types';
import { downloadFile, fetchJson } from '../util/download';
import { paths } from '../util/paths';

const exe = process.platform === 'win32' ? 'javaw.exe' : 'java';

/** Ermittelt die Java-Version anhand der "release"-Datei im Java-Ordner (schnell, ohne Prozessstart). */
function readRelease(javaHome: string): { version: string; major: number } | null {
  try {
    const txt = fs.readFileSync(path.join(javaHome, 'release'), 'utf8');
    const v = /JAVA_VERSION="([^"]+)"/.exec(txt)?.[1];
    if (!v) return null;
    return { version: v, major: parseMajor(v) };
  } catch {
    return null;
  }
}

function parseMajor(v: string): number {
  const parts = v.split(/[._+-]/).map((x) => parseInt(x, 10));
  return parts[0] === 1 ? parts[1] : parts[0];
}

/** Fallback: "java -version" ausführen */
function probeBinary(bin: string): Promise<{ version: string; major: number } | null> {
  const javaExe = bin.replace(/javaw\.exe$/i, 'java.exe');
  return new Promise((resolve) => {
    execFile(javaExe, ['-version'], { timeout: 8000, windowsHide: true }, (_err, _out, stderr) => {
      const v = /version "([^"]+)"/.exec(stderr ?? '')?.[1];
      resolve(v ? { version: v, major: parseMajor(v) } : null);
    });
  });
}

async function inspect(binPath: string, source: string): Promise<JavaInstall | null> {
  if (!fs.existsSync(binPath)) return null;
  const home = path.dirname(path.dirname(binPath));
  const info = readRelease(home) ?? (await probeBinary(binPath));
  return info ? { path: binPath, major: info.major, version: info.version, source } : null;
}

/** Alle gefundenen Java-Installationen */
export async function findJavaInstalls(): Promise<JavaInstall[]> {
  // Schlüssel in Kleinbuchstaben (Windows-Pfade), Wert behält die Original-Schreibweise
  const candidates = new Map<string, { bin: string; source: string }>();
  const add = (bin: string, source: string) => {
    const key = path.resolve(bin).toLowerCase();
    if (!candidates.has(key)) candidates.set(key, { bin: path.resolve(bin), source });
  };

  // 1. Vom Launcher heruntergeladene Runtimes
  if (fs.existsSync(paths.runtime)) {
    for (const d of fs.readdirSync(paths.runtime)) add(path.join(paths.runtime, d, 'bin', exe), 'DeuClient');
  }
  // 2. JAVA_HOME
  if (process.env.JAVA_HOME) add(path.join(process.env.JAVA_HOME, 'bin', exe), 'JAVA_HOME');
  // 3. Typische Installationsordner
  const roots = [process.env.ProgramFiles, process.env['ProgramFiles(x86)'], process.env.LOCALAPPDATA && path.join(process.env.LOCALAPPDATA, 'Programs')]
    .filter(Boolean) as string[];
  const vendors = ['Java', 'Eclipse Adoptium', 'Eclipse Foundation', 'AdoptOpenJDK', 'Microsoft', 'Zulu', 'BellSoft', 'Amazon Corretto', 'Semeru', 'GraalVM'];
  for (const root of roots) {
    for (const vendor of vendors) {
      const dir = path.join(root, vendor);
      if (!fs.existsSync(dir)) continue;
      for (const d of fs.readdirSync(dir)) add(path.join(dir, d, 'bin', exe), vendor);
    }
  }
  // 4. Offizieller Minecraft-Launcher bringt eigene Runtimes mit
  const mcRuntime = process.env.LOCALAPPDATA && path.join(process.env.LOCALAPPDATA, 'Packages', 'Microsoft.4297127D64EC6_8wekyb3d8bbwe', 'LocalCache', 'Local', 'runtime');
  if (mcRuntime && fs.existsSync(mcRuntime)) {
    for (const comp of fs.readdirSync(mcRuntime)) {
      add(path.join(mcRuntime, comp, 'windows-x64', comp, 'bin', exe), 'Minecraft Launcher');
    }
  }
  // 5. PATH
  for (const dir of (process.env.PATH ?? '').split(path.delimiter)) {
    if (dir) add(path.join(dir, exe), 'PATH');
  }

  const results = await Promise.all([...candidates.values()].map(({ bin, source }) => inspect(bin, source)));
  // gleiche Installationen (z. B. über javapath-Links) nur einmal anzeigen
  const seen = new Set<string>();
  return results.filter((r): r is JavaInstall => {
    if (!r) return false;
    const key = `${r.version}|${r.source}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

/**
 * Wählt eine passende Java-Version: bevorzugt exakt die verlangte Hauptversion,
 * sonst die kleinste neuere. Gibt null zurück, wenn nichts passt.
 */
export async function findJavaFor(major: number): Promise<JavaInstall | null> {
  const installs = await findJavaInstalls();
  const exact = installs.find((j) => j.major === major);
  if (exact) return exact;
  // Alte Versionen (Java 8) laufen nicht zuverlässig mit neuerem Java → nur exakt
  if (major <= 8) return null;
  return installs.filter((j) => j.major > major).sort((a, b) => a.major - b.major)[0] ?? null;
}

/** Lädt Eclipse Temurin (JRE) für die gewünschte Hauptversion herunter und entpackt sie. */
export async function downloadTemurin(major: number, onProgress?: (text: string, percent: number) => void): Promise<JavaInstall> {
  onProgress?.(`Suche Temurin ${major} ...`, -1);
  const api = `https://api.adoptium.net/v3/assets/latest/${major}/hotspot?architecture=x64&image_type=jre&os=windows&vendor=eclipse`;
  let assets = await fetchJson<{ binary: { package: { link: string; checksum: string; name: string; size: number } } }[]>(api);
  if (!assets.length) {
    // manche Versionen gibt es nur als JDK
    assets = await fetchJson(api.replace('image_type=jre', 'image_type=jdk'));
  }
  if (!assets.length) throw new Error(`Keine Temurin-Version ${major} für Windows gefunden`);
  const pkg = assets[0].binary.package;

  const zipFile = path.join(paths.cache, pkg.name);
  onProgress?.(`Lade Java ${major} herunter (${Math.round(pkg.size / 1024 / 1024)} MB) ...`, 0.3);
  await downloadFile({ url: pkg.link, dest: zipFile, sha256: pkg.checksum });

  onProgress?.(`Entpacke Java ${major} ...`, 0.8);
  const target = path.join(paths.runtime, `java-${major}`);
  fs.rmSync(target, { recursive: true, force: true });
  const tmp = `${target}-tmp`;
  fs.rmSync(tmp, { recursive: true, force: true });
  new AdmZip(zipFile).extractAllTo(tmp, true);
  // Das Archiv enthält einen Unterordner (z. B. jdk-21.0.5+11-jre) → nach java-<major> verschieben
  const inner = fs.readdirSync(tmp).find((d) => fs.existsSync(path.join(tmp, d, 'bin')));
  fs.renameSync(inner ? path.join(tmp, inner) : tmp, target);
  fs.rmSync(tmp, { recursive: true, force: true });
  fs.rmSync(zipFile, { force: true });

  const install = await inspect(path.join(target, 'bin', exe), 'DeuClient');
  if (!install) throw new Error('Java wurde heruntergeladen, konnte aber nicht gestartet werden');
  onProgress?.(`Java ${major} bereit`, 1);
  return install;
}
