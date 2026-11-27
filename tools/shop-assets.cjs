/**
 * Kopiert die Cosmetics-Assets des Mods fuer den Shop in den Launcher (public/cosmetics -> wird mit der Oberflaeche
 * ausgeliefert). Wird von tools/generate-ingame-ui.cjs aufgerufen, laeuft aber auch allein: `node tools/shop-assets.cjs`.
 *
 *   cape/<id>.png          Cape-Textur (unveraendert)
 *   wings/<id>.bbmodel     3D-Fluegelmodell (Blockbench, ohne eingebettete Textur - der Atlas liegt als PNG daneben)
 *   wings/<id>.png         Textur-Atlas des Fluegels (256x256)
 *   wings/<id>_ui.png      Menue-Vorschau eines Fluegels (256x256, Schulter bei ca. 12 % / 55 %), der Shop spiegelt sie
 *
 * Alles andere in public/cosmetics/wings (z. B. die alten <id>_lower.png der Flachfluegel) wird entfernt.
 */
const fs = require('node:fs');
const path = require('node:path');

const defaultAssets = path.resolve(__dirname, '..', 'mod', 'mc26', 'src', 'main', 'resources', 'assets', 'deucore');
const defaultShopDir = path.resolve(__dirname, '..', 'public', 'cosmetics');

function copyFolder(src, dst) {
  fs.mkdirSync(dst, { recursive: true });
  for (const f of fs.readdirSync(src)) fs.copyFileSync(path.join(src, f), path.join(dst, f));
}

/** Modell ohne die eingebettete Base64-Textur (der Launcher laedt den Atlas als eigene PNG) */
function stripTextures(raw) {
  const json = JSON.parse(raw);
  if (Array.isArray(json.textures)) for (const t of json.textures) delete t.source;
  return JSON.stringify(json);
}

function syncShopAssets(assets = defaultAssets, shopDir = defaultShopDir) {
  copyFolder(path.join(assets, 'textures', 'cosmetics', 'cape'), path.join(shopDir, 'cape'));

  const modelDir = path.join(assets, 'models', 'wings');
  const atlasDir = path.join(assets, 'textures', 'cosmetics', 'wings');
  const uiDir = path.join(assets, 'textures', 'cosmetics', 'ui');
  const out = path.join(shopDir, 'wings');
  fs.mkdirSync(out, { recursive: true });

  const ids = fs.readdirSync(modelDir).filter((f) => f.endsWith('.bbmodel')).map((f) => f.slice(0, -'.bbmodel'.length)).sort();
  const keep = new Set();
  for (const id of ids) {
    const files = {
      [`${id}.bbmodel`]: () => fs.writeFileSync(path.join(out, `${id}.bbmodel`), stripTextures(fs.readFileSync(path.join(modelDir, `${id}.bbmodel`), 'utf8'))),
      [`${id}.png`]: () => fs.copyFileSync(path.join(atlasDir, `${id}.png`), path.join(out, `${id}.png`)),
      [`${id}_ui.png`]: () => fs.copyFileSync(path.join(uiDir, `wings_${id}.png`), path.join(out, `${id}_ui.png`)),
    };
    for (const [name, write] of Object.entries(files)) {
      write();
      keep.add(name);
    }
  }
  for (const f of fs.readdirSync(out)) if (!keep.has(f)) fs.rmSync(path.join(out, f), { recursive: true, force: true });
  return ids;
}

module.exports = { syncShopAssets };

if (require.main === module) {
  const ids = syncShopAssets();
  console.log(`Shop-Assets (public/cosmetics): Capes, Fluegel ${ids.join(', ')}`);
}
