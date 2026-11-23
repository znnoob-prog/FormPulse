package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.QuestionAnswer
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "form_drafts")
data class FormDraftEntity(
    @PrimaryKey val draftKey: String,
    val userId: String,
    val formId: String,
    val respondentName: String,
    val answersJson: String,
    val currentPage: Int,
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    fun decodeAnswers(): Map<String, QuestionAnswer> {
        val result = mutableMapOf<String, QuestionAnswer>()
        try {
            val array = JSONArray(answersJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val qId = obj.optString("questionId", "")
                if (qId.isNotBlank()) {
                    val optsArray = obj.optJSONArray("selectedOptions")
                    val opts = mutableListOf<String>()
                    if (optsArray != null) {
                        for (j in 0 until optsArray.length()) {
                            opts.add(optsArray.optString(j))
                        }
                    }
                    result[qId] = QuestionAnswer(
                        questionId = qId,
                        questionType = obj.optString("questionType", "MULTIPLE_CHOICE"),
                        textValue = obj.optString("textValue", ""),
                        numericValue = obj.optLong("numericValue", 0L),
                        selectedOptions = opts
                    )
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    companion object {
        fun key(userId: String, formId: String): String = "${userId}_$formId"

        fun encodeAnswers(answers: Map<String, QuestionAnswer>): String {
            val array = JSONArray()
            answers.values.forEach { answer ->
                val obj = JSONObject().apply {
                    put("questionId", answer.questionId)
                    put("questionType", answer.questionType)
                    put("textValue", answer.textValue)
                    put("numericValue", answer.numericValue)
                    put("selectedOptions", JSONArray(answer.selectedOptions))
                }
                array.put(obj)
            }
            return array.toString()
        }
    }
}

@Dao
interface FormDraftDao {
    @Query("SELECT * FROM form_drafts WHERE draftKey = :draftKey LIMIT 1")
    fun observeDraft(draftKey: String): Flow<FormDraftEntity?>

    @Query("SELECT * FROM form_drafts WHERE draftKey = :draftKey LIMIT 1")
    suspend fun getDraft(draftKey: String): FormDraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDraft(draft: FormDraftEntity)

    @Query("DELETE FROM form_drafts WHERE draftKey = :draftKey")
    suspend fun deleteDraft(draftKey: String)
}

@Database(entities = [FormDraftEntity::class], version = 1, exportSchema = false)
abstract class FormDraftDatabase : RoomDatabase() {
    abstract fun formDraftDao(): FormDraftDao

    companion object {
        @Volatile
        private var instance: FormDraftDatabase? = null

        fun getInstance(context: Context): FormDraftDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FormDraftDatabase::class.java,
                    "formpulse_drafts.db"
                ).build().also { instance = it }
            }
        }
    }
}
