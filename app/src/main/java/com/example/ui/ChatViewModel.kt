package com.example.ui

import android.app.Application
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

data class ChatUiState(
    val activeSessionId: Long? = null,
    val activeModelId: String = AvailableModels.FLASH.id,
    val temperature: Float = 0.7f,
    val topP: Float = 0.95f,
    val systemPrompt: String = "",
    val isGenerating: Boolean = false,
    val inputText: String = "",
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
            errorBanner = null,
            currentAppTab = 0
        )
    }

    fun sendMessage(userPrompt: String) {
        if (userPrompt.isBlank()) return

        val currentSub = subscriptionStatus.value
        if (currentSub.isChatLimitReached) {
            _uiState.value = _uiState.value.copy(showSubscriptionModal = true)
            return
        }

        _uiState.value = _uiState.value.copy(inputText = "", isGenerating = true, errorBanner = null)

        activeGenerationJob = viewModelScope.launch {
            try {
                // Ensure a session exists
                var sessionId = _uiState.value.activeSessionId
                if (sessionId == null) {
                    val autoTitle = if (userPrompt.length > 25) userPrompt.take(25).trim() + "..." else userPrompt
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
                    userPrompt = userPrompt,
                    modelId = _uiState.value.activeModelId,
                    systemPrompt = _uiState.value.systemPrompt.ifBlank { null },
                    temperature = _uiState.value.temperature,
                    topP = _uiState.value.topP
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
            sendMessage(lastUserMessage.content)
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
