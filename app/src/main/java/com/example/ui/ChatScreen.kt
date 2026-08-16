package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.AvailableModels
import com.example.data.model.PresetPrompts
import com.example.data.model.STARTER_PROMPTS
import com.example.ui.components.ChatDrawerContent
import com.example.ui.components.ChatInputArea
import com.example.ui.components.MessageItem
import com.example.ui.components.ParametersSheet
import com.example.ui.components.SubscriptionModal
import com.example.ui.components.ThinkingIndicator
import com.example.ui.image.ImageStudioScreen
import com.example.ui.theme.GeminiBlue
import com.example.ui.theme.GeminiCyan
import com.example.ui.theme.GeminiGradient
import com.example.ui.theme.IndAiGradient
import com.example.ui.theme.IndGreen
import com.example.ui.theme.IndSaffron
import com.example.ui.theme.ProGoldGradient
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sessions by viewModel.allSessions.collectAsStateWithLifecycle()
    val messages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val speakingMessageId by viewModel.speakingMessageId.collectAsStateWithLifecycle()
    val subscriptionStatus by viewModel.subscriptionStatus.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var showParametersSheet by remember { mutableStateOf(false) }
    var showPresetsSheet by remember { mutableStateOf(false) }

    // Scroll to bottom when new messages arrive or when thinking
    LaunchedEffect(messages.size, uiState.isGenerating) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size)
        }
    }

    if (uiState.showSubscriptionModal) {
        SubscriptionModal(
            subscriptionStatus = subscriptionStatus,
            onUpgrade = { planId -> viewModel.upgradeToPro(planId) },
            onDowngrade = { viewModel.downgradeToFree() },
            onDismiss = { viewModel.hideSubscriptionModal() }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                ChatDrawerContent(
                    sessions = sessions,
                    activeSessionId = uiState.activeSessionId,
                    onSelectSession = { id ->
                        viewModel.selectSession(id)
                    },
                    onNewChat = { viewModel.startNewChat() },
                    onDeleteSession = { id -> viewModel.deleteSession(id) },
                    onRenameSession = { id, title -> viewModel.renameSession(id, title) },
                    onClearAllSessions = { viewModel.clearAllSessions() },
                    onCloseDrawer = { scope.launch { drawerState.close() } }
                )
            }
        }
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize().imePadding(),
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        // IND AI Brand & Model Selector
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                                .clickable { showParametersSheet = true }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(IndAiGradient),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(11.dp)
                                )
                            }

                            val modelInfo = AvailableModels.ALL.firstOrNull { it.id == uiState.activeModelId }
                                ?: AvailableModels.DEFAULT

                            Text(
                                text = "IND AI • ${modelInfo.displayName.replace("Gemini ", "")}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = "▼",
                                fontSize = 8.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier.testTag("drawer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open Menu",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        // Pro Subscription Badge / Upgrade Button
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (subscriptionStatus.isPro) IndGreen.copy(alpha = 0.15f) else IndSaffron.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (subscriptionStatus.isPro) IndGreen else IndSaffron
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { viewModel.showSubscriptionModal() }
                                .testTag("pro_badge_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (subscriptionStatus.isPro) Icons.Default.WorkspacePremium else Icons.Default.FlashOn,
                                    contentDescription = null,
                                    tint = if (subscriptionStatus.isPro) IndGreen else IndSaffron,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (subscriptionStatus.isPro) "PRO" else "₹200/mo",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (subscriptionStatus.isPro) IndGreen else IndSaffron
                                )
                            }
                        }

                        // Tune Parameters Button
                        IconButton(
                            onClick = { showParametersSheet = true },
                            modifier = Modifier.testTag("tune_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Tuning Parameters",
                                tint = GeminiBlue
                            )
                        }

                        // New Chat Button
                        IconButton(
                            onClick = { viewModel.startNewChat() },
                            modifier = Modifier.testTag("new_chat_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New Chat",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                Column {
                    // Chat Input (only shown when on Chat tab)
                    if (uiState.currentAppTab == 0) {
                        ChatInputArea(
                            inputText = uiState.inputText,
                            onInputTextChange = { viewModel.onInputTextChange(it) },
                            selectedImageBase64 = uiState.selectedImageBase64,
                            onImageSelected = { viewModel.onImageSelected(it) },
                            onClearSelectedImage = { viewModel.clearSelectedImage() },
                            isGenerating = uiState.isGenerating,
                            onSendMessage = { viewModel.sendMessage(it) },
                            onStopGenerating = { viewModel.stopGenerating() },
                            systemPrompt = uiState.systemPrompt,
                            onOpenPresets = { showPresetsSheet = true }
                        )
                    }

                    // Main App Navigation Bar (Chat vs Image Studio)
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp
                    ) {
                        NavigationBarItem(
                            selected = uiState.currentAppTab == 0,
                            onClick = { viewModel.setAppTab(0) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.ChatBubble,
                                    contentDescription = "AI Chat"
                                )
                            },
                            label = {
                                Text("IND AI Chat", fontWeight = if (uiState.currentAppTab == 0) FontWeight.Bold else FontWeight.Normal)
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = IndSaffron,
                                selectedTextColor = IndSaffron,
                                indicatorColor = IndSaffron.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag("nav_chat_tab")
                        )

                        NavigationBarItem(
                            selected = uiState.currentAppTab == 1,
                            onClick = { viewModel.setAppTab(1) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Image Studio"
                                )
                            },
                            label = {
                                Text("Image Studio", fontWeight = if (uiState.currentAppTab == 1) FontWeight.Bold else FontWeight.Normal)
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = IndSaffron,
                                selectedTextColor = IndSaffron,
                                indicatorColor = IndSaffron.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag("nav_image_tab")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (uiState.currentAppTab == 1) {
                    // Image Creation Studio Tab
                    ImageStudioScreen(
                        onOpenSubscription = { viewModel.showSubscriptionModal() }
                    )
                } else {
                    // Chat Tab
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Error Alert Banner (if any)
                        AnimatedVisibility(
                            visible = uiState.errorBanner != null,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            if (uiState.errorBanner != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.errorContainer)
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = uiState.errorBanner ?: "",
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { viewModel.dismissErrorBanner() },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Dismiss error",
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Chat Messages or Empty Starter Screen
                        if (messages.isEmpty()) {
                            EmptyChatStarter(
                                onSelectStarterPrompt = { prompt -> viewModel.sendMessage(prompt) },
                                onSwitchToImageStudio = { viewModel.setAppTab(1) },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                items(messages, key = { it.messageId }) { message ->
                                    MessageItem(
                                        message = message,
                                        isSpeaking = speakingMessageId == message.messageId,
                                        onSpeak = { viewModel.speakMessage(message) },
                                        onRegenerate = { viewModel.regenerateLastResponse() },
                                        onFeedback = { isLiked -> viewModel.updateMessageFeedback(message.messageId, isLiked) },
                                        onEditPrompt = { prompt -> viewModel.onInputTextChange(prompt) }
                                    )
                                }

                                if (uiState.isGenerating) {
                                    item {
                                        ThinkingIndicator()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Parameters Modal Sheet
    if (showParametersSheet) {
        ParametersSheet(
            currentModelId = uiState.activeModelId,
            currentTemperature = uiState.temperature,
            currentTopP = uiState.topP,
            currentSystemPrompt = uiState.systemPrompt,
            onApplyParameters = { modelId, temp, topP, systemPrompt ->
                viewModel.applyParameters(modelId, temp, topP, systemPrompt)
            },
            onDismiss = { showParametersSheet = false }
        )
    }

    // Prompt Presets Modal Sheet
    if (showPresetsSheet) {
        val presetSheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showPresetsSheet = false },
            sheetState = presetSheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = GeminiCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "System Persona Presets",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "Select a persona to guide IND AI's tone, formatting, and expertise level:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                PresetPrompts.PRESETS.forEach { preset ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.applyParameters(
                                    modelId = uiState.activeModelId,
                                    temperature = uiState.temperature,
                                    topP = uiState.topP,
                                    systemPrompt = preset.prompt
                                )
                                showPresetsSheet = false
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (uiState.systemPrompt == preset.prompt)
                                GeminiBlue.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = if (uiState.systemPrompt == preset.prompt)
                            androidx.compose.foundation.BorderStroke(1.dp, GeminiBlue)
                        else null,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = preset.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = preset.prompt,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmptyChatStarter(
    onSelectStarterPrompt: (String) -> Unit,
    onSwitchToImageStudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Glowing IND AI Brand Sparkle
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(IndAiGradient),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "IND AI",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Namaste! Welcome to IND AI 🇮🇳",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Advanced AI Chat • Gemini 2.5 Image Creator • Pro at ₹200/mo",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Image Studio Shortcut Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSwitchToImageStudio() },
            colors = CardDefaults.cardColors(
                containerColor = IndSaffron.copy(alpha = 0.12f)
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, IndSaffron.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(IndSaffron),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🎨 Gemini Image Creation Studio",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Generate photorealistic art, 3D Pixar, & anime scenes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "Try →",
                    fontWeight = FontWeight.Bold,
                    color = IndSaffron,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Starter Prompts Grid
        Text(
            text = "SUGGESTED PROMPTS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = IndSaffron,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            STARTER_PROMPTS.take(3).forEach { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectStarterPrompt(item.prompt) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (item.icon) {
                                    "science" -> Icons.Default.Science
                                    "code" -> Icons.Default.Code
                                    "palette" -> Icons.Default.Palette
                                    else -> Icons.Default.Hub
                                },
                                contentDescription = null,
                                tint = IndSaffron,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = item.prompt,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
