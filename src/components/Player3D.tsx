/**
 * Drehbare 3D-Vorschau eines Minecraft-Skins mit Cape und Fluegeln (keine Bibliothek).
 * Spieler und Cape sind CSS-3D, die Fluegel sind echte 3D-Modelle (Blockbench, siehe wingModel.ts) und werden von
 * wingRenderer.ts per WebGL als transparentes Canvas mit derselben Kamera darueber gezeichnet.
 * Ziehen mit der Maus dreht den Spieler. Nur Anzeige - nichts wird angelegt.
 */
import { useEffect, useMemo, useRef, useState } from 'react';
import { WingRenderer, fitWingZoom, loadWingAssets, type WingAssets, type WingLayout } from './wingRenderer';

const S = 5; // Pixel pro Skin-Pixel
const TEX = 8; // Aufloesung der Flaechen-Bilder pro Skin-Pixel

/** Kamera der CSS-Szene: perspective des Containers, Lage der Szenenmitte (top) und Neigung nach vorn */
const SCENE = { perspective: 800, originY: 0.52, tiltX: -8 };
/** Cape in Skin-Pixeln: Groesse w x h x t, Mittelpunkt z (negativ = hinten), Neigung um die Oberkante (pivotY) */
const CAPE = { w: 10, h: 16, t: 1, z: -2.9, tilt: -7, pivotY: -8 };

type Face = 'top' | 'bottom' | 'right' | 'front' | 'left' | 'back';
interface Part { id: string; w: number; h: number; d: number; u: number; v: number; x: number; y: number; hat?: boolean; mirror?: boolean }

/** Mittelpunkte relativ zur Mitte des Spielers (x nach rechts, y nach unten, Skin-Pixel) */
const PARTS: Part[] = [
  { id: 'head', w: 8, h: 8, d: 8, u: 0, v: 0, x: 0, y: -12, hat: true },
  { id: 'body', w: 8, h: 12, d: 4, u: 16, v: 16, x: 0, y: -2 },
  { id: 'rarm', w: 4, h: 12, d: 4, u: 40, v: 16, x: -6, y: -2 },
  { id: 'larm', w: 4, h: 12, d: 4, u: 32, v: 48, x: 6, y: -2, mirror: true },
  { id: 'rleg', w: 4, h: 12, d: 4, u: 0, v: 16, x: -2, y: 10 },
  { id: 'lleg', w: 4, h: 12, d: 4, u: 16, v: 48, x: 2, y: 10, mirror: true },
];

/** Lage jeder Flaeche im Skin (Standard-Layout einer Box) */
function rect(p: Part, f: Face): [number, number, number, number] {
  const { u, v, w, h, d } = p;
  switch (f) {
    case 'top': return [u + d, v, w, d];
    case 'bottom': return [u + d + w, v, w, d];
    case 'right': return [u, v + d, d, h];
    case 'front': return [u + d, v + d, w, h];
    case 'left': return [u + d + w, v + d, d, h];
    default: return [u + d + w + d, v + d, w, h];
  }
}

const FACES: Face[] = ['top', 'bottom', 'right', 'front', 'left', 'back'];

/** Welt der Fluegel im WebGL-Canvas: dieselben Masse wie die CSS-Szene (Koerperteile verdecken die Fluegel nur im Tiefenpuffer) */
const WING_LAYOUT: WingLayout = { unit: S, ...SCENE, parts: PARTS, cape: CAPE };

function loadImage(src?: string): Promise<HTMLImageElement | null> {
  return new Promise((resolve) => {
    if (!src) return resolve(null);
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = () => resolve(null);
    img.src = src;
  });
}

/** Skin als 64x64-Canvas; ohne Skin ein einfacher Platzhalter */
function makeSkin(img: HTMLImageElement | null): HTMLCanvasElement {
  const c = document.createElement('canvas');
  c.width = 64;
  c.height = 64;
  const ctx = c.getContext('2d')!;
  ctx.imageSmoothingEnabled = false;
  if (img) {
    ctx.drawImage(img, 0, 0);
    // alte 64x32-Skins: linke Gliedmassen = gespiegelte rechte
    if (img.height === 32) {
      ctx.drawImage(img, 40, 16, 16, 16, 32, 48, 16, 16);
      ctx.drawImage(img, 0, 16, 16, 16, 16, 48, 16, 16);
    }
  } else {
    const fill = (col: string, x: number, y: number, w: number, h: number) => { ctx.fillStyle = col; ctx.fillRect(x, y, w, h); };
    fill('#c58d6a', 0, 0, 32, 16);
    fill('#2a7f9e', 16, 16, 24, 16);
    fill('#c58d6a', 40, 16, 16, 16);
    fill('#243b5e', 0, 16, 16, 16);
    fill('#c58d6a', 32, 48, 16, 16);
    fill('#243b5e', 16, 48, 16, 16);
  }
  return c;
}

function faceUrl(skin: HTMLCanvasElement, p: Part, f: Face): string {
  const [sx, sy, w, h] = rect(p, f);
  const c = document.createElement('canvas');
  c.width = w * TEX;
  c.height = h * TEX;
  const ctx = c.getContext('2d')!;
  ctx.imageSmoothingEnabled = false;
  ctx.drawImage(skin, sx, sy, w, h, 0, 0, w * TEX, h * TEX);
  if (p.hat) {
    const [hx, hy] = [sx + 32, sy]; // Hut-Ebene liegt 32 Pixel rechts davon
    ctx.drawImage(skin, hx, hy, w, h, 0, 0, w * TEX, h * TEX);
  }
  return c.toDataURL('image/png');
}

function faceStyle(p: Part, f: Face): React.CSSProperties {
  const [, , w, h] = rect(p, f);
  const fw = w * S, fh = h * S;
  const t: Record<Face, string> = {
    front: `translateZ(${(p.d / 2) * S}px)`,
    back: `rotateY(180deg) translateZ(${(p.d / 2) * S}px)`,
    right: `rotateY(-90deg) translateZ(${(p.w / 2) * S}px)`,
    left: `rotateY(90deg) translateZ(${(p.w / 2) * S}px)`,
    top: `rotateX(90deg) translateZ(${(p.h / 2) * S}px)`,
    bottom: `rotateX(-90deg) translateZ(${(p.h / 2) * S}px)`,
  };
  return { position: 'absolute', width: fw, height: fh, left: -fw / 2, top: -fh / 2, transform: t[f], backgroundSize: '100% 100%', imageRendering: 'pixelated' };
}


/** Cape als duenner Quader (1 Skin-Pixel dick): Aussenseite mit Motiv, Innenseite dunkel, Kanten in Randfarbe */
function capeFaces(url: string) {
  const T = CAPE.t * S, W = CAPE.w * S, H = CAPE.h * S;
  const face = (key: string, w: number, h: number, transform: string, style: React.CSSProperties) => (
    <div key={key} style={{ position: 'absolute', width: w, height: h, left: -w / 2, top: -h / 2, transform, ...style }} />
  );
  const img = { backgroundImage: `url(${url})`, backgroundSize: '100% 100%' };
  const edge = { background: '#1a2a20' };
  return [
    face('out', W, H, `rotateY(180deg) translateZ(${T / 2}px)`, img),
    face('in', W, H, `translateZ(${T / 2}px)`, { ...img, filter: 'brightness(0.45)' }),
    face('l', T, H, `rotateY(-90deg) translateZ(${W / 2}px)`, edge),
    face('r', T, H, `rotateY(90deg) translateZ(${W / 2}px)`, edge),
    face('b', W, T, `rotateX(-90deg) translateZ(${H / 2}px)`, edge),
  ];
}

/** Zeichnet die 3D-Fluegel als transparentes Canvas ueber der CSS-Szene (siehe wingRenderer.ts). Ohne WebGL bleibt es leer. */
function WingsCanvas({ assets, angle, zoom, cape }: { assets: WingAssets; angle: number; zoom: number; cape: boolean }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const rendererRef = useRef<WingRenderer | null>(null);
  // aktueller Zustand fuer die Animationsschleife, ohne sie bei jeder Drehung neu zu starten
  const live = useRef({ angle, zoom, cape });
  useEffect(() => { live.current = { angle, zoom, cape }; });

  useEffect(() => {
    if (!canvasRef.current) return;
    const r = new WingRenderer(canvasRef.current, WING_LAYOUT);
    rendererRef.current = r;
    return () => { r.dispose(); rendererRef.current = null; };
  }, []);

  useEffect(() => {
    const r = rendererRef.current;
    if (!r || !r.ok) return;
    r.setModel(assets); // gibt Puffer und Textur des vorigen Modells frei
    let raf = 0;
    const loop = (ms: number) => {
      r.render({ ...live.current, ticks: ms / 50 }); // 20 Ticks pro Sekunde wie im Spiel
      raf = requestAnimationFrame(loop);
    };
    raf = requestAnimationFrame(loop);
    return () => { cancelAnimationFrame(raf); r.setModel(null); };
  }, [assets]);

  return <canvas ref={canvasRef} style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', pointerEvents: 'none', zIndex: 1 }} />;
}

export function Player3D({ skinUrl, cape, wings }: { skinUrl?: string; cape: string; wings: string }) {
  const [faces, setFaces] = useState<Record<string, string>>({});
  const [capeUrl, setCapeUrl] = useState('');
  const [angle, setAngle] = useState(200);
  const [wingAssets, setWingAssets] = useState<WingAssets | null>(null);
  const [box, setBox] = useState({ w: 268, h: 230 });
  const boxRef = useRef<HTMLDivElement>(null);
  const drag = useRef<{ x: number; a: number } | null>(null);

  useEffect(() => {
    let off = false;
    if (!wings) { setWingAssets(null); return; }
    // das vorige Modell bleibt sichtbar, bis das neue geladen ist
    loadWingAssets(wings).then((a) => { if (!off) setWingAssets(a); }).catch(() => { if (!off) setWingAssets(null); });
    return () => { off = true; };
  }, [wings]);

  useEffect(() => {
    const el = boxRef.current;
    if (!el || typeof ResizeObserver === 'undefined') return;
    const ro = new ResizeObserver(() => setBox({ w: el.clientWidth, h: el.clientHeight }));
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  // Fluegel sind breiter und hoeher als der Spieler: die ganze Szene wird verkleinert, bis alles in den Container passt
  const zoom = useMemo(() => (wingAssets ? fitWingZoom(wingAssets.data, WING_LAYOUT, box.w, box.h) : 1), [wingAssets, box]);

  useEffect(() => {
    let off = false;
    void loadImage(skinUrl).then((img) => {
      if (off) return;
      const skin = makeSkin(img);
      const next: Record<string, string> = {};
      for (const p of PARTS) for (const f of FACES) next[`${p.id}.${f}`] = faceUrl(skin, p, f);
      setFaces(next);
    });
    return () => { off = true; };
  }, [skinUrl]);

  useEffect(() => {
    let off = false;
    if (!cape) { setCapeUrl(''); return; }
    void loadImage(`./cosmetics/cape/${cape}.png`).then((img) => {
      if (off || !img) return;
      const k = Math.max(1, Math.round(img.width / 64));
      const c = document.createElement('canvas');
      c.width = 10 * k;
      c.height = 16 * k;
      const ctx = c.getContext('2d')!;
      ctx.imageSmoothingEnabled = k > 1;
      ctx.drawImage(img, k, k, 10 * k, 16 * k, 0, 0, 10 * k, 16 * k);
      setCapeUrl(c.toDataURL('image/png'));
    });
    return () => { off = true; };
  }, [cape]);

  return (
    <div className="space-y-2">
      <div
        ref={boxRef}
        className="relative h-[230px] cursor-grab select-none overflow-hidden rounded-xl bg-ink-950/60 active:cursor-grabbing"
        style={{ perspective: SCENE.perspective }}
        onPointerDown={(e) => { e.currentTarget.setPointerCapture?.(e.pointerId); drag.current = { x: e.clientX, a: angle }; }}
        onPointerMove={(e) => { if (drag.current) setAngle(drag.current.a + (e.clientX - drag.current.x) * 0.8); }}
        onPointerUp={() => { drag.current = null; }}
        onPointerCancel={() => { drag.current = null; }}
      >
        <div style={{ position: 'absolute', left: '50%', top: `${SCENE.originY * 100}%`, width: 0, height: 0, transformStyle: 'preserve-3d', transform: `scale3d(${zoom}, ${zoom}, ${zoom}) rotateX(${SCENE.tiltX}deg) rotateY(${angle}deg)` }}>
          {PARTS.map((p) => (
            <div key={p.id} style={{ position: 'absolute', left: p.x * S, top: p.y * S, width: 0, height: 0, transformStyle: 'preserve-3d' }}>
              {FACES.map((f) => (
                <div key={f} style={{ ...faceStyle(p, f), backgroundImage: faces[`${p.id}.${f}`] ? `url(${faces[`${p.id}.${f}`]})` : undefined }} />
              ))}
            </div>
          ))}
          {capeUrl && (
            <div style={{ position: 'absolute', left: 0, top: 0, width: 0, height: 0, transformStyle: 'preserve-3d', transformOrigin: `0px ${CAPE.pivotY * S}px`, transform: `translate3d(0, 0, ${CAPE.z * S}px) rotateX(${CAPE.tilt}deg)` }}>
              {capeFaces(capeUrl)}
            </div>
          )}
        </div>
        {wingAssets && <WingsCanvas assets={wingAssets} angle={angle} zoom={zoom} cape={!!capeUrl} />}
      </div>
      <div className="flex justify-center gap-2">
        <button className="btn-ghost !px-3 !py-1 text-xs" onClick={() => setAngle(0)}>Vorne</button>
        <button className="btn-ghost !px-3 !py-1 text-xs" onClick={() => setAngle(180)}>Hinten</button>
      </div>
    </div>
  );
}
