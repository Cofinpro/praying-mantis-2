import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    /** Reachable without a session, and shown without the app header (the login page) */
    public?: boolean
  }
}

// Placeholder pages until each feature's story builds the real one
const adminPlaceholder = () => import('../views/admin/AdminPlaceholderView.vue')

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
    // In the nav for admins only; the backend enforces it (decision #11). The layout (sub-nav) is
    // the parent, each section a child route; `/admin` itself opens Users.
    path: '/admin',
    name: 'admin',
    component: () => import('../views/AdminView.vue'),
    redirect: { name: 'admin-users' },
    children: [
      {
        path: 'users',
        name: 'admin-users',
        component: () => import('../views/admin/AdminUsersView.vue'),
      },
      {
        path: 'entitlements',
        name: 'admin-entitlements',
        component: () => import('../views/admin/AdminEntitlementsView.vue'),
      },
      {
        path: 'projects',
        name: 'admin-projects',
        component: adminPlaceholder,
        props: { title: 'Projects', story: 'FE-9.3' },
      },
      {
        path: 'public-holidays',
        name: 'admin-public-holidays',
        component: adminPlaceholder,
        props: { title: 'Public holidays', story: 'FE-9.4' },
      },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

export default router
