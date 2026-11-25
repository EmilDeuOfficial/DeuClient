/**
 * Eigene Capes aus dem Launcher-Shop (Cape-Editor / Upload).
 *
 *   capes/<id>.png           gespeicherte Capes (Minecraft-Cape-Format 64×32)
 *
 * "Anlegen" schreibt die Auswahl in jedes Modpack mit DeuCore:
 *   <modpack>/config/deucore.json            → cape / wings
 *   <modpack>/config/deucore/custom_cape.png → bei eigenem Cape
 */
import fs from 'node:fs';
import path from 'node:path';
import type { CustomCape, EquippedCosmetics, Instance } from '../shared/types';
import { managedModsFor } from './game/deucore';
import { instanceDir, listInstances } from './game/instances';
import { paths } from './util/paths';
import { readJson, writeJson } from './util/store';

const capesDir = () => path.join(paths.data, 'capes');
const stateFile = () => path.join(paths.data, 'cosmetics.json');
/** Obergrenze, damit das Cape klein genug für die Übertragung an andere Spieler bleibt */
const MAX_BYTES = 8 * 1024 * 1024;

/** Liest Breite/Höhe aus dem PNG-Header und prüft das Format */
function pngSize(buf: Buffer): { w: number; h: number } {
  const sig = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  if (buf.length < 24 || !buf.subarray(0, 8).equals(sig)) throw new Error('Keine gültige PNG-Datei.');
  return { w: buf.readUInt32BE(16), h: buf.readUInt32BE(20) };
}

export function listCapes(): CustomCape[] {
  if (!fs.existsSync(capesDir())) return [];
  return fs
    .readdirSync(capesDir())
    .filter((f) => f.endsWith('.png'))
    .map((f) => {
      const file = path.join(capesDir(), f);
      const meta = readJson<{ name?: string }>(file.replace(/\.png$/, '.json'), {});
      return {
        id: f.replace(/\.png$/, ''),
        name: meta.name ?? f.replace(/\.png$/, ''),
        dataUrl: `data:image/png;base64,${fs.readFileSync(file).toString('base64')}`,
        created: fs.statSync(file).mtimeMs,
      };
    })
    .sort((a, b) => b.created - a.created);
}

/** Speichert ein Cape; mit id wird ein vorhandenes ueberschrieben (Bearbeiten) */
export function saveCape(name: string, pngBase64: string, existingId?: string): CustomCape {
  const buf = Buffer.from(pngBase64.replace(/^data:image\/png;base64,/, ''), 'base64');
  const { w, h } = pngSize(buf);
  // Cape-Format 64×32 oder ein Vielfaches davon (HD-Cape bis 2048×1024)
  if (!(w >= 64 && w <= 2048 && w % 64 === 0 && h === w / 2)) throw new Error(`Cape muss 64×32 Pixel oder ein Vielfaches davon groß sein (ist ${w}×${h}).`);
  if (buf.length > MAX_BYTES) throw new Error('Cape-Datei ist zu groß (max. 8 MB).');
  fs.mkdirSync(capesDir(), { recursive: true });
  const id = existingId && fs.existsSync(path.join(capesDir(), `${path.basename(existingId)}.png`)) ? path.basename(existingId) : `cape-${Date.now().toString(36)}`;
  fs.writeFileSync(path.join(capesDir(), `${id}.png`), buf);
  writeJson(path.join(capesDir(), `${id}.json`), { name: name.trim().slice(0, 32) || 'Mein Cape' });
  // war es das angelegte Cape: Kopie in den Modpacks aktualisieren
  if (existingId && getEquipped().cape === `custom:${id}`) equip(getEquipped());
  return listCapes().find((c) => c.id === id)!;
}

export function deleteCape(id: string) {
  const safe = path.basename(id);
  fs.rmSync(path.join(capesDir(), `${safe}.png`), { force: true });
  fs.rmSync(path.join(capesDir(), `${safe}.json`), { force: true });
  const st = getEquipped();
  if (st.cape === `custom:${safe}`) equip({ ...st, cape: '' });
}

export function getEquipped(): EquippedCosmetics {
  const st = readJson<EquippedCosmetics>(stateFile(), { cape: '', wings: '' });
  // entfernte Designs zuruecksetzen
  // (auch die IDs aus der Zeit vor der Umbenennung: 'divine' / 'divinesmp')
  if (['divine', 'divinesmp', 'deu', 'deusmp'].includes(st.cape)) st.cape = '';
  if (['divine', 'deu'].includes(st.wings)) st.wings = '';
  return st;
}

/** Auswahl in alle Modpacks mit DeuCore schreiben. Gibt die Anzahl der Modpacks zurück. */
export function equip(sel: EquippedCosmetics): number {
  writeJson(stateFile(), sel);
  let count = 0;
  for (const inst of listInstances()) {
    if (!managedModsFor(inst).some((m) => m.core)) continue;
    writeToInstance(inst, sel);
    count++;
  }
  return count;
}

function writeToInstance(inst: Instance, sel: EquippedCosmetics): void {
  const configDir = path.join(instanceDir(inst.id), 'config');
  const cfgFile = path.join(configDir, 'deucore.json');
  const cfg = readJson<Record<string, unknown>>(cfgFile, {});
  const customPng = path.join(configDir, 'deucore', 'custom_cape.png');
  if (sel.cape.startsWith('custom:')) {
    const src = path.join(capesDir(), `${path.basename(sel.cape.slice(7))}.png`);
    if (!fs.existsSync(src)) throw new Error('Cape nicht gefunden.');
    fs.mkdirSync(path.dirname(customPng), { recursive: true });
    fs.copyFileSync(src, customPng);
    cfg.cape = 'custom';
  } else {
    cfg.cape = sel.cape;
  }
  cfg.wings = sel.wings;
  writeJson(cfgFile, cfg);
}

/** Vor dem Start: die gemeinsame Auswahl (Launcher-Shop bzw. zuletzt im Spiel gewaehlt) in dieses Modpack schreiben */
export function applyToInstance(inst: Instance): void {
  if (!managedModsFor(inst).some((m) => m.core)) return;
  try {
    if (fs.existsSync(stateFile())) writeToInstance(inst, getEquipped());
  } catch {
    /* ausgewaehltes eigenes Cape fehlt - Modpack-Config bleibt wie sie ist */
  }
}
