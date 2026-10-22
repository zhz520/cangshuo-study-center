package cn.zhzgo.study.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.zhzgo.study.data.Question
import cn.zhzgo.study.network.RetrofitClient
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import cn.zhzgo.study.data.StudyPreferences
import cn.zhzgo.study.data.StudySession
import kotlinx.coroutines.withContext

data class QuestionUiState(
    val questions: List<Question> = emptyList(),
    val currentIndex: Int = 0,
    val userAnswers: Map<Int, Any> = emptyMap(),
    val submittedQuestions: Set<Int> = emptySet(),
    val aiExplanation: String? = null,
    val isLoadingAi: Boolean = false,
    val aiRemaining: Int? = null,
    val favoriteQuestionIds: Set<Int> = emptySet(),
    val isLoadingFavorites: Boolean = true,
    val updatingFavorites: Set<Int> = emptySet(),
    val isLoadingMore: Boolean = false,
    val isLastPage: Boolean = false,
    val parsedOptionsMap: Map<Int, Map<String, String>> = emptyMap(),
    val isInitialLoading: Boolean = true,
    val isMistakesMode: Boolean = false,
    val isShuffleMode: Boolean = false,
    val currentScore: Int = 0,
    val errorMessage: String? = null,
    val previewOnly: Boolean = false,
    val totalCount: Int? = null,
    val pageOffset: Int = 0,
    val immediateFeedback: Boolean = true,
    val pendingSubmissionIds: Set<Int> = emptySet(),
    val maxScore: Int = 0
)

class QuestionViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val PAGE_SIZE = 50
    }

    private val apiService = RetrofitClient.create(application)

    private val _uiState = MutableStateFlow(QuestionUiState())
    val uiState = _uiState.asStateFlow()

    private val studyPreferences = StudyPreferences(application)
    private var aiJob: Job? = null
    private var firstPage = 1
    private var selectedReviewIds: List<Int> = emptyList()
    private var currentPage = 1
    private var currentSubjectId = 0
    private var isFavoritesMode = false
    private var isMistakesMode = false
    private var originalQuestions: List<Question> = emptyList()
    private var requestIdentity: List<Any>? = null
    private var initialLoad: Job? = null
    private var pageLoad: Job? = null
    private var accessRefresh: Job? = null

    private suspend fun parseOptionsAsync(options: Any?): Map<String, String> = withContext(Dispatchers.Default) {
        if (options == null) return@withContext emptyMap()
        try {
            if (options is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                options as Map<String, String>
            } else if (options is List<*>) {
                val map = mutableMapOf<String, String>()
                options.forEachIndexed { index, item ->
                    if (item is Map<*, *>) {
                        val content = item["content"]?.toString() ?: item["text"]?.toString() ?: ""
                        val label = item["label"]?.toString() ?: (Char('A'.code + index)).toString()
                        if (content.isNotBlank()) map[label] = content
                    } else {
                        val str = item.toString()
                        if (str.isNotBlank()) map[(Char('A'.code + index)).toString()] = str
                    }
                }
                map
            } else if (options is String) {
                try {
                    val mapType = object : TypeToken<Map<String, String>>() {}.type
                    Gson().fromJson<Map<String, String>>(options, mapType)
                } catch (e: Exception) {
                    try {
                        val listObjType = object : TypeToken<List<Map<String, Any>>>() {}.type
                        val list: List<Map<String, Any>> = Gson().fromJson(options, listObjType)
                        val map = mutableMapOf<String, String>()
                        list.forEachIndexed { index, item ->
                            val content = item["content"]?.toString() ?: item["text"]?.toString() ?: ""
                            val label = item["label"]?.toString() ?: (Char('A'.code + index)).toString()
                            if (content.isNotBlank()) map[label] = content
                        }
                        map
                    } catch (e2: Exception) {
                        try {
                            val listType = object : TypeToken<List<String>>() {}.type
                            val list: List<String> = Gson().fromJson(options, listType)
                            val map = mutableMapOf<String, String>()
                            list.forEachIndexed { index, item ->
                                map[(Char('A'.code + index)).toString()] = item
                            }
                            map
                        } catch (e3: Exception) {
                            emptyMap()
                        }
                    }
                }
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private suspend fun buildParsedOptionsMap(questions: List<Question>): Map<Int, Map<String, String>> = withContext(Dispatchers.Default) {
        val map = mutableMapOf<Int, Map<String, String>>()
        for (q in questions) {
            if (q.type == "single" || q.type == "multiple") {
                map[q.id] = parseOptionsAsync(q.options)
            }
        }
        map
    }

    fun loadQuestions(subjectId: Int, isFavorites: Boolean = false, isMistakes: Boolean = false, questionId: Int = 0, reviewIds: List<Int> = emptyList(), force: Boolean = false) {
        val identity = listOf(subjectId, isFavorites, isMistakes, questionId, reviewIds)
        if (!force && identity == requestIdentity && (initialLoad?.isActive == true || _uiState.value.questions.isNotEmpty())) return
        requestIdentity = identity
        initialLoad?.cancel()
        pageLoad?.cancel()
        aiJob?.cancel()
        selectedReviewIds = cn.zhzgo.study.data.PracticeWorkflow.reviewQueue(reviewIds)
        currentSubjectId = subjectId
        isFavoritesMode = isFavorites
        isMistakesMode = isMistakes
        currentPage = 1
        _uiState.value = QuestionUiState(isMistakesMode = isMistakes)

        initialLoad = viewModelScope.launch {
            try {
                val memory = studyPreferences.memory.first()
                val session = memory.session?.takeIf { !isFavorites && !isMistakes && subjectId > 0 && it.subjectId == subjectId }
                firstPage = if (session != null) cn.zhzgo.study.data.PracticeWorkflow.pageForIndex(session.index, PAGE_SIZE) else 1
                currentPage = firstPage
                val questionsDeferred = async {
                    if(selectedReviewIds.isNotEmpty()) selectedReviewIds.map { apiService.getQuestion(it) }
                    else fetchQuestionPage(firstPage, questionId)
                }
                val totalDeferred = async {
                    if(subjectId > 0 && !isMistakes && !isFavorites) {
                        try { apiService.getSubjects(null).firstOrNull { it.id==subjectId }?.question_count }
                        catch(e:CancellationException) { throw e }
                        catch(e:Exception) { null }
                    } else null
                }
                val progressDeferred = async {
                    try { apiService.getProgress() } catch (e: Exception) { emptyList<cn.zhzgo.study.data.ProgressResponse>() }
                }
                var result = questionsDeferred.await()
                if(result.isEmpty() && firstPage > 1) {
                    firstPage = 1; currentPage = 1
                    result = fetchQuestionPage(1, questionId)
                }
                val progressList = progressDeferred.await()
                val totalCount = totalDeferred.await()

                val parsedMap = buildParsedOptionsMap(result)

                val userAnsMap = mutableMapOf<Int, Any>()
                val subSet = mutableSetOf<Int>()

                if (!isMistakesMode) {
                    progressList.forEach { p ->
                        val matchCriteria = if (isFavoritesMode) true else p.subject_id == subjectId
                        if (matchCriteria) {
                            val q = result.find { it.id == p.question_id }
                            if (q != null) {
                                subSet.add(p.question_id)
                                if (q.type == "multiple") {
                                    userAnsMap[p.question_id] = normalizeAnswer(q.type, p.answer).map { it.toString() }.toSet()
                                } else {
                                    userAnsMap[p.question_id] = if (q.type in listOf("single", "judge")) normalizeAnswer(q.type, p.answer) else p.answer
                                }
                            }
                        }
                    }
                }

                if(!isMistakesMode) result.filter { it.id !in subSet }.forEach { q ->
                    memory.drafts[q.id]?.let { raw -> userAnsMap[q.id] = cn.zhzgo.study.data.PracticeWorkflow.restoreDraft(q.type,raw) }
                }
                var lastUnansweredIndex = 0
                for (i in result.indices) {
                    if (!subSet.contains(result[i].id)) {
                        lastUnansweredIndex = i
                        break
                    }
                }

                session?.let { saved -> result.indexOfFirst { it.id == saved.questionId }.takeIf { it >= 0 }?.let { lastUnansweredIndex = it } }
                val maxScore = calculateMaxScore(result)
                val currentScore = calculateCurrentScore(result, userAnsMap, subSet)

                originalQuestions = result

                _uiState.update { currentState ->
                    currentState.copy(
                        questions = result,
                        totalCount = if(currentState.previewOnly) totalCount?.coerceAtMost(20) else totalCount,
                        pageOffset = (firstPage - 1) * PAGE_SIZE,
                        isLastPage = selectedReviewIds.isNotEmpty() || result.size < PAGE_SIZE,
                        currentIndex = lastUnansweredIndex,
                        userAnswers = userAnsMap,
                        submittedQuestions = subSet,
                        parsedOptionsMap = parsedMap,
                        isInitialLoading = false,
                        maxScore = maxScore,
                        currentScore = currentScore
                    )
                }

                if (_uiState.value.favoriteQuestionIds.isEmpty()) loadFavorites()
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) {
                _uiState.update { it.copy(questions = emptyList(), isInitialLoading = false, errorMessage = requestError(e)) }
            }
        }
    }

    // Returning from membership keeps the question, drafts and timer; authorization stays server-side.
    fun refreshAccess() {
        if (!_uiState.value.previewOnly || _uiState.value.isInitialLoading || accessRefresh?.isActive == true) return
        accessRefresh = viewModelScope.launch {
            try {
                val user = apiService.getUserProfile("me").user
                val active = user.role == "admin" || cn.zhzgo.study.ui.components.isUserPlus(user.subscription_end_date)
                if (active && currentSubjectId > 0 && !isMistakesMode && !isFavoritesMode) {
                    // Preview pages contain 20 items. Fetch the rest of this same 50-item page
                    // before page 2, otherwise upgrading skips questions 21 through 50.
                    val response = apiService.getQuestionPage(currentSubjectId, firstPage, PAGE_SIZE)
                    if (!response.isSuccessful) throw retrofit2.HttpException(response)
                    val page = response.body().orEmpty()
                    val parsed = buildParsedOptionsMap(page)
                    val added = page.filter { q -> _uiState.value.questions.none { it.id == q.id } }
                    originalQuestions = originalQuestions + added
                    _uiState.update { current ->
                        val all = current.questions + added
                        current.copy(questions = all, parsedOptionsMap = current.parsedOptionsMap + parsed,
                            previewOnly = response.headers()["X-Question-Access"] == "preview",
                            totalCount = null, isLastPage = page.size < PAGE_SIZE,
                            maxScore = calculateMaxScore(all))
                    }
                }
            } catch (e: CancellationException) { throw e }
              catch (_: Exception) { /* The existing readable practice remains available. */ }
        }
    }

    private fun loadFavorites() {
        _uiState.update { it.copy(isLoadingFavorites=true) }
        viewModelScope.launch {
            try {
                val ids = apiService.getFavoriteQuestionIds()
                _uiState.update { it.copy(favoriteQuestionIds = ids.toSet()) }
            } catch(e: CancellationException) { throw e }
              catch(e: Exception) { _uiState.update { it.copy(errorMessage="收藏状态加载失败，请稍后重试") } }
              finally { _uiState.update { it.copy(isLoadingFavorites=false) } }
        }
    }

    fun toggleFavorite(questionId: Int) {
        if(_uiState.value.isLoadingFavorites || questionId in _uiState.value.updatingFavorites) return
        val current = _uiState.value.favoriteQuestionIds
        val isFavorite = current.contains(questionId)

        _uiState.update { it.copy(updatingFavorites=it.updatingFavorites+questionId, favoriteQuestionIds = if (isFavorite) current - questionId else current + questionId) }

        viewModelScope.launch {
             try {
                 if (isFavorite) {
                     apiService.removeFavoriteByQuestionId(questionId)
                 } else {
                     apiService.addFavorite(cn.zhzgo.study.data.FavoriteAddRequest(questionId))
                 }
             } catch (e: Exception) {
                 _uiState.update { it.copy(favoriteQuestionIds = if(isFavorite) it.favoriteQuestionIds+questionId else it.favoriteQuestionIds-questionId,errorMessage=requestError(e)) }
             } finally { _uiState.update { it.copy(updatingFavorites=it.updatingFavorites-questionId) } }
        }
    }

    fun selectSingleOption(questionId: Int, option: String) {
        if (isSubmitted(questionId)) return
        _uiState.update { it.copy(userAnswers = it.userAnswers + (questionId to option)) }
        val question = _uiState.value.questions.find { it.id == questionId }
        saveDraft(questionId)
        if(_uiState.value.immediateFeedback) question?.let { submitQuestion(it) }
    }

    fun selectJudgeOption(questionId: Int, option: String) {
        if (isSubmitted(questionId)) return
        _uiState.update { it.copy(userAnswers = it.userAnswers + (questionId to option)) }
        val question = _uiState.value.questions.find { it.id == questionId }
        saveDraft(questionId)
        if(_uiState.value.immediateFeedback) question?.let { submitQuestion(it) }
    }

    fun toggleMultiOption(questionId: Int, option: String) {
        if (isSubmitted(questionId)) return
        val currentAnswers = _uiState.value.userAnswers
        val existing = currentAnswers[questionId] as? Set<String> ?: emptySet()
        val newSet = if (existing.contains(option)) existing - option else existing + option
        _uiState.update { it.copy(userAnswers = currentAnswers + (questionId to newSet)) }
        saveDraft(questionId)
    }

    fun enterFillBlank(questionId: Int, text: String) {
        if (isSubmitted(questionId)) return
        _uiState.update { it.copy(userAnswers = it.userAnswers + (questionId to text)) }
        saveDraft(questionId)
    }

    fun toggleShuffleMode() {
        aiJob?.cancel()
        _uiState.update { state ->
            val newIsShuffle = !state.isShuffleMode
            val newQuestions = if (newIsShuffle) {
                state.questions.shuffled()
            } else {
                originalQuestions
            }

            // Find first unanswered question in the new list
            var nextIndex = 0
            for (i in newQuestions.indices) {
                if (!state.submittedQuestions.contains(newQuestions[i].id)) {
                    nextIndex = i
                    break
                }
            }

            state.copy(
                isShuffleMode = newIsShuffle,
                aiExplanation = null,
                isLoadingAi = false,
                questions = newQuestions,
                currentIndex = nextIndex
            )
        }
    }

    fun submitCurrentQuestion() {
        val state = _uiState.value
        val currentQ = state.questions.getOrNull(state.currentIndex) ?: return
        submitQuestion(currentQ)
    }

    fun normalizeAnswer(questionType: String, answer: String?): String {
        var cleanAnswer = answer ?: ""
        try {
            if (cleanAnswer.startsWith("[") && cleanAnswer.endsWith("]")) {
                val listType = object : TypeToken<List<String>>() {}.type
                val list: List<String> = Gson().fromJson(cleanAnswer, listType)
                cleanAnswer = list.joinToString("")
            }
        } catch (e: Exception) {}

        if (questionType == "judge") {
            val upper = cleanAnswer.uppercase()
            if (cleanAnswer == "正确" || cleanAnswer == "对" || cleanAnswer == "√" || upper == "A" || upper == "TRUE") return "A"
            if (cleanAnswer == "错误" || cleanAnswer == "错" || cleanAnswer == "×" || upper == "B" || upper == "FALSE") return "B"
        }

        if (questionType != "fill" && questionType != "text" && questionType != "sort") {
            cleanAnswer = cleanAnswer.replace(Regex("[^A-Za-z]"), "").uppercase().toCharArray().sorted().joinToString("")
        }
        return cleanAnswer
    }

    private fun calculateMaxScore(questions: List<Question>): Int {
        return questions.sumOf { q ->
            when (q.type) {
                "single" -> 3
                "multiple" -> 4
                "judge" -> 2
                else -> 0
            }
        }
    }

    private fun calculateCurrentScore(questions: List<Question>, userAnswers: Map<Int, Any>, submittedQuestions: Set<Int>): Int {
        var score = 0
        questions.forEach { q ->
            if (submittedQuestions.contains(q.id)) {
                val userAnswerRaw = userAnswers[q.id]
                val userAnswerStr = formatUserAnswer(userAnswerRaw)

                var normalizedUser = userAnswerStr
                if (q.type != "fill" && q.type != "text" && q.type != "sort") {
                    normalizedUser = normalizedUser.replace(Regex("[^A-Za-z]"), "").uppercase().toCharArray().sorted().joinToString("")
                }
                val normalizedCorrect = normalizeAnswer(q.type, q.answer)
                if (normalizedUser.equals(normalizedCorrect, ignoreCase = true)) {
                    score += when (q.type) {
                        "single" -> 3
                        "multiple" -> 4
                        "judge" -> 2
                        else -> 0
                    }
                }
            }
        }
        return score
    }

    fun getCorrectCount(): Int {
        var count = 0
        _uiState.value.questions.forEach { q ->
            if (isSubmitted(q.id)) {
                val userAnswerRaw = _uiState.value.userAnswers[q.id]
                val userAnswerStr = formatUserAnswer(userAnswerRaw)
                var normalizedUser = userAnswerStr
                if (q.type != "fill" && q.type != "text" && q.type != "sort") {
                    normalizedUser = normalizedUser.replace(Regex("[^A-Za-z]"), "").uppercase().toCharArray().sorted().joinToString("")
                }
                val normalizedCorrect = normalizeAnswer(q.type, q.answer)
                if (normalizedUser.equals(normalizedCorrect, ignoreCase = true)) {
                    count++
                }
            }
        }
        return count
    }

    fun getIncorrectCount(): Int {
        var count = 0
        _uiState.value.questions.forEach { q ->
            if (isSubmitted(q.id)) {
                val userAnswerRaw = _uiState.value.userAnswers[q.id]
                val userAnswerStr = formatUserAnswer(userAnswerRaw)
                var normalizedUser = userAnswerStr
                if (q.type != "fill" && q.type != "text" && q.type != "sort") {
                    normalizedUser = normalizedUser.replace(Regex("[^A-Za-z]"), "").uppercase().toCharArray().sorted().joinToString("")
                }
                val normalizedCorrect = normalizeAnswer(q.type, q.answer)
                if (!normalizedUser.equals(normalizedCorrect, ignoreCase = true)) {
                    count++
                }
            }
        }
        return count
    }

    private fun submitQuestion(question: Question) {
        if (isSubmitted(question.id) || question.id in _uiState.value.pendingSubmissionIds) return

        val userAnswerRaw = _uiState.value.userAnswers[question.id]
        val userAnswerStr = formatUserAnswer(userAnswerRaw)

        var normalizedUser = userAnswerStr
        if (question.type != "fill" && question.type != "text" && question.type != "sort") {
            normalizedUser = normalizedUser.replace(Regex("[^A-Za-z]"), "").uppercase().toCharArray().sorted().joinToString("")
        }
        val normalizedCorrect = normalizeAnswer(question.type, question.answer)
        val isCorrect = normalizedUser.equals(normalizedCorrect, ignoreCase = true)

        _uiState.update { it.copy(
            errorMessage = null,
            pendingSubmissionIds = if(isMistakesMode) it.pendingSubmissionIds else it.pendingSubmissionIds + question.id,
            submittedQuestions = it.submittedQuestions + question.id,
            currentScore = if (isCorrect) it.currentScore + when(question.type) {
                "single" -> 3
                "multiple" -> 4
                "judge" -> 2
                else -> 0
            } else it.currentScore
        ) }

        // In mistakes mode, don't sync to server - keep original first-attempt record intact
        // This preserves leaderboard integrity
        if(isMistakesMode) viewModelScope.launch {runCatching { studyPreferences.recordOutcomes(mapOf("bank:${question.id}" to isCorrect)) }}
        if (!isMistakesMode) {
            viewModelScope.launch {
                try {
                    val request = cn.zhzgo.study.data.ProgressRequest(question.id, isCorrect, userAnswerStr)
                    apiService.syncProgress(request)
                    studyPreferences.removeDraft(question.id)
                    runCatching { studyPreferences.recordOutcomes(mapOf("bank:${question.id}" to isCorrect)) }
                } catch (e: Exception) {
                    _uiState.update { current ->
                        val submitted = current.submittedQuestions - question.id
                        current.copy(submittedQuestions = submitted,
                            currentScore = calculateCurrentScore(current.questions, current.userAnswers, submitted),
                            errorMessage = requestError(e))
                    }
                } finally { _uiState.update { it.copy(pendingSubmissionIds = it.pendingSubmissionIds - question.id) } }
            }
        }
    }

    fun isSubmitted(questionId: Int): Boolean {
        return _uiState.value.submittedQuestions.contains(questionId)
    }

    private fun requestError(error: Exception): String {
        if (error is retrofit2.HttpException) {
            try {
                val body = org.json.JSONObject(error.response()?.errorBody()?.string().orEmpty())
                return body.optString("error").ifBlank { body.optString("message", "请求失败，请稍后重试") }
            } catch (_: Exception) { }
        }
        return "网络请求失败，请检查连接后重试"
    }

    private suspend fun fetchQuestionPage(page: Int, questionId: Int = 0): List<Question> {
        return when {
            currentSubjectId == -3 -> if (page == 1) listOf(apiService.getQuestion(questionId)) else emptyList()
            isFavoritesMode -> apiService.getFavoritePracticeQuestions(page, PAGE_SIZE)
            isMistakesMode -> apiService.getMistakes(page, currentSubjectId.takeIf { it > 0 }, PAGE_SIZE)
            else -> {
                val response = apiService.getQuestionPage(currentSubjectId, page, PAGE_SIZE)
                if (!response.isSuccessful) throw retrofit2.HttpException(response)
                _uiState.update { it.copy(previewOnly = response.headers()["X-Question-Access"] == "preview") }
                response.body() ?: emptyList()
            }
        }
    }

    fun setFeedback(immediate: Boolean) { _uiState.update { it.copy(immediateFeedback = immediate) } }

    private fun saveDraft(id: Int) {
        if(isMistakesMode) return
        val answer = formatUserAnswer(_uiState.value.userAnswers[id])
        viewModelScope.launch { studyPreferences.draft(id, answer) }
    }

    fun checkpoint(subjectName: String) {
        val state = _uiState.value
        val question = state.questions.getOrNull(state.currentIndex) ?: return
        if(currentSubjectId <= 0 || isFavoritesMode || isMistakesMode || state.isShuffleMode) return
        viewModelScope.launch { studyPreferences.checkpoint(StudySession(currentSubjectId,subjectName,question.id,state.pageOffset+state.currentIndex)) }
    }

    fun markMastered(id: Int) { viewModelScope.launch { studyPreferences.mark(setOf(id), true) } }
    fun loadNextPage() = loadPage(false)
    private fun loadPage(prepend: Boolean, advance: Boolean = false) {
        val state = _uiState.value
        if(state.isLoadingMore || state.isInitialLoading || (prepend && firstPage <= 1) || (!prepend && state.isLastPage)) return
        _uiState.update { it.copy(isLoadingMore=true,errorMessage=null) }
        pageLoad = viewModelScope.launch {
            try {
                val page = if(prepend) firstPage-1 else currentPage+1
                val result = fetchQuestionPage(page)
                val added = result.filter { q -> _uiState.value.questions.none { it.id == q.id } }
                val parsed = buildParsedOptionsMap(added)
                val progress = if(isMistakesMode) emptyList() else apiService.getProgress()
                val memory = studyPreferences.memory.first()
                originalQuestions = if(prepend) added+originalQuestions else originalQuestions+added
                _uiState.update { current ->
                    val all = if(prepend) added+current.questions else current.questions+added
                    val answers = current.userAnswers.toMutableMap()
                    val submitted = current.submittedQuestions.toMutableSet()
                    added.forEach { q ->
                        val stored = progress.firstOrNull { it.question_id == q.id }
                        if(stored != null) {
                            submitted.add(q.id)
                            answers[q.id] = if(q.type == "multiple") normalizeAnswer(q.type,stored.answer).map { it.toString() }.toSet()
                                else if(q.type in listOf("single","judge")) normalizeAnswer(q.type,stored.answer) else stored.answer
                        } else if(!isMistakesMode) memory.drafts[q.id]?.let { raw ->
                            answers[q.id] = cn.zhzgo.study.data.PracticeWorkflow.restoreDraft(q.type,raw)
                        }
                    }
                    current.copy(questions=all,userAnswers=answers,submittedQuestions=submitted,
                        currentIndex=if(prepend) maxOf(0,current.currentIndex+added.size-(if(advance)1 else 0)) else if(advance && added.isNotEmpty()) current.currentIndex+1 else current.currentIndex,
                        pageOffset=if(prepend)(page-1)*PAGE_SIZE else current.pageOffset,
                        currentScore=calculateCurrentScore(all,answers,submitted),maxScore=calculateMaxScore(all),
                        parsedOptionsMap=current.parsedOptionsMap+parsed,
                        isLastPage=if(prepend)current.isLastPage else result.size<PAGE_SIZE,aiExplanation=null)
                }
                if(prepend)firstPage=page else currentPage=page
            } catch(e:CancellationException) { throw e }
              catch(e:Exception) { _uiState.update { it.copy(errorMessage=requestError(e)) } }
            finally { _uiState.update { it.copy(isLoadingMore=false) } }
        }
    }
    fun nextQuestion() {
        aiJob?.cancel()
        val state = _uiState.value
        if(state.pendingSubmissionIds.isNotEmpty()) return
        if(state.currentIndex < state.questions.lastIndex) _uiState.update { it.copy(currentIndex=it.currentIndex+1,aiExplanation=null) }
        else loadPage(false, true)
    }
    fun prevQuestion() {
        aiJob?.cancel()
        if(_uiState.value.pendingSubmissionIds.isNotEmpty()) return
        if(_uiState.value.currentIndex>0) _uiState.update { it.copy(currentIndex=it.currentIndex-1,aiExplanation=null) }
        else loadPage(true,true)
    }
    fun jumpToQuestion(index:Int) {
        aiJob?.cancel()
        if(_uiState.value.pendingSubmissionIds.isEmpty() && index in _uiState.value.questions.indices)
            _uiState.update { it.copy(currentIndex=index,aiExplanation=null) }
    }

    fun requestAiExplanation(question: Question) {
        if (_uiState.value.aiExplanation != null) return

        _uiState.update { it.copy(isLoadingAi = true, aiExplanation = "") }
        aiJob = viewModelScope.launch {
            try {
                val userAnswerRaw = _uiState.value.userAnswers[question.id]
                val userAnswerStr = formatUserAnswer(userAnswerRaw)

                val optionsJson = if (question.options != null) {
                    if (question.options is String) question.options
                    else Gson().toJson(question.options)
                } else ""

                val request = mapOf(
                    "question" to question.content,
                    "options" to optionsJson,
                    "userAnswer" to userAnswerStr,
                    "correctAnswer" to normalizeAnswer(question.type, question.answer),
                    "type" to question.type
                )

                withContext(Dispatchers.IO) {
                    val responseBody = apiService.getAiExplanationStream(request)
                    val reader = responseBody.byteStream().bufferedReader()
                    val gson = Gson()

                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val currentLine = line ?: break
                        if (currentLine.startsWith("data: ")) {
                            val data = currentLine.substring(6).trim()
                            if (data == "[DONE]") break

                            try {
                                val json = org.json.JSONObject(data)

                                if (json.has("choices")) {
                                    val choices = json.optJSONArray("choices")
                                    if (choices != null && choices.length() > 0) {
                                        val choice = choices.optJSONObject(0)
                                        val delta = choice?.optJSONObject("delta")
                                        val content = delta?.optString("content", null)
                                        if (content != null) {
                                            withContext(Dispatchers.Main) {
                                                _uiState.update { currentState ->
                                                    currentState.copy(aiExplanation = (currentState.aiExplanation ?: "") + content)
                                                }
                                            }
                                        }
                                    }
                                } else if (json.has("remaining") || json.has("done")) {
                                    val rem = if (json.has("remaining")) json.getInt("remaining") else null
                                    if (rem != null) {
                                        withContext(Dispatchers.Main) {
                                            _uiState.update { it.copy(aiRemaining = rem) }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                // Ignore json parsing errors for incomplete chunks
                            }
                        }
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _uiState.update { it.copy(aiExplanation = "AI 讲解失败，请稍后重试") }
            } finally {
                _uiState.update { it.copy(isLoadingAi = false) }
            }
        }
    }

    fun resetProgress(subjectId: Int) {
        viewModelScope.launch {
            try {
                val ids = apiService.getProgress().filter { it.subject_id==subjectId }.map { it.question_id }.toSet()
                apiService.resetProgress(subjectId.toString())
                studyPreferences.resetSubject(subjectId,ids)
                _uiState.update { currentState ->
                    QuestionUiState(
                        isMistakesMode = currentState.isMistakesMode,
                        maxScore = currentState.maxScore,
                        currentScore = 0
                    )
                }
                kotlinx.coroutines.delay(300)
                loadQuestions(subjectId, isMistakes = _uiState.value.isMistakesMode, force = true)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage=requestError(e)) }
            }
        }
    }

    fun retryQuestion(questionId:Int) {
        _uiState.update { state ->
            val submitted=state.submittedQuestions-questionId
            val answers=state.userAnswers-questionId
            state.copy(submittedQuestions=submitted,userAnswers=answers,aiExplanation=null,
                currentScore=calculateCurrentScore(state.questions,answers,submitted))
        }
    }

    private fun formatUserAnswer(answer: Any?): String {
        return when (answer) {
            is String -> answer
            is Set<*> -> (answer as Set<String>).sorted().joinToString(",")
            else -> ""
        }
    }

    fun submitReport(questionId: Int, reason: String, detail: String?, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val request = cn.zhzgo.study.data.ReportRequest(reason, detail)
                val response = apiService.submitReport(questionId, request)
                onResult(true, response.message ?: "报错成功")
            } catch (e: Exception) {
                onResult(false, "提交失败: ${e.message}")
            }
        }
    }
}
