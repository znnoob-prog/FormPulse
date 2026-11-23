package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.FormDefinition
import com.example.data.model.FormQuestion
import com.example.data.model.FormResponse
import com.example.data.model.QuestionType
import com.example.ui.components.ChartSlice
import com.example.ui.components.ConnectivityStatusPill
import com.example.ui.components.FormAccentHeaderBar
import com.example.ui.components.HistogramBar
import com.example.ui.components.ProgressBarMetric
import com.example.ui.components.PulseDistributionHistogram
import com.example.ui.components.PulseDonutChart
import com.example.ui.components.PulseHorizontalProgressBars
import com.example.ui.theme.ChartPalette
import com.example.ui.theme.PulseAmber
import com.example.ui.theme.PulseCoral
import com.example.ui.theme.PulseMint
import com.example.ui.viewmodel.FormPulseViewModel
import com.example.ui.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormAnalyticsScreen(
    formId: String,
    viewModel: FormPulseViewModel,
    onBack: () -> Unit,
    onOpenResponder: () -> Unit
) {
    LaunchedEffect(formId) {
        viewModel.selectForm(formId)
    }

    val formState by viewModel.activeFormState.collectAsStateWithLifecycle()
    val responsesState by viewModel.activeResponsesState.collectAsStateWithLifecycle()
    val isSystemOnline by viewModel.isSystemOnline.collectAsStateWithLifecycle()
    val isOfflineForced by viewModel.isOfflineModeForced.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Summary, 1 = Individual

    val form = (formState as? UiState.Success)?.data
    val responses = (responsesState as? UiState.Success)?.data.orEmpty()
    val pendingCount = responses.count { it.hasPendingWrites }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = form?.title ?: "Live Responses",
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
                        pendingCount = pendingCount,
                        onToggleOfflineMode = { viewModel.toggleOfflineMode() }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            when {
                formState is UiState.Loading || responsesState is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                form == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Form not found", style = MaterialTheme.typography.titleMedium)
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            AnalyticsHeaderCard(
                                form = form,
                                responses = responses,
                                pendingCount = pendingCount,
                                onOpenResponder = onOpenResponder
                            )
                        }

                        item {
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                SegmentedButton(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                    modifier = Modifier.testTag("analytics_summary_tab")
                                ) {
                                    Text("Summary Charts")
                                }
                                SegmentedButton(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                    modifier = Modifier.testTag("analytics_individual_tab")
                                ) {
                                    Text("Individual (${responses.size})")
                                }
                            }
                        }

                        if (responses.isEmpty()) {
                            item {
                                EmptyResponsesCard(onOpenResponder = onOpenResponder)
                            }
                        } else if (selectedTab == 0) {
                            items(form.questions, key = { it.id }) { question ->
                                QuestionAnalyticsCard(
                                    question = question,
                                    responses = responses,
                                    accentColor = form.accentColor.color
                                )
                            }
                        } else {
                            items(responses, key = { it.id }) { response ->
                                IndividualResponseCard(
                                    form = form,
                                    response = response
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsHeaderCard(
    form: FormDefinition,
    responses: List<FormResponse>,
    pendingCount: Int,
    onOpenResponder: () -> Unit
) {
    val avgSeconds = remember(responses) {
        if (responses.isEmpty()) 0L
        else responses.map { it.completionSeconds }.average().toLong()
    }
    val syncedCount = responses.size - pendingCount

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            FormAccentHeaderBar(accentColor = form.accentColor.color)
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Real-Time Pulse",
                            style = MaterialTheme.typography.labelMedium,
                            color = form.accentColor.color
                        )
                        AnimatedContent(
                            targetState = responses.size,
                            label = "response_count_anim"
                        ) { count ->
                            Text(
                                text = "$count ${if (count == 1) "Response" else "Responses"}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Button(
                        onClick = onOpenResponder,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("analytics_respond_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Respond")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = PulseMint.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = PulseMint,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "$syncedCount Synced",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF047857)
                            )
                        }
                    }

                    Surface(
                        color = if (pendingCount > 0) PulseAmber.copy(alpha = 0.18f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (pendingCount > 0) Icons.Rounded.CloudSync else Icons.Rounded.Schedule,
                                contentDescription = null,
                                tint = if (pendingCount > 0) Color(0xFFB45309) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (pendingCount > 0) "$pendingCount Queued" else "${avgSeconds}s avg",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (pendingCount > 0) Color(0xFFB45309) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionAnalyticsCard(
    question: FormQuestion,
    responses: List<FormResponse>,
    accentColor: Color
) {
    val answers = remember(responses, question.id) {
        responses.mapNotNull { resp ->
            resp.answers.firstOrNull { it.questionId == question.id }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = question.questionType.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor
                )
                Text(
                    text = question.prompt,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            when (question.questionType) {
                QuestionType.MULTIPLE_CHOICE, QuestionType.YES_NO_SWIPE -> {
                    val options = if (question.options.isNotEmpty()) {
                        question.options
                    } else if (question.questionType == QuestionType.YES_NO_SWIPE) {
                        listOf("Yes", "No")
                    } else emptyList()

                    val counts = options.mapIndexed { idx, opt ->
                        val color = if (question.questionType == QuestionType.YES_NO_SWIPE) {
                            if (idx == 0) PulseMint else PulseCoral
                        } else {
                            ChartPalette[idx % ChartPalette.size]
                        }
                        ChartSlice(
                            label = opt,
                            count = answers.count { it.textValue == opt },
                            color = color
                        )
                    }
                    var selectedSlice by remember { mutableStateOf<Int?>(null) }
                    PulseDonutChart(
                        slices = counts,
                        totalResponses = answers.count { it.textValue.isNotBlank() },
                        selectedIndex = selectedSlice,
                        onSelectSlice = { selectedSlice = it }
                    )
                }

                QuestionType.CHECKBOXES -> {
                    val metrics = question.options.mapIndexed { idx, opt ->
                        ProgressBarMetric(
                            label = opt,
                            count = answers.count { opt in it.selectedOptions },
                            color = ChartPalette[idx % ChartPalette.size]
                        )
                    }.sortedByDescending { it.count }
                    PulseHorizontalProgressBars(
                        items = metrics,
                        totalResponses = responses.size
                    )
                }

                QuestionType.LINEAR_SCALE, QuestionType.STAR_RATING -> {
                    val min = question.scaleMin.toInt().coerceAtLeast(1)
                    val max = question.scaleMax.toInt().coerceAtLeast(min + 1)
                    val numericValues = answers.map { it.numericValue.toInt() }.filter { it in min..max }
                    val avg = if (numericValues.isEmpty()) 0.0 else numericValues.average()
                    val bars = (min..max).map { value ->
                        HistogramBar(
                            bucketLabel = if (question.questionType == QuestionType.STAR_RATING) "$value★" else "$value",
                            count = numericValues.count { it == value }
                        )
                    }
                    PulseDistributionHistogram(
                        bars = bars,
                        average = avg,
                        maxScale = max,
                        accentColor = accentColor,
                        isStarQuestion = question.questionType == QuestionType.STAR_RATING
                    )
                }

                QuestionType.SHORT_TEXT -> {
                    val textEntries = responses.mapNotNull { resp ->
                        val ans = resp.answers.firstOrNull { it.questionId == question.id }
                        if (ans != null && ans.textValue.isNotBlank()) {
                            Triple(resp.respondentName, ans.textValue, resp.hasPendingWrites)
                        } else null
                    }
                    if (textEntries.isEmpty()) {
                        Text(
                            text = "No written answers yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            textEntries.take(8).forEach { (author, text, pending) ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = author,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = if (pending) "Queued Offline" else "Synced",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (pending) Color(0xFFB45309) else PulseMint
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = text,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
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
private fun IndividualResponseCard(
    form: FormDefinition,
    response: FormResponse
) {
    val formatter = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    val dateLabel = response.submittedAt?.toDate()?.let { formatter.format(it) } ?: "Pending"

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = response.respondentName.ifBlank { "Anonymous" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$dateLabel  •  ${response.completionSeconds}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = if (response.hasPendingWrites) PulseAmber.copy(alpha = 0.2f)
                    else PulseMint.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = if (response.hasPendingWrites) "Queued Offline" else "Synced",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (response.hasPendingWrites) Color(0xFFB45309) else Color(0xFF047857),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

            form.questions.forEach { q ->
                val ans = response.answers.firstOrNull { it.questionId == q.id }
                val formatted = when (q.questionType) {
                    QuestionType.CHECKBOXES -> ans?.selectedOptions?.joinToString(", ").orEmpty()
                    QuestionType.LINEAR_SCALE -> ans?.numericValue?.takeIf { it > 0 }?.toString().orEmpty()
                    QuestionType.STAR_RATING -> ans?.numericValue?.takeIf { it > 0 }?.let { "$it ★" }.orEmpty()
                    else -> ans?.textValue.orEmpty()
                }.ifBlank { "Skipped" }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = q.prompt,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatted,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyResponsesCard(onOpenResponder: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.BarChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            )
            Text(
                text = "Waiting for your first response",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Submit a response (online or in Offline Mode) to see live charts animate immediately.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onOpenResponder,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("empty_analytics_respond_button")
            ) {
                Text("Open Gesture Responder")
            }
        }
    }
}
