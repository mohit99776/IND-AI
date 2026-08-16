package com.example.ui.image

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.ImageGenerationResult
import com.example.data.db.AppDatabase
import com.example.data.db.GeneratedImageEntity
import com.example.data.model.UserSubscriptionStatus
import com.example.data.repository.ImageRepository
import com.example.data.repository.SubscriptionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ImageStudioUiState(
    val prompt: String = "",
    val selectedStyle: String = "Realistic",
    val selectedAspectRatio: String = "1:1",
    val isGenerating: Boolean = false,
    val isEnhancing: Boolean = false,
    val latestResult: ImageGenerationResult? = null,
    val fullScreenImage: GeneratedImageEntity? = null,
    val errorMessage: String? = null,
    val showUpgradeModal: Boolean = false
)

val IMAGE_STYLES = listOf(
    "Realistic",
    "Indian Heritage",
    "3D Pixar",
    "Anime",
    "Cyberpunk",
    "Cinematic",
    "Fantasy",
    "Watercolor"
)

val ASPECT_RATIOS = listOf(
    "1:1" to "Square (1:1)",
    "16:9" to "Landscape (16:9)",
    "9:16" to "Story (9:16)",
    "4:3" to "Standard (4:3)",
    "3:4" to "Portrait (3:4)"
)

val SUGGESTED_IMAGE_PROMPTS = listOf(
    "A majestic royal Bengal tiger resting near ancient temple ruins surrounded by golden sunset mist",
    "Vibrant Diwali celebration in Varanasi with thousands of floating oil lamps on the sacred Ganges river",
    "Futuristic cyberpunk Mumbai in year 2077 with glowing neon hovercrafts and holographic street food stalls",
    "An ethereal peacock with iridescent sapphire and gold feathers against a mystical starlit nebula",
    "Charming cozy chai stall in misty Himalayan mountain village with snow-capped peaks in the background",
    "Cute robot wearing traditional Indian Kurta painting a colorful Rangoli on digital canvas"
)

class ImageStudioViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val subscriptionRepo = SubscriptionRepository(database.subscriptionDao())
    private val imageRepo = ImageRepository(
        imageDao = database.imageDao(),
        subscriptionRepository = subscriptionRepo
    )

    private val _uiState = MutableStateFlow(ImageStudioUiState())
    val uiState: StateFlow<ImageStudioUiState> = _uiState.asStateFlow()

    val allImages: StateFlow<List<GeneratedImageEntity>> = imageRepo.allImages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val subscriptionStatus: StateFlow<UserSubscriptionStatus> = subscriptionRepo.subscriptionStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSubscriptionStatus())

    fun onPromptChange(newPrompt: String) {
        _uiState.value = _uiState.value.copy(prompt = newPrompt, errorMessage = null)
    }

    fun onStyleSelect(style: String) {
        _uiState.value = _uiState.value.copy(selectedStyle = style)
    }

    fun onAspectRatioSelect(ratio: String) {
        _uiState.value = _uiState.value.copy(selectedAspectRatio = ratio)
    }

    fun applySuggestedPrompt(prompt: String) {
        _uiState.value = _uiState.value.copy(prompt = prompt, errorMessage = null)
    }

    fun enhancePrompt() {
        val currentPrompt = _uiState.value.prompt
        if (currentPrompt.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isEnhancing = true)
            try {
                val enhanced = imageRepo.enhancePrompt(currentPrompt)
                _uiState.value = _uiState.value.copy(prompt = enhanced, isEnhancing = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isEnhancing = false)
            }
        }
    }

    fun generateImage() {
        val prompt = _uiState.value.prompt.trim()
        if (prompt.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter an image prompt first.")
            return
        }

        // Check if free user limit reached
        val currentSub = subscriptionStatus.value
        if (currentSub.isImageLimitReached) {
            _uiState.value = _uiState.value.copy(showUpgradeModal = true)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isGenerating = true,
                errorMessage = null,
                latestResult = null
            )

            try {
                val result = imageRepo.generateAndSaveImage(
                    prompt = prompt,
                    style = _uiState.value.selectedStyle,
                    aspectRatio = _uiState.value.selectedAspectRatio
                )

                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        latestResult = result,
                        isGenerating = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = result.errorMessage ?: "Failed to generate image.",
                        isGenerating = false,
                        showUpgradeModal = result.errorMessage?.contains("limit", ignoreCase = true) == true
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.localizedMessage ?: "Image generation error occurred.",
                    isGenerating = false
                )
            }
        }
    }

    fun openFullScreen(image: GeneratedImageEntity) {
        _uiState.value = _uiState.value.copy(fullScreenImage = image)
    }

    fun closeFullScreen() {
        _uiState.value = _uiState.value.copy(fullScreenImage = null)
    }

    fun deleteImage(id: Long) {
        viewModelScope.launch {
            imageRepo.deleteImage(id)
            if (_uiState.value.fullScreenImage?.id == id) {
                closeFullScreen()
            }
        }
    }

    fun toggleFavorite(id: Long, current: Boolean) {
        viewModelScope.launch {
            imageRepo.toggleFavorite(id, !current)
        }
    }

    fun showUpgradeModal() {
        _uiState.value = _uiState.value.copy(showUpgradeModal = true)
    }

    fun hideUpgradeModal() {
        _uiState.value = _uiState.value.copy(showUpgradeModal = false)
    }

    fun upgradeToPro(planId: String) {
        viewModelScope.launch {
            subscriptionRepo.upgradeToPro(planId)
            _uiState.value = _uiState.value.copy(showUpgradeModal = false, errorMessage = null)
        }
    }

    fun downgradeToFree() {
        viewModelScope.launch {
            subscriptionRepo.downgradeToFree()
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
