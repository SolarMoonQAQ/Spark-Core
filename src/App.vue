<template>
  <Sidebar />   <!-- 这里调用 Sidebar 组件 -->
  <Page />
</template>

<script setup>
import { watch } from 'vue'
import { useI18n } from 'vue-i18n'
import Sidebar from '@/views/Sidebar.vue'  // 注意首字母大写
import Page from '@/views/Page.vue'

/*随时根据语言状态刷新浏览标题*/
const { t, locale } = useI18n()
const updateTitle = () => {
  document.title = t('spark_core')
}
watch(locale, updateTitle, { immediate: true })

</script>

<style>
@font-face {
  font-family: 'maobi';
  src: url('@/assets/fonts/maobi.ttf') format('truetype');
  font-display: swap;
}
</style>
