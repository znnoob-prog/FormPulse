package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.FormDefinition
import com.example.data.model.FormQuestion
import com.example.data.model.QuestionAnswer
import com.example.data.model.QuestionType
import com.example.data.model.SubmitOutcome
import com.example.ui.components.ConnectivityStatusPill
import com.example.ui.components.FormAccentHeaderBar
import com.example.ui.theme.PulseAmber
import com.example.ui.theme.PulseCoral
import com.example.ui.theme.PulseMint
import com.example.ui.viewmodel.FormPulseViewModel
import com.example.ui.viewmodel.UiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormResponderScreen(
    formId: String,
    defaultRespondentName: String,
    viewModel: FormPulseViewModel,
    onBack: () -> Unit,
    onOpenAnalytics: () -> Unit
) {
    LaunchedEffect(formId) {
        viewModel.selectForm(formId)
    }

    val formState by viewModel.activeFormState.collectAsStateWithLifecycle()
    val isSystemOnline by viewModel.isSystemOnline.collectAsStateWithLifecycle()
    val isOfflineForced by viewModel.isOfflineModeForced.collectAsStateWithLifecycle()
    val effectiveOnline = isSystemOnline && !isOfflineForced

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    val form = (formState as? UiState.Success)?.data
    val answersMap = remember(formId) { mutableStateMapOf<String, QuestionAnswer>() }
    var respondentName by remember(formId) { mutableStateOf(defaultRespondentName) }
    var isDeckMode by remember { mutableStateOf(true) }
    var autoAdvance by remember { mutableStateOf(true) }
    var draftRestored by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var submitOutcome by remember(formId) { mutableStateOf<SubmitOutcome?>(null) }
    var startTimeMs by remember(formId) { mutableLongStateOf(System.currentTimeMillis()) }
    var initialPageRestored by remember(formId) { mutableIntStateOf(0) }

    LaunchedEffect(formId) {
        val draft = viewModel.loadDraft(formId)
        if (draft != null) {
            val decoded = draft.decodeAnswers()
            if (decoded.isNotEmpty()) {
                answersMap.putAll(decoded)
                if (draft.respondentName.isNotBlank()) {
                    respondentName = draft.respondentName
                }
                initialPageRestored = draft.currentPage.coerceAtLeast(0)
                draftRestored = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = form?.title ?: "Respond",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    ConnectivityStatusPill(
                        isSystemOnline = isSystemOnline,
                        isOfflineForced = isOfflineForced,
                        pendingCount = 0,
                        onToggleOfflineMode = { viewModel.toggleOfflineMode() }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            when {
                formState is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                form == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Form not found", style = MaterialTheme.typography.titleMedium)
                    }
                }
                submitOutcome != null -> {
                    SubmissionSuccessView(
                        form = form,
                        outcome = submitOutcome!!,
                        elapsedSeconds = ((System.currentTimeMillis() - startTimeMs) / 1000L).coerceAtLeast(1L),
                        onViewCharts = onOpenAnalytics,
                        onSubmitAnother = {
                            answersMap.clear()
                            draftRestored = false
                            submitOutcome = null
                            startTimeMs = System.currentTimeMillis()
                        },
                        onBackToHub = onBack
                    )
                }
                else -> {
                    val questions = form.questions
                    val pagerState = rememberPagerState(
                        initialPage = initialPageRestored.coerceIn(0, (questions.size - 1).coerceAtLeast(0)),
                        pageCount = { questions.size.coerceAtLeast(1) }
                    )

                    fun persistCurrentDraft() {
                        viewModel.saveDraft(
                            formId = form.id,
                            respondentName = respondentName,
                            answers = answersMap.toMap(),
                            currentPage = pagerState.currentPage
                        )
                    }

                    fun updateAnswer(question: FormQuestion, answer: QuestionAnswer, shouldAutoAdvance: Boolean) {
                        answersMap[question.id] = answer
                        persistCurrentDraft()
                        if (shouldAutoAdvance && autoAdvance && isDeckMode && pagerState.currentPage < questions.lastIndex) {
                            scope.launch {
                                delay(220)
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    }

                    fun handleSubmit() {
                        val missingIndex = questions.indexOfFirst { q ->
                            q.required && !(answersMap[q.id]?.isAnswered() ?: false)
                        }
                        if (missingIndex >= 0) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch {
                                if (isDeckMode) {
                                    pagerState.animateScrollToPage(missingIndex)
                                }
                                snackbarHostState.showSnackbar("Please answer required question ${missingIndex + 1}")
                            }
                            return
                        }
                        isSubmitting = true
                        val elapsed = ((System.currentTimeMillis() - startTimeMs) / 1000L).coerceIn(1L, 86400L)
                        val orderedAnswers = questions.map { q ->
                            answersMap[q.id] ?: QuestionAnswer(
                                questionId = q.id,
                                questionType = q.type
                            )
                        }
                        scope.launch {
                            val result = viewModel.submitResponse(
                                formId = form.id,
                                respondentName = respondentName,
                                answers = orderedAnswers,
                                completionSeconds = elapsed
                            )
                            isSubmitting = false
                            result.onSuccess { outcome ->
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                submitOutcome = outcome
                            }.onFailure { err ->
                                snackbarHostState.showSnackbar(err.localizedMessage ?: "Failed to submit")
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .widthIn(max = 680.dp)
                            .fillMaxSize()
                            .imePadding()
                    ) {
                        // Offline Banner & Draft Indicator
                        AnimatedVisibility(visible = !effectiveOnline || draftRestored) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (!effectiveOnline) {
                                    Surface(
                                        color = PulseAmber.copy(alpha = 0.18f),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.CloudOff,
                                                contentDescription = null,
                                                tint = Color(0xFFB45309),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Offline: submissions queue locally & sync automatically",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF92400E)
                                            )
                                        }
                                    }
                                }
                                if (draftRestored) {
                                    Surface(
                                        color = PulseMint.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(
                                            text = "Draft Restored",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF047857),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Familiar Google Forms Header + Progress Strip
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Column {
                                FormAccentHeaderBar(accentColor = form.accentColor.color)
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (form.description.isNotBlank()) {
                                        Text(
                                            text = form.description,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = respondentName,
                                            onValueChange = {
                                                respondentName = it.take(80)
                                                persistCurrentDraft()
                                            },
                                            label = { Text("Your name (optional)") },
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        )
                                        FilterChip(
                                            selected = isDeckMode,
                                            onClick = { isDeckMode = !isDeckMode },
                                            label = { Text(if (isDeckMode) "Deck" else "List") },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = if (isDeckMode) Icons.Rounded.Swipe else Icons.Rounded.ViewAgenda,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            modifier = Modifier.testTag("responder_mode_toggle")
                                        )
                                    }

                                    val answeredCount = questions.count { q ->
                                        answersMap[q.id]?.isAnswered() == true
                                    }
                                    val progress by animateFloatAsState(
                                        targetValue = (answeredCount.toFloat() / questions.size.coerceAtLeast(1).toFloat()),
                                        label = "form_progress"
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        LinearProgressIndicator(
                                            progress = { progress },
                                            color = form.accentColor.color,
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                            strokeCap = StrokeCap.Round,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(7.dp)
                                        )
                                        Text(
                                            text = "$answeredCount/${questions.size}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        if (isDeckMode) {
                            val currentQuestion = questions.getOrNull(pagerState.currentPage)
                            val allowPagerSwipe = currentQuestion?.questionType != QuestionType.YES_NO_SWIPE

                            HorizontalPager(
                                state = pagerState,
                                userScrollEnabled = allowPagerSwipe,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                pageSpacing = 12.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) { pageIndex ->
                                val q = questions[pageIndex]
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    QuestionFocusCard(
                                        index = pageIndex,
                                        total = questions.size,
                                        question = q,
                                        answer = answersMap[q.id],
                                        accentColor = form.accentColor.color,
                                        onAnswerChanged = { ans, advance ->
                                            updateAnswer(q, ans, advance)
                                        }
                                    )
                                }
                            }

                            // Bottom Gesture Navigation Bar
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 4.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            if (pagerState.currentPage > 0) {
                                                scope.launch {
                                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                                }
                                            }
                                        },
                                        enabled = pagerState.currentPage > 0,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.testTag("responder_prev_button")
                                    ) {
                                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Prev")
                                    }

                                    FilterChip(
                                        selected = autoAdvance,
                                        onClick = { autoAdvance = !autoAdvance },
                                        label = { Text("Auto-next") }
                                    )

                                    Spacer(modifier = Modifier.weight(1f))

                                    if (pagerState.currentPage < questions.lastIndex) {
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                                }
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.testTag("responder_next_button")
                                        ) {
                                            Text("Next")
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
                                        }
                                    } else {
                                        Button(
                                            onClick = { handleSubmit() },
                                            enabled = !isSubmitting,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.testTag("submit_response_button")
                                        ) {
                                            if (isSubmitting) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(18.dp),
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                Icon(Icons.Rounded.Check, contentDescription = null)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(stringResource(R.string.submit_response))
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Full Scrollable Overview List Mode
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .navigationBarsPadding(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                itemsIndexed(questions, key = { _, q -> q.id }) { idx, q ->
                                    QuestionFocusCard(
                                        index = idx,
                                        total = questions.size,
                                        question = q,
                                        answer = answersMap[q.id],
                                        accentColor = form.accentColor.color,
                                        onAnswerChanged = { ans, _ ->
                                            updateAnswer(q, ans, shouldAutoAdvance = false)
                                        }
                                    )
                                }
                                item {
                                    Button(
                                        onClick = { handleSubmit() },
                                        enabled = !isSubmitting,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(54.dp)
                                            .testTag("submit_response_list_button")
                                    ) {
                                        Text(stringResource(R.string.submit_response))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionFocusCard(
    index: Int,
    total: Int,
    question: FormQuestion,
    answer: QuestionAnswer?,
    accentColor: Color,
    onAnswerChanged: (QuestionAnswer, Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = accentColor.copy(alpha = 0.13f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Q${index + 1} of $total  •  ${question.questionType.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                if (question.required) {
                    Text(
                        text = "* Required",
                        style = MaterialTheme.typography.labelSmall,
                        color = PulseCoral
                    )
                }
            }

            Text(
                text = question.prompt,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = question.questionType.gestureHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when (question.questionType) {
                QuestionType.YES_NO_SWIPE -> {
                    YesNoSwipeInput(
                        question = question,
                        selectedValue = answer?.textValue.orEmpty(),
                        onSelect = { chosen ->
                            onAnswerChanged(
                                QuestionAnswer(
                                    questionId = question.id,
                                    questionType = question.type,
                                    textValue = chosen
                                ),
                                true
                            )
                        }
                    )
                }
                QuestionType.STAR_RATING -> {
                    StarRatingGestureInput(
                        maxStars = question.scaleMax.toInt().coerceIn(3, 5),
                        currentRating = answer?.numericValue?.toInt() ?: 0,
                        onRate = { rating ->
                            onAnswerChanged(
                                QuestionAnswer(
                                    questionId = question.id,
                                    questionType = question.type,
                                    numericValue = rating.toLong()
                                ),
                                true
                            )
                        }
                    )
                }
                QuestionType.LINEAR_SCALE -> {
                    LinearScaleGestureInput(
                        min = question.scaleMin.toInt().coerceAtLeast(1),
                        max = question.scaleMax.toInt().coerceAtLeast(5),
                        minLabel = question.minLabel,
                        maxLabel = question.maxLabel,
                        currentValue = answer?.numericValue?.toInt() ?: 0,
                        accentColor = accentColor,
                        onSelect = { value ->
                            onAnswerChanged(
                                QuestionAnswer(
                                    questionId = question.id,
                                    questionType = question.type,
                                    numericValue = value.toLong()
                                ),
                                false
                            )
                        }
                    )
                }
                QuestionType.MULTIPLE_CHOICE -> {
                    val options = question.options.ifEmpty { listOf("Option 1", "Option 2") }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.forEach { option ->
                            val isSelected = answer?.textValue == option
                            Surface(
                                onClick = {
                                    onAnswerChanged(
                                        QuestionAnswer(
                                            questionId = question.id,
                                            questionType = question.type,
                                            textValue = option
                                        ),
                                        true
                                    )
                                },
                                color = if (isSelected) accentColor.copy(alpha = 0.13f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                shape = MaterialTheme.shapes.medium,
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = option,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
                QuestionType.CHECKBOXES -> {
                    val options = question.options.ifEmpty { listOf("Item 1", "Item 2") }
                    val selectedList = answer?.selectedOptions.orEmpty()
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.forEach { option ->
                            val checked = option in selectedList
                            Surface(
                                onClick = {
                                    val updated = if (checked) selectedList - option else selectedList + option
                                    onAnswerChanged(
                                        QuestionAnswer(
                                            questionId = question.id,
                                            questionType = question.type,
                                            selectedOptions = updated
                                        ),
                                        false
                                    )
                                },
                                color = if (checked) accentColor.copy(alpha = 0.13f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                shape = MaterialTheme.shapes.medium,
                                border = BorderStroke(
                                    width = if (checked) 1.5.dp else 1.dp,
                                    color = if (checked) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = null
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = option,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
                QuestionType.SHORT_TEXT -> {
                    val currentText = answer?.textValue.orEmpty()
                    OutlinedTextField(
                        value = currentText,
                        onValueChange = { text ->
                            onAnswerChanged(
                                QuestionAnswer(
                                    questionId = question.id,
                                    questionType = question.type,
                                    textValue = text.take(500)
                                ),
                                false
                            )
                        },
                        placeholder = { Text("Write your answer here...") },
                        minLines = 3,
                        maxLines = 6,
                        supportingText = { Text("${currentText.length}/500") },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun YesNoSwipeInput(
    question: FormQuestion,
    selectedValue: String,
    onSelect: (String) -> Unit
) {
    val positiveLabel = question.options.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "Yes"
    val negativeLabel = question.options.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "No"
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val offsetX = remember(question.id) { Animatable(0f) }

    val dragFraction = (offsetX.value / 180f).coerceIn(-1f, 1f)
    val cardColor by animateColorAsState(
        targetValue = when {
            dragFraction > 0.25f || selectedValue == positiveLabel -> PulseMint.copy(alpha = 0.16f)
            dragFraction < -0.25f || selectedValue == negativeLabel -> PulseCoral.copy(alpha = 0.16f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        label = "swipe_bg"
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = cardColor,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(
                    1.5.dp,
                    when {
                        dragFraction > 0.25f || selectedValue == positiveLabel -> PulseMint
                        dragFraction < -0.25f || selectedValue == negativeLabel -> PulseCoral
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                    .graphicsLayer {
                        rotationZ = (offsetX.value / 24f).coerceIn(-10f, 10f)
                    }
                    .pointerInput(question.id, positiveLabel, negativeLabel) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    when {
                                        offsetX.value > 110f -> {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onSelect(positiveLabel)
                                        }
                                        offsetX.value < -110f -> {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onSelect(negativeLabel)
                                        }
                                    }
                                    offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                }
                            },
                            onDragCancel = {
                                scope.launch { offsetX.animateTo(0f) }
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                scope.launch {
                                    offsetX.snapTo((offsetX.value + dragAmount).coerceIn(-220f, 220f))
                                }
                            }
                        )
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = when {
                            dragFraction > 0.25f -> "→ $positiveLabel"
                            dragFraction < -0.25f -> "← $negativeLabel"
                            selectedValue.isNotBlank() -> "Selected: $selectedValue"
                            else -> "← Swipe Left or Right →"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            dragFraction > 0.25f || selectedValue == positiveLabel -> Color(0xFF047857)
                            dragFraction < -0.25f || selectedValue == negativeLabel -> Color(0xFFC2410C)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Or tap a quick choice below",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelect(negativeLabel)
                },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    width = if (selectedValue == negativeLabel) 2.dp else 1.dp,
                    color = if (selectedValue == negativeLabel) PulseCoral else MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.Close, contentDescription = null, tint = PulseCoral)
                Spacer(modifier = Modifier.width(6.dp))
                Text(negativeLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelect(positiveLabel)
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(positiveLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun StarRatingGestureInput(
    maxStars: Int,
    currentRating: Int,
    onRate: (Int) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    var containerWidthPx by remember { mutableFloatStateOf(1f) }
    var lastHapticStar by remember { mutableIntStateOf(currentRating) }

    fun starForOffsetX(x: Float): Int {
        val ratio = (x / containerWidthPx.coerceAtLeast(1f)).coerceIn(0f, 0.999f)
        return (ratio * maxStars).toInt() + 1
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { containerWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(maxStars) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, _ ->
                            val star = starForOffsetX(change.position.x)
                            if (star != lastHapticStar) {
                                lastHapticStar = star
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onRate(star)
                            }
                        }
                    )
                },
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (star in 1..maxStars) {
                val active = star <= currentRating
                val scale by animateFloatAsState(
                    targetValue = if (active) 1.15f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "star_scale_$star"
                )
                IconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onRate(star)
                    },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = if (active) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                        contentDescription = "$star stars",
                        tint = if (active) PulseAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(38.dp)
                            .scale(scale)
                    )
                }
            }
        }
        Text(
            text = if (currentRating > 0) "$currentRating of $maxStars stars" else "Slide or tap a star",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LinearScaleGestureInput(
    min: Int,
    max: Int,
    minLabel: String,
    maxLabel: String,
    currentValue: Int,
    accentColor: Color,
    onSelect: (Int) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val count = (max - min + 1).coerceAtLeast(2)
    var trackWidthPx by remember { mutableFloatStateOf(1f) }
    var lastHapticVal by remember { mutableIntStateOf(currentValue) }

    fun valueForX(x: Float): Int {
        val ratio = (x / trackWidthPx.coerceAtLeast(1f)).coerceIn(0f, 0.999f)
        return min + (ratio * count).toInt().coerceIn(0, count - 1)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { trackWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(min, max) {
                    detectHorizontalDragGestures { change, _ ->
                        val v = valueForX(change.position.x)
                        if (v != lastHapticVal) {
                            lastHapticVal = v
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(v)
                        }
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (v in min..max) {
                val selected = v == currentValue
                Surface(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(v)
                    },
                    color = if (selected) accentColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "$v",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (minLabel.isNotBlank() || maxLabel.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = minLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = maxLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SubmissionSuccessView(
    form: FormDefinition,
    outcome: SubmitOutcome,
    elapsedSeconds: Long,
    onViewCharts: () -> Unit,
    onSubmitAnother: () -> Unit,
    onBackToHub: () -> Unit
) {
    val isSynced = outcome == SubmitOutcome.SYNCED
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    color = if (isSynced) PulseMint.copy(alpha = 0.16f) else PulseAmber.copy(alpha = 0.18f),
                    shape = CircleShape,
                    modifier = Modifier.size(68.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isSynced) Icons.Rounded.CloudDone else Icons.Rounded.CloudQueue,
                            contentDescription = null,
                            tint = if (isSynced) PulseMint else Color(0xFFB45309),
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                Text(
                    text = if (isSynced) "Response Synced!" else "Saved to Offline Queue",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isSynced) {
                        "Submitted to \"${form.title}\" in ${elapsedSeconds}s. Real-time charts have updated."
                    } else {
                        "Your response to \"${form.title}\" (${elapsedSeconds}s) is safely stored locally and will auto-sync as soon as connection returns."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onViewCharts,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("post_submit_view_charts_button")
                ) {
                    Icon(Icons.Rounded.BarChart, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View Real-Time Charts")
                }

                OutlinedButton(
                    onClick = onSubmitAnother,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Submit Another Response")
                }

                OutlinedButton(
                    onClick = onBackToHub,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Back to Forms")
                }
            }
        }
    }
}
