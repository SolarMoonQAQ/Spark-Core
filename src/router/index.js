import {createRouter, createWebHashHistory, createWebHistory} from 'vue-router'
import MarkdownPage from '@/views/MarkdownPage.vue'

const routes = [
    { path: '/', component: MarkdownPage },
    { path: '/lua', component: MarkdownPage },
    { path: '/lua/use', component: MarkdownPage },
    { path: '/lua/method', component: MarkdownPage },
    { path: '/lua/module', component: MarkdownPage },
    { path: '/lua/docs', component: MarkdownPage }
]

export default createRouter({
    history: createWebHashHistory(),
    routes
})
