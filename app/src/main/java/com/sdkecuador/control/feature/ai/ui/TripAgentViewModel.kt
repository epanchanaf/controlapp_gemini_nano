package com.sdkecuador.control.feature.ai.ui

import com.sdkecuador.control.feature.ai.data.AgentRepository
import com.sdkecuador.control.feature.ai.data.AgentVisual

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AgentMessage(val text: String, val fromUser: Boolean, val visual: AgentVisual? = null)
data class TripAgentState(
    val messages: List<AgentMessage> = emptyList(),
    val replying: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class TripAgentViewModel @Inject constructor(private val agent: AgentRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(TripAgentState())
    val state = mutableState.asStateFlow()

    fun ask(question: String) {
        val text = question.trim()
        if (text.isEmpty() || state.value.replying) return
        mutableState.update { it.copy(messages = it.messages + AgentMessage(text, true), replying = true, error = null) }
        viewModelScope.launch {
            try {
                val answer = agent.replyForUi(text)
                mutableState.update { it.copy(messages = it.messages + AgentMessage(answer.text.ifBlank { "No pude obtener una respuesta. Inténtalo de nuevo." }, false, answer.visual)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.update { it.copy(error = e.message ?: "No se pudo consultar el asistente") }
            } finally {
                mutableState.update { it.copy(replying = false) }
            }
        }
    }

    override fun onCleared() { agent.close(); super.onCleared() }
}
