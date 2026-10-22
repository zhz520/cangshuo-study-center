<script setup>
import { ref, onMounted, computed } from "vue";
import { publicApi } from "../api";
import appIcon from "../assets/app_icon.png";
import { useRouter } from "vue-router";

const router = useRouter();
const currentYear = new Date().getFullYear();
const isLoading = ref(true);
const hasError = ref(false);
const appInfo = ref({
  versionName: "",
  updateLog: "",
  downloadUrl: "",
});

const features = [
  { icon: "📚", title: "海量题库", desc: "精选历年真题与模拟题" },
  { icon: "🧠", title: "AI 解析", desc: "实时智能解题，逐步讲解" },
  { icon: "📊", title: "数据报告", desc: "学习进度可视化追踪" },
  { icon: "☁️", title: "云端同步", desc: "多设备无缝切换" },
];

onMounted(async () => {
  try {
    const res = await publicApi.getLatestAppVersion();
    if (res.data.hasUpdate) {
      appInfo.value = {
        versionName: res.data.data.version_name,
        updateLog: res.data.data.update_log,
        downloadUrl: res.data.data.download_url,
      };
    }
  } catch (err) {
    hasError.value = true;
  } finally {
    isLoading.value = false;
  }
});

const updateLogLines = computed(() => {
  return (appInfo.value.updateLog || "").split("\n").filter((l) => l.trim());
});

const handleDownload = () => {
  if (appInfo.value.downloadUrl) {
    window.location.href = appInfo.value.downloadUrl;
  }
};
</script>

<template>
  <div class="page-wrapper landing">
    <!-- ── Particles Background ── -->
    <div class="particles" aria-hidden="true">
      <div
        v-for="i in 12"
        :key="i"
        class="particle"
        :style="{
          left: ((i * 8.5 + 2) % 100) + '%',
          animationDelay: i * 0.4 + 's',
          animationDuration: 10 + (i % 6) + 's',
          width: 2 + (i % 3) + 'px',
          height: 2 + (i % 3) + 'px',
          opacity: 0.1 + (i % 4) * 0.05,
        }"
      ></div>
    </div>

    <!-- ── Ambient Glows ── -->
    <div class="ambient" aria-hidden="true">
      <div class="glow glow-1"></div>
      <div class="glow glow-2"></div>
      <div class="glow glow-3"></div>
    </div>

    <!-- Top Nav -->
    <nav class="top-nav">
      <button class="back-btn glass-card" @click="router.push('/')">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="15 18 9 12 15 6"></polyline>
        </svg>
      </button>
      <div class="nav-brand">
        <img :src="appIcon" alt="烁学" class="nav-logo" />
        <span class="nav-name">下载中心</span>
      </div>
      <div style="width:40px"></div>
    </nav>

    <!-- Hero Section -->
    <main class="content animate-fade-in-up">
      <!-- App Icon + Name -->
      <section class="hero">
        <div class="hero-badge">✨ 官方 Android 版已发布</div>
        
        <!-- App Icon showcase -->
        <div class="hero-icon-showcase">
          <div class="showcase-ring showcase-ring--3"></div>
          <div class="showcase-ring showcase-ring--2"></div>
          <div class="showcase-ring showcase-ring--1"></div>
          <div class="showcase-icon">
            <img :src="appIcon" alt="烁学 App" />
            <div class="rating-badge">★★★★★</div>
          </div>
        </div>

        <h1 class="app-name gradient-text">烁学</h1>
        <p class="app-tagline">专注高效备考的 AI 学习伙伴</p>
        <div class="app-meta">
          <span class="meta-pill">官方正版</span>
          <span class="meta-dot">·</span>
          <span class="meta-text">教育工具</span>
          <span class="meta-dot">·</span>
          <span class="meta-text">Android 8.0+</span>
        </div>
      </section>

      <!-- Download Button -->
      <section class="download-section">
        <div v-if="isLoading" class="loading-state">
          <div class="spinner"></div>
          <p>正在获取最新版本…</p>
        </div>

        <template v-else-if="!hasError && appInfo.downloadUrl">
          <button class="download-btn premium-btn" @click="handleDownload">
            <svg class="download-icon-svg" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
              <polyline points="7 10 12 15 17 10"></polyline>
              <line x1="12" y1="15" x2="12" y2="3"></line>
            </svg>
            <span>立即下载安卓版</span>
            <span v-if="appInfo.versionName" class="version-tag">v{{ appInfo.versionName }}</span>
          </button>
          <p class="platform-note">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor" style="display:inline; vertical-align:middle; margin-right:4px;">
              <path d="M17.523 15.342 7 22V2l10.523 6.658L22 12z" />
            </svg>
            当前支持 Android · iOS 版筹备中
          </p>
        </template>

        <div v-else-if="hasError" class="error-state glass-card p-4">
          <span class="error-icon">⚠️</span>
          <p>获取下载链接失败，请刷新页面重试</p>
        </div>

        <div v-else class="no-version-state glass-card p-4">
          <p>暂无发布版本，请关注后续更新</p>
        </div>
      </section>

      <!-- Divider -->
      <div class="section-divider"></div>

      <!-- Feature Grid -->
      <section class="features-section">
        <h2 class="section-heading">核心功能特性</h2>
        <div class="features-grid">
          <div v-for="f in features" :key="f.title" class="feature-card-modern">
            <div class="feature-icon-wrapper">{{ f.icon }}</div>
            <div class="feature-text">
              <p class="feature-title">{{ f.title }}</p>
              <p class="feature-desc">{{ f.desc }}</p>
            </div>
            <div class="feature-accent"></div>
          </div>
        </div>
      </section>

      <!-- Divider -->
      <div class="section-divider"></div>

      <!-- Update Log -->
      <section v-if="appInfo.versionName && !isLoading" class="changelog-section">
        <div class="changelog-header">
          <h2 class="section-heading">最新更迭</h2>
          <span class="version-badge">Version {{ appInfo.versionName }}</span>
        </div>
        <div class="changelog-card glass-card">
          <ul class="changelog-list">
            <li
              v-for="(line, i) in updateLogLines"
              :key="i"
              class="changelog-item"
            >
              <span class="changelog-dot"></span>
              <span>{{ line.replace(/^[-•·\s]+/, '') }}</span>
            </li>
            <li v-if="updateLogLines.length === 0" class="changelog-item">
              <span class="changelog-dot"></span>
              <span>性能优化及基础体验改进</span>
            </li>
          </ul>
        </div>
      </section>
    </main>

    <!-- Footer -->
    <footer class="page-footer">
      <div class="footer-brand">
        <img :src="appIcon" alt="烁学" class="footer-logo" />
        <span>烁学</span>
      </div>
      <div class="footer-links">
        <router-link to="/agreement">用户协议</router-link>
        <span class="dot">·</span>
        <router-link to="/privacy">隐私政策</router-link>
      </div>
      <p class="footer-copy">© {{ currentYear }} 烁学 Zhzgo Study. All rights reserved.</p>
    </footer>
  </div>
</template>

<style scoped>
/* ── Ported Base Styles from Landing & Home ── */
.landing {
  background: #080810;
  color: #e8e8f0;
  font-family: -apple-system, BlinkMacSystemFont, "SF Pro Display", "SF Pro Text", "Inter", sans-serif;
}

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
  0% { transform: translateY(110vh) scale(0); opacity: 0; }
  10% { opacity: 1; }
  90% { opacity: 1; }
  100% { transform: translateY(-10vh) scale(1); opacity: 0; }
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
  width: 70vw; height: 70vw;
  background: radial-gradient(circle, rgba(79, 70, 229, 0.2), rgba(124, 58, 237, 0.1));
  top: -20%; left: -20%;
}
.glow-2 {
  width: 50vw; height: 50vw;
  background: radial-gradient(circle, rgba(168, 85, 247, 0.15), rgba(236, 72, 153, 0.1));
  bottom: 0; right: -10%;
  animation-delay: -6s;
}
.glow-3 {
  width: 30vw; height: 30vw;
  background: radial-gradient(circle, rgba(6, 182, 212, 0.15), rgba(59, 130, 246, 0.1));
  top: 50%; left: 40%;
  animation-delay: -3s;
}
@keyframes drift {
  from { transform: translate(0, 0) scale(1); }
  to { transform: translate(5%, 8%) scale(1.1); }
}

.gradient-text {
  background: linear-gradient(120deg, #ffffff 30%, #a78bfa 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.glass-card {
  background: rgba(255, 255, 255, 0.03);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 20px;
}

/* ── Nav ── */
.top-nav {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  background: rgba(8, 8, 16, 0.6);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
}
.nav-brand {
  display: flex;
  align-items: center;
  gap: 10px;
}
.nav-logo {
  width: 28px; height: 28px;
  border-radius: 6px;
  filter: invert(1);
}
.nav-name {
  font-size: 1rem;
  font-weight: 800;
  color: #fff;
  letter-spacing: 0.02em;
}
.back-btn {
  width: 38px; height: 38px;
  display: flex; align-items: center; justify-content: center;
  color: rgba(255, 255, 255, 0.6);
  cursor: pointer;
  transition: all 0.2s;
}
.back-btn:hover {
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
  transform: translateX(-2px);
}

/* ── Hero Showcase ── */
.hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 24px 0 32px;
}
.hero-badge {
  display: inline-block;
  background: rgba(99, 102, 241, 0.15);
  border: 1px solid rgba(99, 102, 241, 0.3);
  color: #a78bfa;
  font-size: 0.75rem;
  font-weight: 700;
  padding: 4px 14px;
  border-radius: 100px;
  margin-bottom: 32px;
}

.hero-icon-showcase {
  position: relative;
  width: 140px;
  height: 140px;
  margin-bottom: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.showcase-icon {
  width: 100px;
  height: 100px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  z-index: 10;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(10px);
}
.showcase-icon img {
  width: 60px;
  height: 60px;
  filter: invert(1);
}
.showcase-ring {
  position: absolute;
  border: 1px solid rgba(99, 102, 241, 0.2);
  border-radius: 50%;
  animation: pulse-ring 4s cubic-bezier(0.4, 0, 0.6, 1) infinite;
}
.showcase-ring--1 { width: 120px; height: 120px; animation-delay: 0s; }
.showcase-ring--2 { width: 150px; height: 150px; animation-delay: 1s; }
.showcase-ring--3 { width: 180px; height: 180px; animation-delay: 2s; }

@keyframes pulse-ring {
  0% { transform: scale(0.9); opacity: 0; }
  50% { opacity: 0.5; }
  100% { transform: scale(1.1); opacity: 0; }
}

.rating-badge {
  position: absolute;
  bottom: -6px; right: -6px;
  background: #ffd700;
  color: #000;
  font-size: 10px;
  font-weight: 800;
  padding: 2px 6px;
  border-radius: 6px;
  box-shadow: 0 4px 12px rgba(255, 215, 0, 0.3);
}

.app-name {
  font-size: 2.8rem;
  font-weight: 900;
  margin: 0;
  filter: drop-shadow(0 0 20px rgba(255,255,255,0.1));
}
.app-tagline {
  font-size: 1rem;
  color: rgba(255,255,255,0.5);
  margin-top: 4px;
}
.app-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 16px;
}
.meta-pill {
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
  padding: 3px 12px;
  border-radius: 100px;
  font-size: 0.75rem;
  font-weight: 600;
}
.meta-dot { color: rgba(255, 255, 255, 0.2); }
.meta-text { font-size: 0.8rem; color: rgba(255, 255, 255, 0.4); }

/* ── Premium Button ── */
.premium-btn {
  background: linear-gradient(135deg, #4f46e5, #7c3aed, #a855f7);
  color: #fff;
  border: none;
  border-radius: 18px;
  padding: 16px 32px;
  font-size: 1.05rem;
  font-weight: 800;
  cursor: pointer;
  box-shadow: 0 10px 30px rgba(99, 102, 241, 0.4);
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
  width: 100%;
  max-width: 320px;
}
.premium-btn:hover {
  transform: translateY(-3px) scale(1.02);
  box-shadow: 0 20px 40px rgba(99, 102, 241, 0.5);
}
.premium-btn:active { transform: scale(0.97); }

.download-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 16px 0;
}

.download-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.download-icon-svg {
  width: 22px;
  height: 22px;
  flex-shrink: 0;
}

.version-tag {
  background: rgba(255, 255, 255, 0.2);
  padding: 2px 10px;
  border-radius: 100px;
  font-size: 0.75rem;
  font-weight: 600;
  margin-left: 4px;
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 24px 0;
  color: rgba(255, 255, 255, 0.5);
  font-size: 0.9rem;
}

.error-state,
.no-version-state {
  text-align: center;
  color: rgba(255, 255, 255, 0.5);
  font-size: 0.9rem;
}
.error-icon {
  font-size: 1.5rem;
  display: block;
  margin-bottom: 8px;
}

.platform-note {
  margin-top: 16px;
  font-size: 0.8rem;
  color: rgba(255, 255, 255, 0.3);
}

/* ── Feature Cards ── */
.features-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 14px;
}
.feature-card-modern {
  position: relative;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 20px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  transition: all 0.3s;
  overflow: hidden;
}
.feature-card-modern:hover {
  background: rgba(255, 255, 255, 0.07);
  transform: translateY(-2px);
  border-color: rgba(99, 102, 241, 0.3);
}
.feature-icon-wrapper {
  width: 48px;
  height: 48px;
  background: rgba(99, 102, 241, 0.1);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.6rem;
  flex-shrink: 0;
}
.feature-title {
  font-weight: 800;
  font-size: 1rem;
  color: #fff;
  margin-bottom: 2px;
}
.feature-desc {
  font-size: 0.8rem;
  color: rgba(255, 255, 255, 0.4);
  line-height: 1.5;
}
.feature-accent {
  position: absolute;
  top: 0; left: 0; width: 3px; height: 100%;
  background: #6366f1;
  opacity: 0;
  transition: opacity 0.3s;
}
.feature-card-modern:hover .feature-accent { opacity: 1; }

/* ── Changelog Card ── */
.changelog-card {
  padding: 24px;
  margin-top: 12px;
}
.changelog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.version-badge {
  color: #a78bfa;
  font-size: 0.8rem;
  font-weight: 700;
}
.changelog-list {
  list-style: none;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.changelog-item {
  display: flex;
  gap: 12px;
  font-size: 0.9rem;
  color: rgba(255, 255, 255, 0.6);
  line-height: 1.6;
}
.changelog-dot {
  width: 6px; height: 6px;
  background: #6366f1;
  border-radius: 50%;
  margin-top: 8px;
  flex-shrink: 0;
}

/* ── General ── */
.content {
  position: relative;
  z-index: 10;
  max-width: 500px;
  margin: 0 auto;
  padding: 0 24px 40px;
}
.section-divider {
  height: 1px;
  background: linear-gradient(to right, transparent, rgba(255, 255, 255, 0.1), transparent);
  margin: 40px 0;
}
.section-heading {
  font-size: 1.1rem;
  font-weight: 800;
  color: #fff;
  margin-bottom: 20px;
}

.spinner {
  width: 32px; height: 32px;
  border: 4px solid rgba(255,255,255,0.1);
  border-top-color: #6366f1;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

.page-footer {
  position: relative;
  z-index: 10;
  text-align: center;
  padding: 40px 24px 60px;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
}
.footer-brand {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-weight: 800;
  color: #fff;
  margin-bottom: 12px;
}
.footer-logo { width: 24px; height: 24px; filter: invert(1); }
.footer-links {
  display: flex;
  justify-content: center;
  gap: 10px;
  font-size: 0.8rem;
  color: rgba(255, 255, 255, 0.4);
  margin-bottom: 16px;
}
.footer-links a { color: inherit; text-decoration: none; }
.footer-links a:hover { color: #fff; }
.footer-copy { font-size: 0.75rem; color: rgba(255, 255, 255, 0.2); }

/* ── Animation ── */
.animate-fade-in-up {
  animation: fade-in-up 0.8s cubic-bezier(0.16, 1, 0.3, 1);
}
@keyframes fade-in-up {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

@media (min-width: 640px) {
  .features-grid { grid-template-columns: 1fr 1fr; }
}
</style>
