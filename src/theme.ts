/** Themefarbe: dreht alle Gruentoene der Oberflaeche auf die gewaehlte Farbe (Helligkeit bleibt). */
export const DEFAULT_THEME = '#2eff7a';

export const THEME_PRESETS = ['#2eff7a', '#3b9dff', '#9b6bff', '#ff4d5e', '#ff8a2e', '#ffd12e', '#ff5cb8', '#2ee6d6', '#e8ecef'];

/** Ausgangsfarben; Namen wie in tailwind.config.js (CSS-Variablen --c-<name>) */
const BASE: Record<string, string> = {
  'ink-950': '#050706', 'ink-900': '#0a0d0b', 'ink-850': '#0f1410', 'ink-800': '#141a16', 'ink-700': '#1c241e', 'ink-600': '#26332a',
  'ink-500': '#0f6b34', 'ink-400': '#1fd35f', 'ink-300': '#6bff9e', 'ink-200': '#9fffc0', 'ink-100': '#e6fff0',
  'accent-50': '#f0fff6', 'accent-100': '#d4ffe4', 'accent-200': '#9fffc0', 'accent-300': '#6bff9e', 'accent-400': '#2eff7a',
  'accent-500': '#1fd35f', 'accent-600': '#17a84a', 'accent-700': '#0f7a35',
  text: '#e8f5ec',
};

function toHsv(hex: string): [number, number, number] {
  const [r, g, b] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255);
  const max = Math.max(r, g, b), d = max - Math.min(r, g, b);
  let h = 0;
  if (d > 0) h = max === r ? ((g - b) / d) % 6 : max === g ? (b - r) / d + 2 : (r - g) / d + 4;
  return [(h * 60 + 360) % 360, max === 0 ? 0 : d / max, max];
}

function toRgb(h: number, s: number, v: number): string {
  const f = (n: number) => {
    const k = (n + h / 60) % 6;
    return Math.round(255 * (v - v * s * Math.max(0, Math.min(k, 4 - k, 1))));
  };
  return `${f(5)} ${f(3)} ${f(1)}`;
}

export function applyTheme(color: string | undefined) {
  const hex = color && /^#[0-9a-f]{6}$/i.test(color) ? color : DEFAULT_THEME;
  const [hue, sat] = toHsv(hex);
  const grey = sat < 0.15;
  const shift = grey ? 0 : hue - toHsv(DEFAULT_THEME)[0];
  const root = document.documentElement.style;
  for (const [name, base] of Object.entries(BASE)) {
    const [h, s, v] = toHsv(base);
    root.setProperty(`--c-${name}`, toRgb((h + shift + 360) % 360, grey ? s * 0.08 : s, v));
  }
  // Bilder (Emblem, Schriftzug) mitfaerben
  root.setProperty('--theme-hue', `${Math.round(shift)}deg`);
  root.setProperty('--theme-sat', grey ? '0.1' : '1');
}
