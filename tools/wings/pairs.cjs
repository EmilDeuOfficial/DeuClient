/**
 * Ansichtsdateien mit beiden Fluegeln fuer Blockbench: node tools/wings/pairs.cjs
 * Ergebnis: tools/wings/paare/<id>_paar.bbmodel (linker Fluegel gespiegelt, wie im Spiel).
 * Nur zum Anschauen: das Spiel laedt weiterhin die Ein-Fluegel-Modelle.
 */
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

const src = path.resolve(__dirname, '..', '..', 'mod', 'mc26.3', 'src', 'main', 'resources', 'assets', 'deucore', 'models', 'wings');
const out = path.join(__dirname, 'paare');
fs.mkdirSync(out, { recursive: true });
const clone = (o) => JSON.parse(JSON.stringify(o));
const SPINE = 1; // gleicher Versatz wie im Renderer

for (const id of ['engel', 'daemon', 'eis', 'phoenix', 'schatten', 'smaragd']) {
  const model = JSON.parse(fs.readFileSync(path.join(src, id + '.bbmodel'), 'utf8'));
  const defs = new Map((model.groups || []).map((g) => [g.uuid, g]));
  const els = new Map(model.elements.map((e) => [e.uuid, e]));
  const elements = [];
  const groups = [];

  // side +1 = rechts wie im Modell, -1 = gespiegelt
  const point = (p, side) => (p ? [side * (p[0] + SPINE), p[1], p[2]] : p);
  const turn = (r, side) => (r ? [r[0], side * r[1], side * r[2]] : r);
  function element(e, side) {
    const c = clone(e);
    c.uuid = crypto.randomUUID();
    const a = side * (e.from[0] + SPINE), b = side * (e.to[0] + SPINE);
    c.from[0] = Math.min(a, b);
    c.to[0] = Math.max(a, b);
    c.origin = point(e.origin || [0, 0, 0], side);
    c.rotation = turn(e.rotation, side);
    if (side < 0) {
      const f = c.faces;
      [f.east, f.west] = [f.west, f.east];
      for (const k of Object.keys(f)) if (f[k] && f[k].uv) f[k].uv = [f[k].uv[2], f[k].uv[1], f[k].uv[0], f[k].uv[3]];
    }
    elements.push(c);
    return c.uuid;
  }
  function group(g, side) {
    const def = defs.get(g.uuid);
    const uuid = crypto.randomUUID();
    if (side < 0) mirrored.set(g.uuid, uuid);
    else mirrored.set('rechts:' + g.uuid, uuid);
    const patch = (o) => {
      const c = clone(o);
      c.uuid = uuid;
      if (c.name && side < 0) c.name += '_links';
      if (c.origin) c.origin = point(c.origin, side);
      if (c.rotation) c.rotation = turn(c.rotation, side);
      return c;
    };
    if (def) groups.push(patch({ ...def, children: undefined }));
    const node = patch({ ...g, children: undefined });
    node.children = (g.children || []).map((c) => (typeof c === 'string' ? element(els.get(c), side) : group(c, side)));
    return node;
  }
  const mirrored = new Map(); // Gruppe -> Kennung der gespiegelten Kopie
  const outliner = [];
  for (const side of [1, -1]) for (const c of model.outliner) outliner.push(typeof c === 'string' ? element(els.get(c), side) : group(c, side));
  // Animationen fuer beide Seiten: links mit gespiegelten Drehungen (Y und Z umgekehrt)
  const animations = (model.animations || []).map((a) => {
    const animators = {};
    for (const [key, an] of Object.entries(a.animators)) {
      if (mirrored.has('rechts:' + key)) animators[mirrored.get('rechts:' + key)] = an;
      if (!mirrored.has(key)) continue;
      const copy = clone(an);
      copy.name += '_links';
      for (const k of copy.keyframes) {
        k.uuid = crypto.randomUUID();
        if (k.channel === 'rotation') for (const p of k.data_points) for (const axis of ['y', 'z']) p[axis] = String(-Number(p[axis]));
      }
      animators[mirrored.get(key)] = copy;
    }
    return { ...clone(a), uuid: crypto.randomUUID(), animators };
  });
  const pair = { ...model, name: id + '_paar', elements, outliner, animations };
  if (model.groups) pair.groups = groups;
  fs.writeFileSync(path.join(out, id + '_paar.bbmodel'), JSON.stringify(pair));
  console.log(id, elements.length, 'Cubes');
}
