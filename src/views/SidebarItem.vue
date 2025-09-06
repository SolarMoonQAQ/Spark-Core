<template>
  <div class="menu-item" :class="{ 'has-ribbon': level > 1 }"> <!-- 添加动态类 -->
    <!-- 当前菜单项 -->
    <div
        class="item"
        :style="{ '--level': level }"
        :class="{ open: item.open }"
        @click="toggle"
        @mouseenter="playHoverSound"
    >
      <div class="title">{{ item.label }}</div>
    </div>

    <!-- 子菜单 -->
    <transition name="slide">
      <div v-show="item.open" class="submenu" :class="{ 'has-ribbon': level > 0 }">
        <template v-for="(child, idx) in item.children" :key="idx">
          <MenuItem v-if="child.children" :item="child" :level="level + 1" />
          <router-link
              v-else
              :to="child.to"
              class="item sub-item"
              :style="{ '--level': level + 1 }"
              @mouseenter="playHoverSound"
          >
            {{ child.label }}
          </router-link>
        </template>
      </div>
    </transition>
  </div>
</template>

<script setup>
import MenuItem from './SidebarItem.vue'
import woodenFishSound from '@/assets/sounds/wooden_fish.mp3'

const props = defineProps({
  item: Object,
  level: { type: Number, default: 1 }
})

const toggle = () => {
  props.item.open = !props.item.open
  if (!props.item.open) closeChildren(props.item)
}

/*关闭父级，关闭子集*/
const closeChildren = (menu) => {
  if (menu.children) {
    menu.children.forEach(child => {
      child.open = false
      closeChildren(child)
    })
  }
}

/*播放鼠标悬至音效*/
let lastPlayTime = 0
const cooldown = 100
const playHoverSound = () => {
  const now = Date.now()
  if (now - lastPlayTime < cooldown) return
  lastPlayTime = now

  const audio = new Audio(woodenFishSound)
  audio.volume = 0.5 + Math.random() * 0.1
  audio.playbackRate = 0.9 + Math.random() * 0.1
  audio.play().catch(err => console.warn('音效播放失败：', err))
}
</script>

<style lang="scss" scoped>
@import "@/style.scss";

.item {
  font-size: 16px !important;
  color: #fff !important;
  padding-left: calc(var(--level) * 2rem) !important;
  transition: transform 0.25s ease,
              font-weight 0.25s ease,
              background-color 0.9s cubic-bezier(0.1, 0, 0.2, 1),
              text-shadow 0.25s ease !important;

  background-color: hsl(4, calc(54% + var(--level) * 5%), calc(23% + var(--level) * 3%)) !important;

  &:hover {
    background: lighten(darken($sidebar-color, 5%), 70%) !important;
    text-shadow: 3px 3px 3px rgba(0, 0, 0, 0.5);
  }
}

.item.open {
  font-weight: bold !important;
  transform: scale(calc(1.06 - 0.01 * var(--level)));
  box-shadow: 5px 5px 7px rgba(0, 0, 0, 0.4);
  text-shadow: 3px 3px 3px rgba(0, 0, 0, 0.5);
}

.menu-item {
  position: relative;
  .has-ribbon::after {
    position: absolute;
    left: 100px;
    transform: translateX(-8px); // 你可以根据需要微调
    width: 60px;
    height: 100%;
    background-color: hsl(125, 70%, calc(40% + (var(--level) - 1) * 5%));
    box-shadow: 2px 0 5px rgba(0, 0, 0, 0.3);
  }
}

.slide-enter-active,
.slide-leave-active {
  transition: all 0.3s ease;
  overflow: hidden;
}
.slide-enter-from,
.slide-leave-to {
  max-height: 0;
}
.slide-enter-to,
.slide-leave-from {
  max-height: 500px; /* 这里设一个足够大的值 */
}


</style>
