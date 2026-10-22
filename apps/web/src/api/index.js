import axios from 'axios';
import NProgress from 'nprogress';
import { showSessionExpiredDialog } from '../utils/sessionExpired';

// Configure NProgress
NProgress.configure({ showSpinner: false });

// Prioritize environment variable, fallback to '/api' (proxy mode)
const baseURL = import.meta.env.VITE_API_BASE_URL || '/api';

const api = axios.create({
    baseURL: baseURL,
});

// ── Request Interceptor ──
api.interceptors.request.use(config => {
    NProgress.start();
    const token = localStorage.getItem('token');
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

// ── Refresh Token Logic ──
let isRefreshing = false;
let failedQueue = [];

function processQueue(error, token = null) {
    failedQueue.forEach(prom => {
        if (error) {
            prom.reject(error);
        } else {
            prom.resolve(token);
        }
    });
    failedQueue = [];
}

// ── Response Interceptor ──
api.interceptors.response.use(
    response => {
        NProgress.done();
        if (typeof response.data === 'string' && response.data.trim().toLowerCase().startsWith('<!doctype html>')) {
            console.error('API Error: Received HTML instead of JSON.');
            return Promise.reject(new Error(`API 接口异常: 返回了网页 HTML。请检查接口地址 (${baseURL}) 是否正确。`));
        }
        return response;
    },
    error => {
        NProgress.done();
        const originalRequest = error.config;

        // Only handle 401 errors, and skip the refresh endpoint itself
        if (error.response && error.response.status === 401 && !originalRequest._retry) {
            // Don't try to refresh if the failed request IS the refresh request
            if (originalRequest.url && originalRequest.url.includes('/auth/refresh')) {
                handleSessionExpired();
                return Promise.reject(error);
            }

            if (isRefreshing) {
                // Queue this request while refresh is in progress
                return new Promise((resolve, reject) => {
                    failedQueue.push({ resolve, reject });
                }).then(token => {
                    originalRequest.headers.Authorization = `Bearer ${token}`;
                    return api(originalRequest);
                }).catch(err => {
                    return Promise.reject(err);
                });
            }

            originalRequest._retry = true;
            isRefreshing = true;

            const refreshToken = localStorage.getItem('refreshToken');
            if (!refreshToken) {
                isRefreshing = false;
                handleSessionExpired();
                return Promise.reject(error);
            }

            return new Promise((resolve, reject) => {
                axios.post(`${baseURL}/auth/refresh`, { refreshToken })
                    .then(({ data }) => {
                        const newToken = data.token;
                        localStorage.setItem('token', newToken);
                        if (data.user) {
                            localStorage.setItem('user', JSON.stringify(data.user));
                        }
                        api.defaults.headers.common['Authorization'] = `Bearer ${newToken}`;
                        originalRequest.headers.Authorization = `Bearer ${newToken}`;
                        processQueue(null, newToken);
                        resolve(api(originalRequest));
                    })
                    .catch(err => {
                        processQueue(err, null);
                        handleSessionExpired();
                        reject(err);
                    })
                    .finally(() => {
                        isRefreshing = false;
                    });
            });
        }

        return Promise.reject(error);
    }
);

function handleSessionExpired() {
    const currentPath = window.location.pathname;
    if (currentPath !== '/login') {
        showSessionExpiredDialog();
    }
}

export const auth = {
    me: () => api.get('/auth/me'),
    login: (data) => api.post('/auth/login', data),
    register: (data) => api.post('/auth/register', data),
    sendVerificationCode: (data) => api.post('/auth/send-code', typeof data === 'string' ? { email: data } : data),
    forgotPassword: (data) => api.post('/auth/forgot-password', data),
    changeEmail: (data) => api.post('/auth/change-email', data),
    logout: () => {
        const refreshToken = localStorage.getItem('refreshToken');
        return api.post('/auth/logout', { refreshToken });
    },
};

export const data = {
    // New Hierarchy
    getMajors: () => api.get('/data/majors'),
    getSubjects: (major_id) => api.get('/data/subjects', { params: { major_id } }),
    getQuestions: (subject_id, params) => api.get('/data/questions', { params: { subject_id, ...params } }),
    
    // Progress
    getProgress: () => api.get('/data/progress'),
    syncProgress: (data) => api.post('/data/progress', data),
    syncBatch: (data) => api.post('/data/sync', data),
    resetProgress: (subject_id) => api.delete('/data/progress', { params: { subject_id } }),
    
    // Recommendations
    getRecommendations: (subjectId) => api.get('/data/recommendation', { params: { subject_id: subjectId } }),
    
    // Analysis
    getDetailedStats: () => api.get('/data/detailed-stats'),
    getAnalysis: (subjectId) => api.get('/data/analysis', { params: { subject_id: subjectId } }),
    
    // AI Tutor & Analysis
    getAiExplanation: (data) => api.post('/data/ai-explain', data),
    getAiAnalysis: (data) => api.post('/data/ai-analysis', data),
    exportQuestions: (payload) => api.post('/data/export/questions', payload, { responseType: 'blob' }),
};

export const admin = {
    getUsers: () => api.get('/admin/users'),
    createUser: (data) => api.post('/admin/users', data),
    updateUserRole: (id, role) => api.patch(`/admin/users/${id}/role`, { role }),
    deleteUser: (id) => api.delete(`/admin/users/${id}`),
    
    // Statistics
    getStats: () => api.get('/admin/stats'),

    
    // Major & Subject Management
    getMajors: () => api.get('/admin/majors'),
    addMajor: (data) => api.post('/admin/majors', data),
    deleteMajor: (id) => api.delete(`/admin/majors/${id}`),
    
    getSubjects: (major_id) => api.get('/admin/subjects', { params: { major_id } }),
    addSubject: (data) => api.post('/admin/subjects', data),
    deleteSubject: (id) => api.delete(`/admin/subjects/${id}`),

    // Question Management
    getQuestions: (params) => api.get('/admin/questions', { params }),
    addQuestion: (data) => api.post('/admin/questions', data),
    updateQuestion: (id, data) => api.put(`/admin/questions/${id}`, data),
    deleteQuestion: (id) => api.delete(`/admin/questions/${id}`),

    // Settings & Content
    getSettings: () => api.get('/admin/settings'),
    updateSettings: (data) => api.post('/admin/settings', data),
    getCountdowns: () => api.get('/admin/countdowns'),
    updateCountdown: (data) => api.post('/admin/countdowns', data),
    deleteCountdown: (id) => api.delete(`/admin/countdowns/${id}`),
    getModules: () => api.get('/admin/modules'),
    updateModule: (data) => api.post('/admin/modules', data),
    deleteModule: (id) => api.delete(`/admin/modules/${id}`),
    getLinks: () => api.get('/admin/links'),
    updateLink: (data) => api.post('/admin/links', data),
    deleteLink: (id) => api.delete(`/admin/links/${id}`),
    updateUserDetails: (id, data) => api.put(`/admin/users/${id}`, data),
    
    // App Update Management
    getAppVersions: () => api.get('/admin/app-update/versions'),
    createAppVersion: (data) => api.post('/admin/app-update/versions', data),
    updateAppVersion: (id, data) => api.put(`/admin/app-update/versions/${id}`, data),
    deleteAppVersion: (id) => api.delete(`/admin/app-update/versions/${id}`),
    
    getAppPatches: () => api.get('/admin/app-update/patches'),
    createAppPatch: (data) => api.post('/admin/app-update/patches', data),
    updateAppPatch: (id, data) => api.put(`/admin/app-update/patches/${id}`, data),
    deleteAppPatch: (id) => api.delete(`/admin/app-update/patches/${id}`),
    
    // Articles Management
    getArticles: () => api.get('/admin/articles'),
    getArticle: (id) => api.get(`/admin/articles/detail/${id}`),
    createArticle: (data) => api.post('/admin/articles', data),
    updateArticle: (id, data) => api.put(`/admin/articles/${id}`, data),
    deleteArticle: (id) => api.delete(`/admin/articles/${id}`),
    
    // Comments Management
    getComments: (params) => api.get('/admin/comments', { params }),
    deleteAdminComment: (id) => api.delete(`/admin/comments/${id}`),
    updateAdminCommentStatus: (id, status) => api.patch(`/admin/comments/${id}/status`, { status }),

    // Daily Content
    getDailyContents: (type, page = 1, limit = 10) => api.get('/admin/daily-contents', { params: { type, page, limit } }),
    updateDailyContent: (data) => api.post('/admin/daily-contents', data),
    deleteDailyContent: (id) => api.delete(`/admin/daily-contents/${id}`),
    // JSON Import
    importQuestionsJson: (data) => api.post('/admin/questions/import', data),
    
    // Excel Import
    importQuestionsExcel: (formData) => api.post('/admin/import/excel', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
    }),
    
    // Cards Management
    getCards: (params) => api.get('/admin/cards', { params }),
    generateCards: (data) => api.post('/admin/cards/generate', data),
    deleteCard: (id) => api.delete(`/admin/cards/${id}`),

    // Schedule Management
    getSchedule: () => api.get('/admin/schedule'),
    saveSchedule: (data) => api.post('/admin/schedule/batch', data),

    // File Upload
    uploadImage: (formData) => api.post('/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
    }),

    // Question Reports
    getReports: (params) => api.get('/admin/reports', { params }),
    updateReportStatus: (id, status) => api.patch(`/admin/reports/${id}/status`, { status }),

    // Exams Management
    getExams: (params) => api.get('/admin/exams', { params }),
    getExam: (id) => api.get(`/admin/exams/${id}`),
    updateExam: (id, data) => api.patch(`/admin/exams/${id}`, data),
    deleteExam: (id) => api.delete(`/admin/exams/${id}`),
    getExamRecords: (id, params) => api.get(`/admin/exams/${id}/records`, { params }),
    getExamRecordDetails: (recordId) => api.get(`/admin/exams/records/${recordId}/details`),
    updateExamRetryPermission: (id, can_retry) => api.patch(`/admin/exams/records/${id}/retry`, { can_retry }),
    // Exam Questions
    addExamQuestion: (paperId, data) => api.post(`/admin/exams/${paperId}/questions`, data),
    updateExamQuestion: (id, data) => api.patch(`/admin/exams/questions/${id}`, data),
    deleteExamQuestion: (id) => api.delete(`/admin/exams/questions/${id}`),

    // Access Control Management
    getAccessControls: () => api.get('/admin/access-control'),
    updateAccessControlBatch: (data) => api.post('/admin/access-control/batch', data),

    // Question export authorization
    getExportCredentials: (params) => api.get('/admin/exports', { params }),
    generateExportPassword: (userId) => api.post(`/admin/exports/${userId}`),
    revokeExportPassword: (userId) => api.delete(`/admin/exports/${userId}`),
};

export const publicApi = {
    getHomeData: () => api.get('/public/home-data'),
    getStats: () => api.get('/public/stats'),
    getRandomDailyContent: () => api.get('/data/daily-content/random'),
    getLatestAppVersion: () => api.get('/app/check_update', { params: { versionCode: 0 } }),
    queryScore: (data) => api.post('/public/score-query', data),
    getSchools: () => api.get('/public/schools'),
    analyzeScore: (data) => api.post('/public/score-analysis', data),
};

export default api;
