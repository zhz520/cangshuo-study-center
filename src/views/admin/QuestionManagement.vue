<script setup>
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

// Import Excel Modal
const showExcelImportModal = ref(false);
const excelFile = ref(null);
const excelImportResult = ref(null);
const isImporting = ref(false);

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
        tags: editingQuestion.tags
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
    showJsonImportModal.value = true;
};

const handleJsonImport = async () => {
    try {
        const data = JSON.parse(jsonImportContent.value);
        if (!Array.isArray(data)) throw new Error('Root must be an array');
        // Basic check
        if (data.length === 0) return showMessage('数据为空', 'warning');
        
        // Ensure subject selected
        if (!filters.major_id || !filters.subject_id) return showMessage('请先在筛选栏选择要导入的专业和科目', 'warning');

        await admin.importQuestionsJson({
            major_id: filters.major_id,
            subject_id: filters.subject_id,
            questions: data
        });
        
        showMessage(`成功导入 ${data.length} 道题目`);
        showJsonImportModal.value = false;
        jsonImportContent.value = '';
        loadQuestions();
    } catch (e) {
        showMessage('导入失败: ' + e.message, 'error');
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
        excelImportResult.value = res.data;
        showMessage(res.data.message || '导入成功');
        loadQuestions();
        // Don't close immediately so user can see result
    } catch (e) {
        showMessage('导入失败: ' + (e.response?.data?.error || e.message), 'error');
        excelImportResult.value = { error: e.message };
    } finally {
        isImporting.value = false;
    }
};

const downloadTemplate = () => {
    // CSV Template
    const headers = ['type', 'content', 'options', 'answer', 'analysis', 'difficulty', 'tags'];
    const row1 = ['single', '示例单选题内容', 'A:选项1\\nB:选项2\\nC:选项3\\nD:选项4', 'A', '这是解析内容', '1', 'Math,Geometry'];
    const row2 = ['judge', '示例判断题内容', '', 'TRUE', '解析', '1', ''];
    const row3 = ['fill', '示例填空题内容', '', '答案内容', '解析', '2', ''];
    
    // Add BOM for Excel compatibility with UTF-8 CSV
    const csvContent = "\uFEFF" + [headers, row1, row2, row3].map(e => e.join(",")).join("\n");
    
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement("a");
    const url = URL.createObjectURL(blob);
    link.setAttribute("href", url);
    link.setAttribute("download", "question_import_template.csv");
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
        const subject = subjects.value.find(s => s.id == filters.subject_id);
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
<div class="p-6 space-y-6">
    <div class="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
            <h2 class="text-2xl font-black text-slate-800 dark:text-white tracking-tight">题目管理</h2>
             <p class="text-sm text-slate-500 dark:text-slate-400 font-medium">维护题库内容与解析</p>
        </div>
        <div class="flex gap-2">
            <button @click="openJsonImport" class="px-3 py-2.5 bg-white dark:bg-slate-800 text-slate-600 dark:text-slate-300 border border-slate-200 dark:border-slate-700 rounded-xl font-bold text-xs hover:bg-slate-50 dark:hover:bg-slate-700 transition-all shadow-sm">
                JSON
            </button>
            <button @click="openExcelImport" class="px-3 py-2.5 bg-emerald-500 hover:bg-emerald-600 text-white rounded-xl font-bold text-xs transition-all shadow-sm flex items-center gap-1">
                📗 Excel
            </button>
            <button @click="handleExport" class="px-3 py-2.5 bg-blue-600 hover:bg-blue-700 text-white rounded-xl font-bold text-xs transition-all shadow-sm flex items-center gap-1">
                📤 导出 Word
            </button>

            <button @click="openAddQuestion" class="px-5 py-2.5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl font-black text-xs transition-all active:scale-95 shadow-lg shadow-indigo-200/50 dark:shadow-none flex items-center gap-2">
                <span>+</span> 新增题目
            </button>
        </div>
    </div>

    <!-- Filter Bar -->
    <div class="glass-card p-4 flex flex-col sm:flex-row gap-4 items-center">
        <div class="flex-1 w-full grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
             <div class="relative group">
                <select v-model="filters.major_id" class="w-full pl-3 pr-8 py-2.5 bg-slate-50 dark:bg-slate-800/50 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-slate-200 focus:ring-2 focus:ring-indigo-500/20 outline-none appearance-none transition-all hover:border-indigo-300 dark:hover:border-indigo-600">
                    <option value="">📚 全部分类</option>
                    <option v-for="m in majors" :key="m.id" :value="m.id">{{ m.name }}</option>
                </select>
                <div class="absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none text-slate-400 text-xs">▼</div>
            </div>
            
             <div class="relative group">
                <select v-model="filters.subject_id" class="w-full pl-3 pr-8 py-2.5 bg-slate-50 dark:bg-slate-800/50 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-slate-200 focus:ring-2 focus:ring-indigo-500/20 outline-none appearance-none transition-all hover:border-indigo-300 dark:hover:border-indigo-600">
                    <option value="">全部科目</option>
                    <option v-for="s in filterSubjects" :key="s.id" :value="s.id">{{ s.name }}</option>
                </select>
                 <div class="absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none text-slate-400 text-xs">▼</div>
            </div>
            
            <div class="relative group">
                <select v-model="filters.type" class="w-full pl-3 pr-8 py-2.5 bg-slate-50 dark:bg-slate-800/50 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-slate-200 focus:ring-2 focus:ring-indigo-500/20 outline-none appearance-none transition-all hover:border-indigo-300 dark:hover:border-indigo-600">
                    <option value="">全部类型</option>
                    <option value="single">✅ 单选题</option>
                    <option value="multiple">☑️ 多选题</option>
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
                    class="w-full pl-9 pr-4 py-2.5 bg-slate-50 dark:bg-slate-800/50 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-slate-200 focus:ring-2 focus:ring-indigo-500/20 outline-none transition-all placeholder:font-normal hover:border-indigo-300 dark:hover:border-indigo-600"
                >
                <span class="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400">🔍</span>
            </div>
        </div>
        <button @click="handleSearch" class="w-full sm:w-auto px-6 py-2.5 bg-slate-800 dark:bg-slate-700 text-white rounded-xl font-bold text-xs hover:bg-slate-700 dark:hover:bg-slate-600 transition-all">
            搜索
        </button>
    </div>

    <!-- Batch Actions & Stats Bar -->
    <div class="glass-card p-3 flex flex-col sm:flex-row justify-between items-center gap-3">
        <div class="flex items-center gap-3">
            <label class="flex items-center gap-2 cursor-pointer select-none">
                <input type="checkbox" :checked="selectAll" @change="toggleSelectAll" class="w-4 h-4 rounded border-slate-300 text-indigo-600 focus:ring-indigo-500">
                <span class="text-xs font-bold text-slate-500">{{ selectAll ? '取消全选' : '全选' }}</span>
            </label>
            <Transition name="fade">
                <button v-if="selectedIds.length > 0" @click="batchDelete" class="px-3 py-1.5 bg-rose-500 hover:bg-rose-600 text-white rounded-lg text-xs font-bold transition-all active:scale-95 flex items-center gap-1.5">
                    🗑️ 删除选中 ({{ selectedIds.length }})
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
            <span class="text-4xl block mb-2 opacity-50">🦕</span>
            <p class="text-slate-400 font-bold text-sm">暂无题目数据</p>
        </div>
        
        <div v-else class="grid gap-4">
             <div v-for="q in questions" :key="q.id" class="glass-card p-4 sm:p-6 group hover:translate-x-1 transition-transform duration-300 relative overflow-hidden">
                <div class="absolute top-0 right-0 w-16 h-16 bg-gradient-to-bl from-indigo-50 dark:from-indigo-900/20 to-transparent rounded-bl-3xl -mr-4 -mt-4 transition-all group-hover:scale-110"></div>
                
                <div class="flex justify-between items-start gap-3 sm:gap-4 relative z-10">
                    <!-- Checkbox -->
                    <label class="flex items-center justify-center pt-1 shrink-0 cursor-pointer">
                        <input type="checkbox" :checked="selectedIds.includes(q.id)" @change="toggleSelect(q.id)" class="w-4 h-4 rounded border-slate-300 text-indigo-600 focus:ring-indigo-500">
                    </label>
                    <div class="flex-1 min-w-0">
                        <div class="flex items-center gap-2 mb-2 flex-wrap">
                             <span 
                                class="px-2 py-0.5 rounded text-[10px] font-black uppercase tracking-wider shrink-0"
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
                    
                    <div class="flex flex-row sm:flex-col gap-2 sm:opacity-0 sm:group-hover:opacity-100 transition-all duration-200 shrink-0">
                        <button @click="openEditQuestion(q)" class="p-2 bg-slate-50 hover:bg-indigo-50 text-slate-400 hover:text-indigo-600 dark:bg-slate-800 dark:hover:bg-indigo-900/30 dark:text-slate-500 dark:hover:text-indigo-400 rounded-lg transition-colors" title="编辑">
                            ✏️
                        </button>
                        <button @click="deleteQuestion(q.id)" class="p-2 bg-slate-50 hover:bg-rose-50 text-slate-400 hover:text-rose-600 dark:bg-slate-800 dark:hover:bg-rose-900/30 dark:text-slate-500 dark:hover:text-rose-400 rounded-lg transition-colors" title="删除">
                           🗑️
                        </button>
                    </div>
                </div>
            </div>
        </div>
        
        <!-- Pagination -->
        <div v-if="pagination.totalPages > 1" class="flex flex-col sm:flex-row justify-between items-center py-4 gap-3">
             <span class="text-xs font-bold text-slate-400">PAGE {{ pagination.page }} OF {{ pagination.totalPages }} · 共 {{ pagination.total }} 题</span>
             <div class="flex items-center gap-2">
                <button 
                    @click="changePage(pagination.page - 1)" 
                    :disabled="pagination.page === 1"
                    class="px-4 py-2 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg text-xs font-bold disabled:opacity-50 hover:bg-slate-50 dark:hover:bg-slate-700 transition-colors"
                >
                    PREV
                </button>
                
                <!-- Page Jump -->
                <div class="flex items-center gap-1.5">
                    <input 
                        v-model="jumpPage" 
                        @keydown.enter="goToPage" 
                        type="number" 
                        :min="1" 
                        :max="pagination.totalPages"
                        class="w-14 px-2 py-2 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg text-xs font-bold text-center focus:ring-2 focus:ring-indigo-500/20 outline-none [appearance:textfield] [&::-webkit-outer-spin-button]:appearance-none [&::-webkit-inner-spin-button]:appearance-none"
                        placeholder="页"
                    >
                    <button @click="goToPage" class="px-3 py-2 bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-300 rounded-lg text-xs font-bold hover:bg-slate-200 dark:hover:bg-slate-600 transition-colors">
                        GO
                    </button>
                </div>
                
                <button 
                    @click="changePage(pagination.page + 1)" 
                    :disabled="pagination.page === pagination.totalPages"
                    class="px-4 py-2 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg text-xs font-bold disabled:opacity-50 hover:bg-slate-50 dark:hover:bg-slate-700 transition-colors"
                >
                    NEXT
                </button>
             </div>
        </div>
    </div>

    <!-- Question Modal -->
    <div v-if="showQuestionModal" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 backdrop-blur-sm p-4 transition-all animate-fade-in">
        <div class="glass-card w-full max-w-4xl h-[90vh] flex flex-col shadow-2xl animate-scale-up border border-white/50 dark:border-slate-600/50">
            <!-- Modal Header -->
            <div class="px-6 py-4 border-b border-slate-100 dark:border-slate-700 flex justify-between items-center bg-slate-50/50 dark:bg-slate-800/50">
                <h3 class="text-lg font-black text-slate-800 dark:text-white">{{ editingQuestion.id ? '编辑题目' : '新增题目' }}</h3>
                <div class="flex items-center gap-3">
                    <div class="flex bg-slate-200 dark:bg-slate-700 rounded-lg p-1">
                        <button 
                            @click="editingQuestion.jsonMode = false"
                            class="px-3 py-1 rounded-md text-xs font-bold transition-colors"
                            :class="!editingQuestion.jsonMode ? 'bg-white dark:bg-slate-600 shadow-sm text-indigo-600 dark:text-indigo-300' : 'text-slate-500 dark:text-slate-400'"
                        >可视编辑</button>
                        <button 
                            @click="editingQuestion.jsonMode = true; updateJsonFromForm()"
                            class="px-3 py-1 rounded-md text-xs font-bold transition-colors"
                            :class="editingQuestion.jsonMode ? 'bg-white dark:bg-slate-600 shadow-sm text-indigo-600 dark:text-indigo-300' : 'text-slate-500 dark:text-slate-400'"
                        >JSON 源码</button>
                    </div>
                   <div v-if="!editingQuestion.jsonMode" class="flex bg-slate-200 dark:bg-slate-700 rounded-lg p-1 ml-2">
                        <button 
                            @click="editingQuestion.previewMode = false"
                            class="px-3 py-1 rounded-md text-xs font-bold transition-colors"
                            :class="!editingQuestion.previewMode ? 'bg-white dark:bg-slate-600 shadow-sm text-indigo-600 dark:text-indigo-300' : 'text-slate-500 dark:text-slate-400'"
                        >编辑</button>
                        <button 
                            @click="editingQuestion.previewMode = true"
                            class="px-3 py-1 rounded-md text-xs font-bold transition-colors"
                            :class="editingQuestion.previewMode ? 'bg-white dark:bg-slate-600 shadow-sm text-indigo-600 dark:text-indigo-300' : 'text-slate-500 dark:text-slate-400'"
                        >预览</button>
                    </div>
                    
                    <button @click="showQuestionModal = false" class="text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 text-xl font-bold px-2">×</button>
                </div>
            </div>

            <!-- Modal Content -->
            <div class="flex-1 overflow-y-auto custom-scrollbar p-6 space-y-6 bg-slate-50/30 dark:bg-slate-900/20">
                
                <!-- JSON Mode -->
                <div v-if="editingQuestion.jsonMode" class="h-full flex flex-col">
                    <textarea 
                        v-model="editingQuestion.jsonContent"
                        class="flex-1 w-full bg-slate-900 text-green-400 font-mono text-xs p-4 rounded-xl resize-none focus:outline-none focus:ring-2 focus:ring-indigo-500/50"
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
                            <select v-model="editingQuestion.major_id" class="w-full px-4 py-2.5 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-slate-200 outline-none focus:ring-2 focus:ring-indigo-500/20">
                                <option value="" disabled>选择专业</option>
                                <option v-for="m in majors" :key="m.id" :value="m.id">{{ m.name }}</option>
                            </select>
                        </div>
                        <div class="space-y-1.5">
                            <label class="text-xs font-bold text-slate-500 dark:text-slate-400 ml-1">所属科目</label>
                            <select v-model="editingQuestion.subject_id" class="w-full px-4 py-2.5 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-slate-200 outline-none focus:ring-2 focus:ring-indigo-500/20">
                                <option value="" disabled>选择科目</option>
                                <option v-for="s in modalSubjects" :key="s.id" :value="s.id">{{ s.name }}</option>
                            </select>
                        </div>
                        <div class="space-y-1.5">
                            <label class="text-xs font-bold text-slate-500 dark:text-slate-400 ml-1">题目类型</label>
                            <select v-model="editingQuestion.type" class="w-full px-4 py-2.5 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-bold text-slate-700 dark:text-slate-200 outline-none focus:ring-2 focus:ring-indigo-500/20">
                                <option value="single">单选题</option>
                                <option value="multiple">多选题</option>
                                <option value="judge">判断题</option>
                                <option value="fill">填空题</option>
                                <option value="sort">排序题</option>
                            </select>
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
                                    class="flex-1 py-2.5 rounded-xl text-xs font-bold transition-all border-2 flex items-center justify-center gap-2"
                                    :class="editingQuestion.difficulty === d.val 
                                        ? `border-${d.color.replace('bg-','')} ${d.color} text-white shadow-md` 
                                        : 'border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-500 hover:bg-slate-50'"
                                >
                                    <span>{{ d.val === 1 ? '😊' : (d.val === 2 ? '😐' : '🥵') }}</span>
                                    {{ d.label }}
                                </button>
                            </div>
                        </div>
                        <div class="space-y-1.5">
                             <label class="text-xs font-bold text-slate-500 dark:text-slate-400 ml-1">标签 (Tag)</label>
                             <div class="w-full px-4 py-2 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl flex flex-wrap gap-2 min-h-[46px] relative focus-within:ring-2 focus-within:ring-indigo-500/20">
                                 <span v-for="(tag, idx) in editingQuestion.tags" :key="idx" class="px-2 py-0.5 bg-indigo-100 dark:bg-indigo-900/30 text-indigo-700 dark:text-indigo-300 rounded text-xs font-bold flex items-center gap-1">
                                     {{ tag }}
                                     <button @click="removeTag(idx)" class="hover:text-indigo-900 dark:hover:text-indigo-100">×</button>
                                 </span>
                                 <input 
                                    v-model="editingQuestion.tagInput" 
                                    @keydown.enter.prevent="addTag"
                                    @blur="addTag"
                                    type="text" 
                                    class="flex-1 bg-transparent border-none outline-none text-sm font-medium min-w-[60px] text-slate-700 dark:text-slate-200" 
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
                            <button @click="triggerImageUpload('stem')" class="text-indigo-500 text-xs font-bold hover:underline flex items-center gap-1">
                                🖼️ 插入图片
                            </button>
                        </div>
                        <textarea v-model="editingQuestion.stem" rows="3" class="w-full px-4 py-3 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-medium text-slate-700 dark:text-slate-200 outline-none focus:ring-2 focus:ring-indigo-500/20 resize-none" placeholder="输入题目描述... (支持 Markdown 和图片)"></textarea>
                    </div>

                    <!-- Answer for Fill-in-the-blank -->
                    <div v-if="editingQuestion.type === 'fill'" class="space-y-1.5">
                        <label class="text-xs font-bold text-slate-500 dark:text-slate-400 ml-1">正确答案</label>
                        <input v-model="editingQuestion.answer" type="text" class="w-full px-4 py-3 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-medium text-slate-700 dark:text-slate-200 outline-none focus:ring-2 focus:ring-indigo-500/20" placeholder="输入正确答案...">
                    </div>

                    <!-- Material Sub-Questions -->
                    <div v-if="editingQuestion.type === 'material'" class="space-y-4">
                        <label class="block text-xs font-bold text-slate-500 dark:text-slate-400 ml-1">
                            子题目列表
                        </label>
                        
                        <div v-for="(subQ, idx) in editingQuestion.subQuestions" :key="idx" class="p-4 bg-slate-50 dark:bg-slate-900/50 border border-slate-200 dark:border-slate-700 rounded-xl space-y-4 relative group">
                            <div class="absolute right-4 top-4 opacity-0 group-hover:opacity-100 transition-opacity">
                                <button @click="removeSubQuestion(idx)" class="text-slate-400 hover:text-rose-500 transition-colors">🗑️</button>
                            </div>
                            
                            <div class="flex gap-4">
                                <div class="w-8 h-8 flex-shrink-0 bg-indigo-600 text-white rounded-lg flex items-center justify-center font-bold text-sm">{{ idx + 1 }}</div>
                                <div class="flex-1 space-y-3">
                                    <!-- Sub Type & Stem -->
                                    <div class="flex gap-3">
                                        <select v-model="subQ.type" class="w-32 px-3 py-2 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg text-xs font-bold outline-none focus:ring-2 focus:ring-indigo-500/20">
                                            <option value="single">单选题</option>
                                            <option value="multiple">多选题</option>
                                            <option value="judge">判断题</option>
                                            <option value="fill">填空题</option>
                                        </select>
                                        <input v-model="subQ.stem" type="text" class="flex-1 px-4 py-2 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg text-sm outline-none focus:ring-2 focus:ring-indigo-500/20" placeholder="子题目内容...">
                                    </div>
                                    
                                    <!-- Sub Options Area -->
                                    <div v-if="subQ.type === 'fill'">
                                        <input v-model="subQ.answer" type="text" class="w-full px-4 py-2 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg text-sm outline-none focus:ring-2 focus:ring-emerald-500/20" placeholder="正确答案...">
                                    </div>
                                    
                                    <div v-else-if="subQ.type === 'judge'" class="flex gap-3">
                                        <button 
                                            v-for="opt in ['正确', '错误']" 
                                            :key="opt"
                                            @click="subQ.answer = [opt]"
                                            class="flex-1 py-1.5 rounded-lg border text-xs font-bold transition-all"
                                            :class="subQ.answer?.[0] === opt ? 'bg-emerald-500 border-emerald-500 text-white' : 'bg-white dark:bg-slate-800 border-slate-200 dark:border-slate-700 text-slate-500'"
                                        >{{ opt }}</button>
                                    </div>

                                    <div v-else class="space-y-2">
                                        <div v-for="(opt, optIdx) in subQ.options" :key="optIdx" class="flex gap-2 items-center">
                                            <button 
                                                @click="toggleSubAnswer(subQ, opt.label)"
                                                class="w-8 h-8 rounded border flex items-center justify-center font-bold text-xs transition-colors"
                                                :class="subQ.answer?.includes(opt.label) ? 'bg-indigo-500 border-indigo-500 text-white' : 'bg-white dark:bg-slate-800 border-slate-200 text-slate-400'"
                                            >{{ opt.label }}</button>
                                            <input v-model="opt.content" type="text" class="flex-1 px-3 py-1.5 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded text-xs outline-none focus:ring-2 focus:ring-indigo-500/20" :placeholder="'选项 ' + opt.label">
                                        </div>
                                    </div>

                                    <!-- Sub Analysis -->
                                    <input v-model="subQ.analysis" type="text" class="w-full px-4 py-2 bg-slate-100 dark:bg-slate-800/50 border-none rounded-lg text-xs text-slate-500 focus:bg-white dark:focus:bg-slate-800 transition-colors outline-none" placeholder="子题目解析 (选填)...">
                                </div>
                            </div>
                        </div>

                        <button @click="addSubQuestion" class="w-full py-3 border-2 border-dashed border-slate-300 dark:border-slate-600 rounded-xl text-slate-500 hover:border-indigo-500 hover:text-indigo-500 font-bold transition-all flex items-center justify-center gap-2">
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
                                class="flex-1 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl px-4 py-2.5 focus:ring-2 focus:ring-indigo-500/20 outline-none text-sm font-medium"
                                :placeholder="'排序项 ' + (idx + 1)"
                             >
                             <button @click="removeOption(idx)" class="w-10 h-10 rounded-xl border border-slate-200 dark:border-slate-700 text-slate-400 hover:text-rose-500 hover:bg-rose-50 dark:hover:bg-rose-900/20 transition-colors">
                                 ×
                             </button>
                        </div>
                        <button @click="addOption" class="w-full py-2 border-2 border-dashed border-slate-300 dark:border-slate-600 rounded-xl text-slate-500 hover:border-indigo-500 hover:text-indigo-500 font-bold transition-all">
                            + 添加排序项
                        </button>
                        <p class="text-xs text-slate-400">提示：请直接按正确顺序录入，系统会在考试时自动打乱顺序。</p>
                    </div>

                    <!-- Options -->
                    <div v-if="editingQuestion.type !== 'fill' && editingQuestion.type !== 'sort' && editingQuestion.type !== 'material'" class="space-y-3">
                        <div class="flex justify-between items-center">
                            <label class="text-xs font-bold text-slate-500 dark:text-slate-400 ml-1">选项设置</label>
                            <button v-if="editingQuestion.type !== 'judge'" @click="addOption" class="text-indigo-500 text-xs font-bold hover:underline">+ 添加选项</button>
                        </div>
                        
                        <!-- Judge Options Fixed -->
                        <div v-if="editingQuestion.type === 'judge'" class="flex gap-4">
                            <button 
                                v-for="opt in ['正确', '错误']" 
                                :key="opt"
                                @click="toggleAnswer(opt)"
                                class="flex-1 py-3 rounded-xl border-2 transition-all font-bold text-sm flex items-center justify-center gap-2"
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
                                    class="w-10 h-10 rounded-lg border-2 flex items-center justify-center transition-all font-black text-sm"
                                    :class="editingQuestion.answer.includes(opt.label) ? 'border-indigo-500 bg-indigo-500 text-white' : 'border-slate-200 dark:border-slate-700 text-slate-400 hover:border-indigo-300'"
                                >
                                    {{ opt.label }}
                                </button>
                                <input v-model="opt.content" type="text" class="flex-1 px-4 py-2.5 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-medium focus:ring-2 focus:ring-indigo-500/20 outline-none" :placeholder="`选项 ${opt.label} 内容`">
                                <button @click="removeOption(idx)" class="w-8 h-8 flex items-center justify-center text-slate-300 hover:text-rose-500 transition-colors">×</button>
                            </div>
                        </div>
                    </div>

                    <!-- Analysis -->
                    <div class="space-y-1.5">
                        <div class="flex justify-between items-center">
                            <label class="text-xs font-bold text-slate-500 dark:text-slate-400 ml-1">题目解析</label>
                            <button @click="triggerImageUpload('analysis')" class="text-indigo-500 text-xs font-bold hover:underline flex items-center gap-1">
                                🖼️ 插入图片
                            </button>
                        </div>
                        <textarea v-model="editingQuestion.analysis" rows="3" class="w-full px-4 py-3 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-medium text-slate-700 dark:text-slate-200 outline-none focus:ring-2 focus:ring-indigo-500/20 resize-none" placeholder="输入答案解析... (支持 Markdown 和图片)"></textarea>
                    </div>
                    </div>
                </div> <!-- End of Edit Mode Wrapper --> 
            </div>

            <!-- Modal Footer -->
            <div class="px-6 py-4 border-t border-slate-100 dark:border-slate-700 flex justify-end gap-3 bg-slate-50/50 dark:bg-slate-800/50">
                <button @click="showQuestionModal = false" class="px-6 py-2.5 rounded-xl border border-slate-200 dark:border-slate-700 text-slate-600 dark:text-slate-300 font-bold text-xs hover:bg-slate-100 dark:hover:bg-slate-700 transition-colors">
                    取消
                </button>
                <button @click="saveQuestion" class="px-6 py-2.5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl font-bold text-xs shadow-lg shadow-indigo-200/50 dark:shadow-none transition-all active:scale-95">
                    保存题目
                </button>
            </div>
        </div>
    </div>

    <!-- JSON Import Modal -->
    <div v-if="showJsonImportModal" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-md p-4 transition-all animate-fade-in">
        <div class="glass-card w-full max-w-2xl p-6 shadow-2xl animate-scale-up border border-white/50 dark:border-slate-600/50 flex flex-col h-[80vh]">
            <h3 class="text-xl font-black text-slate-800 dark:text-white mb-4">导入题目 (JSON)</h3>
             <div class="mb-4 p-3 bg-amber-50 dark:bg-amber-900/20 border border-amber-100 dark:border-amber-800/50 rounded-xl text-xs text-amber-600 dark:text-amber-400">
                <span class="font-bold">⚠️ 注意:</span> 请确保先在筛选栏选择好要导入的【专业】和【科目】。JSON 格式必须为对象数组 `[{ stem: "...", ... }]`。
            </div>
            <textarea 
                v-model="jsonImportContent" 
                class="flex-1 w-full bg-slate-900 text-slate-200 font-mono text-xs p-4 rounded-xl resize-none focus:outline-none mb-4"
                placeholder='[
  {
    "type": "single",
    "stem": "Example Question",
    "options": [{"label":"A","content":"Opt1"}],
    "answer": ["A"],
    "analysis": "..."
  }
]'
            ></textarea>
            <div class="flex gap-3 justify-end">
                <button @click="showJsonImportModal = false" class="px-5 py-2.5 text-slate-500 hover:text-slate-700 font-bold text-xs">取消</button>
                <button @click="handleJsonImport" class="px-5 py-2.5 bg-indigo-600 text-white rounded-xl font-bold text-xs shadow-lg shadow-indigo-200/50">确认导入</button>
            </div>
        </div>
    </div>
    
    <!-- Excel Import Modal -->
    <div v-if="showExcelImportModal" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-md p-4 transition-all animate-fade-in">
        <div class="glass-card w-full max-w-lg p-6 shadow-2xl animate-scale-up border border-white/50 dark:border-slate-600/50 flex flex-col">
             <h3 class="text-xl font-black text-slate-800 dark:text-white mb-4">导入题目 (Excel)</h3>
             
             <div class="space-y-4">
                 <div class="p-3 bg-blue-50 dark:bg-blue-900/20 border border-blue-100 dark:border-blue-800/50 rounded-xl text-xs text-blue-600 dark:text-blue-400">
                    <span class="font-bold">📘 说明:</span> 请下载模板，按照格式填写后上传。目前支持单选题、多选题、判断题和填空题。
                 </div>
                 
                 <div class="flex flex-col gap-2">
                     <label class="text-xs font-bold text-slate-500 uppercase">1. 下载模板</label>
                     <a href="/template.xlsx" download class="flex items-center gap-2 px-4 py-3 bg-slate-100 dark:bg-slate-800 rounded-xl text-sm font-bold text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-700 transition-colors">
                         <span>📥</span> 下载 Excel 模板
                     </a>
                 </div>

                 <div class="flex flex-col gap-2">
                     <label class="text-xs font-bold text-slate-500 uppercase">2. 上传文件</label>
                     <div class="relative group">
                         <input 
                            type="file" 
                            accept=".xlsx, .xls" 
                            @change="excelFile = $event.target.files[0]"
                            class="absolute inset-0 w-full h-full opacity-0 cursor-pointer z-10"
                         />
                         <div class="w-full px-4 py-8 border-2 border-dashed border-slate-300 dark:border-slate-600 rounded-xl flex flex-col items-center justify-center gap-2 group-hover:border-indigo-500 group-hover:bg-indigo-50/50 dark:group-hover:bg-indigo-900/20 transition-all">
                             <div class="w-10 h-10 rounded-full bg-slate-100 dark:bg-slate-800 flex items-center justify-center text-xl group-hover:scale-110 transition-transform">
                                 {{ excelFile ? '📄' : '📁' }}
                             </div>
                             <span class="text-sm font-bold text-slate-600 dark:text-slate-300">
                                 {{ excelFile ? excelFile.name : '点击或拖拽上传 Excel 文件' }}
                             </span>
                         </div>
                     </div>
                 </div>

                 <!-- Result -->
                 <div v-if="excelImportResult" class="p-3 rounded-xl text-xs space-y-1" :class="excelImportResult.success > 0 ? 'bg-emerald-50 text-emerald-600' : 'bg-rose-50 text-rose-600'">
                     <p class="font-bold">导入结果:</p>
                     <p>成功: {{ excelImportResult.success }} 条</p>
                     <p>失败: {{ excelImportResult.failure }} 条</p>
                     <div v-if="excelImportResult.errors && excelImportResult.errors.length > 0" class="mt-2 text-[10px] max-h-20 overflow-y-auto border-t border-rose-200 pt-1">
                         <p v-for="(err, i) in excelImportResult.errors" :key="i" class="text-rose-500">• {{ err }}</p>
                     </div>
                 </div>
             </div>

             <div class="flex gap-3 justify-end mt-6">
                <button @click="showExcelImportModal = false" class="px-5 py-2.5 text-slate-500 hover:text-slate-700 font-bold text-xs">取消</button>
                <button 
                    @click="handleExcelUpload" 
                    :disabled="!excelFile || isImporting"
                    class="px-5 py-2.5 bg-indigo-600 text-white rounded-xl font-bold text-xs shadow-lg shadow-indigo-200/50 disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
                >
                    <span v-if="isImporting" class="w-3 h-3 border-2 border-white/30 border-t-white rounded-full animate-spin"></span>
                    <span>{{ isImporting ? '导入中...' : '开始导入' }}</span>
                </button>
            </div>
        </div>
    </div>

    <Toast ref="toast" />
    <ConfirmDialog ref="confirmDialog" />
    
    <input type="file" ref="fileInput" accept="image/*" class="hidden" @change="handleImageSelection">

</div>
</template>
