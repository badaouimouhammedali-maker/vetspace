/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        // brand.green/navy/gray are var-backed so the whole app restyles when the
        // user changes their theme; defaults (in index.css) are the brand colors.
        // `<alpha-value>` lets Tailwind opacity modifiers (bg-brand-green/10) work.
        brand: {
          green: 'rgb(var(--color-primary) / <alpha-value>)',
          'green-hover': '#0B5D57',
          navy: 'rgb(var(--color-secondary) / <alpha-value>)',
          gray: 'rgb(var(--color-tertiary) / <alpha-value>)',
        },
        primary: 'rgb(var(--color-primary) / <alpha-value>)',
        secondary: 'rgb(var(--color-secondary) / <alpha-value>)',
        tertiary: 'rgb(var(--color-tertiary) / <alpha-value>)',

        // Surfaces. Named rather than raw white/gray-50 so "what sits on what" is
        // stated in the markup: bg-canvas is the page, bg-surface is the thing on it.
        surface: '#FFFFFF',
        canvas: '#F9FAFB',

        // Semantic status trio. Deliberately NOT theme-var-backed: a user's chosen
        // accent must never be able to make "danger" look like "success". Use the
        // colour for text/border and `/10` for the soft fill (bg-danger/10).
        success: '#0F8A5F',
        warning: '#B45309',
        danger: '#DC2626',
        // The favourite star. Not `warning` — that amber means "careful", and a starred
        // session is a good thing. Named for its role so it cannot drift back to a raw
        // `text-yellow-400`.
        star: '#F5B301',
      },
      borderColor: {
        // Only `border-subtle`, not a full colour — so it cannot be used as a fill.
        subtle: 'rgb(229 231 235 / 0.7)',
      },
      // Hairlines that must not affect layout — image frames, inset outlines — use a
      // ring rather than a border. Same colour as `border-subtle` so the two read as one
      // line weight wherever they meet.
      ringColor: {
        subtle: 'rgb(229 231 235 / 0.7)',
      },
      borderRadius: {
        sm: '6px',
        md: '10px',
        lg: '14px',
        xl: '20px',
      },
      boxShadow: {
        // Navy-tinted rather than neutral black: a pure-black shadow over this warm
        // grey canvas reads as dirt.
        subtle: '0 1px 2px rgb(18 53 91 / 0.06)',
        card: '0 1px 3px rgb(18 53 91 / 0.08), 0 4px 12px rgb(18 53 91 / 0.05)',
        pop: '0 8px 30px rgb(18 53 91 / 0.12)',
      },
      fontSize: {
        // Weight and tracking baked into the scale, so a heading cannot be
        // half-applied. `text-caption` pairs with `text-gray-500` for the muted role.
        display: ['30px', { lineHeight: '36px', fontWeight: '700', letterSpacing: '-0.02em' }],
        h1: ['24px', { lineHeight: '32px', fontWeight: '700' }],
        h2: ['18px', { lineHeight: '28px', fontWeight: '600' }],
        body: ['15px', { lineHeight: '24px' }],
        caption: ['13px', { lineHeight: '20px' }],
      },
      fontFamily: {
        sans: ['Manrope', 'ui-sans-serif', 'system-ui', 'sans-serif'],
      },
      keyframes: {
        // Enter animations only. Exit animations need the element to stay mounted
        // while they play, which is a whole state machine for very little gain.
        'modal-in': {
          from: { opacity: '0', transform: 'scale(0.96)' },
          to: { opacity: '1', transform: 'scale(1)' },
        },
        'toast-in': {
          from: { opacity: '0', transform: 'translateY(8px)' },
          to: { opacity: '1', transform: 'translateY(0)' },
        },
        'badge-in': {
          from: { transform: 'scale(0)' },
          to: { transform: 'scale(1)' },
        },
      },
      animation: {
        'modal-in': 'modal-in 150ms ease-out',
        'toast-in': 'toast-in 150ms ease-out',
        'badge-in': 'badge-in 200ms cubic-bezier(0.34, 1.56, 0.64, 1)',
      },
      // No `spacing` extension on purpose: Tailwind's default scale is already the
      // 4px grid. Adding one-off values is how a rhythm stops being a rhythm.
    },
  },
  plugins: [],
};
