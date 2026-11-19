/**
 * Eingebaute Modpack-Icons: saubere Vektorgrafiken im DeuClient-Stil (gruenes, dunkles Kachel-Design).
 * Die Schluessel (grass, diamond, ...) bleiben gleich, damit gespeicherte Modpacks ihr Icon behalten.
 */
import { useId, type ReactNode } from 'react';
import emblem from '../assets/emblem.png';

type Glyph = (p: { id: (name: string) => string }) => ReactNode;

const GLYPHS: Record<string, Glyph> = {
  grass: ({ id }) => (
    <>
      <defs>
        <linearGradient id={id('top')} x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#86efac" /><stop offset="1" stopColor="#22c55e" /></linearGradient>
        <linearGradient id={id('l')} x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#9a6a3a" /><stop offset="1" stopColor="#6b4423" /></linearGradient>
        <linearGradient id={id('r')} x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#7a4f29" /><stop offset="1" stopColor="#4d3019" /></linearGradient>
      </defs>
      <polygon points="9,17.5 24,26 24,40 9,31.5" fill={`url(#${id('l')})`} />
      <polygon points="39,17.5 24,26 24,40 39,31.5" fill={`url(#${id('r')})`} />
      <path d="M9 17.5 24 26v4.2c-2-.2-2.6-2-4.4-1.6-1.8.4-1.6 2.2-3.6 2.2s-2.2-2.2-4.2-2c-1.6.2-1.8 1.8-2.8 1.4V17.5z" fill="#2fa84f" />
      <path d="M39 17.5 24 26v4.2c2-.2 2.8-1.8 4.6-1.4 1.6.4 1.8 2 3.6 2 2 0 2.2-2.6 4.2-2.4.8.1 1.6.7 2.6.4V17.5z" fill="#238a40" />
      <polygon points="24,9 39,17.5 24,26 9,17.5" fill={`url(#${id('top')})`} />
    </>
  ),
  diamond: () => (
    <>
      <polygon points="16,12 32,12 28,21 20,21" fill="#d2f8ff" />
      <polygon points="16,12 20,21 8,21" fill="#8fe6f4" />
      <polygon points="32,12 40,21 28,21" fill="#62d3e8" />
      <polygon points="8,21 20,21 24,38" fill="#26b3cc" />
      <polygon points="20,21 28,21 24,38" fill="#45c8de" />
      <polygon points="28,21 40,21 24,38" fill="#1792ac" />
    </>
  ),
  sword: ({ id }) => (
    <>
      <defs>
        <linearGradient id={id('blade')} x1="0" y1="0" x2="1" y2="0"><stop offset="0" stopColor="#f8fafc" /><stop offset="1" stopColor="#94a3b8" /></linearGradient>
      </defs>
      <g transform="rotate(45 24 24)">
        <polygon points="21,5 24,1.5 27,5 27,29 21,29" fill={`url(#${id('blade')})`} />
        <line x1="24" y1="5" x2="24" y2="29" stroke="#64748b" strokeWidth="0.8" strokeOpacity=".6" />
        <rect x="14.5" y="29" width="19" height="4.2" rx="2.1" fill="#22c55e" />
        <rect x="21.8" y="33" width="4.4" height="9" rx="2" fill="#8a5a2b" />
        <circle cx="24" cy="44.5" r="2.7" fill="#4ade80" />
      </g>
    </>
  ),
  pickaxe: ({ id }) => (
    <>
      <defs>
        <linearGradient id={id('head')} x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#d2f8ff" /><stop offset="1" stopColor="#3cc3dc" /></linearGradient>
      </defs>
      <g transform="rotate(40 24 24)">
        <rect x="21.8" y="13" width="4.4" height="32" rx="2.2" fill="#8a5a2b" />
        <path d="M5 21c4-9 13-13.5 19-13.5S39 12 43 21l-4.6 2.2C35.200 16.800 30 13.500 24 13.500S12.800 16.800 9.600 23.200z" fill={`url(#${id('head')})`} />
      </g>
    </>
  ),
  tnt: () => (
    <>
      <rect x="10" y="14" width="28" height="26" rx="4.5" fill="#d83a34" />
      <rect x="10" y="22" width="28" height="9" fill="#f8fafc" />
      <text x="24" y="29.2" textAnchor="middle" fontSize="8" fontWeight="800" fontFamily="Segoe UI, sans-serif" fill="#1a1a1a">TNT</text>
      <path d="M24 14c0-4 3-5.500 6-6" fill="none" stroke="#a1a1aa" strokeWidth="2" strokeLinecap="round" />
      <circle cx="31.500" cy="7.500" r="2.600" fill="#fde047" />
    </>
  ),
  creeper: ({ id }) => (
    <>
      <defs>
        <linearGradient id={id('face')} x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#7be25a" /><stop offset="1" stopColor="#3aa12a" /></linearGradient>
      </defs>
      <rect x="9" y="9" width="30" height="30" rx="7" fill={`url(#${id('face')})`} />
      <rect x="14.500" y="16" width="7" height="7" rx="1.500" fill="#0b120d" />
      <rect x="26.500" y="16" width="7" height="7" rx="1.500" fill="#0b120d" />
      <path d="M20.500 25.500h7v4h2.500v7h-4.500v-3.500h-3v3.500H18v-7h2.500z" fill="#0b120d" />
    </>
  ),
  ender: ({ id }) => (
    <>
      <defs>
        <radialGradient id={id('ring')} cx=".35" cy=".3" r=".9"><stop offset="0" stopColor="#5eead4" /><stop offset="1" stopColor="#0f766e" /></radialGradient>
      </defs>
      <circle cx="24" cy="24" r="16" fill={`url(#${id('ring')})`} />
      <circle cx="24" cy="24" r="9.500" fill="#06201d" />
      <circle cx="24" cy="24" r="5" fill="#5eead4" />
      <circle cx="22.200" cy="22.200" r="1.600" fill="#ffffff" fillOpacity=".85" />
    </>
  ),
  package: ({ id }) => (
    <>
      <defs>
        <linearGradient id={id('t')} x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#bbf7d0" /><stop offset="1" stopColor="#4ade80" /></linearGradient>
        <linearGradient id={id('l')} x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#22c55e" /><stop offset="1" stopColor="#15803d" /></linearGradient>
        <linearGradient id={id('r')} x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#16a34a" /><stop offset="1" stopColor="#14532d" /></linearGradient>
      </defs>
      <polygon points="9,17 24,25 24,41 9,33" fill={`url(#${id('l')})`} />
      <polygon points="39,17 24,25 24,41 39,33" fill={`url(#${id('r')})`} />
      <polygon points="24,9 39,17 24,25 9,17" fill={`url(#${id('t')})`} />
      <polygon points="18.500,12 33.500,20 29.500,22.200 14.500,14.200" fill="#f0fdf4" fillOpacity=".75" />
    </>
  ),
};

export const ICON_KEYS = ['divine', ...Object.keys(GLYPHS)];

export function InstanceIcon({ icon, size = 48, className = '' }: { icon: string; size?: number; className?: string }) {
  const uid = useId().replace(/:/g, '');
  if (icon === 'divine' || !GLYPHS[icon]) {
    return <img src={emblem} alt="" style={{ width: size, height: size }} className={className} draggable={false} />;
  }
  const id = (name: string) => `ic${uid}${name}`;
  return (
    <svg width={size} height={size} viewBox="0 0 48 48" className={className}>
      <defs>
        <linearGradient id={id('bg')} x1="0" y1="0" x2="1" y2="1"><stop offset="0" stopColor="#1b2b20" /><stop offset="1" stopColor="#0a110c" /></linearGradient>
      </defs>
      <rect width="48" height="48" rx="12" fill={`url(#${id('bg')})`} />
      <rect x=".75" y=".75" width="46.500" height="46.500" rx="11.250" fill="none" stroke="#ffffff" strokeOpacity=".09" strokeWidth="1.500" />
      {GLYPHS[icon]({ id })}
    </svg>
  );
}
