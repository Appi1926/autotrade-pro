package com.example.data.ml

import com.example.data.remote.AIRecommendation
import com.example.data.remote.LSTMPredictionResult
import com.example.data.remote.TechnicalIndicators
import kotlin.math.roundToInt

object RecommendationEngine {

    fun generateRecommendation(
        symbol: String,
        currentPrice: Double,
        indicators: TechnicalIndicators,
        lstm: LSTMPredictionResult,
        sentimentScore: Double = 0.2 // from -1.0 to 1.0
    ): AIRecommendation {
        var score = 0.0
        val factors = mutableListOf<String>()

        // 1. Evaluate LSTM Prediction Expected Return
        when {
            lstm.expectedReturnPercent >= 4.0 -> {
                score += 2.5
                factors.add("LSTM model projects +${lstm.expectedReturnPercent}% gain with ${lstm.confidence}% confidence")
            }
            lstm.expectedReturnPercent >= 1.5 -> {
                score += 1.5
                factors.add("Positive LSTM forward price trajectory (+${lstm.expectedReturnPercent}%)")
            }
            lstm.expectedReturnPercent <= -4.0 -> {
                score -= 2.5
                factors.add("LSTM model projects downward correction (${lstm.expectedReturnPercent}%)")
            }
            lstm.expectedReturnPercent <= -1.5 -> {
                score -= 1.5
                factors.add("Negative forward price trajectory (${lstm.expectedReturnPercent}%)")
            }
            else -> {
                factors.add("LSTM indicates sideways consolidation (${lstm.expectedReturnPercent}%)")
            }
        }

        // 2. Evaluate RSI Momentum
        when {
            indicators.rsi14 < 30.0 -> {
                score += 2.0
                factors.add("RSI (${indicators.rsi14.roundToInt()}) indicates oversold rebound potential")
            }
            indicators.rsi14 in 30.0..45.0 -> {
                score += 1.0
                factors.add("RSI (${indicators.rsi14.roundToInt()}) in moderate accumulation zone")
            }
            indicators.rsi14 > 70.0 -> {
                score -= 2.0
                factors.add("RSI (${indicators.rsi14.roundToInt()}) in overbought territory; risk of pullback")
            }
            indicators.rsi14 in 55.0..70.0 -> {
                score += 0.5
                factors.add("Bullish momentum sustained (RSI ${indicators.rsi14.roundToInt()})")
            }
        }

        // 3. Evaluate MACD Crossover & Histogram
        if (indicators.macd > indicators.macdSignal) {
            score += 1.5
            factors.add("MACD line above signal line confirming bullish momentum")
        } else {
            score -= 1.5
            factors.add("MACD below signal line signaling bearish divergence")
        }

        // 4. Evaluate Moving Averages Trend
        if (currentPrice > indicators.sma20 && indicators.sma20 > indicators.sma50) {
            score += 1.5
            factors.add("Price trading above SMA 20 & SMA 50 golden alignment")
        } else if (currentPrice < indicators.sma20 && indicators.sma20 < indicators.sma50) {
            score -= 1.5
            factors.add("Price positioned below key moving averages (SMA 20 & 50)")
        }

        // 5. Evaluate News Market Sentiment
        if (sentimentScore >= 0.3) {
            score += 1.0
            factors.add("AI Market sentiment index is positive/bullish (+${(sentimentScore * 100).roundToInt()}%)")
        } else if (sentimentScore <= -0.3) {
            score -= 1.0
            factors.add("AI Market sentiment index reflects cautious/bearish headwind")
        }

        // Decision thresholds
        val (signal, targetMultiplier, stopLossMultiplier) = when {
            score >= 4.0 -> Triple("STRONG_BUY", 1.065, 0.965)
            score in 1.5..3.99 -> Triple("BUY", 1.04, 0.975)
            score in -1.49..1.49 -> Triple("HOLD", 1.015, 0.985)
            score in -3.99..-1.5 -> Triple("SELL", 0.96, 1.025)
            else -> Triple("STRONG_SELL", 0.935, 1.035)
        }

        val targetPrice = ((currentPrice * targetMultiplier) * 100.0).roundToInt() / 100.0
        val stopLoss = ((currentPrice * stopLossMultiplier) * 100.0).roundToInt() / 100.0

        val reward = kotlin.math.abs(targetPrice - currentPrice)
        val risk = kotlin.math.abs(currentPrice - stopLoss)
        val rrRatio = if (risk > 0) String.format("1 : %.1f", reward / risk) else "1 : 2.0"

        val baseConfidence = (70 + kotlin.math.abs(score) * 5.0).roundToInt().coerceIn(65, 96)

        val rationale = "Synthesized from Deep Learning LSTM sequence projection, RSI(${indicators.rsi14.roundToInt()}) oscillator, MACD histogram, and real-time market sentiment. Current quantitative signal indicates $signal with optimal risk/reward parameters."

        return AIRecommendation(
            symbol = symbol,
            signal = signal,
            confidence = baseConfidence,
            targetPrice = targetPrice,
            stopLoss = stopLoss,
            riskRewardRatio = rrRatio,
            timeHorizon = "1-2 Weeks",
            primaryFactors = factors,
            rationale = rationale
        )
    }
}
