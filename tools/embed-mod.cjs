/**
 * Bettet alle vom Launcher verwalteten Mods ein:
 *   - DeuCore für jede Minecraft-Version (mod/mc<version>/build/libs/deucore-<v>.jar)
 *   - mitgelieferte Mods (resources/bundled-mods/<version>/*.jar, z. B. die Glass-Mods für 26.3)
 *
 * Ergebnis: resources/mods/<version>/*.jar + resources/mods/manifest.json
 * Vorher die Mods bauen:  cd mod/mc26.3 && gradlew build   (und mc1.21.4)
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
for (const dir of fs.readdirSync(path.join(root, 'mod')).filter((d) => d.startsWith('mc'))) {
  const props = readProps(path.join(root, 'mod', dir, 'gradle.properties'));
  const mc = props.minecraft_version;
  const jar = path.join(root, 'mod', dir, 'build', 'libs', `${props.archives_base_name}-${props.mod_version}.jar`);
  if (!fs.existsSync(jar)) {
    console.error(`✗ ${jar} fehlt - bitte zuerst bauen:  cd mod/${dir} && gradlew build`);
    process.exit(1);
  }
  fs.mkdirSync(path.join(outDir, mc), { recursive: true });
  fs.copyFileSync(jar, path.join(outDir, mc, 'DeuCore.jar'));
  manifest.deucore[mc] = props.mod_version;
  manifest.versions[mc] = [{ file: 'DeuCore.jar', id: 'deucore', name: 'DeuCore', version: props.mod_version, core: true }];
  console.log(`✓ DeuCore ${props.mod_version} für Minecraft ${mc}`);
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
