#!/usr/bin/env node
/*
 * DeuClient wing style "daemon" (demon / dragon wings).
 *
 * One script generates everything so that texture and UV layout always match:
 *   - the 256x256 texture atlas        (textures/cosmetics/wings/daemon.png)
 *   - the Blockbench model (right wing)(models/wings/daemon.bbmodel)
 *   - optional debug images / review copies of the model
 *
 * Usage:
 *   node tools/wings/daemon.cjs build [--work <dir>]   write texture + model (+ review copies into <dir>)
 *   node tools/wings/daemon.cjs flat <dir>             also write a flat 2D composite (daemon_flat.png) into <dir>
 *   node tools/wings/daemon.cjs preview <render.png>   build the 256x256 menu preview from a bbmodel_render image
 *
 * Review copies (only written with --work): daemon.bbmodel (identical to the shipped model) and
 * daemon_view.bbmodel, in which the deleted faces point at a fully transparent swatch so that
 * bb-render (which paints untextured faces black) shows the wing the way the game does.
 *
 * Coordinates (see tools/wings/SPEC.md): +X outward, +Y up, +Z backwards. Origin = shoulder.
 */
'use strict';

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { PNG } = require('pngjs');

const ROOT = path.resolve(__dirname, '..', '..');
const RES = path.join(ROOT, 'mod/mc26/src/main/resources/assets/deucore');
const OUT_MODEL = path.join(RES, 'models/wings/daemon.bbmodel');
const OUT_TEX = path.join(RES, 'textures/cosmetics/wings/daemon.png');
const OUT_PREVIEW = path.join(RES, 'textures/cosmetics/ui/wings_daemon.png');
const TEX = 256;
const DENS_BONE = Number(process.env.DENS_BONE || 5);
const DENS_MEM = Number(process.env.DENS_MEM || 3.6);

/* ------------------------------------------------------------------ utils */
const D = Math.PI / 180;
const clamp = (v, a = 0, b = 1) => Math.min(b, Math.max(a, v));
const lerp = (a, b, t) => a + (b - a) * t;
const smooth = (a, b, x) => { const t = clamp((x - a) / (b - a)); return t * t * (3 - 2 * t); };
const hexc = (h) => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];
const mixc = (a, b, t) => [lerp(a[0], b[0], t), lerp(a[1], b[1], t), lerp(a[2], b[2], t)];
const addc = (a, b, k) => [a[0] + b[0] * k, a[1] + b[1] * k, a[2] + b[2] * k];
const dist = (a, b) => Math.hypot(a[0] - b[0], a[1] - b[1]);

function h2(ix, iy, s) {
  let h = Math.imul(ix, 374761393) ^ Math.imul(iy, 668265263) ^ Math.imul(s, 1442695041);
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  h ^= h >>> 16;
  return (h >>> 0) / 4294967296;
}
function vn(x, y, s) {
  const ix = Math.floor(x), iy = Math.floor(y);
  const fx = x - ix, fy = y - iy;
  const u = fx * fx * (3 - 2 * fx), v = fy * fy * (3 - 2 * fy);
  const a = h2(ix, iy, s), b = h2(ix + 1, iy, s), c = h2(ix, iy + 1, s), d = h2(ix + 1, iy + 1, s);
  return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
}
const fbm = (x, y, s) => vn(x, y, s) * 0.55 + vn(x * 2.1, y * 2.1, s + 7) * 0.3 + vn(x * 4.3, y * 4.3, s + 13) * 0.15;

/* ---------------------------------------------------------------- palette */
// spirit of WINGS.daemon in tools/generate-ingame-ui.cjs (base #0a0203, mid #5c0a10, light #e11d2e, glow #ff7a59)
const C = {
  void: hexc('#0a0203'),
  boneDark: hexc('#1c0c0e'),
  bone: hexc('#43231f'),
  boneHi: hexc('#86533f'),
  boneRim: hexc('#c18c68'),
  memDark: hexc('#26070f'),
  memMid: hexc('#6e0e1a'),
  memLight: hexc('#b51c2c'),
  memGlow: hexc('#cf2a2a'),
  red: hexc('#e11d2e'),
  ember: hexc('#ff7a59'),
  hot: hexc('#ffd29a'),
};

/* --------------------------------------------------------------- geometry */
// joints of the right wing (x, y); z comes from the wing surface function below
const S = [0, 0];
const E = [5.5, 10.0];
const WR = [17.5, 15.5];
// the wing surface bends slowly backwards towards the tips
const zs = (x, y) => 1.7 + 0.0021 * (x * x + y * y);

const FINGERS = [
  { id: 'f_01', pts: [WR, [24.8, 18.6], [31.4, 17.2], [36.0, 12.4]], w: [1.9, 1.5, 1.15, 0.7], d: [2.0, 1.6, 1.25, 0.9], tip: 2.4 },
  { id: 'f_02', pts: [WR, [25.0, 10.4], [30.6, 3.0], [34.0, -4.2]], w: [1.8, 1.4, 1.05, 0.7], d: [1.9, 1.55, 1.25, 0.9], tip: 2.4 },
  { id: 'f_03', pts: [WR, [22.2, 7.6], [25.0, -1.6], [26.0, -11.2]], w: [1.7, 1.35, 1.0, 0.65], d: [1.8, 1.5, 1.2, 0.9], tip: 2.4 },
  { id: 'f_04', pts: [WR, [18.0, 7.4], [16.6, -1.6], [15.0, -9.8]], w: [1.7, 1.35, 1.0, 0.65], d: [1.8, 1.5, 1.2, 0.9], tip: 2.2 },
];
const BODY_PT = [2.6, -6.0];

/* membrane outline: wrist, leading finger, scalloped edges between the finger tips, body, arm */
function scallop(a, b, sag, toward, n = 10) {
  const mid = [(a[0] + b[0]) / 2, (a[1] + b[1]) / 2];
  const dx = toward[0] - mid[0], dy = toward[1] - mid[1];
  const l = Math.hypot(dx, dy) || 1;
  const c = [mid[0] + (dx / l) * 2 * sag, mid[1] + (dy / l) * 2 * sag];
  const pts = [];
  for (let i = 1; i < n; i++) {
    const t = i / n;
    pts.push([
      (1 - t) * (1 - t) * a[0] + 2 * (1 - t) * t * c[0] + t * t * b[0],
      (1 - t) * (1 - t) * a[1] + 2 * (1 - t) * t * c[1] + t * t * b[1],
    ]);
  }
  return pts;
}
const tipOf = (f) => f.pts[f.pts.length - 1];
const SCALLOPS = [
  scallop(tipOf(FINGERS[0]), tipOf(FINGERS[1]), 4.2, WR),
  scallop(tipOf(FINGERS[1]), tipOf(FINGERS[2]), 3.2, WR),
  scallop(tipOf(FINGERS[2]), tipOf(FINGERS[3]), 3.4, WR),
  scallop(tipOf(FINGERS[3]), BODY_PT, 3.2, WR),
];
const OUTLINE = [
  ...FINGERS[0].pts,
  ...SCALLOPS[0], tipOf(FINGERS[1]),
  ...SCALLOPS[1], tipOf(FINGERS[2]),
  ...SCALLOPS[2], tipOf(FINGERS[3]),
  ...SCALLOPS[3], BODY_PT, S, E,
];
// open polyline that forms the visible (non bone-lined) membrane edge
const EDGE_LINES = [
  [tipOf(FINGERS[0]), ...SCALLOPS[0], tipOf(FINGERS[1]), ...SCALLOPS[1], tipOf(FINGERS[2]), ...SCALLOPS[2], tipOf(FINGERS[3]), ...SCALLOPS[3], BODY_PT, S],
];

function pointInPoly(p, poly) {
  let inside = false;
  for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
    const a = poly[i], b = poly[j];
    if ((a[1] > p[1]) !== (b[1] > p[1]) && p[0] < ((b[0] - a[0]) * (p[1] - a[1])) / (b[1] - a[1]) + a[0]) inside = !inside;
  }
  return inside;
}
function distSeg(p, a, b) {
  const dx = b[0] - a[0], dy = b[1] - a[1];
  const l2 = dx * dx + dy * dy;
  const t = l2 ? clamp(((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / l2) : 0;
  return Math.hypot(p[0] - (a[0] + dx * t), p[1] - (a[1] + dy * t));
}
function distPoly(p, pts, closed) {
  let m = 1e9;
  const n = pts.length;
  for (let i = 0; i < (closed ? n : n - 1); i++) m = Math.min(m, distSeg(p, pts[i], pts[(i + 1) % n]));
  return m;
}

/* ----------------------------------------------------- polar vein tables */
function table(pts) {
  const out = [];
  for (let i = 0; i < pts.length - 1; i++) {
    const a = pts[i], b = pts[i + 1];
    const n = Math.max(2, Math.ceil(dist(a, b) / 0.3));
    for (let k = 0; k < n; k++) {
      const p = [lerp(a[0], b[0], k / n), lerp(a[1], b[1], k / n)];
      const r = dist(p, WR);
      if (r > 0.4) out.push({ r, th: Math.atan2(p[1] - WR[1], p[0] - WR[0]) });
    }
  }
  const last = pts[pts.length - 1];
  out.push({ r: dist(last, WR), th: Math.atan2(last[1] - WR[1], last[0] - WR[0]) });
  out.sort((p, q) => p.r - q.r);
  return out;
}
function angAt(tb, r) {
  if (r <= tb[0].r) return tb[0].th;
  if (r >= tb[tb.length - 1].r) return tb[tb.length - 1].th;
  let lo = 0, hi = tb.length - 1;
  while (hi - lo > 1) { const m = (lo + hi) >> 1; if (tb[m].r < r) lo = m; else hi = m; }
  const t = (r - tb[lo].r) / (tb[hi].r - tb[lo].r || 1);
  return lerp(tb[lo].th, tb[hi].th, t);
}
const TABLES = [...FINGERS.map((f) => table(f.pts)), table([WR, E, S, BODY_PT])];

/* ----------------------------------------------------------- cube builders */
const cubes = [];
const groupsDef = []; // {name, parent, origin}
function addGroup(name, parent, origin) { groupsDef.push({ name, parent, origin }); }

/** A cube aligned with the 3D segment a -> b (a,b are [x,y]; z from the wing surface plus zoff). */
function seg3d(o) {
  const za = zs(o.a[0], o.a[1]) + (o.zoff || 0);
  const zb = zs(o.b[0], o.b[1]) + (o.zoff || 0);
  const dx = o.b[0] - o.a[0], dy = o.b[1] - o.a[1], dz = zb - za;
  const L = Math.hypot(dx, dy, dz);
  const ux = dx / L, uy = dy / L, uz = dz / L;
  const e0 = o.ext0 || 0, e1 = o.ext1 || 0;
  const P = [o.a[0] - ux * e0, o.a[1] - uy * e0, za - uz * e0];
  const len = L + e0 + e1;
  const th = Math.atan2(dy, dx);
  const ph = Math.atan2(dz, Math.hypot(dx, dy));
  const c = {
    name: o.name, group: o.group, kind: o.kind,
    from: [P[0], P[1] - o.w / 2, P[2] - o.d / 2],
    to: [P[0] + len, P[1] + o.w / 2, P[2] + o.d / 2],
    origin: P.slice(),
    rot: [0, -ph / D, th / D],
    len, w: o.w, d: o.d, th, ph, P,
    paint: o.paint, faces: o.faces || 'full', seed: o.seed || 1,
  };
  cubes.push(c);
  return c;
}
/** world (x,y) of a point in the cube's local frame (lx along the cube, ly across) */
const cubeXY = (c, lx, ly) => [
  c.P[0] + Math.cos(c.th) * Math.cos(c.ph) * lx - Math.sin(c.th) * ly,
  c.P[1] + Math.sin(c.th) * Math.cos(c.ph) * lx + Math.cos(c.th) * ly,
];
/** inverse of cubeXY: local (lx, ly) of a world point */
const cubeLocal = (c, x, y) => {
  const dx = x - c.P[0], dy = y - c.P[1];
  const ct = Math.cos(c.th), st = Math.sin(c.th);
  return [(dx * ct + dy * st) / Math.cos(c.ph), -dx * st + dy * ct];
};

/* ------------------------------------------------------------ bone shading */
function boneShade(c, iu, iv) {
  const across = Math.abs(2 * iv - 1);
  const ridge = 1 - across;
  let col = mixc(C.boneDark, C.bone, smooth(0.0, 0.65, ridge));
  col = mixc(col, C.boneHi, smooth(0.5, 1.0, ridge) * 0.7);
  // sharp highlight line on the upper edge (iv small = local +y side)
  col = mixc(col, C.boneRim, smooth(0.78, 0.97, 1 - iv) * 0.55 * smooth(0.0, 0.15, iu));
  // joint darkening at both ends of the segment
  col = mixc(col, C.void, 0.45 * (smooth(0.1, 0.0, iu) + smooth(0.9, 1.0, iu)));
  // mottling
  const n = fbm(iu * c.len * 0.55 + c.seed * 3.1, iv * 3 + c.seed, c.seed);
  col = mixc(col, C.void, (0.5 - n) * 0.55);
  // glowing fissure through the bone
  const wob = (fbm(iu * c.len * 0.35, c.seed * 1.7, c.seed + 5) - 0.5) * 0.34;
  const dFis = Math.abs(iv - 0.5 - wob);
  const on = smooth(0.4, 0.6, fbm(iu * c.len * 0.28 + 3, c.seed * 2.3, c.seed + 11));
  const fis = smooth(0.11, 0.0, dFis) * on * smooth(0.04, 0.2, iu) * smooth(0.98, 0.8, iu);
  col = mixc(col, C.ember, fis * 0.85);
  col = mixc(col, C.red, smooth(0.3, 0.08, dFis) * on * 0.35 * (1 - fis));
  return [col[0], col[1], col[2], 255];
}
function barbShade(c, iu, iv) {
  const across = Math.abs(2 * iv - 1);
  let col = mixc(C.bone, C.boneHi, (1 - across) * 0.7);
  col = mixc(col, C.red, smooth(0.35, 1.0, iu) * 0.6);
  col = mixc(col, C.ember, smooth(0.65, 1.0, iu) * 0.85);
  col = mixc(col, C.hot, smooth(0.92, 1.0, iu) * 0.75);
  col = mixc(col, C.void, smooth(0.22, 0.0, iu) * 0.6);
  return [col[0], col[1], col[2], 255];
}
function clawShade(c, iu, iv) {
  const across = Math.abs(2 * iv - 1);
  let col = mixc(C.bone, C.boneHi, (1 - across) * 0.7);
  col = mixc(col, C.boneRim, smooth(0.2, 1.0, iu) * 0.8);
  col = mixc(col, hexc('#ecd0b2'), smooth(0.55, 1.0, iu) * 0.8);
  col = mixc(col, C.void, smooth(0.18, 0.0, iu) * 0.6);
  return [col[0], col[1], col[2], 255];
}
function knobShade(c, iu, iv) {
  const dx = iu - 0.5, dy = iv - 0.5;
  const r = Math.hypot(dx, dy) * 2;
  let col = mixc(C.boneHi, C.bone, smooth(0.0, 1.0, r));
  col = mixc(col, C.void, smooth(0.6, 1.15, r) * 0.7);
  col = mixc(col, C.red, smooth(0.5, 0.0, r) * 0.5);
  col = mixc(col, C.ember, smooth(0.28, 0.0, r) * 0.9);
  col = mixc(col, C.hot, smooth(0.1, 0.0, r) * 0.7);
  return [col[0], col[1], col[2], 255];
}
const SHADES = { bone: boneShade, barb: barbShade, claw: clawShade, knob: knobShade };

/* ----------------------------------------------------------- membrane shade */
// bone segments (for ambient occlusion next to the bones): {a, b, hw}
const boneSegs = [];
const VEINS = [[0.24, 0.5, 0.76], [0.34, 0.68], [0.34, 0.68], [0.22, 0.5, 0.78]];
const CROSS = [[8.5, 14.5, 20.5], [9.5, 16.5, 22.5], [10.5, 17.5, 23.5], [9.0, 15.0, 21.0]];

function membraneShade(x, y, seed) {
  const p = [x, y];
  const inside = pointInPoly(p, OUTLINE);
  const dB = distPoly(p, OUTLINE, true);
  const sd = inside ? dB : -dB;
  const alphaEdge = smooth(-0.1, 0.22, sd);

  let dBone = 9;
  for (const s of boneSegs) dBone = Math.min(dBone, distSeg(p, s.a, s.b) - s.hw);
  const occl = smooth(0.0, 3.6, dBone);

  const r = dist(p, WR);
  const th = Math.atan2(y - WR[1], x - WR[0]);
  // wedge coordinates between neighbouring bones
  let wi = 3, u = 1, gapW = 4;
  for (let i = 0; i < 4; i++) {
    const a0 = angAt(TABLES[i], r), a1 = angAt(TABLES[i + 1], r);
    if (th <= a0 + 1e-6 && (th >= a1 || i === 3)) {
      wi = i;
      const g = Math.max(1e-3, a0 - a1);
      u = clamp((a0 - th) / g, -0.5, 1.5);
      gapW = g * r;
      break;
    }
    if (i === 0 && th > a0) { wi = 0; u = -0.2; gapW = 4; break; }
  }
  const thick = lerp(0.5, 0.26, smooth(4, 30, r));
  // main veins radiating from the wrist, gently wavering
  let vein = 0;
  const vf = VEINS[wi];
  for (let k = 0; k < vf.length; k++) {
    const wob = (fbm(r * 0.14 + k * 9, wi * 5 + 2, seed + k) - 0.5) * 0.12;
    const dv = Math.abs(u - vf[k] - wob) * gapW;
    vein = Math.max(vein, Math.exp(-((dv / thick) ** 2)) * smooth(2, 5, r));
  }
  // cross veins along wobbling arcs
  let cross = 0;
  const cr = CROSS[wi];
  for (let k = 0; k < cr.length; k++) {
    const wob = (fbm(u * 2.5 + k * 5, wi * 3 + 1, seed + 20 + k) - 0.5) * 3.4 + Math.sin(u * 6.28 + k * 1.7) * 0.8;
    const dr = Math.abs(r - (cr[k] + wob));
    cross = Math.max(cross, Math.exp(-((dr / (thick * 0.75)) ** 2)) * smooth(0.04, 0.22, u) * smooth(0.96, 0.78, u));
  }
  // small branches that leave the main veins diagonally
  const bph = (u * 9 + r * 0.11) % 1;
  const bq = (Math.abs(bph - 0.5) * gapW * 0.2) / 0.22;
  const bGate = smooth(0.58, 0.82, fbm(r * 0.45 + wi * 7, u * 5, seed + 40));
  const branch = Math.exp(-(bq * bq)) * bGate * smooth(0.12, 0.4, u) * smooth(0.88, 0.6, u) * 0.7;
  const veins = clamp(Math.max(vein, cross * 0.75, branch));

  // heat: hot spots along the veins that fade towards the open membrane
  const heatN = fbm(x * 0.2 + 4, y * 0.2 + 9, seed + 60);
  const heat = smooth(0.4, 0.75, heatN) * (0.45 + 0.55 * smooth(3, 16, r));

  // membrane body: dark near the bones, lighter in the stretched centre of each panel, radial leather streaks
  const panel = Math.sin(clamp(u, 0, 1) * Math.PI);
  let col = mixc(C.memDark, C.memMid, smooth(0.0, 0.8, occl));
  col = mixc(col, C.memLight, panel * smooth(0.25, 1.0, occl) * 0.78);
  const streak = fbm(th * 22 + 3, r * 0.09, seed + 3) - 0.5;
  const mott = fbm(x * 0.3, y * 0.3, seed) - 0.5;
  col = addc(col, [70, 10, 14], streak * 0.55 + mott * 0.3);
  let alpha = 238 - panel * smooth(0.3, 1, occl) * 34;

  // veins
  const veinCol = mixc(C.memGlow, C.ember, heat * 0.9);
  col = mixc(col, veinCol, veins * (0.6 + 0.4 * heat));
  alpha = Math.max(alpha, veins * 252);
  col = mixc(col, C.red, heat * panel * 0.1);

  // glowing rim on the scalloped edge
  let dEdge = 99;
  for (const l of EDGE_LINES) dEdge = Math.min(dEdge, distPoly(p, l, false));
  const rim = smooth(1.7, 0.0, dEdge);
  const rimCore = smooth(0.62, 0.0, dEdge);
  col = mixc(col, C.red, rim * 0.6);
  col = mixc(col, C.ember, rimCore * (0.7 + 0.3 * heat));
  col = mixc(col, C.hot, smooth(0.25, 0.0, dEdge) * heat * 0.6);
  alpha = Math.max(alpha, rim * 255);

  // dark leathery underside next to the bones
  col = mixc(col, C.void, (1 - occl) * 0.4);

  // sparse ember specks
  const qx = Math.floor(x * 2.2), qy = Math.floor(y * 2.2);
  if (inside && h2(qx, qy, seed + 77) > 0.9965) { col = mixc(col, C.hot, 0.9); alpha = 255; }

  return [col[0], col[1], col[2], alpha * alphaEdge];
}

/* ---------------------------------------------------------------- the wing */
function build() {
  addGroup('wing', null, [0, 0, 0]);
  addGroup('seg1', 'wing', [S[0], S[1], zs(S[0], S[1])]);
  addGroup('seg2', 'seg1', [E[0], E[1], zs(E[0], E[1])]);
  addGroup('seg3', 'seg2', [WR[0], WR[1], zs(WR[0], WR[1])]);
  FINGERS.forEach((f) => addGroup(f.id, 'seg3', [WR[0], WR[1], zs(WR[0], WR[1])]));

  const bone = (name, group, a, b, w, d, extra = {}) => {
    boneSegs.push({ a, b, hw: w / 2 });
    return seg3d({ name, group, a, b, w, d, kind: 'bone', paint: 'bone', seed: cubes.length * 7 + 3, ext0: w * 0.35, ext1: w * 0.35, ...extra });
  };
  const spike = (name, group, a, angDeg, len, w, d, extra = {}) => {
    const b = [a[0] + Math.cos(angDeg * D) * len, a[1] + Math.sin(angDeg * D) * len];
    return seg3d({ name, group, a, b, w, d, kind: 'barb', paint: extra.paint || 'barb', seed: cubes.length * 5 + 1, ...extra });
  };

  // ---- seg1: shoulder and upper arm
  bone('shoulder', 'seg1', [0.9, -0.2], [1.7, 2.2], 3.4, 3.2, { ext0: 0.2, ext1: 0.4 });
  bone('humerus', 'seg1', [1.0, 1.6], [E[0] - 0.4, E[1] - 0.8], 3.0, 2.8);
  spike('humerus_barb_a', 'seg1', [2.0, 4.0], 78, 3.2, 1.2, 1.2, { zoff: 0.5 });
  spike('humerus_barb_b', 'seg1', [4.0, 7.6], 70, 2.8, 1.1, 1.1, { zoff: 0.5 });

  // ---- seg2: elbow and forearm
  seg3d({ name: 'elbow', group: 'seg2', a: [E[0] - 1.1, E[1] - 1.1], b: [E[0] + 1.3, E[1] + 1.3], w: 3.7, d: 3.4, kind: 'knob', paint: 'knob', seed: 41 });
  spike('elbow_spike', 'seg2', [E[0] - 0.1, E[1] + 1.0], 92, 6.0, 1.5, 1.5, { zoff: 0.3 });
  const M = [(E[0] + WR[0]) / 2 + 0.3, (E[1] + WR[1]) / 2 - 0.1];
  bone('forearm_a', 'seg2', [E[0] + 0.6, E[1] + 0.3], M, 2.7, 2.5);
  bone('forearm_b', 'seg2', M, [WR[0] - 1.2, WR[1] - 0.5], 2.2, 2.1);
  spike('forearm_barb_a', 'seg2', [10.2, 12.4], 70, 2.8, 1.1, 1.1, { zoff: 0.5 });
  spike('forearm_barb_b', 'seg2', [13.8, 14.1], 62, 2.8, 1.1, 1.1, { zoff: 0.5 });

  // ---- seg3: wrist, thumb claw
  seg3d({ name: 'wrist', group: 'seg3', a: [WR[0] - 1.4, WR[1] - 1.2], b: [WR[0] + 1.5, WR[1] + 1.3], w: 4.0, d: 3.4, kind: 'knob', paint: 'knob', seed: 52 });
  const th0 = [WR[0] - 0.4, WR[1] + 1.0], th1 = [WR[0] - 1.2, WR[1] + 3.8], th2 = [WR[0] - 0.6, WR[1] + 5.9], th3 = [WR[0] + 1.6, WR[1] + 7.0];
  seg3d({ name: 'thumb_a', group: 'seg3', a: th0, b: th1, w: 1.8, d: 1.7, kind: 'bone', paint: 'bone', seed: 61, ext1: 0.5 });
  seg3d({ name: 'thumb_b', group: 'seg3', a: th1, b: th2, w: 1.4, d: 1.4, kind: 'bone', paint: 'bone', seed: 62, ext0: 0.4, ext1: 0.4 });
  seg3d({ name: 'thumb_claw', group: 'seg3', a: th2, b: th3, w: 1.0, d: 1.0, kind: 'barb', paint: 'claw', seed: 63, ext0: 0.4 });

  // ---- fingers (each a chain of tapering cubes inside its own f_ group)
  FINGERS.forEach((f, fi) => {
    for (let i = 0; i < 3; i++) {
      bone(`${f.id}_${i + 1}`, f.id, f.pts[i], f.pts[i + 1], f.w[i], f.d[i], { zoff: 0 });
    }
    // tip claw continues the last direction and curls slightly
    const a = f.pts[3], b0 = f.pts[2];
    const ang = Math.atan2(a[1] - b0[1], a[0] - b0[0]) / D + (fi === 0 ? -14 : 10);
    spike(`${f.id}_tip`, f.id, a, ang, f.tip, f.w[3], f.d[3], { paint: 'claw', ext0: 0.4 });
    // barbs on the leading side of the two inner joints
    for (let j = 1; j <= 2; j++) {
      const p = f.pts[j], q = f.pts[j - 1];
      const dir = Math.atan2(p[1] - q[1], p[0] - q[0]) / D;
      const lead = fi === 0 ? 40 : 34;
      spike(`${f.id}_barb_${j}`, f.id, [p[0], p[1]], dir + lead, 2.9 - j * 0.25 + (fi === 0 ? 0.5 : 0), 0.85, 0.85, { zoff: 0.55 });
    }
  });

  // ---- membrane: a fan of thin translucent strips around the wrist
  const hi = Math.atan2(FINGERS[0].pts[1][1] - WR[1], FINGERS[0].pts[1][0] - WR[0]) / D + 1;
  const lo = Math.atan2(E[1] - WR[1], E[0] - WR[0]) / D - 1;
  const N = 26;
  const step = (hi - lo) / N;
  let idx = 0;
  const strips = [];
  for (let k = 0; k < N; k++) {
    const ang = (hi - (k + 0.5) * step) * D;
    const dir = [Math.cos(ang), Math.sin(ang)];
    const nrm = [-dir[1], dir[0]];
    // furthest radius where the strip band still touches the outline
    let rmax = 0;
    const wGuess = 3.6;
    for (let r = 1.5; r < 48; r += 0.25) {
      for (let l = -1; l <= 1; l += 0.5) {
        const q = [WR[0] + dir[0] * r + nrm[0] * l * wGuess * 0.5, WR[1] + dir[1] * r + nrm[1] * l * wGuess * 0.5];
        if (pointInPoly(q, OUTLINE)) rmax = Math.max(rmax, r);
      }
    }
    if (rmax <= 2) continue;
    const rOut = rmax + 0.4;
    const rIn = 2.0;
    const w = clamp(rOut * step * D * 1.14, 2.0, 4.4);
    const layer = [-0.22, 0, 0.22][idx % 3];
    idx++;
    const a = [WR[0] + dir[0] * rIn, WR[1] + dir[1] * rIn];
    const b = [WR[0] + dir[0] * rOut, WR[1] + dir[1] * rOut];
    // nearest finger (by chord angle) owns the strip so that it sways with it
    let owner = 3, best = 1e9;
    FINGERS.forEach((f, i) => {
      const t = tipOf(f);
      const fa = Math.atan2(t[1] - WR[1], t[0] - WR[0]);
      const dd = Math.abs(fa - ang);
      if (dd < best) { best = dd; owner = i; }
    });
    strips.push({ a, b, w, layer, owner, k });
  }
  for (const s of strips) {
    seg3d({
      name: `mem_${String(s.k + 1).padStart(2, '0')}`, group: FINGERS[s.owner].id, a: s.a, b: s.b, w: s.w, d: 0.4,
      kind: 'membrane', paint: 'membrane', faces: 'plane', zoff: s.layer, seed: 100 + s.k,
    });
  }
}

/* ---------------------------------------------------------- UV atlas layout */
const islands = [];
function addIsland(w, h, fn) { islands.push({ w: Math.max(2, w), h: Math.max(2, h), fn }); return islands.length - 1; }
let swNone = -1;

function layout() {
  // shared swatches: end faces of bones/barbs and a fully transparent one for the review copy
  const swEnd = addIsland(4, 4, (iu, iv) => { const c = mixc(C.boneDark, C.bone, 0.4 + 0.3 * iv); return [c[0], c[1], c[2], 255]; });
  const swEndBarb = addIsland(4, 4, (iu, iv) => { const c = mixc(C.bone, C.ember, 0.35 + 0.3 * iv); return [c[0], c[1], c[2], 255]; });
  swNone = addIsland(4, 4, () => [C.memDark[0], C.memDark[1], C.memDark[2], 0]);
  for (const c of cubes) {
    if (c.paint === 'membrane') {
      const w = Math.ceil(c.len * DENS_MEM), h = Math.ceil(c.w * DENS_MEM);
      c.island = addIsland(w, h, (iu, iv) => {
        const [x, y] = cubeXY(c, iu * c.len, c.w / 2 - iv * c.w);
        return membraneShade(x, y, 11);
      });
      c.endIsland = null;
    } else {
      const fn = SHADES[c.paint] || boneShade;
      const w = Math.ceil(c.len * DENS_BONE), h = Math.max(3, Math.ceil(c.w * DENS_BONE));
      c.island = addIsland(w, h, (iu, iv) => fn(c, iu, iv));
      c.endIsland = c.kind === 'barb' ? swEndBarb : swEnd;
    }
  }
  // shelf packing with a 1 px gutter
  const order = islands.map((_, i) => i).sort((a, b) => islands[b].h - islands[a].h || islands[b].w - islands[a].w);
  let x = 0, y = 0, rowH = 0;
  for (const i of order) {
    const isl = islands[i];
    const gw = isl.w + 2, gh = isl.h + 2;
    if (x + gw > TEX) { x = 0; y += rowH; rowH = 0; }
    if (y + gh > TEX) throw new Error(`atlas overflow at island ${i}`);
    isl.x = x + 1; isl.y = y + 1;
    x += gw; rowH = Math.max(rowH, gh);
  }
  return y + rowH;
}

function paintAtlas() {
  const png = new PNG({ width: TEX, height: TEX });
  for (const isl of islands) {
    for (let py = -1; py <= isl.h; py++) {
      for (let px = -1; px <= isl.w; px++) {
        // gutter pixels repeat the nearest edge texel (avoids bleeding)
        const cx = clamp(px, 0, isl.w - 1), cy = clamp(py, 0, isl.h - 1);
        const [r, g, b, a] = isl.fn((cx + 0.5) / isl.w, (cy + 0.5) / isl.h);
        const o = ((isl.y + py) * TEX + (isl.x + px)) * 4;
        png.data[o] = clamp(Math.round(r), 0, 255);
        png.data[o + 1] = clamp(Math.round(g), 0, 255);
        png.data[o + 2] = clamp(Math.round(b), 0, 255);
        png.data[o + 3] = clamp(Math.round(a), 0, 255);
      }
    }
  }
  return png;
}

/* ------------------------------------------------------------- bbmodel out */
const uuid = () => crypto.randomUUID();
function faceUV(isl, flipU) {
  const u0 = isl.x, v0 = isl.y, u1 = isl.x + isl.w, v1 = isl.y + isl.h;
  return flipU ? [u1, v0, u0, v1] : [u0, v0, u1, v1];
}
const r4 = (v) => Math.round(v * 10000) / 10000;

/** ghost = deleted faces are kept but point at a transparent swatch (review copy for bb-render) */
function buildModel(texPng, ghost) {
  const groupObjs = groupsDef.map((g) => ({
    name: g.name, uuid: uuid(), origin: g.origin.map(r4), rotation: [0, 0, 0],
    export: true, visibility: true, mirror_uv: false, reset: false, color: 0, autouv: 0, shade: true, locked: false,
    _parent: g.parent, _children: [],
  }));
  const gmap = Object.fromEntries(groupObjs.map((g) => [g.name, g]));
  const elements = cubes.map((c) => {
    const isl = islands[c.island];
    const faces = {};
    if (c.faces === 'plane') {
      faces.north = { uv: faceUV(isl, true), texture: 0 };
      faces.south = { uv: faceUV(isl, false), texture: 0 };
      if (ghost) for (const f of ['east', 'west', 'up', 'down']) faces[f] = { uv: faceUV(islands[swNone], false), texture: 0 };
    } else {
      const end = islands[c.endIsland];
      faces.north = { uv: faceUV(isl, true), texture: 0 };
      faces.east = { uv: faceUV(end, false), texture: 0 };
      faces.south = { uv: faceUV(isl, false), texture: 0 };
      faces.west = { uv: faceUV(end, false), texture: 0 };
      faces.up = { uv: faceUV(isl, false), texture: 0 };
      faces.down = { uv: faceUV(isl, false), texture: 0 };
    }
    const el = {
      name: c.name, box_uv: false, rescale: false, locked: false, light_emission: 0, render_order: 'default',
      allow_mirror_modeling: true, from: c.from.map(r4), to: c.to.map(r4), autouv: 0, color: 0,
      origin: c.origin.map(r4), rotation: c.rot.map((v) => r4(v)), faces, type: 'cube', uuid: uuid(),
    };
    gmap[c.group]._children.push(el.uuid);
    return el;
  });
  // outliner
  const nodeOf = (g) => ({
    uuid: g.uuid, isOpen: false,
    children: [
      ...groupObjs.filter((x) => x._parent === g.name).map(nodeOf),
      ...g._children,
    ],
  });
  const outliner = groupObjs.filter((g) => !g._parent).map(nodeOf);
  const groups = groupObjs.map((g) => { const o = { ...g }; delete o._parent; delete o._children; return o; });
  const buf = PNG.sync.write(texPng);
  return {
    meta: { format_version: '5.0', model_format: 'free', box_uv: false },
    name: 'daemon', model_identifier: '', visible_box: [1, 1, 0],
    resolution: { width: TEX, height: TEX },
    elements, groups, outliner,
    textures: [{
      uuid: uuid(), name: 'daemon.png', source: 'data:image/png;base64,' + buf.toString('base64'),
      width: TEX, height: TEX, uv_width: TEX, uv_height: TEX, folder: '', namespace: '', id: '0',
      particle: false, use_as_default: false, layers_enabled: false, sync_to_project: '', render_mode: 'default',
      render_sides: 'auto', frame_time: 1, frame_order_type: 'loop', frame_order: '', frame_interpolate: false,
      visible: true, internal: true, saved: false,
    }],
    variable_placeholders: '', variable_placeholder_buttons: [], timeline_setups: [], unhandled_root_fields: {},
  };
}

/* -------------------------------------------------------- flat 2D composite */
function flatView(sc = 16) {
  const x0 = -3, x1 = 40, y0 = -15, y1 = 26;
  const W = Math.round((x1 - x0) * sc), H = Math.round((y1 - y0) * sc);
  const png = new PNG({ width: W, height: H });
  const order = [...cubes].sort((a, b) => (a.kind === 'membrane' ? 0 : 1) - (b.kind === 'membrane' ? 0 : 1));
  for (let py = 0; py < H; py++) {
    for (let px = 0; px < W; px++) {
      const x = x0 + (px + 0.5) / sc, y = y1 - (py + 0.5) / sc;
      let col = [24, 26, 32], a = 1;
      void a;
      for (const c of order) {
        const [lx, ly] = cubeLocal(c, x, y);
        if (lx < 0 || lx > c.len || Math.abs(ly) > c.w / 2) continue;
        const iu = lx / c.len, iv = (c.w / 2 - ly) / c.w;
        let rgba;
        if (c.paint === 'membrane') rgba = membraneShade(x, y, 11);
        else rgba = (SHADES[c.paint] || boneShade)(c, iu, iv);
        const al = rgba[3] / 255;
        col = [col[0] * (1 - al) + rgba[0] * al, col[1] * (1 - al) + rgba[1] * al, col[2] * (1 - al) + rgba[2] * al];
      }
      const o = (py * W + px) * 4;
      png.data[o] = Math.round(col[0]); png.data[o + 1] = Math.round(col[1]); png.data[o + 2] = Math.round(col[2]); png.data[o + 3] = 255;
    }
  }
  return png;
}

/* --------------------------------------------------------------- preview */
function boundsOfModel(skipMembrane = false) {
  let mn = [1e9, 1e9, 1e9], mx = [-1e9, -1e9, -1e9];
  for (const c of cubes) {
    if (skipMembrane && c.kind === 'membrane') continue; // strips overshoot the visible membrane (alpha cut)
    const cp = Math.cos(c.ph), sp = Math.sin(c.ph), ct = Math.cos(c.th), st = Math.sin(c.th);
    for (const lx of [0, c.len]) for (const ly of [-c.w / 2, c.w / 2]) for (const lz of [-c.d / 2, c.d / 2]) {
      // local -> rotate about Y by -ph, then about Z by th
      const rx = lx * cp - lz * sp, rz = lx * sp + lz * cp, ry = ly;
      const wx = c.P[0] + ct * rx - st * ry, wy = c.P[1] + st * rx + ct * ry, wz = c.P[2] + rz;
      mn = [Math.min(mn[0], wx), Math.min(mn[1], wy), Math.min(mn[2], wz)];
      mx = [Math.max(mx[0], wx), Math.max(mx[1], wy), Math.max(mx[2], wz)];
    }
  }
  return { mn, mx };
}

function makePreview(src) {
  const img = PNG.sync.read(fs.readFileSync(src));
  const { width: W, height: H, data } = img;
  // bounding box of visible pixels
  let x0 = W, y0 = H, x1 = -1, y1 = -1;
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    if (data[(y * W + x) * 4 + 3] > 12) { x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x + 1); y1 = Math.max(y1, y + 1); }
  }
  if (x1 < 0) throw new Error('render is empty');
  // opening of the alpha mask removes 1-2 px render artefacts (edges of fully transparent strip overshoot)
  {
    const R = 3;
    const mask = new Uint8Array(W * H);
    for (let i = 0; i < W * H; i++) mask[i] = data[i * 4 + 3] > 12 ? 1 : 0;
    const pass = (src, grow) => {
      const tmp = new Uint8Array(W * H), dst = new Uint8Array(W * H);
      for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
        let v = grow ? 0 : 1;
        for (let k = -R; k <= R; k++) { const xx = clamp(x + k, 0, W - 1); if (grow ? src[y * W + xx] : !src[y * W + xx]) { v = grow ? 1 : 0; break; } }
        tmp[y * W + x] = v;
      }
      for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
        let v = grow ? 0 : 1;
        for (let k = -R; k <= R; k++) { const yy = clamp(y + k, 0, H - 1); if (grow ? tmp[yy * W + x] : !tmp[yy * W + x]) { v = grow ? 1 : 0; break; } }
        dst[y * W + x] = v;
      }
      return dst;
    };
    const opened = pass(pass(mask, false), true);
    x0 = W; y0 = H; x1 = -1; y1 = -1;
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      const i = y * W + x;
      if (!opened[i]) { data[i * 4 + 3] = 0; continue; }
      x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x + 1); y1 = Math.max(y1, y + 1);
    }
    // gentle brightening so the dark wing reads on the menu background
    for (let i = 0; i < W * H; i++) for (let k = 0; k < 3; k++) data[i * 4 + k] = Math.round(255 * Math.pow(data[i * 4 + k] / 255, 0.8));
  }
  const { mn, mx } = boundsOfModel(true);
  // linear map model (x,y) -> source pixel using the bbox of the opaque pixels (orthographic back view)
  const spx = (x1 - x0) / (mx[0] - mn[0]);
  const spy = (y1 - y0) / (mx[1] - mn[1]);
  const sU = (spx + spy) / 2; // source pixels per unit
  const rootSrc = [x0 + (0 - mn[0]) * spx, y1 - (0 - mn[1]) * spy];
  const OUT = 256;
  const rootT = [0.12 * OUT, 0.55 * OUT];
  const m = 0.05 * OUT;
  const sT = Math.min((OUT - m - rootT[0]) / mx[0], (rootT[1] - m) / mx[1], (OUT - m - rootT[1]) / -mn[1]);
  const k = sT / sU; // target px per source px
  const out = new PNG({ width: OUT, height: OUT });
  // area-averaged resample (premultiplied)
  for (let ty = 0; ty < OUT; ty++) for (let tx = 0; tx < OUT; tx++) {
    const sx0 = rootSrc[0] + (tx - rootT[0]) / k, sx1 = rootSrc[0] + (tx + 1 - rootT[0]) / k;
    const sy0 = rootSrc[1] + (ty - rootT[1]) / k, sy1 = rootSrc[1] + (ty + 1 - rootT[1]) / k;
    let r = 0, g = 0, b = 0, a = 0, wsum = 0;
    for (let sy = Math.floor(sy0); sy < Math.ceil(sy1); sy++) for (let sx = Math.floor(sx0); sx < Math.ceil(sx1); sx++) {
      wsum += 1;
      if (sx < 0 || sy < 0 || sx >= W || sy >= H) continue;
      const o = (sy * W + sx) * 4;
      const al = data[o + 3] / 255;
      r += data[o] * al; g += data[o + 1] * al; b += data[o + 2] * al; a += al;
    }
    const o = (ty * OUT + tx) * 4;
    if (wsum > 0 && a > 0) {
      out.data[o] = Math.round(r / a); out.data[o + 1] = Math.round(g / a); out.data[o + 2] = Math.round(b / a);
      out.data[o + 3] = Math.round((255 * a) / wsum);
    }
  }
  fs.mkdirSync(path.dirname(OUT_PREVIEW), { recursive: true });
  fs.writeFileSync(OUT_PREVIEW, PNG.sync.write(out));
  console.log(`preview written (${OUT}x${OUT}), scale ${sT.toFixed(2)} px/unit, model bounds`, mn.map((v) => v.toFixed(1)).join(','), mx.map((v) => v.toFixed(1)).join(','));
}

/* ------------------------------------------------------------------- main */
function main() {
  const [cmd, arg] = process.argv.slice(2);
  build();
  if (cmd === 'preview') { makePreview(arg); return; }
  const used = layout();
  const png = paintAtlas();
  const model = buildModel(png, false);
  fs.mkdirSync(path.dirname(OUT_TEX), { recursive: true });
  fs.mkdirSync(path.dirname(OUT_MODEL), { recursive: true });
  fs.writeFileSync(OUT_TEX, PNG.sync.write(png));
  fs.writeFileSync(OUT_MODEL, JSON.stringify(model));
  const wi = process.argv.indexOf('--work');
  const work = wi > 0 ? process.argv[wi + 1] : (cmd === 'flat' ? arg : null);
  if (work) {
    fs.mkdirSync(work, { recursive: true });
    fs.writeFileSync(path.join(work, 'daemon.bbmodel'), JSON.stringify(model));
    fs.writeFileSync(path.join(work, 'daemon_view.bbmodel'), JSON.stringify(buildModel(png, true)));
    fs.writeFileSync(path.join(work, 'daemon_tex.png'), PNG.sync.write(png));
    if (cmd === 'flat') fs.writeFileSync(path.join(work, 'daemon_flat.png'), PNG.sync.write(flatView(16)));
  }
  const b = boundsOfModel();
  console.log(`cubes: ${cubes.length}, islands: ${islands.length}, atlas rows used: ${used}/${TEX}`);
  console.log('bounds min', b.mn.map((v) => v.toFixed(1)).join(','), 'max', b.mx.map((v) => v.toFixed(1)).join(','));
}
main();
