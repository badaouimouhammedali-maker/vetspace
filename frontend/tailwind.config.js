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
      },
      fontFamily: {
        sans: ['Manrope', 'ui-sans-serif', 'system-ui', 'sans-serif'],
      },
    },
  },
  plugins: [],
};
