package com.example.ekh2026.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ekh2026.BuildConfig
import com.example.ekh2026.data.model.CriterionDto
import com.example.ekh2026.data.model.scoreKey
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

@Composable
fun JurorAppScreen(viewModel: JurorViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.messages.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            if (state.isAuthenticated) {
                JurorTopBar(
                    jurorName = state.jurorDisplayName,
                    wsConnected = state.wsConnected,
                    onRefresh = viewModel::refreshData,
                    onLogout = viewModel::logout
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            when {
                state.isStarting -> FullscreenLoading("Uruchamianie aplikacji jurora...")
                !state.isAuthenticated -> LoginScreen(
                    state = state,
                    onFirstNameChanged = viewModel::onFirstNameChanged,
                    onSurNameChanged = viewModel::onSurNameChanged,
                    onAdminPasswordChanged = viewModel::onAdminPasswordChanged,
                    onLogin = viewModel::login,
                    onRefreshLoginStatus = viewModel::checkLoginStatus
                )

                else -> ScoresScreen(
                    state = state,
                    onSelectParticipant = viewModel::selectParticipant,
                    onScoreChanged = viewModel::updateDraftScore,
                    onSaveScore = viewModel::submitScore,
                    onRefresh = viewModel::refreshData
                )
            }
        }
    }
}

@Composable
private fun FullscreenLoading(label: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(label)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JurorTopBar(
    jurorName: String,
    wsConnected: Boolean,
    onRefresh: () -> Unit,
    onLogout: () -> Unit
) {
    CenterAlignedTopAppBar(
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Panel jurora",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = jurorName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        actions = {
            ConnectionBadge(wsConnected = wsConnected)
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onRefresh) { Text("Odśwież") }
            TextButton(onClick = onLogout) { Text("Wyloguj") }
        }
    )
}

@Composable
private fun ConnectionBadge(wsConnected: Boolean) {
    val bg = if (wsConnected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val fg = if (wsConnected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(fg)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (wsConnected) "Live" else "Offline",
            style = MaterialTheme.typography.labelMedium,
            color = fg
        )
    }
}

@Composable
private fun LoginScreen(
    state: JurorUiState,
    onFirstNameChanged: (String) -> Unit,
    onSurNameChanged: (String) -> Unit,
    onAdminPasswordChanged: (String) -> Unit,
    onLogin: () -> Unit,
    onRefreshLoginStatus: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .width(560.dp),
            colors = CardDefaults.outlinedCardColors()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Logowanie jurora",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Wpisz imię, nazwisko i hasło administratora.",
                    style = MaterialTheme.typography.bodyMedium
                )

                if (!state.loginEnabled && state.loginStatusLoaded) {
                    Text(
                        text = "Logowanie jurorów jest chwilowo wyłączone przez administratora.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                OutlinedTextField(
                    value = state.firstNameInput,
                    onValueChange = onFirstNameChanged,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Imię") }
                )

                OutlinedTextField(
                    value = state.surNameInput,
                    onValueChange = onSurNameChanged,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nazwisko") }
                )

                OutlinedTextField(
                    value = state.adminPasswordInput,
                    onValueChange = onAdminPasswordChanged,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Hasło administratora") },
                    visualTransformation = PasswordVisualTransformation()
                )

                Text(
                    text = "API: ${BuildConfig.API_BASE_URL}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onLogin,
                        enabled = state.loginEnabled && !state.isLoginInProgress,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (state.isLoginInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Logowanie...")
                        } else {
                            Text("Zaloguj")
                        }
                    }

                    TextButton(onClick = onRefreshLoginStatus) {
                        Text("Sprawdź status")
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoresScreen(
    state: JurorUiState,
    onSelectParticipant: (String) -> Unit,
    onScoreChanged: (participantId: String, criterionId: String, point: Int) -> Unit,
    onSaveScore: (participantId: String, criterionId: String) -> Unit,
    onRefresh: () -> Unit
) {
    if (state.isDataLoading && state.participants.isEmpty()) {
        FullscreenLoading("Pobieranie uczestników i kryteriów...")
        return
    }

    if (state.participants.isEmpty() || state.criteria.isEmpty()) {
        EmptyScoresState(onRefresh = onRefresh)
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTablet = maxWidth >= 900.dp
        if (isTablet) {
            TabletLayout(
                state = state,
                onSelectParticipant = onSelectParticipant,
                onScoreChanged = onScoreChanged,
                onSaveScore = onSaveScore
            )
        } else {
            PhoneLayout(
                state = state,
                onSelectParticipant = onSelectParticipant,
                onScoreChanged = onScoreChanged,
                onSaveScore = onSaveScore
            )
        }
    }
}

@Composable
private fun EmptyScoresState(onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Brak danych do oceniania",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Sprawdź połączenie lub odśwież dane.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRefresh) {
            Text("Odśwież dane")
        }
    }
}

@Composable
private fun TabletLayout(
    state: JurorUiState,
    onSelectParticipant: (String) -> Unit,
    onScoreChanged: (participantId: String, criterionId: String, point: Int) -> Unit,
    onSaveScore: (participantId: String, criterionId: String) -> Unit
) {
    val selectedId = state.selectedParticipantId ?: state.participants.first().id
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedCard(
            modifier = Modifier
                .width(320.dp)
                .fillMaxHeight()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Uczestnicy",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.participants, key = { it.id }) { participant ->
                        val isSelected = participant.id == selectedId
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectParticipant(participant.id) },
                            colors = if (isSelected) {
                                CardDefaults.outlinedCardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            } else {
                                CardDefaults.outlinedCardColors()
                            }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "${participant.name} ${participant.surname}",
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = participant.schoolName,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        CriteriaPanel(
            modifier = Modifier.weight(1f),
            state = state,
            participantId = selectedId,
            onScoreChanged = onScoreChanged,
            onSaveScore = onSaveScore
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneLayout(
    state: JurorUiState,
    onSelectParticipant: (String) -> Unit,
    onScoreChanged: (participantId: String, criterionId: String, point: Int) -> Unit,
    onSaveScore: (participantId: String, criterionId: String) -> Unit
) {
    val selectedId = state.selectedParticipantId ?: state.participants.first().id
    val selectedParticipant = state.participants.firstOrNull { it.id == selectedId }
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Oceniany uczestnik",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                        readOnly = true,
                        value = selectedParticipant?.let { "${it.name} ${it.surname}" } ?: "",
                        onValueChange = {},
                        label = { Text("Uczestnik") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                    )

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        state.participants.forEach { participant ->
                            DropdownMenuItem(
                                text = { Text("${participant.name} ${participant.surname}") },
                                onClick = {
                                    onSelectParticipant(participant.id)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        CriteriaPanel(
            modifier = Modifier.fillMaxSize(),
            state = state,
            participantId = selectedId,
            onScoreChanged = onScoreChanged,
            onSaveScore = onSaveScore
        )
    }
}

@Composable
private fun CriteriaPanel(
    modifier: Modifier,
    state: JurorUiState,
    participantId: String,
    onScoreChanged: (participantId: String, criterionId: String, point: Int) -> Unit,
    onSaveScore: (participantId: String, criterionId: String) -> Unit
) {
    val groupedCriteria = remember(state.criteria) {
        state.criteria
            .sortedWith(compareBy<CriterionDto> { it.categoryName }.thenBy { it.name })
            .groupBy { it.categoryName.ifBlank { "Pozostałe" } }
    }

    OutlinedCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Text(
                text = "Punktacja",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Przesuń suwak i puść, aby zapisać ocenę.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                groupedCriteria.forEach { (categoryName, criteriaInCategory) ->
                    item(key = "category-$categoryName") {
                        Text(
                            text = categoryName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    items(criteriaInCategory, key = { it.id }) { criterion ->
                        val currentPoint = currentScore(
                            state = state,
                            participantId = participantId,
                            criterionId = criterion.id
                        )

                        CriterionScoreRow(
                            criterion = criterion,
                            point = currentPoint,
                            isSaving = state.isSavingScore,
                            onPointChanged = { value ->
                                onScoreChanged(participantId, criterion.id, value)
                            },
                            onCommit = {
                                onSaveScore(participantId, criterion.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CriterionScoreRow(
    criterion: CriterionDto,
    point: Int,
    isSaving: Boolean,
    onPointChanged: (Int) -> Unit,
    onCommit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = criterion.name,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$point / ${criterion.maxPoints}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        Slider(
            value = point.toFloat(),
            onValueChange = { rawValue ->
                onPointChanged(rawValue.roundToInt())
            },
            valueRange = 0f..criterion.maxPoints.toFloat().coerceAtLeast(0f),
            steps = (criterion.maxPoints - 1).coerceAtLeast(0),
            onValueChangeFinished = onCommit,
            enabled = !isSaving && criterion.maxPoints > 0,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = if (isSaving) "Zapisywanie..." else "Zakres: 0..${criterion.maxPoints}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

private fun currentScore(
    state: JurorUiState,
    participantId: String,
    criterionId: String
): Int {
    val key = scoreKey(participantId, criterionId)
    return state.draftScores[key] ?: state.scores[key] ?: 0
}
