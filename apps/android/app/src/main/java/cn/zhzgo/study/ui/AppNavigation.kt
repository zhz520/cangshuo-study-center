package cn.zhzgo.study.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import androidx.browser.customtabs.CustomTabsIntent
import android.net.Uri
import androidx.compose.ui.unit.dp
import cn.zhzgo.study.ui.screens.*
import cn.zhzgo.study.ui.screens.tools.*
import cn.zhzgo.study.data.UserPreferences
import kotlinx.coroutines.flow.first
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember

import cn.zhzgo.study.network.GlobalMessageManager
import cn.zhzgo.study.network.NetworkEvent
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showNetworkSupportDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        GlobalMessageManager.events.collectLatest { event ->
            when (event) {
                is NetworkEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is NetworkEvent.ShowNoNetwork -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "网络连接不可用，请检查设置",
                        actionLabel = "查看方案",
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        showNetworkSupportDialog = true
                    }
                }
            }
        }
    }

    val userPrefs = remember { UserPreferences(context) }
    val authState by userPrefs.authToken.collectAsState(initial = "loading")

    if (authState == "loading") {
        cn.zhzgo.study.ui.components.LoadingOverlay(isLoading = true, message = "启动中...")
        return
    }

    val startDestination = remember { if (authState.isNullOrEmpty()) "login" else "home" }

    val navigateToWebView: (String, String) -> Unit = { url, _ ->
        val customTabsIntent = CustomTabsIntent.Builder().build()
        try {
            customTabsIntent.launchUrl(context, Uri.parse(url))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route?.substringBefore("/")

    val bottomNavRoutes = AppNavigationPolicy.mainRoutes
    fun openTab(route: String) {
        if (currentRoute != route) navController.navigate(route) {
            popUpTo("home") { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    LaunchedEffect(authState, currentRoute) {
        if (authState.isNullOrEmpty() && AppNavigationPolicy.needsLogin(currentRoute)) {
            bottomNavRoutes.forEach { navController.clearBackStack(it) }
            navController.navigate("login") {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    val examViewModel: ExamViewModel = viewModel()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (bottomNavRoutes.contains(currentRoute)) {
                val lastClickTime = remember { androidx.compose.runtime.mutableLongStateOf(0L) }

                Column(Modifier.background(MaterialTheme.colorScheme.surface).navigationBarsPadding()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                NavigationBar(
                    modifier = Modifier.height(64.dp),
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    tonalElevation = 0.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    val tabs = listOf(
                        Triple("home", "首页", Icons.Outlined.Home),
                        Triple("study", "题库", Icons.Outlined.MenuBook),
                        Triple("resources", "资源", Icons.Outlined.FolderOpen),
                        Triple("stats", "统计", Icons.Outlined.BarChart),
                        Triple("profile", "我的", Icons.Outlined.PersonOutline)
                    )
                    tabs.forEach { (route,label,icon) ->
                        NavigationBarItem(selected=currentRoute==route,onClick={
                            openTab(route)
                        },icon={Icon(if(currentRoute==route) when(route) {
                            "home"->Icons.Filled.Home; "study"->Icons.Filled.MenuBook; "resources"->Icons.Filled.Folder;
                            "stats"->Icons.Filled.BarChart; else->Icons.Filled.Person
                        } else icon,contentDescription=null,modifier=Modifier.size(22.dp))},label={Text(label,style=MaterialTheme.typography.labelSmall)},
                            colors=NavigationBarItemDefaults.colors(selectedIconColor=MaterialTheme.colorScheme.primary,
                                selectedTextColor=MaterialTheme.colorScheme.primary,
                                unselectedIconColor=MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor=MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor=androidx.compose.ui.graphics.Color.Transparent))
                    }
                }
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
            enterTransition = {
                if (AppNavigationPolicy.isTabChange(initialState.destination.route, targetState.destination.route))
                    fadeIn(tween(AppNavigationPolicy.FADE_MS))
                else slideInHorizontally(tween(AppNavigationPolicy.DETAIL_MS)) { it / 5 } + fadeIn(tween(AppNavigationPolicy.FADE_MS))
            },
            exitTransition = {
                if (AppNavigationPolicy.isTabChange(initialState.destination.route, targetState.destination.route))
                    fadeOut(tween(AppNavigationPolicy.FADE_MS))
                else slideOutHorizontally(tween(AppNavigationPolicy.DETAIL_MS)) { -it / 10 } + fadeOut(tween(AppNavigationPolicy.FADE_MS))
            },
            popEnterTransition = {
                if (AppNavigationPolicy.isTabChange(initialState.destination.route, targetState.destination.route))
                    fadeIn(tween(AppNavigationPolicy.FADE_MS))
                else slideInHorizontally(tween(AppNavigationPolicy.DETAIL_MS)) { -it / 10 } + fadeIn(tween(AppNavigationPolicy.FADE_MS))
            },
            popExitTransition = {
                if (AppNavigationPolicy.isTabChange(initialState.destination.route, targetState.destination.route))
                    fadeOut(tween(AppNavigationPolicy.FADE_MS))
                else slideOutHorizontally(tween(AppNavigationPolicy.DETAIL_MS)) { it / 5 } + fadeOut(tween(AppNavigationPolicy.FADE_MS))
            }
        ) {

        // --- Search ---
        composable("search") {
            SearchScreen(
                onBack = { navController.navigateUp() },
                onNavigateToQuestion = { questionId, subjectName ->
                    val encodedName = URLEncoder.encode(subjectName, StandardCharsets.UTF_8.toString())
                    navController.navigate("question_search/$questionId/$encodedName")
                }
            )
        }

        composable(
            route = "question_search/{questionId}/{subjectName}",
            arguments = listOf(
                navArgument("questionId") { type = NavType.IntType },
                navArgument("subjectName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val questionId = backStackEntry.arguments?.getInt("questionId") ?: 0
            val subjectName = URLDecoder.decode(backStackEntry.arguments?.getString("subjectName") ?: "", StandardCharsets.UTF_8.toString())

            QuestionScreen(
                subjectId = -3, // Single question mode
                subjectName = subjectName,
                questionId = questionId,
                onBack = { navController.navigateUp() },
                onNavigateToSubscription = { navController.navigate("subscription_plus") }
            )
        }

        // --- Core Auth & Main Screens ---


        composable("login") {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate("register")
                },
                onNavigateToBindQQ = { openId, bindToken ->
                    navController.currentBackStackEntry?.savedStateHandle?.set("qq_bind_token", bindToken)
                    navController.navigate("bind_qq/$openId")
                },
                onNavigateToWebView = navigateToWebView
            )
        }

        composable("register") {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigateUp()
                },
                onBack = {
                    navController.navigateUp()
                },
                onNavigateToWebView = navigateToWebView
            )
        }

        composable(
            route = "bind_qq/{openId}",
            arguments = listOf(navArgument("openId") { type = NavType.StringType })
        ) { backStackEntry ->
            val openId = backStackEntry.arguments?.getString("openId") ?: ""
            val bindToken = remember(backStackEntry) {
                navController.previousBackStackEntry?.savedStateHandle?.remove<String>("qq_bind_token").orEmpty()
            }
            BindQQScreen(
                openId = openId,
                bindToken = bindToken,
                onBindSuccess = {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onBack = { navController.navigateUp() }
            )
        }

        composable("home") {
            HomeScreen(onContinue={id,name->
                val encoded=URLEncoder.encode(name,StandardCharsets.UTF_8.toString())
                navController.navigate("question/$id/$encoded")
            },onBank={openTab("study")},onLearning={navController.navigate("learning")},
                onProfile={openTab("profile")},onFavorites={navController.navigate("favorites")},onSearch={navController.navigate("search")},onExam={navController.navigate("exam_list")},onSchedule={navController.navigate("schedule")})
        }
        composable("profile") {
            ProfileScreen(onSettings={navController.navigate("settings")},onAccount={navController.navigate("account_settings")},
                onSubscription={navController.navigate("subscription_plus")},onPublicProfile={navController.navigate("user_profile/me")},onSettingsAction={action->navController.navigate("settings?action=$action")},onScore={navController.navigate("tools/score_query")},
                onAdmin={
                    val token = if (authState != "loading") authState else ""
                    val adminUrl = "https://study.zhzgo.cn/admin?token=$token&role=admin"
                    val builder = androidx.browser.customtabs.CustomTabsIntent.Builder()

                    // Match app's dark mode aesthetic if possible
                    val colorSchemeParams = androidx.browser.customtabs.CustomTabColorSchemeParams.Builder()
                        .setToolbarColor(android.graphics.Color.parseColor("#121212"))
                        .build()
                    builder.setDefaultColorSchemeParams(colorSchemeParams)

                    val customTabsIntent = builder.build()
                    customTabsIntent.launchUrl(context, android.net.Uri.parse(adminUrl))
                })
        }
        composable("learning") {
            MistakesScreen(onBack={navController.navigateUp()},onSubject={id,name->
                val encoded=URLEncoder.encode(name,StandardCharsets.UTF_8.toString())
                navController.navigate("mistakes/$id/$encoded")
            },onReview={_,_,_->},onFavorites={navController.navigate("favorites")},onStats={openTab("stats")},
                onLeaderboard={navController.navigate("leaderboard")},onData={navController.navigate("data_center")})
        }
        composable("mistakes/{subjectId}/{subjectName}",arguments=listOf(navArgument("subjectId"){type=NavType.IntType},navArgument("subjectName"){type=NavType.StringType})) { entry ->
            val id=entry.arguments?.getInt("subjectId") ?: -1
            val name=URLDecoder.decode(entry.arguments?.getString("subjectName").orEmpty(),StandardCharsets.UTF_8.toString())
            MistakesScreen(subjectId=id,subjectName=name,onBack={navController.navigateUp()},onReview={subject,title,ids->
                val encoded=URLEncoder.encode(title,StandardCharsets.UTF_8.toString())
                val queue=cn.zhzgo.study.data.PracticeWorkflow.reviewQueue(ids).joinToString(",")
                navController.navigate("review/$subject/$encoded?ids=$queue")
            })
        }
        composable("review/{subjectId}/{subjectName}?ids={ids}",arguments=listOf(
            navArgument("subjectId"){type=NavType.IntType},navArgument("subjectName"){type=NavType.StringType},navArgument("ids"){type=NavType.StringType;defaultValue=""})) { entry ->
            val id=entry.arguments?.getInt("subjectId") ?: -1
            val name=URLDecoder.decode(entry.arguments?.getString("subjectName").orEmpty(),StandardCharsets.UTF_8.toString())
            val ids=remember(entry){entry.arguments?.getString("ids").orEmpty().split(",").mapNotNull{it.toIntOrNull()}.take(20)}
            QuestionScreen(subjectId=id,subjectName=name,isMistakes=true,reviewIds=ids,onBack={navController.navigateUp()},
                onNavigateToSubscription={navController.navigate("subscription_plus")},onNavigateToResult={_,_,_,_,_,_->navController.navigateUp()})
        }
        composable("common_links") {CommonLinksScreen(onBack={navController.navigateUp()},onLink=navigateToWebView)}

        composable("leaderboard") {
            LeaderboardScreen(
                onBack = { navController.popBackStack() },
                onUserClick = { userId ->
                    navController.navigate("user_profile/$userId")
                }
            )
        }

        // --- Content & Study Screens ---

        composable("study") {
            StudyScreen(
                onSubjectSelected = { subject, isMistakes ->
                    val encodedName = URLEncoder.encode(subject.name.ifEmpty { "Subject" }, StandardCharsets.UTF_8.toString())
                    navController.navigate("question/${subject.id}/$encodedName?isMistakes=$isMistakes")
                },
                onNavigateToSearch = {
                    navController.navigate("search")
                }
            )
        }

        composable(
            route = "question/{subjectId}/{subjectName}?isMistakes={isMistakes}",
            arguments = listOf(
                navArgument("subjectId") { type = NavType.IntType },
                navArgument("subjectName") { type = NavType.StringType },
                navArgument("isMistakes") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val subjectId = backStackEntry.arguments?.getInt("subjectId") ?: 0
            val subjectName = URLDecoder.decode(backStackEntry.arguments?.getString("subjectName") ?: "", StandardCharsets.UTF_8.toString())
            val isMistakes = backStackEntry.arguments?.getBoolean("isMistakes") ?: false

            QuestionScreen(
                subjectId = subjectId,
                subjectName = subjectName,
                isFavorites = false,
                isMistakes = isMistakes,
                onBack = { navController.navigateUp() },
                onNavigateToSubscription = { navController.navigate("subscription_plus") },
                onNavigateToResult = { score, max, correct, incorrect, unanswered, seconds ->
                    val encodedName = URLEncoder.encode(subjectName, StandardCharsets.UTF_8.toString())
                    navController.navigate("exercise_result/$subjectId/$encodedName/$score/$max/$correct/$incorrect/$unanswered?seconds=$seconds") {
                        popUpTo(backStackEntry.destination.id) { inclusive = true }
                    }
                }
            )
        }

        composable("resources") {
            ResourcesScreen(
                onNavigateToArticle = { articleId -> navController.navigate("article/$articleId") },
                onNavigateToCategory = { categoryId -> /* no-op or filter local */ },
                onNavigateToContribution = { navController.navigate("contribution") },
                onNavigateToTool = { toolRoute -> navController.navigate(toolRoute) },
                onNavigateToAllTools = { navController.navigate("tools") },
                onNavigateToLinks = { navController.navigate("common_links") }
            )
        }

        composable(
            route = "article/{articleId}",
            arguments = listOf(navArgument("articleId") { type = NavType.IntType })
        ) { backStackEntry ->
            val articleId = backStackEntry.arguments?.getInt("articleId") ?: 0
            ArticleDetailScreen(
                articleId = articleId,
                onBack = { navController.navigateUp() },
                onUserClick = { userId -> navController.navigate("user_profile/$userId") },
                onOpenLink = navigateToWebView
            )
        }

        composable(
            route = "user_profile/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            UserProfileScreen(
                userId = userId,
                onBack = { navController.navigateUp() },
                onArticleClick = { articleId -> navController.navigate("article/$articleId") },
                onNavigateToSubscription = { navController.navigate("subscription_plus") }
            )
        }

        composable("contribution") {
            ContributionScreen(
                onBack = { navController.navigateUp() }
            )
        }

        composable("favorites") {
            FavoritesScreen(
                onNavigateToPracticeMode = { ids ->
                    val queue=cn.zhzgo.study.data.PracticeWorkflow.reviewQueue(ids).joinToString(",")
                    navController.navigate("favorite_question?ids=$queue")
                },
                onBack={navController.navigateUp()}
            )
        }

        composable("favorite_question?ids={ids}",arguments=listOf(navArgument("ids"){type=NavType.StringType;defaultValue=""})) { entry ->
            val ids=remember(entry){entry.arguments?.getString("ids").orEmpty().split(",").mapNotNull{it.toIntOrNull()}}
            QuestionScreen(
                subjectId = 0,
                subjectName = "我的收藏",
                isFavorites = true,
                reviewIds = ids,
                onBack = { navController.navigateUp() },
                onNavigateToSubscription = { navController.navigate("subscription_plus") },
                onNavigateToResult = { score, max, correct, incorrect, unanswered, seconds ->
                    navController.navigate("exercise_result/0/我的收藏/$score/$max/$correct/$incorrect/$unanswered?seconds=$seconds") {
                        popUpTo(entry.destination.id) { inclusive = true }
                    }
                }
            )
        }

        // Deprecated admin_webview block removed according to user feedback

        // --- Settings & Metadata ---

        composable("settings?action={action}",arguments=listOf(navArgument("action"){type=NavType.StringType;defaultValue=""})) { settingsEntry ->
            SettingsScreen(initialAction=settingsEntry.arguments?.getString("action").orEmpty(),
                onBack = { navController.navigateUp() },
                onLogout = {
                    navController.navigate("login") {
                        popUpTo("home") { inclusive = true }
                    }
                },
                onNavigateToWebView = navigateToWebView,
                onNavigateToAccountSettings = {
                    navController.navigate("account_settings")
                },
                onNavigateToSubscription = {
                    navController.navigate("subscription_plus")
                }
            )
        }

        composable("account_settings") {
            AccountSettingsScreen(
                onBack = { navController.navigateUp() }
            )
        }

        composable("subscription_plus") {
            SubscriptionScreen(
                onBack = { navController.navigateUp() }
            )
        }

        composable("stats") {
            StatsScreen(
                root = true,
                onBack = { navController.navigateUp() }
            )
        }

        composable("data_center") {
            DataCenterScreen(
                onBack = { navController.navigateUp() }
            )
        }

        composable(
            route = "exercise_result/{subjectId}/{subjectName}/{score}/{maxScore}/{correct}/{incorrect}/{unanswered}?seconds={seconds}",
            arguments = listOf(
                navArgument("subjectId") { type = NavType.IntType },
                navArgument("subjectName") { type = NavType.StringType },
                navArgument("score") { type = NavType.IntType },
                navArgument("maxScore") { type = NavType.IntType },
                navArgument("correct") { type = NavType.IntType },
                navArgument("incorrect") { type = NavType.IntType },
                navArgument("unanswered") { type = NavType.IntType },
                navArgument("seconds") { type = NavType.IntType; defaultValue = 0 }
            )
        ) { backStackEntry ->
            val subjectId = backStackEntry.arguments?.getInt("subjectId") ?: 0
            val subjectName = URLDecoder.decode(backStackEntry.arguments?.getString("subjectName") ?: "", StandardCharsets.UTF_8.toString())
            val score = backStackEntry.arguments?.getInt("score") ?: 0
            val maxScore = backStackEntry.arguments?.getInt("maxScore") ?: 0
            val correct = backStackEntry.arguments?.getInt("correct") ?: 0
            val incorrect = backStackEntry.arguments?.getInt("incorrect") ?: 0
            val unanswered = backStackEntry.arguments?.getInt("unanswered") ?: 0

            ExerciseResultScreen(
                subjectId = subjectId,
                subjectName = subjectName,
                score = score,
                maxScore = maxScore,
                correctCount = correct,
                incorrectCount = incorrect,
                unansweredCount = unanswered,
                durationSeconds = backStackEntry.arguments?.getInt("seconds") ?: 0,
                onBack = {
                    if(!navController.popBackStack()) navController.navigate("home") { launchSingleTop=true }
                },
                onRetry = {
                    val encodedName = URLEncoder.encode(subjectName, StandardCharsets.UTF_8.toString())
                    val target=if(subjectId==0) "favorite_question" else "question/$subjectId/$encodedName"
                    navController.navigate(target) { popUpTo(backStackEntry.destination.id) { inclusive=true } }
                },
                onReviewMistakes = {
                    val encodedName = URLEncoder.encode(subjectName, StandardCharsets.UTF_8.toString())
                    val reviewSubject=subjectId.takeIf{it>0} ?: -1
                    navController.navigate("review/$reviewSubject/$encodedName") { popUpTo(backStackEntry.destination.id) { inclusive=true } }
                }
            )
        }

        composable(
            route = "webview/{url}/{title}",
            arguments = listOf(
                navArgument("url") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val url = URLDecoder.decode(backStackEntry.arguments?.getString("url") ?: "", StandardCharsets.UTF_8.toString())
            val title = URLDecoder.decode(backStackEntry.arguments?.getString("title") ?: "", StandardCharsets.UTF_8.toString())
            WebViewScreen(
                url = url,
                title = title,
                onBack = { navController.navigateUp() }
            )
        }

        // --- Tools Screens ---

        composable("tools") {
            ToolsListScreen(
                onNavigateToTool = { toolRoute ->
                    navController.navigate(toolRoute)
                },
                onBack = { navController.navigateUp() }
            )
        }

        composable("tools/base_converter") { BaseConverterScreen(onBack = { navController.navigateUp() }) }
        composable("tools/bmi_calculator") { BmiCalculatorScreen(onBack = { navController.navigateUp() }) }
        composable("tools/calculator") { CalculatorScreen(onBack = { navController.navigateUp() }) }
        composable("tools/color_picker") { ColorPickerScreen(onBack = { navController.navigateUp() }) }
        composable("tools/date_calculator") { DateCalculatorScreen(onBack = { navController.navigateUp() }) }
        composable("tools/dev_http_test") { DevHttpTestScreen(onBack = { navController.navigateUp() }) }
        composable("tools/dev_json_regex") { DevJsonRegexScreen(onBack = { navController.navigateUp() }) }
        composable("tools/doc_image_to_pdf") { DocImageToPdfScreen(onBack = { navController.navigateUp() }) }
        composable("tools/doc_markdown") { DocMarkdownScreen(onBack = { navController.navigateUp() }) }
        composable("tools/doc_pdf_to_image") { DocPdfToImageScreen(onBack = { navController.navigateUp() }) }
        composable("tools/image_compress") { ImageCompressScreen(onBack = { navController.navigateUp() }) }
        composable("tools/image_filter") { ImageFilterScreen(onBack = { navController.navigateUp() }) }
        composable("tools/image_segment") { ImageSegmentScreen(onBack = { navController.navigateUp() }) }
        composable("tools/image_watermark") { ImageWatermarkScreen(onBack = { navController.navigateUp() }) }
        composable("tools/kinship_calculator") { KinshipCalculatorScreen(onBack = { navController.navigateUp() }) }
        composable("tools/media_audio_format") { MediaAudioFormatScreen(onBack = { navController.navigateUp() }) }
        composable("tools/media_video_compress") { MediaVideoCompressScreen(onBack = { navController.navigateUp() }) }
        composable("tools/media_video_to_audio") { MediaVideoToAudioScreen(onBack = { navController.navigateUp() }) }
        composable("tools/password_generator") { PasswordGeneratorScreen(onBack = { navController.navigateUp() }) }
        composable("tools/random_generator") { RandomGeneratorScreen(onBack = { navController.navigateUp() }) }
        composable("tools/security_codec") { SecurityCodecScreen(onBack = { navController.navigateUp() }) }
        composable("tools/security_crypto") { SecurityCryptoScreen(onBack = { navController.navigateUp() }) }
        composable("tools/security_jwt") { SecurityJwtScreen(onBack = { navController.navigateUp() }) }
        composable("tools/stopwatch") { StopwatchScreen(onBack = { navController.navigateUp() }) }
        composable("tools/text_counter") { TextCounterScreen(onBack = { navController.navigateUp() }) }
        composable("tools/timestamp_converter") { TimestampConverterScreen(onBack = { navController.navigateUp() }) }
        composable("tools/unit_converter") { UnitConverterScreen(onBack = { navController.navigateUp() }) }
        composable("tools/video_parser") { VideoParserScreen(onBack = { navController.navigateUp() }) }
        composable("tools/score_query") { ScoreQueryScreen(onBack = { navController.navigateUp() }) }
        composable("schedule") { ScheduleScreen(onBack = { navController.navigateUp() }) }

        // --- Exam System ---
        composable("exam_list") {
            ExamListScreen(
                viewModel = examViewModel,
                onBack = { navController.navigateUp() },
                onNavigateToPrep = { paperId ->
                    navController.navigate("exam_prep/$paperId")
                },
                onNavigateToResult = { recordId ->
                    navController.navigate("exam_result/$recordId")
                }
            )
        }

        composable(
            route = "exam_prep/{paperId}",
            arguments = listOf(navArgument("paperId") { type = NavType.IntType })
        ) { backStackEntry ->
            val paperId = backStackEntry.arguments?.getInt("paperId") ?: 0
            ExamPrepScreen(
                paperId = paperId,
                viewModel = examViewModel,
                onBack = { navController.navigateUp() },
                onStartExam = { pId, recordId ->
                    navController.navigate("exam_taking/$pId/$recordId") {
                        popUpTo("exam_list") // Clean up the prep screen from backstack
                    }
                }
            )
        }

        composable(
            route = "exam_taking/{paperId}/{recordId}",
            arguments = listOf(
                navArgument("paperId") { type = NavType.IntType },
                navArgument("recordId") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val paperId = backStackEntry.arguments?.getInt("paperId") ?: 0
            val recordId = backStackEntry.arguments?.getInt("recordId") ?: 0
            ExamTakingScreen(
                paperId = paperId,
                recordId = recordId,
                viewModel = examViewModel,
                onExamFinished = { rId ->
                    navController.navigate("exam_result/$rId") {
                        popUpTo("exam_list")
                    }
                },
                onBack = {
                    navController.navigate("exam_list") {
                        popUpTo("exam_list") { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = "exam_result/{recordId}",
            arguments = listOf(navArgument("recordId") { type = NavType.IntType })
        ) { backStackEntry ->
            val recordId = backStackEntry.arguments?.getInt("recordId") ?: 0
            ExamResultScreen(
                recordId = recordId,
                viewModel = examViewModel,
                onBack = {
                    navController.navigate("exam_list") {
                        popUpTo("exam_list") { inclusive = true }
                    }
                }
            )
        }
    }
}

    if (showNetworkSupportDialog) {
        NetworkSupportDialog(onDismiss = { showNetworkSupportDialog = false })
    }
}

@Composable
fun NetworkSupportDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = {
            Text(
                "网络异常解决方案",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "如果您的网络不可用，请尝试：",
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text("• 检查 WiFi 或移动数据是否开启", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("• 尝试切换至其他稳定网络", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("• 检查是否开启了飞行模式", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("• 稍后重试或重启应用", color = MaterialTheme.colorScheme.onSurfaceVariant)

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Text(
                    "如需技术支持，请联系管理员：",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text("微信: cangshuowl", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("QQ: 2183649927", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("邮箱: 14329140@qq.com", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("好的", fontWeight = FontWeight.Bold)
            }
        }
    )
}
