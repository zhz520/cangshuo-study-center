<script setup>
import { ref, onMounted, onUnmounted } from "vue";
import { useRouter } from "vue-router";
import appIcon from "../assets/app_icon.png";
import { publicApi } from "../api";

const router = useRouter();
const currentYear = new Date().getFullYear();

// Navbar scroll effect
const scrolled = ref(false);
const handleScroll = () => {
  scrolled.value = window.scrollY > 40;
};
onMounted(async () => {
  window.addEventListener("scroll", handleScroll);
  try {
    const res = await publicApi.getStats();
    if (res.data && res.data.success) {
      stats.value[0].value = res.data.data.questionCount.toLocaleString() + "+";
      stats.value[1].value = res.data.data.userCount.toLocaleString() + "+";
    }
  } catch (e) {
    console.error("Fetch stats error:", e);
  }
});
onUnmounted(() => window.removeEventListener("scroll", handleScroll));

// Mobile menu
const mobileMenuOpen = ref(false);

const features = [
  {
    icon: "📚",
    title: "海量题库",
    desc: "精选历年真题与模拟题，覆盖多专业方向，持续更新扩充",
    color: "#6366f1",
  },
  {
    icon: "🧠",
    title: "AI 智能解析",
    desc: "实时 AI 逐步讲解每道题，不只给答案，让你真正理解",
    color: "#a855f7",
  },
  {
    icon: "📊",
    title: "学情数据报告",
    desc: "可视化追踪学习进度，掌握薄弱点，科学制定复习计划",
    color: "#06b6d4",
  },
  {
    icon: "☁️",
    title: "多端云同步",
    desc: "账号数据实时同步，Web 端与 App 端无缝切换无缝衔接",
    color: "#10b981",
  },
  {
    icon: "⚡",
    title: "自适应练习",
    desc: "根据作答历史智能推题，让每次练习都高效有针对性",
    color: "#f59e0b",
  },
  {
    icon: "🔒",
    title: "安全可靠",
    desc: "双 Token 鉴权机制，数据加密存储，保护你的学习隐私",
    color: "#ec4899",
  },
];

const stats = ref([
  { value: "...", label: "题库数量" },
  { value: "...", label: "注册用户" },
  { value: "98%", label: "用户满意度" },
  { value: "24/7", label: "AI 在线服务" },
]);

const faqs = [
  {
    q: "烁学支持哪些平台？",
    a: "目前支持 Web 浏览器（PC/移动端）和 Android 原生 App，iOS 版本正在加速开发中。",
  },
  {
    q: "题库内容是否免费？",
    a: "免费账号可预览每个科目的前 20 道题；Plus 会员解锁完整题库、更多 AI 解析次数和学习权益。",
  },
  {
    q: "AI 解析是真实 AI 吗？",
    a: "是的，我们接入了大模型 API，每道题都能获得详细的逐步 AI 解析，而不是预设文本。",
  },
  {
    q: "数据安全性如何保障？",
    a: "采用双 Token 鉴权、HTTPS 加密传输及服务端数据加密存储，你的学习数据安全无忧。",
  },
];

const openFaq = ref(null);
const toggleFaq = (i) => {
  openFaq.value = openFaq.value === i ? null : i;
};
</script>

<template>
  <div class="landing" itemscope itemtype="https://schema.org/WebPage">
    <!-- ── Particles Background ── -->
    <div class="particles" aria-hidden="true">
      <div
        v-for="i in 18"
        :key="i"
        class="particle"
        :style="{
          left: ((i * 5.5 + 2) % 100) + '%',
          animationDelay: i * 0.37 + 's',
          animationDuration: 12 + (i % 8) + 's',
          width: 2 + (i % 3) + 'px',
          height: 2 + (i % 3) + 'px',
          opacity: 0.08 + (i % 5) * 0.04,
        }"
      ></div>
    </div>

    <!-- ── Ambient Glows ── -->
    <div class="ambient" aria-hidden="true">
      <div class="glow glow-1"></div>
      <div class="glow glow-2"></div>
      <div class="glow glow-3"></div>
    </div>

    <!-- ──────────────────── NAVBAR ──────────────────── -->
    <nav :class="['navbar', { 'navbar--scrolled': scrolled }]">
      <div class="nav-inner">
      <a class="nav-brand" href="/" aria-label="烁学首页">
          <img :src="appIcon" alt="烁学 - AI 智能刷题平台" class="nav-logo" width="32" height="32" />
          <span class="nav-name">烁学</span>
        </a>

        <ul class="nav-links">
          <li><a href="#features">功能</a></li>
          <li><a href="#stats">数据</a></li>
          <li><a href="#faq">常见问题</a></li>
          <li><a href="/download">下载 App</a></li>
          <li><a href="/score-query" style="color: #a855f7; font-weight: bold;">联考成绩查询</a></li>
        </ul>

        <div class="nav-actions">
          <button class="btn-ghost" @click="router.push('/login')">登录</button>
          <button class="btn-primary nav-cta" @click="router.push('/register')">
            免费注册
          </button>
        </div>

        <!-- Mobile hamburger -->
        <button
          class="hamburger"
          @click="mobileMenuOpen = !mobileMenuOpen"
          aria-label="菜单"
        >
          <span :class="{ open: mobileMenuOpen }"></span>
          <span :class="{ open: mobileMenuOpen }"></span>
          <span :class="{ open: mobileMenuOpen }"></span>
        </button>
      </div>

      <!-- Mobile Dropdown -->
      <Transition name="mobile-menu-slide">
        <div v-show="mobileMenuOpen" class="mobile-menu">
          <a href="#features" @click="mobileMenuOpen = false">功能</a>
          <a href="#stats" @click="mobileMenuOpen = false">数据</a>
          <a href="#faq" @click="mobileMenuOpen = false">常见问题</a>
          <a href="/download" @click="mobileMenuOpen = false">下载 App</a>
          <a href="/score-query" @click="mobileMenuOpen = false" style="color: #a855f7; font-weight: bold;">联考成绩查询</a>
          <div class="mobile-actions">
            <button class="btn-ghost" @click="router.push('/login')">
              登录
            </button>
            <button class="btn-primary" @click="router.push('/register')">
              免费注册
            </button>
          </div>
        </div>
      </Transition>
    </nav>

    <!-- ──────────────────── HERO ──────────────────── -->
    <section class="hero" role="banner" aria-label="烁学主题横幅">
      <div class="hero-inner">
        <div class="hero-badge">🎉 AI 驱动的学习新方式</div>
        <h1 class="hero-title">
          学习更高效<br />
          <span class="gradient-text">让 AI 成为你的老师</span>
        </h1>
        <p class="hero-sub">
          烁学提供海量题库 · AI 实时解析 · 学情数据分析<br />
          帮助你告别低效刷题，科学备考，稳步提升
        </p>
        <div class="hero-ctas">
          <button class="btn-primary btn-lg" @click="router.push('/register')">
            <span>立即免费使用</span>
            <svg
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2.5"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="m9 18 6-6-6-6" />
            </svg>
          </button>
          <a href="/download" class="btn-outline btn-lg">
            <svg
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2.2"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
              <polyline points="7 10 12 15 17 10" />
              <line x1="12" y1="15" x2="12" y2="3" />
            </svg>
            <span>下载安卓版</span>
          </a>
        </div>

        <!-- App Icon showcase -->
        <div class="hero-icon-showcase">
          <div class="showcase-ring showcase-ring--3"></div>
          <div class="showcase-ring showcase-ring--2"></div>
          <div class="showcase-ring showcase-ring--1"></div>
          <div class="showcase-icon">
            <img :src="appIcon" alt="烁学 App 图标 - AI 智能刷题平台" width="120" height="120" />
          </div>
        </div>

        <p class="hero-platforms">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
            <path
              d="M17.05 20.28c-.98.95-2.05.8-3.08.35-1.09-.46-2.09-.48-3.24 0-1.44.62-2.2.44-3.06-.35C2.79 15.25 3.51 7.7 9.05 7.4c1.39.07 2.36.78 3.18.8 1.22-.26 2.38-.99 3.65-.84 1.56.18 2.72.86 3.47 2.17-3.17 1.93-2.43 6.02.7 7.16-.58 1.39-1.28 2.77-3 3.59zM12.03 7.25c-.15-2.23 1.66-4.07 3.74-4.25.29 2.58-2.34 4.5-3.74 4.25z"
            />
          </svg>
          iOS 版即将推出 &nbsp;·&nbsp;
          <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
            <path d="M17.523 15.342 7 22V2l10.523 6.658L22 12z" />
          </svg>
          Android 版已发布 &nbsp;·&nbsp;
          <svg
            width="14"
            height="14"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
          >
            <rect x="2" y="3" width="20" height="14" rx="2" />
            <path d="M8 21h8M12 17v4" />
          </svg>
          Web 端随时使用
        </p>
      </div>
    </section>

    <!-- ──────────────────── STATS ──────────────────── -->
    <section id="stats" class="stats-section">
      <div class="section-inner">
        <div class="stats-grid">
          <div v-for="s in stats" :key="s.label" class="stat-card">
            <div class="stat-value">{{ s.value }}</div>
            <div class="stat-label">{{ s.label }}</div>
          </div>
        </div>
      </div>
    </section>

    <!-- ──────────────────── FEATURES ──────────────────── -->
    <section id="features" class="features-section" aria-labelledby="features-title">
      <div class="section-inner">
        <div class="section-tag">核心功能</div>
        <h2 id="features-title" class="section-title">为什么选择烁学</h2>
        <p class="section-sub">
          专为需要高效备考的同学打造，每一项功能都直指痛点
        </p>
        <div class="features-grid">
          <div v-for="f in features" :key="f.title" class="feature-card">
            <div
              class="feature-icon"
              :style="{
                background: f.color + '1a',
                border: '1px solid ' + f.color + '40',
              }"
            >
              <span>{{ f.icon }}</span>
            </div>
            <h3 class="feature-title">{{ f.title }}</h3>
            <p class="feature-desc">{{ f.desc }}</p>
            <div
              class="feature-accent"
              :style="{
                background:
                  'linear-gradient(90deg, ' + f.color + ', transparent)',
              }"
            ></div>
          </div>
        </div>
      </div>
    </section>

    <!-- ──────────────────── HOW IT WORKS ──────────────────── -->
    <section class="how-section">
      <div class="section-inner">
        <div class="section-tag">使用流程</div>
        <h2 class="section-title">三步开始高效学习</h2>
        <div class="steps-row">
          <div class="step-card">
            <div class="step-num">01</div>
            <div class="step-body">
              <h3>注册账号</h3>
              <p>
                填写用户名和密码，30 秒即可完成注册，也可通过 Web 端直接开始
              </p>
            </div>
          </div>
          <div class="step-arrow">→</div>
          <div class="step-card">
            <div class="step-num">02</div>
            <div class="step-body">
              <h3>选择科目</h3>
              <p>从题库中选择你需要备考的专业科目，开始个性化刷题训练</p>
            </div>
          </div>
          <div class="step-arrow">→</div>
          <div class="step-card">
            <div class="step-num">03</div>
            <div class="step-body">
              <h3>AI 解析提升</h3>
              <p>遇到不会的题目，AI 实时给出详细解析，真正理解而非死记硬背</p>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ──────────────────── CTA BANNER ──────────────────── -->
    <section class="cta-section">
      <div class="cta-glow cta-glow-l"></div>
      <div class="cta-glow cta-glow-r"></div>
      <div class="section-inner cta-inner">
        <div class="cta-icon">
          <img :src="appIcon" alt="烁学" width="64" height="64" />
        </div>
        <h2 class="cta-title">立即开始你的 AI 学习之旅</h2>
        <p class="cta-sub">
          Web 端免费使用 · Android App 免费下载 · iOS 即将上线
        </p>
        <div class="cta-btns">
          <button class="btn-primary btn-lg" @click="router.push('/register')">
            免费注册 Web 端
          </button>
          <a href="/download" class="btn-outline btn-lg">下载 Android App</a>
        </div>
      </div>
    </section>

    <!-- ──────────────────── FAQ ──────────────────── -->
    <section id="faq" class="faq-section" aria-labelledby="faq-title">
      <div class="section-inner">
        <div class="section-tag">常见问题</div>
        <h2 id="faq-title" class="section-title">你可能想知道</h2>
        <div class="faq-list">
          <div
            v-for="(item, i) in faqs"
            :key="i"
            class="faq-item"
            :class="{ 'faq-item--open': openFaq === i }"
            @click="toggleFaq(i)"
          >
            <div class="faq-q">
              <span>{{ item.q }}</span>
              <svg
                class="faq-chevron"
                width="18"
                height="18"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2.5"
                stroke-linecap="round"
              >
                <path d="m6 9 6 6 6-6" />
              </svg>
            </div>
            <div class="faq-a">
              <p>{{ item.a }}</p>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ──────────────────── FOOTER ──────────────────── -->
    <footer class="footer" role="contentinfo">
      <div class="footer-inner">
        <div class="footer-brand">
          <img :src="appIcon" alt="烁学" class="footer-logo" width="28" height="28" />
          <span>烁学</span>
        </div>
        <p class="footer-tagline">你的 AI 学习伙伴，让备考更高效</p>
        <div class="footer-links">
          <router-link to="/agreement">用户协议</router-link>
          <span class="dot">·</span>
          <router-link to="/privacy">隐私政策</router-link>
          <span class="dot">·</span>
          <router-link to="/download">下载 App</router-link>
          <span class="dot">·</span>
          <router-link to="/login">登录</router-link>
        </div>
        <p class="footer-copy">
          © {{ currentYear }} 烁学. All rights reserved.
        </p>
        <div class="footer-filing">
          <a
            href="https://beian.miit.gov.cn/"
            target="_blank"
            class="filing-item"
          >
            <svg
              width="13"
              height="13"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
            </svg>
            蜀ICP备2025126838号-1
          </a>
          <a
            href="http://www.beian.gov.cn/portal/registerSystemInfo?recordcode=51172202000160"
            target="_blank"
            class="filing-item"
          >
            <svg width="13" height="13" viewBox="0 0 24 24" fill="currentColor">
              <path
                d="M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4zm-1 9h2v2h-2V10zm0 4h2v4h-2v-4z"
              />
            </svg>
            川公网安备51172202000160号
          </a>
        </div>
      </div>
    </footer>
  </div>
</template>

<style scoped>
/* ═══════════════════════════════ BASE ════════════════════════════════ */
*,
*::before,
*::after {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

.landing {
  min-height: 100vh;
  min-height: 100dvh;
  background: #080810;
  color: #e8e8f0;
  font-family:
    -apple-system, BlinkMacSystemFont, "SF Pro Display", "Segoe UI", "Inter",
    sans-serif;
  position: relative;
  overflow-x: hidden;
}

/* ═══════════════════════════════ BACKGROUND ══════════════════════════ */
.particles {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}
.particle {
  position: absolute;
  background: #6366f1;
  border-radius: 50%;
  animation: float linear infinite;
}
@keyframes float {
  0% {
    transform: translateY(110vh) scale(0);
    opacity: 0;
  }
  10% {
    opacity: 1;
  }
  90% {
    opacity: 1;
  }
  100% {
    transform: translateY(-10vh) scale(1);
    opacity: 0;
  }
}

.ambient {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
  overflow: hidden;
}
.glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(100px);
  animation: drift 16s ease-in-out infinite alternate;
}
.glow-1 {
  width: 70vw;
  height: 70vw;
  background: radial-gradient(circle, #4f46e530, #7c3aed20);
  top: -20%;
  left: -20%;
}
.glow-2 {
  width: 50vw;
  height: 50vw;
  background: radial-gradient(circle, #a855f720, #ec489915);
  bottom: 0;
  right: -10%;
  animation-delay: -6s;
  animation-direction: alternate-reverse;
}
.glow-3 {
  width: 30vw;
  height: 30vw;
  background: radial-gradient(circle, #06b6d420, #3b82f615);
  top: 50%;
  left: 40%;
  animation-delay: -3s;
}
@keyframes drift {
  from {
    transform: translate(0, 0) scale(1);
  }
  to {
    transform: translate(5%, 8%) scale(1.1);
  }
}

/* ═══════════════════════════════ HELPERS ═════════════════════════════ */
.gradient-text {
  background: linear-gradient(120deg, #a78bfa, #60a5fa, #34d399);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}
.section-inner {
  max-width: 1100px;
  margin: 0 auto;
  padding: 0 24px;
  position: relative;
  z-index: 1;
}
.section-tag {
  display: inline-block;
  background: rgba(99, 102, 241, 0.15);
  border: 1px solid rgba(99, 102, 241, 0.35);
  color: #a78bfa;
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0.12em;
  padding: 4px 14px;
  border-radius: 100px;
  text-transform: uppercase;
  margin-bottom: 16px;
}
.section-title {
  font-size: clamp(1.8rem, 4vw, 2.8rem);
  font-weight: 800;
  color: #fff;
  letter-spacing: -0.03em;
  margin-bottom: 12px;
}
.section-sub {
  font-size: 1rem;
  color: rgba(255, 255, 255, 0.45);
  max-width: 560px;
  line-height: 1.7;
  margin-bottom: 56px;
}

/* ═══════════════════════════════ BUTTONS ═════════════════════════════ */
.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  background: linear-gradient(135deg, #4f46e5, #7c3aed, #a855f7);
  color: #fff;
  border: none;
  border-radius: 12px;
  padding: 13px 28px;
  font-size: 0.95rem;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 4px 24px rgba(99, 102, 241, 0.45);
  transition: all 0.22s cubic-bezier(0.34, 1.56, 0.64, 1);
  text-decoration: none;
  white-space: nowrap;
}
.btn-primary:hover {
  transform: translateY(-2px) scale(1.02);
  box-shadow: 0 8px 36px rgba(99, 102, 241, 0.6);
}
.btn-primary:active {
  transform: scale(0.97);
}

.btn-outline {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  background: rgba(255, 255, 255, 0.06);
  color: rgba(255, 255, 255, 0.85);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 12px;
  padding: 13px 28px;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  text-decoration: none;
  backdrop-filter: blur(10px);
  transition: all 0.2s;
  white-space: nowrap;
}
.btn-outline:hover {
  background: rgba(255, 255, 255, 0.11);
  border-color: rgba(255, 255, 255, 0.25);
  transform: translateY(-1px);
}

.btn-ghost {
  background: none;
  border: none;
  color: rgba(255, 255, 255, 0.65);
  font-size: 0.9rem;
  font-weight: 600;
  cursor: pointer;
  padding: 8px 16px;
  border-radius: 8px;
  transition: all 0.15s;
  text-decoration: none;
}
.btn-ghost:hover {
  color: #fff;
  background: rgba(255, 255, 255, 0.07);
}

.btn-lg {
  padding: 16px 36px;
  font-size: 1rem;
  border-radius: 14px;
}

/* ═══════════════════════════════ NAVBAR ══════════════════════════════ */
.navbar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 200;
  transition: all 0.3s;
}
.navbar--scrolled {
  background: rgba(8, 8, 16, 0.85);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.07);
  box-shadow: 0 4px 32px rgba(0, 0, 0, 0.3);
}
.nav-inner {
  max-width: 1100px;
  margin: 0 auto;
  padding: 18px 24px;
  display: flex;
  align-items: center;
  gap: 40px;
  overflow: hidden;
}
.nav-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
  flex-shrink: 0;
}
.nav-logo {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: transparent;
  object-fit: contain;
  filter: invert(1);
}
.nav-name {
  font-size: 1.1rem;
  font-weight: 800;
  color: #fff;
  letter-spacing: -0.02em;
}
.nav-links {
  display: flex;
  list-style: none;
  gap: 32px;
  flex: 1;
}
.nav-links a {
  color: rgba(255, 255, 255, 0.55);
  text-decoration: none;
  font-size: 0.9rem;
  font-weight: 500;
  transition: color 0.15s;
}
.nav-links a:hover {
  color: #fff;
}
.nav-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.nav-cta {
  padding: 10px 20px;
  font-size: 0.88rem;
  border-radius: 10px;
}

/* Hamburger */
.hamburger {
  display: none;
  flex-direction: column;
  gap: 5px;
  background: none;
  border: none;
  cursor: pointer;
  padding: 4px;
  margin-left: auto;
}
.hamburger span {
  display: block;
  width: 22px;
  height: 2px;
  background: rgba(255, 255, 255, 0.7);
  border-radius: 2px;
  transition: all 0.25s;
}
.hamburger span.open:nth-child(1) {
  transform: translateY(7px) rotate(45deg);
}
.hamburger span.open:nth-child(2) {
  opacity: 0;
}
.hamburger span.open:nth-child(3) {
  transform: translateY(-7px) rotate(-45deg);
}

.mobile-menu {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 24px 20px;
  background: rgba(8, 8, 16, 0.96);
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  transform-origin: top;
}

/* Vue Transition: mobile menu slide down */
.mobile-menu-slide-enter-active {
  animation: menu-slide-in 0.25s ease;
}
.mobile-menu-slide-leave-active {
  animation: menu-slide-in 0.18s ease reverse;
}
@keyframes menu-slide-in {
  from {
    opacity: 0;
    transform: translateY(-8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.mobile-menu a {
  color: rgba(255, 255, 255, 0.65);
  text-decoration: none;
  font-size: 1rem;
  font-weight: 500;
  padding: 12px 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  transition: color 0.15s;
}
.mobile-menu a:hover {
  color: #fff;
}
.mobile-actions {
  display: flex;
  gap: 10px;
  margin-top: 16px;
}
.mobile-actions .btn-primary,
.mobile-actions .btn-ghost {
  flex: 1;
  justify-content: center;
}

/* ═══════════════════════════════ HERO ════════════════════════════════ */
.hero {
  position: relative;
  z-index: 1;
  padding: 160px 0 100px;
  text-align: center;
}
.hero-inner {
  max-width: 760px;
  margin: 0 auto;
  padding: 0 24px;
}
.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: rgba(99, 102, 241, 0.12);
  border: 1px solid rgba(99, 102, 241, 0.3);
  color: #a78bfa;
  font-size: 0.82rem;
  font-weight: 600;
  padding: 6px 16px;
  border-radius: 100px;
  margin-bottom: 28px;
  letter-spacing: 0.02em;
}
.hero-title {
  font-size: clamp(2.5rem, 7vw, 4.5rem);
  font-weight: 900;
  color: #fff;
  letter-spacing: -0.04em;
  line-height: 1.1;
  margin-bottom: 24px;
}
.hero-sub {
  font-size: clamp(1rem, 2vw, 1.15rem);
  color: rgba(255, 255, 255, 0.45);
  line-height: 1.8;
  margin-bottom: 40px;
}
.hero-ctas {
  display: flex;
  gap: 16px;
  justify-content: center;
  flex-wrap: wrap;
  margin-bottom: 64px;
}

/* Icon Showcase */
.hero-icon-showcase {
  position: relative;
  width: 120px;
  height: 120px;
  margin: 0 auto 32px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.showcase-ring {
  position: absolute;
  border-radius: 50%;
  border: 1px solid rgba(99, 102, 241, 0.25);
  animation: pulse-ring 3s ease-out infinite;
}
.showcase-ring--1 {
  width: 120px;
  height: 120px;
  animation-delay: 0s;
}
.showcase-ring--2 {
  width: 160px;
  height: 160px;
  animation-delay: 0.8s;
}
.showcase-ring--3 {
  width: 200px;
  height: 200px;
  animation-delay: 1.6s;
}
@keyframes pulse-ring {
  0% {
    transform: scale(0.85);
    opacity: 0.5;
  }
  50% {
    opacity: 0.2;
  }
  100% {
    transform: scale(1.05);
    opacity: 0;
  }
}
.showcase-icon {
  width: 88px;
  height: 88px;
  background: linear-gradient(
    145deg,
    rgba(99, 102, 241, 0.2),
    rgba(139, 92, 246, 0.12)
  );
  border-radius: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow:
    0 0 0 1px rgba(99, 102, 241, 0.35),
    0 0 50px rgba(99, 102, 241, 0.3),
    0 20px 60px rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(12px);
  position: relative;
  z-index: 1;
}
.showcase-icon img {
  width: 60px;
  height: 60px;
  object-fit: contain;
  filter: invert(1) drop-shadow(0 2px 10px rgba(99, 102, 241, 0.5));
}

.hero-platforms {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 0.78rem;
  color: rgba(255, 255, 255, 0.25);
  flex-wrap: wrap;
}
.hero-platforms svg {
  vertical-align: middle;
}

/* ═══════════════════════════════ STATS ═══════════════════════════════ */
.stats-section {
  position: relative;
  z-index: 1;
  padding: 0 0 80px;
}
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  justify-content: center;
  align-items: stretch;
  margin: 0 auto;
}
.stat-card {
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 20px;
  padding: 32px 20px;
  text-align: center;
  transition: all 0.2s;
  backdrop-filter: blur(10px);
}
.stat-card:hover {
  background: rgba(255, 255, 255, 0.07);
  border-color: rgba(99, 102, 241, 0.3);
  transform: translateY(-3px);
}
.stat-value {
  font-size: 2.2rem;
  font-weight: 900;
  background: linear-gradient(120deg, #a78bfa, #60a5fa);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  letter-spacing: -0.03em;
  margin-bottom: 6px;
}
.stat-label {
  font-size: 0.85rem;
  color: rgba(255, 255, 255, 0.4);
  font-weight: 500;
}

/* ═══════════════════════════════ FEATURES ════════════════════════════ */
.features-section {
  position: relative;
  z-index: 1;
  padding: 80px 0;
}
.features-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}
.feature-card {
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.07);
  border-radius: 20px;
  padding: 28px 24px;
  position: relative;
  overflow: hidden;
  transition: all 0.25s;
}
.feature-card:hover {
  background: rgba(255, 255, 255, 0.07);
  transform: translateY(-4px);
}
.feature-icon {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.6rem;
  margin-bottom: 18px;
}
.feature-title {
  font-size: 1rem;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.9);
  margin-bottom: 8px;
}
.feature-desc {
  font-size: 0.85rem;
  color: rgba(255, 255, 255, 0.38);
  line-height: 1.65;
}
.feature-accent {
  position: absolute;
  bottom: 0;
  left: 0;
  height: 2px;
  width: 60%;
  opacity: 0.5;
}

/* ═══════════════════════════════ HOW IT WORKS ════════════════════════ */
.how-section {
  position: relative;
  z-index: 1;
  padding: 80px 0;
  text-align: center;
}
.steps-row {
  display: flex;
  align-items: center;
  gap: 20px;
}
.step-arrow {
  font-size: 1.5rem;
  color: rgba(99, 102, 241, 0.5);
  flex-shrink: 0;
  font-weight: 300;
}
.step-card {
  flex: 1;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.07);
  border-radius: 20px;
  padding: 32px 24px;
  text-align: left;
  transition: all 0.25s;
}
.step-card:hover {
  background: rgba(255, 255, 255, 0.07);
  transform: translateY(-3px);
}
.step-num {
  font-size: 2.5rem;
  font-weight: 900;
  background: linear-gradient(135deg, #4f46e5, #a855f7);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  line-height: 1;
  margin-bottom: 16px;
}
.step-body h3 {
  font-size: 1rem;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.9);
  margin-bottom: 8px;
}
.step-body p {
  font-size: 0.85rem;
  color: rgba(255, 255, 255, 0.38);
  line-height: 1.65;
}

/* ═══════════════════════════════ CTA BANNER ══════════════════════════ */
.cta-section {
  position: relative;
  z-index: 1;
  padding: 100px 0;
  text-align: center;
  overflow: hidden;
}
.cta-glow {
  position: absolute;
  width: 50vw;
  height: 50vw;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.15;
  pointer-events: none;
}
.cta-glow-l {
  background: #6366f1;
  top: -20%;
  left: -10%;
}
.cta-glow-r {
  background: #a855f7;
  bottom: -20%;
  right: -10%;
}
.cta-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.cta-icon {
  width: 80px;
  height: 80px;
  background: linear-gradient(
    145deg,
    rgba(99, 102, 241, 0.2),
    rgba(139, 92, 246, 0.12)
  );
  border-radius: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 28px;
  box-shadow:
    0 0 0 1px rgba(99, 102, 241, 0.35),
    0 0 40px rgba(99, 102, 241, 0.3),
    0 20px 60px rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(12px);
}
.cta-icon img {
  width: 52px;
  height: 52px;
  object-fit: contain;
  filter: invert(1) drop-shadow(0 2px 8px rgba(99, 102, 241, 0.4));
}
.cta-title {
  font-size: clamp(1.8rem, 4vw, 2.6rem);
  font-weight: 900;
  color: #fff;
  letter-spacing: -0.03em;
  margin-bottom: 12px;
}
.cta-sub {
  font-size: 0.95rem;
  color: rgba(255, 255, 255, 0.4);
  margin-bottom: 36px;
}
.cta-btns {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  justify-content: center;
}

/* ═══════════════════════════════ FAQ ═════════════════════════════════ */
.faq-section {
  position: relative;
  z-index: 1;
  padding: 80px 0;
}
.faq-list {
  max-width: 720px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.faq-item {
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 16px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.2s;
}
.faq-item:hover,
.faq-item--open {
  border-color: rgba(99, 102, 241, 0.35);
  background: rgba(255, 255, 255, 0.06);
}
.faq-q {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  font-size: 0.95rem;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.85);
}
.faq-chevron {
  flex-shrink: 0;
  color: rgba(99, 102, 241, 0.7);
  transition: transform 0.25s;
}
.faq-item--open .faq-chevron {
  transform: rotate(180deg);
}
.faq-a {
  max-height: 0;
  overflow: hidden;
  transition:
    max-height 0.3s ease,
    padding 0.25s;
}
.faq-item--open .faq-a {
  max-height: 200px;
}
.faq-a p {
  padding: 0 24px 20px;
  font-size: 0.9rem;
  color: rgba(255, 255, 255, 0.4);
  line-height: 1.7;
}

/* ═══════════════════════════════ FOOTER ══════════════════════════════ */
.footer {
  position: relative;
  z-index: 1;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  padding: 48px 24px 40px;
  text-align: center;
}
.footer-inner {
  max-width: 1100px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
}
.footer-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 1.15rem;
  font-weight: 800;
  color: #fff;
}
.footer-logo {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  object-fit: contain;
  background: transparent;
  filter: invert(1);
}
.footer-tagline {
  font-size: 0.85rem;
  color: rgba(255, 255, 255, 0.3);
}
.footer-links {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: center;
}
.footer-links a {
  color: rgba(255, 255, 255, 0.3);
  text-decoration: none;
  font-size: 0.82rem;
  transition: color 0.15s;
}
.footer-links a:hover {
  color: rgba(255, 255, 255, 0.7);
}
.dot {
  color: rgba(255, 255, 255, 0.15);
}
.footer-copy {
  font-size: 0.75rem;
  color: rgba(255, 255, 255, 0.15);
}

/* Filing Info */
.footer-filing {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 7px;
  padding-top: 4px;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  width: 100%;
  max-width: 400px;
}
.filing-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: rgba(255, 255, 255, 0.28);
  text-decoration: none;
  font-size: 0.72rem;
  transition: color 0.15s;
  letter-spacing: 0.01em;
}
.filing-item:hover {
  color: rgba(255, 255, 255, 0.65);
}
.filing-item svg {
  opacity: 0.6;
  flex-shrink: 0;
}

/* ═══════════════════════════════ RESPONSIVE ══════════════════════════ */
@media (max-width: 768px) {
  .nav-links,
  .nav-actions {
    display: none;
    width: 0;
    min-width: 0;
    overflow: hidden;
    flex: 0;
  }
  .hamburger {
    display: flex;
  }
  .stats-grid {
    grid-template-columns: 1fr 1fr;
  }
  .features-grid {
    grid-template-columns: 1fr;
  }
  .steps-row {
    flex-direction: column;
  }
  .step-arrow {
    transform: rotate(90deg);
  }
}
@media (max-width: 480px) {
  .stats-grid {
    grid-template-columns: 1fr 1fr;
  }
  .hero-ctas {
    flex-direction: column;
    align-items: center;
  }
  .hero-ctas .btn-lg {
    width: 100%;
    max-width: 300px;
    justify-content: center;
  }
  .cta-btns {
    flex-direction: column;
    align-items: center;
  }
  .cta-btns a,
  .cta-btns button {
    width: 100%;
    max-width: 300px;
    justify-content: center;
  }
}
</style>
