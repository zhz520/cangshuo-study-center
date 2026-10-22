package cn.zhzgo.study.ui.screens.tools

import android.app.Application
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.zhzgo.study.data.ExamScoreDto
import cn.zhzgo.study.data.ScoreQueryRequest
import cn.zhzgo.study.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class ScoreQueryViewModel(application: Application) : AndroidViewModel(application) {
    private val apiService = RetrofitClient.create(application)

    private val _uiState = MutableStateFlow<ScoreQueryUiState>(ScoreQueryUiState.Initial)
    val uiState: StateFlow<ScoreQueryUiState> = _uiState.asStateFlow()

    fun queryScore(studentName: String, examNumber: String, schoolName: String) {
        val name = studentName.trim()
        val exam = examNumber.trim()
        val school = schoolName.trim()

        if (name.isEmpty()) {
            _uiState.value = ScoreQueryUiState.Error("请输入考生姓名")
            return
        }
        if (exam.isEmpty() && school.isEmpty()) {
            _uiState.value = ScoreQueryUiState.Error("请输入准考证号或学校名称")
            return
        }

        viewModelScope.launch {
            _uiState.value = ScoreQueryUiState.Loading
            try {
                val response = apiService.queryScore(
                    ScoreQueryRequest(
                        student_name = name,
                        exam_number = exam.takeIf { it.isNotEmpty() },
                        school_name = school.takeIf { it.isNotEmpty() }
                    )
                )
                if (response.success && !response.data.isNullOrEmpty()) {
                    _uiState.value = ScoreQueryUiState.Success(response.data)
                } else {
                    _uiState.value = ScoreQueryUiState.Error(response.error ?: "未查询到成绩")
                }
            } catch (e: Exception) {
                // Try to extract error message from HttpException
                val errorMsg = try {
                    if (e is retrofit2.HttpException) {
                        val errorBody = e.response()?.errorBody()?.string()
                        if (errorBody != null) {
                            JSONObject(errorBody).optString("error", "网络请求失败")
                        } else "查询失败，请检查网络"
                    } else "查询失败，请检查网络"
                } catch (ignored: Exception) {
                    "查询失败，请检查网络"
                }
                _uiState.value = ScoreQueryUiState.Error(errorMsg)
            }
        }
    }

    fun reset() {
        _uiState.value = ScoreQueryUiState.Initial
    }
}

sealed class ScoreQueryUiState {
    object Initial : ScoreQueryUiState()
    object Loading : ScoreQueryUiState()
    data class Success(val data: List<ExamScoreDto>) : ScoreQueryUiState()
    data class Error(val message: String) : ScoreQueryUiState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreQueryScreen(onBack: () -> Unit) {
    val viewModel: ScoreQueryViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()

    var studentName by remember { mutableStateOf("") }
    var examNumber by remember { mutableStateOf("") }
    var schoolName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("联考成绩查询", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedContent(
                targetState = uiState is ScoreQueryUiState.Success,
                label = "Score View Transition"
            ) { isSuccess ->
                if (isSuccess) {
                    val dataList = (uiState as ScoreQueryUiState.Success).data
                    ScoreResultView(
                        dataList = dataList,
                        onReset = {
                            viewModel.reset()
                            // Optional: clear fields if desired
                            // examNumber = ""
                        }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        contentPadding = PaddingValues(vertical = 24.dp)
                    ) {
                        item {
                            Text(
                                text = "2025—2026 学年 第四次联考\n四川省职教高考研究联合体",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(bottom = 32.dp)
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = studentName,
                                onValueChange = { studentName = it },
                                label = { Text("考生姓名 *") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        item {
                            Text(
                                text = "— 以下条件任填一项即可 —",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = examNumber,
                                onValueChange = { examNumber = it },
                                label = { Text("准考证号 / 考号") },
                                placeholder = { Text("如：203907001") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = schoolName,
                                onValueChange = { schoolName = it },
                                label = { Text("或 所在学校名称 (支持模糊)") },
                                placeholder = { Text("如：内江市") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        item {
                            if (uiState is ScoreQueryUiState.Error) {
                                Text(
                                    text = (uiState as ScoreQueryUiState.Error).message,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                            }
                        }

                        item {
                            Button(
                                onClick = { viewModel.queryScore(studentName, examNumber, schoolName) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp),
                                shape = RoundedCornerShape(14.dp),
                                enabled = uiState !is ScoreQueryUiState.Loading && studentName.isNotBlank() && (examNumber.isNotBlank() || schoolName.isNotBlank())
                            ) {
                                if (uiState is ScoreQueryUiState.Loading) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("查询成绩", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScoreResultView(dataList: List<ExamScoreDto>, onReset: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (dataList.size > 1) "找到 ${dataList.size} 条记录" else "成绩单",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onReset) {
                    Text("重新查询", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        items(dataList) { scoreData ->
            ScoreCardItem(scoreData)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun ScoreCardItem(scoreData: ExamScoreDto) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                1.dp,
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                RoundedCornerShape(16.dp)
            )
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp)
    ) {
        // Basic Info Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = scoreData.student_name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = scoreData.school_name ?: "未知学校",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "总名次",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Text(
                    text = "No.${scoreData.total_rank ?: "-"}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
        Spacer(modifier = Modifier.height(16.dp))

        // Total Score Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = scoreData.total_score?.let {
                        if (it == it.toLong().toFloat()) it.toLong().toString() else it.toString()
                    } ?: "-",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "分 / 总分",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Info Grid
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            InfoBox("考号", scoreData.exam_number, Modifier.weight(1f))
            Spacer(modifier = Modifier.width(12.dp))
            InfoBox("专业类别", scoreData.major_category ?: "-", Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "各科明细",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Use Typed Details
        val details = scoreData.score_details
        val subjects = listOf(
            "语文" to details?.chinese,
            "数学" to details?.math,
            "英语" to details?.english,
            "专业综合" to details?.professional
        )

        subjects.forEach { (subject, data) ->
            if (data != null && !data.total.isNullOrBlank()) {
                SubjectDetailRow(subject, data)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun InfoBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f))
            .padding(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun SubjectDetailRow(subjectName: String, data: cn.zhzgo.study.data.SubjectScore) {
    val total = data.total ?: "-"
    val objScore = data.objective ?: "-"
    val subScore = data.subjective ?: "-"
    val rankSchool = data.rank_school ?: "-"
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = subjectName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(2.5f)) {
            Text(
                text = "${total}分 (校#$rankSchool)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "客观: $objScore | 主观: $subScore",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}
