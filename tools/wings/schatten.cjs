#!/usr/bin/env node
'use strict';
/*
 * DeuClient wing generator, style "schatten" (shadow wings).
 *
 * Builds the model layout (groups, cubes, per-face UVs) and the texture atlas from the same data, so the
 * texture matches the UVs exactly. Output:
 *   - texture atlas PNG (256x256 RGBA)
 *   - model as .bbmodel (Blockbench 5.0 JSON, Generic Model, box_uv false)
 *   - optional element list for bbmodel_edit (--ops)
 *
 * Usage:
 *   node tools/wings/schatten.cjs --dev              work files only (C:/path/to/models/schatten)
 *   node tools/wings/schatten.cjs                    writes the repo texture and the repo model
 *   node tools/wings/schatten.cjs --ops <dir>        also dumps the bbmodel_edit operation list
 *
 * Coordinates follow tools/wings/SPEC.md: right wing, +X outward, +Y up, +Z backwards, units = 1/16 block.
 */
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { PNG } = require('pngjs');

const ROOT = path.resolve(__dirname, '..', '..');
const RES = path.join(ROOT, 'mod', 'mc26.3', 'src', 'main', 'resources', 'assets', 'deucore');
const REPO_TEX = path.join(RES, 'textures', 'cosmetics', 'wings', 'schatten.png');
const REPO_MODEL = path.join(RES, 'models', 'wings', 'schatten.bbmodel');
const WORK = 'C:/path/to/models/schatten';

const args = process.argv.slice(2);
const flag = (n) => args.includes(n);
const opt = (n) => { const i = args.indexOf(n); return i >= 0 ? args[i + 1] : null; };
const DEV = flag('--dev');

/* ------------------------------------------------------------------ utils */

function rng(seed) {
  let a = seed >>> 0;
  return () => {
    a |= 0; a = (a + 0x6D2B79F5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
function hash2(ix, iy, seed) {
  let h = (Math.imul(ix, 374761393) + Math.imul(iy, 668265263) + Math.imul(seed, 1442695041)) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  h ^= h >>> 16;
  return (h >>> 0) / 4294967296;
}
function vnoise(x, y, seed) {
  const ix = Math.floor(x), iy = Math.floor(y);
  const fx = x - ix, fy = y - iy;
  const sx = fx * fx * (3 - 2 * fx), sy = fy * fy * (3 - 2 * fy);
  const a = hash2(ix, iy, seed), b = hash2(ix + 1, iy, seed), c = hash2(ix, iy + 1, seed), d = hash2(ix + 1, iy + 1, seed);
  return a + (b - a) * sx + (c - a) * sy + (a - b - c + d) * sx * sy;
}
function fbm(x, y, seed, oct = 4) {
  let v = 0, amp = 0.5, f = 1, norm = 0;
  for (let i = 0; i < oct; i++) { v += amp * vnoise(x * f, y * f, seed + i * 17); norm += amp; f *= 2; amp *= 0.5; }
  return v / norm;
}
const clamp = (v, a = 0, b = 1) => (v < a ? a : v > b ? b : v);
const lerp = (a, b, t) => a + (b - a) * t;
const sstep = (a, b, x) => { const t = clamp((x - a) / (b - a)); return t * t * (3 - 2 * t); };
const mix = (a, b, t) => [lerp(a[0], b[0], t), lerp(a[1], b[1], t), lerp(a[2], b[2], t)];
const add = (a, b, k) => [a[0] + b[0] * k, a[1] + b[1] * k, a[2] + b[2] * k];
const DEG = Math.PI / 180;
const r3 = (v) => Math.round(v * 1000) / 1000;
const rv = (a) => a.map(r3);
const rgb = (c) => [clamp(c[0], 0, 255), clamp(c[1], 0, 255), clamp(c[2], 0, 255)];

/* palette (spirit of WINGS.schatten in tools/generate-ingame-ui.cjs: base #050307, mid #2b1745, light #7c3aed, glow #a78bfa) */
const P = {
  black: [5, 3, 7], deep: [13, 7, 24], dark: [24, 12, 42], mid: [43, 23, 69], plum: [58, 26, 98],
  violet: [76, 29, 149], rim: [99, 38, 190], glow: [124, 58, 237], light: [167, 139, 250],
  pale: [205, 190, 254], hot: [240, 234, 255],
  boneDark: [30, 22, 44], bone: [58, 46, 82], boneHi: [112, 96, 150],
};

/* ------------------------------------------------------------------ geometry config */

const TEX = 256;
const PX = 3.6;            // texels per unit for feather islands
const Z_BONE = 1.8;        // centre plane of the arm bones
const Z = { mem: 0.40, l1: 1.05, l2: 1.75, l3: 2.50, step: 0.22 };
const TILT = -2.6;         // slight sweep back (feather tips drift to +Z)

const ARM = [[1.2, 0.4], [5, 10.5], [14.5, 18.5], [28, 17.5]];   // shoulder, elbow, wrist, hand tip
const armLen = [];
let armTotal = 0;
for (let i = 0; i < 3; i++) {
  const l = Math.hypot(ARM[i + 1][0] - ARM[i][0], ARM[i + 1][1] - ARM[i][1]);
  armLen.push(l); armTotal += l;
}
function armPoint(d) {
  let rest = d;
  for (let i = 0; i < 3; i++) {
    if (rest <= armLen[i] || i === 2) {
      const t = rest / armLen[i];
      return { p: [lerp(ARM[i][0], ARM[i + 1][0], t), lerp(ARM[i][1], ARM[i + 1][1], t)], seg: i + 1 };
    }
    rest -= armLen[i];
  }
}

/* trailing edge: tip targets of the longest feather row, parameter u 0..1 from the shoulder to the hand */
const TE = [
  [0.00, 3.4, -11.5], [0.10, 5.8, -13.0], [0.22, 9.2, -13.2], [0.36, 13.5, -11.6], [0.50, 18.5, -8.0],
  [0.64, 23.5, -3.2], [0.78, 29.0, 2.8], [0.90, 33.2, 8.4], [1.00, 35.3, 14.5],
];
function teAt(u) {
  let i = 0;
  while (i < TE.length - 2 && u > TE[i + 1][0]) i++;
  const a = TE[Math.max(0, i - 1)], b = TE[i], c = TE[i + 1], d = TE[Math.min(TE.length - 1, i + 2)];
  const t = clamp((u - b[0]) / (c[0] - b[0]));
  const cr = (p0, p1, p2, p3) => 0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t + (-p0 + 3 * p1 - 3 * p2 + p3) * t * t * t);
  return [cr(a[1], b[1], c[1], d[1]), cr(a[2], b[2], c[2], d[2])];
}

/* ------------------------------------------------------------------ tree + islands */

const islands = [];
function mkIsland(w, h, paint, tag) {
  const i = { w, h, paint, tag, x: 0, y: 0 };
  islands.push(i);
  return i;
}
const planeFaces = (isl, r) => ({ south: { isl, r }, north: { isl, r, flip: true } });

/* ------------------------------------------------------------------ painters (Float32 RGBA, straight alpha) */

function newBuf(w, h) { return new Float32Array(w * h * 4); }
function put(buf, w, x, y, c, a) {
  const i = (y * w + x) * 4;
  buf[i] = c[0]; buf[i + 1] = c[1]; buf[i + 2] = c[2]; buf[i + 3] = a;
}

/* inside distance (in subpixels) by two pass chamfer; out of range counts as outside */
function edt(mask, W, H) {
  const INF = 1e9, d = new Float32Array(W * H);
  for (let i = 0; i < W * H; i++) d[i] = mask[i] ? INF : 0;
  const g = (x, y) => (x < 0 || y < 0 || x >= W || y >= H ? 0 : d[y * W + x]);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const i = y * W + x; if (!d[i]) continue;
    d[i] = Math.min(d[i], g(x - 1, y) + 1, g(x - 1, y - 1) + 1.414, g(x, y - 1) + 1, g(x + 1, y - 1) + 1.414);
  }
  for (let y = H - 1; y >= 0; y--) for (let x = W - 1; x >= 0; x--) {
    const i = y * W + x; if (!d[i]) continue;
    d[i] = Math.min(d[i], g(x + 1, y) + 1, g(x + 1, y + 1) + 1.414, g(x, y + 1) + 1, g(x - 1, y + 1) + 1.414);
  }
  return d;
}

/* rasterise a shape function with supersampling: coverage and inside distance (px) per pixel */
function rasterShape(w, h, inside, SS = 4) {
  const W = w * SS, H = h * SS, mask = new Uint8Array(W * H);
  for (let Y = 0; Y < H; Y++) for (let X = 0; X < W; X++) mask[Y * W + X] = inside((X + 0.5) / SS, (Y + 0.5) / SS) ? 1 : 0;
  const d = edt(mask, W, H);
  const cov = new Float32Array(w * h), de = new Float32Array(w * h);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    let c = 0;
    for (let sy = 0; sy < SS; sy++) for (let sx = 0; sx < SS; sx++) c += mask[(y * SS + sy) * W + x * SS + sx];
    cov[y * w + x] = c / (SS * SS);
    de[y * w + x] = d[(y * SS + (SS >> 1)) * W + x * SS + (SS >> 1)] / SS;
  }
  return { cov, de };
}

/*
 * Feather: root at the top row, tip at the bottom row. Torn silhouette (notched sides, slits, forked tip),
 * smoky body that is darker toward the edges, chevron barbs, thin glowing rachis and branch veins,
 * violet rim light, soft alpha at the edges and the tip.
 */
function paintFeather(w, h, seed, o) {
  const r = rng(seed);
  o = Object.assign({ layer: 0, teeth: w >= 15 ? 3 : 2, notches: 3, lean: 0, rootNarrow: 0.4, taper: 0.26, veins: 4, glow: 1, sparks: 2, tipGlow: 0, slits: 1 }, o);
  const W2 = w / 2 - 0.4;
  const sc = clamp(w / 16, 0.6, 1.4);
  const teeth = [];
  const main = Math.floor(r() * o.teeth);
  for (let k = 0; k < o.teeth; k++) {
    const c = (o.teeth === 1 ? 0 : (-0.55 + 1.1 * k / (o.teeth - 1))) * W2 + (r() - 0.5) * W2 * 0.3;
    const tk = k === main ? 1.0 : 0.80 + r() * 0.13;
    const valley = 0.64 + r() * 0.12;
    const bw = Math.max(1.8, W2 * 1.15 / o.teeth);
    teeth.push({ c, t: tk, s: (tk - valley) / bw });
  }
  const notchL = [], notchR = [];
  for (let k = 0; k < o.notches; k++) {
    (r() < 0.5 ? notchL : notchR).push({ v: h * (0.16 + 0.6 * r()), d: 0.3 + 0.28 * r(), hw: (2.4 + 3.4 * r()) * sc });
  }
  for (let k = 0; k < o.slits; k++) {   // narrow deep tears
    (r() < 0.5 ? notchL : notchR).push({ v: h * (0.35 + 0.4 * r()), d: 0.62, hw: 1.3 * sc });
  }
  const shape = (u, v) => {
    const t = v / h;
    const dx = u - (w / 2 + o.lean * t * t);
    let hw = t < 0.26 ? W2 * lerp(o.rootNarrow, 1, sstep(0, 0.26, t)) : W2;
    if (t > 0.58) hw *= 1 - o.taper * sstep(0.58, 0.95, t);
    const ns = dx < 0 ? notchL : notchR;
    let depth = 0;
    for (const n of ns) { const q = Math.abs(v - n.v) / n.hw; if (q < 1) depth = Math.max(depth, n.d * (1 - q)); }
    hw *= 1 - depth;
    if (Math.abs(dx) > hw) return false;
    let reach = 0;
    for (const T of teeth) reach = Math.max(reach, T.t - Math.abs(dx - T.c) * T.s);
    return t <= reach;
  };
  const { cov, de } = rasterShape(w, h, shape);
  const buf = newBuf(w, h);
  const veins = [];
  for (let k = 0; k < o.veins; k++) veins.push({ v: h * (0.1 + 0.6 * (k + r() * 0.4) / o.veins), len: h * (0.16 + 0.12 * r()), slope: 0.45 + 0.35 * r(), gain: 0.45 + 0.35 * r() });
  const sparks = [];
  for (let k = 0; k < o.sparks; k++) sparks.push({ x: Math.floor(w * (0.25 + 0.5 * r())), y: Math.floor(h * (0.12 + 0.6 * r())) });
  const seedN = Math.floor(r() * 1000);
  const ph = r() * 6.28;
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const i = y * w + x;
    const c = cov[i];
    const u = x + 0.5, v = y + 0.5, t = v / h;
    const dx = u - (w / 2 + o.lean * t * t);
    if (c <= 0) { put(buf, w, x, y, P.deep, 0); continue; }
    const nx = clamp(Math.abs(dx) / W2);
    const smoke = fbm(u * 0.33, v * 0.11, seedN);
    let col = mix(P.deep, P.dark, 0.3 + 0.7 * smoke);
    col = mix(col, P.mid, (0.05 + 0.5 * o.layer) * (0.5 + 0.5 * smoke));
    col = mix(col, P.plum, (0.18 + 0.5 * o.layer) * (1 - Math.pow(nx, 1.2)) * (1 - 0.55 * t) * (0.55 + 0.45 * smoke));
    col = mix(col, P.black, 0.5 * sstep(0.55, 1, nx) + 0.4 * sstep(0.5, 1, t));
    // chevron barbs
    const s = v + Math.abs(dx) * 1.6;
    const fr = (s / 3.1) % 1;
    const barb = Math.pow(Math.max(0, 1 - Math.abs(fr - 0.5) * 2.4), 2);
    col = mix(col, P.violet, barb * (0.14 + 0.1 * o.layer) * (1 - 0.4 * nx) * (1 - 0.5 * t));
    // rachis: thin beaded glow
    const root = 1 - 0.5 * t;
    const bead = 0.7 + 0.3 * Math.sin(v * 0.4 + ph);
    const g1 = Math.exp(-Math.pow(dx / 0.6, 2));
    const g2 = Math.exp(-Math.pow(dx / 1.7, 2));
    col = add(col, P.glow, 0.34 * g2 * root * o.glow);
    col = add(col, P.light, 0.6 * g1 * root * bead * o.glow);
    // branch veins
    for (const vn of veins) {
      const p = v - vn.v;
      if (p > 0 && p < vn.len) {
        const dd = Math.abs(Math.abs(dx) - vn.slope * p);
        const gv = Math.exp(-Math.pow(dd / 0.55, 2)) * (1 - p / vn.len) * vn.gain;
        col = add(col, P.glow, 0.6 * gv * o.glow * root);
        col = add(col, P.light, 0.3 * gv * o.glow * root);
      }
    }
    // rim light along every edge, including the notches
    const rimk = 1 - sstep(0.0, 1.9, de[i]);
    col = mix(col, mix(P.rim, P.glow, 0.5 * o.layer), (0.55 + 0.25 * o.layer) * rimk);
    col = add(col, P.light, (0.14 + 0.16 * o.layer) * (1 - sstep(0, 0.9, de[i])));
    if (o.tipGlow > 0) col = add(col, P.glow, o.tipGlow * sstep(0.6, 1, t));
    let a = c * (0.5 + 0.5 * sstep(0, 2.2, de[i])) * (1 - 0.5 * sstep(0.6, 1, t));
    a *= 0.9 + 0.1 * fbm(u * 0.5, v * 0.2, seedN + 5);
    a = Math.max(a, c * 0.85 * clamp(g1 * root * o.glow));   // veins stay readable
    for (const sp of sparks) {
      const q = Math.abs(x - sp.x) + Math.abs(y - sp.y);
      if (q === 0) { col = mix(col, P.pale, 0.9); a = Math.max(a, c); }
      else if (q === 1) { col = mix(col, P.light, 0.3); }
    }
    put(buf, w, x, y, rgb(col), clamp(a));
  }
  return buf;
}

/* smoky wing membrane patch: bone line at the top row, ragged tendrils at the bottom, glowing veins */
function paintMembrane(w, h, seed) {
  const buf = newBuf(w, h);
  const bottom = (u) => h * (0.42 + 0.58 * fbm(u * 0.085, seed * 0.13, seed)) - 0.4 * h * Math.abs(vnoise(u * 0.55, 4.4, seed) - 0.5);
  const veinPos = [];
  const r = rng(seed);
  const nv = Math.max(4, Math.round(w / 9));
  for (let k = 0; k < nv; k++) veinPos.push({ u: w * (k + 0.5 + (r() - 0.5) * 0.5) / nv, sway: 1 + 2 * r(), ph: r() * 6.28, drift: (r() - 0.5) * 0.3 });
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const u = x + 0.5, v = y + 0.5, t = v / h;
    const b = bottom(u);
    let a = 1 - sstep(b * 0.38, b, v);
    a *= 0.78 + 0.22 * fbm(u * 0.2, v * 0.08, seed + 3);
    a *= sstep(0, 3.2, u) * sstep(0, 3.2, w - u);
    a *= 0.92 - 0.1 * t;
    if (a < 0.02) { put(buf, w, x, y, P.deep, 0); continue; }
    const sm = fbm(u * 0.14, v * 0.09, seed + 9);
    let col = mix(P.black, P.dark, 0.2 + 0.8 * sm);
    col = mix(col, P.mid, 0.35 * sm * (1 - t));
    for (const vp of veinPos) {
      const cu = vp.u + vp.drift * v + Math.sin(v * 0.12 + vp.ph) * vp.sway;
      const fade = Math.pow(1 - clamp(v / b), 1.4);
      col = add(col, P.light, 0.55 * Math.exp(-Math.pow((u - cu) / 0.85, 2)) * fade);
      col = add(col, P.glow, 0.25 * Math.exp(-Math.pow((u - cu) / 2.2, 2)) * fade);
    }
    col = add(col, P.rim, 0.5 * (1 - sstep(0, 2.5, v)));
    put(buf, w, x, y, rgb(col), clamp(a));
  }
  return buf;
}

/* drifting smoke strand: soft S curve, pale violet core, transparent */
function paintWisp(w, h, seed) {
  const buf = newBuf(w, h), r = rng(seed);
  const amp = (w * 0.22) * (0.6 + r() * 0.8), ph = r() * 6.28, fr = 1.2 + r() * 1.3;
  const spark = { x: Math.floor(w * (0.35 + 0.3 * r())), y: Math.floor(h * (0.25 + 0.5 * r())) };
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const u = x + 0.5, v = y + 0.5, t = v / h;
    const cx = w / 2 + Math.sin(t * fr * 3.14 + ph) * amp * Math.sin(t * 3.14);
    const half = (w / 2 - 0.6) * Math.pow(Math.sin(clamp(t * 0.92 + 0.04) * 3.14), 0.7);
    const q = 1 - Math.abs(u - cx) / Math.max(half, 0.01);
    if (q <= 0) { put(buf, w, x, y, P.deep, 0); continue; }
    const core = Math.pow(q, 1.4);
    let col = mix(P.plum, P.light, core * 0.85);
    col = mix(col, P.pale, 0.35 * core * sstep(0.2, 0.9, t));
    let a = (0.14 + 0.5 * core) * (0.55 + 0.45 * Math.sin(t * 3.14));
    a *= 0.9 + 0.1 * fbm(u * 0.5, v * 0.15, seed);
    if (x === spark.x && y === spark.y) { col = P.hot; a = 0.95; }
    put(buf, w, x, y, rgb(col), clamp(a));
  }
  return buf;
}

/* bone: shading across the width, knuckle rings, cracked glow */
function paintBone(w, h, seed, o = {}) {
  const buf = newBuf(w, h), r = rng(seed);
  const crack = [];
  for (let k = 0; k < 6; k++) crack.push({ v: h * r(), len: 4 + 8 * r(), side: r() < 0.5 ? -1 : 1 });
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const u = x + 0.5, v = y + 0.5, t = v / h;
    const nx = (u / w) * 2 - 1;
    const round = Math.sqrt(Math.max(0, 1 - nx * nx));
    let col = mix(P.boneDark, P.bone, 0.35 + 0.55 * round);
    col = add(col, P.boneHi, 0.55 * Math.exp(-Math.pow((nx + 0.4) / 0.28, 2)) * (0.6 + 0.4 * fbm(u * 0.4, v * 0.15, seed)));
    const ring = Math.pow(Math.max(0, Math.cos(v * 0.78 + seed)), 14);
    col = mix(col, P.black, 0.45 * ring);
    col = mix(col, P.deep, 0.5 * (1 - sstep(0, 0.14, t)) + 0.5 * sstep(0.88, 1, t) * (o.darkEnd ? 1 : 0.4));
    for (const cr of crack) {
      const p = v - cr.v;
      if (p > 0 && p < cr.len) {
        const g = Math.exp(-Math.pow((nx - cr.side * (0.1 + 0.05 * p)) * w / 2 / 0.7, 2)) * (1 - p / cr.len);
        col = add(col, P.glow, 0.9 * g);
        col = add(col, P.light, 0.35 * g);
      }
    }
    col = add(col, P.glow, 0.5 * Math.exp(-Math.pow(v / 2, 2)));
    put(buf, w, x, y, rgb(col), 1);
  }
  return buf;
}

/* joint gem: bone ring, glowing violet facet in the middle */
function paintKnob(w, h) {
  const buf = newBuf(w, h);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const nx = ((x + 0.5) / w) * 2 - 1, ny = ((y + 0.5) / h) * 2 - 1;
    const d = Math.max(Math.abs(nx), Math.abs(ny));
    const rr = Math.hypot(nx, ny);
    let col = mix(P.bone, P.boneDark, sstep(0.55, 1.1, rr));
    col = add(col, P.boneHi, 0.4 * (1 - sstep(0.2, 1, rr)) * (nx < 0 ? 1 : 0.4));
    col = mix(col, P.glow, 0.85 * (1 - sstep(0.18, 0.62, rr)));
    col = mix(col, P.light, 0.8 * (1 - sstep(0, 0.3, rr)));
    col = mix(col, P.black, 0.55 * sstep(0.88, 1, d));
    put(buf, w, x, y, rgb(col), 1);
  }
  return buf;
}
function paintCap(w, h) {
  const buf = newBuf(w, h);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) put(buf, w, x, y, P.boneDark, 1);
  return buf;
}
const flipV = (b, w, h) => {
  const out = newBuf(w, h);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) for (let c = 0; c < 4; c++) out[(y * w + x) * 4 + c] = b[((h - 1 - y) * w + x) * 4 + c];
  return out;
};

/* ------------------------------------------------------------------ model construction */

const R = rng(20260);
const mkGroup = (name, origin, rotation) => ({ type: 'group', name, origin, rotation: rotation || [0, 0, 0], children: [] });
const tree = mkGroup('wing', [0, 0, 0]);
const seg = { 1: mkGroup('seg1', [0, 0, 0]) };
seg[2] = mkGroup('seg2', [ARM[1][0], ARM[1][1], Z_BONE]);
seg[3] = mkGroup('seg3', [ARM[2][0], ARM[2][1], Z_BONE]);
// per segment draw order, back to front: membrane, row1, bones, row2, row3, extras; child segment last
const items = {};
for (const k of [1, 2, 3]) items[k] = { mem: [], r1: [], bone: [], r2: [], r3: [], extra: [] };

let featherNo = 0;

function addFeather(segNo, bucket, R0, zc, theta, L, w, o) {
  featherNo++;
  const name = 'f_' + String(featherNo).padStart(2, '0');
  const g = mkGroup(name, [R0[0], R0[1], zc], [TILT, 0, theta]);
  const hpx = Math.max(14, Math.round(L * PX)), wpx = Math.max(6, Math.round(w * PX));
  const isl = mkIsland(wpx, hpx, () => paintFeather(wpx, hpx, o.seed, o.paint || {}), name);
  const t = 0.3;
  if (o.bend) {
    const f = o.split || 0.6, Lm = L * f, hs = Math.round(hpx * f);
    g.children.push({ type: 'cube', name: name + 'a', from: [R0[0] - w / 2, R0[1] - Lm, zc - t / 2], to: [R0[0] + w / 2, R0[1], zc + t / 2], faces: planeFaces(isl, [0, 0, wpx, hs]) });
    g.children.push({ type: 'cube', name: name + 'b', from: [R0[0] - w / 2, R0[1] - L, zc + 0.2 - t / 2], to: [R0[0] + w / 2, R0[1] - Lm, zc + 0.2 + t / 2], origin: [R0[0], R0[1] - Lm, zc + 0.2], rotation: [0, 0, o.bend], faces: planeFaces(isl, [0, hs, wpx, hpx]) });
  } else {
    g.children.push({ type: 'cube', name: name + 'a', from: [R0[0] - w / 2, R0[1] - L, zc - t / 2], to: [R0[0] + w / 2, R0[1], zc + t / 2], faces: planeFaces(isl, [0, 0, wpx, hpx]) });
  }
  items[segNo][bucket].push(g);
}

/* three feather rows: long flight feathers, mid row (about 64 percent), short coverts (about 36 percent) */
const N1 = 14, N2 = 13, N3 = 12;
const dStart = 6.3, dEnd = armTotal - 1.0;
const widthFor = (len, min) => Math.max(min, Math.min(6.4, 0.25 * len + 0.9)) * (0.94 + R() * 0.14);
const l1 = [];
for (let i = 0; i < N1; i++) {
  const u = i / (N1 - 1), d = lerp(dStart, dEnd, u);
  const a = armPoint(d), T = teAt(u);
  const dx = T[0] - a.p[0], dy = T[1] - a.p[1];
  let len = Math.hypot(dx, dy) * (1 + (R() - 0.5) * 0.2);
  if (i % 5 === 3) len *= 0.78;                      // broken, shorter feather
  const theta = Math.atan2(dx, -dy) / DEG + (R() - 0.5) * 5;
  const z = Z.l1 + (i % 3) * Z.step;
  addFeather(a.seg, 'r1', a.p, z, theta, len, widthFor(len, 4.0), {
    seed: 100 + i, bend: 3 + 9 * u + R() * 3, split: 0.56 + R() * 0.1,
    paint: { layer: 0, lean: (R() - 0.3) * 2.2, veins: 5, glow: 1.0, sparks: 1 },
  });
  l1.push({ seg: a.seg, p: a.p, theta, len, z });
}
for (let j = 0; j < N2; j++) {
  const uu = clamp((j + 0.35) / (N2 - 0.3));
  const d = lerp(dStart + 1.3, dEnd - 0.6, uu);
  const a = armPoint(d), T = teAt(uu);
  const dx = T[0] - a.p[0], dy = T[1] - a.p[1];
  let len = Math.hypot(dx, dy) * 0.64 * (1 + (R() - 0.5) * 0.24);
  if (j % 4 === 2) len *= 0.78;
  const theta = Math.atan2(dx, -dy) / DEG + (R() - 0.5) * 6;
  addFeather(a.seg, 'r2', a.p, Z.l2 + (j % 3) * Z.step, theta, len, widthFor(len, 3.0), {
    seed: 300 + j, bend: j % 2 ? 4 + R() * 4 : 0, split: 0.58,
    paint: { layer: 0.5, lean: (R() - 0.4) * 1.6, veins: 4, glow: 1.0, sparks: 0 },
  });
}
for (let k = 0; k < N3; k++) {
  const uu = clamp((k + 0.2) / (N3 - 0.4));
  const d = lerp(dStart + 0.5, dEnd - 0.2, uu);
  const a = armPoint(d), T = teAt(uu);
  const dx = T[0] - a.p[0], dy = T[1] - a.p[1];
  const len = Math.hypot(dx, dy) * 0.36 * (1 + (R() - 0.5) * 0.3);
  const theta = Math.atan2(dx, -dy) / DEG + (R() - 0.5) * 8;
  addFeather(a.seg, 'r3', a.p, Z.l3 + (k % 3) * Z.step, theta, len, widthFor(len, 2.8), {
    seed: 500 + k,
    paint: { layer: 1, teeth: 2, notches: 2, lean: (R() - 0.5) * 1.2, veins: 3, glow: 1.0, sparks: 1, rootNarrow: 0.5, slits: 0 },
  });
}

/* drifting wisps: smoke strands that continue some flight feather tips (attached), plus two fully detached */
const wispDefs = [];
[1, 3, 5, 7, 8, 10, 11, 12].forEach((idx, n) => {
  const f = l1[idx];
  const s = Math.sin(f.theta * DEG), c = Math.cos(f.theta * DEG);
  const rootP = [f.p[0] + 0.8 * f.len * s, f.p[1] - 0.8 * f.len * c];
  wispDefs.push({ seg: f.seg, R: rootP, th: f.theta + (n % 2 ? 1 : -1) * (8 + 12 * R()), L: 7 + 4 * R(), w: 1.8 + 0.6 * R(), z: f.z + 0.55 + (n % 2) * 0.3, tilt: -9 - 7 * R() });
});
wispDefs.push({ seg: 2, R: [14.5, -10.8], th: 60, L: 5.0, w: 1.5, z: 2.4, tilt: -16 });
wispDefs.push({ seg: 3, R: [22.8, -6.6], th: 55, L: 5.0, w: 1.5, z: 2.7, tilt: -18 });
wispDefs.forEach((wd, i) => {
  const sn = Math.sin(wd.th * DEG), cs = Math.cos(wd.th * DEG);   // keep wisps inside the size budget
  if (sn > 0.1) wd.L = Math.min(wd.L, (34.9 - wd.R[0]) / sn);
  if (cs > 0.1) wd.L = Math.min(wd.L, (wd.R[1] + 13.1) / cs);
  wd.L = Math.max(wd.L, 3.5);
  const name = 'f_w' + String(i + 1).padStart(2, '0');
  const g = mkGroup(name, [wd.R[0], wd.R[1], wd.z], [wd.tilt, (i % 2 ? 1 : -1) * (6 + 5 * R()), wd.th]);
  const hpx = Math.round(wd.L * PX), wpx = Math.max(6, Math.round(wd.w * PX));
  const isl = mkIsland(wpx, hpx, () => paintWisp(wpx, hpx, 700 + i), name);
  g.children.push({ type: 'cube', name: name + 'a', from: [wd.R[0] - wd.w / 2, wd.R[1] - wd.L, wd.z - 0.15], to: [wd.R[0] + wd.w / 2, wd.R[1], wd.z + 0.15], faces: planeFaces(isl, [0, 0, wpx, hpx]) });
  items[wd.seg].extra.push(g);
});

/* leading edge tatters: short torn tufts rising from the arm, glowing tips (d = arc length along the arm) */
const tuftDefs = [
  { d: 14.5, lean: -12, L: 4.6, w: 2.6 }, { d: 18.5, lean: -6, L: 5.8, w: 2.8 }, { d: 22.5, lean: 4, L: 4.8, w: 2.6 },
  { d: 28.5, lean: 12, L: 5.2, w: 2.8 }, { d: 32.0, lean: 18, L: 4.4, w: 2.4 }, { d: 35.4, lean: 24, L: 3.6, w: 2.2 },
];
tuftDefs.forEach((td, i) => {
  const name = 'f_t' + String(i + 1).padStart(2, '0');
  const pt = armPoint(td.d);
  const g = mkGroup(name, [pt.p[0], pt.p[1], Z_BONE], [0, 0, -td.lean]);
  const hpx = Math.round(td.L * PX), wpx = Math.max(6, Math.round(td.w * PX));
  // painted with the tip at the top row, because the cube points up
  const isl = mkIsland(wpx, hpx, () => flipV(paintFeather(wpx, hpx, 900 + i, { layer: 0.8, teeth: 2, notches: 2, veins: 2, glow: 1.1, sparks: 1, rootNarrow: 0.6, taper: 0.5, tipGlow: 0.5, slits: 0 }), wpx, hpx), name);
  g.children.push({ type: 'cube', name: name + 'a', from: [pt.p[0] - td.w / 2, pt.p[1], Z_BONE - 0.15], to: [pt.p[0] + td.w / 2, pt.p[1] + td.L, Z_BONE + 0.15], faces: planeFaces(isl, [0, 0, wpx, hpx]) });
  items[pt.seg].extra.push(g);
});

/* membrane patches (behind everything), staggered in Z */
function addMembrane(segNo, A, B, H, z, extra) {
  const dx = B[0] - A[0], dy = B[1] - A[1], len = Math.hypot(dx, dy);
  const beta = Math.atan2(dy, dx) / DEG;
  const L0 = len + 2 * extra;
  const wpx = Math.round(L0 * 2.4), hpx = Math.round(H * 2.4);
  const name = 'mem' + segNo;
  const isl = mkIsland(wpx, hpx, () => paintMembrane(wpx, hpx, 40 + segNo * 11), name);
  const S = [A[0] - dx / len * extra, A[1] - dy / len * extra];
  items[segNo].mem.push({
    type: 'cube', name, from: [S[0], S[1] - H, z - 0.12], to: [S[0] + L0, S[1], z + 0.12], origin: [S[0], S[1], z], rotation: [0, 0, beta],
    faces: planeFaces(isl, [0, 0, wpx, hpx]),
  });
}
addMembrane(1, armPoint(5.2).p, ARM[1], 9, Z.mem, 1.2);
addMembrane(2, ARM[1], ARM[2], 11, Z.mem + 0.22, 1.2);
addMembrane(3, ARM[2], ARM[3], 9, Z.mem + 0.44, 1.2);

/* arm bones, joint gems, claws */
const capIsl = mkIsland(4, 4, () => paintCap(4, 4), 'cap');
function boneCube(segNo, name, A, B, thick, o = {}) {
  const dx = B[0] - A[0], dy = B[1] - A[1], len = Math.hypot(dx, dy);
  const phi = Math.atan2(-dx, dy) / DEG;
  const wpx = Math.max(6, Math.round(thick * 4)), hpx = Math.max(10, Math.round(len * 3.4));
  const isl = mkIsland(wpx, hpx, () => paintBone(wpx, hpx, o.seed || 7, o), name);
  const full = [0, 0, wpx, hpx], cap = [0, 0, 4, 4], tz = o.tz || thick;
  items[segNo].bone.push({
    type: 'cube', name, from: [A[0] - thick / 2, A[1], Z_BONE - tz / 2], to: [A[0] + thick / 2, A[1] + len, Z_BONE + tz / 2], origin: [A[0], A[1], Z_BONE], rotation: [0, 0, phi],
    faces: { south: { isl, r: full }, north: { isl, r: full, flip: true }, east: { isl, r: full }, west: { isl, r: full }, up: { isl: capIsl, r: cap }, down: { isl: capIsl, r: cap } },
  });
}
const knobIsl = mkIsland(10, 10, () => paintKnob(10, 10), 'knob');
function knob(segNo, name, C, size, o = {}) {
  const half = size / 2, z = o.z === undefined ? Z_BONE : o.z, hz = o.hz || half * 0.8;
  const f = { isl: knobIsl, r: [0, 0, 10, 10] };
  items[segNo].bone.push({
    type: 'cube', name, from: [C[0] - half, C[1] - half, z - hz], to: [C[0] + half, C[1] + half, z + hz], origin: [C[0], C[1], z], rotation: [0, 0, 45],
    faces: { south: f, north: f, east: f, west: f, up: f, down: f },
  });
}
boneCube(1, 'bone1', ARM[0], ARM[1], 2.2, { seed: 3, tz: 1.7 });
knob(1, 'knob_sh', [ARM[0][0] + 0.8, ARM[0][1]], 2.9, { z: 1.4 });
boneCube(2, 'bone2', ARM[1], ARM[2], 1.9, { seed: 5, tz: 1.5 });
knob(2, 'knob_el', ARM[1], 3.5);
boneCube(3, 'bone3', ARM[2], ARM[3], 1.6, { seed: 9, tz: 1.3, darkEnd: true });
knob(3, 'knob_wr', ARM[2], 3.7);
boneCube(2, 'spur_el', [ARM[1][0] - 0.4, ARM[1][1] + 0.8], [ARM[1][0] - 3.2, ARM[1][1] + 5.4], 1.1, { seed: 11, tz: 1.0, darkEnd: true });
boneCube(3, 'claw_wr1', [ARM[2][0] + 0.2, ARM[2][1] + 0.6], [ARM[2][0] + 1.3, ARM[2][1] + 3.7], 1.1, { seed: 13, tz: 1.0 });
boneCube(3, 'claw_wr2', [ARM[2][0] + 1.3, ARM[2][1] + 3.6], [ARM[2][0] + 3.3, ARM[2][1] + 4.9], 0.75, { seed: 15, tz: 0.75, darkEnd: true });
boneCube(3, 'claw_tip1', [ARM[3][0] - 0.3, ARM[3][1] + 0.2], [ARM[3][0] + 2.5, ARM[3][1] - 2.3], 1.0, { seed: 17, tz: 0.9 });
boneCube(3, 'claw_tip2', [ARM[3][0] + 2.4, ARM[3][1] - 2.2], [ARM[3][0] + 3.7, ARM[3][1] - 5.0], 0.65, { seed: 19, tz: 0.65, darkEnd: true });

for (const k of [1, 2, 3]) {
  const it = items[k];
  seg[k].children.push(...it.mem, ...it.r1, ...it.bone, ...it.r2, ...it.r3, ...it.extra);
}
seg[1].children.push(seg[2]);
seg[2].children.push(seg[3]);
tree.children.push(seg[1]);

/* ------------------------------------------------------------------ layout: pack islands, paint atlas */

function packIslands() {
  const PAD = 1;
  const order = [...islands].sort((a, b) => b.h - a.h || b.w - a.w);
  let x = 0, y = 0, rowH = 0;
  for (const it of order) {
    if (x + it.w > TEX) { x = 0; y += rowH + PAD; rowH = 0; }
    if (rowH === 0) rowH = it.h;
    it.x = x; it.y = y;
    x += it.w + PAD;
    if (y + it.h > TEX) throw new Error('atlas overflow at island ' + it.tag + ' (y=' + (y + it.h) + ')');
  }
  return y + rowH;
}
const usedH = packIslands();

function paintAtlas() {
  const atlas = new Float32Array(TEX * TEX * 4);
  for (let i = 0; i < TEX * TEX; i++) { atlas[i * 4] = P.deep[0]; atlas[i * 4 + 1] = P.deep[1]; atlas[i * 4 + 2] = P.deep[2]; atlas[i * 4 + 3] = 0; }
  for (const it of islands) {
    const b = it.paint();
    for (let y = 0; y < it.h; y++) for (let x = 0; x < it.w; x++) {
      const s = (y * it.w + x) * 4, d = ((it.y + y) * TEX + it.x + x) * 4;
      for (let c = 0; c < 4; c++) atlas[d + c] = b[s + c];
    }
  }
  const png = new PNG({ width: TEX, height: TEX });
  for (let i = 0; i < TEX * TEX; i++) {
    png.data[i * 4] = Math.round(clamp(atlas[i * 4], 0, 255));
    png.data[i * 4 + 1] = Math.round(clamp(atlas[i * 4 + 1], 0, 255));
    png.data[i * 4 + 2] = Math.round(clamp(atlas[i * 4 + 2], 0, 255));
    png.data[i * 4 + 3] = Math.round(clamp(atlas[i * 4 + 3]) * 255);
  }
  return png;
}

/* ------------------------------------------------------------------ bbmodel writer */

let uidN = 0;
function uuid(tag) {
  const h = crypto.createHash('md5').update('schatten:' + tag + ':' + (uidN++)).digest('hex');
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-a${h.slice(17, 20)}-${h.slice(20, 32)}`;
}
function faceUv(f) {
  const x0 = f.isl.x + f.r[0], y0 = f.isl.y + f.r[1], x1 = f.isl.x + f.r[2], y1 = f.isl.y + f.r[3];
  return f.flip ? [x1, y0, x0, y1] : [x0, y0, x1, y1];
}
function cubeJson(c) {
  const faces = {};
  for (const k of ['north', 'east', 'south', 'west', 'up', 'down']) {
    const f = c.faces[k];
    faces[k] = f ? { uv: faceUv(f), texture: 0 } : { uv: [0, 0, 0, 0], texture: null };
  }
  const o = c.origin || [(c.from[0] + c.to[0]) / 2, (c.from[1] + c.to[1]) / 2, (c.from[2] + c.to[2]) / 2];
  const j = {
    name: c.name, box_uv: false, rescale: false, locked: false, light_emission: 0, render_order: 'default', allow_mirror_modeling: true,
    from: rv(c.from), to: rv(c.to), autouv: 0, color: 0, origin: rv(o),
  };
  if (c.rotation) j.rotation = rv(c.rotation);
  j.faces = faces; j.type = 'cube'; j.uuid = uuid(c.name);
  return j;
}
function buildBbmodel(pngBuf) {
  const elements = [], groups = [];
  const walk = (node) => {
    if (node.type === 'cube') { const j = cubeJson(node); elements.push(j); return j.uuid; }
    const gj = {
      name: node.name, uuid: uuid(node.name), origin: rv(node.origin), rotation: rv(node.rotation), export: true, visibility: true,
      mirror_uv: false, reset: false, color: 0, autouv: 0, shade: true, locked: false,
    };
    groups.push(gj);
    const entry = { uuid: gj.uuid, isOpen: false, children: [] };
    for (const ch of node.children) entry.children.push(walk(ch));
    return entry;
  };
  const outliner = [walk(tree)];
  return {
    meta: { format_version: '5.0', model_format: 'free', box_uv: false },
    name: 'schatten', model_identifier: '', visible_box: [1, 1, 0], resolution: { width: TEX, height: TEX },
    elements, groups, outliner,
    textures: [{
      name: 'schatten', folder: '', namespace: '', id: '0', width: TEX, height: TEX, uv_width: TEX, uv_height: TEX, particle: false,
      use_as_default: false, layers_enabled: false, sync_to_project: '', render_mode: 'default', render_sides: 'auto', frame_time: 1,
      frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: false,
      uuid: uuid('texture'), source: 'data:image/png;base64,' + pngBuf.toString('base64'),
    }],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {},
  };
}

/* operations for bbmodel_edit (same data as the file above) */
function buildOps() {
  const ops = [];
  const walk = (node, parent) => {
    if (node.type === 'cube') {
      const o = { op: 'add_cube', name: node.name, from: rv(node.from), to: rv(node.to), parent, texture: 'schatten' };
      if (node.origin) o.origin = rv(node.origin);
      if (node.rotation) o.rotation = rv(node.rotation);
      const faces = {};
      for (const k of ['north', 'east', 'south', 'west', 'up', 'down']) faces[k] = node.faces[k] ? { uv: faceUv(node.faces[k]) } : { texture: null };
      o.faces = faces;
      ops.push(o);
      return;
    }
    const g = { op: 'add_group', name: node.name, origin: rv(node.origin), rotation: rv(node.rotation) };
    if (parent) g.parent = parent;
    ops.push(g);
    for (const ch of node.children) walk(ch, node.name);
  };
  walk(tree, null);
  return ops;
}

/* ------------------------------------------------------------------ main */

function countCubes(n) { return n.type === 'cube' ? 1 : n.children.reduce((s, c) => s + countCubes(c), 0); }
function bounds() {
  // XY extent of all cube corners (rotations about Z of cubes and groups applied)
  let mnx = 1e9, mny = 1e9, mxx = -1e9, mxy = -1e9;
  const who = {};
  const rotZ = (p, o, a) => { const c = Math.cos(a * DEG), s = Math.sin(a * DEG); const x = p[0] - o[0], y = p[1] - o[1]; return [o[0] + x * c - y * s, o[1] + x * s + y * c]; };
  const walk = (n, chain) => {
    if (n.type === 'cube') {
      const pts = [[n.from[0], n.from[1]], [n.to[0], n.from[1]], [n.to[0], n.to[1]], [n.from[0], n.to[1]]];
      for (let p of pts) {
        if (n.rotation) p = rotZ(p, n.origin, n.rotation[2]);
        for (let i = chain.length - 1; i >= 0; i--) p = rotZ(p, chain[i].origin, chain[i].rotation[2]);
        if (p[0] > mxx) who.maxX = n.name; if (p[0] < mnx) who.minX = n.name; if (p[1] > mxy) who.maxY = n.name; if (p[1] < mny) who.minY = n.name;
        mnx = Math.min(mnx, p[0]); mxx = Math.max(mxx, p[0]); mny = Math.min(mny, p[1]); mxy = Math.max(mxy, p[1]);
      }
      return;
    }
    for (const c of n.children) walk(c, chain.concat([n]));
  };
  walk(tree, []);
  return { x: [mnx, mxx], y: [mny, mxy], who };
}

const png = paintAtlas();
const pngBuf = PNG.sync.write(png);
const model = buildBbmodel(pngBuf);

const outTex = DEV ? path.join(WORK, 'schatten_tex.png') : REPO_TEX;
const outModel = opt('--model') || (DEV ? path.join(WORK, 'schatten_dev.bbmodel') : REPO_MODEL);
fs.mkdirSync(path.dirname(outTex), { recursive: true });
fs.writeFileSync(outTex, pngBuf);
if (!flag('--no-model')) {
  fs.mkdirSync(path.dirname(outModel), { recursive: true });
  fs.writeFileSync(outModel, JSON.stringify(model, null, '\t'));
}
if (opt('--ops')) {
  const dir = opt('--ops');
  fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync(path.join(dir, 'ops.json'), JSON.stringify(buildOps()));
}
if (DEV) {
  // 3x view of the atlas on a grey checker backdrop to judge the painting
  const V = 3, view = new PNG({ width: TEX * V, height: TEX * V });
  for (let y = 0; y < TEX * V; y++) for (let x = 0; x < TEX * V; x++) {
    const s = (((y / V) | 0) * TEX + ((x / V) | 0)) * 4, d = (y * TEX * V + x) * 4, a = png.data[s + 3] / 255;
    const bg = ((((x / 12) | 0) + ((y / 12) | 0)) & 1) ? 118 : 96;
    for (let c = 0; c < 3; c++) view.data[d + c] = Math.round(png.data[s + c] * a + bg * (1 - a));
    view.data[d + 3] = 255;
  }
  fs.writeFileSync(path.join(WORK, 'atlas_view.png'), PNG.sync.write(view));
}
const b = bounds();
console.log(JSON.stringify({
  cubes: countCubes(tree), islands: islands.length, atlasRowsUsed: usedH, featherGroups: featherNo,
  boundsXY: { x: b.x.map(r3), y: b.y.map(r3) }, extremes: b.who, texture: outTex, model: flag('--no-model') ? null : outModel,
}));
