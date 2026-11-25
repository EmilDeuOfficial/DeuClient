/**
 * 3D-Fluegelmodell aus einer Blockbench-Datei (public/cosmetics/wings/<id>.bbmodel) fuer die Anprobe im Launcher.
 *
 * Gleiche Regeln wie der Lader im Spiel (mod/mc26.3/.../cosmetics/WingModel.java, Spec: tools/wings/SPEC.md):
 * nur Cubes, Per-Face-UV (v = 0 ist oben in der Textur, normiert mit "resolution"), Flaechen ohne Textur entfallen,
 * Gruppen bilden die Hierarchie (Pivot + Ruhe-Rotation). Animation nach Gruppennamen: "wing" = Oeffnungswinkel und
 * Flap, "segN" (N >= 2) = phasenverzoegerter Roll, "f_*" = leichtes Wiegen.
 *
 * Blockbench-Raum (16 Einheiten = 1 Block = 16 Skin-Pixel): +X nach aussen, +Y oben, +Z nach hinten, Ursprung am
 * Schulterblatt. Es gibt nur den rechten Fluegel, der linke ist gespiegelt (macht der Renderer ueber die Basis-Matrix).
 *
 * Reine Mathematik, keine DOM- oder WebGL-Abhaengigkeit (wingRenderer.ts zeichnet).
 */

export type Vec3 = [number, number, number];
export type Mat = Float32Array | Float64Array;

export const DEG = Math.PI / 180;
/** Floats pro Vertex: x y z u v nx ny nz */
export const WING_FLOATS = 8;

export const K_OTHER = 0;
export const K_WING = 1;
export const K_SEG = 2;
export const K_FEATHER = 3;

const EPS = 1e-3;
const MAX_DEPTH = 32;

/** Animationsparameter eines Frames (Winkel in Grad), wie WingModel.Anim im Spiel */
export interface WingAnim {
  /** Oeffnungswinkel: Fluegel schwenken mit der Spitze nach hinten */
  open: number;
  /** Amplitude des Flaps (Roll) */
  flapAmp: number;
  /** Phase des Flaps in Radiant */
  flapPhase: number;
  /** Amplitude des Wiegens der Federn */
  swayAmp: number;
  /** Zeit in Ticks (20 pro Sekunde) */
  time: number;
}

/** Ruhe-Animation im Spiel (nicht geduckt, kein Elytra-Flug): 16 Grad offen, kleiner Flap, leichtes Wiegen */
export function idleAnim(ticks: number): WingAnim {
  return { open: 16, flapAmp: 5, flapPhase: ticks * 0.1, swayAmp: 2.5, time: ticks };
}

export interface WingNode {
  name: string;
  kind: number;
  seg: number;
  phase: number;
  origin: Vec3;
  /** Ruhe-Rotation in Radiant (X, Y, Z) */
  rot: Vec3;
  rest: boolean;
  /** Index des Eltern-Knotens in WingModelData.nodes (-1 bei der Wurzel) */
  parent: number;
  /** Erster Vertex und Anzahl der Vertices (Dreiecke) dieses Knotens in WingModelData.vertices */
  first: number;
  count: number;
}

export interface WingBounds {
  min: Vec3;
  max: Vec3;
}

export interface WingModelData {
  /** Vorordnung (Eltern stehen vor ihren Kindern), nodes[0] ist die Wurzel */
  nodes: WingNode[];
  /** Nicht indizierte Dreiecke, WING_FLOATS Floats je Vertex, je Knoten ein zusammenhaengender Bereich */
  vertices: Float32Array;
  vertexCount: number;
  cubeCount: number;
  /** Ausdehnung in der Ruhepose mit idleAnim (Flap 0), im Blockbench-Raum */
  bounds: WingBounds;
}

/* ------------------------------------------------------------------------------------------ Matrizen */
// Spaltenweise 4x4 (wie WebGL/JOML), alle Operationen multiplizieren von rechts: m = m * X

export function mIdentity(m: Mat, o = 0): void {
  for (let i = 0; i < 16; i++) m[o + i] = i % 5 === 0 ? 1 : 0;
}

export function mTranslate(m: Mat, o: number, x: number, y: number, z: number): void {
  for (let i = 0; i < 4; i++) m[o + 12 + i] += m[o + i] * x + m[o + 4 + i] * y + m[o + 8 + i] * z;
}

export function mScale(m: Mat, o: number, x: number, y: number, z: number): void {
  for (let i = 0; i < 4; i++) {
    m[o + i] *= x;
    m[o + 4 + i] *= y;
    m[o + 8 + i] *= z;
  }
}

export function mRotX(m: Mat, o: number, a: number): void {
  const c = Math.cos(a), s = Math.sin(a);
  for (let i = 0; i < 4; i++) {
    const a1 = m[o + 4 + i], a2 = m[o + 8 + i];
    m[o + 4 + i] = a1 * c + a2 * s;
    m[o + 8 + i] = -a1 * s + a2 * c;
  }
}

export function mRotY(m: Mat, o: number, a: number): void {
  const c = Math.cos(a), s = Math.sin(a);
  for (let i = 0; i < 4; i++) {
    const a0 = m[o + i], a2 = m[o + 8 + i];
    m[o + i] = a0 * c - a2 * s;
    m[o + 8 + i] = a0 * s + a2 * c;
  }
}

export function mRotZ(m: Mat, o: number, a: number): void {
  const c = Math.cos(a), s = Math.sin(a);
  for (let i = 0; i < 4; i++) {
    const a0 = m[o + i], a1 = m[o + 4 + i];
    m[o + i] = a0 * c + a1 * s;
    m[o + 4 + i] = -a0 * s + a1 * c;
  }
}

/* ------------------------------------------------------------------------------------------ Parser */

type Json = Record<string, unknown>;
const isObj = (v: unknown): v is Json => typeof v === 'object' && v !== null && !Array.isArray(v);
const obj = (o: Json, k: string): Json | null => (isObj(o[k]) ? (o[k] as Json) : null);
const arr = (o: Json, k: string): unknown[] | null => (Array.isArray(o[k]) ? (o[k] as unknown[]) : null);
const str = (o: Json, k: string): string | null => (typeof o[k] === 'string' ? (o[k] as string) : null);
const num = (o: Json, k: string, def: number): number => (typeof o[k] === 'number' && Number.isFinite(o[k]) ? (o[k] as number) : def);

/** Boolean aus dem Eintrag, sonst aus der Definition (Blockbench 5 trennt Outliner und Gruppen-Daten) */
function bool(o: Json, fallback: Json | null, k: string, def: boolean): boolean {
  if (typeof o[k] === 'boolean') return o[k] as boolean;
  if (fallback && typeof fallback[k] === 'boolean') return fallback[k] as boolean;
  return def;
}

/** Vektor aus 3 Zahlen; fehlt er im Eintrag, wird die Definition benutzt, sonst (0, 0, 0) */
function vec3(o: Json, fallback: Json | null, k: string): Vec3 {
  let a = arr(o, k);
  if (!a && fallback) a = arr(fallback, k);
  const r: Vec3 = [0, 0, 0];
  if (a && a.length >= 3) for (let i = 0; i < 3; i++) r[i] = typeof a[i] === 'number' ? (a[i] as number) : 0;
  return r;
}

/** Flaechen-Reihenfolge: north (-Z), south (+Z), east (+X), west (-X), up (+Y), down (-Y) */
const FACE_NAMES = ['north', 'south', 'east', 'west', 'up', 'down'];
/** Achse, die die Flaeche durchstoesst (0 = X, 1 = Y, 2 = Z) - fuer duenne Ebenen */
const FACE_AXIS = [2, 2, 0, 0, 1, 1];
const FACE_NORMAL: Vec3[] = [[0, 0, -1], [0, 0, 1], [1, 0, 0], [-1, 0, 0], [0, 1, 0], [0, -1, 0]];
/**
 * Ecken je Flaeche (0 = min, 1 = max der Achsen x, y, z) in der Reihenfolge der Blockbench-Flaechen:
 * links oben, links unten, rechts unten, rechts oben - von aussen gesehen.
 */
const FACE_VERTS: number[][][] = [
  [[1, 1, 0], [1, 0, 0], [0, 0, 0], [0, 1, 0]], // north
  [[0, 1, 1], [0, 0, 1], [1, 0, 1], [1, 1, 1]], // south
  [[1, 1, 1], [1, 0, 1], [1, 0, 0], [1, 1, 0]], // east
  [[0, 1, 0], [0, 0, 0], [0, 0, 1], [0, 1, 1]], // west
  [[0, 1, 0], [0, 1, 1], [1, 1, 1], [1, 1, 0]], // up
  [[0, 0, 1], [0, 0, 0], [1, 0, 0], [1, 0, 1]], // down
];
/** Ecken eines Quads zu zwei Dreiecken */
const QUAD_TRIS = [0, 1, 2, 0, 2, 3];

/** Liest ein .bbmodel (Blockbench Generic Model, Per-Face-UV). Wirft bei kaputten Dateien eine Exception. */
export function parseWingModel(input: unknown): WingModelData {
  if (!isObj(input)) throw new Error('Fluegelmodell: kein JSON-Objekt');
  const json = input;

  const res = obj(json, 'resolution');
  const resW = res ? num(res, 'width', 0) : 0;
  const resH = res ? num(res, 'height', 0) : 0;
  if (resW <= 0 || resH <= 0) throw new Error('Fluegelmodell: resolution fehlt oder ist ungueltig');

  const elements = new Map<string, Json>();
  for (const e of arr(json, 'elements') ?? []) {
    if (isObj(e)) {
      const uuid = str(e, 'uuid');
      if (uuid) elements.set(uuid, e);
    }
  }
  const groupDefs = new Map<string, Json>();
  for (const g of arr(json, 'groups') ?? []) {
    if (isObj(g)) {
      const uuid = str(g, 'uuid');
      if (uuid) groupDefs.set(uuid, g);
    }
  }
  const outliner = arr(json, 'outliner');
  if (!outliner) throw new Error('Fluegelmodell: outliner fehlt');

  const groupName = (entry: Json): string | null => {
    const name = str(entry, 'name');
    if (name !== null) return name;
    const uuid = str(entry, 'uuid');
    const def = uuid ? groupDefs.get(uuid) : undefined;
    return def ? str(def, 'name') : null;
  };
  const wingFound = outliner.some((e) => isObj(e) && groupName(e) === 'wing');

  const nodes: WingNode[] = [];
  const chunks: number[][] = [];
  let cubeCount = 0;
  let feathers = 0;

  const addNode = (name: string, kind: number, seg: number, phase: number, origin: Vec3, rotDeg: Vec3, parent: number): number => {
    nodes.push({
      name, kind, seg, phase, origin,
      rot: [rotDeg[0] * DEG, rotDeg[1] * DEG, rotDeg[2] * DEG],
      rest: rotDeg[0] !== 0 || rotDeg[1] !== 0 || rotDeg[2] !== 0,
      parent, first: 0, count: 0,
    });
    chunks.push([]);
    return nodes.length - 1;
  };

  /** Haengt alle renderbaren Flaechen eines Cubes an `out` (Vertices: x y z u v nx ny nz, als Dreiecke) */
  const cube = (out: number[], e: Json): void => {
    const type = str(e, 'type');
    if (type !== null && type !== 'cube') return; // nur Cubes
    if (!bool(e, null, 'export', true)) return;
    cubeCount++;

    const from = vec3(e, null, 'from'), to = vec3(e, null, 'to');
    const inflate = num(e, 'inflate', 0);
    const mn: Vec3 = [0, 0, 0], mx: Vec3 = [0, 0, 0];
    let flat = -1, flatCount = 0;
    for (let i = 0; i < 3; i++) {
      mn[i] = Math.min(from[i], to[i]) - inflate;
      mx[i] = Math.max(from[i], to[i]) + inflate;
      if (mx[i] - mn[i] < EPS) {
        flat = i;
        flatCount++;
      }
    }
    if (flatCount >= 2) return; // Linie/Punkt: keine Flaeche
    const faces = obj(e, 'faces');
    if (!faces) return;

    const org = vec3(e, null, 'origin'), rot = vec3(e, null, 'rotation');
    let rm: Float64Array | null = null;
    if (rot[0] !== 0 || rot[1] !== 0 || rot[2] !== 0) {
      // Blockbench: Euler-Reihenfolge ZYX (X zuerst auf den Vertex), um den Cube-Pivot
      rm = new Float64Array(16);
      mIdentity(rm);
      mTranslate(rm, 0, org[0], org[1], org[2]);
      mRotZ(rm, 0, rot[2] * DEG);
      mRotY(rm, 0, rot[1] * DEG);
      mRotX(rm, 0, rot[0] * DEG);
      mTranslate(rm, 0, -org[0], -org[1], -org[2]);
    }

    let planeDone = false;
    for (let d = 0; d < 6; d++) {
      // duenne Ebene: nur die beiden Flaechen auf der flachen Achse, und davon nur die erste gueltige
      if (flat >= 0 && (FACE_AXIS[d] !== flat || planeDone)) continue;
      const face = obj(faces, FACE_NAMES[d]);
      if (!face) continue;
      if (typeof face.texture !== 'number') continue; // null/false = aus
      const uv = arr(face, 'uv');
      if (!uv || uv.length < 4) continue;

      const u1 = Number(uv[0]) / resW, v1 = Number(uv[1]) / resH;
      const u2 = Number(uv[2]) / resW, v2 = Number(uv[3]) / resH;
      // Ecken der UV-Flaeche: links oben, links unten, rechts unten, rechts oben; v = 0 ist oben in der Textur
      const cu = [u1, u1, u2, u2];
      const cv = [v1, v2, v2, v1];
      const rotation = Math.floor((((Math.round(num(face, 'rotation', 0)) % 360) + 360) % 360) / 90);

      const corner: number[][] = [];
      for (let i = 0; i < 4; i++) {
        const c = FACE_VERTS[d][i];
        let x = c[0] === 0 ? mn[0] : mx[0];
        let y = c[1] === 0 ? mn[1] : mx[1];
        let z = c[2] === 0 ? mn[2] : mx[2];
        if (rm) {
          const tx = rm[0] * x + rm[4] * y + rm[8] * z + rm[12];
          const ty = rm[1] * x + rm[5] * y + rm[9] * z + rm[13];
          const tz = rm[2] * x + rm[6] * y + rm[10] * z + rm[14];
          x = tx;
          y = ty;
          z = tz;
        }
        const k = (i + rotation) & 3; // wie in Minecraft: Rotation verschiebt die UV-Ecken
        corner.push([x, y, z, cu[k], cv[k]]);
      }
      let [nx, ny, nz] = FACE_NORMAL[d];
      if (rm) {
        const tx = rm[0] * nx + rm[4] * ny + rm[8] * nz;
        const ty = rm[1] * nx + rm[5] * ny + rm[9] * nz;
        const tz = rm[2] * nx + rm[6] * ny + rm[10] * nz;
        nx = tx;
        ny = ty;
        nz = tz;
      }
      for (const i of QUAD_TRIS) out.push(...corner[i], nx, ny, nz);
      if (flat >= 0) planeDone = true;
    }
  };

  const fill = (node: number, children: unknown[], depth: number, top: boolean): void => {
    const groups: Json[] = [];
    for (const c of children) {
      if (typeof c === 'string') {
        const el = elements.get(c);
        if (el) cube(chunks[node], el);
      } else if (isObj(c) && depth < MAX_DEPTH) {
        groups.push(c);
      }
    }
    for (const entry of groups) {
      const uuid = str(entry, 'uuid');
      const def = uuid ? groupDefs.get(uuid) ?? null : null;
      if (!bool(entry, def, 'export', true)) continue;
      const name = groupName(entry) ?? '';
      const origin = vec3(entry, def, 'origin');
      const rotation = vec3(entry, def, 'rotation');

      let kind = K_OTHER, seg = 0, phase = 0;
      if (top && (!wingFound || name === 'wing')) {
        kind = K_WING;
      } else if (/^seg\d{1,6}$/.test(name)) {
        kind = K_SEG;
        seg = parseInt(name.slice(3), 10);
      } else if (name.startsWith('f_')) {
        kind = K_FEATHER;
        phase = feathers++ * 0.83;
      }
      const idx = addNode(name, kind, seg, phase, origin, rotation, node);
      const kids = arr(entry, 'children');
      if (kids) fill(idx, kids, depth + 1, false);
    }
  };

  const root = addNode('root', K_OTHER, 0, 0, [0, 0, 0], [0, 0, 0], -1);
  fill(root, outliner, 0, true);

  // Knoten-Bereiche der Reihe nach in einen gemeinsamen Vertex-Puffer legen
  let total = 0;
  for (const c of chunks) total += c.length;
  if (total === 0) throw new Error('Fluegelmodell: keine renderbaren Flaechen (Cubes mit Textur und UV)');
  const vertices = new Float32Array(total);
  let at = 0;
  chunks.forEach((c, i) => {
    nodes[i].first = at / WING_FLOATS;
    nodes[i].count = c.length / WING_FLOATS;
    vertices.set(c, at);
    at += c.length;
  });

  const data: WingModelData = {
    nodes, vertices, vertexCount: total / WING_FLOATS, cubeCount,
    bounds: { min: [0, 0, 0], max: [0, 0, 0] },
  };
  data.bounds = wingBounds(data);
  return data;
}

/* ------------------------------------------------------------------------------------------ Pose */

/**
 * Berechnet die Matrix jedes Knotens (Basis * Pivot * Animation * Ruhe-Rotation * -Pivot, entlang der Hierarchie).
 * `out` hat 16 Floats je Knoten (Reihenfolge wie model.nodes). Die Vertices eines Knotens stehen im Blockbench-Raum
 * und werden direkt mit out[i] transformiert. Gleiche Formeln wie WingModel.draw im Spiel.
 */
export function poseWing(model: WingModelData, base: Mat, a: WingAnim, out: Float32Array): void {
  const nodes = model.nodes;
  for (let i = 0; i < nodes.length; i++) {
    const n = nodes[i];
    const o = i * 16;
    const src = n.parent < 0 ? base : out;
    const so = n.parent < 0 ? 0 : n.parent * 16;
    for (let k = 0; k < 16; k++) out[o + k] = src[so + k];
    if (n.kind === K_OTHER && !n.rest) continue;

    mTranslate(out, o, n.origin[0], n.origin[1], n.origin[2]);
    // Animation sitzt im Raum der Elterngruppe, die Ruhe-Rotation des Modells wird zuerst auf die Vertices angewandt
    if (n.kind === K_WING) {
      mRotY(out, o, -a.open * DEG);
      const roll = a.flapAmp * Math.sin(a.flapPhase);
      if (roll !== 0) mRotZ(out, o, roll * DEG);
    } else if (n.kind === K_SEG) {
      if (n.seg >= 2 && a.flapAmp !== 0) {
        const deep = n.seg - 1;
        const amp = a.flapAmp * Math.min(0.3 * deep, 0.65);
        mRotZ(out, o, amp * Math.sin(a.flapPhase - 0.75 * deep) * DEG);
      }
    } else if (n.kind === K_FEATHER) {
      if (a.swayAmp !== 0 || a.flapAmp !== 0) {
        const sway = a.swayAmp * Math.sin(a.time * 0.11 + n.phase)
          + a.flapAmp * 0.12 * Math.sin(a.flapPhase - 1.1 - n.phase * 0.15);
        mRotZ(out, o, sway * DEG);
      }
    }
    if (n.rest) {
      mRotZ(out, o, n.rot[2]);
      mRotY(out, o, n.rot[1]);
      mRotX(out, o, n.rot[0]);
    }
    mTranslate(out, o, -n.origin[0], -n.origin[1], -n.origin[2]);
  }
}

/** Ausdehnung der Vertices in der Ruhepose (idle-Oeffnung, ohne Flap und Wiegen), im Blockbench-Raum */
function wingBounds(model: WingModelData): WingBounds {
  const base = new Float64Array(16);
  mIdentity(base);
  const pose = new Float32Array(model.nodes.length * 16);
  poseWing(model, base, { open: 16, flapAmp: 0, flapPhase: 0, swayAmp: 0, time: 0 }, pose);
  const min: Vec3 = [Infinity, Infinity, Infinity], max: Vec3 = [-Infinity, -Infinity, -Infinity];
  const v = model.vertices;
  model.nodes.forEach((n, i) => {
    const m = i * 16;
    for (let j = 0; j < n.count; j++) {
      const b = (n.first + j) * WING_FLOATS;
      const x = v[b], y = v[b + 1], z = v[b + 2];
      const p = [
        pose[m] * x + pose[m + 4] * y + pose[m + 8] * z + pose[m + 12],
        pose[m + 1] * x + pose[m + 5] * y + pose[m + 9] * z + pose[m + 13],
        pose[m + 2] * x + pose[m + 6] * y + pose[m + 10] * z + pose[m + 14],
      ];
      for (let k = 0; k < 3; k++) {
        if (p[k] < min[k]) min[k] = p[k];
        if (p[k] > max[k]) max[k] = p[k];
      }
    }
  });
  return { min, max };
}
