package com.example.data.repository

import com.example.data.db.SubscriptionDao
import com.example.data.db.UserSubscriptionEntity
import com.example.data.model.SubscriptionConfig
import com.example.data.model.UserSubscriptionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SubscriptionRepository(
    private val subscriptionDao: SubscriptionDao
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    val subscriptionStatus: Flow<UserSubscriptionStatus> = subscriptionDao.getSubscriptionFlow().map { entity ->
        val today = getTodayString()
        if (entity == null) {
            UserSubscriptionStatus()
        } else {
            // Check if date changed to reset daily usage
            val isSameDay = entity.lastResetDate == today
            val imagesUsed = if (isSameDay) entity.dailyImagesUsed else 0
            val chatsUsed = if (isSameDay) entity.dailyChatsUsed else 0

            // Check if subscription has expired
            val isProActive = entity.isPro && (entity.expiresAt == 0L || entity.expiresAt > System.currentTimeMillis())

            UserSubscriptionStatus(
                isPro = isProActive,
                planName = if (isProActive) "IND AI Pro" else "Free Tier",
                dailyImagesUsed = imagesUsed,
                dailyChatsUsed = chatsUsed,
                dailyImagesLimit = SubscriptionConfig.FREE_DAILY_IMAGES_LIMIT,
                dailyChatsLimit = SubscriptionConfig.FREE_DAILY_CHATS_LIMIT,
                expiresAtTimestamp = if (isProActive) entity.expiresAt else null
            )
        }
    }

    private fun getTodayString(): String = dateFormat.format(Date())

    suspend fun getOrCreateSubscription(): UserSubscriptionEntity {
        val current = subscriptionDao.getSubscription()
        val today = getTodayString()
        if (current == null) {
            val initial = UserSubscriptionEntity(
                id = 1,
                isPro = false,
                planType = "free",
                dailyImagesUsed = 0,
                dailyChatsUsed = 0,
                lastResetDate = today
            )
            subscriptionDao.saveSubscription(initial)
            return initial
        } else if (current.lastResetDate != today) {
            val updated = current.copy(
                dailyImagesUsed = 0,
                dailyChatsUsed = 0,
                lastResetDate = today
            )
            subscriptionDao.saveSubscription(updated)
            return updated
        }
        return current
    }

    suspend fun canGenerateImage(): Boolean {
        val current = getOrCreateSubscription()
        if (current.isPro && (current.expiresAt == 0L || current.expiresAt > System.currentTimeMillis())) {
            return true
        }
        return current.dailyImagesUsed < SubscriptionConfig.FREE_DAILY_IMAGES_LIMIT
    }

    suspend fun recordImageGeneration() {
        val current = getOrCreateSubscription()
        subscriptionDao.saveSubscription(
            current.copy(
                dailyImagesUsed = current.dailyImagesUsed + 1
            )
        )
    }

    suspend fun canSendMessage(): Boolean {
        val current = getOrCreateSubscription()
        if (current.isPro && (current.expiresAt == 0L || current.expiresAt > System.currentTimeMillis())) {
            return true
        }
        return current.dailyChatsUsed < SubscriptionConfig.FREE_DAILY_CHATS_LIMIT
    }

    suspend fun recordChatMessage() {
        val current = getOrCreateSubscription()
        subscriptionDao.saveSubscription(
            current.copy(
                dailyChatsUsed = current.dailyChatsUsed + 1
            )
        )
    }

    suspend fun upgradeToPro(planId: String = "pro_monthly") {
        val current = getOrCreateSubscription()
        val durationDays = if (planId == "pro_yearly") 365L else 30L
        val expiryTime = System.currentTimeMillis() + (durationDays * 24 * 60 * 60 * 1000L)
        subscriptionDao.saveSubscription(
            current.copy(
                isPro = true,
                planType = planId,
                subscribedAt = System.currentTimeMillis(),
                expiresAt = expiryTime
            )
        )
    }

    suspend fun downgradeToFree() {
        val current = getOrCreateSubscription()
        subscriptionDao.saveSubscription(
            current.copy(
                isPro = false,
                planType = "free",
                expiresAt = 0L
            )
        )
    }
}
