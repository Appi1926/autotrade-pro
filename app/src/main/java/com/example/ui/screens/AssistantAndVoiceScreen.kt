package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
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
import com.example.data.local.AppDatabase
import com.example.data.local.ChatMessageEntity
import com.example.data.repository.AssistantRepository
import com.example.data.repository.AuthRepository
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIAssistantScreen(
    database: AppDatabase,
    onNavigateBack: () -> Unit
) {
    val user by AuthRepository.currentUser.collectAsState()
    val userId = user?.id ?: ""
    val messages by AssistantRepository.observeMessages(database, userId).collectAsState(initial = emptyList())

    var inputQuery by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }
    var isListeningVoice by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val quickPrompts = listOf(
        "What is the sentiment for IT stocks today?",
        "Analyze HDFC Bank prediction & RSI",
        "How is my portfolio balance and health?",
        "What are the top AI Buy recommendations?",
        "Explain how the LSTM model works"
    )

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        containerColor = BackgroundWarm,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(PrimaryContainer)
                        ) {
                            Text("🤖", fontSize = 16.sp)
                        }
                        Column {
                            Text("AI Trading Copilot", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Autonomous Market Assistant", fontSize = 10.sp, color = TextSecondary)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                AssistantRepository.clearChat(database, userId)
                            }
                        }
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear Chat", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundWarm)
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .background(BackgroundWarm)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Quick Prompt Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    items(quickPrompts) { prompt ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = SurfaceWhite,
                            border = BorderStroke(1.dp, OutlineBorder),
                            modifier = Modifier.clip(RoundedCornerShape(50)).clickable {
                                inputQuery = prompt
                            }
                        ) {
                            Text(
                                text = prompt,
                                fontSize = 11.sp,
                                color = TextPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Input Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Voice Mic Button
                    IconButton(
                        onClick = {
                            isListeningVoice = !isListeningVoice
                            if (isListeningVoice) {
                                inputQuery = "What is the sentiment for IT stocks today?"
                            }
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (isListeningVoice) LossRedLight else PrimaryContainer)
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "Voice Input",
                            tint = if (isListeningVoice) LossRed else PrimaryPurple
                        )
                    }

                    OutlinedTextField(
                        value = inputQuery,
                        onValueChange = { inputQuery = it },
                        placeholder = { Text("Ask about stocks, LSTM predictions, or portfolio...") },
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = PrimaryPurple,
                            unfocusedBorderColor = OutlineBorder
                        ),
                        modifier = Modifier.weight(1f).testTag("assistant_input_field")
                    )

                    IconButton(
                        onClick = {
                            val q = inputQuery.trim()
                            if (q.isNotEmpty()) {
                                inputQuery = ""
                                isThinking = true
                                coroutineScope.launch {
                                    AssistantRepository.sendMessage(database, userId, q)
                                    isThinking = false
                                }
                            }
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(PrimaryPurple)
                            .testTag("assistant_send_button")
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("👋", fontSize = 32.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Welcome to Autonomous Copilot", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(
                            "I have full context of your portfolio, active Angel One live prices, and deep learning LSTM predictions. Ask me anything!",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            } else {
                items(messages) { msg ->
                    ChatBubbleItem(message = msg)
                }
            }

            if (isThinking) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = PrimaryPurple)
                        Text("Analyzing real-time market data & LSTM model...", fontSize = 11.sp, color = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubbleItem(message: ChatMessageEntity) {
    val isUser = message.isUser

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(PrimaryContainer)
                    .padding(2.dp)
            ) {
                Text("🤖", fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.width(6.dp))
        }

        Surface(
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (isUser) 20.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 20.dp
            ),
            color = if (isUser) PrimaryPurple else Color.White,
            border = if (!isUser) BorderStroke(1.dp, OutlineBorder) else null,
            tonalElevation = 1.dp,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = message.text,
                color = if (isUser) Color.White else TextPrimary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}
