package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.FormAccentColor
import com.example.data.model.FormCategory
import com.example.data.model.FormDefinition
import com.example.data.model.FormQuestion
import com.example.data.model.QuestionType
import com.example.ui.components.FormAccentHeaderBar
import com.example.ui.viewmodel.FormPulseViewModel
import com.example.ui.viewmodel.UiState
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormBuilderScreen(
    formId: String?,
    viewModel: FormPulseViewModel,
    onBack: () -> Unit,
    onSaved: (String) -> Unit
) {
    LaunchedEffect(formId) {
        viewModel.selectForm(formId)
    }

    val formState by viewModel.activeFormState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(FormCategory.SURVEY) }
    var accentColor by remember { mutableStateOf(FormAccentColor.INDIGO) }
    var acceptingResponses by remember { mutableStateOf(true) }
    val questions = remember { mutableStateListOf<FormQuestion>() }
    var initializedForId by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(formId, formState) {
        if (formId == null && initializedForId != "__new__") {
            initializedForId = "__new__"
            title = "Quick Pulse Survey"
            description = "Tap or swipe through these quick questions."
            category = FormCategory.SURVEY
            accentColor = FormAccentColor.INDIGO
            acceptingResponses = true
            questions.clear()
            questions.add(
                FormQuestion(
                    id = "q_${UUID.randomUUID().toString().take(6)}",
                    prompt = "How satisfied are you overall?",
                    type = QuestionType.STAR_RATING.name,
                    required = true,
                    scaleMin = 1L,
                    scaleMax = 5L
                )
            )
            questions.add(
                FormQuestion(
                    id = "q_${UUID.randomUUID().toString().take(6)}",
                    prompt = "Would you like a follow-up?",
                    type = QuestionType.YES_NO_SWIPE.name,
                    required = true,
                    options = listOf("Yes", "No")
                )
            )
        } else if (formId != null && formState is UiState.Success) {
            val existing = (formState as UiState.Success<FormDefinition?>).data
            if (existing != null && initializedForId != existing.id) {
                initializedForId = existing.id
                title = existing.title
                description = existing.description
                category = existing.formCategory
                accentColor = existing.accentColor
                acceptingResponses = existing.acceptingResponses
                questions.clear()
                questions.addAll(existing.questions)
            }
        }
    }

    fun handleSave() {
        if (title.isBlank()) {
            scope.launch { snackbarHostState.showSnackbar("Form title is required") }
            return
        }
        if (questions.isEmpty()) {
            scope.launch { snackbarHostState.showSnackbar("Add at least one question") }
            return
        }
        val invalidPrompt = questions.indexOfFirst { it.prompt.isBlank() }
        if (invalidPrompt >= 0) {
            scope.launch { snackbarHostState.showSnackbar("Question ${invalidPrompt + 1} needs a prompt") }
            return
        }

        isSaving = true
        scope.launch {
            val sanitizedQuestions = questions.take(25).mapIndexed { index, q ->
                val defaultOptions = when (q.questionType) {
                    QuestionType.MULTIPLE_CHOICE, QuestionType.CHECKBOXES ->
                        q.options.filter { it.isNotBlank() }.ifEmpty { listOf("Option 1", "Option 2") }
                    QuestionType.YES_NO_SWIPE ->
                        listOf(
                            q.options.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "Yes",
                            q.options.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "No"
                        )
                    else -> emptyList()
                }
                q.copy(
                    id = q.id.ifBlank { "q_${index + 1}" },
                    options = defaultOptions
                )
            }

            if (formId == null) {
                val newForm = FormDefinition(
                    title = title.trim(),
                    description = description.trim(),
                    category = category.name,
                    accentColorKey = accentColor.name,
                    acceptingResponses = acceptingResponses,
                    questions = sanitizedQuestions
                )
                val res = viewModel.createForm(newForm)
                isSaving = false
                res.onSuccess { createdId -> onSaved(createdId) }
                    .onFailure { err ->
                        snackbarHostState.showSnackbar(err.localizedMessage ?: "Failed to create form")
                    }
            } else {
                val updatedForm = FormDefinition(
                    id = formId,
                    title = title.trim(),
                    description = description.trim(),
                    category = category.name,
                    accentColorKey = accentColor.name,
                    acceptingResponses = acceptingResponses,
                    questions = sanitizedQuestions
                )
                val res = viewModel.updateForm(updatedForm)
                isSaving = false
                res.onSuccess { onSaved(formId) }
                    .onFailure { err ->
                        snackbarHostState.showSnackbar(err.localizedMessage ?: "Failed to update form")
                    }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (formId == null) "Form Builder" else "Edit Form") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = { handleSave() },
                        enabled = !isSaving,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("save_form_button")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save")
                        }
                    }
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
                .padding(innerPadding)
                .imePadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            FormAccentHeaderBar(accentColor = accentColor.color)
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = title,
                                    onValueChange = { title = it.take(120) },
                                    label = { Text("Form Title") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("builder_title_input")
                                )

                                OutlinedTextField(
                                    value = description,
                                    onValueChange = { description = it.take(500) },
                                    label = { Text("Description (optional)") },
                                    maxLines = 3,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Text(
                                    text = "Category",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FormCategory.entries.forEach { item ->
                                        FilterChip(
                                            selected = category == item,
                                            onClick = { category = item },
                                            label = { Text(item.label) }
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Accent",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        FormAccentColor.entries.forEach { colorOption ->
                                            val selected = accentColor == colorOption
                                            Box(
                                                modifier = Modifier
                                                    .size(30.dp)
                                                    .clip(CircleShape)
                                                    .background(colorOption.color)
                                                    .clickable { accentColor = colorOption },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (selected) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Check,
                                                        contentDescription = colorOption.label,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Open",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Switch(
                                            checked = acceptingResponses,
                                            onCheckedChange = { acceptingResponses = it }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "Questions (${questions.size}/25)  •  Swipe left on a card to remove",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                itemsIndexed(questions, key = { _, q -> q.id }) { index, question ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { target ->
                            if (target == SwipeToDismissBoxValue.EndToStart && questions.size > 1) {
                                val removedIndex = index
                                val removedItem = question
                                questions.removeAt(removedIndex)
                                scope.launch {
                                    val snackResult = snackbarHostState.showSnackbar(
                                        message = "Question removed",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (snackResult == SnackbarResult.ActionPerformed) {
                                        questions.add(removedIndex.coerceAtMost(questions.size), removedItem)
                                    }
                                }
                                true
                            } else {
                                false
                            }
                        }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(MaterialTheme.shapes.large)
                                    .background(MaterialTheme.colorScheme.errorContainer)
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete question",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    ) {
                        QuestionEditorCard(
                            index = index,
                            total = questions.size,
                            question = question,
                            accentColor = accentColor.color,
                            onUpdate = { updated -> questions[index] = updated },
                            onMoveUp = {
                                if (index > 0) {
                                    val item = questions.removeAt(index)
                                    questions.add(index - 1, item)
                                }
                            },
                            onMoveDown = {
                                if (index < questions.lastIndex) {
                                    val item = questions.removeAt(index)
                                    questions.add(index + 1, item)
                                }
                            },
                            onDuplicate = {
                                if (questions.size < 25) {
                                    questions.add(
                                        index + 1,
                                        question.copy(id = "q_${UUID.randomUUID().toString().take(6)}")
                                    )
                                }
                            }
                        )
                    }
                }

                item {
                    OutlinedButton(
                        onClick = {
                            if (questions.size < 25) {
                                questions.add(
                                    FormQuestion(
                                        id = "q_${UUID.randomUUID().toString().take(6)}",
                                        prompt = "",
                                        type = QuestionType.MULTIPLE_CHOICE.name,
                                        required = true,
                                        options = listOf("Option 1", "Option 2")
                                    )
                                )
                            }
                        },
                        enabled = questions.size < 25,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("add_question_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Question")
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionEditorCard(
    index: Int,
    total: Int,
    question: FormQuestion,
    accentColor: Color,
    onUpdate: (FormQuestion) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDuplicate: () -> Unit
) {
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
                Surface(
                    color = accentColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Question ${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onMoveUp, enabled = index > 0) {
                        Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move up")
                    }
                    IconButton(onClick = onMoveDown, enabled = index < total - 1) {
                        Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move down")
                    }
                    IconButton(onClick = onDuplicate) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Duplicate question")
                    }
                }
            }

            OutlinedTextField(
                value = question.prompt,
                onValueChange = { onUpdate(question.copy(prompt = it.take(200))) },
                label = { Text("Question Prompt") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuestionType.entries.forEach { qType ->
                    FilterChip(
                        selected = question.questionType == qType,
                        onClick = {
                            val defaultOpts = when (qType) {
                                QuestionType.MULTIPLE_CHOICE, QuestionType.CHECKBOXES ->
                                    question.options.ifEmpty { listOf("Option 1", "Option 2") }
                                QuestionType.YES_NO_SWIPE ->
                                    listOf(
                                        question.options.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "Yes",
                                        question.options.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "No"
                                    )
                                else -> question.options
                            }
                            onUpdate(question.copy(type = qType.name, options = defaultOpts))
                        },
                        label = { Text(qType.label) }
                    )
                }
            }

            when (question.questionType) {
                QuestionType.MULTIPLE_CHOICE, QuestionType.CHECKBOXES -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        question.options.forEachIndexed { optIdx, optVal ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedTextField(
                                    value = optVal,
                                    onValueChange = { next ->
                                        val copy = question.options.toMutableList()
                                        copy[optIdx] = next.take(100)
                                        onUpdate(question.copy(options = copy))
                                    },
                                    label = { Text("Option ${optIdx + 1}") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                )
                                if (question.options.size > 2) {
                                    IconButton(
                                        onClick = {
                                            val copy = question.options.toMutableList()
                                            copy.removeAt(optIdx)
                                            onUpdate(question.copy(options = copy))
                                        }
                                    ) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Remove option")
                                    }
                                }
                            }
                        }
                        if (question.options.size < 12) {
                            TextButton(
                                onClick = {
                                    onUpdate(
                                        question.copy(
                                            options = question.options + "Option ${question.options.size + 1}"
                                        )
                                    )
                                }
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Option")
                            }
                        }
                    }
                }
                QuestionType.YES_NO_SWIPE -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = question.options.getOrNull(0) ?: "Yes",
                            onValueChange = { v ->
                                val second = question.options.getOrNull(1) ?: "No"
                                onUpdate(question.copy(options = listOf(v.take(40), second)))
                            },
                            label = { Text("Swipe Right Label") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = question.options.getOrNull(1) ?: "No",
                            onValueChange = { v ->
                                val first = question.options.getOrNull(0) ?: "Yes"
                                onUpdate(question.copy(options = listOf(first, v.take(40))))
                            },
                            label = { Text("Swipe Left Label") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                QuestionType.LINEAR_SCALE -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = question.minLabel,
                            onValueChange = { onUpdate(question.copy(minLabel = it.take(40))) },
                            label = { Text("Min Label (1)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = question.maxLabel,
                            onValueChange = { onUpdate(question.copy(maxLabel = it.take(40))) },
                            label = { Text("Max Label (5)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                else -> Unit
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Required",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = question.required,
                    onCheckedChange = { onUpdate(question.copy(required = it)) }
                )
            }
        }
    }
}
