/**
 * Rendert das DeuClient-Logo (SVG) in alle benötigten Formate:
 *  - build/icon.png + build/icon.ico   → Fenster-, Taskleisten- und EXE-Icon
 *  - src/assets/emblem.png             → Logo in der Launcher-Oberfläche
 */
const fs = require('node:fs');
const path = require('node:path');
const { Resvg } = require('@resvg/resvg-js');
const pngToIco = require('png-to-ico').default ?? require('png-to-ico');

const root = path.resolve(__dirname, '..');

function render(svgFile, width) {
  const svg = fs.readFileSync(path.join(root, 'img', svgFile), 'utf8');
  const resvg = new Resvg(svg, {
    fitTo: { mode: 'width', value: width },
    font: { loadSystemFonts: true, defaultFontFamily: 'Segoe UI' },
  });
  return resvg.render().asPng();
}

/** Schreibt ein 24-Bit-BMP (Format, das der NSIS-Installer für Seiten-/Kopfbilder verlangt) */
function renderBmp(svgFile, width) {
  const svg = fs.readFileSync(path.join(root, 'img', svgFile), 'utf8');
  const img = new Resvg(svg, { fitTo: { mode: 'width', value: width }, font: { loadSystemFonts: true, defaultFontFamily: 'Segoe UI' } }).render();
  const { width: w, height: h, pixels } = img;
  const rowSize = Math.ceil((w * 3) / 4) * 4;
  const buf = Buffer.alloc(54 + rowSize * h);
  buf.write('BM', 0);
  buf.writeUInt32LE(buf.length, 2);
  buf.writeUInt32LE(54, 10);
  buf.writeUInt32LE(40, 14);
  buf.writeInt32LE(w, 18);
  buf.writeInt32LE(h, 22);
  buf.writeUInt16LE(1, 26);
  buf.writeUInt16LE(24, 28);
  buf.writeUInt32LE(rowSize * h, 34);
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      const i = (y * w + x) * 4;
      const a = pixels[i + 3] / 255;
      const o = 54 + (h - 1 - y) * rowSize + x * 3; // BMP ist von unten nach oben gespeichert
      buf[o] = Math.round(pixels[i + 2] * a); // B
      buf[o + 1] = Math.round(pixels[i + 1] * a); // G
      buf[o + 2] = Math.round(pixels[i] * a); // R
    }
  }
  return buf;
}

function write(file, buf) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, buf);
  console.log('  ✓', path.relative(root, file));
}

(async () => {
  console.log('Erzeuge DeuClient-Icons ...');
  const icon256 = render('emblem.svg', 256);
  write(path.join(root, 'build', 'icon.png'), render('emblem.svg', 512));
  write(path.join(root, 'public', 'icon.png'), render('emblem.svg', 256));
  write(path.join(root, 'src', 'assets', 'emblem.png'), icon256);
  // ICO mit mehreren Größen für Explorer/Taskleiste
  const ico = await pngToIco([16, 24, 32, 48, 64, 128, 256].map((s) => render('emblem.svg', s)));
  write(path.join(root, 'build', 'icon.ico'), ico);
  // Bilder für den Setup-Installer
  write(path.join(root, 'build', 'installerSidebar.bmp'), renderBmp('installer-sidebar.svg', 164));
  write(path.join(root, 'build', 'installerHeader.bmp'), renderBmp('installer-header.svg', 150));


  console.log('Fertig.');
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
