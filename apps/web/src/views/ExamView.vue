<script setup>
import { ref, onMounted, onUnmounted, computed, watch, nextTick, reactive } from "vue";
import { useRoute, useRouter } from "vue-router";
import { data as api } from "../api";
import { normalizeQuestion } from "../utils/question-normalizer";
import Toast from "../components/ui/Toast.vue";
import ConfirmDialog from "../components/ui/ConfirmDialog.vue";
import LoadingState from "../components/ui/LoadingState.vue";
import MarkdownRenderer from "../components/ui/MarkdownRenderer.vue";
import draggable from "vuedraggable";

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
const subjectTotal = ref(null);
const visibleTotal = computed(() => mode.value === 'all' ? (previewOnly.value ? Math.min(subjectTotal.value ?? 20,20) : subjectTotal.value ?? displayList.value.length) : displayList.value.length);
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
  if (e.key === 'Escape') {
    showMenu.value = false;
    showSettings.value = false;
    showSheet.value = false;    return;
  }
  if (showMenu.value || showSettings.value || showSheet.value || confirmDialog.show) return;
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
    const question = questions.value.find(q => q.id === payload.question_id);
    payload.answer_token = question?.answer_token;
    const response = await api.syncProgress(payload);
    if (response.data.question && question) {
      const revealed = normalizeApiQuestion(response.data.question);
      Object.assign(question.content, { answer: revealed.content.answer, analysis: revealed.content.analysis, options: revealed.content.options });
      question.reviewLoaded = true;
    }
    const local = userAnswers.value[payload.question_id];
    if (local && typeof response.data.is_correct === 'boolean') local.isCorrect = response.data.is_correct;
    if (local) { local.submitted = true; local.pending = false; }
    return true;
  } catch (error) {
    const local = userAnswers.value[payload.question_id];
    if (local) { local.submitted = false; local.pending = false; }
    if (error.response?.data?.code === 'QUESTION_READ_REQUIRED') {
      try { const fresh = await api.getQuestion(payload.question_id); questions.value.find(q => q.id === payload.question_id).answer_token = fresh.data.answer_token; }
      catch { /* The next submission remains blocked until a valid ticket is loaded. */ }
    }
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
          if (Array.isArray(options) && q.type !== 'sort' && q.type !== 'material') {
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
              answer_token: q.answer_token,
              subject_name: q.subject_name,
              content: {
                  question: q.content,
                  options: options, // For material, this is SubQuestions array
                  type: q.type,
                  answer: ['fill','text'].includes(q.type) ? String(cleanAnswer ?? '') : Array.isArray(cleanAnswer) ? cleanAnswer : (cleanAnswer ? cleanAnswer.split('') : []),
                  analysis: q.analysis,
                  difficulty: q.difficulty || 1,
                  tags: q.tags || [],
                  _category: q.type
              }
          };
};

watch([() => currentQuestion.value?.id, () => userAnswerData.value?.submitted], async ([id, submitted]) => {
  const question = currentQuestion.value;
  if (!id || !submitted || question.reviewLoaded || question.content.analysis != null) return;
  try {
    const response = await api.getQuestionReview(id, question.answer_token);
    const revealed = normalizeApiQuestion(response.data);
    Object.assign(question.content, { answer: revealed.content.answer, analysis: revealed.content.analysis, options: revealed.content.options });
    question.reviewLoaded = true;
  } catch (error) { toast.value?.add(error.response?.data?.error || '解析加载失败，请稍后重试', 'error'); }
});

const loadNextPage = async () => {
  if (!hasMore.value || loadingMore.value || mode.value === 'recommendation') return;
  loadingMore.value = true;
  try {
    const response = await api.getQuestions(subjectId, { page: currentPage.value + 1, limit: PAGE_SIZE });
    readAccessHeaders(response);
    const known = new Set(questions.value.map(q => q.id));
    questions.value.push(...response.data.filter(q => !known.has(q.id)).map(normalizeApiQuestion));
    currentPage.value++;
    updateDisplayList(false);
    try {
      const progress = await api.getProgress();
      if (Array.isArray(progress.data)) applyProgress(progress.data);
    } catch { toast.value?.add('题目已加载，历史作答暂未同步，请稍后刷新', 'warning'); }
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
    try {
      const subjectsResponse = await api.getSubjects(null);
      const subject = subjectsResponse.data.find(s => Number(s.id) === Number(subjectId));
      if (subject) subjectTotal.value = Number(subject.question_count);
    } catch { /* Directory failure should not discard loaded practice. */ }
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

const restoreAllQuestions = async () => {
  loading.value = true;
  loadError.value = '';
  try {
    const response = await api.getQuestions(subjectId, { page: 1, limit: PAGE_SIZE });
    if (Array.isArray(response.data)) {
      readAccessHeaders(response);
      questions.value = response.data.map(normalizeApiQuestion);
      currentPage.value = 1;
      mode.value = 'all';
      updateDisplayList(true);
      const progress = await api.getProgress();
      if (Array.isArray(progress.data)) applyProgress(progress.data);
    }
  } catch (error) {
    loadError.value = error.response?.data?.error || '返回全部题目失败，请重试';
  } finally {
    loading.value = false;
  }
};

const toggleSmartPractice = async () => {
  showMenu.value = false;
  if (mode.value === 'recommendation') {
    await restoreAllQuestions();
    return;
  }
  mode.value = 'recommendation';
  await loadRecommendations();
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
  if (userAnswerData.value?.pending) return;
  currentIndex.value = index;
  showSheet.value = false;
};

// Answer Sheet Pagination
const sheetPage = ref(0);
const sheetPageSize = 50;
const nextSheetPage = async () => {
  if (loadingMore.value) return;
  const target = sheetPage.value + 1;
  while (target * sheetPageSize >= displayList.value.length && hasMore.value && mode.value !== 'recommendation') {
    const previous = currentPage.value;
    await loadNextPage();
    if(currentPage.value === previous) break;
  }
  if (target * sheetPageSize < displayList.value.length) sheetPage.value = target;
};

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
  if (userAnswerData.value && (userAnswerData.value.submitted || userAnswerData.value.pending)) return;

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
  userAnswers.value[q.id] = { answer: [val], isCorrect: false, submitted: false, pending: true };
  const saved = await saveProgress({
    question_id: q.id,
    answer: JSON.stringify([val]),
  });
  userAnswers.value[q.id].pending = false;
  userAnswers.value[q.id].submitted = saved;

  if (saved && autoAdvance.value && userAnswers.value[q.id].isCorrect && !isLast.value) {
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

  if (ansData.pending) return;
  userAnswers.value[q.id] = { ...ansData, isCorrect: false, submitted: false, pending: true };

  await saveProgress({
    question_id: q.id,
    answer: JSON.stringify(ansData.answer),
  });
};

const fillInput = ref("");
watch(currentQuestion, () => {
  fillInput.value = "";
});

const submitFill = async () => {
  if (!fillInput.value.trim()) return toast.value?.add("请输入答案", "warning");
  const q = currentQuestion.value;
  if (userAnswers.value[q.id]?.pending) return;

  userAnswers.value[q.id] = {
    answer: fillInput.value,
    isCorrect: false,
    submitted: false,
    pending: true,
  };
  await saveProgress({
    question_id: q.id,
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
    if (userAnswers.value[q.id]?.pending) return;

    userAnswers.value[q.id] = {
        answer: userAns,
        isCorrect: false,
        submitted: false,
        pending: true
    };

    await saveProgress({
        question_id: q.id,
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

    if (userAnswers.value[q.id]?.pending) return;
    userAnswers.value[q.id] = {
        answer: materialAnswers.value,
        isCorrect: false,
        submitted: false,
        pending: true
    };

    await saveProgress({
        question_id: q.id,
        answer: JSON.stringify(materialAnswers.value)
    });
};

// Navigation
const next = async () => {
  if (userAnswerData.value?.pending) return;
  if (currentIndex.value >= displayList.value.length - 1 && hasMore.value) await loadNextPage();
  if (currentIndex.value < displayList.value.length - 1) {
    currentIndex.value++;
    nextTick(() => window.scrollTo({ top: 0, behavior: 'smooth' }));
    if (hasMore.value && displayList.value.length - currentIndex.value < 10) void loadNextPage();
  }
};
const prev = () => {
  if (userAnswerData.value?.pending) return;
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
            :style="{ width: `${Math.min(100, ((currentIndex + 1) / (visibleTotal || 1)) * 100)}%` }"
        ></div>
    </div>

    <div v-if="showMenu" class="exam-menu-backdrop" @click="showMenu = false"></div>

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
          <span>{{ visibleTotal }}</span>
        </span>
      </div>

      <div class="exam-menu-wrap">
        <button
          @click="showMenu = !showMenu"
          class="exam-icon-btn"
          aria-label="练习与工具"
          :aria-expanded="showMenu"
          aria-haspopup="menu"
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><circle cx="12" cy="5" r="1.7"/><circle cx="12" cy="12" r="1.7"/><circle cx="12" cy="19" r="1.7"/></svg>
        </button>

        <!-- Dropdown Menu -->
        <div
          v-if="showMenu"
          class="exam-menu"
          role="menu"
          aria-label="练习与工具"
        >
            <p class="exam-menu-label">练习方式</p>
            <button
                @click="toggleSmartPractice"
                class="exam-menu-item"
                :class="{ 'is-active': mode === 'recommendation' }"
                role="menuitem"
            >
                <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><path d="M9 4a3 3 0 0 0-3 3v1a3 3 0 0 0-1 5.8V16a3 3 0 0 0 4 2.8V20"/><path d="M15 4a3 3 0 0 1 3 3v1a3 3 0 0 1 1 5.8V16a3 3 0 0 1-4 2.8V20"/><path d="M9 4a3 3 0 0 1 6 0"/></svg>
                <span class="exam-menu-item-copy"><strong>{{ mode === 'recommendation' ? '退出智能推荐' : '智能练习' }}</strong><small>{{ mode === 'recommendation' ? '恢复当前科目的全部题目' : '按错题与掌握情况优先推荐' }}</small></span>
                <span v-if="mode === 'recommendation'" class="exam-menu-state">进行中</span>
            </button>
            <button
                @click="toggleMode(); showMenu = false"
                class="exam-menu-item"
                :class="{ 'is-active': mode === 'wrong' }"
                role="menuitem"
            >
                <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><path d="M12 3 3 7.5 12 12l9-4.5L12 3Z"/><path d="m3 12 9 4.5 9-4.5M3 16.5 12 21l9-4.5"/></svg>
                <span class="exam-menu-item-copy"><strong>{{ mode === 'wrong' ? '返回全部题目' : '错题攻克' }}</strong><small>{{ mode === 'wrong' ? '按原顺序继续练习' : '只保留当前科目的错题' }}</small></span>
            </button>
            <button
                @click="handleShuffle"
                class="exam-menu-item"
                role="menuitem"
            >
                <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><path d="M3 6h3c5 0 6 12 11 12h4"/><path d="m18 15 3 3-3 3"/><path d="M3 18h3c2.3 0 3.8-2.5 5.2-5.4"/><path d="M13.1 8.5C14.2 7 15.5 6 17 6h4"/><path d="m18 3 3 3-3 3"/></svg>
                <span class="exam-menu-item-copy"><strong>乱序重排</strong><small>未做题目优先，已做题目排在后面</small></span>
            </button>
            <p class="exam-menu-label">工具</p>
            <button
                @click="showMenu = false; router.push('/export')"
                class="exam-menu-item"
                role="menuitem"
            >
                <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><path d="M12 3v12M8 7l4-4 4 4"/><path d="M5 13v6a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-6"/></svg>
                <span class="exam-menu-item-copy"><strong>题库导出</strong><small>需管理员生成的账户导出密码</small></span>
            </button>
            <button
                @click="showSettings = true; showMenu = false"
                class="exam-menu-item"
                role="menuitem"
            >
                <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .34 1.88l.06.06-2.83 2.83-.06-.06A1.7 1.7 0 0 0 15 19.4a1.7 1.7 0 0 0-1 .6 1.7 1.7 0 0 0-.4 1.1V21h-4v-.09A1.7 1.7 0 0 0 8.6 19.4a1.7 1.7 0 0 0-1.88.34l-.06.06-2.83-2.83.06-.06A1.7 1.7 0 0 0 4.6 15a1.7 1.7 0 0 0-.6-1 1.7 1.7 0 0 0-1.1-.4H3v-4h.09A1.7 1.7 0 0 0 4.6 8.6a1.7 1.7 0 0 0-.34-1.88l-.06-.06 2.83-2.83.06.06A1.7 1.7 0 0 0 9 4.6a1.7 1.7 0 0 0 1-.6 1.7 1.7 0 0 0 .4-1.1V3h4v.09A1.7 1.7 0 0 0 15.4 4.6a1.7 1.7 0 0 0 1.88-.34l.06-.06 2.83 2.83-.06.06A1.7 1.7 0 0 0 19.4 9c.1.36.32.7.6 1 .3.26.7.4 1.1.4h.09v4h-.09a1.7 1.7 0 0 0-1.7.6Z"/></svg>
                <span class="exam-menu-item-copy"><strong>做题设置</strong><small>自动跳转、滑动、字号与反馈</small></span>
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
      <LoadingState v-if="loading" label="加载试题中…" />

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
          <div v-if="currentQuestion.content.options && !['fill','text','sort','material'].includes(currentQuestion.content.type) && Object.keys(currentQuestion.content.options).length > 0" class="exam-options">
            <button
              v-for="(optVal, optKey) in currentQuestion.content.options"
              :key="optKey"
              @click="handleAnswer(optKey)"
              :disabled="!!userAnswerData?.submitted || !!userAnswerData?.pending"
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
    <div v-if="showSheet" class="exam-drawer-backdrop print:hidden">
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
               <strong>{{ visibleTotal }}</strong>
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
        <div class="exam-sheet-pagination">
          <button class="ui-btn ui-btn-ghost ui-btn-sm" :disabled="sheetPage === 0 || loadingMore" @click="sheetPage--">上一页</button>
          <span>第 {{ sheetPage + 1 }} 页 · 每页 50 题</span>
          <button class="ui-btn ui-btn-ghost ui-btn-sm" :disabled="loadingMore || ((sheetPage + 1) * sheetPageSize >= displayList.length && !hasMore)" @click="nextSheetPage">{{ loadingMore ? '加载中…' : '下一页' }}</button>
        </div>
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
            @click="showSheet = false; router.push('/export')"
            class="ui-btn ui-btn-outline"
          >
            题库导出
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

    <!-- Settings Panel -->
    <div v-if="showSettings" class="exam-modal-backdrop print:hidden" @click.self="showSettings = false">
      <section class="exam-settings-panel" role="dialog" aria-modal="true" aria-label="做题设置">
        <header class="exam-panel-head">
          <div><h2>做题设置</h2><p>调整自动跳转、手势和阅读字号。</p></div>
          <button class="exam-panel-close" type="button" aria-label="关闭做题设置" @click="showSettings = false">×</button>
        </header>
        <div class="exam-settings-body">
          <div class="exam-setting-row">
            <div class="exam-setting-copy"><strong>答对自动跳转</strong><p>单选和判断题答对后切换到下一题。</p></div>
            <button class="exam-toggle" type="button" :class="{ 'is-on': autoAdvance }" :aria-pressed="autoAdvance" @click="autoAdvance = !autoAdvance"><span></span></button>
          </div>
          <div v-if="autoAdvance" class="exam-setting-detail">
            <div class="exam-detail-line"><span>跳转延迟</span><strong>{{ autoAdvanceDelay }}ms</strong></div>
            <input class="exam-range" type="range" v-model.number="autoAdvanceDelay" min="200" max="2000" step="100" />
            <div class="exam-range-labels"><span>快 200ms</span><span>慢 2s</span></div>
          </div>
          <div class="exam-setting-row">
            <div class="exam-setting-copy"><strong>滑动切换题目</strong><p>左右滑动切换上一题和下一题。</p></div>
            <button class="exam-toggle" type="button" :class="{ 'is-on': swipeEnabled }" :aria-pressed="swipeEnabled" @click="swipeEnabled = !swipeEnabled"><span></span></button>
          </div>
          <div class="exam-setting-row">
            <div class="exam-setting-copy"><strong>震动反馈</strong><p>答题和滑动时提供触觉反馈，仅支持部分设备。</p></div>
            <button class="exam-toggle" type="button" :class="{ 'is-on': hapticFeedback }" :aria-pressed="hapticFeedback" @click="hapticFeedback = !hapticFeedback"><span></span></button>
          </div>
          <div class="exam-setting-block">
            <span class="exam-setting-label">题目字体大小</span>
            <div class="exam-segmented">
              <button type="button" :class="{ 'is-active': fontSize === 'small' }" @click="fontSize = 'small'">小</button>
              <button type="button" :class="{ 'is-active': fontSize === 'normal' }" @click="fontSize = 'normal'">中</button>
              <button type="button" :class="{ 'is-active': fontSize === 'large' }" @click="fontSize = 'large'">大</button>
            </div>
          </div>
          <div class="exam-setting-block">
            <span class="exam-setting-label">键盘快捷键</span>
            <div class="exam-keyboard-grid"><span><kbd>←</kbd> 上一题</span><span><kbd>→</kbd> 下一题</span></div>
          </div>
        </div>
      </section>
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

/* Neutral exam settings panel */
.exam-drawer-backdrop { position: fixed; inset: 0; z-index: 50; display: flex; justify-content: flex-end; }
.exam-modal-backdrop { position: fixed; inset: 0; z-index: 60; display: flex; align-items: center; justify-content: center; padding: 16px; background: rgba(15,23,42,.38); backdrop-filter: blur(3px); }
.exam-settings-panel { width: min(460px, 100%); max-height: 88vh; display: flex; flex-direction: column; overflow: hidden; border: 1px solid var(--border); border-radius: 14px; background: var(--bg-card); box-shadow: 0 24px 60px rgba(15,23,42,.18); }
.exam-panel-head { flex: 0 0 auto; display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; padding: 16px 20px; border-bottom: 1px solid var(--border); }
.exam-panel-head h2 { margin: 0; color: var(--text-main); font-size: 15px; font-weight: 650; }
.exam-panel-head p { margin-top: 3px; color: var(--text-quote); font-size: 10px; }
.exam-panel-close { width: 30px; height: 30px; flex: 0 0 30px; display: grid; place-items: center; border-radius: 7px; color: var(--text-quote); font-size: 18px; }
.exam-panel-close:hover { background: var(--bg-card-highlight); color: var(--text-main); }
.exam-settings-body { flex: 1 1 auto; min-height: 0; overflow-y: auto; padding: 0 20px 18px; }
.exam-setting-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 16px 0; border-bottom: 1px solid var(--border); }
.exam-setting-copy { min-width: 0; }
.exam-setting-copy strong { display: block; color: var(--text-main); font-size: 13px; font-weight: 550; }
.exam-setting-copy p { margin-top: 4px; color: var(--text-quote); font-size: 10px; line-height: 1.55; }
.exam-toggle { position: relative; width: 42px; height: 24px; flex: 0 0 42px; border-radius: 999px; background: var(--border-strong); transition: background .16s ease; }
.exam-toggle span { position: absolute; top: 3px; left: 3px; width: 18px; height: 18px; border-radius: 50%; background: var(--bg-card); box-shadow: 0 1px 2px rgba(15,23,42,.14); transition: transform .16s ease; }
.exam-toggle.is-on { background: var(--primary); }
.exam-toggle.is-on span { transform: translateX(18px); }
.exam-setting-detail { padding: 12px 0 15px; border-bottom: 1px solid var(--border); }
.exam-detail-line { display: flex; align-items: center; justify-content: space-between; gap: 12px; color: var(--text-sub); font-size: 11px; }
.exam-detail-line strong { color: var(--text-main); font-size: 11px; font-weight: 550; }
.exam-range { width: 100%; margin-top: 12px; accent-color: var(--primary); }
.exam-range-labels { display: flex; justify-content: space-between; margin-top: 5px; color: var(--text-quote); font-size: 9px; }
.exam-setting-block { padding: 16px 0; border-bottom: 1px solid var(--border); }
.exam-setting-block:last-child { border-bottom: 0; padding-bottom: 4px; }
.exam-setting-label { display: block; margin-bottom: 9px; color: var(--text-quote); font-size: 10px; font-weight: 600; letter-spacing: .08em; }
.exam-segmented { display: flex; gap: 2px; padding: 2px; border: 1px solid var(--border); border-radius: 8px; background: var(--bg-card-highlight); }
.exam-segmented button { flex: 1; min-height: 32px; border-radius: 6px; color: var(--text-sub); font-size: 12px; }
.exam-segmented button.is-active { background: var(--bg-card); color: var(--text-main); font-weight: 600; box-shadow: var(--shadow); }
.exam-keyboard-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; color: var(--text-sub); font-size: 11px; }
.exam-keyboard-grid span { display: flex; align-items: center; gap: 6px; }
.exam-keyboard-grid kbd { min-width: 24px; padding: 3px 5px; border: 1px solid var(--border); border-radius: 5px; background: var(--bg-card-highlight); color: var(--text-main); font-family: ui-monospace, monospace; font-size: 10px; text-align: center; }
@media (max-width: 600px) {
  .exam-modal-backdrop { align-items: flex-end; padding: 0; }
  .exam-settings-panel { width: 100%; max-height: 92vh; border-radius: 16px 16px 0 0; }
}
</style>
