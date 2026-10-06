package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.MarketRepository
import com.example.data.remote.StockInstrument
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockSearchScreen(
    onNavigateBack: () -> Unit,
    onSelectStock: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedSector by remember { mutableStateOf("All") }
    var stockList by remember { mutableStateOf<List<StockInstrument>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()

    val sectors = listOf("All", "Banking", "IT", "Energy", "Automobile", "Telecom", "FMCG", "Indices")

    LaunchedEffect(searchQuery, selectedSector) {
        coroutineScope.launch {
            val results = MarketRepository.searchStocks(searchQuery)
            stockList = if (selectedSector == "All") {
                results
            } else {
                results.filter { it.sector.equals(selectedSector, ignoreCase = true) }
            }
        }
    }

    Scaffold(
        containerColor = BackgroundWarm,
        topBar = {
            TopAppBar(
                title = { Text("Search Instruments", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundWarm)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search NSE, BSE, NIFTY, Tickers...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = PrimaryPurple) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = PrimaryPurple,
                    unfocusedBorderColor = OutlineBorder
                ),
                modifier = Modifier.fillMaxWidth().testTag("stock_search_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Sector Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(sectors) { sector ->
                    val isSelected = selectedSector == sector
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedSector = sector },
                        label = { Text(sector, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryContainer,
                            selectedLabelColor = OnPrimaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "MATCHING STOCKS (${stockList.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(stockList) { stock ->
                    LiveStockTickerRow(
                        stock = stock,
                        onClick = { onSelectStock(stock.symbol) }
                    )
                }
            }
        }
    }
}
