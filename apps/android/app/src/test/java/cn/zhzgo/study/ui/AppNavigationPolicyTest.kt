package cn.zhzgo.study.ui

import org.junit.Assert.*
import org.junit.Test

class AppNavigationPolicyTest {
    @Test fun mainTabsCrossfadeButDetailsHaveDirection() {
        for (from in AppNavigationPolicy.mainRoutes) for (to in AppNavigationPolicy.mainRoutes)
            assertTrue(AppNavigationPolicy.isTabChange(from, to))
        assertFalse(AppNavigationPolicy.isTabChange("study", "question/{subjectId}/{subjectName}"))
        assertFalse(AppNavigationPolicy.isTabChange("article/{id}", "resources"))
    }
    @Test fun expiredSessionExitsProtectedRoutesButKeepsAuthFlow() {
        assertFalse(AppNavigationPolicy.needsLogin(null))
        for (route in listOf("login", "register", "bind_qq/{openId}")) assertFalse(AppNavigationPolicy.needsLogin(route))
        for (route in listOf("home", "review/{id}", "settings?action={action}", "subscription_plus")) assertTrue(AppNavigationPolicy.needsLogin(route))
    }
}
