import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { getActiveAuthToken } from '../utils/authSession'

const UserPage = () => import('../pages/UserPage.vue')
const AdminPage = () => import('../pages/AdminPage.vue')
const RegisterPage = () => import('../pages/RegisterPage.vue')
const LoginPage = () => import('../pages/LoginPage.vue')

const routes: RouteRecordRaw[] = [
  { path: '/', name: 'user', component: UserPage },
  {
    path: '/admin',
    name: 'admin',
    component: AdminPage,
    meta: { requiresAuth: true },
  },
  { path: '/login', name: 'login', component: LoginPage },
  { path: '/register', name: 'register', component: RegisterPage },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to, _from, next) => {
  if (!to.meta.requiresAuth) {
    next()
    return
  }

  const { token } = getActiveAuthToken()
  const adminToken = localStorage.getItem('accessiblenav_admin_token')

  if (!token) {
    next({ name: 'login', query: { as: 'admin', redirect: to.fullPath } })
    return
  }

  if (!adminToken) {
    try {
      const raw = localStorage.getItem('accessiblenav_user_profile')
      const role = raw ? String(JSON.parse(raw)?.role || '').toUpperCase() : ''
      const ADMIN_ROLES = new Set(['ADMIN', 'REVIEWER', 'EDITOR', 'VIEWER'])
      if (!ADMIN_ROLES.has(role)) {
        next({ name: 'login', query: { as: 'admin', redirect: to.fullPath } })
        return
      }
    } catch {
      next({ name: 'login', query: { as: 'admin', redirect: to.fullPath } })
      return
    }
  }

  next()
})

export default router
