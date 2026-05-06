import type { Config } from 'tailwindcss'

export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      fontFamily: {
        sans: ['Geist', 'ui-sans-serif', 'system-ui', '-apple-system', 'sans-serif'],
        mono: ['Geist Mono', 'ui-monospace', 'Menlo', 'monospace'],
      },
      colors: {
        ink: {
          50:  '#f7f7f7',
          100: '#ededed',
          200: '#dcdcdc',
          300: '#bebebe',
          400: '#8e8e8e',
          500: '#6f6f6f',
          600: '#525252',
          700: '#3d3d3d',
          800: '#1f1f1f',
          900: '#141414',
          950: '#0a0a0a',
        },
        accent: {
          50:  '#eef9f3',
          100: '#d4f1e0',
          500: '#0d8f63',
          600: '#0b7a55',
          700: '#0a6447',
        },
      },
      borderRadius: {
        xl:  '0.875rem',
        '2xl': '1.25rem',
        '3xl': '1.75rem',
      },
      boxShadow: {
        soft: '0 1px 0 rgba(0,0,0,0.02), 0 6px 24px -8px rgba(0,0,0,0.06)',
        card: '0 1px 0 rgba(0,0,0,0.02), 0 12px 48px -16px rgba(0,0,0,0.08)',
      },
    },
  },
  plugins: [],
} satisfies Config
