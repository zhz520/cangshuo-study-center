<script setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import ConfirmDialog from '../../components/ui/ConfirmDialog.vue';

const router = useRouter();
const showSidebar = ref(false);
const confirmDialog = ref(null);

const logout = async () => {
  const confirmed = await confirmDialog.value.confirm({
    title: "确认退出",
    message: "您确定要安全退出管理后台吗？",
    confirmText: "退出",
    cancelText: "取消",
    type: "danger"
  });

  if (confirmed) {
    try {
      const { auth } = await import('../../api');
      await auth.logout();
    } catch (_) {}
    localStorage.removeItem('token');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('user');
    router.push('/login');
  }
};

const toggleSidebar = () => {
  showSidebar.value = !showSidebar.value;
};
</script>

<template>
  <div class="flex h-screen bg-slate-50 dark:bg-slate-900 overflow-hidden relative">
    <!-- Background Blobs -->
    <div class="absolute top-0 left-0 w-full h-full z-0 pointer-events-none overflow-hidden">
        <div class="absolute top-[-10%] left-[-10%] w-[40%] h-[40%] bg-indigo-500/5 rounded-full blur-[100px] animate-pulse-slow"></div>
        <div class="absolute bottom-[-10%] right-[-10%] w-[40%] h-[40%] bg-purple-500/5 rounded-full blur-[100px] animate-pulse-slow" style="animation-delay: 2s"></div>
    </div>

    <!-- Sidebar Backdrop (Mobile Only) -->
    <div 
      v-if="showSidebar" 
      @click="showSidebar = false"
      class="fixed inset-0 bg-slate-900/40 backdrop-blur-sm z-30 lg:hidden transition-opacity duration-300"
    ></div>

    <!-- Sidebar -->
    <aside 
      class="fixed inset-y-0 left-0 w-72 bg-white/80 dark:bg-slate-800/80 backdrop-blur-xl shadow-2xl z-40 transform transition-transform duration-300 lg:relative lg:translate-x-0 lg:shadow-none flex flex-col border-r border-slate-200/50 dark:border-slate-700/50"
      :class="showSidebar ? 'translate-x-0' : '-translate-x-full'"
    >
      <div class="p-8 relative">
        <router-link @click="showSidebar = false" to="/admin" class="block group">
          <h1 class="text-2xl font-black text-slate-800 dark:text-slate-100 italic tracking-tighter group-hover:scale-105 transition-transform origin-left">STUDY<span class="text-indigo-500">.</span>ADMIN</h1>
          <p class="text-[10px] font-bold text-slate-400 uppercase tracking-widest mt-1 group-hover:text-indigo-500 transition-colors">Management Portal</p>
        </router-link>
        
        <!-- Close button for mobile -->
        <button 
          @click="showSidebar = false"
          class="lg:hidden absolute right-4 top-1/2 -translate-y-1/2 w-8 h-8 flex items-center justify-center rounded-full bg-slate-100 dark:bg-slate-700 text-slate-400 hover:bg-slate-200 dark:hover:bg-slate-600 transition-colors"
        >
          ✕
        </button>
      </div>
      
      <nav class="flex-1 px-4 py-2 space-y-2 overflow-y-auto">
        <router-link @click="showSidebar = false" to="/admin" class="flex items-center gap-4 px-5 py-3.5 rounded-2xl text-slate-500 dark:text-slate-400 hover:bg-white dark:hover:bg-slate-700/50 hover:text-indigo-600 dark:hover:text-indigo-400 hover:shadow-md transition-all group border border-transparent hover:border-slate-100 dark:hover:border-slate-700" exact-active-class="bg-indigo-50 dark:bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 font-bold shadow-sm ring-1 ring-indigo-100 dark:ring-indigo-900/30">
          <span class="text-xl group-hover:scale-110 transition-transform duration-300">📊</span> 
          <span>仪表盘概览</span>
        </router-link>
        <router-link @click="showSidebar = false" to="/admin/users" class="flex items-center gap-4 px-5 py-3.5 rounded-2xl text-slate-500 dark:text-slate-400 hover:bg-white dark:hover:bg-slate-700/50 hover:text-indigo-600 dark:hover:text-indigo-400 hover:shadow-md transition-all group border border-transparent hover:border-slate-100 dark:hover:border-slate-700" active-class="bg-indigo-50 dark:bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 font-bold shadow-sm ring-1 ring-indigo-100 dark:ring-indigo-900/30">
          <span class="text-xl group-hover:scale-110 transition-transform duration-300">👥</span> 
          <span>用户权限管理</span>
        </router-link>
        <router-link @click="showSidebar = false" to="/admin/questions" class="flex items-center gap-4 px-5 py-3.5 rounded-2xl text-slate-500 dark:text-slate-400 hover:bg-white dark:hover:bg-slate-700/50 hover:text-indigo-600 dark:hover:text-indigo-400 hover:shadow-md transition-all group border border-transparent hover:border-slate-100 dark:hover:border-slate-700" active-class="bg-indigo-50 dark:bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 font-bold shadow-sm ring-1 ring-indigo-100 dark:ring-indigo-900/30">
          <span class="text-xl group-hover:scale-110 transition-transform duration-300">📚</span> 
          <span>核心题库管理</span>
        </router-link>
        <router-link @click="showSidebar = false" to="/admin/access-control" class="flex items-center gap-4 px-5 py-3.5 rounded-2xl text-slate-500 dark:text-slate-400 hover:bg-white dark:hover:bg-slate-700/50 hover:text-indigo-600 dark:hover:text-indigo-400 hover:shadow-md transition-all group border border-transparent hover:border-slate-100 dark:hover:border-slate-700" active-class="bg-indigo-50 dark:bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 font-bold shadow-sm ring-1 ring-indigo-100 dark:ring-indigo-900/30">
          <span class="text-xl group-hover:scale-110 transition-transform duration-300">🔐</span> 
          <span>题库访问控制</span>
        </router-link>
        <router-link @click="showSidebar = false" to="/admin/exams" class="flex items-center gap-4 px-5 py-3.5 rounded-2xl text-slate-500 dark:text-slate-400 hover:bg-white dark:hover:bg-slate-700/50 hover:text-indigo-600 dark:hover:text-indigo-400 hover:shadow-md transition-all group border border-transparent hover:border-slate-100 dark:hover:border-slate-700" active-class="bg-indigo-50 dark:bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 font-bold shadow-sm ring-1 ring-indigo-100 dark:ring-indigo-900/30">
          <span class="text-xl group-hover:scale-110 transition-transform duration-300">📝</span> 
          <span>考试系统管理</span>
        </router-link>
        <router-link @click="showSidebar = false" to="/admin/reports" class="flex items-center gap-4 px-5 py-3.5 rounded-2xl text-slate-500 dark:text-slate-400 hover:bg-white dark:hover:bg-slate-700/50 hover:text-rose-600 dark:hover:text-rose-400 hover:shadow-md transition-all group border border-transparent hover:border-slate-100 dark:hover:border-slate-700" active-class="bg-rose-50 dark:bg-rose-500/10 text-rose-600 dark:text-rose-400 font-bold shadow-sm ring-1 ring-rose-100 dark:ring-rose-900/30">
          <span class="text-xl group-hover:scale-110 transition-transform duration-300">🚨</span> 
          <span>题目报错处理</span>
        </router-link>
        <router-link @click="showSidebar = false" to="/admin/structure" class="flex items-center gap-4 px-5 py-3.5 rounded-2xl text-slate-500 dark:text-slate-400 hover:bg-white dark:hover:bg-slate-700/50 hover:text-indigo-600 dark:hover:text-indigo-400 hover:shadow-md transition-all group border border-transparent hover:border-slate-100 dark:hover:border-slate-700" active-class="bg-indigo-50 dark:bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 font-bold shadow-sm ring-1 ring-indigo-100 dark:ring-indigo-900/30">
          <span class="text-xl group-hover:scale-110 transition-transform duration-300">🏗️</span> 
          <span>知识结构管理</span>
        </router-link>
        <router-link @click="showSidebar = false" to="/admin/settings" class="flex items-center gap-4 px-5 py-3.5 rounded-2xl text-slate-500 dark:text-slate-400 hover:bg-white dark:hover:bg-slate-700/50 hover:text-indigo-600 dark:hover:text-indigo-400 hover:shadow-md transition-all group border border-transparent hover:border-slate-100 dark:hover:border-slate-700" active-class="bg-indigo-50 dark:bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 font-bold shadow-sm ring-1 ring-indigo-100 dark:ring-indigo-900/30">
          <span class="text-xl group-hover:scale-110 transition-transform duration-300">⚙️</span> 
          <span>系统策略设置</span>
        </router-link>
        <router-link @click="showSidebar = false" to="/admin/articles" class="flex items-center gap-4 px-5 py-3.5 rounded-2xl text-slate-500 dark:text-slate-400 hover:bg-white dark:hover:bg-slate-700/50 hover:text-indigo-600 dark:hover:text-indigo-400 hover:shadow-md transition-all group border border-transparent hover:border-slate-100 dark:hover:border-slate-700" active-class="bg-indigo-50 dark:bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 font-bold shadow-sm ring-1 ring-indigo-100 dark:ring-indigo-900/30">
          <span class="text-xl group-hover:scale-110 transition-transform duration-300">📰</span> 
          <span>资源文章管理</span>
        </router-link>
        <router-link @click="showSidebar = false" to="/admin/comments" class="flex items-center gap-4 px-5 py-3.5 rounded-2xl text-slate-500 dark:text-slate-400 hover:bg-white dark:hover:bg-slate-700/50 hover:text-indigo-600 dark:hover:text-indigo-400 hover:shadow-md transition-all group border border-transparent hover:border-slate-100 dark:hover:border-slate-700" active-class="bg-indigo-50 dark:bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 font-bold shadow-sm ring-1 ring-indigo-100 dark:ring-indigo-900/30">
          <span class="text-xl group-hover:scale-110 transition-transform duration-300">💬</span> 
          <span>评论弹幕管理</span>
        </router-link>
        <router-link @click="showSidebar = false" to="/admin/cards" class="flex items-center gap-4 px-5 py-3.5 rounded-2xl text-slate-500 dark:text-slate-400 hover:bg-white dark:hover:bg-slate-700/50 hover:text-indigo-600 dark:hover:text-indigo-400 hover:shadow-md transition-all group border border-transparent hover:border-slate-100 dark:hover:border-slate-700" active-class="bg-indigo-50 dark:bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 font-bold shadow-sm ring-1 ring-indigo-100 dark:ring-indigo-900/30">
          <span class="text-xl group-hover:scale-110 transition-transform duration-300">💳</span> 
          <span>订阅卡密管理</span>
        </router-link>
        <router-link @click="showSidebar = false" to="/admin/app-update" class="flex items-center gap-4 px-5 py-3.5 rounded-2xl text-slate-500 dark:text-slate-400 hover:bg-white dark:hover:bg-slate-700/50 hover:text-indigo-600 dark:hover:text-indigo-400 hover:shadow-md transition-all group border border-transparent hover:border-slate-100 dark:hover:border-slate-700" active-class="bg-indigo-50 dark:bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 font-bold shadow-sm ring-1 ring-indigo-100 dark:ring-indigo-900/30">
          <span class="text-xl group-hover:scale-110 transition-transform duration-300">📱</span> 
          <span>App版本管理</span>
        </router-link>
        <router-link @click="showSidebar = false" to="/admin/schedule" class="flex items-center gap-4 px-5 py-3.5 rounded-2xl text-slate-500 dark:text-slate-400 hover:bg-white dark:hover:bg-slate-700/50 hover:text-indigo-600 dark:hover:text-indigo-400 hover:shadow-md transition-all group border border-transparent hover:border-slate-100 dark:hover:border-slate-700" active-class="bg-indigo-50 dark:bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 font-bold shadow-sm ring-1 ring-indigo-100 dark:ring-indigo-900/30">
          <span class="text-xl group-hover:scale-110 transition-transform duration-300">📅</span> 
          <span>课程表管理</span>
        </router-link>
      </nav>

      <div class="p-6 border-t border-slate-200/50 dark:border-slate-700/50 space-y-3 bg-white/50 dark:bg-slate-800/50 backdrop-blur-sm">
        <button @click="router.push('/')" class="w-full flex items-center justify-center gap-2 py-3.5 text-xs font-bold text-slate-500 hover:text-indigo-600 dark:text-slate-400 dark:hover:text-indigo-400 transition-colors bg-white dark:bg-slate-700/50 rounded-2xl border border-slate-200 dark:border-slate-600 hover:border-indigo-200 dark:hover:border-indigo-500/30 shadow-sm hover:shadow-md active:scale-95 group">
          <span class="group-hover:-translate-x-1 transition-transform">←</span> 返回学习大厅
        </button>
        <button @click="logout" class="w-full py-4 bg-rose-50 dark:bg-rose-500/10 text-rose-500 rounded-2xl text-xs font-black hover:bg-rose-100 dark:hover:bg-rose-500/20 transition-all active:scale-95 flex items-center justify-center gap-2 group">
          <span>安全退出</span>
          <span class="group-hover:translate-x-1 transition-transform">→</span>
        </button>
      </div>
    </aside>

    <!-- Main Content -->
    <div class="flex-1 flex flex-col h-screen overflow-hidden relative z-10">
      <!-- Top navbar for mobile -->
      <header class="lg:hidden h-16 bg-white/80 dark:bg-slate-800/80 backdrop-blur-md border-b border-slate-200/50 dark:border-slate-700/50 px-4 flex items-center justify-between flex-shrink-0 z-20 shadow-sm">
        <button @click="toggleSidebar" class="w-10 h-10 flex items-center justify-center rounded-xl bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-300 active:scale-95 transition-transform">
          <span class="text-xl">☰</span>
        </button>
        <h2 class="font-black text-slate-800 dark:text-slate-100 text-sm tracking-tighter uppercase">Admin Panel</h2>
        <div class="w-10"></div> <!-- Placeholder for balance -->
      </header>

      <main class="flex-1 overflow-y-auto p-4 sm:p-6 md:p-8 relative scroll-smooth">
        <router-view v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>

    <ConfirmDialog ref="confirmDialog" />
  </div>
</template>

<style scoped>
@keyframes pulse-slow {
    0%, 100% { opacity: 0.3; transform: scale(1); }
    50% { opacity: 0.5; transform: scale(1.1); }
}
.animate-pulse-slow {
    animation: pulse-slow 8s cubic-bezier(0.4, 0, 0.6, 1) infinite;
}

/* Page Transition */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
</style>
