<template>
  <div class="markdown-body" v-html="renderedMarkdown"></div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import MarkdownIt from 'markdown-it'

const route = useRoute()
const renderedMarkdown = ref('')
const md = new MarkdownIt({ html: true, linkify: true, typographer: true })

// 路由路径与 Markdown 文件名映射（去掉开头的 /，方便拼接）
const routeToFileMap = {
  '/': 'SparkCore.md',
  '/lua': 'lua/Lua.md',
  '/lua/use': 'lua/Use.md',
  '/lua/method': 'lua/LuaMethods.md',
  '/lua/module': 'lua/Module.md',
  '/lua/docs': 'lua/DocGenerate.md'
}

// 加载 Markdown 文件（从 public 目录）
const loadMarkdown = async (path) => {
  const fileName = routeToFileMap[path]
  if (!fileName) {
    renderedMarkdown.value = '<h2>未找到对应文档</h2>'
    return
  }

  try {
    // 如果部署在 GitHub Pages 仓库子路径，需要加上仓库名
    const base = import.meta.env.BASE_URL  // Vite 会自动替换成正确的路径
    const res = await fetch(`${base}component/zh_cn/${fileName}`)
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    const text = await res.text()
    renderedMarkdown.value = md.render(text)
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
