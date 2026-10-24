<script setup>
import AdminPageHeading from '../../components/ui/AdminPageHeading.vue';
import { ref, reactive, onMounted, computed, watch } from 'vue';
import { admin } from '../../api';
import Toast from '../../components/ui/Toast.vue';
import ConfirmDialog from '../../components/ui/ConfirmDialog.vue';
import MarkdownRenderer from '../../components/ui/MarkdownRenderer.vue';
import { exportToWord } from '../../utils/export-docx';

const loading = ref(false);
const toast = ref(null);

// Data
const majors = ref([]);
const filterSubjects = ref([]);
const modalSubjects = ref([]);
const questions = ref([]);
const pagination = reactive({
    page: 1,
    pageSize: 10,
    total: 0,
    totalPages: 0
});

// Filters
const filters = reactive({
    major_id: '',
    subject_id: '',
    type: '',
    keyword: ''
});

// Selection
const selectedMajor = computed(() => majors.value.find(m => m.id == filters.major_id));
const selectedSubject = computed(() => filterSubjects.value.find(s => s.id == filters.subject_id));

// Modal & Forms
const showQuestionModal = ref(false);
// Batch Selection
const selectedIds = ref([]);
const selectAll = ref(false);

const editingQuestion = reactive({
    id: null,
    major_id: '',
    subject_id: '',
    type: 'single',
    stem: '',
    options: [],
    answer: [],
    analysis: '',
    difficulty: 1, // 1: Easy, 2: Medium, 3: Hard
    tags: [], // Array of strings
    tagInput: '', // Temporary input for tags
    is_active: 1,
    jsonMode: false,
    jsonContent: '',
    previewMode: false,
    subQuestions: [] // For material type
});

// Image Upload
const fileInput = ref(null);
const activeTextarea = ref(null); // 'stem' or 'analysis'

// Import JSON Modal
const showJsonImportModal = ref(false);
const jsonImportContent = ref('');
const jsonImportResult = ref(null);
const isImportingJson = ref(false);

// Import Excel Modal
const showExcelImportModal = ref(false);
const excelFile = ref(null);
const excelImportResult = ref(null);
const isImporting = ref(false);
const importTarget = computed(() => selectedMajor.value && selectedSubject.value ? `${selectedMajor.value.name} / ${selectedSubject.value.name}` : '');

// Confirmation
const confirmDialog = ref(null);

const showMessage = (msg, type = 'success') => toast.value?.add(msg, type);

// Initial Load
const loadMajors = async () => {
    try {
        const res = await admin.getMajors();
        majors.value = res.data;
    } catch (e) { showMessage('加载专业失败', 'error'); }
};

// Start Loading Chain
watch(() => filters.major_id, async (newVal) => {
    if (!newVal) {
        filterSubjects.value = [];
        filters.subject_id = '';
        return;
    }
    const mId = parseInt(newVal);
    try {
        const res = await admin.getSubjects(mId);
        filterSubjects.value = res.data;
        filters.subject_id = ''; // Reset subject
        loadQuestions();
    } catch (e) { showMessage('加载科目失败', 'error'); }
});

watch(() => filters.subject_id, () => {
    pagination.page = 1;
    loadQuestions();
});

watch(() => filters.type, () => {
    pagination.page = 1;
    loadQuestions();
});

// Modal Logic
watch(() => editingQuestion.major_id, async (newVal) => {
    if (!newVal) {
        modalSubjects.value = [];
        return;
    }
    try {
        const res = await admin.getSubjects(newVal);
        modalSubjects.value = res.data;
    } catch (e) { showMessage('加载编辑科目失败', 'error'); }
});

const loadQuestions = async () => {
    loading.value = true;
    try {
        const params = {
            page: pagination.page,
            limit: pagination.pageSize,
            major_id: filters.major_id || undefined,
            type: filters.type || undefined,
            subject_id: filters.subject_id || undefined,
            search: filters.keyword || undefined
        };
        const res = await admin.getQuestions(params);
        questions.value = res.data.data || [];
        if (res.data.pagination) {
            pagination.total = res.data.pagination.total || 0;
            pagination.totalPages = res.data.pagination.totalPages || 0;
        } else {
            // Fallback for potential legacy/flat structure
            pagination.total = res.data.total || 0;
            pagination.totalPages = res.data.totalPages || 0;
        }
    } catch (e) {
        showMessage('加载题目失败', 'error');
        questions.value = [];
    } finally {
        loading.value = false;
    }
};

const handleSearch = () => {
    pagination.page = 1;
    loadQuestions();
};

const changePage = (page) => {
    if (page < 1 || page > pagination.totalPages) return;
    pagination.page = page;
    loadQuestions();
};

// CRUD
const initQuestionForm = () => {
    editingQuestion.id = null;
    editingQuestion.major_id = filters.major_id;
    editingQuestion.subject_id = filters.subject_id;
    editingQuestion.type = 'single';
    editingQuestion.stem = '';
    editingQuestion.options = [
        { label: 'A', content: '' },
        { label: 'B', content: '' },
        { label: 'C', content: '' },
        { label: 'D', content: '' }
    ];
    editingQuestion.answer = [];
    editingQuestion.analysis = '';
    editingQuestion.difficulty = 1;
    editingQuestion.tags = [];
    editingQuestion.tagInput = '';
    editingQuestion.is_active = 1;
    editingQuestion.jsonMode = false;
    editingQuestion.jsonContent = '';
    editingQuestion.subQuestions = [];
};

const openAddQuestion = () => {
    initQuestionForm();
    showQuestionModal.value = true;
};

const openEditQuestion = async (q) => {
    // Clone logic
    editingQuestion.id = q.id;
    editingQuestion.major_id = q.major_id;
    editingQuestion.subject_id = q.subject_id;
    editingQuestion.type = q.type;
    editingQuestion.stem = q.content || q.stem || '';

    // Load subjects for this major
    if (q.major_id) {
        try {
            const res = await admin.getSubjects(q.major_id);
            modalSubjects.value = res.data;
        } catch (e) { showMessage('加载科目失败', 'error'); }
    }

    // Parse options - handle both object {"A":"text"} and array [{label,content}] formats
    let parsedOptions = q.options;
    if (typeof q.options === 'string') {
        try { parsedOptions = JSON.parse(q.options); } catch (e) { parsedOptions = {}; }
    }
    // Convert object format {"A":"text","B":"text"} to array format [{label:"A",content:"text"}]
    if (parsedOptions && !Array.isArray(parsedOptions) && typeof parsedOptions === 'object') {
        editingQuestion.options = Object.entries(parsedOptions).map(([key, val]) => ({
            label: key,
            content: val
        }));
    } else if (Array.isArray(parsedOptions)) {
        editingQuestion.options = parsedOptions;
    } else {
        editingQuestion.options = [
            { label: 'A', content: '' },
            { label: 'B', content: '' },
            { label: 'C', content: '' },
            { label: 'D', content: '' }
        ];
    }

    // Parse answer
    let parsedAnswer = q.answer;
    if (typeof q.answer === 'string') {
        try { parsedAnswer = JSON.parse(q.answer); } catch (e) {
            // If comma separated "A,B,C"
            parsedAnswer = q.answer.split(',').map(s => s.trim());
        }
    }
    if (!Array.isArray(parsedAnswer)) parsedAnswer = [parsedAnswer];

    // For judge questions: map A/B to 正确/错误 for display
    if (q.type === 'judge') {
        parsedAnswer = parsedAnswer.map(a => {
            if (a === 'A' || a === '对' || a === '√') return '正确';
            if (a === 'B' || a === '错' || a === '×') return '错误';
            return a;
        });
    }
    editingQuestion.answer = parsedAnswer;

    editingQuestion.analysis = q.analysis || '';
    editingQuestion.difficulty = q.difficulty || 1;

    // Parse tags
    let parsedTags = q.tags;
    if (typeof q.tags === 'string') {
        try { parsedTags = JSON.parse(q.tags); } catch (e) { parsedTags = []; }
    }
    editingQuestion.tags = Array.isArray(parsedTags) ? parsedTags : [];
    editingQuestion.tagInput = '';
    editingQuestion.is_active = q.is_active !== undefined ? q.is_active : 1;
    editingQuestion.jsonMode = false;

    // Pre-fill JSON mode content
    updateJsonFromForm();

    // Material options parsing
    if (q.type === 'material') {
        try {
            editingQuestion.subQuestions = typeof q.options === 'string' ? JSON.parse(q.options) : q.options;
            if (!Array.isArray(editingQuestion.subQuestions)) editingQuestion.subQuestions = [];
        } catch (e) { editingQuestion.subQuestions = []; }
    }

    showQuestionModal.value = true;
};

const deleteQuestion = async (id) => {
    const confirmed = await confirmDialog.value.confirm({
        title: '删除题目',
        message: '确定要删除这道题目吗？',
        type: 'danger',
        confirmText: '删除',
        cancelText: '取消'
    });
    if (!confirmed) return;
    try {
        await admin.deleteQuestion(id);
        showMessage('删除成功');
        loadQuestions();
    } catch (e) { showMessage('删除失败', 'error'); }
};

// Form Logic
const addOption = () => {
    const nextLabel = String.fromCharCode(65 + editingQuestion.options.length);
    editingQuestion.options.push({ label: nextLabel, content: '' });
};

const removeOption = (index) => {
    editingQuestion.options.splice(index, 1);
    // Re-label
    editingQuestion.options.forEach((opt, idx) => {
        opt.label = String.fromCharCode(65 + idx);
    });
};

const toggleAnswer = (label) => {
    if (editingQuestion.type === 'single' || editingQuestion.type === 'judge') {
        editingQuestion.answer = [label];
    } else { // multiple
        const idx = editingQuestion.answer.indexOf(label);
        if (idx > -1) editingQuestion.answer.splice(idx, 1);
        else editingQuestion.answer.push(label);
        editingQuestion.answer.sort();
    }
};

const updateJsonFromForm = () => {
    const obj = {
        stem: editingQuestion.stem,
        type: editingQuestion.type,
        options: editingQuestion.options,
        answer: editingQuestion.answer,
        analysis: editingQuestion.analysis,
        difficulty: editingQuestion.difficulty,
        tags: editingQuestion.tags
    };
    editingQuestion.jsonContent = JSON.stringify(obj, null, 2);
};

const updateFormFromJson = () => {
    try {
        const obj = JSON.parse(editingQuestion.jsonContent);
        editingQuestion.stem = obj.stem || '';
        editingQuestion.type = obj.type || 'single';
        editingQuestion.options = obj.options || [];
        editingQuestion.answer = obj.answer || [];
        editingQuestion.analysis = obj.analysis || '';
        editingQuestion.difficulty = obj.difficulty || 1;
        editingQuestion.tags = obj.tags || [];
    } catch (e) {
        showMessage('JSON 格式错误', 'error');
    }
};

const addTag = () => {
    const val = editingQuestion.tagInput.trim();
    if (val && !editingQuestion.tags.includes(val)) {
        editingQuestion.tags.push(val);
    }
    editingQuestion.tagInput = '';
};

const removeTag = (index) => {
    editingQuestion.tags.splice(index, 1);
};

// Sub-question Logic
const addSubQuestion = () => {
    editingQuestion.subQuestions.push({
        type: 'single',
        stem: '',
        options: [
            { label: 'A', content: '' },
            { label: 'B', content: '' },
            { label: 'C', content: '' },
            { label: 'D', content: '' }
        ],
        answer: [],
        analysis: ''
    });
};

const removeSubQuestion = (idx) => {
    editingQuestion.subQuestions.splice(idx, 1);
};

const toggleSubAnswer = (subQ, label) => {
    if (subQ.type === 'single' || subQ.type === 'judge') {
        subQ.answer = [label];
    } else { // multiple
        const idx = subQ.answer.indexOf(label);
        if (idx > -1) subQ.answer.splice(idx, 1);
        else subQ.answer.push(label);
        subQ.answer.sort();
    }
};

const saveQuestion = async () => {
    if (editingQuestion.jsonMode) updateFormFromJson();

    // Basic Validation
    if (!editingQuestion.major_id || !editingQuestion.subject_id) return showMessage('请选择专业和科目', 'warning');
    if (!editingQuestion.stem) return showMessage('请输入题干', 'warning');
    if (editingQuestion.type !== 'fill' && editingQuestion.type !== 'sort' && editingQuestion.answer.length === 0) return showMessage('请设置正确答案', 'warning');

    const payload = {
        major_id: editingQuestion.major_id,
        subject_id: editingQuestion.subject_id,
        type: editingQuestion.type,
        content: editingQuestion.stem,
        options: editingQuestion.type === 'judge'
            ? { A: '正确', B: '错误' }
            : editingQuestion.options,
        answer: editingQuestion.type === 'sort'
            ? editingQuestion.options.map(o => o.content) // For sort, answer is the order of options
            : editingQuestion.type === 'judge'
                ? (editingQuestion.answer.includes('正确') ? 'TRUE' : 'FALSE') // Convert back to TRUE/FALSE
                : editingQuestion.answer,
        analysis: editingQuestion.analysis,
        difficulty: editingQuestion.difficulty,
        tags: editingQuestion.tags,
        is_active: editingQuestion.is_active
    };

    // Stringify JSON fields (if backend expects strings, which it seems to based on previous code)
    // Actually, backend questions.js does JSON.stringify(options) and answer.
    // But let's check backend. It does:
    // JSON.stringify(tags || []), content, JSON.stringify(options), answer
    // So options should be object/array. Answer is string or JSON string.

    // Wait, let's keep it consistent with what backend expects.
    // Backend: option is JSON.stringify(options). answer is answer.
    // If answer is array (multiple/sort), it should be stringified?
    // Backend: answer, ...
    // If answer is ['A', 'B'], backend stores "A,B" or JSON?
    // Let's check import.js usage: type==='fill' ? answer : JSON.stringify(answer).
    // So for multiple/sort, we should stringify answer.

    // Let's adjust payload construction to be safe
    const finalOptions = editingQuestion.type === 'judge' ? { A: '正确', B: '错误' } : editingQuestion.options;
    let finalAnswer = editingQuestion.answer;

    if (editingQuestion.type === 'sort') {
        finalAnswer = editingQuestion.options.map(o => o.content);
    } else if (editingQuestion.type === 'judge') {
         // UI uses ['正确'] or ['错误']
         const val = Array.isArray(editingQuestion.answer) ? editingQuestion.answer[0] : editingQuestion.answer;
         finalAnswer = (val === '正确' || val === 'TRUE') ? 'TRUE' : 'FALSE';
    }

    let payloadOptions = finalOptions;
    let payloadAnswer = finalAnswer;

    if (editingQuestion.type === 'material') {
        // For material: options field stores subQuestions
        payloadOptions = editingQuestion.subQuestions.map(sq => {
             // Simplify sub-question structure for storage
             const sqOptions = sq.type === 'judge' ? { A: '正确', B: '错误' } : sq.options;
             let sqAnswer = sq.answer;
             if (sq.type === 'judge') {
                 const val = Array.isArray(sq.answer) ? sq.answer[0] : sq.answer;
                 sqAnswer = (val === '正确' || val === 'TRUE') ? 'TRUE' : 'FALSE';
             }
             return {
                 ...sq,
                 options: sqOptions,
                 answer: sqAnswer
             };
        });
        payloadAnswer = 'SEE_OPTIONS'; // Dummy answer
    }

    const reqBody = {
        ...payload,
        options: payloadOptions, // Backend will stringify this
        answer: (Array.isArray(payloadAnswer) || typeof payloadAnswer === 'object') ? JSON.stringify(payloadAnswer) : payloadAnswer
    };

    try {
        if (editingQuestion.id) {
            await admin.updateQuestion(editingQuestion.id, reqBody);
            showMessage('更新成功');
        } else {
            await admin.addQuestion(reqBody);
            showMessage('添加成功');
        }
        showQuestionModal.value = false;
        loadQuestions();
    } catch (e) {
        showMessage('保存失败: ' + (e.response?.data?.error || e.message), 'error');
    }
};

// JSON Import
const openJsonImport = () => {
    jsonImportContent.value = '';
    jsonImportResult.value = null;
    showJsonImportModal.value = true;
};

const handleJsonImport = async () => {
    if (isImportingJson.value) return;
    isImportingJson.value = true;
    jsonImportResult.value = null;
    try {
        const data = JSON.parse(jsonImportContent.value);
        if (!Array.isArray(data)) throw new Error('Root must be an array');
        // Basic check
        if (data.length === 0) return showMessage('数据为空', 'warning');

        // Ensure subject selected
        if (!filters.major_id || !filters.subject_id) return showMessage('请先在筛选栏选择要导入的专业和科目', 'warning');

        const res = await admin.importQuestionsJson({
            major_id: filters.major_id,
            subject_id: filters.subject_id,
            questions: data
        });

        const result = res.data;
        jsonImportResult.value = result;
        if (result.errors && result.errors.length > 0) {
            showMessage(`${result.message}（${result.errors.length} 条出错）`, 'warning');
        } else {
            showMessage(result.message || `成功导入 ${data.length} 道题目`);
        }
        loadQuestions();
    } catch (e) {
        const message = e.response?.data?.error || e.message;
        jsonImportResult.value = { error: message };
        showMessage('导入失败: ' + message, 'error');
    } finally {
        isImportingJson.value = false;
    }
};

// Excel Import
const openExcelImport = () => {
    excelFile.value = null;
    excelImportResult.value = null;
    showExcelImportModal.value = true;
};

const handleExcelSelect = (event) => {
    const file = event.target.files[0];
    if (file) {
        excelFile.value = file;
        excelImportResult.value = null; // Reset result on new file
    }
};

const handleExcelUpload = async () => {
    if (!excelFile.value) return showMessage('请选择 Excel 文件', 'warning');
    if (!filters.major_id || !filters.subject_id) return showMessage('请先在筛选栏选择要导入的专业和科目', 'warning');

    isImporting.value = true;
    const formData = new FormData();
    formData.append('file', excelFile.value);
    formData.append('subject_id', filters.subject_id);
    formData.append('major_id', filters.major_id);

    try {
        const res = await admin.importQuestionsExcel(formData);
        const result = res.data || {};
        excelImportResult.value = {
            successCount: Number(result.successCount || 0),
            failureCount: Number(result.failureCount ?? (Array.isArray(result.errors) ? result.errors.length : 0)),
            totalCount: Number(result.totalCount || 0),
            errors: Array.isArray(result.errors) ? result.errors : [],
        };
        showMessage(result.message || '导入完成', result.errors?.length ? 'warning' : 'success');
        loadQuestions();
    } catch (e) {
        showMessage('导入失败: ' + (e.response?.data?.error || e.message), 'error');
        excelImportResult.value = { error: e.response?.data?.error || e.message };
    } finally {
        isImporting.value = false;
    }
};

const downloadTemplate = () => {
    const link = document.createElement("a");
    link.setAttribute("href", "/question-import-template.xlsx");
    link.setAttribute("download", "question-import-template.xlsx");
    link.style.visibility = 'hidden';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
};

// Image Upload Logic
const triggerImageUpload = (target) => {
    activeTextarea.value = target;
    fileInput.value.click();
};

const handleImageSelection = async (event) => {
    const file = event.target.files[0];
    if (!file) return;

    if (!file.type.startsWith('image/')) {
        showMessage('请选择图片文件', 'warning');
        return;
    }

    const formData = new FormData();
    formData.append('image', file);

    try {
        showMessage('正在上传图片...', 'info');
        const res = await admin.uploadImage(formData);
        const imageUrl = res.data.url;

        const imageMarkdown = `\n![图片描述](${imageUrl})\n`;

        if (activeTextarea.value === 'stem') {
            editingQuestion.stem += imageMarkdown;
        } else if (activeTextarea.value === 'analysis') {
            editingQuestion.analysis += imageMarkdown;
        } else {
             // Default to stem if unknown (shouldn't happen)
             editingQuestion.stem += imageMarkdown;
        }

        showMessage('图片上传成功');
    } catch (e) {
        showMessage('图片上传失败: ' + (e.response?.data?.error || e.message), 'error');
    } finally {
        // Reset input
        event.target.value = '';
    }
};

// --- Batch Operations ---
const toggleSelect = (id) => {
    const idx = selectedIds.value.indexOf(id);
    if (idx > -1) selectedIds.value.splice(idx, 1);
    else selectedIds.value.push(id);
    selectAll.value = selectedIds.value.length === questions.value.length;
};

const toggleSelectAll = () => {
    if (selectAll.value) {
        selectedIds.value = [];
        selectAll.value = false;
    } else {
        selectedIds.value = questions.value.map(q => q.id);
        selectAll.value = true;
    }
};

const batchDelete = async () => {
    if (selectedIds.value.length === 0) return showMessage('请先选择题目', 'warning');
    const confirmed = await confirmDialog.value.confirm({
        title: '批量删除',
        message: `确定要删除选中的 ${selectedIds.value.length} 道题目吗？此操作不可恢复！`,
        type: 'danger',
        confirmText: `删除 ${selectedIds.value.length} 题`,
        cancelText: '取消'
    });
    if (!confirmed) return;

    try {
        loading.value = true;
        let successCount = 0;
        for (const id of selectedIds.value) {
            try {
                await admin.deleteQuestion(id);
                successCount++;
            } catch (e) { /* skip individual failures */ }
        }
        showMessage(`成功删除 ${successCount} 道题目`);
        selectedIds.value = [];
        selectAll.value = false;
        loadQuestions();
    } catch (e) {
        showMessage('批量删除失败', 'error');
    } finally {
        loading.value = false;
    }
};

// --- Page Jump ---
const jumpPage = ref('');
const goToPage = () => {
    const p = parseInt(jumpPage.value);
    if (p >= 1 && p <= pagination.totalPages) {
        pagination.page = p;
        loadQuestions();
    } else {
        showMessage('无效的页码', 'warning');
    }
    jumpPage.value = '';
};

onMounted(() => {
    loadMajors();
    loadQuestions(); // Load all questions on mount
});

const getTypeName = (type) => {
    const map = {
        single: '单选题',
        multiple: '多选题',
        judge: '判断题',
        fill: '填空题',
        sort: '排序题',
        material: '材料题'
    };
    return map[type] || type;
};

const formatAnswer = (answer) => {
    if (!answer) return '-';
    let parsed = answer;
    if (typeof answer === 'string') {
        try { parsed = JSON.parse(answer); } catch (e) {
            return answer; // Return raw string if not JSON
        }
    }
    if (Array.isArray(parsed)) {
        return parsed.join(', ');
    }
    return String(parsed);
};

// Export to Word
const handleExport = async () => {
    if (questions.value.length === 0) return showMessage('当前列表没有题目可导出', 'warning');

    let scopeDesc = '';
    if (filters.major_id) {
        const major = majors.value.find(m => m.id == filters.major_id);
        if (major) scopeDesc += major.name + ' ';
    }
    if (filters.subject_id) {
        const subject = filterSubjects.value.find(s => s.id == filters.subject_id);
        if (subject) scopeDesc += subject.name + ' ';
    }
    if (filters.type) scopeDesc += getTypeName(filters.type) + ' ';
    scopeDesc = scopeDesc.trim() || '题目导出';

    const confirmed = await confirmDialog.value.confirm({
        title: '确认导出',
        message: `即将导出当前列表中的 ${questions.value.length} 道题目到 Word 文档（含答案和解析）。`,
        type: 'info',
        confirmText: '确认导出',
        cancelText: '取消'
    });

    if (confirmed) {
        try {
            loading.value = true;
            // Normalize questions for export-docx format
            const exportData = questions.value.map(q => {
                let options = typeof q.options === 'string' ? JSON.parse(q.options) : q.options;
                if (Array.isArray(options) && q.type !== 'sort' && q.type !== 'material') {
                    const obj = {};
                    options.forEach(o => { if (o.label) obj[o.label] = o.content; });
                    options = obj;
                }
                let answer = q.answer;
                try { if (typeof answer === 'string') answer = JSON.parse(answer); } catch(e) {}
                if (typeof answer === 'string') answer = answer.split('');

                return {
                    id: q.id,
                    content: {
                        question: q.content || q.stem,
                        options: options,
                        type: q.type,
                        answer: Array.isArray(answer) ? answer : [answer],
                        analysis: q.analysis
                    }
                };
            });
            await exportToWord(exportData, scopeDesc, true);
            showMessage('导出成功！', 'success');
        } catch (e) {
            console.error(e);
            showMessage('导出失败: ' + e.message, 'error');
        } finally {
            loading.value = false;
        }
    }
};
</script>

<template>
<div class="admin-workspace ui-page space-y-6">
    <header class="question-page-head">
        <AdminPageHeading title="题库管理" description="维护题库内容，支持批量导入、当前页导出和单题编辑。" />
        <div class="question-actions">
            <div class="question-action-group" aria-label="题目工具">
                <button type="button" class="question-action" @click="openJsonImport">导入 JSON</button>
                <button type="button" class="question-action" @click="openExcelImport">导入 Excel</button>
                <button type="button" class="question-action" @click="handleExport">导出当前页 Word</button>
            </div>
            <button type="button" class="ui-btn ui-btn-primary question-add-btn" @click="openAddQuestion"><span aria-hidden="true">＋</span>新增题目</button>
        </div>
    </header>

    <!-- Filter Bar -->
    <div class="ui-card question-filter-bar flex flex-col sm:flex-row gap-4 items-center">
        <div class="flex-1 w-full grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
             <div class="relative group">
                <select v-model="filters.major_id" class="w-full pl-3 pr-8 py-2.5 bg-slate-50 dark:bg-slate-800/50 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-slate-200 focus:ring-2 focus:ring-indigo-500/20 outline-none appearance-none transition-all hover:border-indigo-300 dark:hover:border-indigo-600 ui-select">
                    <option value="">全部分类</option>
                    <option v-for="m in majors" :key="m.id" :value="m.id">{{ m.name }}</option>
                </select>
                <div class="absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none text-slate-400 text-xs">▼</div>
            </div>

             <div class="relative group">
                <select v-model="filters.subject_id" class="w-full pl-3 pr-8 py-2.5 bg-slate-50 dark:bg-slate-800/50 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-slate-200 focus:ring-2 focus:ring-indigo-500/20 outline-none appearance-none transition-all hover:border-indigo-300 dark:hover:border-indigo-600 ui-select">
                    <option value="">全部科目</option>
                    <option v-for="s in filterSubjects" :key="s.id" :value="s.id">{{ s.name }}</option>
                </select>
                 <div class="absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none text-slate-400 text-xs">▼</div>
            </div>

            <div class="relative group">
                <select v-model="filters.type" class="w-full pl-3 pr-8 py-2.5 bg-slate-50 dark:bg-slate-800/50 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-slate-200 focus:ring-2 focus:ring-indigo-500/20 outline-none appearance-none transition-all hover:border-indigo-300 dark:hover:border-indigo-600 ui-select">
                    <option value="">全部类型</option>
                    <option value="single">单选题</option>
                    <option value="multiple">多选题</option>
                    <option value="judge">判断题</option>
                    <option value="fill">填空题</option>
                    <option value="sort">排序题</option>
                    <option value="material">材料题</option>
                </select>
                <div class="absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none text-slate-400 text-xs">▼</div>
            </div>

            <div class="relative">
                <input
                    v-model="filters.keyword"
                    @keyup.enter="handleSearch"
                    placeholder="搜索题干关键词..."
                    class="w-full pl-9 pr-4 py-2.5 bg-slate-50 dark:bg-slate-800/50 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-slate-200 focus:ring-2 focus:ring-indigo-500/20 outline-none transition-all placeholder:font-normal hover:border-indigo-300 dark:hover:border-indigo-600 ui-input"
                >
                <svg class="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/></svg>
            </div>
        </div>
        <button @click="handleSearch" class="w-full sm:w-auto px-6 py-2.5 bg-slate-800 dark:bg-slate-700 text-white rounded-xl font-bold text-xs hover:bg-slate-700 dark:hover:bg-slate-600 transition-all admin-control">
            搜索
        </button>
    </div>

    <!-- Batch Actions & Stats Bar -->
    <div class="ui-card question-bulk-bar flex flex-col sm:flex-row justify-between items-center gap-3">
        <div class="flex items-center gap-3">
            <label class="flex items-center gap-2 cursor-pointer select-none">
                <input type="checkbox" :checked="selectAll" @change="toggleSelectAll" class="w-4 h-4 rounded border-slate-300 text-indigo-600 focus:ring-indigo-500">
                <span class="text-xs font-bold text-slate-500">{{ selectAll ? '取消全选' : '全选' }}</span>
            </label>
            <Transition name="fade">
                <button v-if="selectedIds.length > 0" @click="batchDelete" class="px-3 py-1.5 bg-rose-500 hover:bg-rose-600 text-white rounded-lg text-xs font-bold transition-all flex items-center gap-1.5 admin-control">
                    删除选中（{{ selectedIds.length }}）
                </button>
            </Transition>
        </div>
        <div class="flex items-center gap-3 text-xs font-bold text-slate-400">
            <span>共 <b class="text-indigo-600 dark:text-indigo-400">{{ pagination.total }}</b> 题</span>
            <span class="text-slate-200 dark:text-slate-700">|</span>
            <span>第 {{ pagination.page }}/{{ pagination.totalPages || 1 }} 页</span>
        </div>
    </div>

    <!-- Question List -->
    <div class="space-y-4">
        <div v-if="loading" class="py-20 text-center animate-pulse">
            <div class="w-12 h-12 border-4 border-slate-200 border-t-indigo-500 rounded-full animate-spin mx-auto mb-4"></div>
            <p class="text-slate-400 font-bold text-sm">LOADING DATA...</p>
        </div>

        <div v-else-if="questions.length === 0" class="py-20 text-center border-2 border-dashed border-slate-200 dark:border-slate-700 rounded-[32px]">
            <p class="admin-empty-title">暂无题目数据</p><p class="admin-empty-copy">调整筛选条件，或新增第一道题目。</p>
        </div>

        <div v-else class="admin-question-list">
             <div v-for="q in questions" :key="q.id" class="admin-question-row group">

                <div class="flex justify-between items-start gap-3 sm:gap-4 relative z-10">
                    <!-- Checkbox -->
                    <label class="flex items-center justify-center pt-1 shrink-0 cursor-pointer">
                        <input type="checkbox" :checked="selectedIds.includes(q.id)" @change="toggleSelect(q.id)" class="w-4 h-4 rounded border-slate-300 text-indigo-600 focus:ring-indigo-500">
                    </label>
                    <div class="flex-1 min-w-0">
                        <div class="flex items-center gap-2 mb-2 flex-wrap">
                             <span
                                class="px-2 py-0.5 rounded text-[10px] uppercase shrink-0"
                                :class="{
                                    'bg-indigo-100 text-indigo-700 dark:bg-indigo-900/30 dark:text-indigo-300': q.type === 'single',
                                    'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-300': q.type === 'multiple',
                                    'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-300': q.type === 'judge',
                                    'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-300': q.type === 'fill',
                                    'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300': q.type === 'sort',
                                    'bg-rose-100 text-rose-700 dark:bg-rose-900/30 dark:text-rose-300': q.type === 'material'
                                }"
                            >
                                {{ getTypeName(q.type) }}
                            </span>
                            <span class="text-[10px] text-slate-400 font-mono">ID: {{ q.id }}</span>
                             <span class="text-[10px] text-slate-400/50 hidden sm:inline">•</span>
                            <span class="text-[10px] text-slate-400 font-bold truncate max-w-[150px] hidden sm:inline">{{ q.major_name || '未分类' }} / {{ q.subject_name || '未分类' }}</span>
                             <span
                                class="px-2 py-0.5 rounded text-[10px] uppercase ml-1"
                                :class="q.is_active ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300' : 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-400'"
                            >
                                {{ q.is_active ? '已上线' : '未上线' }}
                            </span>
                        </div>
                        <h3 class="text-sm sm:text-base font-bold text-slate-800 dark:text-slate-100 mb-2 leading-relaxed line-clamp-2">
                            <MarkdownRenderer :content="q.content || q.stem || '(无题干)'" />
                        </h3>
                        <div class="text-xs text-slate-500 dark:text-slate-400 flex flex-wrap gap-2">
                            <span class="font-bold">答案:</span>
                            <span class="font-mono bg-slate-100 dark:bg-slate-700 px-1.5 rounded text-indigo-600 dark:text-indigo-400">
                                {{ formatAnswer(q.answer) }}
                            </span>
                        </div>
                    </div>

                    <div class="flex flex-row sm:flex-col gap-2 shrink-0">
                        <button @click="openEditQuestion(q)" class="p-2 bg-slate-50 hover:bg-indigo-50 text-slate-400 hover:text-indigo-600 dark:bg-slate-800 dark:hover:bg-indigo-900/30 dark:text-slate-500 dark:hover:text-indigo-400 rounded-lg transition-colors admin-control" title="编辑">
                            编辑
                        </button>
                        <button @click="deleteQuestion(q.id)" class="p-2 bg-slate-50 hover:bg-rose-50 text-slate-400 hover:text-rose-600 dark:bg-slate-800 dark:hover:bg-rose-900/30 dark:text-slate-500 dark:hover:text-rose-400 rounded-lg transition-colors admin-control" title="删除">
                           删除
                        </button>
                    </div>
                </div>
            </div>
        </div>

        <!-- Pagination -->
        <div v-if="pagination.totalPages > 1" class="flex flex-col sm:flex-row justify-between items-center py-4 gap-3">
             <span class="text-xs text-slate-500">第 {{ pagination.page }} / {{ pagination.totalPages }} 页 · 共 {{ pagination.total }} 题</span>
             <div class="flex items-center gap-2">
                <button
                    @click="changePage(pagination.page - 1)"
                    :disabled="pagination.page === 1"
                    class="px-4 py-2 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg text-xs font-bold disabled:opacity-50 hover:bg-slate-50 dark:hover:bg-slate-700 transition-colors admin-control"
                >
                    上一页
                </button>

                <!-- Page Jump -->
                <div class="flex items-center gap-1.5">
                    <input
                        v-model="jumpPage"
                        @keydown.enter="goToPage"
                        type="number"
                        :min="1"
                        :max="pagination.totalPages"
                        class="w-14 px-2 py-2 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg text-xs font-bold text-center focus:ring-2 focus:ring-indigo-500/20 outline-none [appearance:textfield] [&::-webkit-outer-spin-button]:appearance-none [&::-webkit-inner-spin-button]:appearance-none ui-input"
                        placeholder="页"
                    >
                    <button @click="goToPage" class="px-3 py-2 bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-300 rounded-lg text-xs font-bold hover:bg-slate-200 dark:hover:bg-slate-600 transition-colors admin-control">
                        跳转
                    </button>
                </div>

                <button
                    @click="changePage(pagination.page + 1)"
                    :disabled="pagination.page === pagination.totalPages"
                    class="px-4 py-2 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg text-xs font-bold disabled:opacity-50 hover:bg-slate-50 dark:hover:bg-slate-700 transition-colors admin-control"
                >
                    下一页
                </button>
             </div>
        </div>
    </div>

    <!-- Question Modal -->
    <div v-if="showQuestionModal" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 p-4 transition-all animate-fade-in">
        <div class="ui-card w-full max-w-4xl h-[90vh] flex flex-col animate-scale-up border border-white/50 dark:border-slate-600/50">
            <!-- Modal Header -->
            <div class="px-6 py-4 border-b border-slate-100 dark:border-slate-700 flex justify-between items-center bg-slate-50/50 dark:bg-slate-800/50">
                <h3 class="text-lg text-slate-800 dark:text-white">{{ editingQuestion.id ? '编辑题目' : '新增题目' }}</h3>
                <div class="flex items-center gap-3">
                    <div class="flex bg-slate-200 dark:bg-slate-700 rounded-lg p-1">
                        <button
                            @click="editingQuestion.jsonMode = false"
                            class="px-3 py-1 rounded-md text-xs font-bold transition-colors admin-control"
                            :class="!editingQuestion.jsonMode ? 'bg-white dark:bg-slate-600 shadow-sm text-indigo-600 dark:text-indigo-300' : 'text-slate-500 dark:text-slate-400'"
                        >可视编辑</button>
                        <button
                            @click="editingQuestion.jsonMode = true; updateJsonFromForm()"
                            class="px-3 py-1 rounded-md text-xs font-bold transition-colors admin-control"
                            :class="editingQuestion.jsonMode ? 'bg-white dark:bg-slate-600 shadow-sm text-indigo-600 dark:text-indigo-300' : 'text-slate-500 dark:text-slate-400'"
                        >JSON 源码</button>
                    </div>
                   <div v-if="!editingQuestion.jsonMode" class="flex bg-slate-200 dark:bg-slate-700 rounded-lg p-1 ml-2">
                        <button
                            @click="editingQuestion.previewMode = false"
                            class="px-3 py-1 rounded-md text-xs font-bold transition-colors admin-control"
                            :class="!editingQuestion.previewMode ? 'bg-white dark:bg-slate-600 shadow-sm text-indigo-600 dark:text-indigo-300' : 'text-slate-500 dark:text-slate-400'"
                        >编辑</button>
                        <button
                            @click="editingQuestion.previewMode = true"
                            class="px-3 py-1 rounded-md text-xs font-bold transition-colors admin-control"
                            :class="editingQuestion.previewMode ? 'bg-white dark:bg-slate-600 shadow-sm text-indigo-600 dark:text-indigo-300' : 'text-slate-500 dark:text-slate-400'"
                        >预览</button>
                    </div>

                    <button @click="showQuestionModal = false" class="text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 text-xl font-bold px-2 admin-control">×</button>
                </div>
            </div>

            <!-- Modal Content -->
            <div class="flex-1 overflow-y-auto custom-scrollbar p-6 space-y-6 bg-slate-50/30 dark:bg-slate-900/20">

                <!-- JSON Mode -->
                <div v-if="editingQuestion.jsonMode" class="h-full flex flex-col">
                    <textarea
                        v-model="editingQuestion.jsonContent"
                        class="flex-1 w-full bg-slate-900 text-green-400 font-mono text-xs p-4 rounded-xl resize-none focus:outline-none focus:ring-2 focus:ring-indigo-500/50 ui-textarea"
                        placeholder="Paste JSON object here..."
                    ></textarea>
                    <p class="text-xs text-slate-500 mt-2">直接编辑 JSON 对象结构。保存时会自动解析并校验。</p>
                </div>

                <!-- Visual Mode -->
                <div v-else class="space-y-6">

                    <!-- Preview Mode Content -->
                    <div v-if="editingQuestion.previewMode" class="space-y-6 p-4">
                        <div class="space-y-2">
                            <h4 class="text-sm font-bold text-slate-500 uppercase">题干预览</h4>
                            <div class="p-4 bg-white dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-slate-700">
                                <MarkdownRenderer :content="editingQuestion.stem || '(空)'" />
                            </div>
                        </div>
                        <div class="space-y-2">
                            <h4 class="text-sm font-bold text-slate-500 uppercase">解析预览</h4>
                            <div class="p-4 bg-white dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-slate-700">
                                <MarkdownRenderer :content="editingQuestion.analysis || '(空)'" />
                            </div>
                        </div>
                    </div>

                    <!-- Edit Mode Content -->
                    <div v-else class="space-y-6">
                    <!-- Basics -->
                    <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
                         <div class="space-y-1.5">
                            <label class="text-xs font-bold text-slate-500 dark:text-slate-400 ml-1">所属专业</label>
                            <select v-model="editingQuestion.major_id" class="w-full px-4 py-2.5 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-slate-200 outline-none focus:ring-2 focus:ring-indigo-500/20 ui-select">
                                <option value="" disabled>选择专业</option>
                                <option v-for="m in majors" :key="m.id" :value="m.id">{{ m.name }}</option>
                            </select>
                        </div>
                        <div class="space-y-1.5">
                            <label class="text-xs font-bold text-slate-500 dark:text-slate-400 ml-1">所属科目</label>
                            <select v-model="editingQuestion.subject_id" class="w-full px-4 py-2.5 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-slate-200 outline-none focus:ring-2 focus:ring-indigo-500/20 ui-select">
                                <option value="" disabled>选择科目</option>
                                <option v-for="s in modalSubjects" :key="s.id" :value="s.id">{{ s.name }}</option>
                            </select>
                        </div>
                        <div class="space-y-1.5">
                            <label class="text-xs font-bold text-slate-500 dark:text-slate-400 ml-1">题目类型</label>
                            <select v-model="editingQuestion.type" class="w-full px-4 py-2.5 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-slate-200 outline-none focus:ring-2 focus:ring-indigo-500/20 ui-select">
                                <option value="single">单选题</option>
                                <option value="multiple">多选题</option>
                                <option value="judge">判断题</option>
                                <option value="fill">填空题</option>
                                <option value="sort">排序题</option>
                                <option value="material">材料题</option>
                            </select>
                        </div>
                        <div class="space-y-1.5 flex flex-col justify-end pb-1.5 flex-row items-center gap-2">
                             <label class="text-xs font-bold text-slate-500 dark:text-slate-400 select-none cursor-pointer flex items-center gap-2">
                                <input type="checkbox" v-model="editingQuestion.is_active" :true-value="1" :false-value="0" class="w-4 h-4 rounded border-slate-300 text-indigo-600 focus:ring-indigo-500">
                                上线状态
                             </label>
                             <span :class="editingQuestion.is_active ? 'text-green-500' : 'text-slate-400'" class="text-[10px] font-bold">
                                {{ editingQuestion.is_active ? '已发布到前台' : '暂不下线/仅管理员可见' }}
                             </span>
                        </div>
                    </div>

                    <!-- Difficulty & Tags -->
                    <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div class="space-y-1.5">
                            <label class="text-xs font-bold text-slate-500 dark:text-slate-400 ml-1">难度等级</label>
                            <div class="flex gap-2">
                                <button
                                    v-for="d in [{val:1, label:'简单', color:'bg-emerald-500'}, {val:2, label:'中等', color:'bg-amber-500'}, {val:3, label:'困难', color:'bg-rose-500'}]"
                                    :key="d.val"
                                    @click="editingQuestion.difficulty = d.val"
                                    class="flex-1 py-2.5 rounded-xl text-xs font-bold transition-all border-2 flex items-center justify-center gap-2 admin-control"
                                    :class="editingQuestion.difficulty === d.val
                                        ? `border-${d.color.replace('bg-','')} ${d.color} text-white shadow-md`
                                        : 'border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-500 hover:bg-slate-50'"
                                >
                                    {{ d.label }}
                                </button>
                            </div>
                        </div>
                        <div class="space-y-1.5">
                             <label class="text-xs font-bold text-slate-500 dark:text-slate-400 ml-1">标签 (Tag)</label>
                             <div class="w-full px-4 py-2 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl flex flex-wrap gap-2 min-h-[46px] relative focus-within:ring-2 focus-within:ring-indigo-500/20">
                                 <span v-for="(tag, idx) in editingQuestion.tags" :key="idx" class="px-2 py-0.5 bg-indigo-100 dark:bg-indigo-900/30 text-indigo-700 dark:text-indigo-300 rounded text-xs font-bold flex items-center gap-1">
                                     {{ tag }}
                                     <button @click="removeTag(idx)" class="hover:text-indigo-900 dark:hover:text-indigo-100 admin-control">×</button>
                                 </span>
                                 <input
                                    v-model="editingQuestion.tagInput"
                                    @keydown.enter.prevent="addTag"
                                    @blur="addTag"
                                    type="text"
                                    class="flex-1 bg-transparent border-none outline-none text-sm font-medium min-w-[60px] text-slate-700 dark:text-slate-200 ui-input"
                                    placeholder="输入标签回车..."
                                 >
                             </div>
                        </div>
                    </div>

                    <!-- Stem -->
                    <div class="space-y-1.5">
                        <div class="flex justify-between items-center">
                            <label class="text-xs font-bold text-slate-500 dark:text-slate-400 ml-1">
                                {{ editingQuestion.type === 'material' ? '材料内容' : '题干内容' }}
                            </label>
                            <button @click="triggerImageUpload('stem')" class="text-indigo-500 text-xs font-bold hover:underline flex items-center gap-1 admin-control">
                                插入图片
                            </button>
                        </div>
                        <textarea v-model="editingQuestion.stem" rows="3" class="w-full px-4 py-3 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-medium text-slate-700 dark:text-slate-200 outline-none focus:ring-2 focus:ring-indigo-500/20 resize-none ui-textarea" placeholder="输入题目描述... (支持 Markdown 和图片)"></textarea>
                    </div>

                    <!-- Answer for Fill-in-the-blank -->
                    <div v-if="editingQuestion.type === 'fill'" class="space-y-1.5">
                        <label class="text-xs font-bold text-slate-500 dark:text-slate-400 ml-1">正确答案</label>
                        <input v-model="editingQuestion.answer" type="text" class="w-full px-4 py-3 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-medium text-slate-700 dark:text-slate-200 outline-none focus:ring-2 focus:ring-indigo-500/20 ui-input" placeholder="输入正确答案...">
                    </div>

                    <!-- Material Sub-Questions -->
                    <div v-if="editingQuestion.type === 'material'" class="space-y-4">
                        <label class="block text-xs font-bold text-slate-500 dark:text-slate-400 ml-1">
                            子题目列表
                        </label>

                        <div v-for="(subQ, idx) in editingQuestion.subQuestions" :key="idx" class="p-4 bg-slate-50 dark:bg-slate-900/50 border border-slate-200 dark:border-slate-700 rounded-xl space-y-4 relative group">
                            <div class="absolute right-4 top-4 opacity-0 group-hover:opacity-100 transition-opacity">
                                <button @click="removeSubQuestion(idx)" class="text-slate-400 hover:text-rose-500 transition-colors admin-control">删除</button>
                            </div>

                            <div class="flex gap-4">
                                <div class="w-8 h-8 flex-shrink-0 bg-indigo-600 text-white rounded-lg flex items-center justify-center font-bold text-sm">{{ idx + 1 }}</div>
                                <div class="flex-1 space-y-3">
                                    <!-- Sub Type & Stem -->
                                    <div class="flex gap-3">
                                        <select v-model="subQ.type" class="w-32 px-3 py-2 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg text-xs font-bold outline-none focus:ring-2 focus:ring-indigo-500/20 ui-select">
                                            <option value="single">单选题</option>
                                            <option value="multiple">多选题</option>
                                            <option value="judge">判断题</option>
                                            <option value="fill">填空题</option>
                                        </select>
                                        <input v-model="subQ.stem" type="text" class="flex-1 px-4 py-2 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg text-sm outline-none focus:ring-2 focus:ring-indigo-500/20 ui-input" placeholder="子题目内容...">
                                    </div>

                                    <!-- Sub Options Area -->
                                    <div v-if="subQ.type === 'fill'">
                                        <input v-model="subQ.answer" type="text" class="w-full px-4 py-2 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg text-sm outline-none focus:ring-2 focus:ring-emerald-500/20 ui-input" placeholder="正确答案...">
                                    </div>

                                    <div v-else-if="subQ.type === 'judge'" class="flex gap-3">
                                        <button
                                            v-for="opt in ['正确', '错误']"
                                            :key="opt"
                                            @click="subQ.answer = [opt]"
                                            class="flex-1 py-1.5 rounded-lg border text-xs font-bold transition-all admin-control"
                                            :class="subQ.answer?.[0] === opt ? 'bg-emerald-500 border-emerald-500 text-white' : 'bg-white dark:bg-slate-800 border-slate-200 dark:border-slate-700 text-slate-500'"
                                        >{{ opt }}</button>
                                    </div>

                                    <div v-else class="space-y-2">
                                        <div v-for="(opt, optIdx) in subQ.options" :key="optIdx" class="flex gap-2 items-center">
                                            <button
                                                @click="toggleSubAnswer(subQ, opt.label)"
                                                class="w-8 h-8 rounded border flex items-center justify-center font-bold text-xs transition-colors admin-control"
                                                :class="subQ.answer?.includes(opt.label) ? 'bg-indigo-500 border-indigo-500 text-white' : 'bg-white dark:bg-slate-800 border-slate-200 text-slate-400'"
                                            >{{ opt.label }}</button>
                                            <input v-model="opt.content" type="text" class="flex-1 px-3 py-1.5 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded text-xs outline-none focus:ring-2 focus:ring-indigo-500/20 ui-input" :placeholder="'选项 ' + opt.label">
                                        </div>
                                    </div>

                                    <!-- Sub Analysis -->
                                    <input v-model="subQ.analysis" type="text" class="w-full px-4 py-2 bg-slate-100 dark:bg-slate-800/50 border-none rounded-lg text-xs text-slate-500 focus:bg-white dark:focus:bg-slate-800 transition-colors outline-none ui-input" placeholder="子题目解析 (选填)...">
                                </div>
                            </div>
                        </div>

                        <button @click="addSubQuestion" class="w-full py-3 border-2 border-dashed border-slate-300 dark:border-slate-600 rounded-xl text-slate-500 hover:border-indigo-500 hover:text-indigo-500 font-bold transition-all flex items-center justify-center gap-2 admin-control">
                            <span>+</span> 添加子题目
                        </button>
                    </div>

                    <!-- Sort Options -->
                    <div v-if="editingQuestion.type === 'sort'" class="space-y-3">
                        <label class="block text-xs font-bold text-slate-500 dark:text-slate-400 ml-1">
                            排序项 (请按正确顺序添加)
                        </label>
                        <div v-for="(opt, idx) in editingQuestion.options" :key="idx" class="flex gap-2">
                             <span class="w-8 h-10 flex items-center justify-center bg-slate-100 dark:bg-slate-700 rounded-lg font-bold text-slate-500">{{ idx + 1 }}</span>
                             <input
                                v-model="editingQuestion.options[idx].content"
                                type="text"
                                class="flex-1 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl px-4 py-2.5 focus:ring-2 focus:ring-indigo-500/20 outline-none text-sm font-medium ui-input"
                                :placeholder="'排序项 ' + (idx + 1)"
                             >
                             <button @click="removeOption(idx)" class="w-10 h-10 rounded-xl border border-slate-200 dark:border-slate-700 text-slate-400 hover:text-rose-500 hover:bg-rose-50 dark:hover:bg-rose-900/20 transition-colors admin-control">
                                 ×
                             </button>
                        </div>
                        <button @click="addOption" class="w-full py-2 border-2 border-dashed border-slate-300 dark:border-slate-600 rounded-xl text-slate-500 hover:border-indigo-500 hover:text-indigo-500 font-bold transition-all admin-control">
                            + 添加排序项
                        </button>
                        <p class="text-xs text-slate-400">提示：请直接按正确顺序录入，系统会在考试时自动打乱顺序。</p>
                    </div>

                    <!-- Options -->
                    <div v-if="editingQuestion.type !== 'fill' && editingQuestion.type !== 'sort' && editingQuestion.type !== 'material'" class="space-y-3">
                        <div class="flex justify-between items-center">
                            <label class="text-xs font-bold text-slate-500 dark:text-slate-400 ml-1">选项设置</label>
                            <button v-if="editingQuestion.type !== 'judge'" @click="addOption" class="text-indigo-500 text-xs font-bold hover:underline admin-control">+ 添加选项</button>
                        </div>

                        <!-- Judge Options Fixed -->
                        <div v-if="editingQuestion.type === 'judge'" class="flex gap-4">
                            <button
                                v-for="opt in ['正确', '错误']"
                                :key="opt"
                                @click="toggleAnswer(opt)"
                                class="flex-1 py-3 rounded-xl border-2 transition-all font-bold text-sm flex items-center justify-center gap-2 admin-control"
                                :class="editingQuestion.answer.includes(opt) ? 'border-green-500 bg-green-50/50 text-green-600 dark:bg-green-900/20 dark:text-green-400' : 'border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-500'"
                            >
                                <span class="w-4 h-4 rounded-full border-2 flex items-center justify-center" :class="editingQuestion.answer.includes(opt) ? 'border-green-500' : 'border-slate-300'">
                                    <div v-if="editingQuestion.answer.includes(opt)" class="w-2 h-2 rounded-full bg-green-500"></div>
                                </span>
                                {{ opt }}
                            </button>
                        </div>

                        <!-- Dynamic Options -->
                        <div v-else class="space-y-2">
                             <div v-for="(opt, idx) in editingQuestion.options" :key="idx" class="flex items-center gap-3">
                                <button
                                    @click="toggleAnswer(opt.label)"
                                    class="w-10 h-10 rounded-lg border-2 flex items-center justify-center transition-all text-sm admin-control"
                                    :class="editingQuestion.answer.includes(opt.label) ? 'border-indigo-500 bg-indigo-500 text-white' : 'border-slate-200 dark:border-slate-700 text-slate-400 hover:border-indigo-300'"
                                >
                                    {{ opt.label }}
                                </button>
                                <input v-model="opt.content" type="text" class="flex-1 px-4 py-2.5 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-medium focus:ring-2 focus:ring-indigo-500/20 outline-none ui-input" :placeholder="`选项 ${opt.label} 内容`">
                                <button @click="removeOption(idx)" class="w-8 h-8 flex items-center justify-center text-slate-300 hover:text-rose-500 transition-colors admin-control">×</button>
                            </div>
                        </div>
                    </div>

                    <!-- Analysis -->
                    <div class="space-y-1.5">
                        <div class="flex justify-between items-center">
                            <label class="text-xs font-bold text-slate-500 dark:text-slate-400 ml-1">题目解析</label>
                            <button @click="triggerImageUpload('analysis')" class="text-indigo-500 text-xs font-bold hover:underline flex items-center gap-1 admin-control">
                                插入图片
                            </button>
                        </div>
                        <textarea v-model="editingQuestion.analysis" rows="3" class="w-full px-4 py-3 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-medium text-slate-700 dark:text-slate-200 outline-none focus:ring-2 focus:ring-indigo-500/20 resize-none ui-textarea" placeholder="输入答案解析... (支持 Markdown 和图片)"></textarea>
                    </div>
                    </div>
                </div> <!-- End of Edit Mode Wrapper -->
            </div>

            <!-- Modal Footer -->
            <div class="px-6 py-4 border-t border-slate-100 dark:border-slate-700 flex justify-end gap-3 bg-slate-50/50 dark:bg-slate-800/50">
                <button @click="showQuestionModal = false" class="px-6 py-2.5 rounded-xl border border-slate-200 dark:border-slate-700 text-slate-600 dark:text-slate-300 font-bold text-xs hover:bg-slate-100 dark:hover:bg-slate-700 transition-colors admin-control">
                    取消
                </button>
                <button @click="saveQuestion" class="px-6 py-2.5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl font-bold text-xs dark:shadow-none transition-all admin-control">
                    保存题目
                </button>
            </div>
        </div>
    </div>

    <div v-if="showJsonImportModal" class="admin-import-backdrop" @click.self="showJsonImportModal = false">
      <section class="admin-import-modal" role="dialog" aria-modal="true" aria-labelledby="json-import-title">
        <header class="admin-import-head"><div><span class="ui-eyebrow">批量录入</span><h2 id="json-import-title">导入 JSON</h2><p>提交对象数组，服务端逐题校验并加密入库。</p></div><button type="button" class="admin-import-close" aria-label="关闭" @click="showJsonImportModal = false">×</button></header>
        <div class="import-target" :class="{ 'is-missing': !importTarget }"><span>导入目标</span><strong>{{ importTarget || '尚未选择专业和科目' }}</strong><small>{{ importTarget ? '如需更换目标，请关闭弹窗后在筛选栏重新选择。' : '请关闭弹窗，在筛选栏选择专业和科目。' }}</small></div>
        <div class="import-example"><span>数据格式</span><code>[{ "type": "single", "stem": "题干", "options": [{"label":"A","content":"选项"}], "answer": ["A"], "analysis": "解析" }]</code></div>
        <textarea v-model="jsonImportContent" class="ui-textarea import-json-editor" spellcheck="false" placeholder='[
  {
    "type": "single",
    "stem": "示例题目",
    "options": [{"label":"A","content":"选项 A"},{"label":"B","content":"选项 B"}],
    "answer": ["A"],
    "analysis": "解析内容"
  }
]' @input="jsonImportResult = null"></textarea>
        <div v-if="jsonImportResult" class="import-result" :class="jsonImportResult.error ? 'is-error' : (jsonImportResult.errors?.length ? 'is-warning' : 'is-success')">
          <template v-if="jsonImportResult.error"><strong>导入失败</strong><span>{{ jsonImportResult.error }}</span></template>
          <template v-else><strong>{{ jsonImportResult.message }}</strong><span>成功 {{ jsonImportResult.successCount || 0 }} 条 · 共 {{ jsonImportResult.totalCount || 0 }} 条</span><ul v-if="jsonImportResult.errors?.length"><li v-for="(item, index) in jsonImportResult.errors" :key="index">{{ item }}</li></ul></template>
        </div>
        <footer class="admin-import-foot"><button type="button" class="ui-btn ui-btn-outline ui-btn-sm admin-control" @click="showJsonImportModal = false">关闭</button><button type="button" class="ui-btn ui-btn-primary ui-btn-sm admin-control" :disabled="!jsonImportContent.trim() || !importTarget || isImportingJson" @click="handleJsonImport">{{ isImportingJson ? '导入中…' : '确认导入' }}</button></footer>
      </section>
    </div>

    <div v-if="showExcelImportModal" class="admin-import-backdrop" @click.self="showExcelImportModal = false">
      <section class="admin-import-modal" role="dialog" aria-modal="true" aria-labelledby="excel-import-title">
        <header class="admin-import-head"><div><span class="ui-eyebrow">表格录入</span><h2 id="excel-import-title">导入 Excel</h2><p>使用标准模板填写，支持单选、多选、判断和填空题。</p></div><button type="button" class="admin-import-close" aria-label="关闭" @click="showExcelImportModal = false">×</button></header>
        <div class="import-target" :class="{ 'is-missing': !importTarget }"><span>导入目标</span><strong>{{ importTarget || '尚未选择专业和科目' }}</strong><small>{{ importTarget ? '如需更换目标，请关闭弹窗后在筛选栏重新选择。' : '请关闭弹窗，在筛选栏选择专业和科目。' }}</small></div>
        <div class="import-step"><span class="import-step-number">01</span><div><strong>下载标准模板</strong><p>表头、示例和多选格式已准备好，下载后直接填写。</p></div><button type="button" class="ui-btn ui-btn-outline ui-btn-sm admin-control" @click="downloadTemplate">下载 XLSX</button></div>
        <div class="import-step is-upload"><span class="import-step-number">02</span><div class="import-upload-copy"><strong>选择文件</strong><p>{{ excelFile ? excelFile.name : '支持 .xlsx / .xls，最大 5MB' }}</p></div><label class="ui-btn ui-btn-primary ui-btn-sm admin-control import-file-button">选择文件<input type="file" accept=".xlsx,.xls" @change="handleExcelSelect" /></label></div>
        <div v-if="excelImportResult" class="import-result" :class="excelImportResult.error ? 'is-error' : (excelImportResult.failureCount ? 'is-warning' : 'is-success')">
          <template v-if="excelImportResult.error"><strong>导入失败</strong><span>{{ excelImportResult.error }}</span></template>
          <template v-else><strong>导入完成</strong><span>成功 {{ excelImportResult.successCount }} 条 · 失败 {{ excelImportResult.failureCount }} 条 · 共 {{ excelImportResult.totalCount }} 条</span><ul v-if="excelImportResult.errors?.length"><li v-for="(item, index) in excelImportResult.errors" :key="index">{{ item }}</li></ul></template>
        </div>
        <footer class="admin-import-foot"><button type="button" class="ui-btn ui-btn-outline ui-btn-sm admin-control" @click="showExcelImportModal = false">关闭</button><button type="button" class="ui-btn ui-btn-primary ui-btn-sm admin-control" :disabled="!excelFile || !importTarget || isImporting" @click="handleExcelUpload">{{ isImporting ? '导入中…' : '开始导入' }}</button></footer>
      </section>
    </div>

    <Toast ref="toast" />
    <ConfirmDialog ref="confirmDialog" />

    <input type="file" ref="fileInput" accept="image/*" class="hidden" @change="handleImageSelection">

</div>
</template>

<style scoped>
.question-page-head { display:flex; align-items:flex-start; justify-content:space-between; gap:20px; padding-bottom:18px; border-bottom:1px solid var(--border); }
.question-actions { display:flex; align-items:center; flex-wrap:wrap; gap:8px; }
.question-action-group { display:flex; overflow:hidden; border:1px solid var(--border-strong); border-radius:9px; background:var(--bg-card); }
.question-action { min-height:38px; padding:0 12px; border-right:1px solid var(--border); color:var(--text-sub); font-size:11px; font-weight:550; white-space:nowrap; }
.question-action:last-child { border-right:0; }
.question-action:hover { background:var(--bg-card-highlight); color:var(--text-main); }
.question-add-btn { min-height:38px; padding:0 14px; }
.question-add-btn span { margin-right:5px; font-size:15px; line-height:1; }
.question-filter-bar { border-radius:10px; }
.question-bulk-bar { border-radius:10px; }
.admin-import-backdrop { position:fixed; inset:0; z-index:60; display:flex; align-items:center; justify-content:center; padding:20px; background:rgba(15,23,42,.48); }
.admin-import-modal { width:min(680px,100%); max-height:min(88vh,760px); overflow-y:auto; padding:22px; border:1px solid var(--border); border-radius:14px; background:var(--bg-card); box-shadow:0 24px 70px rgba(15,23,42,.22); }
.admin-import-head { display:flex; align-items:flex-start; justify-content:space-between; gap:16px; padding-bottom:14px; border-bottom:1px solid var(--border); }
.admin-import-head h2 { margin:4px 0 0; color:var(--text-main); font-size:21px; line-height:1.3; font-weight:650; letter-spacing:-.02em; }
.admin-import-head p { margin-top:5px; color:var(--text-sub); font-size:11px; line-height:1.6; }
.admin-import-close { width:32px; height:32px; flex:0 0 32px; border-radius:7px; color:var(--text-sub); font-size:23px; line-height:1; }
.admin-import-close:hover { background:var(--bg-card-highlight); color:var(--text-main); }
.import-target { display:flex; flex-direction:column; gap:3px; margin-top:14px; padding:10px 12px; border:1px solid color-mix(in srgb,var(--primary) 18%,var(--border)); border-radius:8px; background:var(--primary-soft); }
.import-target.is-missing { border-color:color-mix(in srgb,var(--warning) 30%,var(--border)); background:color-mix(in srgb,var(--warning) 6%,var(--bg-card)); }
.import-target > span { color:var(--text-quote); font-size:9px; }
.import-target strong { color:var(--text-main); font-size:12px; font-weight:600; }
.import-target small { color:var(--text-sub); font-size:9px; }
.import-example { display:flex; flex-direction:column; gap:5px; margin-top:12px; }
.import-example > span { color:var(--text-sub); font-size:10px; font-weight:550; }
.import-example code { overflow-x:auto; padding:8px 10px; border-radius:6px; background:var(--bg-card-highlight); color:var(--text-sub); font-size:9px; white-space:nowrap; }
.import-json-editor { min-height:280px; margin-top:10px; padding:13px; border-color:#1e293b; background:#0f172a; color:#e2e8f0; font-family:ui-monospace,SFMono-Regular,Menlo,monospace; font-size:11px; line-height:1.7; }
.import-result { display:flex; flex-direction:column; gap:4px; margin-top:12px; padding:11px 12px; border:1px solid var(--border); border-radius:8px; font-size:10px; }
.import-result.is-success { border-color:color-mix(in srgb,var(--success) 22%,var(--border)); background:color-mix(in srgb,var(--success) 6%,var(--bg-card)); }
.import-result.is-warning { border-color:color-mix(in srgb,var(--warning) 24%,var(--border)); background:color-mix(in srgb,var(--warning) 6%,var(--bg-card)); }
.import-result.is-error { border-color:color-mix(in srgb,var(--danger) 22%,var(--border)); background:color-mix(in srgb,var(--danger) 6%,var(--bg-card)); }
.import-result strong { color:var(--text-main); font-size:11px; font-weight:600; }
.import-result span { color:var(--text-sub); }
.import-result ul { max-height:105px; overflow-y:auto; margin:5px 0 0; padding-left:16px; color:var(--danger); font-size:9px; line-height:1.6; }
.import-step { display:flex; align-items:center; gap:12px; padding:13px 0; border-bottom:1px solid var(--border); }
.import-step-number { flex:0 0 28px; color:var(--text-quote); font-family:ui-monospace,SFMono-Regular,Menlo,monospace; font-size:10px; }
.import-step > div { flex:1; min-width:0; }
.import-step strong { color:var(--text-main); font-size:12px; font-weight:550; }
.import-step p { margin-top:3px; overflow:hidden; color:var(--text-quote); font-size:9px; line-height:1.5; text-overflow:ellipsis; white-space:nowrap; }
.import-upload-copy { display:flex; flex-direction:column; }
.import-file-button { position:relative; overflow:hidden; }
.import-file-button input { position:absolute; inset:0; opacity:0; cursor:pointer; }
.admin-import-foot { display:flex; align-items:center; justify-content:flex-end; gap:7px; padding-top:14px; }
@media(max-width:760px) {
  .question-page-head { align-items:flex-start; flex-direction:column; }
  .question-actions { width:100%; }
  .question-action-group { flex:1; min-width:0; }
  .question-action { flex:1; padding:0 7px; font-size:10px; }
  .question-add-btn { flex:0 0 auto; }
  .admin-import-backdrop { align-items:flex-end; padding:0; }
  .admin-import-modal { width:100%; max-height:92vh; border-radius:14px 14px 0 0; padding:18px; }
  .import-step { align-items:flex-start; flex-wrap:wrap; }
  .import-step .ui-btn { margin-left:40px; }
}
</style>
