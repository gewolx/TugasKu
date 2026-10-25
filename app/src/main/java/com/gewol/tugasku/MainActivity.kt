
package com.gewol.tugasku

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gewol.tugasku.ui.AboutScreen
import com.gewol.tugasku.ui.ArchiveScreen
import com.gewol.tugasku.ui.CalendarScreen
import com.gewol.tugasku.ui.SettingsScreen
import com.gewol.tugasku.ui.StatsScreen
import com.gewol.tugasku.ui.TaskEditorScreen
import com.gewol.tugasku.ui.TaskListScreen
import com.gewol.tugasku.ui.TugasKuTheme
import com.gewol.tugasku.ui.UpdateDialog
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val vm: TaskViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onResume() {
        super.onResume()
        vm.reload()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationHelper.ensureChannel(this)
        setContent {
            TugasKuApp(vm)
        }
    }
}

private const val NONE = -2L
private const val NEW = -1L

/** Kedalaman layar: dipakai untuk menentukan arah animasi (maju = geser dari kanan, mundur = dari kiri). */
private fun screenDepth(screen: Int): Int = when (screen) {
    0 -> 0
    2, 5 -> 2   // editor, tentang aplikasi
    else -> 1   // kalender, arsip, pengaturan, statistik
}

@Composable
fun TugasKuApp(vm: TaskViewModel = viewModel()) {
    val context = LocalContext.current

    // Izin notifikasi Android 13+
    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }

    // Cadangan & pemulihan data (file dipilih lewat pemilih file bawaan Android, tanpa izin penyimpanan)
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching { BackupManager.export(context, uri) }.isSuccess
                }
                Toast.makeText(
                    context,
                    context.getString(if (ok) R.string.backup_saved else R.string.backup_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { BackupManager.import(context, uri) }
                }
                val message = result.fold(
                    onSuccess = { count ->
                        vm.reloadAfterRestore()
                        context.resources.getQuantityString(R.plurals.restore_success, count, count)
                    },
                    onFailure = { e ->
                        val tooNew = (e as? BackupManager.BackupException)?.problem == BackupManager.Problem.TOO_NEW
                        context.getString(if (tooNew) R.string.restore_too_new else R.string.restore_invalid)
                    }
                )
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }

    // Cek pembaruan aplikasi (GitHub): otomatis saat dibuka, atau manual dari halaman Tentang
    var updateInfo by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }
    val checkForUpdate: (Boolean) -> Unit = { manual ->
        if (!checkingUpdate) {
            checkingUpdate = true
            scope.launch {
                val result = runCatching { UpdateChecker.check(context) }
                checkingUpdate = false
                result.onSuccess { info ->
                    if (info != null) {
                        updateInfo = info
                    } else if (manual) {
                        Toast.makeText(context, context.getString(R.string.update_latest), Toast.LENGTH_SHORT).show()
                    }
                }.onFailure {
                    if (manual) {
                        Toast.makeText(context, context.getString(R.string.update_check_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { checkForUpdate(false) }

    // Detak waktu agar label waktu selalu segar
    var now by remember {
        mutableLongStateOf(System.currentTimeMillis())
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    // State navigasi
    var editorId by rememberSaveable {
        mutableLongStateOf(NONE)
    }
    var showCalendar by rememberSaveable {
        mutableStateOf(false)
    }
    var showArchive by rememberSaveable {
        mutableStateOf(false)
    }
    var showSettings by rememberSaveable {
        mutableStateOf(false)
    }
    var showAbout by rememberSaveable {
        mutableStateOf(false)
    }
    var showStats by rememberSaveable {
        mutableStateOf(false)
    }

    // Posisi scroll Pengaturan disimpan di sini agar tidak kembali ke atas setelah dari halaman Tentang
    val settingsScroll = rememberScrollState()

    TugasKuTheme(themeMode = vm.themeMode, themeColor = vm.themeColor) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            // Layar aktif (urutan prioritas: editor > tentang > lainnya)
            // 0 = daftar
            // 1 = kalender
            // 2 = editor
            // 3 = arsip
            // 4 = pengaturan
            // 5 = tentang aplikasi
            // 6 = statistik
            val screen = when {
                editorId != NONE -> 2
                showAbout -> 5
                showCalendar -> 1
                showArchive -> 3
                showSettings -> 4
                showStats -> 6
                else -> 0
            }

            // Satu BackHandler untuk semua layar: selalu menutup layar yang sedang tampil
            BackHandler(enabled = screen != 0) {
                when (screen) {
                    2 -> editorId = NONE
                    5 -> showAbout = false
                    1 -> showCalendar = false
                    3 -> showArchive = false
                    4 -> showSettings = false
                    6 -> showStats = false
                }
            }

            updateInfo?.let { info ->
                UpdateDialog(info = info, onDismiss = { updateInfo = null })
            }

            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    val forward = screenDepth(targetState) >= screenDepth(initialState)
                    val dur = 300
                    if (forward) {
                        (slideInHorizontally(tween(dur)) { it / 4 } + fadeIn(tween(dur)))
                            .togetherWith(
                                slideOutHorizontally(tween(dur)) { -it / 4 } + fadeOut(tween(dur))
                            )
                    } else {
                        (slideInHorizontally(tween(dur)) { -it / 4 } + fadeIn(tween(dur)))
                            .togetherWith(
                                slideOutHorizontally(tween(dur)) { it / 4 } + fadeOut(tween(dur))
                            )
                    }
                },
                label = "screen"
            ) { current ->

                when (current) {
                    // Editor tugas
                    2 -> {
                        TaskEditorScreen(
                            existing = vm.tasks.find {
                                it.id == editorId
                            },
                            use24Hour = vm.use24Hour,
                            defaultReminders = vm.defaultReminders,
                            subjectColors = vm.subjectColors,
                            onSubjectColor = vm::setSubjectColor,
                            onSave = {
                                vm.save(it)
                                editorId = NONE
                            },
                            onBack = {
                                editorId = NONE
                            }
                        )
                    }

                    // Arsip dan sampah
                    3 -> {
                        ArchiveScreen(
                            archived = vm.archived,
                            trash = vm.trash,
                            use24Hour = vm.use24Hour,
                            now = now,
                            archiveDays = vm.archiveDays,
                            onBack = {
                                showArchive = false
                            },
                            onUnarchive = vm::unarchive,
                            onMoveToTrash = vm::delete,
                            onRestore = vm::restoreFromTrash,
                            onDeleteForever = vm::deleteForever,
                            onEmptyTrash = vm::emptyTrash
                        )
                    }

                    // Pengaturan
                    4 -> {
                        SettingsScreen(
                            scrollState = settingsScroll,
                            currentLanguage = LocaleHelper.getSaved(
                                context
                            ),
                            onSelectLanguage = { code ->
                                (context as? Activity)?.let {
                                    LocaleHelper.setLanguage(it, code)
                                }
                            },
                            themeMode = vm.themeMode,
                            onThemeMode = vm::setThemeMode,
                            themeColor = vm.themeColor,
                            onThemeColor = vm::setThemeColor,
                            use24Hour = vm.use24Hour,
                            onUse24Hour = vm::setUse24Hour,
                            snoozeMinutes = vm.snoozeMinutes,
                            onSnoozeMinutes = vm::setSnoozeMinutes,
                            defaultReminders = vm.defaultReminders,
                            onDefaultReminders = vm::setDefaultReminders,
                            archiveDays = vm.archiveDays,
                            onArchiveDays = vm::setArchiveDays,
                            weekStartsSunday = vm.weekStartsSunday,
                            onWeekStartsSunday = vm::setWeekStartsSunday,

                            onBackup = {
                                exportLauncher.launch("tugasku-backup-${LocalDate.now()}.json")
                            },
                            onRestore = {
                                importLauncher.launch(arrayOf("*/*"))
                            },

                            // Buka halaman About
                            onAbout = {
                                showAbout = true
                            },

                            onBack = {
                                showSettings = false
                            }
                        )
                    }

                    // Kalender
                    1 -> {
                        CalendarScreen(
                            tasks = vm.tasks,
                            use24Hour = vm.use24Hour,
                            now = now,
                            weekStartsSunday = vm.weekStartsSunday,
                            subjectColors = vm.subjectColors,
                            onBack = {
                                showCalendar = false
                            },
                            onEdit = {
                                editorId = it.id
                            }
                        )
                    }

                    // Tentang aplikasi
                    5 -> {
                        AboutScreen(
                            developerEmail = "gewol.dev@gmail.com",
                            onCheckUpdate = { checkForUpdate(true) },
                            checkingUpdate = checkingUpdate,
                            onBack = {
                                showAbout = false
                            }
                        )
                    }

                    // Statistik
                    6 -> {
                        StatsScreen(
                            tasks = vm.tasks + vm.archived,
                            now = now,
                            weekStartsSunday = vm.weekStartsSunday,
                            subjectColors = vm.subjectColors,
                            onBack = {
                                showStats = false
                            }
                        )
                    }

                    // Daftar tugas utama
                    else -> {
                        TaskListScreen(
                            tasks = vm.tasks,
                            use24Hour = vm.use24Hour,
                            now = now,
                            onOpenCalendar = {
                                showCalendar = true
                            },
                            onOpenSettings = {
                                showSettings = true
                            },
                            onOpenStats = {
                                showStats = true
                            },
                            archiveCount = vm.archived.size + vm.trash.size,
                            onOpenArchive = {
                                showArchive = true
                            },
                            onAdd = {
                                editorId = NEW
                            },
                            onEdit = {
                                editorId = it.id
                            },
                            onToggleDone = vm::toggleDone,
                            onUndoDone = vm::undoDone,
                            onDelete = vm::delete,
                            onRestore = vm::save,
                            subjectColors = vm.subjectColors,
                            sortByPriority = vm.sortByPriority,
                            onSortByPriority = vm::setSortByPriority
                        )
                    }
                }
            }
        }
    }
}
