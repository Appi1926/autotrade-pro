package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val email: String,
    val mobile: String = "9876543210",
    val name: String,
    val passwordHash: String,
    val isAdmin: Boolean = false,
    val virtualBalance: Double = 1000000.0, // ₹10,00,000 initial virtual wallet
    val sessionToken: String = "",
    val sessionExpiresAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "portfolios")
data class PortfolioEntity(
    @PrimaryKey val userId: String,
    val totalInvested: Double = 0.0,
    val currentValuation: Double = 0.0,
    val totalProfitLoss: Double = 0.0,
    val returnsPercentage: Double = 0.0,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "holdings")
data class HoldingEntity(
    @PrimaryKey val id: String, // userId_symbol
    val userId: String,
    val symbol: String,
    val name: String,
    val exchange: String = "NSE",
    val quantity: Int,
    val averageBuyPrice: Double,
    val currentPrice: Double,
    val investedValue: Double,
    val currentValue: Double,
    val profitLoss: Double,
    val pnlPercentage: Double,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val symbol: String,
    val name: String,
    val type: String, // "BUY" or "SELL"
    val quantity: Int,
    val price: Double,
    val totalAmount: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "EXECUTED"
)

@Entity(tableName = "watchlist")
data class WatchlistEntity(
    @PrimaryKey val id: String, // userId_symbol
    val userId: String,
    val symbol: String,
    val name: String,
    val exchange: String = "NSE",
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val symbol: String,
    val targetPrice: Double,
    val condition: String, // "ABOVE" or "BELOW"
    val isTriggered: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val message: String,
    val type: String, // "TRADE", "PRICE_ALERT", "AI_RECOMMENDATION", "SYSTEM"
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
