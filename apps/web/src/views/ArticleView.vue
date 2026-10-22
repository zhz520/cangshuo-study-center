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
  <div class="article-page">
    <header class="article-topbar">
      <div class="article-topbar-inner">
        <button class="web-brand" @click="router.push('/')">
          <span class="web-brand-mark">烁</span>
          <span>烁学</span>
        </button>
        <div class="web-actions">
          <button class="ui-btn ui-btn-ghost ui-btn-sm" @click="openInApp">在 App 中打开</button>
          <button class="ui-btn ui-btn-outline ui-btn-sm" @click="copyLink">复制链接</button>
        </div>
      </div>
    </header>

    <main class="article-main">
      <div v-if="isLoading" class="ui-loading">
        <div class="ui-spinner" style="margin: 0 auto 10px"></div>
        正在加载文章…
      </div>

      <div v-else-if="error" class="article-error">
        <p>{{ error }}</p>
        <button class="ui-btn ui-btn-outline ui-btn-sm" @click="router.go(0)">重新加载</button>
      </div>

      <article v-else-if="article">
        <div class="article-eyebrow">
          <span v-if="article.category" class="ui-badge ui-badge-blue">{{ article.category }}</span>
          <span>{{ formattedDate }}</span>
          <span v-if="article.view_count != null">{{ article.view_count }} 次阅读</span>
        </div>

        <h1 class="article-title">{{ article.title }}</h1>
        <p v-if="article.summary" class="article-summary">{{ article.summary }}</p>

        <img
          v-if="article.cover_url"
          :src="article.cover_url"
          :alt="article.title"
          class="article-cover"
        />

        <div v-if="article.content" class="article-body">
          <div class="article-truncated">
            <MarkdownRenderer :content="article.content" />
            <div class="article-mask"></div>
          </div>
          <div class="article-open">
            <p>网页端展示部分内容，完整文章请在 App 内阅读。</p>
            <button class="ui-btn ui-btn-primary" @click="openInApp">在 App 中继续阅读</button>
          </div>
        </div>
        <div v-else class="ui-empty">暂无正文内容</div>

        <div class="article-cta">
          <div>
            <strong>烁学 App</strong>
            <p>题库练习、AI 解析与学习记录同步。</p>
          </div>
          <router-link to="/download" class="ui-btn ui-btn-dark">下载 App</router-link>
        </div>
      </article>
    </main>

    <footer class="article-footer">
      <span>© {{ new Date().getFullYear() }} 烁学</span>
      <div>
        <router-link to="/agreement">用户协议</router-link>
        <router-link to="/privacy">隐私政策</router-link>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.article-page { min-height: 100vh; display: flex; flex-direction: column; background: var(--bg-body); color: var(--text-main); }
.article-topbar { position: sticky; top: 0; z-index: 40; border-bottom: 1px solid var(--border); background: color-mix(in srgb, var(--bg-body) 92%, transparent); backdrop-filter: blur(14px); }
.article-topbar-inner { width: min(760px, 100%); height: 58px; margin: 0 auto; padding: 0 18px; display: flex; align-items: center; gap: 12px; }
.article-main { width: min(720px, 100%); margin: 0 auto; padding: 34px 20px 64px; flex: 1; }
.article-eyebrow { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; font-size: 11px; color: var(--text-quote); }
.article-title { margin-top: 12px; font-size: clamp(24px, 4vw, 32px); line-height: 1.3; font-weight: 700; letter-spacing: -.03em; color: var(--text-main); }
.article-summary { margin-top: 12px; font-size: 14px; line-height: 1.8; color: var(--text-sub); }
.article-cover { width: 100%; max-height: 340px; object-fit: cover; margin-top: 22px; border: 1px solid var(--border); border-radius: 14px; }
.article-body { margin-top: 26px; font-size: 15px; line-height: 1.9; color: var(--text-main); }
.article-truncated { position: relative; max-height: 560px; overflow: hidden; }
.article-mask { position: absolute; inset: auto 0 0; height: 160px; background: linear-gradient(to bottom, transparent, var(--bg-body) 92%); pointer-events: none; }
.article-open { display: flex; flex-direction: column; align-items: center; gap: 10px; margin-top: -8px; text-align: center; }
.article-open p { font-size: 12px; color: var(--text-quote); }
.article-error { padding: 56px 0; text-align: center; color: var(--text-sub); font-size: 13px; }
.article-error p { margin-bottom: 12px; }
.article-cta { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-top: 36px; padding-top: 20px; border-top: 1px solid var(--border); }
.article-cta strong { font-size: 13px; font-weight: 600; }
.article-cta p { margin-top: 3px; font-size: 11px; color: var(--text-quote); }
.article-footer { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 12px; width: min(720px, 100%); margin: 0 auto; padding: 18px 20px 30px; border-top: 1px solid var(--border); font-size: 11px; color: var(--text-quote); }
.article-footer div { display: flex; gap: 16px; }
.article-footer a:hover { color: var(--text-sub); }
.article-body :deep(img) { max-width: 100%; height: auto; border-radius: 10px; margin: 14px 0; }
.article-body :deep(h1), .article-body :deep(h2), .article-body :deep(h3), .article-body :deep(h4) { margin: 1.5em 0 .6em; font-weight: 650; line-height: 1.45; color: var(--text-main); }
.article-body :deep(p) { margin: .85em 0; }
.article-body :deep(ul), .article-body :deep(ol) { margin: .7em 0; padding-left: 1.5em; }
.article-body :deep(blockquote) { margin: 1em 0; padding-left: 14px; border-left: 2px solid var(--border-strong); color: var(--text-sub); }
.article-body :deep(pre) { padding: 14px; border-radius: 10px; background: var(--bg-card-highlight); overflow-x: auto; font-size: 13px; }
.article-body :deep(code) { padding: 2px 5px; border-radius: 4px; background: var(--bg-card-highlight); font-size: 13px; }
.article-body :deep(a) { color: var(--primary); text-decoration: underline; text-underline-offset: 2px; }
@media (max-width: 560px) {
  .article-main { padding: 22px 16px 48px; }
  .article-cta { align-items: flex-start; flex-direction: column; }
  .article-title { font-size: 23px; }
}
</style>
