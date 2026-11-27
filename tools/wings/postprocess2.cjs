/**
 * Zweite Nachbearbeitung (nach postprocess.cjs): node tools/wings/postprocess2.cjs
 *  - alle Fluegel: Anschlussstueck von x = -1 (Wirbelsaeule, der Renderer versetzt um +1) zum Armansatz, beide Fluegel treffen sich am Ruecken;
 *    Drehpunkt der Gruppe "wing" liegt am Anschluss, damit dort beim Schlagen nichts aufklafft
 *  - smaragd: Vorderseite wie die Rueckseite (Texturen, Kiele, Funkeln), Armbalken geschlossen
 *  - engel: Glow dichter
 * Jedes Modell wird nur einmal bearbeitet (Merker "deu_post2").
 */
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { PNG } = require('pngjs');

const assets = path.resolve(__dirname, '..', '..', 'mod', 'mc26.3', 'src', 'main', 'resources', 'assets', 'deucore');
const ARM = /bone|arm|humerus|forearm|shoulder|knob|orb/i;
const SKIP = /halo|glow|barb|spur|claw|keel|star|spk|gem|^mem|connector/i;
const round = (v) => Math.round(v * 1000) / 1000;
const tex = (f) => f && f.texture !== null && f.texture !== undefined;
const clone = (o) => JSON.parse(JSON.stringify(o));
const flipU = (uv) => [uv[2], uv[1], uv[0], uv[3]];

function corners(e) {
  const o = e.origin || [0, 0, 0];
  const [rx, ry, rz] = (e.rotation || [0, 0, 0]).map((d) => (d * Math.PI) / 180);
  const out = [];
  for (const x of [e.from[0], e.to[0]]) for (const y of [e.from[1], e.to[1]]) for (const z of [e.from[2], e.to[2]]) {
    let p = [x - o[0], y - o[1], z - o[2]];
    p = [p[0], p[1] * Math.cos(rx) - p[2] * Math.sin(rx), p[1] * Math.sin(rx) + p[2] * Math.cos(rx)];
    p = [p[0] * Math.cos(ry) + p[2] * Math.sin(ry), p[1], -p[0] * Math.sin(ry) + p[2] * Math.cos(ry)];
    p = [p[0] * Math.cos(rz) - p[1] * Math.sin(rz), p[0] * Math.sin(rz) + p[1] * Math.cos(rz), p[2]];
    out.push([p[0] + o[0], p[1] + o[1], p[2] + o[2]]);
  }
  return out;
}

for (const id of ['engel', 'daemon', 'eis', 'phoenix', 'schatten', 'smaragd']) {
  const file = path.join(assets, 'models', 'wings', id + '.bbmodel');
  const model = JSON.parse(fs.readFileSync(file, 'utf8'));
  if (model.deu_post2) {
    console.log(id, 'schon bearbeitet');
    continue;
  }
  const defs = new Map((model.groups || []).map((g) => [g.uuid, g]));
  const wing = model.outliner.find((c) => typeof c === 'object');
  const wingDef = defs.get(wing.uuid);
  const add = (src, name, list) => {
    const e = clone(src);
    e.uuid = crypto.randomUUID();
    e.name = name;
    model.elements.push(e);
    list.push(e.uuid);
    return e;
  };

  if (id === 'smaragd') {
    for (const e of model.elements) {
      if (/_bar/.test(e.name)) {
        for (const d of ['up', 'down']) if (!tex(e.faces[d]) && tex(e.faces.east)) e.faces[d] = clone(e.faces.east);
      }
      if (tex(e.faces.south)) e.faces.north = { ...clone(e.faces.south), uv: flipU(e.faces.south.uv) };
    }
    // Kiele und Funkelplaettchen an der Tiefe des Armbalkens nach vorne spiegeln
    const bar = model.elements.find((e) => /arm1_bar/.test(e.name));
    const zc = (bar.from[2] + bar.to[2]) / 2;
    const parentOf = new Map();
    (function walk(g) {
      for (const c of g.children || []) {
        if (typeof c === 'string') parentOf.set(c, g.children);
        else walk(c);
      }
    })(wing);
    for (const e of [...model.elements]) {
      if (!/keel|star/i.test(e.name)) continue;
      const m = add(e, e.name + '_front', parentOf.get(e.uuid) || wing.children);
      [m.from[2], m.to[2]] = [round(2 * zc - e.to[2]), round(2 * zc - e.from[2])];
      if (m.origin) m.origin[2] = round(2 * zc - m.origin[2]);
      if (m.rotation) m.rotation = [-m.rotation[0], -m.rotation[1], m.rotation[2]];
      if (tex(e.faces.south)) m.faces.north = { ...clone(e.faces.south), uv: flipU(e.faces.south.uv) };
    }
  }

  // Anschluss am Ruecken
  const candidates = model.elements.filter((e) => ARM.test(e.name) && !SKIP.test(e.name));
  let best = null;
  for (const e of candidates) {
    const c = corners(e);
    const minX = Math.min(...c.map((p) => p[0]));
    if (!best || minX < best.minX) best = { e, c, minX };
  }
  const inner = [...best.c].sort((a, b) => a[0] - b[0]).slice(0, 4);
  // engel und smaragd: Anschluss an der Wurzel (Schulter-Edelstein), sonst am inneren Armende
  const root = { engel: [2, 0, 3.4], smaragd: [4.4, 1, 3.15] }[id];
  const yc = root ? root[1] : inner.reduce((s, p) => s + p[1], 0) / 4;
  const zc = root ? root[2] : inner.reduce((s, p) => s + p[2], 0) / 4;
  const con = add(best.e, 'connector', wing.children);
  con.from = [-1, round(yc - 1.1), round(zc - 0.9)];
  con.to = [round(root ? root[0] : Math.max(best.minX, 0) + 1.8), round(yc + 1.1), round(zc + 0.9)];
  con.origin = [-1, round(yc), round(zc)];
  con.rotation = [0, 0, 0];
  const base = best.e.faces.south && tex(best.e.faces.south) ? best.e.faces.south : Object.values(best.e.faces).find(tex);
  con.faces = {};
  for (const d of ['north', 'south', 'east', 'up', 'down']) con.faces[d] = clone(tex(best.e.faces[d]) ? best.e.faces[d] : base);
  con.faces.west = { uv: [0, 0, 0, 0], texture: null };
  for (const g of [wing, wingDef]) if (g && g.origin) g.origin = [-1, round(yc), round(zc)];

  if (id === 'engel') {
    const texFile = path.join(assets, 'textures', 'cosmetics', 'wings', id + '.png');
    const img = PNG.sync.read(fs.readFileSync(texFile));
    const src = Buffer.from(img.data);
    const sx = img.width / model.resolution.width, sy = img.height / model.resolution.height;
    for (const e of model.elements) {
      if (!/halo|glow/i.test(e.name)) continue;
      for (const f of Object.values(e.faces)) {
        if (!tex(f)) continue;
        const x0 = Math.floor(Math.min(f.uv[0], f.uv[2]) * sx), x1 = Math.ceil(Math.max(f.uv[0], f.uv[2]) * sx);
        const y0 = Math.floor(Math.min(f.uv[1], f.uv[3]) * sy), y1 = Math.ceil(Math.max(f.uv[1], f.uv[3]) * sy);
        for (let y = y0; y < y1; y++) {
          for (let x = x0; x < x1; x++) {
            // um 2 Texel verbreitern (nur innerhalb der Insel), dann Deckkraft anheben
            let a = 0, c = (y * img.width + x) * 4;
            for (let yy = Math.max(y0, y - 2); yy <= Math.min(y1 - 1, y + 2); yy++) {
              for (let xx = Math.max(x0, x - 2); xx <= Math.min(x1 - 1, x + 2); xx++) {
                const i = (yy * img.width + xx) * 4;
                if (src[i + 3] > a) { a = src[i + 3]; c = i; }
              }
            }
            const o = (y * img.width + x) * 4;
            for (let k = 0; k < 3; k++) img.data[o + k] = src[c + k];
            img.data[o + 3] = Math.min(240, Math.round(a * 1.5));
          }
        }
      }
    }
    fs.writeFileSync(texFile, PNG.sync.write(img));
    for (const t of model.textures || []) if (t.source) t.source = 'data:image/png;base64,' + fs.readFileSync(texFile).toString('base64');
  }

  model.deu_post2 = true;
  fs.writeFileSync(file, JSON.stringify(model));
  console.log(id, 'bearbeitet,', model.elements.length, 'Cubes, Anschluss an', best.e.name, 'x', round(best.minX), 'y', round(yc), 'z', round(zc));
}
