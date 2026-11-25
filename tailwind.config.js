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
          500: '#0f6b34',
          400: '#1fd35f',
          300: '#6bff9e',
          200: '#9fffc0',
          100: '#e6fff0',
        },
        accent: {
          50: '#f0fff6',
          100: '#d4ffe4',
          200: '#9fffc0',
          300: '#6bff9e',
          400: '#2eff7a',
          500: '#1fd35f',
          600: '#17a84a',
          700: '#0f7a35',
        },
      },
      fontFamily: {
        sans: ['"Segoe UI Variable Text"', '"Segoe UI"', 'system-ui', 'sans-serif'],
        display: ['"Segoe UI Variable Display"', '"Segoe UI"', 'system-ui', 'sans-serif'],
        mono: ['"Cascadia Mono"', 'Consolas', 'monospace'],
      },
      boxShadow: {
        accent: '0 0 0 1px rgba(46,255,122,.4)',
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
