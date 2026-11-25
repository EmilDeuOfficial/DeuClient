/**
 * Erzeugt die Deu-Cosmetics als PNG für die DeuCore-Mod (26.3):
 *   - Capes (HD-Cape-Format 256×128, vierfache Aufloesung des Minecraft-Formats 64×32, wird auch fuer die Elytra genutzt)
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
function cropToAlpha(buf) {
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
  const out = new PNG({ width: x1 - x0 + 1, height: y1 - y0 + 1 });
  PNG.bitblt(src, out, x0, y0, out.width, out.height, 0, 0);
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

/*
 * Capes in HD (256x128 = 4x das Minecraft-Format): sauberer Vektor-Look mit Verlaeufen statt Pixelblöcken.
 * Jedes Design liefert die Vorderseite als SVG im 10x16-Koordinatensystem sowie zwei Randfarben.
 */
const star = (x, y, r, o = 1) => `<path d="M${x} ${y - r}L${x + r * 0.28} ${y - r * 0.28}L${x + r} ${y}L${x + r * 0.28} ${y + r * 0.28}L${x} ${y + r}L${x - r * 0.28} ${y + r * 0.28}L${x - r} ${y}L${x - r * 0.28} ${y - r * 0.28}Z" fill="#fff" opacity="${o}"/>`;
const frame = (c) => `<rect x="0.32" y="0.32" width="9.36" height="15.36" rx="0.5" fill="none" stroke="${c}" stroke-width="0.64"/>`;
const grad = (id, stops) => `<linearGradient id="${id}" x1="0" y1="0" x2="0" y2="1">${stops.map(([o, c]) => `<stop offset="${o}" stop-color="${c}"/>`).join('')}</linearGradient>`;

const CAPES = {
  gold: {
    name: 'Gold',
    top: '#ffe08a', bottom: '#8a5d00',
    art: () => `<defs>${grad('g', [[0, '#fff0b8'], [0.5, '#f5c542'], [1, '#a8780a']])}</defs>
      <rect width="10" height="16" fill="url(#g)"/>
      <polygon points="0,0 5,0 0,8" fill="#fff" opacity="0.16"/>
      ${frame('#8a5d00')}
      <path d="M4.1 3.2h1.8v3.1H9v1.8H5.9v4.7H4.1V8.1H1V6.3h3.1z" fill="#fff8e1"/>
      <path d="M4.1 3.2h1.8v3.1H9v1.8H5.9v4.7H4.1V8.1H1V6.3h3.1z" fill="none" stroke="#c98f12" stroke-width="0.25"/>`,
  },
  nacht: {
    name: 'Sternennacht',
    top: '#1b2a6b', bottom: '#05061a',
    art: () => {
      let stars = '';
      for (let i = 0; i < 26; i++) stars += `<circle cx="${(noise(i, 1, 2) * 8.6 + 0.7).toFixed(2)}" cy="${(noise(i, 2, 2) * 14.4 + 0.8).toFixed(2)}" r="${(0.07 + noise(i, 3, 2) * 0.13).toFixed(2)}" fill="#fff" opacity="${(0.5 + noise(i, 4, 2) * 0.5).toFixed(2)}"/>`;
      return `<defs>${grad('g', [[0, '#26397f'], [1, '#04051a']])}</defs>
      <rect width="10" height="16" fill="url(#g)"/>${stars}
      <circle cx="6.9" cy="3.5" r="1.55" fill="#fff3c4"/><circle cx="7.5" cy="3.1" r="1.4" fill="#1d2d70"/>
      ${star(2.4, 7.2, 0.7, 0.95)}${star(6.8, 10.6, 0.5, 0.9)}${star(3.6, 12.6, 0.38, 0.8)}
      ${frame('#8aa0ff')}`;
    },
  },
  flamme: {
    name: 'Flamme',
    top: '#ff5a1f', bottom: '#5a0a0a',
    art: () => `<defs>${grad('g', [[0, '#5e0808'], [0.45, '#c92a12'], [1, '#ff7a1a']])}</defs>
      <rect width="10" height="16" fill="url(#g)"/>
      <path d="M0 16V9.6Q1 7.4 1.9 9.2Q2.6 4.6 3.9 8.6Q4.7 3 5.9 8.2Q6.9 5.6 7.7 9Q8.9 7.2 10 9.8V16Z" fill="#ff8f1f"/>
      <path d="M0 16V12Q1.1 10 2.1 11.6Q3 8.6 4.1 11.4Q5 7.8 6 11.2Q7 9.4 7.9 11.8Q9 10.4 10 12.2V16Z" fill="#ffc43a"/>
      <path d="M0 16V14.2Q1.3 12.8 2.4 14Q3.4 12 4.6 13.9Q5.6 11.8 6.6 13.8Q7.8 12.6 8.8 14Q9.5 13.4 10 14.4V16Z" fill="#fff3a8"/>
      ${frame('#ff9a3a')}`,
  },
  eis: {
    name: 'Eiskristall',
    top: '#9fe9ff', bottom: '#1a5b8a',
    art: () => `<defs>${grad('g', [[0, '#b8f0ff'], [0.55, '#5fb8e6'], [1, '#1a5b8a']])}</defs>
      <rect width="10" height="16" fill="url(#g)"/>
      <polygon points="5,1.6 7.9,7.2 5,14.4 2.1,7.2" fill="#f4fdff" opacity="0.38"/>
      <polygon points="5,1.6 7.9,7.2 5,7.2" fill="#fff" opacity="0.4"/>
      <polygon points="0.4,10 2.6,8.4 3.4,12.6 1,14.6" fill="#e8fbff" opacity="0.28"/>
      <polygon points="9.6,5 7.4,3.6 6.8,7.6 9.2,8.8" fill="#e8fbff" opacity="0.28"/>
      <path d="M5 1.6L5 14.4M2.1 7.2L7.9 7.2" stroke="#fff" stroke-width="0.18" opacity="0.7"/>
      ${star(2.2, 3.4, 0.55, 0.95)}${star(8, 12.4, 0.5, 0.9)}${star(7.8, 2.6, 0.3, 0.8)}
      ${frame('#d8f6ff')}`,
  },
  smaragd: {
    name: 'Smaragd',
    top: '#2be07a', bottom: '#0b3d22',
    art: () => {
      let facets = '';
      for (let row = 0; row < 5; row++) for (let col = 0; col < 3; col++) {
        const cx = col * 3.4 + (row % 2) * 1.7 + 0.9, cy = row * 3.4 + 1.2;
        facets += `<polygon points="${cx},${cy - 1.7} ${cx + 1.7},${cy} ${cx},${cy + 1.7} ${cx - 1.7},${cy}" fill="#7dffb0" opacity="${(0.12 + noise(row, col, 7) * 0.22).toFixed(2)}" stroke="#0a6b3a" stroke-width="0.14"/>`;
      }
      return `<defs>${grad('g', [[0, '#3df08a'], [1, '#0a3a20']])}</defs>
      <rect width="10" height="16" fill="url(#g)"/>${facets}${star(7.6, 2.8, 0.5, 0.9)}${star(2.5, 12.8, 0.4, 0.85)}
      ${frame('#c8ffd8')}`;
    },
  },
};

/** 256x128: Aussen- und Innenseite (gespiegelt) in vierfacher Aufloesung, Rest = Rand-/Elytra-Verlauf */
function capeSvg(design) {
  const art = design.art();
  return `<svg xmlns="http://www.w3.org/2000/svg" width="256" height="128" viewBox="0 0 256 128">
  <defs>${grad('base', [[0, design.top], [1, design.bottom]])}</defs>
  <rect x="0" y="0" width="184" height="92" fill="url(#base)"/>
  <g transform="translate(4 4) scale(4)">${art}</g>
  <g transform="translate(88 4) scale(-4 4)">${art}</g>
</svg>`;
}

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
for (const [id, design] of Object.entries(CAPES)) {
  write(`textures/cosmetics/cape/${id}.png`, pngSmooth(capeSvg(design), 512));
  // weich gefilterte Vorderseite fuer die Menues (160x256 - nahe an der Anzeigegroesse, kein Flimmern)
  const front = `<svg xmlns="http://www.w3.org/2000/svg" width="10" height="16" viewBox="0 0 10 16"><defs>${grad('base', [[0, design.top], [1, design.bottom]])}</defs><rect width="10" height="16" fill="url(#base)"/>${design.art()}</svg>`;
  write(`textures/cosmetics/ui/cape_${id}.png`, pngSmooth(front, 160));
  index.capes.push({ id, name: design.name });
}
// Flügel-Texturen erzeugt generate-ingame-ui.cjs (zweistufige Galaxie-Flügel)
for (const [id, w] of Object.entries(WINGS)) index.wings.push({ id, name: w.name });
// Namensicon = das neue DeuClient-Emblem (hochaufloesend, wird im Spiel auf Textgroesse skaliert)
// nur der Smaragd: Kachel-Hintergrund, Rahmen und Schein werden entfernt, Ausschnitt auf den Stein
const EMBLEM_SVG = fs
  .readFileSync(path.resolve(__dirname, '..', 'img', 'emblem.svg'), 'utf8')
  .replace(/<rect[^>]*rx="112"[^>]*\/>\s*/g, '')
  .replace(/<circle cx="256" cy="240" r="230"[^>]*\/>\s*/, '')
  .replace('viewBox="0 0 512 512"', 'viewBox="46 44 420 420"');
write('textures/font/logo.png', cropToAlpha(pngSmooth(EMBLEM_SVG, 128)));
write(
  'font/logo.json',
  Buffer.from(JSON.stringify({ providers: [{ type: 'bitmap', file: 'deucore:font/logo.png', ascent: 8, height: 8, chars: [''] }, { type: 'space', advances: { ' ': 1 } }] }, null, 2)),
);
write('cosmetics.json', Buffer.from(JSON.stringify(index, null, 2)));
console.log('Fertig.');
