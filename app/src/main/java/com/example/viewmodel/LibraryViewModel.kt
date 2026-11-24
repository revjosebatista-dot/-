package com.example.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BookRepository
import com.example.model.BookProject
import com.example.model.UiState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val repository: BookRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    val booksState: StateFlow<UiState<List<BookProject>>> = combine(
        repository.observeBooks(currentUserId),
        _searchQuery
    ) { books, query ->
        val filtered = if (query.isBlank()) {
            books
        } else {
            val q = query.trim().lowercase()
            books.filter {
                it.title.lowercase().contains(q) ||
                    it.subject.lowercase().contains(q) ||
                    it.authorName.lowercase().contains(q)
            }
        }
        UiState.Success(filtered) as UiState<List<BookProject>>
    }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing books", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "Falha ao carregar livros."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun createBook(
        title: String,
        subtitle: String,
        authorName: String,
        subject: String,
        targetLanguage: String,
        onCreated: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.createBook(title, subtitle, authorName, subject, targetLanguage)
            _isBusy.value = false
            result.onSuccess { bookId ->
                _events.emit("Projeto \"$title\" criado com sucesso.")
                onCreated(bookId)
            }.onFailure { err ->
                _events.emit(err.localizedMessage ?: "Erro ao criar projeto.")
            }
        }
    }

    fun duplicateBook(bookId: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.duplicateBook(bookId)
            _isBusy.value = false
            result.onSuccess {
                _events.emit("Projeto duplicado com sucesso.")
            }.onFailure { err ->
                _events.emit(err.localizedMessage ?: "Falha ao duplicar projeto.")
            }
        }
    }

    fun deleteBook(bookId: String, context: Context) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.deleteBook(bookId, context.applicationContext)
            _isBusy.value = false
            result.onSuccess {
                _events.emit("Projeto excluído.")
            }.onFailure { err ->
                _events.emit(err.localizedMessage ?: "Falha ao excluir projeto.")
            }
        }
    }

    private companion object {
        const val TAG = "LibraryVM"
    }
}
