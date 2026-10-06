package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ml.LSTMPredictionEngine
import com.example.data.ml.RecommendationEngine
import com.example.data.ml.TechnicalAnalysisEngine
import com.example.data.remote.CandleStick
import com.example.data.remote.MarketRepository
import com.example.data.remote.StockInstrument
import com.example.ui.components.CleanCard
import com.example.ui.components.PulsingDot
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIPredictionScreen(
    symbol: String,
    onNavigateBack: () -> Unit,
    onNavigateToRecommendation: (String) -> Unit
) {
    var stock by remember { mutableStateOf<StockInstrument?>(null) }
    var candles by remember { mutableStateOf<List<CandleStick>>(emptyList()) }

    LaunchedEffect(symbol) {
        stock = MarketRepository.getStockDetails(symbol)
        candles = MarketRepository.getHistoricalCandles(symbol, 60)
    }

    val lstm = remember(candles) { LSTMPredictionEngine.predictPriceTrajectory(symbol, candles, 7) }
    val currentPrice = stock?.currentPrice ?: lstm.currentPrice

    Scaffold(
        containerColor = BackgroundWarm,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("$symbol LSTM Prediction", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Deep Learning Sequence Forecaster", fontSize = 11.sp, color = TextSecondary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Confidence & Trajectory Banner
            CleanCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PulsingDot(color = PrimaryPurple)
                        Text("LSTM MODEL INFERENCE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = PrimaryContainer
                    ) {
                        Text(
                            text = "${lstm.confidence}% Confidence",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryPurple,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text("Expected 7-Day Move", fontSize = 11.sp, color = TextSecondary)
                        val isGain = lstm.expectedReturnPercent >= 0
                        Text(
                            text = "${if (isGain) "+" else ""}${lstm.expectedReturnPercent}%",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isGain) GainGreen else LossRed
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text("Risk Assessment", fontSize = 11.sp, color = TextSecondary)
                        val riskColor = when (lstm.riskLevel) {
                            "LOW" -> GainGreen
                            "MEDIUM" -> Color(0xFFEAB308)
                            else -> LossRed
                        }
                        Text(
                            text = "${lstm.riskLevel} RISK",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = riskColor
                        )
                    }
                }
            }

            // 7-Day Multi-Step Price Projections List
            CleanCard(modifier = Modifier.fillMaxWidth()) {
                Text("FORWARD PRICE TRAJECTORY (DAYS 1-7)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                Spacer(modifier = Modifier.height(12.dp))

                lstm.predictedPrices.forEachIndexed { index, price ->
                    val dayNum = index + 1
                    val diff = price - currentPrice
                    val diffPct = if (currentPrice > 0) (diff / currentPrice) * 100.0 else 0.0
                    val isUp = diff >= 0

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(SurfaceVariantLavender, CircleShape)
                            ) {
                                Text("D$dayNum", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryPurple)
                            }
                            Text("Day $dayNum Forecast", fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("₹${String.format("%,.2f", price)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(
                                text = "${if (isUp) "+" else ""}${String.format("%.2f", diffPct)}%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isUp) GainGreen else LossRed
                            )
                        }
                    }
                    if (index < lstm.predictedPrices.lastIndex) {
                        HorizontalDivider(color = Color(0xFFF1F5F9))
                    }
                }
            }

            // Key Support & Resistance Levels
            CleanCard(modifier = Modifier.fillMaxWidth()) {
                Text("PIVOT LEVELS & ACCURACY", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                Spacer(modifier = Modifier.height(10.dp))

                StatRow("Dynamic Support", "₹${String.format("%,.2f", lstm.supportLevel)}")
                StatRow("Dynamic Resistance", "₹${String.format("%,.2f", lstm.resistanceLevel)}")
                StatRow("Model Historical Accuracy", "${lstm.modelAccuracy}%")
                StatRow("Sequence Window", "20 timesteps (Normalized)")
            }

            Button(
                onClick = { onNavigateToRecommendation(symbol) },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("View AI BUY/SELL Recommendation", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendationScreen(
    symbol: String,
    onNavigateBack: () -> Unit,
    onNavigateToPaperTrading: (String, String) -> Unit
) {
    var stock by remember { mutableStateOf<StockInstrument?>(null) }
    var candles by remember { mutableStateOf<List<CandleStick>>(emptyList()) }

    LaunchedEffect(symbol) {
        stock = MarketRepository.getStockDetails(symbol)
        candles = MarketRepository.getHistoricalCandles(symbol, 60)
    }

    val currentStock = stock ?: StockInstrument("0", symbol, symbol, "NSE", 0.05, 1, 1000.0, 0.0, 0.0, 1000.0, 1010.0, 990.0, 1000.0, 100000)
    val indicators = remember(candles) { TechnicalAnalysisEngine.calculateIndicators(candles) }
    val lstm = remember(candles) { LSTMPredictionEngine.predictPriceTrajectory(symbol, candles, 7) }
    val rec = remember(candles, indicators, lstm) {
        RecommendationEngine.generateRecommendation(symbol, currentStock.currentPrice, indicators, lstm)
    }

    Scaffold(
        containerColor = BackgroundWarm,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("$symbol AI Recommendation", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Multi-Factor Quantitative Signal", fontSize = 11.sp, color = TextSecondary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundWarm)
            )
        },
        bottomBar = {
            Surface(
                color = SurfaceWhite,
                tonalElevation = 8.dp,
                border = BorderStroke(1.dp, OutlineBorder)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val action = if (rec.signal.contains("BUY")) "BUY" else "SELL"
                    Button(
                        onClick = { onNavigateToPaperTrading(symbol, action) },
                        colors = ButtonDefaults.buttonColors(containerColor = if (action == "BUY") GainGreen else LossRed),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Execute Paper $action Order", fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
            // Signal Hero Card
            val isBuy = rec.signal.contains("BUY")
            CleanCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("RECOMMENDED SIGNAL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = rec.signal.replace("_", " "),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isBuy) GainGreen else LossRed
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isBuy) GainGreenLight else LossRedLight
                    ) {
                        Text(
                            text = "${rec.confidence}% Confidence",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isBuy) GainGreen else LossRed,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFFF1F5F9))
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Target Price", fontSize = 11.sp, color = TextSecondary)
                        Text("₹${String.format("%,.2f", rec.targetPrice)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = GainGreen)
                    }
                    Column {
                        Text("Stop Loss", fontSize = 11.sp, color = TextSecondary)
                        Text("₹${String.format("%,.2f", rec.stopLoss)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LossRed)
                    }
                    Column {
                        Text("Risk / Reward", fontSize = 11.sp, color = TextSecondary)
                        Text(rec.riskRewardRatio, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                }
            }

            // Primary Supporting Factors
            CleanCard(modifier = Modifier.fillMaxWidth()) {
                Text("KEY SUPPORTING CATALYSTS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                Spacer(modifier = Modifier.height(12.dp))

                rec.primaryFactors.forEach { factor ->
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PrimaryPurple, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                        Text(factor, fontSize = 13.sp, color = TextPrimary, lineHeight = 18.sp)
                    }
                }
            }

            // Educational Signal Rationale
            CleanCard(modifier = Modifier.fillMaxWidth()) {
                Text("MODEL RATIONALE & TIME HORIZON", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))
                Text(rec.rationale, fontSize = 13.sp, color = TextPrimary, lineHeight = 19.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Expected Time Horizon: ${rec.timeHorizon}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryPurple)
            }

            // Mandatory Educational Disclaimer
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFFEF3C7),
                border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                    Text(
                        "DISCLAIMER: AI recommendations are educational signals generated by mathematical LSTM machine learning algorithms and technical indicators for simulated paper trading. They do not constitute guaranteed financial advice.",
                        fontSize = 11.sp,
                        color = Color(0xFF92400E),
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}
