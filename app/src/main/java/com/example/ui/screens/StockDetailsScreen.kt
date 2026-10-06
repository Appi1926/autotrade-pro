package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AppDatabase
import com.example.data.ml.LSTMPredictionEngine
import com.example.data.ml.RecommendationEngine
import com.example.data.ml.TechnicalAnalysisEngine
import com.example.data.remote.CandleStick
import com.example.data.remote.MarketRepository
import com.example.data.remote.StockInstrument
import com.example.data.repository.AlertRepository
import com.example.data.repository.AuthRepository
import com.example.data.repository.WatchlistRepository
import com.example.ui.components.CleanCard
import com.example.ui.components.StockCandleChart
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockDetailsScreen(
    database: AppDatabase,
    symbol: String,
    onNavigateBack: () -> Unit,
    onNavigateToCharts: (String) -> Unit,
    onNavigateToPrediction: (String) -> Unit,
    onNavigateToRecommendation: (String) -> Unit,
    onNavigateToPaperTrading: (String, String) -> Unit,
    onNavigateToBacktest: ((String) -> Unit)? = null
) {
    val user by AuthRepository.currentUser.collectAsState()
    var stock by remember { mutableStateOf<StockInstrument?>(null) }
    var candles by remember { mutableStateOf<List<CandleStick>>(emptyList()) }
    var isInWatchlist by remember { mutableStateOf(false) }
    var showAlertDialog by remember { mutableStateOf(false) }
    var isCandleMode by remember { mutableStateOf(true) }

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(symbol) {
        stock = MarketRepository.getStockDetails(symbol)
        candles = MarketRepository.getHistoricalCandles(symbol, 45)
        user?.let {
            isInWatchlist = WatchlistRepository.isInWatchlist(database, it.id, symbol)
        }
    }

    val currentStock = stock ?: StockInstrument("0", symbol, symbol, "NSE", 0.05, 1, 1000.0, 0.0, 0.0, 1000.0, 1010.0, 990.0, 1000.0, 100000)
    val indicators = remember(candles) { TechnicalAnalysisEngine.calculateIndicators(candles) }
    val lstm = remember(candles) { LSTMPredictionEngine.predictPriceTrajectory(symbol, candles, 5) }
    val recommendation = remember(candles, indicators, lstm) {
        RecommendationEngine.generateRecommendation(symbol, currentStock.currentPrice, indicators, lstm)
    }

    Scaffold(
        containerColor = BackgroundWarm,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(currentStock.symbol, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text(currentStock.name, fontSize = 11.sp, color = TextSecondary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                user?.let {
                                    val added = WatchlistRepository.toggleWatchlist(database, it.id, currentStock.symbol, currentStock.name)
                                    isInWatchlist = added
                                }
                            }
                        }
                    ) {
                        Icon(
                            if (isInWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Watchlist",
                            tint = if (isInWatchlist) PrimaryPurple else TextSecondary
                        )
                    }

                    IconButton(onClick = { showAlertDialog = true }) {
                        Icon(Icons.Default.AddAlert, contentDescription = "Set Alert", tint = PrimaryPurple)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundWarm)
            )
        },
        bottomBar = {
            // Trading Buy / Sell Action Bar (Paper & Live)
            Surface(
                color = SurfaceWhite,
                tonalElevation = 8.dp,
                border = BorderStroke(1.dp, OutlineBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { onNavigateToPaperTrading(symbol, "BUY") },
                        colors = ButtonDefaults.buttonColors(containerColor = GainGreen),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.weight(1f).height(48.dp).testTag("details_buy_button")
                    ) {
                        Text("BUY (Paper / Live)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { onNavigateToPaperTrading(symbol, "SELL") },
                        colors = ButtonDefaults.buttonColors(containerColor = LossRed),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.weight(1f).height(48.dp).testTag("details_sell_button")
                    ) {
                        Text("SELL (Paper / Live)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Price & Change Banner
            CleanCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text("CURRENT MARKET PRICE (CMP)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("₹${String.format("%,.2f", currentStock.currentPrice)}", fontSize = 30.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                    }

                    val isUp = currentStock.changePercentage >= 0
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isUp) GainGreenLight else LossRedLight
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${if (isUp) "+" else ""}${String.format("%.2f", currentStock.change)} (${if (isUp) "+" else ""}${String.format("%.2f", currentStock.changePercentage)}%)",
                                color = if (isUp) GainGreen else LossRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Interactive Chart Card
            CleanCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("PRICE ACTION & SMA (20)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = { isCandleMode = !isCandleMode }, modifier = Modifier.size(32.dp)) {
                            Text(if (isCandleMode) "🕯️" else "📈", fontSize = 14.sp)
                        }
                        IconButton(onClick = { onNavigateToCharts(symbol) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen Chart", tint = PrimaryPurple)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                StockCandleChart(candles = candles, isCandleMode = isCandleMode, showSMA = true)
            }

            // AI & LSTM Recommendation Summary Pill
            CleanCard(
                onClick = { onNavigateToRecommendation(symbol) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("AI QUANTITATIVE SIGNAL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(recommendation.signal, fontSize = 20.sp, fontWeight = FontWeight.Black, color = if (recommendation.signal.contains("BUY")) GainGreen else LossRed)
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = PrimaryContainer
                        ) {
                            Text(
                                text = "${recommendation.confidence}% Confidence",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryPurple,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Text("Target: ₹${recommendation.targetPrice}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                }
            }

            // Technical Indicators Quick Grid
            CleanCard(modifier = Modifier.fillMaxWidth()) {
                Text("TECHNICAL INDICATORS (60D)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    IndicatorMetricBox("RSI (14)", "${indicators.rsi14.toInt()}", if (indicators.rsi14 < 35) "Oversold" else if (indicators.rsi14 > 65) "Overbought" else "Neutral")
                    IndicatorMetricBox("MACD", String.format("%.2f", indicators.macd), if (indicators.macd > indicators.macdSignal) "Bullish Cross" else "Bearish Cross")
                    IndicatorMetricBox("Trend", indicators.trend, "SMA 20/50")
                }
            }

            // Market Depth / Key Statistics
            CleanCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("MARKET STATISTICS (ANGEL ONE)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(shape = RoundedCornerShape(50), color = GainGreenLight) {
                            Text(
                                "Connected",
                                color = GainGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                StatRow("Today's High", "₹${String.format("%,.2f", currentStock.high)}")
                StatRow("Today's Low", "₹${String.format("%,.2f", currentStock.low)}")
                StatRow("Previous Close", "₹${String.format("%,.2f", currentStock.close)}")
                StatRow("Volume", String.format("%,d", currentStock.volume))
                StatRow("Exchange / Sector", "${currentStock.exchange} • ${currentStock.sector}")
                StatRow("SmartAPI Token", currentStock.token)
                StatRow("Lot Size / Tick", "${currentStock.lotSize} / ₹${currentStock.tickSize}")
            }

            // Deep Dive Navigation Buttons
            Button(
                onClick = { onNavigateToPrediction(symbol) },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryContainer, contentColor = OnPrimaryContainer),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Deep Learning LSTM Sequence Forecast 🤖", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { onNavigateToBacktest?.invoke(symbol) },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantLavender, contentColor = PrimaryPurple),
                border = BorderStroke(1.dp, OutlineBorder),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("details_to_backtest_button")
            ) {
                Text("Quantitative Strategy Backtester 📊", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showAlertDialog) {
        SetPriceAlertDialog(
            symbol = symbol,
            currentPrice = currentStock.currentPrice,
            onDismiss = { showAlertDialog = false },
            onConfirm = { targetPrice, condition ->
                coroutineScope.launch {
                    user?.let {
                        AlertRepository.createAlert(database, it.id, symbol, targetPrice, condition)
                    }
                    showAlertDialog = false
                }
            }
        )
    }
}

@Composable
fun IndicatorMetricBox(title: String, value: String, subtitle: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 11.sp, color = TextSecondary)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(subtitle, fontSize = 9.sp, color = PrimaryPurple, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = TextSecondary)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    }
}

@Composable
fun SetPriceAlertDialog(
    symbol: String,
    currentPrice: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double, String) -> Unit
) {
    var priceText by remember { mutableStateOf(String.format("%.2f", currentPrice * 1.03)) }
    var condition by remember { mutableStateOf("ABOVE") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Smart Price Alert", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Trigger an in-app notification when $symbol price reaches your target.")
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Target Price (₹)") },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = condition == "ABOVE",
                        onClick = { condition = "ABOVE" },
                        label = { Text("Rises Above") }
                    )
                    FilterChip(
                        selected = condition == "BELOW",
                        onClick = { condition = "BELOW" },
                        label = { Text("Falls Below") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = priceText.toDoubleOrNull() ?: currentPrice
                    onConfirm(p, condition)
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
            ) {
                Text("Set Alert")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
