<template>
  <div class="markdown-body" v-html="renderedMarkdown" />
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import MarkdownIt from 'markdown-it'

// 直接静态导入所有 Markdown 文件
import SparkCore from '../assets/markdown/zh_cn/SparkCore.md?raw'
import Lua from '../assets/markdown/zh_cn/lua/Lua.md?raw'
import LuaUse from '../assets/markdown/zh_cn/lua/Use.md?raw'
import LuaMethods from '../assets/markdown/zh_cn/lua/LuaMethods.md?raw'
import LuaModule from '../assets/markdown/zh_cn/lua/Module.md?raw'
import LuaDocs from '../assets/markdown/zh_cn/lua/DocGenerate.md?raw'

const route = useRoute()
const renderedMarkdown = ref('')
const md = new MarkdownIt({ html: true, linkify: true, typographer: true })

// 路由路径与已导入内容的映射
const routeToContentMap = {
  '/': SparkCore,
  '/lua': Lua,
  '/lua/use': LuaUse,
  '/lua/method': LuaMethods,
  '/lua/module': LuaModule,
  '/lua/docs': LuaDocs
}

// 加载 Markdown（静态映射）
const loadMarkdown = (path) => {
  const content = routeToContentMap[path]
  if (content) {
    renderedMarkdown.value = md.render(content)
  } else {
    renderedMarkdown.value = `<h2>未找到对应文档</h2>`
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
