import { createI18n } from 'vue-i18n'
import zhCN from '@/assets/lang/zh_cn.json'
import enUS from '@/assets/lang/zh_cn.json'

const savedLang = localStorage.getItem('lang') || 'zh-cn'

const i18n = createI18n({
    legacy: true, // 开启 legacy 模式，才能用 $t
    globalInjection: true,
    locale: savedLang,
    fallbackLocale: 'zh-cn',
    messages: {
        'zh-cn': zhCN,
        'en-us': enUS
    }
})

export default i18n
