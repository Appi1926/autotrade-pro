package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users WHERE mobile = :mobile LIMIT 1")
    suspend fun getUserByMobile(mobile: String): UserEntity?

    @Query("SELECT * FROM users WHERE email = :query OR mobile = :query LIMIT 1")
    suspend fun getUserByEmailOrMobile(query: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserById(userId: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    fun observeUser(userId: String): Flow<UserEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("UPDATE users SET virtualBalance = :newBalance WHERE id = :userId")
    suspend fun updateBalance(userId: String, newBalance: Double)

    @Query("SELECT * FROM users")
    suspend fun getAllUsers(): List<UserEntity>
}

@Dao
interface PortfolioDao {
    @Query("SELECT * FROM portfolios WHERE userId = :userId LIMIT 1")
    fun observePortfolio(userId: String): Flow<PortfolioEntity?>

    @Query("SELECT * FROM portfolios WHERE userId = :userId LIMIT 1")
    suspend fun getPortfolio(userId: String): PortfolioEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePortfolio(portfolio: PortfolioEntity)
}

@Dao
interface TradingDao {
    @Query("SELECT * FROM holdings WHERE userId = :userId")
    fun observeHoldings(userId: String): Flow<List<HoldingEntity>>

    @Query("SELECT * FROM holdings WHERE userId = :userId")
    suspend fun getHoldings(userId: String): List<HoldingEntity>

    @Query("SELECT * FROM holdings WHERE userId = :userId AND symbol = :symbol LIMIT 1")
    suspend fun getHolding(userId: String, symbol: String): HoldingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHolding(holding: HoldingEntity)

    @Delete
    suspend fun deleteHolding(holding: HoldingEntity)

    @Query("DELETE FROM holdings WHERE userId = :userId AND symbol = :symbol")
    suspend fun deleteHoldingBySymbol(userId: String, symbol: String)

    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY timestamp DESC")
    fun observeTransactions(userId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY timestamp DESC")
    suspend fun getTransactions(userId: String): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity)
}

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM watchlist WHERE userId = :userId ORDER BY addedAt DESC")
    fun observeWatchlist(userId: String): Flow<List<WatchlistEntity>>

    @Query("SELECT * FROM watchlist WHERE userId = :userId AND symbol = :symbol LIMIT 1")
    suspend fun getWatchlistItem(userId: String, symbol: String): WatchlistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchlist(item: WatchlistEntity)

    @Query("DELETE FROM watchlist WHERE userId = :userId AND symbol = :symbol")
    suspend fun deleteFromWatchlist(userId: String, symbol: String)
}

@Dao
interface AlertDao {
    @Query("SELECT * FROM alerts WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeAlerts(userId: String): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts WHERE isTriggered = 0")
    suspend fun getActiveAlerts(): List<AlertEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: AlertEntity)

    @Update
    suspend fun updateAlert(alert: AlertEntity)

    @Query("DELETE FROM alerts WHERE id = :alertId")
    suspend fun deleteAlert(alertId: String)
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications WHERE userId = :userId ORDER BY timestamp DESC")
    fun observeNotifications(userId: String): Flow<List<NotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity)

    @Query("UPDATE notifications SET isRead = 1 WHERE userId = :userId")
    suspend fun markAllAsRead(userId: String)

    @Query("DELETE FROM notifications WHERE userId = :userId")
    suspend fun clearNotifications(userId: String)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages WHERE userId = :userId ORDER BY timestamp ASC")
    fun observeMessages(userId: String): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE userId = :userId")
    suspend fun clearHistory(userId: String)
}
