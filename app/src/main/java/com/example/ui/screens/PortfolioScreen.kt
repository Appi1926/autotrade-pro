package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AppDatabase
import com.example.data.local.HoldingEntity
import com.example.data.local.TransactionEntity
import com.example.data.remote.LiveTradingGateway
import com.example.data.repository.AuthRepository
import com.example.data.repository.TradingRepository
import com.example.ui.components.AssetAllocationPieChart
import com.example.ui.components.CleanCard
import com.example.ui.components.PulsingDot
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class PositionFilter {
    ALL, PROFIT, LOSS
}

enum class TradeStatusFilter {
    ALL, COMPLETED, CANCELLED, BUY, SELL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(
    database: AppDatabase,
    onNavigateBack: () -> Unit,
    onNavigateToStockDetails: (String) -> Unit,
    onNavigateToPaperTrading: (String, String) -> Unit
) {
    val user by AuthRepository.currentUser.collectAsState()
    val userId = user?.id ?: ""

    val portfolio by TradingRepository.observePortfolio(database, userId).collectAsState(initial = null)
    val holdings by TradingRepository.observeHoldings(database, userId).collectAsState(initial = emptyList())
    val transactions by TradingRepository.observeTransactions(database, userId).collectAsState(initial = emptyList())

    // Seeding sample transactions and active positions if empty so user has instant rich data
    LaunchedEffect(userId) {
        if (userId.isNotEmpty()) {
            TradingRepository.seedSampleTradesIfEmpty(database, userId)
        }
    }

    var portfolioMode by remember { mutableStateOf(0) } // 0: Paper Portfolio, 1: Live Demat Portfolio
    val isLiveAuth by LiveTradingGateway.isAngelOneAuthenticated.collectAsState()
    val isExpired by LiveTradingGateway.isSessionExpired.collectAsState()
    val liveClientCode by LiveTradingGateway.liveClientCode.collectAsState()
    val liveBalance by LiveTradingGateway.liveAccountBalance.collectAsState()
    val liveHoldings by LiveTradingGateway.liveHoldings.collectAsState()
    val livePositions by LiveTradingGateway.livePositions.collectAsState()
    val liveOrders by LiveTradingGateway.liveOrders.collectAsState()
    var showLiveConnectDialog by remember { mutableStateOf(false) }
    var selectedLiveTab by remember { mutableStateOf(0) }

    var selectedTab by remember { mutableStateOf(0) } // 0: Positions, 1: Trade History, 2: Allocation & Stats
    var isOverviewExpanded by remember { mutableStateOf(true) }

    // Position tab state
    var positionFilter by remember { mutableStateOf(PositionFilter.ALL) }
    var positionSearchQuery by remember { mutableStateOf("") }

    // Trade History tab state
    var tradeFilter by remember { mutableStateOf(TradeStatusFilter.ALL) }
    var tradeSearchQuery by remember { mutableStateOf("") }
    var selectedTradeForReceipt by remember { mutableStateOf<TransactionEntity?>(null) }
    var showSimulateCancelDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    // Virtual Capital Metrics
    val initialSeedCapital = 1000000.0 // ₹10,00,000 initial paper trading allocation
    val virtualCash = user?.virtualBalance ?: 1000000.0
    val totalEquity = holdings.sumOf { it.currentValue }
    val totalInvested = holdings.sumOf { it.investedValue }
    val totalPortfolioVal = virtualCash + totalEquity
    val totalUnrealizedPnl = totalEquity - totalInvested
    val totalUnrealizedPnlPct = if (totalInvested > 0) (totalUnrealizedPnl / totalInvested) * 100.0 else 0.0
    val overallReturnOnSeed = ((totalPortfolioVal - initialSeedCapital) / initialSeedCapital) * 100.0

    // Filtered Holdings
    val filteredHoldings = remember(holdings, positionFilter, positionSearchQuery) {
        holdings.filter { h ->
            val matchesQuery = h.symbol.contains(positionSearchQuery, ignoreCase = true) ||
                    h.name.contains(positionSearchQuery, ignoreCase = true)
            val matchesFilter = when (positionFilter) {
                PositionFilter.ALL -> true
                PositionFilter.PROFIT -> h.profitLoss >= 0
                PositionFilter.LOSS -> h.profitLoss < 0
            }
            matchesQuery && matchesFilter
        }
    }
    val profitablePositionsCount = holdings.count { it.profitLoss >= 0 }
    val losingPositionsCount = holdings.count { it.profitLoss < 0 }

    // Filtered Transactions for Trade History Tab
    val filteredTransactions = remember(transactions, tradeFilter, tradeSearchQuery) {
        transactions.filter { tx ->
            val matchesQuery = tx.symbol.contains(tradeSearchQuery, ignoreCase = true) ||
                    tx.name.contains(tradeSearchQuery, ignoreCase = true) ||
                    tx.id.contains(tradeSearchQuery, ignoreCase = true)
            val isCompleted = tx.status.equals("COMPLETED", ignoreCase = true) || tx.status.equals("EXECUTED", ignoreCase = true)
            val isCancelled = tx.status.equals("CANCELLED", ignoreCase = true)
            val matchesFilter = when (tradeFilter) {
                TradeStatusFilter.ALL -> true
                TradeStatusFilter.COMPLETED -> isCompleted
                TradeStatusFilter.CANCELLED -> isCancelled
                TradeStatusFilter.BUY -> tx.type.equals("BUY", ignoreCase = true)
                TradeStatusFilter.SELL -> tx.type.equals("SELL", ignoreCase = true)
            }
            matchesQuery && matchesFilter
        }
    }
    val completedCount = transactions.count { it.status.equals("COMPLETED", ignoreCase = true) || it.status.equals("EXECUTED", ignoreCase = true) }
    val cancelledCount = transactions.count { it.status.equals("CANCELLED", ignoreCase = true) }
    val buyCount = transactions.count { it.type.equals("BUY", ignoreCase = true) }
    val sellCount = transactions.count { it.type.equals("SELL", ignoreCase = true) }
    val completedTurnover = transactions
        .filter { it.status.equals("COMPLETED", ignoreCase = true) || it.status.equals("EXECUTED", ignoreCase = true) }
        .sumOf { it.totalAmount }

    Scaffold(
        containerColor = BackgroundWarm,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Portfolio Tracker",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "Active Paper Trading & Trade History",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("portfolio_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { isOverviewExpanded = !isOverviewExpanded },
                        modifier = Modifier.testTag("portfolio_toggle_overview_button")
                    ) {
                        Icon(
                            imageVector = if (isOverviewExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isOverviewExpanded) "Collapse Summary" else "Expand Summary",
                            tint = PrimaryPurple
                        )
                    }
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                TradingRepository.recalculatePortfolio(database, userId)
                            }
                        },
                        modifier = Modifier.testTag("portfolio_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Portfolio",
                            tint = PrimaryPurple
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundWarm)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 1. Collapsible Portfolio Overview Header (Hero M3 Card + Dual Metric Cards)
            AnimatedVisibility(
                visible = isOverviewExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Hero Material 3 Card: Unrealized P&L and Portfolio Value
                    ElevatedCard(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.elevatedCardColors(containerColor = PrimaryPurple),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("portfolio_hero_m3_card")
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            PrimaryPurple,
                                            Color(0xFF4F378B),
                                            Color(0xFF381E72)
                                        )
                                    )
                                )
                                .padding(18.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "TOTAL PORTFOLIO VALUE",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.2.sp,
                                            color = Color.White.copy(alpha = 0.75f)
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = "₹${String.format("%,.2f", totalPortfolioVal)}",
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color.White.copy(alpha = 0.15f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .clip(CircleShape)
                                                    .background(if (totalUnrealizedPnl >= 0) GainGreen else LossRed)
                                            )
                                            Text(
                                                text = "Paper Trading",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                                HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                                Spacer(modifier = Modifier.height(10.dp))

                                // Unrealized Profit & Loss Highlight Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Total Unrealized P&L",
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.8f)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        val isGain = totalUnrealizedPnl >= 0
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isGain) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                                                contentDescription = null,
                                                tint = if (isGain) Color(0xFF86EFAC) else Color(0xFFFCA5A5),
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Text(
                                                text = "${if (isGain) "+" else ""}₹${String.format("%,.2f", totalUnrealizedPnl)}",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Black,
                                                color = if (isGain) Color(0xFF86EFAC) else Color(0xFFFCA5A5)
                                            )
                                            Text(
                                                text = "(${if (isGain) "+" else ""}${String.format("%.2f", totalUnrealizedPnlPct)}%)",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isGain) Color(0xFF86EFAC) else Color(0xFFFCA5A5)
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color.White.copy(alpha = 0.15f)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            horizontalAlignment = Alignment.End
                                        ) {
                                            Text(
                                                text = "Overall ROI",
                                                fontSize = 9.sp,
                                                color = Color.White.copy(alpha = 0.7f)
                                            )
                                            Text(
                                                text = "${if (overallReturnOnSeed >= 0) "+" else ""}${String.format("%.2f", overallReturnOnSeed)}%",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (overallReturnOnSeed >= 0) Color(0xFF86EFAC) else Color(0xFFFCA5A5)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Material 3 Dual Metric Cards: Initial Capital vs Capital Invested
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedCard(
                            modifier = Modifier
                                .weight(1f)
                                .testTag("initial_capital_card"),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.outlinedCardColors(containerColor = SurfaceWhite),
                            border = BorderStroke(1.dp, OutlineBorder)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = PrimaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.AccountBalance,
                                                contentDescription = null,
                                                tint = PrimaryPurple,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Initial Capital",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "₹${String.format("%,.0f", initialSeedCapital)}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Cash: ₹${String.format("%,.0f", virtualCash)}",
                                    fontSize = 9.sp,
                                    color = TextTertiary
                                )
                            }
                        }

                        OutlinedCard(
                            modifier = Modifier
                                .weight(1f)
                                .testTag("capital_invested_card"),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.outlinedCardColors(containerColor = SurfaceWhite),
                            border = BorderStroke(1.dp, OutlineBorder)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = GainGreenLight,
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ShowChart,
                                                contentDescription = null,
                                                tint = GainGreen,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Active Invested",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "₹${String.format("%,.2f", totalInvested)}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                val marginUsedPct = if (initialSeedCapital > 0) (totalInvested / initialSeedCapital) * 100.0 else 0.0
                                Text(
                                    text = "${String.format("%.1f", marginUsedPct)}% Deployed",
                                    fontSize = 9.sp,
                                    color = PrimaryPurple,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Compact summary bar when overview is collapsed
            AnimatedVisibility(
                visible = !isOverviewExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    color = SurfaceWhite,
                    border = BorderStroke(1.dp, OutlineBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { isOverviewExpanded = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Portfolio: ₹${String.format("%,.2f", totalPortfolioVal)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            val isGain = totalUnrealizedPnl >= 0
                            Text(
                                text = "${if (isGain) "+" else ""}₹${String.format("%,.0f", totalUnrealizedPnl)} (${String.format("%.1f", totalUnrealizedPnlPct)}%)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isGain) GainGreen else LossRed
                            )
                        }
                        Text(
                            text = "Expand ▲",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryPurple
                        )
                    }
                }
            }

            // 2. Pinned Material 3 Navigation Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = BackgroundWarm,
                contentColor = PrimaryPurple,
                divider = { HorizontalDivider(color = OutlineBorder.copy(alpha = 0.5f)) },
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.testTag("portfolio_tab_positions"),
                    text = {
                        Text(
                            text = "Positions (${holdings.size})",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.testTag("portfolio_tab_trade_history"),
                    text = {
                        Text(
                            text = "Trade History (${transactions.size})",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    modifier = Modifier.testTag("portfolio_tab_allocation"),
                    text = {
                        Text(
                            text = "Allocation & Stats",
                            fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp
                        )
                    }
                )
            }

            // 3. Tab Contents with Dedicated LazyColumns
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (selectedTab) {
                    // TAB 0: Active Paper Trading Positions (LazyColumn)
                    0 -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp)
                                .testTag("portfolio_positions_lazy_column"),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
                        ) {
                            // Filter & Search Controls
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = positionSearchQuery,
                                        onValueChange = { positionSearchQuery = it },
                                        placeholder = { Text("Search active positions...", fontSize = 13.sp, color = TextTertiary) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Search, contentDescription = "Search", tint = TextSecondary)
                                        },
                                        trailingIcon = {
                                            if (positionSearchQuery.isNotEmpty()) {
                                                IconButton(onClick = { positionSearchQuery = "" }) {
                                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary)
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = PrimaryPurple,
                                            unfocusedBorderColor = OutlineBorder,
                                            focusedContainerColor = SurfaceWhite,
                                            unfocusedContainerColor = SurfaceWhite
                                        ),
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("portfolio_search_input")
                                    )

                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        item {
                                            FilterChip(
                                                selected = positionFilter == PositionFilter.ALL,
                                                onClick = { positionFilter = PositionFilter.ALL },
                                                label = { Text("All (${holdings.size})", fontSize = 11.sp) },
                                                shape = RoundedCornerShape(50),
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = PrimaryContainer,
                                                    selectedLabelColor = PrimaryPurple
                                                )
                                            )
                                        }
                                        item {
                                            FilterChip(
                                                selected = positionFilter == PositionFilter.PROFIT,
                                                onClick = { positionFilter = PositionFilter.PROFIT },
                                                label = { Text("In Profit ($profitablePositionsCount)", fontSize = 11.sp) },
                                                shape = RoundedCornerShape(50),
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = GainGreenLight,
                                                    selectedLabelColor = GainGreen
                                                )
                                            )
                                        }
                                        item {
                                            FilterChip(
                                                selected = positionFilter == PositionFilter.LOSS,
                                                onClick = { positionFilter = PositionFilter.LOSS },
                                                label = { Text("In Loss ($losingPositionsCount)", fontSize = 11.sp) },
                                                shape = RoundedCornerShape(50),
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = LossRedLight,
                                                    selectedLabelColor = LossRed
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            if (filteredHoldings.isEmpty()) {
                                item {
                                    OutlinedCard(
                                        shape = RoundedCornerShape(24.dp),
                                        colors = CardDefaults.outlinedCardColors(containerColor = SurfaceWhite),
                                        border = BorderStroke(1.dp, OutlineBorder),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(32.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = PrimaryContainer,
                                                modifier = Modifier.size(56.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Default.WorkOutline,
                                                        contentDescription = null,
                                                        tint = PrimaryPurple,
                                                        modifier = Modifier.size(28.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = if (holdings.isEmpty()) "No Active Paper Positions" else "No matching positions found",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = if (holdings.isEmpty()) "Execute paper buy orders from the Market tab to start tracking active positions." else "Try adjusting your filter or search query.",
                                                fontSize = 12.sp,
                                                color = TextSecondary,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(filteredHoldings, key = { it.id }) { holding ->
                                    PositionCardM3(
                                        holding = holding,
                                        onStockClick = { onNavigateToStockDetails(holding.symbol) },
                                        onBuyMoreClick = { onNavigateToPaperTrading(holding.symbol, "BUY") },
                                        onSellClick = { onNavigateToPaperTrading(holding.symbol, "SELL") }
                                    )
                                }
                            }
                        }
                    }

                    // TAB 1: Trade History Tab (USING LAZYCOLUMN WITH COMPLETED & CANCELLED STATUS TAGS)
                    1 -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp)
                                .testTag("trade_history_lazy_column"),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
                        ) {
                            // Search Bar for Trade History
                            item {
                                OutlinedTextField(
                                    value = tradeSearchQuery,
                                    onValueChange = { tradeSearchQuery = it },
                                    placeholder = { Text("Search by symbol, company, or order ID...", fontSize = 13.sp, color = TextTertiary) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Search, contentDescription = "Search", tint = TextSecondary)
                                    },
                                    trailingIcon = {
                                        if (tradeSearchQuery.isNotEmpty()) {
                                            IconButton(onClick = { tradeSearchQuery = "" }) {
                                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary)
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PrimaryPurple,
                                        unfocusedBorderColor = OutlineBorder,
                                        focusedContainerColor = SurfaceWhite,
                                        unfocusedContainerColor = SurfaceWhite
                                    ),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("trade_history_search_input")
                                )
                            }

                            // Filter Chips Row: All, Completed, Cancelled, BUY, SELL
                            item {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    item {
                                        FilterChip(
                                            selected = tradeFilter == TradeStatusFilter.ALL,
                                            onClick = { tradeFilter = TradeStatusFilter.ALL },
                                            label = { Text("All (${transactions.size})", fontSize = 11.sp) },
                                            shape = RoundedCornerShape(50),
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = PrimaryContainer,
                                                selectedLabelColor = PrimaryPurple
                                            ),
                                            modifier = Modifier.testTag("filter_all_trades")
                                        )
                                    }
                                    item {
                                        FilterChip(
                                            selected = tradeFilter == TradeStatusFilter.COMPLETED,
                                            onClick = { tradeFilter = TradeStatusFilter.COMPLETED },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = GainGreen,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            },
                                            label = { Text("Completed ($completedCount)", fontSize = 11.sp) },
                                            shape = RoundedCornerShape(50),
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = GainGreenLight,
                                                selectedLabelColor = GainGreen
                                            ),
                                            modifier = Modifier.testTag("filter_completed_trades")
                                        )
                                    }
                                    item {
                                        FilterChip(
                                            selected = tradeFilter == TradeStatusFilter.CANCELLED,
                                            onClick = { tradeFilter = TradeStatusFilter.CANCELLED },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Cancel,
                                                    contentDescription = null,
                                                    tint = LossRed,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            },
                                            label = { Text("Cancelled ($cancelledCount)", fontSize = 11.sp) },
                                            shape = RoundedCornerShape(50),
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = LossRedLight,
                                                selectedLabelColor = LossRed
                                            ),
                                            modifier = Modifier.testTag("filter_cancelled_trades")
                                        )
                                    }
                                    item {
                                        FilterChip(
                                            selected = tradeFilter == TradeStatusFilter.BUY,
                                            onClick = { tradeFilter = TradeStatusFilter.BUY },
                                            label = { Text("BUY ($buyCount)", fontSize = 11.sp) },
                                            shape = RoundedCornerShape(50),
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = PrimaryContainer,
                                                selectedLabelColor = PrimaryPurple
                                            )
                                        )
                                    }
                                    item {
                                        FilterChip(
                                            selected = tradeFilter == TradeStatusFilter.SELL,
                                            onClick = { tradeFilter = TradeStatusFilter.SELL },
                                            label = { Text("SELL ($sellCount)", fontSize = 11.sp) },
                                            shape = RoundedCornerShape(50),
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = PrimaryContainer,
                                                selectedLabelColor = PrimaryPurple
                                            )
                                        )
                                    }
                                }
                            }

                            // Trade History Summary & Telemetry Card
                            item {
                                OutlinedCard(
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.outlinedCardColors(containerColor = SurfaceWhite),
                                    border = BorderStroke(1.dp, OutlineBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("trade_history_summary_card")
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.History,
                                                    contentDescription = null,
                                                    tint = PrimaryPurple,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    text = "EXECUTION SUMMARY",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    letterSpacing = 1.sp,
                                                    color = TextSecondary
                                                )
                                            }

                                            OutlinedButton(
                                                onClick = { showSimulateCancelDialog = true },
                                                shape = RoundedCornerShape(10.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                border = BorderStroke(1.dp, PrimaryPurple.copy(alpha = 0.5f)),
                                                modifier = Modifier.testTag("simulate_cancel_order_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = null,
                                                    tint = PrimaryPurple,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Test Cancelled",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = PrimaryPurple
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text("Total Executed", fontSize = 10.sp, color = TextTertiary)
                                                Text("$completedCount Trades", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = GainGreen)
                                            }
                                            Column {
                                                Text("Cancelled", fontSize = 10.sp, color = TextTertiary)
                                                Text("$cancelledCount Orders", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = LossRed)
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("Completed Volume", fontSize = 10.sp, color = TextTertiary)
                                                Text("₹${String.format("%,.2f", completedTurnover)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                            }
                                        }
                                    }
                                }
                            }

                            // Empty State when no transactions match
                            if (filteredTransactions.isEmpty()) {
                                item {
                                    OutlinedCard(
                                        shape = RoundedCornerShape(24.dp),
                                        colors = CardDefaults.outlinedCardColors(containerColor = SurfaceWhite),
                                        border = BorderStroke(1.dp, OutlineBorder),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(32.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = PrimaryContainer,
                                                modifier = Modifier.size(56.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Default.ReceiptLong,
                                                        contentDescription = null,
                                                        tint = PrimaryPurple,
                                                        modifier = Modifier.size(28.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = if (transactions.isEmpty()) "No Paper Trade History Yet" else "No matching transactions found",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = if (transactions.isEmpty())
                                                    "Place your first paper order to track executed and cancelled orders with live status tags."
                                                else
                                                    "Try clearing your search query or switching the status filter tab.",
                                                fontSize = 12.sp,
                                                color = TextSecondary,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )

                                            if (tradeFilter != TradeStatusFilter.ALL || tradeSearchQuery.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(14.dp))
                                                OutlinedButton(
                                                    onClick = {
                                                        tradeFilter = TradeStatusFilter.ALL
                                                        tradeSearchQuery = ""
                                                    },
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Text("Reset Filters", fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Listing each past paper trading transaction with status tags
                                items(filteredTransactions, key = { it.id }) { tx ->
                                    TradeTransactionCardM3(
                                        tx = tx,
                                        onClick = { selectedTradeForReceipt = tx },
                                        onTradeAgain = { onNavigateToPaperTrading(tx.symbol, tx.type) }
                                    )
                                }
                            }
                        }
                    }

                    // TAB 2: Asset Allocation & Performance Summary (LazyColumn)
                    2 -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp)
                                .testTag("portfolio_allocation_lazy_column"),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
                        ) {
                            item {
                                OutlinedCard(
                                    shape = RoundedCornerShape(24.dp),
                                    colors = CardDefaults.outlinedCardColors(containerColor = SurfaceWhite),
                                    border = BorderStroke(1.dp, OutlineBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(20.dp)) {
                                        Text(
                                            text = "PORTFOLIO CAPITAL ALLOCATION",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp,
                                            color = TextSecondary
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        AssetAllocationPieChart(holdings = holdings, virtualCash = virtualCash)
                                    }
                                }
                            }

                            item {
                                OutlinedCard(
                                    shape = RoundedCornerShape(24.dp),
                                    colors = CardDefaults.outlinedCardColors(containerColor = SurfaceWhite),
                                    border = BorderStroke(1.dp, OutlineBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Text(
                                            text = "RISK & EXECUTION TELEMETRY",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp,
                                            color = TextSecondary
                                        )

                                        MetricRow(label = "Initial Virtual Seed Fund", value = "₹${String.format("%,.2f", initialSeedCapital)}")
                                        MetricRow(label = "Total Active Equity Value", value = "₹${String.format("%,.2f", totalEquity)}")
                                        MetricRow(label = "Liquid Cash in Reserve", value = "₹${String.format("%,.2f", virtualCash)}")
                                        MetricRow(label = "Total Active Positions", value = "${holdings.size} Stocks")
                                        MetricRow(label = "Total Completed Trades", value = "$completedCount Orders")
                                        MetricRow(label = "Total Cancelled Orders", value = "$cancelledCount Orders")
                                        MetricRow(
                                            label = "Win / Loss Ratio",
                                            value = if (holdings.isNotEmpty()) "$profitablePositionsCount Win / $losingPositionsCount Loss" else "N/A"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Trade Receipt & Order Details Dialog
    if (selectedTradeForReceipt != null) {
        val tx = selectedTradeForReceipt!!
        TradeReceiptDialog(
            transaction = tx,
            onDismiss = { selectedTradeForReceipt = null },
            onTradeAgain = {
                selectedTradeForReceipt = null
                onNavigateToPaperTrading(tx.symbol, tx.type)
            }
        )
    }

    // Dialog to simulate/record a cancelled order for testing
    if (showSimulateCancelDialog) {
        SimulateCancelOrderDialog(
            onDismiss = { showSimulateCancelDialog = false },
            onConfirm = { symbol, name, type, qty, price, reason ->
                coroutineScope.launch {
                    TradingRepository.recordCancelledOrder(
                        database = database,
                        userId = userId,
                        symbol = symbol,
                        name = name,
                        type = type,
                        quantity = qty,
                        price = price,
                        reason = reason
                    )
                }
                showSimulateCancelDialog = false
            }
        )
    }
}

/**
 * Status tag for paper trading transactions: Displays 'Completed' or 'Cancelled'
 * with distinct styling, icons, and testTags.
 */
@Composable
fun TradeStatusTag(
    status: String,
    transactionId: String,
    modifier: Modifier = Modifier
) {
    val isCancelled = status.equals("CANCELLED", ignoreCase = true)
    val containerColor = if (isCancelled) LossRedLight else GainGreenLight
    val contentColor = if (isCancelled) LossRed else GainGreen
    val borderColor = if (isCancelled) LossRed.copy(alpha = 0.35f) else GainGreen.copy(alpha = 0.35f)
    val label = if (isCancelled) "Cancelled" else "Completed"
    val icon = if (isCancelled) Icons.Default.Cancel else Icons.Default.CheckCircle
    val testTagVal = if (isCancelled) "status_tag_cancelled_$transactionId" else "status_tag_completed_$transactionId"

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.testTag(testTagVal)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = contentColor
            )
        }
    }
}

/**
 * Material 3 Card for a Paper Trading Transaction in the Trade History tab
 */
@Composable
fun TradeTransactionCardM3(
    tx: TransactionEntity,
    onClick: () -> Unit,
    onTradeAgain: () -> Unit
) {
    val dateStr = remember(tx.timestamp) {
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(tx.timestamp))
    }
    val isBuy = tx.type.equals("BUY", ignoreCase = true)
    val isCancelled = tx.status.equals("CANCELLED", ignoreCase = true)

    OutlinedCard(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = SurfaceWhite),
        border = BorderStroke(1.dp, OutlineBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("trade_transaction_card_${tx.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Type Badge, Symbol, Product Tag, and STATUS TAG
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Type Badge (BUY / SELL)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isBuy) GainGreenLight else LossRedLight
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = if (isBuy) Icons.Default.NorthEast else Icons.Default.SouthEast,
                                contentDescription = null,
                                tint = if (isBuy) GainGreen else LossRed,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = tx.type,
                                color = if (isBuy) GainGreen else LossRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    Text(
                        text = tx.symbol,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = PrimaryContainer
                    ) {
                        Text(
                            text = "PAPER • NSE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryPurple,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Dedicated Status Tag for 'Completed' or 'Cancelled'
                TradeStatusTag(status = tx.status, transactionId = tx.id)
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = tx.name,
                fontSize = 11.sp,
                color = TextSecondary,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = OutlineBorder.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))

            // Financial Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isCancelled) "Order Price" else "Execution Price",
                        fontSize = 10.sp,
                        color = TextTertiary
                    )
                    Text(
                        text = "₹${String.format("%,.2f", tx.price)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${tx.quantity} shares",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (isCancelled) "Cancelled Total Value" else "Total Traded Amount",
                        fontSize = 10.sp,
                        color = TextTertiary
                    )
                    Text(
                        text = "₹${String.format("%,.2f", tx.totalAmount)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isCancelled) TextSecondary else TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Zero Brokerage • Paper Trade",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GainGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = OutlineBorder.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(8.dp))

            // Footer Row: Timestamp, Order ID & Quick Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = dateStr,
                        fontSize = 10.sp,
                        color = TextTertiary
                    )
                    Text(
                        text = "• #${tx.id.takeLast(7)}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryPurple
                    )
                }

                TextButton(
                    onClick = onTradeAgain,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.testTag("trade_again_${tx.id}")
                ) {
                    Text("Trade Again", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryPurple)
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = PrimaryPurple,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

/**
 * Detailed Order Receipt / Transaction Modal Dialog
 */
@Composable
fun TradeReceiptDialog(
    transaction: TransactionEntity,
    onDismiss: () -> Unit,
    onTradeAgain: () -> Unit
) {
    val dateStr = remember(transaction.timestamp) {
        SimpleDateFormat("dd MMMM yyyy, hh:mm:ss a", Locale.getDefault()).format(Date(transaction.timestamp))
    }
    val isBuy = transaction.type.equals("BUY", ignoreCase = true)
    val isCancelled = transaction.status.equals("CANCELLED", ignoreCase = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Order Receipt",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextPrimary
                )
                TradeStatusTag(status = transaction.status, transactionId = transaction.id)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "${transaction.name} (${transaction.symbol})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )

                HorizontalDivider(color = OutlineBorder)

                MetricRow(label = "Order ID", value = transaction.id)
                MetricRow(label = "Transaction Type", value = "${transaction.type} • PAPER DELIV.")
                MetricRow(label = "Order Status", value = if (isCancelled) "Cancelled" else "Completed")
                MetricRow(label = "Execution Price", value = "₹${String.format("%,.2f", transaction.price)}")
                MetricRow(label = "Quantity", value = "${transaction.quantity} shares")
                MetricRow(label = "Total Value", value = "₹${String.format("%,.2f", transaction.totalAmount)}")
                MetricRow(label = "Brokerage & Taxes", value = "₹0.00 (Paper Simulation)")
                MetricRow(label = "Execution Timestamp", value = dateStr)

                HorizontalDivider(color = OutlineBorder)

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = PrimaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isCancelled)
                            "ℹ️ This order was cancelled before execution. No funds were debited from your paper trading balance."
                        else
                            "✅ This trade executed with simulated market liquidity. Your paper portfolio equity and virtual wallet balances reflect this trade.",
                        fontSize = 10.sp,
                        color = OnPrimaryContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onTradeAgain,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
            ) {
                Text("Place New Order")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

/**
 * Dialog to simulate a cancelled order for testing and demonstration
 */
@Composable
fun SimulateCancelOrderDialog(
    onDismiss: () -> Unit,
    onConfirm: (symbol: String, name: String, type: String, quantity: Int, price: Double, reason: String) -> Unit
) {
    val stockOptions = listOf(
        Pair("HDFCBANK", "HDFC Bank Ltd."),
        Pair("TATAMOTORS", "Tata Motors Ltd."),
        Pair("SBIN", "State Bank of India"),
        Pair("BAJFINANCE", "Bajaj Finance Ltd."),
        Pair("WIPRO", "Wipro Ltd.")
    )
    var selectedStockIndex by remember { mutableStateOf(0) }
    var orderType by remember { mutableStateOf("BUY") }
    var quantityText by remember { mutableStateOf("25") }
    var priceText by remember { mutableStateOf("1580.00") }
    val reasons = listOf("Cancelled by user", "Limit price not triggered", "Expired at market close", "Margin limit check")
    var selectedReasonIndex by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Simulate Cancelled Order",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = TextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Add a simulated cancelled trade to test status tags in the Trade History tab:",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                // Select Stock
                Text("Stock Symbol", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(stockOptions.indices.toList()) { idx ->
                        FilterChip(
                            selected = selectedStockIndex == idx,
                            onClick = { selectedStockIndex = idx },
                            label = { Text(stockOptions[idx].first, fontSize = 10.sp) },
                            shape = RoundedCornerShape(50)
                        )
                    }
                }

                // Buy / Sell
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = orderType == "BUY",
                        onClick = { orderType = "BUY" },
                        label = { Text("BUY", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GainGreenLight,
                            selectedLabelColor = GainGreen
                        )
                    )
                    FilterChip(
                        selected = orderType == "SELL",
                        onClick = { orderType = "SELL" },
                        label = { Text("SELL", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = LossRedLight,
                            selectedLabelColor = LossRed
                        )
                    )
                }

                // Quantity
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Quantity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Reason
                Text("Cancellation Reason", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(reasons.indices.toList()) { idx ->
                        FilterChip(
                            selected = selectedReasonIndex == idx,
                            onClick = { selectedReasonIndex = idx },
                            label = { Text(reasons[idx], fontSize = 10.sp) },
                            shape = RoundedCornerShape(50)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val stock = stockOptions[selectedStockIndex]
                    val qty = quantityText.toIntOrNull() ?: 10
                    val pr = priceText.toDoubleOrNull() ?: 1000.0
                    val reason = reasons[selectedReasonIndex]
                    onConfirm(stock.first, stock.second, orderType, qty, pr, reason)
                },
                colors = ButtonDefaults.buttonColors(containerColor = LossRed)
            ) {
                Text("Record Cancelled Order")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Material 3 Card representing an active paper trading position with comprehensive metrics
 */
@Composable
fun PositionCardM3(
    holding: HoldingEntity,
    onStockClick: () -> Unit,
    onBuyMoreClick: () -> Unit,
    onSellClick: () -> Unit
) {
    val isGain = holding.profitLoss >= 0
    val pnlColor = if (isGain) GainGreen else LossRed
    val pnlBgColor = if (isGain) GainGreenLight else LossRedLight

    OutlinedCard(
        onClick = onStockClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = SurfaceWhite),
        border = BorderStroke(1.dp, OutlineBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("position_card_${holding.symbol}")
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header Row: Symbol, Tag, and Unrealized P&L Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = holding.symbol,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = PrimaryContainer
                    ) {
                        Text(
                            text = "LONG • ${holding.exchange}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryPurple,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Unrealized P&L Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = pnlBgColor
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = if (isGain) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                            contentDescription = null,
                            tint = pnlColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "${if (isGain) "+" else ""}${String.format("%.2f", holding.pnlPercentage)}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = pnlColor
                        )
                    }
                }
            }

            Text(
                text = holding.name,
                fontSize = 11.sp,
                color = TextSecondary,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = OutlineBorder.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            // 4-Quadrant Metric Grid for Paper Trading Position
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Column 1: Quantity & Avg Buy Price
                Column {
                    Text(
                        text = "Holding Qty",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "${holding.quantity} shares",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Avg Buy Price",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "₹${String.format("%.2f", holding.averageBuyPrice)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }

                // Column 2: Invested vs Current Valuation
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Current Value (LTP: ₹${String.format("%.2f", holding.currentPrice)})",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "₹${String.format("%,.2f", holding.currentValue)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Unrealized P&L",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "${if (isGain) "+" else ""}₹${String.format("%,.2f", holding.profitLoss)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = pnlColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons: Buy More & Exit/Sell
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onBuyMoreClick,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, GainGreen),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GainGreen),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("position_buy_more_${holding.symbol}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ Buy More", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onSellClick,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LossRed),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("position_exit_sell_${holding.symbol}")
                ) {
                    Icon(
                        imageVector = Icons.Default.TrendingDown,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Exit / Sell", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.sp, color = TextSecondary)
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    }
}
