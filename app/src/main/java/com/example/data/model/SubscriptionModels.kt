package com.example.data.model

data class SubscriptionPlan(
    val id: String,
    val title: String,
    val priceInr: Int,
    val period: String,
    val subtitle: String,
    val isPopular: Boolean,
    val features: List<String>
)

object SubscriptionConfig {
    const val FREE_DAILY_IMAGES_LIMIT = 5
    const val FREE_DAILY_CHATS_LIMIT = 20

    val PLANS = listOf(
        SubscriptionPlan(
            id = "pro_monthly",
            title = "IND AI Pro (Monthly)",
            priceInr = 200,
            period = "/ month",
            subtitle = "Best for active creators & developers",
            isPopular = true,
            features = listOf(
                "✨ Unlimited AI Image Creation (Imagen & Gemini 2.5)",
                "🧠 Unlimited Chat with Gemini 3.1 Pro Reasoning",
                "🚀 5x Turbo Processing Speed & Zero Waiting",
                "🎨 4K Ultra-HD Downloads & Custom Aspect Ratios",
                "🇮🇳 All Indian Regional Languages & Code Assist",
                "⚡ Priority Access to upcoming AI Features"
            )
        ),
        SubscriptionPlan(
            id = "pro_yearly",
            title = "IND AI Pro (Annual)",
            priceInr = 1999,
            period = "/ year",
            subtitle = "Save ₹401 + 2 Months Free",
            isPopular = false,
            features = listOf(
                "✨ Everything in Pro Monthly",
                "🎁 2 Months Free (Save 17%)",
                "⭐ Exclusive VIP Supporter Badge",
                "🔮 Priority Beta Model Access"
            )
        )
    )
}

data class UserSubscriptionStatus(
    val isPro: Boolean = false,
    val planName: String = "Free Tier",
    val dailyImagesUsed: Int = 0,
    val dailyChatsUsed: Int = 0,
    val dailyImagesLimit: Int = SubscriptionConfig.FREE_DAILY_IMAGES_LIMIT,
    val dailyChatsLimit: Int = SubscriptionConfig.FREE_DAILY_CHATS_LIMIT,
    val expiresAtTimestamp: Long? = null
) {
    val remainingImages: Int
        get() = if (isPro) Int.MAX_VALUE else (dailyImagesLimit - dailyImagesUsed).coerceAtLeast(0)

    val remainingChats: Int
        get() = if (isPro) Int.MAX_VALUE else (dailyChatsLimit - dailyChatsUsed).coerceAtLeast(0)

    val isImageLimitReached: Boolean
        get() = !isPro && dailyImagesUsed >= dailyImagesLimit

    val isChatLimitReached: Boolean
        get() = !isPro && dailyChatsUsed >= dailyChatsLimit
}
