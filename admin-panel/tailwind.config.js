/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        primary: {
          50: '#151B24',
          100: '#1B2432',
          200: '#242F40',
          300: '#34455E',
          400: '#4E9CFF',
          500: '#39CDBE',
          600: '#39CDBE',
          700: '#4E9CFF',
          800: '#F2A74B',
          900: '#E06B5B',
        },
      },
    },
  },
  plugins: [],
}
