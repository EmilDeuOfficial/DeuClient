/**
 * Erzeugt die Deu-Cosmetics als PNG für die DeuCore-Mod (26.3):
 *   - Capes (Zeichnungen in tools/capes.cjs)
 *   - (Flügel: siehe generate-ingame-ui.cjs)
 *   - Logo-Glyph (16×16) für das Deu-Logo neben Spielernamen (eigene Schriftart)
 *
 * Alles wird per Code als SVG gebaut und mit resvg gerendert - keine fremden Grafiken.
 */
const fs = require('node:fs');
const path = require('node:path');
const { Resvg } = require('@resvg/resvg-js');

const assets = path.resolve(__dirname, '..', 'mod', 'mc26.3', 'src', 'main', 'resources', 'assets', 'deucore');

function pngSmooth(svg, width) {
  return new Resvg(svg, { fitTo: { mode: 'width', value: width }, shapeRendering: 2 }).render().asPng();
}
/** Schneidet ein PNG auf den sichtbaren Bereich zu (kein Leerraum, damit Icon und Text sauber sitzen) */
function cropToAlpha(buf, padTop = 0, padBottom = 0) {
  const { PNG } = require('pngjs');
  const src = PNG.sync.read(buf);
  let x0 = src.width, x1 = -1, y0 = src.height, y1 = -1;
  for (let y = 0; y < src.height; y++) {
    for (let x = 0; x < src.width; x++) {
      if (src.data[(y * src.width + x) * 4 + 3] > 0) {
        x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y);
      }
    }
  }
  const out = new PNG({ width: x1 - x0 + 1, height: y1 - y0 + 1 + padTop + padBottom });
  PNG.bitblt(src, out, x0, y0, x1 - x0 + 1, y1 - y0 + 1, 0, padTop);
  return PNG.sync.write(out);
}
function png(svg, width) {
  return new Resvg(svg, { fitTo: { mode: 'width', value: width }, shapeRendering: 0 }).render().asPng();
}
function write(rel, buf) {
  const file = path.join(assets, rel);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, buf);
  console.log('  ✓', rel);
}

/* ---------------- Capes ---------------- */

const lerp = (a, b, t) => Math.round(a + (b - a) * t);
const hex = (c) => '#' + c.map((v) => Math.max(0, Math.min(255, v)).toString(16).padStart(2, '0')).join('');
const rgb = (h) => [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16));
const mix = (a, b, t) => hex(rgb(a).map((v, i) => lerp(v, rgb(b)[i], t)));
// deterministisches "Rauschen" für Sterne/Funken
const noise = (x, y, s = 1) => {
  const n = Math.sin(x * 12.9898 + y * 78.233 + s * 37.719) * 43758.5453;
  return n - Math.floor(n);
};

// Capes zeichnet tools/capes.cjs (Mythos-Kollektion)
const capes = require('./capes.cjs');

/* ---------------- Flügel ---------------- */

const WINGS = {
  engel: { name: 'Engel', a: '#ffffff', b: '#cfd6ea', edge: '#ffe08a' },
  daemon: { name: 'Dämon', a: '#8a1010', b: '#1a0505', edge: '#ff5a1f' },
  eis: { name: 'Eis', a: '#bff6ff', b: '#1a5b8a', edge: '#ffffff' },
  phoenix: { name: 'Phönix', a: '#ffb020', b: '#b8200a', edge: '#fff1a8' },
  schatten: { name: 'Schatten', a: '#3a2a50', b: '#07030f', edge: '#9461f0' },
  smaragd: { name: 'Smaragd', a: '#2be07a', b: '#0b3d22', edge: '#c8ffd8' },
};

/** Ein Flügel: Wurzel links (x=0, y≈18), Spitze rechts oben, Federn hängen nach unten */
function wingSvg(w) {
  let feathers = '';
  // Schwungfedern (lang, unten) - von außen nach innen
  for (let i = 0; i < 7; i++) {
    const x = 58 - i * 8;
    const len = 46 - i * 4;
    feathers += `<path d="M${x} ${10 + i} Q${x + 5} ${10 + len * 0.6} ${x - 2} ${10 + len} Q${x - 7} ${10 + len * 0.55} ${x - 6} ${12 + i} Z" fill="url(#f)" stroke="${w.edge}" stroke-width="1" stroke-opacity="0.7"/>`;
  }
  // Deckfedern (kurz, oben)
  for (let i = 0; i < 6; i++) {
    const x = 50 - i * 8;
    feathers += `<ellipse cx="${x}" cy="${16 + i * 0.5}" rx="6" ry="9" fill="url(#c)" stroke="${w.edge}" stroke-width="0.8" stroke-opacity="0.6"/>`;
  }
  return `<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 64 64">
  <defs>
    <linearGradient id="f" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${w.a}"/><stop offset="1" stop-color="${w.b}"/></linearGradient>
    <linearGradient id="c" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="${mix(w.a, '#ffffff', 0.25)}"/><stop offset="1" stop-color="${w.a}"/></linearGradient>
  </defs>
  <path d="M0 14 Q20 2 63 3 Q60 12 50 18 L0 24 Z" fill="${w.b}"/>
  ${feathers}
  <path d="M0 14 Q20 2 63 3" fill="none" stroke="${w.edge}" stroke-width="2"/>
</svg>`;
}

/* ---------------- Logo-Glyph ---------------- */

const LOGO_GLYPH = `<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 16 16">
  <defs><linearGradient id="g" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#fff1b8"/><stop offset="1" stop-color="#d49a10"/></linearGradient></defs>
  <polygon points="8,0.5 15.5,8 8,15.5 0.5,8" fill="url(#g)"/>
  <polygon points="8,3.5 12.5,8 8,12.5 3.5,8" fill="#7c3aed"/>
  <polygon points="8,5.8 10.2,8 8,10.2 5.8,8" fill="#fff6d6"/>
</svg>`;

console.log('Erzeuge Deu-Cosmetics ...');
const index = { capes: [], wings: [] };
capes.build();
for (const [id, design] of Object.entries(capes.CAPES)) index.capes.push({ id, name: design.name });
// Flügel-Texturen erzeugt generate-ingame-ui.cjs (zweistufige Galaxie-Flügel)
for (const [id, w] of Object.entries(WINGS)) index.wings.push({ id, name: w.name });
// Namensicon = das neue DeuClient-Emblem (hochaufloesend, wird im Spiel auf Textgroesse skaliert)
// nur der Smaragd: Kachel-Hintergrund, Rahmen und Schein werden entfernt, Ausschnitt auf den Stein
const EMBLEM_SVG = fs
  .readFileSync(path.resolve(__dirname, '..', 'img', 'emblem.svg'), 'utf8')
  .replace(/<rect[^>]*rx="112"[^>]*\/>\s*/g, '')
  .replace(/<circle cx="256" cy="240" r="230"[^>]*\/>\s*/, '')
  .replace('viewBox="0 0 512 512"', 'viewBox="46 44 420 420"');
write('textures/font/logo.png', cropToAlpha(pngSmooth(EMBLEM_SVG, 128), 7, 6));
write(
  'font/logo.json',
  Buffer.from(JSON.stringify({ providers: [{ type: 'bitmap', file: 'deucore:font/logo.png', ascent: 8, height: 10, chars: [''] }, { type: 'space', advances: { ' ': 1 } }] }, null, 2)),
);
write('cosmetics.json', Buffer.from(JSON.stringify(index, null, 2)));
console.log('Fertig.');
