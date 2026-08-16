package com.example.data.model

data class GeminiModelInfo(
    val id: String,
    val displayName: String,
    val tag: String,
    val description: String,
    val badge: String,
    val iconName: String
)

object AvailableModels {
    val FLASH = GeminiModelInfo(
        id = "gemini-3.5-flash",
        displayName = "Gemini 3.5 Flash",
        tag = "Default",
        description = "Next-generation multimodal model for high-speed general tasks and chat.",
        badge = "⚡ Fast & General",
        iconName = "flash"
    )

    val PRO = GeminiModelInfo(
        id = "gemini-3.1-pro-preview",
        displayName = "Gemini 3.1 Pro",
        tag = "Reasoning",
        description = "Advanced reasoning, coding, mathematical logic, and complex STEM tasks.",
        badge = "🧠 Deep Reasoning",
        iconName = "psychology"
    )

    val FLASH_IMAGE = GeminiModelInfo(
        id = "gemini-2.5-flash-image",
        displayName = "Gemini 2.5 Flash Image",
        tag = "Image Gen",
        description = "Specialized visual synthesis model for generating photorealistic and artistic images from text.",
        badge = "🎨 Image Creator",
        iconName = "image"
    )

    val FLASH_LITE = GeminiModelInfo(
        id = "gemini-3.1-flash-lite-preview",
        displayName = "Gemini 3.1 Flash Lite",
        tag = "Ultra-Fast",
        description = "Lightweight model optimized for low-latency tasks and quick interactions.",
        badge = "🚀 Ultra-Fast",
        iconName = "speed"
    )

    val ALL = listOf(FLASH, PRO, FLASH_IMAGE, FLASH_LITE)
    val DEFAULT = FLASH
}

data class SystemPromptPreset(
    val id: String,
    val title: String,
    val category: String,
    val prompt: String,
    val icon: String
)

object PresetPrompts {
    val PRESETS = listOf(
        SystemPromptPreset(
            id = "general",
            title = "Default AI Studio Assistant",
            category = "General",
            prompt = "You are a helpful, versatile, and precise AI assistant powered by Google Gemini. Format your responses with clean Markdown, clear headings, bullet points, and code blocks where helpful.",
            icon = "sparkles"
        ),
        SystemPromptPreset(
            id = "coding",
            title = "Senior Software Architect",
            category = "Coding",
            prompt = "You are a Principal Software Engineer and Android/Kotlin expert. Provide clean, production-grade, idiomatic code with thorough explanations, edge-case analysis, and best architectural practices.",
            icon = "code"
        ),
        SystemPromptPreset(
            id = "concise",
            title = "Executive Summarizer",
            category = "Productivity",
            prompt = "You are an executive assistant. Give ultra-concise, high-impact bullet points and actionable takeaways. Avoid fluff and preamble.",
            icon = "summarize"
        ),
        SystemPromptPreset(
            id = "creative",
            title = "Creative Copywriter & Designer",
            category = "Creative",
            prompt = "You are a creative director and storyteller. Provide vivid, engaging, and memorable copy with expressive language and aesthetic structure.",
            icon = "palette"
        ),
        SystemPromptPreset(
            id = "tutor",
            title = "Socratic STEM Tutor",
            category = "Education",
            prompt = "You are an encouraging and pedagogical STEM professor. Explain complex concepts using intuitive real-world analogies, step-by-step reasoning, and thoughtful questions.",
            icon = "school"
        )
    )
}

data class StarterPrompt(
    val title: String,
    val prompt: String,
    val category: String,
    val icon: String
)

val STARTER_PROMPTS = listOf(
    StarterPrompt(
        title = "Explain Quantum Computing",
        prompt = "Explain quantum computing and qubits using an intuitive everyday analogy that a high schooler would easily understand.",
        category = "Science",
        icon = "science"
    ),
    StarterPrompt(
        title = "Kotlin Coroutines & Flow",
        prompt = "Explain the difference between StateFlow, SharedFlow, and regular Channels in Kotlin Coroutines with clear Android code examples.",
        category = "Coding",
        icon = "code"
    ),
    StarterPrompt(
        title = "Modern Jetpack Compose UI",
        prompt = "How can I build smooth, high-performance animations and custom layout shapes in Jetpack Compose Material 3?",
        category = "Design",
        icon = "palette"
    ),
    StarterPrompt(
        title = "System Design Interview",
        prompt = "Design a globally distributed, low-latency URL shortener service (like bit.ly) handling 100M active requests daily.",
        category = "Architecture",
        icon = "hub"
    )
)
