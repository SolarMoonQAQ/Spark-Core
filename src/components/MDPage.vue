<script setup>
import { ref } from 'vue';
import { MdEditor } from 'md-editor-v3';
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css';
import content from '@/md/zh_cn/SparkCore.md?raw'

const text = ref('Hello Editor!');
</script>

<template>
  <v-card elevation="16" class="d-flex align-center justify-center page" width="1400" height="1000">
    <MdPreview :model-value="content" class="md" />
  </v-card>
</template>

<style scoped>
.page {
  background: linear-gradient(135deg, #fdfcf7, #f8f5e9);
  padding: 20px;
  border-radius: 12px;
  box-sizing: border-box;
}
.md {
  width: 100%;
  height: 100%;
}
</style>
