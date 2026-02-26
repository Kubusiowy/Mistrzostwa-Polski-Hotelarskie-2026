package com.example.ekh2026.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ekh2026.BuildConfig
import com.example.ekh2026.data.JurorRepository
import com.example.ekh2026.data.RepoResult
import com.example.ekh2026.data.model.CriterionDto
import com.example.ekh2026.data.model.JurorSession
import com.example.ekh2026.data.model.JurorSnapshot
import com.example.ekh2026.data.model.ParticipantDto
import com.example.ekh2026.data.model.UpsertMyScoreRequest
import com.example.ekh2026.data.model.scoreKey
import com.example.ekh2026.data.network.JurorApiServiceFactory
import com.example.ekh2026.data.network.JurorWebSocketClient
import com.example.ekh2026.data.storage.SecureSessionStorage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val ROLE_JUROR = "JUROR"

data class JurorUiState(
    val isStarting: Boolean = true,
    val isAuthenticated: Boolean = false,
    val jurorDisplayName: String = "",
    val firstNameInput: String = "",
    val surNameInput: String = "",
    val adminPasswordInput: String = "",
    val loginEnabled: Boolean = true,
    val loginStatusLoaded: Boolean = false,
    val isLoginInProgress: Boolean = false,
    val isDataLoading: Boolean = false,
    val isSavingScore: Boolean = false,
    val wsConnected: Boolean = false,
    val participants: List<ParticipantDto> = emptyList(),
    val criteria: List<CriterionDto> = emptyList(),
    val scores: Map<String, Int> = emptyMap(),
    val draftScores: Map<String, Int> = emptyMap(),
    val selectedParticipantId: String? = null,
    val connectionNote: String = ""
)

class JurorViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionStorage = SecureSessionStorage(application)
    private val network = JurorApiServiceFactory.create(BuildConfig.API_BASE_URL)

    private val repository = JurorRepository(
        authApi = network.authApi,
        jurorApi = network.jurorApi,
        sessionStorage = sessionStorage
    )

    private val websocketClient = JurorWebSocketClient(
        httpClient = network.httpClient,
        baseUrl = BuildConfig.API_BASE_URL,
        listener = object : JurorWebSocketClient.Listener {
            override fun onSocketConnected() {
                viewModelScope.launch {
                    _uiState.update {
                        it.copy(
                            wsConnected = true,
                            connectionNote = "Połączono live"
                        )
                    }
                    sendInitMessage()
                }
            }

            override fun onSnapshot(snapshot: JurorSnapshot) {
                viewModelScope.launch {
                    applySnapshot(snapshot)
                    _uiState.update {
                        it.copy(
                            isDataLoading = false,
                            isSavingScore = false,
                            wsConnected = true,
                            connectionNote = "Dane odświeżone live"
                        )
                    }
                }
            }

            override fun onAck(action: String, message: String) {
                if (action != "upsertMyScore") return
                viewModelScope.launch {
                    _uiState.update { it.copy(isSavingScore = false) }
                    publishMessage(if (message.isBlank()) "Punkty zapisane." else "Punkty zapisane: $message")
                }
            }

            override fun onError(action: String, message: String) {
                viewModelScope.launch {
                    _uiState.update {
                        it.copy(
                            isSavingScore = false,
                            connectionNote = "Błąd live"
                        )
                    }
                    publishMessage(
                        if (action.isBlank()) {
                            message.ifBlank { "Błąd komunikacji WebSocket." }
                        } else {
                            "$action: ${message.ifBlank { "Błąd operacji." }}"
                        }
                    )
                }
            }

            override fun onDisconnected(reason: String) {
                viewModelScope.launch {
                    _uiState.update {
                        it.copy(
                            wsConnected = false,
                            connectionNote = "Rozłączono live"
                        )
                    }
                    publishMessage("Połączenie live przerwane: $reason")
                    scheduleReconnect()
                    if (_uiState.value.participants.isEmpty()) {
                        loadDataFromRest(showLoader = true)
                    }
                }
            }
        }
    )

    private val _uiState = MutableStateFlow(JurorUiState())
    val uiState = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    private var reconnectJob: Job? = null
    private var shouldReconnect: Boolean = true

    init {
        bootstrap()
    }

    fun onFirstNameChanged(value: String) {
        _uiState.update { it.copy(firstNameInput = value) }
    }

    fun onSurNameChanged(value: String) {
        _uiState.update { it.copy(surNameInput = value) }
    }

    fun onAdminPasswordChanged(value: String) {
        _uiState.update { it.copy(adminPasswordInput = value) }
    }

    fun checkLoginStatus() {
        viewModelScope.launch {
            when (val statusResult = repository.checkJurorLoginStatus()) {
                is RepoResult.Ok -> {
                    _uiState.update {
                        it.copy(
                            loginEnabled = statusResult.data,
                            loginStatusLoaded = true
                        )
                    }
                }

                is RepoResult.Err -> {
                    _uiState.update {
                        it.copy(
                            loginEnabled = true,
                            loginStatusLoaded = true
                        )
                    }
                }
            }
        }
    }

    fun login() {
        viewModelScope.launch {
            val state = _uiState.value
            val firstName = state.firstNameInput.trim()
            val surName = state.surNameInput.trim()
            val adminPassword = state.adminPasswordInput

            if (!state.loginEnabled) {
                publishMessage("Logowanie jurorów jest obecnie wyłączone przez administratora.")
                return@launch
            }

            if (firstName.isBlank() || surName.isBlank() || adminPassword.isBlank()) {
                publishMessage("Uzupełnij: imię, nazwisko i hasło administratora.")
                return@launch
            }

            _uiState.update { it.copy(isLoginInProgress = true) }

            when (val loginResult = repository.login(firstName, surName, adminPassword)) {
                is RepoResult.Ok -> {
                    shouldReconnect = true
                    onSessionReady(loginResult.data)
                }

                is RepoResult.Err -> {
                    _uiState.update { it.copy(isLoginInProgress = false) }
                    publishMessage(loginResult.message)
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            shouldReconnect = false
            reconnectJob?.cancel()
            websocketClient.disconnect()
            repository.clearSession()

            _uiState.update {
                JurorUiState(
                    isStarting = false,
                    firstNameInput = it.firstNameInput,
                    surNameInput = it.surNameInput,
                    adminPasswordInput = "",
                    loginEnabled = it.loginEnabled,
                    loginStatusLoaded = it.loginStatusLoaded
                )
            }

            checkLoginStatus()
        }
    }

    fun refreshData() {
        if (_uiState.value.isAuthenticated) {
            loadDataFromRest(showLoader = true)
            if (!_uiState.value.wsConnected) {
                connectWebSocket()
            }
        } else {
            checkLoginStatus()
        }
    }

    fun selectParticipant(participantId: String) {
        _uiState.update { it.copy(selectedParticipantId = participantId) }
    }

    fun updateDraftScore(participantId: String, criterionId: String, point: Int) {
        val criterion = _uiState.value.criteria.firstOrNull { it.id == criterionId } ?: return
        val safePoint = point.coerceIn(0, criterion.maxPoints)
        val key = scoreKey(participantId, criterionId)

        _uiState.update {
            it.copy(
                draftScores = it.draftScores.toMutableMap().apply { put(key, safePoint) }
            )
        }
    }

    fun submitScore(participantId: String, criterionId: String) {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.isSavingScore) return@launch

            val criterion = state.criteria.firstOrNull { it.id == criterionId }
            if (criterion == null) {
                publishMessage("Kryterium nie istnieje.")
                return@launch
            }

            val key = scoreKey(participantId, criterionId)
            val point = state.draftScores[key] ?: state.scores[key] ?: 0

            if (point !in 0..criterion.maxPoints) {
                publishMessage("Punkty muszą być w zakresie 0..${criterion.maxPoints}.")
                return@launch
            }

            _uiState.update { it.copy(isSavingScore = true) }

            val sentByWebsocket = state.wsConnected && websocketClient.sendUpsertMyScore(
                participantId = participantId,
                criterionId = criterionId,
                point = point
            )

            if (sentByWebsocket) {
                _uiState.update {
                    val updatedScores = it.scores.toMutableMap().apply { put(key, point) }
                    val updatedDrafts = it.draftScores.toMutableMap().apply { remove(key) }
                    it.copy(
                        scores = updatedScores,
                        draftScores = updatedDrafts,
                        isSavingScore = false
                    )
                }
                return@launch
            }

            when (
                val saveResult = repository.upsertMyScore(
                    UpsertMyScoreRequest(
                        participantId = participantId,
                        criterionId = criterionId,
                        point = point
                    )
                )
            ) {
                is RepoResult.Ok -> {
                    _uiState.update {
                        val updatedScores = it.scores.toMutableMap().apply {
                            put(key, saveResult.data.point)
                        }
                        val updatedDrafts = it.draftScores.toMutableMap().apply { remove(key) }
                        it.copy(
                            scores = updatedScores,
                            draftScores = updatedDrafts,
                            isSavingScore = false
                        )
                    }
                    publishMessage("Punkty zapisane.")
                }

                is RepoResult.Err -> {
                    _uiState.update { it.copy(isSavingScore = false) }
                    handleRepoError(saveResult)
                }
            }
        }
    }

    override fun onCleared() {
        shouldReconnect = false
        reconnectJob?.cancel()
        websocketClient.disconnect()
        super.onCleared()
    }

    private fun bootstrap() {
        viewModelScope.launch {
            _uiState.update { it.copy(isStarting = true) }

            val session = repository.readSession()
            if (session == null || session.role != ROLE_JUROR) {
                _uiState.update { it.copy(isStarting = false) }
                checkLoginStatus()
                return@launch
            }

            val refreshed = repository.refreshAccessToken()
            if (!refreshed) {
                repository.clearSession()
                _uiState.update { it.copy(isStarting = false) }
                checkLoginStatus()
                return@launch
            }

            val refreshedSession = repository.readSession()
            if (refreshedSession == null) {
                _uiState.update { it.copy(isStarting = false) }
                checkLoginStatus()
                return@launch
            }

            shouldReconnect = true
            onSessionReady(refreshedSession)
        }
    }

    private suspend fun onSessionReady(session: JurorSession) {
        _uiState.update {
            it.copy(
                isStarting = false,
                isAuthenticated = true,
                isLoginInProgress = false,
                jurorDisplayName = "${session.jurorName} ${session.jurorSurname}",
                adminPasswordInput = "",
                isDataLoading = true
            )
        }

        connectWebSocket()
        loadDataFromRest(showLoader = true)
    }

    private fun connectWebSocket() {
        viewModelScope.launch {
            if (!shouldReconnect) return@launch
            val session = repository.readSession() ?: return@launch
            websocketClient.connect(session.accessToken)
        }
    }

    private fun loadDataFromRest(showLoader: Boolean) {
        viewModelScope.launch {
            if (showLoader) {
                _uiState.update { it.copy(isDataLoading = true) }
            }

            when (val dataResult = repository.loadAllData()) {
                is RepoResult.Ok -> {
                    applySnapshot(dataResult.data)
                    _uiState.update { it.copy(isDataLoading = false) }
                }

                is RepoResult.Err -> {
                    _uiState.update { it.copy(isDataLoading = false) }
                    handleRepoError(dataResult)
                }
            }
        }
    }

    private suspend fun applySnapshot(snapshot: JurorSnapshot) {
        val scoreMap = snapshot.scores.associate { score ->
            scoreKey(score.participantId, score.criterionId) to score.point
        }

        _uiState.update { state ->
            val selected = state.selectedParticipantId
            val selectedStillExists = snapshot.participants.any { it.id == selected }
            state.copy(
                participants = snapshot.participants,
                criteria = snapshot.criteria,
                scores = scoreMap,
                draftScores = state.draftScores.filterKeys { key ->
                    val parts = key.split('|')
                    if (parts.size != 2) return@filterKeys false
                    snapshot.participants.any { it.id == parts[0] } &&
                        snapshot.criteria.any { it.id == parts[1] }
                },
                selectedParticipantId = if (selectedStillExists) {
                    selected
                } else {
                    snapshot.participants.firstOrNull()?.id
                }
            )
        }
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect) return
        if (reconnectJob?.isActive == true) return

        reconnectJob = viewModelScope.launch {
            delay(1_500)
            if (!shouldReconnect) return@launch

            val refreshed = repository.refreshAccessToken()
            if (!refreshed) {
                forceLogout("Sesja wygasła. Zaloguj się ponownie.")
                return@launch
            }

            connectWebSocket()
        }
    }

    private fun sendInitMessage() {
        websocketClient.sendInit()
    }

    private fun handleRepoError(error: RepoResult.Err) {
        if (error.code == 401) {
            viewModelScope.launch {
                forceLogout("Sesja wygasła. Zaloguj się ponownie.")
            }
            return
        }

        viewModelScope.launch {
            publishMessage(error.message)
        }
    }

    private suspend fun forceLogout(message: String) {
        shouldReconnect = false
        reconnectJob?.cancel()
        websocketClient.disconnect()
        repository.clearSession()

        _uiState.update {
            JurorUiState(
                isStarting = false,
                firstNameInput = it.firstNameInput,
                surNameInput = it.surNameInput,
                adminPasswordInput = "",
                loginEnabled = it.loginEnabled,
                loginStatusLoaded = it.loginStatusLoaded
            )
        }

        publishMessage(message)
        checkLoginStatus()
    }

    private suspend fun publishMessage(message: String) {
        if (message.isNotBlank()) {
            _messages.emit(message)
        }
    }
}
