/** Tailwind-Theme im DeuClient-Stil: Schwarz mit gruenen Akzenten */
module.exports = {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // die wenigen Smaragd-Klassen folgen ebenfalls der Themefarbe
        emerald: {
          200: 'rgb(var(--c-accent-200) / <alpha-value>)',
          300: 'rgb(var(--c-accent-300) / <alpha-value>)',
          400: 'rgb(var(--c-accent-400) / <alpha-value>)',
          500: 'rgb(var(--c-accent-500) / <alpha-value>)',
        },
        ink: {
          950: 'rgb(var(--c-ink-950) / <alpha-value>)',
          900: 'rgb(var(--c-ink-900) / <alpha-value>)',
          850: 'rgb(var(--c-ink-850) / <alpha-value>)',
          800: 'rgb(var(--c-ink-800) / <alpha-value>)',
          700: 'rgb(var(--c-ink-700) / <alpha-value>)',
          600: 'rgb(var(--c-ink-600) / <alpha-value>)',
          500: 'rgb(var(--c-ink-500) / <alpha-value>)',
          400: 'rgb(var(--c-ink-400) / <alpha-value>)',
          300: 'rgb(var(--c-ink-300) / <alpha-value>)',
          200: 'rgb(var(--c-ink-200) / <alpha-value>)',
          100: 'rgb(var(--c-ink-100) / <alpha-value>)',
        },
        accent: {
          50: 'rgb(var(--c-accent-50) / <alpha-value>)',
          100: 'rgb(var(--c-accent-100) / <alpha-value>)',
          200: 'rgb(var(--c-accent-200) / <alpha-value>)',
          300: 'rgb(var(--c-accent-300) / <alpha-value>)',
          400: 'rgb(var(--c-accent-400) / <alpha-value>)',
          500: 'rgb(var(--c-accent-500) / <alpha-value>)',
          600: 'rgb(var(--c-accent-600) / <alpha-value>)',
          700: 'rgb(var(--c-accent-700) / <alpha-value>)',
        },
      },
      fontFamily: {
        sans: ['"Segoe UI Variable Text"', '"Segoe UI"', 'system-ui', 'sans-serif'],
        display: ['"Segoe UI Variable Display"', '"Segoe UI"', 'system-ui', 'sans-serif'],
        mono: ['"Cascadia Mono"', 'Consolas', 'monospace'],
      },
      boxShadow: {
        accent: '0 0 0 1px rgb(var(--c-accent-400) / .4)',
        glow: '0 0 0 1px rgba(255,255,255,.08)',
        card: '0 4px 16px rgba(0,0,0,.35)',
      },
      keyframes: {
        'fade-up': { from: { opacity: 0, transform: 'translateY(6px)' }, to: { opacity: 1, transform: 'none' } },
        'spin-slow': { to: { transform: 'rotate(360deg)' } },
      },
      animation: {
        float: 'none',
        shimmer: 'none',
        ping: 'none',
        bounce: 'none',
        pulse: 'none',
        'spin-slow': 'none',
        'fade-up': 'fade-up .2s ease-out both',
        'pulse-glow': 'none',
      },
    },
  },
  plugins: [],
};
