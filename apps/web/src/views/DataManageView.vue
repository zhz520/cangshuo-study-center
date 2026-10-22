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
  <div class="ui-page data-page">
    <header class="ui-page-head">
      <div><p class="ui-eyebrow">账户数据</p><h1 class="ui-title">学习数据</h1><p class="ui-subtitle">备份、恢复学习进度，或清理账户数据。</p></div>
      <RouterLink to="/export" class="ui-btn ui-btn-outline ui-btn-sm">导出题库</RouterLink>
    </header>

    <section class="ui-grid ui-grid-2 data-actions">
      <div class="ui-card ui-card-pad">
        <div class="ui-row"><span class="data-icon">↓</span><div><h2 class="ui-section-title">导出备份</h2><p class="ui-small ui-muted">将学习进度保存到本地 JSON</p></div></div>
        <div class="ui-row data-button-row"><button class="ui-btn ui-btn-outline" :disabled="loading" @click="exportData">下载 JSON</button><button class="ui-btn ui-btn-ghost" :disabled="loading" @click="copyData">复制数据</button></div>
      </div>
      <div class="ui-card ui-card-pad">
        <div class="ui-row"><span class="data-icon">↑</span><div><h2 class="ui-section-title">导入恢复</h2><p class="ui-small ui-muted">支持当前进度 JSON 和旧版备份</p></div></div>
        <div class="ui-row data-button-row"><button class="ui-btn ui-btn-primary" @click="showImportModal = true">导入数据</button><RouterLink to="/export" class="ui-btn ui-btn-ghost">完整题库导出</RouterLink></div>
      </div>
    </section>

    <section class="ui-card ui-card-pad data-info">
      <h2 class="ui-section-title">数据说明</h2>
      <div class="ui-grid ui-grid-3"><div><strong>进度备份</strong><p>这里导出的是学习进度和作答记录，不包含完整题库文件。</p></div><div><strong>题库导出</strong><p>需要管理员生成账户专属随机密码，导出范围仍受会员和专业权限限制。</p></div><div><strong>安全存储</strong><p>服务器端题库为加密存储；浏览器只显示当前授权范围内的内容。</p></div></div>
    </section>

    <section class="data-danger">
      <div class="ui-section-head"><div><h2 class="ui-section-title" style="color:var(--danger)">危险操作</h2><p class="ui-small ui-muted">清空后无法在服务器恢复</p></div></div>
      <div class="ui-card ui-card-pad data-danger-card"><div><strong>清空所有学习进度</strong><p class="ui-small ui-muted">会影响作答记录、错题和科目进度，请先导出备份。</p></div><button class="ui-btn ui-btn-outline" style="color:var(--danger);border-color:color-mix(in srgb,var(--danger) 30%,var(--border))" @click="clearAll">确认清空</button></div>
    </section>

    <div v-if="showImportModal" class="ui-sheet-backdrop" @click="showImportModal = false"></div>
    <div v-if="showImportModal" class="ui-modal" role="dialog" aria-modal="true" aria-label="导入学习数据">
      <div class="ui-row-between"><h3 class="ui-section-title">导入学习数据</h3><button class="web-icon-btn" @click="showImportModal = false">×</button></div>
      <p class="ui-small ui-muted">旧版备份需要选择目标专业和科目。</p>
      <div class="ui-stack mt-4"><select v-model="importMajorId" class="ui-select" @change="handleMajorChange"><option value="">选择专业</option><option v-for="m in majors" :key="m.id" :value="m.id">{{ m.name }}</option></select><select v-if="importMajorId" v-model="importSubjectId" class="ui-select"><option value="">选择科目</option><option v-for="s in subjects" :key="s.id" :value="s.id">{{ s.name }}</option></select><textarea v-model="importText" class="ui-textarea data-import-text" placeholder="粘贴 JSON 备份内容"></textarea></div>
      <div class="ui-row data-modal-actions"><button class="ui-btn ui-btn-outline" @click="showImportModal = false">取消</button><button class="ui-btn ui-btn-primary" :disabled="loading" @click="handleImport">执行导入</button></div>
    </div>
    <Toast ref="toast" />
    <ConfirmDialog v-model:show="confirmDialog.show" :title="confirmDialog.title" :content="confirmDialog.content" :type="confirmDialog.type" @confirm="confirmDialog.onConfirm" @cancel="confirmDialog.onCancel" />
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
