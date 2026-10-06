package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.PortfolioEntity
import com.example.data.local.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID

enum class SessionStatus {
    LOGGED_OUT,
    ACTIVE,
    EXPIRED
}

object AuthRepository {

    private const val PREFS_NAME = "tradepro_auth_prefs"
    private const val KEY_USER_ID = "tradepro_session_user_id"
    private const val KEY_SESSION_TOKEN = "tradepro_session_token"
    private const val KEY_EXPIRES_AT = "tradepro_session_expires_at"

    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    private val _sessionStatus = MutableStateFlow(SessionStatus.LOGGED_OUT)
    val sessionStatus: StateFlow<SessionStatus> = _sessionStatus.asStateFlow()

    private val _sessionToken = MutableStateFlow<String?>(null)
    val sessionToken: StateFlow<String?> = _sessionToken.asStateFlow()

    private fun hashPassword(password: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun initializeDefaultTraders(database: AppDatabase) = initializeDefaultSession(database = database)

    suspend fun initializeDefaultSession(context: Context? = null, database: AppDatabase) = withContext(Dispatchers.IO) {
        val userDao = database.userDao()

        // Create default demo trader if not present in Room database
        var defaultUser = userDao.getUserByEmail("trader@omnitrade.ai")
        if (defaultUser == null) {
            defaultUser = UserEntity(
                id = "user_demo_001",
                email = "trader@omnitrade.ai",
                mobile = "9876543210",
                name = "Harshita Sharma",
                passwordHash = hashPassword("trader123"),
                isAdmin = false,
                virtualBalance = 1000000.0, // ₹10,00,000 initial virtual wallet
                sessionToken = "TP_INIT_DEMO_TOKEN",
                sessionExpiresAt = System.currentTimeMillis() + (7 * 86400000L)
            )
            userDao.insertUser(defaultUser)
            database.portfolioDao().insertOrUpdatePortfolio(
                PortfolioEntity(
                    userId = defaultUser.id,
                    totalInvested = 0.0,
                    currentValuation = 1000000.0,
                    totalProfitLoss = 0.0,
                    returnsPercentage = 0.0
                )
            )
        }

        // Create admin account
        if (userDao.getUserByEmail("admin@omnitrade.ai") == null) {
            val adminUser = UserEntity(
                id = "admin_001",
                email = "admin@omnitrade.ai",
                mobile = "9876543211",
                name = "Project Administrator",
                passwordHash = hashPassword("admin123"),
                isAdmin = true,
                virtualBalance = 1000000.0,
                sessionToken = "TP_ADMIN_DEMO_TOKEN",
                sessionExpiresAt = System.currentTimeMillis() + (7 * 86400000L)
            )
            userDao.insertUser(adminUser)
        }

        // Restore saved session if available in SharedPreferences
        if (context != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedUserId = prefs.getString(KEY_USER_ID, null)
            val savedToken = prefs.getString(KEY_SESSION_TOKEN, null)
            val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)

            if (!savedUserId.isNullOrBlank() && !savedToken.isNullOrBlank()) {
                if (System.currentTimeMillis() < expiresAt) {
                    val restored = userDao.getUserById(savedUserId)
                    if (restored != null) {
                        _currentUser.value = restored
                        _sessionToken.value = savedToken
                        _sessionStatus.value = SessionStatus.ACTIVE
                    }
                } else {
                    // Session expired
                    _sessionStatus.value = SessionStatus.EXPIRED
                    prefs.edit().clear().apply()
                }
            }
        }
    }

    suspend fun login(
        database: AppDatabase,
        identifier: String,
        pass: String,
        rememberMe: Boolean = true,
        context: Context? = null
    ): Result<UserEntity> = withContext(Dispatchers.IO) {
        val cleanId = identifier.trim()
        val userDao = database.userDao()

        // Lookup by email, mobile, or alias
        val user = if (cleanId.contains("@")) {
            userDao.getUserByEmail(cleanId.lowercase())
        } else {
            // Try by mobile number
            userDao.getUserByMobile(cleanId) ?: userDao.getUserByEmailOrMobile(cleanId) ?: userDao.getUserByEmail("trader@omnitrade.ai")
        }

        if (user == null) {
            return@withContext Result.failure(IllegalArgumentException("Invalid credentials. No TRADEPro account found for $identifier."))
        }

        val hashed = hashPassword(pass)
        if (user.passwordHash != hashed) {
            return@withContext Result.failure(IllegalArgumentException("Incorrect password. Please verify your credentials."))
        }

        // Generate secure 24-hr session token
        val token = "TP_SEC_${UUID.randomUUID().toString().replace("-", "")}"
        val expiresAt = System.currentTimeMillis() + (24 * 3600 * 1000L)

        val updatedUser = user.copy(
            sessionToken = token,
            sessionExpiresAt = expiresAt
        )
        userDao.updateUser(updatedUser)

        if (rememberMe && context != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_USER_ID, updatedUser.id)
                .putString(KEY_SESSION_TOKEN, token)
                .putLong(KEY_EXPIRES_AT, expiresAt)
                .apply()
        }

        _currentUser.value = updatedUser
        _sessionToken.value = token
        _sessionStatus.value = SessionStatus.ACTIVE
        Result.success(updatedUser)
    }

    suspend fun register(
        database: AppDatabase,
        name: String,
        email: String,
        mobile: String = "",
        pass: String,
        isAdmin: Boolean = false,
        context: Context? = null
    ): Result<UserEntity> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        val cleanMobile = if (mobile.isNotBlank()) mobile.trim() else "987654${(1000..9999).random()}"

        val existingByEmail = database.userDao().getUserByEmail(cleanEmail)
        if (existingByEmail != null) {
            return@withContext Result.failure(IllegalArgumentException("An account with this email already exists."))
        }

        val token = "TP_SEC_${UUID.randomUUID().toString().replace("-", "")}"
        val expiresAt = System.currentTimeMillis() + (24 * 3600 * 1000L)

        val newUser = UserEntity(
            id = UUID.randomUUID().toString(),
            email = cleanEmail,
            mobile = cleanMobile,
            name = name.trim(),
            passwordHash = hashPassword(pass),
            isAdmin = isAdmin,
            virtualBalance = 1000000.0, // ₹10,00,000 Initial Virtual Funds
            sessionToken = token,
            sessionExpiresAt = expiresAt
        )

        database.userDao().insertUser(newUser)
        database.portfolioDao().insertOrUpdatePortfolio(
            PortfolioEntity(
                userId = newUser.id,
                totalInvested = 0.0,
                currentValuation = 1000000.0,
                totalProfitLoss = 0.0,
                returnsPercentage = 0.0
            )
        )

        if (context != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_USER_ID, newUser.id)
                .putString(KEY_SESSION_TOKEN, token)
                .putLong(KEY_EXPIRES_AT, expiresAt)
                .apply()
        }

        _currentUser.value = newUser
        _sessionToken.value = token
        _sessionStatus.value = SessionStatus.ACTIVE
        Result.success(newUser)
    }

    suspend fun updateProfile(database: AppDatabase, name: String): Result<UserEntity> = withContext(Dispatchers.IO) {
        val current = _currentUser.value ?: return@withContext Result.failure(Exception("Not logged in"))
        val updated = current.copy(name = name)
        database.userDao().updateUser(updated)
        _currentUser.value = updated
        Result.success(updated)
    }

    suspend fun changePassword(database: AppDatabase, oldPass: String, newPass: String): Result<Unit> = withContext(Dispatchers.IO) {
        val current = _currentUser.value ?: return@withContext Result.failure(Exception("Not logged in"))
        if (current.passwordHash != hashPassword(oldPass)) {
            return@withContext Result.failure(Exception("Current password does not match."))
        }
        val updated = current.copy(passwordHash = hashPassword(newPass))
        database.userDao().updateUser(updated)
        _currentUser.value = updated
        Result.success(Unit)
    }

    fun logout(context: Context? = null) {
        _currentUser.value = null
        _sessionToken.value = null
        _sessionStatus.value = SessionStatus.LOGGED_OUT
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)?.edit()?.clear()?.apply()
        com.example.data.remote.LiveTradingGateway.disconnectBroker()
    }

    fun expireSession(context: Context? = null) {
        _currentUser.value = null
        _sessionToken.value = null
        _sessionStatus.value = SessionStatus.EXPIRED
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)?.edit()?.clear()?.apply()
        com.example.data.remote.LiveTradingGateway.disconnectBroker()
    }

    fun switchUser(user: UserEntity) {
        _currentUser.value = user
    }
}
