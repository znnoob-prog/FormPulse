package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.local.FormDraftDao
import com.example.data.local.FormDraftDatabase
import com.example.data.local.FormDraftEntity
import com.example.data.model.FormDefinition
import com.example.data.model.FormResponse
import com.example.data.model.QuestionAnswer
import com.example.data.model.SubmitOutcome
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class FormRepository(
    private val db: FirebaseFirestore,
    private val draftDao: FormDraftDao,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        draftDao = FormDraftDatabase.getInstance(context.applicationContext).formDraftDao()
    )

    private fun requireUserId(expectedUserId: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        check(uid == expectedUserId) { "Signed-in user changed; refusing cross-user write." }
        return uid
    }

    fun observeForms(userId: String): Flow<List<FormDefinition>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/forms"
        emitAll(
            db.collection("users").document(uid).collection("forms")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(
                            FormDefinition::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )?.copy(hasPendingWrites = doc.metadata.hasPendingWrites())
                    }.sortedByDescending { it.updatedAt?.toDate()?.time ?: it.createdAt?.toDate()?.time ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeForm(userId: String, formId: String): Flow<FormDefinition?> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/forms/$formId"
        emitAll(
            db.collection("users").document(uid).collection("forms").document(formId)
                .snapshots(MetadataChanges.INCLUDE)
                .map { doc ->
                    if (!doc.exists()) null
                    else doc.toObject(
                        FormDefinition::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )?.copy(hasPendingWrites = doc.metadata.hasPendingWrites())
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeResponses(userId: String, formId: String): Flow<List<FormResponse>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/forms/$formId/responses"
        emitAll(
            db.collection("users").document(uid)
                .collection("forms").document(formId)
                .collection("responses")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(
                            FormResponse::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )?.copy(hasPendingWrites = doc.metadata.hasPendingWrites())
                    }.sortedByDescending { it.submittedAt?.toDate()?.time ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createForm(
        userId: String,
        form: FormDefinition,
        isNetworkAllowed: Boolean
    ): Result<String> {
        var docPath = "users/$userId/forms"
        return try {
            val uid = requireUserId(userId)
            require(form.title.trim().isNotEmpty()) { "Form title cannot be empty" }
            require(form.questions.isNotEmpty() && form.questions.size <= 25) {
                "Form must have between 1 and 25 questions"
            }
            val docRef = db.collection("users").document(uid).collection("forms").document()
            docPath = docRef.path
            val payload = form.toCreateMap(uid)
            val task = docRef.set(payload)
            if (isNetworkAllowed) {
                withTimeoutOrNull(3500L) { task.await() }
            } else {
                task.addOnFailureListener { e ->
                    handleFirestoreError(e, OperationType.CREATE, docPath)
                }
            }
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateForm(
        userId: String,
        form: FormDefinition,
        isNetworkAllowed: Boolean
    ): Result<Unit> {
        var docPath = "users/$userId/forms/${form.id}"
        return try {
            val uid = requireUserId(userId)
            require(form.id.isNotBlank()) { "Form ID must not be blank for update" }
            require(form.title.trim().isNotEmpty()) { "Form title cannot be empty" }
            require(form.questions.isNotEmpty() && form.questions.size <= 25) {
                "Form must have between 1 and 25 questions"
            }
            val docRef = db.collection("users").document(uid).collection("forms").document(form.id)
            docPath = docRef.path
            val task = docRef.update(form.toUpdateMap())
            if (isNetworkAllowed) {
                withTimeoutOrNull(3500L) { task.await() }
            } else {
                task.addOnFailureListener { e ->
                    handleFirestoreError(e, OperationType.UPDATE, docPath)
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleAcceptingResponses(
        userId: String,
        formId: String,
        accepting: Boolean,
        isNetworkAllowed: Boolean
    ): Result<Unit> {
        var docPath = "users/$userId/forms/$formId"
        return try {
            val uid = requireUserId(userId)
            require(formId.isNotBlank()) { "Form ID cannot be blank" }
            val docRef = db.collection("users").document(uid).collection("forms").document(formId)
            docPath = docRef.path
            val task = docRef.update(
                mapOf(
                    "acceptingResponses" to accepting,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (isNetworkAllowed) {
                withTimeoutOrNull(3500L) { task.await() }
            } else {
                task.addOnFailureListener { e ->
                    handleFirestoreError(e, OperationType.UPDATE, docPath)
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteForm(
        userId: String,
        formId: String,
        isNetworkAllowed: Boolean
    ): Result<Unit> {
        var docPath = "users/$userId/forms/$formId"
        return try {
            val uid = requireUserId(userId)
            require(formId.isNotBlank()) { "Form ID cannot be blank" }
            val docRef = db.collection("users").document(uid).collection("forms").document(formId)
            docPath = docRef.path
            val task = docRef.delete()
            if (isNetworkAllowed) {
                withTimeoutOrNull(3500L) { task.await() }
            } else {
                task.addOnFailureListener { e ->
                    handleFirestoreError(e, OperationType.DELETE, docPath)
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun submitResponse(
        userId: String,
        formId: String,
        response: FormResponse,
        isNetworkAllowed: Boolean
    ): Result<SubmitOutcome> {
        var docPath = "users/$userId/forms/$formId/responses"
        return try {
            val uid = requireUserId(userId)
            require(formId.isNotBlank()) { "Form ID must not be blank" }
            require(response.answers.isNotEmpty() && response.answers.size <= 25) {
                "At least one answer is required"
            }
            val formRef = db.collection("users").document(uid).collection("forms").document(formId)
            val responseRef = formRef.collection("responses").document()
            docPath = responseRef.path

            val payload = response.copy(submittedOffline = !isNetworkAllowed)
                .toCreateMap(uid, formId)

            val batch = db.batch()
            batch.set(responseRef, payload)
            batch.update(
                formRef,
                mapOf(
                    "responseCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            val task = batch.commit()
            val outcome = if (isNetworkAllowed) {
                val synced = withTimeoutOrNull(3500L) {
                    task.await()
                    true
                } ?: false
                if (synced) SubmitOutcome.SYNCED else SubmitOutcome.QUEUED_OFFLINE
            } else {
                task.addOnFailureListener { e ->
                    handleFirestoreError(e, OperationType.CREATE, docPath)
                }
                SubmitOutcome.QUEUED_OFFLINE
            }

            draftDao.deleteDraft(FormDraftEntity.key(uid, formId))
            Result.success(outcome)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun getDraft(userId: String, formId: String): FormDraftEntity? =
        draftDao.getDraft(FormDraftEntity.key(userId, formId))

    suspend fun saveDraft(
        userId: String,
        formId: String,
        respondentName: String,
        answers: Map<String, QuestionAnswer>,
        currentPage: Int
    ) {
        draftDao.upsertDraft(
            FormDraftEntity(
                draftKey = FormDraftEntity.key(userId, formId),
                userId = userId,
                formId = formId,
                respondentName = respondentName,
                answersJson = FormDraftEntity.encodeAnswers(answers),
                currentPage = currentPage,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun clearDraft(userId: String, formId: String) {
        draftDao.deleteDraft(FormDraftEntity.key(userId, formId))
    }
}
