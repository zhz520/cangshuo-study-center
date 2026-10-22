/** @type {import('tailwindcss').Config} */
export default {
    darkMode: 'class',
    content: [
        "./index.html",
        "./src/**/*.{vue,js,ts,jsx,tsx}",
    ],
    theme: {
        extend: {
            colors: {
                primary: 'var(--primary)',
                'primary-hover': '#4338ca',
                'bg-body': 'var(--bg-body)',
                'bg-card': 'var(--bg-card)',
            }
        },
    },
    plugins: [],
}
