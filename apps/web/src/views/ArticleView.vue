<script setup>
import { ref, onMounted, computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import axios from "axios";
import MarkdownRenderer from "../components/ui/MarkdownRenderer.vue";

const route = useRoute();
const router = useRouter();
const articleId = computed(() => route.params.id);

const article = ref(null);
const isLoading = ref(true);
const error = ref(null);

const baseURL = import.meta.env.VITE_API_BASE_URL || "/api";

onMounted(async () => {
  try {
    const res = await axios.get(
      `${baseURL}/data/resources/articles/${articleId.value}`,
    );
    article.value = res.data;
  } catch (e) {
    if (e.response?.status === 404) {
      error.value = "文章不存在或已下架";
    } else {
      error.value = "加载失败，请稍后重试";
    }
  } finally {
    isLoading.value = false;
  }
});

const formattedDate = computed(() => {
  if (!article.value?.created_at) return "";
  const d = new Date(article.value.created_at);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
});

const shareLink = computed(() => `https://study.zhzgo.cn/article/${articleId.value}`);

const openInApp = () => {
  const schemeUrl = `studyapp://article/${articleId.value}`;
  
  // 尝试通过自定义 Scheme 唤起 APP
  window.location.href = schemeUrl;
  
  // 如果 2.5s 后还在当前页，可能是没装 APP 或唤起失败，不做强制操作，由用户自行决定
  setTimeout(() => {
    if (!document.hidden) {
      console.log("App may not be installed or wake up failed.");
    }
  }, 2500);
};

const copyLink = () => {
  navigator.clipboard.writeText(shareLink.value).then(() => {
    alert("链接已复制到剪贴板");
  });
};
</script>

<template>
  <div class="page-wrapper">
    <!-- Ambient background -->
    <div class="ambient-bg">
      <div class="blob blob-1"></div>
      <div class="blob blob-2"></div>
    </div>

    <!-- Top Nav -->
    <nav class="top-nav">
      <button class="back-btn" @click="router.push('/')">
        <svg
          width="20"
          height="20"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2.5"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <polyline points="15 18 9 12 15 6"></polyline>
        </svg>
      </button>
      <span class="nav-title">文章详情</span>
      <div class="nav-actions">
        <button class="open-app-nav-btn" @click="openInApp" title="在 APP 中打开">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"></path>
            <polyline points="15 3 21 3 21 9"></polyline>
            <line x1="10" y1="14" x2="21" y2="3"></line>
          </svg>
        </button>
        <button class="share-btn" @click="copyLink" title="复制链接">
          <svg
            width="18"
            height="18"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path d="M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8" />
            <polyline points="16 6 12 2 8 6" />
            <line x1="12" y1="2" x2="12" y2="15" />
          </svg>
        </button>
      </div>
    </nav>

    <!-- Loading -->
    <div v-if="isLoading" class="state-box">
      <div class="spinner"></div>
      <p>加载中…</p>
    </div>

    <!-- Error -->
    <div v-else-if="error" class="state-box">
      <span class="error-emoji">😢</span>
      <p>{{ error }}</p>
      <button class="retry-btn" @click="router.go(0)">重试</button>
    </div>

    <!-- Article Content -->
    <main v-else-if="article" class="content">
      <!-- Cover -->
      <img
        v-if="article.cover_url"
        :src="article.cover_url"
        :alt="article.title"
        class="cover-img"
      />

      <!-- Meta -->
      <div class="article-meta">
        <span v-if="article.category" class="meta-category">{{
          article.category
        }}</span>
        <span class="meta-views" v-if="article.view_count != null"
          >👁 {{ article.view_count }}</span
        >
        <span class="meta-date">{{ formattedDate }}</span>
      </div>

      <!-- Title -->
      <h1 class="article-title">{{ article.title }}</h1>

      <!-- Summary -->
      <p v-if="article.summary" class="article-summary">
        {{ article.summary }}
      </p>

      <!-- Divider -->
      <div class="divider"></div>

      <!-- Body -->
      <div v-if="article.content" class="article-body-wrapper">
        <div class="truncated-content">
          <MarkdownRenderer :content="article.content" />
          <div class="read-more-mask">
            <button class="mask-open-btn" @click="openInApp">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"></path>
                <polyline points="15 3 21 3 21 9"></polyline>
                <line x1="10" y1="14" x2="21" y2="3"></line>
              </svg>
              <span>在 APP 中完整阅读</span>
            </button>
          </div>
        </div>
      </div>
      <div v-else class="empty-content">暂无正文内容</div>

      <!-- Download CTA -->
      <div class="cta-card">
        <div class="cta-text">
          <p class="cta-title">📱 在「烁学」APP 中阅读更多</p>
          <p class="cta-desc">题库练习 · AI 解析 · 云端同步</p>
        </div>
        <router-link to="/download" class="cta-btn">下载 APP</router-link>
      </div>
    </main>

    <!-- Footer -->
    <footer class="page-footer">
      <p>© {{ new Date().getFullYear() }} 烁学. All rights reserved.</p>
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
  min-height: 100vh;
  min-height: 100dvh;
  background: #0a0a0f;
  color: #f0f0f5;
  display: flex;
  flex-direction: column;
  position: relative;
  overflow-x: hidden;
  font-family:
    -apple-system, BlinkMacSystemFont, "SF Pro Display", "Segoe UI", sans-serif;
}

/* ── Ambient ── */
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
  opacity: 0.2;
}
.blob-1 {
  width: 50vw;
  height: 50vw;
  background: radial-gradient(circle, #6366f1, #8b5cf6);
  top: -10%;
  left: -10%;
  animation: drift 12s ease-in-out infinite alternate;
}
.blob-2 {
  width: 40vw;
  height: 40vw;
  background: radial-gradient(circle, #a855f7, #ec4899);
  bottom: 10%;
  right: -10%;
  animation: drift 15s ease-in-out infinite alternate-reverse;
}
@keyframes drift {
  from {
    transform: translate(0, 0) scale(1);
  }
  to {
    transform: translate(4%, 6%) scale(1.08);
  }
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
  background: rgba(10, 10, 15, 0.75);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.07);
}
.back-btn,
.share-btn {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 50%;
  color: #a0a0b0;
  cursor: pointer;
  transition: all 0.2s;
}
.back-btn:hover,
.share-btn:hover {
  background: rgba(255, 255, 255, 0.14);
  color: #fff;
}
.nav-title {
  font-size: 0.95rem;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.85);
}
.nav-actions {
  display: flex; gap: 8px;
}
.back-btn, .share-btn, .open-app-nav-btn {
  width: 40px; height: 40px;
  display: flex; align-items: center; justify-content: center;
  background: rgba(255,255,255,0.08);
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 50%;
  color: #a0a0b0;
  cursor: pointer;
  transition: all 0.2s;
}
.back-btn:hover, .share-btn:hover, .open-app-nav-btn:hover { background: rgba(255,255,255,0.14); color: #fff; }
.open-app-nav-btn { color: #818cf8; border-color: rgba(129, 140, 248, 0.3); }

/* ── States ── */
.state-box {
  flex: 1;
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: rgba(255, 255, 255, 0.4);
  font-size: 0.95rem;
}
.spinner {
  width: 32px;
  height: 32px;
  border: 3px solid rgba(99, 102, 241, 0.25);
  border-top-color: #6366f1;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
.error-emoji {
  font-size: 2.4rem;
}
.retry-btn {
  background: rgba(99, 102, 241, 0.2);
  border: 1px solid rgba(99, 102, 241, 0.35);
  color: #a5b4fc;
  padding: 8px 24px;
  border-radius: 10px;
  cursor: pointer;
  font-weight: 600;
  transition: background 0.2s;
}
.retry-btn:hover {
  background: rgba(99, 102, 241, 0.35);
}

/* ── Content ── */
.content {
  flex: 1;
  position: relative;
  z-index: 1;
  max-width: 720px;
  width: 100%;
  margin: 0 auto;
  padding: 0 20px 40px;
}

/* ── Cover ── */
.cover-img {
  width: 100%;
  max-height: 360px;
  object-fit: cover;
  border-radius: 16px;
  margin: 24px 0 20px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.5);
}

/* ── Meta ── */
.article-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 0.78rem;
  color: rgba(255, 255, 255, 0.35);
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.meta-category {
  background: rgba(99, 102, 241, 0.15);
  border: 1px solid rgba(99, 102, 241, 0.25);
  color: #a5b4fc;
  padding: 2px 10px;
  border-radius: 100px;
  font-weight: 600;
  font-size: 0.72rem;
}
.meta-views,
.meta-date {
  font-weight: 500;
}

/* ── Title ── */
.article-title {
  font-size: 1.8rem;
  font-weight: 800;
  line-height: 1.35;
  letter-spacing: -0.02em;
  color: rgba(255, 255, 255, 0.95);
  margin: 0 0 12px;
}

/* ── Summary ── */
.article-summary {
  font-size: 0.95rem;
  line-height: 1.7;
  color: rgba(255, 255, 255, 0.5);
  margin: 0 0 20px;
}

/* ── Divider ── */
.divider {
  height: 1px;
  background: linear-gradient(
    to right,
    transparent,
    rgba(255, 255, 255, 0.1),
    transparent
  );
  margin: 0 0 24px;
}

/* ── Body ── */
.article-body-wrapper {
  margin-top: 12px;
}
.truncated-content {
  max-height: 500px;
  overflow: hidden;
  position: relative;
}
.read-more-mask {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 240px;
  background: linear-gradient(to bottom, transparent, #0a0a0f 85%);
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding-bottom: 40px;
  z-index: 10;
}
.mask-open-btn {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 28px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  border: none;
  border-radius: 14px;
  font-weight: 700;
  font-size: 0.95rem;
  cursor: pointer;
  box-shadow: 0 10px 25px rgba(99, 102, 241, 0.4);
  transition: transform 0.2s, box-shadow 0.2s;
}
.mask-open-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 14px 32px rgba(99, 102, 241, 0.55);
}
.article-body :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 10px;
  margin: 12px 0;
}
.article-body :deep(a) {
  color: #818cf8;
}
.article-body :deep(pre) {
  background: rgba(255, 255, 255, 0.06);
  padding: 14px;
  border-radius: 10px;
  overflow-x: auto;
  font-size: 0.88rem;
}
.article-body :deep(code) {
  background: rgba(255, 255, 255, 0.06);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 0.88rem;
}
.article-body :deep(h1),
.article-body :deep(h2),
.article-body :deep(h3),
.article-body :deep(h4) {
  margin-top: 1.4em;
  margin-bottom: 0.5em;
  color: rgba(255, 255, 255, 0.9);
}
.article-body :deep(blockquote) {
  border-left: 3px solid #818cf8;
  margin-left: 0;
  padding-left: 16px;
  opacity: 0.8;
}
.article-body :deep(p) {
  margin: 0.8em 0;
}
.article-body :deep(ul),
.article-body :deep(ol) {
  margin: 0.6em 0;
  padding-left: 1.5em;
}

.empty-content {
  text-align: center;
  color: rgba(255, 255, 255, 0.25);
  padding: 48px 0;
  font-size: 0.95rem;
}

/* ── CTA Card ── */
.cta-card {
  margin-top: 40px;
  background: rgba(99, 102, 241, 0.08);
  border: 1px solid rgba(99, 102, 241, 0.2);
  border-radius: 16px;
  padding: 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.cta-text {
  flex: 1;
  min-width: 180px;
}
.cta-title {
  font-size: 1rem;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.85);
  margin: 0 0 4px;
}
.cta-desc {
  font-size: 0.78rem;
  color: rgba(255, 255, 255, 0.4);
  margin: 0;
}
.cta-btn {
  display: inline-flex;
  align-items: center;
  padding: 10px 22px;
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  color: #fff;
  font-weight: 700;
  font-size: 0.88rem;
  border-radius: 12px;
  text-decoration: none;
  transition:
    transform 0.2s,
    box-shadow 0.2s;
  box-shadow: 0 4px 16px rgba(99, 102, 241, 0.4);
}
.cta-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 24px rgba(99, 102, 241, 0.55);
}

/* ── Footer ── */
.page-footer {
  position: relative;
  z-index: 1;
  text-align: center;
  padding: 20px 20px 36px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  font-size: 0.72rem;
  color: rgba(255, 255, 255, 0.2);
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
  color: rgba(255, 255, 255, 0.3);
  text-decoration: none;
  transition: color 0.15s;
}
.footer-links a:hover {
  color: rgba(255, 255, 255, 0.6);
}

/* ── Responsive ── */
@media (max-width: 480px) {
  .article-title {
    font-size: 1.4rem;
  }
  .cover-img {
    border-radius: 12px;
    max-height: 240px;
  }
  .cta-card {
    flex-direction: column;
    text-align: center;
  }
}
@media (min-width: 768px) {
  .content {
    padding: 0 32px 48px;
  }
  .article-title {
    font-size: 2.2rem;
  }
}
</style>
