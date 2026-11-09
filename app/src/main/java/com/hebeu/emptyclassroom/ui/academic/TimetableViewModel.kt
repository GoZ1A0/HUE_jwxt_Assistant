package com.hebeu.emptyclassroom.ui.academic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hebeu.emptyclassroom.data.local.TimetableWorkspaceStore
import com.hebeu.emptyclassroom.data.local.WakeUpBackupImporter
import com.hebeu.emptyclassroom.domain.model.*
import com.hebeu.emptyclassroom.domain.repository.AcademicRepository
import com.hebeu.emptyclassroom.domain.repository.StartupSyncGate
import com.hebeu.emptyclassroom.ui.common.AppErrorText
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class TimetableState(
    val schedule: ScheduleData? = null,
    val terms: List<AcademicTerm> = emptyList(),
    val workspace: TimetableWorkspace = TimetableWorkspace(),
    val week: Int = 1,
    val loading: Boolean = true,
    val error: String? = null,
    val notice: String? = null,
    val listMode: Boolean = false,
    val cached: Boolean = false,
    val reminderStatus: String = "",
    val showUpdateReview: Boolean = false,
    val grades: List<GradeRecord> = emptyList(),
    val syncWarning: String? = null,
    val startupSyncEnabled: Boolean = true,
) {
    val local: SavedTimetable? get() = workspace.profiles.firstOrNull { it.id == workspace.activeId }
    val name: String get() = local?.name ?: "教务课表"
    val appearance: TimetableAppearance get() = local?.appearance ?: workspace.schoolAppearance
    val colors: Map<String, String> get() = local?.colors ?: workspace.schoolColors
    val lessonTimes: Map<String, ImportedLessonTime> get() = local?.lessonTimes.orEmpty()
    val sync: AcademicSyncState? get() = workspace.academic[workspace.schoolTermKey]
    val exams: List<ExamInfo> get() = workspace.academic[schedule?.term?.key]?.exams.orEmpty()
    val maxWeek: Int get() {
        val start = schedule?.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val lastExam = if (start == null) 1 else exams.mapNotNull { exam -> exam.date?.let { date ->
            runCatching { (Math.floorDiv(java.time.temporal.ChronoUnit.DAYS.between(start, LocalDate.parse(date)), 7) + 1).toInt() }.getOrNull()
        } }.maxOrNull() ?: 1
        return maxOf(ScheduleViewModel.maxWeeks(schedule), lastExam).coerceIn(1, 60)
    }
}

class TimetableViewModel(
    private val repository: AcademicRepository,
    private val store: TimetableWorkspaceStore? = null,
    private val account: String = "local",
    private val updateReminders: (ScheduleData?, ReminderSettings, Map<String, ImportedLessonTime>, Map<String, String>, String, List<ExamInfo>) -> String = { _, _, _, _, _, _ -> "" },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val startupSync: StartupSyncGate = StartupSyncGate(),
    private val startupEnabled: Flow<Boolean> = flowOf(true),
    private val saveStartupEnabled: suspend (Boolean) -> Unit = {},
) : ViewModel() {
    private val _state = MutableStateFlow(TimetableState())
    val state = _state.asStateFlow()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var school: ScheduleData? = null
    private var request: Job? = null
    private var saving: Job? = null
    private var notifying: Job? = null
    init {
        viewModelScope.launch {
            val workspace = withContext(ioDispatcher) { store?.load(account) ?: TimetableWorkspace() }
            val enabled = startupEnabled.first()
            _state.update { it.copy(workspace = workspace, startupSyncEnabled = enabled) }
            school = workspace.academic[workspace.schoolTermKey]?.accepted
            val local = workspace.profiles.firstOrNull { it.id == workspace.activeId }
            (local?.schedule ?: school)?.let { activate(it, cached = local == null) }
                ?: _state.update { it.copy(loading = false) }
            try { repository.cachedGrades(null)?.let { saved -> _state.update { it.copy(grades = saved.records) } } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { }
            val firstStartup = startupSync.claim(account)
            if (enabled && firstStartup) refresh(checkSchool = true)
            startupEnabled.collect { value -> _state.update { it.copy(startupSyncEnabled = value) } }
        }
    }

    fun week(value: Int) { _state.update { it.copy(week = value.coerceIn(1, it.maxWeek)) } }
    fun today() { _state.update { it.copy(week = currentWeek(it)) } }
    private fun currentWeek(state: TimetableState): Int {
        val start = state.schedule?.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return 1
        return (Math.floorDiv(java.time.temporal.ChronoUnit.DAYS.between(start, LocalDate.now(ZoneId.of("Asia/Shanghai"))), 7) + 1)
            .toInt().coerceIn(1, state.maxWeek)
    }
    fun date(value: String) {
        val start = _state.value.schedule?.startDate?.let(LocalDate::parse) ?: return
        val days = java.time.temporal.ChronoUnit.DAYS.between(start, LocalDate.parse(value))
        val selected = (Math.floorDiv(days, 7) + 1).toInt()
        if (selected !in 1.._state.value.maxWeek) { message("考试日期 $value 在当前课表显示学期之外，请查看考试日程"); return }
        week(selected)
    }
    fun toggleList() { _state.update { it.copy(listMode = !it.listMode) } }
    fun dismissNotice() { _state.update { it.copy(notice = null) } }
    fun message(text: String) { _state.update { it.copy(notice = text) } }

    fun refresh(term: AcademicTerm? = null, checkSchool: Boolean = false) {
        if (_state.value.local != null && term == null && !checkSchool) { activate(_state.value.local!!.schedule); return }
        request?.cancel()
        request = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, syncWarning = null) }
            try {
                val options = repository.options()
                val selected = term ?: options.selected
                _state.update { it.copy(terms = options.terms) }
                val saved = _state.value.workspace.academic[selected.key]
                val cached = saved?.accepted ?: repository.cachedSchedule(selected)
                if (cached != null) {
                    school = cached
                    _state.update { it.copy(workspace = it.workspace.copy(schoolTermKey = selected.key,
                        academic = it.workspace.academic + (selected.key to (saved ?: AcademicSyncState(accepted = cached))))) }
                    if (_state.value.local == null) activate(cached, true, resetWeek = _state.value.schedule?.term?.key != cached.term.key)
                }
                repository.cachedGrades(null)?.let { grades -> _state.update { it.copy(grades = grades.records) } }
                val data = repository.schedule(selected)
                val warnings = mutableListOf<String>()
                suspend fun <T> extra(label: String, block: suspend () -> T): T? = try { block() }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { warnings += "$label：${AppErrorText.toUiError(e).message}"; null }
                val exams = extra("考试信息", { repository.exams(selected) })
                val notices = extra("调课消息", { repository.notices() })
                val grades = extra("课程成绩", { repository.grades(null) })
                val previous = _state.value.workspace.academic[selected.key] ?: AcademicSyncState(accepted = data)
                val relevant = notices?.filter { notice ->
                    val day = notice.createdAt.take(10)
                    data.startDate?.let { day >= it && day <= LocalDate.parse(it).plusWeeks(data.totalWeeks.toLong()).toString() } ?: true
                } ?: previous.notices
                var sync = previous.copy(latest = data, latestExams = exams ?: previous.latestExams,
                    notices = relevant, checkedAt = System.currentTimeMillis())
                if (!sync.pending) sync = sync.copy(accepted = data, exams = exams ?: sync.exams)
                school = sync.accepted
                _state.update { it.copy(workspace = it.workspace.copy(schoolTermKey = selected.key,
                    academic = it.workspace.academic + (selected.key to sync)), loading = false,
                    showUpdateReview = sync.pending && sync.batchKey !in it.workspace.deferredUpdates,
                    grades = grades?.records ?: it.grades, syncWarning = warnings.takeIf { it.isNotEmpty() }?.joinToString("\n")) }
                if (_state.value.local == null) school?.let { activate(it, resetWeek = _state.value.schedule?.term?.key != it.term.key) } else notifySchedule()
                persist()
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { _state.update { it.copy(loading = false, error = AppErrorText.toUiError(e).message) } }
        }
    }

    fun checkUpdates() = refresh(checkSchool = true)
    fun startupSyncEnabled(value: Boolean) {
        _state.update { it.copy(startupSyncEnabled = value) }
        viewModelScope.launch {
            try { saveStartupEnabled(value) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { _state.update { it.copy(startupSyncEnabled = !value) }; message("设置保存失败，请重试") }
        }
    }
    fun reviewUpdates() { _state.update { it.copy(showUpdateReview = true) } }
    fun deferUpdates() {
        val sync = _state.value.sync ?: return
        _state.update { it.copy(showUpdateReview = false, workspace = it.workspace.copy(deferredUpdates = (it.workspace.deferredUpdates + sync.batchKey).toList().takeLast(100).toSet())) }
        persist()
    }
    fun acceptUpdates(useSchool: Set<String> = emptySet()) {
        val state = _state.value; val sync = state.sync ?: return
        val remote = sync.latest ?: sync.accepted ?: return
        val linked = state.workspace.profiles.filter { it.schoolBase?.term?.key == remote.term.key }
        val nextProfiles = state.workspace.profiles.map { profile ->
            if (profile !in linked) profile else {
                val chosen = useSchool.filter { it.startsWith("${profile.id}|") }.map { it.substringAfter('|') }.toSet()
                val merged = AcademicChanges.merge(requireNotNull(profile.schoolBase), profile.schedule, remote, chosen)
                profile.copy(schedule = merged.schedule, schoolBase = remote)
            }
        }
        val history = AcademicRevision(System.currentTimeMillis(), "确认 ${sync.courseChanges.size} 门课程变更 · ${sync.latestExams?.size ?: sync.exams.size} 场考试",
            sync.accepted, sync.exams, linked)
        val next = sync.copy(accepted = remote, exams = sync.latestExams ?: sync.exams,
            handledNotices = sync.handledNotices + sync.notices.map { it.key }, history = (sync.history + history).takeLast(5))
        _state.update { it.copy(showUpdateReview = false, workspace = it.workspace.copy(profiles = nextProfiles,
            academic = it.workspace.academic + (remote.term.key to next)), notice = "教务更新已应用，可在同步记录中撤销") }
        school = remote
        activate(_state.value.local?.schedule ?: remote, resetWeek = false); persist()
    }
    fun undoUpdate(at: Long) {
        val state = _state.value; val sync = state.sync ?: return
        val revision = sync.history.firstOrNull { it.at == at && it.undoable } ?: return
        // 仅允许撤销最近一次确认，保持历史顺序。
        if (sync.history.lastOrNull { it.undoable }?.at != at) return
        val key = state.workspace.schoolTermKey ?: return
        val oldProfiles = revision.profiles.associateBy { it.id }
        val next = sync.copy(accepted = revision.schedule, exams = revision.exams,
            history = sync.history.map { if (it.at == at) it.copy(undoable = false, label = "已撤销 · ${it.label}") else it })
        _state.update { it.copy(workspace = it.workspace.copy(academic = it.workspace.academic + (key to next),
            profiles = it.workspace.profiles.map { p -> oldProfiles[p.id] ?: p }), notice = "已恢复本次更新前的课表与考试安排") }
        school = revision.schedule
        (_state.value.local?.schedule ?: school)?.let { activate(it, resetWeek = false) }; persist()
    }

    fun selectProfile(id: String) {
        request?.cancel()
        _state.update { it.copy(workspace = it.workspace.copy(activeId = id), error = null) }
        val profile = _state.value.local
        if (profile != null) activate(profile.schedule)
        else if (school != null) activate(school!!) else refresh()
        persist()
    }

    fun create(name: String = "我的课表", clone: Boolean = false) {
        val current = _state.value
        val today = LocalDate.now(ZoneId.of("Asia/Shanghai"))
        val data = current.schedule?.copy(lessons = if (clone) current.schedule.lessons else emptyList())
            ?: ScheduleData(AcademicTerm(today.year.toString(), "3", "自定义学期"), emptyList(), System.currentTimeMillis(),
                today.minusDays((today.dayOfWeek.value - 1).toLong()).toString())
        val profile = SavedTimetable(UUID.randomUUID().toString(), name.ifBlank { "我的课表" }, data,
            current.appearance, if (clone) current.colors else emptyMap(), if (clone) current.lessonTimes else emptyMap(),
            if (clone) current.local?.credits.orEmpty() else emptyMap(),
            if (clone) current.local?.schoolBase ?: current.schedule.takeIf { current.local == null } else null)
        _state.update { it.copy(workspace = it.workspace.copy(activeId = profile.id, profiles = it.workspace.profiles + profile)) }
        activate(data); persist()
    }

    fun importText(text: String) {
        try {
            val native = runCatching { json.decodeFromString<TimetableExport>(text) }.getOrNull()?.takeIf { it.format == "hebeu-timetable-v1" }
            val profile = if (native != null) native.timetable.copy(id = UUID.randomUUID().toString(), schoolBase = null) else {
                val imported = WakeUpBackupImporter.importBackup(text)
                val style = imported.importedStyle
                SavedTimetable(UUID.randomUUID().toString(), imported.name, imported.schedule,
                    TimetableAppearance(rowHeightDp = style.rowHeightDp.coerceIn(44, 150), textSizeSp = style.textSizeSp.coerceIn(9, 22),
                        cardAlpha = style.cardAlpha.coerceIn(0.2f, 1f), showTimes = style.showTimes,
                        showSaturday = style.showSaturday, showSunday = style.showSunday,
                        showOtherWeeks = style.showOtherWeeks, sundayFirst = style.sundayFirst,
                        nodes = style.nodes.coerceIn(1, 30), textColorArgb = style.textColorArgb,
                        courseTextColorArgb = style.courseTextColorArgb), imported.courseColors, imported.lessonTimes, imported.courseCredits)
            }
            request?.cancel()
            _state.update { it.copy(workspace = it.workspace.copy(activeId = profile.id, profiles = it.workspace.profiles + profile),
                notice = "已导入 ${profile.schedule.lessons.map { c -> c.name }.distinct().size} 门课程、${profile.schedule.lessons.size} 条排课") }
            activate(profile.schedule); persist()
        } catch (e: Exception) { message(e.message ?: "课表文件读取失败") }
    }

    fun exportText(): String {
        val state = _state.value
        val profile = state.local ?: SavedTimetable("school", state.name, requireNotNull(state.schedule), state.appearance, state.colors)
        return json.encodeToString(TimetableExport(timetable = profile))
    }

    fun appearance(value: TimetableAppearance) {
        val state = _state.value
        val local = state.local
        _state.update { it.copy(workspace = if (local == null) it.workspace.copy(schoolAppearance = value)
            else it.workspace.copy(profiles = it.workspace.profiles.map { p -> if (p.id == local.id) p.copy(appearance = value) else p })) }
        persist()
    }

    fun updateCalendar(name: String, date: String, weeks: Int, periods: Map<Int, String>) {
        val parsed = LocalDate.parse(date)
        if (_state.value.local == null) create("教务课表副本", true)
        val state = _state.value
        val profile = requireNotNull(state.local)
        val data = profile.schedule.copy(startDate = parsed.toString(), totalWeeks = weeks, periodTimes = periods)
        replace(profile.copy(name = name.ifBlank { profile.name }, schedule = data))
    }

    fun saveLesson(lesson: Lesson, color: String?) {
        if (_state.value.local == null) create("我的课表", true)
        val profile = requireNotNull(_state.value.local)
        val data = profile.schedule.copy(lessons = profile.schedule.lessons.filterNot { it.id == lesson.id } + lesson)
        val relatedIds = data.lessons.filter { it.name == lesson.name }.map { it.id }.toSet()
        val colors = (profile.colors - relatedIds - "name:${lesson.name}").let { clean ->
            if (color == null) clean else clean + relatedIds.associateWith { color } + ("name:${lesson.name}" to color)
        }
        val changedPeriods = profile.schedule.lessons.firstOrNull { it.id == lesson.id }?.periods?.let { it != lesson.periods } == true
        replace(profile.copy(schedule = data, colors = colors, lessonTimes = if (changedPeriods) profile.lessonTimes - lesson.id else profile.lessonTimes))
    }

    fun deleteLesson(id: String) {
        if (_state.value.local == null) create("教务课表副本", true)
        val profile = requireNotNull(_state.value.local)
        replace(profile.copy(schedule = profile.schedule.copy(lessons = profile.schedule.lessons.filterNot { it.id == id }),
            colors = profile.colors - id, lessonTimes = profile.lessonTimes - id, credits = profile.credits - id))
    }
    fun deleteProfile(id: String) {
        _state.update { it.copy(workspace = it.workspace.copy(profiles = it.workspace.profiles.filterNot { p -> p.id == id })) }
        if (_state.value.workspace.activeId == id) selectProfile("school") else persist()
    }
    fun reminderSettings(settings: ReminderSettings) {
        _state.update { it.copy(workspace = it.workspace.copy(reminders = settings)) }
        persist(); notifySchedule()
    }
    fun refreshReminderStatus() { notifySchedule() }
    private fun replace(profile: SavedTimetable) {
        _state.update { it.copy(workspace = it.workspace.copy(profiles = it.workspace.profiles.map { p -> if (p.id == profile.id) profile else p })) }
        activate(profile.schedule, resetWeek = false); persist()
    }
    private fun activate(data: ScheduleData, cached: Boolean = false, resetWeek: Boolean = true) {
        _state.update { it.copy(schedule = data).let { next -> next.copy(week = if (resetWeek) currentWeek(next) else next.week.coerceIn(1, next.maxWeek), loading = false, error = null, cached = cached) } }
        notifySchedule()
    }
    private fun notifySchedule() {
        notifying?.cancel()
        notifying = viewModelScope.launch {
        delay(100)
        val state = _state.value
        runCatching { withContext(ioDispatcher) { updateReminders(state.schedule, state.workspace.reminders, state.lessonTimes, state.colors, state.name, state.exams) } }
            .onSuccess { text -> _state.update { it.copy(reminderStatus = text) } }
            .onFailure { message(it.message ?: "提醒更新失败") }
    } }
    private fun persist() {
        saving?.cancel()
        saving = viewModelScope.launch {
            delay(150)
            try { withContext(ioDispatcher) { store?.save(account, _state.value.workspace) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message("课表保存失败，请重试") }
        }
    }
}
