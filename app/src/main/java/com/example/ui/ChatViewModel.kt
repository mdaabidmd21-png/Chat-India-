package com.example.ui

import android.app.Application
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ChatDatabase
import com.example.data.model.ConversationEntity
import com.example.data.model.MessageEntity
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(application: Application) : AndroidViewModel(application), TextToSpeech.OnInitListener {

    private val database = ChatDatabase.getDatabase(application)
    private val repository = ChatRepository(database.chatDao())

    val conversations: StateFlow<List<ConversationEntity>> = repository.getConversations()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _currentConversationId = MutableStateFlow<Long?>(null)
    val currentConversationId: StateFlow<Long?> = _currentConversationId.asStateFlow()

    val messages: StateFlow<List<MessageEntity>> = _currentConversationId.flatMapLatest { convId ->
        if (convId != null) {
            repository.getMessages(convId)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _selectedTone = MutableStateFlow("Balanced")
    val selectedTone: StateFlow<String> = _selectedTone.asStateFlow()

    private val _isTtsSpeaking = MutableStateFlow(false)
    val isTtsSpeaking: StateFlow<Boolean> = _isTtsSpeaking.asStateFlow()

    private val _ttsSpeakingText = MutableStateFlow<String?>(null)
    val ttsSpeakingText: StateFlow<String?> = _ttsSpeakingText.asStateFlow()

    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false

    init {
        tts = TextToSpeech(application, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsInitialized = true
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isTtsSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isTtsSpeaking.value = false
                    _ttsSpeakingText.value = null
                }

                override fun onError(utteranceId: String?) {
                    _isTtsSpeaking.value = false
                    _ttsSpeakingText.value = null
                }
            })
        }
    }

    fun updateInputText(text: String) {
        _inputText.value = text
    }

    fun setTone(tone: String) {
        _selectedTone.value = tone
    }

    fun selectConversation(id: Long) {
        _currentConversationId.value = id
        stopSpeaking()
    }

    fun startNewChat() {
        _currentConversationId.value = null
        stopSpeaking()
        _inputText.value = ""
    }

    fun sendMessage(promptText: String? = null) {
        val prompt = (promptText ?: _inputText.value).trim()
        if (prompt.isBlank() || _isGenerating.value) return

        _inputText.value = ""
        _isGenerating.value = true

        viewModelScope.launch {
            try {
                // If no active conversation, create one using prompt as title
                var convId = _currentConversationId.value
                if (convId == null) {
                    val title = if (prompt.length > 30) prompt.take(30) + "..." else prompt
                    convId = repository.createConversation(title)
                    _currentConversationId.value = convId
                }

                // Save user message
                repository.saveMessage(convId, role = "user", content = prompt)

                // Call Gemini AI
                val result = repository.generateAiResponse(
                    conversationId = convId,
                    userPrompt = prompt,
                    systemMode = _selectedTone.value
                )

                result.onSuccess { reply ->
                    repository.saveMessage(convId, role = "model", content = reply)
                }.onFailure { error ->
                    repository.saveMessage(
                        convId,
                        role = "model",
                        content = "Sorry, I encountered an issue: ${error.localizedMessage ?: "Unknown error"}. Please check your connection and try again.",
                        isError = true
                    )
                }
            } catch (e: Exception) {
                val convId = _currentConversationId.value
                if (convId != null) {
                    repository.saveMessage(
                        convId,
                        role = "model",
                        content = "An unexpected error occurred: ${e.message}",
                        isError = true
                    )
                }
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun regenerateLastResponse() {
        val currentMsgs = messages.value
        val convId = _currentConversationId.value ?: return
        if (_isGenerating.value || currentMsgs.isEmpty()) return

        val lastUserMsg = currentMsgs.lastOrNull { it.role == "user" } ?: return

        _isGenerating.value = true
        viewModelScope.launch {
            try {
                val result = repository.generateAiResponse(
                    conversationId = convId,
                    userPrompt = lastUserMsg.content,
                    systemMode = _selectedTone.value
                )

                result.onSuccess { reply ->
                    repository.saveMessage(convId, role = "model", content = reply)
                }.onFailure { error ->
                    repository.saveMessage(
                        convId,
                        role = "model",
                        content = "Regeneration failed: ${error.localizedMessage}",
                        isError = true
                    )
                }
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun toggleSpeak(content: String) {
        if (_isTtsSpeaking.value && _ttsSpeakingText.value == content) {
            stopSpeaking()
        } else {
            speakText(content)
        }
    }

    private fun speakText(text: String) {
        if (!isTtsInitialized || tts == null) return
        stopSpeaking()

        // Clean markdown symbols for cleaner TTS speech
        val cleanSpeech = text
            .replace(Regex("```[a-zA-Z]*"), "")
            .replace("```", "")
            .replace("**", "")
            .replace("*", "")
            .replace("#", "")
            .replace("`", "")
            .trim()

        _ttsSpeakingText.value = text
        _isTtsSpeaking.value = true

        val containsHindi = text.any { it in '\u0900'..'\u097F' }
        if (containsHindi) {
            tts?.language = Locale.forLanguageTag("hi-IN")
        } else {
            tts?.language = Locale.US
        }

        tts?.speak(cleanSpeech, TextToSpeech.QUEUE_FLUSH, null, "AI_SPEECH_${System.currentTimeMillis()}")
    }

    fun stopSpeaking() {
        tts?.stop()
        _isTtsSpeaking.value = false
        _ttsSpeakingText.value = null
    }

    fun renameConversation(id: Long, title: String) {
        viewModelScope.launch {
            repository.updateConversationTitle(id, title)
        }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            repository.deleteConversation(id)
            if (_currentConversationId.value == id) {
                _currentConversationId.value = null
            }
        }
    }

    fun clearAllConversations() {
        viewModelScope.launch {
            repository.deleteAllConversations()
            _currentConversationId.value = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        tts?.stop()
        tts?.shutdown()
    }
}
