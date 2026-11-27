/**
 * Blockbench-Animationen fuer die Fluegel als Elytra-Ersatz: node tools/wings/animations.cjs
 * Schreibt in jedes assets/deucore/models/wings/<id>.bbmodel die Animationen
 *   stehen      wie die normalen Fluegel in Ruhe
 *   schleichen  wie die normalen Fluegel beim Schleichen
 *   gleiten     weit ausgebreitet, Spitzen zittern leicht im Wind
 *   schlagen    kraeftiger Fluegelschlag (Steigflug, Rakete)
 *   sturz       nach hinten zusammengezogen
 * Die Mod spielt genau diese Keyframes ab; Aenderungen in Blockbench wirken also im Spiel.
 * Nach den Postprocess-Skripten ausfuehren, danach pairs.cjs.
 *
 * Alle Posen haben die volle Fluegelgroesse.
 *
 * Achsen (Raum der Elterngruppe, rechter Fluegel): Z+ hebt die Spitze, Y- schwenkt sie vom Ruecken weg
 * (im Gleitflug ist das "nach oben"), Y+ zum Bauch.
 */
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

const dir = path.resolve(__dirname, '..', '..', 'mod', 'mc26.3', 'src', 'main', 'resources', 'assets', 'deucore', 'models', 'wings');
const IDS = ['engel', 'daemon', 'eis', 'phoenix', 'schatten', 'smaragd'];
const TAU = Math.PI * 2;
const wave = (p, shift = 0) => Math.sin(TAU * (p - shift));

/**
 * Ruheposen: dieselbe Haltung und Bewegung wie die normalen Fluegel ohne Elytra (WingModel, prozedural):
 * open = Schwenk nach hinten in Grad, Roll 5 Grad, Armsegmente folgen verzoegert, Federn wiegen leicht.
 */
const rest = (open) => ({
  length: 3.14, frames: 12, unfold: 0,
  wing: (p) => [0, -open, 5 * wave(p)],
  seg2: (p) => [0, 0, 1.5 * wave(p, 0.119)],
  seg3: (p) => [0, 0, 3 * wave(p, 0.239)],
  feather: (p, ph) => 2.5 * wave(p, ph) + 0.6 * wave(p, 0.175),
});

/**
 * Jede Pose: Laenge in Sekunden, Anzahl Keyframes, Drehungen [x, y, z] je Gruppe als Funktion der Phase p (0..1).
 * unfold: Anteil, um den haengende Federn die Faltung ihrer Armsegmente ausgleichen (sonst zeigen sie zur Mitte).
 */
const POSES = {
  stehen: rest(6),
  schleichen: rest(16),
  gleiten: {
    length: 2.4, frames: 8, unfold: 0,
    wing: (p) => [-14, -10 - 3 * wave(p), -6 + 2 * wave(p, 0.15)], // X: Hinterkante leicht angehoben, damit man die Federn sieht
    seg2: (p) => [0, -4 - 3 * wave(p, 0.15), 3 + wave(p, 0.25)],
    seg3: (p) => [0, -6 - 5 * wave(p, 0.3), 5 + 2 * wave(p, 0.4)],
    feather: (p, ph) => 2.6 * wave(p * 3, ph),
  },
  schlagen: {
    length: 0.9, frames: 12, unfold: 0,
    wing: (p) => [0, -14 - 34 * wave(p), 2 + 9 * wave(p, 0.08)],
    seg2: (p) => [0, -22 * wave(p, 0.13), 6 * wave(p, 0.18)],
    seg3: (p) => [0, -28 * wave(p, 0.25), 8 * wave(p, 0.3)],
    feather: (p, ph) => 5 * wave(p, 0.3 + ph * 0.25),
  },
  sturz: {
    length: 1.2, frames: 8, unfold: 0.25,
    wing: (p) => [0, -22 - 2 * wave(p), -38 + 1.5 * wave(p * 2)],
    seg2: () => [0, -6, -18],
    seg3: (p) => [0, -8 - 2 * wave(p * 2, 0.2), -20 + 2 * wave(p * 2, 0.3)],
    feather: (p, ph) => 3.5 * wave(p * 3, ph),
  },
};

const round = (v) => String(+v.toFixed(2));

/** Alle animierbaren Gruppen mit der Zahl der Armsegmente ueber ihnen (fuer den Federausgleich) */
function groups(model) {
  const defs = new Map((model.groups || []).map((g) => [g.uuid, g]));
  const out = [];
  let feather = 0;
  const walk = (node, chain) => {
    if (typeof node === 'string') return;
    const name = { ...defs.get(node.uuid), ...node }.name || '';
    const next = /^seg[23]$/.test(name) ? [...chain, name] : chain;
    if (name === 'wing') out.push({ uuid: node.uuid, name, kind: 'wing' });
    else if (name === 'seg2' || name === 'seg3') out.push({ uuid: node.uuid, name, kind: name });
    else if (name.startsWith('f_')) out.push({ uuid: node.uuid, name, kind: 'feather', chain, phase: (feather++ * 0.137) % 1 });
    for (const c of node.children || []) walk(c, name === 'wing' ? [...next, 'wing'] : next);
  };
  for (const n of model.outliner) walk(n, []);
  return out;
}

function value(pose, g, p) {
  if (g.kind !== 'feather') return pose[g.kind](p);
  // Federn: leichtes Wiegen, in angelegten Posen zurueckgedreht, damit sie weiter nach unten haengen
  const fold = g.chain.reduce((sum, name) => sum + pose[name](p)[2], 0);
  return [0, 0, pose.feather(p, g.phase) - fold * pose.unfold];
}

function animation(name, pose, list) {
  const animators = {};
  for (const g of list) {
    const keyframes = [];
    for (let i = 0; i <= pose.frames; i++) {
      const [x, y, z] = value(pose, g, (i % pose.frames) / pose.frames);
      keyframes.push({
        channel: 'rotation', data_points: [{ x: round(x), y: round(y), z: round(z) }],
        uuid: crypto.randomUUID(), time: +((i * pose.length) / pose.frames).toFixed(4), color: -1, interpolation: 'catmullrom',
      });
    }
    animators[g.uuid] = { name: g.name, type: 'bone', keyframes };
  }
  return { uuid: crypto.randomUUID(), name, loop: 'loop', override: false, length: pose.length, snapping: 24, selected: false, animators };
}

for (const id of IDS) {
  const file = path.join(dir, id + '.bbmodel');
  const model = JSON.parse(fs.readFileSync(file, 'utf8'));
  const list = groups(model);
  model.animations = Object.entries(POSES).map(([name, pose]) => animation(name, pose, list));
  fs.writeFileSync(file, JSON.stringify(model));
  console.log(id, list.length, 'Gruppen,', model.animations.length, 'Animationen');
}
