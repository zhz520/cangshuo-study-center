<script setup>
import { ref } from "vue";
import { useRouter } from "vue-router";
import { auth } from "../api";
import { verifyGeetest } from "../utils/geetest";

const router = useRouter();
const form = ref({
  username: "",
  password: "",
});
const forgotForm = ref({
  email: "",
  code: "",
  newPassword: "",
});
const isForgotPassword = ref(false);
const error = ref("");
const forgotError = ref("");
const toast = ref(null);

const countdown = ref(0);
let timer = null;

const handleSendForgotCode = async () => {
  if (!forgotForm.value.email) {
    forgotError.value = "请先输入邮箱";
    return;
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(forgotForm.value.email)) {
    forgotError.value = "请输入有效的邮箱格式";
    return;
  }

  try {
    const geetestData = await verifyGeetest();
    forgotError.value = "";
    await auth.sendVerificationCode({ email: forgotForm.value.email, ...geetestData });
    toast.value?.add("验证码已发送", "success");

    countdown.value = 60;
    timer = setInterval(() => {
      countdown.value--;
      if (countdown.value <= 0) {
        clearInterval(timer);
      }
    }, 1000);
  } catch (err) {
    forgotError.value = err.response?.data?.message || "发送失败";
  }
};

const handleResetPassword = async () => {
  try {
    const geetestData = await verifyGeetest();
    await auth.forgotPassword({ ...forgotForm.value, ...geetestData });
    toast.value?.add("密码重置成功，请使用新密码登录", "success");
    isForgotPassword.value = false;
    form.value.username = "";
    form.value.password = "";
  } catch (err) {
    forgotError.value = err.response?.data?.message || "重置失败";
  }
};

const handleLogin = async () => {
  try {
    const geetestData = await verifyGeetest();
    const res = await auth.login({ ...form.value, ...geetestData });
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
      
      <!-- Forgot Password Form -->
      <form v-if="isForgotPassword" @submit.prevent="handleResetPassword" class="space-y-6">
        <div class="space-y-2">
          <label class="block ml-1 text-xs font-black text-slate-400 uppercase tracking-widest">绑定邮箱</label>
          <input
            v-model="forgotForm.email"
            type="email"
            required
            placeholder="账号绑定的邮箱"
            class="w-full px-5 py-4 bg-slate-50 dark:bg-slate-900/50 border-2 border-slate-100 dark:border-slate-700 rounded-2xl focus:border-indigo-500 focus:ring-4 focus:ring-indigo-500/10 outline-none transition-all dark:text-slate-200 text-sm font-bold placeholder:font-normal"
          />
        </div>
        
        <div class="space-y-2">
          <label class="block ml-1 text-xs font-black text-slate-400 uppercase tracking-widest">验证码</label>
          <div class="flex gap-2 relative">
            <input v-model="forgotForm.code" type="text" required placeholder="6位验证码" maxlength="6" class="flex-1 w-full px-5 py-4 bg-slate-50 dark:bg-slate-900/50 border-2 border-slate-100 dark:border-slate-700 rounded-2xl focus:border-indigo-500 focus:ring-4 focus:ring-indigo-500/10 outline-none transition-all dark:text-slate-200 text-sm font-bold placeholder:font-normal">
            <button type="button" @click="handleSendForgotCode" :disabled="countdown > 0" class="absolute right-2 top-1/2 -translate-y-1/2 px-4 py-2 bg-indigo-50 text-indigo-600 dark:bg-indigo-900/30 dark:text-indigo-400 hover:bg-indigo-100 dark:hover:bg-indigo-900/50 rounded-xl font-bold text-xs transition-colors disabled:opacity-50 disabled:cursor-not-allowed">
              {{ countdown > 0 ? `${countdown}s 后重试` : '获取验证码' }}
            </button>
          </div>
        </div>

        <div class="space-y-2">
          <label class="block ml-1 text-xs font-black text-slate-400 uppercase tracking-widest">新密码</label>
          <input
            v-model="forgotForm.newPassword"
            type="password"
            required
            placeholder="输入新密码"
            class="w-full px-5 py-4 bg-slate-50 dark:bg-slate-900/50 border-2 border-slate-100 dark:border-slate-700 rounded-2xl focus:border-indigo-500 focus:ring-4 focus:ring-indigo-500/10 outline-none transition-all dark:text-slate-200 text-sm font-bold placeholder:font-normal"
          />
        </div>
        
        <div v-if="forgotError" class="bg-rose-50 dark:bg-rose-900/20 text-rose-500 text-xs py-3 px-4 rounded-xl text-center border border-rose-100 dark:border-rose-900/30 font-bold flex items-center justify-center gap-2">
          <span>⚠️</span> {{ forgotError }}
        </div>

        <button
          type="submit"
          class="w-full py-4 mt-2 text-white bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-700 hover:to-purple-700 rounded-2xl font-bold transition-all shadow-lg shadow-indigo-500/30 active:scale-[0.98] flex items-center justify-center gap-2 group"
        >
          <span>重置密码</span>
          <span class="group-hover:translate-x-1 transition-transform">→</span>
        </button>

        <div class="text-center text-sm font-medium mt-4">
          <button type="button" @click="isForgotPassword = false; forgotError = ''" class="text-slate-500 hover:text-slate-700 dark:hover:text-slate-300 transition-colors">取消</button>
        </div>
      </form>

      <!-- Login Form -->
      <form v-else @submit.prevent="handleLogin" class="space-y-6">
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
          <div class="flex justify-between items-center">
            <label class="block ml-1 text-xs font-black text-slate-400 uppercase tracking-widest">密码</label>
            <button type="button" @click="isForgotPassword = true" class="text-xs text-indigo-500 font-bold hover:underline">忘记密码？</button>
          </div>
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

      <div v-show="!isForgotPassword" class="mt-6 pt-6 border-t border-slate-100 dark:border-slate-700/50 flex flex-col items-center justify-center">
        <div class="flex items-center gap-2 group cursor-pointer" @click="router.push('/download')">
           <div class="w-8 h-8 rounded-full bg-slate-100 dark:bg-slate-700/50 flex items-center justify-center text-indigo-500 group-hover:bg-indigo-50 dark:group-hover:bg-indigo-500/10 transition-colors">
               <span class="text-xs">⬇️</span>
           </div>
           <span class="text-sm font-bold text-slate-500 group-hover:text-indigo-600 dark:group-hover:text-indigo-400 transition-colors">下载烁学手机版</span>
        </div>
        <p class="text-[10px] text-slate-400 mt-1 uppercase tracking-widest">支持 Android</p>
      </div>
    </div>
    <import-toast/>
  </div>
</template>

<script>
import Toast from '../components/ui/Toast.vue';
export default {
    components: {
        'import-toast': Toast
    }
}
</script>

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
