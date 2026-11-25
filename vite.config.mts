import { defineConfig, type Plugin } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from 'tailwindcss';
import autoprefixer from 'autoprefixer';
import { readFileSync } from 'node:fs';

const pkg = JSON.parse(readFileSync(new URL('./package.json', import.meta.url), 'utf8')) as { version: string };

/** Fügt im Produktions-Build eine strikte Content-Security-Policy ein (im Dev-Modus braucht Vite Inline-Scripts). */
function cspPlugin(): Plugin {
  return {
    name: 'deuclient-csp',
    apply: 'build',
    transformIndexHtml(html) {
      const csp =
        "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; " +
        "img-src 'self' data: blob: https:; font-src 'self' data:; connect-src 'self'";
      return html.replace('<head>', `<head>\n    <meta http-equiv="Content-Security-Policy" content="${csp}" />`);
    },
  };
}

export default defineConfig({
  define: { __APP_VERSION__: JSON.stringify(pkg.version) },
  // Relative Pfade, damit die gebaute Oberfläche per file:// aus der .exe geladen werden kann
  base: './',
  plugins: [react(), cspPlugin()],
  css: { postcss: { plugins: [tailwindcss(), autoprefixer()] } },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    chunkSizeWarningLimit: 2000,
  },
  server: { port: 5173, strictPort: true },
});
