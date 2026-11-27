/**
 * Bettet alle vom Launcher verwalteten Mods ein:
 *   - DeuCore für jede Minecraft-Version: Projekte mit targets/<ziel>.properties liefern je Ziel ein JAR
 *     (mod/mc26/build/<ziel>/libs), die anderen eines (mod/mc<version>/build/libs)
 *   - mitgelieferte Mods (resources/bundled-mods/<version>/*.jar, z. B. die Glass-Mods für 26.3)
 *
 * Ergebnis: resources/mods/<ordner>/*.jar + resources/mods/manifest.json
 * Vorher die Mods bauen:  tools/build.ps1  (oder je Ziel: gradlew build -Ptarget=26.2)
 */
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const outDir = path.resolve(__dirname, '..', 'resources', 'mods');

function readProps(file) {
  return Object.fromEntries(
    fs
      .readFileSync(file, 'utf8')
      .split(/\r?\n/)
      .filter((l) => l.includes('=') && !l.trim().startsWith('#'))
      .map((l) => [l.slice(0, l.indexOf('=')).trim(), l.slice(l.indexOf('=') + 1).trim()]),
  );
}

/** Liest id/name/description aus der fabric.mod.json eines JARs (ohne Zusatzpaket: einfache ZIP-Suche) */
function modMeta(jarFile) {
  const AdmZip = require('adm-zip');
  const entry = new AdmZip(jarFile).getEntry('fabric.mod.json');
  const json = JSON.parse(entry.getData().toString('utf8'));
  return { id: json.id, name: json.name, version: json.version, description: json.description };
}

fs.rmSync(outDir, { recursive: true, force: true });
fs.mkdirSync(outDir, { recursive: true });
const manifest = { deucore: {}, versions: {} };

// 1. DeuCore pro Minecraft-Version
/** Ein JAR kann mehrere Unterversionen abdecken (minecraft_versions): es wird nur einmal abgelegt */
function embedCore(jar, folder, versions, modVersion, hint) {
  if (!fs.existsSync(jar)) {
    console.error(`✗ ${jar} fehlt - bitte zuerst bauen:  ${hint}`);
    process.exit(1);
  }
  fs.mkdirSync(path.join(outDir, folder), { recursive: true });
  fs.copyFileSync(jar, path.join(outDir, folder, 'DeuCore.jar'));
  for (const mc of versions) {
    manifest.deucore[mc] = modVersion;
    manifest.versions[mc] = [{ file: 'DeuCore.jar', dir: folder, id: 'deucore', name: 'DeuCore', version: modVersion, core: true }];
  }
  console.log(`✓ DeuCore ${modVersion} für Minecraft ${versions.join(', ')}`);
}

for (const dir of fs.readdirSync(path.join(root, 'mod')).filter((d) => d.startsWith('mc'))) {
  const project = path.join(root, 'mod', dir);
  const props = readProps(path.join(project, 'gradle.properties'));
  const jarName = `${props.archives_base_name}-${props.mod_version}.jar`;
  const targets = path.join(project, 'targets');
  if (!fs.existsSync(targets)) {
    const mc = props.minecraft_version;
    embedCore(path.join(project, 'build', 'libs', jarName), mc, [mc], props.mod_version, `cd mod/${dir} && gradlew build`);
    continue;
  }
  for (const file of fs.readdirSync(targets).filter((f) => f.endsWith('.properties'))) {
    const target = file.replace(/\.properties$/, '');
    const conf = readProps(path.join(targets, file));
    const versions = conf.minecraft_versions.split(',').map((v) => v.trim());
    embedCore(path.join(project, 'build', target, 'libs', jarName), target, versions, props.mod_version, `cd mod/${dir} && gradlew build -Ptarget=${target}`);
  }
}

// 2. Mitgelieferte Mods (resources/bundled-mods/<version>/*.jar)
const bundled = path.join(root, 'resources', 'bundled-mods');
if (fs.existsSync(bundled)) {
  for (const mc of fs.readdirSync(bundled)) {
    for (const file of fs.readdirSync(path.join(bundled, mc)).filter((f) => f.endsWith('.jar'))) {
      const src = path.join(bundled, mc, file);
      const meta = modMeta(src);
      fs.mkdirSync(path.join(outDir, mc), { recursive: true });
      fs.copyFileSync(src, path.join(outDir, mc, file));
      (manifest.versions[mc] ??= []).push({ file, ...meta, core: false });
      console.log(`✓ ${meta.name} ${meta.version} für Minecraft ${mc}`);
    }
  }
}

fs.writeFileSync(path.join(outDir, 'manifest.json'), JSON.stringify(manifest, null, 2));

console.log('✓ manifest.json geschrieben');
