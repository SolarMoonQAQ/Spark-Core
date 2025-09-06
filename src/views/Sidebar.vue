<template>
  <div class="sidebar-container">
    <!-- 侧边栏标题 -->
    <div class="sidebar-header">
      {{ $t('spark_core') }}
    </div>

    <!-- 菜单 -->
    <div class="ui vertical menu">
      <SidebarItem
          v-for="(menu, index) in menus"
          :key="index"
          :item="menu"
      />
    </div>
  </div>
</template>

<script setup>
import { reactive } from 'vue'
import SidebarItem from '@/views/SidebarItem.vue'

const menus = reactive([
  {
    label: '首页',
    open: false,
    children: [
      { label: '简介', to: '/' },
    ]
  },
  {
    label: 'Lua脚本',
    open: false,
    children: [
      { label: '简介', to: '/lua' },
      { label: '快速入门', to: '/lua/use' },
      { label: '添加拓展方法', to: '/lua/method' },
      { label: '模块', to: '/lua/module' },
      { label: '注释文档生成', to: '/lua/docs' },
    ]
  },
  {
    label: '联系',
    open: false,
    children: [
      { label: '邮箱', to: '/contact/email' },
      { label: '电话', to: '/contact/phone' }
    ]
  }
])
</script>

<style lang="scss" scoped>
@import "@/style.scss";

.sidebar-container {
  position: fixed;
  top: 0;
  left: 0;
  height: 100vh;
  width: $sidebarWidth;
  background: $sidebar-color;
  box-shadow: 5px 0 5px rgba(0, 0, 0, 0.4);
  overflow: visible;
  z-index: 2000;
}

/*菜单列整体样式*/
.ui.vertical.menu {
  transform: translateY(16px);
  width: 100%;
  border: none;
}


.sidebar-header {
  position: relative;
  font-family: 'maobi', cursive;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36px;
  font-weight: 400;
  color: black;
  height: 100px;
  user-select: none;
  transform: translate3d(0px, 16px, 0px);
  z-index: 1;

  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0px;
    width: calc(100% + 10px);
    height: 100%;
    border-top-right-radius: 12px;
    border-bottom-right-radius: 12px;
    box-shadow: 2px 2px 6px rgba(0,0,0,0.15);
    background:
        radial-gradient(rgba(0,0,0,0.5) 1px, transparent 1px),
        linear-gradient(135deg, #fdfcf7 0%, #f8f5e9 50%, #fdfcf7 100%);
    background-size: auto, 3px 3px;
    z-index: -1;
  }

  &::after {
    content: '';
    position: absolute;
    top: 0;
    left: calc(100% - 40px);
    width: 40px;
    height: 100%;
    background: linear-gradient(135deg, #fdfcf7 0%, #f8f5e9 50%, #fdfcf7 100%);
    clip-path: polygon(100% 0, 0 20%, 0 80%, 100% 100%);
    z-index: -1;
  }
}
</style>
