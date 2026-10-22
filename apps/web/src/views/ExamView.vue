<script setup>
import { ref, onMounted, onUnmounted, computed, watch, nextTick, reactive } from "vue";
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

// === Settings & Preferences ===
const showSettings = ref(false);
const savedSettings = JSON.parse(localStorage.getItem('examSettings') || '{}');
const autoAdvance = ref(savedSettings.autoAdvance !== undefined ? savedSettings.autoAdvance : true);
const swipeEnabled = ref(savedSettings.swipeEnabled !== undefined ? savedSettings.swipeEnabled : true);
const autoAdvanceDelay = ref(savedSettings.autoAdvanceDelay || 500); // ms
const fontSize = ref(savedSettings.fontSize || 'normal'); // 'small', 'normal', 'large'
const hapticFeedback = ref(savedSettings.hapticFeedback !== undefined ? savedSettings.hapticFeedback : true);

const saveSettings = () => {
  localStorage.setItem('examSettings', JSON.stringify({
    autoAdvance: autoAdvance.value,
    swipeEnabled: swipeEnabled.value,
    autoAdvanceDelay: autoAdvanceDelay.value,
    fontSize: fontSize.value,
    hapticFeedback: hapticFeedback.value
  }));
};
watch([autoAdvance, swipeEnabled, autoAdvanceDelay, fontSize, hapticFeedback], saveSettings);

// Haptic feedback helper
const triggerHaptic = (type = 'light') => {
  if (!hapticFeedback.value || !navigator.vibrate) return;
  if (type === 'light') navigator.vibrate(10);
  if (type === 'heavy') navigator.vibrate([30, 40, 30]);
};

// === Card Transition Direction ===
const slideDirection = ref('right'); // 'left' or 'right'
const cardKey = ref(0);
watch(currentIndex, (newVal, oldVal) => {
  slideDirection.value = newVal > oldVal ? 'right' : 'left';
  cardKey.value++;
});

// === Swipe Gesture ===
const questionArea = ref(null);
let touchStartX = 0;
let touchStartY = 0;
let touchStartTime = 0;
let isSwiping = false;

const onTouchStart = (e) => {
  if (!swipeEnabled.value) return;
  const touch = e.touches[0];
  touchStartX = touch.clientX;
  touchStartY = touch.clientY;
  touchStartTime = Date.now();
  isSwiping = false;
};

const onTouchMove = (e) => {
  if (!swipeEnabled.value) return;
  const touch = e.touches[0];
  const diffX = Math.abs(touch.clientX - touchStartX);
  const diffY = Math.abs(touch.clientY - touchStartY);
  // If horizontal movement is dominant, mark as swipe
  if (diffX > 15 && diffX > diffY * 1.5) {
    isSwiping = true;
  }
};

const onTouchEnd = (e) => {
  if (!swipeEnabled.value || !isSwiping) return;
  const touch = e.changedTouches[0];
  const diffX = touch.clientX - touchStartX;
  const elapsed = Date.now() - touchStartTime;
  const minSwipeDistance = 60;
  const maxSwipeTime = 500;

  if (Math.abs(diffX) > minSwipeDistance && elapsed < maxSwipeTime) {
    if (diffX < 0 && !isLast.value) {
      triggerHaptic('light');
      next(); // Swipe left -> next
    } else if (diffX > 0 && !isFirst.value) {
      triggerHaptic('light');
      prev(); // Swipe right -> prev
    }
  }
  isSwiping = false;
};

// === Keyboard Navigation ===
const handleKeydown = (e) => {
  if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') return;
  if (e.key === 'ArrowRight' || e.key === 'ArrowDown') { e.preventDefault(); next(); }
  if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') { e.preventDefault(); prev(); }
};
onMounted(() => window.addEventListener('keydown', handleKeydown));
onUnmounted(() => window.removeEventListener('keydown', handleKeydown));

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
  () => currentIndex.value === displayList.value.length - 1 && (!hasMore.value || mode.value === 'recommendation'),
);
const isFirst = computed(() => currentIndex.value === 0);

const applyProgress = (progress) => {
      progress.forEach((p) => {
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
};

const saveProgress = async (payload) => {
  try {
    const response = await api.syncProgress(payload);
    const local = userAnswers.value[payload.question_id];
    if (local && typeof response.data.is_correct === 'boolean') local.isCorrect = response.data.is_correct;
    return true;
  } catch (error) {
    const local = userAnswers.value[payload.question_id];
    if (local) local.submitted = false;
    toast.value?.add(error.response?.data?.error || '作答未保存，请稍后重新提交', 'error');
    return false;
  }
};

const PAGE_SIZE = 50;
const currentPage = ref(1);
const hasMore = ref(false);
const loadingMore = ref(false);
const previewOnly = ref(false);
const loadError = ref('');
const readAccessHeaders = (res) => {
  hasMore.value = res.headers['x-question-has-more'] === 'true';
  previewOnly.value = res.headers['x-question-access'] === 'preview';
};
const normalizeApiQuestion = (q) => {
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
             if (val === '正确' || val === '对' || val === '√' || val === 'A' || val === 'TRUE' || val === 'true') cleanAnswer = 'A';
             else if (val === '错误' || val === '错' || val === '×' || val === 'B' || val === 'FALSE' || val === 'false') cleanAnswer = 'B';
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
};

const loadNextPage = async () => {
  if (!hasMore.value || loadingMore.value || mode.value === 'recommendation') return;
  loadingMore.value = true;
  try {
    const response = await api.getQuestions(subjectId, { page: currentPage.value + 1, limit: PAGE_SIZE });
    readAccessHeaders(response);
    const known = new Set(questions.value.map(q => q.id));
    questions.value.push(...response.data.filter(q => !known.has(q.id)).map(normalizeApiQuestion));
    currentPage.value++;
    const progress = await api.getProgress();
    if (Array.isArray(progress.data)) applyProgress(progress.data);
    updateDisplayList(false);
  } catch (error) {
    toast.value?.add(error.response?.data?.error || '加载更多题目失败，请稍后重试', 'error');
  } finally { loadingMore.value = false; }
};

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
    const res = await api.getQuestions(subjectId, { page: 1, limit: PAGE_SIZE });
    if (Array.isArray(res.data)) {
      readAccessHeaders(res);
      questions.value = res.data.map(normalizeApiQuestion);

      // Set Subject Title from first question if available
      if (questions.value.length > 0) {
          subjectTitle.value = questions.value[0].subject_name;
      }
    }



    // 1.5. Check for Recommendation Mode (via query param or just default to normal)
    if (route.query.mode === 'recommendation') {
        mode.value = 'recommendation';
        await loadRecommendations();
    } else if (route.query.mode === 'wrong') {
        mode.value = 'wrong';
        updateDisplayList(false);
    } else {
        // Normal Load
        updateDisplayList(false);
    }

    // 2. Fetch Progress
    const progRes = await api.getProgress();
    if (Array.isArray(progRes.data)) {
      applyProgress(progRes.data);
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
    loadError.value = err.response?.data?.error || "题目加载失败，请稍后重试";
    toast.value?.add(loadError.value, "error");
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
            questions.value = res.data.map(q => ({ ...normalizeApiQuestion(q), _isRecommended: true }));
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

const showMenu = ref(false);

const handleShuffle = () => {
    let unanswered = [];
    let answered = [];

    displayList.value.forEach(idx => {
        const q = questions.value[idx];
        if (userAnswers.value[q.id] && userAnswers.value[q.id].submitted) {
            answered.push(idx);
        } else {
            unanswered.push(idx);
        }
    });

    // Shuffle unanswered
    unanswered = unanswered.sort(() => Math.random() - 0.5);
    // Shuffle answered
    answered = answered.sort(() => Math.random() - 0.5);

    displayList.value = [...unanswered, ...answered];
    currentIndex.value = 0;
    toast.value?.add("已乱序重排，未做题目优先显示", "success");
    showMenu.value = false;
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

  triggerHaptic('light');

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
  await saveProgress({
    question_id: q.id,
    is_correct: isCorrect,
    answer: JSON.stringify([val]),
  });

  if (autoAdvance.value && isCorrect && !isLast.value) {
    setTimeout(() => next(), autoAdvanceDelay.value);
  }
};

const submitMulti = async () => {
  const q = currentQuestion.value;
  const ansData = userAnswers.value[q.id];
  if (!ansData || !ansData.answer || ansData.answer.length === 0) {
    triggerHaptic('heavy');
    return toast.value?.add("请至少选择一项", "warning");
  }

  triggerHaptic('light');

  const userAns = ansData.answer.sort().join("");
  const correctAns = q.content.answer.sort().join("");

  const isCorrect = userAns === correctAns;

  // Update
  userAnswers.value[q.id] = { ...ansData, isCorrect, submitted: true };

  await saveProgress({
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
  saveProgress({
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

    await saveProgress({
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

    await saveProgress({
        question_id: q.id,
        is_correct: allCorrect,
        answer: JSON.stringify(materialAnswers.value)
    });
};

// Navigation
const next = async () => {
  if (currentIndex.value >= displayList.value.length - 1 && hasMore.value) await loadNextPage();
  if (currentIndex.value < displayList.value.length - 1) {
    currentIndex.value++;
    nextTick(() => window.scrollTo({ top: 0, behavior: 'smooth' }));
    if (hasMore.value && displayList.value.length - currentIndex.value < 10) void loadNextPage();
  }
};
const prev = () => {
  if (!isFirst.value) {
    currentIndex.value--;
    nextTick(() => window.scrollTo({ top: 0, behavior: 'smooth' }));
  }
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
    if (val === "A" || val === "正确" || val === "true" || val === "TRUE") return "正确 (A)";
    if (val === "B" || val === "错误" || val === "false" || val === "FALSE") return "错误 (B)";
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
        list = questions.value.filter(q => wrongIds.includes(String(q.id)));
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
        const token = localStorage.getItem('token');
        const baseURL = import.meta.env.VITE_API_BASE_URL || '/api';
        const url = baseURL.replace(/\/$/, '') + '/data/ai-explain';
        const res = await fetch(url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                ...((token) ? { 'Authorization': `Bearer ${token}` } : {})
            },
            body: JSON.stringify({
                question: q.content.question,
                options: q.content.options,
                userAnswer: formatAnswer(ua.answer, q.content.type),
                correctAnswer: formatAnswer(q.content.answer, q.content.type),
                type: q.content.type
            })
        });

        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            if (res.status === 403) {
                 toast.value?.add(data.error || "访问被拒绝", "warning");
                 return;
            }
            throw new Error(data.error || '请求失败');
        }

        const reader = res.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';

        while (true) {
            const { done, value } = await reader.read();
            if (done) break;

            buffer += decoder.decode(value, { stream: true });
            const lines = buffer.split('\n');
            buffer = lines.pop(); // Keep the incomplete line in the buffer

            for (const line of lines) {
                if (line.trim() === '') continue;
                if (line.startsWith('data: ')) {
                    const dataStr = line.slice(6).trim();
                    if (dataStr === '[DONE]') continue;

                    try {
                        const parsed = JSON.parse(dataStr);
                        if (parsed.choices && parsed.choices[0]?.delta?.content) {
                            aiExplanation.value += parsed.choices[0].delta.content;
                        } else if (parsed.remaining !== undefined) {
                            aiRemaining.value = parsed.remaining;
                        }
                    } catch (e) {
                         // Ignore JSON parse errors for incomplete chunks
                    }
                }
            }
        }
    } catch (e) {
        toast.value?.add(e.message || "AI 讲解请求失败", "error");
    } finally {
        aiLoading.value = false;
    }
};
</script>

<template>
  <div class="exam-shell">
    <!-- Progress Bar (Sticky Top) -->
    <div class="exam-progress">
        <div
            class="exam-progress-bar"
            :style="{ width: `${((currentIndex + 1) / (displayList.length || 1)) * 100}%` }"
        ></div>
    </div>

    <!-- Top Bar -->
    <header class="exam-topbar print:hidden">
      <button
        @click="router.back()"
        class="exam-icon-btn"
        aria-label="返回"
      >
        <span>←</span>
      </button>

      <div class="exam-title-wrap">
        <span class="exam-title">
           <span v-if="mode === 'wrong'" class="exam-dot"></span>
           <span v-if="mode === 'recommendation'" class="exam-dot"></span>
           {{ mode === "wrong" ? "错题攻克" : (mode === "recommendation" ? "智能练习" : (subjectTitle || "全真模拟")) }}
        </span>
        <span class="exam-counter">
          <span class="exam-counter-current">{{ currentIndex + 1 }}</span>
          <span class="exam-counter-sep">/</span>
          <span>{{ displayList.length }}</span>
        </span>
      </div>

      <div class="flex gap-1.5 sm:gap-2 relative">
        <button
          @click="showMenu = !showMenu"
          class="exam-icon-btn"
          aria-label="更多操作"
        >
          <span>⋮</span>
        </button>

        <!-- Backdrop for dropdown -->
        <div v-if="showMenu" @click="showMenu = false" class="fixed inset-0 z-40"></div>

        <!-- Dropdown Menu -->
        <div
          v-if="showMenu"
          class="exam-menu"
        >
            <button
                @click="showConfirm('智能练习', '由于前端限制，请返回首页选择科目重新进入来切换到智能练习模式。或我们可以加一个按钮触发。', 'info').then(() => { if(mode !== 'recommendation') { mode = 'recommendation'; loadRecommendations(); } else { mode = 'all'; window.location.reload(); } }); showMenu = false"
                class="px-4 py-3.5 flex items-center gap-3 hover:bg-slate-50 dark:hover:bg-slate-700/50 transition-colors text-left"
            >
                <span class="text-lg">🧠</span>
                <span class="font-bold text-slate-700 dark:text-slate-200 text-sm">{{ mode === 'recommendation' ? '退出智能推荐' : '智能练习' }}</span>
            </button>
            <button
                @click="toggleMode(); showMenu = false"
                class="px-4 py-3.5 flex items-center gap-3 hover:bg-slate-50 dark:hover:bg-slate-700/50 transition-colors text-left"
            >
                <span class="text-lg">⚡</span>
                <span class="font-bold text-slate-700 dark:text-slate-200 text-sm">{{ mode === 'wrong' ? '切换回全部' : '错题攻克' }}</span>
            </button>
            <button
                @click="handleShuffle"
                class="px-4 py-3.5 flex items-center gap-3 hover:bg-slate-50 dark:hover:bg-slate-700/50 transition-colors text-left"
            >
                <span class="text-lg">🔀</span>
                <span class="font-bold text-slate-700 dark:text-slate-200 text-sm">乱序重排(未做优先)</span>
            </button>
            <div class="h-px bg-slate-100 dark:bg-slate-700/50 my-1 mx-2"></div>
            <button
                @click="showExportModal = true; showMenu = false"
                class="px-4 py-3.5 flex items-center gap-3 hover:bg-slate-50 dark:hover:bg-slate-700/50 transition-colors text-left"
            >
                <span class="text-lg">📤</span>
                <span class="font-bold text-slate-700 dark:text-slate-200 text-sm">导出/打印</span>
            </button>
            <button
                @click="showSettings = true; showMenu = false"
                class="px-4 py-3.5 flex items-center gap-3 hover:bg-slate-50 dark:hover:bg-slate-700/50 transition-colors text-left"
            >
                <span class="text-lg">⚙️</span>
                <span class="font-bold text-slate-700 dark:text-slate-200 text-sm">做题设置</span>
            </button>
        </div>
      </div>
    </header>

    <div v-if="previewOnly && !loading" class="exam-hint" role="status">
      当前为免费预览，每科开放前 20 题。升级 Plus 后可继续学习。
    </div>
    <p v-if="loadingMore" class="exam-loading-more" role="status">正在加载下一批题目…</p>
    <!-- Content -->
    <main
      ref="questionArea"
      class="exam-main print:hidden exam-content"
      @touchstart.passive="onTouchStart"
      @touchmove.passive="onTouchMove"
      @touchend.passive="onTouchEnd"
    >
      <div v-if="loading" class="exam-loading">
          <div class="ui-spinner"></div>
          <p>加载试题中…</p>
      </div>

      <div v-else-if="loadError" class="exam-error" role="alert">{{ loadError }}</div>
      <div
        v-else-if="displayList.length === 0"
        class="exam-empty"
      >
        <h3>当前模式下没有题目</h3>
        <p>错题已经清空，或者这个科目还没有练习记录。</p>
        <button
          v-if="mode === 'wrong'"
          @click="toggleMode"
          class="ui-btn ui-btn-primary"
        >
          返回全部题目
        </button>
      </div>

      <div v-else class="exam-body">
        <!-- Swipe Hint (shown once) -->
        <div v-if="swipeEnabled && displayList.length > 1" class="exam-swipe-hint">
          <span>← 上一题</span>
          <span>滑动切换</span>
          <span>下一题 →</span>
        </div>

        <!-- Question Card -->
        <transition :name="'slide-' + slideDirection" mode="out-in">
        <div :key="cardKey" class="exam-card">
          <div class="exam-question-head">
            <div class="exam-meta">
               <span
                  class="exam-type"
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
                <span class="exam-category">
                    {{ currentQuestion.content._category || "未分类" }}
                </span>

                <!-- Difficulty Badge -->
                <span
                    class="exam-difficulty"
                >
                    {{ currentQuestion.content.difficulty == 1 ? '简单' : (currentQuestion.content.difficulty == 2 ? '中等' : '困难') }}
                </span>

                <!-- Tags -->
                <div v-if="currentQuestion.content.tags && currentQuestion.content.tags.length > 0" class="exam-tags">
                    <span
                        v-for="(tag, idx) in currentQuestion.content.tags"
                        :key="idx"
                        class="exam-tag"
                    >
                        #{{ tag }}
                    </span>
                </div>
            </div>

            <h2
              class="exam-question select-text"
              :class="{
                  'is-small': fontSize === 'small',
                  'is-normal': fontSize === 'normal',
                  'is-large': fontSize === 'large'
              }"
            >
              <MarkdownRenderer :content="currentQuestion.content.question" />
            </h2>
          </div>

          <!-- Options Area -->
          <div v-if="currentQuestion.content.options && currentQuestion.content.type !== 'fill' && currentQuestion.content.type !== 'sort' && Object.keys(currentQuestion.content.options).length > 0" class="exam-options">
            <button
              v-for="(optVal, optKey) in currentQuestion.content.options"
              :key="optKey"
              @click="handleAnswer(optKey)"
              :disabled="!!userAnswerData?.submitted"
              class="exam-option"
              :class="{
                'is-selected': !userAnswerData?.submitted && userAnswerData?.answer?.includes(optKey),
                'is-correct': userAnswerData?.submitted && currentQuestion.content.answer.includes(optKey),
                'is-wrong':
                  userAnswerData?.submitted &&
                  userAnswerData?.answer?.includes(optKey) &&
                  !userAnswerData.isCorrect &&
                  !currentQuestion.content.answer.includes(optKey),
                'is-dim':
                  userAnswerData?.submitted &&
                  !currentQuestion.content.answer.includes(optKey) &&
                  !userAnswerData?.answer?.includes(optKey),
              }"
            >
              <!-- Option Letter Circle -->
              <span class="exam-option-key">{{ optKey }}</span>

              <span class="exam-option-text"><MarkdownRenderer :content="String(optVal)" /></span>

              <!-- Result Icon -->
              <span v-if="userAnswerData?.submitted && currentQuestion.content.answer.includes(optKey)" class="exam-option-mark is-correct">✓</span>
              <span v-else-if="userAnswerData?.submitted && userAnswerData?.answer?.includes(optKey) && !userAnswerData.isCorrect" class="exam-option-mark is-wrong">✕</span>
            </button>

            <!-- Multi-select Submit Button -->
            <div
              v-if="currentQuestion.content.type === 'multiple' && !userAnswerData?.submitted"
              class="exam-submit-row"
            >
              <button
                @click="submitMulti"
                class="ui-btn ui-btn-primary ui-btn-lg"
              >
                确认提交
              </button>
            </div>
          </div>

          <!-- Sort Area -->
          <div v-else-if="currentQuestion.content.type === 'sort'" class="exam-sort">
              <draggable
                v-model="sortList"
                item-key="index"
                class="exam-sort-list"
                :disabled="!!userAnswerData?.submitted"
                ghost-class="opacity-50"
              >
                  <template #item="{element, index}">
                      <div class="exam-sort-item">
                          <span class="exam-sort-handle">☰</span>
                          <span class="exam-sort-text">{{ element }}</span>
                      </div>
                  </template>
              </draggable>

              <div v-if="!userAnswerData?.submitted" class="exam-submit-row">
                  <button @click="submitSort" class="ui-btn ui-btn-primary ui-btn-lg">
                      确认排序
                  </button>
              </div>
          </div>

          <!-- Material Area -->
          <div v-else-if="currentQuestion.content.type === 'material'" class="exam-material">
              <!-- Material Content -->
              <div class="exam-material-box">
                  <span class="exam-material-label">
                    阅读材料
                  </span>
                  <div class="exam-material-copy">
                     <MarkdownRenderer :content="currentQuestion.content.question" />
                  </div>
              </div>

              <!-- Sub Questions -->
              <div class="space-y-8">
                  <div v-for="(subQ, idx) in currentQuestion.content.options" :key="idx" class="exam-subquestion">
                       <!-- Sub Question Header -->
                       <div class="flex gap-3 items-start">
                           <span class="exam-subquestion-index">{{ idx + 1 }}</span>
                           <div class="space-y-1">
                               <span class="exam-subquestion-stem">{{ subQ.stem }}</span>
                               <span class="exam-subquestion-type">
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
                      class="ui-input"
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
                                   class="exam-subquestion-choice"
                                   :class="{
                                       'is-selected': materialAnswers[idx] === opt && !userAnswerData?.submitted,

                                       // Result states
                                       'is-correct': userAnswerData?.submitted && subQ.answer === opt,
                                       'is-wrong': userAnswerData?.submitted && materialAnswers[idx] === opt && subQ.answer !== opt,
                                       'is-dim': userAnswerData?.submitted && materialAnswers[idx] !== opt && subQ.answer !== opt
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
                                   class="exam-subquestion-option"
                                   :class="{
                                      'is-selected': !userAnswerData?.submitted && (Array.isArray(materialAnswers[idx]) ? materialAnswers[idx].includes(optKey) : materialAnswers[idx] === optKey),
                                      'is-correct': userAnswerData?.submitted && (Array.isArray(subQ.answer) ? subQ.answer.includes(optKey) : subQ.answer === optKey),
                                      'is-wrong': userAnswerData?.submitted && (Array.isArray(materialAnswers[idx]) ? materialAnswers[idx].includes(optKey) : materialAnswers[idx] === optKey) && !(Array.isArray(subQ.answer) ? subQ.answer.includes(optKey) : subQ.answer === optKey),
                                   }"
                               >
                                   <span class="exam-subquestion-key">
                                       {{ optKey }}
                                   </span>
                                   <span class="exam-subquestion-copy"><MarkdownRenderer :content="String(optContent)" /></span>
                               </button>
                           </div>

                           <!-- Sub Analysis -->
                           <div v-if="userAnswerData?.submitted && subQ.analysis" class="exam-subquestion-analysis">
                               <span>解析：</span> <MarkdownRenderer :content="subQ.analysis || ''" />
                           </div>
                       </div>
                  </div>
              </div>

              <div v-if="!userAnswerData?.submitted" class="exam-submit-row">
                  <button @click="submitMaterial" class="ui-btn ui-btn-primary ui-btn-lg">
                      提交本题
                  </button>
              </div>
          </div>

          <!-- Input Area: Fill in the blank -->
          <div v-else class="exam-fill">
            <div class="relative w-full">
              <input
                v-model="fillInput"
                @keyup.enter="submitFill"
                :disabled="!!userAnswerData"
                type="text"
                placeholder="在此输入答案..."
                class="exam-fill-input"
                :class="{
                  'is-correct': userAnswerData && userAnswerData.isCorrect,
                  'is-wrong': userAnswerData && !userAnswerData.isCorrect,
                }"
              />
              <button
                v-if="!userAnswerData"
                @click="submitFill"
                class="exam-fill-submit"
              >
                提交
              </button>
            </div>
          </div>
        </div>
        </transition>

        <!-- Analysis Card -->
        <div
          v-if="userAnswerData?.submitted"
          class="exam-analysis"
          :class="{ 'is-wrong': !userAnswerData.isCorrect }"
        >
          <div class="exam-analysis-head">
            <div class="exam-analysis-icon" :class="userAnswerData.isCorrect ? 'is-correct' : 'is-wrong'">
                {{ userAnswerData.isCorrect ? '✓' : '✕' }}
            </div>
            <div class="ui-grow">
                 <h3 class="exam-analysis-title">
                     {{ userAnswerData.isCorrect ? '回答正确' : '回答错误' }}
                 </h3>
                 <p class="exam-analysis-answer">
                     正确答案：<span class="font-mono font-bold">{{ formatAnswer(currentQuestion.content.answer, currentQuestion.content.type) }}</span>
                 </p>
            </div>
          </div>

          <div class="exam-analysis-body">
            <span class="exam-analysis-label">解析</span>
            <MarkdownRenderer :content="currentQuestion.content.analysis || '暂无详细解析'" />
          </div>

          <div class="exam-analysis-actions">
              <a
                :href="'https://www.baidu.com/s?wd=' + encodeURIComponent(currentQuestion.content.question)"
                target="_blank"
                class="ui-btn ui-btn-outline ui-btn-sm"
              >
                百度一下
              </a>

              <button
                @click="handleAiExplain"
                 :disabled="aiLoading"
                class="ui-btn ui-btn-ghost ui-btn-sm"
              >
                {{ aiLoading ? 'AI 思考中…' : 'AI 讲解' }}
                <span v-if="aiRemaining !== null" class="ui-badge">
                    剩余{{ aiRemaining }}次
                </span>
              </button>
          </div>

          <!-- AI Explanation Area -->
          <div v-if="aiExplanation" class="exam-ai">
              <h4 class="exam-ai-title">
                  AI 讲解
              </h4>
              <div class="exam-ai-body">
                  <MarkdownRenderer :content="aiExplanation" />
              </div>
          </div>
        </div>
      </div>
    </main>

    <!-- Bottom Nav (Floating) -->
    <nav
      class="exam-nav print:hidden"
    >
      <div class="exam-nav-inner">
        <button
          @click="prev"
          :disabled="isFirst"
          class="exam-nav-btn"
        >
          <span>←</span> 上一题
        </button>
        <button
          @click="showSheet = true"
          class="exam-nav-sheet"
          title="答题卡"
        >
          <span class="exam-nav-sheet-count">{{ stats.answered }}</span>
          <span>答题卡</span>
        </button>
        <button
          @click="next"
          :disabled="isLast"
          class="exam-nav-btn is-primary"
        >
          下一题 <span>→</span>
        </button>
      </div>
    </nav>

    <!-- Answer Sheet Drawer -->
    <div v-if="showSheet" class="fixed inset-0 z-50 flex justify-end print:hidden">
      <!-- Backdrop -->
      <div
        class="absolute inset-0 bg-slate-900/20 backdrop-blur-[2px] transition-opacity"
        @click="showSheet = false"
      ></div>

      <!-- Drawer Content -->
      <div class="exam-sheet">
        <div class="exam-sheet-head">
          <div>
              <h3 class="exam-sheet-title">答题卡</h3>
              <p class="exam-sheet-sub">点击题号快速跳转</p>
          </div>
          <button @click="showSheet = false" class="exam-icon-btn" aria-label="关闭答题卡">
            ✕
          </button>
        </div>

        <!-- Stats Row -->
        <div class="exam-sheet-stats">
           <div class="exam-sheet-stat">
               <span>题目</span>
               <strong>{{ stats.total }}</strong>
           </div>
           <div class="exam-sheet-stat is-correct">
               <span>正确</span>
               <strong>{{ stats.correct }}</strong>
           </div>
           <div class="exam-sheet-stat is-wrong">
               <span>错误</span>
               <strong>{{ stats.wrong }}</strong>
           </div>
           <div class="exam-sheet-stat">
               <span>正确率</span>
               <strong>{{ stats.rate }}%</strong>
           </div>
        </div>

        <!-- Group Selector -->
        <div v-if="sheetGroups.length > 0" class="exam-sheet-groups">
            <button
                v-for="g in sheetGroups"
                :key="g.index"
                @click="sheetPage = g.index"
                class="exam-sheet-group"
                :class="{ 'is-active': sheetPage === g.index }"
            >
                {{ g.label }}
            </button>
        </div>

        <!-- Grid -->
        <div class="exam-sheet-body">
          <div class="exam-sheet-grid">
            <button
              v-for="item in visibleSheetItems"
              :key="item.realIndex"
              @click="jumpTo(item.displayIndex)"
              class="exam-sheet-cell"
              :class="{
                'is-correct':
                  userAnswers[questions[item.realIndex].id] &&
                  userAnswers[questions[item.realIndex].id].isCorrect,
                'is-wrong':
                  userAnswers[questions[item.realIndex].id] &&
                  !userAnswers[questions[item.realIndex].id].isCorrect,
                'is-unanswered':
                  !userAnswers[questions[item.realIndex].id],
                'is-current':
                  currentIndex === item.displayIndex,
              }"
            >
              {{ item.displayIndex + 1 }}
            </button>
          </div>

        </div>

        <!-- Actions -->
        <div class="exam-sheet-actions">
          <button
            @click="showExportModal = true"
            class="ui-btn ui-btn-outline"
          >
            导出 / 打印
          </button>
          <button
            @click="resetProgress"
            class="ui-btn ui-btn-ghost exam-danger"
          >
            清空当前进度
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

    <!-- Settings Panel -->
    <div v-if="showSettings" class="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-0 sm:p-4 print:hidden">
        <div class="absolute inset-0 bg-slate-900/40 backdrop-blur-sm" @click="showSettings = false"></div>
        <div class="relative w-full max-w-md bg-white dark:bg-slate-800 rounded-t-3xl sm:rounded-2xl shadow-2xl animate-slide-up overflow-hidden">
            <!-- Header -->
            <div class="flex items-center justify-between p-5 border-b border-slate-100 dark:border-slate-700/50">
                <div class="flex items-center gap-3">
                    <span class="w-10 h-10 rounded-xl bg-indigo-100 dark:bg-indigo-900/30 flex items-center justify-center text-lg">⚙️</span>
                    <div>
                        <h3 class="font-bold text-slate-800 dark:text-slate-100">做题设置</h3>
                        <p class="text-[11px] text-slate-400">自定义你的做题体验</p>
                    </div>
                </div>
                <button @click="showSettings = false" class="w-8 h-8 rounded-full bg-slate-100 dark:bg-slate-700 flex items-center justify-center text-slate-400 hover:bg-slate-200 dark:hover:bg-slate-600 transition-colors text-sm">✕</button>
            </div>

            <div class="p-5 space-y-5 max-h-[70vh] overflow-y-auto">
                <!-- Auto Advance -->
                <div class="flex items-center justify-between p-4 bg-slate-50 dark:bg-slate-700/30 rounded-2xl">
                    <div class="flex-1 mr-4">
                        <div class="flex items-center gap-2">
                            <span class="text-base">🚀</span>
                            <span class="font-bold text-slate-700 dark:text-slate-200 text-sm">答对自动跳转</span>
                        </div>
                        <p class="text-[11px] text-slate-400 mt-1 ml-7">单选/判断答对后自动切换到下一题</p>
                    </div>
                    <button
                        @click="autoAdvance = !autoAdvance"
                        class="w-12 h-7 rounded-full transition-all duration-300 relative shrink-0"
                        :class="autoAdvance ? 'bg-indigo-500 shadow-md shadow-indigo-500/30' : 'bg-slate-200 dark:bg-slate-600'"
                    >
                        <span class="absolute top-1 left-1 w-5 h-5 bg-white rounded-full transition-transform duration-300 shadow-sm" :class="autoAdvance ? 'translate-x-5' : ''"></span>
                    </button>
                </div>

                <!-- Auto Advance Delay -->
                <div v-if="autoAdvance" class="px-4 py-3 bg-indigo-50/50 dark:bg-indigo-900/10 rounded-2xl border border-indigo-100 dark:border-indigo-900/30 animate-fade-in-up">
                    <div class="flex items-center justify-between mb-2">
                        <span class="text-xs font-bold text-indigo-600 dark:text-indigo-400">跳转延迟</span>
                        <span class="text-xs font-mono font-bold text-indigo-500 bg-indigo-100 dark:bg-indigo-900/30 px-2 py-0.5 rounded-md">{{ autoAdvanceDelay }}ms</span>
                    </div>
                    <input
                        type="range"
                        v-model.number="autoAdvanceDelay"
                        min="200" max="2000" step="100"
                        class="w-full h-1.5 bg-indigo-200 dark:bg-indigo-900/50 rounded-full appearance-none cursor-pointer accent-indigo-500"
                    >
                    <div class="flex justify-between text-[10px] text-indigo-400 mt-1">
                        <span>快 (200ms)</span>
                        <span>慢 (2s)</span>
                    </div>
                </div>

                <!-- Swipe Gesture -->
                <div class="flex items-center justify-between p-4 bg-slate-50 dark:bg-slate-700/30 rounded-2xl">
                    <div class="flex-1 mr-4">
                        <div class="flex items-center gap-2">
                            <span class="text-base">👆</span>
                            <span class="font-bold text-slate-700 dark:text-slate-200 text-sm">滑动切换题目</span>
                        </div>
                        <p class="text-[11px] text-slate-400 mt-1 ml-7">左右滑动手势切换上/下一题</p>
                    </div>
                    <button
                        @click="swipeEnabled = !swipeEnabled"
                        class="w-12 h-7 rounded-full transition-all duration-300 relative shrink-0"
                        :class="swipeEnabled ? 'bg-indigo-500 shadow-md shadow-indigo-500/30' : 'bg-slate-200 dark:bg-slate-600'"
                    >
                        <span class="absolute top-1 left-1 w-5 h-5 bg-white rounded-full transition-transform duration-300 shadow-sm" :class="swipeEnabled ? 'translate-x-5' : ''"></span>
                    </button>
                </div>
                <!-- Haptic Feedback -->
                <div class="flex items-center justify-between p-4 bg-slate-50 dark:bg-slate-700/30 rounded-2xl">
                    <div class="flex-1 mr-4">
                        <div class="flex items-center gap-2">
                            <span class="text-base">📳</span>
                            <span class="font-bold text-slate-700 dark:text-slate-200 text-sm">震动反馈</span>
                        </div>
                        <p class="text-[11px] text-slate-400 mt-1 ml-7">答题和滑动时提供触觉反馈 (仅支持部分设备)</p>
                    </div>
                    <button
                        @click="hapticFeedback = !hapticFeedback"
                        class="w-12 h-7 rounded-full transition-all duration-300 relative shrink-0"
                        :class="hapticFeedback ? 'bg-indigo-500 shadow-md shadow-indigo-500/30' : 'bg-slate-200 dark:bg-slate-600'"
                    >
                        <span class="absolute top-1 left-1 w-5 h-5 bg-white rounded-full transition-transform duration-300 shadow-sm" :class="hapticFeedback ? 'translate-x-5' : ''"></span>
                    </button>
                </div>

                <!-- Font Size -->
                <div class="p-4 bg-slate-50 dark:bg-slate-700/30 rounded-2xl">
                    <div class="flex items-center gap-2 mb-3">
                        <span class="text-base">Aa</span>
                        <span class="font-bold text-slate-700 dark:text-slate-200 text-sm">题目字体大小</span>
                    </div>
                    <div class="flex bg-slate-200/50 dark:bg-slate-800 rounded-xl p-1">
                        <button
                            @click="fontSize = 'small'"
                            class="flex-1 py-1.5 rounded-lg text-xs font-bold transition-all"
                            :class="fontSize === 'small' ? 'bg-white dark:bg-slate-600 text-indigo-600 dark:text-indigo-400 shadow-sm' : 'text-slate-500 hover:text-slate-700 dark:hover:text-slate-300'"
                        >小</button>
                        <button
                            @click="fontSize = 'normal'"
                            class="flex-1 py-1.5 rounded-lg text-sm font-bold transition-all"
                            :class="fontSize === 'normal' ? 'bg-white dark:bg-slate-600 text-indigo-600 dark:text-indigo-400 shadow-sm' : 'text-slate-500 hover:text-slate-700 dark:hover:text-slate-300'"
                        >中</button>
                        <button
                            @click="fontSize = 'large'"
                            class="flex-1 py-1.5 rounded-lg text-base font-bold transition-all"
                            :class="fontSize === 'large' ? 'bg-white dark:bg-slate-600 text-indigo-600 dark:text-indigo-400 shadow-sm' : 'text-slate-500 hover:text-slate-700 dark:hover:text-slate-300'"
                        >大</button>
                    </div>
                </div>
                <!-- Keyboard Hints -->
                <div class="p-4 bg-slate-50 dark:bg-slate-700/30 rounded-2xl">
                    <div class="flex items-center gap-2 mb-3">
                        <span class="text-base">⌨️</span>
                        <span class="font-bold text-slate-700 dark:text-slate-200 text-sm">快捷键</span>
                    </div>
                    <div class="grid grid-cols-2 gap-2 text-xs">
                        <div class="flex items-center gap-2 text-slate-500 dark:text-slate-400">
                            <kbd class="px-2 py-1 bg-white dark:bg-slate-700 rounded-md border border-slate-200 dark:border-slate-600 font-mono text-[10px] shadow-sm">←</kbd>
                            <span>上一题</span>
                        </div>
                        <div class="flex items-center gap-2 text-slate-500 dark:text-slate-400">
                            <kbd class="px-2 py-1 bg-white dark:bg-slate-700 rounded-md border border-slate-200 dark:border-slate-600 font-mono text-[10px] shadow-sm">→</kbd>
                            <span>下一题</span>
                        </div>
                    </div>
                </div>
            </div>

            <!-- iOS Home Indicator Space -->
            <div class="h-safe" style="padding-bottom: 24px; padding-bottom: env(safe-area-inset-bottom, 24px);"></div>
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
                        >当前已加载题目</button>
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
/* === Card Slide Transitions (Swipe) === */
.slide-right-enter-active,
.slide-right-leave-active,
.slide-left-enter-active,
.slide-left-leave-active {
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}
.slide-right-enter-from { opacity: 0; transform: translateX(40px); }
.slide-right-leave-to { opacity: 0; transform: translateX(-40px); }
.slide-left-enter-from { opacity: 0; transform: translateX(-40px); }
.slide-left-leave-to { opacity: 0; transform: translateX(40px); }

/* === Base Animations === */
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

/* === Scrollbar Utilities === */
.scrollbar-hide::-webkit-scrollbar { display: none; }
.scrollbar-hide { -ms-overflow-style: none; scrollbar-width: none; }

/* === Glassmorphism === */
.glass-nav {
  background-color: rgba(255, 255, 255, 0.85);
  backdrop-filter: saturate(180%) blur(16px);
  -webkit-backdrop-filter: saturate(180%) blur(16px);
  border-bottom: 1px solid rgba(229, 231, 235, 0.5);
}
.dark .glass-nav {
  background-color: rgba(15, 23, 42, 0.85);
  border-bottom: 1px solid rgba(30, 41, 59, 0.5);
}

.glass-card {
  background-color: rgba(255, 255, 255, 0.85);
  backdrop-filter: saturate(120%) blur(12px);
  -webkit-backdrop-filter: saturate(120%) blur(12px);
  border: 1px solid rgba(229, 231, 235, 0.5);
  border-radius: 1.25rem;
  box-shadow: 0 8px 24px -4px rgba(0, 0, 0, 0.06), 0 2px 8px -2px rgba(0, 0, 0, 0.04);
}
.dark .glass-card {
  background-color: rgba(30, 41, 59, 0.85);
  border: 1px solid rgba(71, 85, 105, 0.5);
  box-shadow: 0 8px 24px -4px rgba(0, 0, 0, 0.2), 0 2px 8px -2px rgba(0, 0, 0, 0.1);
}

/* === Cross-Device Touch & Scroll === */
.exam-content {
  -webkit-overflow-scrolling: touch; /* iOS smooth scroll */
  overscroll-behavior-x: none; /* Prevent accidental browser back gesture */
  touch-action: pan-y pinch-zoom; /* Allow vertical scroll + pinch, block horizontal for swipe */
}

/* === Range Input Styling === */
input[type="range"] {
  -webkit-appearance: none;
  appearance: none;
  height: 6px;
  border-radius: 999px;
  outline: none;
}
input[type="range"]::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #6366f1;
  cursor: pointer;
  box-shadow: 0 2px 6px rgba(99, 102, 241, 0.4);
  border: 2px solid white;
}
input[type="range"]::-moz-range-thumb {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #6366f1;
  cursor: pointer;
  box-shadow: 0 2px 6px rgba(99, 102, 241, 0.4);
  border: 2px solid white;
}

/* === Mobile Optimizations === */
@supports (padding-bottom: env(safe-area-inset-bottom)) {
  .pb-safe {
    padding-bottom: env(safe-area-inset-bottom, 0px);
  }
}

/* Prevent iOS text size adjustment */
html {
  -webkit-text-size-adjust: 100%;
  text-size-adjust: 100%;
}

/* Prevent pull-to-refresh on Chrome Android */
body {
  overscroll-behavior-y: contain;
}

/* === Print === */
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

/* === Responsive Fine-tuning === */
@media (max-width: 640px) {
  .glass-card {
    border-radius: 1rem;
  }
}

/* Active state haptic-like feedback */
@media (hover: none) and (pointer: coarse) {
  button:active {
    transform: scale(0.97);
    transition: transform 0.1s;
  }
}
</style>
