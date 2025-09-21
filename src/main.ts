import { createApp } from 'vue'
import App from './App.vue'
import {createI18n} from "vue-i18n";
import zhCN from './assets/lang/zh_cn.json'
import '@/assets/style/variables.css';
import '@/assets/font/maobi.ttf'

// Vuetify
import 'vuetify/styles'
import { createVuetify } from 'vuetify'

const vuetify = createVuetify()

const i18n = createI18n({
  locale: 'zh-cn',
  messages: {
    'zh-cn': zhCN
  }
})

const app = createApp(App)
app
  .use(i18n)
  .use(vuetify)
  .mount('#app')
