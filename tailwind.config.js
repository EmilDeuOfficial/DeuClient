/** Tailwind-Theme im DeuClient-Stil: Schwarz mit gruenen Akzenten */
module.exports = {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        ink: {
          950: '#050706',
          900: '#0a0d0b',
          850: '#0f1410',
          800: '#141a16',
          700: '#1c241e',
          600: '#26332a',
          500: '#166534',
          400: '#16a34a',
          300: '#4ade80',
          200: '#86efac',
          100: '#dcfce7',
        },
        accent: {
          50: '#f0fdf4',
          100: '#bbf7d0',
          200: '#86efac',
          300: '#4ade80',
          400: '#22c55e',
          500: '#16a34a',
          600: '#15803d',
          700: '#14532d',
        },
      },
      fontFamily: {
        sans: ['"Segoe UI Variable Text"', '"Segoe UI"', 'system-ui', 'sans-serif'],
        display: ['"Segoe UI Variable Display"', '"Segoe UI"', 'system-ui', 'sans-serif'],
        mono: ['"Cascadia Mono"', 'Consolas', 'monospace'],
      },
      boxShadow: {
        accent: '0 0 0 1px rgba(34,197,94,.4)',
        glow: '0 0 0 1px rgba(255,255,255,.08)',
        card: '0 4px 16px rgba(0,0,0,.35)',
      },
      keyframes: {
        'fade-up': { from: { opacity: 0 }, to: { opacity: 1 } },
        'spin-slow': { to: { transform: 'rotate(360deg)' } },
      },
      animation: {
        float: 'none',
        shimmer: 'none',
        ping: 'none',
        bounce: 'none',
        pulse: 'none',
        'spin-slow': 'none',
        'fade-up': 'fade-up .15s ease-out both',
        'pulse-glow': 'none',
      },
    },
  },
  plugins: [],
};
