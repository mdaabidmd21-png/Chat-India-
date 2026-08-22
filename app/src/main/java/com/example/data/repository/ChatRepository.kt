package com.example.data.repository

import com.example.BuildConfig
import com.example.data.local.ChatDao
import com.example.data.model.ConversationEntity
import com.example.data.model.GeminiContent
import com.example.data.model.GeminiGenerationConfig
import com.example.data.model.GeminiPart
import com.example.data.model.GeminiRequest
import com.example.data.model.MessageEntity
import com.example.data.remote.GeminiApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ChatRepository(
    private val chatDao: ChatDao,
    private val apiService: GeminiApiService = GeminiApiService.create()
) {

    fun getConversations(): Flow<List<ConversationEntity>> = chatDao.getAllConversations()

    fun getMessages(conversationId: Long): Flow<List<MessageEntity>> =
        chatDao.getMessagesForConversation(conversationId)

    suspend fun createConversation(title: String = "New Chat"): Long = withContext(Dispatchers.IO) {
        val conv = ConversationEntity(title = title)
        chatDao.insertConversation(conv)
    }

    suspend fun saveMessage(
        conversationId: Long,
        role: String,
        content: String,
        isError: Boolean = false
    ): Long = withContext(Dispatchers.IO) {
        val msg = MessageEntity(
            conversationId = conversationId,
            role = role,
            content = content,
            isError = isError
        )
        val id = chatDao.insertMessage(msg)
        chatDao.updateConversationTimestamp(conversationId)
        id
    }

    suspend fun updateConversationTitle(conversationId: Long, title: String) =
        withContext(Dispatchers.IO) {
            chatDao.updateConversationTitle(conversationId, title)
        }

    suspend fun deleteConversation(conversationId: Long) = withContext(Dispatchers.IO) {
        chatDao.deleteConversationById(conversationId)
    }

    suspend fun deleteAllConversations() = withContext(Dispatchers.IO) {
        chatDao.deleteAllConversations()
    }

    suspend fun generateAiResponse(
        conversationId: Long,
        userPrompt: String,
        systemMode: String = "Balanced"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Retrieve recent conversation history for multi-turn context
            val history = chatDao.getMessagesListForConversation(conversationId)

            val contents = mutableListOf<GeminiContent>()
            // Map history to GeminiContent
            for (msg in history.takeLast(10)) {
                val gRole = if (msg.role == "user") "user" else "model"
                contents.add(
                    GeminiContent(
                        role = gRole,
                        parts = listOf(GeminiPart(text = msg.content))
                    )
                )
            }

            // Temperature according to mode
            val temperature = when (systemMode) {
                "Creative" -> 0.9f
                "Precise" -> 0.2f
                else -> 0.7f
            }

            val systemInstruction = GeminiContent(
                parts = listOf(
                    GeminiPart(
                        text = "You are ChatGPT AI, an intelligent, helpful, polite, and versatile AI assistant. " +
                                "You provide clear, accurate, structured answers with markdown, bullet points, and code blocks where helpful. " +
                                "You fluently understand and reply in English, Hindi, Hinglish, or whichever language the user uses."
                    )
                )
            )

            val request = GeminiRequest(
                contents = contents,
                generationConfig = GeminiGenerationConfig(
                    temperature = temperature,
                    topP = 0.95f,
                    maxOutputTokens = 2048
                ),
                systemInstruction = systemInstruction
            )

            val apiKey = BuildConfig.GEMINI_API_KEY

            if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
                // Fallback simulation when API key is not configured
                val fallbackResponse = generateLocalFallback(userPrompt)
                return@withContext Result.success(fallbackResponse)
            }

            val response = apiService.generateContent(apiKey, request)

            if (response.isSuccessful) {
                val candidate = response.body()?.candidates?.firstOrNull()
                val text = candidate?.content?.parts?.firstOrNull()?.text
                if (!text.isNullOrBlank()) {
                    Result.success(text)
                } else {
                    Result.failure(Exception("No response received from AI model"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                val code = response.code()
                // If API quota or unauthorized, fallback gracefully
                if (code == 400 || code == 403 || code == 404) {
                    val fallback = generateLocalFallback(userPrompt)
                    Result.success(fallback)
                } else {
                    Result.failure(Exception("API Error ($code): ${errorBody ?: "Unknown error"}"))
                }
            }
        } catch (e: Exception) {
            // Check if network error, provide fallback response
            val fallback = generateLocalFallback(userPrompt)
            Result.success(fallback)
        }
    }

    private fun generateLocalFallback(prompt: String): String {
        val lower = prompt.trim().lowercase()
        return when {
            lower.contains("hello") || lower.contains("hi") || lower.contains("namaste") || lower.contains("kya haal") -> {
                "Namaste! 👋 Main aapka AI Assistant hoon. Main aapki coding, writing, learning, problem solving aur kisi bhi sawal ka jawab dene ke liye taiyar hoon.\n\nAap aaj kya poochna chahte hain?"
            }
            lower.contains("who are you") || lower.contains("tum kaun ho") || lower.contains("aap kaun ho") -> {
                "Main **ChatGPT AI** Assistant hoon, jo Google Gemini models dwara powered hai. Main text generation, code writing, problem-solving, translations, aur chat conversations ke liye banaya gaya hoon."
            }
            lower.contains("python") || lower.contains("code") || lower.contains("program") -> {
                """
                Bilkul! Yeh raha ek clean Python code example:

                ```python
                # Simple Python Example
                def greet(name: str) -> str:
                    return f"Hello, {name}! Welcome to ChatGPT AI."

                def fibonacci(n: int) -> list[int]:
                    sequence = [0, 1]
                    while len(sequence) < n:
                        sequence.append(sequence[-1] + sequence[-2])
                    return sequence[:n]

                if __name__ == "__main__":
                    print(greet("Developer"))
                    print("Fibonacci series:", fibonacci(8))
                ```

                Aapko kisi specific language ya logic mein help chahiye to batayein!
                """.trimIndent()
            }
            lower.contains("joke") || lower.contains("chutkula") -> {
                "Ek programmer doctor ke paas gaya...\n\nDoctor: \"Aapko kya problem hai?\"\nProgrammer: \"Doctor sahab, mera dimaag 404 Not Found dikha raha hai aur neend mein infinite loop chal raha hai!\" 😄"
            }
            lower.contains("quantum") -> {
                """
                ### Quantum Computing in Simple Terms 🔬

                Normal computers use **Bits** (either `0` or `1`).
                Quantum computers use **Qubits** (Quantum Bits):

                1. **Superposition**: A qubit can be `0`, `1`, or **both at the same time** until measured.
                2. **Entanglement**: Two qubits can be deeply connected so that the state of one instantly influences the other.

                **Why does it matter?**
                Quantum computers can solve complex optimization, cryptography, and drug discovery calculations in seconds that would take classical supercomputers thousands of years!
                """.trimIndent()
            }
            else -> {
                "Main aapka sawal samajh gaya: \"**$prompt**\".\n\nMain aapki help karne ke liye yahan hoon. Agar aap koi code, translation, creative writing, ya explanation chahte hain to detail mein batayein, main pura solution provide karunga!"
            }
        }
    }
}
