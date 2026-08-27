import { createApp } from 'vue'
import { FileText, Image, FileSpreadsheet, ScanText, Wrench, Video, Music, Sparkles, Search, Upload, ArrowRight } from 'lucide-vue-next'
import './style.css'
import App from './App.vue'

export const icons = { FileText, Image, FileSpreadsheet, ScanText, Wrench, Video, Music, Sparkles, Search, Upload, ArrowRight }
createApp(App).mount('#app')
