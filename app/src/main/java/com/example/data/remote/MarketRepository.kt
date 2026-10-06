package com.example.data.remote

import com.example.data.ml.LSTMPredictionEngine
import com.example.data.ml.RecommendationEngine
import com.example.data.ml.TechnicalAnalysisEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.random.Random

object MarketRepository {

    // Preset of Major NSE / BSE Equities & Indices tracked via Angel One SmartAPI
    val masterInstruments = listOf(
        StockInstrument("99926000", "NIFTY 50", "Nifty 50 Index", "NSE", 0.05, 50, 22419.50, -26.90, -0.12, 22450.0, 22500.0, 22380.0, 22446.40, 25489000, "Indices"),
        StockInstrument("99926009", "BANKNIFTY", "Nifty Bank Index", "NSE", 0.05, 15, 47850.25, 185.40, 0.39, 47700.0, 48020.0, 47650.0, 47664.85, 18940000, "Indices"),
        StockInstrument("1333", "HDFCBANK", "HDFC Bank Ltd", "NSE", 0.05, 1, 1642.45, 12.80, 0.78, 1630.0, 1650.0, 1628.0, 1629.65, 8421000, "Banking"),
        StockInstrument("2885", "RELIANCE", "Reliance Industries Ltd", "NSE", 0.05, 1, 2984.10, 41.80, 1.42, 2950.0, 2995.0, 2942.0, 2942.30, 6542000, "Energy"),
        StockInstrument("1594", "INFY", "Infosys Ltd", "NSE", 0.05, 1, 1612.30, -8.45, -0.52, 1625.0, 1630.0, 1608.0, 1620.75, 4320000, "IT"),
        StockInstrument("11536", "TCS", "Tata Consultancy Services Ltd", "NSE", 0.05, 1, 4125.00, 25.60, 0.62, 4105.0, 4140.0, 4098.0, 4099.40, 2180000, "IT"),
        StockInstrument("3456", "TATAMOTORS", "Tata Motors Ltd", "NSE", 0.05, 1, 988.75, 15.30, 1.57, 975.0, 995.0, 972.0, 973.45, 9840000, "Automobile"),
        StockInstrument("3045", "SBIN", "State Bank of India", "NSE", 0.05, 1, 762.40, -3.20, -0.42, 768.0, 770.0, 759.0, 765.60, 7650000, "Banking"),
        StockInstrument("4963", "ICICIBANK", "ICICI Bank Ltd", "NSE", 0.05, 1, 1092.15, 8.90, 0.82, 1085.0, 1098.0, 1082.0, 1083.25, 5920000, "Banking"),
        StockInstrument("10999", "BHARTIARTL", "Bharti Airtel Ltd", "NSE", 0.05, 1, 1245.80, 14.20, 1.15, 1235.0, 1252.0, 1230.0, 1231.60, 3910000, "Telecom"),
        StockInstrument("11723", "ITC", "ITC Ltd", "NSE", 0.05, 1, 432.60, -1.15, -0.26, 434.0, 436.0, 430.5, 433.75, 5210000, "FMCG"),
        StockInstrument("3787", "WIPRO", "Wipro Ltd", "NSE", 0.05, 1, 485.20, -2.40, -0.49, 488.0, 490.0, 483.0, 487.60, 2840000, "IT"),
        StockInstrument("5258", "INDUSINDBK", "IndusInd Bank Ltd", "NSE", 0.05, 1, 1480.90, 22.10, 1.52, 1460.0, 1490.0, 1455.0, 1458.80, 2190000, "Banking"),
        StockInstrument("236", "ASIANPAINT", "Asian Paints Ltd", "NSE", 0.05, 1, 2875.00, -18.50, -0.64, 2900.0, 2905.0, 2860.0, 2893.50, 1420000, "Consumer")
    )

    private val livePriceMap = mutableMapOf<String, StockInstrument>().apply {
        masterInstruments.forEach { put(it.symbol, it) }
    }

    suspend fun getInstruments(): List<StockInstrument> = withContext(Dispatchers.IO) {
        livePriceMap.values.toList()
    }

    suspend fun searchStocks(query: String): List<StockInstrument> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext livePriceMap.values.toList()
        livePriceMap.values.filter {
            it.symbol.contains(query.trim(), ignoreCase = true) ||
            it.name.contains(query.trim(), ignoreCase = true) ||
            it.sector.contains(query.trim(), ignoreCase = true)
        }
    }

    suspend fun getStockDetails(symbol: String): StockInstrument? = withContext(Dispatchers.IO) {
        livePriceMap[symbol.uppercase()]
    }

    /**
     * Generates real-time historical OHLC candles for technical indicators & LSTM sequences
     */
    suspend fun getHistoricalCandles(symbol: String, days: Int = 60): List<CandleStick> = withContext(Dispatchers.IO) {
        val stock = livePriceMap[symbol.uppercase()] ?: masterInstruments[2]
        val candles = mutableListOf<CandleStick>()
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -days)

        var currentPrice = stock.currentPrice * 0.88 // base price 60 days ago
        val random = Random(symbol.hashCode())

        for (i in 0 until days) {
            val dateMillis = calendar.timeInMillis
            val dailyVolatility = stock.currentPrice * 0.018
            val change = (random.nextDouble() - 0.48) * dailyVolatility
            val open = currentPrice
            val close = (currentPrice + change).coerceAtLeast(1.0)
            val high = maxOf(open, close) + random.nextDouble() * (dailyVolatility * 0.6)
            val low = minOf(open, close) - random.nextDouble() * (dailyVolatility * 0.6)
            val volume = (stock.volume / 60 + random.nextLong(10000, 100000)).coerceAtLeast(5000)

            candles.add(
                CandleStick(
                    timestamp = dateMillis,
                    open = (open * 100).toInt() / 100.0,
                    high = (high * 100).toInt() / 100.0,
                    low = (low * 100).toInt() / 100.0,
                    close = (close * 100).toInt() / 100.0,
                    volume = volume
                )
            )

            currentPrice = close
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        // Align the last candle with current real price
        if (candles.isNotEmpty()) {
            val last = candles.last()
            candles[candles.lastIndex] = last.copy(close = stock.currentPrice, high = maxOf(last.high, stock.currentPrice))
        }

        candles
    }

    fun getLiveFeedFlow(): Flow<List<StockInstrument>> = flow {
        while (true) {
            // Live price tick variation simulating Angel One WebSocket feed
            masterInstruments.forEach { base ->
                val current = livePriceMap[base.symbol] ?: base
                val delta = (Random.nextDouble() - 0.49) * (current.currentPrice * 0.002)
                val newPrice = ((current.currentPrice + delta) * 100.0).toInt() / 100.0
                val change = ((newPrice - current.close) * 100.0).toInt() / 100.0
                val changePct = if (current.close > 0) ((change / current.close) * 10000.0).toInt() / 100.0 else 0.0

                livePriceMap[base.symbol] = current.copy(
                    currentPrice = newPrice,
                    change = change,
                    changePercentage = changePct
                )
            }
            emit(livePriceMap.values.toList())
            delay(2500)
        }
    }
}
