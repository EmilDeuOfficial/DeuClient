/**
 * Renders the in-game logo assets of the DivineCore mods from img/:
 *   - gui/emblem.png      emblem (img/emblem.svg), 256x256
 *   - gui/wordmark.png    DeuClient logo (img/logo.png), 1024 px wide, used for loading screen and main menu
 *   - icon.png, icon/icon_32.png, icon/icon_64.png   window and mod icons
 *   - font/logo.png       16x16 glyph shown next to the player name (26.3 only)
 */
const fs = require('node:fs');
const path = require('node:path');
const { Resvg } = require('@resvg/resvg-js');

const root = path.resolve(__dirname, '..');
const emblemSvg = fs.readFileSync(path.join(root, 'img', 'emblem.svg'), 'utf8');
const logoPng = fs.readFileSync(path.join(root, 'img', 'logo.png'));

const renderSvg = (svg, width) => new Resvg(svg, { fitTo: { mode: 'width', value: width } }).render().asPng();

/** Scales a PNG to the given width through resvg (smooth, keeps transparency) */
function scalePng(buf, srcW, srcH, width) {
  const height = Math.round((width * srcH) / srcW);
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">` +
    `<image width="${width}" height="${height}" xlink:href="data:image/png;base64,${buf.toString('base64')}"/></svg>`;
  return { png: renderSvg(svg, width), height };
}

const logoW = logoPng.readUInt32BE(16);
const logoH = logoPng.readUInt32BE(20);
const wordmark = scalePng(logoPng, logoW, logoH, 1024);

for (const v of fs.readdirSync(path.join(root, 'mod')).filter((d) => d.startsWith('mc'))) {
  const assets = path.join(root, 'mod', v, 'src', 'main', 'resources', 'assets', 'divinecore');
  const write = (rel, buf) => {
    const f = path.join(assets, rel);
    fs.mkdirSync(path.dirname(f), { recursive: true });
    fs.writeFileSync(f, buf);
  };
  write('textures/gui/emblem.png', renderSvg(emblemSvg, 256));
  write('textures/gui/wordmark.png', wordmark.png);
  write('icon.png', renderSvg(emblemSvg, 128));
  write('icon/icon_32.png', renderSvg(emblemSvg, 32));
  write('icon/icon_64.png', renderSvg(emblemSvg, 64));
  if (v !== 'mc1.21.4') write('textures/font/logo.png', renderSvg(emblemSvg, 16));
  console.log(`  ok ${v}: emblem, wordmark ${1024}x${wordmark.height}, icons`);
}
console.log(`WORDMARK_H for the mods: ${wordmark.height}`);
