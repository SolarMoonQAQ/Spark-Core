<template>
  <div class="main-content">
    <transition name="page-flip" mode="out-in">
      <router-view :key="$route.fullPath" />
    </transition>

  </div>
</template>


<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import f1 from "@/assets/sounds/flip1.mp3"
import f2 from "@/assets/sounds/flip2.mp3"
import f3 from "@/assets/sounds/flip3.mp3"

const router = useRouter()

let lastPlayTime = 0
const cooldown = 100 // 毫秒

const playEnterSound = () => {
  const now = Date.now()
  if (now - lastPlayTime < cooldown) return
  lastPlayTime = now

  const sounds = [f1, f2, f3]
  const src = sounds[Math.floor(Math.random() * sounds.length)]

  const audio = new Audio(src)
  audio.volume = 0.9 + Math.random() * 0.1
  audio.playbackRate = 0.95 + Math.random() * 0.1
  audio.play().catch(err => console.warn('音效播放失败：', err))
}

onMounted(() => {
  router.afterEach(() => {
    playEnterSound()
  })
})
</script>

<style lang="scss" scoped>
.main-content {
  @import "../style.scss";

  margin-left: $sidebarWidth;
  min-height: 100vh;
  padding: 2rem;
  background: rgba(253, 252, 247, 0.6);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  box-shadow: inset 0 0 30px rgba(0, 0, 0, 0.05);
}

.page-flip-enter-active,
.page-flip-leave-active {
  transition: transform 0.6s ease, opacity 0.6s ease;
  transform-style: preserve-3d;
}
.page-flip-enter-from {
  transform: rotateY(90deg);
  opacity: 0;
}
.page-flip-enter-to {
  transform: rotateY(0deg);
  opacity: 1;
}
.page-flip-leave-from {
  transform: rotateY(0deg);
  opacity: 1;
}
.page-flip-leave-to {
  transform: rotateY(-90deg);
  opacity: 0;
}


</style>
