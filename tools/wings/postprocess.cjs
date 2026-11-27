/**
 * Nachbearbeitung der Fluegelmodelle (nach tools/wings/<id>.cjs ausfuehren): node tools/wings/postprocess.cjs
 *  - haengende Teile (Federn, Zacken) liegen in der Tiefe direkt unter dem Arm ihres Segments, ohne Kippung um X
 *  - engel: nur noch eine Glow-Lage in der Mitte, dichter
 *  - eis: Armstuecke und Spitzen oben und unten geschlossen
 * Jedes Modell wird nur einmal bearbeitet (Merker "deu_post").
 */
const fs = require('node:fs');
const path = require('node:path');
const { PNG } = require('pngjs');

const assets = path.resolve(__dirname, '..', '..', 'mod', 'mc26', 'src', 'main', 'resources', 'assets', 'deucore');
const ARM = /bone|arm|humerus|forearm|shoulder|elbow|wrist|knob|orb|gem|sg_|claw|spur|thumb|spk/i;
const HALO = /halo|glow/i;
const SPREAD = 0.35; // verbleibender Anteil der urspruenglichen Tiefenstaffelung

const round = (v) => Math.round(v * 1000) / 1000;
const mid = (e) => (e.from[2] + e.to[2]) / 2;
const mean = (a) => a.reduce((s, v) => s + v, 0) / a.length;
function moveZ(e, dz) {
  e.from[2] = round(e.from[2] + dz);
  e.to[2] = round(e.to[2] + dz);
  if (e.origin) e.origin[2] = round(e.origin[2] + dz);
}

for (const id of ['engel', 'daemon', 'eis', 'phoenix', 'schatten', 'smaragd']) {
  const file = path.join(assets, 'models', 'wings', id + '.bbmodel');
  const model = JSON.parse(fs.readFileSync(file, 'utf8'));
  if (model.deu_post) {
    console.log(id, 'schon bearbeitet');
    continue;
  }
  const defs = new Map((model.groups || []).map((g) => [g.uuid, g]));
  const els = new Map(model.elements.map((e) => [e.uuid, e]));
  const parts = []; // { el, seg, feather }
  (function walk(list, seg, feather) {
    for (const c of list) {
      if (typeof c === 'string') {
        parts.push({ el: els.get(c), seg, feather });
        continue;
      }
      const def = defs.get(c.uuid);
      const name = c.name || (def && def.name) || '';
      const isFeather = feather || name.startsWith('f_');
      if (isFeather) {
        for (const g of [c, def]) if (g && g.rotation) g.rotation[0] = 0;
      }
      walk(c.children || [], /^seg\d+$/.test(name) ? name : seg, isFeather);
    }
  })(model.outliner, 'seg1', false);

  // daemon: Membran und Fingerknochen haengen schon zusammen
  if (id !== 'daemon') {
    const armParts = parts.filter((p) => ARM.test(p.el.name) && !HALO.test(p.el.name) && !/spk|star|keel/i.test(p.el.name));
    const armAll = mean(armParts.map((p) => mid(p.el)));
    for (const seg of new Set(parts.map((p) => p.seg))) {
      const arm = armParts.filter((p) => p.seg === seg);
      const armZ = arm.length ? mean(arm.map((p) => mid(p.el))) : armAll;
      const hang = parts.filter((p) => p.seg === seg && !ARM.test(p.el.name) && !HALO.test(p.el.name));
      if (!hang.length) continue;
      const hz = mean(hang.map((p) => mid(p.el)));
      for (const p of hang) {
        moveZ(p.el, armZ + (mid(p.el) - hz) * SPREAD - mid(p.el));
        if (p.el.rotation) p.el.rotation[0] = 0;
      }
    }
    if (id === 'engel') {
      const remove = new Set(parts.filter((p) => /halo_hi|glow_hi/i.test(p.el.name)).map((p) => p.el.uuid));
      model.elements = model.elements.filter((e) => !remove.has(e.uuid));
      (function prune(list) {
        for (let i = list.length - 1; i >= 0; i--) {
          const c = list[i];
          if (typeof c === 'string') {
            if (remove.has(c)) list.splice(i, 1);
          } else prune(c.children || []);
        }
      })(model.outliner);
      // eine Lage in der Mitte (Armtiefe), Deckkraft deutlich erhoeht
      const texFile = path.join(assets, 'textures', 'cosmetics', 'wings', id + '.png');
      const tex = PNG.sync.read(fs.readFileSync(texFile));
      const sx = tex.width / model.resolution.width, sy = tex.height / model.resolution.height;
      const done = new Set();
      for (const p of parts) {
        if (!HALO.test(p.el.name) || remove.has(p.el.uuid)) continue;
        moveZ(p.el, armAll - mid(p.el));
        for (const f of Object.values(p.el.faces)) {
          if (f.texture === null || f.texture === undefined) continue;
          const x0 = Math.floor(Math.min(f.uv[0], f.uv[2]) * sx), x1 = Math.ceil(Math.max(f.uv[0], f.uv[2]) * sx);
          const y0 = Math.floor(Math.min(f.uv[1], f.uv[3]) * sy), y1 = Math.ceil(Math.max(f.uv[1], f.uv[3]) * sy);
          for (let y = y0; y < y1; y++) {
            for (let x = x0; x < x1; x++) {
              const i = y * tex.width + x;
              if (done.has(i)) continue;
              done.add(i);
              tex.data[i * 4 + 3] = Math.min(220, Math.round(tex.data[i * 4 + 3] * 2.2));
            }
          }
        }
      }
      fs.writeFileSync(texFile, PNG.sync.write(tex));
      for (const t of model.textures || []) if (t.source) t.source = 'data:image/png;base64,' + fs.readFileSync(texFile).toString('base64');
    }
  }
  if (id === 'eis') {
    let closed = 0;
    for (const e of model.elements) {
      if (!/arm|spk/i.test(e.name)) continue;
      const side = e.faces.north && e.faces.north.texture !== null ? e.faces.north : e.faces.east;
      if (!side) continue;
      // Deckel aus dem oberen Stueck der Seitenflaeche
      const [u1, v1, u2, v2] = side.uv;
      const h = Math.min(Math.abs(v2 - v1), Math.abs(u2 - u1));
      for (const dir of ['up', 'down']) {
        const f = e.faces[dir];
        if (f && f.texture !== null && f.texture !== undefined) continue;
        e.faces[dir] = { uv: [u1, v1, u2, v1 + Math.sign(v2 - v1) * h], texture: side.texture };
        closed++;
      }
    }
    console.log('eis: Flaechen geschlossen', closed);
  }
  model.deu_post = true;
  fs.writeFileSync(file, JSON.stringify(model));
  console.log(id, 'bearbeitet,', model.elements.length, 'Cubes');
}
