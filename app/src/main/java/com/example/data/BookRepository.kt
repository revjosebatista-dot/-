package com.example.data

import android.content.Context
import com.example.R
import com.example.model.AudioRecording
import com.example.model.BookChapter
import com.example.model.BookProject
import com.example.model.BookVersion
import com.example.model.QcIssueItem
import com.example.model.QcReport
import com.example.model.RecordingStatus
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.io.File

class BookRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("É necessário entrar com sua conta Google antes de acessar o banco de dados.")
    }

    private fun booksCollection(uid: String) =
        db.collection("users").document(uid).collection("books")

    fun observeBooks(userId: String): Flow<List<BookProject>> = flow {
        require(userId.isNotBlank()) { "User ID obrigatório." }
        val path = "users/$userId/books"
        emitAll(
            booksCollection(userId)
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        BookProject::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeBook(userId: String, bookId: String): Flow<BookProject?> = flow {
        require(userId.isNotBlank() && bookId.isNotBlank())
        val docRef = booksCollection(userId).document(bookId)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            BookProject::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeRecordings(userId: String, bookId: String): Flow<List<AudioRecording>> = flow {
        val colRef = booksCollection(userId).document(bookId).collection("recordings")
        emitAll(
            colRef.orderBy("orderIndex", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AudioRecording::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeChapters(userId: String, bookId: String): Flow<List<BookChapter>> = flow {
        val colRef = booksCollection(userId).document(bookId).collection("chapters")
        emitAll(
            colRef.orderBy("orderIndex", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        BookChapter::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeVersions(userId: String, bookId: String): Flow<List<BookVersion>> = flow {
        val colRef = booksCollection(userId).document(bookId).collection("versions")
        emitAll(
            colRef.orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        BookVersion::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeLatestQcReport(userId: String, bookId: String): Flow<QcReport?> = flow {
        val colRef = booksCollection(userId).document(bookId).collection("qcReports")
        emitAll(
            colRef.orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(1)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        QcReport::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).firstOrNull()
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun createBook(
        title: String,
        subtitle: String,
        authorName: String,
        subject: String,
        targetLanguage: String
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/books"
        return try {
            val uid = requireUserId()
            val cleanTitle = title.trim()
            require(cleanTitle.isNotEmpty() && cleanTitle.length <= 200) {
                "Informe um título entre 1 e 200 caracteres."
            }
            val docRef = booksCollection(uid).document()
            docPath = docRef.path
            val book = BookProject(
                userId = uid,
                title = cleanTitle,
                subtitle = subtitle.trim().take(300),
                authorName = authorName.trim().take(120),
                subject = subject.trim().take(1000),
                currentStep = 2L,
                structureApproved = false,
                targetLanguage = if (targetLanguage == "original") "original" else "pt-BR"
            )
            docRef.set(book.toCreateMap(uid)).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateBookMetadata(bookId: String, updates: Map<String, Any>): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/books/$bookId"
        return try {
            val uid = requireUserId()
            val docRef = booksCollection(uid).document(bookId)
            docPath = docRef.path
            val payload = updates.toMutableMap().apply {
                this["updatedAt"] = FieldValue.serverTimestamp()
            }
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun duplicateBook(sourceBookId: String): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/books/$sourceBookId"
        return try {
            val uid = requireUserId()
            val srcRef = booksCollection(uid).document(sourceBookId)
            val srcSnap = srcRef.get().await()
            val srcBook = srcSnap.toObject(BookProject::class.java)
                ?: return Result.failure(IllegalStateException("Livro original não encontrado."))

            val newBookRef = booksCollection(uid).document()
            docPath = newBookRef.path
            val copyTitle = "${srcBook.title.take(185)} (Cópia)"
            val clonedBook = srcBook.copy(
                id = "",
                title = copyTitle,
                createdAt = null,
                updatedAt = null
            )
            newBookRef.set(clonedBook.toCreateMap(uid)).await()

            val srcChapters = srcRef.collection("chapters")
                .orderBy("orderIndex", Query.Direction.ASCENDING)
                .get()
                .await()
                .toObjects(BookChapter::class.java)

            if (srcChapters.isNotEmpty()) {
                val batch = db.batch()
                srcChapters.forEach { chap ->
                    val chapRef = newBookRef.collection("chapters").document()
                    batch.set(chapRef, chap.copy(id = "", bookId = newBookRef.id).toCreateMap(uid, newBookRef.id))
                }
                batch.commit().await()
            }

            val srcRecordings = srcRef.collection("recordings")
                .orderBy("orderIndex", Query.Direction.ASCENDING)
                .get()
                .await()
                .toObjects(AudioRecording::class.java)

            if (srcRecordings.isNotEmpty()) {
                val batch = db.batch()
                srcRecordings.forEach { rec ->
                    val recRef = newBookRef.collection("recordings").document()
                    batch.set(recRef, rec.copy(id = "", bookId = newBookRef.id).toCreateMap(uid, newBookRef.id))
                }
                batch.commit().await()
            }

            Result.success(newBookRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteBook(bookId: String, context: Context): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/books/$bookId"
        return try {
            val uid = requireUserId()
            val bookRef = booksCollection(uid).document(bookId)
            docPath = bookRef.path

            val subCollections = listOf("recordings", "chapters", "versions", "qcReports")
            for (sub in subCollections) {
                val docs = bookRef.collection(sub).get().await().documents
                docs.chunked(400).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { batch.delete(it.reference) }
                    batch.commit().await()
                }
            }
            bookRef.delete().await()

            val audioDir = File(context.filesDir, "audio/$bookId")
            if (audioDir.exists()) {
                audioDir.deleteRecursively()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addRecording(bookId: String, recording: AudioRecording): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/books/$bookId/recordings"
        return try {
            val uid = requireUserId()
            val docRef = booksCollection(uid).document(bookId).collection("recordings").document()
            docPath = docRef.path
            docRef.set(recording.toCreateMap(uid, bookId)).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateRecordingProgress(
        bookId: String,
        recordingId: String,
        updates: Map<String, Any>
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/books/$bookId/recordings/$recordingId"
        return try {
            val uid = requireUserId()
            val docRef = booksCollection(uid).document(bookId)
                .collection("recordings").document(recordingId)
            docPath = docRef.path
            val payload = updates.toMutableMap().apply {
                this["updatedAt"] = FieldValue.serverTimestamp()
            }
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteRecording(bookId: String, recording: AudioRecording): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/books/$bookId/recordings/${recording.id}"
        return try {
            val uid = requireUserId()
            val docRef = booksCollection(uid).document(bookId)
                .collection("recordings").document(recording.id)
            docPath = docRef.path
            docRef.delete().await()
            if (recording.localFilePath.isNotBlank()) {
                runCatching { File(recording.localFilePath).delete() }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun reorderRecordings(bookId: String, ordered: List<AudioRecording>): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/books/$bookId/recordings"
        return try {
            val uid = requireUserId()
            val batch = db.batch()
            ordered.forEachIndexed { index, rec ->
                val ref = booksCollection(uid).document(bookId)
                    .collection("recordings").document(rec.id)
                batch.update(
                    ref,
                    mapOf(
                        "orderIndex" to index.toLong(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun replaceBookChapters(bookId: String, newChapters: List<BookChapter>): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/books/$bookId/chapters"
        return try {
            val uid = requireUserId()
            val chaptersCol = booksCollection(uid).document(bookId).collection("chapters")
            val existingDocs = chaptersCol.get().await().documents
            val batch = db.batch()
            existingDocs.forEach { batch.delete(it.reference) }
            newChapters.forEachIndexed { idx, chap ->
                val ref = chaptersCol.document()
                val mapped = chap.copy(
                    id = "",
                    bookId = bookId,
                    chapterNumber = (idx + 1).toLong(),
                    orderIndex = idx.toLong()
                ).toCreateMap(uid, bookId)
                batch.set(ref, mapped)
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveChapter(bookId: String, chapter: BookChapter): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/books/$bookId/chapters"
        return try {
            val uid = requireUserId()
            val colRef = booksCollection(uid).document(bookId).collection("chapters")
            if (chapter.id.isBlank()) {
                val ref = colRef.document()
                docPath = ref.path
                ref.set(chapter.toCreateMap(uid, bookId)).await()
                Result.success(ref.id)
            } else {
                val ref = colRef.document(chapter.id)
                docPath = ref.path
                ref.update(
                    mapOf(
                        "partTitle" to chapter.partTitle.take(200),
                        "chapterNumber" to chapter.chapterNumber.coerceIn(1L, 500L),
                        "title" to chapter.title.trim().ifBlank { "Capítulo ${chapter.chapterNumber}" }.take(250),
                        "subtitle" to chapter.subtitle.take(300),
                        "content" to chapter.content.take(300000),
                        "summary" to chapter.summary.take(5000),
                        "orderIndex" to chapter.orderIndex.coerceIn(0L, 500L),
                        "approved" to chapter.approved,
                        "needsReviewCount" to countReviewMarkers(chapter.content).toLong(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                Result.success(chapter.id)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteChapter(bookId: String, chapterId: String, remainingChapters: List<BookChapter>): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/books/$bookId/chapters/$chapterId"
        return try {
            val uid = requireUserId()
            val colRef = booksCollection(uid).document(bookId).collection("chapters")
            val batch = db.batch()
            batch.delete(colRef.document(chapterId))
            remainingChapters.filter { it.id != chapterId }.forEachIndexed { idx, chap ->
                batch.update(
                    colRef.document(chap.id),
                    mapOf(
                        "orderIndex" to idx.toLong(),
                        "chapterNumber" to (idx + 1).toLong(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun reorderChapters(bookId: String, ordered: List<BookChapter>): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/books/$bookId/chapters"
        return try {
            val uid = requireUserId()
            val colRef = booksCollection(uid).document(bookId).collection("chapters")
            val batch = db.batch()
            ordered.forEachIndexed { idx, chap ->
                batch.update(
                    colRef.document(chap.id),
                    mapOf(
                        "orderIndex" to idx.toLong(),
                        "chapterNumber" to (idx + 1).toLong(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveVersionSnapshot(
        book: BookProject,
        chapters: List<BookChapter>,
        label: String,
        note: String
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/books/${book.id}/versions"
        return try {
            val uid = requireUserId()
            val ref = booksCollection(uid).document(book.id).collection("versions").document()
            docPath = ref.path
            val snapshotList = chapters.take(100).map { chap ->
                mapOf(
                    "partTitle" to chap.partTitle.take(200),
                    "chapterNumber" to chap.chapterNumber,
                    "title" to chap.title.take(250),
                    "subtitle" to chap.subtitle.take(300),
                    "content" to chap.content.take(40000),
                    "summary" to chap.summary.take(2000),
                    "approved" to chap.approved
                )
            }
            val totalWords = chapters.sumOf { it.wordCount }.toLong()
            val payload = mapOf(
                "userId" to uid,
                "bookId" to book.id,
                "label" to label.trim().ifBlank { "Versão salva" }.take(150),
                "note" to note.trim().take(1000),
                "bookTitleSnapshot" to book.title.take(200),
                "bookSubtitleSnapshot" to book.subtitle.take(300),
                "chapterCount" to chapters.size.toLong(),
                "wordCount" to totalWords,
                "chaptersSnapshot" to snapshotList,
                "createdAt" to FieldValue.serverTimestamp()
            )
            ref.set(payload).await()
            Result.success(ref.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun restoreVersionSnapshot(bookId: String, version: BookVersion): Result<Unit> {
        return try {
            val uid = requireUserId()
            val restoredChapters = version.chaptersSnapshot.mapIndexed { idx, map ->
                val content = map["content"] as? String ?: ""
                BookChapter(
                    userId = uid,
                    bookId = bookId,
                    partTitle = map["partTitle"] as? String ?: "",
                    chapterNumber = (idx + 1).toLong(),
                    title = map["title"] as? String ?: "Capítulo ${idx + 1}",
                    subtitle = map["subtitle"] as? String ?: "",
                    content = content,
                    summary = map["summary"] as? String ?: "",
                    orderIndex = idx.toLong(),
                    approved = map["approved"] as? Boolean ?: false,
                    needsReviewCount = countReviewMarkers(content).toLong()
                )
            }
            replaceBookChapters(bookId, restoredChapters).getOrThrow()
            if (version.bookTitleSnapshot.isNotBlank()) {
                updateBookMetadata(
                    bookId,
                    mapOf(
                        "title" to version.bookTitleSnapshot,
                        "subtitle" to version.bookSubtitleSnapshot
                    )
                ).getOrThrow()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/${auth.currentUser?.uid}/books/$bookId")
            Result.failure(e)
        }
    }

    suspend fun saveQcReport(
        bookId: String,
        summary: String,
        readyWithCaveats: Boolean,
        issues: List<QcIssueItem>
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/books/$bookId/qcReports"
        return try {
            val uid = requireUserId()
            val ref = booksCollection(uid).document(bookId).collection("qcReports").document()
            docPath = ref.path
            val payload = mapOf(
                "userId" to uid,
                "bookId" to bookId,
                "summary" to summary.trim().ifBlank { "Revisão concluída." }.take(5000),
                "readyWithCaveats" to readyWithCaveats,
                "issueCount" to issues.size.toLong(),
                "issues" to issues.take(100).map { it.toMap() },
                "createdAt" to FieldValue.serverTimestamp()
            )
            ref.set(payload).await()
            Result.success(ref.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    companion object {
        fun countReviewMarkers(text: String): Int {
            return Regex("\\[REVISAR[^\\]]*\\]", RegexOption.IGNORE_CASE)
                .findAll(text)
                .count()
        }
    }
}
