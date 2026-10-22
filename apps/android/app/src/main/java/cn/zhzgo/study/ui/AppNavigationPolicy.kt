package cn.zhzgo.study.ui

/** Shared by bottom navigation and shortcuts so there is one stack per main tab. */
object AppNavigationPolicy {
    val mainRoutes = setOf("home", "study", "resources", "stats", "profile")
    private val authRoutes = setOf("login", "register", "bind_qq")
    const val FADE_MS = 140
    const val DETAIL_MS = 220

    fun root(route: String?) = route?.substringBefore('/')?.substringBefore('?')
    fun isTabChange(from: String?, to: String?) = root(from) in mainRoutes && root(to) in mainRoutes
    fun needsLogin(route: String?) = route != null && root(route) !in authRoutes
}
