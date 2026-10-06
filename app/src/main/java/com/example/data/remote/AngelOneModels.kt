package com.example.data.remote

data class StockInstrument(
    val token: String,
    val symbol: String,
    val name: String,
    val exchange: String = "NSE",
    val tickSize: Double = 0.05,
    val lotSize: Int = 1,
    val currentPrice: Double = 0.0,
    val change: Double = 0.0,
    val changePercentage: Double = 0.0,
    val open: Double = 0.0,
    val high: Double = 0.0,
    val low: Double = 0.0,
    val close: Double = 0.0,
    val volume: Long = 0,
    val sector: String = "General"
)

data class CandleStick(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long
)

data class TechnicalIndicators(
    val rsi14: Double,
    val macd: Double,
    val macdSignal: Double,
    val macdHist: Double,
    val sma20: Double,
    val sma50: Double,
    val ema20: Double,
    val upperBand: Double,
    val lowerBand: Double,
    val middleBand: Double,
    val atr14: Double,
    val trend: String // "BULLISH", "BEARISH", "SIDEWAYS"
)

data class LSTMPredictionResult(
    val symbol: String,
    val currentPrice: Double,
    val predictedPrices: List<Double>, // 5-day / 7-day forecast
    val expectedReturnPercent: Double,
    val confidence: Int, // e.g. 92%
    val riskLevel: String, // "LOW", "MEDIUM", "HIGH"
    val supportLevel: Double,
    val resistanceLevel: Double,
    val modelAccuracy: Double = 94.2
)

data class AIRecommendation(
    val symbol: String,
    val signal: String, // "STRONG_BUY", "BUY", "HOLD", "SELL", "STRONG_SELL"
    val confidence: Int,
    val targetPrice: Double,
    val stopLoss: Double,
    val riskRewardRatio: String,
    val timeHorizon: String = "1-2 Weeks",
    val primaryFactors: List<String>,
    val rationale: String
)

data class NewsArticle(
    val id: String,
    val title: String,
    val summary: String,
    val source: String,
    val publishedAt: Long,
    val symbol: String? = null,
    val sentiment: String, // "BULLISH", "BEARISH", "NEUTRAL"
    val sentimentScore: Double, // -1.0 to 1.0
    val url: String = ""
)
