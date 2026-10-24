package cn.zhzgo.study.network

import cn.zhzgo.study.data.*
import okhttp3.MultipartBody
import retrofit2.http.*

interface ApiService {
    @GET("api/data/appearance")
    suspend fun getMemberAppearance(): MemberAppearance

    @PUT("api/data/appearance")
    suspend fun saveMemberAppearance(@Body appearance: MemberAppearanceRequest): MemberAppearance

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): RegisterResponse

    @POST("api/auth/send-code")
    suspend fun sendVerificationCode(@Body request: SendCodeRequest): GenericMessageResponse

    @POST("api/auth/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): GenericMessageResponse

    @POST("api/auth/change-email")
    suspend fun changeEmail(@Body request: ChangeEmailRequest): retrofit2.Response<LoginResponse>

    @POST("api/auth/qq-login")
    suspend fun qqLogin(@Body request: QQLoginRequest): retrofit2.Response<QQLoginResponse>

    @POST("api/auth/bind-qq")
    suspend fun bindQq(@Body request: QQBindRequest): retrofit2.Response<LoginResponse>

    @POST("api/auth/unbind-qq")
    suspend fun unbindQq(): GenericMessageResponse

    @PATCH("api/auth/update-profile")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): GenericMessageResponse

    @GET("api/data/majors")
    suspend fun getMajors(): List<Major>

    @GET("api/data/subjects")
    suspend fun getSubjects(@Query("major_id") majorId: Int?): List<Subject>

    @GET("api/data/questions")
    suspend fun getQuestions(
        @Query("subject_id") subjectId: Int,
        @Query("type") type: String?,
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): List<Question>

    @GET("api/data/questions")
    suspend fun getQuestionPage(
        @Query("subject_id") subjectId: Int,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50
    ): retrofit2.Response<List<Question>>

    @GET("api/data/questions/{id}")
    suspend fun getQuestion(@Path("id") id: Int): Question

    @GET("api/data/questions/{id}/review")
    suspend fun getQuestionReview(@Path("id") id: Int, @Query("answer_token") answerToken: String?): Question


    @GET("api/data/daily-content/random")
    suspend fun getDailyContent(): DailyContentResponse

    @GET("api/public/home-data")
    suspend fun getHomeData(): HomeDataResponse

    @GET("api/data/user-stats")
    suspend fun getUserStats(): UserStats

    @GET("api/data/detailed-stats")
    suspend fun getDetailedStats(): DetailedStatsResponse

    @GET("api/data/leaderboard")
    suspend fun getLeaderboard(
        @Query("type") type: String,
        @Query("subject_id") subjectId: String? = "all",
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50
    ): List<LeaderboardUser>

    @POST("api/data/progress")
    suspend fun syncProgress(@Body request: ProgressRequest): ProgressGradeResponse

    @DELETE("api/data/progress")
    suspend fun resetProgress(@Query("subject_id") subjectId: String)

    @POST("api/data/sync")
    suspend fun syncBatch(@Body payload: @JvmSuppressWildcards Map<String, Any>)

    // Get User Progress
    @GET("api/data/progress")
    suspend fun getProgress(): List<ProgressResponse>

    @POST("api/data/ai-explain")
    suspend fun getAiExplanation(@Body request: @JvmSuppressWildcards Map<String, Any>): @JvmSuppressWildcards Map<String, Any>

    @Streaming
    @POST("api/data/ai-explain")
    suspend fun getAiExplanationStream(@Body request: @JvmSuppressWildcards Map<String, Any>): okhttp3.ResponseBody

    // AI Learning Analytics (stats screen)
    @POST("api/data/ai-analysis")
    suspend fun getAiLearningAnalysis(@Body request: @JvmSuppressWildcards Map<String, Any>): @JvmSuppressWildcards Map<String, Any>

    @POST("api/data/update-avatar")
    suspend fun updateAvatar(@Body request: @JvmSuppressWildcards Map<String, String>): @JvmSuppressWildcards Map<String, String>
    
    @Multipart
    @POST("api/upload")
    suspend fun uploadImage(@Part image: MultipartBody.Part): UploadResponse
    
    // Settings APIs
    @POST("api/auth/change-password")
    suspend fun changePassword(@Body request: @JvmSuppressWildcards Map<String, String>): @JvmSuppressWildcards Map<String, String>

    @POST("api/data/submit-rating")
    suspend fun submitRating(@Body request: @JvmSuppressWildcards Map<String, Any>): @JvmSuppressWildcards Map<String, String>

    @POST("api/auth/refresh")
    suspend fun refreshToken(@Body request: RefreshRequest): RefreshResponse

    @POST("api/auth/logout")
    suspend fun logout(@Body request: LogoutRequest)

    // App Update Endpoint
    @GET("api/app/check_update")
    suspend fun checkUpdate(@Query("versionCode") versionCode: Int): UpdateResponse

    @GET("api/app/versions")
    suspend fun getAppVersionHistory(): AppVersionHistoryResponse

    // Resources & Favorites
    @GET("api/data/resources/categories")
    suspend fun getResourceCategories(): List<ResourceCategory>

    @GET("api/data/resources/articles")
    suspend fun getArticles(
        @Query("category") category: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 10
    ): List<Article>

    @GET("api/data/resources/articles/{id}")
    suspend fun getArticleDetail(@Path("id") id: Int): Article

    @GET("api/data/favorites")
    suspend fun getFavorites(@Query("page") page: Int = 1): List<FavoriteQuestion>

    @GET("api/data/favorites/ids")
    suspend fun getFavoriteQuestionIds(): List<Int>

    @GET("api/data/favorites/practice")
    suspend fun getFavoritePracticeQuestions(@Query("page") page: Int = 1, @Query("limit") limit: Int = 50): List<Question>

    @POST("api/data/favorites")
    suspend fun addFavorite(@Body request: FavoriteAddRequest): GenericMessageResponse

    @DELETE("api/data/favorites/{id}")
    suspend fun removeFavorite(@Path("id") id: Int): GenericMessageResponse

    @DELETE("api/data/favorites/question/{questionId}")
    suspend fun removeFavoriteByQuestionId(@Path("questionId") questionId: Int): GenericMessageResponse

    // Comment APIs
    @GET("api/data/resources/articles/{id}/comments")
    suspend fun getComments(@Path("id") id: Int): List<Comment>

    @POST("api/data/resources/articles/{id}/comments")
    suspend fun postComment(@Path("id") id: Int, @Body request: CommentRequest): GenericMessageResponse

    @DELETE("api/data/comments/{id}")
    suspend fun deleteComment(@Path("id") id: Int): GenericMessageResponse

    // User Profile API
    @GET("api/data/user/{id}")
    suspend fun getUserProfile(@Path("id") id: String): UserProfileResponse

    // Article Submission API
    @POST("api/data/resources/articles")
    suspend fun submitArticle(@Body article: @JvmSuppressWildcards Map<String, Any?>): GenericMessageResponse

    // Video Parser API
    @POST("api/public/video/parse")
    suspend fun parseVideo(@Body request: VideoParseRequest): VideoParseResponse

    // Subscription Redemption
    @POST("api/data/subscribe")
    suspend fun redeemSubscription(@Body request: SubscriptionRedeemRequest): SubscriptionRedeemResponse

    @GET("api/data/schedule")
    suspend fun getSchedule(): List<ScheduleCell>

    @GET("api/data/mistakes")
    suspend fun getMistakes(@Query("page") page: Int = 1, @Query("subject_id") subjectId: Int? = null, @Query("limit") limit: Int = 50): List<Question>

    @GET("api/data/questions/search")
    suspend fun searchQuestions(
        @Query("keyword") keyword: String?,
        @Query("type") type: String? = null,
        @Query("subjectId") subjectId: Int? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50
    ): List<Question>

    @POST("api/data/user/fcm-token")
    suspend fun postFcmToken(@Body request: FcmTokenRequest): GenericMessageResponse

    @GET("api/data/notifications")
    suspend fun getNotifications(): List<Notification>

    @POST("api/data/questions/{id}/report")
    suspend fun submitReport(@Path("id") id: Int, @Body request: ReportRequest): GenericMessageResponse

    @POST("api/public/score-query")
    suspend fun queryScore(@Body request: ScoreQueryRequest): ScoreQueryResponse

    // ===== Exam System =====
    @GET("api/data/exams")
    suspend fun getExamPapers(): List<ExamPaper>

    @GET("api/data/exams/records")
    suspend fun getExamRecords(): List<ExamRecord>

    @POST("api/data/exams/{paperId}/start")
    suspend fun startExam(@Path("paperId") paperId: Int): ExamStartResponse

    @POST("api/data/exams/{recordId}/submit")
    suspend fun submitExam(@Path("recordId") recordId: Int, @Body request: ExamSubmitRequest): ExamSubmitResponse

    @POST("api/data/exams/{recordId}/abandon")
    suspend fun abandonExam(@Path("recordId") recordId: Int): GenericMessageResponse
}

