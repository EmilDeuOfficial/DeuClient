/**
 * Shop: Capes und Flügel ansehen und anlegen, eigene Capes im Pixel-Editor gestalten
 * oder als Bild hochladen. Alles kostenlos – "Anlegen" überträgt die Auswahl in alle
 * Modpacks mit DeuCore.
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import type { CustomCape, EquippedCosmetics } from '../../shared/types';
import { api } from '../api';
import { Checkbox, ColorInput } from '../components/Controls';
import { Icon } from '../components/Icons';
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

export function Shop() {
  const { toast } = useApp();
  const [tab, setTab] = useState<Tab>('catalog');
  const [equipped, setEquipped] = useState<EquippedCosmetics>({ cape: '', wings: '' });
  const [mine, setMine] = useState<CustomCape[]>([]);
  const [editorSeed, setEditorSeed] = useState<string[] | null>(null);

  const reload = useCallback(async () => {
    setEquipped(await api.capes.equipped());
    setMine(await api.capes.list());
  }, []);
  useEffect(() => void reload(), [reload]);

  const equip = async (sel: EquippedCosmetics) => {
    try {
      const n = await api.capes.equip(sel);
      setEquipped(sel);
      toast(n ? `Angelegt in ${n} Modpack${n === 1 ? '' : 's'} ✓` : 'Gespeichert – wird in Modpacks mit DeuCore übernommen', 'success');
    } catch (e) {
      toast((e as Error).message, 'error');
    }
  };

  return (
    <div className="mx-auto max-w-6xl space-y-5 pb-28">
      <div className="flex items-end justify-between gap-6">
        <div>
          <h2 className="font-display text-3xl font-black text-accent-gradient">Shop</h2>
          <p className="mt-1 max-w-2xl text-sm text-ink-200/60">
            Capes und Flügel für den DeuClient – alles kostenlos. Gestalte dein eigenes Cape oder lade ein Bild hoch.
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

      {tab === 'catalog' && <Catalog equipped={equipped} onEquip={equip} />}
      {tab === 'editor' && (
        <CapeEditor
          key={editorSeed ? editorSeed.join('') : 'new'}
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
          onDelete={async (id) => {
            await api.capes.remove(id);
            await reload();
          }}
          onUploaded={async (result) => {
            if (result.kind === 'saved') {
              await reload();
              toast('Cape hochgeladen ✓', 'success');
            } else {
              setEditorSeed(result.grid);
              setTab('editor');
              toast('Bild ins Cape-Format umgewandelt – du kannst es jetzt noch anpassen', 'info');
            }
          }}
        />
      )}
    </div>
  );
}

/* ====================== Vorschau-Bausteine ====================== */

/** Zeichnet die Vorderseite eines Capes (Bereich 1,1 – 10×16). HD-Capes werden weich skaliert, Pixel-Capes scharf. */
function CapeFront({ src, size = 64 }: { src: string; size?: number }) {
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
      ctx.imageSmoothingEnabled = k > 1;
      ctx.imageSmoothingQuality = 'high';
      ctx.drawImage(img, 1 * k, 1 * k, 10 * k, 16 * k, 0, 0, 10 * k, 16 * k);
    };
    img.src = src;
  }, [src]);
  return <canvas ref={ref} width={10} height={16} style={{ width: (size * 10) / 16, height: size, imageRendering: scale > 1 ? 'auto' : 'pixelated' }} className="rounded-md shadow-card" />;
}

function WingPair({ id, size = 56 }: { id: string; size?: number }) {
  const src = `./cosmetics/wings/${id}.png`;
  return (
    <div className="flex">
      <img src={src} alt="" style={{ width: size, height: size, transform: 'scaleX(-1)' }} />
      <img src={src} alt="" style={{ width: size, height: size, marginLeft: -6 }} />
    </div>
  );
}

function Tile({ active, children, title, action, onAction, disabled }: { active: boolean; children: React.ReactNode; title: string; action: string; onAction: () => void; disabled?: boolean }) {
  return (
    <div className={`card flex flex-col items-center gap-3 p-4 transition-colors hover:border-accent-400/30 ${active ? 'border-accent-400/60 shadow-accent' : ''}`}>
      <div className="flex h-28 items-center justify-center">{children}</div>
      <div className="text-center">
        <div className="font-bold">{title}</div>
        <div className="text-[11px] font-semibold text-emerald-300/80">Kostenlos</div>
      </div>
      <button className={active ? 'btn-ghost w-full !text-accent-200' : 'btn-accent w-full'} disabled={active || disabled} onClick={onAction}>
        {active && <Icon name="check" size={14} />}
        {active ? 'Angelegt' : action}
      </button>
    </div>
  );
}

/* ====================== Katalog ====================== */

function Catalog({ equipped, onEquip }: { equipped: EquippedCosmetics; onEquip: (s: EquippedCosmetics) => void }) {
  return (
    <div className="space-y-6">
      <section>
        <div className="mb-3 flex items-center justify-between">
          <h3 className="font-display text-lg font-bold">Capes</h3>
          {equipped.cape && <button className="btn-ghost !py-1.5 text-xs" onClick={() => onEquip({ ...equipped, cape: '' })}><Icon name="x" size={12} /> Cape ablegen</button>}
        </div>
        <div className="grid grid-cols-[repeat(auto-fill,minmax(150px,1fr))] gap-4">
          {CAPES.map(([id, name]) => (
            <Tile key={id} active={equipped.cape === id} title={name} action="Anlegen" onAction={() => onEquip({ ...equipped, cape: id })}>
              <CapeFront src={`./cosmetics/cape/${id}.png`} size={96} />
            </Tile>
          ))}
        </div>
      </section>
      <section>
        <div className="mb-3 flex items-center justify-between">
          <h3 className="font-display text-lg font-bold">Galaxie-Flügel</h3>
          {equipped.wings && <button className="btn-ghost !py-1.5 text-xs" onClick={() => onEquip({ ...equipped, wings: '' })}><Icon name="x" size={12} /> Flügel ablegen</button>}
        </div>
        <div className="grid grid-cols-[repeat(auto-fill,minmax(150px,1fr))] gap-4">
          {WINGS.map(([id, name]) => (
            <Tile key={id} active={equipped.wings === id} title={name} action="Anlegen" onAction={() => onEquip({ ...equipped, wings: id })}>
              <WingPair id={id} size={64} />
            </Tile>
          ))}
        </div>
      </section>
    </div>
  );
}

/* ====================== Pixel-Editor ====================== */

const W = 10, H = 16;
const PALETTE = [
  '#22c55e', '#86efac', '#15803d', '#16a34a', '#4ade80', '#14532d', '#0a110c', '#000000',
  '#ffffff', '#cfd6ea', '#ff5c72', '#c81e3a', '#ff9a1f', '#2be07a', '#0b6b3a', '#4fd1ff', '#1d4ed8', '#5c4033',
];

const TEMPLATES: Record<string, (x: number, y: number) => string> = {
  DeuClient: (x, y) => {
    if (x === 0 || x === 9 || y === 0 || y === 15) return '#22c55e';
    const d = Math.abs(x - 4.5) + Math.abs(y - 7.5) * 0.8;
    if (d <= 1.9) return '#dcfce7';
    if (d <= 3.5) return '#16a34a';
    return y < 8 ? '#15803d' : '#0f3d22';
  },
  Leer: () => '#0a110c',
  Streifen: (x) => (x % 2 === 0 ? '#16a34a' : '#22c55e'),
  Verlauf: (_x, y) => ['#86efac', '#22c55e', '#16a34a', '#15803d', '#16a34a', '#15803d', '#14532d', '#0f3d22'][Math.floor(y / 2)],
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

function CapeEditor({ seed, onSaved }: { seed: string[] | null; onSaved: (c: CustomCape) => void }) {
  const { toast } = useApp();
  const [res, setRes] = useState(1); // 1 = 64×32, 2 = 128×64
  const [grid, setGrid] = useState<string[]>(() => seed ?? fromTemplate('DeuClient', 1));
  const cols = W * res, rows = H * res;
  const changeRes = (next: number) => {
    if (next === res) return;
    setGrid(resample(grid, res, next));
    setHistory([]);
    setRes(next);
  };
  const [color, setColor] = useState('#22c55e');
  const [edge, setEdge] = useState('#2a0f52');
  const [tool, setTool] = useState<Tool>('pen');
  const [mirror, setMirror] = useState(false);
  const [name, setName] = useState('Mein Cape');
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
    const value = tool === 'eraser' ? edge : color;
    const next = start ? [...grid] : grid.slice();
    next[i] = value;
    if (mirror) next[y * cols + (cols - 1 - x)] = value;
    if (start) commit(next);
    else setGrid(next);
  };

  const preview = buildCapePng(grid, edge, res);

  const save = async () => {
    try {
      onSaved(await api.capes.save(name, preview));
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
        <div className="mt-3 text-xs text-ink-200/55">Vorderseite des Capes ({cols} × {rows} Pixel) – Klicken und ziehen zum Malen</div>
      </section>

      {/* Vorschau + Speichern */}
      <section className="card flex flex-col gap-4 p-5">
        <div className="label !mb-0">Vorschau</div>
        <div className="flex items-end justify-center gap-4 rounded-xl bg-ink-900/60 p-5">
          <CapeFront src={preview} size={128} />
          <div className="text-center">
            <img src={preview} alt="" style={{ width: 128, height: 64, imageRendering: 'pixelated' }} className="rounded border border-white/10" />
            <div className="mt-1 text-[10px] text-ink-200/50">Textur {64 * res}×{32 * res} (inkl. Elytra)</div>
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

function MyCapes({ capes, equipped, onEquip, onDelete, onUploaded }: {
  capes: CustomCape[]; equipped: EquippedCosmetics; onEquip: (id: string) => void; onDelete: (id: string) => void; onUploaded: (r: UploadResult) => void;
}) {
  const { toast } = useApp();
  const input = useRef<HTMLInputElement>(null);

  const upload = (file: File) => {
    const reader = new FileReader();
    reader.onload = () => {
      const img = new Image();
      img.onload = async () => {
        try {
          const exact = (img.width === 64 && img.height === 32) || (img.width === 128 && img.height === 64);
          if (exact) {
            // fertiges Cape-Format → direkt speichern (128×64 wird auf 64×32 verkleinert)
            const c = document.createElement('canvas');
            c.width = 64;
            c.height = 32;
            const ctx = c.getContext('2d')!;
            ctx.imageSmoothingEnabled = false;
            ctx.drawImage(img, 0, 0, 64, 32);
            await api.capes.save(file.name.replace(/\.[^.]+$/, ''), c.toDataURL('image/png'));
            onUploaded({ kind: 'saved' });
          } else {
            // beliebiges Bild → passend zuschneiden und auf 10×16 verkleinern
            const c = document.createElement('canvas');
            c.width = W;
            c.height = H;
            const ctx = c.getContext('2d')!;
            ctx.imageSmoothingEnabled = true;
            ctx.imageSmoothingQuality = 'high';
            const scale = Math.max(W / img.width, H / img.height);
            const dw = img.width * scale, dh = img.height * scale;
            ctx.drawImage(img, (W - dw) / 2, (H - dh) / 2, dw, dh);
            const data = ctx.getImageData(0, 0, W, H).data;
            const grid = Array.from({ length: W * H }, (_, i) => {
              const [r, g, b, a] = [data[i * 4], data[i * 4 + 1], data[i * 4 + 2], data[i * 4 + 3]];
              if (a < 40) return '#0a110c';
              return `#${[r, g, b].map((v) => v.toString(16).padStart(2, '0')).join('')}`;
            });
            onUploaded({ kind: 'convert', grid });
          }
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
            <div className="text-xs text-ink-200/60">PNG im Cape-Format (64×32 / 128×64) wird direkt übernommen – jedes andere Bild wird automatisch zum Cape umgewandelt. Auch per Drag & Drop.</div>
          </div>
        </div>
        <input ref={input} type="file" accept="image/png,image/jpeg,image/webp,image/gif" className="hidden" onChange={(e) => { const f = e.target.files?.[0]; if (f) upload(f); e.target.value = ''; }} />
        <button className="btn-accent shrink-0" onClick={() => input.current?.click()}><Icon name="upload" size={15} /> Bild wählen</button>
      </section>

      {!capes.length ? (
        <div className="card p-10 text-center text-sm text-ink-200/55">Noch keine eigenen Capes – gestalte eins im Cape-Editor oder lade ein Bild hoch.</div>
      ) : (
        <div className="grid grid-cols-[repeat(auto-fill,minmax(160px,1fr))] gap-4">
          {capes.map((c) => {
            const active = equipped.cape === `custom:${c.id}`;
            return (
              <div key={c.id} className={`card flex flex-col items-center gap-3 p-4 transition-colors hover:border-accent-400/30 ${active ? 'border-accent-400/60 shadow-accent' : ''}`}>
                <div className="flex h-28 items-center"><CapeFront src={c.dataUrl} size={96} /></div>
                <div className="w-full truncate text-center font-bold">{c.name}</div>
                <div className="flex w-full gap-2">
                  <button className={active ? 'btn-ghost flex-1 !text-accent-200' : 'btn-accent flex-1'} disabled={active} onClick={() => onEquip(c.id)}>
                    <Icon name={active ? 'check' : 'sparkle'} size={13} /> {active ? 'Angelegt' : 'Anlegen'}
                  </button>
                  <button className="btn-icon hover:!border-red-400/50 hover:!text-red-300" onClick={() => confirm(`"${c.name}" löschen?`) && onDelete(c.id)}><Icon name="trash" size={14} /></button>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
