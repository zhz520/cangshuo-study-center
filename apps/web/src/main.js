import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './style.css'
import './assets/theme.css'
import App from './App.vue'
import router from './router'
import 'nprogress/nprogress.css' // Global Progress Bar


const app = createApp(App)

// --- STALE SESSION KILLER REMOVED ---
// ------------------------------------

app.use(createPinia())
app.use(router)

app.mount('#app')
