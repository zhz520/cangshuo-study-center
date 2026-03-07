<script setup>
import { ref } from "vue";
import { useRouter } from "vue-router";
import { auth } from "../api";

const router = useRouter();
const form = ref({
  username: "",
  password: "",
});
const error = ref("");

const handleLogin = async () => {
  try {
    const res = await auth.login(form.value);
    localStorage.setItem("token", res.data.token);
    localStorage.setItem("refreshToken", res.data.refreshToken);
    localStorage.setItem("user", JSON.stringify(res.data.user));
    router.push("/home");
  } catch (err) {
    error.value = err.response?.data?.message || "登录失败";
  }
};
</script>

<template>
  <div class="flex items-center justify-center min-h-screen bg-slate-50 dark:bg-slate-900 px-6 transition-colors duration-500 relative overflow-hidden">
    <!-- Background Blobs -->
    <div class="absolute top-0 left-1/2 -translate-x-1/2 w-full h-full z-0 pointer-events-none">
        <div class="absolute top-[-10%] left-[-10%] w-[50%] h-[50%] bg-indigo-500/20 rounded-full blur-[100px] animate-pulse-slow"></div>
        <div class="absolute bottom-[-10%] right-[-10%] w-[50%] h-[50%] bg-purple-500/20 rounded-full blur-[100px] animate-pulse-slow" style="animation-delay: 2s"></div>
    </div>

    <div class="w-full max-w-md p-8 sm:p-10 space-y-8 bg-white/80 dark:bg-slate-800/80 backdrop-blur-xl rounded-[32px] shadow-2xl shadow-indigo-500/10 border border-white/50 dark:border-slate-700/50 animate-slide-up relative z-10 transition-all duration-300 hover:shadow-indigo-500/20">
      <div class="text-center space-y-2">
        <div class="w-16 h-16 bg-gradient-to-br from-indigo-500 to-purple-500 rounded-2xl mx-auto flex items-center justify-center shadow-lg shadow-indigo-500/30 mb-4 transform rotate-3 hover:rotate-6 transition-transform">
            <span class="text-3xl">🎓</span>
        </div>
        <h2 class="text-3xl font-black text-slate-800 dark:text-slate-100 tracking-tight">欢迎回来</h2>
        <p class="text-sm text-slate-500 font-medium">登录您的账号以同步学习进度</p>
      </div>
      
      <form @submit.prevent="handleLogin" class="space-y-6">
        <div class="space-y-2">
          <label class="block ml-1 text-xs font-black text-slate-400 uppercase tracking-widest">用户名</label>
          <div class="relative">
             <input
                v-model="form.username"
                type="text"
                required
                placeholder="输入用户名"
                class="w-full pl-5 pr-5 py-4 bg-slate-50 dark:bg-slate-900/50 border-2 border-slate-100 dark:border-slate-700 rounded-2xl focus:border-indigo-500 focus:ring-4 focus:ring-indigo-500/10 outline-none transition-all dark:text-slate-200 text-sm font-bold placeholder:font-normal"
             />
          </div>
        </div>
        <div class="space-y-2">
          <label class="block ml-1 text-xs font-black text-slate-400 uppercase tracking-widest">密码</label>
          <input
            v-model="form.password"
            type="password"
            required
            placeholder="输入密码"
            class="w-full px-5 py-4 bg-slate-50 dark:bg-slate-900/50 border-2 border-slate-100 dark:border-slate-700 rounded-2xl focus:border-indigo-500 focus:ring-4 focus:ring-indigo-500/10 outline-none transition-all dark:text-slate-200 text-sm font-bold placeholder:font-normal"
          />
        </div>
        
        <div v-if="error" class="bg-rose-50 dark:bg-rose-900/20 text-rose-500 text-xs py-3 px-4 rounded-xl text-center border border-rose-100 dark:border-rose-900/30 font-bold flex items-center justify-center gap-2">
          <span>⚠️</span> {{ error }}
        </div>

        <button
          type="submit"
          class="w-full py-4 mt-2 text-white bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-700 hover:to-purple-700 rounded-2xl font-bold transition-all shadow-lg shadow-indigo-500/30 active:scale-[0.98] flex items-center justify-center gap-2 group"
        >
          <span>立即登录</span>
          <span class="group-hover:translate-x-1 transition-transform">→</span>
        </button>
        
        <div class="text-center text-xs text-slate-400 mt-4">
          登录即代表您同意
          <router-link to="/agreement" class="text-indigo-500 hover:underline">《用户服务协议》</router-link>
          和
          <router-link to="/privacy" class="text-indigo-500 hover:underline">《隐私政策》</router-link>
        </div>
      </form>

      <div class="text-center text-sm font-medium">
        <span class="text-slate-400">还没有账号？</span>
        <router-link to="/register" class="ml-1 text-indigo-500 font-bold hover:text-indigo-600 hover:underline transition-colors block mt-2 sm:inline sm:mt-0"
          >免费注册</router-link
        >
      </div>

      <!-- APP Download Link Section -->
      <div class="mt-6 pt-6 border-t border-slate-100 dark:border-slate-700/50 flex flex-col items-center justify-center">
        <div class="flex items-center gap-2 group cursor-pointer" @click="router.push('/download')">
           <div class="w-8 h-8 rounded-full bg-slate-100 dark:bg-slate-700/50 flex items-center justify-center text-indigo-500 group-hover:bg-indigo-50 dark:group-hover:bg-indigo-500/10 transition-colors">
               <span class="text-xs">⬇️</span>
           </div>
           <span class="text-sm font-bold text-slate-500 group-hover:text-indigo-600 dark:group-hover:text-indigo-400 transition-colors">下载烁学手机版</span>
        </div>
        <p class="text-[10px] text-slate-400 mt-1 uppercase tracking-widest">支持 Android</p>
      </div>
    </div>
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
.animate-slide-up {
    animation: slideUp 0.6s cubic-bezier(0.16, 1, 0.3, 1);
}
@keyframes slideUp {
    from { opacity: 0; transform: translateY(20px); }
    to { opacity: 1; transform: translateY(0); }
}
</style>
