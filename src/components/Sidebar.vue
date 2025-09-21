<template>
  <v-card class="card" elevation="24">
    <v-list
      opened="{{ open }}"
      open-strategy="multiple"
      :mandatory=true
      class="bar"
    >
      <v-list-group value="Admin">
        <template v-slot:activator="{ props }">
          <v-hover v-slot="{ isHovering, props: hoverProps }">
            <v-list-item
              :ripple="false"
              v-bind="{ ...props, ...hoverProps }"
              :elevation="isHovering ? 12 : 1"
              title="Admin"
            />
          </v-hover>
        </template>


        <v-hover
          v-for="([title], i) in admins"
          :key="i"
          v-slot="{ isHovering, props }"
        >
          <v-list-item
            v-bind="props"
            :elevation="isHovering ? 12 : 1"
            :ripple="false"
            :title="title"
            :value="title"
          />
        </v-hover>

      </v-list-group>

      <v-list-group value="Actions">
        <template v-slot:activator="{ props }">
          <v-hover v-slot="{ isHovering, props: hoverProps }">
            <v-list-item
              :ripple="false"
              v-bind="{ ...props, ...hoverProps }"
              :elevation="isHovering ? 12 : 1"
              title="Actions"
            />
          </v-hover>
        </template>

        <v-hover
          v-for="([title], i) in cruds"
          :key="i"
          v-slot="{ isHovering, props }"
        >
          <v-list-item
            :ripple="false"
            v-bind="props"
            :elevation="isHovering ? 12 : 1"
            :title="title"
            :value="title"
          />
        </v-hover>

      </v-list-group>
    </v-list>
  </v-card>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const open = ref(['Users'])

const admins = [
  ['Management'],
  ['Settings'],
]
const cruds = [
  ['Create'],
  ['Read'],
  ['Update'],
  ['Delete'],
]
</script>

<style scoped lang="scss">

.card {
  border-radius: 0;
  width: 100%;
  user-select: none;
  overflow: initial;
  z-index: initial;
}

.bar {
  overflow: initial;
  z-index: initial;
  background-color: var(--sidebar-color);
}

.v-list-item--active {
  transform: scale(1.025);
  background-color: color-mix(in srgb, #ffedc2 80%, var(--sidebar-color));
  font-style: revert;
  border-radius: 0 6px 6px 0;
  color: #fff8dc;
  text-shadow:
    0 1px 0 rgba(0, 0, 0, 0.4),
    0 2px 2px rgba(0, 0, 0, 0.3);
  box-shadow: var(--el-box-shadow-dark);
}

.v-list-item {
  transform-origin: center left;
  transition: transform 0.3s ease, background-color 0.3s ease, color 0.3s ease, box-shadow 0.3s ease;
  color: #fff;
}

.v-list-group.v-list-group--open > .v-list-item {
  transform: scale(1.05);
  background-color: color-mix(in srgb, black 40%, var(--sidebar-color));
  border-radius: 0 6px 6px 0;
  color: #fff8dc;
  text-shadow:
    0 1px 0 rgba(0, 0, 0, 0.4),
    0 2px 2px rgba(0, 0, 0, 0.3);
  box-shadow: var(--el-box-shadow-dark);
}

:deep(.v-list-item__overlay) {
  background-color: transparent !important;
}


</style>
