package com.example.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.BookRepository
import com.example.model.BookProject
import com.example.model.UiState
import com.example.model.WorkspaceTab
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOut
import com.example.ui.screens.AudioStudioScreen
import com.example.ui.screens.LayoutExportScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.ManuscriptEditorScreen
import com.example.ui.screens.QualityControlScreen
import com.example.ui.screens.StructureScreen
import com.example.ui.theme.Azure50
import com.example.ui.theme.Azure600
import com.example.ui.theme.Navy900
import com.example.ui.theme.PureWhite
import com.example.viewmodel.BookWorkspaceViewModel
import com.example.viewmodel.LibraryViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

@Composable
fun LivroAppRoot() {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = { currentUser = Firebase.auth.currentUser })
    } else {
        key(user.uid) {
            AuthenticatedEditorialStudio(
                currentUserId = user.uid,
                userEmail = user.email.orEmpty(),
                userDisplayName = user.displayName ?: "Autor"
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedEditorialStudio(
    currentUserId: String,
    userEmail: String,
    userDisplayName: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val libraryViewModel: LibraryViewModel = viewModel(
        key = "lib_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                LibraryViewModel(BookRepository(db), currentUserId)
            }
        }
    )

    LaunchedEffect(libraryViewModel) {
        libraryViewModel.events.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    var activeBookId by rememberSaveable(currentUserId) { mutableStateOf<String?>(null) }
    var activeTab by rememberSaveable(currentUserId) { mutableStateOf(WorkspaceTab.LIBRARY) }
    var selectedChapterId by rememberSaveable(currentUserId) { mutableStateOf<String?>(null) }

    BackHandler(enabled = activeBookId != null || activeTab != WorkspaceTab.LIBRARY) {
        if (activeTab != WorkspaceTab.LIBRARY) {
            activeTab = WorkspaceTab.LIBRARY
        } else {
            activeBookId = null
        }
    }

    val booksState by libraryViewModel.booksState.collectAsStateWithLifecycle()
    val searchQuery by libraryViewModel.searchQuery.collectAsStateWithLifecycle()
    val isBusy by libraryViewModel.isBusy.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val activeBookFromList = (booksState as? UiState.Success)?.data?.firstOrNull { it.id == activeBookId }

    @Composable
    fun DrawerBodyContent(closeDrawer: () -> Unit) {
        Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            Surface(
                color = Navy900,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = PureWhite)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("LIVRO IA", style = MaterialTheme.typography.titleLarge, color = PureWhite)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = userDisplayName,
                        style = MaterialTheme.typography.labelLarge,
                        color = PureWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (userEmail.isNotBlank()) {
                        Text(
                            text = userEmail,
                            style = MaterialTheme.typography.bodySmall,
                            color = PureWhite.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val tabsToShow = WorkspaceTab.entries
            tabsToShow.forEach { tab ->
                val enabled = tab == WorkspaceTab.LIBRARY || activeBookId != null
                NavigationDrawerItem(
                    icon = { Icon(iconForTab(tab), contentDescription = null) },
                    label = { Text(tab.titlePt) },
                    selected = activeTab == tab,
                    onClick = {
                        if (enabled) {
                            activeTab = tab
                            closeDrawer()
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar("Selecione ou crie um livro na Biblioteca primeiro.")
                            }
                        }
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = Azure50,
                        selectedIconColor = Azure600,
                        selectedTextColor = Navy900
                    ),
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            NavigationDrawerItem(
                icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                label = { Text("Sair da conta Google") },
                selected = false,
                onClick = {
                    closeDrawer()
                    signOut(credentialManager, scope)
                }
            )
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= 1000.dp

        val mainScaffold: @Composable () -> Unit = {
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = Navy900
                        ),
                        navigationIcon = {
                            if (!isExpanded) {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Abrir menu lateral")
                                }
                            }
                        },
                        title = {
                            Column {
                                Text(
                                    text = if (activeTab == WorkspaceTab.LIBRARY) "LIVRO IA" else activeTab.titlePt,
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (activeBookFromList != null && activeTab != WorkspaceTab.LIBRARY) {
                                    Text(
                                        text = activeBookFromList.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Azure600,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    )
                },
                bottomBar = {
                    if (!isExpanded && activeBookId != null) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            val bottomTabs = listOf(
                                WorkspaceTab.LIBRARY,
                                WorkspaceTab.AUDIO,
                                WorkspaceTab.STRUCTURE,
                                WorkspaceTab.EDITOR,
                                WorkspaceTab.QUALITY,
                                WorkspaceTab.EXPORT
                            )
                            bottomTabs.forEach { tab ->
                                NavigationBarItem(
                                    selected = activeTab == tab,
                                    onClick = { activeTab = tab },
                                    icon = { Icon(iconForTab(tab), contentDescription = tab.titlePt) },
                                    label = { Text(tab.shortPt, maxLines = 1) }
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (activeTab == WorkspaceTab.LIBRARY || activeBookId == null) {
                        LibraryScreen(
                            booksState = booksState,
                            searchQuery = searchQuery,
                            isBusy = isBusy,
                            onSearchChange = libraryViewModel::setSearchQuery,
                            onCreateBook = { title, subtitle, author, subject, lang ->
                                libraryViewModel.createBook(title, subtitle, author, subject, lang) { newId ->
                                    activeBookId = newId
                                    activeTab = WorkspaceTab.AUDIO
                                }
                            },
                            onOpenBook = { book ->
                                activeBookId = book.id
                                activeTab = when {
                                    book.currentStep >= 12L -> WorkspaceTab.EXPORT
                                    book.currentStep >= 11L -> WorkspaceTab.QUALITY
                                    book.currentStep >= 10L -> WorkspaceTab.EDITOR
                                    book.currentStep >= 6L -> WorkspaceTab.STRUCTURE
                                    else -> WorkspaceTab.AUDIO
                                }
                            },
                            onDuplicateBook = libraryViewModel::duplicateBook,
                            onDeleteBook = { deletedId ->
                                if (activeBookId == deletedId) activeBookId = null
                                libraryViewModel.deleteBook(deletedId, context)
                            }
                        )
                    } else {
                        val bookId = activeBookId!!
                        key("$currentUserId:$bookId") {
                            BookWorkspaceContent(
                                currentUserId = currentUserId,
                                bookId = bookId,
                                activeTab = activeTab,
                                selectedChapterId = selectedChapterId,
                                onSelectChapter = { selectedChapterId = it },
                                onChangeTab = { activeTab = it },
                                onShowMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                            )
                        }
                    }
                }
            }
        }

        if (isExpanded) {
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet(
                        drawerContainerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.width(268.dp)
                    ) {
                        DrawerBodyContent(closeDrawer = {})
                    }
                },
                content = mainScaffold
            )
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.width(288.dp)
                    ) {
                        DrawerBodyContent(closeDrawer = { scope.launch { drawerState.close() } })
                    }
                },
                content = mainScaffold
            )
        }
    }
}

@Composable
private fun BookWorkspaceContent(
    currentUserId: String,
    bookId: String,
    activeTab: WorkspaceTab,
    selectedChapterId: String?,
    onSelectChapter: (String) -> Unit,
    onChangeTab: (WorkspaceTab) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val workspaceVm: BookWorkspaceViewModel = viewModel(
        key = "${currentUserId}_$bookId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                BookWorkspaceViewModel(BookRepository(db), currentUserId, bookId)
            }
        }
    )

    LaunchedEffect(workspaceVm) {
        workspaceVm.messages.collect { msg -> onShowMessage(msg) }
    }

    val bookState by workspaceVm.bookState.collectAsStateWithLifecycle()
    val recordings by workspaceVm.recordingsState.collectAsStateWithLifecycle()
    val chapters by workspaceVm.chaptersState.collectAsStateWithLifecycle()
    val versions by workspaceVm.versionsState.collectAsStateWithLifecycle()
    val latestQc by workspaceVm.latestQcState.collectAsStateWithLifecycle()
    val proposedStructure by workspaceVm.proposedStructure.collectAsStateWithLifecycle()
    val isAnalyzing by workspaceVm.isAnalyzingStructure.collectAsStateWithLifecycle()
    val generationProgress by workspaceVm.manuscriptGenerationProgress.collectAsStateWithLifecycle()
    val streamingPreview by workspaceVm.streamingChapterPreview.collectAsStateWithLifecycle()
    val autoSaveStatus by workspaceVm.autoSaveStatus.collectAsStateWithLifecycle()
    val canUndo by workspaceVm.canUndo.collectAsStateWithLifecycle()
    val isRunningRewrite by workspaceVm.isRunningRewrite.collectAsStateWithLifecycle()
    val pendingSuggestion by workspaceVm.pendingSuggestion.collectAsStateWithLifecycle()
    val isRunningQc by workspaceVm.isRunningQc.collectAsStateWithLifecycle()
    val isExporting by workspaceVm.isExporting.collectAsStateWithLifecycle()
    val lastExported by workspaceVm.lastExportedFile.collectAsStateWithLifecycle()

    when (val state = bookState) {
        is UiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Navy900)
            }
        }
        is UiState.Error -> {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(state.message, color = MaterialTheme.colorScheme.error)
            }
        }
        is UiState.Success -> {
            val book: BookProject = state.data ?: run {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Projeto não encontrado.")
                }
                return
            }

            when (activeTab) {
                WorkspaceTab.LIBRARY -> Unit
                WorkspaceTab.AUDIO -> AudioStudioScreen(
                    book = book,
                    recordings = recordings,
                    onImportAudioUris = { uris, translate -> workspaceVm.importAudioFiles(context, uris, translate) },
                    onAddRecordedAudio = { file, dur, title, translate -> workspaceVm.addRecordedAudio(context, file, dur, title, translate) },
                    onReprocessRecording = { rec -> workspaceVm.reprocessRecording(context, rec) },
                    onMoveRecording = workspaceVm::moveRecording,
                    onToggleTranslation = workspaceVm::toggleRecordingTranslation,
                    onRenameRecording = workspaceVm::renameRecording,
                    onDeleteRecording = workspaceVm::deleteRecording,
                    onGoToStructure = { onChangeTab(WorkspaceTab.STRUCTURE) }
                )
                WorkspaceTab.STRUCTURE -> StructureScreen(
                    book = book,
                    recordings = recordings,
                    chapters = chapters,
                    proposedStructure = proposedStructure,
                    isAnalyzing = isAnalyzing,
                    generationProgress = generationProgress,
                    streamingPreview = streamingPreview,
                    onProposeStructure = workspaceVm::proposeBookStructure,
                    onUpdateBookFields = workspaceVm::savePrePostTextualFields,
                    onUpdateProposedChapters = workspaceVm::updateProposedOutlineChapters,
                    onApproveAndGenerate = {
                        workspaceVm.approveStructureAndGenerateManuscript()
                    },
                    onOpenEditor = { onChangeTab(WorkspaceTab.EDITOR) }
                )
                WorkspaceTab.EDITOR -> ManuscriptEditorScreen(
                    book = book,
                    chapters = chapters,
                    versions = versions,
                    selectedChapterId = selectedChapterId,
                    onSelectChapter = onSelectChapter,
                    autoSaveStatus = autoSaveStatus,
                    canUndo = canUndo,
                    isRunningRewrite = isRunningRewrite,
                    pendingSuggestion = pendingSuggestion,
                    onUpdateChapter = { prev, upd -> workspaceVm.updateChapterWithAutoSave(prev, upd) },
                    onUndo = workspaceVm::undoLastChapterEdit,
                    onCreateChapter = workspaceVm::createNewChapter,
                    onDeleteChapter = workspaceVm::deleteChapter,
                    onMoveChapter = workspaceVm::moveChapter,
                    onSplitChapter = workspaceVm::splitChapterAtParagraph,
                    onMergeWithNext = workspaceVm::mergeChapterWithNext,
                    onRequestAiRewrite = workspaceVm::requestAiRewrite,
                    onAcceptSuggestion = workspaceVm::acceptAiSuggestion,
                    onRejectSuggestion = workspaceVm::rejectAiSuggestion,
                    onSaveVersion = workspaceVm::saveVersion,
                    onRestoreVersion = workspaceVm::restoreVersion
                )
                WorkspaceTab.QUALITY -> QualityControlScreen(
                    book = book,
                    chapters = chapters,
                    latestReport = latestQc,
                    isRunningQc = isRunningQc,
                    onRunQualityAudit = workspaceVm::runQualityAudit,
                    onJumpToChapter = { chapId ->
                        onSelectChapter(chapId)
                        onChangeTab(WorkspaceTab.EDITOR)
                    },
                    onGoToExport = { onChangeTab(WorkspaceTab.EXPORT) }
                )
                WorkspaceTab.EXPORT -> LayoutExportScreen(
                    book = book,
                    chapters = chapters,
                    isExporting = isExporting,
                    lastExported = lastExported,
                    onUpdateLayout = workspaceVm::updateLayoutConfig,
                    onExport = { fmt -> workspaceVm.exportBook(context, fmt) },
                    onCopyToDestination = { uri -> workspaceVm.copyLastExportToUri(context, uri) }
                )
            }
        }
    }
}

private fun iconForTab(tab: WorkspaceTab): ImageVector = when (tab) {
    WorkspaceTab.LIBRARY -> Icons.Default.LibraryBooks
    WorkspaceTab.AUDIO -> Icons.Default.GraphicEq
    WorkspaceTab.STRUCTURE -> Icons.Default.AccountTree
    WorkspaceTab.EDITOR -> Icons.Default.EditNote
    WorkspaceTab.QUALITY -> Icons.Default.FactCheck
    WorkspaceTab.EXPORT -> Icons.Default.Print
}
