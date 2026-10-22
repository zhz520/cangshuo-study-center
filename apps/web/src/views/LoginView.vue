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
  <div class="auth-page">
    <section class="auth-brand-panel">
      <RouterLink to="/" class="web-brand"><span class="web-brand-mark">烁</span><span>烁学</span></RouterLink>
      <div class="auth-brand-copy">
        <span class="ui-eyebrow">专注每一次进步</span>
        <h1>保持专注，<br />也保持节奏。</h1>
        <p>登录后继续上次的题目、错题和模拟考试。Web 与 Android 共用账号与学习进度。</p>
        <div class="auth-brand-points"><div><b>01</b><span>服务端保存题目与学习权限</span></div><div><b>02</b><span>答案只在授权范围内展示</span></div><div><b>03</b><span>练习断点、错题和收藏可恢复</span></div></div>
      </div>
      <span class="ui-small ui-faint">烁学 · Zhzgo Study</span>
    </section>
    <section class="auth-form-panel">
      <div class="auth-card">
        <div class="auth-head">
          <h2>{{ isForgotPassword ? "重置密码" : "欢迎回来" }}</h2>
          <p>{{ isForgotPassword ? "使用绑定邮箱验证后设置新密码。" : "登录账号，继续你的学习进度。" }}</p>
        </div>

        <form v-if="isForgotPassword" class="auth-form" @submit.prevent="handleResetPassword">
          <div class="auth-field"><label for="forgot-email">绑定邮箱</label><input id="forgot-email" v-model="forgotForm.email" class="ui-input" type="email" required autocomplete="email" placeholder="账号绑定的邮箱" /></div>
          <div class="auth-field"><label for="forgot-code">验证码</label><div class="auth-code-wrap"><input id="forgot-code" v-model="forgotForm.code" class="ui-input" type="text" required maxlength="6" inputmode="numeric" placeholder="6 位验证码" /><button type="button" class="ui-btn ui-btn-ghost ui-btn-sm auth-code-btn" :disabled="countdown > 0" @click="handleSendForgotCode">{{ countdown > 0 ? `${countdown}s 后重试` : "获取验证码" }}</button></div></div>
          <div class="auth-field"><label for="forgot-password">新密码</label><input id="forgot-password" v-model="forgotForm.newPassword" class="ui-input" type="password" required autocomplete="new-password" placeholder="输入新密码" /></div>
          <div v-if="forgotError" class="auth-error">{{ forgotError }}</div>
          <button class="ui-btn ui-btn-primary ui-btn-lg auth-submit" type="submit">重置密码</button>
          <div class="auth-foot"><button type="button" class="ui-btn ui-btn-ghost ui-btn-sm" @click="isForgotPassword = false; forgotError = ''">返回登录</button><RouterLink to="/register">创建账号</RouterLink></div>
        </form>

        <form v-else class="auth-form" @submit.prevent="handleLogin">
          <div class="auth-field"><label for="login-username">用户名</label><input id="login-username" v-model="form.username" class="ui-input" type="text" required autocomplete="username" placeholder="输入用户名" /></div>
          <div class="auth-field"><div class="auth-row-between"><label for="login-password">密码</label><button type="button" class="ui-btn ui-btn-ghost ui-btn-sm" @click="isForgotPassword = true; error = ''">忘记密码？</button></div><input id="login-password" v-model="form.password" class="ui-input" type="password" required autocomplete="current-password" placeholder="输入密码" /></div>
          <div v-if="error" class="auth-error">{{ error }}</div>
          <button class="ui-btn ui-btn-primary ui-btn-lg auth-submit" type="submit">立即登录</button>
          <p class="ui-small ui-faint text-center">登录即代表同意 <RouterLink to="/agreement" class="font-medium" style="color:var(--primary)">用户协议</RouterLink> 和 <RouterLink to="/privacy" class="font-medium" style="color:var(--primary)">隐私政策</RouterLink></p>
          <div class="auth-foot"><span>还没有账号？<RouterLink to="/register" class="ml-1 font-semibold">免费注册</RouterLink></span><button type="button" class="ui-btn ui-btn-ghost ui-btn-sm" @click="router.push('/download')">下载 Android App</button></div>
        </form>
      </div>
    </section>
    <import-toast />
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
