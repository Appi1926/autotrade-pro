package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.local.HoldingEntity
import com.example.data.local.NotificationEntity
import com.example.data.local.PortfolioEntity
import com.example.data.local.TransactionEntity
import com.example.data.remote.MarketRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

object TradingRepository {

    suspend fun executeBuyOrder(
        database: AppDatabase,
        userId: String,
        symbol: String,
        name: String,
        quantity: Int,
        price: Double
    ): Result<TransactionEntity> = withContext(Dispatchers.IO) {
        if (quantity <= 0) return@withContext Result.failure(Exception("Quantity must be greater than 0"))
        if (price <= 0) return@withContext Result.failure(Exception("Invalid stock price"))

        val totalAmount = quantity * price
        val user = database.userDao().getUserById(userId)
            ?: return@withContext Result.failure(Exception("User account not found"))

        if (user.virtualBalance < totalAmount) {
            return@withContext Result.failure(
                Exception("Insufficient virtual funds. Balance: ₹${String.format("%.2f", user.virtualBalance)}, Required: ₹${String.format("%.2f", totalAmount)}")
            )
        }

        // 1. Deduct balance
        val newBalance = user.virtualBalance - totalAmount
        database.userDao().updateBalance(userId, newBalance)
        AuthRepository.switchUser(user.copy(virtualBalance = newBalance))

        // 2. Insert Transaction Record
        val transaction = TransactionEntity(
            id = "TX_${UUID.randomUUID().toString().take(8).uppercase()}",
            userId = userId,
            symbol = symbol,
            name = name,
            type = "BUY",
            quantity = quantity,
            price = price,
            totalAmount = totalAmount,
            timestamp = System.currentTimeMillis(),
            status = "COMPLETED"
        )
        database.tradingDao().insertTransaction(transaction)

        // 3. Update Holdings
        val existingHolding = database.tradingDao().getHolding(userId, symbol)
        if (existingHolding != null) {
            val totalQty = existingHolding.quantity + quantity
            val totalInvested = (existingHolding.quantity * existingHolding.averageBuyPrice) + totalAmount
            val newAvgPrice = totalInvested / totalQty
            val currentValue = totalQty * price
            val pnl = currentValue - totalInvested
            val pnlPct = (pnl / totalInvested) * 100.0

            val updated = existingHolding.copy(
                quantity = totalQty,
                averageBuyPrice = (newAvgPrice * 100.0).toInt() / 100.0,
                currentPrice = price,
                investedValue = totalInvested,
                currentValue = currentValue,
                profitLoss = pnl,
                pnlPercentage = pnlPct,
                lastUpdated = System.currentTimeMillis()
            )
            database.tradingDao().insertHolding(updated)
        } else {
            val holding = HoldingEntity(
                id = "${userId}_$symbol",
                userId = userId,
                symbol = symbol,
                name = name,
                exchange = "NSE",
                quantity = quantity,
                averageBuyPrice = price,
                currentPrice = price,
                investedValue = totalAmount,
                currentValue = totalAmount,
                profitLoss = 0.0,
                pnlPercentage = 0.0,
                lastUpdated = System.currentTimeMillis()
            )
            database.tradingDao().insertHolding(holding)
        }

        // 4. Update Portfolio
        recalculatePortfolio(database, userId)

        // 5. Trigger notification
        database.notificationDao().insertNotification(
            NotificationEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                title = "Paper Buy Order Executed",
                message = "Bought $quantity shares of $symbol at ₹${String.format("%.2f", price)}. Total: ₹${String.format("%.2f", totalAmount)}",
                type = "TRADE"
            )
        )

        Result.success(transaction)
    }

    suspend fun executeSellOrder(
        database: AppDatabase,
        userId: String,
        symbol: String,
        quantity: Int,
        price: Double
    ): Result<TransactionEntity> = withContext(Dispatchers.IO) {
        if (quantity <= 0) return@withContext Result.failure(Exception("Quantity must be greater than 0"))

        val holding = database.tradingDao().getHolding(userId, symbol)
            ?: return@withContext Result.failure(Exception("You do not hold any shares of $symbol"))

        if (holding.quantity < quantity) {
            return@withContext Result.failure(
                Exception("Cannot sell $quantity shares. You only own ${holding.quantity} shares.")
            )
        }

        val totalAmount = quantity * price
        val user = database.userDao().getUserById(userId)
            ?: return@withContext Result.failure(Exception("User account not found"))

        // 1. Credit balance
        val newBalance = user.virtualBalance + totalAmount
        database.userDao().updateBalance(userId, newBalance)
        AuthRepository.switchUser(user.copy(virtualBalance = newBalance))

        // 2. Insert Transaction Record
        val transaction = TransactionEntity(
            id = "TX_${UUID.randomUUID().toString().take(8).uppercase()}",
            userId = userId,
            symbol = symbol,
            name = holding.name,
            type = "SELL",
            quantity = quantity,
            price = price,
            totalAmount = totalAmount,
            timestamp = System.currentTimeMillis(),
            status = "COMPLETED"
        )
        database.tradingDao().insertTransaction(transaction)

        // 3. Update Holdings
        val remainingQty = holding.quantity - quantity
        if (remainingQty <= 0) {
            database.tradingDao().deleteHolding(holding)
        } else {
            val remainingInvested = remainingQty * holding.averageBuyPrice
            val remainingCurrent = remainingQty * price
            val pnl = remainingCurrent - remainingInvested
            val pnlPct = if (remainingInvested > 0) (pnl / remainingInvested) * 100.0 else 0.0

            val updated = holding.copy(
                quantity = remainingQty,
                currentPrice = price,
                investedValue = remainingInvested,
                currentValue = remainingCurrent,
                profitLoss = pnl,
                pnlPercentage = pnlPct,
                lastUpdated = System.currentTimeMillis()
            )
            database.tradingDao().insertHolding(updated)
        }

        // 4. Update Portfolio
        recalculatePortfolio(database, userId)

        // 5. Trigger notification
        database.notificationDao().insertNotification(
            NotificationEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                title = "Paper Sell Order Executed",
                message = "Sold $quantity shares of $symbol at ₹${String.format("%.2f", price)}. Total: ₹${String.format("%.2f", totalAmount)}",
                type = "TRADE"
            )
        )

        Result.success(transaction)
    }

    suspend fun recalculatePortfolio(database: AppDatabase, userId: String) = withContext(Dispatchers.IO) {
        val holdings = database.tradingDao().getHoldings(userId)
        val user = database.userDao().getUserById(userId) ?: return@withContext

        var totalInvested = 0.0
        var currentHoldingsValuation = 0.0

        holdings.forEach { h ->
            val liveStock = MarketRepository.getStockDetails(h.symbol)
            val cmp = liveStock?.currentPrice ?: h.currentPrice
            val curVal = h.quantity * cmp
            val invested = h.quantity * h.averageBuyPrice

            totalInvested += invested
            currentHoldingsValuation += curVal
        }

        val totalPortfolioValue = user.virtualBalance + currentHoldingsValuation
        val totalProfitLoss = currentHoldingsValuation - totalInvested
        val returnsPercentage = if (totalInvested > 0) (totalProfitLoss / totalInvested) * 100.0 else 0.0

        val portfolio = PortfolioEntity(
            userId = userId,
            totalInvested = totalInvested,
            currentValuation = totalPortfolioValue,
            totalProfitLoss = totalProfitLoss,
            returnsPercentage = returnsPercentage,
            lastUpdated = System.currentTimeMillis()
        )
        database.portfolioDao().insertOrUpdatePortfolio(portfolio)
    }

    fun observeHoldings(database: AppDatabase, userId: String): Flow<List<HoldingEntity>> {
        return database.tradingDao().observeHoldings(userId)
    }

    fun observeTransactions(database: AppDatabase, userId: String): Flow<List<TransactionEntity>> {
        return database.tradingDao().observeTransactions(userId)
    }

    fun observePortfolio(database: AppDatabase, userId: String): Flow<PortfolioEntity?> {
        return database.portfolioDao().observePortfolio(userId)
    }

    suspend fun recordCancelledOrder(
        database: AppDatabase,
        userId: String,
        symbol: String,
        name: String,
        type: String,
        quantity: Int,
        price: Double,
        reason: String = "Cancelled by trader"
    ): Result<TransactionEntity> = withContext(Dispatchers.IO) {
        val totalAmount = quantity * price
        val transaction = TransactionEntity(
            id = "TX_${UUID.randomUUID().toString().take(8).uppercase()}",
            userId = userId,
            symbol = symbol,
            name = name,
            type = type,
            quantity = quantity,
            price = price,
            totalAmount = totalAmount,
            timestamp = System.currentTimeMillis(),
            status = "CANCELLED"
        )
        database.tradingDao().insertTransaction(transaction)
        database.notificationDao().insertNotification(
            NotificationEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                title = "Paper Order Cancelled",
                message = "$type order for $quantity shares of $symbol @ ₹${String.format("%.2f", price)} cancelled ($reason).",
                type = "TRADE"
            )
        )
        Result.success(transaction)
    }

    suspend fun seedSampleTradesIfEmpty(database: AppDatabase, userId: String) = withContext(Dispatchers.IO) {
        val existingTx = database.tradingDao().getTransactions(userId)
        if (existingTx.isNotEmpty()) return@withContext

        val now = System.currentTimeMillis()
        val sampleTrades = listOf(
            TransactionEntity(
                id = "TX_REL782A",
                userId = userId,
                symbol = "RELIANCE",
                name = "Reliance Industries Ltd.",
                type = "BUY",
                quantity = 25,
                price = 2940.50,
                totalAmount = 25 * 2940.50,
                timestamp = now - (2 * 3600 * 1000L), // 2 hrs ago
                status = "COMPLETED"
            ),
            TransactionEntity(
                id = "TX_TCS419B",
                userId = userId,
                symbol = "TCS",
                name = "Tata Consultancy Services Ltd.",
                type = "BUY",
                quantity = 15,
                price = 4120.00,
                totalAmount = 15 * 4120.00,
                timestamp = now - (6 * 3600 * 1000L), // 6 hrs ago
                status = "COMPLETED"
            ),
            TransactionEntity(
                id = "TX_HDFC91C",
                userId = userId,
                symbol = "HDFCBANK",
                name = "HDFC Bank Ltd.",
                type = "BUY",
                quantity = 30,
                price = 1595.00,
                totalAmount = 30 * 1595.00,
                timestamp = now - (18 * 3600 * 1000L), // 18 hrs ago
                status = "CANCELLED"
            ),
            TransactionEntity(
                id = "TX_INFY55D",
                userId = userId,
                symbol = "INFY",
                name = "Infosys Ltd.",
                type = "SELL",
                quantity = 10,
                price = 1860.25,
                totalAmount = 10 * 1860.25,
                timestamp = now - (26 * 3600 * 1000L), // 1 day ago
                status = "COMPLETED"
            ),
            TransactionEntity(
                id = "TX_TATA22E",
                userId = userId,
                symbol = "TATAMOTORS",
                name = "Tata Motors Ltd.",
                type = "SELL",
                quantity = 20,
                price = 1115.00,
                totalAmount = 20 * 1115.00,
                timestamp = now - (48 * 3600 * 1000L), // 2 days ago
                status = "CANCELLED"
            ),
            TransactionEntity(
                id = "TX_ICIC88F",
                userId = userId,
                symbol = "ICICIBANK",
                name = "ICICI Bank Ltd.",
                type = "BUY",
                quantity = 40,
                price = 1205.50,
                totalAmount = 40 * 1205.50,
                timestamp = now - (72 * 3600 * 1000L), // 3 days ago
                status = "COMPLETED"
            )
        )

        sampleTrades.forEach { tx ->
            database.tradingDao().insertTransaction(tx)
        }

        // Seed initial holdings corresponding to the completed BUYs if no holdings exist
        val existingHoldings = database.tradingDao().getHoldings(userId)
        if (existingHoldings.isEmpty()) {
            val h1 = HoldingEntity(
                id = "${userId}_RELIANCE",
                userId = userId,
                symbol = "RELIANCE",
                name = "Reliance Industries Ltd.",
                exchange = "NSE",
                quantity = 25,
                averageBuyPrice = 2940.50,
                currentPrice = 2985.00,
                investedValue = 25 * 2940.50,
                currentValue = 25 * 2985.00,
                profitLoss = (25 * 2985.00) - (25 * 2940.50),
                pnlPercentage = ((2985.00 - 2940.50) / 2940.50) * 100.0,
                lastUpdated = now
            )
            val h2 = HoldingEntity(
                id = "${userId}_TCS",
                userId = userId,
                symbol = "TCS",
                name = "Tata Consultancy Services Ltd.",
                exchange = "NSE",
                quantity = 15,
                averageBuyPrice = 4120.00,
                currentPrice = 4190.00,
                investedValue = 15 * 4120.00,
                currentValue = 15 * 4190.00,
                profitLoss = (15 * 4190.00) - (15 * 4120.00),
                pnlPercentage = ((4190.00 - 4120.00) / 4120.00) * 100.0,
                lastUpdated = now
            )
            val h3 = HoldingEntity(
                id = "${userId}_ICICIBANK",
                userId = userId,
                symbol = "ICICIBANK",
                name = "ICICI Bank Ltd.",
                exchange = "NSE",
                quantity = 40,
                averageBuyPrice = 1205.50,
                currentPrice = 1235.00,
                investedValue = 40 * 1205.50,
                currentValue = 40 * 1235.00,
                profitLoss = (40 * 1235.00) - (40 * 1205.50),
                pnlPercentage = ((1235.00 - 1205.50) / 1205.50) * 100.0,
                lastUpdated = now
            )
            database.tradingDao().insertHolding(h1)
            database.tradingDao().insertHolding(h2)
            database.tradingDao().insertHolding(h3)

            val totalInvested = h1.investedValue + h2.investedValue + h3.investedValue
            val user = database.userDao().getUserById(userId)
            if (user != null) {
                val updatedBalance = (1000000.0 - totalInvested).coerceAtLeast(100000.0)
                database.userDao().updateBalance(userId, updatedBalance)
                AuthRepository.switchUser(user.copy(virtualBalance = updatedBalance))
            }
            recalculatePortfolio(database, userId)
        }
    }
}
