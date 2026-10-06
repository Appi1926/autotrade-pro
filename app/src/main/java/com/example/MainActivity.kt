package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.data.local.AppDatabase
import com.example.data.repository.AlertRepository
import com.example.data.repository.AuthRepository
import com.example.data.repository.SessionStatus
import com.example.ui.navigation.Screen
import com.example.ui.screens.*
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppRoot()
            }
        }
    }
}

data class BottomNavItem(
    val title: String,
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun MainAppRoot() {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val navController = rememberNavController()

    // Initialize default mock users & portfolios in Room DB
    LaunchedEffect(Unit) {
        AuthRepository.initializeDefaultTraders(database)
        while (isActive) {
            delay(5000)
            AlertRepository.checkAndTriggerAlerts(database)
        }
    }

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    val bottomNavItems = listOf(
        BottomNavItem("Home", Screen.Dashboard.route, Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
        BottomNavItem("Watchlist", Screen.Watchlist.route, Icons.Filled.Bookmark, Icons.Outlined.BookmarkBorder, "nav_watchlist"),
        BottomNavItem("AI Copilot", Screen.AIAssistant.route, Icons.Filled.Psychology, Icons.Outlined.Psychology, "nav_ai_copilot"),
        BottomNavItem("News", Screen.News.route, Icons.Filled.Article, Icons.Outlined.Article, "nav_news"),
        BottomNavItem("Portfolio", Screen.Portfolio.route, Icons.Filled.PieChart, Icons.Outlined.PieChartOutline, "nav_portfolio")
    )

    val showBottomBar = currentRoute in bottomNavItems.map { it.route }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = BackgroundWarm,
        bottomBar = {
            if (showBottomBar) {
                Surface(
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = Color.White,
                    tonalElevation = 6.dp,
                    border = BorderStroke(1.dp, OutlineBorder)
                ) {
                    NavigationBar(
                        containerColor = Color.White,
                        tonalElevation = 0.dp,
                        modifier = Modifier.height(72.dp)
                    ) {
                        bottomNavItems.forEach { item ->
                            val isSelected = currentRoute == item.route
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    if (currentRoute != item.route) {
                                        navController.navigate(item.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.title,
                                        tint = if (isSelected) PrimaryPurple else TextSecondary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = item.title,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) PrimaryPurple else TextSecondary
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = PrimaryPurple,
                                    selectedTextColor = PrimaryPurple,
                                    indicatorColor = PrimaryContainer
                                ),
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Splash.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            // 1. Splash Screen
            composable(Screen.Splash.route) {
                SplashScreen(
                    onSplashFinished = {
                        val user = AuthRepository.currentUser.value
                        val sessionStatus = AuthRepository.sessionStatus.value
                        if (user != null && sessionStatus == SessionStatus.ACTIVE) {
                            navController.navigate(Screen.Dashboard.route) {
                                popUpTo(Screen.Splash.route) { inclusive = true }
                            }
                        } else {
                            navController.navigate(Screen.Login.route) {
                                popUpTo(Screen.Splash.route) { inclusive = true }
                            }
                        }
                    }
                )
            }

            // 2. Login Screen
            composable(Screen.Login.route) {
                LoginScreen(
                    database = database,
                    onLoginSuccess = { isAdmin ->
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    },
                    onNavigateToRegister = { navController.navigate(Screen.Register.route) },
                    onNavigateToForgotPassword = { navController.navigate(Screen.ForgotPassword.route) }
                )
            }

            // 3. Register Screen
            composable(Screen.Register.route) {
                RegisterScreen(
                    database = database,
                    onRegisterSuccess = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Register.route) { inclusive = true }
                        }
                    },
                    onNavigateToLogin = { navController.navigate(Screen.Login.route) }
                )
            }

            // 4. Forgot Password Screen
            composable(Screen.ForgotPassword.route) {
                ForgotPasswordScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // 5. Dashboard Screen
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    database = database,
                    onNavigateToStockDetails = { symbol -> navController.navigate(Screen.StockDetails.createRoute(symbol)) },
                    onNavigateToCharts = { symbol -> navController.navigate(Screen.Charts.createRoute(symbol)) },
                    onNavigateToPrediction = { symbol -> navController.navigate(Screen.AIPrediction.createRoute(symbol)) },
                    onNavigateToRecommendation = { symbol -> navController.navigate(Screen.Recommendation.createRoute(symbol)) },
                    onNavigateToPaperTrading = { symbol -> navController.navigate(Screen.PaperTrading.createRoute(symbol, "BUY")) },
                    onNavigateToPortfolio = { navController.navigate(Screen.Portfolio.route) },
                    onNavigateToSearch = { navController.navigate(Screen.StockSearch.route) },
                    onNavigateToAssistant = { navController.navigate(Screen.AIAssistant.route) },
                    onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                    onNavigateToNotifications = { navController.navigate(Screen.Notifications.route) },
                    onNavigateToBacktest = { symbol -> navController.navigate(Screen.Backtest.createRoute(symbol)) }
                )
            }

            // 6. Stock Search Screen
            composable(Screen.StockSearch.route) {
                StockSearchScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onSelectStock = { symbol -> navController.navigate(Screen.StockDetails.createRoute(symbol)) }
                )
            }

            // 7. Stock Details Screen
            composable(
                route = Screen.StockDetails.route,
                arguments = listOf(navArgument("symbol") { type = NavType.StringType })
            ) { backStack ->
                val symbol = backStack.arguments?.getString("symbol") ?: "HDFCBANK"
                StockDetailsScreen(
                    database = database,
                    symbol = symbol,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToCharts = { s -> navController.navigate(Screen.Charts.createRoute(s)) },
                    onNavigateToPrediction = { s -> navController.navigate(Screen.AIPrediction.createRoute(s)) },
                    onNavigateToRecommendation = { s -> navController.navigate(Screen.Recommendation.createRoute(s)) },
                    onNavigateToPaperTrading = { s, action -> navController.navigate(Screen.PaperTrading.createRoute(s, action)) },
                    onNavigateToBacktest = { s -> navController.navigate(Screen.Backtest.createRoute(s)) }
                )
            }

            // 8. Charts Screen
            composable(
                route = Screen.Charts.route,
                arguments = listOf(navArgument("symbol") { type = NavType.StringType })
            ) { backStack ->
                val symbol = backStack.arguments?.getString("symbol") ?: "HDFCBANK"
                ChartsScreen(
                    symbol = symbol,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // 9. AI Prediction Screen
            composable(
                route = Screen.AIPrediction.route,
                arguments = listOf(navArgument("symbol") { type = NavType.StringType })
            ) { backStack ->
                val symbol = backStack.arguments?.getString("symbol") ?: "HDFCBANK"
                AIPredictionScreen(
                    symbol = symbol,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToRecommendation = { s -> navController.navigate(Screen.Recommendation.createRoute(s)) }
                )
            }

            // 10. AI Recommendation Screen
            composable(
                route = Screen.Recommendation.route,
                arguments = listOf(navArgument("symbol") { type = NavType.StringType })
            ) { backStack ->
                val symbol = backStack.arguments?.getString("symbol") ?: "HDFCBANK"
                RecommendationScreen(
                    symbol = symbol,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToPaperTrading = { s, action -> navController.navigate(Screen.PaperTrading.createRoute(s, action)) }
                )
            }

            // 11. Paper Trading Screen
            composable(
                route = Screen.PaperTrading.route,
                arguments = listOf(
                    navArgument("symbol") { type = NavType.StringType },
                    navArgument("action") { type = NavType.StringType; defaultValue = "BUY" }
                )
            ) { backStack ->
                val symbol = backStack.arguments?.getString("symbol") ?: "HDFCBANK"
                val action = backStack.arguments?.getString("action") ?: "BUY"
                PaperTradingScreen(
                    database = database,
                    symbol = symbol,
                    initialAction = action,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToPortfolio = {
                        navController.navigate(Screen.Portfolio.route) {
                            popUpTo(Screen.Dashboard.route)
                        }
                    }
                )
            }

            // 12. Portfolio Screen
            composable(Screen.Portfolio.route) {
                PortfolioScreen(
                    database = database,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToStockDetails = { symbol -> navController.navigate(Screen.StockDetails.createRoute(symbol)) },
                    onNavigateToPaperTrading = { symbol, action -> navController.navigate(Screen.PaperTrading.createRoute(symbol, action)) }
                )
            }

            // 13. Watchlist Screen
            composable(Screen.Watchlist.route) {
                WatchlistScreen(
                    database = database,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToStockDetails = { symbol -> navController.navigate(Screen.StockDetails.createRoute(symbol)) },
                    onNavigateToSearch = { navController.navigate(Screen.StockSearch.route) }
                )
            }

            // 14. News & Sentiment Screen
            composable(Screen.News.route) {
                NewsSentimentScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onSelectStock = { symbol -> navController.navigate(Screen.StockDetails.createRoute(symbol)) }
                )
            }

            // 15. AI Assistant Screen
            composable(Screen.AIAssistant.route) {
                AIAssistantScreen(
                    database = database,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // 16. Notifications Screen
            composable(Screen.Notifications.route) {
                NotificationsScreen(
                    database = database,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // 17. Profile Screen
            composable(Screen.Profile.route) {
                ProfileScreen(
                    database = database,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    onNavigateToAdmin = { navController.navigate(Screen.AdminDashboard.route) },
                    onLogout = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            // 18. Settings Screen
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // 19. Admin Dashboard Screen
            composable(Screen.AdminDashboard.route) {
                AdminDashboardScreen(
                    database = database,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // 20. Backtest Screen
            composable(
                route = Screen.Backtest.route,
                arguments = listOf(navArgument("symbol") { type = NavType.StringType; defaultValue = "RELIANCE" })
            ) { backStack ->
                val symbol = backStack.arguments?.getString("symbol") ?: "RELIANCE"
                BacktestScreen(
                    initialSymbol = symbol,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToPaperTrading = { s, action -> navController.navigate(Screen.PaperTrading.createRoute(s, action)) }
                )
            }
        }
    }
}
