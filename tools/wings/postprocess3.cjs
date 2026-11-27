/**
 * Dritte Nachbearbeitung (nach postprocess2.cjs): node tools/wings/postprocess3.cjs
 *  - engel: Glow-Rand entfernt
 *  - alle Fluegel: Armbalken der Segmente leicht verschieden tief, damit an den Gelenken nichts flackert
 *  - smaragd: Gelenk-Edelsteine stehen vorn und hinten klar ueber den Balken, flache Funkelplaettchen entfernt
 * Jedes Modell wird nur einmal bearbeitet (Merker "deu_post3").
 */
const fs = require('node:fs');
const path = require('node:path');

const dir = path.resolve(__dirname, '..', '..', 'mod', 'mc26', 'src', 'main', 'resources', 'assets', 'deucore', 'models', 'wings');
const round = (v) => Math.round(v * 1000) / 1000;
const BAR = /bone|_bar|keel|^arm|humerus|forearm/i;

for (const id of ['engel', 'daemon', 'eis', 'phoenix', 'schatten', 'smaragd']) {
  const file = path.join(dir, id + '.bbmodel');
  const model = JSON.parse(fs.readFileSync(file, 'utf8'));
  if (model.deu_post3) {
    console.log(id, 'schon bearbeitet');
    continue;
  }
  const remove = new Set(
    model.elements.filter((e) => (id === 'engel' && /halo|glow/i.test(e.name)) || (id === 'smaragd' && /star/i.test(e.name))).map((e) => e.uuid),
  );
  model.elements = model.elements.filter((e) => !remove.has(e.uuid));
  (function prune(list) {
    for (let i = list.length - 1; i >= 0; i--) {
      if (typeof list[i] === 'string') {
        if (remove.has(list[i])) list.splice(i, 1);
      } else prune(list[i].children || []);
    }
  })(model.outliner);

  // sich kreuzende Balken duerfen keine deckungsgleichen Flaechen haben: jeder bekommt eine eigene Tiefe
  let n = 0;
  for (const e of model.elements) {
    if (!BAR.test(e.name)) continue;
    const d = 0.07 * (n++ % 5);
    if (e.to[2] - e.from[2] > 0.6) {
      e.from[2] = round(e.from[2] + d);
      e.to[2] = round(e.to[2] - d);
    }
  }
  if (id === 'smaragd') {
    const bars = model.elements.filter((e) => BAR.test(e.name));
    const lo = Math.min(...bars.map((e) => e.from[2])), hi = Math.max(...bars.map((e) => e.to[2]));
    let k = 0;
    for (const e of model.elements) {
      if (!/gem|sg_/i.test(e.name)) continue;
      const d = 0.25 + 0.12 * k++;
      e.from[2] = round(lo - d);
      e.to[2] = round(hi + d);
      if (e.origin) e.origin[2] = round((lo + hi) / 2);
    }
  }
  model.deu_post3 = true;
  fs.writeFileSync(file, JSON.stringify(model));
  console.log(id, 'bearbeitet,', model.elements.length, 'Cubes, entfernt', remove.size);
}
