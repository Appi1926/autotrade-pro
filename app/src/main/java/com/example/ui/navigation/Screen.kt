package com.example.ui.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Register : Screen("register")
    object ForgotPassword : Screen("forgot_password")
    object Dashboard : Screen("dashboard")
    object StockSearch : Screen("stock_search")
    object StockDetails : Screen("stock_details/{symbol}") {
        fun createRoute(symbol: String) = "stock_details/$symbol"
    }
    object Charts : Screen("charts/{symbol}") {
        fun createRoute(symbol: String) = "charts/$symbol"
    }
    object AIPrediction : Screen("ai_prediction/{symbol}") {
        fun createRoute(symbol: String) = "ai_prediction/$symbol"
    }
    object Recommendation : Screen("recommendation/{symbol}") {
        fun createRoute(symbol: String) = "recommendation/$symbol"
    }
    object PaperTrading : Screen("paper_trading/{symbol}/{action}") {
        fun createRoute(symbol: String, action: String = "BUY") = "paper_trading/$symbol/$action"
    }
    object Portfolio : Screen("portfolio")
    object Watchlist : Screen("watchlist")
    object News : Screen("news")
    object AIAssistant : Screen("ai_assistant")
    object Notifications : Screen("notifications")
    object Profile : Screen("profile")
    object Settings : Screen("settings")
    object AdminDashboard : Screen("admin_dashboard")
    object Backtest : Screen("backtest/{symbol}") {
        fun createRoute(symbol: String = "RELIANCE") = "backtest/$symbol"
    }
}
