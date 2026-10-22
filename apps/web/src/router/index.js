import { createRouter, createWebHistory } from 'vue-router'
import { auth } from '../api'
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
            component: LandingView,
            meta: { title: '烁学 - AI 智能刷题平台 | 海量题库 · AI 解析 · 科学备考' }
        },
        {
            path: '/home',
            name: 'home',
            component: HomeView,
            meta: { requiresAuth: true, shell: true }
        },
        {
            path: '/login',
            name: 'login',
            component: LoginView,
            meta: { title: '登录 - 烁学' }
        },
        {
            path: '/register',
            name: 'register',
            component: RegisterView,
            meta: { title: '免费注册 - 烁学' }
        },
        {
            path: '/agreement',
            name: 'agreement',
            component: () => import('../views/UserAgreementView.vue'),
            meta: { title: '用户协议 - 烁学' }
        },
        {
            path: '/privacy',
            name: 'privacy',
            component: () => import('../views/PrivacyPolicyView.vue'),
            meta: { title: '隐私政策 - 烁学' }
        },
        {
            path: '/download',
            name: 'download',
            component: () => import('../views/AppDownloadView.vue'),
            meta: { title: '下载 App - 烁学' }
        },
        {
            path: '/score-query',
            name: 'score-query',
            component: () => import('../views/ScoreQueryView.vue'),
            meta: { title: '联考成绩查询 - 烁学' }
        },
        {
            path: '/article/:id',
            name: 'article-detail',
            component: () => import('../views/ArticleView.vue'),
            meta: { title: '文章详情 - 烁学' }
        },
        {
            path: '/data',
            name: 'data',
            component: () => import('../views/DataManageView.vue'),
            meta: { requiresAuth: true, shell: true }
        },
        {
            path: '/export',
            name: 'export',
            component: () => import('../views/ExportView.vue'),
            meta: { requiresAuth: true, shell: true, title: '题库导出 - 烁学' }
        },
        {
            path: '/stats',
            name: 'stats',
            component: () => import('../views/StatsView.vue'),
            meta: { requiresAuth: true, shell: true }
        },
        {
            path: '/analysis',
            name: 'analysis',
            component: () => import('../views/AnalysisView.vue'),
            meta: { requiresAuth: true, shell: true }
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
                },
                {
                    path: 'cards',
                    name: 'admin-cards',
                    component: () => import('../views/admin/CardsManagement.vue')
                },
                {
                    path: 'schedule',
                    name: 'admin-schedule',
                    component: () => import('../views/admin/ScheduleManagement.vue')
                },
                {
                    path: 'reports',
                    name: 'admin-reports',
                    component: () => import('../views/admin/QuestionReports.vue')
                },
                {
                    path: 'exams',
                    name: 'admin-exams',
                    component: () => import('../views/admin/ExamManagement.vue')
                },
                {
                    path: 'access-control',
                    name: 'admin-access-control',
                    component: () => import('../views/admin/AccessControlManagement.vue')
                },
                {
                    path: 'exports',
                    name: 'admin-exports',
                    component: () => import('../views/admin/ExportAuthorization.vue')
                },
                {
                    path: 'integrations',
                    name: 'admin-integrations',
                    component: () => import('../views/admin/IntegrationsView.vue')
                }
            ]
        },
        {
            path: '/:pathMatch(.*)*',
            name: 'not-found',
            component: () => import('../views/NotFoundView.vue'),
            meta: { title: '页面不存在 - 烁学' }
        }
    ]
})

router.beforeEach(async (to, from, next) => {
    // Intercept native Android/iOS WebView token injection
    if (typeof to.query.token === 'string') {
        localStorage.setItem('token', to.query.token);
        try {
            const response = await auth.me();
            localStorage.setItem('user', JSON.stringify(response.data.user));
        } catch {
            localStorage.removeItem('token');
            localStorage.removeItem('user');
            return next({ name: 'login', replace: true });
        }
        
        // Clean URL to prevent token leaking in address bar
        const cleanQuery = { ...to.query };
        delete cleanQuery.token;
        delete cleanQuery.role;
        return next({ path: to.path, query: cleanQuery, replace: true });
    }
    const token = localStorage.getItem('token');
    const userStr = localStorage.getItem('user');
    let user = null;
    try { user = userStr ? JSON.parse(userStr) : null; } catch { localStorage.removeItem('user'); }
    if (to.meta.requiresAdmin && token) {
        try {
            const response = await auth.me();
            user = response.data.user;
            localStorage.setItem('user', JSON.stringify(user));
        } catch { return next({ name: 'login' }); }
    }

    if (to.meta.requiresAuth && !token) {
        next({ name: 'login' });
    } else if (['login', 'register'].includes(to.name) && token) {
        // Redirect logged-in users to home
        next({ name: 'home' });
    } else if (to.meta.requiresAdmin) {
        if (user && user.role === 'admin') {
            next();
        } else if (user && user.role === 'demo') {
            alert('演示账号不能进入管理后台，请使用学习页面。');
            next({ path: '/home' });
        } else {
            if (confirm('权限不足: 需要管理员身份。\n是否退出当前账号并重新登录？')) {
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

// Dynamic document title for SEO
router.afterEach((to) => {
    const defaultTitle = '烁学 - AI 智能刷题平台';
    document.title = to.meta.title || defaultTitle;
});

export default router
