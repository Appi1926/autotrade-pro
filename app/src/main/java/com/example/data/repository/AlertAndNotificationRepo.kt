package com.example.data.repository

import com.example.data.local.AlertEntity
import com.example.data.local.AppDatabase
import com.example.data.local.NotificationEntity
import com.example.data.remote.MarketRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

object AlertRepository {

    fun observeAlerts(database: AppDatabase, userId: String): Flow<List<AlertEntity>> {
        return database.alertDao().observeAlerts(userId)
    }

    suspend fun createAlert(
        database: AppDatabase,
        userId: String,
        symbol: String,
        targetPrice: Double,
        condition: String
    ): Result<AlertEntity> = withContext(Dispatchers.IO) {
        if (targetPrice <= 0) return@withContext Result.failure(Exception("Target price must be positive"))

        val alert = AlertEntity(
            id = "ALERT_${UUID.randomUUID().toString().take(6).uppercase()}",
            userId = userId,
            symbol = symbol,
            targetPrice = targetPrice,
            condition = condition,
            isTriggered = false
        )
        database.alertDao().insertAlert(alert)

        database.notificationDao().insertNotification(
            NotificationEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                title = "Smart Price Alert Created",
                message = "Alert set for $symbol when price moves $condition ₹${String.format("%.2f", targetPrice)}",
                type = "PRICE_ALERT"
            )
        )

        Result.success(alert)
    }

    suspend fun checkAndTriggerAlerts(database: AppDatabase) = withContext(Dispatchers.IO) {
        val active = database.alertDao().getActiveAlerts()
        for (alert in active) {
            val stock = MarketRepository.getStockDetails(alert.symbol) ?: continue
            val triggered = when (alert.condition) {
                "ABOVE" -> stock.currentPrice >= alert.targetPrice
                "BELOW" -> stock.currentPrice <= alert.targetPrice
                else -> false
            }

            if (triggered) {
                database.alertDao().updateAlert(alert.copy(isTriggered = true))
                database.notificationDao().insertNotification(
                    NotificationEntity(
                        id = UUID.randomUUID().toString(),
                        userId = alert.userId,
                        title = "🚨 Price Alert Triggered: ${alert.symbol}",
                        message = "${alert.symbol} crossed your target price of ₹${String.format("%.2f", alert.targetPrice)} (CMP: ₹${String.format("%.2f", stock.currentPrice)})",
                        type = "PRICE_ALERT"
                    )
                )
            }
        }
    }

    suspend fun deleteAlert(database: AppDatabase, alertId: String) = withContext(Dispatchers.IO) {
        database.alertDao().deleteAlert(alertId)
    }
}

object NotificationRepository {

    fun observeNotifications(database: AppDatabase, userId: String): Flow<List<NotificationEntity>> {
        return database.notificationDao().observeNotifications(userId)
    }

    suspend fun markAllAsRead(database: AppDatabase, userId: String) = withContext(Dispatchers.IO) {
        database.notificationDao().markAllAsRead(userId)
    }

    suspend fun clearNotifications(database: AppDatabase, userId: String) = withContext(Dispatchers.IO) {
        database.notificationDao().clearNotifications(userId)
    }
}
