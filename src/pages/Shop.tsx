/**
 * Shop: Die DeuClient-Capes und -Flügel lassen sich hier nur am eigenen Skin ausprobieren (Vorschau) -
 * gekauft werden können sie erst, wenn sie live gehen. Eigene Capes (Editor / Upload) können angelegt
 * werden: "Anlegen" überträgt die Auswahl in alle Modpacks mit DeuCore.
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import type { CustomCape, EquippedCosmetics } from '../../shared/types';
import { api } from '../api';
import { tx } from '../autotranslate';
import { confirmDialog } from '../components/Confirm';
import { Checkbox, ColorInput } from '../components/Controls';
import { Icon } from '../components/Icons';
import { Player3D } from '../components/Player3D';
import { useApp } from '../store';

const CAPES = [
  ['gold', 'Gold'], ['nacht', 'Sternennacht'], ['flamme', 'Flamme'],
  ['eis', 'Eiskristall'], ['smaragd', 'Smaragd'],
] as const;
const WINGS = [
  ['engel', 'Engel'], ['daemon', 'Dämon'], ['eis', 'Eis'],
  ['phoenix', 'Phönix'], ['schatten', 'Schatten'], ['smaragd', 'Smaragd'],
] as const;

type Tab = 'catalog' | 'editor' | 'mine';

/** Startwerte fuer den Cape-Editor (neues Bild oder ein gespeichertes Cape zum Bearbeiten) */
interface EditorSeed { grid: string[]; res: number; name: string; edge?: string; id?: string }

export function Shop() {
  const { toast } = useApp();
  const [tab, setTab] = useState<Tab>('catalog');
  const [equipped, setEquipped] = useState<EquippedCosmetics>({ cape: '', wings: '' });
  const [mine, setMine] = useState<CustomCape[]>([]);
  const [editorSeed, setEditorSeed] = useState<EditorSeed | null>(null);

  const reload = useCallback(async () => {
    setEquipped(await api.capes.equipped());
    setMine(await api.capes.list());
  }, []);
  useEffect(() => void reload(), [reload]);
  // im Spiel gewaehlte Cosmetics uebernehmen, sobald man zum Launcher zurueckkehrt
  useEffect(() => {
    const onFocus = () => void reload();
    window.addEventListener('focus', onFocus);
    return () => window.removeEventListener('focus', onFocus);
  }, [reload]);

  /** Gespeichertes Cape in den Editor laden (Vorderseite -> Raster) */
  const editCape = async (c: CustomCape) => {
    const img = await new Promise<HTMLImageElement>((resolve, reject) => {
      const i = new Image();
      i.onload = () => resolve(i);
      i.onerror = () => reject(new Error('Cape konnte nicht gelesen werden'));
      i.src = c.dataUrl;
    }).catch((e: Error) => { toast(e.message, 'error'); return null; });
    if (!img) return;
    const k = Math.max(1, Math.round(img.width / 64));
    const res = Math.min(k, 2); // der Editor arbeitet mit 10×16 oder 20×32 Pixeln
    const cols = W * res, rows = H * res;
    // Vorderseite des Capes in Editor-Aufloesung (HD-Capes werden dabei sauber verkleinert)
    const cv = document.createElement('canvas');
    cv.width = cols;
    cv.height = rows;
    const ctx = cv.getContext('2d', { willReadFrequently: true })!;
    ctx.imageSmoothingEnabled = k > res;
    ctx.imageSmoothingQuality = 'high';
    ctx.drawImage(img, k, k, 10 * k, 16 * k, 0, 0, cols, rows);
    const data = ctx.getImageData(0, 0, cols, rows).data;
    const grid = Array.from({ length: cols * rows }, (_, i) => {
      const [r, g, b, a] = [data[i * 4], data[i * 4 + 1], data[i * 4 + 2], data[i * 4 + 3]];
      return a < 40 ? '#0a110c' : `#${[r, g, b].map((v) => v.toString(16).padStart(2, '0')).join('')}`;
    });
    const px = document.createElement('canvas');
    px.width = px.height = 1;
    const pctx = px.getContext('2d', { willReadFrequently: true })!;
    pctx.drawImage(img, 0, 0, 1, 1, 0, 0, 1, 1);
    const [er, eg, eb] = pctx.getImageData(0, 0, 1, 1).data;
    const edge = `#${[er, eg, eb].map((v) => v.toString(16).padStart(2, '0')).join('')}`;
    if (k > 2) toast('HD-Cape: für die Bearbeitung wird es auf 128×64 verkleinert', 'info');
    setEditorSeed({ grid, res, name: c.name, edge, id: c.id });
    setTab('editor');
  };

  const equip = async (sel: EquippedCosmetics) => {
    try {
      const n = await api.capes.equip(sel);
      setEquipped(sel);
      toast(n ? `Angelegt in ${n} Modpack${n === 1 ? '' : 's'} ✓` : 'Gespeichert - wird in Modpacks mit DeuCore übernommen', 'success');
    } catch (e) {
      toast((e as Error).message, 'error');
    }
  };

  return (
    <div className="mx-auto max-w-6xl space-y-5">
      <div className="flex items-end justify-between gap-6">
        <div>
          <h2 className="font-display text-3xl font-black text-accent-gradient">Shop</h2>
          <p className="mt-1 max-w-2xl text-sm text-ink-200/60">
            Probiere die DeuClient-Capes und -Flügel an deinem eigenen Skin aus. Kaufen kannst du sie, sobald sie live gehen. Gestalte außerdem dein eigenes Cape oder lade ein Bild hoch.
          </p>
        </div>
      </div>

      <div className="flex w-fit items-center gap-1 rounded-2xl border border-white/10 bg-ink-950/60 p-1">
        {([['catalog', 'Capes & Flügel', 'sparkle'], ['editor', 'Cape-Editor', 'edit'], ['mine', `Meine Capes (${mine.length})`, 'image']] as const).map(([id, label, icon]) => (
          <button
            key={id}
            onClick={() => setTab(id)}
            className={`flex items-center gap-2 rounded-xl px-4 py-2 text-sm font-bold transition ${tab === id ? 'bg-accent-500 text-ink-950' : 'text-ink-100/70 hover:text-white'}`}
          >
            <Icon name={icon} size={18} className="shrink-0" />
            {label}
          </button>
        ))}
      </div>

      {tab === 'catalog' && <Catalog />}
      {tab === 'editor' && (
        <CapeEditor
          key={editorSeed ? (editorSeed.id ?? editorSeed.grid.join('')) : 'new'}
          seed={editorSeed}
          onSaved={async (cape) => {
            await reload();
            setTab('mine');
            toast(`"${cape.name}" gespeichert`, 'success');
            setEditorSeed(null);
          }}
        />
      )}
      {tab === 'mine' && (
        <MyCapes
          capes={mine}
          equipped={equipped}
          onEquip={(id) => equip({ ...equipped, cape: `custom:${id}` })}
          onEdit={(c) => void editCape(c)}
          onDelete={async (id) => {
            await api.capes.remove(id);
            await reload();
          }}
          onUploaded={async (result) => {
            if (result.kind === 'saved') {
              await reload();
              toast('Cape in bester Qualität importiert ✓', 'success');
            } else {
              setEditorSeed({ grid: result.grid, res: 1, name: 'Mein Cape' });
              setTab('editor');
              toast('Bild ins Cape-Format umgewandelt - du kannst es jetzt noch anpassen', 'info');
            }
          }}
        />
      )}
    </div>
  );
}

/* ====================== Vorschau-Bausteine ====================== */

/** Zeichnet die Vorderseite eines Capes (Bereich 1,1 - 10×16). HD-Capes werden weich skaliert, Pixel-Capes scharf. */
function CapeFront({ src, size = 64, crisp = false }: { src: string; size?: number; crisp?: boolean }) {
  const ref = useRef<HTMLCanvasElement>(null);
  const [scale, setScale] = useState(1);
  useEffect(() => {
    const img = new Image();
    img.onload = () => {
      const k = Math.max(1, Math.round(img.width / 64)); // 64×32 = 1, 256×128 = 4
      setScale(k);
      const c = ref.current;
      const ctx = c?.getContext('2d');
      if (!c || !ctx) return;
      c.width = 10 * k;
      c.height = 16 * k;
      // Pixel-Capes (64×32 / 128×64) scharf mit harten Kanten, nur echte HD-Capes weich skalieren
      ctx.imageSmoothingEnabled = k > 2 && !crisp;
      ctx.imageSmoothingQuality = 'high';
      ctx.drawImage(img, 1 * k, 1 * k, 10 * k, 16 * k, 0, 0, 10 * k, 16 * k);
    };
    img.src = src;
  }, [src, crisp]);
  return <canvas ref={ref} width={10} height={16} style={{ width: (size * 10) / 16, height: size, imageRendering: scale > 2 && !crisp ? 'auto' : 'pixelated' }} className="rounded-md shadow-card" />;
}

/** Menue-Vorschau eines Fluegels (256x256, Schulter bei ca. 12 % / 55 %, Fluegel nach rechts); die linke Haelfte ist gespiegelt */
function WingPair({ id, size = 56 }: { id: string; size?: number }) {
  const src = `./cosmetics/wings/${id}_ui.png`;
  return (
    <div className="flex">
      <img src={src} alt="" style={{ width: size, height: size, transform: 'scaleX(-1)' }} />
      <img src={src} alt="" style={{ width: size, height: size, marginLeft: -0.24 * size }} />
    </div>
  );
}

function Tile({ active, children, title, action, onAction, disabled, note = 'Kostenlos', activeLabel = 'Angelegt' }: { active: boolean; children: React.ReactNode; title: string; action: string; onAction: () => void; disabled?: boolean; note?: string; activeLabel?: string }) {
  return (
    <div className={`card flex flex-col items-center gap-3 p-4 transition-colors hover:border-accent-400/30 ${active ? 'border-accent-400/60 shadow-accent' : ''}`}>
      <div className="flex h-28 items-center justify-center">{children}</div>
      <div className="text-center">
        <div className="font-bold">{title}</div>
        <div className="text-[11px] font-semibold text-emerald-300/80">{note}</div>
      </div>
      <button className={active ? 'btn-ghost w-full !text-accent-200' : 'btn-accent w-full'} disabled={active || disabled} onClick={onAction}>
        {active && <Icon name="check" size={14} />}
        {active ? activeLabel : action}
      </button>
    </div>
  );
}

/* ====================== Katalog ====================== */

function Catalog() {
  const { accounts } = useApp();
  const skinUrl = accounts.accounts.find((a) => a.uuid === accounts.activeUuid)?.skinUrl;
  const [trying, setTrying] = useState<EquippedCosmetics>({ cape: '', wings: '' });
  return (
    <div className="grid grid-cols-[300px_1fr] items-start gap-6">
      <aside className="card sticky top-2 space-y-3 p-4">
        <div className="flex items-center justify-between">
          <h3 className="font-display text-lg font-bold">Anprobe</h3>
          {(trying.cape || trying.wings) && (
            <button className="btn-ghost !py-1.5 text-xs" onClick={() => setTrying({ cape: '', wings: '' })}><Icon name="x" size={12} /> Zurücksetzen</button>
          )}
        </div>
        <Player3D skinUrl={skinUrl} cape={trying.cape} wings={trying.wings} />
      </aside>
      <div className="space-y-6">
        <section>
          <h3 className="mb-3 font-display text-lg font-bold">Capes</h3>
          <div className="grid grid-cols-[repeat(auto-fill,minmax(150px,1fr))] gap-4">
            {CAPES.map(([id, name]) => (
              <Tile key={id} active={trying.cape === id} title={name} action="Testen" activeLabel="Aktiv" note="Bald kaufbar" onAction={() => setTrying({ ...trying, cape: id })}>
                <CapeFront src={`./cosmetics/cape/${id}.png`} size={96} />
              </Tile>
            ))}
          </div>
        </section>
        <section>
          <h3 className="mb-3 font-display text-lg font-bold">Galaxie-Flügel</h3>
          <div className="grid grid-cols-[repeat(auto-fill,minmax(150px,1fr))] gap-4">
            {WINGS.map(([id, name]) => (
              <Tile key={id} active={trying.wings === id} title={name} action="Testen" activeLabel="Aktiv" note="Bald kaufbar" onAction={() => setTrying({ ...trying, wings: id })}>
                <WingPair id={id} size={64} />
              </Tile>
            ))}
          </div>
        </section>
      </div>
    </div>
  );
}

/* ====================== Pixel-Editor ====================== */

const W = 10, H = 16;
const PALETTE = [
  '#2eff7a', '#9fffc0', '#17a84a', '#1fd35f', '#6bff9e', '#0f7a35', '#0a110c', '#000000',
  '#ffffff', '#cfd6ea', '#ff5c72', '#c81e3a', '#ff9a1f', '#3dff8a', '#0b7a37', '#4fd1ff', '#1d4ed8', '#5c4033',
];

const TEMPLATES: Record<string, (x: number, y: number) => string> = {
  DeuClient: (x, y) => {
    if (x === 0 || x === 9 || y === 0 || y === 15) return '#2eff7a';
    const d = Math.abs(x - 4.5) + Math.abs(y - 7.5) * 0.8;
    if (d <= 1.9) return '#e6fff0';
    if (d <= 3.5) return '#1fd35f';
    return y < 8 ? '#17a84a' : '#0f4025';
  },
  Leer: () => '#0a110c',
  Streifen: (x) => (x % 2 === 0 ? '#1fd35f' : '#2eff7a'),
  Verlauf: (_x, y) => ['#9fffc0', '#2eff7a', '#1fd35f', '#17a84a', '#1fd35f', '#17a84a', '#0f7a35', '#0f4025'][Math.floor(y / 2)],
  Schach: (x, y) => ((x + y) % 2 === 0 ? '#ffffff' : '#000000'),
};
/** Vorlage in der gewuenschten Aufloesung (k = 1: 10x16, k = 2: 20x32) */
const fromTemplate = (name: string, k = 1) =>
  Array.from({ length: W * H * k * k }, (_, i) => TEMPLATES[name](Math.floor((i % (W * k)) / k), Math.floor(Math.floor(i / (W * k)) / k)));

/** Raster auf eine andere Aufloesung bringen (hoch: Pixel verdoppeln, runter: jeden zweiten Pixel uebernehmen) */
function resample(grid: string[], from: number, to: number): string[] {
  const fw = W * from, tw = W * to, th = H * to;
  return Array.from({ length: tw * th }, (_, i) => {
    const x = i % tw, y = Math.floor(i / tw);
    return grid[Math.floor((y * from) / to) * fw + Math.floor((x * from) / to)];
  });
}

type Tool = 'pen' | 'fill' | 'eraser' | 'picker';

/** Baut aus der Vorderseite (10k×16k) ein vollstaendiges Cape (64k×32k): Rueckseite, Raender, Elytra */
function buildCapePng(grid: string[], edge: string, k: number): string {
  const cols = W * k, rows = H * k;
  const c = document.createElement('canvas');
  c.width = 64 * k;
  c.height = 32 * k;
  const ctx = c.getContext('2d')!;
  ctx.fillStyle = edge;
  ctx.fillRect(0, 0, 22 * k, 17 * k); // Raender (oben, unten, Seiten)
  for (let y = 0; y < rows; y++) {
    for (let x = 0; x < cols; x++) {
      ctx.fillStyle = grid[y * cols + x];
      ctx.fillRect(k + x, k + y, 1, 1); // Aussenseite
      ctx.fillRect(12 * k + (cols - 1 - x), k + y, 1, 1); // Innenseite (gespiegelt)
    }
  }
  // Elytra: Rand-Farbe als Grund, Motiv vergroessert hineinzeichnen
  ctx.fillStyle = edge;
  ctx.fillRect(22 * k, 0, 24 * k, 22 * k);
  for (let y = 0; y < rows; y++) {
    for (let x = 0; x < cols; x++) {
      ctx.fillStyle = grid[y * cols + x];
      ctx.fillRect(24 * k + x * 2, 2 * k + y, 2, 1);
    }
  }
  return c.toDataURL('image/png');
}

function CapeEditor({ seed, onSaved }: { seed: EditorSeed | null; onSaved: (c: CustomCape) => void }) {
  const { toast } = useApp();
  const [res, setRes] = useState(seed?.res ?? 1); // 1 = 64×32, 2 = 128×64
  const [grid, setGrid] = useState<string[]>(() => seed?.grid ?? fromTemplate('DeuClient', 1));
  const cols = W * res, rows = H * res;
  const changeRes = (next: number) => {
    if (next === res) return;
    setGrid(resample(grid, res, next));
    setHistory([]);
    setRes(next);
  };
  const [color, setColor] = useState('#2eff7a');
  const [edge, setEdge] = useState(seed?.edge ?? '#2a0f52');
  const [tool, setTool] = useState<Tool>('pen');
  const [mirror, setMirror] = useState(false);
  const [name, setName] = useState(seed?.name ?? 'Mein Cape');
  const [history, setHistory] = useState<string[][]>([]);
  const painting = useRef(false);

  const commit = (next: string[]) => {
    setHistory((h) => [...h.slice(-30), grid]);
    setGrid(next);
  };

  const apply = (i: number, start: boolean) => {
    const x = i % cols, y = Math.floor(i / cols);
    if (tool === 'picker') {
      setColor(grid[i]);
      setTool('pen');
      return;
    }
    if (tool === 'fill') {
      if (!start) return;
      const target = grid[i];
      if (target === color) return;
      const next = [...grid];
      const stack = [i];
      while (stack.length) {
        const j = stack.pop()!;
        if (next[j] !== target) continue;
        next[j] = color;
        const jx = j % cols, jy = Math.floor(j / cols);
        if (jx > 0) stack.push(j - 1);
        if (jx < cols - 1) stack.push(j + 1);
        if (jy > 0) stack.push(j - cols);
        if (jy < rows - 1) stack.push(j + cols);
      }
      commit(next);
      return;
    }
    const value = tool === 'eraser' ? '#ffffff' : color; // Radierer = weisser Hintergrund
    const next = start ? [...grid] : grid.slice();
    next[i] = value;
    if (mirror) next[y * cols + (cols - 1 - x)] = value;
    if (start) commit(next);
    else setGrid(next);
  };

  const preview = buildCapePng(grid, edge, res);

  const save = async () => {
    try {
      onSaved(await api.capes.save(name, preview, seed?.id));
    } catch (e) {
      toast((e as Error).message, 'error');
    }
  };

  return (
    <div className="grid grid-cols-[auto_1fr_260px] gap-5">
      {/* Werkzeuge */}
      <section className="card flex w-[200px] flex-col gap-4 p-4">
        <div>
          <div className="label">Werkzeug</div>
          <div className="grid grid-cols-2 gap-2">
            {([['pen', 'Stift', 'edit'], ['fill', 'Füllen', 'bucket'], ['eraser', 'Radierer', 'eraser'], ['picker', 'Pipette', 'pipette']] as const).map(([id, label, icon]) => (
              <button key={id} onClick={() => setTool(id)} className={`btn !gap-2 !px-2 !py-2.5 text-xs ${tool === id ? 'bg-accent-400 text-ink-950' : 'btn-ghost'}`}>
                <Icon name={icon as never} size={17} className="shrink-0" /> {label}
              </button>
            ))}
          </div>
        </div>
        <div>
          <div className="label">Farbe</div>
          <div className="grid grid-cols-6 gap-1.5">
            {PALETTE.map((c) => (
              <button key={c} onClick={() => setColor(c)} className={`h-6 w-6 rounded-md border-2 ${color === c ? 'border-white' : 'border-transparent'}`} style={{ background: c }} />
            ))}
          </div>
          <label className="mt-2 flex items-center gap-2 text-xs text-ink-200/70">
            <ColorInput compact value={color} onChange={setColor} /> Eigene Farbe
          </label>
        </div>
        <div>
          <div className="label">Rand / Rückseite</div>
          <ColorInput value={edge} onChange={setEdge} />
        </div>
        <label className="flex items-center justify-between text-xs font-semibold">
          Spiegeln <Checkbox checked={mirror} onChange={setMirror} />
        </label>
        <div>
          <div className="label">Auflösung</div>
          <div className="mb-3 flex gap-1.5">
            {([[1, '64 × 32'], [2, '128 × 64']] as const).map(([k, label]) => (
              <button key={k} className={`chip ${res === k ? 'chip-active' : ''}`} onClick={() => changeRes(k)}>{label}</button>
            ))}
          </div>
          <div className="label">Vorlagen</div>
          <div className="flex flex-wrap gap-1.5">
            {Object.keys(TEMPLATES).map((t) => (
              <button key={t} className="chip" onClick={() => commit(fromTemplate(t, res))}>{t}</button>
            ))}
          </div>
        </div>
        <button className="btn-ghost !py-1.5 text-xs" disabled={!history.length} onClick={() => { setGrid(history[history.length - 1]); setHistory((h) => h.slice(0, -1)); }}>
          <Icon name="refresh" size={12} /> Rückgängig
        </button>
      </section>

      {/* Leinwand */}
      <section className="card flex flex-col items-center justify-center p-6" onMouseUp={() => (painting.current = false)} onMouseLeave={() => (painting.current = false)}>
        <div
          className="grid select-none overflow-hidden rounded-lg border-2 border-accent-400/40 shadow-card"
          style={{ gridTemplateColumns: `repeat(${cols}, ${26 / res}px)`, background: edge }}
          onContextMenu={(e) => e.preventDefault()}
        >
          {grid.map((c, i) => (
            <div
              key={i}
              onMouseDown={() => { painting.current = true; apply(i, true); }}
              onMouseEnter={() => painting.current && tool !== 'fill' && tool !== 'picker' && apply(i, false)}
              className="cursor-crosshair border-[0.5px] border-black/10 hover:outline hover:outline-1 hover:outline-white/60"
              style={{ background: c, width: 26 / res, height: 26 / res }}
            />
          ))}
        </div>
        <div className="mt-3 text-xs text-ink-200/55">{`Vorderseite des Capes (${cols} × ${rows} Pixel) - Klicken und ziehen zum Malen`}</div>
      </section>

      {/* Vorschau + Speichern */}
      <section className="card flex flex-col gap-4 p-5">
        <div className="label !mb-0">Vorschau</div>
        <div className="flex items-end justify-center gap-4 rounded-xl bg-ink-900/60 p-5">
          <CapeFront src={preview} size={160} crisp />
          <div className="text-center">
            <img src={preview} alt="" style={{ width: 128, height: 64, imageRendering: 'pixelated' }} className="rounded border border-white/10" />
            <div className="mt-1 text-[10px] text-ink-200/50">{`Textur ${64 * res}×${32 * res} (inkl. Elytra)`}</div>
          </div>
        </div>
        <div>
          <label className="label">Name</label>
          <input className="input" maxLength={32} value={name} onChange={(e) => setName(e.target.value)} />
        </div>
        <button className="btn-accent" onClick={save}><Icon name="check" size={15} /> Cape speichern</button>
        <p className="text-[11px] leading-relaxed text-ink-200/50">
          Eigene Capes werden in allen Modpacks mit DeuCore angelegt.
        </p>
      </section>
    </div>
  );
}

/* ====================== Meine Capes + Upload ====================== */

type UploadResult = { kind: 'saved' } | { kind: 'convert'; grid: string[] };

/** Beliebiges Bild -> vollstaendiges HD-Cape (1024×512, 16-fache Aufloesung): Aussenseite, Innenseite, Raender, Elytra */
function buildHdCape(img: HTMLImageElement): string {
  const k = 16;
  const c = document.createElement('canvas');
  c.width = 64 * k;
  c.height = 32 * k;
  const ctx = c.getContext('2d')!;
  // Randfarbe = Durchschnittsfarbe des Bildes
  const avg = document.createElement('canvas');
  avg.width = avg.height = 1;
  const actx = avg.getContext('2d')!;
  actx.drawImage(img, 0, 0, 1, 1);
  const [r, g, b] = actx.getImageData(0, 0, 1, 1).data;
  const edge = `rgb(${r},${g},${b})`;
  ctx.fillStyle = edge;
  ctx.fillRect(0, 0, 22 * k, 17 * k);
  ctx.fillRect(22 * k, 0, 24 * k, 22 * k);
  // Ausschnitt im Seitenverhaeltnis 10:16 (zentriert, ohne Verzerrung)
  const target = 10 / 16;
  let sw = img.width, sh = img.height, sx = 0, sy = 0;
  if (sw / sh > target) { sw = sh * target; sx = (img.width - sw) / 2; } else { sh = sw / target; sy = (img.height - sh) / 2; }
  ctx.imageSmoothingEnabled = true;
  ctx.imageSmoothingQuality = 'high';
  ctx.drawImage(img, sx, sy, sw, sh, k, k, 10 * k, 16 * k); // Aussenseite
  ctx.save();
  ctx.translate(22 * k, k);
  ctx.scale(-1, 1);
  ctx.drawImage(img, sx, sy, sw, sh, 0, 0, 10 * k, 16 * k); // Innenseite (gespiegelt)
  ctx.restore();
  ctx.drawImage(img, sx, sy, sw, sh, 24 * k, 2 * k, 20 * k, 16 * k); // Elytra
  return c.toDataURL('image/png');
}

function MyCapes({ capes, equipped, onEquip, onDelete, onEdit, onUploaded }: {
  capes: CustomCape[]; equipped: EquippedCosmetics; onEquip: (id: string) => void; onDelete: (id: string) => void; onEdit: (c: CustomCape) => void; onUploaded: (r: UploadResult) => void;
}) {
  const { toast } = useApp();
  const input = useRef<HTMLInputElement>(null);

  const upload = (file: File) => {
    const reader = new FileReader();
    reader.onload = () => {
      const img = new Image();
      img.onload = async () => {
        try {
          const name = file.name.replace(/\.[^.]+$/, '');
          const exact = img.width >= 64 && img.width <= 2048 && img.width % 64 === 0 && img.height === img.width / 2;
          if (exact) {
            // fertiges Cape-Format (auch HD) → unveraendert in voller Aufloesung speichern
            const c = document.createElement('canvas');
            c.width = img.width;
            c.height = img.height;
            c.getContext('2d')!.drawImage(img, 0, 0);
            await api.capes.save(name, c.toDataURL('image/png'));
          } else {
            // beliebiges Bild → in bester Cape-Qualitaet (HD 1024×512) umwandeln
            await api.capes.save(name, buildHdCape(img));
          }
          onUploaded({ kind: 'saved' });
        } catch (e) {
          toast((e as Error).message, 'error');
        }
      };
      img.onerror = () => toast('Bild konnte nicht gelesen werden', 'error');
      img.src = String(reader.result);
    };
    reader.readAsDataURL(file);
  };

  return (
    <div className="space-y-4">
      <section
        className="card flex items-center justify-between gap-4 border-dashed p-5"
        onDragOver={(e) => e.preventDefault()}
        onDrop={(e) => {
          e.preventDefault();
          const f = e.dataTransfer.files[0];
          if (f) upload(f);
        }}
      >
        <div className="flex items-center gap-3">
          <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-accent-400/15 text-accent-300"><Icon name="upload" size={20} /></span>
          <div>
            <div className="font-bold">Cape hochladen</div>
            <div className="text-xs text-ink-200/60">PNG im Cape-Format (64×32 oder HD) wird unverändert übernommen - jedes andere Bild wird in bester Cape-Qualität (HD 1024×512) umgewandelt. Auch per Drag & Drop.</div>
          </div>
        </div>
        <input ref={input} type="file" accept="image/png,image/jpeg,image/webp,image/gif" className="hidden" onChange={(e) => { const f = e.target.files?.[0]; if (f) upload(f); e.target.value = ''; }} />
        <button className="btn-accent shrink-0" onClick={() => input.current?.click()}><Icon name="upload" size={15} /> Bild wählen</button>
      </section>

      {!capes.length ? (
        <div className="card p-10 text-center text-sm text-ink-200/55">Noch keine eigenen Capes - gestalte eins im Cape-Editor oder lade ein Bild hoch.</div>
      ) : (
        <div className="grid grid-cols-[repeat(auto-fill,minmax(190px,1fr))] gap-4">
          {capes.map((c) => {
            const active = equipped.cape === `custom:${c.id}`;
            return (
              <div key={c.id} className={`card flex flex-col items-center gap-3 p-4 transition-colors hover:border-accent-400/30 ${active ? 'border-accent-400/60 shadow-accent' : ''}`}>
                <div className="flex h-28 items-center"><CapeFront src={c.dataUrl} size={96} /></div>
                <div className="w-full truncate text-center font-bold">{c.name}</div>
                <div className="flex w-full flex-col gap-2">
                  <button className={active ? 'btn-ghost w-full !text-accent-200' : 'btn-accent w-full'} disabled={active} onClick={() => onEquip(c.id)}>
                    {active && <Icon name="check" size={13} />} {active ? 'Angelegt' : 'Anlegen'}
                  </button>
                  <div className="grid grid-cols-2 gap-2">
                    <button className="btn-ghost !px-2" onClick={() => onEdit(c)}><Icon name="edit" size={14} /> Bearbeiten</button>
                    <button className="btn-ghost !px-2 hover:!border-red-400/50 hover:!text-red-300" onClick={async () => { if (await confirmDialog(tx(`"${c.name}" löschen?`), { danger: true, confirmLabel: tx('Löschen') })) onDelete(c.id); }}><Icon name="trash" size={14} /> Löschen</button>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
