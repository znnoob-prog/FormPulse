package com.example.data.model

import androidx.annotation.Keep
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.PulseAmber
import com.example.ui.theme.PulseCoral
import com.example.ui.theme.PulseIndigo
import com.example.ui.theme.PulseMint
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class FormCategory(val label: String) {
    SURVEY("Survey"),
    FEEDBACK("Feedback"),
    EVENT("Event"),
    QUIZ("Quiz"),
    CHECKLIST("Checklist");

    companion object {
        fun fromRaw(raw: String?): FormCategory =
            entries.firstOrNull { it.name == raw } ?: SURVEY
    }
}

enum class FormAccentColor(val label: String, val color: Color) {
    INDIGO("Indigo", PulseIndigo),
    MINT("Mint", PulseMint),
    CORAL("Coral", PulseCoral),
    AMBER("Amber", PulseAmber);

    companion object {
        fun fromRaw(raw: String?): FormAccentColor =
            entries.firstOrNull { it.name == raw } ?: INDIGO
    }
}

enum class QuestionType(val label: String, val gestureHint: String) {
    MULTIPLE_CHOICE("Multiple Choice", "Tap a card or swipe right to advance"),
    CHECKBOXES("Checkboxes", "Tap to toggle multiple options"),
    LINEAR_SCALE("Linear Scale", "Drag or tap along the scale"),
    STAR_RATING("Star Rating", "Swipe across stars to rate"),
    YES_NO_SWIPE("Swipe Decision", "Swipe card right for Yes, left for No"),
    SHORT_TEXT("Short Answer", "Type your response");

    companion object {
        fun fromRaw(raw: String?): QuestionType =
            entries.firstOrNull { it.name == raw } ?: MULTIPLE_CHOICE
    }
}

@Keep
@IgnoreExtraProperties
data class FormQuestion(
    val id: String = "",
    val prompt: String = "",
    val type: String = QuestionType.MULTIPLE_CHOICE.name,
    val required: Boolean = true,
    val options: List<String> = emptyList(),
    val scaleMin: Long = 1L,
    val scaleMax: Long = 5L,
    val minLabel: String = "",
    val maxLabel: String = ""
) {
    @get:Exclude
    val questionType: QuestionType
        get() = QuestionType.fromRaw(type)

    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "prompt" to prompt.trim().take(200),
        "type" to QuestionType.fromRaw(type).name,
        "required" to required,
        "options" to options.take(12).map { it.trim().take(100) },
        "scaleMin" to scaleMin,
        "scaleMax" to scaleMax,
        "minLabel" to minLabel.trim().take(40),
        "maxLabel" to maxLabel.trim().take(40)
    )
}

@Keep
@IgnoreExtraProperties
data class FormDefinition(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = FormCategory.SURVEY.name,
    val accentColorKey: String = FormAccentColor.INDIGO.name,
    val acceptingResponses: Boolean = true,
    val questions: List<FormQuestion> = emptyList(),
    val responseCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    @get:Exclude val hasPendingWrites: Boolean = false
) {
    @get:Exclude
    val formCategory: FormCategory
        get() = FormCategory.fromRaw(category)

    @get:Exclude
    val accentColor: FormAccentColor
        get() = FormAccentColor.fromRaw(accentColorKey)

    fun toCreateMap(ownerUid: String): Map<String, Any> = mapOf(
        "userId" to ownerUid,
        "title" to title.trim().ifBlank { "Untitled Form" }.take(120),
        "description" to description.trim().take(500),
        "category" to FormCategory.fromRaw(category).name,
        "accentColorKey" to FormAccentColor.fromRaw(accentColorKey).name,
        "acceptingResponses" to acceptingResponses,
        "questions" to questions.take(25).map { it.toMap() },
        "responseCount" to responseCount.coerceAtLeast(0L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "title" to title.trim().ifBlank { "Untitled Form" }.take(120),
        "description" to description.trim().take(500),
        "category" to FormCategory.fromRaw(category).name,
        "accentColorKey" to FormAccentColor.fromRaw(accentColorKey).name,
        "acceptingResponses" to acceptingResponses,
        "questions" to questions.take(25).map { it.toMap() },
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class QuestionAnswer(
    val questionId: String = "",
    val questionType: String = QuestionType.MULTIPLE_CHOICE.name,
    val textValue: String = "",
    val numericValue: Long = 0L,
    val selectedOptions: List<String> = emptyList()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "questionId" to questionId,
        "questionType" to QuestionType.fromRaw(questionType).name,
        "textValue" to textValue.trim().take(500),
        "numericValue" to numericValue,
        "selectedOptions" to selectedOptions.take(12)
    )

    fun isAnswered(): Boolean = when (QuestionType.fromRaw(questionType)) {
        QuestionType.MULTIPLE_CHOICE, QuestionType.YES_NO_SWIPE -> textValue.isNotBlank()
        QuestionType.SHORT_TEXT -> textValue.isNotBlank()
        QuestionType.CHECKBOXES -> selectedOptions.isNotEmpty()
        QuestionType.LINEAR_SCALE, QuestionType.STAR_RATING -> numericValue > 0L
    }
}

@Keep
@IgnoreExtraProperties
data class FormResponse(
    @DocumentId val id: String = "",
    val userId: String = "",
    val formId: String = "",
    val respondentName: String = "Anonymous",
    val answers: List<QuestionAnswer> = emptyList(),
    val completionSeconds: Long = 0L,
    val submittedOffline: Boolean = false,
    @ServerTimestamp val submittedAt: Timestamp? = null,
    @get:Exclude val hasPendingWrites: Boolean = false
) {
    fun toCreateMap(ownerUid: String, targetFormId: String): Map<String, Any> = mapOf(
        "userId" to ownerUid,
        "formId" to targetFormId,
        "respondentName" to respondentName.trim().ifBlank { "Anonymous" }.take(80),
        "answers" to answers.take(25).map { it.toMap() },
        "completionSeconds" to completionSeconds.coerceIn(0L, 86400L),
        "submittedOffline" to submittedOffline,
        "submittedAt" to FieldValue.serverTimestamp()
    )
}

enum class SubmitOutcome {
    SYNCED,
    QUEUED_OFFLINE
}

object FormTemplates {
    fun availableTemplates(): List<FormDefinition> = listOf(
        FormDefinition(
            title = "Event Experience Pulse",
            description = "Fast gesture-driven post-session feedback built for mobile attendees.",
            category = FormCategory.FEEDBACK.name,
            accentColorKey = FormAccentColor.INDIGO.name,
            acceptingResponses = true,
            questions = listOf(
                FormQuestion(
                    id = "q_1",
                    prompt = "Would you recommend this event to a colleague?",
                    type = QuestionType.YES_NO_SWIPE.name,
                    required = true,
                    options = listOf("Yes, definitely", "Not this time")
                ),
                FormQuestion(
                    id = "q_2",
                    prompt = "Rate the overall speaker quality",
                    type = QuestionType.STAR_RATING.name,
                    required = true,
                    scaleMin = 1L,
                    scaleMax = 5L
                ),
                FormQuestion(
                    id = "q_3",
                    prompt = "Which sessions were most valuable?",
                    type = QuestionType.CHECKBOXES.name,
                    required = false,
                    options = listOf("Keynote", "Hands-on Lab", "Live Q&A", "Networking")
                ),
                FormQuestion(
                    id = "q_4",
                    prompt = "How clear were the workshop takeaways?",
                    type = QuestionType.LINEAR_SCALE.name,
                    required = true,
                    scaleMin = 1L,
                    scaleMax = 5L,
                    minLabel = "Unclear",
                    maxLabel = "Crystal clear"
                ),
                FormQuestion(
                    id = "q_5",
                    prompt = "One thing we should improve next time?",
                    type = QuestionType.SHORT_TEXT.name,
                    required = false
                )
            )
        ),
        FormDefinition(
            title = "Weekly Team Check-In",
            description = "30-second pulse check for workload, blockers, and morale.",
            category = FormCategory.SURVEY.name,
            accentColorKey = FormAccentColor.MINT.name,
            acceptingResponses = true,
            questions = listOf(
                FormQuestion(
                    id = "q_1",
                    prompt = "How is your energy level this week?",
                    type = QuestionType.LINEAR_SCALE.name,
                    required = true,
                    scaleMin = 1L,
                    scaleMax = 5L,
                    minLabel = "Drained",
                    maxLabel = "Energized"
                ),
                FormQuestion(
                    id = "q_2",
                    prompt = "Are you blocked on any deliverables?",
                    type = QuestionType.YES_NO_SWIPE.name,
                    required = true,
                    options = listOf("Yes, need help", "No, on track")
                ),
                FormQuestion(
                    id = "q_3",
                    prompt = "Primary focus area this sprint",
                    type = QuestionType.MULTIPLE_CHOICE.name,
                    required = true,
                    options = listOf("Feature Delivery", "Bug Triage", "Architecture", "Design Polish")
                ),
                FormQuestion(
                    id = "q_4",
                    prompt = "Quick shout-out or note for the team",
                    type = QuestionType.SHORT_TEXT.name,
                    required = false
                )
            )
        ),
        FormDefinition(
            title = "Field Inspection Checklist",
            description = "Offline-ready site readiness & safety verification form.",
            category = FormCategory.CHECKLIST.name,
            accentColorKey = FormAccentColor.CORAL.name,
            acceptingResponses = true,
            questions = listOf(
                FormQuestion(
                    id = "q_1",
                    prompt = "Is the primary access area clear and safe?",
                    type = QuestionType.YES_NO_SWIPE.name,
                    required = true,
                    options = listOf("Clear & Safe", "Needs Attention")
                ),
                FormQuestion(
                    id = "q_2",
                    prompt = "Verified equipment items",
                    type = QuestionType.CHECKBOXES.name,
                    required = true,
                    options = listOf("Power Backup", "Network Kit", "Safety Gear", "Signage")
                ),
                FormQuestion(
                    id = "q_3",
                    prompt = "Overall site readiness score",
                    type = QuestionType.STAR_RATING.name,
                    required = true,
                    scaleMin = 1L,
                    scaleMax = 5L
                )
            )
        )
    )
}
