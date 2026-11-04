import { createApp } from 'vue'
import App from './App.vue'
import './style.css'
import './mobile.css'
import './reading-mobile.css'
import './night-mobile.css'
import router from './router'

createApp(App).use(router).mount('#app')

