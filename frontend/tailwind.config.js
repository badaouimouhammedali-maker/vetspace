/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        // Every colour resolves to a token in index.css — there are no hexes here.
        // `<alpha-value>` lets Tailwind opacity modifiers (bg-accent/10) work, which is
        // how all tints are made: there is no "light accent" colour, only accent/10.

        // Emerald — "interact with this".
        accent: 'rgb(var(--accent) / <alpha-value>)',
        'accent-hover': 'rgb(var(--accent-hover) / <alpha-value>)',
        'accent-pressed': 'rgb(var(--accent-pressed) / <alpha-value>)',
        // Required for emerald text/links/icons on navy: the accent itself is 2.3:1
        // there. Using `text-accent` on a navy surface is a bug, not a style choice.
        'accent-on-dark': 'rgb(var(--accent-on-dark) / <alpha-value>)',

        brand: {
          navy: 'rgb(var(--brand-navy) / <alpha-value>)',
          'navy-deep': 'rgb(var(--brand-navy-deep) / <alpha-value>)',
        },

        // Surfaces. Named rather than raw white/gray-50 so "what sits on what" is
        // stated in the markup: bg-canvas is the page, bg-surface is the thing on it.
        surface: 'rgb(var(--bg-card) / <alpha-value>)',
        canvas: 'rgb(var(--bg-app) / <alpha-value>)',

        // Type. `ink` rather than `text-primary` so the class reads `text-ink` instead
        // of the stuttering `text-text-primary`.
        ink: 'rgb(var(--text-primary) / <alpha-value>)',
        'ink-muted': 'rgb(var(--text-secondary) / <alpha-value>)',
        'on-navy': 'rgb(var(--text-on-navy) / <alpha-value>)',
        'on-navy-muted': 'rgb(var(--text-on-navy-muted) / <alpha-value>)',

        // Fresh green — "this went well". `success` is a fill/bar/icon colour only
        // (3.3:1 on white); any green *text* must use `success-text` (5.4:1).
        success: 'rgb(var(--success) / <alpha-value>)',
        'success-text': 'rgb(var(--success-text) / <alpha-value>)',

        // Semantic states. Deliberately NOT theme-var-backed: a user's chosen accent
        // must never be able to make "danger" look like "success". Use the colour for
        // text/border and `/10` for the soft fill (bg-danger/10).
        warning: 'rgb(var(--warning) / <alpha-value>)',
        danger: 'rgb(var(--danger) / <alpha-value>)',
        star: 'rgb(var(--star) / <alpha-value>)',
      },
      borderColor: {
        // Preflight gives every element `border-color: #e5e7eb`, so any `border` written
        // without a colour class silently paints Tailwind's grey instead of ours. Making
        // the token the default closes that hole rather than relying on call sites.
        DEFAULT: 'rgb(var(--border) / <alpha-value>)',
        // Only `border-subtle`/`border-strong`, not full colours — so they cannot be
        // used as fills. `strong` is for hover and focus borders only.
        subtle: 'rgb(var(--border) / <alpha-value>)',
        strong: 'rgb(var(--border-strong) / <alpha-value>)',
      },
      // Hairlines that must not affect layout — image frames, inset outlines — use a
      // ring rather than a border. Same colour as `border-subtle` so the two read as one
      // line weight wherever they meet.
      ringColor: {
        subtle: 'rgb(var(--border) / <alpha-value>)',
      },
      // The charter's tint ladder. Tailwind's default opacity scale jumps 5 → 10 → 20,
      // so the 6% hover and 18% active-nav tints would otherwise have to be written as
      // arbitrary values at every call site.
      opacity: {
        6: '0.06',
        8: '0.08',
        18: '0.18',
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
        subtle: '0 1px 2px rgb(var(--brand-navy) / 0.06)',
        card: '0 1px 3px rgb(var(--brand-navy) / 0.08), 0 4px 12px rgb(var(--brand-navy) / 0.05)',
        pop: '0 8px 30px rgb(var(--brand-navy) / 0.12)',
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
