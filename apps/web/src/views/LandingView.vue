<script setup>
import BrandLogo from '../components/BrandLogo.vue';
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import appIcon from '../assets/app_icon.png';
import { publicApi } from '../api';

const router = useRouter();
const mobileMenuOpen = ref(false);
const openFaq = ref(null);
const features = [
  { title: '系统化题库', desc: '按专业和科目组织，支持单选、多选、判断、填空、排序和材料题。' },
  { title: '沉浸式练习', desc: '题号、进度、计时、收藏和答题卡集中在一个专注界面。' },
  { title: 'AI 深度解析', desc: '提交后按需获取逐步讲解，帮助定位知识盲区。' },
  { title: '错题与收藏', desc: '错题按科目复习，收藏独立管理，学习记录保留在账号中。' },
  { title: '模拟考试', desc: '试卷计时、标记、答题卡和交卷前检查保持完整流程。' },
  { title: '多端同步', desc: 'Web 与 Android 共用账号和学习进度，随时继续上次练习。' },
];
const stats = ref([
  { value: '', label: '题库数量' },
  { value: '', label: '注册用户' },
  { value: 'Web / Android', label: '学习进度同步' },
]);
const faqs = [
  { q: '烁学支持哪些平台？', a: '支持 Web 浏览器和 Android 原生 App，账号学习进度在两端同步。' },
  { q: '题库内容是否免费？', a: '免费账号可预览每个科目的前 20 道题；Plus 会员解锁完整题库、更多 AI 解析次数和学习权益。' },
  { q: 'AI 解析是真实 AI 吗？', a: '是，接入大模型 API，答案解析按题目和作答内容生成或读取。' },
  { q: '数据安全性如何保障？', a: '采用双 Token 鉴权、HTTPS 传输和服务端加密存储，题库不会以全量明文形式公开。' },
];

const toggleFaq = (i) => { openFaq.value = openFaq.value === i ? null : i; };
const go = (path) => { mobileMenuOpen.value = false; router.push(path); };

onMounted(async () => {
  try {
    const res = await publicApi.getStats();
    if (res.data?.success) {
      stats.value[0].value = `${Number(res.data.data.questionCount).toLocaleString()}+`;
      stats.value[1].value = `${Number(res.data.data.userCount).toLocaleString()}+`;
    }
  } catch {}
});
</script>

<template>
  <div class="landing-page">
    <header class="landing-nav">
      <div class="landing-container landing-nav-inner">
        <RouterLink to="/" class="web-brand"><BrandLogo /><span>烁学</span></RouterLink>
        <nav class="landing-links" aria-label="产品导航">
          <a href="#features">产品功能</a><a href="#flow">学习路径</a><a href="#faq">常见问题</a>
        </nav>
        <div class="landing-nav-actions">
          <button class="ui-btn ui-btn-ghost" @click="go('/login')">登录</button>
          <button class="landing-menu-btn" type="button" aria-label="打开导航" @click="mobileMenuOpen = !mobileMenuOpen">☰</button>
        </div>
      </div>
      <div v-if="mobileMenuOpen" class="landing-mobile-menu">
        <a href="#features" @click="mobileMenuOpen = false">产品功能</a><a href="#flow" @click="mobileMenuOpen = false">学习路径</a><a href="#faq" @click="mobileMenuOpen = false">常见问题</a><RouterLink to="/score-query">成绩查询</RouterLink><RouterLink to="/download">下载 App</RouterLink><RouterLink to="/login">登录</RouterLink><RouterLink to="/register">免费开始</RouterLink>
      </div>
    </header>

    <main>
      <section class="landing-hero">
        <div class="landing-container landing-hero-grid">
          <div>
            <span class="ui-badge ui-badge-blue">专注每一次进步</span>
            <h1>把刷题做得更清楚，<br /><span>也更专注。</span></h1>
            <p>烁学把题库、练习、错题、模拟考试和学习分析放在一个安静的学习空间里。Web 与 Android 同步，随时继续。</p>
            <div class="landing-hero-actions">
              <button class="ui-btn ui-btn-primary ui-btn-lg" @click="go('/register')">开始学习</button>
              <RouterLink to="/download" class="ui-btn ui-btn-outline ui-btn-lg">下载 Android App</RouterLink>
            </div>
            <div class="landing-trust"><span>服务端权限控制</span><span>题库加密存储</span><span>学习记录可恢复</span></div>
          </div>
          <div class="landing-product">
            <div class="landing-product-bar"><img :src="appIcon" alt="烁学图标" /><span>烁学</span><span class="ui-badge ui-badge-green">学习空间</span></div>
            <div class="landing-product-body">
              <div class="mini-head"><span>今日学习</span><span class="ui-faint">继续上次练习</span></div>
              <div class="mini-title">计算机基础</div>
              <div class="mini-progress"><span style="width:64%"></span></div>
              <div class="mini-stats"><div><strong>32</strong><small>今日完成</small></div><div><strong>83%</strong><small>正确率</small></div><div><strong>12</strong><small>连续学习</small></div></div>
              <div class="mini-list"><div><span>数据结构与算法</span><span class="ui-faint">继续 ›</span></div><div><span>错题复习</span><span class="ui-faint">8 题待复习</span></div><div><span>模拟考试</span><span class="ui-faint">查看试卷 ›</span></div></div>
            </div>
          </div>
        </div>
      </section>

      <section id="stats" class="landing-stats">
        <div class="landing-container landing-stats-grid"><div v-for="item in stats" v-show="item.value" :key="item.label"><strong :class="{ 'is-small': item.value.length > 8 }">{{ item.value }}</strong><span>{{ item.label }}</span></div></div>
      </section>

      <section id="features" class="landing-section landing-container">
        <div class="landing-section-head"><span class="ui-eyebrow">产品能力</span><h2>少一点干扰，多一点完成</h2><p>从进入题库到复盘，保持一条清楚的学习路径。</p></div>
        <div class="landing-feature-grid"><article v-for="(feature, i) in features" :key="feature.title" class="landing-feature"><span class="feature-index">0{{ i + 1 }}</span><h3>{{ feature.title }}</h3><p>{{ feature.desc }}</p></article></div>
      </section>

      <section id="flow" class="landing-section landing-container landing-flow">
        <div class="landing-section-head"><span class="ui-eyebrow">学习路径</span><h2>每次打开都知道下一步</h2></div>
        <div class="landing-flow-grid"><div><b>01</b><strong>选择科目</strong><span>按专业查找，保留最近使用。</span></div><div><b>02</b><strong>专注练习</strong><span>题目、选项、进度和答题卡放在一个界面。</span></div><div><b>03</b><strong>回看进步</strong><span>错题、收藏、趋势和模拟考试持续积累。</span></div></div>
      </section>

      <section id="faq" class="landing-section landing-container landing-faq">
        <div class="landing-section-head"><span class="ui-eyebrow">常见问题</span><h2>你可能想知道</h2></div>
        <div class="ui-list-card ui-card"><button v-for="(item, i) in faqs" :key="item.q" class="ui-row-button faq-row" @click="toggleFaq(i)"><span class="ui-grow"><strong>{{ item.q }}</strong><small v-if="openFaq === i" class="block ui-small ui-muted faq-answer">{{ item.a }}</small></span><span class="ui-faint">{{ openFaq === i ? '−' : '+' }}</span></button></div>
      </section>

      <section class="landing-cta"><div class="landing-container landing-cta-inner"><div><span class="ui-eyebrow">现在开始</span><h2>把下一次练习，变成看得见的进步。</h2></div><div><button class="ui-btn ui-btn-primary ui-btn-lg" @click="go('/register')">开始刷题</button></div></div></section>
    </main>

    <footer class="landing-footer"><div class="landing-container landing-footer-inner"><span>© {{ new Date().getFullYear() }} 沧烁</span><a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener">蜀ICP备2025126838号-1</a><a href="https://www.beian.gov.cn/portal/registerSystemInfo?recordcode=51172202000160" target="_blank" rel="noopener">川公网安备51172202000160号</a><div><RouterLink to="/score-query">成绩查询</RouterLink><RouterLink to="/download">下载 App</RouterLink><RouterLink to="/agreement">用户协议</RouterLink><RouterLink to="/privacy">隐私政策</RouterLink><RouterLink to="/login">登录</RouterLink></div></div></footer>
  </div>
</template>

<style scoped>
.landing-page { min-height: 100vh; background: var(--bg-body); color: var(--text-main); }
.landing-container { width: min(1120px, 100%); margin: 0 auto; padding-left: 22px; padding-right: 22px; }
.landing-nav { position: sticky; top: 0; z-index: 30; border-bottom: 1px solid var(--border); background: color-mix(in srgb, var(--bg-body) 94%, transparent); backdrop-filter: blur(14px); }
.landing-nav-inner { min-height: 62px; display: flex; align-items: center; gap: 28px; }
.landing-links { display: flex; align-items: center; gap: 22px; flex: 1; color: var(--text-sub); font-size: 13px; }
.landing-links a:hover { color: var(--text-main); }
.landing-nav-actions { display: flex; align-items: center; gap: 6px; }
.landing-menu-btn { display: none; width: 36px; height: 36px; border-radius: 9px; color: var(--text-sub); font-size: 18px; }
.landing-mobile-menu { display: none; }
.landing-hero { padding: 64px 0 48px; border-bottom: 1px solid var(--border); background: var(--bg-body); }
.landing-hero-grid { display: grid; grid-template-columns: minmax(0,1.1fr) minmax(320px,.9fr); gap: 72px; align-items: center; }
.landing-hero h1 { margin-top: 20px; font-size: clamp(38px, 5vw, 58px); line-height: 1.08; letter-spacing: -.055em; font-weight: 720; }
.landing-hero h1 span { color: var(--text-main); }
.landing-hero p { max-width: 520px; margin-top: 20px; font-size: 15px; line-height: 1.8; color: var(--text-sub); }
.landing-hero-actions { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 28px; }
.landing-trust { display: flex; flex-wrap: wrap; gap: 16px; margin-top: 24px; color: var(--text-quote); font-size: 11px; }
.landing-trust span::before { content: '·'; margin-right: 6px; color: var(--success); }
.landing-product { border: 1px solid var(--border); border-radius: 14px; background: var(--bg-card); box-shadow: 0 8px 26px rgba(15,23,42,.05); overflow: hidden; }
.landing-product-bar { height: 52px; display: flex; align-items: center; gap: 9px; padding: 0 16px; border-bottom: 1px solid var(--border); font-size: 12px; font-weight: 600; background: var(--bg-card-highlight); }
.landing-product-bar img { width: 24px; height: 24px; border-radius: 6px; }
.landing-product-bar .ui-badge { margin-left: auto; }
.landing-product-body { padding: 22px; }
.mini-head { display: flex; justify-content: space-between; font-size: 11px; color: var(--text-sub); }
.mini-title { margin-top: 10px; font-size: 19px; font-weight: 650; }
.mini-progress { height: 4px; margin-top: 14px; border-radius: 99px; background: var(--border); overflow: hidden; }
.mini-progress span { display: block; height: 100%; background: var(--primary); }
.mini-stats { display: grid; grid-template-columns: repeat(3,1fr); gap: 8px; margin-top: 18px; padding: 14px 0; border-top: 1px solid var(--border); border-bottom: 1px solid var(--border); }
.mini-stats div { display: flex; flex-direction: column; gap: 2px; }
.mini-stats strong { font-size: 18px; }
.mini-stats small { font-size: 10px; color: var(--text-sub); }
.mini-list { margin-top: 6px; }
.mini-list div { display: flex; justify-content: space-between; gap: 12px; padding: 11px 0; border-bottom: 1px solid var(--border); font-size: 12px; }
.mini-list div:last-child { border-bottom: 0; }
.landing-stats { border-bottom: 1px solid var(--border); background: var(--bg-card); }
.landing-stats-grid { display: grid; grid-template-columns: repeat(auto-fit,minmax(220px,1fr)); padding-top: 22px; padding-bottom: 22px; }
.landing-stats-grid div { display: flex; flex-direction: column; gap: 4px; padding-left: 22px; border-left: 1px solid var(--border); }
.landing-stats-grid div:first-child { border-left: 0; padding-left: 0; }
.landing-stats-grid strong { font-size: 24px; letter-spacing: -.03em; }
.landing-stats-grid strong.is-small { font-size: 15px; letter-spacing: -.01em; }
.landing-stats-grid span { font-size: 11px; color: var(--text-sub); }
.landing-section { padding-top: 64px; padding-bottom: 64px; }
.landing-section-head { max-width: 620px; margin-bottom: 26px; }
.landing-section-head h2 { margin-top: 9px; font-size: 30px; line-height: 1.25; letter-spacing: -.035em; font-weight: 680; }
.landing-section-head p { margin-top: 8px; color: var(--text-sub); font-size: 14px; }
.landing-feature-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 0 48px; border-top: 1px solid var(--border); }
.landing-feature { display: grid; grid-template-columns: 30px minmax(0,1fr); gap: 12px; padding: 20px 0; border-bottom: 1px solid var(--border); background: none; }
.feature-index { font-size: 11px; color: var(--primary); font-weight: 700; }
.landing-feature h3 { margin-top: 0; font-size: 15px; font-weight: 650; }
.landing-feature p { margin-top: 7px; font-size: 12px; line-height: 1.7; color: var(--text-sub); }
.landing-flow { border-top: 1px solid var(--border); }
.landing-flow-grid { display: grid; grid-template-columns: repeat(3,1fr); gap: 0; border-top: 1px solid var(--border); }
.landing-flow-grid div { display: flex; flex-direction: column; gap: 8px; padding: 20px 28px 0 0; border-right: 1px solid var(--border); }
.landing-flow-grid div + div { padding-left: 28px; }
.landing-flow-grid div:last-child { border-right: 0; }
.landing-flow-grid b { font-size: 12px; color: var(--primary); }
.landing-flow-grid strong { font-size: 17px; }
.landing-flow-grid span { font-size: 12px; line-height: 1.7; color: var(--text-sub); }
.landing-faq { max-width: 850px; border-top: 1px solid var(--border); }
.faq-row { padding-left: 18px; padding-right: 18px; }
.faq-answer { padding-top: 8px; line-height: 1.75; }
.landing-cta { border-top: 1px solid var(--border); border-bottom: 1px solid var(--border); background: var(--bg-card); }
.landing-cta-inner { display: flex; align-items: center; justify-content: space-between; gap: 24px; padding-top: 40px; padding-bottom: 40px; }
.landing-cta h2 { margin-top: 8px; font-size: 24px; letter-spacing: -.03em; }
.landing-cta-inner > div:last-child { display: flex; gap: 8px; flex-wrap: wrap; }
.landing-footer { padding: 22px 0; }
.landing-footer-inner { display: flex; flex-wrap: wrap; gap: 10px 18px; align-items: center; color: var(--text-quote); font-size: 11px; }
.landing-footer-inner div { display: flex; gap: 14px; margin-left: auto; }
.landing-footer a:hover { color: var(--text-main); }
@media (max-width: 900px) { .landing-hero-grid { grid-template-columns: 1fr; gap: 36px; } .landing-product { max-width: 460px; transform: none; } .landing-feature-grid { grid-template-columns: 1fr; } }
@media (max-width: 700px) { .landing-links, .landing-nav-actions > .ui-btn { display: none; } .landing-menu-btn { display: grid; place-items: center; } .landing-mobile-menu { display: flex; flex-direction: column; padding: 8px 22px 18px; gap: 2px; border-top: 1px solid var(--border); background: var(--bg-card); } .landing-mobile-menu a { padding: 12px 0; font-size: 13px; border-bottom: 1px solid var(--border); } .landing-hero { padding-top: 52px; } .landing-hero h1 { font-size: 38px; } .landing-stats-grid { grid-template-columns: repeat(2,1fr); gap: 16px; } .landing-stats-grid div:nth-child(3) { border-left: 0; padding-left: 0; } .landing-section { padding-top: 52px; padding-bottom: 52px; } .landing-feature-grid, .landing-flow-grid { grid-template-columns: 1fr; } .landing-flow-grid div { border-right: 0; border-bottom: 1px solid var(--border); padding: 16px 0; } .landing-flow-grid div + div { padding-left: 0; } .landing-flow-grid div:last-child { border-bottom: 0; } .landing-cta-inner { align-items: flex-start; flex-direction: column; } .landing-footer-inner div { margin-left: 0; } }
</style>
