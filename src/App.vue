<script setup lang="ts">
import Header from "@/components/Header.vue";
import Sidebar from "@/components/Sidebar.vue";
import MDPage from "@/components/MDPage.vue";
</script>

<template>
  <v-app>
    <!-- 全局背景 -->
    <v-main class="app-background">
      <v-container fluid>
        <v-row no-gutters>
          <v-col class="sidebar">
            <!-- 顶部 Header 占位 -->
            <v-sheet class="header-placeholder" >
              <Header class="header" />
              <!-- 分隔线 -->
              <v-divider inset class="divider" thickness="4" />
            </v-sheet>

            <!-- 侧边菜单 -->
            <Sidebar class="sidebar-body " />
          </v-col>

          <v-col class="d-flex align-center justify-center mdpage">
            <v-card class="mdpage-wrapper" flat>
              <MDPage />
            </v-card>
          </v-col>


        </v-row>
      </v-container>
    </v-main>
  </v-app>
</template>

<style scoped>

.app-background {
  padding: 0;
  min-height: 100vh;
  background:
    radial-gradient(rgba(0,0,0,0.05) 1px, transparent 1px),
    linear-gradient(135deg, #fdfcf7 0%, #f8f5e9 50%, #fdfcf7 100%);
  background-size: 3px 3px, 100% 100%;
  box-shadow: inset 0 0 30px rgba(0,0,0,0.35);
}

.v-container {
  padding: 0;
}

.header {
  z-index: 1000;
}

.sidebar {
  display: flex;
  flex-direction: column;
  height: 100vh;
  width: 275px;
  flex: 0 0 275px;
}


.header-placeholder {
  flex: 0 0 150px;
  background-color: var(--sidebar-color);

  display: flex;
  align-items: center;
  justify-content: center;

  position: relative; /* 关键：让 divider 以它为定位参照 */
}

.divider {
  position: absolute;
  bottom: 5%;
  transform: translateX(-20%);

  height: 6px;
  width: 80%;
  box-shadow:
    inset 0 2px rgba(0, 0, 0, 0.2),
    inset 0 -2px rgba(255, 255, 255, 0.1);
}

.sidebar-body {
  flex: 1; /* 占剩余空间 */
  background-color: var(--sidebar-color);
}

.mdpage {



}

</style>
