/**
 * Icons fuer die Karten im Mod-Menue aus img/*.svg: weiss, auf die sichtbare Flaeche zugeschnitten, 256 px.
 * Aufruf: node tools/generate-mod-icons.cjs
 */
const fs = require('node:fs');
const path = require('node:path');
const { Resvg } = require('@resvg/resvg-js');
const { PNG } = require('pngjs');

const img = path.resolve(__dirname, '..', 'img');
const out = path.resolve(__dirname, '..', 'mod', 'mc26', 'src', 'main', 'resources', 'assets', 'deucore', 'textures', 'gui', 'icons_hd');
const NAMES = ['armor', 'call', 'cape', 'color-palette', 'compass', 'crosshair', 'eye', 'fps', 'hangup', 'headphones', 'headphones-off', 'info', 'keyboard', 'mic', 'mic-off', 'volume', 'location', 'ping', 'speed', 'tag', 'time', 'wings', 'zoom'];

/** img/wing.svg zeigt nur einen Fluegel: das Icon "wings" setzt ihn und sein Spiegelbild nebeneinander */
function source(name) {
  if (name !== 'wings') return fs.readFileSync(path.join(img, name + '.svg'), 'utf8');
  const wing = fs.readFileSync(path.join(img, 'wing.svg'), 'utf8');
  const d = /<path[^>]*\sd="([^"]+)"/.exec(wing)[1];
  return `<svg xmlns="http://www.w3.org/2000/svg" width="66" height="32" viewBox="0 0 66 32"><path d="${d}"/><path d="${d}" transform="translate(66 0) scale(-1 1)"/></svg>`;
}

for (const name of NAMES) {
  const raw = source(name);
  const vb = /viewBox="([^"]+)"/.exec(raw)[1].trim().split(/[\s,]+/).map(Number);
  // Groesse und Stil des Wurzelelements ersetzen, damit der Ausschnitt frei gewaehlt werden kann
  const root = (box) =>
    raw.replace(/<svg\b[^>]*>/, (tag) =>
      tag
        .replace(/\s(width|height|viewBox|style)="[^"]*"/g, '')
        .replace(/<svg/, `<svg width="1024" height="1024" viewBox="${box.join(' ')}"`));
  const size = Math.max(vb[2], vb[3]);
  const square = [vb[0] + (vb[2] - size) / 2, vb[1] + (vb[3] - size) / 2, size, size];
  const probe = PNG.sync.read(new Resvg(root(square), { fitTo: { mode: 'width', value: 1024 } }).render().asPng());
  let x0 = probe.width, y0 = probe.height, x1 = -1, y1 = -1;
  for (let y = 0; y < probe.height; y++) {
    for (let x = 0; x < probe.width; x++) {
      if (probe.data[(y * probe.width + x) * 4 + 3] > 6) {
        x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y);
      }
    }
  }
  const k = size / probe.width;
  const side = Math.max(x1 - x0 + 1, y1 - y0 + 1) * k * 1.06;
  const box = [square[0] + ((x0 + x1 + 1) / 2) * k - side / 2, square[1] + ((y0 + y1 + 1) / 2) * k - side / 2, side, side];
  const png = PNG.sync.read(new Resvg(root(box), { fitTo: { mode: 'width', value: 256 } }).render().asPng());
  for (let i = 0; i < png.data.length; i += 4) png.data[i] = png.data[i + 1] = png.data[i + 2] = 255;
  fs.writeFileSync(path.join(out, 'mod_' + name + '.png'), PNG.sync.write(png));
  console.log('mod_' + name, x1 >= 0 ? 'ok' : 'LEER');
}
