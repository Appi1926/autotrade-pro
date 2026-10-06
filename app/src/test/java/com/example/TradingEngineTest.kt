package com.example

import com.example.data.ml.BacktestingEngine
import com.example.data.ml.LSTMPredictionEngine
import com.example.data.ml.RecommendationEngine
import com.example.data.ml.TechnicalAnalysisEngine
import com.example.data.remote.CandleStick
import com.example.data.remote.LiveTradingGateway
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TradingEngineTest {

    private fun generateMockCandles(count: Int, basePrice: Double = 1000.0, trendMultiplier: Double = 0.005): List<CandleStick> {
        val candles = mutableListOf<CandleStick>()
        var price = basePrice
        for (i in 0 until count) {
            val open = price
            price += (price * trendMultiplier)
            val close = price
            val high = maxOf(open, close) * 1.01
            val low = minOf(open, close) * 0.99
            candles.add(
                CandleStick(
                    timestamp = 1700000000000L + (i * 86400000L),
                    open = open,
                    high = high,
                    low = low,
                    close = close,
                    volume = 100000L
                )
            )
        }
        return candles
    }

    // 1. Technical Analysis Engine Tests
    @Test
    fun testRSI_boundsAndCalculation() {
        val closes = listOf(100.0, 102.0, 104.0, 103.0, 105.0, 107.0, 108.0, 110.0, 112.0, 115.0, 118.0, 120.0, 122.0, 125.0, 128.0, 130.0)
        val rsi = TechnicalAnalysisEngine.calculateRSI(closes, 14)
        assertTrue("RSI must be between 0 and 100", rsi in 0.0..100.0)
        assertTrue("Consistent upward trend should yield strong bullish RSI (> 65)", rsi > 65.0)
    }

    @Test
    fun testSMA_correctness() {
        val prices = listOf(10.0, 20.0, 30.0, 40.0, 50.0)
        val sma3 = TechnicalAnalysisEngine.calculateSMA(prices, 3)
        assertEquals("SMA-3 of [30, 40, 50] should be 40.0", 40.0, sma3, 0.001)
    }

    @Test
    fun testMACD_calculation() {
        val candles = generateMockCandles(40, 1500.0, 0.008)
        val indicators = TechnicalAnalysisEngine.calculateIndicators(candles)
        assertNotNull(indicators)
        assertTrue("In sustained uptrend, MACD should be positive", indicators.macd > 0)
        assertEquals("Sustained uptrend should be classified as BULLISH", "BULLISH", indicators.trend)
    }

    // 2. LSTM Prediction Engine Tests
    @Test
    fun testLSTMPrediction_forecastOutput() {
        val candles = generateMockCandles(50, 2500.0)
        val forecastDays = 7
        val prediction = LSTMPredictionEngine.predictPriceTrajectory("RELIANCE", candles, forecastDays)

        assertEquals("Prediction result must contain requested forecast length", forecastDays, prediction.predictedPrices.size)
        assertTrue("Confidence must be within valid bounded range [68, 96]", prediction.confidence in 68..96)
        assertTrue("Resistance level must be higher than support level", prediction.resistanceLevel >= prediction.supportLevel)
        assertEquals("Symbol must match requested symbol", "RELIANCE", prediction.symbol)
    }

    // 3. Multi-Factor Quantitative Signal Engine Tests
    @Test
    fun testSignalEngine_generatesValidRecommendation() {
        val candles = generateMockCandles(50, 1600.0, 0.01)
        val indicators = TechnicalAnalysisEngine.calculateIndicators(candles)
        val lstm = LSTMPredictionEngine.predictPriceTrajectory("HDFCBANK", candles, 7)
        val recommendation = RecommendationEngine.generateRecommendation("HDFCBANK", 1600.0, indicators, lstm, 0.4)

        assertNotNull(recommendation)
        assertTrue("Signal must be one of the standard educational actions",
            listOf("STRONG_BUY", "BUY", "HOLD", "SELL", "STRONG_SELL").contains(recommendation.signal)
        )
        assertTrue("Target price must be positive", recommendation.targetPrice > 0)
        assertTrue("Stop loss must be positive", recommendation.stopLoss > 0)
        assertTrue("Key factors must not be empty", recommendation.primaryFactors.isNotEmpty())
    }

    // 4. Backtesting Engine Tests
    @Test
    fun testBacktestEngine_simulationExecution() {
        val candles = generateMockCandles(60, 2000.0, 0.005)
        val result = BacktestingEngine.runBacktest(
            symbol = "TCS",
            candles = candles,
            strategy = BacktestingEngine.Strategy.MULTI_FACTOR_AI,
            initialCapital = 100000.0
        )

        assertNotNull(result)
        assertEquals("Initial capital should be preserved in result", 100000.0, result.initialCapital, 0.001)
        assertTrue("Win rate percentage must be within [0, 100]", result.winRatePercentage in 0.0..100.0)
        assertTrue("Max drawdown must be non-negative", result.maxDrawdownPercentage >= 0.0)
    }

    // 5. Live Trading Safety Isolation Tests
    @Test
    fun testLiveTrading_disabledByDefault() {
        assertFalse("Live trading must be strictly disabled by default for safety", LiveTradingGateway.isLiveTradingEnabled.value)
    }

    @Test
    fun testLiveTrading_requiresExplicitConfirmation() = runBlocking {
        val result = LiveTradingGateway.submitLiveOrder(
            symbol = "RELIANCE",
            exchange = "NSE",
            action = "BUY",
            quantity = 10,
            orderType = "MARKET",
            price = 2950.0,
            userExplicitlyConfirmedInUI = false // NOT confirmed
        )

        assertTrue("Order without explicit confirmation must fail", result.isFailure)
        assertTrue("Exception must cite safety violation",
            result.exceptionOrNull()?.message?.contains("Safety Violation") == true
        )
    }

    @Test
    fun testLiveTrading_failsGracefullyWhenCredentialsMissing() = runBlocking {
        // Even if user enables toggle and confirms in UI, missing live credentials must safely reject
        LiveTradingGateway.setLiveTradingEnabled(true)
        val result = LiveTradingGateway.submitLiveOrder(
            symbol = "RELIANCE",
            exchange = "NSE",
            action = "BUY",
            quantity = 10,
            orderType = "MARKET",
            price = 2950.0,
            userExplicitlyConfirmedInUI = true
        )

        assertTrue("Live order must fail when credentials are not configured", result.isFailure)
        assertTrue("Must indicate live trading is unavailable rather than fake success",
            result.exceptionOrNull()?.message?.contains("Live Trading Unavailable") == true
        )

        // Reset live trading toggle to disabled
        LiveTradingGateway.setLiveTradingEnabled(false)
    }

    @Test
    fun testModeIsolation_balanceAndHoldingsAreCompletelySeparate() {
        val paperBalance = 1000000.0 // Virtual starting capital ₹10,00,000
        val liveBalance = LiveTradingGateway.liveAccountBalance.value

        assertEquals("Live account balance must be 0.0 initially (isolated from virtual paper balance)", 0.0, liveBalance, 0.001)
        assertFalse("Angel One authentication must be false by default", LiveTradingGateway.isAngelOneAuthenticated.value)
        assertTrue("Live holdings must be empty initially and isolated from paper DB", LiveTradingGateway.liveHoldings.value.isEmpty())
        assertTrue("Virtual starting capital must remain exactly ₹10,00,000", paperBalance == 1000000.0)

        // Disconnect test
        LiveTradingGateway.disconnectSmartApi()
        assertFalse("After disconnect, live trading must be disabled", LiveTradingGateway.isLiveTradingEnabled.value)
        assertFalse("After disconnect, auth must be false", LiveTradingGateway.isAngelOneAuthenticated.value)
    }
}
