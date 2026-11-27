/**
 * Capes der Mythos-Kollektion: node tools/capes.cjs
 * Jedes Design wird als SVG gezeichnet (8 Pixel je Cape-Einheit) und ergibt:
 *   textures/cosmetics/cape/<id>.png     Cape + Elytra im Minecraft-Format 64x32, achtfach (512x256)
 *   textures/cosmetics/ui/cape_<id>.png  Menue-Vorschau (160x256)
 *   tools/capes/<id>.bbmodel             Ansicht in Blockbench (Cape und Elytra)
 *   tools/capes/alle_capes.bbmodel       alle Capes nebeneinander
 * Danach `node tools/shop-assets.cjs`, damit der Launcher die neuen Bilder bekommt.
 */
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { Resvg } = require('@resvg/resvg-js');

const assets = path.resolve(__dirname, '..', 'mod', 'mc26', 'src', 'main', 'resources', 'assets', 'deucore');
const viewDir = path.join(__dirname, 'capes');

const f = (v) => +v.toFixed(2);
const noise = (a, b, s = 1) => {
  const n = Math.sin(a * 12.9898 + b * 78.233 + s * 37.719) * 43758.5453;
  return n - Math.floor(n);
};

/* ---------------- Bausteine ---------------- */

const stops = (list) => list.map(([o, c, a = 1]) => `<stop offset="${o}" stop-color="${c}" stop-opacity="${a}"/>`).join('');
const linear = (id, list, x2 = 0, y2 = 1) => `<linearGradient id="${id}" x1="0" y1="0" x2="${x2}" y2="${y2}">${stops(list)}</linearGradient>`;
const radial = (id, list) => `<radialGradient id="${id}" cx="0.5" cy="0.5" r="0.5">${stops(list)}</radialGradient>`;
const glow = (x, y, rx, ry, id) => `<ellipse cx="${x}" cy="${y}" rx="${rx}" ry="${ry}" fill="url(#${id})"/>`;
const mirror = (w, content) => `${content}<g transform="translate(${w} 0) scale(-1 1)">${content}</g>`;

const star = (x, y, r, c = '#ffffff', o = 1) =>
  `<path d="M${f(x)} ${f(y - r)}L${f(x + r * 0.22)} ${f(y - r * 0.22)}L${f(x + r)} ${f(y)}L${f(x + r * 0.22)} ${f(y + r * 0.22)}L${f(x)} ${f(y + r)}L${f(x - r * 0.22)} ${f(y + r * 0.22)}L${f(x - r)} ${f(y)}L${f(x - r * 0.22)} ${f(y - r * 0.22)}Z" fill="${c}" opacity="${o}"/>`;
const dust = (w, h, n, seed, c) => {
  let s = '';
  for (let i = 0; i < n; i++) s += `<circle cx="${f(3 + noise(i, 1, seed) * (w - 6))}" cy="${f(3 + noise(i, 2, seed) * (h - 6))}" r="${f(0.25 + noise(i, 3, seed) * 0.55)}" fill="${c}" opacity="${f(0.3 + noise(i, 4, seed) * 0.6)}"/>`;
  return s;
};

/** Feder: Wurzel bei (x,y), haengt nach unten; angle > 0 schwenkt die Spitze nach links */
function feather(x, y, len, w, angle, fill, edge, vein = edge) {
  const body = `M0 0C${w} ${f(len * 0.18)} ${f(w * 1.05)} ${f(len * 0.68)} 0 ${len}C${f(-w * 1.05)} ${f(len * 0.68)} ${-w} ${f(len * 0.18)} 0 0Z`;
  const half = `M0 0C${w} ${f(len * 0.18)} ${f(w * 1.05)} ${f(len * 0.68)} 0 ${len}Z`;
  return `<g transform="translate(${f(x)} ${f(y)}) rotate(${f(angle)})"><path d="${body}" fill="${fill}" stroke="${edge}" stroke-width="0.7"/><path d="${half}" fill="#000" opacity="0.12"/><path d="M0 ${f(len * 0.1)}L0 ${f(len * 0.82)}" stroke="${vein}" stroke-width="0.6" opacity="0.8" fill="none"/></g>`;
}

/** Kristallsplitter mit heller und dunkler Seite */
function shard(x, y, len, w, angle, light, dark, edge) {
  return `<g transform="translate(${f(x)} ${f(y)}) rotate(${f(angle)})"><path d="M0 0L${-w} ${f(w * 1.3)}L${f(-w * 0.75)} ${f(len * 0.72)}L0 ${len}Z" fill="${light}"/><path d="M0 0L${w} ${f(w * 1.3)}L${f(w * 0.75)} ${f(len * 0.72)}L0 ${len}Z" fill="${dark}"/><path d="M0 0L${w} ${f(w * 1.3)}L${f(w * 0.75)} ${f(len * 0.72)}L0 ${len}L${f(-w * 0.75)} ${f(len * 0.72)}L${-w} ${f(w * 1.3)}Z" fill="none" stroke="${edge}" stroke-width="0.6"/><path d="M0 1L0 ${f(len - 2)}" stroke="${edge}" stroke-width="0.45" opacity="0.75" fill="none"/></g>`;
}

/** Facettierte Raute in Metallfassung */
function gem(x, y, r, [hi, mid, low, deep], metal, metalDark) {
  const d = (s) => `M0 ${f(-s * 1.2)}L${s} 0L0 ${f(s * 1.2)}L${-s} 0Z`;
  const i = r * 0.46;
  return `<g transform="translate(${x} ${y})"><path d="${d(r + 2.6)}" fill="${metalDark}"/><path d="${d(r + 1.7)}" fill="${metal}"/><path d="${d(r)}" fill="${mid}"/>
    <path d="M0 ${f(-r * 1.2)}L${-r} 0L${f(-i)} 0L0 ${f(-i * 1.2)}Z" fill="${hi}"/><path d="M${r} 0L0 ${f(r * 1.2)}L0 ${f(i * 1.2)}L${f(i)} 0Z" fill="${deep}"/><path d="M${-r} 0L0 ${f(r * 1.2)}L0 ${f(i * 1.2)}L${f(-i)} 0Z" fill="${low}"/>
    <path d="M0 ${f(-i * 1.2)}L${f(i)} 0L0 ${f(i * 1.2)}L${f(-i)} 0Z" fill="${mid}" stroke="${hi}" stroke-width="0.4"/>${star(-r * 0.28, -r * 0.34, r * 0.38)}</g>`;
}

/** Doppelter Rahmen mit Eckrauten */
function frame(w, h, dark, metal, shine) {
  const corner = (x, y) => `<path d="M${x} ${y - 4.2}L${x + 4.2} ${y}L${x} ${y + 4.2}L${x - 4.2} ${y}Z" fill="url(#${metal})" stroke="${dark}" stroke-width="0.7"/><circle cx="${x}" cy="${y}" r="1" fill="${shine}"/>`;
  return `<rect x="0.8" y="0.8" width="${w - 1.6}" height="${h - 1.6}" fill="none" stroke="${dark}" stroke-width="1.6"/>
    <rect x="2.7" y="2.7" width="${w - 5.4}" height="${h - 5.4}" rx="1.5" fill="none" stroke="url(#${metal})" stroke-width="2.4"/>
    <rect x="5.4" y="5.4" width="${w - 10.8}" height="${h - 10.8}" rx="1" fill="none" stroke="${shine}" stroke-width="0.45" opacity="0.65"/>
    ${corner(5.4, 5.4)}${corner(w - 5.4, 5.4)}${corner(5.4, h - 5.4)}${corner(w - 5.4, h - 5.4)}`;
}

/** Reihe haengender Elemente, symmetrisch von aussen nach innen (die Mitte liegt obenauf) */
function fan(w, count, draw) {
  let half = '';
  for (let i = count; i >= 1; i--) half += draw(i / count, -1);
  let other = '';
  for (let i = count; i >= 1; i--) other += draw(i / count, 1);
  return half + other + draw(0, 0);
}

/* ---------------- Designs (w x h: Cape 80x128, Elytra 80x160) ---------------- */

const CAPES = {
  /* Gold: Gegenstueck zu den Engelsfluegeln */
  gold: {
    name: 'Gold',
    trim: ['au-metal', '#6a470a', '#fff8dc', '#ffd35a'],
    edge: ['#e8c76a', '#7a5410'],
    defs: linear('au-bg', [[0, '#fffaf0'], [0.45, '#f3dc9c'], [1, '#8a5e12']])
      + linear('au-metal', [[0, '#fff6cf'], [0.35, '#f2c14a'], [0.65, '#b9820f'], [1, '#ffe08a']], 1, 1)
      + linear('au-feather', [[0, '#ffffff'], [0.6, '#fdf3da'], [1, '#e2b954']])
      + radial('au-glow', [[0, '#ffffff', 0.95], [1, '#ffffff', 0]]),
    art(w, h) {
      const cx = w / 2, gy = h * 0.34;
      let rays = '';
      for (let i = 0; i < 16; i++) rays += `<path d="M${cx} ${gy}L${f(cx + Math.cos((i / 16) * 6.283) * 90 - 3)} ${f(gy + Math.sin((i / 16) * 6.283) * 90)}L${f(cx + Math.cos((i / 16) * 6.283) * 90 + 3)} ${f(gy + Math.sin((i / 16) * 6.283) * 90 + 3)}Z" fill="#fff" opacity="0.16"/>`;
      let wing = '';
      for (let i = 6; i >= 0; i--) wing += feather(cx - 6 - i * 3.6, gy - 1 - i * 2.2, 27 - i * 1.6, 4.4, 38 + i * 8, 'url(#au-feather)', '#c8921c');
      const rows = [[h - 56, 26, 6], [h - 42, 28, 5.6], [h - 28, 22, 5]]
        .map(([y, len, wd]) => fan(w, 4, (t, s) => feather(cx + s * t * 31, y - t * 9, len - t * 6, wd, s * -t * 22, 'url(#au-feather)', '#c8921c'))).join('');
      return `<rect width="${w}" height="${h}" fill="url(#au-bg)"/>${rays}${glow(cx, gy, 34, 34, 'au-glow')}
        ${rows}${mirror(w, wing)}
        <ellipse cx="${cx}" cy="${gy - 18.5}" rx="12.5" ry="3.4" fill="none" stroke="#8a5e12" stroke-width="3"/><ellipse cx="${cx}" cy="${gy - 18.5}" rx="12.5" ry="3.4" fill="none" stroke="url(#au-metal)" stroke-width="1.8"/>
        ${gem(cx, gy, 8, ['#fffdf0', '#ffd35a', '#e19a12', '#a86a08'], 'url(#au-metal)', '#7a5410')}
        ${star(15, 17, 4)}${star(66, 22, 3)}${star(63, h * 0.52, 2.4)}${star(17, h * 0.56, 2)}
        ${frame(w, h, '#6a470a', 'au-metal', '#fff8dc')}`;
    },
  },

  /* Sternennacht: Gegenstueck zu den Schattenfluegeln */
  nacht: {
    name: 'Sternennacht',
    trim: ['na-metal', '#0c0724', '#d9ccff', '#9461f0'],
    edge: ['#2c1f63', '#07040f'],
    defs: linear('na-bg', [[0, '#241a5c'], [0.5, '#140d36'], [1, '#05030d']])
      + linear('na-metal', [[0, '#c9b4ff'], [0.4, '#7d5ae0'], [0.7, '#3d2688'], [1, '#a98cff']], 1, 1)
      + linear('na-feather', [[0, '#5a3fb0'], [0.55, '#2a1b62'], [1, '#0d0722']])
      + radial('na-violet', [[0, '#a15cff', 0.65], [1, '#a15cff', 0]])
      + radial('na-blue', [[0, '#3f7bff', 0.5], [1, '#3f7bff', 0]])
      + radial('na-moon', [[0, '#fff6d8', 0.75], [1, '#fff6d8', 0]]),
    art(w, h) {
      const cx = w / 2, gy = h * 0.47;
      const moon = `${glow(cx + 9, 27, 24, 24, 'na-moon')}<path d="M${cx + 12} 12A15.5 15.5 0 1 0 ${cx + 25} 33A12.5 12.5 0 1 1 ${cx + 12} 12Z" fill="#fff3c8" stroke="#ffe79a" stroke-width="0.6"/>`;
      const lines = [[12, 22], [21, 33], [15, 46], [27, 52]];
      const constellation = `<path d="M${lines.map((p) => p.join(' ')).join('L')}" fill="none" stroke="#b9c6ff" stroke-width="0.45" opacity="0.6"/>${lines.map(([x, y]) => star(x, y, 2.4, '#e6ebff')).join('')}`;
      const rows = [[h - 54, 34, 6.4], [h - 38, 32, 5.8], [h - 22, 20, 5]]
        .map(([y, len, wd]) => fan(w, 4, (t, s) => feather(cx + s * t * 32, y - t * 13, len - t * 8, wd, s * -t * 16, 'url(#na-feather)', '#8a67e8', '#b49bff'))).join('');
      return `<rect width="${w}" height="${h}" fill="url(#na-bg)"/>
        ${glow(18, h * 0.32, 42, 30, 'na-violet')}${glow(64, h * 0.55, 38, 34, 'na-blue')}${glow(40, h * 0.12, 40, 16, 'na-blue')}
        ${dust(w, h * 0.7, 90, 3, '#dfe6ff')}${moon}${constellation}
        ${rows}${glow(cx, gy, 22, 22, 'na-violet')}
        ${gem(cx, gy, 7, ['#e6d6ff', '#9461f0', '#5a2fb8', '#33176e'], 'url(#na-metal)', '#1a0f44')}
        ${star(63, 53, 3.4)}${star(10, 66, 2.2)}${star(70, 14, 2)}
        ${frame(w, h, '#0c0724', 'na-metal', '#d9ccff')}`;
    },
  },

  /* Flamme: Gegenstueck zu den Phoenixfluegeln */
  flamme: {
    name: 'Flamme',
    trim: ['fl-metal', '#2a0702', '#ffe6a0', '#ffc43a'],
    edge: ['#7a1408', '#2a0503'],
    defs: linear('fl-bg', [[0, '#240403'], [0.45, '#6e0f08'], [0.8, '#c4360c'], [1, '#ff8a14']])
      + linear('fl-metal', [[0, '#ffe39a'], [0.4, '#e88a1c'], [0.7, '#8a3a08'], [1, '#ffc14a']], 1, 1)
      + linear('fl-feather', [[0, '#fff2a0'], [0.35, '#ffb020'], [0.75, '#f0480c'], [1, '#9a1206']])
      + linear('fl-fire', [[0, '#ff7a14', 0], [0.4, '#ff9a1f'], [1, '#fff3a8']])
      + radial('fl-glow', [[0, '#ffd060', 0.85], [1, '#ff5a14', 0]]),
    art(w, h) {
      const cx = w / 2, gy = h * 0.34;
      let wing = '';
      for (let i = 6; i >= 0; i--) wing += feather(cx - 4 - i * 3.5, gy + 3 - i * 2.9, 28 - i * 1.4, 3.9, 52 + i * 13, 'url(#fl-feather)', '#ffd36a', '#fff3b0');
      const tail = [-2, 2, -1, 1, 0].map((i) => {
        const x = cx + i * 7, len = h * 0.36 - Math.abs(i) * 7;
        return `<path d="M${cx} ${gy + 12}C${cx + i * 9} ${f(gy + len * 0.45)} ${x - i * 5} ${f(gy + len * 0.7)} ${x + i * 6} ${f(gy + 14 + len)}C${x + i * 2 + 6} ${f(gy + len * 0.75)} ${cx + i * 12 + 6} ${f(gy + len * 0.4)} ${cx} ${gy + 12}Z" fill="url(#fl-feather)" stroke="#ffc14a" stroke-width="0.5"/>${star(x + i * 5, gy + 6 + len, 2, '#fff3b0')}`;
      }).join('');
      const flames = (base, amp, o, seed) => {
        let d = `M0 ${h}L0 ${base}`;
        for (let x = 0; x < w; x += 10) {
          const top = base - amp * (0.5 + noise(x, seed, 5));
          d += `C${x + 2} ${f(base - 4)} ${x + 6} ${f(top + 8)} ${x + 5} ${f(top)}C${x + 10} ${f(top + 9)} ${x + 7} ${f(base - 3)} ${x + 10} ${base}`;
        }
        return `<path d="${d}L${w} ${h}Z" fill="url(#fl-fire)" opacity="${o}"/>`;
      };
      return `<rect width="${w}" height="${h}" fill="url(#fl-bg)"/>${glow(cx, gy, 40, 42, 'fl-glow')}
        ${flames(h - 12, 24, 0.75, 1)}${tail}${flames(h - 2, 14, 0.9, 2)}
        ${mirror(w, wing)}
        <path d="M${cx} ${gy - 21}C${cx + 2} ${gy - 17} ${cx + 6} ${gy - 14} ${cx + 3.5} ${gy - 9}C${cx + 7} ${gy - 4} ${cx + 7} ${gy + 8} ${cx} ${gy + 17}C${cx - 7} ${gy + 8} ${cx - 7} ${gy - 4} ${cx - 3.5} ${gy - 9}C${cx - 6} ${gy - 14} ${cx - 2} ${gy - 17} ${cx} ${gy - 21}Z" fill="url(#fl-feather)" stroke="#fff0a0" stroke-width="0.7"/>
        <path d="M${cx - 1} ${gy - 19}Q${cx - 6} ${gy - 27} ${cx - 1} ${gy - 31}Q${cx - 1} ${gy - 26} ${cx + 1} ${gy - 24}Q${cx + 4} ${gy - 27} ${cx + 6} ${gy - 26}Q${cx + 2} ${gy - 23} ${cx + 1} ${gy - 19}Z" fill="#ffd34a"/>
        ${gem(cx, gy + 1, 4.6, ['#fffbe0', '#ffc43a', '#f06a0c', '#b82a06'], 'url(#fl-metal)', '#5a1404')}
        ${dust(w, h, 46, 8, '#ffd060')}${star(14, 20, 2.6, '#fff3b0')}${star(66, 15, 2, '#fff3b0')}
        ${frame(w, h, '#2a0702', 'fl-metal', '#ffe6a0')}`;
    },
  },

  /* Eiskristall: Gegenstueck zu den Eisfluegeln */
  eis: {
    name: 'Eiskristall',
    trim: ['ei-metal', '#0b3358', '#ffffff', '#3187c0'],
    edge: ['#5aa9d8', '#0c2f52'],
    defs: linear('ei-bg', [[0, '#c9f4ff'], [0.35, '#6cc0ea'], [0.7, '#2672ac'], [1, '#08233f']])
      + linear('ei-metal', [[0, '#ffffff'], [0.4, '#aee6ff'], [0.7, '#4a97c8'], [1, '#e2f8ff']], 1, 1)
      + radial('ei-glow', [[0, '#ffffff', 0.9], [1, '#bff0ff', 0]]),
    art(w, h) {
      const cx = w / 2, gy = h * 0.37;
      let icicles = '';
      for (let i = 0; i < 9; i++) icicles += shard(7 + i * 8.25, 2, 10 + noise(i, 4, 2) * 15, 3.6, 0, '#f2fdff', '#9fdcf8', '#ffffff');
      let burst = '';
      for (let i = 0; i < 12; i++) {
        const long = i % 2 === 0;
        burst += shard(cx, gy, long ? 33 : 23, long ? 4.2 : 3, i * 30, '#e4faff', '#7fc9ee', '#ffffff');
      }
      const rows = [[h - 58, 36, 5.4], [h - 38, 30, 4.8]]
        .map(([y, len, wd]) => fan(w, 4, (t, s) => shard(cx + s * t * 31, y - t * 12, len - t * 9, wd, s * -t * 20, '#c4efff', '#3f93c6', '#eefcff'))).join('');
      const hex = (r) => Array.from({ length: 6 }, (_, i) => `${f(cx + Math.sin((i / 6) * 6.283) * r)} ${f(gy - Math.cos((i / 6) * 6.283) * r * 1.15)}`);
      const outer = hex(13), inner = hex(6.5);
      const facets = outer.map((p, i) => `<path d="M${p}L${outer[(i + 1) % 6]}L${inner[(i + 1) % 6]}L${inner[i]}Z" fill="${['#ffffff', '#a8e4fb', '#5cb4e4', '#3187c0', '#6fc2ec', '#d6f6ff'][i]}" stroke="#ffffff" stroke-width="0.5"/>`).join('');
      return `<rect width="${w}" height="${h}" fill="url(#ei-bg)"/>
        <path d="M0 ${h * 0.5}L26 ${h * 0.34}L18 ${h * 0.68}Z M${w} ${h * 0.18}L52 ${h * 0.3}L66 ${h * 0.55}Z" fill="#ffffff" opacity="0.16"/>
        ${glow(cx, gy, 36, 36, 'ei-glow')}${rows}${burst}
        <path d="M${outer.join('L')}Z" fill="#8fd6f6" stroke="url(#ei-metal)" stroke-width="3.4"/>${facets}
        <path d="M${inner.join('L')}Z" fill="#dff8ff" stroke="#ffffff" stroke-width="0.6"/>${star(cx - 2.5, gy - 3, 4.4)}
        ${icicles}${dust(w, h, 50, 11, '#ffffff')}${star(13, h * 0.52, 3)}${star(67, h * 0.56, 2.4)}${star(66, 33, 2)}
        ${frame(w, h, '#0b3358', 'ei-metal', '#ffffff')}`;
    },
  },

  /* Smaragd: Gegenstueck zu den Smaragdfluegeln */
  smaragd: {
    name: 'Smaragd',
    trim: ['sm-metal', '#032414', '#e6fff0', '#1fae60'],
    edge: ['#157a45', '#031a0d'],
    defs: linear('sm-bg', [[0, '#1f9e58'], [0.45, '#0d5a31'], [1, '#021209']])
      + linear('sm-metal', [[0, '#f0fff5'], [0.4, '#8ff0b4'], [0.7, '#2c9c5e'], [1, '#d4ffe4']], 1, 1)
      + radial('sm-glow', [[0, '#7dffb0', 0.75], [1, '#7dffb0', 0]]),
    art(w, h) {
      const cx = w / 2, gy = h * 0.34;
      let lattice = '';
      for (let row = 0; row * 11 < h + 11; row++) for (let col = 0; col < 6; col++) {
        const x = col * 18 + (row % 2) * 9 - 5, y = row * 11;
        lattice += `<path d="M${x} ${y - 11}L${x + 9} ${y}L${x} ${y + 11}L${x - 9} ${y}Z" fill="#7dffb0" opacity="${f(0.02 + noise(row, col, 7) * 0.09)}" stroke="#04301a" stroke-width="0.5" stroke-opacity="0.5"/>`;
      }
      const rows = [[h - 62, 38, 6], [h - 42, 34, 5.4]]
        .map(([y, len, wd]) => fan(w, 4, (t, s) => shard(cx + s * t * 31, y - t * 11, len - t * 9, wd, s * -t * 20, '#8dffb9', '#159a52', '#d4ffe4'))).join('');
      const crown = fan(w, 2, (t, s) => shard(cx + s * t * 14, gy - 13 + t * 5, 17 - t * 4, 4, 180 + s * t * 40, '#a8ffca', '#1fae60', '#e6fff0'));
      // Smaragdschliff: Achteck mit Stufen
      const oct = (a, b, c) => [[-a + c, -b], [a - c, -b], [a, -b + c], [a, b - c], [a - c, b], [-a + c, b], [-a, b - c], [-a, -b + c]].map(([x, y]) => `${f(cx + x)} ${f(gy + y)}`);
      const o = oct(14, 18, 7), i = oct(7.5, 10.5, 3.8);
      const tones = ['#c8ffdc', '#5ef099', '#27b967', '#127a42', '#0d6636', '#17894c', '#3fdc84', '#8dffb9'];
      const facets = o.map((p, k) => `<path d="M${p}L${o[(k + 1) % 8]}L${i[(k + 1) % 8]}L${i[k]}Z" fill="${tones[k]}" stroke="#e6fff0" stroke-width="0.45"/>`).join('');
      return `<rect width="${w}" height="${h}" fill="url(#sm-bg)"/>${lattice}${glow(cx, gy, 38, 40, 'sm-glow')}
        ${rows}${crown}
        <path d="M${oct(17, 21, 8.5).join('L')}Z" fill="url(#sm-metal)" stroke="#04301a" stroke-width="0.9"/>${facets}
        <path d="M${i.join('L')}Z" fill="#35d67e" stroke="#e6fff0" stroke-width="0.7"/>
        <path d="M${cx - 5} ${gy - 8}L${cx + 2} ${gy - 8}L${cx - 5} ${gy + 4}Z" fill="#ffffff" opacity="0.45"/>
        ${star(cx - 8, gy - 12, 5)}${dust(w, h, 34, 5, '#c8ffd8')}${star(14, 18, 2.6, '#e6fff0')}${star(66, h * 0.5, 3, '#e6fff0')}${star(12, h * 0.54, 2, '#e6fff0')}
        ${frame(w, h, '#032414', 'sm-metal', '#e6fff0')}`;
    },
  },

  /* Daemon: Gegenstueck zu den Daemonenfluegeln */
  daemon: {
    name: 'Dämon',
    trim: ['de-metal', '#160203', '#ffb09a', '#ff3a24'],
    edge: ['#5a0a0e', '#120102'],
    defs: linear('de-bg', [[0, '#170204'], [0.4, '#4c070d'], [0.75, '#2a0306'], [1, '#080001']])
      + linear('de-metal', [[0, '#ff9a7a'], [0.4, '#b81c1c'], [0.7, '#450507'], [1, '#e0503a']], 1, 1)
      + linear('de-skin', [[0, '#c41a1c'], [0.55, '#7a0a10'], [1, '#240204']])
      + linear('de-horn', [[0, '#f4e0c4'], [0.4, '#9a6a4a'], [1, '#2a100a']])
      + radial('de-glow', [[0, '#ff4a24', 0.75], [1, '#ff1a14', 0]]),
    art(w, h) {
      const cx = w / 2, gy = h * 0.36;
      // Aderung im Hintergrund
      let veins = '';
      for (let i = 0; i < 9; i++) {
        let x = 4 + noise(i, 1, 13) * (w - 8), y = noise(i, 2, 13) * h * 0.3 + i * h * 0.08, d = `M${f(x)} ${f(y)}`;
        for (let k = 0; k < 4; k++) {
          x += (noise(i, k, 14) - 0.5) * 22;
          y += 4 + noise(i, k, 15) * 9;
          d += `L${f(x)} ${f(y)}`;
        }
        veins += `<path d="${d}" fill="none" stroke="#ff3a2a" stroke-width="0.55" opacity="0.22"/>`;
      }
      // linke Haelfte: Horn und Flughaut mit Knochen
      const wrist = [cx - 17, gy - 12];
      const tips = [[7, gy - 22], [8, gy + 2], [14, gy + 18], [27, gy + 24]];
      const skin = `M${cx - 3} ${gy}L${wrist}L${tips[0]}Q17 ${gy - 9} ${tips[1]}Q19 ${gy + 3} ${tips[2]}Q23 ${gy + 10} ${tips[3]}Q32 ${gy + 12} ${cx - 2} ${gy + 15}Z`;
      const bone = (from, to, width) => `<path d="M${from}L${to}" stroke="#14070a" stroke-width="${width}" stroke-linecap="round" fill="none"/><path d="M${from}L${to}" stroke="#a8403a" stroke-width="${f(width * 0.3)}" stroke-linecap="round" fill="none"/>`;
      const claw = ([x, y]) => `<path d="M${x - 1.3} ${y}Q${x - 3.6} ${f(y + 1.5)} ${x - 2.4} ${f(y + 5.5)}Q${x - 0.6} ${f(y + 2.6)} ${x + 1.2} ${f(y + 0.8)}Z" fill="url(#de-horn)" stroke="#14070a" stroke-width="0.4"/>`;
      const wing = `<path d="${skin}" fill="url(#de-skin)" stroke="#ff5a30" stroke-width="0.7"/>
        ${tips.map((t) => bone(wrist, t, 1.9)).join('')}${bone([cx - 3, gy], wrist, 2.8)}${tips.map(claw).join('')}
        <circle cx="${wrist[0]}" cy="${wrist[1]}" r="2.1" fill="#2a0c0e" stroke="#ff7a50" stroke-width="0.5"/>`;
      const horn = `<path d="M${cx - 3} ${gy - 8}C${cx - 15} ${gy - 11} ${cx - 21} ${gy - 22} ${cx - 13} ${gy - 33}C${cx - 14} ${gy - 24} ${cx - 8} ${gy - 18} ${cx - 0.5} ${gy - 15}Z" fill="url(#de-horn)" stroke="#14070a" stroke-width="0.8"/>
        <path d="M${cx - 14} ${gy - 22}l5 -1.5M${cx - 11} ${gy - 17}l4 -2.5M${cx - 15.5} ${gy - 27}l4 0" stroke="#2a100a" stroke-width="0.6" opacity="0.8" fill="none"/>`;
      // Reihen aus Drachenschuppen
      const scale = (x, y, len, wd, angle) => {
        const body = `M0 0L${wd} ${f(len * 0.2)}Q${f(wd * 0.75)} ${f(len * 0.62)} 0 ${len}Q${f(-wd * 0.75)} ${f(len * 0.62)} ${-wd} ${f(len * 0.2)}Z`;
        return `<g transform="translate(${f(x)} ${f(y)}) rotate(${f(angle)})"><path d="${body}" fill="url(#de-skin)" stroke="#ff4a2a" stroke-width="0.65"/><path d="M0 0L${wd} ${f(len * 0.2)}Q${f(wd * 0.75)} ${f(len * 0.62)} 0 ${len}Z" fill="#000" opacity="0.28"/><path d="M0 1.5L0 ${f(len * 0.86)}" stroke="#16050a" stroke-width="1.1" fill="none"/></g>`;
      };
      const rows = [[h - 54, 31, 6.2], [h - 39, 29, 5.6], [h - 23, 19, 4.8]]
        .map(([y, len, wd]) => fan(w, 4, (t, s) => scale(cx + s * t * 31, y - t * 11, len - t * 7, wd, s * -t * 20))).join('');
      return `<rect width="${w}" height="${h}" fill="url(#de-bg)"/>${veins}${glow(cx, gy, 42, 40, 'de-glow')}
        ${rows}${mirror(w, wing + horn)}${glow(cx, gy + 1, 17, 17, 'de-glow')}
        ${gem(cx, gy + 1, 7, ['#ffd0b0', '#ff3a24', '#b8100c', '#6a0406'], 'url(#de-metal)', '#14070a')}
        ${dust(w, h, 48, 17, '#ff8a50')}${star(14, 17, 2.4, '#ffc0a0')}${star(66, 52, 2.2, '#ffc0a0')}${star(63, 12, 1.8, '#ffc0a0')}
        ${frame(w, h, '#160203', 'de-metal', '#ffb09a')}`;
    },
  },
};

/* ---------------- Ausgabe ---------------- */

const svg = (width, height, vw, vh, body) => `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${vw} ${vh}">${body}</svg>`;
const render = (source, width) => new Resvg(source, { fitTo: { mode: 'width', value: width }, shapeRendering: 2 }).render().asPng();
const panel = (x, y, w, h, content, flip = false) =>
  `<svg x="${x}" y="${y}" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}">${flip ? `<g transform="translate(${w} 0) scale(-1 1)">${content}</g>` : content}</svg>`;

/** Schmale Kante (Breite w, Laenge len): dunkler Stoffrand mit feiner Metallnaht und kleinen Punkten */
function trimStrip(w, len, [metal, dark], edge) {
  const c = w / 2, r = w * 0.17;
  let s = `<rect width="${w}" height="${len}" fill="url(#${edge})"/><rect width="${w}" height="${len}" fill="${dark}" opacity="0.35"/>`
    + `<rect x="${f(c - w * 0.07)}" width="${f(w * 0.14)}" height="${len}" fill="url(#${metal})" opacity="0.45"/>`;
  for (let y = 16; y < len; y += 32) {
    s += `<path d="M${c} ${f(y - r * 1.6)}L${f(c + r)} ${y}L${c} ${f(y + r * 1.6)}L${f(c - r)} ${y}Z" fill="url(#${metal})" opacity="0.6"/>`;
  }
  return s;
}

/** Textur 512x256 = 64x32 achtfach. Cape: aussen (1,1), innen (12,1); Elytra: innen (24,2), aussen (36,2) */
function texture(d) {
  const cape = d.art(80, 128), wing = d.art(80, 160);
  const shade = (w, h) => `<rect width="${w}" height="${h}" fill="#000" opacity="0.22"/>`;
  // Kanten: senkrechte Streifen, oben und unten um 90 Grad gedreht
  const strip = (w, len) => trimStrip(w, len, d.trim, `${d.id}-edge`);
  const side = (x, y, w, len) => panel(x, y, w, len, strip(w, len));
  const flat = (x, y, len, w) => panel(x, y, len, w, `<g transform="translate(${len} 0) rotate(90)">${strip(w, len)}</g>`);
  const edges = side(0, 8, 8, 128) + side(88, 8, 8, 128) + flat(8, 0, 80, 8) + flat(88, 0, 80, 8)
    + side(176, 16, 16, 160) + side(272, 16, 16, 160) + flat(192, 0, 80, 16) + flat(272, 0, 80, 16);
  return svg(512, 256, 512, 256, `<defs>${d.defs}${linear(`${d.id}-edge`, [[0, d.edge[0]], [1, d.edge[1]]])}</defs>
    <rect width="176" height="136" fill="url(#${d.id}-edge)"/>
    <rect x="176" width="192" height="176" fill="url(#${d.id}-edge)"/>
    ${panel(8, 8, 80, 128, cape)}${panel(96, 8, 80, 128, cape + shade(80, 128), true)}
    ${panel(192, 16, 80, 160, wing + shade(80, 160), true)}${panel(288, 16, 80, 160, wing)}${edges}`);
}

/* ---------------- Blockbench-Ansicht ---------------- */

const face = (uv, tex) => ({ uv, texture: tex });
function cube(name, from, to, faces, rotation = [0, 0, 0], origin = [0, 0, 0]) {
  return { name, type: 'cube', uuid: crypto.randomUUID(), box_uv: false, rescale: false, locked: false, autouv: 0, color: 0, from, to, origin, rotation, faces };
}

/** Cape und beide Elytra-Haelften; die Aussenseite zeigt nach vorn (Norden), damit man sie sofort sieht */
function viewCubes(id, tex, x) {
  const edge = (a, b) => ({ east: face(a, tex), west: face(b, tex) });
  const cape = cube(`${id}_cape`, [x - 5, 24, -0.5], [x + 5, 40, 0.5], {
    north: face([1, 1, 11, 17], tex), south: face([12, 1, 22, 17], tex),
    up: face([1, 0, 11, 1], tex), down: face([11, 0, 21, 1], tex), ...edge([0, 1, 1, 17], [11, 1, 12, 17]),
  });
  const wing = (side, mirrored) => {
    const from = side < 0 ? x - 11 : x + 1;
    return cube(`${id}_elytra_${side < 0 ? 'a' : 'b'}`, [from, 0, -1], [from + 10, 20, 1], {
      north: face(mirrored ? [46, 2, 36, 22] : [36, 2, 46, 22], tex), south: face(mirrored ? [34, 2, 24, 22] : [24, 2, 34, 22], tex),
      up: face([24, 0, 34, 2], tex), down: face([34, 0, 44, 2], tex), ...edge([22, 2, 24, 22], [34, 2, 36, 22]),
    }, [0, 0, side * -8], [from + 5, 20, 0]);
  };
  return [cape, wing(-1, false), wing(1, true)];
}

function bbmodel(name, items) {
  const elements = [], outliner = [];
  items.forEach(({ id, x }, tex) => {
    const cubes = viewCubes(id, tex, x);
    elements.push(...cubes);
    outliner.push({ name: id, uuid: crypto.randomUUID(), origin: [x, 20, 0], isOpen: false, children: cubes.map((c) => c.uuid) });
  });
  return JSON.stringify({
    meta: { format_version: '4.10', model_format: 'free', box_uv: false },
    name,
    resolution: { width: 64, height: 32 },
    elements,
    outliner,
    textures: items.map(({ id, png }, i) => ({
      name: `${id}.png`, id: String(i), uuid: crypto.randomUUID(), width: 512, height: 256, uv_width: 64, uv_height: 32,
      render_mode: 'default', visible: true, mode: 'bitmap', saved: true, source: `data:image/png;base64,${png.toString('base64')}`,
    })),
  });
}

function build() {
  fs.mkdirSync(viewDir, { recursive: true });
  const items = [];
  for (const [id, d] of Object.entries(CAPES)) {
    d.id = id;
    const png = render(texture(d), 512);
    fs.writeFileSync(path.join(assets, 'textures', 'cosmetics', 'cape', `${id}.png`), png);
    const front = svg(80, 128, 80, 128, `<defs>${d.defs}</defs>${d.art(80, 128)}`);
    fs.writeFileSync(path.join(assets, 'textures', 'cosmetics', 'ui', `cape_${id}.png`), render(front, 160));
    fs.writeFileSync(path.join(viewDir, `${id}.bbmodel`), bbmodel(`${id}_cape`, [{ id, x: 0, png }]));
    items.push({ id, png });
    console.log('  ✓', d.name);
  }
  // nebeneinander; von vorn gesehen liegt +X links, daher absteigend
  fs.writeFileSync(path.join(viewDir, 'alle_capes.bbmodel'), bbmodel('alle_capes', items.map((it, i) => ({ ...it, x: ((items.length - 1) / 2 - i) * 28 }))));
}

module.exports = { CAPES, build };

if (require.main === module) build();
