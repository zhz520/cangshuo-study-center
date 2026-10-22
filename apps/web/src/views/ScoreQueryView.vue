<template>
  <div class="score-page">
    <header class="score-topbar">
      <div class="score-topbar-inner">
        <router-link to="/" class="score-back">← 返回主页</router-link>
        <span class="score-brand">联考成绩查询</span>
        <span class="score-topbar-spacer"></span>
      </div>
    </header>

    <main class="score-main">
      <section v-if="scoreDataList.length === 0" class="score-form-card" :class="{ 'is-loading': isLoading }">
        <div class="score-form-head">
          <div class="score-eyebrow">模拟考试</div>
          <h1>成绩查询</h1>
          <p>输入考生信息，查看联考成绩、单科表现与全省排名。</p>
        </div>

        <div class="score-fields">
          <label class="score-field">
            <span>考生姓名</span>
            <input v-model="form.student_name" class="ui-input" placeholder="例如：张三" @keyup.enter="handleQuery" />
          </label>

          <div class="score-or"><span>任选一种方式查询</span></div>

          <div class="score-field-grid">
            <label class="score-field">
              <span>准考证号</span>
              <input v-model="form.exam_number" class="ui-input" placeholder="输入准考证号" @keyup.enter="handleQuery" />
            </label>

            <div class="score-field score-combo" v-click-outside="closeSchoolDropdown">
              <span>学校</span>
              <div class="score-combo-control">
                <input
                  v-model="schoolSearchText"
                  class="ui-input"
                  placeholder="选择或搜索学校"
                  @focus="isSchoolDropdownOpen = true"
                  @click="isSchoolDropdownOpen = true"
                />
                <span class="score-combo-caret" :class="{ 'is-open': isSchoolDropdownOpen }">⌄</span>
              </div>
              <div v-show="isSchoolDropdownOpen" class="score-combo-menu">
                <p v-if="filteredSchools.length === 0" class="score-combo-empty">无可匹配学校</p>
                <ul v-else>
                  <li @click="selectSchool('')" :class="{ 'is-active': schoolSearchText === '' }">不限学校</li>
                  <li
                    v-for="school in filteredSchools"
                    :key="school"
                    @click="selectSchool(school)"
                    :class="{ 'is-active': schoolSearchText === school }"
                  >
                    {{ school }}
                  </li>
                </ul>
              </div>
            </div>
          </div>

          <button class="ui-btn ui-btn-primary ui-btn-lg score-submit" @click="handleQuery" :disabled="isLoading || !isFormValid">
            <span v-if="isLoading" class="score-submit-spinner"></span>
            {{ isLoading ? '正在查询…' : '查询成绩' }}
          </button>

          <p v-if="errorMsg" class="score-error">{{ errorMsg }}</p>
        </div>
      </section>

      <section v-else class="score-result">
        <div class="score-result-head">
          <div>
            <div class="score-eyebrow">成绩报告</div>
            <h2>{{ scoreDataList.length > 1 ? '匹配到 ' + scoreDataList.length + ' 条记录' : '成绩报告' }}</h2>
            <p>查询结果仅供本人参考。</p>
          </div>
          <button class="ui-btn ui-btn-outline ui-btn-sm" @click="resetQuery">重新查询</button>
        </div>

        <article v-for="(scoreData, index) in scoreDataList" :key="scoreData.id" class="score-record" :class="{ 'is-separated': index > 0 }">
          <div class="score-info-grid">
            <div class="score-info"><span>考生</span><strong>{{ scoreData.student_name }}</strong></div>
            <div class="score-info"><span>准考证号</span><strong>{{ scoreData.exam_number || '-' }}</strong></div>
            <div class="score-info is-wide">
              <span>学校</span>
              <strong>{{ scoreData.school_name || '未登记' }}</strong>
              <em v-if="scoreData.major_category">{{ scoreData.major_category }}</em>
            </div>
          </div>

          <div class="score-total">
            <div class="score-total-item">
              <span>全省排名</span>
              <strong><small>#</small>{{ scoreData.total_rank || '-' }}</strong>
            </div>
            <div class="score-total-divider"></div>
            <div class="score-total-item">
              <span>总成绩</span>
              <strong>{{ formatScore(scoreData.total_score) }}<small>分</small></strong>
            </div>
          </div>

          <h3 class="score-section-title">单科成绩</h3>
          <div class="score-subjects">
            <div v-for="(sub, i) in getScoreSubjects(scoreData)" :key="i" class="score-subject">
              <div class="score-subject-head">
                <strong>{{ sub.name }}</strong>
                <span>{{ sub.data?.total || '-' }} 分</span>
              </div>
              <div class="score-subject-meta">
                <span>客观题 {{ sub.data?.objective || '-' }}</span>
                <span>主观题 {{ sub.data?.subjective || '-' }}</span>
                <span>校内排名 {{ sub.data?.rank_school || '-' }}</span>
              </div>
            </div>
          </div>

          <ScoreAnalysisCard :record="scoreData" />
        </article>
      </section>
    </main>
  </div>
</template>


<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { publicApi } from '@/api';
import ScoreAnalysisCard from './components/ScoreAnalysisCard.vue';

const form = ref({
  student_name: '',
  exam_number: '',
  school_name: ''
});

// UI State
const isLoading = ref(false);
const errorMsg = ref('');
const scoreDataList = ref([]);
const schoolsList = ref([]);
const isSchoolDropdownOpen = ref(false);
const schoolSearchText = ref('');

// Custom Directives for clicking outside dropdown
const vClickOutside = {
  mounted(el, binding) {
    el.clickOutsideEvent = function(event) {
      if (!(el === event.target || el.contains(event.target))) {
        binding.value(event, el);
      }
    };
    document.body.addEventListener('click', el.clickOutsideEvent);
  },
  unmounted(el) {
    document.body.removeEventListener('click', el.clickOutsideEvent);
  }
};

onMounted(async () => {
  try {
    const res = await publicApi.getSchools();
    if (res.data && res.data.success) {
      schoolsList.value = res.data.data;
    }
  } catch (e) {
    console.error("Failed to fetch schools list:", e);
  }
});

const filteredSchools = computed(() => {
  if (!schoolSearchText.value) return schoolsList.value;
  const searchLower = schoolSearchText.value.toLowerCase();
  return schoolsList.value.filter(s => s && s.toLowerCase().includes(searchLower));
});

const selectSchool = (schoolName) => {
  schoolSearchText.value = schoolName;
  form.value.school_name = schoolName;
  isSchoolDropdownOpen.value = false;
};

// Valid if name is filled AND either exam number OR school selection exists
const isFormValid = computed(() => {
  const actualSchool = schoolSearchText.value;
  return form.value.student_name.trim() !== '' && 
    (form.value.exam_number.trim() !== '' || actualSchool.trim() !== '');
});

const getScoreSubjects = (record) => {
  let details = {};
  if (record?.score_details) {
    try {
      details = typeof record.score_details === 'string' ? JSON.parse(record.score_details) : record.score_details;
    } catch (e) {}
  }
  return [
    { name: '语文', icon: '📝', data: details.chinese },
    { name: '数学', icon: '📐', data: details.math },
    { name: '英语', icon: '🔤', data: details.english },
    { name: '专业综合', icon: '💼', data: details.professional },
  ];
};

const formatScore = (val) => {
  return val != null ? Number(val).toString() : '-';
};

const handleQuery = async () => {
  if (!isFormValid.value || isLoading.value) return;
  // sync selected textual school logic
  form.value.school_name = schoolSearchText.value;
  
  isLoading.value = true;
  errorMsg.value = '';
  
  try {
    const res = await publicApi.queryScore({
      student_name: form.value.student_name.trim(),
      exam_number: form.value.exam_number.trim(),
      school_name: form.value.school_name.trim()
    });
    
    if (res.data.success && res.data.data && res.data.data.length > 0) {
      scoreDataList.value = res.data.data;
    } else {
      errorMsg.value = '档案库未检索到您的信息，核对姓名与考号、学校是否完全一致。';
    }
  } catch (err) {
    errorMsg.value = err.response?.data?.error || '网络查询线路拥挤导致失败，请稍后刷新重试。';
  } finally {
    isLoading.value = false;
  }
};

const closeSchoolDropdown = () => {
  isSchoolDropdownOpen.value = false;
};

const resetQuery = () => {
  scoreDataList.value = [];
  form.value.student_name = '';
  form.value.exam_number = '';
  schoolSearchText.value = '';
  form.value.school_name = '';
  errorMsg.value = '';
};
</script>

<style scoped>

/* Copying Landing UI Styles directly for perfect match */
.landing {
  background: #080810;
}

.particles {
  position: absolute;
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
  position: absolute;
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
  width: 70vw; height: 70vw; background: radial-gradient(circle, #4f46e520, #7c3aed15);
  top: -20%; left: -20%;
}
.glow-2 {
  width: 50vw; height: 50vw; background: radial-gradient(circle, #a855f715, #ec489910);
  bottom: 0; right: -10%; animation-delay: -6s; animation-direction: alternate-reverse;
}
.glow-3 {
  width: 30vw; height: 30vw; background: radial-gradient(circle, #06b6d415, #3b82f610);
  top: 50%; left: 40%; animation-delay: -3s;
}
@keyframes drift {
  from { transform: translate(0, 0) scale(1); }
  to { transform: translate(5%, 8%) scale(1.1); }
}

.glass-panel {
  background: rgba(14, 15, 23, 0.75);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
}

.glass-input {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: #e8e8f0;
  padding-left: 3rem;
  padding-right: 1.5rem;
  padding-top: 1rem;
  padding-bottom: 1rem;
  border-radius: 1rem;
  outline: none;
  font-size: 1rem;
  transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}
.glass-input:focus {
  background: rgba(255, 255, 255, 0.06);
  border-color: #a855f7;
  box-shadow: 0 0 0 2px rgba(168, 85, 247, 0.2);
}

::placeholder {
  color: #52525b;
}

.btn-primary {
  display: inline-flex;
  background: linear-gradient(135deg, #4f46e5, #7c3aed, #a855f7);
  color: #fff;
  border: none;
  border-radius: 1rem;
  padding: 1rem 2rem;
  font-size: 1.1rem;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 8px 24px rgba(124, 58, 237, 0.4);
  transition: all 0.25s cubic-bezier(0.34, 1.56, 0.64, 1);
  text-decoration: none;
}
.btn-primary:hover:not(:disabled) {
  transform: translateY(-2px) scale(1.02);
  box-shadow: 0 12px 32px rgba(124, 58, 237, 0.6);
}

.gradient-text {
  background: linear-gradient(120deg, #a78bfa, #60a5fa, #34d399);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

@keyframes fadeInUp {
  from { opacity: 0; transform: translateY(40px) scale(0.98); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}
.animate-fade-in-up {
  animation: fadeInUp 0.7s cubic-bezier(0.16, 1, 0.3, 1) forwards;
}

.custom-scrollbar::-webkit-scrollbar {
  width: 5px;
}
.custom-scrollbar::-webkit-scrollbar-track {
  background: transparent;
}
.custom-scrollbar::-webkit-scrollbar-thumb {
  background-color: rgba(255, 255, 255, 0.2);
  border-radius: 10px;
}
.custom-scrollbar::-webkit-scrollbar-thumb:hover {
  background-color: rgba(255, 255, 255, 0.4);
}

/* Score query page */
.score-page { min-height: 100vh; display: flex; flex-direction: column; background: var(--bg-body); color: var(--text-main); }
.score-topbar { position: sticky; top: 0; z-index: 40; border-bottom: 1px solid var(--border); background: color-mix(in srgb, var(--bg-body) 92%, transparent); backdrop-filter: blur(14px); }
.score-topbar-inner { display: flex; align-items: center; justify-content: space-between; width: min(840px, 100%); height: 58px; margin: 0 auto; padding: 0 20px; }
.score-back { font-size: 12px; color: var(--text-sub); }
.score-back:hover { color: var(--text-main); }
.score-brand { font-size: 13px; font-weight: 600; }
.score-topbar-spacer { width: 76px; }
.score-main { flex: 1; width: min(760px, 100%); margin: 0 auto; padding: 40px 20px 64px; }
.score-form-card { max-width: 560px; margin: 0 auto; padding: 30px; border: 1px solid var(--border); border-radius: 16px; background: var(--bg-card); }
.score-form-card.is-loading { opacity: .6; pointer-events: none; }
.score-form-head { margin-bottom: 26px; }
.score-eyebrow { font-size: 10px; font-weight: 600; letter-spacing: .1em; text-transform: uppercase; color: var(--text-quote); }
.score-form-head h1 { margin-top: 6px; font-size: 27px; line-height: 1.25; font-weight: 700; letter-spacing: -.035em; }
.score-form-head p { margin-top: 8px; font-size: 12px; line-height: 1.7; color: var(--text-sub); }
.score-fields { display: flex; flex-direction: column; gap: 15px; }
.score-field { display: flex; flex-direction: column; gap: 6px; }
.score-field > span { font-size: 11px; font-weight: 600; color: var(--text-sub); }
.score-field .ui-input { min-height: 42px; }
.score-field-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.score-or { display: flex; align-items: center; gap: 10px; font-size: 10px; color: var(--text-quote); }
.score-or::before, .score-or::after { content: ''; flex: 1; height: 1px; background: var(--border); }
.score-combo { position: relative; }
.score-combo-control { position: relative; }
.score-combo-control .ui-input { padding-right: 32px; }
.score-combo-caret { position: absolute; right: 12px; top: 50%; transform: translateY(-50%); color: var(--text-quote); font-size: 12px; pointer-events: none; transition: transform .15s ease; }
.score-combo-caret.is-open { transform: translateY(-50%) rotate(180deg); }
.score-combo-menu { position: absolute; z-index: 50; left: 0; right: 0; top: calc(100% + 6px); max-height: 240px; overflow-y: auto; padding: 5px; border: 1px solid var(--border); border-radius: 10px; background: var(--bg-card); box-shadow: 0 14px 34px rgba(15, 23, 42, .12); }
.score-combo-menu li { padding: 8px 10px; border-radius: 6px; font-size: 12px; color: var(--text-main); cursor: pointer; }
.score-combo-menu li:hover { background: var(--bg-card-highlight); }
.score-combo-menu li.is-active { background: var(--primary-soft); color: var(--primary); }
.score-combo-empty { padding: 12px; text-align: center; font-size: 11px; color: var(--text-quote); }
.score-submit { width: 100%; margin-top: 4px; }
.score-submit-spinner { width: 14px; height: 14px; border: 2px solid rgba(255,255,255,.4); border-top-color: #fff; border-radius: 50%; animation: ui-spin .8s linear infinite; }
.score-error { padding: 10px 12px; border: 1px solid color-mix(in srgb, var(--danger) 20%, var(--border)); border-radius: 8px; background: color-mix(in srgb, var(--danger) 5%, var(--bg-card)); font-size: 12px; color: var(--danger); text-align: center; }
.score-result { max-width: 720px; margin: 0 auto; }
.score-result-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; padding-bottom: 16px; border-bottom: 1px solid var(--border); }
.score-result-head h2 { margin-top: 4px; font-size: 24px; font-weight: 700; letter-spacing: -.03em; }
.score-result-head p { margin-top: 4px; font-size: 11px; color: var(--text-quote); }
.score-record { padding-top: 24px; }
.score-record.is-separated { margin-top: 24px; border-top: 1px solid var(--border); }
.score-info-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
.score-info { display: flex; flex-direction: column; gap: 3px; padding: 13px 15px; border: 1px solid var(--border); border-radius: 10px; background: var(--bg-card); }
.score-info.is-wide { grid-column: span 1; }
.score-info span { font-size: 10px; color: var(--text-quote); }
.score-info strong { font-size: 14px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.score-info em { margin-left: 6px; font-size: 10px; font-style: normal; color: var(--text-sub); }
.score-total { display: flex; align-items: center; justify-content: center; gap: 40px; margin-top: 14px; padding: 26px 20px; border: 1px solid var(--border); border-radius: 14px; background: var(--bg-card); }
.score-total-item { display: flex; flex-direction: column; align-items: center; gap: 4px; }
.score-total-item span { font-size: 11px; color: var(--text-sub); }
.score-total-item strong { font-size: 34px; font-weight: 700; letter-spacing: -.04em; font-variant-numeric: tabular-nums; }
.score-total-item small { margin: 0 3px; font-size: 12px; font-weight: 500; color: var(--text-quote); }
.score-total-divider { width: 1px; height: 44px; background: var(--border); }
.score-section-title { margin: 24px 0 10px; font-size: 14px; font-weight: 650; }
.score-subjects { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.score-subject { padding: 14px 16px; border: 1px solid var(--border); border-radius: 10px; background: var(--bg-card); }
.score-subject-head { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; }
.score-subject-head strong { font-size: 13px; font-weight: 600; }
.score-subject-head span { font-size: 14px; font-weight: 650; font-variant-numeric: tabular-nums; }
.score-subject-meta { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 7px; font-size: 10px; color: var(--text-quote); }
@media (max-width: 620px) {
  .score-main { padding: 24px 14px 48px; }
  .score-form-card { padding: 20px; }
  .score-form-head h1 { font-size: 23px; }
  .score-field-grid { grid-template-columns: 1fr; }
  .score-info-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .score-info.is-wide { grid-column: span 2; }
  .score-subjects { grid-template-columns: 1fr; }
  .score-total { gap: 24px; padding: 20px 14px; }
  .score-total-item strong { font-size: 28px; }
  .score-result-head { align-items: flex-start; flex-direction: column; gap: 12px; }
}
</style>
