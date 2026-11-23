package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.FormDraftEntity
import com.example.data.model.FormDefinition
import com.example.data.model.FormResponse
import com.example.data.model.QuestionAnswer
import com.example.data.model.SubmitOutcome
import com.example.data.remote.ConnectivityObserver
import com.example.data.repository.FormRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class FormPulseViewModel(
    private val repository: FormRepository,
    private val connectivityObserver: ConnectivityObserver,
    private val currentUserId: String
) : ViewModel() {

    val isSystemOnline: StateFlow<Boolean> = connectivityObserver.isSystemOnline
    val isOfflineModeForced: StateFlow<Boolean> = connectivityObserver.isOfflineModeForced

    val isEffectivelyOnline: StateFlow<Boolean> = combine(
        connectivityObserver.isSystemOnline,
        connectivityObserver.isOfflineModeForced
    ) { sysOnline, forcedOffline ->
        sysOnline && !forcedOffline
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = connectivityObserver.isEffectivelyOnline()
    )

    val formsState: StateFlow<UiState<List<FormDefinition>>> = repository.observeForms(currentUserId)
        .map<List<FormDefinition>, UiState<List<FormDefinition>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing forms", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load forms"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    private val activeFormId = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeFormState: StateFlow<UiState<FormDefinition?>> = activeFormId
        .flatMapLatest { formId ->
            if (formId.isNullOrBlank()) {
                flowOf(UiState.Success(null))
            } else {
                repository.observeForm(currentUserId, formId)
                    .map<FormDefinition?, UiState<FormDefinition?>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        Log.w(TAG, "Error observing form $formId", rootError)
                        emit(UiState.Error(rootError.message ?: "Failed to load form"))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeResponsesState: StateFlow<UiState<List<FormResponse>>> = activeFormId
        .flatMapLatest { formId ->
            if (formId.isNullOrBlank()) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeResponses(currentUserId, formId)
                    .map<List<FormResponse>, UiState<List<FormResponse>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        Log.w(TAG, "Error observing responses for $formId", rootError)
                        emit(UiState.Error(rootError.message ?: "Failed to load responses"))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    fun selectForm(formId: String?) {
        activeFormId.value = formId
    }

    fun observeFormById(formId: String): Flow<FormDefinition?> =
        repository.observeForm(currentUserId, formId)

    fun toggleOfflineMode() {
        viewModelScope.launch {
            val next = !connectivityObserver.isOfflineModeForced.value
            connectivityObserver.setOfflineModeForced(next)
            _actionMessage.value = if (next) {
                "Offline Mode enabled — submissions queue locally until reconnected"
            } else {
                "Back online — syncing queued forms & responses"
            }
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    suspend fun createForm(form: FormDefinition): Result<String> {
        val result = repository.createForm(
            userId = currentUserId,
            form = form,
            isNetworkAllowed = connectivityObserver.isEffectivelyOnline()
        )
        result.onFailure {
            _actionMessage.value = it.localizedMessage ?: "Unable to save form"
        }
        return result
    }

    suspend fun updateForm(form: FormDefinition): Result<Unit> {
        val result = repository.updateForm(
            userId = currentUserId,
            form = form,
            isNetworkAllowed = connectivityObserver.isEffectivelyOnline()
        )
        result.onFailure {
            _actionMessage.value = it.localizedMessage ?: "Unable to update form"
        }
        return result
    }

    fun toggleAcceptingResponses(formId: String, accepting: Boolean) {
        viewModelScope.launch {
            val result = repository.toggleAcceptingResponses(
                userId = currentUserId,
                formId = formId,
                accepting = accepting,
                isNetworkAllowed = connectivityObserver.isEffectivelyOnline()
            )
            result.onFailure {
                _actionMessage.value = it.localizedMessage ?: "Failed to update form status"
            }
        }
    }

    fun deleteForm(formId: String) {
        viewModelScope.launch {
            val result = repository.deleteForm(
                userId = currentUserId,
                formId = formId,
                isNetworkAllowed = connectivityObserver.isEffectivelyOnline()
            )
            result.onSuccess {
                _actionMessage.value = "Form deleted"
            }.onFailure {
                _actionMessage.value = it.localizedMessage ?: "Failed to delete form"
            }
        }
    }

    suspend fun submitResponse(
        formId: String,
        respondentName: String,
        answers: List<QuestionAnswer>,
        completionSeconds: Long
    ): Result<SubmitOutcome> {
        val response = FormResponse(
            userId = currentUserId,
            formId = formId,
            respondentName = respondentName.trim().ifBlank { "Anonymous" },
            answers = answers,
            completionSeconds = completionSeconds
        )
        val result = repository.submitResponse(
            userId = currentUserId,
            formId = formId,
            response = response,
            isNetworkAllowed = connectivityObserver.isEffectivelyOnline()
        )
        result.onFailure {
            _actionMessage.value = it.localizedMessage ?: "Submission failed"
        }
        return result
    }

    suspend fun loadDraft(formId: String): FormDraftEntity? =
        repository.getDraft(currentUserId, formId)

    fun saveDraft(
        formId: String,
        respondentName: String,
        answers: Map<String, QuestionAnswer>,
        currentPage: Int
    ) {
        viewModelScope.launch {
            repository.saveDraft(currentUserId, formId, respondentName, answers, currentPage)
        }
    }

    fun clearDraft(formId: String) {
        viewModelScope.launch {
            repository.clearDraft(currentUserId, formId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        connectivityObserver.unregister()
    }

    private companion object {
        const val TAG = "FormPulseVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
