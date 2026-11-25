'use strict';
/**
 * DeuClient wing style "smaragd" (emerald crystal wings).
 *
 * Generates, from ONE layout, both
 *   - the texture atlas  (256x256 RGBA PNG)  and
 *   - the element list for the Blockbench model (bbmodel_edit operations as JSON),
 * so texture islands and per-face UVs always match exactly.
 *
 * Usage (from the repo root):
 *   node tools/wings/smaragd.cjs [--ops <ops.json>] [--tex <atlas.png>] [--debug <silhouette.png>]
 *
 * Coordinates: Blockbench units, origin = shoulder, +X outward, +Y up, +Z backwards.
 * Only the RIGHT wing is authored, the renderer mirrors it.
 */
const fs = require('fs');
const os = require('os');
const path = require('path');
const { PNG } = require('pngjs');
const { Resvg } = require('@resvg/resvg-js');

const ROOT = path.resolve(__dirname, '..', '..');
const argv = process.argv.slice(2);
const arg = (k, d) => { const i = argv.indexOf(k); return i >= 0 ? argv[i + 1] : d; };
const TEX_OUT = arg('--tex', path.join(ROOT, 'mod/mc26.3/src/main/resources/assets/deucore/textures/cosmetics/wings/smaragd.png'));
const OPS_OUT = arg('--ops', path.join(os.tmpdir(), 'smaragd-ops.json'));
const DEBUG_OUT = arg('--debug', '');

const TEX = 256;      // atlas resolution (project texture size)
const D = 4;          // texels per model unit

/* ------------------------------------------------------------------ helpers */
const clamp = (x, a = 0, b = 1) => Math.min(b, Math.max(a, x));
const lerp = (a, b, t) => a + (b - a) * t;
const rad = (d) => (d * Math.PI) / 180;
const deg = (r) => (r * 180) / Math.PI;
const r3 = (x) => Math.round(x * 1000) / 1000;
const hexc = (h) => [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16));
const hash = (x, y, s) => {
  let h = (x * 374761393 + y * 668265263 + s * 1274126177) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  return ((h ^ (h >>> 16)) >>> 0) / 4294967296;
};

// emerald ramp: 0 = deepest emerald, 1 = white-hot mint (keeps the spirit of WINGS.smaragd and the emblem)
const STOPS = ['#02150a', '#04301a', '#07522b', '#0c7a3e', '#12a352', '#1fd063', '#54ec8f', '#a6fac8', '#f2fff7'].map(hexc);
function ramp(t) {
  const x = clamp(t) * (STOPS.length - 1);
  const i = Math.min(STOPS.length - 2, Math.floor(x));
  const f = x - i;
  return STOPS[i].map((c, k) => Math.round(lerp(c, STOPS[i + 1][k], f)));
}
const col = (t, a = 255) => { const c = ramp(t); return [c[0], c[1], c[2], Math.round(clamp(a, 0, 255))]; };

/* ------------------------------------------------------------ island system */
// painter = { w, h, shape(u,v,px,py)->bool (optional), color(u,v,px,py)->[r,g,b,a] }
const islands = new Map(); // key -> {key,w,h,painter,x,y}
function isl(key, w, h, painter) {
  if (!islands.has(key)) islands.set(key, { key, w: Math.max(1, Math.round(w)), h: Math.max(1, Math.round(h)), painter, x: 0, y: 0 });
  return key;
}
const px = (units, d = D) => Math.max(2, Math.round(units * d));

// MaxRects (best short side fit) packer, 1px gap between islands
function packIslands() {
  const list = [...islands.values()].sort((a, b) => Math.max(b.w, b.h) - Math.max(a.w, a.h) || b.w * b.h - a.w * a.h);
  let free = [{ x: 0, y: 0, w: TEX + 1, h: TEX + 1 }];
  const hit = (a, b) => !(b.x >= a.x + a.w || b.x + b.w <= a.x || b.y >= a.y + a.h || b.y + b.h <= a.y);
  let used = 0;
  for (const it of list) {
    const iw = it.w + 1, ih = it.h + 1;
    let best = null, bs = Infinity, bl = Infinity;
    for (const f of free) {
      if (f.w < iw || f.h < ih) continue;
      const ss = Math.min(f.w - iw, f.h - ih), ls = Math.max(f.w - iw, f.h - ih);
      if (ss < bs || (ss === bs && ls < bl)) { best = f; bs = ss; bl = ls; }
    }
    if (!best) throw new Error(`atlas overflow while placing ${it.key} (${it.w}x${it.h})`);
    it.x = best.x; it.y = best.y;
    used = Math.max(used, it.y + it.h);
    const placed = { x: best.x, y: best.y, w: iw, h: ih };
    const next = [];
    for (const f of free) {
      if (!hit(f, placed)) { next.push(f); continue; }
      if (placed.x > f.x) next.push({ x: f.x, y: f.y, w: placed.x - f.x, h: f.h });
      if (placed.x + placed.w < f.x + f.w) next.push({ x: placed.x + placed.w, y: f.y, w: f.x + f.w - placed.x - placed.w, h: f.h });
      if (placed.y > f.y) next.push({ x: f.x, y: f.y, w: f.w, h: placed.y - f.y });
      if (placed.y + placed.h < f.y + f.h) next.push({ x: f.x, y: placed.y + placed.h, w: f.w, h: f.y + f.h - placed.y - placed.h });
    }
    // drop free rects contained in another
    free = next.filter((a, i) => !next.some((b, j) => j !== i && a.x >= b.x && a.y >= b.y && a.x + a.w <= b.x + b.w && a.y + a.h <= b.y + b.h && (i > j || a.w !== b.w || a.h !== b.h || a.x !== b.x || a.y !== b.y)));
  }
  return used;
}

function renderAtlas() {
  const png = new PNG({ width: TEX, height: TEX });
  const S = 4;
  for (const it of islands.values()) {
    const p = it.painter;
    for (let py = 0; py < it.h; py++) {
      for (let pxl = 0; pxl < it.w; pxl++) {
        let n = 0, cu = 0, cv = 0;
        if (p.shape) {
          for (let sy = 0; sy < S; sy++) for (let sx = 0; sx < S; sx++) {
            const fx = (sx + 0.5) / S, fy = (sy + 0.5) / S;
            if (p.shape((pxl + fx) / it.w, (py + fy) / it.h, pxl, py)) { n++; cu += fx; cv += fy; }
          }
          if (n === 0) continue;
          cu /= n; cv /= n;
        } else { n = S * S; cu = 0.5; cv = 0.5; }
        const c = p.color((pxl + cu) / it.w, (py + cv) / it.h, pxl, py);
        if (!c) continue;
        const a = Math.round(c[3] * (n / (S * S)));
        const o = ((it.y + py) * TEX + (it.x + pxl)) * 4;
        png.data[o] = c[0]; png.data[o + 1] = c[1]; png.data[o + 2] = c[2]; png.data[o + 3] = a;
      }
    }
  }
  return png;
}

/* ---------------------------------------------------------------- painters */
// Blade half (lozenge with pointed tip). root at the top of the island (v=0), tip at the bottom (v=1).
// half 'R': spine at u=0, outer edge at u=1. half 'L': mirrored.
const bladeHW = (s) => (s < 0.22 ? 0.7 + 0.3 * (s / 0.22) : s < 0.64 ? 1 : Math.max(0, (1 - s) / 0.36));
const ROWS = {
  C: { base: 0.30, top: 0.52, a: 240 },   // back row: deep emerald
  B: { base: 0.40, top: 0.64, a: 242 },
  A: { base: 0.52, top: 0.76, a: 244 },   // front row: bright mint
  S: { base: 0.60, top: 0.84, a: 248 },   // spikes
};
function bladePainter(half, rowKey, w, h, seed) {
  const row = ROWS[rowKey];
  return {
    w, h,
    shape: (u, v) => {
      const d = half === 'R' ? u : 1 - u;
      return d <= bladeHW(v) + 1e-9;
    },
    color: (u, v, pxl, pyl) => {
      const d = half === 'R' ? u : 1 - u;
      const s = v;
      const H = Math.max(bladeHW(s), 1e-6);
      const q = clamp(d / H);
      const edgePx = (H - d) * w;     // distance to the outer edge in texels
      const spinePx = d * w;
      let t = lerp(row.base, row.top, Math.pow(s, 0.85));
      let a = row.a;
      // three lengthwise facets: bright ridge facet, main facet, dark outer bevel (all converge on the tip)
      if (q < 0.30) { t += 0.10; a += 8; }
      else if (q > 0.74) { t -= 0.08; }
      // one diagonal cut near the tip: the polished tip facet is lighter
      const cut = 0.58 + 0.10 * q;
      if (s > cut) { t += 0.07; }
      else if (s > cut - 0.012) { t -= 0.08; }
      // tone: left halves catch the light, right halves are darker (V shaped bevel)
      t += half === 'L' ? 0.035 : -0.045;
      if (spinePx < 1.0) { t = Math.max(t, Math.min(0.9, row.top + 0.1)); a = 255; }     // crest line at the ridge
      if (edgePx < 1.0 || s > 0.97) { t = Math.min(0.92, row.top + 0.16); a = 255; } // glow rim
      // very few sparkles
      if (q > 0.35 && q < 0.7 && hash(pxl, pyl, seed) < 0.006) { t = 1; a = 255; }
      return col(t, a);
    },
  };
}

// Plain face: gradient along v, 1px bright rim, optional centre crest (for arm bars and spines).
function barPainter(w, h, o) {
  return {
    w, h,
    color: (u, v, pxl, pyl) => {
      let t = lerp(o.far, o.near, v);                    // v=0 far end, v=1 near end
      const side = u < 0.5 ? o.lit : o.dark;
      t += side;
      if (o.crest !== false) {
        if (Math.abs(u - 0.5) * w < 0.55) t = Math.max(t, 0.88);
      }
      const ex = Math.min(u, 1 - u) * w, ey = Math.min(v, 1 - v) * h;
      if (o.rim !== false && (ex < 1 || ey < 1)) t = Math.max(t, o.rimT || 0.7);
      if (o.seg) {
        const k = (v * h) % o.seg;
        if (k < 1) t -= 0.14; else if (k < 2) t += 0.08;
      }
      if (hash(pxl, pyl, o.seed || 3) < 0.01) t = 1;
      return col(t, o.alpha || 250);
    },
  };
}

// Concentric step-cut gem: octagon (c > 0) or square (c = 0), faces lit from `light`.
function gemPainter(w, h, o) {
  const hx = o.hx, hy = o.hy, c = o.c || 0;
  const norm = (u, v) => {
    const x = (u - 0.5) * 2, y = (v - 0.5) * 2;
    const ax = Math.abs(x), ay = Math.abs(y);
    const oct = c > 0 ? (ax * hx + ay * hy) / (hx + hy - c) : 0;
    return { x, y, ax, ay, r: Math.max(ax, ay, oct), oct };
  };
  return {
    w, h,
    shape: (u, v) => norm(u, v).r <= 1 + 1e-9,
    color: (u, v, pxl, pyl) => {
      const { x, y, ax, ay, r, oct } = norm(u, v);
      // facet normal (sector) of this pixel
      let nx, ny;
      if (r === ax && ax >= ay && ax >= oct) { nx = Math.sign(x); ny = 0; }
      else if (r === ay && ay >= oct) { nx = 0; ny = Math.sign(y); }
      else { nx = Math.sign(x) * 0.707; ny = Math.sign(y) * 0.707; }
      const shade = nx * o.light[0] + ny * o.light[1];         // -1 dark .. +1 lit
      let t, a = o.alpha || 245;
      const steps = o.steps || [0.88, 0.66, 0.42];
      if (r > steps[0]) { t = 0.58 + 0.16 * shade; }
      else if (r > steps[1]) { t = 0.46 + 0.17 * shade; }
      else if (r > steps[2]) { t = 0.38 + 0.15 * shade; }
      else { t = 0.64 + 0.07 * shade; }
      // ring boundary lines
      for (const b of steps) if (Math.abs(r - b) * Math.min(w, h) * 0.5 < 0.7) t -= 0.16;
      // outer rim glow
      if ((1 - r) * Math.min(w, h) * 0.5 < 1.0) { t = 0.92; a = 255; }
      // glint stripes over the table
      if (r < steps[2] + 0.12) {
        const g = (x * hx - y * hy * 0.6) / Math.max(hx, hy);
        if (Math.abs(g - 0.14) < 0.075 || Math.abs(g + 0.2) < 0.03) t = Math.max(t, 0.93);
      }
      if (hash(pxl, pyl, o.seed || 7) < 0.02 && r < 0.9) t += 0.1;
      return col(t, a);
    },
  };
}

// 4-pointed sparkle (the star of the DeuClient emblem)
function starPainter(w, h) {
  return {
    w, h,
    shape: (u, v) => Math.pow(Math.abs(u - 0.5) * 2, 0.55) + Math.pow(Math.abs(v - 0.5) * 2, 0.55) <= 1,
    color: (u, v) => {
      const r = Math.hypot((u - 0.5) * 2, (v - 0.5) * 2);
      return col(lerp(1.0, 0.72, clamp(r)), 255);
    },
  };
}

// soft radial glow (alpha falls off to 0)
function haloPainter(w, h, peak, tone) {
  return {
    w, h,
    color: (u, v) => {
      const r = Math.hypot((u - 0.5) * 2, (v - 0.5) * 2);
      const f = clamp(1 - r);
      return col(tone, peak * f * f);
    },
  };
}

// flat gradient for side faces of crystals (v = 0 top)
function sidePainter(w, h, t0, t1, alpha) {
  return {
    w, h,
    color: (u, v) => {
      let t = lerp(t0, t1, v);
      const ex = Math.min(u, 1 - u) * w, ey = Math.min(v, 1 - v) * h;
      if (ex < 1 || ey < 1) t += 0.14;
      return col(t, alpha || 240);
    },
  };
}

/* ------------------------------------------------------------ model builder */
const ops = [];
const geom2d = { blades: [], bars: [] };

function addGroup(name, origin, rotation, parent) {
  const o = { op: 'add_group', name, origin: origin.map(r3), rotation: rotation.map(r3) };
  if (parent) o.parent = parent;
  ops.push(o);
}
// faces: { south: {key, fu, fv}, ... } (island refs are resolved to UVs after packing)
function addCube(name, from, to, parent, faces, origin, rotation) {
  const o = { op: 'add_cube', name, from: from.map(r3), to: to.map(r3), parent, faces };
  if (origin) o.origin = origin.map(r3);
  if (rotation) o.rotation = rotation.map(r3);
  ops.push(o);
}

// ---- arm skeleton (rest pose, XY plane) ----
const S = [0, 0];
const E = [6.5, 10.5];
const W = [14.0, 15.0];
const H = [20.5, 13.0];
const dist = (a, b) => Math.hypot(b[0] - a[0], b[1] - a[1]);
const LSE = dist(S, E), LEW = dist(E, W), LWH = dist(W, H);
const ARM = LSE + LEW + LWH;
function armPoint(d) {
  d = clamp(d, 0, ARM);
  if (d <= LSE) { const f = d / LSE; return { p: [lerp(S[0], E[0], f), lerp(S[1], E[1], f)], seg: 'seg1' }; }
  d -= LSE;
  if (d <= LEW) { const f = d / LEW; return { p: [lerp(E[0], W[0], f), lerp(E[1], W[1], f)], seg: 'seg2' }; }
  d -= LEW;
  const f = d / LWH;
  return { p: [lerp(W[0], H[0], f), lerp(W[1], H[1], f)], seg: 'seg3' };
}

// layer depths (Z, backwards). rows are separated by 1.2, alternate blades inside a row by 0.4
const Z = { C: 0.6, B: 1.8, A: 3.0, S: 3.2 };
const BAR_Z0 = 1.8, BAR_Z1 = 4.6;
const TILT = -5;        // blades curve slightly backwards (+Z)
const ROOF = 14;        // V bevel angle of the two blade halves

// ---- blades ----
function blade(name, parent, R, T, zBase, w, rowKey, idx, spine) {
  const dx = T[0] - R[0], dy = T[1] - R[1];
  const L = Math.hypot(dx, dy) / Math.cos(rad(TILT));
  const phi = deg(Math.atan2(dy, dx));
  const theta = phi + 90;
  const z0 = zBase + (idx % 2) * 0.4;
  const hw = w / 2;
  addGroup(name, [R[0], R[1], z0], [TILT, 0, theta], parent);
  const wp = px(hw), hp = px(L);
  const keyL = isl(`${name}_L`, wp, hp, bladePainter('L', rowKey, wp, hp, idx * 7 + 1));
  const keyR = isl(`${name}_R`, wp, hp, bladePainter('R', rowKey, wp, hp, idx * 7 + 2));
  const o = [R[0], R[1], z0];
  addCube(`${name}_L`, [R[0] - hw, R[1] - L, z0], [R[0], R[1], z0], name,
    { south: { key: keyL }, north: { key: keyL, fu: true } }, o, [0, -ROOF, 0]);
  addCube(`${name}_R`, [R[0], R[1] - L, z0], [R[0] + hw, R[1], z0], name,
    { south: { key: keyR }, north: { key: keyR, fu: true } }, o, [0, ROOF, 0]);
  if (spine) {
    const sp = ROWS[rowKey];
    const sS = isl(`sp_${rowKey}_S`, 3, 64, barPainter(3, 64, { far: sp.top + 0.1, near: sp.base + 0.18, lit: 0, dark: 0, crest: false, rimT: 0.8, alpha: 255, seed: 11 }));
    const sN = isl(`sp_${rowKey}_N`, 3, 64, barPainter(3, 64, { far: sp.top - 0.2, near: sp.base - 0.06, lit: 0, dark: 0, crest: false, rim: false, alpha: 250 }));
    const sE = isl(`sp_${rowKey}_E`, 3, 64, barPainter(3, 64, { far: sp.top, near: sp.base + 0.1, lit: 0, dark: 0, crest: false, rim: false, alpha: 250 }));
    const sW = isl(`sp_${rowKey}_W`, 3, 64, barPainter(3, 64, { far: sp.top - 0.12, near: sp.base, lit: 0, dark: 0, crest: false, rim: false, alpha: 250 }));
    addCube(`${name}_s`, [R[0] - 0.4, R[1] - 0.86 * L, z0 - 0.25], [R[0] + 0.4, R[1] - 0.05 * L, z0 + 0.35], name,
      { south: { key: sS, fv: true }, north: { key: sN, fv: true }, east: { key: sE, fv: true }, west: { key: sW, fv: true } });
  }
  geom2d.blades.push({ R, T, phi, L, w, rowKey });
}

// tip target curves: ellipse around (cx, cy), parameter angle t in degrees
const CX = 12, CY = 5.5;
const ell = (rx, ry, t) => [CX + rx * Math.cos(rad(t)), CY + ry * Math.sin(rad(t))];

// piecewise linear control curve: [[f, value], ...]
const pw = (c, f) => {
  for (let i = 0; i < c.length - 1; i++) {
    if (f <= c[i + 1][0]) return lerp(c[i][1], c[i + 1][1], (f - c[i][0]) / (c[i + 1][0] - c[i][0]));
  }
  return c[c.length - 1][1];
};

function buildFeathers() {
  // fan of blade rows: roots along the arm (d0..d1), direction angle phi and length L as curves of f in 0..1
  const rows = [
    { key: 'C', n: 10, d0: 3.5, d1: ARM - 1.0, w: 4.8, prefix: 'f_c',
      phi: [[0, -88], [0.25, -78], [0.5, -64], [0.75, -46], [1, -26]],
      len: [[0, 15], [0.25, 21.5], [0.5, 25], [0.75, 24], [1, 17.5]] },
    { key: 'B', n: 9, d0: 5.0, d1: ARM - 2.2, w: 4.2, prefix: 'f_b',
      phi: [[0, -86], [0.5, -66], [1, -38]],
      len: [[0, 11], [0.5, 18.5], [1, 13.5]] },
    { key: 'A', n: 8, d0: 3.0, d1: ARM - 3.0, w: 3.6, prefix: 'f_a',
      phi: [[0, -90], [0.5, -68], [1, -42]],
      len: [[0, 6.5], [0.5, 10.5], [1, 8.5]] },
  ];
  for (const r of rows) {
    for (let k = 0; k < r.n; k++) {
      const f = r.n === 1 ? 0 : k / (r.n - 1);
      const ap = armPoint(lerp(r.d0, r.d1, f));
      const phi = pw(r.phi, f), L = pw(r.len, f);
      const T = [ap.p[0] + L * Math.cos(rad(phi)), ap.p[1] + L * Math.sin(rad(phi))];
      const nm = `${r.prefix}${String(k + 1).padStart(2, '0')}`;
      blade(nm, ap.seg, ap.p, T, Z[r.key], r.w, r.key, k, r.key !== 'A');
    }
  }
  // crystal spikes: elbow, wrist and the hand tip
  const spike = (name, parent, R, phi, L, w, idx) => {
    const T = [R[0] + L * Math.cos(rad(phi)), R[1] + L * Math.sin(rad(phi))];
    blade(name, parent, R, T, Z.S, w, 'S', idx, true);
  };
  spike('f_s1', 'seg2', E, 86, 6.5, 3.2, 0);
  spike('f_s2', 'seg3', W, 66, 8.0, 3.4, 1);
  spike('f_s3', 'seg3', H, -12, 14.5, 3.6, 2);
}

// ---- arm bars ----
function bar(name, parent, a, b, width, keyBase) {
  const dx = b[0] - a[0], dy = b[1] - a[1];
  const len = Math.hypot(dx, dy);
  const beta = deg(Math.atan2(-dx, dy));
  const w = px(width), h = px(len);
  const hw = width / 2;
  const t = keyBase;
  const dz = px(BAR_Z1 - BAR_Z0, 2);
  const south = isl(`${name}_S`, w, h, barPainter(w, h, { far: 0.56 + t, near: 0.26 + t, lit: 0.09, dark: -0.09, seg: 22, seed: 5 }));
  const north = isl(`${name}_N`, w, h, barPainter(w, h, { far: 0.34 + t, near: 0.12 + t, lit: 0.03, dark: -0.05, rim: false, crest: false, alpha: 255 }));
  const east = isl(`${name}_E`, dz, h, barPainter(dz, h, { far: 0.52 + t, near: 0.2 + t, lit: 0, dark: 0, crest: false, rimT: 0.6 }));
  const west = isl(`${name}_W`, dz, h, barPainter(dz, h, { far: 0.4 + t, near: 0.12 + t, lit: 0, dark: 0, crest: false, rimT: 0.55 }));
  const o = [a[0], a[1], 0];
  addCube(name, [a[0] - hw, a[1], BAR_Z0], [a[0] + hw, a[1] + len, BAR_Z1], parent,
    { south: { key: south }, north: { key: north }, east: { key: east }, west: { key: west } }, o, [0, 0, beta]);
  // bright crystal keel on top of the bar
  const kw = 0.9;
  const kS = isl(`${name}_kS`, px(kw), h, barPainter(px(kw), h, { far: 0.95, near: 0.62, lit: 0, dark: 0, crest: false, rimT: 0.97, alpha: 255 }));
  const kE = isl(`${name}_kE`, 2, h, barPainter(2, h, { far: 0.8, near: 0.5, lit: 0, dark: 0, crest: false, rim: false, alpha: 255 }));
  const kW = isl(`${name}_kW`, 2, h, barPainter(2, h, { far: 0.66, near: 0.4, lit: 0, dark: 0, crest: false, rim: false, alpha: 255 }));
  addCube(`${name}_keel`, [a[0] - kw / 2, a[1] + len * 0.1, BAR_Z1], [a[0] + kw / 2, a[1] + len * 0.92, BAR_Z1 + 0.6], parent,
    { south: { key: kS }, east: { key: kE }, west: { key: kW } }, o, [0, 0, beta]);
  geom2d.bars.push({ a, b, width });
}

// ---- gems ----
function diamondGem(name, parent, c, side, zc, thick, seed) {
  const hs = side / 2;
  const wp = px(side);
  const front = isl(`${name}_F`, wp, wp, gemPainter(wp, wp, { hx: 1, hy: 1, c: 0, light: [-0.25, -1], steps: [0.84, 0.6, 0.34], seed }));
  const back = isl(`${name}_B`, wp, wp, sidePainter(wp, wp, 0.18, 0.3, 250));
  const sd = isl(`${name}_sd`, px(thick), wp, sidePainter(px(thick), wp, 0.3, 0.55, 245));
  const sd2 = isl(`${name}_sd2`, px(thick), wp, sidePainter(px(thick), wp, 0.16, 0.36, 245));
  const o = [c[0], c[1], zc];
  addCube(name, [c[0] - hs, c[1] - hs, zc - thick / 2], [c[0] + hs, c[1] + hs, zc + thick / 2], parent,
    { south: { key: front }, north: { key: back }, east: { key: sd2 }, west: { key: sd }, up: { key: sd }, down: { key: sd2 } }, o, [0, 0, 45]);
  // star sparkle in front (emblem star)
  const ss = side * 0.8;
  const star = isl(`${name}_star`, px(ss), px(ss), starPainter(px(ss), px(ss)));
  addCube(`${name}_star`, [c[0] - ss / 2, c[1] - ss / 2, zc + thick / 2 + 0.3], [c[0] + ss / 2, c[1] + ss / 2, zc + thick / 2 + 0.3], parent,
    { south: { key: star }, north: { key: star, fu: true } });
}

function shoulderGem(parent) {
  const c = [1.8, 1.6];
  const hxw = 3.2, hyh = 4.6, ch = 1.6;
  const zf = 5.2;
  const wp = px(hxw * 2), hp = px(hyh * 2);
  const sdA = isl('sg_sdA', px(4.0), px(hyh * 2), sidePainter(px(4.0), px(hyh * 2), 0.28, 0.5, 245));
  const sdB = isl('sg_sdB', px(4.0), px(hyh * 2), sidePainter(px(4.0), px(hyh * 2), 0.16, 0.34, 245));
  const sdC = isl('sg_sdC', px(hxw * 2), px(4.0), sidePainter(px(hxw * 2), px(4.0), 0.34, 0.5, 245));
  const sdD = isl('sg_sdD', px(hxw * 2), px(4.0), sidePainter(px(hxw * 2), px(4.0), 0.14, 0.3, 245));
  const dark = isl('sg_back', wp, hp, sidePainter(wp, hp, 0.1, 0.26, 252));
  // stepped octagon body: tall + wide box
  addCube('sg_tall', [c[0] - 2.2, c[1] - 4.6, 1.2], [c[0] + 2.2, c[1] + 4.6, zf], parent,
    { east: { key: sdB }, west: { key: sdA }, up: { key: sdC }, down: { key: sdD }, north: { key: dark } });
  addCube('sg_wide', [c[0] - 3.2, c[1] - 3.2, 1.4], [c[0] + 3.2, c[1] + 3.2, zf - 0.2], parent,
    { east: { key: sdB }, west: { key: sdA }, up: { key: sdC }, down: { key: sdD }, north: { key: dark } });
  // emerald-cut table plane (octagon, step cut)
  const front = isl('sg_front', wp, hp, gemPainter(wp, hp, { hx: hxw, hy: hyh, c: ch, light: [-0.7, -0.7], steps: [0.86, 0.64, 0.40], seed: 21, alpha: 250 }));
  addCube('sg_table', [c[0] - hxw, c[1] - hyh, zf], [c[0] + hxw, c[1] + hyh, zf], parent,
    { south: { key: front }, north: { key: front, fu: true } });
  const ss = 4.0;
  const star = isl('sg_star', px(ss), px(ss), starPainter(px(ss), px(ss)));
  addCube('sg_star', [c[0] - ss / 2, c[1] - ss / 2, zf + 0.6], [c[0] + ss / 2, c[1] + ss / 2, zf + 0.6], parent,
    { south: { key: star }, north: { key: star, fu: true } });
  const hw2 = 7.5, hh2 = 9.5;
  const halo = isl('sg_halo', px(hw2 * 2, 3), px(hh2 * 2, 3), haloPainter(px(hw2 * 2, 3), px(hh2 * 2, 3), 120, 0.55));
  addCube('sg_halo', [c[0] - hw2, c[1] - hh2, 0.2], [c[0] + hw2, c[1] + hh2, 0.2], parent,
    { south: { key: halo }, north: { key: halo, fu: true } });
}

function buildModel() {
  addGroup('wing', [0, 0, 0], [0, 0, 0]);
  addGroup('seg1', [S[0], S[1], 0], [0, 0, 0], 'wing');
  addGroup('seg2', [E[0], E[1], 0], [0, 0, 0], 'seg1');
  addGroup('seg3', [W[0], W[1], 0], [0, 0, 0], 'seg2');
  shoulderGem('wing');
  bar('arm1', 'seg1', S, E, 3.8, 0.0);
  bar('arm2', 'seg2', E, W, 3.2, 0.04);
  bar('arm3', 'seg3', W, H, 2.6, 0.08);
  diamondGem('gem_e', 'seg2', E, 3.6, 3.4, 2.2, 31);
  diamondGem('gem_w', 'seg3', W, 3.0, 3.4, 2.0, 37);
  buildFeathers();
}

/* ------------------------------------------------------------------ output */
function debugSilhouette(file) {
  const sc = 14, ox = 40, oy = 360;
  const X = (x) => ox + x * sc, Y = (y) => oy - y * sc;
  const colr = { C: '#0a6b35', B: '#12a352', A: '#6bff9e', S: '#d6ffe8' };
  let svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${X(40)}" height="${Y(-20)}"><rect width="100%" height="100%" fill="#10151a"/>`;
  svg += `<rect x="${X(-4)}" y="${Y(0)}" width="${4 * sc}" height="${12 * sc}" fill="#333"/>`;
  for (const rk of ['C', 'B', 'A', 'S']) {
    for (const b of geom2d.blades.filter((q) => q.rowKey === rk)) {
      const dx = Math.cos(rad(b.phi)), dy = Math.sin(rad(b.phi));
      const nx = -dy, ny = dx;
      const pts = [];
      for (let i = 0; i <= 12; i++) { const s = i / 12; const hwv = bladeHW(s) * b.w / 2; pts.push([b.R[0] + dx * b.L * s + nx * hwv, b.R[1] + dy * b.L * s + ny * hwv]); }
      for (let i = 12; i >= 0; i--) { const s = i / 12; const hwv = bladeHW(s) * b.w / 2; pts.push([b.R[0] + dx * b.L * s - nx * hwv, b.R[1] + dy * b.L * s - ny * hwv]); }
      svg += `<polygon points="${pts.map((p) => `${X(p[0]).toFixed(1)},${Y(p[1]).toFixed(1)}`).join(' ')}" fill="${colr[rk]}" fill-opacity="0.85" stroke="#021a0c" stroke-width="1"/>`;
    }
  }
  for (const b of geom2d.bars) svg += `<line x1="${X(b.a[0])}" y1="${Y(b.a[1])}" x2="${X(b.b[0])}" y2="${Y(b.b[1])}" stroke="#2fe07b" stroke-width="${b.width * sc}" stroke-linecap="round" stroke-opacity="0.9"/>`;
  svg += '</svg>';
  fs.writeFileSync(file, new Resvg(svg).render().asPng());
}

buildModel();
if (argv.includes('--stats')) {
  const g = {};
  for (const it of islands.values()) {
    const k = it.key.replace(/[0-9]+/g, '#').replace(/_[LR]$/, '_half');
    g[k] = (g[k] || 0) + it.w * it.h;
  }
  const lines = Object.entries(g).sort((a, b) => b[1] - a[1]).map((e) => `${e[0]}: ${e[1]}`);
  console.log(lines.join('\n'));
  console.log(`total island area ${Object.values(g).reduce((a, b) => a + b, 0)} of ${TEX * TEX}`);
}
const usedRows = packIslands();
const atlas = renderAtlas();
fs.mkdirSync(path.dirname(TEX_OUT), { recursive: true });
fs.writeFileSync(TEX_OUT, PNG.sync.write(atlas));

// resolve per-face UVs and write the bbmodel_edit operations
const out = ops.map((o) => {
  if (o.op !== 'add_cube') return o;
  const faces = {};
  for (const [dir, f] of Object.entries(o.faces)) {
    const it = islands.get(f.key);
    const u1 = it.x, v1 = it.y, u2 = it.x + it.w, v2 = it.y + it.h;
    faces[dir] = { uv: [f.fu ? u2 : u1, f.fv ? v2 : v1, f.fu ? u1 : u2, f.fv ? v1 : v2], texture: 0 };
  }
  return { ...o, faces };
});
fs.writeFileSync(OPS_OUT, JSON.stringify(out));
if (DEBUG_OUT) debugSilhouette(DEBUG_OUT);

// Optional: write the finished .bbmodel directly (same schema Blockbench / bbmodel_edit produce).
// Used for fast iteration; the shipped model is the one built with bbmodel_create + bbmodel_edit from the same ops.
const BBMODEL_OUT = arg('--bbmodel', '');
if (BBMODEL_OUT) {
  const { randomUUID } = require('crypto');
  const tex = fs.readFileSync(TEX_OUT);
  const texId = randomUUID();
  const groups = [], elements = [];
  const gByName = new Map();
  const outliner = [];
  for (const o of out) {
    if (o.op === 'add_group') {
      const uuid = randomUUID();
      groups.push({ uuid, name: o.name, origin: o.origin, rotation: o.rotation, export: true, visibility: true, mirror_uv: false, reset: false, color: 0, autouv: 0, shade: true, locked: false });
      const node = { uuid, isOpen: false, children: [] };
      gByName.set(o.name, node);
      (o.parent ? gByName.get(o.parent).children : outliner).push(node);
    } else {
      const uuid = randomUUID();
      const f = {};
      for (const dir of ['north', 'east', 'south', 'west', 'up', 'down']) f[dir] = o.faces[dir] || { uv: [0, 0, 0, 0] };
      const el = { uuid, name: o.name, origin: o.origin || [(o.from[0] + o.to[0]) / 2, (o.from[1] + o.to[1]) / 2, (o.from[2] + o.to[2]) / 2], color: 0, type: 'cube', from: o.from, to: o.to, box_uv: false, faces: f, rescale: false, locked: false, light_emission: 0, render_order: 'default', allow_mirror_modeling: true, autouv: 0 };
      if (o.rotation) el.rotation = o.rotation;
      elements.push(el);
      gByName.get(o.parent).children.push(uuid);
    }
  }
  const model = {
    meta: { format_version: '5.0', model_format: 'free', box_uv: false },
    name: 'smaragd', model_identifier: '', visible_box: [1, 1, 0], resolution: { width: TEX, height: TEX },
    elements, groups, outliner,
    textures: [{ uuid: texId, name: 'smaragd.png', source: 'data:image/png;base64,' + tex.toString('base64'), path: '', width: TEX, height: TEX, uv_width: TEX, uv_height: TEX, folder: '', namespace: '', id: '0', particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '', render_mode: 'default', render_sides: 'auto', frame_time: 1, frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: false, relative_path: '' }],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {}, ai_used: true, ai_agents: 'local-agent-mode-bbmodel',
  };
  fs.writeFileSync(BBMODEL_OUT, JSON.stringify(model, null, '\t'));
  console.log(`bbmodel -> ${BBMODEL_OUT}`);
}

const nCube = out.filter((o) => o.op === 'add_cube').length;
console.log(`smaragd: ${nCube} cubes, ${islands.size} islands, atlas rows used ${usedRows}/${TEX}px`);
console.log(`texture -> ${TEX_OUT}`);
console.log(`ops     -> ${OPS_OUT}`);

/* ---------------------------------------------------------------- menu preview */
// Step 1 (--preview-prep <plain.bbmodel> <marker.bbmodel>): derive two helper models from --bbmodel output:
//   plain  = the model without the soft halo plane (a halo renders as a hard disc in transparent renders)
//   marker = plain + a small plane at the shoulder origin, so the origin can be located in the render.
// Step 2: render both with bbmodel_render (back view, orthographic, flat light, transparent, same size).
// Step 3 (--compose <plain.png> <marker.png> <out.png>): crop, scale and place the wing into the 256x256 menu preview
//   (shoulder root at x=12%, y=55%, wing fills the frame with a 5% margin).
if (argv.includes('--preview-prep')) {
  const i = argv.indexOf('--preview-prep');
  const m = JSON.parse(fs.readFileSync(BBMODEL_OUT, 'utf8'));
  const halo = m.elements.find((e) => e.name === 'sg_halo');
  const strip = (nodes) => nodes.filter((n) => n !== halo.uuid && (typeof n === 'string' || ((n.children = strip(n.children)), true)));
  m.outliner = strip(m.outliner);
  m.elements = m.elements.filter((e) => e !== halo);
  fs.writeFileSync(argv[i + 1], JSON.stringify(m));
  const { randomUUID } = require('crypto');
  // brightest fully opaque atlas texel gives the marker a texture that always renders
  const at = PNG.sync.read(fs.readFileSync(TEX_OUT));
  let bx = 0, by = 0, bv = -1;
  for (let y = 0; y < TEX; y++) for (let x = 0; x < TEX; x++) {
    const o = (y * TEX + x) * 4;
    if (at.data[o + 3] === 255 && at.data[o] + at.data[o + 1] + at.data[o + 2] > bv) { bv = at.data[o] + at.data[o + 1] + at.data[o + 2]; bx = x; by = y; }
  }
  const mk = { uuid: randomUUID(), name: 'marker', origin: [0, 0, 5.5], color: 0, type: 'cube', from: [-0.8, -0.8, 5.5], to: [0.8, 0.8, 5.5], box_uv: false,
    faces: { north: { uv: [0, 0, 0, 0] }, east: { uv: [0, 0, 0, 0] }, south: { uv: [bx, by, bx + 1, by + 1], texture: 0 }, west: { uv: [0, 0, 0, 0] }, up: { uv: [0, 0, 0, 0] }, down: { uv: [0, 0, 0, 0] } },
    rescale: false, locked: false, light_emission: 0, render_order: 'default', allow_mirror_modeling: true, autouv: 0 };
  m.elements.push(mk);
  m.outliner.push(mk.uuid);
  fs.writeFileSync(argv[i + 2], JSON.stringify(m));
  console.log('preview helper models written');
}
if (argv.includes('--compose')) {
  const i = argv.indexOf('--compose');
  const plain = PNG.sync.read(fs.readFileSync(argv[i + 1]));
  const mark = PNG.sync.read(fs.readFileSync(argv[i + 2]));
  const W0 = plain.width, H0 = plain.height;
  // locate the marker: pixels that differ between the two renders
  let mx = 0, my = 0, mn = 0;
  for (let y = 0; y < H0; y++) for (let x = 0; x < W0; x++) {
    const o = (y * W0 + x) * 4;
    const d = Math.abs(plain.data[o] - mark.data[o]) + Math.abs(plain.data[o + 1] - mark.data[o + 1]) + Math.abs(plain.data[o + 2] - mark.data[o + 2]) + Math.abs(plain.data[o + 3] - mark.data[o + 3]);
    if (d > 40) { mx += x; my += y; mn++; }
  }
  if (!mn) throw new Error('marker not found in render');
  const ox = mx / mn, oy = my / mn;
  // alpha bounding box of the wing
  let x0 = W0, y0 = H0, x1 = 0, y1 = 0;
  for (let y = 0; y < H0; y++) for (let x = 0; x < W0; x++) if (plain.data[(y * W0 + x) * 4 + 3] > 24) { x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y); }
  const N = 256, rx = 0.12 * N, ry = 0.55 * N, lo = 0.05 * N, hi = 0.95 * N;
  const sc = Math.min((hi - rx) / (x1 - ox), (rx - lo) / (ox - x0), (ry - lo) / (oy - y0), (hi - ry) / (y1 - oy));
  const out = new PNG({ width: N, height: N });
  const gain = parseFloat(arg('--gain', '1.0'));
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    // source footprint of this destination pixel (premultiplied box filter)
    const sx0 = ox + (x - rx) / sc, sx1 = ox + (x + 1 - rx) / sc, sy0 = oy + (y - ry) / sc, sy1 = oy + (y + 1 - ry) / sc;
    let r = 0, g = 0, b = 0, a = 0, w = 0;
    for (let yy = Math.floor(sy0); yy < Math.ceil(sy1); yy++) for (let xx = Math.floor(sx0); xx < Math.ceil(sx1); xx++) {
      if (xx < 0 || yy < 0 || xx >= W0 || yy >= H0) { w += 1; continue; }
      const wx = Math.min(xx + 1, sx1) - Math.max(xx, sx0), wy = Math.min(yy + 1, sy1) - Math.max(yy, sy0);
      const ww = wx * wy; if (ww <= 0) continue;
      const o = (yy * W0 + xx) * 4, al = plain.data[o + 3] / 255;
      r += plain.data[o] * al * ww; g += plain.data[o + 1] * al * ww; b += plain.data[o + 2] * al * ww; a += al * ww; w += ww;
    }
    const o = (y * N + x) * 4;
    if (a > 0) { out.data[o] = clamp((r / a) * gain, 0, 255); out.data[o + 1] = clamp((g / a) * gain, 0, 255); out.data[o + 2] = clamp((b / a) * gain, 0, 255); out.data[o + 3] = Math.round(255 * clamp(a / w)); }
  }
  fs.writeFileSync(argv[i + 3], PNG.sync.write(out));
  console.log(`preview: scale ${sc.toFixed(4)}, origin px ${ox.toFixed(1)},${oy.toFixed(1)}, bbox ${x0},${y0}-${x1},${y1} -> ${argv[i + 3]}`);
  process.exit(0);
}
