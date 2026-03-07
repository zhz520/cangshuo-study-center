import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import LoginView from '../views/LoginView.vue'
import RegisterView from '../views/RegisterView.vue'
import ExamView from '../views/ExamView.vue'
import LandingView from '../views/LandingView.vue'

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes: [
        {
            path: '/',
            name: 'landing',
            component: LandingView
        },
        {
            path: '/home',
            name: 'home',
            component: HomeView,
            meta: { requiresAuth: true }
        },
        {
            path: '/login',
            name: 'login',
            component: LoginView
        },
        {
            path: '/register',
            name: 'register',
            component: RegisterView
        },
        {
            path: '/agreement',
            name: 'agreement',
            component: () => import('../views/UserAgreementView.vue')
        },
        {
            path: '/privacy',
            name: 'privacy',
            component: () => import('../views/PrivacyPolicyView.vue')
        },
        {
            path: '/download',
            name: 'download',
            component: () => import('../views/AppDownloadView.vue')
        },
        {
            path: '/article/:id',
            name: 'article-detail',
            component: () => import('../views/ArticleView.vue')
        },
        {
            path: '/data',
            name: 'data',
            component: () => import('../views/DataManageView.vue'),
            meta: { requiresAuth: true }
        },
        {
            path: '/stats',
            name: 'stats',
            component: () => import('../views/StatsView.vue'),
            meta: { requiresAuth: true }
        },
        {
            path: '/analysis',
            name: 'analysis',
            component: () => import('../views/AnalysisView.vue'),
            meta: { requiresAuth: true }
        },
        {
            path: '/exam/:subjectId',
            name: 'exam',
            component: ExamView,
            meta: { requiresAuth: true }
        },
        {
            path: '/admin',
            component: () => import('../views/admin/AdminLayout.vue'),
            meta: { requiresAuth: true, requiresAdmin: true },
            children: [
                {
                    path: '',
                    name: 'admin-dashboard',
                    component: () => import('../views/admin/DashboardView.vue')
                },
                {
                    path: 'users',
                    name: 'admin-users',
                    component: () => import('../views/admin/UserManagement.vue')
                },
                {
                    path: 'questions',
                    name: 'admin-questions',
                    component: () => import('../views/admin/QuestionManagement.vue')
                },
                {
                    path: 'settings',
                    name: 'admin-settings',
                    component: () => import('../views/admin/SettingsView.vue')
                },
                {
                    path: 'structure',
                    name: 'admin-structure',
                    component: () => import('../views/admin/StructureManagement.vue')
                },
                {
                    path: 'app-update',
                    name: 'admin-app-update',
                    component: () => import('../views/admin/AppManagement.vue')
                },
                {
                    path: 'articles',
                    name: 'admin-articles',
                    component: () => import('../views/admin/ArticleManagement.vue')
                },
                {
                    path: 'comments',
                    name: 'admin-comments',
                    component: () => import('../views/admin/CommentManagement.vue')
                }
            ]
        }
    ]
})

router.beforeEach((to, from, next) => {
    // Intercept native Android/iOS WebView token injection
    if (to.query.token && to.query.role) {
        localStorage.setItem('token', to.query.token);
        localStorage.setItem('user', JSON.stringify({ role: to.query.role, username: 'AppAdmin' }));
        
        // Clean URL to prevent token leaking in address bar
        const cleanQuery = { ...to.query };
        delete cleanQuery.token;
        delete cleanQuery.role;
        return next({ path: to.path, query: cleanQuery, replace: true });
    }
    const token = localStorage.getItem('token');
    const userStr = localStorage.getItem('user');
    const user = userStr ? JSON.parse(userStr) : null;

    if (to.meta.requiresAuth && !token) {
        next({ name: 'login' });
    } else if (to.meta.requiresAdmin) {
        if (user && (user.role === 'admin' || user.role === 'demo')) {
            next();
        } else {
            if (confirm('权限不足: 需要管理员或演示账号身份。\n是否退出当前账号并重新登录？')) {
                localStorage.removeItem('token');
                localStorage.removeItem('refreshToken');
                localStorage.removeItem('user');
                next({ name: 'login' });
            } else {
                next({ path: '/home' });
            }
        }
    } else {
        next();
    }
})

export default router
