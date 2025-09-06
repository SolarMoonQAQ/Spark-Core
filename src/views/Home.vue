<template>
  <div class="home-page">
    <h1 class="ui header"></h1>
    <div class="markdown-body" v-html="renderedMarkdown" />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import MarkdownIt from 'markdown-it'
import rawMarkdown from '@/component/zh_cn/SparkCore.md?raw' // ✅ 确保路径正确

const md = new MarkdownIt({
  html: true,
  linkify: true,
  typographer: true
})

const renderedMarkdown = ref(md.render(rawMarkdown))
</script>

<style scoped>
.markdown-body img {
  max-width: 100%;
  height: auto;
  display: block;
  margin: 1rem auto;
  border-radius: 4px;
  box-shadow: 0 0 6px rgba(0, 0, 0, 0.1);
}

</style>
