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
  <div class="download-page">
    <header class="download-topbar">
      <div class="download-topbar-inner">
        <button class="download-back" @click="router.push('/')" aria-label="返回首页">←</button>
        <div class="download-brand">
          <img :src="appIcon" alt="烁学" />
          <span>下载中心</span>
        </div>
        <div style="width: 36px"></div>
      </div>
    </header>

    <main class="download-main">
      <section class="download-hero">
        <img :src="appIcon" alt="烁学 App" class="download-icon" />
        <div>
          <div class="download-eyebrow">官方 Android 版</div>
          <h1 class="download-title">烁学</h1>
          <p class="download-tagline">题库练习、AI 解析与学习记录同步。</p>
          <div class="download-meta">
            <span>教育工具</span>
            <span>Android 8.0+</span>
            <span>官方正版</span>
          </div>
        </div>
      </section>

      <section class="download-action">
        <div v-if="isLoading" class="download-state">
          <div class="ui-spinner" style="margin: 0 auto 10px"></div>
          正在获取最新版本…
        </div>

        <template v-else-if="!hasError && appInfo.downloadUrl">
          <button class="ui-btn ui-btn-primary ui-btn-lg download-button" @click="handleDownload">
            下载安卓版
            <span v-if="appInfo.versionName" class="download-version">v{{ appInfo.versionName }}</span>
          </button>
          <p class="download-note">当前支持 Android，iOS 版本筹备中。</p>
        </template>

        <div v-else-if="hasError" class="download-state is-error">
          获取下载链接失败，请刷新页面重试。
        </div>

        <div v-else class="download-state">
          暂无发布版本，请关注后续更新。
        </div>
      </section>

      <section class="download-section">
        <h2 class="download-section-title">核心功能</h2>
        <div class="download-features">
          <div v-for="(f, index) in features" :key="f.title" class="download-feature">
            <span class="download-feature-index">{{ String(index + 1).padStart(2, '0') }}</span>
            <div>
              <strong>{{ f.title }}</strong>
              <p>{{ f.desc }}</p>
            </div>
          </div>
        </div>
      </section>

      <section v-if="appInfo.versionName && !isLoading" class="download-section">
        <div class="download-section-head">
          <h2 class="download-section-title">更新记录</h2>
          <span class="ui-badge">v{{ appInfo.versionName }}</span>
        </div>
        <ul class="download-changelog">
          <li v-for="(line, i) in updateLogLines" :key="i">
            {{ line.replace(/^[-•·\s]+/, '') }}
          </li>
          <li v-if="updateLogLines.length === 0">性能优化及基础体验改进</li>
        </ul>
      </section>
    </main>

    <footer class="download-footer">
      <span>© {{ currentYear }} 烁学</span>
      <div>
        <router-link to="/agreement">用户协议</router-link>
        <router-link to="/privacy">隐私政策</router-link>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.download-page { min-height: 100vh; display: flex; flex-direction: column; background: var(--bg-body); color: var(--text-main); }
.download-topbar { position: sticky; top: 0; z-index: 40; border-bottom: 1px solid var(--border); background: color-mix(in srgb, var(--bg-body) 92%, transparent); backdrop-filter: blur(14px); }
.download-topbar-inner { display: flex; align-items: center; justify-content: space-between; width: min(760px, 100%); height: 58px; margin: 0 auto; padding: 0 18px; }
.download-back { width: 36px; height: 36px; display: grid; place-items: center; border-radius: 9px; color: var(--text-sub); font-size: 16px; }
.download-back:hover { background: var(--bg-card-highlight); color: var(--text-main); }
.download-brand { display: flex; align-items: center; gap: 8px; font-size: 13px; font-weight: 600; }
.download-brand img { width: 22px; height: 22px; border-radius: 6px; }
.download-main { width: min(720px, 100%); margin: 0 auto; padding: 40px 20px 64px; flex: 1; }
.download-hero { display: flex; align-items: flex-start; gap: 20px; }
.download-icon { width: 76px; height: 76px; flex: 0 0 76px; border: 1px solid var(--border); border-radius: 18px; }
.download-eyebrow { font-size: 10px; font-weight: 600; letter-spacing: .1em; text-transform: uppercase; color: var(--text-quote); }
.download-title { margin-top: 4px; font-size: 30px; font-weight: 700; letter-spacing: -.04em; }
.download-tagline { margin-top: 6px; font-size: 13px; line-height: 1.7; color: var(--text-sub); }
.download-meta { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 10px; }
.download-meta span { padding: 3px 7px; border-radius: 5px; background: var(--bg-card-highlight); font-size: 10px; color: var(--text-sub); }
.download-action { margin-top: 30px; padding: 22px; border: 1px solid var(--border); border-radius: 14px; background: var(--bg-card); }
.download-button { width: 100%; }
.download-version { margin-left: 8px; padding: 2px 6px; border-radius: 5px; background: rgba(255,255,255,.18); font-size: 10px; font-weight: 500; }
.download-note { margin-top: 10px; text-align: center; font-size: 11px; color: var(--text-quote); }
.download-state { padding: 18px 0; text-align: center; font-size: 12px; color: var(--text-sub); }
.download-state.is-error { color: var(--danger); }
.download-section { margin-top: 34px; }
.download-section-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.download-section-title { margin-bottom: 12px; font-size: 14px; font-weight: 650; }
.download-features { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 28px; border-top: 1px solid var(--border); }
.download-feature { display: flex; gap: 10px; padding: 14px 0; border-bottom: 1px solid var(--border); }
.download-feature-index { font-size: 10px; font-weight: 600; color: var(--text-quote); font-variant-numeric: tabular-nums; padding-top: 2px; }
.download-feature strong { font-size: 13px; font-weight: 600; }
.download-feature p { margin-top: 3px; font-size: 11px; line-height: 1.6; color: var(--text-sub); }
.download-changelog { border-top: 1px solid var(--border); }
.download-changelog li { position: relative; padding: 11px 0 11px 14px; border-bottom: 1px solid var(--border); font-size: 12px; line-height: 1.7; color: var(--text-sub); }
.download-changelog li::before { content: ''; position: absolute; left: 0; top: 19px; width: 3px; height: 3px; border-radius: 50%; background: var(--text-quote); }
.download-footer { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 12px; width: min(720px, 100%); margin: 0 auto; padding: 18px 20px 30px; border-top: 1px solid var(--border); font-size: 11px; color: var(--text-quote); }
.download-footer div { display: flex; gap: 16px; }
.download-footer a:hover { color: var(--text-sub); }
@media (max-width: 560px) {
  .download-main { padding: 26px 16px 48px; }
  .download-hero { gap: 14px; }
  .download-icon { width: 60px; height: 60px; flex-basis: 60px; border-radius: 14px; }
  .download-title { font-size: 25px; }
  .download-features { grid-template-columns: 1fr; }
}
</style>
