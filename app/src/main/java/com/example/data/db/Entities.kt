package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey(autoGenerate = true) val sessionId: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val systemPrompt: String? = null,
    val modelId: String = "gemini-3.5-flash",
    val temperature: Float = 0.7f,
    val topP: Float = 0.95f
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val messageId: Long = 0,
    val sessionId: Long,
    val role: String, // "user", "model", or "error"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val tokenCount: Int = 0,
    val latencyMs: Long = 0,
    val modelUsed: String? = null,
    val isLiked: Boolean? = null
)

@Entity(tableName = "generated_images")
data class GeneratedImageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val prompt: String,
    val style: String = "Realistic",
    val aspectRatio: String = "1:1",
    val imageUrl: String? = null,
    val base64Data: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val modelUsed: String = "gemini-2.5-flash-image",
    val isFavorite: Boolean = false
)

@Entity(tableName = "user_subscription")
data class UserSubscriptionEntity(
    @PrimaryKey val id: Int = 1,
    val isPro: Boolean = false,
    val planType: String = "free", // "free", "pro_monthly", "pro_yearly"
    val subscribedAt: Long = 0L,
    val expiresAt: Long = 0L,
    val dailyImagesUsed: Int = 0,
    val dailyChatsUsed: Int = 0,
    val lastResetDate: String = "" // "YYYY-MM-DD"
)
