/**
 * WebGL-Renderer fuer die 3D-Fluegel der Anprobe (Player3D.tsx). Keine Bibliothek, nur rohes WebGL.
 *
 * Der Spieler selbst ist reines CSS-3D. Die Fluegel liegen als transparentes Canvas ueber der Szene und benutzen
 * dieselbe Kamera wie das CSS (perspective, Szenenmitte, Neigung, Drehung, Zoom). Damit die Fluegel hinter dem Koerper
 * verschwinden, werden Koerperteile und Cape als unsichtbare Quader nur in den Tiefenpuffer geschrieben.
 *
 * Koordinaten wie in CSS: x nach rechts, y nach unten, z zum Betrachter, Einheit = CSS-Pixel, Ursprung = Szenenmitte.
 * Blockbench -> Szene (wie WingsLayer im Spiel): x nach aussen (gespiegelt links), y hoch -> -y, z nach hinten -> -z.
 */
import {
  DEG, WING_FLOATS, idleAnim, mIdentity, mRotX, mRotY, mScale, mTranslate, parseWingModel, poseWing,
  type Mat, type WingModelData,
} from './wingModel';

/** Koerperteil als Quader: Mittelpunkt (x, y) relativ zur Szenenmitte, Groesse w x h x d, alles in Skin-Pixeln */
export interface WingBox { x: number; y: number; w: number; h: number; d: number }

export interface WingLayout {
  /** CSS-Pixel pro Skin-Pixel */
  unit: number;
  /** CSS-perspective in Pixeln */
  perspective: number;
  /** Lage der Szenenmitte von oben (0.5 = Mitte des Containers, wie `top: 52%`) */
  originY: number;
  /** rotateX der Szene in Grad */
  tiltX: number;
  /** Koerperteile; das Teil mit id "body" bestimmt den Fluegelansatz */
  parts: (WingBox & { id: string })[];
  /** Cape: Groesse w x h x t, Mittelpunkt z, Neigung um die Oberkante (pivotY) */
  cape: { w: number; h: number; t: number; z: number; tilt: number; pivotY: number };
}

export interface WingAssets {
  id: string;
  data: WingModelData;
  image: HTMLImageElement;
}

export interface WingFrame {
  /** Drehung des Spielers in Grad (rotateY der Szene) */
  angle: number;
  /** Skalierung der ganzen Szene (CSS scale3d) */
  zoom: number;
  /** Cape angelegt: Fluegel sitzen dann etwas weiter hinten, sonst stecken sie im Cape */
  cape: boolean;
  /** Zeit in Spiel-Ticks (20 pro Sekunde) */
  ticks: number;
}

/** Ansatz der Fluegel am Ruecken wie im Spiel (WingsLayer): 3 Pixel unter der Schulter, 0.4 Pixel hinter dem Ruecken, 1 Pixel von der Wirbelsaeule */
export function wingMount(layout: WingLayout, cape: boolean): { rootY: number; spineX: number; backZ: number } {
  const body = layout.parts.find((p) => p.id === 'body') ?? { x: 0, y: -2, w: 8, h: 12, d: 4 };
  let backZ = body.d / 2 + 0.4;
  if (cape) backZ = Math.max(backZ, Math.abs(layout.cape.z) + layout.cape.t / 2 + 0.2);
  return { rootY: body.y - body.h / 2 + 3, spineX: 1, backZ };
}

/** Zoom der ganzen Szene, damit Spieler und Fluegel in den Container (CSS-Pixel) passen; nie groesser als 1 */
export function fitWingZoom(data: WingModelData, layout: WingLayout, width: number, height: number): number {
  const { unit: u } = layout;
  const m = wingMount(layout, false);
  const { min, max } = data.bounds;
  const needX = (m.spineX + Math.max(Math.abs(min[0]), Math.abs(max[0]))) * u;
  // Blockbench y (hoch) -> Szene y = (rootY - y) * unit (nach unten); der Spieler selbst ist 16 Pixel ueber/unter der Mitte
  const up = Math.max(16 * u, (max[1] - m.rootY) * u);
  const down = Math.max(16 * u, (m.rootY - min[1]) * u);
  const k = Math.min(
    (width / 2) / needX,
    (height * layout.originY) / up,
    (height * (1 - layout.originY)) / down,
  );
  return Math.max(0.3, Math.min(1, k * 0.9));
}

/** Laedt Modell und Atlas eines Fluegels (relativ zur Seite, aus public/cosmetics/wings) */
export async function loadWingAssets(id: string): Promise<WingAssets> {
  const base = `./cosmetics/wings/${encodeURIComponent(id)}`;
  const [data, image] = await Promise.all([
    fetch(`${base}.bbmodel`).then((r) => r.json()).then(parseWingModel),
    new Promise<HTMLImageElement>((resolve, reject) => {
      const img = new Image();
      img.onload = () => resolve(img);
      img.onerror = () => reject(new Error(`Fluegel-Textur ${id} nicht geladen`));
      img.src = `${base}.png`;
    }),
  ]);
  return { id, data, image };
}

/* ------------------------------------------------------------------------------------------ Shader */

const VERT = `
attribute vec3 aPos;
attribute vec2 aUv;
attribute vec3 aNrm;
uniform mat4 uProj;
uniform mat4 uMV;
varying vec2 vUv;
varying float vShade;
void main() {
  gl_Position = uProj * (uMV * vec4(aPos, 1.0));
  vUv = aUv;
  vec3 n = normalize((uMV * vec4(aNrm, 0.0)).xyz);
  // beidseitig beleuchtet (wie die Fluegel im Spiel), Licht fest an der Kamera
  vShade = 0.78 + 0.22 * abs(dot(n, normalize(vec3(-0.35, -0.55, 0.76))));
}`;

/** uPass: 0 = deckende Texel (schreiben Tiefe), 1 = durchscheinende Texel (nur testen), 2 = Quader ohne Textur */
const FRAG = `
precision mediump float;
uniform sampler2D uTex;
uniform float uPass;
varying vec2 vUv;
varying float vShade;
void main() {
  vec4 c = texture2D(uTex, vUv);
  if (uPass < 1.5) {
    if (c.a < 0.02) discard;
    if (uPass < 0.5) { if (c.a < 0.92) discard; } else { if (c.a >= 0.92) discard; }
  }
  gl_FragColor = vec4(c.rgb * vShade * c.a, c.a);
}`;

const NEAR = 100;
const FAR = 3000;

/** Einheitswuerfel (-0.5 .. 0.5) als Dreiecke: x y z u v nx ny nz */
function unitCube(): Float32Array {
  const faces: [number[], number[], number[]][] = [
    [[0, 0, 1], [1, 0, 0], [0, 1, 0]],
    [[0, 0, -1], [-1, 0, 0], [0, 1, 0]],
    [[1, 0, 0], [0, 0, -1], [0, 1, 0]],
    [[-1, 0, 0], [0, 0, 1], [0, 1, 0]],
    [[0, 1, 0], [1, 0, 0], [0, 0, -1]],
    [[0, -1, 0], [1, 0, 0], [0, 0, 1]],
  ];
  const out: number[] = [];
  for (const [n, a, b] of faces) {
    const corner = (s: number, t: number) => [0, 1, 2].map((i) => n[i] * 0.5 + a[i] * s * 0.5 + b[i] * t * 0.5);
    const c = [corner(-1, -1), corner(1, -1), corner(1, 1), corner(-1, 1)];
    for (const i of [0, 1, 2, 0, 2, 3]) out.push(...c[i], 0, 0, ...n);
  }
  return new Float32Array(out);
}

export class WingRenderer {
  private gl: WebGLRenderingContext | null;
  private prog: WebGLProgram | null = null;
  private loc: { aPos: number; aUv: number; aNrm: number; uProj: WebGLUniformLocation | null; uMV: WebGLUniformLocation | null; uTex: WebGLUniformLocation | null; uPass: WebGLUniformLocation | null } | null = null;
  private cubeBuf: WebGLBuffer | null = null;
  private modelBuf: WebGLBuffer | null = null;
  private tex: WebGLTexture | null = null;
  private model: WingModelData | null = null;
  /** je Seite (rechts, links) die Matrizen aller Knoten und fertige Sichten darauf fuer uniformMatrix4fv */
  private pose: { all: Float32Array; views: Float32Array[] }[] = [];

  private readonly proj = new Float32Array(16);
  private readonly view = new Float32Array(16);
  private readonly mv = new Float32Array(16);
  private readonly base = new Float32Array(16);

  constructor(private readonly canvas: HTMLCanvasElement, private readonly layout: WingLayout) {
    let gl: WebGLRenderingContext | null = null;
    try {
      gl = canvas.getContext('webgl', { alpha: true, premultipliedAlpha: true, antialias: true, depth: true }) as WebGLRenderingContext | null;
    } catch {
      gl = null;
    }
    this.gl = gl;
    if (gl && !this.init(gl)) this.gl = null; // ohne WebGL: keine Fluegel, der Rest der Anprobe laeuft weiter
  }

  /** false, wenn WebGL nicht verfuegbar ist */
  get ok(): boolean {
    return this.gl !== null;
  }

  private init(gl: WebGLRenderingContext): boolean {
    const compile = (type: number, src: string): WebGLShader | null => {
      const s = gl.createShader(type);
      if (!s) return null;
      gl.shaderSource(s, src);
      gl.compileShader(s);
      return gl.getShaderParameter(s, gl.COMPILE_STATUS) ? s : null;
    };
    const vs = compile(gl.VERTEX_SHADER, VERT), fs = compile(gl.FRAGMENT_SHADER, FRAG);
    const prog = gl.createProgram();
    if (!vs || !fs || !prog) return false;
    gl.attachShader(prog, vs);
    gl.attachShader(prog, fs);
    gl.linkProgram(prog);
    gl.deleteShader(vs);
    gl.deleteShader(fs);
    if (!gl.getProgramParameter(prog, gl.LINK_STATUS)) {
      gl.deleteProgram(prog);
      return false;
    }
    this.prog = prog;
    this.loc = {
      aPos: gl.getAttribLocation(prog, 'aPos'), aUv: gl.getAttribLocation(prog, 'aUv'), aNrm: gl.getAttribLocation(prog, 'aNrm'),
      uProj: gl.getUniformLocation(prog, 'uProj'), uMV: gl.getUniformLocation(prog, 'uMV'),
      uTex: gl.getUniformLocation(prog, 'uTex'), uPass: gl.getUniformLocation(prog, 'uPass'),
    };
    this.cubeBuf = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, this.cubeBuf);
    gl.bufferData(gl.ARRAY_BUFFER, unitCube(), gl.STATIC_DRAW);
    return true;
  }

  /** Setzt Modell und Atlas (null = nichts zeichnen). Die Ressourcen des vorigen Modells werden freigegeben. */
  setModel(assets: WingAssets | null): void {
    const gl = this.gl;
    if (!gl) return;
    this.releaseModel();
    if (!assets) return;
    const { data, image } = assets;

    this.modelBuf = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, this.modelBuf);
    gl.bufferData(gl.ARRAY_BUFFER, data.vertices, gl.STATIC_DRAW);

    this.tex = gl.createTexture();
    gl.bindTexture(gl.TEXTURE_2D, this.tex);
    gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, false); // v = 0 ist die oberste Bildzeile, wie im Modell
    gl.pixelStorei(gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL, false);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, image);
    // scharfe Texel wie im Spiel (und kein Ausbluten zwischen den UV-Inseln des Atlas)
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.NEAREST);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.NEAREST);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);

    this.model = data;
    this.pose = [0, 1].map(() => {
      const all = new Float32Array(data.nodes.length * 16);
      return { all, views: data.nodes.map((_, i) => all.subarray(i * 16, i * 16 + 16)) };
    });
  }

  private releaseModel(): void {
    const gl = this.gl;
    if (gl) {
      if (this.modelBuf) gl.deleteBuffer(this.modelBuf);
      if (this.tex) gl.deleteTexture(this.tex);
    }
    this.modelBuf = null;
    this.tex = null;
    this.model = null;
    this.pose = [];
  }

  /** Leert das Canvas (kein Modell) */
  clear(): void {
    const gl = this.gl;
    if (!gl) return;
    gl.clearColor(0, 0, 0, 0);
    gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
  }

  render(f: WingFrame): void {
    const gl = this.gl, loc = this.loc, model = this.model;
    if (!gl || !loc || !model || !this.prog || !this.modelBuf || !this.cubeBuf) return;
    const { canvas, layout: L } = this;

    // Pixelgroesse des Canvas an die Anzeigegroesse anpassen (CSS-Pixel x devicePixelRatio)
    const cw = canvas.clientWidth, ch = canvas.clientHeight;
    if (cw < 2 || ch < 2) return;
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const pw = Math.round(cw * dpr), ph = Math.round(ch * dpr);
    if (canvas.width !== pw || canvas.height !== ph) {
      canvas.width = pw;
      canvas.height = ph;
    }
    gl.viewport(0, 0, pw, ph);

    // Kamera wie CSS: perspective(d) um die Containermitte, Szenenmitte bei originY, transform: scale3d rotateX rotateY
    const d = L.perspective, oy = (L.originY - 0.5) * ch;
    const A = (FAR + NEAR) / (FAR - NEAR), B = (-2 * FAR * NEAR) / (FAR - NEAR);
    const p = this.proj;
    p.fill(0);
    p[0] = d / (cw / 2);
    p[5] = -d / (ch / 2);
    p[13] = (-oy * d) / (ch / 2);
    p[10] = -A;
    p[14] = A * d + B;
    p[11] = -1;
    p[15] = d;
    const v = this.view;
    mIdentity(v);
    mScale(v, 0, f.zoom, f.zoom, f.zoom);
    mRotX(v, 0, L.tiltX * DEG);
    mRotY(v, 0, f.angle * DEG);

    gl.clearColor(0, 0, 0, 0);
    gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
    gl.useProgram(this.prog);
    gl.uniformMatrix4fv(loc.uProj, false, p);
    gl.uniform1i(loc.uTex, 0);
    gl.activeTexture(gl.TEXTURE0);
    gl.bindTexture(gl.TEXTURE_2D, this.tex);
    gl.enable(gl.DEPTH_TEST);
    gl.depthFunc(gl.LEQUAL);
    gl.disable(gl.CULL_FACE); // beidseitig: der gespiegelte Fluegel hat umgekehrte Windung
    gl.enable(gl.BLEND);
    gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA); // Ausgabe ist vormultipliziert

    this.drawOccluders(gl, loc, f);
    this.drawWings(gl, loc, model, f);
  }

  /** Koerper und Cape nur in den Tiefenpuffer: verdeckt Fluegel, die hinter dem Spieler liegen */
  private drawOccluders(gl: WebGLRenderingContext, loc: NonNullable<WingRenderer['loc']>, f: WingFrame): void {
    const { layout: L, mv, view } = this;
    const u = L.unit;
    this.bindLayout(gl, loc, this.cubeBuf);
    gl.uniform1f(loc.uPass, 2);
    gl.colorMask(false, false, false, false);
    gl.depthMask(true);
    const draw = () => {
      gl.uniformMatrix4fv(loc.uMV, false, mv);
      gl.drawArrays(gl.TRIANGLES, 0, 36);
    };
    for (const part of L.parts) {
      mv.set(view);
      mTranslate(mv, 0, part.x * u, part.y * u, 0);
      mScale(mv, 0, part.w * u, part.h * u, part.d * u);
      draw();
    }
    if (f.cape) {
      const c = L.cape;
      mv.set(view);
      mTranslate(mv, 0, 0, c.pivotY * u, 0);
      mTranslate(mv, 0, 0, 0, c.z * u);
      mRotX(mv, 0, c.tilt * DEG);
      mTranslate(mv, 0, 0, -c.pivotY * u, 0);
      mScale(mv, 0, c.w * u, c.h * u, c.t * u);
      draw();
    }
    gl.colorMask(true, true, true, true);
  }

  private drawWings(gl: WebGLRenderingContext, loc: NonNullable<WingRenderer['loc']>, model: WingModelData, f: WingFrame): void {
    const { layout: L, base, view } = this;
    const u = L.unit;
    const mount = wingMount(L, f.cape);
    const anim = idleAnim(f.ticks);
    // je Seite: Szene * Ansatz * Spiegelung; Blockbench (x, y, z) -> (side * x, -y, -z) * unit
    [1, -1].forEach((side, i) => {
      base.set(view);
      mTranslate(base, 0, side * mount.spineX * u, mount.rootY * u, -mount.backZ * u);
      mScale(base, 0, side * u, -u, -u);
      poseWing(model, base as Mat, anim, this.pose[i].all);
    });

    this.bindLayout(gl, loc, this.modelBuf);
    // zuerst deckende Texel (schreiben Tiefe), dann durchscheinende (nur Tiefentest, unsortiert wie im Spiel)
    for (let pass = 0; pass < 2; pass++) {
      gl.uniform1f(loc.uPass, pass);
      gl.depthMask(pass === 0);
      for (const side of this.pose) {
        model.nodes.forEach((n, k) => {
          if (n.count === 0) return;
          gl.uniformMatrix4fv(loc.uMV, false, side.views[k]);
          gl.drawArrays(gl.TRIANGLES, n.first, n.count);
        });
      }
    }
    gl.depthMask(true);
  }

  private bindLayout(gl: WebGLRenderingContext, loc: NonNullable<WingRenderer['loc']>, buf: WebGLBuffer | null): void {
    const stride = WING_FLOATS * 4;
    gl.bindBuffer(gl.ARRAY_BUFFER, buf);
    gl.enableVertexAttribArray(loc.aPos);
    gl.vertexAttribPointer(loc.aPos, 3, gl.FLOAT, false, stride, 0);
    gl.enableVertexAttribArray(loc.aUv);
    gl.vertexAttribPointer(loc.aUv, 2, gl.FLOAT, false, stride, 12);
    gl.enableVertexAttribArray(loc.aNrm);
    gl.vertexAttribPointer(loc.aNrm, 3, gl.FLOAT, false, stride, 20);
  }

  /** Gibt alle GPU-Ressourcen frei. Der Kontext selbst bleibt (React StrictMode mountet dasselbe Canvas ein zweites Mal). */
  dispose(): void {
    const gl = this.gl;
    this.releaseModel();
    if (gl) {
      if (this.cubeBuf) gl.deleteBuffer(this.cubeBuf);
      if (this.prog) gl.deleteProgram(this.prog);
    }
    this.cubeBuf = null;
    this.prog = null;
    this.loc = null;
    this.gl = null;
  }
}
