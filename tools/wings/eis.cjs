'use strict';
/**
 * DeuClient wing style "eis" (ice wings): generates the Blockbench model and its texture atlas.
 *
 *   node tools/wings/eis.cjs                    -> writes model + texture into the mod resources
 *   node tools/wings/eis.cjs --work <dir>       -> additionally writes a copy (eis.bbmodel + eis.png) into <dir>
 *   node tools/wings/eis.cjs preview <render> [out]
 *                                                -> builds the 256x256 menu preview from a back-view render
 *
 * Model and texture come from the same layout, so every face UV matches its painted island exactly.
 * Conventions: see tools/wings/SPEC.md (right wing, +X outward, +Y up, +Z backwards, 16 units = 1 block).
 */
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { PNG } = require('pngjs');

const ROOT = path.resolve(__dirname, '..', '..');
const RES = path.join(ROOT, 'mod', 'mc26.3', 'src', 'main', 'resources', 'assets', 'deucore');
const OUT_MODEL = path.join(RES, 'models', 'wings', 'eis.bbmodel');
const OUT_TEX = path.join(RES, 'textures', 'cosmetics', 'wings', 'eis.png');
const OUT_PREVIEW = path.join(RES, 'textures', 'cosmetics', 'ui', 'wings_eis.png');

const TEX = 256; // atlas size
const D = 7; // texels per model unit on feather and arm faces

/* ------------------------------------------------------------------ helpers */
const clamp = (x, a = 0, b = 1) => Math.min(b, Math.max(a, x));
const lerp = (a, b, t) => a + (b - a) * t;
const smooth = (a, b, x) => {
  const t = clamp((x - a) / (b - a));
  return t * t * (3 - 2 * t);
};
const hex = (h) => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];
const mix3 = (a, b, t) => [lerp(a[0], b[0], t), lerp(a[1], b[1], t), lerp(a[2], b[2], t)];
const shade = (c, k) => (k >= 0 ? mix3(c, [255, 255, 255], k) : mix3(c, [0, 0, 0], -k));
const r3 = (n) => Math.round(n * 1000) / 1000;
const deg = (r) => (r * 180) / Math.PI;
const rad = (d) => (d * Math.PI) / 180;

function rng32(seed) {
  let a = seed | 0;
  return () => {
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
function hash3(x, y, s) {
  let h = Math.imul(x | 0, 374761393) + Math.imul(y | 0, 668265263) + Math.imul(s | 0, 1442695041);
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  h ^= h >>> 16;
  return (h >>> 0) / 4294967296;
}
function vnoise(x, y, s) {
  const xi = Math.floor(x);
  const yi = Math.floor(y);
  const xf = x - xi;
  const yf = y - yi;
  const u = xf * xf * (3 - 2 * xf);
  const v = yf * yf * (3 - 2 * yf);
  return lerp(
    lerp(hash3(xi, yi, s), hash3(xi + 1, yi, s), u),
    lerp(hash3(xi, yi + 1, s), hash3(xi + 1, yi + 1, s), u),
    v
  );
}
function gradient(stops, t) {
  t = clamp(t);
  for (let i = 1; i < stops.length; i++) {
    if (t <= stops[i][0]) {
      const k = (t - stops[i - 1][0]) / (stops[i][0] - stops[i - 1][0]);
      return mix3(stops[i - 1][1], stops[i][1], k);
    }
  }
  return stops[stops.length - 1][1];
}
function uuidOf(name) {
  const h = crypto.createHash('md5').update('eis:' + name).digest('hex');
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-a${h.slice(17, 20)}-${h.slice(20, 32)}`;
}

/* ------------------------------------------------------------------ palette (from WINGS.eis, refined) */
const WHITE = [255, 255, 255];
const RIM = hex('#e9fbff');
// colour ramps along a feather, root to tip; nearer rows are deeper blue, the back row is frostier
const ICE_FEATHER = {
  P: [[0.0, hex('#2468c8')], [0.3, hex('#34a0ec')], [0.62, hex('#5cc8f8')], [0.85, hex('#8ee4ff')], [1.0, hex('#c4f3ff')]],
  S: [[0.0, hex('#1f5fc0')], [0.35, hex('#2f92e6')], [0.7, hex('#4fbcf4')], [1.0, hex('#86dfff')]],
  C: [[0.0, hex('#1b54b4')], [0.4, hex('#2a85de')], [0.75, hex('#4db5f2')], [1.0, hex('#8fe0ff')]],
};
const ICE_ARM = [
  [0.0, hex('#1b4fa0')],
  [0.4, hex('#2f86d6')],
  [0.75, hex('#6fcdf8')],
  [1.0, hex('#c5f3ff')],
];
const ICE_CRYSTAL = [
  [0.0, hex('#2d8fe0')],
  [0.45, hex('#6fd3fb')],
  [0.8, hex('#c4f5ff')],
  [1.0, hex('#ffffff')],
];

/* ------------------------------------------------------------------ island painters */
// A painter returns fn(u, v, px, py) -> [rgb, alpha 0..1]; v runs top to bottom of the face.
function bladePainter(p) {
  // p: wU, hU (face size in units), t0..t1 (feather parameter at v=0 / v=1), L (feather length), cls, seed, last
  const bandLen = p.cls === 'C' ? 2.0 : 2.6;
  const veinW = Math.min(0.3, p.wU * 0.1);
  return (u, v, px, py) => {
    const t = lerp(p.t0, p.t1, v);
    const x = (u - 0.5) * p.wU;
    const ax = Math.abs(x);
    const yGlobal = t * p.L;
    let col = gradient(ICE_FEATHER[p.cls], clamp(t));
    let a = 0.98;
    // angular barb bands (herringbone), like crystal slabs
    const d = yGlobal - ax * 0.95;
    const bi = Math.floor(d / bandLen);
    const fr = d / bandLen - bi;
    col = shade(col, (hash3(bi, x < 0 ? 1 : 2, p.seed) - 0.5) * 0.3);
    if (fr < 0.08) col = mix3(col, WHITE, 0.4 * (1 - fr / 0.08));
    // light from the left: left facet brighter than right
    col = shade(col, x < 0 ? 0.07 : -0.12);
    // soft frost clouds
    col = mix3(col, WHITE, 0.16 * smooth(0.55, 0.85, vnoise(x * 0.7 + 3, yGlobal * 0.55, p.seed)));
    // diagonal glass glint across the piece
    const gl = Math.abs(((x + yGlobal * 0.5) % 3.4) - 1.7);
    if (gl < 0.18) col = mix3(col, WHITE, 0.3 * (1 - gl / 0.18));
    // central vein
    const vein = Math.max(0, 1 - ax / veinW);
    col = mix3(col, WHITE, vein * 0.7);
    // shingle edges: darker where the piece tucks under its neighbour, bright where it ends
    if (!p.first) col = shade(col, -0.14 * (1 - smooth(0, 0.16, v)));
    if (!p.last) col = mix3(col, RIM, 0.55 * smooth(0.9, 0.985, v));
    // bevelled edge: translucent saturated outer line, bright rim line inside it
    const e = (0.5 - Math.abs(u - 0.5)) * p.wU;
    a *= lerp(0.9, 1, smooth(0.0, 0.8, e));
    if (e < 0.13) {
      col = mix3(col, hex('#3aa4f0'), 0.6);
      a = Math.min(a, 0.82);
    } else if (e < 0.33) {
      col = mix3(col, RIM, 0.85);
      a = Math.max(a, 0.95);
    }
    // frost grain
    col = shade(col, (vnoise(px * 0.8, py * 0.8, p.seed) - 0.5) * 0.1);
    if (hash3(px, py, p.seed + 7) > 0.985) col = shade(col, 0.3);
    // fading tip
    if (p.last) a *= lerp(1, 0.92, smooth(0.3, 1, v));
    return [col, clamp(a)];
  };
}

function armPainter(p) {
  // p: wU, hU, t0 (proximal) .. t1 (distal), seed, dark (side face darker)
  const cracks = [];
  const r = rng32(p.seed * 31 + 5);
  for (let i = 0; i < 4; i++) {
    const x0 = (r() - 0.5) * p.wU * 0.8;
    const y0 = r() * p.hU;
    const len = 1.2 + r() * 2.2;
    const ang = rad(100 + r() * 60);
    cracks.push([x0, y0, x0 + Math.cos(ang) * len, y0 + Math.sin(ang) * len]);
  }
  return (u, v, px, py) => {
    const x = (u - 0.5) * p.wU;
    const ax = Math.abs(x);
    const y = v * p.hU;
    const t = lerp(p.t1, p.t0, v); // v=0 is the distal end
    let col = gradient(ICE_ARM, t);
    // chevron facets pointing to the wrist
    const d = (p.hU - y) + ax * 0.8;
    const bl = 3.4;
    const bi = Math.floor(d / bl);
    const fr = d / bl - bi;
    col = shade(col, (hash3(bi, x < 0 ? 3 : 4, p.seed) - 0.5) * 0.22);
    if (fr < 0.07) col = mix3(col, WHITE, 0.4 * (1 - fr / 0.07));
    col = shade(col, (x < 0 ? 0.06 : -0.06) + (p.dark ? -0.1 : 0.02));
    // centre highlight
    const hi = Math.max(0, 1 - ax / 0.55);
    col = mix3(col, WHITE, hi * 0.45);
    // cracks
    for (const c of cracks) {
      const dx = c[2] - c[0];
      const dy = c[3] - c[1];
      const tt = clamp(((x - c[0]) * dx + (y - c[1]) * dy) / (dx * dx + dy * dy));
      const dist = Math.hypot(x - (c[0] + dx * tt), y - (c[1] + dy * tt));
      if (dist < 0.13) col = mix3(col, WHITE, 0.55 * (1 - dist / 0.13));
    }
    // rim
    const e = (0.5 - Math.abs(u - 0.5)) * p.wU;
    if (e < 0.2) col = mix3(col, RIM, 0.75);
    col = shade(col, (vnoise(px * 0.7, py * 0.7, p.seed) - 0.5) * 0.14);
    if (hash3(px, py, p.seed + 3) > 0.98) col = shade(col, 0.3);
    return [col, e < 0.2 ? 1 : 0.97];
  };
}

function crystalPainter(p) {
  // p: wU, hU, seed, tipAtTop, tipPart
  return (u, v, px, py) => {
    const x = (u - 0.5) * p.wU;
    const ax = Math.abs(x);
    const s = p.tipAtTop ? 1 - v : v; // 0 = base, 1 = tip
    let col = gradient(ICE_CRYSTAL, p.tipPart ? 0.65 + 0.35 * s : s * 0.85);
    col = shade(col, x < 0 ? 0.12 : -0.1);
    // glassy highlight stripe on the lit side
    const stripe = Math.max(0, 1 - Math.abs(x + p.wU * 0.22) / 0.2);
    col = mix3(col, WHITE, stripe * 0.65);
    // inner facet line
    const bl = 2.6;
    const d = s * p.hU - ax * 0.9;
    const fr = d / bl - Math.floor(d / bl);
    if (fr < 0.08) col = mix3(col, WHITE, 0.4);
    const e = (0.5 - Math.abs(u - 0.5)) * p.wU;
    if (e < 0.18) col = mix3(col, RIM, 0.8);
    col = shade(col, (vnoise(px * 0.8, py * 0.8, p.seed) - 0.5) * 0.1);
    const a = lerp(0.95, 0.8, smooth(0.6, 1, s)) * (e < 0.18 ? 1.05 : 1);
    return [col, clamp(a)];
  };
}

function gemPainter(p) {
  return (u, v, px, py) => {
    const x = u - 0.5;
    const y = v - 0.5;
    // four triangular facets around the centre
    let k;
    if (Math.abs(x) > Math.abs(y)) k = x < 0 ? 0.0 : -0.16;
    else k = y < 0 ? 0.22 : -0.08;
    let col = shade(hex('#58c3f4'), k);
    const dc = Math.hypot(x, y);
    col = mix3(col, WHITE, Math.max(0, 1 - dc / 0.22) * 0.8);
    const diag = Math.min(Math.abs(Math.abs(x) - Math.abs(y)), 1) * 8;
    if (diag < 0.5) col = mix3(col, WHITE, 0.5 * (1 - diag / 0.5));
    if (Math.min(u, 1 - u, v, 1 - v) < 0.07) col = mix3(col, RIM, 0.8);
    col = shade(col, (vnoise(px * 0.9, py * 0.9, p.seed) - 0.5) * 0.1);
    return [col, 0.95];
  };
}

function stripPainter() {
  return (u, v) => [gradient([[0, hex('#8fe3ff')], [0.6, hex('#d4f8ff')], [1, hex('#ffffff')]], v), lerp(0.95, 0.78, v)];
}
function capPainter() {
  return () => [hex('#f4feff'), 0.92];
}

/* ------------------------------------------------------------------ atlas */
const islands = [];
const islandByKey = new Map();
function reg(key, w, h, painter, opts = {}) {
  if (islandByKey.has(key)) throw new Error('duplicate island ' + key);
  const isl = { key, w: Math.max(2, Math.round(w)), h: Math.max(2, Math.round(h)), painter, stars: opts.stars || [], seed: opts.seed || 1 };
  islands.push(isl);
  islandByKey.set(key, isl);
  return key;
}

function paintIsland(isl) {
  const { w, h } = isl;
  const buf = new Uint8Array(w * h * 4);
  const SS = 3;
  for (let py = 0; py < h; py++) {
    for (let px = 0; px < w; px++) {
      let r = 0, g = 0, b = 0, a = 0;
      for (let sy = 0; sy < SS; sy++) {
        for (let sx = 0; sx < SS; sx++) {
          const u = (px + (sx + 0.5) / SS) / w;
          const v = (py + (sy + 0.5) / SS) / h;
          const [c, al] = isl.painter(u, v, px, py);
          r += c[0] * al; g += c[1] * al; b += c[2] * al; a += al;
        }
      }
      const i = (py * w + px) * 4;
      if (a > 0) {
        buf[i] = Math.round(r / a);
        buf[i + 1] = Math.round(g / a);
        buf[i + 2] = Math.round(b / a);
        buf[i + 3] = Math.round(clamp(a / (SS * SS)) * 255);
      }
    }
  }
  // sparkle stars (4-point glints with a soft glow)
  const rnd = rng32(isl.seed * 977 + 13);
  for (const st of isl.stars) {
    const rr = st.r;
    const cx = Math.round(rr + 1 + rnd() * Math.max(0, w - 2 * rr - 3));
    const cy = Math.round(rr + 1 + rnd() * Math.max(0, h - 2 * rr - 3));
    for (let y = cy - rr; y <= cy + rr; y++) {
      for (let x = cx - rr; x <= cx + rr; x++) {
        if (x < 0 || y < 0 || x >= w || y >= h) continue;
        const dx = x - cx;
        const dy = y - cy;
        const arm1 = Math.max(0, 1 - Math.abs(dx) / (rr + 0.5)) ** 1.6 * Math.exp(-(dy * dy) / 0.3);
        const arm2 = Math.max(0, 1 - Math.abs(dy) / (rr + 0.5)) ** 1.6 * Math.exp(-(dx * dx) / 0.3);
        const core = Math.exp(-(dx * dx + dy * dy) / (rr * rr * 0.16)) * 0.75;
        const k = clamp(Math.max(arm1, arm2) + core) * (st.k || 1);
        if (k <= 0.02) continue;
        const i = (y * w + x) * 4;
        buf[i] = Math.round(lerp(buf[i], 255, k));
        buf[i + 1] = Math.round(lerp(buf[i + 1], 255, k));
        buf[i + 2] = Math.round(lerp(buf[i + 2], 255, k));
        buf[i + 3] = Math.max(buf[i + 3], Math.round(k * 255));
      }
    }
  }
  isl.buf = buf;
}

// MaxRects packing (best short side fit), 2 texel gap between islands
function packAtlas() {
  const pad = 2;
  let free = [{ x: 0, y: 0, w: TEX, h: TEX }];
  const order = islands.slice().sort((a, b) => b.w * b.h - a.w * a.h || b.h - a.h);
  let used = 0, bottom = 0;
  for (const isl of order) {
    const w = isl.w + pad;
    const h = isl.h + pad;
    let best = null;
    for (const f of free) {
      if (w <= f.w && h <= f.h) {
        const score = Math.min(f.w - w, f.h - h) * 1000 + Math.max(f.w - w, f.h - h) + f.y * 0.01;
        if (!best || score < best.score) best = { f, score };
      }
    }
    if (!best) throw new Error(`atlas overflow while placing ${isl.key} (${isl.w}x${isl.h})`);
    isl.x = best.f.x + 1;
    isl.y = best.f.y + 1;
    const used_r = { x: best.f.x, y: best.f.y, w, h };
    const next = [];
    for (const f of free) {
      if (used_r.x >= f.x + f.w || used_r.x + used_r.w <= f.x || used_r.y >= f.y + f.h || used_r.y + used_r.h <= f.y) {
        next.push(f);
        continue;
      }
      if (used_r.x > f.x) next.push({ x: f.x, y: f.y, w: used_r.x - f.x, h: f.h });
      if (used_r.x + used_r.w < f.x + f.w) next.push({ x: used_r.x + used_r.w, y: f.y, w: f.x + f.w - (used_r.x + used_r.w), h: f.h });
      if (used_r.y > f.y) next.push({ x: f.x, y: f.y, w: f.w, h: used_r.y - f.y });
      if (used_r.y + used_r.h < f.y + f.h) next.push({ x: f.x, y: used_r.y + used_r.h, w: f.w, h: f.y + f.h - (used_r.y + used_r.h) });
    }
    free = next.filter((a, i) => !next.some((b, j) => i !== j && a.x >= b.x && a.y >= b.y && a.x + a.w <= b.x + b.w && a.y + a.h <= b.y + b.h && (i > j || a.x !== b.x || a.y !== b.y || a.w !== b.w || a.h !== b.h)));
    used += isl.w * isl.h;
    bottom = Math.max(bottom, isl.y + isl.h + 1);
  }
  return { bottom, used };
}

function buildAtlas() {
  const png = new PNG({ width: TEX, height: TEX });
  png.data.fill(0);
  for (const isl of islands) {
    paintIsland(isl);
    for (let py = -1; py <= isl.h; py++) {
      for (let px = -1; px <= isl.w; px++) {
        const inside = px >= 0 && py >= 0 && px < isl.w && py < isl.h;
        const sx = clamp(px, 0, isl.w - 1);
        const sy = clamp(py, 0, isl.h - 1);
        const gx = isl.x + px;
        const gy = isl.y + py;
        if (gx < 0 || gy < 0 || gx >= TEX || gy >= TEX) continue;
        const si = (sy * isl.w + sx) * 4;
        const di = (gy * TEX + gx) * 4;
        // bleed the border colour one texel outwards (alpha 0 there keeps edges clean for nearest sampling)
        png.data[di] = isl.buf[si];
        png.data[di + 1] = isl.buf[si + 1];
        png.data[di + 2] = isl.buf[si + 2];
        png.data[di + 3] = inside ? isl.buf[si + 3] : 0;
      }
    }
  }
  return png;
}

/* ------------------------------------------------------------------ face specs */
// A face spec is: null (face not rendered) | island key | {strip: faceLengthUnits} | {cap: faceWidthUnits}
function uvOfFace(spec) {
  if (spec === null || spec === undefined) return null;
  if (typeof spec === 'string') {
    const i = islandByKey.get(spec);
    if (!i) throw new Error('unknown island ' + spec);
    return [i.x, i.y, i.x + i.w, i.y + i.h];
  }
  if (spec.strip !== undefined) {
    const i = islandByKey.get('strip');
    const len = Math.min(spec.strip * D, i.h);
    return [i.x, i.y, i.x + 2, i.y + len];
  }
  if (spec.cap !== undefined) {
    const i = islandByKey.get('cap');
    return [i.x, i.y, i.x + Math.min(Math.max(2, Math.round(spec.cap * D)), i.w), i.y + 2];
  }
  throw new Error('bad face spec');
}

/* ------------------------------------------------------------------ model data structures */
const cubes = []; // flat list: {name, from, to, origin, rotation, faces, parent(group node)}
function mkGroup(name, origin, rotation = [0, 0, 0]) {
  return { name, origin, rotation, groups: [], cubes: [] };
}
function addCube(parent, c) {
  const cube = {
    rotation: [0, 0, 0],
    faces: { north: null, east: null, south: null, west: null, up: null, down: null },
    ...c,
  };
  cube.origin = cube.origin || [(c.from[0] + c.to[0]) / 2, (c.from[1] + c.to[1]) / 2, (c.from[2] + c.to[2]) / 2];
  parent.cubes.push(cube);
  cubes.push(cube);
  return cube;
}

/* ------------------------------------------------------------------ wing design */
const ZARM = 4.1; // z of the arm centre line
const ARM = { S: [0.5, 0], E: [7.5, 11.5], W: [17.5, 18], T: [27.5, 20.5] };
const armSegs = [
  { a: ARM.S, b: ARM.E },
  { a: ARM.E, b: ARM.W },
  { a: ARM.W, b: ARM.T },
].map((s) => {
  const dx = s.b[0] - s.a[0];
  const dy = s.b[1] - s.a[1];
  const len = Math.hypot(dx, dy);
  return { ...s, len, alpha: deg(Math.atan2(dx, dy)), dir: [dx / len, dy / len] };
});
const armTotal = armSegs.reduce((s, g) => s + g.len, 0);
function armAt(s) {
  let rest = clamp(s, 0, armTotal);
  for (let i = 0; i < 3; i++) {
    const g = armSegs[i];
    if (rest <= g.len || i === 2) {
      return { x: g.a[0] + g.dir[0] * rest, y: g.a[1] + g.dir[1] * rest, seg: i };
    }
    rest -= g.len;
  }
  return null;
}

// feather classes: piece length fractions and width fractions (stepped lanceolate shard), kink angles
const CLS = {
  P: { fr: [0.34, 0.28, 0.22, 0.16], wf: [1, 0.86, 0.62, 0.3], kink: [0, 5, -4, 7], rows: [0.6, 1.0] },
  S: { fr: [0.4, 0.33, 0.27], wf: [1, 0.78, 0.42], kink: [0, 5, -5], rows: [1.7, 2.1] },
  C: { fr: [0.58, 0.42], wf: [1, 0.6], kink: [0, 6], rows: [2.8, 3.2] },
};
const PIECE = ['A', 'B', 'C', 'D'];

// primaries: arm position s, tip target (x, y)
const P_TABLE = [
  [11.0, 4.0, -8.0],
  [14.0, 9.0, -11.5],
  [17.0, 14.5, -13.5],
  [20.0, 20.0, -13.0],
  [23.0, 25.0, -9.5],
  [26.0, 29.5, -4.0],
  [28.6, 33.0, 1.5],
  [30.9, 35.3, 7.5],
  [33.0, 35.8, 13.0],
];
const feathers = [];
const prim = P_TABLE.map(([s, tx, ty]) => {
  const r = armAt(s);
  const dx = tx - r.x;
  const dy = ty - r.y;
  return { s, x: r.x, y: r.y, seg: r.seg, phi: deg(Math.atan2(dx, -dy)), L: Math.hypot(dx, dy) };
});
prim.forEach((p, i) => {
  feathers.push({ id: 'p' + String(i + 1).padStart(2, '0'), cls: 'P', idx: i, seg: p.seg, rx: p.x, ry: p.y, L: p.L, w: clamp(p.L * 0.21, 3.6, 5.3), phi: p.phi });
});
// secondaries: inner extras + one between each pair of primaries
const secDefs = [];
[[3.5, -8, 7.5], [7.0, -9, 10.5]].forEach(([s, phi, L]) => {
  const r = armAt(s);
  secDefs.push({ s, x: r.x, y: r.y, seg: r.seg, phi, L });
});
for (let i = 0; i < prim.length - 1; i++) {
  const a = prim[i];
  const b = prim[i + 1];
  const s = (a.s + b.s) / 2;
  const r = armAt(s);
  secDefs.push({ s, x: r.x, y: r.y, seg: r.seg, phi: (a.phi + b.phi) / 2, L: (0.66 * (a.L + b.L)) / 2 });
}
secDefs.forEach((p, i) => {
  feathers.push({ id: 's' + String(i + 1).padStart(2, '0'), cls: 'S', idx: i, seg: p.seg, rx: p.x, ry: p.y, L: p.L, w: clamp(p.L * 0.27, 3.4, 4.6), phi: p.phi });
});
// coverts: short shards along the whole arm
function phiAt(s) {
  const pts = [[0, -10]].concat(prim.map((p) => [p.s, p.phi]));
  for (let i = 1; i < pts.length; i++) {
    if (s <= pts[i][0]) return lerp(pts[i - 1][1], pts[i][1], (s - pts[i - 1][0]) / (pts[i][0] - pts[i - 1][0]));
  }
  return pts[pts.length - 1][1];
}
const COVERT_L = [8, 9, 8.5, 9.5, 8, 9, 8.5, 9.5, 8, 8.5, 8, 7.5];
for (let i = 0; i < 12; i++) {
  const s = 3 + i * 2.8;
  const r = armAt(s);
  feathers.push({ id: 'c' + String(i + 1).padStart(2, '0'), cls: 'C', idx: i, seg: r.seg, rx: r.x, ry: r.y, L: COVERT_L[i], w: 3.5, phi: phiAt(s) + (i % 2 ? 3 : -2) });
}

const TILT = -5; // feathers sweep back slightly (negative X rotation moves the tip towards +Z)

/* ------------------------------------------------------------------ islands for the feathers */
const VARIANTS = 3;
const ROOT_OVER = 0.8; // piece A reaches this far above the root (hidden under the arm)
const JOIN = 0.4; // overlap of consecutive pieces
const classInfo = {};
['P', 'S', 'C'].forEach((cls) => {
  const list = feathers.filter((f) => f.cls === cls);
  const L = list.reduce((s, f) => s + f.L, 0) / list.length;
  const w = list.reduce((s, f) => s + f.w, 0) / list.length;
  const c = CLS[cls];
  classInfo[cls] = { L, w };
  let acc = 0;
  c.fr.forEach((fr, i) => {
    const t0 = acc;
    acc += fr;
    const wU = w * c.wf[i];
    const hU = fr * L + (i === 0 ? ROOT_OVER : JOIN);
    for (let v = 0; v < VARIANTS; v++) {
      const seed = (cls.charCodeAt(0) * 13 + v * 7 + i * 3) | 0;
      const stars = i === 0 ? (cls === 'P' ? [{ r: 4 }, { r: 2 }] : [{ r: 3 }]) : i === 1 && cls !== 'C' ? [{ r: 2 }] : [];
      reg(`${PIECE[i]}_${cls}${v}`, wU * D, hU * D, bladePainter({ wU, hU, t0, t1: acc, L, cls, seed, first: i === 0, last: i === c.fr.length - 1 }), { stars, seed });
    }
  });
});
reg('strip', 4, 150, stripPainter());
reg('cap', 12, 4, capPainter());
reg('gem', 20, 20, gemPainter({ seed: 5 }), { seed: 5, stars: [{ r: 3 }] });

/* ------------------------------------------------------------------ build the group tree */
const wing = mkGroup('wing', [0, 0, 0]);
const seg1 = mkGroup('seg1', [ARM.S[0], ARM.S[1], ZARM]);
const seg2 = mkGroup('seg2', [ARM.E[0], ARM.E[1], ZARM]);
const seg3 = mkGroup('seg3', [ARM.W[0], ARM.W[1], ZARM]);
wing.groups.push(seg1);
seg1.groups.push(seg2);
seg2.groups.push(seg3);
const segNodes = [seg1, seg2, seg3];

// arm prisms (diamond cross section: cube twisted 45 degrees about its own axis)
const armT = [3.0, 2.6, 2.2];
const armRange = [[0, 0.38], [0.38, 0.72], [0.72, 1]];
armSegs.forEach((g, i) => {
  const len = g.len + 1.6;
  const mx = (g.a[0] + g.b[0]) / 2;
  const my = (g.a[1] + g.b[1]) / 2;
  const t = armT[i];
  const ka = reg(`arm${i}a`, t * D, len * D, armPainter({ wU: t, hU: len, t0: armRange[i][0], t1: armRange[i][1], seed: 40 + i, dark: false }), { seed: 40 + i, stars: [{ r: 3 }, { r: 2 }] });
  const kb = reg(`arm${i}b`, t * D, len * D, armPainter({ wU: t, hU: len, t0: armRange[i][0], t1: armRange[i][1], seed: 50 + i, dark: true }), { seed: 50 + i, stars: [{ r: 2 }] });
  addCube(segNodes[i], {
    name: `arm${i + 1}`,
    from: [mx - t / 2, my - len / 2, ZARM - t / 2],
    to: [mx + t / 2, my + len / 2, ZARM + t / 2],
    origin: [mx, my, ZARM],
    rotation: [0, 45, -g.alpha],
    faces: { north: ka, south: ka, east: kb, west: kb, up: i === 2 ? null : { cap: t }, down: i === 0 ? { cap: t } : null },
  });
});

// crystal spikes (stepped needle: three cubes, each narrower) --------------
let spikeNo = 0;
const SPIKE_FR = [0.48, 0.32, 0.2];
const SPIKE_WF = [1, 0.62, 0.28];
function spike(parent, B, alpha, tilt, len, thick) {
  spikeNo++;
  const n = `spk${spikeNo}`;
  let y = 0;
  SPIKE_FR.forEach((fr, i) => {
    const pl = fr * len;
    const w = thick * SPIKE_WF[i];
    const over = i === 0 ? 0 : 0.4;
    const key = `${n}${i}`;
    reg(key, w * D, (pl + over) * D, crystalPainter({ wU: w, hU: pl + over, seed: 120 + spikeNo * 3 + i, tipAtTop: true, tipPart: i > 0 }), { seed: 120 + spikeNo * 3 + i, stars: i === 0 && len > 6 ? [{ r: 2 }] : [] });
    addCube(parent, {
      name: `${n}_${i}`,
      from: [B[0] - w / 2, B[1] + y - over, B[2] - w / 2],
      to: [B[0] + w / 2, B[1] + y + pl, B[2] + w / 2],
      origin: [B[0], B[1], B[2]],
      rotation: [tilt, 0, -alpha],
      faces: { north: key, south: key, east: key, west: key, up: i === SPIKE_FR.length - 1 ? { cap: w } : null, down: null },
    });
    y += pl;
  });
}
function gem(parent, name, c, size, rot) {
  addCube(parent, {
    name,
    from: [c[0] - size / 2, c[1] - size / 2, c[2] - size / 2],
    to: [c[0] + size / 2, c[1] + size / 2, c[2] + size / 2],
    origin: [c[0], c[1], c[2]],
    rotation: rot,
    faces: { north: 'gem', south: 'gem', east: 'gem', west: 'gem', up: 'gem', down: 'gem' },
  });
}
// shoulder anchor
gem(seg1, 'gem_shoulder', [ARM.S[0] + 0.4, ARM.S[1] + 0.2, ZARM + 0.3], 3.2, [30, 45, 20]);
// crystals on the leading edge of the upper arm and forearm
spike(seg1, [3.0, 6.6, ZARM], -52, 22, 4.6, 1.9);
spike(seg2, [11.4, 15.6, ZARM], -34, 22, 4.8, 2.0);
// elbow cluster
gem(seg2, 'gem_elbow', [ARM.E[0], ARM.E[1], ZARM + 0.5], 4.2, [35, 45, 25]);
spike(seg2, [ARM.E[0] - 0.4, ARM.E[1] + 1.2, ZARM], -22, 12, 6.5, 2.4);
spike(seg2, [ARM.E[0] + 0.6, ARM.E[1] + 1.4, ZARM], 6, 24, 8.5, 2.7);
spike(seg2, [ARM.E[0] + 1.8, ARM.E[1] + 0.8, ZARM], 38, 30, 5.2, 2.1);
// wrist cluster
gem(seg3, 'gem_wrist', [ARM.W[0], ARM.W[1], ZARM + 0.5], 4.0, [30, 40, 35]);
spike(seg3, [ARM.W[0] - 0.6, ARM.W[1] + 1.2, ZARM], -8, 14, 5.6, 2.4);
spike(seg3, [ARM.W[0] + 1.0, ARM.W[1] + 1.0, ZARM], 22, 26, 7.0, 2.6);
spike(seg3, [ARM.W[0] + 2.6, ARM.W[1] + 0.8, ZARM], 52, 30, 5.0, 2.0);
// tip: long blade continuing the arm
spike(seg3, [ARM.T[0] - 1.5, ARM.T[1] - 0.3, ZARM], 82, 4, 7.5, 2.2);

// feathers -----------------------------------------------------------------
function buildFeather(f) {
  const c = CLS[f.cls];
  const row = c.rows[f.idx % 2];
  const g = mkGroup('f_' + f.id, [r3(f.rx), r3(f.ry), r3(row + 0.2)], [TILT, 0, r3(f.phi)]);
  segNodes[f.seg].groups.push(g);
  const v = f.idx % VARIANTS;
  const sgn = f.idx % 2 ? 1 : -1;
  let jx = f.rx;
  let jy = f.ry; // joint (top of the current piece)
  c.fr.forEach((fr, i) => {
    const len = fr * f.L;
    const w = f.w * c.wf[i];
    const theta = sgn * c.kink[i];
    const z = row - (i % 2 ? 0.2 : 0);
    const key = `${PIECE[i]}_${f.cls}${v}`;
    const top = i === 0 ? ROOT_OVER : JOIN;
    addCube(g, {
      name: `${f.id}_${PIECE[i].toLowerCase()}`,
      from: [jx - w / 2, jy - len, z],
      to: [jx + w / 2, jy + top, z + 0.4],
      origin: [jx, jy, z + 0.2],
      rotation: [0, 0, r3(theta)],
      faces: {
        north: key,
        south: key,
        east: { strip: len + top },
        west: { strip: len + top },
        up: null,
        down: i === c.fr.length - 1 ? { cap: w } : null,
      },
    });
    jx += len * Math.sin(rad(theta));
    jy -= len * Math.cos(rad(theta));
  });
}
feathers.forEach(buildFeather);

/* ------------------------------------------------------------------ transforms (bounds, sanity) */
function rotMat(rx, ry, rz) {
  const a = rad(rx), b = rad(ry), c = rad(rz);
  const cx = Math.cos(a), sx = Math.sin(a), cy = Math.cos(b), sy = Math.sin(b), cz = Math.cos(c), sz = Math.sin(c);
  return [
    [cz * cy, cz * sy * sx - sz * cx, cz * sy * cx + sz * sx],
    [sz * cy, sz * sy * sx + cz * cx, sz * sy * cx - cz * sx],
    [-sy, cy * sx, cy * cx],
  ];
}
function rotAbout(p, o, rot) {
  const m = rotMat(rot[0], rot[1], rot[2]);
  const d = [p[0] - o[0], p[1] - o[1], p[2] - o[2]];
  return [
    o[0] + m[0][0] * d[0] + m[0][1] * d[1] + m[0][2] * d[2],
    o[1] + m[1][0] * d[0] + m[1][1] * d[1] + m[1][2] * d[2],
    o[2] + m[2][0] * d[0] + m[2][1] * d[1] + m[2][2] * d[2],
  ];
}
function worldBounds() {
  const min = [1e9, 1e9, 1e9];
  const max = [-1e9, -1e9, -1e9];
  const walk = (node, chain) => {
    const here = chain.concat([node]);
    node.cubes.forEach((c) => {
      for (let i = 0; i < 8; i++) {
        let p = [i & 1 ? c.to[0] : c.from[0], i & 2 ? c.to[1] : c.from[1], i & 4 ? c.to[2] : c.from[2]];
        p = rotAbout(p, c.origin, c.rotation);
        for (let k = here.length - 1; k >= 0; k--) p = rotAbout(p, here[k].origin, here[k].rotation);
        for (let a = 0; a < 3; a++) {
          min[a] = Math.min(min[a], p[a]);
          max[a] = Math.max(max[a], p[a]);
        }
      }
    });
    node.groups.forEach((g) => walk(g, here));
  };
  walk(wing, []);
  return { min, max };
}

/* ------------------------------------------------------------------ output */
function faceJson(spec) {
  const uv = uvOfFace(spec);
  if (!uv) return { uv: [0, 0, 0, 0], texture: null };
  return { uv: uv.map((n) => r3(n)), texture: 0 };
}
function buildModel(pngBuffer) {
  const elements = [];
  const groupsOut = [];
  const outliner = [];
  const emitGroup = (node) => {
    const uuid = uuidOf('g:' + node.name);
    groupsOut.push({
      name: node.name,
      uuid,
      origin: node.origin.map(r3),
      rotation: node.rotation.map(r3),
      export: true,
      visibility: true,
      mirror_uv: false,
      reset: false,
      color: 0,
      autouv: 0,
      shade: true,
      locked: false,
    });
    const children = [];
    node.cubes.forEach((c) => {
      const cu = uuidOf('c:' + c.name);
      elements.push({
        name: c.name,
        box_uv: false,
        rescale: false,
        locked: false,
        light_emission: 0,
        render_order: 'default',
        allow_mirror_modeling: true,
        from: c.from.map(r3),
        to: c.to.map(r3),
        autouv: 0,
        color: 0,
        origin: c.origin.map(r3),
        rotation: c.rotation.map(r3),
        faces: {
          north: faceJson(c.faces.north),
          east: faceJson(c.faces.east),
          south: faceJson(c.faces.south),
          west: faceJson(c.faces.west),
          up: faceJson(c.faces.up),
          down: faceJson(c.faces.down),
        },
        type: 'cube',
        uuid: cu,
      });
      children.push(cu);
    });
    node.groups.forEach((g) => children.push(emitGroup(g)));
    return { uuid, isOpen: false, children };
  };
  outliner.push(emitGroup(wing));
  return {
    meta: { format_version: '5.0', model_format: 'free', box_uv: false },
    name: 'eis',
    model_identifier: '',
    visible_box: [1, 1, 0],
    resolution: { width: TEX, height: TEX },
    elements,
    groups: groupsOut,
    outliner,
    textures: [
      {
        path: '',
        name: 'eis.png',
        folder: '',
        namespace: '',
        id: '0',
        width: TEX,
        height: TEX,
        uv_width: TEX,
        uv_height: TEX,
        particle: false,
        use_as_default: false,
        layers_enabled: false,
        sync_to_project: '',
        render_mode: 'default',
        render_sides: 'auto',
        frame_time: 1,
        frame_order_type: 'loop',
        frame_order: '',
        frame_interpolate: false,
        visible: true,
        internal: true,
        saved: false,
        uuid: uuidOf('tex'),
        relative_path: '',
        source: 'data:image/png;base64,' + pngBuffer.toString('base64'),
      },
    ],
    variable_placeholders: '',
    variable_placeholder_buttons: [],
    timeline_setups: [],
    unhandled_root_fields: {},
    ai_used: true,
    ai_agents: 'local-agent-mode-bbmodel',
  };
}

function build(workDir) {
  const { bottom, used } = packAtlas();
  const png = buildAtlas();
  const buf = PNG.sync.write(png);
  const model = buildModel(buf);
  fs.mkdirSync(path.dirname(OUT_TEX), { recursive: true });
  fs.mkdirSync(path.dirname(OUT_MODEL), { recursive: true });
  fs.writeFileSync(OUT_TEX, buf);
  fs.writeFileSync(OUT_MODEL, JSON.stringify(model, null, '\t'));
  if (workDir) {
    fs.mkdirSync(workDir, { recursive: true });
    fs.writeFileSync(path.join(workDir, 'eis.png'), buf);
    fs.writeFileSync(path.join(workDir, 'eis.bbmodel'), JSON.stringify(model, null, '\t'));
  }
  const b = worldBounds();
  console.log(`cubes: ${cubes.length}  feathers: ${feathers.length}  islands: ${islands.length}`);
  console.log(`atlas: ${TEX}x${TEX}, rows used ${bottom}, fill ${((used / (TEX * TEX)) * 100).toFixed(1)}%`);
  console.log('bounds min', b.min.map(r3).join(' '), 'max', b.max.map(r3).join(' '));
  console.log('wrote', path.relative(ROOT, OUT_MODEL), path.relative(ROOT, OUT_TEX));
}

/* ------------------------------------------------------------------ menu preview */
// Takes a transparent back-view render (orthographic, whole wing) and places it per SPEC.md:
// 256x256, shoulder root at about (12 %, 55 %), wing fills the frame with about 5 % margin.
function preview(renderPath, outPath) {
  const src = PNG.sync.read(fs.readFileSync(renderPath));
  const { width: W, height: H, data } = src;
  let x0 = W, y0 = H, x1 = -1, y1 = -1;
  for (let y = 0; y < H; y++) {
    for (let x = 0; x < W; x++) {
      if (data[(y * W + x) * 4 + 3] > 8) {
        x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y);
      }
    }
  }
  if (x1 < 0) throw new Error('render is empty');
  const b = worldBounds();
  // the render shows the wing viewed from behind: +X to the right, +Y up
  const spanX = b.max[0] - b.min[0];
  const spanY = b.max[1] - b.min[1];
  const pxPerUnit = ((x1 - x0 + 1) / spanX + (y1 - y0 + 1) / spanY) / 2; // render scale (px per unit)
  const rootSrcX = x0 + (0 - b.min[0]) * pxPerUnit;
  const rootSrcY = y0 + (b.max[1] - 0) * pxPerUnit;
  const S = 256;
  const margin = 0.05 * S;
  const rootDstX = 0.12 * S;
  const rootDstY = 0.57 * S;
  // largest scale keeping every extent inside the margins with the root on its anchor
  const scale = Math.min(
    (S - margin - rootDstX) / (b.max[0] * 1),
    (rootDstY - margin) / b.max[1],
    (S - margin - rootDstY) / -b.min[1]
  );
  const out = new PNG({ width: S, height: S });
  out.data.fill(0);
  const k = scale / pxPerUnit; // dst px per src px
  for (let dy = 0; dy < S; dy++) {
    for (let dx = 0; dx < S; dx++) {
      // box filter over the source footprint
      const sx0 = rootSrcX + (dx - rootDstX) / k;
      const sy0 = rootSrcY + (dy - rootDstY) / k;
      const sx1 = sx0 + 1 / k;
      const sy1 = sy0 + 1 / k;
      let r = 0, g = 0, bl = 0, a = 0, wsum = 0;
      for (let yy = Math.floor(sy0); yy < Math.ceil(sy1); yy++) {
        for (let xx = Math.floor(sx0); xx < Math.ceil(sx1); xx++) {
          if (xx < 0 || yy < 0 || xx >= W || yy >= H) { wsum += 1; continue; }
          const wx = Math.min(xx + 1, sx1) - Math.max(xx, sx0);
          const wy = Math.min(yy + 1, sy1) - Math.max(yy, sy0);
          const wgt = Math.max(0, wx) * Math.max(0, wy);
          const i = (yy * W + xx) * 4;
          const al = data[i + 3] / 255;
          r += data[i] * al * wgt; g += data[i + 1] * al * wgt; bl += data[i + 2] * al * wgt; a += al * wgt; wsum += wgt;
        }
      }
      if (wsum > 0 && a > 0) {
        const o = (dy * S + dx) * 4;
        // the flat-lit render is about 20 % darker than the in-game (minimum light) look, lift it back
        out.data[o] = Math.min(255, Math.round((r / a) * 1.2));
        out.data[o + 1] = Math.min(255, Math.round((g / a) * 1.2));
        out.data[o + 2] = Math.min(255, Math.round((bl / a) * 1.15));
        out.data[o + 3] = Math.round(clamp(a / wsum) * 255);
      }
    }
  }
  fs.mkdirSync(path.dirname(outPath), { recursive: true });
  fs.writeFileSync(outPath, PNG.sync.write(out));
  console.log(`preview written: ${outPath} (scale ${scale.toFixed(2)} px/unit, render ${pxPerUnit.toFixed(2)} px/unit)`);
}

/* ------------------------------------------------------------------ cli */
const args = process.argv.slice(2);
if (args[0] === 'preview') {
  preview(path.resolve(args[1]), path.resolve(args[2] || OUT_PREVIEW));
} else {
  const wi = args.indexOf('--work');
  build(wi >= 0 ? path.resolve(args[wi + 1]) : null);
}
