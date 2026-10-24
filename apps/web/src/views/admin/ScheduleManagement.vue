<script setup>
import AdminPageHeading from '../../components/ui/AdminPageHeading.vue';
import { ref, computed, onMounted } from 'vue';
import { admin } from '../../api';

const loading = ref(true);
const saving = ref(false);
const message = ref('');
const messageType = ref('success');

const PERIODS = ['早自习', '1', '2', '3', '4', '5', '6', '7', '晚自习1', '晚自习2', '晚自习3', '晚自习4'];
const DAYS = [
  { key: 1, label: '星期一' },
  { key: 2, label: '星期二' },
  { key: 3, label: '星期三' },
  { key: 4, label: '星期四' },
  { key: 5, label: '星期五' },
  { key: 6, label: '星期六' },
];

// Grid data: scheduleGrid[period][day_of_week] = { subject, location }
const scheduleGrid = ref({});

function initGrid() {
  const grid = {};
  PERIODS.forEach(period => {
    grid[period] = {};
    DAYS.forEach(day => {
      grid[period][day.key] = { subject: '', location: '' };
    });
  });
  return grid;
}

async function fetchSchedule() {
  loading.value = true;
  try {
    const { data } = await admin.getSchedule();
    const grid = initGrid();
    data.forEach(cell => {
      if (grid[cell.period] && grid[cell.period][cell.day_of_week]) {
        grid[cell.period][cell.day_of_week] = {
          subject: cell.subject || '',
          location: cell.location || ''
        };
      }
    });
    scheduleGrid.value = grid;
  } catch (err) {
    showMessage('加载失败：' + (err.response?.data?.error || err.message), 'error');
    scheduleGrid.value = initGrid();
  } finally {
    loading.value = false;
  }
}

async function saveSchedule() {
  saving.value = true;
  try {
    const cells = [];
    PERIODS.forEach((period, idx) => {
      DAYS.forEach(day => {
        const cell = scheduleGrid.value[period]?.[day.key];
        if (cell && cell.subject.trim()) {
          cells.push({
            period,
            day_of_week: day.key,
            subject: cell.subject.trim(),
            location: cell.location?.trim() || '',
            sort_order: idx
          });
        }
      });
    });
    await admin.saveSchedule({ schedule_name: '默认课程表', cells });
    showMessage('课程表已保存', 'success');
  } catch (err) {
    showMessage('保存失败：' + (err.response?.data?.error || err.message), 'error');
  } finally {
    saving.value = false;
  }
}

function clearAll() {
  if (confirm('确定要清空整个课程表吗？此操作不可撤销。')) {
    scheduleGrid.value = initGrid();
  }
}

function showMessage(text, type = 'success') {
  message.value = text;
  messageType.value = type;
  setTimeout(() => { message.value = ''; }, 3000);
}

function getPeriodLabel(period) {
  const num = parseInt(period);
  if (!isNaN(num)) return `第${num}节`;
  return period;
}

onMounted(fetchSchedule);
</script>

<template>
  <div class="admin-workspace ui-page admin-stack">
    <!-- Header -->
    <div class="admin-page-bar">
      <AdminPageHeading title="课程表" description="编辑班级课程表，修改后点击保存。" />
      <div class="admin-page-actions">
        <button class="ui-btn ui-btn-outline ui-btn-sm admin-control" type="button" @click="clearAll">清空</button>
        <button class="ui-btn ui-btn-primary ui-btn-sm admin-control" type="button" @click="saveSchedule" :disabled="saving">
          {{ saving ? '保存中…' : '保存课程表' }}
        </button>
      </div>
    </div>

    <!-- Message Toast -->
    <transition name="fade">
      <div
        v-if="message"
        :class="messageType === 'success' ? 'bg-emerald-50 text-emerald-700 border-emerald-200' : 'bg-red-50 text-red-700 border-red-200'"
        class="mb-6 p-4 rounded-xl border text-sm font-medium"
      >
        {{ message }}
      </div>
    </transition>

    <!-- Loading -->
    <div v-if="loading" class="text-center py-20">
      <div class="animate-spin w-8 h-8 border-4 border-indigo-500 border-t-transparent rounded-full mx-auto"></div>
      <p class="mt-4 text-sm text-slate-400">正在加载课程表...</p>
    </div>

    <!-- Schedule Grid -->
    <div v-else class="admin-table-card overflow-x-auto">
      <table class="w-full min-w-[800px] admin-data-table">
        <thead>
          <tr class="bg-slate-50 dark:bg-slate-700/50">
            <th class="px-4 py-3 text-left text-xs font-bold text-slate-500 dark:text-slate-400 uppercase w-24 border-b border-slate-200 dark:border-slate-600">节次</th>
            <th
              v-for="day in DAYS"
              :key="day.key"
              class="px-3 py-3 text-center text-xs font-bold text-slate-500 dark:text-slate-400 uppercase border-b border-slate-200 dark:border-slate-600 border-l"
            >
              {{ day.label }}
            </th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="(period, pidx) in PERIODS"
            :key="period"
            :class="pidx % 2 === 0 ? 'bg-white dark:bg-slate-800' : 'bg-slate-50/50 dark:bg-slate-750'"
            class="group hover:bg-indigo-50/30 dark:hover:bg-indigo-500/5 transition-colors"
          >
            <td class="px-4 py-2 border-b border-slate-100 dark:border-slate-700 font-bold text-xs text-slate-600 dark:text-slate-300 whitespace-nowrap">
              {{ getPeriodLabel(period) }}
            </td>
            <td
              v-for="day in DAYS"
              :key="day.key"
              class="px-1 py-1 border-b border-l border-slate-100 dark:border-slate-700"
            >
              <div class="schedule-cell">
                <input
                  v-model="scheduleGrid[period][day.key].subject"
                  type="text"
                  placeholder="课程"
                  class="admin-schedule-input"
                />
                <input
                  v-model="scheduleGrid[period][day.key].location"
                  type="text"
                  placeholder="教室"
                  class="admin-schedule-input is-location"
                />
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.schedule-cell { display:flex; flex-direction:column; gap:4px; }
.admin-schedule-input { width:100%; min-height:30px; padding:0 8px; border:1px solid var(--border); border-radius:6px; background:var(--bg-body); color:var(--text-main); font-size:11px; font-weight:500; outline:none; transition:border-color .15s ease, box-shadow .15s ease; }
.admin-schedule-input.is-location { min-height:26px; border-color:transparent; background:transparent; color:var(--text-sub); font-size:10px; font-weight:400; }
.admin-schedule-input:hover { border-color:var(--border-strong); }
.admin-schedule-input:focus { border-color:var(--primary); box-shadow:0 0 0 2px color-mix(in srgb,var(--primary) 14%,transparent); }
.admin-schedule-input::placeholder { color:var(--text-quote); }
.fade-enter-active, .fade-leave-active { transition: opacity 0.3s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
