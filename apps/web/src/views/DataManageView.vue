<script setup>
import { ref, reactive, onMounted } from "vue";
import { useRouter } from "vue-router";
import { data as api } from "../api";
import Toast from "../components/ui/Toast.vue";
import ConfirmDialog from "../components/ui/ConfirmDialog.vue";

const router = useRouter();
const loading = ref(false);
const importText = ref("");
const importMajorId = ref("");
const importSubjectId = ref("");
const showImportModal = ref(false);
const toast = ref(null);

const majors = ref([]);
const subjects = ref([]);

// Confirmation Dialog State
const confirmDialog = reactive({
  show: false,
  title: "",
  content: "",
  type: "info",
  onConfirm: () => {},
  onCancel: () => {},
});

const showMessage = (msg, type = "success") => toast.value?.add(msg, type);

const loadMajors = async () => {
    try {
        const res = await api.getMajors();
        majors.value = res.data;
    } catch (e) { console.error(e); }
};

const handleMajorChange = async () => {
    importSubjectId.value = '';
    if (!importMajorId.value) {
        subjects.value = [];
        return;
    }
    try {
        const res = await api.getSubjects(importMajorId.value);
        subjects.value = res.data;
    } catch (e) { console.error(e); }
};

onMounted(() => {
    loadMajors();
});

const showConfirm = (title, content, type = "info") => {
  return new Promise((resolve) => {
    confirmDialog.title = title;
    confirmDialog.content = content;
    confirmDialog.type = type;
    confirmDialog.show = true;
    confirmDialog.onConfirm = () => {
      confirmDialog.show = false;
      resolve(true);
    };
    confirmDialog.onCancel = () => {
      confirmDialog.show = false;
      resolve(false);
    };
  });
};

// Export Data (Download JSON)
const exportData = async () => {
  try {
    loading.value = true;
    const res = await api.getProgress();
    const dataStr = JSON.stringify(res.data, null, 2);
    const blob = new Blob([dataStr], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `study_backup_${new Date().toISOString().slice(0, 10)}.json`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    toast.value.add("导出成功，已开始下载", "success");
  } catch (e) {
    toast.value.add("导出失败: " + e.message, "error");
  } finally {
    loading.value = false;
  }
};

// Copy Data to Clipboard
const copyData = async () => {
  try {
    loading.value = true;
    const res = await api.getProgress();
    const dataStr = JSON.stringify(res.data);
    await navigator.clipboard.writeText(dataStr);
    toast.value.add("数据已复制到剪贴板！", "success");
  } catch (e) {
    toast.value.add("复制失败: " + e.message, "error");
  } finally {
    loading.value = false;
  }
};

// Import Data
const handleImport = async () => {
  if (!importText.value.trim())
    return toast.value.add("请输入JSON数据", "warning");
  try {
    loading.value = true;
    let data = JSON.parse(importText.value);

    // Handle Version 1.0 Wrapper ({ version: "1.0", data: { ... } })
    if (data.version && data.data) {
      data = data.data;
    }

    // Pre-check confirm
    const confirmed = await showConfirm(
      "确认导入",
      "即将导入数据，确定继续吗？\n(可能会覆盖部分现有记录)",
      "info",
    );
    if (!confirmed) {
      loading.value = false;
      return;
    }

    // Modern format: Array of progress objects
    if (Array.isArray(data)) {
        let successCount = 0;
        for (const item of data) {
            if (item.question_id) {
                await api.syncProgress(item);
                successCount++;
            }
        }
        toast.value.add(`成功导入 ${successCount} 条记录`, "success");
        showImportModal.value = false;
        importText.value = "";
        return;
    }

    // Legacy format: Object with numeric keys or named sections
    // If it's a "Global" backup from old version, it has keys like 'jy', 'aa'.
    // Users should select which subject to import into if it's a single bank.
    
    if (!importSubjectId.value) {
        toast.value.add("导入老版本数据请先选择目标科目", "warning");
        loading.value = false;
        return;
    }

    let items = [];
    let rawAnswers = null;

    // Detect if nested (like data['jy']) or flat
    if (data.answers) rawAnswers = data.answers;
    else if (typeof data === 'object') rawAnswers = data;

    if (rawAnswers) {
        const hasZero = rawAnswers.hasOwnProperty("0");
        const offset = hasZero ? 1 : 0;

        for (const [key, val] of Object.entries(rawAnswers)) {
            if (!val) continue;
            const ans = val.choice || val.userAnswer || val.answer;
            if (ans && !isNaN(parseInt(key))) {
                items.push({
                    original_id: parseInt(key) + offset,
                    is_correct: val.isCorrect || false,
                    answer: ans
                });
            }
        }
    }

    if (items.length > 0) {
        await api.syncBatch({ subject_id: importSubjectId.value, items });
        toast.value.add(`导入成功！恢复了 ${items.length} 条记录到所选科目`, "success");
        showImportModal.value = false;
        importText.value = "";
    } else {
        toast.value.add("未发现有效记录，请检查JSON格式", "warning");
    }

  } catch (e) {
    toast.value.add("数据解析错误: " + e.message, "error");
  } finally {
    loading.value = false;
  }
};

const clearAll = async () => {
  const confirmed = await showConfirm(
    "危险操作",
    "此操作将永久清空服务器上的所有进度数据！\n是否继续？",
    "danger",
  );

  if (confirmed) {
    try {
      await api.resetProgress("all");
      toast.value.add("所有进度已清空", "success");
    } catch (e) {
      toast.value.add("清空失败: " + e.message, "error");
    }
  }
};
</script>

<template>
  <div class="min-h-screen bg-slate-50 dark:bg-slate-900 pb-20">
    <!-- Header -->
    <header
      class="bg-white dark:bg-slate-800 shadow-sm p-4 flex items-center gap-4 sticky top-0 z-10"
    >
      <button
        @click="router.back()"
        class="w-10 h-10 rounded-full bg-slate-100 dark:bg-slate-700 flex items-center justify-center text-slate-600 dark:text-slate-300"
      >
        ←
      </button>
      <h1 class="text-lg font-bold text-slate-800 dark:text-slate-100">
        数据管理
      </h1>
    </header>

    <main class="p-6 max-w-lg mx-auto space-y-6">
      <!-- Export Card -->
      <section
        class="bg-white dark:bg-slate-800 rounded-3xl p-6 shadow-sm border border-slate-100 dark:border-slate-700"
      >
        <div class="flex items-center gap-3 mb-4">
          <span
            class="w-10 h-10 rounded-2xl bg-indigo-100 text-indigo-600 flex items-center justify-center text-xl"
            >📤</span
          >
          <div>
            <h2 class="font-bold text-slate-800 dark:text-slate-100">
              导出备份
            </h2>
            <p class="text-xs text-slate-500">将进度保存到本地</p>
          </div>
        </div>
        <div class="grid grid-cols-2 gap-3">
          <button
            @click="exportData"
            :disabled="loading"
            class="py-3 px-4 rounded-xl bg-slate-50 dark:bg-slate-700 border border-slate-200 dark:border-slate-600 font-bold text-sm text-slate-700 dark:text-slate-200 hover:bg-indigo-50 hover:border-indigo-200 hover:text-indigo-600 transition-colors"
          >
            下载 JSON
          </button>
          <button
            @click="copyData"
            :disabled="loading"
            class="py-3 px-4 rounded-xl bg-slate-50 dark:bg-slate-700 border border-slate-200 dark:border-slate-600 font-bold text-sm text-slate-700 dark:text-slate-200 hover:bg-indigo-50 hover:border-indigo-200 hover:text-indigo-600 transition-colors"
          >
            复制到剪贴板
          </button>
        </div>
      </section>

      <!-- Import Card -->
      <section
        class="bg-white dark:bg-slate-800 rounded-3xl p-6 shadow-sm border border-slate-100 dark:border-slate-700"
      >
        <div class="flex items-center gap-3 mb-4">
          <span
            class="w-10 h-10 rounded-2xl bg-emerald-100 text-emerald-600 flex items-center justify-center text-xl"
            >📥</span
          >
          <div>
            <h2 class="font-bold text-slate-800 dark:text-slate-100">
              导入恢复
            </h2>
            <p class="text-xs text-slate-500">从备份文件恢复进度</p>
          </div>
        </div>
        <button
          @click="showImportModal = true"
          class="w-full py-3 rounded-xl bg-emerald-500 text-white font-bold shadow-lg shadow-emerald-200 dark:shadow-none hover:bg-emerald-600 transition-colors"
        >
          导入数据
        </button>
      </section>

      <!-- Danger Zone -->
      <section class="mt-10">
        <h3
          class="text-xs font-bold text-slate-400 uppercase tracking-wider mb-3 ml-2"
        >
          危险区域
        </h3>
        <div
          class="bg-red-50 dark:bg-red-900/10 rounded-3xl p-6 border border-red-100 dark:border-red-900/30"
        >
          <div class="flex items-center gap-3 mb-4">
            <span
              class="w-10 h-10 rounded-2xl bg-red-100 text-red-600 flex items-center justify-center text-xl"
              >🗑️</span
            >
            <div>
              <h2 class="font-bold text-red-600 dark:text-red-400">
                清空所有数据
              </h2>
              <p class="text-xs text-slate-500">此操作无法撤销</p>
            </div>
          </div>
          <button
            @click="clearAll"
            class="w-full py-3 rounded-xl bg-white dark:bg-slate-800 border-2 border-red-100 dark:border-red-900/50 text-red-500 font-bold hover:bg-red-50 transition-colors"
          >
            确认清空
          </button>
        </div>
      </section>
    </main>

    <!-- Import Modal -->
    <div
      v-if="showImportModal"
      class="fixed inset-0 z-50 flex items-center justify-center p-4"
    >
      <div
        class="absolute inset-0 bg-black/40 backdrop-blur-sm"
        @click="showImportModal = false"
      ></div>
      <div
        class="bg-white dark:bg-slate-800 rounded-3xl w-full max-w-md p-6 relative z-10 shadow-2xl animate-fade-in"
      >
        <h3 class="font-bold text-lg mb-2 text-slate-800 dark:text-slate-100">
          导入数据恢复
        </h3>

        <!-- Category Select for Legacy Data -->
        <div class="mb-4 space-y-2">
          <label class="text-xs text-slate-500 block"
            >若导入老版本JSON数据，请选择目标科目：</label
          >
          <select
            v-model="importMajorId"
            @change="handleMajorChange"
            class="w-full p-2 rounded-lg bg-slate-50 dark:bg-slate-900 border border-slate-200 dark:border-slate-700 text-sm dark:text-slate-300"
          >
            <option value="">-- 选择专业 --</option>
            <option v-for="m in majors" :key="m.id" :value="m.id">{{ m.name }}</option>
          </select>
          <select
            v-if="importMajorId"
            v-model="importSubjectId"
            class="w-full p-2 rounded-lg bg-slate-50 dark:bg-slate-900 border border-slate-200 dark:border-slate-700 text-sm dark:text-slate-300"
          >
            <option value="">-- 选择科目 --</option>
            <option v-for="s in subjects" :key="s.id" :value="s.id">{{ s.name }}</option>
          </select>
        </div>

        <textarea
          v-model="importText"
          class="w-full h-40 bg-slate-50 dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-700 p-3 text-xs font-mono mb-4 focus:ring-2 focus:ring-indigo-500 outline-none dark:text-slate-300 resize-none"
          placeholder="粘贴JSON内容..."
        ></textarea>
        <div class="flex gap-3">
          <button
            @click="showImportModal = false"
            class="flex-1 py-3 rounded-xl bg-slate-100 dark:bg-slate-700 text-slate-500 font-bold hover:bg-slate-200 dark:hover:bg-slate-600 transition-colors"
          >
            取消
          </button>
          <button
            @click="handleImport"
            class="flex-1 py-3 rounded-xl bg-indigo-500 text-white font-bold shadow-lg shadow-indigo-200 hover:bg-indigo-600 transition-colors"
          >
            执行导入
          </button>
        </div>
      </div>
    </div>

    <!-- Toast & Confirm Dialog -->
    <Toast ref="toast" />
    <ConfirmDialog
      v-model:show="confirmDialog.show"
      :title="confirmDialog.title"
      :content="confirmDialog.content"
      :type="confirmDialog.type"
      @confirm="confirmDialog.onConfirm"
      @cancel="confirmDialog.onCancel"
    />
  </div>
</template>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.2s ease-out;
}
@keyframes fadeIn {
  from {
    opacity: 0;
    transform: scale(0.95);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}
</style>
