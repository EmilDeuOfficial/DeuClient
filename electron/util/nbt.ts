/**
 * Kleiner Leser/Schreiber für Minecrafts NBT-Format (gzip), z. B. level.dat.
 * Unveränderte Werte bleiben als Rohbytes erhalten, damit beim Zurückschreiben nichts verfälscht wird.
 */
import fs from 'node:fs';
import zlib from 'node:zlib';

export const BYTE = 1;
export const INT = 3;
export const STRING = 8;
export const LIST = 9;
export const COMPOUND = 10;
export const INT_ARRAY = 11;

/** Feste Breite der Zahlentypen (Byte, Short, Int, Long, Float, Double) */
const SIZE: Record<number, number> = { 1: 1, 2: 2, 3: 4, 4: 8, 5: 4, 6: 8 };

export interface Tag {
  type: number;
  /** Zahlen, Text (ohne Längenangabe) und Felder (mit Längenangabe) */
  raw?: Buffer;
  /** Liste: Typ der Einträge */
  elem?: number;
  list?: Tag[];
  map?: Map<string, Tag>;
}

export interface NbtFile {
  name: string;
  root: Tag;
}

export function parseNbt(data: Buffer): NbtFile {
  let pos = 0;
  const take = (n: number) => {
    if (n < 0 || pos + n > data.length) throw new Error('Beschädigte NBT-Datei');
    const b = data.subarray(pos, pos + n);
    pos += n;
    return b;
  };
  const u8 = () => take(1)[0];
  // Namen byteweise (latin1) lesen: so kommen sie unverändert wieder heraus
  const name = () => take(take(2).readUInt16BE(0)).toString('latin1');
  const payload = (type: number, depth: number): Tag => {
    if (depth > 512) throw new Error('Beschädigte NBT-Datei');
    if (SIZE[type]) return { type, raw: take(SIZE[type]) };
    if (type === 7 || type === 11 || type === 12) {
      const width = type === 7 ? 1 : type === 11 ? 4 : 8;
      const count = data.readInt32BE(pos);
      return { type, raw: take(4 + count * width) };
    }
    if (type === STRING) return { type, raw: take(take(2).readUInt16BE(0)) };
    if (type === LIST) {
      const elem = u8();
      const count = take(4).readInt32BE(0);
      const list: Tag[] = [];
      for (let i = 0; i < count; i++) list.push(payload(elem, depth + 1));
      return { type, elem, list };
    }
    if (type === COMPOUND) {
      const map = new Map<string, Tag>();
      for (let t = u8(); t !== 0; t = u8()) {
        const key = name();
        map.set(key, payload(t, depth + 1));
      }
      return { type, map };
    }
    throw new Error(`Unbekannter NBT-Typ ${type}`);
  };
  if (u8() !== COMPOUND) throw new Error('Keine NBT-Datei');
  const rootName = name();
  return { name: rootName, root: payload(COMPOUND, 0) };
}

export function writeNbt(file: NbtFile): Buffer {
  const parts: Buffer[] = [];
  const u8 = (v: number) => parts.push(Buffer.from([v]));
  const u16 = (v: number) => {
    const b = Buffer.alloc(2);
    b.writeUInt16BE(v, 0);
    parts.push(b);
  };
  const name = (s: string) => {
    const b = Buffer.from(s, 'latin1');
    u16(b.length);
    parts.push(b);
  };
  const payload = (tag: Tag) => {
    if (tag.type === STRING) {
      u16(tag.raw!.length);
      parts.push(tag.raw!);
    } else if (tag.type === LIST) {
      u8(tag.elem!);
      const count = Buffer.alloc(4);
      count.writeInt32BE(tag.list!.length, 0);
      parts.push(count);
      tag.list!.forEach(payload);
    } else if (tag.type === COMPOUND) {
      for (const [key, child] of tag.map!) {
        u8(child.type);
        name(key);
        payload(child);
      }
      u8(0);
    } else {
      parts.push(tag.raw!);
    }
  };
  u8(COMPOUND);
  name(file.name);
  payload(file.root);
  return Buffer.concat(parts);
}

export function readNbtFile(file: string): NbtFile {
  return parseNbt(zlib.gunzipSync(fs.readFileSync(file)));
}

/** Schreibt erst in eine Nebendatei; die bisherige Fassung bleibt als backup erhalten */
export function writeNbtFile(file: string, nbt: NbtFile, backup?: string): void {
  const tmp = `${file}.deu-tmp`;
  fs.writeFileSync(tmp, zlib.gzipSync(writeNbt(nbt)));
  if (backup && fs.existsSync(file)) fs.copyFileSync(file, backup);
  fs.renameSync(tmp, file);
}

/* ---------------- Werte ---------------- */

export function numberOf(tag: Tag | undefined): number | undefined {
  if (!tag?.raw) return undefined;
  if (tag.type === 1) return tag.raw.readInt8(0);
  if (tag.type === 2) return tag.raw.readInt16BE(0);
  if (tag.type === 3) return tag.raw.readInt32BE(0);
  if (tag.type === 4) return Number(tag.raw.readBigInt64BE(0));
  return undefined;
}

export function numberTag(type: 1 | 3, value: number): Tag {
  const raw = Buffer.alloc(SIZE[type]);
  if (type === 1) raw.writeInt8(value, 0);
  else raw.writeInt32BE(value, 0);
  return { type, raw };
}

/** Setzt eine Zahl und behält den vorhandenen Typ (Byte oder Int) bei */
export function setNumber(compound: Tag, key: string, value: number, fallback: 1 | 3): void {
  const type = compound.map!.get(key)?.type;
  compound.map!.set(key, numberTag(type === 1 || type === 3 ? type : fallback, value));
}

/** Java speichert Text in "modified UTF-8" (Zeichen außerhalb der Grundebene als Surrogatpaar) */
export function stringOf(tag: Tag | undefined): string | undefined {
  if (!tag?.raw || tag.type !== STRING) return undefined;
  const b = tag.raw;
  let out = '';
  for (let i = 0; i < b.length; ) {
    const c = b[i];
    if (c < 0x80) {
      out += String.fromCharCode(c);
      i += 1;
    } else if ((c & 0xe0) === 0xc0) {
      out += String.fromCharCode(((c & 0x1f) << 6) | (b[i + 1] & 0x3f));
      i += 2;
    } else {
      out += String.fromCharCode(((c & 0x0f) << 12) | ((b[i + 1] & 0x3f) << 6) | (b[i + 2] & 0x3f));
      i += 3;
    }
  }
  return out;
}

export function stringTag(value: string): Tag {
  const bytes: number[] = [];
  for (let i = 0; i < value.length; i++) {
    const c = value.charCodeAt(i);
    if (c > 0 && c < 0x80) bytes.push(c);
    else if (c < 0x800) bytes.push(0xc0 | (c >> 6), 0x80 | (c & 0x3f));
    else bytes.push(0xe0 | (c >> 12), 0x80 | ((c >> 6) & 0x3f), 0x80 | (c & 0x3f));
  }
  return { type: STRING, raw: Buffer.from(bytes) };
}

export function compoundOf(tag: Tag | undefined, key: string): Tag | undefined {
  const child = tag?.map?.get(key);
  return child?.type === COMPOUND ? child : undefined;
}
