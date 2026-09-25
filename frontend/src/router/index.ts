import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    /** Reachable without a session, and shown without the app header (the login page) */
    public?: boolean
  }
}

// Placeholder pages until each feature's story builds the real one
const placeholder = () => import('../views/PlaceholderView.vue')

export const routes: RouteRecordRaw[] = [
  { path: '/', redirect: { name: 'absences' } },
  {
    // src/api/client.ts and router/authGuard.ts redirect here without a session
    path: '/login',
    name: 'login',
    component: () => import('../views/LoginView.vue'),
    meta: { public: true },
  },
  {
    path: '/absences',
    name: 'absences',
    component: () => import('../views/AbsencesView.vue'),
  },
  {
    path: '/timesheets',
    name: 'timesheets',
    // `?week=YYYY-MM-DD` opens that week (notifications link there)
    component: () => import('../views/TimesheetsView.vue'),
  },
  {
    // In the nav for team leads only. Anyone can open the URL; the backend decides what they see
    // (decision #11), and everyone else gets "Nothing to approve right now."
    path: '/approvals',
    name: 'approvals',
    component: () => import('../views/ApprovalsView.vue'),
  },
  {
    // In the nav for admins only; the backend enforces it (decision #11)
    path: '/admin',
    name: 'admin',
    component: placeholder,
    props: { title: 'Admin', story: 'FE-9.1' },
  },
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

export default router
