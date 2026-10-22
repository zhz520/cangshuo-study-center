package cn.zhzgo.study.data

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
@Immutable
data class LoginRequest(
    val username: String,
    val password: String,
    val lot_number: String? = null,
    val captcha_output: String? = null,
    val pass_token: String? = null,
    val gen_time: String? = null,
    val client_type: String? = "android"
)

@Immutable
data class LoginResponse(
    val token: String,
    val refreshToken: String,
    val user: User
)

@Immutable
data class RegisterRequest(
    val username: String,
    val email: String,
    val code: String,
    val password: String,
    val lot_number: String? = null,
    val captcha_output: String? = null,
    val pass_token: String? = null,
    val gen_time: String? = null,
    val client_type: String? = "android"
)

@Immutable
data class ForgotPasswordRequest(
    val email: String,
    val code: String,
    val newPassword: String,
    val lot_number: String? = null,
    val captcha_output: String? = null,
    val pass_token: String? = null,
    val gen_time: String? = null,
    val client_type: String? = "android"
)

@Immutable
data class ChangeEmailRequest(
    val email: String,
    val code: String
)

@Immutable
data class SendCodeRequest(
    val email: String,
    val lot_number: String? = null,
    val captcha_output: String? = null,
    val pass_token: String? = null,
    val gen_time: String? = null,
    val client_type: String? = "android"
)

@Immutable
data class RefreshRequest(
    val refreshToken: String
)

@Immutable
data class RefreshResponse(
    val token: String,
    val user: User
)

@Immutable
data class LogoutRequest(
    val refreshToken: String
)

@Immutable
data class RegisterResponse(
    val message: String?
)

@Immutable
data class QQLoginRequest(
    val openid: String,
    val access_token: String
)

@Immutable
data class QQLoginResponse(
    val bind_token: String? = null,
    val need_bind: Boolean?,
    val message: String?,
    val token: String?,
    val refreshToken: String?,
    val user: User?
)

@Immutable
data class QQBindRequest(
    val access_token: String? = null,
    val bind_token: String? = null,
    val openid: String,
    val username: String? = null,
    val password: String? = null,
    val nickname: String? = null,
    val avatar_icon: String? = null
)

@Immutable
data class User(
    val id: Int,
    val username: String,
    val role: String,
    val avatar_icon: String? = null,
    val nickname: String? = null,
    val slogan: String? = null,
    val qq_openid: String? = null,
    val email: String? = null,
    val subscription_end_date: String? = null
)

@Immutable
data class Major(
    val id: Int,
    val name: String,
    val description: String?
)

@Immutable
data class Subject(
    val id: Int,
    val name: String,
    val major_id: Int,
    val icon: String?,
    val question_count: Int? = null,
    val max_score: Int? = null,
    val current_score: Int? = null
)

@Immutable
data class Question(
    val id: Int,
    val subject_name: String?,
    val type: String, // "single", "multiple", "judge", "fill", "material", "sort"
    val content: String,
    val options: Any?, // Can be String (JSON), List, or Map. Handled in ViewModel.
    val answer: String?,
    val analysis: String?,
    val difficulty: Int?,
    val tags: Any?,
    val category: String?
)

@Immutable
data class DailyContentResponse(
    val quote: String?,
    val proverb: DualLanguageContent?,
    val poetry: PoetryContent?
)

@Immutable
data class DualLanguageContent(
    val en: String?,
    val zh: String?
)

@Immutable
data class PoetryContent(
    val line: String?,
    val src: String?
)

@Immutable
data class Countdown(
    val id: Int?,
    val title: String?,
    val target_date: String?
)

@Immutable
data class ExternalLink(
    val id: Int,
    val name: String,
    val url: String
)

@Immutable
data class HomeDataResponse(
    val countdowns: List<Countdown>,
    val externalLinks: List<ExternalLink>,
    val settings: Map<String, String>? = null
)

@Immutable
data class UserStats(
    val active_days: Int,
    val total_questions: Int,
    val accuracy: Int
)

@Immutable
data class LeaderboardUser(
    val rank: Int,
    val user_id: Int,
    val username: String,
    val nickname: String?,
    val avatar_icon: String?,
    val score: Int,
    val subscription_end_date: String? = null
)

@Immutable
data class RecommendationResponse(
    val recommendations: List<Question>
)

@Immutable
data class ProgressRequest(
    val question_id: Int,
    val is_correct: Boolean,
    val answer: String
)

@Immutable
data class ProgressResponse(
    val question_id: Int,
    val is_correct: Int,
    val answer: String,
    val subject_id: Int
)

// Detailed Stats Models
@Immutable
data class SubjectStat(
    val subject_id: Int,
    val subject_name: String,
    val total: Int,
    val correct: Int,
    val accuracy: Int
)

@Immutable
data class TypeStat(
    val type: String,
    val total: Int,
    val correct: Int,
    val accuracy: Int
)

@Immutable
data class WeekDay(
    val day: String,
    val count: Int
)

@Immutable
data class DetailedStatsResponse(
    val subjectStats: List<SubjectStat>,
    val typeStats: List<TypeStat>,
    val weekHeatmap: List<WeekDay>,
    val streak: Int
)

@Immutable
data class Article(
    val id: Int,
    val title: String,
    val summary: String? = null,
    val content: String? = null,
    val cover_url: String? = null,
    val coverUrl: String? = null, // backwards compat alias
    val category: String? = null,
    val is_published: Int? = 1,
    val is_pinned: Int? = 0,
    val view_count: Int? = 0,
    val author_id: Int? = null,
    val author_name: String? = null,
    val author_avatar: String? = null,
    val author_subscription_end_date: String? = null,
    val content_markdown: String? = null,
    val content_format: String? = null,
    val publish_time: String? = null,
    val publishTime: String? = null // backwards compat alias
) {
    fun getCover(): String? {
        val url = cover_url ?: coverUrl ?: return null
        return if (url.startsWith("/")) "https://studyapi.zhzgo.cn$url" else url
    }
    fun getAuthorAvatar(): String? {
        val url = author_avatar ?: return null
        return if (url.startsWith("/")) "https://studyapi.zhzgo.cn$url" else url
    }
    fun getTime(): String? = publish_time ?: publishTime
}

@Immutable
data class ResourceCategory(
    val id: Int,
    val name: String,
    val iconRes: Int // Can be mapped to local icons or URLs
)

@Immutable
data class FavoriteQuestion(
    val id: Int,
    val question_id: Int,
    val title: String,
    val type: String,
    val favorite_time: String
)

@Immutable
data class FavoriteAddRequest(
    val question_id: Int
)

@Immutable
data class GenericMessageResponse(
    val message: String
)

@Immutable
data class UploadResponse(
    val url: String,
    val filename: String,
    val size: Long
)

@Immutable
data class Comment(
    val id: Int,
    val article_id: Int,
    val user_id: Int,
    val content: String,
    val created_at: String,
    val username: String,
    val avatar_icon: String? = null,
    val subscription_end_date: String? = null,
    val article_title: String? = null // For Dynamics
)

@Immutable
data class UserProfileData(
    val id: Int,
    val username: String,
    val avatar_icon: String? = null,
    val nickname: String? = null,
    val slogan: String? = null,
    val role: String,
    val join_date: String? = null,
    val email: String? = null,
    val qq_openid: String? = null,
    val subscription_end_date: String? = null
)

@Immutable
data class UserProfileResponse(
    val user: UserProfileData,
    val articles: List<Article>,
    val dynamics: List<Comment>
)

@Immutable
data class CommentRequest(
    val content: String
)

// Video Parser
@Immutable
data class VideoParseRequest(
    val url: String
)

@Immutable
data class VideoParseResult(
    val platform: String? = null,
    val type: String? = null,
    val title: String? = null,
    val videoUrl: String? = null,
    val coverUrl: String? = null,
    val images: List<String>? = null,
    val author: String? = null
)

@Immutable
data class VideoParseResponse(
    val success: Boolean,
    val data: VideoParseResult? = null,
    val error: String? = null
)

@Immutable
data class SubscriptionRedeemRequest(
    val code: String
)

@Immutable
data class SubscriptionRedeemResponse(
    val message: String,
    val subscription_end_date: String? = null
)
@Immutable
data class ScheduleCell(
    val id: Int,
    val schedule_name: String,
    val period: String,
    val day_of_week: Int,
    val subject: String,
    val location: String? = null,
    val sort_order: Int
)

@Immutable
data class ScheduleResponse(
    val cells: List<ScheduleCell>
)

@Immutable
data class Notification(
    val id: Int,
    val user_id: Int,
    val title: String,
    val message: String,
    val type: String,
    val link_id: Int?,
    val is_read: Int,
    val created_at: String
)

@Immutable
data class FcmTokenRequest(
    val token: String
)

@Immutable
data class ReportRequest(
    val reason: String,
    val detail: String?
)
@Immutable
data class UpdateProfileRequest(
    val nickname: String? = null,
    val slogan: String? = null
)

@Immutable
data class ScoreQueryRequest(
    val student_name: String,
    val exam_number: String?,
    val school_name: String?
)

@Immutable
data class SubjectScore(
    val total: String? = null,
    val objective: String? = null,
    val subjective: String? = null,
    val rank_overall: String? = null,
    val rank_city: String? = null,
    val rank_district: String? = null,
    val rank_school: String? = null,
    val rank_class: String? = null
)

@Immutable
data class ScoreDetails(
    val chinese: SubjectScore? = null,
    val math: SubjectScore? = null,
    val english: SubjectScore? = null,
    val professional: SubjectScore? = null
)

@Immutable
data class ExamScoreDto(
    val id: Int,
    val exam_name: String,
    val student_name: String,
    val exam_number: String,
    val school_name: String?,
    val major_category: String?,
    val total_score: Float?,
    val total_rank: Int?,
    val score_details: ScoreDetails?
)

@Immutable
data class ScoreQueryResponse(
    val success: Boolean,
    val data: List<ExamScoreDto>?,
    val error: String?
)

// =====================================================
// 考试系统 (Exam System) Models
// =====================================================

@Immutable
data class ExamPaper(
    val id: Int,
    val title: String,
    val description: String? = null,
    val duration_minutes: Int = 90,
    val total_score: Int = 100,
    val pass_score: Int = 60,
    val instructions: String? = null,
    val question_count: Int = 0,
    val latest_record: ExamRecord? = null,
    val has_completed: Boolean = false,
    val has_abandoned: Boolean = false,
    val can_take: Boolean = true,
    val is_in_progress: Boolean = false
)

@Immutable
data class ExamRecord(
    val id: Int,
    val user_id: Int? = null,
    val paper_id: Int,
    val score: Int = 0,
    val total_score: Int? = null,
    val correct_count: Int = 0,
    val incorrect_count: Int = 0,
    val unanswered_count: Int = 0,
    val status: String = "in_progress",
    val started_at: String? = null,
    val finished_at: String? = null,
    val can_retry: Int = 0,
    val paper_title: String? = null,
    val paper_description: String? = null,
    // Admin fields
    val username: String? = null,
    val nickname: String? = null,
    val avatar_icon: String? = null,
    val answers: String? = null
)

@Immutable
data class ExamQuestion(
    val id: Int,
    val index: Int = 0,
    val type: String,
    val content: String,
    val options: Any? = null,
    val score: Int = 1
)

@Immutable
data class ExamStartResponse(
    val record_id: Int,
    val paper: ExamPaperInfo,
    val questions: List<ExamQuestion>
)

@Immutable
data class ExamPaperInfo(
    val id: Int,
    val title: String,
    val duration_minutes: Int,
    val total_score: Int,
    val pass_score: Int
)

@Immutable
data class ExamSubmitRequest(
    val answers: Map<String, String>
)

@Immutable
data class ExamAnswerDetail(
    val question_id: Int,
    val question_index: Int,
    val user_answer: String = "",
    val correct_answer: String? = null,
    val is_correct: Boolean = false,
    val score_earned: Int = 0,
    val analysis: String? = null
)

@Immutable
data class ExamSubmitResponse(
    val record_id: Int,
    val score: Int,
    val total_score: Int,
    val correct_count: Int,
    val incorrect_count: Int,
    val unanswered_count: Int,
    val passed: Boolean,
    val details: List<ExamAnswerDetail>? = null
)
