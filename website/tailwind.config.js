// tailwind.config.js
const colors = require('tailwindcss/colors');

/** @type {import('tailwindcss').Config} */
module.exports = {
  important: true,
  content: ['./src/**/*.{js,jsx,ts,tsx}', './docs/**/*.mdx'],
  darkMode: ['class', '[data-theme="dark"]'], // hooks into docusaurus' dark mode settings
  theme: {
    extend: {
      colors: {
        primary: {
          DEFAULT: colors.red[600],
          ...colors.red,
        },
        accent: {
          DEFAULT: colors.amber[500],
          ...colors.amber,
        },
      },
    },
  },
  plugins: [],
};
