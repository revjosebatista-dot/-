package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.BookProject
import com.example.model.UiState
import com.example.ui.theme.Azure100
import com.example.ui.theme.Azure50
import com.example.ui.theme.Azure600
import com.example.ui.theme.Navy900
import com.example.ui.theme.PureWhite
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun LibraryScreen(
    booksState: UiState<List<BookProject>>,
    searchQuery: String,
    isBusy: Boolean,
    onSearchChange: (String) -> Unit,
    onCreateBook: (title: String, subtitle: String, authorName: String, subject: String, targetLang: String) -> Unit,
    onOpenBook: (BookProject) -> Unit,
    onDuplicateBook: (String) -> Unit,
    onDeleteBook: (String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var bookToDelete by remember { mutableStateOf<BookProject?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Hero Studio Header
            Surface(
                color = Navy900,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Painel Editorial",
                            style = MaterialTheme.typography.labelSmall,
                            color = Azure100
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Seus Livros em Produção",
                            style = MaterialTheme.typography.headlineMedium,
                            color = PureWhite
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Transforme aulas, pregações e entrevistas em manuscritos organizados com Gemini AI.",
                            style = MaterialTheme.typography.bodySmall,
                            color = PureWhite.copy(alpha = 0.85f)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = { showCreateDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Azure600,
                            contentColor = PureWhite
                        ),
                        modifier = Modifier.testTag("new_book_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Novo Livro")
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar livros") },
                placeholder = { Text("Buscar por título, assunto ou autor...") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_books_input")
            )

            Spacer(modifier = Modifier.height(14.dp))

            when (booksState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Navy900)
                    }
                }

                is UiState.Error -> {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = booksState.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                is UiState.Success -> {
                    val books = booksState.data
                    if (books.isEmpty()) {
                        EmptyLibraryState(
                            hasSearch = searchQuery.isNotBlank(),
                            onCreateClick = { showCreateDialog = true }
                        )
                    } else {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val columns = when {
                                maxWidth >= 900.dp -> 3
                                maxWidth >= 600.dp -> 2
                                else -> 1
                            }
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(columns),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = PaddingValues(bottom = 88.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(books, key = { it.id }) { book ->
                                    BookProjectCard(
                                        book = book,
                                        enabled = !isBusy,
                                        onOpen = { onOpenBook(book) },
                                        onDuplicate = { onDuplicateBook(book.id) },
                                        onDelete = { bookToDelete = book }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showCreateDialog = true },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text("Novo Livro") },
            containerColor = Navy900,
            contentColor = PureWhite,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("fab_new_book")
        )
    }

    if (showCreateDialog) {
        CreateBookDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { title, subtitle, author, subject, targetLang ->
                showCreateDialog = false
                onCreateBook(title, subtitle, author, subject, targetLang)
            }
        )
    }

    bookToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { bookToDelete = null },
            title = { Text("Excluir projeto?") },
            text = {
                Text("Tem certeza de que deseja excluir \"${target.title}\" e todos os seus capítulos, transcrições e versões? Esta ação não pode ser desfeita.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        bookToDelete = null
                        onDeleteBook(target.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Excluir definitivamente")
                }
            },
            dismissButton = {
                TextButton(onClick = { bookToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun BookProjectCard(
    book: BookProject,
    enabled: Boolean,
    onOpen: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val updatedText = remember(book.updatedAt, book.createdAt) {
        val ts = book.updatedAt ?: book.createdAt
        ts?.toDate()?.let { dateFormat.format(it) } ?: "Agora"
    }
    val stepFraction = (book.currentStep.toFloat() / 12f).coerceIn(0.08f, 1f)

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onOpen)
            .testTag("book_card_${book.id}")
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = Azure50,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "ETAPA ${book.currentStep}/12",
                        style = MaterialTheme.typography.labelSmall,
                        color = Azure600,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = if (book.targetLanguage == "original") "Idioma Original" else "PT-BR",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = book.title,
                style = MaterialTheme.typography.headlineSmall,
                color = Navy900,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (book.subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = book.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (book.subject.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = book.subject,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { stepFraction },
                color = Azure600,
                trackColor = Azure100,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(4.dp))
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Atualizado: $updatedText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row {
                    IconButton(onClick = onDuplicate, enabled = enabled) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Duplicar projeto",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDelete, enabled = enabled) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Excluir projeto",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryState(
    hasSearch: Boolean,
    onCreateClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_empty_library),
            contentDescription = "Biblioteca vazia",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(200.dp)
                .clip(MaterialTheme.shapes.large)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (hasSearch) "Nenhum livro encontrado" else "Comece seu primeiro livro",
            style = MaterialTheme.typography.headlineSmall,
            color = Navy900
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (hasSearch) {
                "Tente outro termo de busca."
            } else {
                "Crie um projeto, envie seus áudios ou grave diretamente pelo celular e deixe o LIVRO IA organizar tudo."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!hasSearch) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onCreateClick,
                colors = ButtonDefaults.buttonColors(containerColor = Navy900)
            ) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Criar Primeiro Livro")
            }
        }
    }
}

@Composable
private fun CreateBookDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, subtitle: String, author: String, subject: String, targetLang: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var subtitle by remember { mutableStateOf("") }
    var authorName by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var targetLanguage by remember { mutableStateOf("pt-BR") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo Projeto de Livro") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título provisório *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_book_title_input")
                )
                OutlinedTextField(
                    value = subtitle,
                    onValueChange = { subtitle = it },
                    label = { Text("Subtítulo (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = authorName,
                    onValueChange = { authorName = it },
                    label = { Text("Nome do autor") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Assunto / Tema principal *") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_book_subject_input")
                )
                Text(
                    text = "Idioma do livro final",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = targetLanguage == "pt-BR",
                        onClick = { targetLanguage = "pt-BR" },
                        label = { Text("Traduzir p/ PT-BR") }
                    )
                    FilterChip(
                        selected = targetLanguage == "original",
                        onClick = { targetLanguage = "original" },
                        label = { Text("Manter Original") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title, subtitle, authorName, subject, targetLanguage) },
                enabled = title.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Navy900),
                modifier = Modifier.testTag("confirm_create_book_button")
            ) {
                Text("Criar Projeto")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
