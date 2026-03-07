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
  <div class="page-wrapper">
    <!-- Ambient background -->
    <div class="ambient-bg">
      <div class="blob blob-1"></div>
      <div class="blob blob-2"></div>
      <div class="blob blob-3"></div>
    </div>

    <!-- Top Nav -->
    <nav class="top-nav">
      <button class="back-btn" @click="router.push('/')">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="15 18 9 12 15 6"></polyline>
        </svg>
      </button>
      <span class="nav-title">下载应用</span>
      <div style="width:40px"></div>
    </nav>

    <!-- Hero Section -->
    <main class="content">
      <!-- App Icon + Name -->
      <section class="hero">
        <div class="app-icon-wrapper">
          <div class="app-icon">
            <img :src="appIcon" class="icon-img" alt="烁学 App 图标" />
          </div>
          <div class="rating-badge">
            <span>★★★★★</span>
          </div>
        </div>
        <h1 class="app-name">烁学</h1>
        <p class="app-tagline">你的 AI 学习伙伴</p>
        <div class="app-meta">
          <span class="meta-pill">免费</span>
          <span class="meta-dot">·</span>
          <span class="meta-text">教育</span>
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
          <button class="download-btn" @click="handleDownload">
            <svg class="download-icon-svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
              <polyline points="7 10 12 15 17 10"></polyline>
              <line x1="12" y1="15" x2="12" y2="3"></line>
            </svg>
            <span>下载安卓版</span>
            <span v-if="appInfo.versionName" class="version-tag">v{{ appInfo.versionName }}</span>
          </button>
          <p class="platform-note">仅支持 Android · 正在开发 iOS 版</p>
        </template>

        <div v-else-if="hasError" class="error-state">
          <span class="error-icon">⚠️</span>
          <p>获取下载链接失败，请稍后重试</p>
        </div>

        <div v-else class="no-version-state">
          <p>暂无发布版本，敬请期待</p>
        </div>
      </section>

      <!-- Divider -->
      <div class="section-divider"></div>

      <!-- Feature Grid -->
      <section class="features-section">
        <h2 class="section-heading">为什么选择烁学</h2>
        <div class="features-grid">
          <div v-for="f in features" :key="f.title" class="feature-card">
            <div class="feature-icon">{{ f.icon }}</div>
            <div class="feature-text">
              <p class="feature-title">{{ f.title }}</p>
              <p class="feature-desc">{{ f.desc }}</p>
            </div>
          </div>
        </div>
      </section>

      <!-- Divider -->
      <div class="section-divider"></div>

      <!-- Update Log -->
      <section v-if="appInfo.versionName && !isLoading" class="changelog-section">
        <div class="changelog-header">
          <h2 class="section-heading" style="margin-bottom:0">更新日志</h2>
          <span class="version-badge">v{{ appInfo.versionName }}</span>
        </div>
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
            <span>包含体验优化与问题修复</span>
          </li>
        </ul>
      </section>
    </main>

    <!-- Footer -->
    <footer class="page-footer">
      <p>© {{ currentYear }} 烁学. All rights reserved.</p>
      <div class="footer-links">
        <router-link to="/agreement">用户协议</router-link>
        <span>·</span>
        <router-link to="/privacy">隐私政策</router-link>
      </div>
    </footer>
  </div>
</template>

<style scoped>
/* ── Base ── */
.page-wrapper {
  min-height: 100dvh;
  background: #0a0a0f;
  color: #f0f0f5;
  display: flex;
  flex-direction: column;
  position: relative;
  overflow-x: hidden;
  font-family: -apple-system, BlinkMacSystemFont, "SF Pro Display", "Segoe UI", sans-serif;
}

/* ── Ambient glows ── */
.ambient-bg {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
  overflow: hidden;
}
.blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.25;
}
.blob-1 {
  width: 60vw; height: 60vw;
  background: radial-gradient(circle, #6366f1, #8b5cf6);
  top: -15%; left: -15%;
  animation: drift 12s ease-in-out infinite alternate;
}
.blob-2 {
  width: 50vw; height: 50vw;
  background: radial-gradient(circle, #a855f7, #ec4899);
  bottom: 5%; right: -10%;
  animation: drift 15s ease-in-out infinite alternate-reverse;
}
.blob-3 {
  width: 30vw; height: 30vw;
  background: radial-gradient(circle, #3b82f6, #06b6d4);
  top: 45%; left: 40%;
  animation: drift 10s ease-in-out infinite alternate;
}
@keyframes drift {
  from { transform: translate(0,0) scale(1); }
  to   { transform: translate(4%, 6%) scale(1.08); }
}

/* ── Nav ── */
.top-nav {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  background: rgba(10, 10, 15, 0.7);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-bottom: 1px solid rgba(255,255,255,0.07);
}
.back-btn {
  width: 40px; height: 40px;
  display: flex; align-items: center; justify-content: center;
  background: rgba(255,255,255,0.08);
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 50%;
  color: #a0a0b0;
  cursor: pointer;
  transition: all 0.2s;
}
.back-btn:hover, .back-btn:active { background: rgba(255,255,255,0.14); color: #fff; }
.nav-title {
  font-size: 0.95rem;
  font-weight: 600;
  color: rgba(255,255,255,0.85);
  letter-spacing: 0.02em;
}

/* ── Content ── */
.content {
  flex: 1;
  position: relative;
  z-index: 1;
  max-width: 480px;
  width: 100%;
  margin: 0 auto;
  padding: 32px 20px 24px;
}

/* ── Hero ── */
.hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding-bottom: 32px;
}
.app-icon-wrapper {
  position: relative;
  margin-bottom: 20px;
}
.app-icon {
  width: 104px; height: 104px;
  background: linear-gradient(145deg, rgba(99,102,241,0.18), rgba(139,92,246,0.1));
  border-radius: 26px;
  display: flex; align-items: center; justify-content: center;
  box-shadow:
    0 0 0 1px rgba(99,102,241,0.3),
    0 0 40px rgba(99,102,241,0.25),
    0 20px 60px rgba(0,0,0,0.5),
    inset 0 1px 0 rgba(255,255,255,0.08);
  backdrop-filter: blur(12px);
  transition: transform 0.3s ease, box-shadow 0.3s ease;
  position: relative;
  overflow: visible;
}
.app-icon::before {
  content: '';
  position: absolute;
  inset: -1px;
  border-radius: 27px;
  background: linear-gradient(135deg, rgba(99,102,241,0.4), rgba(168,85,247,0.2), transparent);
  z-index: 0;
  pointer-events: none;
}
.app-icon:hover {
  transform: scale(1.05) translateY(-3px);
  box-shadow:
    0 0 0 1px rgba(99,102,241,0.5),
    0 0 60px rgba(99,102,241,0.4),
    0 28px 70px rgba(0,0,0,0.6),
    inset 0 1px 0 rgba(255,255,255,0.12);
}
.icon-img {
  width: 68px; height: 68px;
  object-fit: contain;
  position: relative; z-index: 1;
  filter: invert(1) drop-shadow(0 2px 8px rgba(99,102,241,0.4));
}
.rating-badge {
  position: absolute;
  bottom: -8px; right: -8px;
  background: rgba(255,215,0,0.18);
  border: 1px solid rgba(255,215,0,0.35);
  border-radius: 20px;
  padding: 2px 7px;
  font-size: 0.6rem;
  color: #ffd700;
  letter-spacing: 0.05em;
  backdrop-filter: blur(8px);
}

.app-name {
  font-size: 2.4rem;
  font-weight: 800;
  letter-spacing: -0.03em;
  background: linear-gradient(120deg, #ffffff 0%, rgba(255,255,255,0.7) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin: 0 0 6px;
}
.app-tagline {
  font-size: 1rem;
  color: rgba(255,255,255,0.45);
  margin: 0 0 14px;
  font-weight: 400;
}
.app-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.8rem;
  color: rgba(255,255,255,0.35);
}
.meta-pill {
  background: rgba(99,102,241,0.2);
  border: 1px solid rgba(99,102,241,0.35);
  color: #a5b4fc;
  padding: 2px 10px;
  border-radius: 100px;
  font-weight: 600;
  font-size: 0.75rem;
}
.meta-text { font-weight: 500; }

/* ── Download Section ── */
.download-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding-bottom: 36px;
}
.download-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  width: 100%;
  max-width: 340px;
  padding: 17px 28px;
  background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 50%, #a855f7 100%);
  color: #fff;
  border: none;
  border-radius: 16px;
  font-size: 1.05rem;
  font-weight: 700;
  letter-spacing: 0.01em;
  cursor: pointer;
  box-shadow:
    0 4px 24px rgba(99,102,241,0.5),
    0 1px 0 rgba(255,255,255,0.15) inset;
  transition: all 0.22s cubic-bezier(0.34,1.56,0.64,1);
  position: relative;
  overflow: hidden;
}
.download-btn::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(to bottom, rgba(255,255,255,0.12) 0%, transparent 100%);
}
.download-btn:hover {
  transform: translateY(-2px) scale(1.01);
  box-shadow: 0 8px 36px rgba(99,102,241,0.65), 0 1px 0 rgba(255,255,255,0.15) inset;
}
.download-btn:active { transform: scale(0.97); }
.download-icon-svg {
  width: 20px; height: 20px; flex-shrink: 0;
  position: relative; z-index: 1;
}
.download-btn span { position: relative; z-index: 1; }
.version-tag {
  background: rgba(255,255,255,0.18);
  border-radius: 100px;
  padding: 2px 9px;
  font-size: 0.72rem;
  font-weight: 600;
  letter-spacing: 0.03em;
}
.platform-note {
  font-size: 0.75rem;
  color: rgba(255,255,255,0.3);
  font-weight: 500;
}

/* Loading / Error */
.loading-state, .error-state, .no-version-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  color: rgba(255,255,255,0.4);
  font-size: 0.9rem;
  padding: 20px 0;
}
.spinner {
  width: 32px; height: 32px;
  border: 3px solid rgba(99,102,241,0.25);
  border-top-color: #6366f1;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
.error-icon { font-size: 1.6rem; }

/* Divider */
.section-divider {
  height: 1px;
  background: linear-gradient(to right, transparent, rgba(255,255,255,0.08), transparent);
  margin: 0 0 36px;
}

/* ── Features ── */
.features-section { padding-bottom: 36px; }
.section-heading {
  font-size: 1.05rem;
  font-weight: 700;
  color: rgba(255,255,255,0.8);
  letter-spacing: 0.01em;
  margin: 0 0 20px;
}
.features-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.feature-card {
  background: rgba(255,255,255,0.04);
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 16px;
  padding: 18px 16px;
  display: flex;
  align-items: flex-start;
  gap: 12px;
  transition: background 0.2s;
}
.feature-card:hover { background: rgba(255,255,255,0.07); }
.feature-icon { font-size: 1.5rem; flex-shrink: 0; }
.feature-text { display: flex; flex-direction: column; }
.feature-title {
  font-size: 0.88rem;
  font-weight: 700;
  color: rgba(255,255,255,0.85);
  margin: 0 0 3px;
}
.feature-desc {
  font-size: 0.73rem;
  color: rgba(255,255,255,0.35);
  margin: 0;
  line-height: 1.4;
}

/* ── Changelog ── */
.changelog-section { padding-bottom: 8px; }
.changelog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.version-badge {
  background: rgba(99,102,241,0.15);
  border: 1px solid rgba(99,102,241,0.3);
  color: #a5b4fc;
  font-size: 0.72rem;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: 100px;
}
.changelog-list {
  list-style: none;
  margin: 0; padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.changelog-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  font-size: 0.85rem;
  color: rgba(255,255,255,0.5);
  line-height: 1.5;
}
.changelog-dot {
  width: 6px; height: 6px;
  border-radius: 50%;
  background: #6366f1;
  flex-shrink: 0;
  margin-top: 6px;
}

/* ── Footer ── */
.page-footer {
  position: relative; z-index: 1;
  text-align: center;
  padding: 20px 20px 36px;
  border-top: 1px solid rgba(255,255,255,0.06);
  font-size: 0.72rem;
  color: rgba(255,255,255,0.2);
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.footer-links {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}
.footer-links a {
  color: rgba(255,255,255,0.3);
  text-decoration: none;
  transition: color 0.15s;
}
.footer-links a:hover { color: rgba(255,255,255,0.6); }

/* ── Responsive tweaks for small screens ── */
@media (max-width: 360px) {
  .app-icon { width: 88px; height: 88px; }
  .icon-emoji { font-size: 40px; }
  .app-name { font-size: 2rem; }
  .features-grid { grid-template-columns: 1fr; }
}
@media (min-width: 768px) {
  .content { padding: 48px 24px 32px; }
  .app-icon { width: 120px; height: 120px; }
  .icon-emoji { font-size: 56px; }
  .app-name { font-size: 3rem; }
}
</style>
