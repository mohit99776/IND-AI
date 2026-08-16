package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.db.ChatMessageEntity
import com.example.data.db.ChatSessionEntity
import com.example.data.model.AvailableModels
import com.example.data.model.UserSubscriptionStatus
import com.example.data.repository.ChatRepository
import com.example.data.repository.SubscriptionRepository
import com.example.util.TextToSpeechHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

data class ChatUiState(
    val activeSessionId: Long? = null,
    val activeModelId: String = AvailableModels.FLASH.id,
    val temperature: Float = 0.7f,
    val topP: Float = 0.95f,
    val systemPrompt: String = "",
    val isGenerating: Boolean = false,
    val inputText: String = "",
    val selectedImageBase64: String? = null,
    val errorBanner: String? = null,
    val showSubscriptionModal: Boolean = false,
    val currentAppTab: Int = 0 // 0: Chat, 1: Image Studio
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    val subscriptionRepo = SubscriptionRepository(database.subscriptionDao())
    private val repository = ChatRepository(
        chatDao = database.chatDao(),
        subscriptionRepository = subscriptionRepo
    )
    val ttsHelper = TextToSpeechHelper(application)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // Observe subscription status
    val subscriptionStatus: StateFlow<UserSubscriptionStatus> = subscriptionRepo.subscriptionStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSubscriptionStatus())

    // Observe all sessions
    val allSessions: StateFlow<List<ChatSessionEntity>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Observe active messages
    val activeMessages: StateFlow<List<ChatMessageEntity>> = _uiState
        .flatMapLatest { state ->
            if (state.activeSessionId != null) {
                repository.getMessages(state.activeSessionId)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val speakingMessageId: StateFlow<Long?> = ttsHelper.speakingMessageId

    private var activeGenerationJob: Job? = null

    init {
        // Automatically select the latest session if available
        viewModelScope.launch {
            repository.allSessions.collect { sessions ->
                if (_uiState.value.activeSessionId == null && sessions.isNotEmpty()) {
                    val latest = sessions.first()
                    _uiState.value = _uiState.value.copy(
                        activeSessionId = latest.sessionId,
                        activeModelId = latest.modelId,
                        temperature = latest.temperature,
                        topP = latest.topP,
                        systemPrompt = latest.systemPrompt ?: ""
                    )
                }
            }
        }
    }

    fun setAppTab(tabIndex: Int) {
        _uiState.value = _uiState.value.copy(currentAppTab = tabIndex)
    }

    fun onInputTextChange(newText: String) {
        _uiState.value = _uiState.value.copy(inputText = newText)
    }

    fun onImageSelected(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val inputStream = context.contentResolver.openInputStream(uri)
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (originalBitmap != null) {
                    // Downscale bitmap if too large for API
                    val maxDimension = 1024
                    val ratio = originalBitmap.width.toFloat() / originalBitmap.height.toFloat()
                    val targetWidth: Int
                    val targetHeight: Int
                    if (ratio > 1f) {
                        targetWidth = maxDimension.coerceAtMost(originalBitmap.width)
                        targetHeight = (targetWidth / ratio).toInt()
                    } else {
                        targetHeight = maxDimension.coerceAtMost(originalBitmap.height)
                        targetWidth = (targetHeight * ratio).toInt()
                    }

                    val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, true)
                    val outputStream = ByteArrayOutputStream()
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                    val byteArray = outputStream.toByteArray()
                    val base64 = Base64.encodeToString(byteArray, Base64.NO_WRAP)

                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(selectedImageBase64 = base64)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(errorBanner = "Could not load selected image")
                }
            }
        }
    }

    fun clearSelectedImage() {
        _uiState.value = _uiState.value.copy(selectedImageBase64 = null)
    }

    fun selectSession(sessionId: Long) {
        viewModelScope.launch {
            val session = repository.getSession(sessionId)
            if (session != null) {
                _uiState.value = _uiState.value.copy(
                    activeSessionId = session.sessionId,
                    activeModelId = session.modelId,
                    temperature = session.temperature,
                    topP = session.topP,
                    systemPrompt = session.systemPrompt ?: "",
                    errorBanner = null,
                    currentAppTab = 0
                )
            }
        }
    }

    fun startNewChat() {
        _uiState.value = _uiState.value.copy(
            activeSessionId = null,
            inputText = "",
            selectedImageBase64 = null,
            errorBanner = null,
            currentAppTab = 0
        )
    }

    fun sendMessage(userPrompt: String, attachedImage: String? = _uiState.value.selectedImageBase64) {
        if (userPrompt.isBlank() && attachedImage == null) return

        val promptToSend = if (userPrompt.isBlank()) "What is in this image?" else userPrompt

        val currentSub = subscriptionStatus.value
        if (currentSub.isChatLimitReached) {
            _uiState.value = _uiState.value.copy(showSubscriptionModal = true)
            return
        }

        _uiState.value = _uiState.value.copy(
            inputText = "",
            selectedImageBase64 = null,
            isGenerating = true,
            errorBanner = null
        )

        activeGenerationJob = viewModelScope.launch {
            try {
                // Ensure a session exists
                var sessionId = _uiState.value.activeSessionId
                if (sessionId == null) {
                    val autoTitle = if (promptToSend.length > 25) promptToSend.take(25).trim() + "..." else promptToSend
                    sessionId = repository.createNewSession(
                        title = autoTitle,
                        systemPrompt = _uiState.value.systemPrompt.ifBlank { null },
                        modelId = _uiState.value.activeModelId,
                        temperature = _uiState.value.temperature,
                        topP = _uiState.value.topP
                    )
                    _uiState.value = _uiState.value.copy(activeSessionId = sessionId)
                }

                val result = repository.sendMessageAndGetReply(
                    sessionId = sessionId,
                    userPrompt = promptToSend,
                    modelId = _uiState.value.activeModelId,
                    systemPrompt = _uiState.value.systemPrompt.ifBlank { null },
                    temperature = _uiState.value.temperature,
                    topP = _uiState.value.topP,
                    imageAttachmentBase64 = attachedImage
                )

                if (!result.isSuccess && result.errorMessage?.contains("limit", ignoreCase = true) == true) {
                    _uiState.value = _uiState.value.copy(showSubscriptionModal = true)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorBanner = e.localizedMessage ?: "Failed to generate reply")
            } finally {
                _uiState.value = _uiState.value.copy(isGenerating = false)
            }
        }
    }

    fun stopGenerating() {
        activeGenerationJob?.cancel()
        activeGenerationJob = null
        _uiState.value = _uiState.value.copy(isGenerating = false)
    }

    fun regenerateLastResponse() {
        val currentMessages = activeMessages.value
        val lastUserMessage = currentMessages.lastOrNull { it.role == "user" }
        if (lastUserMessage != null) {
            sendMessage(lastUserMessage.content, lastUserMessage.imageAttachmentBase64)
        }
    }

    fun applyParameters(modelId: String, temperature: Float, topP: Float, systemPrompt: String) {
        _uiState.value = _uiState.value.copy(
            activeModelId = modelId,
            temperature = temperature,
            topP = topP,
            systemPrompt = systemPrompt
        )

        val sessionId = _uiState.value.activeSessionId
        if (sessionId != null) {
            viewModelScope.launch {
                val session = repository.getSession(sessionId)
                if (session != null) {
                    repository.updateSession(
                        session.copy(
                            modelId = modelId,
                            temperature = temperature,
                            topP = topP,
                            systemPrompt = systemPrompt.ifBlank { null }
                        )
                    )
                }
            }
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (_uiState.value.activeSessionId == sessionId) {
                startNewChat()
            }
        }
    }

    fun renameSession(sessionId: Long, title: String) {
        viewModelScope.launch {
            val session = repository.getSession(sessionId)
            if (session != null) {
                repository.updateSession(session.copy(title = title))
            }
        }
    }

    fun clearAllSessions() {
        viewModelScope.launch {
            allSessions.value.forEach { repository.deleteSession(it.sessionId) }
            startNewChat()
        }
    }

    fun updateMessageFeedback(messageId: Long, isLiked: Boolean?) {
        viewModelScope.launch {
            repository.updateMessageFeedback(messageId, isLiked)
        }
    }

    fun speakMessage(message: ChatMessageEntity) {
        ttsHelper.speak(message.messageId, message.content)
    }

    fun showSubscriptionModal() {
        _uiState.value = _uiState.value.copy(showSubscriptionModal = true)
    }

    fun hideSubscriptionModal() {
        _uiState.value = _uiState.value.copy(showSubscriptionModal = false)
    }

    fun upgradeToPro(planId: String) {
        viewModelScope.launch {
            subscriptionRepo.upgradeToPro(planId)
            _uiState.value = _uiState.value.copy(showSubscriptionModal = false)
        }
    }

    fun downgradeToFree() {
        viewModelScope.launch {
            subscriptionRepo.downgradeToFree()
        }
    }

    fun dismissErrorBanner() {
        _uiState.value = _uiState.value.copy(errorBanner = null)
    }

    override fun onCleared() {
        super.onCleared()
        ttsHelper.shutdown()
    }
}
