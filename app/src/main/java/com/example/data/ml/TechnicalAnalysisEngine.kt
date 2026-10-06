package com.example.data.ml

import com.example.data.remote.CandleStick
import com.example.data.remote.TechnicalIndicators
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object TechnicalAnalysisEngine {

    fun calculateIndicators(candles: List<CandleStick>): TechnicalIndicators {
        if (candles.isEmpty()) {
            return TechnicalIndicators(
                rsi14 = 50.0,
                macd = 0.0,
                macdSignal = 0.0,
                macdHist = 0.0,
                sma20 = 0.0,
                sma50 = 0.0,
                ema20 = 0.0,
                upperBand = 0.0,
                lowerBand = 0.0,
                middleBand = 0.0,
                atr14 = 0.0,
                trend = "SIDEWAYS"
            )
        }

        val closes = candles.map { it.close }
        val rsi = calculateRSI(closes, 14)
        val sma20 = calculateSMA(closes, 20)
        val sma50 = calculateSMA(closes, 50)
        val ema20 = calculateEMA(closes, 20)
        val (macd, macdSignal, macdHist) = calculateMACD(closes)
        val (upperBand, middleBand, lowerBand) = calculateBollingerBands(closes, 20, 2.0)
        val atr = calculateATR(candles, 14)

        val latestClose = closes.last()
        val trend = when {
            latestClose > sma20 && sma20 > sma50 && macd > macdSignal -> "BULLISH"
            latestClose < sma20 && sma20 < sma50 && macd < macdSignal -> "BEARISH"
            else -> "SIDEWAYS"
        }

        return TechnicalIndicators(
            rsi14 = rsi,
            macd = macd,
            macdSignal = macdSignal,
            macdHist = macdHist,
            sma20 = sma20,
            sma50 = sma50,
            ema20 = ema20,
            upperBand = upperBand,
            lowerBand = lowerBand,
            middleBand = middleBand,
            atr14 = atr,
            trend = trend
        )
    }

    fun calculateRSI(prices: List<Double>, period: Int = 14): Double {
        if (prices.size <= period) return 50.0

        var gains = 0.0
        var losses = 0.0

        for (i in 1..period) {
            val diff = prices[i] - prices[i - 1]
            if (diff >= 0) gains += diff else losses -= diff
        }

        var avgGain = gains / period
        var avgLoss = losses / period

        for (i in (period + 1) until prices.size) {
            val diff = prices[i] - prices[i - 1]
            if (diff >= 0) {
                avgGain = (avgGain * (period - 1) + diff) / period
                avgLoss = (avgLoss * (period - 1)) / period
            } else {
                avgGain = (avgGain * (period - 1)) / period
                avgLoss = (avgLoss * (period - 1) - diff) / period
            }
        }

        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return (100.0 - (100.0 / (1.0 + rs))).coerceIn(0.0, 100.0)
    }

    fun calculateSMA(prices: List<Double>, period: Int): Double {
        if (prices.size < period) {
            return if (prices.isNotEmpty()) prices.average() else 0.0
        }
        return prices.takeLast(period).average()
    }

    fun calculateEMA(prices: List<Double>, period: Int): Double {
        if (prices.isEmpty()) return 0.0
        if (prices.size < period) return prices.average()

        val multiplier = 2.0 / (period + 1)
        var ema = prices.take(period).average()

        for (i in period until prices.size) {
            ema = (prices[i] - ema) * multiplier + ema
        }
        return ema
    }

    fun calculateMACD(prices: List<Double>): Triple<Double, Double, Double> {
        if (prices.size < 26) return Triple(0.0, 0.0, 0.0)

        val ema12List = mutableListOf<Double>()
        val ema26List = mutableListOf<Double>()

        val k12 = 2.0 / (12 + 1)
        val k26 = 2.0 / (26 + 1)

        var e12 = prices.take(12).average()
        for (i in 12 until prices.size) {
            e12 = (prices[i] - e12) * k12 + e12
            ema12List.add(e12)
        }

        var e26 = prices.take(26).average()
        val macdLine = mutableListOf<Double>()
        for (i in 26 until prices.size) {
            e26 = (prices[i] - e26) * k26 + e26
            ema26List.add(e26)
            val corresponding12 = ema12List[i - 12]
            macdLine.add(corresponding12 - e26)
        }

        if (macdLine.isEmpty()) return Triple(0.0, 0.0, 0.0)

        val macd = macdLine.last()
        val signal = if (macdLine.size >= 9) {
            calculateEMA(macdLine, 9)
        } else {
            macdLine.average()
        }
        val hist = macd - signal

        return Triple(macd, signal, hist)
    }

    fun calculateBollingerBands(prices: List<Double>, period: Int = 20, multiplier: Double = 2.0): Triple<Double, Double, Double> {
        if (prices.size < period) {
            val avg = if (prices.isNotEmpty()) prices.average() else 0.0
            return Triple(avg * 1.02, avg, avg * 0.98)
        }

        val subset = prices.takeLast(period)
        val sma = subset.average()
        val variance = subset.map { (it - sma) * (it - sma) }.average()
        val stdDev = sqrt(variance)

        return Triple(sma + (multiplier * stdDev), sma, sma - (multiplier * stdDev))
    }

    fun calculateATR(candles: List<CandleStick>, period: Int = 14): Double {
        if (candles.size < 2) return 0.0

        val trList = mutableListOf<Double>()
        for (i in 1 until candles.size) {
            val current = candles[i]
            val prev = candles[i - 1]
            val tr = max(
                current.high - current.low,
                max(
                    kotlin.math.abs(current.high - prev.close),
                    kotlin.math.abs(current.low - prev.close)
                )
            )
            trList.add(tr)
        }

        if (trList.isEmpty()) return 0.0
        return trList.takeLast(period).average()
    }
}
