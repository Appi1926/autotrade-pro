package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AppDatabase
import com.example.data.local.WatchlistEntity
import com.example.data.remote.MarketRepository
import com.example.data.remote.NewsArticle
import com.example.data.repository.AuthRepository
import com.example.data.repository.NewsRepository
import com.example.data.repository.WatchlistRepository
import com.example.ui.components.CleanCard
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    database: AppDatabase,
    onNavigateBack: () -> Unit,
    onNavigateToStockDetails: (String) -> Unit,
    onNavigateToSearch: () -> Unit
) {
    val user by AuthRepository.currentUser.collectAsState()
    val userId = user?.id ?: ""
    val watchlist by WatchlistRepository.observeWatchlist(database, userId).collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        containerColor = BackgroundWarm,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("My Watchlist", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Real-Time Tracking (${watchlist.size})", fontSize = 11.sp, color = TextSecondary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Search Stocks", tint = PrimaryPurple)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundWarm)
            )
        }
    ) { padding ->
        if (watchlist.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⭐", fontSize = 36.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Your Watchlist is Empty", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Add your favorite stocks to track real-time Angel One feeds.", fontSize = 12.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onNavigateToSearch,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("Explore Stocks")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(watchlist) { item ->
                    var livePrice by remember { mutableStateOf(0.0) }
                    var changePct by remember { mutableStateOf(0.0) }

                    LaunchedEffect(item.symbol) {
                        val details = MarketRepository.getStockDetails(item.symbol)
                        if (details != null) {
                            livePrice = details.currentPrice
                            changePct = details.changePercentage
                        }
                    }

                    CleanCard(
                        onClick = { onNavigateToStockDetails(item.symbol) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(item.symbol, fontSize = 15.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                                Text(item.name, fontSize = 11.sp, color = TextSecondary)
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("₹${String.format("%,.2f", if (livePrice > 0) livePrice else 1500.0)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    val isUp = changePct >= 0
                                    Text(
                                        text = "${if (isUp) "+" else ""}${String.format("%.2f", changePct)}%",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isUp) GainGreen else LossRed
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            WatchlistRepository.toggleWatchlist(database, userId, item.symbol, item.name)
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = TextTertiary, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsSentimentScreen(
    onNavigateBack: () -> Unit,
    onSelectStock: (String) -> Unit
) {
    val news = remember { NewsRepository.getNewsArticles() }
    val (overallSentiment, score, summary) = remember { NewsRepository.getOverallMarketSentiment() }

    Scaffold(
        containerColor = BackgroundWarm,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Market News & AI Sentiment", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Real-Time Financial Intelligence", fontSize = 11.sp, color = TextSecondary)
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Sentiment Banner
            item {
                CleanCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("AI MARKET SENTIMENT INDEX", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = overallSentiment,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = if (overallSentiment == "BULLISH") GainGreen else if (overallSentiment == "BEARISH") LossRed else PrimaryPurple
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = PrimaryContainer
                        ) {
                            Text(
                                text = "Score: +${(score * 100).toInt()}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryPurple,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(summary, fontSize = 12.sp, color = TextPrimary, lineHeight = 17.sp)
                }
            }

            item {
                Text(
                    text = "LATEST FINANCIAL DISPATCHES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = TextSecondary
                )
            }

            items(news) { article ->
                val timeStr = remember(article.publishedAt) {
                    SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(article.publishedAt))
                }
                val isBullish = article.sentiment == "BULLISH"
                val isBearish = article.sentiment == "BEARISH"

                CleanCard(
                    onClick = { article.symbol?.let { onSelectStock(it) } },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${article.source} • $timeStr", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary)

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (isBullish) GainGreenLight else if (isBearish) LossRedLight else SurfaceVariantLavender
                        ) {
                            Text(
                                text = article.sentiment,
                                color = if (isBullish) GainGreen else if (isBearish) LossRed else TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(article.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary, lineHeight = 19.sp)

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(article.summary, fontSize = 12.sp, color = TextSecondary, lineHeight = 17.sp)

                    if (article.symbol != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Tagged: ", fontSize = 10.sp, color = TextTertiary)
                            Text(article.symbol, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryPurple)
                        }
                    }
                }
            }
        }
    }
}
