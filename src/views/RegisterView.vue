<script setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { auth } from '../api';
import Toast from '../components/ui/Toast.vue';

const router = useRouter();
const form = ref({
  username: '',
  password: '',
  confirmPassword: ''
});
const error = ref('');
const toast = ref(null);

const handleRegister = async () => {
  if (form.value.password !== form.value.confirmPassword) {
    error.value = '两次输入的密码不一致';
    return;
  }
  try {
    await auth.register({
      username: form.value.username,
      password: form.value.password
    });
    toast.value?.add('注册成功，请登录', 'success');
    setTimeout(() => router.push('/login'), 1500);
  } catch (err) {
    // If backend returns 403 (Registration Closed), it sends { message: '...' }
    // Other errors might be in { error: '...' } or { message: '...' }
    error.value = err.response?.data?.message || err.response?.data?.error || '注册失败';
  }
};
</script>

<template>
  <div class="flex items-center justify-center min-h-screen bg-slate-50 dark:bg-slate-900 px-6 transition-colors duration-500 relative overflow-hidden">
    <!-- Background Blobs -->
    <div class="absolute top-0 left-1/2 -translate-x-1/2 w-full h-full z-0 pointer-events-none">
        <div class="absolute top-[-10%] right-[-10%] w-[50%] h-[50%] bg-indigo-500/20 rounded-full blur-[100px] animate-pulse-slow"></div>
        <div class="absolute bottom-[-10%] left-[-10%] w-[50%] h-[50%] bg-pink-500/20 rounded-full blur-[100px] animate-pulse-slow" style="animation-delay: 2s"></div>
    </div>

    <div class="w-full max-w-md p-8 sm:p-10 space-y-8 bg-white/80 dark:bg-slate-800/80 backdrop-blur-xl rounded-[32px] shadow-2xl shadow-indigo-500/10 border border-white/50 dark:border-slate-700/50 animate-slide-up relative z-10 transition-all duration-300 hover:shadow-indigo-500/20">
      <div class="text-center space-y-2">
        <div class="w-16 h-16 bg-gradient-to-br from-pink-500 to-rose-500 rounded-2xl mx-auto flex items-center justify-center shadow-lg shadow-pink-500/30 mb-4 transform -rotate-3 hover:-rotate-6 transition-transform">
            <span class="text-3xl">🚀</span>
        </div>
        <h2 class="text-3xl font-black text-slate-800 dark:text-slate-100 tracking-tight">申请注册</h2>
        <p class="text-sm text-slate-500 font-medium">创建一个新账号以开始您的学习之旅</p>
      </div>

      <form @submit.prevent="handleRegister" class="space-y-5">
        <div class="space-y-2">
          <label class="block ml-1 text-xs font-black text-slate-400 uppercase tracking-widest">用户名</label>
          <input v-model="form.username" type="text" required placeholder="建议使用姓名全拼" class="w-full px-5 py-4 bg-slate-50 dark:bg-slate-900/50 border-2 border-slate-100 dark:border-slate-700 rounded-2xl focus:border-indigo-500 focus:ring-4 focus:ring-indigo-500/10 outline-none transition-all dark:text-slate-200 text-sm font-bold placeholder:font-normal">
        </div>
        <div class="space-y-2">
          <label class="block ml-1 text-xs font-black text-slate-400 uppercase tracking-widest">设置密码</label>
          <input v-model="form.password" type="password" required placeholder="建议6位以上字符" class="w-full px-5 py-4 bg-slate-50 dark:bg-slate-900/50 border-2 border-slate-100 dark:border-slate-700 rounded-2xl focus:border-indigo-500 focus:ring-4 focus:ring-indigo-500/10 outline-none transition-all dark:text-slate-200 text-sm font-bold placeholder:font-normal">
        </div>
        <div class="space-y-2">
          <label class="block ml-1 text-xs font-black text-slate-400 uppercase tracking-widest">确认密码</label>
          <input v-model="form.confirmPassword" type="password" required placeholder="再次输入密码" class="w-full px-5 py-4 bg-slate-50 dark:bg-slate-900/50 border-2 border-slate-100 dark:border-slate-700 rounded-2xl focus:border-indigo-500 focus:ring-4 focus:ring-indigo-500/10 outline-none transition-all dark:text-slate-200 text-sm font-bold placeholder:font-normal">
        </div>
        
        <div v-if="error" class="bg-rose-50 dark:bg-rose-900/20 text-rose-500 text-xs py-3 px-4 rounded-xl text-center border border-rose-100 dark:border-rose-900/30 font-bold flex items-center justify-center gap-2">
            <span>⚠️</span> {{ error }}
        </div>
        
        <button type="submit" class="w-full py-4 mt-2 text-white bg-gradient-to-r from-pink-600 to-rose-600 hover:from-pink-700 hover:to-rose-700 rounded-2xl font-bold transition-all shadow-lg shadow-pink-500/30 active:scale-[0.98] flex items-center justify-center gap-2 group">
          <span>注册并登录</span>
          <span class="group-hover:translate-x-1 transition-transform">→</span>
        </button>

        <div class="text-center text-xs text-slate-400 mt-4">
          注册即代表您同意
          <router-link to="/agreement" class="text-pink-500 hover:underline">《用户服务协议》</router-link>
          和
          <router-link to="/privacy" class="text-pink-500 hover:underline">《隐私政策》</router-link>
        </div>
      </form>

      <div class="text-center text-sm font-medium">
        <span class="text-slate-500">已有账号？</span> 
        <router-link to="/login" class="ml-1 text-indigo-500 font-bold hover:text-indigo-600 hover:underline transition-colors">返回登录</router-link>
      </div>
    </div>

    <Toast ref="toast" />
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
