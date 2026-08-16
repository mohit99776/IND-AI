package com.example.data.repository

import com.example.data.api.ContentJson
import com.example.data.api.GeminiApiRepository
import com.example.data.api.GeminiGenerationResult
import com.example.data.api.PartJson
import com.example.data.db.ChatDao
import com.example.data.db.ChatMessageEntity
import com.example.data.db.ChatSessionEntity
import kotlinx.coroutines.flow.Flow

class ChatRepository(
    private val chatDao: ChatDao,
    private val geminiApiRepository: GeminiApiRepository = GeminiApiRepository(),
    private val subscriptionRepository: SubscriptionRepository? = null
) {
    val allSessions: Flow<List<ChatSessionEntity>> = chatDao.getAllSessions()

    fun getMessages(sessionId: Long): Flow<List<ChatMessageEntity>> =
        chatDao.getMessagesForSession(sessionId)

    suspend fun getSession(sessionId: Long): ChatSessionEntity? =
        chatDao.getSessionById(sessionId)

    suspend fun createNewSession(
        title: String = "New Chat",
        systemPrompt: String? = null,
        modelId: String = "gemini-3.5-flash",
        temperature: Float = 0.7f,
        topP: Float = 0.95f
    ): Long {
        val session = ChatSessionEntity(
            title = title,
            systemPrompt = systemPrompt,
            modelId = modelId,
            temperature = temperature,
            topP = topP,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        return chatDao.insertSession(session)
    }

    suspend fun updateSession(session: ChatSessionEntity) {
        chatDao.updateSession(session)
    }

    suspend fun deleteSession(sessionId: Long) {
        chatDao.deleteSession(sessionId)
    }

    suspend fun clearSessionMessages(sessionId: Long) {
        chatDao.clearMessagesForSession(sessionId)
    }

    suspend fun updateMessageFeedback(messageId: Long, isLiked: Boolean?) {
        chatDao.updateMessageFeedback(messageId, isLiked)
    }

    suspend fun sendMessageAndGetReply(
        sessionId: Long,
        userPrompt: String,
        modelId: String,
        systemPrompt: String?,
        temperature: Float,
        topP: Float
    ): GeminiGenerationResult {
        // 1. Check subscription limit
        if (subscriptionRepository != null && !subscriptionRepository.canSendMessage()) {
            val limitMessage = "⚠️ **Daily Chat Limit Reached (20/20)**\n\nYou've used all 20 free messages today. Upgrade to **IND AI Pro** for **₹200/month** for unlimited deep reasoning chats with Gemini 3.1 Pro and unlimited AI image creations!\n\n*Tap the Pro badge above to upgrade.*"
            val userMessage = ChatMessageEntity(
                sessionId = sessionId,
                role = "user",
                content = userPrompt,
                timestamp = System.currentTimeMillis()
            )
            chatDao.insertMessage(userMessage)

            val errorMessage = ChatMessageEntity(
                sessionId = sessionId,
                role = "error",
                content = limitMessage,
                timestamp = System.currentTimeMillis() + 50,
                modelUsed = modelId
            )
            chatDao.insertMessage(errorMessage)

            return GeminiGenerationResult(
                text = limitMessage,
                totalTokens = 0,
                latencyMs = 100,
                isSuccess = false,
                errorMessage = "Daily chat limit reached."
            )
        }

        // Record message in subscription counter
        subscriptionRepository?.recordChatMessage()

        // 1. Save user message to database
        val userMessage = ChatMessageEntity(
            sessionId = sessionId,
            role = "user",
            content = userPrompt,
            timestamp = System.currentTimeMillis()
        )
        chatDao.insertMessage(userMessage)

        // 2. Fetch past conversation for context
        val pastMessages = chatDao.getMessagesForSessionList(sessionId)
        
        // Auto-update session title if it's default
        val session = chatDao.getSessionById(sessionId)
        if (session != null && (session.title == "New Chat" || session.title.isBlank())) {
            val autoTitle = if (userPrompt.length > 28) {
                userPrompt.take(28).trim() + "..."
            } else userPrompt.trim()
            chatDao.updateSessionTitleAndTimestamp(sessionId, autoTitle, System.currentTimeMillis())
        } else if (session != null) {
            chatDao.updateSessionTitleAndTimestamp(sessionId, session.title, System.currentTimeMillis())
        }

        // Convert past messages to Gemini ContentJson
        // Only use the last 12 turns to prevent hitting token limits while maintaining context
        val contextTurns = pastMessages.takeLast(12).map { msg ->
            val role = if (msg.role == "user") "user" else "model"
            ContentJson(
                role = role,
                parts = listOf(PartJson(text = msg.content))
            )
        }

        // 3. Call Gemini API
        val result = geminiApiRepository.generateResponse(
            modelId = modelId,
            conversationHistory = contextTurns,
            systemInstruction = systemPrompt,
            temperature = temperature,
            topP = topP
        )

        // 4. Save model reply to database
        val modelMessage = ChatMessageEntity(
            sessionId = sessionId,
            role = if (result.isSuccess) "model" else "error",
            content = result.text,
            timestamp = System.currentTimeMillis(),
            tokenCount = result.totalTokens,
            latencyMs = result.latencyMs,
            modelUsed = modelId
        )
        chatDao.insertMessage(modelMessage)

        return result
    }
}
