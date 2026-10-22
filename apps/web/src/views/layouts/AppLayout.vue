<script setup>
import { ref, onMounted } from 'vue';
import { useRouter, useRoute } from 'vue-router';

const router = useRouter();
const route = useRoute();
const isSidebarOpen = ref(window.innerWidth >= 1024); // Default open on desktop
const isMobile = ref(window.innerWidth < 1024);

// Menu Items
const menuItems = [
    { label: '首页', icon: '🏠', path: '/' },
    { label: '题库', icon: '📚', path: '/library' }, // Placeholder, maybe links to anchors in home
    { label: '学情', icon: '📊', path: '/analysis' },
    { label: '数据', icon: '💾', path: '/data' },
];

// Admin check
const isAdmin = ref(false);

const toggleSidebar = () => {
    isSidebarOpen.value = !isSidebarOpen.value;
};

const navigateTo = (path) => {
    router.push(path);
    if (isMobile.value) isSidebarOpen.value = false;
};

// Theme Toggle
const isDark = ref(false);
const toggleTheme = () => {
    isDark.value = !isDark.value;
    if (isDark.value) {
        document.documentElement.classList.add('dark');
        localStorage.setItem('theme', 'dark');
    } else {
        document.documentElement.classList.remove('dark');
        localStorage.setItem('theme', 'light');
    }
};

onMounted(() => {
    const userString = localStorage.getItem('user');
    if (userString) {
        const user = JSON.parse(userString);
        isAdmin.value = user.role === 'admin' || user.role === 'demo';
    }
    
    // Init Theme
    const savedTheme = localStorage.getItem('theme');
    if (savedTheme === 'dark' || (!savedTheme && window.matchMedia('(prefers-color-scheme: dark)').matches)) {
        isDark.value = true;
        document.documentElement.classList.add('dark');
    }

    // Responsive listener
    window.addEventListener('resize', () => {
        const mobile = window.innerWidth < 1024;
        if (mobile !== isMobile.value) {
            isMobile.value = mobile;
            isSidebarOpen.value = !mobile;
        }
    });

    if (isAdmin.value) {
        menuItems.push({ label: '后台', icon: '⚙️', path: '/admin' });
    }
});
</script>

<template>
    <div class="h-screen w-full flex bg-background-soft transition-colors duration-300">
        
        <!-- Mobile Overlay -->
        <div v-if="isMobile && isSidebarOpen" 
             class="fixed inset-0 bg-black/40 z-40 backdrop-blur-sm transition-opacity"
             @click="isSidebarOpen = false">
        </div>

        <!-- Sidebar -->
        <aside 
            class="fixed lg:static inset-y-0 left-0 z-50 w-64 bg-background border-r border-border transform transition-all duration-300 ease-spring flex flex-col"
            :class="[
                isSidebarOpen ? 'translate-x-0' : '-translate-x-full lg:w-0 lg:overflow-hidden lg:border-none'
            ]"
        >
            <!-- Logo Section -->
            <div class="h-16 flex items-center px-6 border-b border-border/50">
                <div class="w-8 h-8 bg-primary rounded-lg flex items-center justify-center text-white font-bold mr-3 shadow-md shadow-primary/30">
                    S
                </div>
                <span class="text-lg font-bold text-heading tracking-tight">刷题中心</span>
            </div>

            <!-- Navigation -->
            <nav class="flex-1 overflow-y-auto py-6 px-3 space-y-1">
                <button 
                    v-for="item in menuItems" 
                    :key="item.path"
                    @click="navigateTo(item.path)"
                    class="w-full flex items-center gap-3 px-4 py-3 rounded-xl transition-all duration-200 group"
                    :class="route.path === item.path ? 'bg-primary/10 text-primary font-bold' : 'text-text hover:bg-background-mute hover:text-heading'"
                >
                    <span class="text-xl transition-transform group-hover:scale-110">{{ item.icon }}</span>
                    <span class="text-sm">{{ item.label }}</span>
                </button>
            </nav>

            <!-- Bottom Actions -->
            <div class="p-4 border-t border-border/50">
                <button @click="toggleTheme" class="w-full flex items-center justify-between px-4 py-3 rounded-xl hover:bg-background-mute transition-colors text-text">
                    <span class="text-sm font-medium">Dark Mode</span>
                    <div class="w-10 h-6 rounded-full p-1 transition-colors duration-300 relative"
                         :class="isDark ? 'bg-primary' : 'bg-slate-300'">
                        <div class="w-4 h-4 bg-white rounded-full shadow-sm transform transition-transform duration-300"
                             :class="isDark ? 'translate-x-4' : 'translate-x-0'"></div>
                    </div>
                </button>
            </div>
        </aside>

        <!-- Main Content -->
        <main class="flex-1 flex flex-col h-screen overflow-hidden relative">
            
            <!-- Top Header -->
            <header class="h-16 flex items-center justify-between px-6 bg-background/80 backdrop-blur-md border-b border-border sticky top-0 z-30">
                <div class="flex items-center gap-4">
                    <button @click="toggleSidebar" class="p-2 rounded-lg hover:bg-background-mute text-text transition-colors">
                        <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 12h16M4 18h16" />
                        </svg>
                    </button>
                    <!-- Breadcrumbs or Title could go here -->
                    <h1 class="text-lg font-bold text-heading lg:hidden">刷题中心</h1>
                </div>

                <div class="flex items-center gap-3">
                    <!-- User Avatar or Profile -->
                    <div class="w-8 h-8 rounded-full bg-indigo-100 dark:bg-indigo-900/30 text-indigo-500 flex items-center justify-center font-bold text-xs">
                        U
                    </div>
                </div>
            </header>

            <!-- Page Content (Scrollable) -->
            <div class="flex-1 overflow-y-auto p-4 md:p-8 scroll-smooth will-change-transform">
                <div class="max-w-7xl mx-auto min-h-full animate-enter-slide-up">
                    <slot></slot>
                </div>
            </div>

        </main>
    </div>
</template>

<style scoped>
.ease-spring {
    transition-timing-function: cubic-bezier(0.34, 1.56, 0.64, 1);
}
</style>
