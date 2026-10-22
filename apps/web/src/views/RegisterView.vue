<script setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { auth } from '../api';
import { verifyGeetest } from '../utils/geetest';
import Toast from '../components/ui/Toast.vue';

const router = useRouter();
const form = ref({
  username: '',
  email: '',
  code: '',
  password: '',
  confirmPassword: ''
});
const error = ref('');
const toast = ref(null);

const countdown = ref(0);
let timer = null;

const handleSendCode = async () => {
  if (!form.value.email) {
    error.value = '请先输入邮箱';
    return;
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.value.email)) {
    error.value = '请输入有效的邮箱格式';
    return;
  }
  
  try {
    const geetestData = await verifyGeetest();
    error.value = '';
    await auth.sendVerificationCode({ email: form.value.email, ...geetestData });
    toast.value?.add('验证码已发送至邮箱', 'success');
    
    countdown.value = 60;
    timer = setInterval(() => {
      countdown.value--;
      if (countdown.value <= 0) {
        clearInterval(timer);
      }
    }, 1000);
  } catch (err) {
    error.value = err.response?.data?.message || err.response?.data?.error || '发送失败';
  }
};

const handleRegister = async () => {
  if (form.value.password !== form.value.confirmPassword) {
    error.value = '两次输入的密码不一致';
    return;
  }
  try {
    const geetestData = await verifyGeetest();
    await auth.register({
      username: form.value.username,
      email: form.value.email,
      code: form.value.code,
      password: form.value.password,
      ...geetestData
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
  <div class="auth-page">
    <section class="auth-brand-panel">
      <RouterLink to="/" class="web-brand"><span class="web-brand-mark">烁</span><span>烁学</span></RouterLink>
      <div class="auth-brand-copy">
        <span class="ui-eyebrow">创建学习空间</span>
        <h1>从一个账号开始，<br />把进步留在这里。</h1>
        <p>注册后可以保存练习进度、错题、收藏和学习统计。邮箱只用于验证和账号安全。</p>
        <div class="auth-brand-points"><div><b>01</b><span>Web 与 Android 学习进度同步</span></div><div><b>02</b><span>不同题库入口共用同一权限边界</span></div><div><b>03</b><span>账号数据由服务端保存</span></div></div>
      </div>
      <span class="ui-small ui-faint">烁学 · Zhzgo Study</span>
    </section>
    <section class="auth-form-panel">
      <div class="auth-card">
        <div class="auth-head"><h2>创建账号</h2><p>填写邮箱并完成验证，开始使用烁学。</p></div>
        <form class="auth-form" @submit.prevent="handleRegister">
          <div class="auth-field"><label for="reg-username">用户名</label><input id="reg-username" v-model="form.username" class="ui-input" type="text" required autocomplete="username" placeholder="建议使用姓名全拼" /></div>
          <div class="auth-field"><label for="reg-email">邮箱</label><input id="reg-email" v-model="form.email" class="ui-input" type="email" required autocomplete="email" placeholder="接收验证码" /></div>
          <div class="auth-field"><label for="reg-code">验证码</label><div class="auth-code-wrap"><input id="reg-code" v-model="form.code" class="ui-input" type="text" required maxlength="6" inputmode="numeric" placeholder="6 位验证码" /><button type="button" class="ui-btn ui-btn-ghost ui-btn-sm auth-code-btn" :disabled="countdown > 0" @click="handleSendCode">{{ countdown > 0 ? `${countdown}s 后重试` : "获取验证码" }}</button></div></div>
          <div class="auth-field"><label for="reg-password">设置密码</label><input id="reg-password" v-model="form.password" class="ui-input" type="password" required autocomplete="new-password" placeholder="至少 8 位，包含字母和数字" /></div>
          <div class="auth-field"><label for="reg-confirm">确认密码</label><input id="reg-confirm" v-model="form.confirmPassword" class="ui-input" type="password" required autocomplete="new-password" placeholder="再次输入密码" /></div>
          <div v-if="error" class="auth-error">{{ error }}</div>
          <button class="ui-btn ui-btn-primary ui-btn-lg auth-submit" type="submit">创建账号</button>
          <p class="ui-small ui-faint text-center">注册即代表同意 <RouterLink to="/agreement" class="font-medium" style="color:var(--primary)">用户协议</RouterLink> 和 <RouterLink to="/privacy" class="font-medium" style="color:var(--primary)">隐私政策</RouterLink></p>
          <div class="auth-foot"><span>已有账号？<RouterLink to="/login" class="ml-1 font-semibold">返回登录</RouterLink></span><RouterLink to="/download">下载 Android App</RouterLink></div>
        </form>
      </div>
    </section>
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
