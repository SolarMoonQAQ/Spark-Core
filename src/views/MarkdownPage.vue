<!-- MarkdownPage.vue -->
<template>
  <div class="markdown-body" v-html="renderedMarkdown" />
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import MarkdownIt from 'markdown-it'

const route = useRoute()
const renderedMarkdown = ref('')
const md = new MarkdownIt({ html: true, linkify: true, typographer: true })

// 路由路径与 Markdown 文件名映射
const routeToFileMap = {
  '/': 'SparkCore.md',
  '/lua': 'lua/Lua.md',
  '/lua/use': 'lua/Use.md',
  '/lua/method': 'lua/LuaMethods.md',
  '/lua/module': 'lua/Module.md',
  '/lua/docs': 'lua/DocGenerate.md'
}

// 加载 Markdown 文件
const loadMarkdown = async (path) => {
  const fileName = routeToFileMap[path]

  try {
    const rawModule = await import(/* @vite-ignore */ new URL(`../assets/markdown/zh_cn/${fileName}`, import.meta.url).href + '?raw')
    renderedMarkdown.value = md.render(rawModule.default)
  } catch (err) {
    renderedMarkdown.value = `<h2>加载失败：${fileName}</h2><p>${err}</p>`
  }
}

// 初始加载
loadMarkdown(route.path)

// 监听路由变化
watch(() => route.path, (newPath) => {
  loadMarkdown(newPath)
})
</script>

<style scoped>
.markdown-body {
  font-size: 16px;
  line-height: 1.6;
  color: #333;
  background: #fff;
  padding: 2rem;
  border-radius: 8px;
  box-shadow: 0 0 10px rgba(0,0,0,0.05);
}
.markdown-body img {
  max-width: 100%;
  height: auto;
  display: block;
  margin: 1rem auto;
}
</style>
