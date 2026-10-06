package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.local.WatchlistEntity
import com.example.data.remote.NewsArticle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

object WatchlistRepository {

    fun observeWatchlist(database: AppDatabase, userId: String): Flow<List<WatchlistEntity>> {
        return database.watchlistDao().observeWatchlist(userId)
    }

    suspend fun toggleWatchlist(database: AppDatabase, userId: String, symbol: String, name: String): Boolean = withContext(Dispatchers.IO) {
        val existing = database.watchlistDao().getWatchlistItem(userId, symbol)
        if (existing != null) {
            database.watchlistDao().deleteFromWatchlist(userId, symbol)
            false
        } else {
            database.watchlistDao().insertWatchlist(
                WatchlistEntity(
                    id = "${userId}_$symbol",
                    userId = userId,
                    symbol = symbol,
                    name = name,
                    addedAt = System.currentTimeMillis()
                )
            )
            true
        }
    }

    suspend fun isInWatchlist(database: AppDatabase, userId: String, symbol: String): Boolean = withContext(Dispatchers.IO) {
        database.watchlistDao().getWatchlistItem(userId, symbol) != null
    }
}

object NewsRepository {

    private val newsFeed = listOf(
        NewsArticle(
            id = "n_001",
            title = "RBI Keeps Repo Rate Steady; Banking Equities Rally on Robust Credit Growth",
            summary = "The Reserve Bank of India maintained the benchmark policy repo rate, boosting investor confidence across large-cap private and PSU banks including HDFC Bank, ICICI Bank, and SBI.",
            source = "Economic Times",
            publishedAt = System.currentTimeMillis() - 1000 * 60 * 45,
            symbol = "HDFCBANK",
            sentiment = "BULLISH",
            sentimentScore = 0.78
        ),
        NewsArticle(
            id = "n_002",
            title = "Reliance Retail & Jio Platforms Post Record EBITDA Margins in Q3",
            summary = "Reliance Industries witnessed substantial traction across its digital services and retail divisions, driving positive revisions from domestic and institutional brokerage houses.",
            source = "Mint",
            publishedAt = System.currentTimeMillis() - 1000 * 60 * 120,
            symbol = "RELIANCE",
            sentiment = "BULLISH",
            sentimentScore = 0.82
        ),
        NewsArticle(
            id = "n_003",
            title = "IT Sector Faces Short-Term Margin Pressure Amid Global Discretionary Tech Slowdown",
            summary = "Tier-1 IT exporters including TCS, Infosys, and Wipro navigate cautious client enterprise spending in North American and European verticals, though generative AI pipeline deals expand.",
            source = "Business Standard",
            publishedAt = System.currentTimeMillis() - 1000 * 60 * 240,
            symbol = "INFY",
            sentiment = "NEUTRAL",
            sentimentScore = -0.15
        ),
        NewsArticle(
            id = "n_004",
            title = "Tata Motors EV Market Share Crosses 72% with Next-Gen Battery Architecture Rollout",
            summary = "Tata Motors accelerates its passenger electric mobility push with strong commercial vehicle replacement cycles and order book strength in domestic markets.",
            source = "Financial Express",
            publishedAt = System.currentTimeMillis() - 1000 * 60 * 360,
            symbol = "TATAMOTORS",
            sentiment = "BULLISH",
            sentimentScore = 0.88
        ),
        NewsArticle(
            id = "n_005",
            title = "Global Crude Volatility Prompts Mixed Momentum in FMCG and Paint Sectors",
            summary = "Raw material pricing adjustments cause minor margin headwinds for decorative paint manufacturers like Asian Paints, while rural consumption recovery supports FMCG majors.",
            source = "Reuters India",
            publishedAt = System.currentTimeMillis() - 1000 * 60 * 480,
            symbol = "ASIANPAINT",
            sentiment = "BEARISH",
            sentimentScore = -0.42
        )
    )

    fun getNewsArticles(symbol: String? = null): List<NewsArticle> {
        return if (symbol.isNullOrBlank()) {
            newsFeed
        } else {
            newsFeed.filter { it.symbol.equals(symbol, ignoreCase = true) }.ifEmpty { newsFeed.take(3) }
        }
    }

    fun getOverallMarketSentiment(): Triple<String, Double, String> {
        val avgScore = newsFeed.map { it.sentimentScore }.average()
        val sentiment = when {
            avgScore > 0.2 -> "BULLISH"
            avgScore < -0.2 -> "BEARISH"
            else -> "NEUTRAL"
        }
        val summary = "Positive macroeconomic signals and banking sector outperformance maintain moderate bullish momentum despite global tech headwinds."
        return Triple(sentiment, (avgScore * 100).toInt() / 100.0, summary)
    }
}
