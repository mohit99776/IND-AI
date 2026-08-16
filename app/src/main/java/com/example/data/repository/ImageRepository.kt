package com.example.data.repository

import com.example.data.api.GeminiApiRepository
import com.example.data.api.ImageGenerationResult
import com.example.data.db.GeneratedImageEntity
import com.example.data.db.ImageDao
import kotlinx.coroutines.flow.Flow

class ImageRepository(
    private val imageDao: ImageDao,
    private val geminiApiRepository: GeminiApiRepository = GeminiApiRepository(),
    private val subscriptionRepository: SubscriptionRepository
) {
    val allImages: Flow<List<GeneratedImageEntity>> = imageDao.getAllImages()

    suspend fun generateAndSaveImage(
        prompt: String,
        style: String = "Realistic",
        aspectRatio: String = "1:1"
    ): ImageGenerationResult {
        // 1. Check if user is eligible to generate image under current subscription
        val canGenerate = subscriptionRepository.canGenerateImage()
        if (!canGenerate) {
            return ImageGenerationResult(
                prompt = prompt,
                style = style,
                aspectRatio = aspectRatio,
                isSuccess = false,
                errorMessage = "Daily free image limit reached (5/5). Please upgrade to IND AI Pro for ₹200/month for unlimited image generation."
            )
        }

        // 2. Call Gemini API
        val result = geminiApiRepository.generateImage(
            prompt = prompt,
            style = style,
            aspectRatio = aspectRatio
        )

        // 3. If successful, record usage and save to DB
        if (result.isSuccess) {
            subscriptionRepository.recordImageGeneration()

            val entity = GeneratedImageEntity(
                prompt = prompt,
                style = style,
                aspectRatio = aspectRatio,
                imageUrl = result.imageUrl,
                base64Data = result.base64Data,
                createdAt = System.currentTimeMillis(),
                modelUsed = "gemini-2.5-flash-image"
            )
            imageDao.insertImage(entity)
        }

        return result
    }

    suspend fun enhancePrompt(prompt: String): String {
        return geminiApiRepository.enhancePrompt(prompt)
    }

    suspend fun deleteImage(id: Long) {
        imageDao.deleteImage(id)
    }

    suspend fun toggleFavorite(id: Long, isFavorite: Boolean) {
        imageDao.toggleFavorite(id, isFavorite)
    }
}
