package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ml.TechnicalAnalysisEngine
import com.example.data.remote.CandleStick
import com.example.data.remote.MarketRepository
import com.example.data.remote.StockInstrument
import com.example.ui.components.CleanCard
import com.example.ui.components.StockCandleChart
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartsScreen(
    symbol: String,
    onNavigateBack: () -> Unit
) {
    var stock by remember { mutableStateOf<StockInstrument?>(null) }
    var candles by remember { mutableStateOf<List<CandleStick>>(emptyList()) }
    var selectedTimeframe by remember { mutableStateOf("60D") }
    var showSMA by remember { mutableStateOf(true) }
    var isCandleMode by remember { mutableStateOf(true) }

    LaunchedEffect(symbol, selectedTimeframe) {
        val days = when (selectedTimeframe) {
            "15D" -> 15
            "30D" -> 30
            "60D" -> 60
            else -> 90
        }
        stock = MarketRepository.getStockDetails(symbol)
        candles = MarketRepository.getHistoricalCandles(symbol, days)
    }

    val indicators = remember(candles) { TechnicalAnalysisEngine.calculateIndicators(candles) }

    Scaffold(
        containerColor = BackgroundWarm,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("$symbol Technical Chart", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Angel One SmartAPI OHLC Feed", fontSize = 11.sp, color = TextSecondary)
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
            // Timeframe & Chart Style Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("15D", "30D", "60D", "90D").forEach { tf ->
                        FilterChip(
                            selected = selectedTimeframe == tf,
                            onClick = { selectedTimeframe = tf },
                            label = { Text(tf, fontSize = 11.sp) },
                            shape = RoundedCornerShape(50)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = isCandleMode,
                        onClick = { isCandleMode = !isCandleMode },
                        label = { Text(if (isCandleMode) "Candles" else "Line", fontSize = 11.sp) },
                        shape = RoundedCornerShape(50)
                    )
                }
            }

            // Primary Interactive Chart
            CleanCard(modifier = Modifier.fillMaxWidth()) {
                StockCandleChart(candles = candles, isCandleMode = isCandleMode, showSMA = showSMA)
            }

            // Indicator Overlays & Control
            CleanCard(modifier = Modifier.fillMaxWidth()) {
                Text("INDICATOR METRICS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("RSI (14)", fontSize = 11.sp, color = TextSecondary)
                        Text("${indicators.rsi14.toInt()}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (indicators.rsi14 > 70) LossRed else if (indicators.rsi14 < 30) GainGreen else TextPrimary)
                    }
                    Column {
                        Text("MACD (12,26)", fontSize = 11.sp, color = TextSecondary)
                        Text(String.format("%.2f", indicators.macd), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryPurple)
                    }
                    Column {
                        Text("SMA (20)", fontSize = 11.sp, color = TextSecondary)
                        Text("₹${String.format("%.1f", indicators.sma20)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFFF1F5F9))
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Bollinger Upper", fontSize = 11.sp, color = TextSecondary)
                        Text("₹${String.format("%.1f", indicators.upperBand)}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    }
                    Column {
                        Text("Bollinger Lower", fontSize = 11.sp, color = TextSecondary)
                        Text("₹${String.format("%.1f", indicators.lowerBand)}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    }
                    Column {
                        Text("ATR (14)", fontSize = 11.sp, color = TextSecondary)
                        Text("₹${String.format("%.2f", indicators.atr14)}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    }
                }
            }

            // Interpretations Card
            CleanCard(modifier = Modifier.fillMaxWidth()) {
                Text("STRATEGY INTERPRETATION", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "• Trend Status: ${indicators.trend}\n• MACD Histogram: ${if (indicators.macdHist >= 0) "Positive (+${String.format("%.2f", indicators.macdHist)})" else "Negative (${String.format("%.2f", indicators.macdHist)})"}\n• Volatility: ATR of ₹${String.format("%.2f", indicators.atr14)} indicates moderate intra-day movement.",
                    fontSize = 13.sp,
                    color = TextPrimary,
                    lineHeight = 20.sp
                )
            }
        }
    }
}
