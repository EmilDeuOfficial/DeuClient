'use strict';
/**
 * Modpack-Icons als Blockbench-Modelle (Format "free"), ein Modell je Icon-Schluessel.
 *
 *   node tools/pack-icons.cjs            Modelle erzeugen und rendern
 *   node tools/pack-icons.cjs --render   nur die vorhandenen Modelle rendern (nach Aenderungen in Blockbench)
 *
 * Modelle: tools/pack-icons/<key>.bbmodel. Die Bilder des Launchers (src/assets/pack-icons/<key>.png,
 * 256x256, transparent, Motiv mittig) sind Renderings dieser Modelle: orthografisch, flaches Licht (die Schattierung steckt
 * in der Textur), Ansicht "iso" fuer die Bloecke, sonst "front".
 * Zum Rendern dient bb-render aus dem Blockbench-MCP (blockbench-mcp); anderer Ort: Umgebungsvariable BB_RENDER.
 * Achtung: ein Lauf ohne --render ueberschreibt Aenderungen, die in Blockbench gemacht wurden.
 */
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const os = require('os');
const { execFileSync } = require('child_process');
const { PNG } = require('pngjs');

const OUT = path.join(__dirname, 'pack-icons');
const PNG_OUT = path.join(__dirname, '..', 'src', 'assets', 'pack-icons');
const RENDERER = process.env.BB_RENDER || path.join(process.env.LOCALAPPDATA || os.homedir(), 'blockbench-mcp-headless', 'render', 'cli.mjs');
const ISO = ['grass', 'tnt', 'creeper', 'package'];
const SIZE = 256;
/** Laengere Seite des sichtbaren Motivs in Bildpunkten (optisch angeglichen) */
const FILL = { deu: 216, grass: 240, diamond: 204, sword: 232, pickaxe: 232, tnt: 240, creeper: 240, ender: 232, package: 240 };
const FACES = ['north', 'east', 'south', 'west', 'up', 'down'];

/* Gruentoene der DeuClient-Oberflaeche: Modpack-Icons bleiben immer gruen */
const GREEN = { 50: '#f0fff6', 100: '#d4ffe4', 200: '#9fffc0', 300: '#6bff9e', 400: '#2eff7a', 500: '#1fd35f', 600: '#17a84a', 700: '#0f7a35', 800: '#0a5526', 900: '#06371a' };

const rgb = (hex) => [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16));
const hex = (c) => '#' + c.map((v) => Math.max(0, Math.min(255, Math.round(v))).toString(16).padStart(2, '0')).join('');
const shade = (color, f) => hex(rgb(color).map((v) => v * f));
const hash = (x, y, s) => {
  let h = (x * 374761393 + y * 668265263 + s * 1274126177) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  return ((h ^ (h >>> 16)) >>> 0) / 4294967296;
};
const pick = (list, x, y, s) => list[Math.floor(hash(x, y, s) * list.length)];
const uuid = (seed) => {
  const h = crypto.createHash('sha1').update(seed).digest('hex');
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`;
};

/* ------------------------------------------------------------ Modell */
class Model {
  constructor(key, rotation = [0, 0, 0]) {
    this.key = key;
    this.rotation = rotation;
    this.elements = [];
    this.pixels = new Map(); // "x,y" -> hex
    this.x = 0;
    this.y = 0;
    this.row = 0;
    this.swatches = new Map();
  }

  /** Platz im Atlas (Zeilen-Packer, 1 Pixel Abstand) */
  alloc(w, h) {
    if (this.x + w > 128) {
      this.x = 0;
      this.y += this.row + 1;
      this.row = 0;
    }
    const at = [this.x, this.y];
    this.x += w + 1;
    this.row = Math.max(this.row, h);
    return at;
  }

  /** Einfarbiges Feld, Rueckgabe: Bildpunkt in dessen Mitte */
  swatch(color) {
    if (!this.swatches.has(color)) {
      const [x, y] = this.alloc(3, 3);
      for (let i = 0; i < 3; i++) for (let j = 0; j < 3; j++) this.pixels.set(`${x + i},${y + j}`, color);
      this.swatches.set(color, [x + 1, y + 1]);
    }
    return this.swatches.get(color);
  }

  element(name, data) {
    const id = uuid(`${this.key}/${this.elements.length}/${name}`);
    this.elements.push({ uuid: id, name, color: 0, locked: false, ...data });
  }

  /**
   * Wuerfel. paint(face, x, y, w, h) liefert die Farbe eines Texturpunkts (x/y von links oben, von aussen gesehen),
   * density = Texturpunkte je Einheit.
   */
  cube(name, from, to, paint, density = 1) {
    const [dx, dy, dz] = to.map((v, i) => v - from[i]);
    const size = { north: [dx, dy], south: [dx, dy], east: [dz, dy], west: [dz, dy], up: [dx, dz], down: [dx, dz] };
    const faces = {};
    for (const face of FACES) {
      const w = Math.max(1, Math.round(size[face][0] * density));
      const h = Math.max(1, Math.round(size[face][1] * density));
      const [ax, ay] = this.alloc(w, h);
      for (let x = 0; x < w; x++) for (let y = 0; y < h; y++) this.pixels.set(`${ax + x},${ay + y}`, paint(face, x, y, w, h));
      faces[face] = { uv: [ax, ay, ax + w, ay + h], texture: 0 };
    }
    this.element(name, { type: 'cube', from, to, origin: [0, 0, 0], box_uv: false, rescale: false, autouv: 0, faces });
  }

  /** Einfarbiger Wuerfel mit eigener Farbe je Seitenrichtung */
  solid(name, from, to, color, light) {
    const faces = {};
    for (const face of FACES) {
      const [x, y] = this.swatch(shade(color, light[face] ?? 1));
      faces[face] = { uv: [x, y, x + 0.5, y + 0.5], texture: 0 };
    }
    this.element(name, { type: 'cube', from, to, origin: [0, 0, 0], box_uv: false, rescale: false, autouv: 0, faces });
  }

  /** Facetten-Netz: faces = [{ points: [[x,y,z], ...3 oder 4], color }], Punkte gegen den Uhrzeigersinn von aussen */
  mesh(name, faces) {
    const vertices = {};
    const keys = new Map();
    const key = (p) => {
      const k = p.map((v) => v.toFixed(3)).join(',');
      if (!keys.has(k)) {
        keys.set(k, `v${keys.size}`);
        vertices[keys.get(k)] = p.map((v) => Math.round(v * 1000) / 1000);
      }
      return keys.get(k);
    };
    const out = {};
    faces.forEach((f, i) => {
      const [x, y] = this.swatch(f.color);
      const ids = f.points.map(key);
      out[`f${i}`] = { uv: Object.fromEntries(ids.map((id) => [id, [x, y]])), vertices: ids, texture: 0 };
    });
    this.element(name, { type: 'mesh', origin: [0, 0, 0], rotation: [0, 0, 0], vertices, faces: out });
  }

  save() {
    const height = Math.max(16, 2 ** Math.ceil(Math.log2(this.y + this.row + 1)));
    const png = new PNG({ width: 128, height });
    png.data.fill(0);
    for (const [at, color] of this.pixels) {
      const [x, y] = at.split(',').map(Number);
      png.data.set([...rgb(color), 255], (y * 128 + x) * 4);
    }
    const root = uuid(`${this.key}/root`);
    const model = {
      meta: { format_version: '5.0', model_format: 'free', box_uv: false },
      name: this.key,
      resolution: { width: 128, height },
      elements: this.elements,
      groups: [{ uuid: root, name: this.key, origin: [0, 0, 0], rotation: this.rotation, color: 0, export: true, visibility: true }],
      outliner: [{ uuid: root, isOpen: false, children: this.elements.map((e) => e.uuid) }],
      textures: [{
        uuid: uuid(`${this.key}/texture`), name: `${this.key}.png`, id: '0', width: 128, height, uv_width: 128, uv_height: height,
        render_mode: 'default', visible: true, internal: true,
        source: 'data:image/png;base64,' + PNG.sync.write(png).toString('base64'),
      }],
    };
    fs.writeFileSync(path.join(OUT, `${this.key}.bbmodel`), JSON.stringify(model));
    console.log(`${this.key}: ${this.elements.length} Teile`);
  }
}

/* ------------------------------------------------------------ Bloecke (Ansicht iso) */
/** Helligkeit je Seite: oben hell, Vorderseite mittel, die anderen Seiten dunkel */
const BLOCK_LIGHT = { up: 1, north: 0.86, south: 0.86, east: 0.62, west: 0.62, down: 0.45 };

function grass() {
  const m = new Model('grass');
  const dirt = ['#8a5a2b', '#8a5a2b', '#7a4f29', '#9a6a3a', '#6b4423'];
  const top = [GREEN[400], GREEN[400], GREEN[500], GREEN[300], GREEN[500]];
  // Tiefe des herabhaengenden Grases je Spalte
  const drip = [4, 5, 3, 3, 4, 6, 4, 3, 3, 4, 5, 3, 4, 3, 5, 4];
  m.cube('block', [-8, -8, -8], [8, 8, 8], (face, x, y) => {
    const s = FACES.indexOf(face);
    const l = BLOCK_LIGHT[face];
    if (face === 'up') return shade(pick(top, x, y, 1), l);
    const green = face !== 'down' && y < drip[(x + s * 5) % 16];
    if (green) return shade(y === drip[(x + s * 5) % 16] - 1 ? GREEN[600] : pick([GREEN[500], GREEN[500], GREEN[400], GREEN[600]], x, y, s + 2), l);
    return shade(hash(x, y, s + 9) < 0.04 ? '#a8845c' : pick(dirt, x, y, s + 3), l);
  });
  m.save();
}

function creeper() {
  const m = new Model('creeper');
  const skin = [GREEN[500], GREEN[400], GREEN[600], GREEN[500], GREEN[300], GREEN[600], GREEN[700]];
  const faceRows = ['........', '........', '.##..##.', '.##..##.', '...##...', '..####..', '..####..', '..#..#..'];
  m.cube('head', [-8, -8, -8], [8, 8, 8], (face, x, y) => {
    if (face === 'north' && faceRows[y][x] === '#') return (x + y) % 3 === 0 ? '#0e1a12' : '#07100a';
    return shade(pick(skin, x, y, FACES.indexOf(face) + 21), BLOCK_LIGHT[face]);
  }, 0.5);
  m.save();
}

function tnt() {
  const m = new Model('tnt');
  const letters = ['###.#..#.###', '.#..##.#..#.', '.#..#.##..#.', '.#..#..#..#.', '.#..#..#..#.'];
  m.cube('block', [-8, -8, -8], [8, 7, 8], (face, x, y) => {
    const l = BLOCK_LIGHT[face];
    if (face === 'up' || face === 'down') {
      const cx = x % 4, cy = y % 4;
      const stick = cx > 0 && cx < 3 && cy > 0 && cy < 3;
      return shade(stick ? '#e9c99b' : cx === 0 || cy === 0 ? '#a82621' : '#d83a34', l);
    }
    if (y >= 4 && y <= 10) {
      const ink = y >= 5 && y <= 9 && x >= 2 && x < 14 && letters[y - 5][x - 2] === '#';
      return shade(ink ? '#17171a' : y === 4 || y === 10 ? '#d4d4d8' : '#f8fafc', l);
    }
    return shade(x % 4 === 0 ? '#9c211d' : x % 4 === 2 ? '#e8483f' : '#d83a34', l);
  });
  const fuse = { up: 1, north: 0.86, east: 0.62, west: 0.62, south: 0.86, down: 0.5 };
  m.solid('fuse', [-1, 7, -1], [1, 10, 1], '#8b8b93', fuse);
  m.solid('fuse_tip', [0, 10, -1], [2, 11, 1], '#6b6b73', fuse);
  m.solid('spark', [1, 11, -1.5], [3.5, 13.5, 1], '#fde047', { up: 1.1, north: 1, east: 0.85, west: 0.85 });
  m.save();
}

function parcel() {
  const m = new Model('package');
  const tape = (face, x, w) => {
    const mid = x >= w / 2 - 2 && x < w / 2 + 2;
    return mid && ['up', 'north', 'south', 'down'].includes(face);
  };
  m.cube('box', [-7, -8, -7], [7, 3, 7], (face, x, y, w, h) => {
    const l = BLOCK_LIGHT[face];
    if (tape(face, x, w)) return shade(GREEN[100], l);
    if (y === 0 && face !== 'up' && face !== 'down') return shade(GREEN[800], l);
    if (x === 0 || x === w - 1 || y === h - 1) return shade(GREEN[700], l);
    return shade(pick([GREEN[500], GREEN[500], GREEN[500], GREEN[600]], x, y, FACES.indexOf(face) + 40), l);
  });
  m.cube('lid', [-8, 3, -8], [8, 7, 8], (face, x, y, w, h) => {
    const l = BLOCK_LIGHT[face];
    if (tape(face, x, w)) return shade(GREEN[50], l);
    if (face === 'up') return shade(x === 0 || y === 0 || x === w - 1 || y === h - 1 ? GREEN[300] : GREEN[400], l);
    return shade(y === 0 ? GREEN[300] : y === h - 1 ? GREEN[600] : GREEN[400], l);
  });
  m.save();
}

/* ------------------------------------------------------------ Edelsteine (Netze) */
const sub = (a, b) => a.map((v, i) => v - b[i]);
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const norm = (a) => a.map((v) => v / (Math.hypot(...a) || 1));

/** Facette eines konvexen Koerpers: Umlaufsinn nach aussen drehen, Farbe nach dem Lichteinfall */
function facet(points, ramp, bias = 0) {
  const centre = points[0].map((_, i) => points.reduce((s, p) => s + p[i], 0) / points.length);
  let n = norm(cross(sub(points[1], points[0]), sub(points[2], points[0])));
  let list = points;
  if (dot(n, centre) < 0) {
    list = [...points].reverse();
    n = n.map((v) => -v);
  }
  // Licht von links oben vorn (vorn = -Z)
  const light = (dot(n, norm([-0.55, 0.65, -0.52])) + 1) / 2;
  const i = Math.max(0, Math.min(ramp.length - 1, Math.round(light * (ramp.length - 1) + bias)));
  return { points: list, color: ramp[i] };
}

/** Funkeln: vierzackiger Stern knapp vor dem Stein */
function sparkle(m, name, cx, cy, z, r, color) {
  const thin = r * 0.22;
  const tips = [[0, r], [r, 0], [0, -r], [-r, 0]];
  const inner = [[thin, thin], [thin, -thin], [-thin, -thin], [-thin, thin]];
  const at = ([x, y]) => [cx + x, cy + y, z];
  const faces = [];
  tips.forEach((tip, i) => {
    const a = inner[(i + 3) % 4];
    const b = inner[i];
    faces.push({ points: [at(tip), at(a), at([0, 0]), at(b)].reverse(), color });
  });
  // Rueckseite mit umgekehrtem Umlauf, damit der Stern aus jeder Ansicht sichtbar ist
  m.mesh(name, [...faces, ...faces.map((f) => ({ points: [...f.points].reverse(), color }))]);
}

/** Das DeuClient-Emblem als geschliffener Smaragd */
function deu() {
  const m = new Model('deu', [-6, 16, 0]);
  const girdle = [[0, 10, 0], [8.6, 0, 0], [0, -10, 0], [-8.6, 0, 0]];
  const table = girdle.map(([x, y]) => [x * 0.56, y * 0.56, -3.4]);
  const apex = [0, 0, 4];
  // oben links, unten links, unten rechts, oben rechts (von vorn gesehen ist +X links)
  const front = [GREEN[200], GREEN[500], GREEN[700], GREEN[300]];
  const faces = [];
  for (let i = 0; i < 4; i++) {
    const j = (i + 1) % 4;
    faces.push({ ...facet([girdle[i], girdle[j], table[j], table[i]], [GREEN[700]]), color: front[i] });
    faces.push(facet([girdle[i], girdle[j], apex], [GREEN[900], GREEN[800], GREEN[700], GREEN[600]]));
  }
  faces.push({ ...facet([table[0], table[1], table[2]], [GREEN[500]]), color: GREEN[400] });
  faces.push({ ...facet([table[0], table[2], table[3]], [GREEN[500]]), color: GREEN[500] });
  m.mesh('gem', faces);
  sparkle(m, 'sparkle', 0, 0, -4.2, 4.4, GREEN[50]);
  sparkle(m, 'dot_top', -7.4, 6.6, -2, 1.6, GREEN[100]);
  sparkle(m, 'dot_bottom', 7.2, -5.6, -2, 1.2, GREEN[200]);
  m.save();
}

function diamond() {
  const m = new Model('diamond', [-16, 0, 0]);
  const ramp = ['#0f6f86', '#1792ac', '#26b3cc', '#45c8de', '#62d3e8', '#8fe6f4', '#d2f8ff'];
  const ring = (r, y, turn) => Array.from({ length: 8 }, (_, i) => {
    const a = ((i + turn) / 8) * Math.PI * 2;
    return [Math.sin(a) * r, y, -Math.cos(a) * r];
  });
  const table = ring(5.2, 6.4, 0.5);
  const crown = ring(8, 4.4, 0);
  const girdle = ring(10, 1.6, 0.5);
  const culet = [0, -9.4, 0];
  const faces = [];
  for (let i = 0; i < 8; i++) {
    const j = (i + 1) % 8;
    const k = (i + 7) % 8;
    faces.push(facet([table[i], table[j], crown[j]], ramp, 1));
    faces.push(facet([table[i], crown[i], crown[j]].map((p) => p), ramp, i % 2 ? 0 : 1));
    faces.push(facet([crown[i], girdle[k], girdle[i]], ramp, i % 2 ? -1 : 0));
    faces.push(facet([crown[i], girdle[i], crown[j]], ramp, 0));
    faces.push(facet([girdle[i], girdle[j], culet], ramp, i % 2 ? -1 : 1));
    faces.push({ ...facet([table[i], table[j], [0, 6.4, 0]], ramp), color: i % 2 ? '#d2f8ff' : '#aeeffa' });
  }
  m.mesh('gem', faces);
  sparkle(m, 'sparkle', 6.6, 6.8, -10.5, 2.6, '#ffffff');
  m.save();
}

/* ------------------------------------------------------------ Enderauge (Voxelkugel) */
function ender() {
  const m = new Model('ender', [-8, 14, 0]);
  const R = 8;
  const ramp = [GREEN[900], GREEN[800], GREEN[700], GREEN[600], GREEN[500], GREEN[400], GREEN[300]];
  const inside = (x, y, z) => Math.hypot(x + 0.5, y + 0.5, z + 0.5) <= R;
  const colorAt = (x, y, z) => {
    const p = [x + 0.5, y + 0.5, z + 0.5];
    const flat = Math.hypot(p[0], p[1]);
    if (p[2] < 0) {
      // Auge auf der Vorderseite: dunkler Ring, heller Kern, Glanzpunkt
      if (Math.hypot(p[0] - 1.5, p[1] - 1.5) <= 0.8) return '#ffffff';
      if (flat <= 2) return GREEN[200];
      if (flat <= 4.6) return flat <= 3.4 ? '#05160b' : '#0a2414';
    }
    const light = (dot(norm(p), norm([0.5, 0.65, -0.57])) + 1) / 2;
    return ramp[Math.min(ramp.length - 1, Math.floor(light * light * ramp.length + 0.4))];
  };
  const flatLight = { north: 1, south: 0.9, east: 0.8, west: 0.8, up: 1.08, down: 0.7 };
  for (let y = -R; y < R; y++) {
    for (let z = -R; z < R; z++) {
      for (let x = -R; x < R; ) {
        const surface = inside(x, y, z) && [[1, 0, 0], [-1, 0, 0], [0, 1, 0], [0, -1, 0], [0, 0, 1], [0, 0, -1]].some(([a, b, c]) => !inside(x + a, y + b, z + c));
        if (!surface) {
          x++;
          continue;
        }
        const color = colorAt(x, y, z);
        let end = x + 1;
        while (inside(end, y, z) && colorAt(end, y, z) === color) end++;
        m.solid(`v_${x}_${y}_${z}`, [x, y, z], [end, y + 1, z + 1], color, flatLight);
        x = end;
      }
    }
  }
  m.save();
}

/* ------------------------------------------------------------ Werkzeuge (aufrecht gebaut, um 45 Grad gekippt) */
/** Netzflaeche, deren Vorderseite in Richtung out zeigt */
function side(points, color, out) {
  const n = cross(sub(points[1], points[0]), sub(points[2], points[0]));
  return { points: dot(n, out) < 0 ? [...points].reverse() : points, color };
}

const TOOL_LIGHT = { north: 1, south: 0.8, east: 0.62, west: 0.78, up: 1.15, down: 0.55 };
const WOOD = '#9a6a3a';

function sword() {
  const m = new Model('sword', [0, 24, 45]);
  // Klinge mit Mittelgrat: links hell, rechts dunkler (von vorn gesehen ist +X links)
  const faces = [];
  for (const z of [-1, 1]) {
    const out = [0, 0, z];
    const ridge = (y) => [0, y, 1.3 * z];
    for (const x of [2.9, -2.9]) {
      const front = x > 0 ? '#f8fafc' : '#b6c3d4';
      const color = z < 0 ? front : shade(front, 0.75);
      faces.push(side([[x, -3, 0], [x, 10, 0], ridge(10), ridge(-3)], color, out));
      faces.push(side([[x, 10, 0], [0, 15.5, 0], ridge(10)], x > 0 ? shade(color, 0.94) : shade(color, 0.9), out));
    }
  }
  m.mesh('blade', faces);
  // Parierstange, Griff mit Wicklung, Knauf
  m.solid('guard', [-6, -5.4, -1.7], [6, -2.6, 1.7], GREEN[400], TOOL_LIGHT);
  for (const x of [-8.2, 6]) m.solid(`guard_end_${x}`, [x, -5.8, -2], [x + 2.2, -1.4, 2], GREEN[600], TOOL_LIGHT);
  m.solid('grip', [-1.2, -11, -1.2], [1.2, -5.4, 1.2], WOOD, TOOL_LIGHT);
  for (const y of [-7.4, -9.6]) m.solid(`wrap_${y}`, [-1.45, y, -1.45], [1.45, y + 0.8, 1.45], '#5c3a1c', TOOL_LIGHT);
  m.solid('pommel', [-2.2, -14.4, -1.8], [2.2, -11, 1.8], GREEN[400], TOOL_LIGHT);
  // Edelstein in der Mitte der Parierstange
  const gem = [[0, -2.2, -2.2], [1.8, -4, -2.2], [0, -5.8, -2.2], [-1.8, -4, -2.2]];
  const tip = [0, -4, -3.2];
  m.mesh('gem', [GREEN[50], GREEN[200], GREEN[500], GREEN[300]].map((color, i) => side([gem[i], gem[(i + 1) % 4], tip], color, [0, 0, -1])));
  m.save();
}

function pickaxe() {
  const m = new Model('pickaxe', [0, 24, 45]);
  m.solid('handle', [-1.3, -14, -1.3], [1.3, 8, 1.3], WOOD, TOOL_LIGHT);
  m.solid('handle_end', [-1.6, -14.6, -1.6], [1.6, -12.6, 1.6], '#5c3a1c', TOOL_LIGHT);
  // Kopf: gebogener Balken mit spitzen Enden, oben ein heller Streifen
  const STEPS = 14;
  const at = (i) => {
    const t = (i / STEPS) * 2 - 1;
    const top = 12.6 - 8.4 * t * t;
    const thick = 5.2 * (1 - Math.abs(t) ** 1.7);
    return { x: t * 13.5, top, mid: top - thick * 0.4, bottom: top - thick };
  };
  const faces = [];
  const D = 1.7;
  for (let i = 0; i < STEPS; i++) {
    const p = at(i), q = at(i + 1);
    for (const z of [-D, D]) {
      const dim = z < 0 ? 1 : 0.75;
      for (const [hi, lo, color] of [['top', 'mid', '#a6ecf8'], ['mid', 'bottom', '#3cc3dc']]) {
        const points = [[p.x, p[hi], z], [q.x, q[hi], z], [q.x, q[lo], z], [p.x, p[lo], z]].filter((v, k, l) => l.findIndex((w) => w.join() === v.join()) === k);
        faces.push(side(points, shade(color, dim), [0, 0, z]));
      }
    }
    for (const [edge, color, out] of [['top', '#d2f8ff', [0, 1, 0]], ['bottom', '#1a8aa3', [0, -1, 0]]]) {
      faces.push(side([[p.x, p[edge], -D], [q.x, q[edge], -D], [q.x, q[edge], D], [p.x, p[edge], D]], color, out));
    }
  }
  m.mesh('head', faces);
  m.solid('socket', [-2.1, 6.2, -2.2], [2.1, 13.4, 2.2], '#26a8c2', TOOL_LIGHT);
  m.save();
}

/** Schneidet das Motiv auf seine sichtbare Flaeche zu und setzt es genau in die Mitte (Flaechenmittel beim Verkleinern) */
function centre(source, fill) {
  let left = source.width, top = source.height, right = 0, bottom = 0;
  for (let y = 0; y < source.height; y++) {
    for (let x = 0; x < source.width; x++) {
      if (source.data[(y * source.width + x) * 4 + 3] < 8) continue;
      left = Math.min(left, x);
      top = Math.min(top, y);
      right = Math.max(right, x + 1);
      bottom = Math.max(bottom, y + 1);
    }
  }
  const scale = fill / Math.max(right - left, bottom - top);
  const offX = (SIZE - (right - left) * scale) / 2, offY = (SIZE - (bottom - top) * scale) / 2;
  const out = new PNG({ width: SIZE, height: SIZE });
  out.data.fill(0);
  for (let y = 0; y < SIZE; y++) {
    for (let x = 0; x < SIZE; x++) {
      // Quellbereich dieses Zielpunkts
      const x0 = left + (x - offX) / scale, x1 = x0 + 1 / scale;
      const y0 = top + (y - offY) / scale, y1 = y0 + 1 / scale;
      const sum = [0, 0, 0];
      let alpha = 0;
      for (let v = Math.max(top, Math.floor(y0)); v < Math.min(bottom, Math.ceil(y1)); v++) {
        for (let u = Math.max(left, Math.floor(x0)); u < Math.min(right, Math.ceil(x1)); u++) {
          const area = (Math.min(u + 1, x1) - Math.max(u, x0)) * (Math.min(v + 1, y1) - Math.max(v, y0));
          const at = (v * source.width + u) * 4;
          const a = (source.data[at + 3] / 255) * area;
          for (let c = 0; c < 3; c++) sum[c] += source.data[at + c] * a;
          alpha += a;
        }
      }
      if (alpha <= 0) continue;
      const at = (y * SIZE + x) * 4;
      for (let c = 0; c < 3; c++) out.data[at + c] = Math.round(sum[c] / alpha);
      out.data[at + 3] = Math.min(255, Math.round(alpha * scale * scale * 255));
    }
  }
  return out;
}

/** Flaches Licht liefert Farbe / PI, die lineare Belichtung mit PI stellt die Texturfarben genau wieder her */
function render(key) {
  const large = path.join(os.tmpdir(), `deu-pack-icon-${key}.png`);
  execFileSync(process.execPath, [
    RENDERER, path.join(OUT, `${key}.bbmodel`), '-o', large,
    '--width', '512', '--height', '512', '--transparent', '--ortho', '--view', ISO.includes(key) ? 'iso' : 'front', '--padding', '1.06',
    '--lighting', 'flat', '--tone-mapping', 'linear', '--exposure', String(Math.PI),
    '--texture-filter', 'nearest', '--no-shadows', '--ground', 'none', '--quiet',
  ], { stdio: 'inherit' });
  fs.writeFileSync(path.join(PNG_OUT, `${key}.png`), PNG.sync.write(centre(PNG.sync.read(fs.readFileSync(large)), FILL[key])));
  fs.rmSync(large);
}

const ICONS = { deu, grass, diamond, sword, pickaxe, tnt, creeper, ender, package: parcel };
fs.mkdirSync(OUT, { recursive: true });
fs.mkdirSync(PNG_OUT, { recursive: true });
if (!process.argv.includes('--render')) for (const build of Object.values(ICONS)) build();
if (!fs.existsSync(RENDERER)) {
  console.error(`Renderer fehlt: ${RENDERER}`);
  process.exit(1);
}
for (const key of Object.keys(ICONS)) render(key);
console.log(`${Object.keys(ICONS).length} Bilder in ${PNG_OUT}`);
