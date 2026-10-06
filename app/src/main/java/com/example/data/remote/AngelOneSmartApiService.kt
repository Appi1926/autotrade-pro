package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

/**
 * Official Angel One SmartAPI (SmartConnect) REST API Models and Retrofit Interface.
 * Base URL: https://apiconnect.angelbroking.com
 */

@JsonClass(generateAdapter = true)
data class SmartApiLoginRequest(
    @Json(name = "clientcode") val clientCode: String,
    @Json(name = "password") val password: String,
    @Json(name = "totp") val totp: String
)

@JsonClass(generateAdapter = true)
data class SmartApiLoginResponse(
    @Json(name = "status") val status: Boolean = false,
    @Json(name = "message") val message: String = "",
    @Json(name = "errorcode") val errorCode: String? = null,
    @Json(name = "data") val data: SmartApiSessionData? = null
)

@JsonClass(generateAdapter = true)
data class SmartApiSessionData(
    @Json(name = "jwtToken") val jwtToken: String = "",
    @Json(name = "refreshToken") val refreshToken: String = "",
    @Json(name = "feedToken") val feedToken: String = ""
)

@JsonClass(generateAdapter = true)
data class SmartApiCandleRequest(
    @Json(name = "exchange") val exchange: String = "NSE",
    @Json(name = "symboltoken") val symbolToken: String,
    @Json(name = "interval") val interval: String = "ONE_DAY",
    @Json(name = "fromdate") val fromDate: String,
    @Json(name = "todate") val toDate: String
)

@JsonClass(generateAdapter = true)
data class SmartApiCandleResponse(
    @Json(name = "status") val status: Boolean = false,
    @Json(name = "message") val message: String = "",
    @Json(name = "errorcode") val errorCode: String? = null,
    @Json(name = "data") val data: List<List<Any>>? = null
)

@JsonClass(generateAdapter = true)
data class SmartApiQuoteRequest(
    @Json(name = "mode") val mode: String = "FULL",
    @Json(name = "exchangeTokens") val exchangeTokens: Map<String, List<String>>
)

@JsonClass(generateAdapter = true)
data class SmartApiQuoteResponse(
    @Json(name = "status") val status: Boolean = false,
    @Json(name = "message") val message: String = "",
    @Json(name = "errorcode") val errorCode: String? = null,
    @Json(name = "data") val data: SmartApiQuoteData? = null
)

@JsonClass(generateAdapter = true)
data class SmartApiQuoteData(
    @Json(name = "fetched") val fetched: List<SmartApiQuoteItem>? = null
)

@JsonClass(generateAdapter = true)
data class SmartApiQuoteItem(
    @Json(name = "exchange") val exchange: String = "NSE",
    @Json(name = "symbolToken") val symbolToken: String = "",
    @Json(name = "tradingSymbol") val tradingSymbol: String = "",
    @Json(name = "ltp") val ltp: Double = 0.0,
    @Json(name = "open") val open: Double = 0.0,
    @Json(name = "high") val high: Double = 0.0,
    @Json(name = "low") val low: Double = 0.0,
    @Json(name = "close") val close: Double = 0.0,
    @Json(name = "tradeVolume") val tradeVolume: Long = 0
)

@JsonClass(generateAdapter = true)
data class SmartApiOrderRequest(
    @Json(name = "variety") val variety: String = "NORMAL",
    @Json(name = "tradingsymbol") val tradingSymbol: String,
    @Json(name = "symboltoken") val symbolToken: String,
    @Json(name = "transactiontype") val transactionType: String, // BUY or SELL
    @Json(name = "exchange") val exchange: String = "NSE",
    @Json(name = "ordertype") val orderType: String = "MARKET",
    @Json(name = "producttype") val productType: String = "DELIVERY",
    @Json(name = "duration") val duration: String = "DAY",
    @Json(name = "price") val price: String = "0",
    @Json(name = "squareoff") val squareOff: String = "0",
    @Json(name = "stoploss") val stopLoss: String = "0",
    @Json(name = "quantity") val quantity: String
)

@JsonClass(generateAdapter = true)
data class SmartApiOrderResponse(
    @Json(name = "status") val status: Boolean = false,
    @Json(name = "message") val message: String = "",
    @Json(name = "errorcode") val errorCode: String? = null,
    @Json(name = "data") val data: SmartApiOrderIdData? = null
)

@JsonClass(generateAdapter = true)
data class SmartApiOrderIdData(
    @Json(name = "orderid") val orderId: String = ""
)

/**
 * Retrofit Interface for Angel One SmartConnect Endpoints.
 */
interface AngelOneSmartApiService {

    @Headers(
        "Content-Type: application/json",
        "Accept: application/json",
        "X-UserType: USER",
        "X-SourceID: WEB",
        "X-ClientLocalIP: 127.0.0.1",
        "X-ClientPublicIP: 106.193.147.98",
        "X-MACAddress: fe80::216:3eff:fe12:3456"
    )
    @POST("rest/auth/partner/v1/generate-session")
    suspend fun generateSession(
        @Header("X-PrivateKey") apiKey: String,
        @Body request: SmartApiLoginRequest
    ): Response<SmartApiLoginResponse>

    @Headers(
        "Content-Type: application/json",
        "Accept: application/json",
        "X-UserType: USER",
        "X-SourceID: WEB"
    )
    @POST("rest/secure/angelbroking/historical/v1/getCandleData")
    suspend fun getCandleData(
        @Header("Authorization") authorization: String,
        @Header("X-PrivateKey") apiKey: String,
        @Body request: SmartApiCandleRequest
    ): Response<SmartApiCandleResponse>

    @Headers(
        "Content-Type: application/json",
        "Accept: application/json",
        "X-UserType: USER",
        "X-SourceID: WEB"
    )
    @POST("rest/secure/angelbroking/market/v1/quote")
    suspend fun getQuote(
        @Header("Authorization") authorization: String,
        @Header("X-PrivateKey") apiKey: String,
        @Body request: SmartApiQuoteRequest
    ): Response<SmartApiQuoteResponse>

    @Headers(
        "Content-Type: application/json",
        "Accept: application/json",
        "X-UserType: USER",
        "X-SourceID: WEB"
    )
    @POST("rest/secure/angelbroking/order/v1/placeOrder")
    suspend fun placeOrder(
        @Header("Authorization") authorization: String,
        @Header("X-PrivateKey") apiKey: String,
        @Body request: SmartApiOrderRequest
    ): Response<SmartApiOrderResponse>
}

/**
 * Singleton client factory and connection manager for Angel One SmartAPI.
 */
object AngelOneApiClient {
    private const val BASE_URL = "https://apiconnect.angelbroking.com/"

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    val service: AngelOneSmartApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(AngelOneSmartApiService::class.java)
    }
}
