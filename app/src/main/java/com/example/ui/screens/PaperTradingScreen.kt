package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AppDatabase
import com.example.data.local.HoldingEntity
import com.example.data.remote.LiveTradingGateway
import com.example.data.remote.MarketRepository
import com.example.data.remote.StockInstrument
import com.example.data.repository.AuthRepository
import com.example.data.repository.TradingRepository
import com.example.ui.components.CleanCard
import com.example.ui.components.PulsingDot
import com.example.ui.theme.*
import kotlinx.coroutines.launch

enum class TradingExecutionMode {
    PAPER_TRADING,
    LIVE_TRADING
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperTradingScreen(
    database: AppDatabase,
    symbol: String,
    initialAction: String = "BUY",
    onNavigateBack: () -> Unit,
    onNavigateToPortfolio: () -> Unit
) {
    val user by AuthRepository.currentUser.collectAsState()
    var selectedSymbol by remember { mutableStateOf(symbol) }
    var stock by remember { mutableStateOf<StockInstrument?>(null) }
    var paperHolding by remember { mutableStateOf<HoldingEntity?>(null) }
    var executionMode by remember { mutableStateOf(TradingExecutionMode.PAPER_TRADING) }
    var action by remember { mutableStateOf(initialAction) } // "BUY" or "SELL"
    var exchange by remember { mutableStateOf("NSE") } // "NSE" or "BSE"
    var quantityText by remember { mutableStateOf("10") }
    var orderType by remember { mutableStateOf("MARKET") } // "MARKET" or "LIMIT"
    var limitPriceText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successDialog by remember { mutableStateOf(false) }
    var executedDetails by remember { mutableStateOf("") }
    var isExecuting by remember { mutableStateOf(false) }

    // Live Trading state from LiveTradingGateway
    val isLiveAuth by LiveTradingGateway.isAngelOneAuthenticated.collectAsState()
    val isExpired by LiveTradingGateway.isSessionExpired.collectAsState()
    val liveClientCode by LiveTradingGateway.liveClientCode.collectAsState()
    val liveBalance by LiveTradingGateway.liveAccountBalance.collectAsState()
    val liveHoldings by LiveTradingGateway.liveHoldings.collectAsState()
    val liveOrders by LiveTradingGateway.liveOrders.collectAsState()

    // Dialogs
    var showLiveAuthDialog by remember { mutableStateOf(false) }
    var showOrderReviewDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    // Load stock details and paper holding when symbol changes
    LaunchedEffect(selectedSymbol) {
        stock = MarketRepository.getStockDetails(selectedSymbol)
        stock?.let { s ->
            limitPriceText = String.format("%.2f", s.currentPrice)
        }
        user?.let {
            paperHolding = database.tradingDao().getHolding(it.id, selectedSymbol)
        }
    }

    val currentStock = stock ?: StockInstrument("0", selectedSymbol, selectedSymbol, exchange, 0.05, 1, 1000.0, 0.0, 0.0, 1000.0, 1010.0, 990.0, 1000.0, 100000)
    val quantity = quantityText.toIntOrNull() ?: 0
    val executionPrice = if (orderType == "LIMIT") (limitPriceText.toDoubleOrNull() ?: currentStock.currentPrice) else currentStock.currentPrice
    val totalRequired = quantity * executionPrice
    val virtualPaperBalance = user?.virtualBalance ?: 1000000.0
    val paperOwnedQty = paperHolding?.quantity ?: 0

    // Live holding for selected symbol (isolated from paper holdings)
    val liveHoldingForSymbol = liveHoldings.find { it.symbol.equals(selectedSymbol, ignoreCase = true) }
    val liveOwnedQty = liveHoldingForSymbol?.quantity ?: 0

    // Visually distinct container colors for Paper vs Live mode
    val isLiveMode = executionMode == TradingExecutionMode.LIVE_TRADING
    val scaffoldBgColor by animateColorAsState(
        targetValue = if (isLiveMode) Color(0xFFFFF7F7) else BackgroundWarm,
        label = "ScaffoldBackground"
    )
    val topBarBgColor by animateColorAsState(
        targetValue = if (isLiveMode) Color(0xFF1E1E2D) else BackgroundWarm,
        label = "TopBarBackground"
    )
    val topBarTextColor by animateColorAsState(
        targetValue = if (isLiveMode) Color.White else TextPrimary,
        label = "TopBarText"
    )

    Scaffold(
        containerColor = scaffoldBgColor,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isLiveMode) {
                                PulsingDot(color = LossRed)
                                Text(
                                    text = "LIVE TRADING EXECUTION",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp,
                                    color = Color(0xFFFF5252)
                                )
                            } else {
                                Text(
                                    text = "Paper Trading Simulator",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = TextPrimary
                                )
                            }
                        }
                        Text(
                            text = if (isLiveMode)
                                "Angel One SmartAPI • Real Funds • Zero AI Automation"
                            else
                                "${currentStock.symbol} • ${currentStock.exchange} • ₹10,00,000 Virtual Fund",
                            fontSize = 11.sp,
                            color = if (isLiveMode) Color(0xFFB0B0C0) else TextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (isLiveMode) Color.White else TextPrimary
                        )
                    }
                },
                actions = {
                    if (isLiveMode && isLiveAuth) {
                        IconButton(onClick = { LiveTradingGateway.disconnectSmartApi() }) {
                            Icon(Icons.Default.PowerSettingsNew, contentDescription = "Disconnect Session", tint = LossRed)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = topBarBgColor)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // =========================================================================
            // 1. CLEAR MODE SELECTOR: [ PAPER TRADING ] [ LIVE TRADING ]
            // =========================================================================
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceWhite,
                border = BorderStroke(2.dp, if (isLiveMode) LossRed.copy(alpha = 0.5f) else OutlineBorder),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth().testTag("mode_selector_container")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // PAPER TRADING TAB
                    Surface(
                        onClick = {
                            executionMode = TradingExecutionMode.PAPER_TRADING
                            errorMessage = null
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (!isLiveMode) PrimaryPurple else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("tab_mode_paper_trading")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text("🛡️", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "PAPER TRADING",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (!isLiveMode) Color.White else TextSecondary
                            )
                        }
                    }

                    // LIVE TRADING TAB
                    Surface(
                        onClick = {
                            executionMode = TradingExecutionMode.LIVE_TRADING
                            errorMessage = null
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isLiveMode) Color(0xFFDC2626) else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("tab_mode_live_trading")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text("⚡", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "LIVE TRADING",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isLiveMode) Color.White else TextSecondary
                            )
                        }
                    }
                }
            }

            // =========================================================================
            // 2. VISUALLY OBVIOUS MODE BANNER
            // =========================================================================
            if (!isLiveMode) {
                // PAPER TRADING BANNER
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = PrimaryContainer.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, OutlineBorder),
                    modifier = Modifier.fillMaxWidth().testTag("banner_paper_trading")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("🛡️", fontSize = 22.sp)
                        Column {
                            Text(
                                text = "PAPER TRADING MODE (SIMULATED)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = OnPrimaryContainer
                            )
                            Text(
                                text = "Virtual starting balance: ₹10,00,000. Orders are strictly simulated and never sent to Angel One. Zero risk.",
                                fontSize = 11.sp,
                                color = TextPrimary,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            } else {
                // LIVE TRADING HIGH-VIGILANCE BANNER
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFFEF2F2),
                    border = BorderStroke(1.5.dp, Color(0xFFDC2626)),
                    modifier = Modifier.fillMaxWidth().testTag("banner_live_trading")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Live Warning",
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "LIVE TRADING — REAL CAPITAL AT RISK",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFFDC2626)
                                )
                                PulsingDot(color = Color(0xFFDC2626))
                            }
                            Text(
                                text = "Real money execution via Angel One SmartAPI. AI & LSTM models are prohibited from placing live orders. Two-step explicit review required.",
                                fontSize = 11.sp,
                                color = Color(0xFF7F1D1D),
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            // =========================================================================
            // 3. LIVE TRADING: ANGEL ONE AUTHENTICATION & LIVE ACCOUNT GATE
            // =========================================================================
            if (isLiveMode && !isLiveAuth) {
                CleanCard(modifier = Modifier.fillMaxWidth().testTag("live_auth_status_card")) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.AccountBalance, contentDescription = null, tint = PrimaryPurple)
                            Text("LIVE TRADING BROKER CONNECTION", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (isExpired) Color(0xFFFEF3C7) else Color(0xFFFEE2E2)
                        ) {
                            Text(
                                text = if (isExpired) "SESSION EXPIRED" else "NOT CONNECTED",
                                color = if (isExpired) Color(0xFFB45309) else Color(0xFFDC2626),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (isExpired)
                            "Angel One session expired. Please reconnect your account."
                        else
                            "Connect your Angel One account to enable Live Trading.",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isExpired) Color(0xFFB45309) else TextPrimary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Connect your Angel One account to continue. Live funds, Demat holdings, and real order routing are strictly restricted to authenticated broker sessions. Virtual paper trading (₹10,00,000) remains completely separate.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { showLiveAuthDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("connect_broker_button")
                            .testTag("authenticate_angel_one_button")
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isExpired) "Reconnect Angel One" else "Connect Angel One",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                // Safety Mandates Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, OutlineBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🛡️ Live Trading Safety & Isolation Rules", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("• Zero automatic live orders: AI Copilot & LSTM predictions cannot place live trades.", fontSize = 11.sp, color = TextSecondary)
                        Text("• Zero fake balances: Live funds & holdings are only shown from authenticated Angel One account.", fontSize = 11.sp, color = TextSecondary)
                        Text("• Mandatory 9-point order confirmation dialog with explicit user authorization required.", fontSize = 11.sp, color = TextSecondary)
                        Text("• Complete isolation: ₹10,00,000 Paper Trading virtual capital is never affected.", fontSize = 11.sp, color = TextSecondary)
                    }
                }
            } else if (isLiveMode && isLiveAuth) {
                CleanCard(modifier = Modifier.fillMaxWidth().testTag("live_auth_status_card")) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PulsingDot(color = GainGreen)
                            Text("Angel One Connected ✓", fontSize = 13.sp, fontWeight = FontWeight.Black, color = GainGreen)
                        }
                        TextButton(onClick = { LiveTradingGateway.disconnectBroker() }) {
                            Text("Disconnect", color = LossRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    StatRow("Connected Client ID", liveClientCode ?: "Connected")
                    StatRow("Available Live Balance", "₹${String.format("%,.2f", liveBalance)}")
                    StatRow("Live Demat Holdings (${currentStock.symbol})", "$liveOwnedQty shares")
                    StatRow("Live Active Orders", "${liveOrders.size} placed")
                    StatRow("Virtual Fund Isolation", "₹10,00,000 Unaffected")
                }
            }

            // =========================================================================
            // 4. INSTRUMENT SELECTION & ORDER FLOW (ONLY WHEN PAPER OR AUTHENTICATED LIVE)
            // =========================================================================
            if (!isLiveMode || isLiveAuth) {
            CleanCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (isLiveMode) "SELECT LIVE INSTRUMENT (ANGEL ONE)" else "SELECT STOCK INSTRUMENT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Instrument Switcher Row
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(listOf("RELIANCE", "HDFCBANK", "INFY", "TCS", "TATAMOTORS", "SBIN", "ICICIBANK")) { sym ->
                        FilterChip(
                            selected = selectedSymbol == sym,
                            onClick = {
                                selectedSymbol = sym
                                errorMessage = null
                            },
                            label = { Text(sym, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(currentStock.name, fontSize = 12.sp, color = TextSecondary)
                        Text(
                            text = "₹${String.format("%,.2f", currentStock.currentPrice)}",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        val isUp = currentStock.changePercentage >= 0
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (isUp) GainGreenLight else LossRedLight
                        ) {
                            Text(
                                text = "${if (isUp) "+" else ""}${String.format("%.2f", currentStock.changePercentage)}%",
                                color = if (isUp) GainGreen else LossRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isLiveMode) "Token: ${currentStock.token}" else "Lot: ${currentStock.lotSize}",
                            fontSize = 10.sp,
                            color = TextTertiary
                        )
                    }
                }
            }

            // =========================================================================
            // 5. ORDER CONFIGURATION (BUY/SELL, EXCHANGE, ORDER TYPE, QUANTITY)
            // =========================================================================
            // BUY / SELL Action Mode Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { action = "BUY"; errorMessage = null },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (action == "BUY") GainGreen else Color.White,
                        contentColor = if (action == "BUY") Color.White else TextPrimary
                    ),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, if (action == "BUY") GainGreen else OutlineBorder),
                    modifier = Modifier.weight(1f).height(46.dp).testTag("action_buy_toggle")
                ) {
                    Text(if (isLiveMode) "LIVE BUY" else "PAPER BUY", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { action = "SELL"; errorMessage = null },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (action == "SELL") LossRed else Color.White,
                        contentColor = if (action == "SELL") Color.White else TextPrimary
                    ),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, if (action == "SELL") LossRed else OutlineBorder),
                    modifier = Modifier.weight(1f).height(46.dp).testTag("action_sell_toggle")
                ) {
                    Text(if (isLiveMode) "LIVE SELL" else "PAPER SELL", fontWeight = FontWeight.Bold)
                }
            }

            // Exchange & Order Type Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Exchange Picker
                CleanCard(modifier = Modifier.weight(1f)) {
                    Text("EXCHANGE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("NSE", "BSE").forEach { ex ->
                            FilterChip(
                                selected = exchange == ex,
                                onClick = { exchange = ex },
                                label = { Text(ex, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                        }
                    }
                }

                // Order Type Picker
                CleanCard(modifier = Modifier.weight(1f)) {
                    Text("ORDER TYPE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("MARKET", "LIMIT").forEach { ot ->
                            FilterChip(
                                selected = orderType == ot,
                                onClick = { orderType = ot },
                                label = { Text(ot, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                        }
                    }
                }
            }

            // Quantity & Limit Price Inputs
            CleanCard(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = {
                        quantityText = it.filter { ch -> ch.isDigit() }
                        errorMessage = null
                    },
                    label = { Text("Quantity (Shares)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("trade_quantity_input")
                )

                // Quick Quantity Shortcuts
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(5, 10, 25, 50, 100).forEach { qty ->
                        OutlinedButton(
                            onClick = { quantityText = qty.toString(); errorMessage = null },
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("+$qty", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // If LIMIT order, show limit price input
                AnimatedVisibility(visible = orderType == "LIMIT") {
                    Column {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = limitPriceText,
                            onValueChange = { limitPriceText = it },
                            label = { Text("Limit Price (₹)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().testTag("limit_price_input")
                        )
                    }
                }

                // Holdings Ownership Notice
                Spacer(modifier = Modifier.height(8.dp))
                if (action == "SELL") {
                    val owned = if (isLiveMode) liveOwnedQty else paperOwnedQty
                    val accountType = if (isLiveMode) "Angel One Demat" else "Virtual Portfolio"
                    Text(
                        text = "Available to Sell: $owned shares ($accountType)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (owned > 0) PrimaryPurple else LossRed
                    )
                }
            }

            // =========================================================================
            // 6. BALANCE & MARGIN ISOLATION BREAKDOWN
            // =========================================================================
            CleanCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (isLiveMode) "LIVE TRADING MARGIN BREAKDOWN" else "VIRTUAL MARGIN & ALLOCATION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = if (isLiveMode) LossRed else TextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))

                val availableFunds = if (isLiveMode) liveBalance else virtualPaperBalance
                StatRow("Available Funds", "₹${String.format("%,.2f", availableFunds)}")
                StatRow("Execution Price", "₹${String.format("%,.2f", executionPrice)} (${orderType})")
                StatRow("Estimated Order Value", "₹${String.format("%,.2f", totalRequired)}")
                val projectedRemaining = if (action == "BUY") availableFunds - totalRequired else availableFunds + totalRequired
                StatRow("Projected Balance After Order", "₹${String.format("%,.2f", projectedRemaining.coerceAtLeast(0.0))}")
                StatRow(
                    label = "Brokerage & Exchange Schedule",
                    value = if (isLiveMode) "Standard Angel One (₹20 or 0.03%)" else "₹0.00 (Paper Simulation)"
                )
                StatRow(
                    label = "Account Mode Isolation",
                    value = if (isLiveMode) "Isolated from ₹10L Paper Fund" else "Offline Room Persistence"
                )
            }

            // Error display
            if (errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = LossRedLight,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = LossRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // =========================================================================
            // 7. PRIMARY SUBMIT / REVIEW BUTTON
            // =========================================================================
            Button(
                onClick = {
                    if (quantity <= 0) {
                        errorMessage = "Please enter a valid quantity (> 0)"
                        return@Button
                    }
                    if (action == "SELL") {
                        val available = if (isLiveMode) liveOwnedQty else paperOwnedQty
                        if (quantity > available) {
                            errorMessage = "Cannot sell $quantity shares. You only hold $available shares in this mode."
                            return@Button
                        }
                    }

                    if (isLiveMode) {
                        // LIVE TRADING: Must show explicit Order-Review Screen before any API call!
                        if (!isLiveAuth) {
                            errorMessage = "Angel One session is unauthenticated. Please authenticate before reviewing live orders."
                            showLiveAuthDialog = true
                            return@Button
                        }
                        if (action == "BUY" && totalRequired > liveBalance) {
                            errorMessage = "Insufficient live funds. Required: ₹${String.format("%,.2f", totalRequired)}, Available: ₹${String.format("%,.2f", liveBalance)}"
                            return@Button
                        }
                        // Open explicit order review screen
                        showOrderReviewDialog = true
                    } else {
                        // PAPER TRADING: Execute simulated trade locally
                        if (action == "BUY" && totalRequired > virtualPaperBalance) {
                            errorMessage = "Insufficient virtual funds for this paper trade."
                            return@Button
                        }

                        coroutineScope.launch {
                            isExecuting = true
                            errorMessage = null
                            val uId = user?.id ?: return@launch

                            val res = if (action == "BUY") {
                                TradingRepository.executeBuyOrder(database, uId, currentStock.symbol, currentStock.name, quantity, executionPrice)
                            } else {
                                TradingRepository.executeSellOrder(database, uId, currentStock.symbol, quantity, executionPrice)
                            }

                            isExecuting = false
                            res.onSuccess { tx ->
                                executedDetails = "${tx.type} ${tx.quantity} ${tx.symbol} @ ₹${String.format("%.2f", tx.price)}"
                                successDialog = true
                            }.onFailure { err ->
                                errorMessage = err.message ?: "Execution failed"
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLiveMode) Color(0xFFDC2626) else (if (action == "BUY") GainGreen else LossRed)
                ),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("execute_order_button"),
                enabled = !isExecuting
            ) {
                if (isExecuting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text(
                        text = if (isLiveMode)
                            "Review Live $action Order ⚡"
                        else
                            "Execute Paper $action Order 🛡️",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            } // Close if (!isLiveMode || isLiveAuth)
        }
    }

    // =========================================================================
    // 8. DEDICATED LIVE ORDER REVIEW SCREEN (MODAL)
    // =========================================================================
    if (showOrderReviewDialog) {
        var hasAgreedToRisk by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showOrderReviewDialog = false },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFDC2626),
                    modifier = Modifier.size(40.dp)
                )
            },
            title = {
                Text(
                    text = "LIVE ORDER REVIEW",
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFDC2626)
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Please verify all order parameters before live broker submission:",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    HorizontalDivider(color = OutlineBorder)

                    // Required Fields Display
                    ReviewItemRow("Mode", "LIVE TRADING (Real Brokerage)")
                    ReviewItemRow("Symbol", currentStock.symbol)
                    ReviewItemRow("Exchange", exchange)
                    ReviewItemRow("Side", action)
                    ReviewItemRow("Quantity", "$quantity shares")
                    ReviewItemRow("Order Type", orderType)
                    ReviewItemRow("Execution Price", "₹${String.format("%,.2f", executionPrice)}")
                    ReviewItemRow("Estimated Value", "₹${String.format("%,.2f", totalRequired)}")
                    ReviewItemRow("Available Live Funds", "₹${String.format("%,.2f", liveBalance)}")

                    HorizontalDivider(color = OutlineBorder)

                    // Mandatory Risk Warning
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFEF2F2),
                        border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "⚠️ MANDATORY RISK WARNING",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFDC2626)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Real money will be deducted from your Angel One account. Orders are irreversible once filled. Orders are NEVER placed automatically by AI Copilot or LSTM models. You must explicitly authorize this submission.",
                                fontSize = 10.sp,
                                color = Color(0xFF7F1D1D),
                                lineHeight = 14.sp
                            )
                        }
                    }

                    // Explicit user acknowledgment checkbox
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { hasAgreedToRisk = !hasAgreedToRisk }
                    ) {
                        Checkbox(
                            checked = hasAgreedToRisk,
                            onCheckedChange = { hasAgreedToRisk = it },
                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFFDC2626))
                        )
                        Text(
                            text = "I explicitly confirm and authorize this live order.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showOrderReviewDialog = false
                        coroutineScope.launch {
                            isExecuting = true
                            errorMessage = null
                            val result = LiveTradingGateway.submitLiveOrder(
                                symbol = currentStock.symbol,
                                exchange = exchange,
                                action = action,
                                quantity = quantity,
                                orderType = orderType,
                                price = executionPrice,
                                userExplicitlyConfirmedInUI = true
                            )
                            isExecuting = false
                            result.onSuccess { orderId ->
                                executedDetails = "Live Order Dispatched: $orderId ($action $quantity ${currentStock.symbol})"
                                successDialog = true
                            }.onFailure { err ->
                                errorMessage = err.message ?: "Live trading rejected"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    enabled = hasAgreedToRisk,
                    modifier = Modifier.testTag("confirm_live_order_button")
                ) {
                    Text("Submit Live Order 🚀", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showOrderReviewDialog = false }) {
                    Text("Cancel Review")
                }
            }
        )
    }

    // =========================================================================
    // 9. ANGEL ONE INLINE AUTHENTICATION DIALOG
    // =========================================================================
    if (showLiveAuthDialog) {
        var clientCodeInput by remember { mutableStateOf("") }
        var mpinInput by remember { mutableStateOf("") }
        var totpInput by remember { mutableStateOf("") }
        var apiKeyInput by remember { mutableStateOf("") }
        var authError by remember { mutableStateOf<String?>(null) }
        var isAuthenticating by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showLiveAuthDialog = false },
            icon = { Icon(Icons.Default.Key, contentDescription = null, tint = PrimaryPurple, modifier = Modifier.size(36.dp)) },
            title = { Text("Authenticate Angel One SmartAPI", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Enter your Angel One SmartAPI session credentials. Broker login is isolated from TRADEPro login. Secrets are never exposed in Logcat.",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )

                    OutlinedTextField(
                        value = clientCodeInput,
                        onValueChange = { clientCodeInput = it },
                        label = { Text("Client Code (e.g. S681943 or DEMO_S681943)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("auth_client_code_input")
                    )

                    OutlinedTextField(
                        value = mpinInput,
                        onValueChange = { mpinInput = it },
                        label = { Text("MPIN / Password") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("auth_mpin_input")
                    )

                    OutlinedTextField(
                        value = totpInput,
                        onValueChange = { totpInput = it },
                        label = { Text("TOTP (6-digit Authenticator)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("auth_totp_input")
                    )

                    OutlinedButton(
                        onClick = {
                            clientCodeInput = "DEMO_S681943"
                            mpinInput = "1234"
                            totpInput = "654321"
                            authError = null
                        },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🧪 Quick Fill Demo Broker Credentials", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    if (authError != null) {
                        Text(authError ?: "", color = LossRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isAuthenticating = true
                            authError = null
                            val res = LiveTradingGateway.connectBroker(clientCodeInput, mpinInput, totpInput, apiKeyInput)
                            isAuthenticating = false
                            res.onSuccess {
                                showLiveAuthDialog = false
                            }.onFailure { err ->
                                authError = err.message ?: "Authentication failed"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                    enabled = !isAuthenticating
                ) {
                    if (isAuthenticating) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                    } else {
                        Text("Connect Session")
                    }
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showLiveAuthDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // =========================================================================
    // 10. SUCCESS EXECUTION DIALOG
    // =========================================================================
    if (successDialog) {
        AlertDialog(
            onDismissRequest = {
                successDialog = false
                onNavigateToPortfolio()
            },
            icon = {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = GainGreen,
                    modifier = Modifier.size(44.dp)
                )
            },
            title = {
                Text(
                    text = if (isLiveMode) "Live Order Dispatched" else "Paper Order Executed",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    if (isLiveMode)
                        "Your live order has been submitted to Angel One SmartAPI:\n\n$executedDetails\n\nLive order record is logged."
                    else
                        "Your paper trading order has been filled at market price:\n\n$executedDetails\n\nVirtual portfolio valuation, holdings, and trade history have been updated in Room database."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        successDialog = false
                        onNavigateToPortfolio()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                ) {
                    Text("View Portfolio")
                }
            }
        )
    }
}

@Composable
private fun ReviewItemRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 11.sp, color = TextSecondary)
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    }
}
