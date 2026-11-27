'use strict';
/**
 * Phoenix wing (flame wings) - generator for the texture atlas and the model elements.
 *
 * Writes the texture atlas (256x256, RGBA) and optionally the Blockbench model
 * (.bbmodel, free format, cubes only, per-face UV) from ONE layout, so the UV islands
 * and the pixels always match exactly.
 *
 *   node tools/wings/phoenix.cjs                         texture only
 *   node tools/wings/phoenix.cjs --model <file.bbmodel>  texture + model written by this script
 *   node tools/wings/phoenix.cjs --ops <file.json>       element list as bbmodel_edit operations
 *   node tools/wings/phoenix.cjs --info                  print bounds and counts only
 *
 * Coordinates: Blockbench units, +X outward, +Y up, +Z backwards, origin = shoulder.
 * See tools/wings/SPEC.md.
 */
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { PNG } = require('pngjs');

const ROOT = path.resolve(__dirname, '..', '..');
const RES = path.join(ROOT, 'mod', 'mc26', 'src', 'main', 'resources', 'assets', 'deucore');
const TEX_PATH = path.join(RES, 'textures', 'cosmetics', 'wings', 'phoenix.png');
const ATLAS = 256;
const D2R = Math.PI / 180;

/* ------------------------------------------------------------------ helpers */
const clamp = (v, a = 0, b = 1) => Math.min(b, Math.max(a, v));
const lerp = (a, b, t) => a + (b - a) * t;
const smooth = (a, b, v) => {
  const t = clamp((v - a) / (b - a));
  return t * t * (3 - 2 * t);
};
const r3 = (v) => Math.round(v * 1000) / 1000;
const hex = (h) => [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16));
const mix3 = (a, b, t) => [lerp(a[0], b[0], t), lerp(a[1], b[1], t), lerp(a[2], b[2], t)];
const rotZ = (v, deg) => {
  const c = Math.cos(deg * D2R);
  const s = Math.sin(deg * D2R);
  return [v[0] * c - v[1] * s, v[0] * s + v[1] * c];
};
function uuidOf(name) {
  const h = crypto.createHash('md5').update('phoenix-wing:' + name).digest('hex');
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-a${h.slice(17, 20)}-${h.slice(20, 32)}`;
}
function hash2(ix, iy, s) {
  let h = Math.imul(ix, 374761393) ^ Math.imul(iy, 668265263) ^ Math.imul(s, 1274126177);
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  h ^= h >>> 16;
  return (h >>> 0) / 4294967295;
}
function vnoise(x, y, s) {
  const ix = Math.floor(x);
  const iy = Math.floor(y);
  const fx = x - ix;
  const fy = y - iy;
  const u = fx * fx * (3 - 2 * fx);
  const v = fy * fy * (3 - 2 * fy);
  return lerp(
    lerp(hash2(ix, iy, s), hash2(ix + 1, iy, s), u),
    lerp(hash2(ix, iy + 1, s), hash2(ix + 1, iy + 1, s), u),
    v,
  );
}

/* ------------------------------------------------------------------ palette */
// Spirit of WINGS.phoenix in tools/generate-ingame-ui.cjs (base #3d0702, mid #d9480f,
// light #ffd43b, edge #1f0300, glow #fff3bf), refined into a continuous heat ramp.
const HEAT = [
  [0.0, hex('#34050a')],
  [0.16, hex('#6e0e08')],
  [0.34, hex('#b82508')],
  [0.5, hex('#e8570e')],
  [0.64, hex('#ff8618')],
  [0.78, hex('#ffb42c')],
  [0.9, hex('#ffd95a')],
  [1.0, hex('#fff3bf')],
];
function ramp(h) {
  h = clamp(h, 0, 1);
  for (let i = 1; i < HEAT.length; i++) {
    if (h <= HEAT[i][0]) {
      const a = HEAT[i - 1];
      const b = HEAT[i];
      return mix3(a[1], b[1], (h - a[0]) / (b[0] - a[0]));
    }
  }
  return HEAT[HEAT.length - 1][1];
}
const GLOW = hex('#fff6cc');

/* ------------------------------------------------------------------ wing layout */
// Arm chain (right wing, rest pose in the XY plane).
const S = [0, 0];
const E = [6.5, 9];
const WR = [14.5, 14.8];
const H = [21.5, 17.5];
// the arm sweeps backwards (+Z) towards the hand, offsets of the joints in Z
const ZJ = [0, 0.9, 1.9, 2.7];
const ARM = [
  { name: 'seg1', a: S, b: E, cross: 1.7, za: ZJ[0], zb: ZJ[1] },
  { name: 'seg2', a: E, b: WR, cross: 1.4, za: ZJ[1], zb: ZJ[2] },
  { name: 'seg3', a: WR, b: H, cross: 1.1, za: ZJ[2], zb: ZJ[3] },
];
const armLen = ARM.map((s) => Math.hypot(s.b[0] - s.a[0], s.b[1] - s.a[1]));
const armTotal = armLen.reduce((a, b) => a + b, 0);
function armPoint(t) {
  let d = clamp(t) * armTotal;
  for (let i = 0; i < ARM.length; i++) {
    if (d <= armLen[i] || i === ARM.length - 1) {
      const u = clamp(d / armLen[i]);
      const s = ARM[i];
      const dir = [(s.b[0] - s.a[0]) / armLen[i], (s.b[1] - s.a[1]) / armLen[i]];
      return { seg: s.name, p: [lerp(s.a[0], s.b[0], u), lerp(s.a[1], s.b[1], u)], z: lerp(s.za, s.zb, u), dir };
    }
    d -= armLen[i];
  }
  return null;
}

const Z_BONE = 1.55; // bone spans Z_BONE .. Z_BONE + BONE_D (plus the sweep offset)
const BONE_D = 1.55;
const Z_ROW = [0.15, 1.1, 2.05]; // feather rows: deep / middle / front (centre plane of the slab)
const SLAB = 0.3; // feather thickness
const OVERLAP = 0.45; // segment overlap along the feather

// Row 0: long flight feathers (t = position on the arm, phi = fan direction, L = length)
const ROW0 = [
  { id: 'a01', t: 1.0, phi: 28, L: 10, n: 2 },
  { id: 'a02', t: 0.94, phi: 19, L: 15, n: 3 },
  { id: 'a03', t: 0.87, phi: 9, L: 17, n: 3 },
  { id: 'a04', t: 0.79, phi: 2, L: 18, n: 3 },
  { id: 'a05', t: 0.71, phi: -10, L: 18.5, n: 3 },
  { id: 'a06', t: 0.63, phi: -23, L: 18.5, n: 3 },
  { id: 'a07', t: 0.55, phi: -37, L: 18.5, n: 3 },
  { id: 'a08', t: 0.47, phi: -51, L: 18.5, n: 3 },
  // trailing streamers (extra long, slim, curling, forked flame tips)
  { id: 't01', t: 0.39, phi: -65, L: 19, n: 3, streamer: true },
  { id: 't02', t: 0.31, phi: -77, L: 17, n: 3, streamer: true },
  { id: 't03', t: 0.23, phi: -87, L: 15, n: 3, streamer: true },
  { id: 't04', t: 0.15, phi: -93, L: 12, n: 3, streamer: true },
];

function buildFeatherSpecs() {
  const out = [];
  ROW0.forEach((r, i) => {
    out.push({
      ...r,
      row: 0,
      hmax: r.streamer ? 1.3 : 2.0,
      heat: -0.06,
      curl: r.streamer ? 7 : 5,
      tiltStep: 4,
      tilt0: i * 0.8,
      lean: r.streamer ? 1.6 : 1.15,
      tongue: { side: 1, t0: r.streamer ? 0.5 : 0.46, t1: r.streamer ? 0.93 : 0.86, w: r.streamer ? 0.5 : 0.42 },
      z: Z_ROW[0] + (i % 4) * 0.22,
      seed: 10 + i,
    });
  });
  // Row 1: middle covert feathers, shorter, between the roots of row 0
  ROW0.forEach((r, i) => {
    if (r.id === 'a01') return;
    out.push({
      id: 'b' + r.id,
      t: clamp(r.t - 0.035, 0.1, 1),
      phi: r.phi + 4,
      L: r.L * (r.streamer ? 0.5 : 0.6),
      n: 2,
      row: 1,
      hmax: 1.75,
      heat: -0.02,
      curl: 5.5,
      tiltStep: 4.5,
      tilt0: i * 0.8,
      lean: 1.0,
      tongue: { side: 1, t0: 0.5, t1: 0.85, w: 0.4 },
      z: Z_ROW[1] + (i % 4) * 0.22,
      seed: 40 + i,
    });
  });
  // Row 2: short front coverts at the arm, golden
  ROW0.forEach((r, i) => {
    if (r.id === 'a01') return;
    out.push({
      id: 'c' + r.id,
      t: clamp(r.t - 0.01, 0.05, 1),
      phi: r.phi + 8,
      L: r.L * 0.3,
      n: 1,
      row: 2,
      hmax: 1.5,
      heat: 0.05,
      curl: 5,
      tiltStep: 4,
      tilt0: i * 0.8,
      lean: 0.9,
      z: Z_ROW[2] + (i % 4) * 0.22,
      seed: 70 + i,
    });
  });
  return out;
}

// Flame wisps along the top edge of the arm (small tongues rising from the bone)
const WISPS = [
  { id: 'w01', t: 0.13, phi: 97, L: 3.8 },
  { id: 'w02', t: 0.25, phi: 100, L: 4.6 },
  { id: 'w03', t: 0.4, phi: 94, L: 5.2 },
  { id: 'w04', t: 0.55, phi: 88, L: 5.2 },
  { id: 'w05', t: 0.7, phi: 80, L: 5.0 },
  { id: 'w06', t: 0.86, phi: 70, L: 4.8 },
];

/* ------------------------------------------------------------------ feather geometry */
const FRACS = { 1: [1], 2: [0.55, 0.45], 3: [0.34, 0.34, 0.32] };

function buildChain(f, L) {
  const fr = FRACS[f.n];
  const segs = [];
  let b = [0, 0, 0];
  let bend = 0;
  let tilt = f.tilt0 || 0;
  let y0 = 0;
  for (let i = 0; i < f.n; i++) {
    if (i > 0) {
      bend += f.curl;
      tilt += f.tiltStep;
    }
    const len = L * fr[i];
    segs.push({ base: b.slice(), len, bend, tilt, y0 });
    const bb = bend * D2R;
    const tb = tilt * D2R;
    b = [b[0] - Math.sin(bb) * Math.cos(tb) * len, b[1] + Math.cos(bb) * Math.cos(tb) * len, b[2] + Math.sin(tb) * len];
    y0 += len;
  }
  return { segs, tip: b, L };
}

// Choose group rotation and length so the (projected) tip lands where the fan table says.
function fitFeather(f, root) {
  const target = [root[0] + f.L * Math.cos(f.phi * D2R), root[1] + f.L * Math.sin(f.phi * D2R)];
  const D = f.L;
  let phi0 = f.phi;
  let L = f.L;
  let chain = null;
  for (let it = 0; it < 6; it++) {
    chain = buildChain(f, L);
    const t = rotZ([chain.tip[0], chain.tip[1]], phi0 - 90);
    const ang = Math.atan2(t[1], t[0]) / D2R;
    phi0 += f.phi - ang;
    L *= D / Math.hypot(t[0], t[1]);
  }
  chain = buildChain(f, L);
  return { chain, phi0, L, target };
}

/* flame feather outline: half width profile and centre-line sway */
function shape(T) {
  const rise = 0.6 + 0.4 * smooth(0, 0.2, T);
  const tail = T < 0.4 ? 1 : Math.pow(Math.max(0, 1 - Math.pow((T - 0.4) / 0.6, 1.45)), 0.85);
  return rise * tail;
}
function edges(T, f) {
  const hw = f.hmax * shape(T);
  const c =
    -f.lean * f.hmax * Math.pow(smooth(0.4, 1, T), 1.7) +
    0.22 * f.hmax * Math.sin(T * Math.PI * 2.1 + f.seed) * smooth(0.12, 0.8, T);
  const amp = (f.scal === undefined ? 0.11 : f.scal) * smooth(0.28, 0.9, T);
  const sl = 1 + amp * Math.sin(T * Math.PI * 2 * 2.6 + f.seed * 1.7);
  const sr = 1 + amp * Math.sin(T * Math.PI * 2 * 2.6 + f.seed * 1.7 + 2.3);
  const e = { c, hw, xl: c - hw * sl, xr: c + hw * sr, tc: 0, thw: 0 };
  // secondary flame tongue on the outer side: gives forked, licking tips
  const tg = f.tongue;
  if (tg) {
    const u = (T - tg.t0) / (tg.t1 - tg.t0);
    if (u > 0 && u < 1) {
      e.thw = f.hmax * tg.w * Math.pow(1 - Math.pow(u, 1.35), 0.9) * smooth(0, 0.14, u);
      e.tc = c + tg.side * (hw * 0.85 + 0.15 * e.thw);
    }
  }
  return e;
}
// signed distance (units, positive inside) to the outline in X, plus the distance to the local quill
function outline(xu, T, f) {
  const e = edges(T, f);
  const dA = Math.min(xu - e.xl, e.xr - xu);
  if (e.thw > 0) {
    const dB = e.thw - Math.abs(xu - e.tc);
    if (dB > dA) return { d: dB, dc: Math.abs(xu - e.tc), hw: e.thw, c: e.tc };
  }
  return { d: dA, dc: Math.abs(xu - e.c), hw: Math.max(e.hw, 0.001), c: e.c };
}
function segWidth(f, seg) {
  let m = 0.4;
  for (let i = 0; i <= 24; i++) {
    const T = (seg.y0 + (seg.len * i) / 24) / f.Lfit;
    const e = edges(Math.min(T, 1), f);
    m = Math.max(m, Math.abs(e.xl), Math.abs(e.xr));
    if (e.thw > 0) m = Math.max(m, Math.abs(e.tc - e.thw), Math.abs(e.tc + e.thw));
  }
  return Math.max(1.2, 2 * m + 0.3);
}

/* ------------------------------------------------------------------ painters */
// All painters get the texel (px, py) of an island of size W x Hh and return [r,g,b,a] (0-255).
function featherPainter(f, seg, Wu, Hu, DENS) {
  return (px, py, W, Hh) => {
    const xu = ((px + 0.5) / W - 0.5) * Wu;
    const yu = (1 - (py + 0.5) / Hh) * Hu;
    const Y = seg.y0 + yu;
    const T = clamp(Y / f.Lfit, 0, 1);
    const o = outline(xu, T, f);
    const dpx = o.d * DENS;
    const dc = o.dc;
    const rel = clamp(dc / Math.max(o.hw, 0.2));

    // heat: dark red base -> orange -> gold -> yellow white tip
    let heat = 0.1 + 0.9 * Math.pow(T, 0.88) + f.heat + (vnoise(xu * 1.1 + 40, Y * 0.55, f.seed) - 0.5) * 0.12;
    heat += smooth(0.7, 1, T) * 0.2;
    // bright quill and soft barbs
    const quill = Math.exp(-Math.pow(dc / 0.3, 2));
    heat += quill * 0.2;
    const pitch = 1.7;
    const band = Math.sin(((Y * 1.0 - dc * 1.5) / pitch) * Math.PI * 2);
    let col = ramp(heat);
    col = col.map((v) => v * (1 + 0.1 * band * smooth(0.12, 0.4, rel)));
    // hot rim
    const rim = 1 - smooth(0.2, 1.7, dpx);
    col = mix3(col, ramp(heat + 0.24), rim * 0.7);
    // tip glow
    col = mix3(col, GLOW, smooth(0.84, 1, T) * 0.75);
    // embers
    if (T > 0.25 && dpx > 1.5 && hash2(px, py, f.seed + 7) < 0.006) col = mix3(col, hex('#ffe9a0'), 0.9);

    let a = clamp(dpx + 0.5);
    const ghost = 0.66 + 0.34 * smooth(0, 2.4, dpx);
    a *= lerp(1, ghost, smooth(0.12, 0.36, T));
    a *= 1 - 0.14 * smooth(0.9, 1, T);
    a *= 0.965;
    return [col[0], col[1], col[2], a * 255];
  };
}

function bonePainter(armIdx, Lu) {
  return (px, py, W, Hh) => {
    const u = (px + 0.5) / W;
    const v = (py + 0.5) / Hh;
    const e = Math.abs(v - 0.5) * 2; // 0 centre .. 1 edge
    const along = (armIdx + u) / ARM.length;
    const core = Math.exp(-Math.pow(e / 0.42, 2));
    const dark = mix3(hex('#1c0303'), hex('#4a0b06'), 1 - e);
    let col = mix3(dark, ramp(0.5 + along * 0.38), core);
    col = mix3(col, GLOW, Math.pow(core, 2.2) * (0.5 + 0.4 * along));
    // knuckle rings every few units
    const ring = Math.abs(((u * Lu) % 3.2) - 1.6);
    if (ring > 1.45) col = col.map((c) => c * 0.62);
    // top highlight line
    if (v < 1.2 / Hh + 0.05 && Hh > 4) col = mix3(col, ramp(0.82), 0.5);
    return [col[0], col[1], col[2], 255];
  };
}
function boneEdgePainter(armIdx) {
  return (px, py, W, Hh) => {
    const u = (px + 0.5) / W;
    const v = (py + 0.5) / Hh;
    const e = Math.abs(v - 0.5) * 2;
    const along = (armIdx + u) / ARM.length;
    const col = mix3(hex('#2a0504'), ramp(0.36 + along * 0.3), Math.exp(-Math.pow(e / 0.5, 2)) * 0.75);
    return [col[0], col[1], col[2], 255];
  };
}
function capPainter() {
  const c = hex('#3a0905');
  return () => [c[0], c[1], c[2], 255];
}
function orbPainter() {
  return (px, py, W, Hh) => {
    const x = ((px + 0.5) / W - 0.5) * 2;
    const y = ((py + 0.5) / Hh - 0.5) * 2;
    const r = Math.hypot(x, y);
    let col = mix3(GLOW, ramp(0.62), smooth(0.15, 1.1, r));
    col = mix3(col, hex('#7a1206'), smooth(0.85, 1.25, r));
    return [col[0], col[1], col[2], 255];
  };
}

/* ------------------------------------------------------------------ model assembly */
function assemble(DENS, opts = {}) {
  const islands = new Map(); // id -> {w, h, paint}
  const addIsland = (id, wu, hu, paint, minW = 2, minH = 2) => {
    if (!islands.has(id)) {
      islands.set(id, { id, w: Math.max(minW, Math.ceil(wu * DENS)), h: Math.max(minH, Math.ceil(hu * DENS)), paint });
    }
    return id;
  };
  const groups = []; // {name, origin, rotation, parent}
  const cubes = []; // {name, parent, from, to, origin, rotation, faces:{face:{island, flip}}}
  groups.push({ name: 'wing', origin: [0, 0, 0], rotation: [0, 0, 0], parent: null });
  const boneZ = (k) => Z_BONE + BONE_D / 2 + ZJ[k]; // centre plane of the bone at joint k
  ARM.forEach((s, i) => {
    groups.push({ name: s.name, origin: [s.a[0], s.a[1], i === 0 ? 0 : r3(boneZ(i))], rotation: [0, 0, 0], parent: i === 0 ? 'wing' : ARM[i - 1].name });
  });

  const names = new Set();
  const uniq = (n) => {
    if (names.has(n)) throw new Error('duplicate name ' + n);
    names.add(n);
    return n;
  };

  const addFeather = (f, root, segName, zOff) => {
    const fit = fitFeather(f, root);
    f.Lfit = fit.L;
    const gname = uniq('f_' + f.id);
    const zc = f.z + zOff;
    groups.push({ name: gname, origin: [r3(root[0]), r3(root[1]), r3(zc)], rotation: [0, 0, r3(fit.phi0 - 90)], parent: segName });
    fit.chain.segs.forEach((seg, i, all) => {
      const Wu = segWidth(f, seg);
      // every segment but the last reaches a little into the next one so bends leave no gap
      const Hu = seg.len + (i < all.length - 1 ? OVERLAP : 0);
      const id = addIsland(`${f.id}_${i}`, Wu, Hu, featherPainter(f, seg, Wu, Hu, DENS), 4, 4);
      const b = [root[0] + seg.base[0], root[1] + seg.base[1], zc + seg.base[2]];
      cubes.push({
        name: uniq(`${f.id}_${i}`),
        parent: gname,
        from: [r3(b[0] - Wu / 2), r3(b[1]), r3(b[2] - SLAB / 2)],
        to: [r3(b[0] + Wu / 2), r3(b[1] + Hu), r3(b[2] + SLAB / 2)],
        origin: [r3(b[0]), r3(b[1]), r3(b[2])],
        rotation: [r3(seg.tilt), 0, r3(seg.bend)],
        faces: { south: { island: id, flip: false }, north: { island: id, flip: true } },
      });
    });
    f.fit = fit;
  };

  for (const f of buildFeatherSpecs()) {
    const ai = armPoint(f.t);
    addFeather(f, ai.p, ai.seg, ai.z);
  }

  // bones and orbs
  if (opts.hideSides) addIsland('void', 1, 1, () => [0, 0, 0, 0], 3, 3);
  const orbIsl = addIsland('orb', 2, 2, orbPainter(), 6, 6);
  const capIsl = addIsland('cap', 1, 1, capPainter(), 2, 2);
  ARM.forEach((s, i) => {
    const len = armLen[i];
    const dz = s.zb - s.za;
    const len3 = Math.hypot(len, dz);
    const ang = Math.atan2(s.b[1] - s.a[1], s.b[0] - s.a[0]) / D2R;
    const sweep = -Math.asin(dz / len3) / D2R; // rotation about Y: turns +X towards +Z
    const zc = boneZ(i);
    const sIsl = addIsland(`bone${i + 1}_s`, len, s.cross, bonePainter(i, len), 4, 4);
    const eIsl = addIsland(`bone${i + 1}_e`, len, BONE_D, boneEdgePainter(i), 4, 3);
    cubes.push({
      name: uniq(`bone${i + 1}`),
      parent: s.name,
      from: [r3(s.a[0]), r3(s.a[1] - s.cross / 2), r3(zc - BONE_D / 2)],
      to: [r3(s.a[0] + len3), r3(s.a[1] + s.cross / 2), r3(zc + BONE_D / 2)],
      origin: [r3(s.a[0]), r3(s.a[1]), r3(zc)],
      rotation: [0, r3(sweep), r3(ang)],
      faces: {
        south: { island: sIsl, flip: false },
        north: { island: sIsl, flip: true },
        up: { island: eIsl, flip: false },
        down: { island: eIsl, flip: false },
        east: { island: capIsl, flip: false },
        west: { island: capIsl, flip: false },
      },
    });
  });
  // orbs at shoulder, elbow, wrist, hand (diamonds, glowing)
  [
    ['orb_s', [0.8, 0.6], 1.9, 'seg1', 0],
    ['orb_e', E, 2.4, 'seg2', 1],
    ['orb_w', WR, 2.1, 'seg3', 2],
    ['orb_h', H, 1.8, 'seg3', 3],
  ].forEach(([nm, p, sz, seg, k]) => {
    const zc = boneZ(k) + 0.1;
    cubes.push({
      name: uniq(nm),
      parent: seg,
      from: [r3(p[0] - sz / 2), r3(p[1] - sz / 2), r3(zc - sz / 2)],
      to: [r3(p[0] + sz / 2), r3(p[1] + sz / 2), r3(zc + sz / 2)],
      origin: [r3(p[0]), r3(p[1]), r3(zc)],
      rotation: [0, 0, 45],
      faces: Object.fromEntries(['north', 'east', 'south', 'west', 'up', 'down'].map((f) => [f, { island: orbIsl, flip: false }])),
    });
  });

  // flame wisps along the top edge of the arm
  WISPS.forEach((w, i) => {
    const spec = {
      ...w,
      n: w.L > 5.5 ? 2 : 1,
      row: 3,
      hmax: 1.25,
      heat: 0.16,
      curl: 7,
      tiltStep: 3,
      lean: 0.9,
      scal: 0.16,
      z: Z_BONE + 0.5 + (i % 2) * 0.35,
      seed: 100 + i,
    };
    const ai = armPoint(w.t);
    const nrm = [-ai.dir[1], ai.dir[0]]; // upper normal of the arm
    const cross = ARM.find((s) => s.name === ai.seg).cross;
    addFeather(spec, [ai.p[0] + nrm[0] * cross * 0.35, ai.p[1] + nrm[1] * cross * 0.35], ai.seg, ai.z);
  });

  return { islands, groups, cubes, hideSides: !!opts.hideSides };
}

/* ------------------------------------------------------------------ atlas */
function packIslands(islands, gap) {
  const order = [...islands.values()].sort((a, b) => b.h - a.h || b.w - a.w);
  let x = gap;
  let y = gap;
  let rowH = 0;
  for (const isl of order) {
    if (isl.w + 2 * gap > ATLAS) return false;
    if (x + isl.w + gap > ATLAS) {
      x = gap;
      y += rowH + gap;
      rowH = 0;
    }
    if (y + isl.h + gap > ATLAS) return false;
    isl.x = x;
    isl.y = y;
    x += isl.w + gap;
    rowH = Math.max(rowH, isl.h);
  }
  return true;
}

function paintAtlas(islands) {
  const png = new PNG({ width: ATLAS, height: ATLAS });
  png.data.fill(0);
  const put = (x, y, c) => {
    const i = (y * ATLAS + x) * 4;
    png.data[i] = Math.round(clamp(c[0], 0, 255));
    png.data[i + 1] = Math.round(clamp(c[1], 0, 255));
    png.data[i + 2] = Math.round(clamp(c[2], 0, 255));
    png.data[i + 3] = Math.round(clamp(c[3], 0, 255));
  };
  const painted = new Map();
  for (const isl of islands.values()) {
    const buf = [];
    for (let y = 0; y < isl.h; y++) {
      for (let x = 0; x < isl.w; x++) {
        const c = isl.paint(x, y, isl.w, isl.h);
        put(isl.x + x, isl.y + y, c);
        buf.push(c);
      }
    }
    painted.set(isl.id, buf);
  }
  // one pixel of edge bleed around every island (colour only, so filtering never darkens edges)
  for (const isl of islands.values()) {
    for (let y = -1; y <= isl.h; y++) {
      for (let x = -1; x <= isl.w; x++) {
        if (x >= 0 && x < isl.w && y >= 0 && y < isl.h) continue;
        const c = painted.get(isl.id)[clamp(y, 0, isl.h - 1) * isl.w + clamp(x, 0, isl.w - 1)];
        const gx = isl.x + x;
        const gy = isl.y + y;
        if (gx < 0 || gy < 0 || gx >= ATLAS || gy >= ATLAS) continue;
        const i = (gy * ATLAS + gx) * 4;
        if (png.data[i + 3] !== 0 || png.data[i] || png.data[i + 1] || png.data[i + 2]) continue;
        png.data[i] = Math.round(c[0]);
        png.data[i + 1] = Math.round(c[1]);
        png.data[i + 2] = Math.round(c[2]);
        png.data[i + 3] = 0;
      }
    }
  }
  return png;
}

/* ------------------------------------------------------------------ output */
const FACE_ORDER = ['north', 'east', 'south', 'west', 'up', 'down'];
function faceUV(isl, flip) {
  return flip ? [isl.x + isl.w, isl.y, isl.x, isl.y + isl.h] : [isl.x, isl.y, isl.x + isl.w, isl.y + isl.h];
}

function buildBbmodel(model, pngBuf) {
  const { groups, cubes, islands } = model;
  const groupList = groups.map((g) => ({
    uuid: uuidOf('g:' + g.name),
    name: g.name,
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
  // outliner order inside a group: feather groups (deep rows first), own cubes, child arm segment last
  const nodes = new Map();
  groups.forEach((g, i) => nodes.set(g.name, { uuid: groupList[i].uuid, isOpen: false, children: [] }));
  const outliner = [nodes.get('wing')];
  const kids = new Map(groups.map((g) => [g.name, { feathers: [], cubes: [], segs: [] }]));
  groups.forEach((g) => {
    if (!g.parent) return;
    kids.get(g.parent)[g.name.startsWith('seg') ? 'segs' : 'feathers'].push(g.name);
  });
  cubes.forEach((c) => {
    if (kids.has(c.parent) && !c.parent.startsWith('seg')) nodes.get(c.parent).children.push(uuidOf('c:' + c.name));
    else kids.get(c.parent).cubes.push(c.name);
  });
  for (const g of groups) {
    if (!g.name.startsWith('seg') && g.name !== 'wing') continue;
    const k = kids.get(g.name);
    const node = nodes.get(g.name);
    for (const fn of k.feathers) node.children.push(nodes.get(fn));
    for (const cn of k.cubes) node.children.push(uuidOf('c:' + cn));
    for (const sn of k.segs) node.children.push(nodes.get(sn));
  }
  const elements = cubes.map((c) => {
    const faces = {};
    for (const f of FACE_ORDER) {
      const fc = c.faces[f];
      faces[f] = fc ? { uv: faceUV(islands.get(fc.island), fc.flip), texture: 0 } : model.hideSides ? { uv: faceUV(islands.get('void'), false), texture: 0 } : { uv: [0, 0, 0, 0] };
    }
    return {
      uuid: uuidOf('c:' + c.name),
      name: c.name,
      origin: c.origin,
      rotation: c.rotation,
      color: 0,
      type: 'cube',
      from: c.from,
      to: c.to,
      box_uv: false,
      faces,
      rescale: false,
      locked: false,
      light_emission: 0,
      render_order: 'default',
      allow_mirror_modeling: true,
      autouv: 0,
    };
  });
  return {
    meta: { format_version: '5.0', model_format: 'free', box_uv: false },
    name: 'phoenix',
    model_identifier: '',
    visible_box: [1, 1, 0],
    resolution: { width: ATLAS, height: ATLAS },
    elements,
    groups: groupList,
    outliner,
    textures: [
      {
        uuid: uuidOf('texture'),
        name: 'phoenix.png',
        source: 'data:image/png;base64,' + pngBuf.toString('base64'),
        path: '',
        width: ATLAS,
        height: ATLAS,
        uv_width: ATLAS,
        uv_height: ATLAS,
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

function buildOps(model) {
  const ops = [];
  for (const g of model.groups) {
    ops.push({ op: 'add_group', name: g.name, origin: g.origin, rotation: g.rotation, ...(g.parent ? { parent: g.parent } : {}) });
  }
  for (const c of model.cubes) {
    const faces = {};
    for (const [f, fc] of Object.entries(c.faces)) faces[f] = { uv: faceUV(model.islands.get(fc.island), fc.flip), texture: 0 };
    ops.push({ op: 'add_cube', name: c.name, from: c.from, to: c.to, origin: c.origin, rotation: c.rotation, parent: c.parent, box_uv: false, faces });
  }
  return ops;
}

function worldBounds(model) {
  // world-space bounds of every cube corner (cube rotation Rz*Ry*Rx about its origin, then group rotations)
  const gmap = new Map(model.groups.map((g) => [g.name, g]));
  const per = [];
  const min = [1e9, 1e9, 1e9];
  const max = [-1e9, -1e9, -1e9];
  for (const c of model.cubes) {
    const cmin = [1e9, 1e9, 1e9];
    const cmax = [-1e9, -1e9, -1e9];
    for (const x of [c.from[0], c.to[0]]) {
      for (const y of [c.from[1], c.to[1]]) {
        for (const z of [c.from[2], c.to[2]]) {
          let v = [x - c.origin[0], y - c.origin[1], z - c.origin[2]];
          const ax = c.rotation[0] * D2R;
          const ay = c.rotation[1] * D2R;
          v = [v[0], v[1] * Math.cos(ax) - v[2] * Math.sin(ax), v[1] * Math.sin(ax) + v[2] * Math.cos(ax)];
          v = [v[0] * Math.cos(ay) + v[2] * Math.sin(ay), v[1], -v[0] * Math.sin(ay) + v[2] * Math.cos(ay)];
          const zz = rotZ([v[0], v[1]], c.rotation[2]);
          v = [zz[0] + c.origin[0], zz[1] + c.origin[1], v[2] + c.origin[2]];
          let g = gmap.get(c.parent);
          while (g) {
            if (g.rotation[2]) {
              const r = rotZ([v[0] - g.origin[0], v[1] - g.origin[1]], g.rotation[2]);
              v = [r[0] + g.origin[0], r[1] + g.origin[1], v[2]];
            }
            g = g.parent ? gmap.get(g.parent) : null;
          }
          for (let i = 0; i < 3; i++) {
            cmin[i] = Math.min(cmin[i], v[i]);
            cmax[i] = Math.max(cmax[i], v[i]);
          }
        }
      }
    }
    for (let i = 0; i < 3; i++) {
      min[i] = Math.min(min[i], cmin[i]);
      max[i] = Math.max(max[i], cmax[i]);
    }
    per.push({ name: c.name, min: cmin.map(r3), max: cmax.map(r3) });
  }
  return { min: min.map(r3), max: max.map(r3), per };
}

function build(opts) {
  for (let dens = 5.5; dens >= 2.5; dens -= 0.25) {
    const model = assemble(dens, opts);
    if (packIslands(model.islands, 2)) {
      model.dens = dens;
      return model;
    }
  }
  throw new Error('atlas overflow');
}

function main() {
  const args = process.argv.slice(2);
  const arg = (n) => {
    const i = args.indexOf(n);
    return i >= 0 ? args[i + 1] : null;
  };
  // --dev: untextured faces point at a transparent island (only for headless preview renders)
  const dev = args.includes('--dev');
  const model = build({ hideSides: dev });
  const png = paintAtlas(model.islands);
  const buf = PNG.sync.write(png);
  let used = 0;
  for (const i of model.islands.values()) used += i.w * i.h;
  const wb = worldBounds(model);
  const info = {
    cubes: model.cubes.length,
    groups: model.groups.length,
    islands: model.islands.size,
    density: model.dens,
    atlasFill: Math.round((used / (ATLAS * ATLAS)) * 100) + '%',
    min: wb.min,
    max: wb.max,
  };
  if (args.includes('--extents')) {
    const top = (key, dir, n) => [...wb.per].sort((a, b) => dir * (b[key][dir > 0 ? 1 : 0] - a[key][dir > 0 ? 1 : 0])).slice(0, n);
    console.log('top Y', top('max', 1, 4).map((c) => c.name + ' ' + c.max[1]).join(', '));
    console.log('low Y', [...wb.per].sort((a, b) => a.min[1] - b.min[1]).slice(0, 4).map((c) => c.name + ' ' + c.min[1]).join(', '));
    console.log('max X', [...wb.per].sort((a, b) => b.max[0] - a.max[0]).slice(0, 4).map((c) => c.name + ' ' + c.max[0]).join(', '));
    console.log('min X', [...wb.per].sort((a, b) => a.min[0] - b.min[0]).slice(0, 4).map((c) => c.name + ' ' + c.min[0]).join(', '));
    console.log('max Z', [...wb.per].sort((a, b) => b.max[2] - a.max[2]).slice(0, 3).map((c) => c.name + ' ' + c.max[2]).join(', '));
  }
  if (!args.includes('--info')) {
    const texOut = arg('--tex') || TEX_PATH;
    fs.mkdirSync(path.dirname(texOut), { recursive: true });
    fs.writeFileSync(texOut, buf);
    console.log('texture', path.relative(ROOT, texOut));
  }
  const modelOut = arg('--model');
  if (modelOut) {
    fs.mkdirSync(path.dirname(modelOut), { recursive: true });
    fs.writeFileSync(modelOut, JSON.stringify(buildBbmodel(model, buf), null, '	'));
    console.log('model', modelOut);
  }
  const opsOut = arg('--ops');
  if (opsOut) {
    fs.writeFileSync(opsOut, JSON.stringify(buildOps(model)));
    console.log('ops', opsOut);
  }
  console.log(JSON.stringify(info));
}

if (require.main === module) main();
module.exports = { build, assemble, packIslands, buildBbmodel, buildOps, paintAtlas, worldBounds };
