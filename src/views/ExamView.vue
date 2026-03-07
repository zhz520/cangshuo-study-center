<script setup>
import { ref, onMounted, computed, watch, nextTick, reactive } from "vue";
import { useRoute, useRouter } from "vue-router";
import { data as api } from "../api";
import { normalizeQuestion } from "../utils/question-normalizer";
import Toast from "../components/ui/Toast.vue";
import ConfirmDialog from "../components/ui/ConfirmDialog.vue";
import MarkdownRenderer from "../components/ui/MarkdownRenderer.vue";
import draggable from "vuedraggable";
import { exportToWord } from "../utils/export-docx"; // Import export utility

const route = useRoute();
const router = useRouter();
const subjectId = route.params.subjectId;
const toast = ref(null);

// Confirmation Dialog State
const confirmDialog = reactive({
  show: false,
  title: "",
  content: "",
  type: "info",
  onConfirm: () => {},
  onCancel: () => {},
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

// State
const questions = ref([]);
const userAnswers = ref({}); // { questionId: { answer: 'A', isCorrect: true } }
const loading = ref(true);
const currentIndex = ref(0); // This is the index in displayList
const showSheet = ref(false); // Answer Sheet Modal
const mode = ref("all"); // 'all', 'wrong', 'recommendation'
const displayList = ref([]); // Array of logical indices [0, 1, 2, 5, ...]
const subjectTitle = ref(""); // Subject Name for Header
const isRecommendation = computed(() => mode.value === 'recommendation');

// Computed
const currentRealIndex = computed(() => {
  if (displayList.value.length === 0) return -1;
  return displayList.value[currentIndex.value];
});

const currentQuestion = computed(() => {
  if (currentRealIndex.value === -1) return null;
  return questions.value[currentRealIndex.value];
});

const userAnswerData = computed(() => {
  if (!currentQuestion.value) return null;
  return userAnswers.value[currentQuestion.value.id];
});

const isLast = computed(
  () => currentIndex.value === displayList.value.length - 1,
);
const isFirst = computed(() => currentIndex.value === 0);

// Stats
const stats = computed(() => {
  const total = questions.value.length;
  const answered = Object.values(userAnswers.value).length;
  const correct = Object.values(userAnswers.value).filter(
    (a) => a.isCorrect,
  ).length;
  const wrong = answered - correct;
  const rate = answered === 0 ? 0 : Math.round((correct / answered) * 100);
  return { total, answered, correct, wrong, rate };
});

// Initialization
onMounted(async () => {
  try {
    // 1. Fetch Questions
    // Fetch by subject_id
    const res = await api.getQuestions(subjectId, { limit: 5000 });
    if (Array.isArray(res.data)) {
      questions.value = res.data.map((q) => {
          let options = typeof q.options === 'string' ? JSON.parse(q.options) : q.options;
          
          // Fix: Handle array format options [{label: 'A', content: '...'}, ...] -> { 'A': '...' }
          if (Array.isArray(options) && q.type !== 'sort') {
              const optsObj = {};
              options.forEach(opt => {
                  if (opt.label && opt.content !== undefined) {
                      optsObj[opt.label] = opt.content;
                  }
              });
              options = optsObj;
          }

          // Fix: Force standard options for judge questions
          if (q.type === 'judge') {
              options = { 'A': '正确', 'B': '错误' };
          }

          // Fix: Clean answer string (remove commas, spaces)
          let cleanAnswer = q.answer;
          
          try {
              if (typeof cleanAnswer === 'string') {
                  const parsed = JSON.parse(cleanAnswer);
                  cleanAnswer = parsed;
              }
          } catch(e) {}

          // Normalize Judge Answers: "正确" -> "A", "错误" -> "B"
          if (q.type === 'judge') {
             let val = Array.isArray(cleanAnswer) ? cleanAnswer[0] : cleanAnswer;
             if (val === '正确' || val === '对' || val === '√' || val === 'A') cleanAnswer = 'A';
             else if (val === '错误' || val === '错' || val === '×' || val === 'B') cleanAnswer = 'B';
          }

          if (typeof cleanAnswer === 'string' && q.type !== 'fill' && q.type !== 'text' && q.type !== 'sort') {
              // Remove commas, spaces, and ensure uppercase (ONLY for selection questions)
              cleanAnswer = cleanAnswer.replace(/[^A-Za-z]/g, '').toUpperCase();
          }

          // Material Normalization
          if (q.type === 'material') {
               // Ensure options is array of sub-questions
               if (!Array.isArray(options)) options = [];
               // Normalize sub-questions options if needed
               options = options.map(sq => {
                   let sqOpts = sq.options;
                   if (sq.type === 'judge') sqOpts = { 'A': '正确', 'B': '错误' };
                   return { ...sq, options: sqOpts };
               });
          }

          return {
              id: q.id,
              subject_name: q.subject_name,
              content: {
                  question: q.content,
                  options: options, // For material, this is SubQuestions array
                  type: q.type,
                  answer: Array.isArray(cleanAnswer) ? cleanAnswer : (cleanAnswer ? cleanAnswer.split('') : []),
                  analysis: q.analysis,
                  difficulty: q.difficulty || 1,
                  tags: q.tags || [],
                  _category: q.type 
              }
          };
      });

      // Set Subject Title from first question if available
      if (questions.value.length > 0) {
          subjectTitle.value = questions.value[0].subject_name;
      }
    }



    // 1.5. Check for Recommendation Mode (via query param or just default to normal)
    if (route.query.mode === 'recommendation') {
        mode.value = 'recommendation';
        await loadRecommendations();
    } else {
        // Normal Load
        updateDisplayList(false); 
    }

    // 2. Fetch Progress
    const progRes = await api.getProgress();
    if (Array.isArray(progRes.data)) {
      progRes.data.forEach((p) => {
        // Only load progress for questions that exist in this category
        const qExists = questions.value.find((q) => q.id === p.question_id);
        if (qExists) {
          // Normalize stored answer
          let ans = p.answer;
          try {
            ans = JSON.parse(ans);
          } catch (e) {}

          // Safeguard: Ensure ans supports .includes (String or Array)
          if (typeof ans === "number") ans = String(ans);
          if (typeof ans === "boolean") ans = ans ? "A" : "B"; // Map boolean to A/B if legacy
          if (!Array.isArray(ans) && typeof ans !== "string") ans = []; // Fallback

          userAnswers.value[p.question_id] = {
            answer: ans,
            isCorrect: p.is_correct,
            // Infer 'submitted' if it exists in DB, it's submitted
            submitted: true,
          };
        }
      });
    }

    updateDisplayList(false); // Don't reset index yet

    // 3. Resume Logic
    // Find the first unanswered question
    let firstUnansweredIndex = 0;
    if (displayList.value.length > 0) {
      firstUnansweredIndex = displayList.value.findIndex((realIdx) => {
        const q = questions.value[realIdx];
        return !userAnswers.value[q.id];
      });

      // If all are answered, go to last
      if (firstUnansweredIndex === -1 && displayList.value.length > 0) {
        firstUnansweredIndex = displayList.value.length - 1;
      }

      // Set index
      if (firstUnansweredIndex > 0) {
        currentIndex.value = firstUnansweredIndex;
        // Notify user
        setTimeout(() => {
          toast.value?.add(
            `已自动定位到上次进度 (第 ${firstUnansweredIndex + 1} 题)`,
            "info",
            2000,
          );
        }, 500);
      }
    }
  } catch (err) {
    console.error(err);
  } finally {
    loading.value = false;
  }
});

// Logic
const loadRecommendations = async () => {
    loading.value = true;
    try {
        const res = await api.getRecommendations(subjectId);
        if (Array.isArray(res.data) && res.data.length > 0) {
            // Replace questions list with recommendations
            // Note: We need to normalize them just like regular questions
            questions.value = res.data.map(q => {
                 let options = typeof q.options === 'string' ? JSON.parse(q.options) : q.options;
                 if (Array.isArray(options)) {
                      const optsObj = {};
                      options.forEach(opt => { if (opt.label) optsObj[opt.label] = opt.content; });
                      options = optsObj;
                 }
                 if (q.type === 'judge') options = { 'A': '正确', 'B': '错误' };
                 
                 let cleanAnswer = q.answer;
                 try { if (typeof cleanAnswer === 'string') cleanAnswer = JSON.parse(cleanAnswer); } catch(e){}
                 
                 if (q.type === 'judge') {
                     let val = Array.isArray(cleanAnswer) ? cleanAnswer[0] : cleanAnswer;
                     if (['正确','对','√','A'].includes(val)) cleanAnswer = 'A';
                     else if (['错误','错','×','B'].includes(val)) cleanAnswer = 'B';
                 }
                 
                 if (typeof cleanAnswer === 'string' && q.type !== 'fill' && q.type !== 'text') {
                     cleanAnswer = cleanAnswer.replace(/[^A-Za-z]/g, '').toUpperCase();
                 }

                 return {
                     id: q.id,
                     subject_name: q.subject_name,
                     content: {
                         question: q.content,
                         options: options,
                         type: q.type,
                         answer: Array.isArray(cleanAnswer) ? cleanAnswer : (cleanAnswer ? cleanAnswer.split('') : []),
                         analysis: q.analysis,
                         difficulty: q.difficulty || 1,
                         tags: q.tags || [],
                         _category: q.type
                     },
                     // Mark as recommended
                     _isRecommended: true
                 };
            });
            displayList.value = questions.value.map((_, i) => i);
            currentIndex.value = 0;
            toast.value?.add(`已为您智能推荐 ${questions.value.length} 道题目`, "success");
        } else {
            toast.value?.add("暂无推荐题目 (可能因为错题较少)", "info");
            mode.value = 'all';
            updateDisplayList(true);
        }
    } catch (e) {
        console.error(e);
        toast.value?.add("获取推荐失败", "error");
        mode.value = 'all';
        updateDisplayList(true);
    } finally {
        loading.value = false;
    }
};

const updateDisplayList = (resetIndex = false) => {
  if (mode.value === 'recommendation') return; // Do nothing, list is already set
  
  if (mode.value === "wrong") {
    displayList.value = questions.value
      .map((q, i) => {
        const ua = userAnswers.value[q.id];
        return ua && !ua.isCorrect ? i : -1;
      })
      .filter((i) => i !== -1);
  } else {
    displayList.value = questions.value.map((_, i) => i);
  }

  if (resetIndex) currentIndex.value = 0;
  else if (currentIndex.value >= displayList.value.length) {
    currentIndex.value = 0;
  }
};

const toggleSheet = () => {
  showSheet.value = !showSheet.value;
  if (showSheet.value) {
    nextTick(() => {
      scrollToActiveSheetItem();
    });
  }
};

const scrollToActiveSheetItem = () => {
  const activeItem = document.querySelector(".sheet-item-active");
  if (activeItem) {
    activeItem.scrollIntoView({ block: "center", behavior: "smooth" });
  }
};

const toggleMode = () => {
  if (mode.value === "all") {
    const hasWrong = Object.values(userAnswers.value).some((a) => !a.isCorrect);
    if (!hasWrong) return toast.value?.add("当前没有错题记录！", "warning");
    mode.value = "wrong";
  } else {
    mode.value = "all";
  }
  updateDisplayList(true);
};

const jumpTo = (index) => {
  currentIndex.value = index;
  showSheet.value = false;
};

// Answer Sheet Pagination
const sheetPage = ref(0);
const sheetPageSize = 100;

const sheetGroups = computed(() => {
  const total = displayList.value.length;
  if (total <= sheetPageSize) return [];
  
  const count = Math.ceil(total / sheetPageSize);
  const groups = [];
  for (let i = 0; i < count; i++) {
    const start = i * sheetPageSize + 1;
    const end = Math.min((i + 1) * sheetPageSize, total);
    groups.push({ index: i, label: `${start}-${end}` });
  }
  return groups;
});

const visibleSheetItems = computed(() => {
  const start = sheetPage.value * sheetPageSize;
  const end = start + sheetPageSize;
  return displayList.value.slice(start, end).map((realIndex, idx) => ({
    realIndex,
    displayIndex: start + idx
  }));
});

watch(showSheet, (val) => {
  if (val) {
    sheetPage.value = Math.floor(currentIndex.value / sheetPageSize);
  }
});

// Interaction
const handleAnswer = async (val) => {
  // If user locked, do nothing
  if (userAnswerData.value && userAnswerData.value.submitted) return;

  const q = currentQuestion.value;
  const type = q.content.type; // Normalized type: single, multiple, judge, text

  // 1. Multi Logic (Toggle)
  if (type === "multiple") {
    let currentAns = userAnswerData.value ? userAnswerData.value.answer : [];
    if (!Array.isArray(currentAns)) currentAns = [];

    if (currentAns.includes(val)) {
      currentAns = currentAns.filter((x) => x !== val);
    } else {
      currentAns = [...currentAns, val].sort();
    }

    userAnswers.value[q.id] = {
      answer: currentAns,
      isCorrect: false,
      submitted: false,
    };
    return;
  }

  // 2. Single/Judge Logic (Immediate Submit)
  const correctAns = q.content.answer; // Array ["A"]
  const isCorrect = correctAns.includes(val) && correctAns.length === 1;

  userAnswers.value[q.id] = { answer: [val], isCorrect, submitted: true };
  await api.syncProgress({
    question_id: q.id,
    is_correct: isCorrect,
    answer: JSON.stringify([val]),
  });

  if (isCorrect && !isLast.value) {
    setTimeout(() => next(), 500);
  }
};

const submitMulti = async () => {
  const q = currentQuestion.value;
  const ansData = userAnswers.value[q.id];
  if (!ansData || !ansData.answer || ansData.answer.length === 0)
    return toast.value?.add("请至少选择一项", "warning");

  const userAns = ansData.answer.sort().join("");
  const correctAns = q.content.answer.sort().join(""); 

  const isCorrect = userAns === correctAns;

  // Update
  userAnswers.value[q.id] = { ...ansData, isCorrect, submitted: true };

  await api.syncProgress({
    question_id: q.id,
    is_correct: isCorrect,
    answer: JSON.stringify(ansData.answer),
  });
};

const fillInput = ref("");
watch(currentQuestion, () => {
  fillInput.value = "";
});

const submitFill = () => {
  if (!fillInput.value.trim()) return toast.value?.add("请输入答案", "warning");
  const q = currentQuestion.value;
  const correctAns = q.content.answer;

  const isCorrect =
    fillInput.value.trim().toLowerCase() ===
    correctAns.toString().trim().toLowerCase();

  userAnswers.value[q.id] = {
    answer: fillInput.value,
    isCorrect,
    submitted: true,
  };
  api.syncProgress({
    question_id: q.id,
    is_correct: isCorrect,
    answer: fillInput.value,
  });
};

// Sort Logic
const sortList = ref([]);

watch(currentQuestion, (newQ) => {
    if (!newQ) return;
    fillInput.value = "";
    
    if (newQ.content.type === 'sort') {
        const ua = userAnswers.value[newQ.id];
        if (ua && ua.answer) {
            // Restore user order
            // Ensure deep copy to avoid reference issues
            sortList.value = JSON.parse(JSON.stringify(ua.answer));
        } else {
            // Initialize with options (Shuffle them for challenge)
            // Options is array [{label, content}] or just array of contents if normalized?
            // In normalization, if type=sort, we kept it as array.
            // But QuestionManagement saves it as [{label:'A', content:'...'}].
            // We just want content for sorting usually.
            // Let's extract content.
            
            let items = [];
            if (Array.isArray(newQ.content.options)) {
                items = newQ.content.options.map(o => o.content || o); // Handle {content} or raw string
            }
            
            // Shuffle
            sortList.value = items.sort(() => Math.random() - 0.5);
        }
    }
}, { immediate: true });

const submitSort = async () => {
    const q = currentQuestion.value;
    // Current order is in sortList
    const userAns = sortList.value; // Array of strings
    const correctAns = q.content.answer; // Array of strings
    
    // Compare arrays
    const isCorrect = JSON.stringify(userAns) === JSON.stringify(correctAns);
    
    userAnswers.value[q.id] = {
        answer: userAns,
        isCorrect,
        submitted: true
    };
    
    await api.syncProgress({
        question_id: q.id,
        is_correct: isCorrect,
        answer: JSON.stringify(userAns)
    });
};

// Material Logic
const materialAnswers = ref({}); // { subIdx: answer }

watch(currentQuestion, (newQ) => {
    if (!newQ) return;
    materialAnswers.value = {};
    if (newQ.content.type === 'material') {
        const ua = userAnswers.value[newQ.id];
        if (ua && ua.answer) {
             try {
                materialAnswers.value = typeof ua.answer === 'string' ? JSON.parse(ua.answer) : ua.answer;
             } catch(e) { materialAnswers.value = {}; }
        }
    }
}, { immediate: true });

const handleMaterialSubAnswer = (subIdx, val, type) => {
    if (userAnswerData.value?.submitted) return;
    
    // Initialize if needed
    if (!materialAnswers.value) materialAnswers.value = {};
    
    if (type === 'multiple') {
        let current = materialAnswers.value[subIdx] || [];
        if (current.includes(val)) current = current.filter(x => x !== val);
        else current = [...current, val].sort();
        materialAnswers.value[subIdx] = current;
    } else {
        materialAnswers.value[subIdx] = val;
    }
};

const submitMaterial = async () => {
    const q = currentQuestion.value;
    const subQuestions = q.content.options; // Array of sub-qs
    
    // Validation: Check if all answered? Or allow partial?
    // Let's require at least one answer
    if (Object.keys(materialAnswers.value).length === 0) return toast.value?.add("请至少回答一个小题", "warning");

    let allCorrect = true;
    
    // Check correctness
    subQuestions.forEach((sq, idx) => {
        const userAns = materialAnswers.value[idx];
        const correctAns = sq.answer; // "A" or ["A","B"] or "TRUE"
        
        let isSubCorrect = false;
        
        if (!userAns) {
            isSubCorrect = false;
        } else if (sq.type === 'multiple') {
            // Compare arrays
            const u = Array.isArray(userAns) ? userAns.sort().join('') : String(userAns);
            const c = Array.isArray(correctAns) ? correctAns.sort().join('') : String(correctAns);
            isSubCorrect = u === c;
        } else if (sq.type === 'judge') {
             // CorrectAns might be "TRUE"/"FALSE" or "A"/"B" (if normalized)
             // Admin saves as "TRUE"/"FALSE".
             // ExamView normalizes main q judge options to A/B, but maybe not sub judge?
             // My normalization above for Material Sub Judge set options to Correct/Error.
             // But Answer check needs to match.
             // Let's rely on what Admin saves: "TRUE"/"FALSE".
             // User clicks "Correct" -> we should store "TRUE".
             // User clicks "Error" -> we should store "FALSE".
             // My UI logic needs to send TRUE/FALSE.
             
             isSubCorrect = userAns === correctAns;
        } else {
             // Single / Fill
             // UserAns for fill might be text. CorrectAns is string.
             isSubCorrect = String(userAns).trim().toLowerCase() === String(correctAns).trim().toLowerCase();
        }
        
        if (!isSubCorrect) allCorrect = false;
    });

    userAnswers.value[q.id] = {
        answer: materialAnswers.value,
        isCorrect: allCorrect,
        submitted: true
    };
    
    await api.syncProgress({
        question_id: q.id,
        is_correct: allCorrect,
        answer: JSON.stringify(materialAnswers.value)
    });
};

// Navigation
const next = () => {
  if (!isLast.value) currentIndex.value++;
};
const prev = () => {
  if (!isFirst.value) currentIndex.value--;
};

// Reset
const resetProgress = async () => {
  const confirmed = await showConfirm(
    "清空当前进度",
    "确定要清空当前题库的所有进度吗？此操作不可恢复。",
    "danger",
  );

  if (confirmed) {
    try {
      await api.resetProgress(subjectId);
      userAnswers.value = {}; // Clear local state
      updateDisplayList(true); // Reset list
      toast.value.add("进度已重置", "success");
    } catch (e) {
      toast.value.add("重置失败: " + e.message, "error");
    }
  }
};

// Helper
const formatAnswer = (ans, type) => {
  if (!ans || (Array.isArray(ans) && ans.length === 0)) return "无答案";
  
  // Normalize to string for checking
  // If it's array, join it. If string, just use it.
  // We want to avoid ",," issues if they exist in data, but we cleaned them in onMounted.
  // Still good to be safe.
  let val = Array.isArray(ans) ? ans.join("") : String(ans);
  
  if (type === 'judge') { 
    if (val === "A" || val === "正确" || val === "true") return "正确 (A)";
    if (val === "B" || val === "错误" || val === "false") return "错误 (B)";
  }
  
  return val;
};

// Print
const showExportModal = ref(false);
const exportConfig = reactive({
    scope: 'wrong', // 'all' or 'wrong'
    includeAnswer: true
});

const getExportData = () => {
    let list = [];
    if (exportConfig.scope === 'wrong') {
        const wrongIds = Object.keys(userAnswers.value).filter(id => !userAnswers.value[id].isCorrect);
        list = questions.value.filter(q => wrongIds.includes(q.id));
        if (list.length === 0) {
            toast.value?.add("没有错题可导出", "warning");
            return [];
        }
    } else {
        list = questions.value;
    }
    return list;
};

const handleExportWord = async () => {
    const list = getExportData();
    if (list.length === 0) return;
    
    try {
        await exportToWord(list, `${subjectTitle.value || '试题'} - ${exportConfig.scope === 'wrong' ? '错题集' : '练习卷'}`, exportConfig.includeAnswer);
        toast.value?.add("导出成功", "success");
        showExportModal.value = false;
    } catch (e) {
        console.error(e);
        toast.value?.add("导出失败", "error");
    }
};

const handlePrint = () => {
    // For print, we rely on the specific print-area div
    // We need to update that div's content based on selection
    // But currently the print-area is hardcoded for wrong questions.
    // Let's just trigger window.print() and let the CSS handle hiding/showing.
    // Wait, the current CSS print implementation only shows #print-area.
    // And #print-area has v-for over WRONG questions.
    // If user wants ALL, we need to change that logic or have two print areas.
    // Simplified: Just print scope logic here.
    
    // Actually, making print-area reactive to exportConfig is better.
    window.print();
    showExportModal.value = false;
};

const printQuestions = computed(() => {
    if (exportConfig.scope === 'all') return questions.value;
    return questions.value.filter(q => userAnswers.value[q.id] && !userAnswers.value[q.id].isCorrect);
});

// AI Tutor Logic
const aiExplanation = ref("");
const aiLoading = ref(false);

watch(currentQuestion, () => {
    aiExplanation.value = "";
    aiLoading.value = false;
});

const aiRemaining = ref(null);

const handleAiExplain = async () => {
    if (aiExplanation.value) return; // Already loaded
    
    const q = currentQuestion.value;
    const ua = userAnswers.value[q.id];
    
    if (!ua) return toast.value?.add("请先作答后再询问 AI", "warning");

    aiLoading.value = true;
    try {
        const res = await api.getAiExplanation({
            question: q.content.question,
            options: q.content.options,
            userAnswer: formatAnswer(ua.answer, q.content.type),
            correctAnswer: formatAnswer(q.content.answer, q.content.type),
            type: q.content.type
        });
        aiExplanation.value = res.data.explanation;
        if (res.data.remaining !== undefined) {
             aiRemaining.value = res.data.remaining;
        }
    } catch (e) {
        if (e.response && e.response.status === 403) {
             toast.value?.add(e.response.data.error, "warning");
        } else {
             toast.value?.add("AI 讲解请求失败", "error");
        }
    } finally {
        aiLoading.value = false;
    }
};
</script>

<template>
  <div
    class="min-h-screen bg-slate-50 dark:bg-slate-900 flex flex-col transition-colors duration-500"
  >
    <!-- Progress Bar (Sticky Top) -->
    <div class="fixed top-0 left-0 right-0 h-1 bg-slate-200 dark:bg-slate-800 z-50">
        <div 
            class="h-full bg-indigo-500 transition-all duration-300 ease-out shadow-[0_0_10px_rgba(99,102,241,0.5)]"
            :style="{ width: `${((currentIndex + 1) / (displayList.length || 1)) * 100}%` }"
        ></div>
    </div>

    <!-- Top Bar -->
    <header
      class="glass-nav px-4 py-3 flex justify-between items-center sticky top-0 z-40 print:hidden shadow-sm"
    >
      <button
        @click="router.back()"
        class="w-10 h-10 flex items-center justify-center rounded-xl bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-700 transition-all active:scale-95"
      >
        <span class="text-xl">←</span>
      </button>

      <div class="flex flex-col items-center">
        <span class="font-bold text-slate-700 dark:text-slate-200 text-sm flex items-center gap-1">
           <span v-if="mode === 'wrong'" class="w-2 h-2 rounded-full bg-red-500 animate-pulse"></span>
           <span v-if="mode === 'recommendation'" class="w-2 h-2 rounded-full bg-indigo-500 animate-pulse"></span>
           {{ mode === "wrong" ? "错题攻克" : (mode === "recommendation" ? "智能练习" : (subjectTitle || "全真模拟")) }}
        </span>
        <span class="text-[10px] text-slate-400 dark:text-slate-500 font-mono font-bold tracking-wider mt-0.5 truncate max-w-[150px]">
          <span class="text-indigo-500 dark:text-indigo-400 text-base">{{ currentIndex + 1 }}</span>
          <span class="mx-1">/</span>
          <span>{{ displayList.length }}</span>
        </span>
      </div>

      <div class="flex gap-3">
        <button
          @click="showConfirm('智能练习', '由于前端限制，请返回首页选择科目重新进入来切换到智能练习模式。或我们可以加一个按钮触发。', 'info').then(() => { if(mode !== 'recommendation') { mode = 'recommendation'; loadRecommendations(); } else { mode = 'all'; window.location.reload(); } })"
          :title="mode === 'recommendation' ? '退出智能推荐' : '智能推荐'"
          class="w-10 h-10 rounded-xl flex items-center justify-center transition-all active:scale-95 shadow-sm"
          :class="mode === 'recommendation' ? 'bg-indigo-100 dark:bg-indigo-900/40 text-indigo-500 dark:text-indigo-400 ring-2 ring-indigo-200 dark:ring-indigo-900' : 'bg-white dark:bg-slate-800 text-slate-400 dark:text-slate-400 hover:text-indigo-500'"
        >
          <span class="text-lg">🧠</span>
        </button>
        <button
          @click="toggleMode"
          :title="mode === 'wrong' ? '切换回全部' : '只看错题'"
          class="w-10 h-10 rounded-xl flex items-center justify-center transition-all active:scale-95 shadow-sm"
          :class="mode === 'wrong' ? 'bg-red-100 dark:bg-red-900/40 text-red-500 dark:text-red-400 ring-2 ring-red-200 dark:ring-red-900' : 'bg-white dark:bg-slate-800 text-slate-400 dark:text-slate-400 hover:text-indigo-500'"
        >
          <span class="text-lg">⚡</span>
        </button>
        <button
          @click="showSheet = true"
          title="答题卡"
          class="w-10 h-10 rounded-xl bg-white dark:bg-slate-800 text-indigo-500 dark:text-indigo-400 hover:bg-indigo-50 dark:hover:bg-indigo-900/20 flex items-center justify-center transition-all active:scale-95 shadow-sm ring-1 ring-slate-100 dark:ring-slate-700"
        >
          <span class="text-lg">☰</span>
        </button>
        <button
          @click="showExportModal = true"
          title="导出/打印"
          class="w-10 h-10 rounded-xl bg-white dark:bg-slate-800 text-indigo-500 dark:text-indigo-400 hover:bg-indigo-50 dark:hover:bg-indigo-900/20 flex items-center justify-center transition-all active:scale-95 shadow-sm ring-1 ring-slate-100 dark:ring-slate-700 ml-2"
        >
          <span class="text-lg">📤</span>
        </button>
      </div>
    </header>

    <!-- Content -->
    <main class="flex-1 p-4 pb-32 max-w-3xl mx-auto w-full print:hidden relative z-0">
      <div v-if="loading" class="flex flex-col items-center justify-center py-32 space-y-4">
          <div class="relative w-16 h-16">
              <div class="absolute inset-0 rounded-full border-4 border-slate-100 dark:border-slate-800"></div>
              <div class="absolute inset-0 rounded-full border-4 border-indigo-500 border-t-transparent animate-spin"></div>
          </div>
          <p class="text-slate-400 text-sm animate-pulse">加载试题中...</p>
      </div>

      <div
        v-else-if="displayList.length === 0"
        class="flex flex-col items-center justify-center py-20 text-slate-400 min-h-[50vh]"
      >
        <div class="text-6xl mb-6 opacity-50">🎉</div>
        <h3 class="text-xl font-bold text-slate-700 dark:text-slate-200 mb-2">太棒了！</h3>
        <p class="text-sm">当前模式下没有题目需要处理。</p>
        <button
          v-if="mode === 'wrong'"
          @click="toggleMode"
          class="mt-8 px-8 py-3 bg-indigo-500 hover:bg-indigo-600 text-white rounded-full font-bold shadow-lg shadow-indigo-500/30 transition-all active:scale-95"
        >
          返回全部题目
        </button>
      </div>

      <div v-else class="space-y-6 animate-fade-in-up">
        <!-- Question Card -->
        <div class="glass-card p-6 sm:p-8 relative overflow-hidden transition-all duration-300">
           <!-- Decorative BG -->
           <div class="absolute -right-10 -top-10 w-40 h-40 bg-gradient-to-br from-indigo-500/5 to-purple-500/5 rounded-full blur-3xl pointer-events-none"></div>

          <div class="mb-6 relative z-10">
            <div class="flex items-center gap-3 mb-4">
               <span
                  class="px-2.5 py-1 rounded-lg text-[10px] uppercase font-black tracking-wider text-white shadow-sm"
                  :class="{
                    'bg-gradient-to-r from-blue-400 to-blue-600': currentQuestion.content.type === 'single',
                    'bg-gradient-to-r from-purple-400 to-purple-600': currentQuestion.content.type === 'multiple',
                    'bg-gradient-to-r from-orange-400 to-orange-600': currentQuestion.content.type === 'judge',
                    'bg-gradient-to-r from-emerald-400 to-emerald-600': currentQuestion.content.type === 'text' || currentQuestion.content.type === 'fill',
                    'bg-gradient-to-r from-cyan-400 to-cyan-600': currentQuestion.content.type === 'sort',
                    'bg-gradient-to-r from-slate-500 to-slate-700': currentQuestion.content.type === 'material',
                  }"
                >
                    {{
                      currentQuestion.content.type === "single"
                        ? "单选题"
                        : currentQuestion.content.type === "multiple"
                          ? "多选题"
                          : currentQuestion.content.type === "judge"
                            ? "判断题"
                            : currentQuestion.content.type === "sort"
                              ? "排序题"
                              : currentQuestion.content.type === "material"
                                ? "材料题"
                                : "填空题"
                    }}
                </span>
                <span class="text-[10px] font-bold text-slate-400 dark:text-slate-500 bg-slate-100 dark:bg-slate-800 px-2 py-1 rounded-md">
                    {{ currentQuestion.content._category || "未分类" }}
                </span>
                
                <!-- Difficulty Badge -->
                <span 
                    class="px-2 py-1 rounded-md text-[10px] font-bold border"
                    :class="{
                        'bg-emerald-50 text-emerald-600 border-emerald-200 dark:bg-emerald-900/20 dark:text-emerald-400 dark:border-emerald-800': currentQuestion.content.difficulty == 1,
                        'bg-amber-50 text-amber-600 border-amber-200 dark:bg-amber-900/20 dark:text-amber-400 dark:border-amber-800': currentQuestion.content.difficulty == 2,
                        'bg-rose-50 text-rose-600 border-rose-200 dark:bg-rose-900/20 dark:text-rose-400 dark:border-rose-800': currentQuestion.content.difficulty == 3
                    }"
                >
                    {{ currentQuestion.content.difficulty == 1 ? '简单' : (currentQuestion.content.difficulty == 2 ? '中等' : '困难') }}
                </span>

                <!-- Tags -->
                <div v-if="currentQuestion.content.tags && currentQuestion.content.tags.length > 0" class="flex gap-1">
                    <span 
                        v-for="(tag, idx) in currentQuestion.content.tags" 
                        :key="idx"
                        class="px-1.5 py-0.5 rounded text-[10px] font-bold bg-indigo-50 text-indigo-500 dark:bg-indigo-900/20 dark:text-indigo-300 border border-indigo-100 dark:border-indigo-800"
                    >
                        #{{ tag }}
                    </span>
                </div>
            </div>
            
            <h2 class="text-lg sm:text-xl font-bold text-slate-800 dark:text-slate-100 leading-relaxed whitespace-pre-line select-text tracking-wide">
              <MarkdownRenderer :content="currentQuestion.content.question" />
            </h2>
          </div>

          <!-- Options Area -->
          <div v-if="currentQuestion.content.options && currentQuestion.content.type !== 'fill' && currentQuestion.content.type !== 'sort' && Object.keys(currentQuestion.content.options).length > 0" class="space-y-3 relative z-10">
            <button
              v-for="(optVal, optKey) in currentQuestion.content.options"
              :key="optKey"
              @click="handleAnswer(optKey)"
              :disabled="!!userAnswerData?.submitted"
              class="w-full text-left p-4 sm:p-5 rounded-2xl border transition-all duration-200 flex items-start group relative overflow-hidden"
              :class="{
                'disabled:cursor-default': !!userAnswerData?.submitted,

                /* Default State */
                'border-slate-100 dark:border-slate-700/50 bg-white dark:bg-slate-800/50 hover:bg-slate-50 dark:hover:bg-slate-800 hover:border-indigo-200 dark:hover:border-indigo-800 text-slate-700 dark:text-slate-200':
                  !userAnswerData?.submitted &&
                  !userAnswerData?.answer?.includes(optKey),

                /* Selected (Multi-select In-progress) */
                'border-indigo-500 bg-indigo-50 dark:bg-indigo-900/20 text-indigo-700 dark:text-indigo-300 shadow-md shadow-indigo-500/10':
                  !userAnswerData?.submitted &&
                  userAnswerData?.answer?.includes(optKey),

                /* Correct Answer (Show Green) */
                'border-emerald-500 bg-emerald-50 dark:bg-emerald-900/20 text-emerald-700 dark:text-emerald-300 shadow-md shadow-emerald-500/10':
                  userAnswerData?.submitted &&
                  currentQuestion.content.answer.includes(optKey),

                /* Wrong Selection (Show Red) */
                'border-rose-500 bg-rose-50 dark:bg-rose-900/20 text-rose-700 dark:text-rose-300 shadow-md shadow-rose-500/10':
                  userAnswerData?.submitted &&
                  userAnswerData?.answer?.includes(optKey) &&
                  !userAnswerData.isCorrect &&
                  !currentQuestion.content.answer.includes(optKey),

                /* Unselected & Not Answer (Dimmed) */
                'opacity-60 grayscale-[0.5] border-slate-100 dark:border-slate-700 bg-slate-50/50 dark:bg-slate-900/20 text-slate-500 dark:text-slate-400':
                  userAnswerData?.submitted &&
                  !currentQuestion.content.answer.includes(optKey) &&
                  !userAnswerData?.answer?.includes(optKey),
              }"
            >
              <!-- Option Letter Circle -->
              <span
                class="w-8 h-8 rounded-xl flex items-center justify-center text-sm font-black mr-4 flex-shrink-0 transition-all shadow-sm"
                :class="{
                  'bg-slate-100 dark:bg-slate-700 text-slate-500 dark:text-slate-400 group-hover:bg-white dark:group-hover:bg-slate-600':
                    !userAnswerData?.submitted && !userAnswerData?.answer?.includes(optKey),

                  'bg-indigo-500 text-white':
                    !userAnswerData?.submitted && userAnswerData?.answer?.includes(optKey),

                  'bg-emerald-500 text-white':
                    userAnswerData?.submitted && currentQuestion.content.answer.includes(optKey),

                  'bg-rose-500 text-white':
                    userAnswerData?.submitted &&
                    userAnswerData?.answer?.includes(optKey) &&
                    !userAnswerData.isCorrect &&
                    !currentQuestion.content.answer.includes(optKey),
                }"
              >
                {{ optKey }}
              </span>
              
              <span class="text-base font-medium leading-normal py-0.5"><MarkdownRenderer :content="String(optVal)" /></span>
              
              <!-- Result Icon -->
              <div v-if="userAnswerData?.submitted" class="absolute right-4 top-1/2 -translate-y-1/2 text-xl">
                  <span v-if="currentQuestion.content.answer.includes(optKey)">✅</span>
                  <span v-else-if="userAnswerData?.answer?.includes(optKey) && !userAnswerData.isCorrect">❌</span>
              </div>
            </button>

            <!-- Multi-select Submit Button -->
            <div
              v-if="currentQuestion.content.type === 'multiple' && !userAnswerData?.submitted"
              class="pt-4 flex justify-end"
            >
              <button
                @click="submitMulti"
                class="bg-indigo-600 hover:bg-indigo-700 text-white px-8 py-3 rounded-xl font-bold shadow-lg shadow-indigo-500/30 transition-all active:scale-95 flex items-center gap-2"
              >
                <span>确认提交</span>
                <span class="text-indigo-200">➜</span>
              </button>
            </div>
          </div>

          <!-- Sort Area -->
          <div v-else-if="currentQuestion.content.type === 'sort'" class="space-y-4">
              <draggable 
                v-model="sortList" 
                item-key="index"
                class="space-y-3"
                :disabled="!!userAnswerData?.submitted"
                ghost-class="opacity-50"
              >
                  <template #item="{element, index}">
                      <div class="p-4 bg-white dark:bg-slate-800 border-2 border-slate-200 dark:border-slate-700 rounded-xl cursor-move hover:border-indigo-400 dark:hover:border-indigo-600 transition-colors shadow-sm flex items-center gap-4 group">
                          <span class="text-slate-400 group-hover:text-indigo-500">☰</span>
                          <span class="font-bold text-slate-700 dark:text-slate-200">{{ element }}</span>
                      </div>
                  </template>
              </draggable>

              <div v-if="!userAnswerData?.submitted" class="flex justify-end pt-4">
                  <button @click="submitSort" class="bg-indigo-600 hover:bg-indigo-700 text-white px-8 py-3 rounded-xl font-bold shadow-lg shadow-indigo-500/30 transition-all active:scale-95">
                      确认排序
                  </button>
              </div>
          </div>
          
          <!-- Material Area -->
          <div v-else-if="currentQuestion.content.type === 'material'" class="flex flex-col gap-6">
              <!-- Material Content -->
              <div class="p-6 bg-slate-50 dark:bg-slate-900/50 rounded-2xl border border-slate-200 dark:border-slate-700 max-h-80 overflow-y-auto custom-scrollbar shadow-inner">
                  <span class="text-xs font-bold text-slate-400 mb-3 block uppercase tracking-wider flex items-center gap-2">
                    <span>📖</span> 
                    阅读材料
                  </span>
                  <div class="prose prose-sm dark:prose-invert max-w-none">
                     <MarkdownRenderer :content="currentQuestion.content.question" />
                  </div>
              </div>

              <!-- Sub Questions -->
              <div class="space-y-8">
                  <div v-for="(subQ, idx) in currentQuestion.content.options" :key="idx" class="space-y-4 relative">
                       <!-- Sub Question Header -->
                       <div class="flex gap-3 items-start">
                           <span class="w-6 h-6 rounded-lg bg-indigo-100 dark:bg-indigo-900/30 text-indigo-600 dark:text-indigo-400 flex items-center justify-center font-black text-xs shrink-0 mt-0.5">{{ idx + 1 }}</span>
                           <div class="space-y-1">
                               <span class="font-bold text-slate-800 dark:text-slate-100 text-base">{{ subQ.stem }}</span>
                               <span class="block text-[10px] text-slate-400 font-bold uppercase">
                                   {{ subQ.type === 'single' ? '单选题' : subQ.type === 'multiple' ? '多选题' : subQ.type === 'judge' ? '判断题' : '填空题' }}
                               </span>
                           </div>
                       </div>
                       
                       <div class="pl-9">
                           <!-- Fill -->
                           <div v-if="subQ.type === 'fill'">
                               <input 
                                  :value="materialAnswers[idx]" 
                                  @input="e => handleMaterialSubAnswer(idx, e.target.value, 'fill')"
                                  :disabled="!!userAnswerData?.submitted"
                                  type="text" 
                                  class="w-full px-4 py-3 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl outline-none focus:ring-2 focus:ring-indigo-500/20 font-medium" 
                                  placeholder="输入答案..."
                                  :class="{
                                      'border-emerald-500 bg-emerald-50/20': userAnswerData?.submitted && String(materialAnswers[idx]).trim().toLowerCase() === String(subQ.answer).trim().toLowerCase(),
                                      'border-rose-500 bg-rose-50/20': userAnswerData?.submitted && String(materialAnswers[idx]).trim().toLowerCase() !== String(subQ.answer).trim().toLowerCase()
                                  }"
                               >
                               <div v-if="userAnswerData?.submitted" class="mt-2 text-xs">
                                   <span class="text-slate-400">正确答案: </span>
                                   <span class="font-bold text-emerald-600 dark:text-emerald-400">{{ subQ.answer }}</span>
                               </div>
                           </div>

                           <!-- Judge -->
                           <div v-else-if="subQ.type === 'judge'" class="flex gap-4">
                               <button 
                                   v-for="opt in ['TRUE', 'FALSE']" 
                                   :key="opt"
                                   @click="handleMaterialSubAnswer(idx, opt, 'judge')"
                                   :disabled="!!userAnswerData?.submitted"
                                   class="flex-1 py-3 rounded-xl border-2 font-bold text-sm transition-all"
                                   :class="{
                                       'border-indigo-500 bg-indigo-50 dark:bg-indigo-900/20 text-indigo-700': materialAnswers[idx] === opt && !userAnswerData?.submitted,
                                       'border-slate-200 dark:border-slate-700': materialAnswers[idx] !== opt && !userAnswerData?.submitted,
                                       
                                       // Result states
                                       'border-emerald-500 bg-emerald-50 text-emerald-600': userAnswerData?.submitted && subQ.answer === opt,
                                       'border-rose-500 bg-rose-50 text-rose-600': userAnswerData?.submitted && materialAnswers[idx] === opt && subQ.answer !== opt,
                                       'opacity-50': userAnswerData?.submitted && materialAnswers[idx] !== opt && subQ.answer !== opt
                                   }"
                               >
                                   {{ opt === 'TRUE' ? '正确' : '错误' }}
                               </button>
                           </div>

                           <!-- Single / Multiple -->
                           <div v-else class="space-y-2">
                               <button 
                                   v-for="(optContent, optKey) in subQ.options" 
                                   :key="optKey"
                                   @click="handleMaterialSubAnswer(idx, optKey, subQ.type)"
                                   :disabled="!!userAnswerData?.submitted"
                                   class="w-full text-left p-3 rounded-xl border flex items-center gap-3 transition-all group"
                                   :class="{
                                      // Default
                                      'border-slate-200 dark:border-slate-700 hover:bg-slate-50': !userAnswerData?.submitted && (!materialAnswers[idx] || (Array.isArray(materialAnswers[idx]) ? !materialAnswers[idx].includes(optKey) : materialAnswers[idx] !== optKey)),
                                      
                                      // Selected (Active)
                                      'border-indigo-500 bg-indigo-50 dark:bg-indigo-900/20 text-indigo-700': !userAnswerData?.submitted && (Array.isArray(materialAnswers[idx]) ? materialAnswers[idx].includes(optKey) : materialAnswers[idx] === optKey),

                                      // Correct (Submitted)
                                      'border-emerald-500 bg-emerald-50 text-emerald-700': userAnswerData?.submitted && (Array.isArray(subQ.answer) ? subQ.answer.includes(optKey) : subQ.answer === optKey),

                                      // Wrong (Submitted)
                                      'border-rose-500 bg-rose-50 text-rose-700': userAnswerData?.submitted && (Array.isArray(materialAnswers[idx]) ? materialAnswers[idx].includes(optKey) : materialAnswers[idx] === optKey) && !(Array.isArray(subQ.answer) ? subQ.answer.includes(optKey) : subQ.answer === optKey),
                                   }"
                               >
                                   <span class="w-6 h-6 rounded flex items-center justify-center text-xs font-bold border"
                                        :class="{
                                            'border-current': true,
                                            'bg-current text-white': (Array.isArray(materialAnswers[idx]) ? materialAnswers[idx].includes(optKey) : materialAnswers[idx] === optKey) || (userAnswerData?.submitted && (Array.isArray(subQ.answer) ? subQ.answer.includes(optKey) : subQ.answer === optKey))
                                        }"
                                   >
                                       {{ optKey }}
                                   </span>
                                   <span class="text-sm font-medium"><MarkdownRenderer :content="String(optContent)" /></span>
                               </button>
                           </div>
                           
                           <!-- Sub Analysis -->
                           <div v-if="userAnswerData?.submitted && subQ.analysis" class="mt-3 p-3 bg-slate-100 dark:bg-slate-800 rounded-lg text-xs text-slate-600 dark:text-slate-400">
                               <span class="font-bold text-indigo-500">解析:</span> <MarkdownRenderer :content="subQ.analysis || ''" />
                           </div>
                       </div>
                  </div>
              </div>
              
              <div v-if="!userAnswerData?.submitted" class="flex justify-end pt-4 border-t border-slate-100 dark:border-slate-800 mt-4">
                  <button @click="submitMaterial" class="bg-indigo-600 hover:bg-indigo-700 text-white px-8 py-3 rounded-xl font-bold shadow-lg shadow-indigo-500/30 transition-all active:scale-95">
                      提交本题
                  </button>
              </div>
          </div>

          <!-- Input Area: Fill in the blank -->
          <div v-else class="mt-6 relative z-10">
            <div class="relative w-full">
              <input
                v-model="fillInput"
                @keyup.enter="submitFill"
                :disabled="!!userAnswerData"
                type="text"
                placeholder="在此输入答案..."
                class="w-full p-5 pr-24 rounded-2xl border-2 border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-900/50 focus:border-indigo-500 focus:ring-4 focus:ring-indigo-500/10 focus:outline-none transition-all font-bold text-lg text-slate-800 dark:text-slate-100 placeholder:font-normal"
                :class="{
                  'border-emerald-500 bg-emerald-50 dark:bg-emerald-900/20': userAnswerData && userAnswerData.isCorrect,
                  'border-rose-500 bg-rose-50 dark:bg-rose-900/20': userAnswerData && !userAnswerData.isCorrect,
                }"
              />
              <button
                v-if="!userAnswerData"
                @click="submitFill"
                class="absolute right-2 top-2 bottom-2 bg-indigo-500 hover:bg-indigo-600 text-white px-5 rounded-xl font-bold text-sm transition-all active:scale-95 shadow-md"
              >
                提交
              </button>
            </div>
          </div>
        </div>

        <!-- Analysis Card -->
        <div
          v-if="userAnswerData?.submitted"
          class="bg-white/80 dark:bg-slate-800/80 backdrop-blur-md rounded-2xl p-6 border border-slate-200 dark:border-slate-700 animate-slide-up shadow-sm overflow-hidden relative"
        >
          <div class="absolute top-0 left-0 w-1.5 h-full" :class="userAnswerData.isCorrect ? 'bg-emerald-500' : 'bg-rose-500'"></div>
          
          <div class="flex items-center gap-3 mb-4">
            <div class="w-10 h-10 rounded-full flex items-center justify-center text-xl shrink-0" :class="userAnswerData.isCorrect ? 'bg-emerald-100 text-emerald-600' : 'bg-rose-100 text-rose-600'">
                {{ userAnswerData.isCorrect ? '👏' : '🤔' }}
            </div>
            <div>
                 <h3 class="font-bold text-slate-800 dark:text-slate-100">
                     {{ userAnswerData.isCorrect ? '回答正确' : '回答错误' }}
                 </h3>
                 <p class="text-xs text-slate-500">
                     正确答案：<span class="font-mono font-bold">{{ formatAnswer(currentQuestion.content.answer, currentQuestion.content.type) }}</span>
                 </p>
            </div>
          </div>

          <div class="bg-slate-50 dark:bg-slate-900/50 rounded-xl p-4 text-sm text-slate-600 dark:text-slate-300 leading-relaxed">
            <span class="font-bold text-indigo-500 block mb-1">📝 解析</span>
            <MarkdownRenderer :content="currentQuestion.content.analysis || '暂无详细解析'" />
          </div>

          <div class="flex flex-wrap gap-2 mt-4">
              <a
                :href="'https://www.baidu.com/s?wd=' + encodeURIComponent(currentQuestion.content.question)"
                target="_blank"
                class="inline-flex items-center gap-1.5 px-4 py-2 bg-slate-100 dark:bg-slate-700/50 rounded-full text-xs font-bold text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-700 transition-colors"
              >
                <span>🔍</span> 百度一下
              </a>

              <button
                @click="handleAiExplain"
                 :disabled="aiLoading"
                class="inline-flex items-center gap-1.5 px-4 py-2 bg-indigo-100 dark:bg-indigo-900/30 rounded-full text-xs font-bold text-indigo-600 dark:text-indigo-400 hover:bg-indigo-200 dark:hover:bg-indigo-900/50 transition-colors disabled:opacity-50 disabled:cursor-wait"
              >
                <span v-if="aiLoading" class="animate-spin">⏳</span>
                <span v-else>🤖</span>
                {{ aiLoading ? 'AI 思考中...' : 'AI 老师讲解' }}
                <span v-if="aiRemaining !== null" class="ml-1 bg-indigo-200 dark:bg-indigo-800 text-[10px] px-1.5 py-0.5 rounded-md text-indigo-700 dark:text-indigo-300">
                    剩余{{ aiRemaining }}次
                </span>
              </button>
          </div>

          <!-- AI Explanation Area -->
          <div v-if="aiExplanation" class="mt-4 bg-gradient-to-br from-indigo-50 to-purple-50 dark:from-indigo-900/10 dark:to-purple-900/10 rounded-xl p-4 border border-indigo-100 dark:border-indigo-900/30 animate-fade-in-up">
              <h4 class="font-bold text-indigo-600 dark:text-indigo-400 text-sm mb-2 flex items-center gap-2">
                  <span>🤖</span> AI 老师的一对一辅导
              </h4>
              <div class="text-sm text-slate-600 dark:text-slate-300 leading-relaxed">
                  <MarkdownRenderer :content="aiExplanation" />
              </div>
          </div>
        </div>
      </div>
    </main>

    <!-- Bottom Nav (Floating) -->
    <nav
      class="fixed bottom-6 left-1/2 -translate-x-1/2 w-[90%] max-w-md bg-white/80 dark:bg-slate-800/80 backdrop-blur-lg border border-white/50 dark:border-slate-700/50 p-2 rounded-[24px] flex gap-2 print:hidden z-30 shadow-2xl shadow-indigo-500/20"
    >
      <button
        @click="prev"
        :disabled="isFirst"
        class="flex-1 py-3.5 rounded-[20px] font-bold text-slate-600 dark:text-slate-300 bg-transparent hover:bg-slate-100 dark:hover:bg-slate-700/50 disabled:opacity-30 active:scale-95 transition-all text-sm flex items-center justify-center gap-2"
      >
        <span>←</span> 上一题
      </button>
      <div class="w-px bg-slate-200 dark:bg-slate-700 my-2"></div>
      <button
        @click="next"
        :disabled="isLast"
        class="flex-1 py-3.5 rounded-[20px] font-bold text-indigo-600 dark:text-indigo-400 bg-indigo-50/50 dark:bg-indigo-900/20 hover:bg-indigo-100 dark:hover:bg-indigo-900/40 disabled:opacity-30 active:scale-95 transition-all text-sm flex items-center justify-center gap-2"
      >
        下一题 <span>→</span>
      </button>
    </nav>

    <!-- Answer Sheet Drawer -->
    <div v-if="showSheet" class="fixed inset-0 z-50 flex justify-end print:hidden">
      <!-- Backdrop -->
      <div
        class="absolute inset-0 bg-slate-900/20 backdrop-blur-[2px] transition-opacity"
        @click="showSheet = false"
      ></div>
      
      <!-- Drawer Content -->
      <div class="relative w-full max-w-md bg-white/95 dark:bg-slate-900/95 backdrop-blur-xl h-full shadow-2xl flex flex-col animate-slide-left border-l border-white/20">
        <div class="p-5 border-b border-slate-100 dark:border-slate-800 flex justify-between items-center">
          <div>
              <h3 class="font-black text-xl text-slate-800 dark:text-slate-100">答题卡</h3>
              <p class="text-xs text-slate-400 mt-1">点击题号快速跳转</p>
          </div>
          <button @click="showSheet = false" class="w-8 h-8 rounded-full bg-slate-100 dark:bg-slate-800 flex items-center justify-center text-slate-500 hover:bg-slate-200 dark:hover:bg-slate-700 transition-colors">
            ✕
          </button>
        </div>

        <!-- Stats Row -->
        <div class="grid grid-cols-4 gap-2 p-4 bg-slate-50/50 dark:bg-slate-800/30 border-b border-slate-100 dark:border-slate-800/50">
           <div class="flex flex-col items-center p-2 rounded-xl bg-white dark:bg-slate-800 border border-slate-100 dark:border-slate-700 shadow-sm">
               <span class="text-xs text-slate-400 font-bold uppercase">Total</span>
               <span class="text-lg font-black text-slate-700 dark:text-slate-200">{{ stats.total }}</span>
           </div>
           <div class="flex flex-col items-center p-2 rounded-xl bg-white dark:bg-slate-800 border border-slate-100 dark:border-slate-700 shadow-sm">
               <span class="text-xs text-emerald-500 font-bold uppercase">Right</span>
               <span class="text-lg font-black text-emerald-500">{{ stats.correct }}</span>
           </div>
           <div class="flex flex-col items-center p-2 rounded-xl bg-white dark:bg-slate-800 border border-slate-100 dark:border-slate-700 shadow-sm">
               <span class="text-xs text-rose-500 font-bold uppercase">Wrong</span>
               <span class="text-lg font-black text-rose-500">{{ stats.wrong }}</span>
           </div>
           <div class="flex flex-col items-center p-2 rounded-xl bg-white dark:bg-slate-800 border border-slate-100 dark:border-slate-700 shadow-sm">
               <span class="text-xs text-indigo-500 font-bold uppercase">Rate</span>
               <span class="text-lg font-black text-indigo-500">{{ stats.rate }}%</span>
           </div>
        </div>

        <!-- Group Selector -->
        <div v-if="sheetGroups.length > 0" class="px-5 pb-2 overflow-x-auto flex gap-2 no-scrollbar shrink-0">
            <button 
                v-for="g in sheetGroups" 
                :key="g.index"
                @click="sheetPage = g.index"
                class="px-3 py-1.5 rounded-lg text-xs font-bold transition-all whitespace-nowrap"
                :class="sheetPage === g.index 
                    ? 'bg-indigo-500 text-white shadow-md shadow-indigo-500/20' 
                    : 'bg-slate-100 dark:bg-slate-800 text-slate-500 dark:text-slate-400 hover:bg-slate-200 dark:hover:bg-slate-700'"
            >
                {{ g.label }}
            </button>
        </div>

        <!-- Grid -->
        <div class="flex-1 overflow-y-auto p-5 pt-2 scrollbar-hide">
          <div class="grid grid-cols-5 gap-3 content-start">
            <button
              v-for="item in visibleSheetItems"
              :key="item.realIndex"
              @click="jumpTo(item.displayIndex)"
              class="aspect-square rounded-2xl flex items-center justify-center text-sm font-bold border-2 transition-all active:scale-90 shadow-sm"
              :class="{
                'bg-emerald-500 border-emerald-500 text-white shadow-emerald-500/30':
                  userAnswers[questions[item.realIndex].id] &&
                  userAnswers[questions[item.realIndex].id].isCorrect,
                'bg-rose-500 border-rose-500 text-white shadow-rose-500/30':
                  userAnswers[questions[item.realIndex].id] &&
                  !userAnswers[questions[item.realIndex].id].isCorrect,
                'bg-white dark:bg-slate-800 border-slate-200 dark:border-slate-700 text-slate-400 hover:border-indigo-400':
                  !userAnswers[questions[item.realIndex].id],
                'ring-4 ring-indigo-500/20 scale-110 z-10':
                  currentIndex === item.displayIndex,
              }"
            >
              {{ item.displayIndex + 1 }}
            </button>
          </div>

        </div>

        <!-- Actions -->
        <div class="p-5 border-t border-slate-100 dark:border-slate-800 bg-white/50 dark:bg-slate-900/50 space-y-3 pb-8">
          <button
            @click="showExportModal = true"
            class="w-full py-3.5 rounded-xl bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 font-bold flex items-center justify-center gap-2 hover:bg-slate-200 dark:hover:bg-slate-700 transition-colors"
          >
            <span>📤</span> 导出/打印
          </button>
          <button
            @click="resetProgress"
            class="w-full py-3.5 rounded-xl bg-rose-50 dark:bg-rose-900/20 text-rose-500 font-bold flex items-center justify-center gap-2 hover:bg-rose-100 dark:hover:bg-rose-900/40 transition-colors"
          >
            <span>🗑️</span> 清空当前进度
          </button>
        </div>
      </div>
    </div>

    <!-- Print Area -->
    <div id="print-area" class="hidden print:block p-8 bg-white text-black font-serif">
      <div class="text-center border-b-2 border-black pb-4 mb-6">
          <h1 class="text-3xl font-bold mb-2">{{ exportConfig.scope === 'wrong' ? '错题集' : '练习卷' }}</h1>
          <p class="text-sm">生成时间：{{ new Date().toLocaleString() }}</p>
      </div>
      
      <div class="columns-2 gap-8">

          <div
            v-for="(q, i) in printQuestions"
            :key="q.id"
            class="mb-6 break-inside-avoid"
          >
            <div class="flex items-baseline gap-1 mb-1 font-bold">
                <span class="text-lg">{{ i + 1 }}.</span>
                <span class="text-xs px-1 py-0.5 border rounded border-black ml-1">{{ q.content.type === 'single' ? '单选' : q.content.type === 'multiple' ? '多选' : '判断' }}</span>
            </div>
            
            <div class="font-medium text-base mb-2 leading-snug">
              {{ q.content.question }}
            </div>

            <div v-if="q.content.options" class="pl-4 mb-2 space-y-0.5 text-sm">
              <div v-for="(val, key) in q.content.options" :key="key">
                <span class="font-bold">{{ key }}.</span> {{ val }}
              </div>
            </div>

            <div v-if="exportConfig.includeAnswer" class="mt-2 p-2 bg-gray-100 border border-gray-300 rounded text-xs">
              <div class="font-bold">正确答案：{{ formatAnswer(q.content.answer, q.content.type) }}</div>
              <div class="mt-1 text-gray-700">
                解析：{{ q.content.analysis || "无" }}
              </div>
            </div>
          </div>
      </div>
      
      <div
        v-if="printQuestions.length === 0"
        class="text-center py-10 italic"
      >
        没有错题需要打印，继续保持！
      </div>
    </div>

    <Toast ref="toast" />
    <ConfirmDialog
      v-model:show="confirmDialog.show"
      :title="confirmDialog.title"
      :content="confirmDialog.content"
      :type="confirmDialog.type"
      @confirm="confirmDialog.onConfirm"
      @cancel="confirmDialog.onCancel"
    />
    
    <!-- Export Modal -->
    <div v-if="showExportModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 print:hidden">
        <div class="absolute inset-0 bg-slate-900/40 backdrop-blur-sm" @click="showExportModal = false"></div>
        <div class="relative w-full max-w-sm bg-white dark:bg-slate-800 rounded-2xl shadow-2xl p-6 space-y-6 animate-fade-in-up">
            <h3 class="text-lg font-bold text-slate-800 dark:text-slate-100 flex items-center gap-2">
                <span>🖨️</span> 导出与打印
            </h3>
            
            <div class="space-y-4">
                <div class="space-y-2">
                    <label class="text-xs font-bold text-slate-500 uppercase">导出内容</label>
                    <div class="grid grid-cols-2 gap-2">
                        <button 
                            @click="exportConfig.scope = 'all'"
                            class="p-3 rounded-xl border text-sm font-bold transition-all"
                            :class="exportConfig.scope === 'all' ? 'border-indigo-500 bg-indigo-50 text-indigo-600' : 'border-slate-200 dark:border-slate-700 text-slate-500'"
                        >全部题目</button>
                        <button 
                            @click="exportConfig.scope = 'wrong'"
                            class="p-3 rounded-xl border text-sm font-bold transition-all"
                            :class="exportConfig.scope === 'wrong' ? 'border-rose-500 bg-rose-50 text-rose-600' : 'border-slate-200 dark:border-slate-700 text-slate-500'"
                        >仅错题</button>
                    </div>
                </div>

                <div class="space-y-2">
                    <label class="text-xs font-bold text-slate-500 uppercase">包含答案</label>
                     <div class="flex items-center gap-2">
                        <button 
                            @click="exportConfig.includeAnswer = !exportConfig.includeAnswer"
                            class="w-10 h-6 rounded-full transition-colors relative"
                            :class="exportConfig.includeAnswer ? 'bg-indigo-500' : 'bg-slate-200 dark:bg-slate-700'"
                        >
                            <span class="absolute top-1 left-1 w-4 h-4 bg-white rounded-full transition-transform" :class="exportConfig.includeAnswer ? 'translate-x-4' : ''"></span>
                        </button>
                        <span class="text-sm text-slate-600 dark:text-slate-300">{{ exportConfig.includeAnswer ? '包含答案与解析' : '仅题目 (测试卷)' }}</span>
                    </div>
                </div>
            </div>

            <div class="pt-2 flex gap-3">
                <button 
                    @click="handleExportWord"
                    class="flex-1 py-3 bg-blue-600 hover:bg-blue-700 text-white rounded-xl font-bold flex items-center justify-center gap-2 shadow-lg shadow-blue-500/30"
                >
                    <span>📄</span> 导出 Word
                </button>
                <button 
                    @click="handlePrint"
                    class="flex-1 py-3 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl font-bold flex items-center justify-center gap-2"
                >
                    <span>🖨️</span> 直接打印
                </button>
            </div>
        </div>
    </div>
  </div>
</template>

<style scoped>
/* Slide Animations */
.animate-fade-in-up {
  animation: fadeInUp 0.4s cubic-bezier(0.16, 1, 0.3, 1);
}
.animate-slide-left {
  animation: slideLeft 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}
.animate-slide-up {
    animation: slideUp 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes fadeInUp {
  from { opacity: 0; transform: translateY(20px) scale(0.98); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}
@keyframes slideLeft {
  from { transform: translateX(100%); opacity: 0; }
  to { transform: translateX(0); opacity: 1; }
}
@keyframes slideUp {
    from { opacity: 0; transform: translateY(10px); }
    to { opacity: 1; transform: translateY(0); }
}

.scrollbar-hide::-webkit-scrollbar {
  display: none;
}
.scrollbar-hide {
  -ms-overflow-style: none;
  scrollbar-width: none;
}

/* Glassmorphism for Nav */
.glass-nav {
  background-color: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border-bottom: 1px solid rgba(229, 231, 235, 0.5);
}
.dark .glass-nav {
  background-color: rgba(15, 23, 42, 0.8);
  border-bottom: 1px solid rgba(30, 41, 59, 0.5);
}

/* Glassmorphism for Card */
.glass-card {
  background-color: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid rgba(229, 231, 235, 0.5);
  border-radius: 1.5rem; /* rounded-2xl */
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05);
}
.dark .glass-card {
  background-color: rgba(30, 41, 59, 0.8);
  border: 1px solid rgba(71, 85, 105, 0.5);
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.2), 0 4px 6px -2px rgba(0, 0, 0, 0.1);
}

@media print {
  body * {
    visibility: hidden;
  }
  #print-area,
  #print-area * {
    visibility: visible;
  }
  #print-area {
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
  }
}
</style>
