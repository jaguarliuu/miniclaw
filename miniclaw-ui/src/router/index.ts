import { createRouter, createWebHistory } from 'vue-router'
import WorkspaceView from '@/views/WorkspaceView.vue'
import SettingsView from '@/views/SettingsView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'workspace',
      component: WorkspaceView
    },
    {
      path: '/settings',
      redirect: '/settings/skills'
    },
    {
      path: '/settings/:section',
      name: 'settings',
      component: SettingsView,
      props: true
    }
  ]
})

export default router
