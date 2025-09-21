<script setup lang="ts">
</script>

<template>
  <v-card class="card" flat>
    <!-- 顶部分隔线 -->
    <v-divider class="divider divider-top" thickness="4" />

    <!-- 标题文字 -->
    <div class="text">
      {{ $t('title') }}
    </div>

    <!-- 底部分隔线 -->
    <v-divider class="divider divider-bottom" thickness="4" />

    <!-- 右侧梯形装饰 -->
    <div class="side"></div>
    <div class="side-border"></div>
  </v-card>
</template>

<style scoped lang="scss">
.card {
  width: 275px;
  height: 100px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border-radius: 0;
  background:
    radial-gradient(rgba(0,0,0,0.5) 1px, transparent 1px),
    linear-gradient(135deg, #fdfcf7 0%, #f8f5e9 50%, #fdfcf7 100%);
  background-size: auto, 3px 3px;
  user-select: none;
  box-shadow: 8px 8px 12px rgba(0,0,0,0.6);
  opacity: 0.95;
  overflow: visible;
}

.text {
  font-family: 'maobi', cursive;
  font-size: 36px;
  font-weight: 400;
  color: black;
}

.side {
  position: absolute;
  top: 0;
  right: -10px;
  width: 10px;
  height: 100%;
  background: linear-gradient(135deg, #e0ddd0 0%, #d6d3c6 50%, #e0ddd0 100%);
  clip-path: polygon(0 0, 100% 5%, 100% 95%, 0 100%);
  box-shadow: -4px 0 60px rgba(0, 0, 0, 0.25);
}

.divider-top {
  position: absolute;
  width: 100%;
  top: 5px;        /* 贴顶部 */
}

.divider-bottom {
  position: absolute;
  width: 100%;
  bottom: 5px;     /* 贴底部 */
}

</style>
