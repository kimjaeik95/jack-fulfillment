import { createApp } from 'vue'
import App from './App.vue'
import router from './router/index.js'
import { pinia } from './stores/index.js'
import './assets/styles.css'

// 저장된 테마 적용 (기본 라이트)
document.documentElement.dataset.theme = localStorage.getItem('wms-admin-theme') || 'light'

createApp(App).use(pinia).use(router).mount('#app')
