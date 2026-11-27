'use strict';
/**
 * DeuClient wing style "engel" (classic angel wings).
 *
 * Generates, from ONE layout description:
 *   - the texture atlas  (textures/cosmetics/wings/engel.png, 256x256 RGBA)
 *   - the Blockbench model (models/wings/engel.bbmodel, free format, per-face UV, right wing only)
 *   - an optional 2D preview (--preview=<png>) used while tuning the silhouette
 *
 * Coordinates (see tools/wings/SPEC.md): +X outward, +Y up, +Z backwards, 16 units = 1 block.
 *
 * Usage:  node tools/wings/engel.cjs [--also=<extra model path>] [--preview=<png>] [--scale=<texels per unit>]
 */
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { PNG } = require('pngjs');

const ROOT = path.resolve(__dirname, '..', '..');
const ASSETS = path.join(ROOT, 'mod', 'mc26.3', 'src', 'main', 'resources', 'assets', 'deucore');
const OUT_MODEL = path.join(ASSETS, 'models', 'wings', 'engel.bbmodel');
const OUT_TEX = path.join(ASSETS, 'textures', 'cosmetics', 'wings', 'engel.png');
const OUT_MENU = path.join(ASSETS, 'textures', 'cosmetics', 'ui', 'wings_engel.png');
const TEX = 256;

const args = Object.fromEntries(process.argv.slice(2).map((a) => {
  const m = a.match(/^--([^=]+)=?(.*)$/);
  return m ? [m[1], m[2] || true] : [a, true];
}));

// ---------------------------------------------------------------------------------------------
// small helpers
// ---------------------------------------------------------------------------------------------
const clamp = (x, a, b) => Math.max(a, Math.min(b, x));
const lerp = (a, b, t) => a + (b - a) * t;
const smooth = (a, b, x) => { const t = clamp((x - a) / (b - a), 0, 1); return t * t * (3 - 2 * t); };
const rad = (d) => (d * Math.PI) / 180;
const mix = (c1, c2, t) => [lerp(c1[0], c2[0], t), lerp(c1[1], c2[1], t), lerp(c1[2], c2[2], t)];
const scaleCol = (c, k) => [c[0] * k, c[1] * k, c[2] * k];
const rnd = (seed) => {
  let s = seed >>> 0;
  return () => {
    s = (s + 0x6d2b79f5) >>> 0;
    let t = s;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
};
const uuidOf = (name) => {
  const h = crypto.createHash('md5').update('deuclient-engel:' + name).digest('hex');
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-a${h.slice(17, 20)}-${h.slice(20, 32)}`;
};
const r2 = (n) => Math.round(n * 1000) / 1000;

// ---------------------------------------------------------------------------------------------
// palette (spirit of WINGS.engel: base #8f94a8, mid #e7e9f2, light #ffffff, glow #ffe8a3)
// ---------------------------------------------------------------------------------------------
const C = {
  white: [255, 255, 255],
  pearl: [240, 241, 249],
  mist: [217, 221, 236],
  shade: [233, 237, 248],
  cream: [250, 241, 214],
  paleGold: [255, 233, 168],
  champagne: [250, 228, 160],
  gold: [247, 213, 118],
  deepGold: [217, 167, 66],
  amber: [176, 128, 44],
  ivory: [250, 243, 224],
  boneShade: [208, 190, 148],
};

// ---------------------------------------------------------------------------------------------
// arm chain (rest pose, XY plane). Bones are rotated cubes, the seg groups hold the pivots.
// ---------------------------------------------------------------------------------------------
const GS = 0.88; // global size factor of the arm and the feather tips (keeps the model inside the spec size)
const gs = (p) => [p[0] * GS, p[1] * GS];
// the arm root sits 2 units out from the attach point so the shoulder gem never crosses the spine (mirrored pair)
const J = { S: [2, 0], E: gs([5, 9.5]), W: gs([15.5, 14.2]), H: gs([22.5, 11.8]) };
const ZB = 3.4; // bone centre depth
const SEG = [
  { name: 'seg1', a: J.S, b: J.E, t: 3.0, d: 2.8 },
  { name: 'seg2', a: J.E, b: J.W, t: 2.6, d: 2.4 },
  { name: 'seg3', a: J.W, b: J.H, t: 2.1, d: 2.0 },
];
for (const s of SEG) {
  s.len = Math.hypot(s.b[0] - s.a[0], s.b[1] - s.a[1]);
  s.ang = (Math.atan2(s.b[1] - s.a[1], s.b[0] - s.a[0]) * 180) / Math.PI;
}
const segPoint = (si, t) => [lerp(SEG[si].a[0], SEG[si].b[0], t), lerp(SEG[si].a[1], SEG[si].b[1], t)];

// ---------------------------------------------------------------------------------------------
// registry of texture islands, groups and cubes
// ---------------------------------------------------------------------------------------------
const islands = [];
const groups = [];
const elements = [];
const placed = []; // flat feathers / bones for the 2D preview and the glow mask

function isl(wu, hu, paint, sMul = 1) {
  const o = { wu, hu, paint, sMul, x: 0, y: 0, w: 0, h: 0, data: null };
  islands.push(o);
  return o;
}

function addGroup(name, origin, rotation, parent) {
  const g = { name, origin: origin.map(r2), rotation, parent };
  groups.push(g);
  return g;
}

// ---------------------------------------------------------------------------------------------
// feather outline
// ---------------------------------------------------------------------------------------------
const SHAPES = {
  primary: { rootW: 0.2, maxAt: 0.3, tipStart: 0.7, tipExp: 2.1, tipRound: 0.5, taper: 0.1 },
  secondary: { rootW: 0.22, maxAt: 0.3, tipStart: 0.76, tipExp: 2.1, tipRound: 0.5, taper: 0.06 },
  covert: { rootW: 0.3, maxAt: 0.26, tipStart: 0.6, tipExp: 2.2, tipRound: 0.5, taper: 0.0 },
  small: { rootW: 0.36, maxAt: 0.22, tipStart: 0.52, tipExp: 2.2, tipRound: 0.5, taper: 0.0 },
};
function hw(t, sh) {
  if (t < 0 || t > 1) return -1;
  const rootPart = sh.rootW + (1 - sh.rootW) * smooth(0, sh.maxAt, t);
  let tipPart = 1;
  if (t > sh.tipStart) {
    const u = (t - sh.tipStart) / (1 - sh.tipStart);
    tipPart = Math.pow(Math.max(0, 1 - Math.pow(u, sh.tipExp)), sh.tipRound);
  }
  return rootPart * tipPart * (1 - sh.taper * t);
}

// ---------------------------------------------------------------------------------------------
// painters
// ---------------------------------------------------------------------------------------------
function paintFeather(il, st) {
  const { w: pw, h: ph } = il;
  const data = Buffer.alloc(pw * ph * 4);
  const R = rnd(st.seed);
  const tone = st.tone;
  for (let y = 0; y < ph; y++) {
    for (let x = 0; x < pw; x++) {
      let cnt = 0;
      for (let sy = 0; sy < 3; sy++) {
        for (let sx = 0; sx < 3; sx++) {
          const tt = (y + (sy + 0.5) / 3) / ph;
          const xx = (((x + (sx + 0.5) / 3) / pw) - 0.5) * 2;
          if (Math.abs(xx) <= hw(tt, st.shape)) cnt++;
        }
      }
      if (cnt === 0) continue;
      const cov = cnt / 9;
      const t = (y + 0.5) / ph;
      const xn = (((x + 0.5) / pw) - 0.5) * 2;
      const h = Math.max(hw(t, st.shape), 0.04);
      const ax = Math.min(Math.abs(xn), h);
      const edge = Math.max(0, h - ax) * (pw / 2); // texels from the side edge
      const rel = ax / h; // 0 at shaft, 1 at edge

      // body: shaded root, white middle
      let col = mix(C.shade, C.white, smooth(0.0, 0.2, t));
      col = mix(col, C.mist, tone.dim);
      // pale gold towards the tip and the edges
      const g = tone.gold * smooth(0.5, 1.0, t) * (0.3 + 0.7 * Math.pow(rel, 1.6));
      col = mix(col, C.champagne, clamp(g, 0, 1) * 0.7);
      // barbs (long soft veins running from the shaft towards the tip)
      const bx = (t * ph - ax * (pw / 2) * 0.9) / 5.2;
      col = scaleCol(col, 1 + tone.barb * Math.sin(bx * Math.PI * 2));
      // shaft
      const sd = ax * (pw / 2);
      if (sd < 0.8 && t > 0.03) {
        const k = (1 - smooth(0.72, 0.99, t)) * 0.85;
        col = mix(col, [236, 216, 160], k);
      } else if (sd < 1.7 && t > 0.1) {
        col = scaleCol(col, 1 - 0.03 * (1 - smooth(0.7, 1, t)));
      }
      // soft inner glow plus a thin golden outline that separates the overlapping layers
      col = mix(col, C.paleGold, 0.3 * tone.rim * Math.pow(clamp(1 - edge / 3.2, 0, 1), 1.5));
      const rim = Math.pow(clamp(1 - edge / tone.rimW, 0, 1), 1.0) * tone.rim;
      col = mix(col, C.gold, rim * 0.85);
      // tip glow
      col = mix(col, C.paleGold, 0.4 * smooth(0.88, 1.0, t) * tone.gold);
      // root shadow where covered by the next row
      col = mix(col, C.shade, tone.rootShade * (1 - smooth(0.0, 0.28, t)));

      let a = 255 * cov;
      const i = (y * pw + x) * 4;
      data[i] = clamp(Math.round(col[0]), 0, 255);
      data[i + 1] = clamp(Math.round(col[1]), 0, 255);
      data[i + 2] = clamp(Math.round(col[2]), 0, 255);
      data[i + 3] = clamp(Math.round(a), 0, 255);
    }
  }
  // sparkles (tiny golden-white stars)
  if (tone.sparkle > 0 && pw >= 9 && ph >= 24) {
    const n = Math.floor(tone.sparkle + R());
    for (let k = 0; k < n; k++) {
      const t = 0.3 + R() * 0.6;
      const y = Math.floor(t * ph);
      const hh = hw(t, st.shape);
      const x = Math.floor(((R() * 2 - 1) * hh * 0.7 * 0.5 + 0.5) * pw);
      const set = (xx, yy, k2) => {
        if (xx < 0 || yy < 0 || xx >= pw || yy >= ph) return;
        const i = (yy * pw + xx) * 4;
        if (data[i + 3] < 200) return;
        data[i] = Math.round(lerp(data[i], 255, k2));
        data[i + 1] = Math.round(lerp(data[i + 1], 250, k2));
        data[i + 2] = Math.round(lerp(data[i + 2], 225, k2));
      };
      set(x, y, 1);
      set(x - 1, y, 0.55); set(x + 1, y, 0.55); set(x, y - 1, 0.55); set(x, y + 1, 0.55);
    }
  }
  il.data = data;
}

function putPx(il, x, y, col, a = 255) {
  const i = (y * il.w + x) * 4;
  il.data[i] = clamp(Math.round(col[0]), 0, 255);
  il.data[i + 1] = clamp(Math.round(col[1]), 0, 255);
  il.data[i + 2] = clamp(Math.round(col[2]), 0, 255);
  il.data[i + 3] = clamp(Math.round(a), 0, 255);
}

// bone side faces: u along the bone (symmetric), v across (top = leading edge)
function paintBoneSide(il) {
  il.data = Buffer.alloc(il.w * il.h * 4);
  for (let y = 0; y < il.h; y++) {
    for (let x = 0; x < il.w; x++) {
      const u = (x + 0.5) / il.w;
      const v = (y + 0.5) / il.h;
      const cx = 1 - Math.pow(Math.abs(2 * v - 1), 2);
      let col = mix(C.boneShade, C.ivory, smooth(0, 0.85, cx));
      col = mix(col, C.white, 0.55 * Math.exp(-Math.pow((v - 0.3) / 0.13, 2)));
      const dEnd = Math.min(u, 1 - u) * il.w;
      for (const b of [0.1, 0.5, 0.9]) {
        const d = Math.abs(u - b) * il.w;
        if (d < 0.8) col = mix(col, C.deepGold, 0.9);
        else if (d < 1.8) col = mix(col, C.gold, 0.55);
      }
      if (dEnd < 1.2) col = mix(col, C.amber, 0.7);
      if (v < 0.16) col = mix(col, C.paleGold, 0.65 * (1 - v / 0.16));
      if (v > 0.9) col = mix(col, C.amber, 0.35);
      putPx(il, x, y, col);
    }
  }
}

function paintBoneEdge(il, leading) {
  il.data = Buffer.alloc(il.w * il.h * 4);
  for (let y = 0; y < il.h; y++) {
    for (let x = 0; x < il.w; x++) {
      const u = (x + 0.5) / il.w;
      const v = (y + 0.5) / il.h;
      let col = leading ? mix(C.gold, C.paleGold, smooth(0.15, 0.5, v) * (1 - smooth(0.5, 0.95, v))) : mix(C.boneShade, C.cream, 0.4);
      col = scaleCol(col, 1 + 0.06 * Math.sin(u * il.w * 1.4));
      if (Math.min(u, 1 - u) * il.w < 1) col = mix(col, C.amber, 0.6);
      putPx(il, x, y, col);
    }
  }
}

function paintGem(il, big) {
  il.data = Buffer.alloc(il.w * il.h * 4);
  for (let y = 0; y < il.h; y++) {
    for (let x = 0; x < il.w; x++) {
      const u = ((x + 0.5) / il.w) * 2 - 1;
      const v = ((y + 0.5) / il.h) * 2 - 1;
      const r = Math.hypot(u, v) / Math.SQRT2;
      let col = mix([255, 248, 222], C.gold, smooth(0.0, 0.8, r));
      col = mix(col, C.deepGold, smooth(0.65, 1.0, r) * 0.7);
      const hx = u + 0.3; const hy = v + 0.3;
      col = mix(col, C.white, 0.8 * Math.exp(-(hx * hx + hy * hy) / (big ? 0.07 : 0.1)));
      const ed = Math.min(x, y, il.w - 1 - x, il.h - 1 - y);
      if (ed < 1) col = mix(col, C.amber, 0.75);
      putPx(il, x, y, col);
    }
  }
}

function paintGold(il) {
  il.data = Buffer.alloc(il.w * il.h * 4);
  for (let y = 0; y < il.h; y++) {
    for (let x = 0; x < il.w; x++) {
      const v = (y + 0.5) / il.h;
      let col = mix(C.paleGold, C.deepGold, v * 0.7);
      const ed = Math.min(x, y, il.w - 1 - x, il.h - 1 - y);
      if (ed < 1) col = mix(col, C.amber, 0.6);
      putPx(il, x, y, col);
    }
  }
}

// ---------------------------------------------------------------------------------------------
// builders
// ---------------------------------------------------------------------------------------------
let featherCounter = 0;
const FT = 0.25; // feather thickness (z)

/**
 * root: [x,y], theta: direction of the feather in degrees from +X (counter clockwise, seen from +Z)
 */
function outlineMinX(root, phi, W, L, shape) {
  const c = Math.cos(rad(phi)); const s = Math.sin(rad(phi));
  let mn = 1e9;
  for (let k = 0; k <= 20; k++) {
    const t = k / 20;
    const h = (W / 2) * hw(t, shape);
    for (const lx of [-h, h]) {
      const X = root[0] + lx * c - (-t * L) * s;
      mn = Math.min(mn, X);
    }
  }
  return mn;
}

function addFeather(segIndex, root, z, theta, L, W, shapeName, tone, tag) {
  // keep every feather on the right side of the spine (x >= 0.3), slim it down where needed
  for (let n = 0; n < 40 && outlineMinX(root, theta + 90, W, L, SHAPES[shapeName]) < 0.3 && W > 1.6; n++) W -= 0.2;
  featherCounter += 1;
  const seed = featherCounter * 977 + 13;
  const name = `f_${String(featherCounter).padStart(2, '0')}`;
  const phi = theta + 90;
  const g = addGroup(name, [r2(root[0]), r2(root[1]), r2(z)], [0, 0, r2(phi)], SEG[segIndex].name);
  const il = isl(W, L, (i) => paintFeather(i, { shape: SHAPES[shapeName], tone, seed }));
  elements.push({
    name: `${tag}_${name.slice(2)}`,
    parent: g.name,
    from: [r2(root[0] - W / 2), r2(root[1] - L), r2(z - FT / 2)],
    to: [r2(root[0] + W / 2), r2(root[1]), r2(z + FT / 2)],
    origin: [r2(root[0]), r2(root[1]), r2(z)],
    rotation: [0, 0, 0],
    faces: { north: il, south: il },
    z,
    zf: z + FT / 2,
  });
  placed.push({ root, phi, W, L, z, il, shape: SHAPES[shapeName], order: placed.length });
}

function addBone(si) {
  const s = SEG[si];
  const cx = (s.a[0] + s.b[0]) / 2;
  const cy = (s.a[1] + s.b[1]) / 2;
  const len = s.len;
  const side = isl(len, s.t, paintBoneSide);
  const lead = isl(len, s.d, (i) => paintBoneEdge(i, true));
  const trail = isl(len, s.d, (i) => paintBoneEdge(i, false));
  const capA = isl(s.d, s.t, paintGold);
  const capB = isl(s.d, s.t, paintGold);
  elements.push({
    name: `bone_${s.name}`,
    parent: s.name,
    from: [r2(cx - len / 2), r2(cy - s.t / 2), r2(ZB - s.d / 2)],
    to: [r2(cx + len / 2), r2(cy + s.t / 2), r2(ZB + s.d / 2)],
    origin: [r2(cx), r2(cy), r2(ZB)],
    rotation: [0, 0, r2(s.ang)],
    faces: { north: side, south: side, up: lead, down: trail, east: capA, west: capB },
    z: ZB + 10,
    zf: ZB + s.d / 2,
  });
  placed.push({ bone: true, c: [cx, cy], len, t: s.t, ang: s.ang, z: ZB + s.d / 2, il: side, order: placed.length });
}

function addGemCube(name, parent, c, size, depth, big) {
  const il = isl(size, size, (i) => paintGem(i, big));
  const side = isl(size, depth, paintGold);
  elements.push({
    name,
    parent,
    from: [r2(c[0] - size / 2), r2(c[1] - size / 2), r2(ZB - depth / 2)],
    to: [r2(c[0] + size / 2), r2(c[1] + size / 2), r2(ZB + depth / 2)],
    origin: [r2(c[0]), r2(c[1]), r2(ZB)],
    rotation: [0, 0, 45],
    faces: { north: il, south: il, up: side, down: side, east: side, west: side },
    z: ZB + 11,
    zf: ZB + depth / 2,
  });
  placed.push({ gem: true, c, len: size, t: size, ang: 45, z: ZB + depth / 2 + 0.01, il, order: placed.length });
}

// ---------------------------------------------------------------------------------------------
// design: flight feathers (tip based so the silhouette is controlled directly)
// ---------------------------------------------------------------------------------------------
const P_TIPS = [[32, 10.5], [33.4, 5], [33.6, -0.8], [31.8, -7], [28, -11.8], [23.5, -13.6], [18.5, -13.6]].map(gs);
const S_TIPS = [[13.2, -10.5], [10, -7.5], [7.6, -4.5], [6, -1.5], [4.9, 1.5], [4.1, 4.2]].map(gs);

const TONES = {
  prim: { dim: 0.0, gold: 0.9, rim: 0.9, rimW: 1.2, barb: 0.015, rootShade: 0.12, sparkle: 1.2 },
  sec: { dim: 0.0, gold: 0.65, rim: 0.85, rimW: 1.2, barb: 0.015, rootShade: 0.12, sparkle: 1.0 },
  under: { dim: 0.05, gold: 0.55, rim: 0.8, rimW: 1.1, barb: 0.012, rootShade: 0.08, sparkle: 0.3 },
  covC: { dim: 0.02, gold: 0.5, rim: 1.0, rimW: 1.25, barb: 0.01, rootShade: 0.2, sparkle: 0.8 },
  covB: { dim: 0.0, gold: 0.45, rim: 1.0, rimW: 1.25, barb: 0.01, rootShade: 0.18, sparkle: 0.6 },
  covA: { dim: 0.0, gold: 0.4, rim: 1.0, rimW: 1.15, barb: 0.008, rootShade: 0.15, sparkle: 0.3 },
  alula: { dim: 0.0, gold: 0.6, rim: 1.0, rimW: 1.15, barb: 0.008, rootShade: 0.12, sparkle: 0.2 },
};

const flight = []; // {kind, segIndex, root, theta, L, W, s}
const armS = (si, t) => (si > 0 ? SEG.slice(0, si).reduce((a, b) => a + b.len, 0) : 0) + t * SEG[si].len;

function buildFlight() {
  // primaries on seg3 (root t from 1.0 towards the wrist), secondaries on seg2 (wrist towards elbow)
  P_TIPS.forEach((tip, i) => {
    const t = 1 - i * 0.15;
    const root = segPoint(2, t);
    const dx = tip[0] - root[0];
    const dy = tip[1] - root[1];
    flight.push({ kind: 'primary', segIndex: 2, root, theta: (Math.atan2(dy, dx) * 180) / Math.PI, L: Math.hypot(dx, dy), W: lerp(4.4, 4.9, i / 6), s: armS(2, t) });
  });
  S_TIPS.forEach((tip, j) => {
    const t = 0.9 - j * (0.75 / 5);
    const root = segPoint(1, t);
    const dx = tip[0] - root[0];
    const dy = tip[1] - root[1];
    flight.push({ kind: 'secondary', segIndex: 1, root, theta: (Math.atan2(dy, dx) * 180) / Math.PI, L: Math.hypot(dx, dy), W: lerp(4.9, 3.9, j / 5), s: armS(1, t) });
  });
}

function interpFlight(s) {
  const arr = [...flight].sort((a, b) => a.s - b.s);
  if (s <= arr[0].s) {
    const a = arr[0]; const b = arr[1];
    const k = (s - a.s) / (b.s - a.s);
    return { theta: lerp(a.theta, b.theta, k), L: lerp(a.L, b.L, k) };
  }
  for (let i = 0; i < arr.length - 1; i++) {
    if (s <= arr[i + 1].s) {
      const k = (s - arr[i].s) / (arr[i + 1].s - arr[i].s);
      return { theta: lerp(arr[i].theta, arr[i + 1].theta, k), L: lerp(arr[i].L, arr[i + 1].L, k) };
    }
  }
  const a = arr[arr.length - 1];
  return { theta: a.theta, L: a.L };
}

const cyc = (i) => 0.25 * (i % 3);

function build() {
  buildFlight();
  const zBase = { under: 0.6, main: 1.5, covC: 2.4, covB: 3.1, covA: 3.8, alula: 4.1 };

  // group hierarchy
  addGroup('wing', [0, 0, 0], [0, 0, 0], null);
  addGroup('seg1', [J.S[0], J.S[1], ZB], [0, 0, 0], 'wing');
  addGroup('seg2', [J.E[0], J.E[1], ZB], [0, 0, 0], 'seg1');
  addGroup('seg3', [J.W[0], J.W[1], ZB], [0, 0, 0], 'seg2');

  // under row: between neighbouring flight feathers, darker, a bit longer
  for (let i = 0; i < flight.length - 1; i++) {
    const a = flight[i]; const b = flight[i + 1];
    const root = [(a.root[0] + b.root[0]) / 2, (a.root[1] + b.root[1]) / 2];
    const seg = a.segIndex === b.segIndex ? a.segIndex : 2;
    addFeather(seg, root, zBase.under + cyc(i), (a.theta + b.theta) / 2, (a.L + b.L) / 2 + 1.6, (a.W + b.W) / 2 - 0.3, a.kind === 'primary' ? 'primary' : 'secondary', TONES.under, 'under');
  }
  // main flight row
  flight.forEach((f, i) => {
    addFeather(f.segIndex, f.root, zBase.main + cyc(i), f.theta, f.L, f.W, f.kind, f.kind === 'primary' ? TONES.prim : TONES.sec, f.kind === 'primary' ? 'prim' : 'sec');
  });

  // covert rows follow the same fan
  const total = SEG.reduce((a, b) => a + b.len, 0);
  const rows = [
    { n: 8, k: 0.44, W: 4.9, tone: TONES.covC, z: zBase.covC, tag: 'covC', off: 0, minL: 5.5, shape: 'covert', s0: 3.0 },
    { n: 8, k: 0.3, W: 4.1, tone: TONES.covB, z: zBase.covB, tag: 'covB', off: 1, minL: 4.2, shape: 'covert', s0: 2.4 },
    { n: 9, k: 0.17, W: 3.3, tone: TONES.covA, z: zBase.covA, tag: 'covA', off: 0, minL: 3.2, shape: 'small', s0: 1.8 },
  ];
  const sLast = flight[flight.length - 1];
  for (const row of rows) {
    const sp = (total - 0.8 - row.s0) / (row.n - 1 + (row.off ? 0.5 : 0));
    for (let i = 0; i < row.n; i++) {
      const s = Math.min(total - 0.8, row.s0 + sp * i + (row.off ? sp * 0.5 : 0));
      let si = 0; let acc = 0;
      while (si < 2 && s > acc + SEG[si].len) { acc += SEG[si].len; si += 1; }
      const t = clamp((s - acc) / SEG[si].len, 0, 1);
      const root = segPoint(si, t);
      let ip = interpFlight(s);
      if (s < sLast.s) {
        // humerus region: coverts hang down and slightly outward instead of leaning towards the spine
        const k = clamp(s / sLast.s, 0, 1);
        ip = { theta: lerp(-72, sLast.theta, k), L: lerp(sLast.L * 0.7, sLast.L, k) };
      }
      addFeather(si, root, row.z + cyc(i), ip.theta, Math.min(ip.L * 0.85, Math.max(row.minL, ip.L * row.k)), row.W, row.shape, row.tone, row.tag);
    }
  }
  // alula (thumb feathers) at the wrist, pointing up and out
  [[0.1, 72, 5.8, 2.6], [0.32, 50, 5.2, 2.5], [0.54, 30, 4.6, 2.4]].forEach(([t, th, L, W], i) => {
    addFeather(2, segPoint(2, t), zBase.alula + 0.25 * i, th, L, W, 'small', TONES.alula, 'alula');
  });

  // bones and joints
  addBone(0); addBone(1); addBone(2);
  addGemCube('gem_shoulder', 'seg1', J.S, 2.8, 3.0, false);
  addGemCube('gem_elbow', 'seg2', J.E, 4.2, 3.4, true);
  addGemCube('gem_wrist', 'seg3', J.W, 4.0, 3.2, true);
  addGemCube('gem_hand', 'seg3', J.H, 2.6, 2.4, false);
}

// ---------------------------------------------------------------------------------------------
// glow halo (flat translucent plane behind everything, shaped after the wing silhouette)
// ---------------------------------------------------------------------------------------------
function inFeather(f, X, Y) {
  const dx = X - f.root[0]; const dy = Y - f.root[1];
  const lx = dx * f.cs + dy * f.sn;
  const ly = -dx * f.sn + dy * f.cs;
  if (ly > 0 || ly < -f.L) return false;
  const t = -ly / f.L;
  return Math.abs(lx) <= (f.W / 2) * hw(t, f.shape);
}
function inBone(b, X, Y) {
  const dx = X - b.c[0]; const dy = Y - b.c[1];
  const lx = dx * b.cs + dy * b.sn;
  const ly = -dx * b.sn + dy * b.cs;
  return Math.abs(lx) <= b.len / 2 && Math.abs(ly) <= b.t / 2;
}

// The halo is a very soft pale-gold ring that floats OUTSIDE the wing: its alpha is exactly 0 inside the
// silhouette (feathers, bones and gems) dilated by HALO.gap units, so it can never overlap a feather or the gold
// arm. It is built from small tiles in two thin layers that share the same islands: one at the lowest z (behind
// the feathers for a viewer behind the player) and one above the whole stack (behind the feathers for a viewer
// in front of the player). The north faces use mirrored islands (the north face runs U towards -X).
const HALO = { gap: 1.6, reach: 3.0, margin: 3.3, tile: 5.5, peak: 0.24, lo: [0.05, 0.25], hi: [5.3, 5.5] };

function addGlow() {
  for (const f of placed) {
    const a = f.bone || f.gem ? f.ang : f.phi;
    f.cs = Math.cos(rad(a)); f.sn = Math.sin(rad(a));
  }
  let minX = 1e9, maxX = -1e9, minY = 1e9, maxY = -1e9;
  for (const f of placed) {
    if (f.bone || f.gem) continue;
    for (let k = 0; k <= 24; k++) {
      const t = k / 24;
      const h = (f.W / 2) * Math.max(0, hw(t, f.shape));
      for (const lx of [-h, h]) {
        const ly = -t * f.L;
        const X = f.root[0] + lx * f.cs - ly * f.sn;
        const Y = f.root[1] + lx * f.sn + ly * f.cs;
        minX = Math.min(minX, X); maxX = Math.max(maxX, X); minY = Math.min(minY, Y); maxY = Math.max(maxY, Y);
      }
    }
  }
  const b = { x0: Math.max(0.5, minX - HALO.margin), x1: maxX + HALO.margin, y0: minY - HALO.margin, y1: maxY + HALO.margin };
  // silhouette mask and distance field (0.2 unit cells)
  const cell = 0.2;
  const nx = Math.ceil((b.x1 - b.x0) / cell); const ny = Math.ceil((b.y1 - b.y0) / cell);
  const shapes = placed.filter((p) => !p.glow);
  const mask = new Uint8Array(nx * ny);
  for (let y = 0; y < ny; y++) {
    for (let x = 0; x < nx; x++) {
      const X = b.x0 + (x + 0.5) * cell; const Y = b.y1 - (y + 0.5) * cell;
      for (const f of shapes) { if (f.bone || f.gem ? inBone(f, X, Y) : inFeather(f, X, Y)) { mask[y * nx + x] = 1; break; } }
    }
  }
  const rch = Math.ceil(HALO.reach / cell);
  const dist = new Float32Array(nx * ny).fill(99);
  for (let y = 0; y < ny; y++) {
    for (let x = 0; x < nx; x++) {
      if (mask[y * nx + x]) { dist[y * nx + x] = 0; continue; }
      let d = 99;
      for (let dy = -rch; dy <= rch; dy++) for (let dx = -rch; dx <= rch; dx++) {
        const xx = x + dx; const yy = y + dy;
        if (xx < 0 || yy < 0 || xx >= nx || yy >= ny || !mask[yy * nx + xx]) continue;
        d = Math.min(d, Math.hypot(dx, dy) * cell);
      }
      dist[y * nx + x] = d;
    }
  }
  const field = (X, Y) => {
    const x = Math.floor((X - b.x0) / cell); const y = Math.floor((b.y1 - Y) / cell);
    if (x < 0 || y < 0 || x >= nx || y >= ny) return { a: 0, d: 99 };
    const d = dist[y * nx + x];
    if (d < HALO.gap || d >= HALO.reach) return { a: 0, d };
    // soft bump: 0 at the gap, peak close to it, 0 again at the reach; faded out towards the spine side
    const u = (d - HALO.gap) / (HALO.reach - HALO.gap);
    const a = HALO.peak * smooth(0, 0.3, u) * (1 - smooth(0.3, 1, u)) * smooth(0.6, 2.4, X);
    return { a: a * 1.0, d };
  };
  // tiles, the ones without any halo are dropped
  const nTx = Math.ceil((b.x1 - b.x0) / HALO.tile); const nTy = Math.ceil((b.y1 - b.y0) / HALO.tile);
  const tw = (b.x1 - b.x0) / nTx; const th = (b.y1 - b.y0) / nTy;
  let n = 0;
  let minD = 99;
  for (let j = 0; j < nTy; j++) {
    for (let i = 0; i < nTx; i++) {
      const t = { x0: b.x0 + i * tw, x1: b.x0 + (i + 1) * tw, y0: b.y0 + j * th, y1: b.y0 + (j + 1) * th };
      let mxa = 0;
      for (let yy = t.y0 + 0.1; yy < t.y1; yy += 0.25) for (let xx = t.x0 + 0.1; xx < t.x1; xx += 0.25) mxa = Math.max(mxa, field(xx, yy).a);
      if (mxa < 0.04) continue;
      n += 1;
      const paint = (mirror) => (il) => {
        il.data = Buffer.alloc(il.w * il.h * 4);
        for (let y = 0; y < il.h; y++) {
          for (let x = 0; x < il.w; x++) {
            const u = (x + 0.5) / il.w;
            const X = t.x0 + (mirror ? 1 - u : u) * (t.x1 - t.x0);
            const Y = t.y1 - ((y + 0.5) / il.h) * (t.y1 - t.y0);
            const { a, d } = field(X, Y);
            if (a < 0.015) continue;
            HALO.minD = Math.min(HALO.minD === undefined ? 99 : HALO.minD, d);
            const col = mix(C.paleGold, C.gold, smooth(0.0, 1.0, (d - HALO.gap) / (HALO.reach - HALO.gap)));
            const p = (y * il.w + x) * 4;
            il.data[p] = Math.round(col[0]); il.data[p + 1] = Math.round(col[1]); il.data[p + 2] = Math.round(col[2]);
            il.data[p + 3] = Math.round(clamp(a, 0, 1) * 255);
          }
        }
      };
      const ilS = isl(t.x1 - t.x0, t.y1 - t.y0, paint(false), 0.6);
      const ilN = isl(t.x1 - t.x0, t.y1 - t.y0, paint(true), 0.6);
      const nn = String(n).padStart(2, '0');
      for (const [layer, zr, zf] of [['lo', HALO.lo, -1], ['hi', HALO.hi, 99]]) {
        elements.push({
          name: 'halo_' + layer + '_' + nn,
          parent: 'wing',
          from: [r2(t.x0), r2(t.y0), zr[0]],
          to: [r2(t.x1), r2(t.y1), zr[1]],
          origin: [0, 0, 0],
          rotation: [0, 0, 0],
          faces: { north: ilN, south: ilS },
          z: zf,
          zf,
        });
      }
      placed.push({ glow: true, il: ilS, x0: t.x0, x1: t.x1, y0: t.y0, y1: t.y1, z: 0.1, order: -1 });
    }
  }
  b.tiles = n;
  HALO.stats = b;
  console.log('halo: ' + n + ' tiles x 2 layers, bounds x ' + b.x0.toFixed(1) + '..' + b.x1.toFixed(1) + ' y ' + b.y0.toFixed(1) + '..' + b.y1.toFixed(1));
}

// ---------------------------------------------------------------------------------------------
// atlas layout
// ---------------------------------------------------------------------------------------------
function pack(scale) {
  for (const i of islands) {
    i.w = Math.max(2, Math.ceil(i.wu * scale * i.sMul));
    i.h = Math.max(2, Math.ceil(i.hu * scale * i.sMul));
  }
  // first-fit decreasing shelves with a 1 px gutter
  const list = [...islands].sort((a, b) => b.h - a.h || b.w - a.w);
  const shelves = [];
  let nextY = 1;
  for (const i of list) {
    let shelf = shelves.find((sh) => i.h <= sh.h && sh.x + i.w + 1 <= TEX);
    if (!shelf) {
      if (nextY + i.h + 1 > TEX) return false;
      shelf = { y: nextY, h: i.h, x: 1 };
      shelves.push(shelf);
      nextY += i.h + 1;
    }
    i.x = shelf.x; i.y = shelf.y; shelf.x += i.w + 1;
  }
  return true;
}

function layout() {
  let s = args.scale ? parseFloat(args.scale) : 4.5;
  while (s > 1.5 && !pack(s)) s -= 0.125;
  if (!pack(s)) throw new Error('atlas overflow');
  return s;
}

// ---------------------------------------------------------------------------------------------
// output
// ---------------------------------------------------------------------------------------------
function writeTexture() {
  const png = new PNG({ width: TEX, height: TEX });
  png.data.fill(0);
  for (const i of islands) {
    i.paint(i);
    for (let y = 0; y < i.h; y++) {
      for (let x = 0; x < i.w; x++) {
        const s = (y * i.w + x) * 4;
        const d = ((i.y + y) * TEX + (i.x + x)) * 4;
        png.data[d] = i.data[s]; png.data[d + 1] = i.data[s + 1]; png.data[d + 2] = i.data[s + 2]; png.data[d + 3] = i.data[s + 3];
      }
    }
  }
  const buf = PNG.sync.write(png);
  fs.mkdirSync(path.dirname(OUT_TEX), { recursive: true });
  fs.writeFileSync(OUT_TEX, buf);
  return { buf, png };
}

function modelJson(texBuf) {
  const faceNames = ['north', 'east', 'south', 'west', 'up', 'down'];
  const gUuid = (g) => uuidOf('group:' + g.name);
  const groupObjs = groups.map((g) => ({
    name: g.name,
    uuid: gUuid(g),
    origin: g.origin,
    rotation: g.rotation,
    export: true,
    visibility: true,
    mirror_uv: false,
    reset: false,
    color: 0,
    autouv: 0,
    shade: true,
    locked: false,
  }));
  const elObjs = elements.map((e) => {
    const faces = {};
    for (const f of faceNames) {
      const il = e.faces[f];
      faces[f] = il ? { uv: [il.x, il.y, il.x + il.w, il.y + il.h], texture: 0 } : { uv: [0, 0, 0, 0], texture: null };
    }
    const o = {
      name: e.name,
      box_uv: false,
      rescale: false,
      locked: false,
      light_emission: 0,
      render_order: 'default',
      allow_mirror_modeling: true,
      from: e.from,
      to: e.to,
      autouv: 0,
      color: 0,
      origin: e.origin,
      faces,
      type: 'cube',
      uuid: uuidOf('cube:' + e.name),
    };
    if (e.rotation && (e.rotation[0] || e.rotation[1] || e.rotation[2])) o.rotation = e.rotation;
    return o;
  });
  // outliner = draw order: halo tiles first, then everything sorted by the depth of its front face (far to near
  // for a viewer behind the player). Nested groups are sorted by the mean depth of their contents; inside one
  // seg group the child seg and the feathers interleave by that key.
  const cubesIn = (g) => [...elements.filter((e) => e.parent === g.name), ...groups.filter((x) => x.parent === g.name).flatMap(cubesIn)];
  const keyOfGroup = (g) => { const c = cubesIn(g); return c.length ? c.reduce((a, e) => a + e.zf, 0) / c.length : 99; };
  const build = (g) => {
    const items = [];
    for (const x of groups.filter((q) => q.parent === g.name)) items.push({ k: keyOfGroup(x), node: () => build(x) });
    for (const c of elements.filter((e) => e.parent === g.name)) items.push({ k: c.zf, node: () => uuidOf('cube:' + c.name) });
    items.sort((p, q) => p.k - q.k);
    return { uuid: gUuid(g), isOpen: false, children: items.map((it) => it.node()) };
  };
  const wingNode = build(groups.find((g) => g.name === 'wing'));
  const ids = (p) => elements.filter((e) => e.name.startsWith(p)).map((e) => uuidOf('cube:' + e.name));
  const loIds = ids('halo_lo_'); const hiIds = ids('halo_hi_');
  wingNode.children = [...loIds, ...wingNode.children.filter((c) => !loIds.includes(c) && !hiIds.includes(c)), ...hiIds];

  return {
    meta: { format_version: '5.0', model_format: 'free', box_uv: false },
    name: 'engel',
    model_identifier: '',
    visible_box: [1, 1, 0],
    resolution: { width: TEX, height: TEX },
    elements: elObjs,
    groups: groupObjs,
    outliner: [wingNode],
    textures: [
      {
        uuid: uuidOf('texture'),
        name: 'engel.png',
        source: 'data:image/png;base64,' + texBuf.toString('base64'),
        path: '',
        width: TEX,
        height: TEX,
        uv_width: TEX,
        uv_height: TEX,
        folder: '',
        namespace: '',
        id: '0',
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
        relative_path: '',
      },
    ],
    variable_placeholders: '',
    variable_placeholder_buttons: [],
    timeline_setups: [],
    unhandled_root_fields: {},
  };
}

// ---------------------------------------------------------------------------------------------
// 2D preview (painter's algorithm over the actual atlas pixels) for fast silhouette tuning
// ---------------------------------------------------------------------------------------------
function renderPreview(file, png) {
  const S = 14; const x0 = -4; const x1 = 40; const y0 = -18; const y1 = 28;
  const W = (x1 - x0) * S; const H = (y1 - y0) * S;
  const out = new PNG({ width: W, height: H });
  for (let i = 0; i < out.data.length; i += 4) { out.data[i] = 22; out.data[i + 1] = 26; out.data[i + 2] = 44; out.data[i + 3] = 255; }
  const sample = (il, u, v) => {
    const px = clamp(Math.floor(u * il.w), 0, il.w - 1); const py = clamp(Math.floor(v * il.h), 0, il.h - 1);
    const d = ((il.y + py) * TEX + (il.x + px)) * 4;
    return [png.data[d], png.data[d + 1], png.data[d + 2], png.data[d + 3]];
  };
  const list = [...placed].sort((a, b) => (a.z - b.z) || (a.order - b.order));
  for (const f of list) {
    for (let py = 0; py < H; py++) {
      for (let px = 0; px < W; px++) {
        const X = x0 + (px + 0.5) / S; const Y = y1 - (py + 0.5) / S;
        let u; let v;
        if (f.glow) {
          if (X < f.x0 || X > f.x1 || Y < f.y0 || Y > f.y1) continue;
          u = (X - f.x0) / (f.x1 - f.x0); v = (f.y1 - Y) / (f.y1 - f.y0);
        } else if (f.bone || f.gem) {
          const dx = X - f.c[0]; const dy = Y - f.c[1];
          const lx = dx * f.cs + dy * f.sn; const ly = -dx * f.sn + dy * f.cs;
          if (Math.abs(lx) > f.len / 2 || Math.abs(ly) > f.t / 2) continue;
          u = (lx + f.len / 2) / f.len; v = (f.t / 2 - ly) / f.t;
        } else {
          const dx = X - f.root[0]; const dy = Y - f.root[1];
          const lx = dx * f.cs + dy * f.sn; const ly = -dx * f.sn + dy * f.cs;
          if (ly > 0 || ly < -f.L || Math.abs(lx) > f.W / 2) continue;
          u = (lx + f.W / 2) / f.W; v = -ly / f.L;
        }
        const col = sample(f.il, u, v);
        const a = col[3] / 255;
        if (a <= 0) continue;
        const o = (py * W + px) * 4;
        out.data[o] = Math.round(lerp(out.data[o], col[0], a));
        out.data[o + 1] = Math.round(lerp(out.data[o + 1], col[1], a));
        out.data[o + 2] = Math.round(lerp(out.data[o + 2], col[2], a));
      }
    }
  }
  fs.writeFileSync(file, PNG.sync.write(out));
}

// ---------------------------------------------------------------------------------------------
// menu preview: takes a transparent bbmodel render (view back, orthographic, flat lighting), finds the
// opaque wing in it, calibrates pixels per model unit from the known geometry and places the right wing
// into a 256x256 canvas: shoulder root at 12 % / 55 %, wing to the right and up, glow inside the frame.
// ---------------------------------------------------------------------------------------------
function contentBounds() {
  let x0 = 1e9, x1 = -1e9, y0 = 1e9, y1 = -1e9;
  const add = (X, Y) => { x0 = Math.min(x0, X); x1 = Math.max(x1, X); y0 = Math.min(y0, Y); y1 = Math.max(y1, Y); };
  for (const f of placed) {
    if (f.glow) continue;
    if (f.bone || f.gem) {
      for (const sx of [-1, 1]) for (const sy of [-1, 1]) {
        const lx = (sx * f.len) / 2; const ly = (sy * f.t) / 2;
        add(f.c[0] + lx * f.cs - ly * f.sn, f.c[1] + lx * f.sn + ly * f.cs);
      }
      continue;
    }
    for (let k = 0; k <= 200; k++) {
      const t = k / 200;
      const h = (f.W / 2) * Math.max(0, hw(t, f.shape));
      for (const lx of [-h, h]) { const ly = -t * f.L; add(f.root[0] + lx * f.cs - ly * f.sn, f.root[1] + lx * f.sn + ly * f.cs); }
    }
  }
  return { x0, x1, y0, y1 };
}

function composeMenu(srcFile, outFile) {
  const src = PNG.sync.read(fs.readFileSync(srcFile));
  const { width: W, height: H, data } = src;
  let bx0 = W, bx1 = -1, by0 = H, by1 = -1;
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    if (data[(y * W + x) * 4 + 3] >= 170) { bx0 = Math.min(bx0, x); bx1 = Math.max(bx1, x); by0 = Math.min(by0, y); by1 = Math.max(by1, y); }
  }
  const cb = contentBounds();
  const kx = (bx1 - bx0 + 1) / (cb.x1 - cb.x0); const ky = (by1 - by0 + 1) / (cb.y1 - cb.y0);
  // the horizontal span ends in a sharp gem corner, the vertical one in rounded feather tips (cut by the alpha
  // threshold), so trust kx and anchor vertically on the centre of the span
  const sc = kx;
  console.log('menu calibration: px/unit x ' + kx.toFixed(2) + ' y ' + ky.toFixed(2));
  // brightness: the flat render is dimmer than the texture, restore the brightest white to ~250
  const mx = [];
  for (let i = 0; i < data.length; i += 4) if (data[i + 3] >= 250) mx.push(Math.max(data[i], data[i + 1], data[i + 2]));
  mx.sort((a, b) => a - b);
  const gain = clamp(250 / mx[Math.floor(mx.length * 0.995)], 1, 2.4);
  console.log('menu gain ' + gain.toFixed(2));
  const OUT = 256; const T = 6.5; const rootX = 0.12 * OUT; const rootY = 0.55 * OUT;
  const out = new PNG({ width: OUT, height: OUT });
  const foot = sc / T; // source pixels per target pixel
  // source px of model point (X,Y)
  const spx = (X) => bx0 + (X - cb.x0) * sc;
  const spy = (Y) => (by0 + by1 + 1) / 2 + ((cb.y0 + cb.y1) / 2 - Y) * sc;
  for (let j = 0; j < OUT; j++) {
    for (let i = 0; i < OUT; i++) {
      const X = (i + 0.5 - rootX) / T; const Y = (rootY - (j + 0.5)) / T;
      const cx = spx(X); const cy = spy(Y);
      const xa = cx - foot / 2; const xb = cx + foot / 2; const ya = cy - foot / 2; const yb = cy + foot / 2;
      let sa = 0, sr = 0, sg = 0, sb = 0, sw = 0;
      for (let y = Math.floor(ya); y < Math.ceil(yb); y++) {
        const wy = Math.min(y + 1, yb) - Math.max(y, ya);
        for (let x = Math.floor(xa); x < Math.ceil(xb); x++) {
          const wx = Math.min(x + 1, xb) - Math.max(x, xa);
          const w = wx * wy; sw += w;
          if (x < 0 || y < 0 || x >= W || y >= H) continue;
          const p = (y * W + x) * 4; const a = data[p + 3] / 255;
          sa += a * w; sr += data[p] * a * w; sg += data[p + 1] * a * w; sb += data[p + 2] * a * w;
        }
      }
      const o = (j * OUT + i) * 4;
      if (sa > 0 && sw > 0) {
        out.data[o] = clamp(Math.round((sr / sa) * gain), 0, 255);
        out.data[o + 1] = clamp(Math.round((sg / sa) * gain), 0, 255);
        out.data[o + 2] = clamp(Math.round((sb / sa) * gain), 0, 255);
        out.data[o + 3] = clamp(Math.round((sa / sw) * 255), 0, 255);
      }
    }
  }
  fs.mkdirSync(path.dirname(outFile), { recursive: true });
  fs.writeFileSync(outFile, PNG.sync.write(out));
  let ob = [OUT, OUT, -1, -1];
  for (let j = 0; j < OUT; j++) for (let i = 0; i < OUT; i++) if (out.data[(j * OUT + i) * 4 + 3] > 40) { ob = [Math.min(ob[0], i), Math.min(ob[1], j), Math.max(ob[2], i), Math.max(ob[3], j)]; }
  console.log('menu preview ' + outFile + ' visible bbox (alpha>40): ' + ob.join(','));
}

// ---------------------------------------------------------------------------------------------
build();
addGlow();
const scale = layout();
const { buf, png } = writeTexture();
const model = modelJson(buf);
const json = JSON.stringify(model, null, '\t');
fs.mkdirSync(path.dirname(OUT_MODEL), { recursive: true });
fs.writeFileSync(OUT_MODEL, json);
if (args.also) { fs.mkdirSync(path.dirname(args.also), { recursive: true }); fs.writeFileSync(args.also, json); }
if (args.preview) renderPreview(args.preview, png);
console.log('closest visible halo texel to any feather, bone or gem: ' + HALO.minD.toFixed(2) + ' units (gap ' + HALO.gap + ')');
if (args.menu) composeMenu(args.menu, args.menuout || OUT_MENU);

let minX = 1e9, maxX = -1e9, minY = 1e9, maxY = -1e9, minZ = 1e9, maxZ = -1e9;
for (const f of placed) {
  if (f.glow || f.bone || f.gem) continue;
  for (let k = 0; k <= 24; k++) {
    const t = k / 24;
    const h = (f.W / 2) * Math.max(0, hw(t, f.shape));
    for (const lx of [-h, h]) {
      const ly = -t * f.L;
      const X = f.root[0] + lx * f.cs - ly * f.sn; const Y = f.root[1] + lx * f.sn + ly * f.cs;
      minX = Math.min(minX, X); maxX = Math.max(maxX, X); minY = Math.min(minY, Y); maxY = Math.max(maxY, Y);
    }
  }
  minZ = Math.min(minZ, f.z); maxZ = Math.max(maxZ, f.z);
}
console.log(`engel: ${elements.length} cubes, ${groups.length} groups, ${islands.length} UV islands, ${scale.toFixed(3)} texels/unit`);
console.log(`feather extent x ${minX.toFixed(1)}..${maxX.toFixed(1)}  y ${minY.toFixed(1)}..${maxY.toFixed(1)}  z ${minZ}..${maxZ}`);
let used = 0;
for (let i = 3; i < png.data.length; i += 4) if (png.data[i] > 0) used++;
console.log(`atlas fill ${((used / (TEX * TEX)) * 100).toFixed(1)} % non-transparent texels`);
