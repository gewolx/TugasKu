package com.gewol.tugasku

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import java.io.File

private const val DAY_MS = 24 * 60 * 60_000L

/**
 * Informasi untuk membatalkan "selesai".
 * [createdId] = id tugas minggu depan yang ikut dibuat (tugas mingguan), null jika tidak ada.
 */
data class DoneUndo(val original: Task, val createdId: Long?)

class TaskViewModel(app: Application) : AndroidViewModel(app) {

    private val store = TaskStore(app)
    private val ctx: Application get() = getApplication()

    /** Semua tugas, termasuk yang diarsipkan dan yang ada di sampah. */
    private var all by mutableStateOf(emptyList<Task>())

    /** Tugas yang tampil di daftar utama, kalender, dan widget. */
    val tasks: List<Task>
        get() = all.filter { it.deletedAt == 0L && !it.archived }

    /** Tugas selesai yang sudah masuk arsip (terbaru di atas). */
    val archived: List<Task>
        get() = all.filter { it.deletedAt == 0L && it.archived }
            .sortedByDescending { if (it.doneAt > 0L) it.doneAt else it.deadline }

    /** Tugas di sampah (yang terakhir dihapus di atas). */
    val trash: List<Task>
        get() = all.filter { it.deletedAt > 0L }.sortedByDescending { it.deletedAt }

    private val _themeMode = mutableStateOf(store.themeMode)
    val themeMode: Int get() = _themeMode.value

    private val _themeColor = mutableStateOf(store.themeColor)
    val themeColor: Int get() = _themeColor.value

    private val _use24Hour = mutableStateOf(store.use24Hour)
    val use24Hour: Boolean get() = _use24Hour.value

    private val _snoozeMinutes = mutableStateOf(store.snoozeMinutes)
    val snoozeMinutes: Int get() = _snoozeMinutes.value

    private val _defaultReminders = mutableStateOf(store.defaultReminders)
    val defaultReminders: List<Int> get() = _defaultReminders.value

    /** 0 = tidak pernah diarsipkan otomatis. */
    private val _archiveDays = mutableStateOf(store.archiveDays)
    val archiveDays: Int get() = _archiveDays.value

    private val _weekStartsSunday = mutableStateOf(store.weekStartsSunday)
    val weekStartsSunday: Boolean get() = _weekStartsSunday.value

    private val _sortByPriority = mutableStateOf(store.sortByPriority)
    val sortByPriority: Boolean get() = _sortByPriority.value

    private val _digestEnabled = mutableStateOf(store.digestEnabled)
    val digestEnabled: Boolean get() = _digestEnabled.value

    private val _digestMinutes = mutableStateOf(store.digestMinutes)
    val digestMinutes: Int get() = _digestMinutes.value

    /** Warna mata kuliah pilihan pengguna ([subjectKey] -> indeks palet). */
    private val _subjectColors = mutableStateOf(store.subjectColors)
    val subjectColors: Map<String, Int> get() = _subjectColors.value

    /** Teks yang baru dibagikan dari aplikasi lain; diambil sekali oleh layar utama lalu dikosongkan. */
    var pendingShare by mutableStateOf<SharedTask?>(null)
        private set

    fun receiveShare(task: SharedTask) {
        pendingShare = task
    }

    fun clearShare() {
        pendingShare = null
    }

    /** Riwayat notifikasi (terbaru di atas). */
    private val _notifications = mutableStateOf(NotificationHistory.load(app))
    val notifications: List<NotifRecord> get() = _notifications.value
    val unreadNotifCount: Int get() = _notifications.value.count { !it.read }

    init {
        all = maintain(store.load())
        sweepOrphanPhotos()
    }

    fun setThemeColor(index: Int) {
        _themeColor.value = index
        store.themeColor = index
    }

    fun setUse24Hour(value: Boolean) {
        _use24Hour.value = value
        store.use24Hour = value
    }

    fun setSnoozeMinutes(minutes: Int) {
        _snoozeMinutes.value = minutes
        store.snoozeMinutes = minutes
    }

    fun setDefaultReminders(list: List<Int>) {
        _defaultReminders.value = list
        store.defaultReminders = list
    }

    /** Terapkan langsung: tugas selesai yang sudah melewati jeda baru ikut diarsipkan. */
    fun setArchiveDays(days: Int) {
        _archiveDays.value = days
        store.archiveDays = days
        all = maintain(all)
        refreshWidget()
    }

    fun setWeekStartsSunday(value: Boolean) {
        _weekStartsSunday.value = value
        store.weekStartsSunday = value
    }

    fun setDigestEnabled(value: Boolean) {
        _digestEnabled.value = value
        store.digestEnabled = value
        DigestScheduler.schedule(ctx)
    }

    fun setDigestMinutes(minutes: Int) {
        _digestMinutes.value = minutes
        store.digestMinutes = minutes
        DigestScheduler.schedule(ctx)
    }

    fun setSortByPriority(value: Boolean) {
        _sortByPriority.value = value
        store.sortByPriority = value
    }

    fun setSubjectColor(subject: String, index: Int) {
        val key = subjectKey(subject)
        if (key.isEmpty() || _subjectColors.value[key] == index) return
        val updated = _subjectColors.value + (key to index)
        _subjectColors.value = updated
        store.subjectColors = updated
    }

    /** Muat ulang semua data dan pengaturan setelah file cadangan dipulihkan. */
    fun reloadAfterRestore() {
        _themeMode.value = store.themeMode
        _themeColor.value = store.themeColor
        _use24Hour.value = store.use24Hour
        _snoozeMinutes.value = store.snoozeMinutes
        _defaultReminders.value = store.defaultReminders
        _archiveDays.value = store.archiveDays
        _weekStartsSunday.value = store.weekStartsSunday
        _sortByPriority.value = store.sortByPriority
        _subjectColors.value = store.subjectColors
        _digestEnabled.value = store.digestEnabled
        _digestMinutes.value = store.digestMinutes
        DigestScheduler.schedule(ctx)
        all = maintain(store.load())
        refreshWidget()
    }

    /** Muat ulang dari penyimpanan (mis. setelah tugas diubah lewat tombol notifikasi). */
    fun reload() {
        all = maintain(store.load())
        reloadNotifications()
        refreshWidget()
    }

    /** Muat ulang riwayat notifikasi (setelah ada catatan baru dari receiver / pengecekan pembaruan). */
    fun reloadNotifications() {
        _notifications.value = NotificationHistory.load(ctx)
    }

    fun markNotificationRead(id: Long) {
        NotificationHistory.markRead(ctx, id)
        reloadNotifications()
    }

    fun markAllNotificationsRead() {
        NotificationHistory.markAllRead(ctx)
        reloadNotifications()
    }

    fun deleteNotification(id: Long) {
        NotificationHistory.delete(ctx, id)
        reloadNotifications()
    }

    fun clearNotifications() {
        NotificationHistory.clear(ctx)
        reloadNotifications()
    }

    fun save(task: Task) {
        all = all.filterNot { it.id == task.id } + task
        store.save(all)
        ReminderScheduler.schedule(ctx, task)
        refreshWidget()
    }

    /**
     * Tandai selesai / aktifkan lagi. Mengembalikan [DoneUndo] hanya saat tugas
     * baru saja ditandai selesai (supaya bisa diurungkan), selain itu null.
     */
    fun toggleDone(id: Long): DoneUndo? {
        val t = tasks.find { it.id == id } ?: return null
        if (t.done) {
            save(t.copy(done = false, doneAt = 0L))
            return null
        }
        // Tugas mingguan: tandai selesai, lalu buat tugas minggu depan.
        val now = System.currentTimeMillis()
        val next = t.nextWeekly(now)
        save(t.copy(done = true, repeatWeekly = false, doneAt = now))
        next?.let { save(it) }
        return DoneUndo(original = t, createdId = next?.id)
    }

    /** Batalkan "selesai": kembalikan tugas semula dan buang salinan minggu depan (jika ada). */
    fun undoDone(undo: DoneUndo) {
        undo.createdId?.let { discard(it) }
        save(undo.original)
    }

    /** Buang tugas begitu saja (tanpa sampah), mis. salinan mingguan yang dibatalkan. */
    private fun discard(id: Long) {
        if (all.none { it.id == id }) return
        all = all.filterNot { it.id == id }
        store.save(all)
        ReminderScheduler.cancel(ctx, id)
        refreshWidget()
    }

    /** Pindahkan ke sampah (belum dihapus permanen). */
    fun delete(id: Long) {
        if (all.none { it.id == id }) return
        val now = System.currentTimeMillis()
        all = all.map { if (it.id == id) it.copy(deletedAt = now) else it }
        store.save(all)
        ReminderScheduler.cancel(ctx, id)
        refreshWidget()
    }

    /** Keluarkan dari sampah. Tugas kembali ke daftar (atau arsip, jika sebelumnya diarsipkan). */
    fun restoreFromTrash(id: Long) {
        val t = all.find { it.id == id } ?: return
        save(t.copy(deletedAt = 0L))
    }

    /** Hapus permanen satu tugas dari sampah, beserta foto lampirannya. */
    fun deleteForever(id: Long) {
        val removed = all.filter { it.id == id }
        if (removed.isEmpty()) return
        all = all.filterNot { it.id == id }
        store.save(all)
        deleteUnusedPhotos(removed, all)
        refreshWidget()
    }

    fun emptyTrash() {
        val removed = all.filter { it.deletedAt > 0L }
        if (removed.isEmpty()) return
        all = all.filter { it.deletedAt == 0L }
        store.save(all)
        deleteUnusedPhotos(removed, all)
        refreshWidget()
    }

    /** Kembalikan tugas dari arsip ke daftar "Selesai". */
    fun unarchive(id: Long) {
        val t = all.find { it.id == id } ?: return
        // doneAt di-reset agar tidak langsung diarsipkan lagi.
        save(t.copy(archived = false, doneAt = System.currentTimeMillis()))
    }

    /** 0 = ikuti sistem, 1 = terang, 2 = gelap */
    fun setThemeMode(mode: Int) {
        _themeMode.value = mode
        store.themeMode = mode
    }

    /**
     * Pemeliharaan otomatis:
     * - sampah yang lebih tua dari [TRASH_DAYS] hari dihapus permanen,
     * - tugas selesai yang lebih tua dari jeda arsip (Pengaturan, 0 = tidak pernah) dipindah ke arsip.
     * Hasilnya disimpan jika ada perubahan.
     */
    private fun maintain(list: List<Task>): List<Task> {
        val now = System.currentTimeMillis()
        val archiveAfterDays = store.archiveDays

        val expired = list.filter { it.deletedAt > 0L && now - it.deletedAt >= TRASH_DAYS * DAY_MS }
        val expiredIds = expired.map { it.id }.toSet()

        val result = list.filterNot { it.id in expiredIds }.map { t ->
            // Data lama belum punya doneAt, pakai tenggat sebagai acuan.
            val reference = if (t.doneAt > 0L) t.doneAt else t.deadline
            if (archiveAfterDays > 0 && t.done && !t.archived && t.deletedAt == 0L &&
                now - reference >= archiveAfterDays * DAY_MS
            ) {
                t.copy(archived = true)
            } else {
                t
            }
        }

        if (result != list) {
            store.save(result)
            deleteUnusedPhotos(expired, result)
        }
        return result
    }

    /** Hapus file foto milik tugas yang dibuang, kecuali masih dipakai tugas lain. */
    private fun deleteUnusedPhotos(removed: List<Task>, remaining: List<Task>) {
        val inUse = remaining.flatMap { it.attachments }.toSet()
        removed.flatMap { it.attachments }
            .filter { !isLinkAttachment(it) && it !in inUse }
            .forEach { runCatching { File(it).delete() } }
    }

    /**
     * Bersihkan foto yatim (mis. foto yang dipilih di editor lalu dibatalkan).
     * Hanya file yang tidak dipakai tugas mana pun DAN lebih tua dari 1 hari,
     * supaya foto di editor yang sedang terbuka tidak ikut terhapus.
     */
    private fun sweepOrphanPhotos() {
        val files = File(ctx.filesDir, "attachments").listFiles() ?: return
        val inUse = all.flatMap { it.attachments }.toSet()
        val cutoff = System.currentTimeMillis() - DAY_MS
        files
            .filter { it.absolutePath !in inUse && it.lastModified() < cutoff }
            .forEach { runCatching { it.delete() } }
    }

    private fun refreshWidget() = TaskWidgetProvider.refresh(ctx)
}
