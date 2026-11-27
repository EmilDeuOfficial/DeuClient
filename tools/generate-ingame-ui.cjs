/**
 * Erzeugt die Oberflächen-Grafiken für den DeuClient (DeuCore 26.3):
 *   - Schriften: Inter Medium / ExtraBold (SIL Open Font License) als glatte UI-Schrift
 *   - Linien-Icons (weiß, 64×64) für Buttons, Karten und Tabs
 *   - Galaxie-Flügel (zwei Ebenen: große obere + kleine untere Flügel) in mehreren Farben
 */
const fs = require('node:fs');
const path = require('node:path');
const { Resvg } = require('@resvg/resvg-js');

const assets = path.resolve(__dirname, '..', 'mod', 'mc26', 'src', 'main', 'resources', 'assets', 'deucore');
const write = (rel, buf) => {
  const f = path.join(assets, rel);
  fs.mkdirSync(path.dirname(f), { recursive: true });
  fs.writeFileSync(f, buf);
};
const png = (svg, w) => new Resvg(svg, { fitTo: { mode: 'width', value: w } }).render().asPng();
const pngSmooth = (svg, w) => new Resvg(svg, { fitTo: { mode: 'width', value: w }, shapeRendering: 2 }).render().asPng();

/* ---------------- Schrift ---------------- */
const interDir = path.resolve(__dirname, '..', 'node_modules', '@expo-google-fonts', 'inter');
write('font/inter_medium.ttf', fs.readFileSync(path.join(interDir, '500Medium', 'Inter_500Medium.ttf')));
write('font/inter_bold.ttf', fs.readFileSync(path.join(interDir, '800ExtraBold', 'Inter_800ExtraBold.ttf')));
write('font/inter_license.txt', fs.readFileSync(path.join(interDir, 'LICENSE_FONT')));
// Die Schrift-Textur wird von Minecraft ohne Filter (NEAREST) gezeichnet: nur bei oversample == GUI-Skalierung
// landet jedes Textur-Pixel genau auf einem Bildschirm-Pixel (scharf). Daher eine Schrift je GUI-Skalierung (ui_2, ui_bold_3 ...).
const ttf = (file, size, oversample) => ({ providers: [{ type: 'ttf', file: `deucore:${file}`, shift: [0, 0.5], size, oversample, skip: '' }, { type: 'reference', id: 'minecraft:default' }] });
write('font/ui.json', Buffer.from(JSON.stringify(ttf('inter_medium.ttf', 8.5, 2.0), null, 2)));
write('font/ui_bold.json', Buffer.from(JSON.stringify(ttf('inter_bold.ttf', 8.5, 2.0), null, 2)));
// eine Schrift je Viertelschritt (ui_x4 = 1.0 bis ui_x48 = 12.0): HUD-Elemente bleiben in jeder Groesse scharf
for (let q = 4; q <= 48; q++) {
  write(`font/ui_x${q}.json`, Buffer.from(JSON.stringify(ttf('inter_medium.ttf', 8.5, q / 4), null, 2)));
  write(`font/ui_bold_x${q}.json`, Buffer.from(JSON.stringify(ttf('inter_bold.ttf', 8.5, q / 4), null, 2)));
  // Kartentitel im Mod-Menue: groesser, normale Staerke
  write(`font/ui_title_x${q}.json`, Buffer.from(JSON.stringify(ttf('inter_medium.ttf', 10, q / 4), null, 2)));
}
console.log('  ✓ Schrift Inter (ui, ui_bold, je Viertelschritt 1-12)');

/* ---------------- Linien-Icons ---------------- */
// 24×24, weiße Linien - im Spiel per Farbe eingefärbt
const ICONS = {
  grid: '<rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/>',
  play: '<path fill="#fff" d="M8.5 5.5v13L18.5 12z"/>',
  lock: '<rect x="5" y="11" width="14" height="10" rx="2"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/><circle cx="12" cy="16" r="1.2" fill="#fff"/>',
  unlock: '<rect x="5" y="11" width="14" height="10" rx="2"/><path d="M8 11V8a4 4 0 0 1 7.5-2"/><circle cx="12" cy="16" r="1.2" fill="#fff"/>',
  star: '<path d="M12 3.5l2.6 5.4 5.9.8-4.3 4.1 1 5.8L12 16.9l-5.2 2.7 1-5.8-4.3-4.1 5.9-.8z"/>',
  gear: '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 0 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 0 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 0 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 0 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/>',
  heart: '<path d="M12 6.00019C10.2006 3.90317 7.19377 3.2551 4.93923 5.17534C2.68468 7.09558 2.36727 10.3061 4.13778 12.5772C5.60984 14.4654 10.0648 18.4479 11.5249 19.7369C11.6882 19.8811 11.7699 19.9532 11.8652 19.9815C11.9483 20.0062 12.0393 20.0062 12.1225 19.9815C12.2178 19.9532 12.2994 19.8811 12.4628 19.7369C13.9229 18.4479 18.3778 14.4654 19.8499 12.5772C21.6204 10.3061 21.3417 7.07538 19.0484 5.17534C16.7551 3.2753 13.7994 3.90317 12 6.00019Z"/>',
  heart_filled: '<path fill="#fff" d="M12 6.00019C10.2006 3.90317 7.19377 3.2551 4.93923 5.17534C2.68468 7.09558 2.36727 10.3061 4.13778 12.5772C5.60984 14.4654 10.0648 18.4479 11.5249 19.7369C11.6882 19.8811 11.7699 19.9532 11.8652 19.9815C11.9483 20.0062 12.0393 20.0062 12.1225 19.9815C12.2178 19.9532 12.2994 19.8811 12.4628 19.7369C13.9229 18.4479 18.3778 14.4654 19.8499 12.5772C21.6204 10.3061 21.3417 7.07538 19.0484 5.17534C16.7551 3.2753 13.7994 3.90317 12 6.00019Z"/>',
  close: '<path d="M6 6l12 12M18 6 6 18"/>',
  person: '<circle cx="12" cy="8" r="4"/><path d="M4 21c0-4 3.6-7 8-7s8 3 8 7"/>',
  people: '<circle cx="9" cy="8" r="3.5"/><path d="M2.5 20c0-3.6 2.9-6 6.5-6s6.5 2.4 6.5 6"/><path d="M16 4.6a3.5 3.5 0 0 1 0 6.8M18 14.3c2.2.7 3.5 2.8 3.5 5.7"/>',
  search: '<circle cx="11" cy="11" r="7"/><path d="M21 21l-4.5-4.5"/>',
  power: '<path d="M12 3v8"/><path d="M6.4 6.6a8 8 0 1 0 11.2 0"/>',
  home: '<path d="M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z"/>',
  hourglass: '<path d="M6 3h12M6 21h12M7 3c0 5 10 5 10 9s-10 4-10 9M17 3c0 5-10 5-10 9s10 4 10 9"/>',
  button: '<rect x="2.5" y="7" width="19" height="10" rx="3"/><path d="M8 12h8"/>',
  sparkle: '<path transform="translate(0 1)" fill="#fff" d="M12 2c.7 5.6 3.3 8.2 9 9-5.7.8-8.3 3.4-9 9-.7-5.6-3.3-8.2-9-9 5.7-.8 8.3-3.4 9-9z"/>',
  window: '<rect x="3" y="4" width="18" height="16" rx="2"/><path d="M3 9h18M7 6.5h.01M10 6.5h.01"/>',
  info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v6M12 7.5h.01"/>',
  tag: '<path d="M3 12V4a1 1 0 0 1 1-1h8l9 9-9 9z"/><circle cx="8" cy="8" r="1.5"/>',
  zoom: '<circle cx="11" cy="11" r="7"/><path d="M21 21l-4.5-4.5M11 8v6M8 11h6"/>',
  crosshair: '<circle cx="12" cy="12" r="8"/><path d="M12 2v5M12 17v5M2 12h5M17 12h5"/>',
  cape: '<path d="M8 3h8l3 3-1.5 15H6.5L5 6z"/><path d="M8 3c0 2 1.8 3.5 4 3.5S16 5 16 3"/>',
  wings: '<g transform="translate(0 3)"><path d="M12 9C9 5 5 4 2 5c1 3 2 5 5 6-2 0-3 1-3 3 3 0 5-1 8-3z"/><path d="M12 9c3-4 7-5 10-4-1 3-2 5-5 6 2 0 3 1 3 3-3 0-5-1-8-3z"/></g>',
  hud: '<rect x="3" y="4" width="18" height="16" rx="2"/><path d="M7 9h4M7 13h7M15 9h2"/>',
  chat: '<path d="M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z"/>',
  music: '<path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/>',
  eye: '<path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/>',
  globe: '<circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3a14 14 0 0 1 0 18M12 3a14 14 0 0 0 0 18"/>',
  paint: '<path d="M12 3a9 9 0 0 0 0 18c1.5 0 2-1 2-2 0-1.5-1-2-1-3s.5-2 2-2h2a4 4 0 0 0 4-4c0-4-4-7-9-7z"/><circle cx="7.5" cy="10.5" r="1"/><circle cx="12" cy="7.5" r="1"/><circle cx="16.5" cy="10.5" r="1"/>',
  moon: '<path d="M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z"/>',
  language: '<path d="M4 5h8M8 3v2M5 13c2-1.5 4-4.5 5-8M7 9c1 2 3 4 5 5"/><path d="M13 21l4-9 4 9M14.5 18h5"/>',
  edit: '<path d="M4 20h4L19 9a2.8 2.8 0 0 0-4-4L4 16zM13.5 6.5l4 4"/>',
  chevron_left: '<path d="M15 6l-6 6 6 6"/>',
  chevron_right: '<path d="M9 6l6 6-6 6"/>',
  minus: '<path d="M5 12h14"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  check: '<path d="M5 12.5 10 17.5 19.5 7"/>',
  server: '<rect x="3" y="4" width="18" height="7" rx="2"/><rect x="3" y="13" width="18" height="7" rx="2"/><path d="M7 7.5h.01M7 16.5h.01"/>',
};
for (const [name, body] of Object.entries(ICONS)) {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="64" height="64" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${body}</svg>`;
  write(`textures/gui/icons/${name}.png`, png(svg, 64));
}
console.log(`  ✓ ${Object.keys(ICONS).length} Linien-Icons`);

/* ---------------- HUD-Icons aus img/*.svg ---------------- */
// Die mitgelieferten SVGs werden weiss gefaerbt, auf ihre sichtbare Ausdehnung zugeschnitten und quadratisch
// mit 256 px gespeichert. Das Spiel verkleinert sie zur Laufzeit exakt auf Bildschirm-Pixel (scharf).
{
  const imgDir = path.resolve(__dirname, '..', 'img');
  const white = (svg) => svg.replace(/(fill|stroke)="#[0-9a-fA-F]{3,8}"/g, '$1="#fff"');
  const extent = (svg, vb) => {
    const r = new Resvg(svg.replace(/width="[^"]*" height="[^"]*"/, 'width="1200" height="1200"'), { fitTo: { mode: 'width', value: 1200 } }).render();
    const { width, height, pixels } = r;
    let x0 = width, y0 = height, x1 = -1, y1 = -1;
    for (let y = 0; y < height; y++) for (let x = 0; x < width; x++) if (pixels[(y * width + x) * 4 + 3] > 6) { x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y); }
    const k = vb / width;
    return { x: x0 * k, y: y0 * k, w: (x1 - x0 + 1) * k, h: (y1 - y0 + 1) * k };
  };
  const fit = (svg, vb, ext) => {
    const size = Math.max(ext.w, ext.h) * 1.06;
    const vx = ext.x + ext.w / 2 - size / 2, vy = ext.y + ext.h / 2 - size / 2;
    return svg.replace(/width="[^"]*" height="[^"]*" viewBox="[^"]*"/, `width="256" height="256" viewBox="${vx.toFixed(3)} ${vy.toFixed(3)} ${size.toFixed(3)} ${size.toFixed(3)}"`);
  };
  const out = (name, svg) => write(`textures/gui/icons_hd/${name}.png`, new Resvg(svg, { fitTo: { mode: 'width', value: 256 } }).render().asPng());
  let n = 0;
  for (const [name, file] of [['hud_fps', 'fps'], ['hud_speed', 'speed'], ['hud_time', 'time'], ['hud_sun', 'sun'], ['hud_moon', 'moon'], ['hud_ping', 'ping']]) {
    const svg = white(fs.readFileSync(path.join(imgDir, file + '.svg'), 'utf8'));
    const vb = Number(/viewBox="0 0 (\d+(?:\.\d+)?) /.exec(svg)[1]);
    const ext = extent(svg, vb);
    out(name, fit(svg, vb, ext));
    n++;
    if (name === 'hud_ping') {
      // Balken einzeln (Signalstaerke): Pfad an "z" trennen, relative Startpunkte in absolute umrechnen
      const d = /<path d="([^"]+)"/.exec(svg)[1];
      let sx = 0, sy = 0;
      const bars = [];
      d.split(/z/i).map((p) => p.trim()).filter((p) => /c/.test(p)).forEach((p, i) => {
        const m = /^m\s+(-?[\d.]+)\s+(-?[\d.]+)\s*(.*)$/s.exec(p);
        const dx = Number(m[1]), dy = Number(m[2]);
        if (i === 0) { sx = dx; sy = dy; } else { sx += dx; sy += dy; }
        bars.push(`M ${sx} ${sy} ${m[3]} z`);
      });
      bars.forEach((b, i) => out(`hud_ping${i + 1}`, fit(svg.replace(/<path d="[^"]+"/, `<path d="${b}"`), vb, ext)));
      n += bars.length;
    }
  }
  console.log(`  ✓ ${n} HUD-Icons aus img/*.svg`);
}

/* ---------------- Galaxie-Flügel ---------------- */
const WINGS = {
  engel: { base: '#8f94a8', mid: '#e7e9f2', light: '#ffffff', edge: '#5d6273', glow: '#ffe8a3' },
  daemon: { base: '#0a0203', mid: '#5c0a10', light: '#e11d2e', edge: '#000000', glow: '#ff7a59' },
  eis: { base: '#06173a', mid: '#1d6fb8', light: '#9ee9ff', edge: '#020a1c', glow: '#e0fbff' },
  phoenix: { base: '#3d0702', mid: '#d9480f', light: '#ffd43b', edge: '#1f0300', glow: '#fff3bf' },
  schatten: { base: '#050307', mid: '#2b1745', light: '#7c3aed', edge: '#000000', glow: '#a78bfa' },
  smaragd: { base: '#02210f', mid: '#0f8a46', light: '#69f0ae', edge: '#01110a', glow: '#d3ffe4' },
};

// deterministischer Zufall für Sterne
let seed = 1;
const rnd = () => {
  seed = (seed * 16807) % 2147483647;
  return seed / 2147483647;
};

/**
 * Ein Flügel: Arm-Bogen von der Wurzel (links) zur Spitze (rechts oben),
 * Federn hängen vom Arm nach unten-außen (drei Lagen: Schwung-, Arm- und Deckfedern).
 */
function wingSvg(c, W, H, scale) {
  seed = 7;
  const A = [16, H * 0.44], C = [W * 0.42, H * 0.1], B = [W - 8, H * 0.06];
  const P = (t) => [
    (1 - t) ** 2 * A[0] + 2 * (1 - t) * t * C[0] + t * t * B[0],
    (1 - t) ** 2 * A[1] + 2 * (1 - t) * t * C[1] + t * t * B[1],
  ];
  /** Feder mit runder Spitze: Basis am Arm, hängt im Winkel nach unten-außen */
  const feather = (t, len, width, angleDeg, grad) => {
    const [bx, by] = P(t);
    const a = (angleDeg * Math.PI) / 180;
    const dx = Math.sin(a), dy = Math.cos(a); // 0° = senkrecht nach unten, positiv = nach außen
    const nx = dy, ny = -dx; // Normale (zur Flügelspitze hin)
    const w = width / 2;
    const tx = bx + dx * len, ty = by + dy * len;
    const f = (x) => x.toFixed(1);
    const c1 = [bx + dx * len * 0.25 + nx * w * 1.1, by + dy * len * 0.25 + ny * w * 1.1];
    const c2 = [tx + nx * w * 0.9 - dx * w * 0.6, ty + ny * w * 0.9 - dy * w * 0.6];
    const c3 = [tx - nx * w * 0.9 - dx * w * 0.6, ty - ny * w * 0.9 - dy * w * 0.6];
    const c4 = [bx + dx * len * 0.25 - nx * w * 1.1, by + dy * len * 0.25 - ny * w * 1.1];
    const tip = [tx + dx * w * 0.35, ty + dy * w * 0.35];
    const d = `M${f(bx)} ${f(by)} C${f(c1[0])} ${f(c1[1])} ${f(c2[0])} ${f(c2[1])} ${f(tip[0])} ${f(tip[1])} C${f(c3[0])} ${f(c3[1])} ${f(c4[0])} ${f(c4[1])} ${f(bx)} ${f(by)} Z`;
    // Mittelkiel der Feder
    const q = `M${f(bx + dx * len * 0.15)} ${f(by + dy * len * 0.15)} L${f(tx - dx * w * 0.3)} ${f(ty - dy * w * 0.3)}`;
    return { d, q, grad };
  };
  const layers = [];
  // Schwungfedern (lang, außen) - von innen nach außen, damit die äußeren oben liegen
  for (let i = 0; i < 8; i++) {
    const t = 0.4 + (i / 7) * 0.58;
    layers.push(feather(t, Math.min((50 + i * 6) * scale, H * 0.8), 17 * scale, 10 + i * 4.5, "p"));
  }
  // Armfedern (mittel, innen)
  for (let i = 0; i < 7; i++) {
    const t = 0.03 + (i / 6) * 0.42;
    layers.push(feather(t, (40 + i * 3) * scale, 17 * scale, 2 + i * 1.5, 's'));
  }
  // Deckfedern (kurz, rund, am Arm)
  for (let i = 0; i < 10; i++) {
    const t = 0.02 + (i / 9) * 0.92;
    layers.push(feather(t, (20 + (i % 2) * 4) * scale, 15 * scale, 6 + i * 4, 'c'));
  }
  const paths = layers.map((f) => `<path d="${f.d}" fill="url(#${f.grad})" stroke="${c.edge}" stroke-width="${1.6 * scale}" stroke-linejoin="round"/><path d="${f.q}" stroke="${c.light}" stroke-opacity="0.35" stroke-width="${0.8 * scale}"/>`).join('');
  const clip = layers.map((f) => `<path d="${f.d}"/>`).join('');
  let stars = '';
  for (let i = 0; i < 70; i++) {
    const r = rnd() < 0.85 ? 0.6 : 1.2;
    stars += `<circle cx="${(rnd() * W).toFixed(1)}" cy="${(rnd() * H).toFixed(1)}" r="${(r * scale).toFixed(2)}" fill="#fff" opacity="${(0.5 + rnd() * 0.5).toFixed(2)}"/>`;
  }
  const [ax, ay] = P(0), [cx, cy] = P(0.5), [ex, ey] = P(1);
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">
  <defs>
    <linearGradient id="p" x1="0" y1="0" x2="0.3" y2="1"><stop offset="0" stop-color="${c.base}"/><stop offset="0.55" stop-color="${c.mid}"/><stop offset="1" stop-color="${c.light}"/></linearGradient>
    <linearGradient id="s" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${c.base}"/><stop offset="0.7" stop-color="${c.mid}"/><stop offset="1" stop-color="${c.light}"/></linearGradient>
    <linearGradient id="c" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${c.mid}"/><stop offset="1" stop-color="${c.light}"/></linearGradient>
    <radialGradient id="neb" cx="0.6" cy="0.45" r="0.6"><stop offset="0" stop-color="${c.glow}" stop-opacity="0.35"/><stop offset="1" stop-color="${c.glow}" stop-opacity="0"/></radialGradient>
    <clipPath id="wing">${clip}</clipPath>
  </defs>
  ${paths}
  <g clip-path="url(#wing)"><rect width="${W}" height="${H}" fill="url(#neb)"/>${stars}</g>
  <path d="M${ax} ${ay} Q${C[0]} ${C[1]} ${ex} ${ey}" fill="none" stroke="${c.edge}" stroke-width="${3 * scale}" stroke-linecap="round"/>
  <path d="M${ax} ${ay - 1} Q${C[0]} ${C[1] - 1} ${ex} ${ey - 1}" fill="none" stroke="${c.light}" stroke-opacity="0.6" stroke-width="${1.2 * scale}" stroke-linecap="round"/>
  <circle cx="${cx}" cy="${cy}" r="0" />
</svg>`;
}

/** Zeichnet den Fluegel in ein erweitertes Sichtfenster und liefert dessen echte Ausdehnung (nichts wird abgeschnitten) */
function wingExtent(c) {
  const svg = wingSvg(c, 128, 128, 1.15).replace('width="128" height="128" viewBox="0 0 128 128"', 'width="840" height="640" viewBox="-60 -60 280 214"');
  const img = new Resvg(svg).render();
  const { width, height, pixels } = img;
  let x0 = width, y0 = height, x1 = -1, y1 = -1;
  for (let y = 0; y < height; y++) for (let x = 0; x < width; x++) if (pixels[(y * width + x) * 4 + 3] > 8) { x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y); }
  const k = 280 / width; // Einheiten pro Pixel
  return { x: -60 + x0 * k, y: -60 + y0 * k, w: (x1 - x0 + 1) * k, h: (y1 - y0 + 1) * k };
}
/** Fluegel-Textur: gleicher Ausschnitt fuer oben/unten, damit Groessenverhaeltnis stimmt; quadratisch mit Rand */
function wingPng(c, scale, ext, res = 512) {
  const pad = 3, size = Math.max(ext.w, ext.h) + pad * 2;
  const vx = ext.x + ext.w / 2 - size / 2, vy = ext.y + ext.h / 2 - size / 2;
  const svg = wingSvg(c, 128, 128, scale).replace('width="128" height="128" viewBox="0 0 128 128"', `width="128" height="128" viewBox="${vx.toFixed(2)} ${vy.toFixed(2)} ${size.toFixed(2)} ${size.toFixed(2)}"`);
  return pngSmooth(svg, res);
}
// Die Flügel sind jetzt 3D-Modelle (models/wings/<id>.bbmodel) mit eigener Textur (textures/cosmetics/wings/<id>.png)
// und Menü-Vorschau (textures/cosmetics/ui/wings_<id>.png) - erzeugt über tools/wings/*.cjs, siehe tools/wings/SPEC.md.
// Hier wird absichtlich nichts mehr für Flügel geschrieben, damit `npm run icons` die Modell-Assets nicht überschreibt.
// WINGS, wingSvg, wingExtent und wingPng bleiben nur als Palette/Referenz für die alten Galaxie-Flügel stehen.
console.log('  - Flügel: 3D-Modelle, werden hier nicht mehr erzeugt (tools/wings)');

// Kopien für den Shop im Launcher (public/cosmetics → wird mit der Oberfläche ausgeliefert):
// Capes, pro Flügel <id>.bbmodel + <id>.png (Atlas) + <id>_ui.png (Menü-Vorschau). Siehe tools/shop-assets.cjs
// (läuft auch allein: node tools/shop-assets.cjs).
require('./shop-assets.cjs').syncShopAssets(assets);
console.log('  ✓ Shop-Assets (public/cosmetics): Capes, Flügel-Modelle, Atlanten, Vorschauen');
