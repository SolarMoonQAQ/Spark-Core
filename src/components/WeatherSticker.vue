<template>
  <div class="weather-icon" v-html="svgCode"></div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'

// 固定白天，也可以根据时间判断 day/night
const type = 'day'

// 所有天气代码
const codes = Object.keys(window.config.weathericonname[type])

// 当前索引
const index = ref(0)

// 当前 SVG
const svgCode = computed(() => {
  const code = codes[index.value]
  return window.config.weathericon[type]?.[code] || ''
})

let timer
onMounted(() => {
  timer = setInterval(() => {
    // 顺序轮换
    index.value = (index.value + 1) % codes.length
  }, 3000) // 每 3 秒切换一次
})

onBeforeUnmount(() => {
  clearInterval(timer)
})
</script>

<style>
@import '/WeatherIcon.css';
.weather-icon {
  width: 150px;
  height: 150px;
}
</style>
