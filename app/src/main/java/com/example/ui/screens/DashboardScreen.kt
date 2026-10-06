package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.example.data.local.AppDatabase
import com.example.data.remote.MarketRepository
import com.example.data.remote.StockInstrument
import com.example.data.repository.AuthRepository
import com.example.data.repository.TradingRepository
import com.example.ui.components.CleanCard
import com.example.ui.components.HeroPortfolioCard
import com.example.ui.components.PulsingDot
import com.example.ui.theme.*

@Composable
fun DashboardScreen(
    database: AppDatabase,
    onNavigateToStockDetails: (String) -> Unit,
    onNavigateToCharts: (String) -> Unit,
    onNavigateToPrediction: (String) -> Unit,
    onNavigateToRecommendation: (String) -> Unit,
    onNavigateToPaperTrading: (String) -> Unit,
    onNavigateToPortfolio: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToAssistant: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToBacktest: ((String) -> Unit)? = null
) {
    val user by AuthRepository.currentUser.collectAsState()
    val portfolio by TradingRepository.observePortfolio(database, user?.id ?: "").collectAsState(initial = null)
    val liveStocks by MarketRepository.getLiveFeedFlow().collectAsState(initial = emptyList())

    val totalValuation = portfolio?.currentValuation ?: (user?.virtualBalance ?: 1000000.0)
    val returnPct = portfolio?.returnsPercentage ?: 2.45
    val investedVal = portfolio?.totalInvested ?: 0.0

    Scaffold(
        containerColor = BackgroundWarm,
        topBar = {
            // Top Bar matching Clean Minimalism Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "AUTONOMOUS AI",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "OmniTrade Pro",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = OnPrimaryContainer
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateToNotifications,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(SurfaceVariantLavender)
                    ) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = "Notifications",
                            tint = PrimaryPurple,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(PrimaryContainer)
                            .border(2.dp, Color.White, CircleShape)
                            .clickable { onNavigateToProfile() }
                    ) {
                        Text(
                            text = "👤",
                            fontSize = 20.sp
                        )
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Broker API Live Status Indicator
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SurfaceWhite,
                    border = BorderStroke(1.dp, OutlineBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToPaperTrading("RELIANCE") }
                        .testTag("angel_one_connection_badge")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PulsingDot(color = GainGreen)
                            Text(
                                text = "Angel One SmartAPI: Connected",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = PrimaryContainer
                        ) {
                            Text(
                                text = "Trade Live / Paper ➔",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryPurple,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            // 1. Hero Virtual Portfolio Card
            item {
                HeroPortfolioCard(
                    portfolioValue = totalValuation,
                    profitPercent = returnPct,
                    investedAmount = investedVal,
                    onAnalyticsClick = onNavigateToPortfolio,
                    modifier = Modifier.testTag("hero_portfolio_card")
                )
            }

            // 2. LSTM Prediction Signal Spotlight Card (Matching Design HTML)
            item {
                CleanCard(
                    onClick = { onNavigateToPrediction("HDFCBANK") },
                    modifier = Modifier.fillMaxWidth().testTag("lstm_spotlight_card")
                ) {
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
                            Text(
                                text = "LSTM PREDICTION SIGNAL",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                color = TextSecondary
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = PrimaryContainer
                        ) {
                            Text(
                                text = "94% Confidence",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryPurple,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(BackgroundWarm)
                                    .border(1.dp, OutlineBorder, RoundedCornerShape(16.dp))
                            ) {
                                Text(
                                    text = "HDFC",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = TextSecondary
                                )
                            }

                            Column {
                                Text(
                                    text = "HDFC Bank Ltd",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "NSE • CMP: ₹1,642.45",
                                    fontSize = 11.sp,
                                    color = TextTertiary
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "BUY",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = GainGreen
                            )
                            Text(
                                text = "Target: ₹1,685",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            // 3. Quick Action Grid
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionPill(
                        icon = "🔍",
                        label = "Search",
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToSearch
                    )
                    QuickActionPill(
                        icon = "📊",
                        label = "Charts",
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToCharts("HDFCBANK") }
                    )
                    QuickActionPill(
                        icon = "🎯",
                        label = "Signals",
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToRecommendation("HDFCBANK") }
                    )
                    QuickActionPill(
                        icon = "📈",
                        label = "Backtest",
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToBacktest?.invoke("RELIANCE") }
                    )
                    QuickActionPill(
                        icon = "⚡",
                        label = "Live/Paper",
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToPaperTrading("RELIANCE") }
                    )
                }
            }

            // 4. Voice / AI Assistant Prompt Bar (Matching HTML Layout)
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = SurfaceVariantLavender,
                    border = BorderStroke(1.dp, OutlineBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onNavigateToAssistant() }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        ) {
                            Text("🎙️", fontSize = 16.sp)
                        }

                        Text(
                            text = "\"Hey assistant, what is the sentiment for IT stocks today?\"",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 5. Angel One Market Feed (Live Connected Header)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Angel One Market Feed",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PulsingDot(color = GainGreen)
                        Text(
                            text = "Live Connected",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = GainGreen
                        )
                    }
                }
            }

            // Live Ticker Items
            val displayStocks = if (liveStocks.isNotEmpty()) liveStocks else MarketRepository.masterInstruments
            items(displayStocks.take(8)) { stock ->
                LiveStockTickerRow(
                    stock = stock,
                    onClick = { onNavigateToStockDetails(stock.symbol) }
                )
            }
        }
    }
}

@Composable
fun QuickActionPill(
    icon: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, OutlineBorder),
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(icon, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
    }
}

@Composable
fun LiveStockTickerRow(
    stock: StockInstrument,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (stock.changePercentage >= 0) "📈" else "📉",
                    fontSize = 18.sp
                )
                Column {
                    Text(
                        text = stock.symbol,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = stock.name,
                        fontSize = 10.sp,
                        color = TextTertiary,
                        maxLines = 1
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "₹${String.format("%,.2f", stock.currentPrice)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                val isUp = stock.changePercentage >= 0
                Text(
                    text = "${if (isUp) "+" else ""}${String.format("%.2f", stock.changePercentage)}%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isUp) GainGreen else LossRed
                )
            }
        }
    }
}
