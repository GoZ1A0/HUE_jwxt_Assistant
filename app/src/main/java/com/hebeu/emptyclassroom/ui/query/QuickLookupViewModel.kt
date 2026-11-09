package com.hebeu.emptyclassroom.ui.query

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hebeu.emptyclassroom.domain.model.*
import com.hebeu.emptyclassroom.domain.repository.ClassroomRepository
import com.hebeu.emptyclassroom.ui.common.AppErrorText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.update
import java.time.LocalTime
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId

data class QuickLookupState(
    val loading: Boolean = false,
    val description: String = "新校区 · 基础一、二 · 自动匹配当前时间",
    val rooms: List<Classroom> = emptyList(),
    val queriedAt: Long? = null,
    val error: String? = null,
    val completed: Boolean = false,
    val availability: Map<String, RoomAvailability> = emptyMap(),
    val durationLoading: Boolean = false,
    val durationError: String? = null,
    val minMinutes: Int = 0,
)

class QuickLookupViewModel(
    private val repository: ClassroomRepository,
    private val clock: Clock = Clock.system(ZoneId.of("Asia/Shanghai")),
) : ViewModel() {
    private val _state = MutableStateFlow(QuickLookupState())
    val state = _state.asStateFlow()
    private var job: Job? = null
    private var options: QueryOptions? = null
    private var campus: CampusDetail? = null
    private var currentSlot: String? = null
    fun minimumMinutes(value: Int) { _state.update { it.copy(minMinutes = value) } }

    fun refresh(force: Boolean = true) {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            try {
                if (force || options == null) _state.value = _state.value.copy(loading = true, error = null)
                // 每次手动刷新重新读取当前学期，跨天/跨学期不会沿用旧校历。
                if (force) { options = null; campus = null }
                val opts = options ?: repository.loadOptions().also { options = it }
                val newCampus = opts.campuses.firstOrNull { it.name.contains("新校区") }
                    ?: error("查询选项中未找到新校区")
                val detail = campus ?: repository.loadCampusDetail(newCampus.id).also { campus = it }
                val now = LocalDateTime.now(clock)
                val plan = QuickLookupPlanner.plan(opts, detail.periods, now)
                val slot = "${now.toLocalDate()}-${plan.period.number}-${plan.isBetweenClasses}"
                if (!force && currentSlot == slot && _state.value.completed &&
                    clock.millis() - (_state.value.queriedAt ?: 0L) < 300_000) return@launch
                val minimum = _state.value.minMinutes
                _state.value = QuickLookupState(loading = true, description = plan.description, minMinutes = minimum)
                val buildings = detail.buildings.filter { QuickLookupPlanner.isStudyBuilding(it.name) }
                check(buildings.isNotEmpty()) { "查询选项中未找到基础一、二教学楼" }
                val term = opts.terms.first { it.key == opts.selectedTermKey }
                val rooms = mutableListOf<Classroom>()
                val queries = mutableListOf<Pair<ClassroomQuery, List<Classroom>>>()
                for (building in buildings) {
                    val query = ClassroomQuery(term, newCampus.id, building.id, setOf(plan.week),
                        setOf(plan.weekday), setOf(plan.period.number), null, null, null, null, null)
                    val found = loadAll(query)
                    rooms += found; queries += query to found
                }
                currentSlot = slot
                _state.value = QuickLookupState(description = plan.description,
                    rooms = rooms.distinctBy { it.id.ifBlank { "${it.buildingName}-${it.name}" } }
                        .sortedWith(compareBy({ it.buildingName }, { it.name })),
                    queriedAt = clock.millis(), completed = true, durationLoading = rooms.isNotEmpty(), minMinutes = minimum)
                val periodTimes = detail.periods.mapNotNull { period ->
                    val times = period.timeRange?.split('-') ?: return@mapNotNull null
                    runCatching { Triple(period.number, LocalTime.parse(times[0].trim()), LocalTime.parse(times[1].trim())) }.getOrNull()
                }.filter { it.third > it.second }.sortedBy { it.second }
                val position = periodTimes.indexOfFirst { it.first == plan.period.number }
                if (position >= 0) coroutineScope {
                    queries.map { (query, initial) -> async {
                        var available = initial.map { it.lookupKey() }.toSet()
                        val ranges = available.associateWith { RoomAvailability(now.toLocalDate().atTime(periodTimes[position].third), position == periodTimes.lastIndex) }.toMutableMap()
                        try {
                            for (index in position + 1..periodTimes.lastIndex) {
                                if (available.isEmpty()) break
                                val range = periodTimes.subList(position, index + 1)
                                val stillFree = loadAll(query.copy(periods = range.map { it.first }.toSet())).map { it.lookupKey() }.toSet()
                                (available - stillFree).forEach { id -> ranges[id] = RoomAvailability(now.toLocalDate().atTime(periodTimes[index].second)) }
                                available = available.intersect(stillFree)
                                available.forEach { id -> ranges[id] = RoomAvailability(now.toLocalDate().atTime(periodTimes[index].third), index == periodTimes.lastIndex) }
                                _state.update { it.copy(availability = it.availability + ranges) }
                            }
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) { _state.update { it.copy(durationError = "部分连续空闲时长未查全，已保留确认的时段") } }
                        _state.update { it.copy(availability = it.availability + ranges) }
                    } }.awaitAll()
                }
                _state.update { it.copy(durationLoading = false) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                currentSlot = null
                _state.value = QuickLookupState(error = if (e is IllegalStateException) e.message
                    else AppErrorText.toUiError(e).message)
            }
        }
    }
    private suspend fun loadAll(query: ClassroomQuery): List<Classroom> {
        val rooms = mutableListOf<Classroom>(); var page = 1
        do { val result = repository.query(query, page, 500); rooms += result.items; page++ } while (result.hasMore)
        return rooms
    }
}
