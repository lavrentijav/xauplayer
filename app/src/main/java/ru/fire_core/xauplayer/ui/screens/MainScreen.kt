package ru.fire_core.xauplayer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.fire_core.xauplayer.ui.screens.tabs.AccountTab
import ru.fire_core.xauplayer.ui.screens.tabs.BooksTab
import ru.fire_core.xauplayer.ui.screens.tabs.SettingsTab
import ru.fire_core.xauplayer.ui.screens.tabs.PlayerTab
import ru.fire_core.xauplayer.ui.screens.tabs.LogsTab
import ru.fire_core.xauplayer.ui.screens.tabs.MiniPlayer
import ru.fire_core.xauplayer.ui.screens.tabs.ChaptersListDialog
import ru.fire_core.xauplayer.ui.screens.tabs.SpeedSliderDialog
import ru.fire_core.xauplayer.ui.screens.tabs.FullScreenPlayerModal
import ru.fire_core.xauplayer.ui.viewmodel.PlayerViewModel
import ru.fire_core.xauplayer.ui.viewmodel.BooksViewModel
import ru.fire_core.xauplayer.ui.viewmodel.SettingsViewModel

@Composable
fun MainScreen(
    onLogout: () -> Unit = {},
    playerVm: PlayerViewModel = hiltViewModel(),
    booksVm: BooksViewModel = hiltViewModel(),
    settingsVm: SettingsViewModel = hiltViewModel()
) {
    var tab by remember { mutableStateOf(1) } // Автоматически открываем вкладку плеера
    var selectedBook by remember { mutableStateOf<ru.fire_core.xauplayer.data.local.entities.Book?>(null) }
    var showChaptersList by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showPlayerModal by remember { mutableStateOf(false) }
    
    val playerState by playerVm.uiState.collectAsState()
    val showLogs by settingsVm.showLogs.collectAsState()
    
    // Автоматически синхронизируем при входе в приложение
    LaunchedEffect(Unit) {
        // Это выполнится один раз при первом открытии MainScreen
        // PlayerTab сам загрузит последний прослушанный контент и синхронизирует данные
        playerVm.loadBooks()
    }
    
    // Получаем текущую книгу и главу для определения видимости мини-плеера
    val currentBook = playerState.selectedBook
    val currentChapter = playerState.selectedChapter
    
    // Высота мини-плеера: 8dp (верхний padding) + 12dp (верхний внутренний) + 56dp (обложка) + 12dp (нижний внутренний) + 8dp (нижний padding) + 80dp (отступ от нижней панели) = ~176dp
    // Округляем до 100dp для удобства (мини-плеер + отступ)
    val miniPlayerHeight = 100.dp
    val logsTabIndex = if (showLogs) 4 else -1
    val settingsTabIndex = 3
    val showMiniPlayer = tab != settingsTabIndex && tab != logsTabIndex && currentBook != null && currentChapter != null
    
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                BottomAppBar(actions = {
                    IconButton(onClick = { tab = 0 }) { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) }
                    IconButton(onClick = { tab = 1 }) { Icon(Icons.Default.PlayArrow, contentDescription = null) }
                    IconButton(onClick = { tab = 2 }) { Icon(Icons.Default.AccountCircle, contentDescription = null) }
                    IconButton(onClick = { tab = settingsTabIndex }) { Icon(Icons.Default.Settings, contentDescription = null) }
                    if (showLogs) {
                        IconButton(onClick = { tab = logsTabIndex }) { Icon(Icons.Default.BugReport, contentDescription = null) }
                    }
                })
            }
        ) { padding ->
            when (tab) {
                0 -> BooksTab(
                    Modifier
                        .padding(padding)
                        .padding(bottom = if (showMiniPlayer) miniPlayerHeight else 0.dp),
                    onBookSelected = { book ->
                        selectedBook = book
                        tab = 1 // Переключаемся на вкладку плеера
                    }
                )
                1 -> {
                    if (selectedBook != null) {
                        PlayerTab(
                            modifier = Modifier
                                .padding(padding)
                                .padding(bottom = if (showMiniPlayer) miniPlayerHeight else 0.dp),
                            initialBook = selectedBook,
                            vm = playerVm,
                            booksVm = booksVm,
                            showPlayerModal = showPlayerModal,
                            onShowPlayerModalChange = { showPlayerModal = it }
                        )
                    } else {
                        PlayerTab(
                            modifier = Modifier
                                .padding(padding)
                                .padding(bottom = if (showMiniPlayer) miniPlayerHeight else 0.dp),
                            vm = playerVm,
                            booksVm = booksVm,
                            showPlayerModal = showPlayerModal,
                            onShowPlayerModalChange = { showPlayerModal = it }
                        )
                    }
                }
                2 -> AccountTab(
                    Modifier
                        .padding(padding)
                        .padding(bottom = if (showMiniPlayer) miniPlayerHeight else 0.dp),
                    onLogout = onLogout
                )
                3 -> SettingsTab(Modifier.padding(padding))
                4 -> {
                    if (showLogs) {
                        LogsTab(Modifier.padding(padding))
                    } else {
                        SettingsTab(Modifier.padding(padding))
                    }
                }
                else -> AccountTab(
                    Modifier
                        .padding(padding)
                        .padding(bottom = if (showMiniPlayer) miniPlayerHeight else 0.dp),
                    onLogout = onLogout
                )
            }
        }
        
        // Мини-плеер внизу - показываем на всех вкладках кроме настроек (tab 3)
        // showMiniPlayer уже содержит проверку на null для currentBook и currentChapter
        if (showMiniPlayer) {
            MiniPlayer(
                book = currentBook,
                chapter = currentChapter,
                state = playerState,
                vm = playerVm,
                onClick = { showPlayerModal = true },
                onChapterClick = { showChaptersList = true },
                onPlayPause = {
                    if (playerState.isPlaying) {
                        playerVm.pause()
                    } else {
                        playerVm.resume()
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 80.dp) // Увеличенный отступ от нижней панели для предотвращения перекрытия кнопок
            )
        }
        
        // Диалог списка глав
        if (showChaptersList && currentBook != null) {
            ChaptersListDialog(
                chapters = playerState.chapters,
                selectedChapter = playerState.selectedChapter,
                bookId = currentBook.id,
                playbackSpeed = playerState.playbackSpeed,
                vm = playerVm,
                onDismiss = { showChaptersList = false },
                onChapterSelected = { chapter ->
                    playerVm.playChapter(chapter, currentBook)
                    showChaptersList = false
                }
            )
        }
        
        // Диалог выбора скорости с ползунком
        if (showSpeedDialog) {
            SpeedSliderDialog(
                currentSpeed = playerState.playbackSpeed,
                onSpeedSelected = { speed ->
                    playerVm.setSpeed(speed)
                    showSpeedDialog = false
                },
                onDismiss = { showSpeedDialog = false }
            )
        }
        
        // Полноэкранный модальный плеер - доступен из любой вкладки
        if (showPlayerModal && playerState.selectedBook != null) {
            FullScreenPlayerModal(
                vm = playerVm,
                state = playerState,
                onDismiss = { showPlayerModal = false },
                onSpeedClick = { showSpeedDialog = true }
            )
        }
    }
}


