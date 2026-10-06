package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ml.BacktestResult
import com.example.data.ml.BacktestingEngine
import com.example.data.remote.MarketRepository
import com.example.ui.components.CleanCard
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BacktestScreen(
    initialSymbol: String = "RELIANCE",
    onNavigateBack: () -> Unit,
    onNavigateToPaperTrading: (String, String) -> Unit
) {
    var selectedSymbol by remember { mutableStateOf(initialSymbol) }
    var selectedStrategy by remember { mutableStateOf(BacktestingEngine.Strategy.MULTI_FACTOR_AI) }
    var selectedDays by remember { mutableStateOf(90) }
    var backtestResult by remember { mutableStateOf<BacktestResult?>(null) }
    var isRunning by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    // Run backtest initially or when parameters change
    fun executeBacktest() {
        coroutineScope.launch {
            isRunning = true
            val candles = MarketRepository.getHistoricalCandles(selectedSymbol, selectedDays)
            val result = BacktestingEngine.runBacktest(
                symbol = selectedSymbol,
                candles = candles,
                strategy = selectedStrategy,
                initialCapital = 100000.0
            )
            backtestResult = result
            isRunning = false
        }
    }

    LaunchedEffect(selectedSymbol, selectedStrategy, selectedDays) {
        executeBacktest()
    }

    val stockSymbols = listOf("RELIANCE", "TCS", "HDFCBANK", "INFY", "TATAMOTORS", "ICICIBANK", "SBIN")
    val durationOptions = listOf(30, 60, 90, 180)

    Scaffold(
        containerColor = BackgroundWarm,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Quantitative Strategy Backtester", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextPrimary)
                        Text("Academic Strategy Simulation on OHLCV Data", fontSize = 11.sp, color = TextSecondary)
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("backtest_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { executeBacktest() },
                        modifier = Modifier.testTag("backtest_refresh_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Rerun Backtest", tint = PrimaryPurple)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundWarm)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 32.dp)
        ) {
            // 1. Parameter Selection Controls
            item {
                OutlinedCard(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = SurfaceWhite),
                    border = BorderStroke(1.dp, OutlineBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "BACKTEST CONFIGURATION",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = TextSecondary
                        )

                        // Stock Symbol Selector
                        Text("Asset Symbol", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(stockSymbols) { sym ->
                                FilterChip(
                                    selected = selectedSymbol == sym,
                                    onClick = { selectedSymbol = sym },
                                    label = { Text(sym, fontSize = 11.sp) },
                                    shape = RoundedCornerShape(50),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PrimaryContainer,
                                        selectedLabelColor = PrimaryPurple
                                    ),
                                    modifier = Modifier.testTag("backtest_symbol_$sym")
                                )
                            }
                        }

                        // Quantitative Strategy Selector
                        Text("Quantitative Strategy", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(BacktestingEngine.Strategy.values()) { strat ->
                                val label = when (strat) {
                                    BacktestingEngine.Strategy.MULTI_FACTOR_AI -> "AI Multi-Factor"
                                    BacktestingEngine.Strategy.RSI_MEAN_REVERSION -> "RSI Mean Reversion"
                                    BacktestingEngine.Strategy.MACD_CROSSOVER -> "MACD Crossover"
                                    BacktestingEngine.Strategy.SMA_TREND_FOLLOWING -> "SMA Trend"
                                }
                                FilterChip(
                                    selected = selectedStrategy == strat,
                                    onClick = { selectedStrategy = strat },
                                    label = { Text(label, fontSize = 11.sp) },
                                    shape = RoundedCornerShape(50),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PrimaryContainer,
                                        selectedLabelColor = PrimaryPurple
                                    ),
                                    modifier = Modifier.testTag("backtest_strategy_${strat.name}")
                                )
                            }
                        }

                        // Time Horizon Selector
                        Text("Historical Horizon", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(durationOptions) { d ->
                                FilterChip(
                                    selected = selectedDays == d,
                                    onClick = { selectedDays = d },
                                    label = { Text("${d} Days", fontSize = 11.sp) },
                                    shape = RoundedCornerShape(50),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PrimaryContainer,
                                        selectedLabelColor = PrimaryPurple
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 2. Loading State or Results
            if (isRunning) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(color = PrimaryPurple)
                            Text("Simulating algorithmic strategy...", fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                }
            } else if (backtestResult != null) {
                val res = backtestResult!!

                // 3. Performance Hero Card
                item {
                    val isGain = res.totalReturnPercentage >= 0
                    ElevatedCard(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = if (isGain) Color(0xFF1E3A2F) else Color(0xFF3B1E2B)
                        ),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
                        modifier = Modifier.fillMaxWidth().testTag("backtest_performance_hero")
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "${res.strategyName} • ${res.symbol}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        color = Color.White.copy(alpha = 0.75f)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${if (isGain) "+" else ""}${String.format("%.2f", res.totalReturnPercentage)}%",
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (isGain) Color(0xFF86EFAC) else Color(0xFFFCA5A5)
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White.copy(alpha = 0.15f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        Text("Buy & Hold Benchmark", fontSize = 9.sp, color = Color.White.copy(alpha = 0.7f))
                                        Text(
                                            text = "${if (res.benchmarkReturnPercentage >= 0) "+" else ""}${String.format("%.2f", res.benchmarkReturnPercentage)}%",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Initial Capital", fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
                                    Text("₹${String.format("%,.0f", res.initialCapital)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Simulated Ending Equity", fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
                                    Text("₹${String.format("%,.2f", res.finalCapital)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Alpha vs Benchmark", fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
                                    val alpha = res.totalReturnPercentage - res.benchmarkReturnPercentage
                                    Text(
                                        text = "${if (alpha >= 0) "+" else ""}${String.format("%.2f", alpha)}%",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (alpha >= 0) Color(0xFF86EFAC) else Color(0xFFFCA5A5)
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Quantitative Metrics Grid
                item {
                    OutlinedCard(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.outlinedCardColors(containerColor = SurfaceWhite),
                        border = BorderStroke(1.dp, OutlineBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "RISK & STATISTICAL METRICS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = TextSecondary
                            )

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                MetricCell("Win Rate", "${res.winRatePercentage}%", "${res.winningTrades}W / ${res.losingTrades}L")
                                MetricCell("Profit Factor", "${res.profitFactor}", if (res.profitFactor > 1.5) "Robust" else "Moderate")
                                MetricCell("Sharpe Ratio", "${res.sharpeRatio}", "Risk-adjusted")
                            }

                            HorizontalDivider(color = OutlineBorder.copy(alpha = 0.5f))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                MetricCell("Max Drawdown", "-${res.maxDrawdownPercentage}%", "Peak-to-trough")
                                MetricCell("Total Trades", "${res.totalTrades}", "Round trips")
                                MetricCell("Avg Trade Return", "${if (res.averageTradeReturn >= 0) "+" else ""}${res.averageTradeReturn}%", "Per position")
                            }
                        }
                    }
                }

                // 5. Strategy Educational Description Card
                item {
                    CleanCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "STRATEGY LOGIC & ACADEMIC RATIONALE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        val rationale = when (selectedStrategy) {
                            BacktestingEngine.Strategy.MULTI_FACTOR_AI ->
                                "Multi-Factor AI synthesizes 20-step recurrent LSTM price projections with RSI-14 momentum boundaries, MACD trend direction, and moving average crossovers. Orders enter when composite score exceeds +1.5, with automatic 3.5% stop loss and 5.5% profit target."
                            BacktestingEngine.Strategy.RSI_MEAN_REVERSION ->
                                "RSI Mean Reversion generates BUY triggers when RSI-14 drops below 35 (oversold condition), anticipating a statistical snapback toward the 50-neutral equilibrium line."
                            BacktestingEngine.Strategy.MACD_CROSSOVER ->
                                "MACD Crossover enters long positions when the 12/26 MACD fast line crosses above the 9-period signal line, confirming bullish momentum acceleration."
                            BacktestingEngine.Strategy.SMA_TREND_FOLLOWING ->
                                "SMA Trend Following buys when current price trades above the 20-day Simple Moving Average while the 20-day SMA is greater than the 50-day SMA (Golden Trend alignment)."
                        }
                        Text(text = rationale, fontSize = 12.sp, color = TextPrimary, lineHeight = 18.sp)
                    }
                }

                // 6. Action Button: Execute in Paper Trading
                item {
                    Button(
                        onClick = { onNavigateToPaperTrading(selectedSymbol, "BUY") },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("backtest_execute_paper_button")
                    ) {
                        Icon(Icons.Default.TrendingUp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Deploy in Paper Trading (${selectedSymbol})", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // 7. Trade Log Header
                item {
                    Text(
                        text = "Simulated Trade History (${res.trades.size} Trades)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                }

                // 8. Individual Trade Log Cards
                if (res.trades.isEmpty()) {
                    item {
                        Text(
                            text = "No trades were triggered during this historical period for the selected strategy parameters.",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                } else {
                    items(res.trades) { trade ->
                        val isWin = trade.profitLoss > 0
                        val dateFmt = remember(trade.entryDate) {
                            SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(trade.entryDate))
                        }
                        val exitFmt = remember(trade.exitDate) {
                            SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(trade.exitDate))
                        }

                        OutlinedCard(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.outlinedCardColors(containerColor = SurfaceWhite),
                            border = BorderStroke(1.dp, OutlineBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = GainGreenLight
                                        ) {
                                            Text(trade.type, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GainGreen, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                        Text("$dateFmt → $exitFmt", fontSize = 11.sp, color = TextTertiary)
                                    }
                                    Text("${trade.quantity} Qty @ ₹${trade.entryPrice} → ₹${trade.exitPrice}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                    Text(trade.exitReason, fontSize = 10.sp, color = TextSecondary)
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${if (isWin) "+" else ""}₹${String.format("%,.2f", trade.profitLoss)}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isWin) GainGreen else LossRed
                                    )
                                    Text(
                                        text = "(${if (isWin) "+" else ""}${String.format("%.2f", trade.returnPercentage)}%)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isWin) GainGreen else LossRed
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

@Composable
private fun MetricCell(title: String, value: String, subtitle: String) {
    Column {
        Text(title, fontSize = 10.sp, color = TextSecondary)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(subtitle, fontSize = 9.sp, color = TextTertiary)
    }
}
