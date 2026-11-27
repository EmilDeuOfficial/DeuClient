/**
 * Einstellungen einer Einzelspieler-Welt lesen und ändern (Name, Spielmodus, Schwierigkeit, Cheats, Spielregeln).
 * Unterstützt den Aufbau ab Minecraft 26.1 (difficulty_settings, players/data, data/minecraft/game_rules.dat)
 * und den älteren (alles in level.dat).
 */
import fs from 'node:fs';
import path from 'node:path';
import type { WorldDifficulty, WorldRule, WorldSettings } from '../../shared/types';
import {
  BYTE, COMPOUND, INT_ARRAY, compoundOf, numberOf, numberTag, readNbtFile, setNumber, stringOf, stringTag, writeNbtFile,
  type NbtFile, type Tag,
} from '../util/nbt';

const DIFFICULTIES: WorldDifficulty[] = ['peaceful', 'easy', 'normal', 'hard'];

/** Spielregel: Name ab 26.1 (bzw. 1.21.11) und der frühere Name */
const RULES: Record<WorldRule, string[]> = {
  keepInventory: ['minecraft:keep_inventory', 'keepInventory'],
  mobGriefing: ['minecraft:mob_griefing', 'mobGriefing'],
  daylightCycle: ['minecraft:advance_time', 'doDaylightCycle'],
  weatherCycle: ['minecraft:advance_weather', 'doWeatherCycle'],
  mobSpawning: ['minecraft:spawn_mobs', 'doMobSpawning'],
  immediateRespawn: ['minecraft:immediate_respawn', 'doImmediateRespawn'],
  fallDamage: ['minecraft:fall_damage', 'fallDamage'],
  pvp: ['minecraft:pvp', 'pvp'],
};

interface World {
  level: NbtFile;
  data: Tag;
  /** Spielerdaten des Einzelspielers (ab 26.1 eine eigene Datei) */
  playerFile?: { file: string; nbt: NbtFile };
  player?: Tag;
  /** Spielregeln: eigene Datei ab 26.1, sonst in level.dat */
  rulesFile?: { file: string; nbt: NbtFile };
  rules?: Tag;
}

function readLevel(dir: string): { level: NbtFile; data: Tag } {
  const level = readNbtFile(path.join(dir, 'level.dat'));
  const data = compoundOf(level.root, 'Data');
  if (!data) throw new Error('level.dat ist unvollständig');
  return { level, data };
}

function uuidOf(tag: Tag | undefined): string | undefined {
  if (tag?.type !== INT_ARRAY || tag.raw?.length !== 20) return undefined;
  const hex = tag.raw.subarray(4).toString('hex');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

function open(dir: string): World {
  const world: World = readLevel(dir);
  const uuid = uuidOf(world.data.map!.get('singleplayer_uuid'));
  const playerPath = uuid ? path.join(dir, 'players', 'data', `${uuid}.dat`) : '';
  if (playerPath && fs.existsSync(playerPath)) {
    world.playerFile = { file: playerPath, nbt: readNbtFile(playerPath) };
    world.player = world.playerFile.nbt.root;
  } else {
    world.player = compoundOf(world.data, 'Player');
  }
  const rulesPath = path.join(dir, 'data', 'minecraft', 'game_rules.dat');
  if (fs.existsSync(rulesPath)) {
    world.rulesFile = { file: rulesPath, nbt: readNbtFile(rulesPath) };
    world.rules = compoundOf(world.rulesFile.nbt.root, 'data');
  } else {
    world.rules = compoundOf(world.data, 'game_rules') ?? compoundOf(world.data, 'GameRules');
  }
  return world;
}

function ruleValue(tag: Tag): boolean {
  const text = stringOf(tag);
  return text !== undefined ? text === 'true' : numberOf(tag) !== 0;
}

function settingsOf(w: World, folder: string): WorldSettings {
  const d = w.data;
  const modern = compoundOf(d, 'difficulty_settings');
  const named = stringOf(modern?.map!.get('difficulty')) as WorldDifficulty | undefined;
  const difficulty = named ?? DIFFICULTIES[numberOf(d.map!.get('Difficulty')) ?? 2] ?? 'normal';
  const rules: WorldSettings['rules'] = {};
  for (const [rule, keys] of Object.entries(RULES) as [WorldRule, string[]][]) {
    const tag = keys.map((k) => w.rules?.map!.get(k)).find((t) => !!t);
    if (tag) rules[rule] = ruleValue(tag);
  }
  return {
    name: stringOf(d.map!.get('LevelName')) ?? folder,
    // der Modus des Spielers zählt (z. B. nach /gamemode), sonst der der Welt
    gameMode: numberOf(w.player?.map!.get('playerGameType')) ?? numberOf(d.map!.get('GameType')) ?? 0,
    difficulty,
    difficultyLocked: !!numberOf(modern ? modern.map!.get('locked') : d.map!.get('DifficultyLocked')),
    hardcore: !!numberOf(modern ? modern.map!.get('hardcore') : d.map!.get('hardcore')),
    allowCommands: !!numberOf(d.map!.get('allowCommands')),
    rules,
  };
}

export function readWorldSettings(dir: string): WorldSettings {
  return settingsOf(open(dir), path.basename(dir));
}

/** Kurzangaben für die Weltenliste (liest nur level.dat) */
export function readWorldSummary(dir: string): { name?: string; lastPlayed?: number; gameMode?: number; hardcore?: boolean } {
  try {
    const { data } = readLevel(dir);
    const modern = compoundOf(data, 'difficulty_settings');
    return {
      name: stringOf(data.map!.get('LevelName')),
      lastPlayed: numberOf(data.map!.get('LastPlayed')),
      gameMode: numberOf(data.map!.get('GameType')),
      hardcore: !!numberOf(modern ? modern.map!.get('hardcore') : data.map!.get('hardcore')),
    };
  } catch {
    return {};
  }
}

/** Schreibt nur, was sich geändert hat */
export function writeWorldSettings(dir: string, next: WorldSettings): void {
  const w = open(dir);
  const cur = settingsOf(w, path.basename(dir));
  const d = w.data;
  let levelChanged = false;
  let playerChanged = false;
  let rulesChanged = false;

  const name = next.name.trim().slice(0, 64);
  if (name && name !== cur.name) {
    d.map!.set('LevelName', stringTag(name));
    levelChanged = true;
  }
  if (next.gameMode !== cur.gameMode && [0, 1, 2, 3].includes(next.gameMode)) {
    setNumber(d, 'GameType', next.gameMode, 3);
    levelChanged = true;
    if (w.player) {
      setNumber(w.player, 'playerGameType', next.gameMode, 3);
      playerChanged = true;
    }
  }
  const difficultyChanged =
    next.difficulty !== cur.difficulty || next.difficultyLocked !== cur.difficultyLocked || next.hardcore !== cur.hardcore;
  if (difficultyChanged && DIFFICULTIES.includes(next.difficulty)) {
    const modern = compoundOf(d, 'difficulty_settings');
    if (modern) {
      modern.map!.set('difficulty', stringTag(next.difficulty));
      setNumber(modern, 'locked', next.difficultyLocked ? 1 : 0, BYTE);
      setNumber(modern, 'hardcore', next.hardcore ? 1 : 0, BYTE);
    } else {
      setNumber(d, 'Difficulty', DIFFICULTIES.indexOf(next.difficulty), BYTE);
      setNumber(d, 'DifficultyLocked', next.difficultyLocked ? 1 : 0, BYTE);
      setNumber(d, 'hardcore', next.hardcore ? 1 : 0, BYTE);
    }
    levelChanged = true;
  }
  if (next.allowCommands !== cur.allowCommands) {
    d.map!.set('allowCommands', numberTag(BYTE, next.allowCommands ? 1 : 0));
    levelChanged = true;
  }
  for (const [rule, value] of Object.entries(next.rules) as [WorldRule, boolean][]) {
    if (cur.rules[rule] === undefined || cur.rules[rule] === value || w.rules?.type !== COMPOUND) continue;
    const key = RULES[rule]?.find((k) => w.rules!.map!.has(k));
    if (!key) continue;
    const old = w.rules.map!.get(key)!;
    w.rules.map!.set(key, stringOf(old) !== undefined ? stringTag(String(value)) : numberTag(BYTE, value ? 1 : 0));
    if (w.rulesFile) rulesChanged = true;
    else levelChanged = true;
  }

  // Spielerdaten, die in level.dat liegen, werden mit level.dat gespeichert
  if (playerChanged && !w.playerFile) levelChanged = true;
  if (levelChanged) writeNbtFile(path.join(dir, 'level.dat'), w.level, path.join(dir, 'level.dat_old'));
  if (playerChanged && w.playerFile) writeNbtFile(w.playerFile.file, w.playerFile.nbt, `${w.playerFile.file}_old`);
  if (rulesChanged && w.rulesFile) writeNbtFile(w.rulesFile.file, w.rulesFile.nbt);
}

/** true, solange Minecraft die Welt geöffnet hält (gesperrte session.lock) */
export function worldInUse(dir: string): boolean {
  let fd: number | undefined;
  try {
    fd = fs.openSync(path.join(dir, 'session.lock'), 'r+');
    fs.readSync(fd, Buffer.alloc(1), 0, 1, 0);
    return false;
  } catch (e) {
    return (e as NodeJS.ErrnoException).code !== 'ENOENT';
  } finally {
    if (fd !== undefined) fs.closeSync(fd);
  }
}
