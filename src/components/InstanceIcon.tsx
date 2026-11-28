/**
 * Eingebaute Modpack-Icons: Renderings der Blockbench-Modelle in tools/pack-icons (siehe tools/pack-icons.cjs)
 * auf einer Kachel in der Themefarbe. Die Schluessel (grass, diamond, ...) bleiben gleich, damit gespeicherte Modpacks
 * ihr Icon behalten. Die Bilder behalten ihre Farben (Gruen bleibt Gruen), nur die Kachel folgt der Themefarbe.
 */
import { useId } from 'react';

const IMAGES = import.meta.glob('../assets/pack-icons/*.png', { eager: true, query: '?url', import: 'default' }) as Record<string, string>;
const image = (key: string) => IMAGES[`../assets/pack-icons/${key}.png`];

export const ICON_KEYS = ['deu', 'grass', 'diamond', 'sword', 'pickaxe', 'tnt', 'creeper', 'ender', 'package'];

export function InstanceIcon({ icon, size = 48, className = '' }: { icon: string; size?: number; className?: string }) {
  const uid = useId().replace(/:/g, '');
  const key = ICON_KEYS.includes(icon) ? icon : 'deu';
  return (
    <svg width={size} height={size} viewBox="0 0 48 48" className={className}>
      <defs>
        <radialGradient id={`ic${uid}`} cx=".5" cy=".4" r=".75">
          <stop offset="0" style={{ stopColor: 'rgb(var(--c-accent-700))' }} />
          <stop offset="1" style={{ stopColor: 'rgb(var(--c-ink-900))' }} />
        </radialGradient>
      </defs>
      <rect width="48" height="48" rx="12" fill={`url(#ic${uid})`} />
      <rect x=".75" y=".75" width="46.500" height="46.500" rx="11.250" fill="none" strokeOpacity=".45" strokeWidth="1.500" style={{ stroke: 'rgb(var(--c-accent-400))' }} />
      <image href={image(key)} x="4" y="4" width="40" height="40" />
    </svg>
  );
}
