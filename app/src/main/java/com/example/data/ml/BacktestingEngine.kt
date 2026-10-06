package com.example.data.ml

import com.example.data.remote.CandleStick
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class BacktestTrade(
    val entryDate: Long,
    val exitDate: Long,
    val symbol: String,
    val type: String, // "BUY" or "SELL"
    val entryPrice: Double,
    val exitPrice: Double,
    val quantity: Int,
    val profitLoss: Double,
    val returnPercentage: Double,
    val exitReason: String
)

data class BacktestResult(
    val symbol: String,
    val strategyName: String,
    val initialCapital: Double,
    val finalCapital: Double,
    val totalReturnPercentage: Double,
    val benchmarkReturnPercentage: Double, // Buy & Hold return
    val totalTrades: Int,
    val winningTrades: Int,
    val losingTrades: Int,
    val winRatePercentage: Double,
    val profitFactor: Double,
    val maxDrawdownPercentage: Double,
    val sharpeRatio: Double,
    val averageTradeReturn: Double,
    val trades: List<BacktestTrade>
)

object BacktestingEngine {

    enum class Strategy {
        MULTI_FACTOR_AI,
        RSI_MEAN_REVERSION,
        MACD_CROSSOVER,
        SMA_TREND_FOLLOWING
    }

    /**
     * Executes an event-driven backtesting simulation on historical OHLCV candles
     */
    fun runBacktest(
        symbol: String,
        candles: List<CandleStick>,
        strategy: Strategy = Strategy.MULTI_FACTOR_AI,
        initialCapital: Double = 100000.0,
        riskPerTradePercentage: Double = 2.0 // risk 2% per trade
    ): BacktestResult {
        if (candles.size < 25) {
            return emptyResult(symbol, strategy.name, initialCapital)
        }

        var capital = initialCapital
        var maxCapital = initialCapital
        var maxDrawdown = 0.0

        val trades = mutableListOf<BacktestTrade>()
        var inPosition = false
        var positionEntryPrice = 0.0
        var positionEntryDate = 0L
        var positionQty = 0
        var positionType = "BUY"
        var stopLoss = 0.0
        var targetPrice = 0.0

        val closes = candles.map { it.close }
        val benchmarkReturn = if (closes.isNotEmpty() && closes.first() > 0) {
            ((closes.last() - closes.first()) / closes.first()) * 100.0
        } else 0.0

        // Step through candles chronologically starting after warmup period
        val warmupPeriod = 20
        for (i in warmupPeriod until candles.size) {
            val window = candles.subList(0, i + 1)
            val currentCandle = candles[i]
            val currentPrice = currentCandle.close

            // Check exit conditions if in position
            if (inPosition) {
                var shouldExit = false
                var exitReason = ""
                var exitPrice = currentPrice

                if (positionType == "BUY") {
                    if (currentCandle.low <= stopLoss) {
                        shouldExit = true
                        exitPrice = stopLoss
                        exitReason = "Stop Loss Hit"
                    } else if (currentCandle.high >= targetPrice) {
                        shouldExit = true
                        exitPrice = targetPrice
                        exitReason = "Target Reached"
                    } else if (i == candles.lastIndex) {
                        shouldExit = true
                        exitPrice = currentPrice
                        exitReason = "Simulation End"
                    }
                }

                if (shouldExit) {
                    val pnl = (exitPrice - positionEntryPrice) * positionQty
                    val tradeReturnPct = ((exitPrice - positionEntryPrice) / positionEntryPrice) * 100.0
                    capital += (positionQty * positionEntryPrice) + pnl
                    maxCapital = max(maxCapital, capital)

                    val currentDrawdown = if (maxCapital > 0) ((maxCapital - capital) / maxCapital) * 100.0 else 0.0
                    maxDrawdown = max(maxDrawdown, currentDrawdown)

                    trades.add(
                        BacktestTrade(
                            entryDate = positionEntryDate,
                            exitDate = currentCandle.timestamp,
                            symbol = symbol,
                            type = positionType,
                            entryPrice = (positionEntryPrice * 100.0).roundToInt() / 100.0,
                            exitPrice = (exitPrice * 100.0).roundToInt() / 100.0,
                            quantity = positionQty,
                            profitLoss = (pnl * 100.0).roundToInt() / 100.0,
                            returnPercentage = (tradeReturnPct * 100.0).roundToInt() / 100.0,
                            exitReason = exitReason
                        )
                    )

                    inPosition = false
                }
            }

            // Check entry signal if not currently in a position
            if (!inPosition && i < candles.lastIndex) {
                val signal = evaluateSignal(strategy, symbol, window, currentPrice)
                if (signal == "BUY") {
                    val riskCapital = capital * (riskPerTradePercentage / 100.0)
                    val calculatedStop = currentPrice * 0.965 // 3.5% stop loss
                    val calculatedTarget = currentPrice * 1.055 // 5.5% profit target
                    val riskPerShare = currentPrice - calculatedStop

                    val qty = if (riskPerShare > 0) {
                        (riskCapital / riskPerShare).toInt().coerceAtLeast(1)
                    } else 1

                    val totalCost = qty * currentPrice
                    if (totalCost <= capital) {
                        capital -= totalCost
                        inPosition = true
                        positionEntryPrice = currentPrice
                        positionEntryDate = currentCandle.timestamp
                        positionQty = qty
                        positionType = "BUY"
                        stopLoss = calculatedStop
                        targetPrice = calculatedTarget
                    }
                }
            }
        }

        // Close any remaining position at the end of the simulation
        if (inPosition) {
            val lastPrice = candles.last().close
            val pnl = (lastPrice - positionEntryPrice) * positionQty
            val tradeReturnPct = ((lastPrice - positionEntryPrice) / positionEntryPrice) * 100.0
            capital += (positionQty * positionEntryPrice) + pnl

            trades.add(
                BacktestTrade(
                    entryDate = positionEntryDate,
                    exitDate = candles.last().timestamp,
                    symbol = symbol,
                    type = positionType,
                    entryPrice = (positionEntryPrice * 100.0).roundToInt() / 100.0,
                    exitPrice = (lastPrice * 100.0).roundToInt() / 100.0,
                    quantity = positionQty,
                    profitLoss = (pnl * 100.0).roundToInt() / 100.0,
                    returnPercentage = (tradeReturnPct * 100.0).roundToInt() / 100.0,
                    exitReason = "Period Close"
                )
            )
        }

        // Compute performance statistics
        val winningTrades = trades.count { it.profitLoss > 0 }
        val losingTrades = trades.count { it.profitLoss <= 0 }
        val winRate = if (trades.isNotEmpty()) (winningTrades.toDouble() / trades.size) * 100.0 else 0.0

        val grossProfit = trades.filter { it.profitLoss > 0 }.sumOf { it.profitLoss }
        val grossLoss = kotlin.math.abs(trades.filter { it.profitLoss < 0 }.sumOf { it.profitLoss })
        val profitFactor = if (grossLoss > 0) grossProfit / grossLoss else if (grossProfit > 0) 9.99 else 0.0

        val totalReturnPct = ((capital - initialCapital) / initialCapital) * 100.0
        val avgTradeReturn = if (trades.isNotEmpty()) trades.map { it.returnPercentage }.average() else 0.0

        // Sharpe Ratio approximation (Annualized assuming daily candles with 252 trading days)
        val tradeReturns = trades.map { it.returnPercentage / 100.0 }
        val stdDev = if (tradeReturns.size > 1) {
            val mean = tradeReturns.average()
            sqrt(tradeReturns.map { (it - mean) * (it - mean) }.average())
        } else 0.01

        val riskFreeDaily = 0.06 / 252.0 // 6% risk-free rate
        val sharpeRatio = if (stdDev > 0) {
            ((tradeReturns.average() - riskFreeDaily) / stdDev) * sqrt(252.0)
        } else 0.0

        return BacktestResult(
            symbol = symbol,
            strategyName = strategy.name.replace("_", " "),
            initialCapital = initialCapital,
            finalCapital = (capital * 100.0).roundToInt() / 100.0,
            totalReturnPercentage = (totalReturnPct * 100.0).roundToInt() / 100.0,
            benchmarkReturnPercentage = (benchmarkReturn * 100.0).roundToInt() / 100.0,
            totalTrades = trades.size,
            winningTrades = winningTrades,
            losingTrades = losingTrades,
            winRatePercentage = (winRate * 10.0).roundToInt() / 10.0,
            profitFactor = (profitFactor * 100.0).roundToInt() / 100.0,
            maxDrawdownPercentage = (maxDrawdown * 10.0).roundToInt() / 10.0,
            sharpeRatio = (sharpeRatio * 100.0).roundToInt() / 100.0,
            averageTradeReturn = (avgTradeReturn * 100.0).roundToInt() / 100.0,
            trades = trades
        )
    }

    private fun evaluateSignal(
        strategy: Strategy,
        symbol: String,
        window: List<CandleStick>,
        currentPrice: Double
    ): String {
        return when (strategy) {
            Strategy.MULTI_FACTOR_AI -> {
                val indicators = TechnicalAnalysisEngine.calculateIndicators(window)
                val lstm = LSTMPredictionEngine.predictPriceTrajectory(symbol, window, 5)
                val rec = RecommendationEngine.generateRecommendation(symbol, currentPrice, indicators, lstm)
                if (rec.signal.contains("BUY")) "BUY" else "HOLD"
            }
            Strategy.RSI_MEAN_REVERSION -> {
                val closes = window.map { it.close }
                val rsi = TechnicalAnalysisEngine.calculateRSI(closes, 14)
                if (rsi < 35.0) "BUY" else "HOLD"
            }
            Strategy.MACD_CROSSOVER -> {
                val closes = window.map { it.close }
                val (macd, signal, _) = TechnicalAnalysisEngine.calculateMACD(closes)
                if (macd > signal) "BUY" else "HOLD"
            }
            Strategy.SMA_TREND_FOLLOWING -> {
                val closes = window.map { it.close }
                val sma20 = TechnicalAnalysisEngine.calculateSMA(closes, 20)
                val sma50 = TechnicalAnalysisEngine.calculateSMA(closes, 50)
                if (currentPrice > sma20 && sma20 > sma50) "BUY" else "HOLD"
            }
        }
    }

    private fun emptyResult(symbol: String, strategy: String, capital: Double) = BacktestResult(
        symbol = symbol,
        strategyName = strategy,
        initialCapital = capital,
        finalCapital = capital,
        totalReturnPercentage = 0.0,
        benchmarkReturnPercentage = 0.0,
        totalTrades = 0,
        winningTrades = 0,
        losingTrades = 0,
        winRatePercentage = 0.0,
        profitFactor = 0.0,
        maxDrawdownPercentage = 0.0,
        sharpeRatio = 0.0,
        averageTradeReturn = 0.0,
        trades = emptyList()
    )
}
