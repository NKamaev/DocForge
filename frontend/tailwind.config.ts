import type { Config } from 'tailwindcss'

const config: Config = {
  darkMode: ['selector', '[data-theme="dark"]'],
  content: [
    './index.html',
    './src/**/*.{js,ts,jsx,tsx}',
  ],
  theme: {
    extend: {
      colors: {
        brand: 'var(--brand)',
        'brand-soft': 'var(--brand-soft)',
        'brand-softer': 'var(--brand-softer)',
        'brand-ink': 'var(--brand-ink)',
        'brand-line': 'var(--brand-line)',
        
        accent: {
          amber: 'var(--accent-amber)',
          'amber-soft': 'var(--accent-amber-soft)',
          green: 'var(--accent-green)',
          'green-soft': 'var(--accent-green-soft)',
          red: 'var(--accent-red)',
          'red-soft': 'var(--accent-red-soft)',
          violet: 'var(--accent-violet)',
        },
        
        bg: 'var(--bg)',
        'bg-soft': 'var(--bg-soft)',
        'bg-canvas': 'var(--bg-canvas)',
        
        surface: 'var(--surface)',
        'surface-2': 'var(--surface-2)',
        
        line: 'var(--line)',
        'line-2': 'var(--line-2)',
        'line-soft': 'var(--line-soft)',
        
        ink: {
          1: 'var(--ink-1)',
          2: 'var(--ink-2)',
          3: 'var(--ink-3)',
          4: 'var(--ink-4)',
        },
      },
      
      fontFamily: {
        ui: ['var(--font-ui)'],
        doc: ['var(--font-doc)'],
        mono: ['var(--font-mono)'],
        display: ['var(--font-display)'],
      },
      
      borderRadius: {
        xs: 'var(--radius-xs)',
        sm: 'var(--radius-sm)',
        DEFAULT: 'var(--radius)',
        lg: 'var(--radius-lg)',
        xl: 'var(--radius-xl)',
      },
      
      boxShadow: {
        card: 'var(--shadow-card)',
        page: 'var(--shadow-page)',
        pop: 'var(--shadow-pop)',
      },
      
      spacing: {
        'd-row': 'var(--d-row)',
        'd-pad': 'var(--d-pad)',
        'd-gap': 'var(--d-gap)',
      },
    },
  },
  plugins: [],
}

export default config
