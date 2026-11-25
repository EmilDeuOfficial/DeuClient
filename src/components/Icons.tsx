/** Eigene, schlanke Strich-Icons (24×24), damit keine Icon-Bibliothek nötig ist. */
import type { SVGProps } from 'react';

const paths = {
  home: 'M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z',
  layers: 'M12 3 2 8l10 5 10-5-10-5zM2 13l10 5 10-5M2 17.5l10 5 10-5',
  puzzle:
    'M10 3a2 2 0 0 1 4 0v2h4a1 1 0 0 1 1 1v4h-2a2 2 0 0 0 0 4h2v4a1 1 0 0 1-1 1h-4v-2a2 2 0 0 0-4 0v2H6a1 1 0 0 1-1-1v-4h2a2 2 0 0 0 0-4H5V6a1 1 0 0 1 1-1h4z',
  settings:
    'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 0 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 0 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 0 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 0 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z',
  play: 'M7 4.5v15l12.5-7.5z',
  minus: 'M5 12h14',
  square: 'M5 5h14v14H5z',
  x: 'M6 6l12 12M18 6 6 18',
  folder: 'M3 6a1 1 0 0 1 1-1h5l2 2.5h9a1 1 0 0 1 1 1V18a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1z',
  edit: 'M4 20h4L19 9a2.8 2.8 0 0 0-4-4L4 16zM13.5 6.5l4 4',
  trash: 'M4 7h16M10 11v6M14 11v6M5 7l1 12a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2l1-12M9 7V4h6v3',
  plus: 'M12 5v14M5 12h14',
  download: 'M12 4v11m0 0-4.5-4.5M12 15l4.5-4.5M4 19h16',
  upload: 'M12 20V9m0 0-4.5 4.5M12 9l4.5 4.5M4 5h16',
  search: 'M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14zM21 21l-4.5-4.5',
  check: 'M5 12.5 10 17.5 19.5 7',
  refresh: 'M20 11a8 8 0 0 0-14.6-4.5M4 4v4h4M4 13a8 8 0 0 0 14.6 4.5M20 20v-4h-4',
  users: 'M16 20v-1.5a3.5 3.5 0 0 0-3.5-3.5h-5A3.5 3.5 0 0 0 4 18.5V20M10 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7zM20 20v-1.5a3.5 3.5 0 0 0-2.5-3.4M15.5 4.2a3.5 3.5 0 0 1 0 6.6',
  signal: 'M4 20v-3M9 20v-7M14 20V9M19 20V4',
  terminal: 'M4 5h16v14H4zM7.5 9.5 10.5 12l-3 2.5M12.5 15h4',
  chevronDown: 'M6 9l6 6 6-6',
  chevronRight: 'M9 6l6 6-6 6',
  external: 'M14 4h6v6M20 4l-9 9M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5',
  logout: 'M15 4h4a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1h-4M10 16l4-4-4-4M14 12H4',
  cpu: 'M7 7h10v10H7zM10 10h4v4h-4zM9 3v4M15 3v4M9 17v4M15 17v4M3 9h4M3 15h4M17 9h4M17 15h4',
  globe: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM3 12h18M12 3a14 14 0 0 1 0 18M12 3a14 14 0 0 0 0 18',
  package: 'M12 3 4 7.5v9L12 21l8-4.5v-9zM4 7.5l8 4.5 8-4.5M12 12v9',
  star: 'M12 3.5l2.6 5.4 5.9.8-4.3 4.1 1 5.8L12 16.9l-5.2 2.7 1-5.8-4.3-4.1 5.9-.8z',
  heart: 'M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z',
  copy: 'M9 9h10v10H9zM5 15V5h10',
  stop: 'M7 7h10v10H7z',
  sparkle: 'M12 4c.6 4.8 2.7 7 7.5 8-4.8 1-6.9 3.2-7.5 8-.6-4.8-2.7-7-7.5-8 4.8-1 6.9-3.2 7.5-8z',
  image: 'M4 5h16v14H4zM4 16l4.5-4.5 3.5 3.5 3-3 5 5M15 9.5a1.5 1.5 0 1 0 0-.01',
  sun: 'M12 16a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4',
  clock: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 7v5l3 2',
  monitor: 'M3 4h18v12H3zM8 20h8M12 16v4',
  bucket: 'M4 12l8-8 8 8-7 7a2 2 0 0 1-2.8 0L4 14.8a2 2 0 0 1 0-2.8zM4 12h16M20 16s2 2.2 2 3.5a2 2 0 0 1-4 0C18 18.2 20 16 20 16z',
  eraser: 'M3 17l9.5-9.5a2 2 0 0 1 2.8 0l3.2 3.2a2 2 0 0 1 0 2.8L12 21H7zM9 12l6 6M12 21h9',
  pipette: 'M20.5 3.5a2.1 2.1 0 0 0-3 0L14 7l-1-1-2 2 6 6 2-2-1-1 3.5-3.5a2.1 2.1 0 0 0 0-3zM11 9l-7 7v4h4l7-7',
  bag: 'M5.5 8h13l1 12.5h-15zM9 8V7a3 3 0 0 1 6 0v1M9 12v.5M15 12v.5',
  bolt: 'M13 2.5 4.5 13.5H11l-1 8 8.5-11H12z',
  coffee: 'M4 8h13v5a5 5 0 0 1-5 5H9a5 5 0 0 1-5-5zM17 9h1.5a2.5 2.5 0 0 1 0 5H17M7 3v2M10.5 3v2M14 3v2',
} as const;

export type IconName = keyof typeof paths;

/** Optischer Ausgleich fuer Icons, deren Pfad nicht mittig im 24x24-Feld liegt */
const OFFSET: Partial<Record<IconName, string>> = { puzzle: 'translate(0 1.2)', logout: 'translate(1 0)' };

export function Icon({ name, size = 18, strokeWidth = 2, ...rest }: { name: IconName; size?: number; strokeWidth?: number } & SVGProps<SVGSVGElement>) {
  const filled = name === 'play' || name === 'stop' || name === 'sparkle';
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill={filled ? 'currentColor' : 'none'}
      stroke="currentColor"
      strokeWidth={filled ? 0 : strokeWidth}
      strokeLinecap="round"
      strokeLinejoin="round"
      {...rest}
    >
      <path d={paths[name]} transform={OFFSET[name]} />
    </svg>
  );
}
