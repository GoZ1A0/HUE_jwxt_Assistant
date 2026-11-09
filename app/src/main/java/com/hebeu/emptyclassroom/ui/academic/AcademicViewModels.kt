package com.hebeu.emptyclassroom.ui.academic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hebeu.emptyclassroom.domain.model.*
import com.hebeu.emptyclassroom.domain.repository.AcademicRepository
import com.hebeu.emptyclassroom.ui.common.AppErrorText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class ScheduleState(
    val terms: List<AcademicTerm> = emptyList(), val selected: AcademicTerm? = null,
    val data: ScheduleData? = null, val week: Int = 1, val loading: Boolean = false,
    val cached: Boolean = false, val error: String? = null, val listMode: Boolean = false,
)

class ScheduleViewModel(private val repository: AcademicRepository) : ViewModel() {
    private val _state = MutableStateFlow(ScheduleState())
    val state = _state.asStateFlow()
    private var job: Job? = null
    init { refresh() }
    fun refresh() { load(_state.value.selected) }
    fun selectTerm(term: AcademicTerm) { if (term != _state.value.selected) load(term) }
    fun setWeek(week: Int) { _state.update { it.copy(week = week.coerceIn(1, maxWeeks(it.data))) } }
    fun thisWeek() { _state.update { it.copy(week = currentWeek(it.data)) } }
    fun toggleMode() { _state.update { it.copy(listMode = !it.listMode) } }
    private fun load(requested: AcademicTerm?) {
        job?.cancel()
        job = viewModelScope.launch {
            val oldTerm = _state.value.selected
            _state.update { it.copy(loading = true, error = null,
                data = if (requested == null || requested == oldTerm) it.data else null) }
            try {
                val options = if (_state.value.terms.isEmpty()) repository.options() else null
                val term = requested ?: options?.selected ?: _state.value.selected ?: return@launch
                _state.update { it.copy(terms = options?.terms ?: it.terms, selected = term) }
                val cached = repository.cachedSchedule(term)
                if (cached != null) _state.update { it.copy(data = cached, cached = true,
                    week = if (oldTerm == term) it.week else currentWeek(cached)) }
                val data = repository.schedule(term)
                _state.update { it.copy(data = data, loading = false, cached = false,
                    week = if (oldTerm == term) it.week.coerceIn(1, maxWeeks(data)) else currentWeek(data)) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { _state.update { it.copy(loading = false, cached = it.data != null,
                error = AppErrorText.toUiError(e).message) } }
        }
    }
    companion object {
        fun maxWeeks(data: ScheduleData?): Int = maxOf(data?.totalWeeks ?: 20,
            data?.lessons?.flatMap { it.weeks }?.maxOrNull() ?: 1).coerceAtMost(60)
        fun currentWeek(data: ScheduleData?): Int {
            val start = data?.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return 1
            val today = LocalDate.now(ZoneId.of("Asia/Shanghai"))
            return (Math.floorDiv(ChronoUnit.DAYS.between(start, today), 7).toInt() + 1).coerceIn(1, maxWeeks(data))
        }
    }
}

data class GradesState(
    val terms: List<AcademicTerm> = emptyList(), val selected: AcademicTerm? = null,
    val data: GradeData? = null, val loading: Boolean = false, val cached: Boolean = false,
    val error: String? = null, val search: String = "",
)

class GradesViewModel(private val repository: AcademicRepository) : ViewModel() {
    private val _state = MutableStateFlow(GradesState())
    val state = _state.asStateFlow()
    private var job: Job? = null
    init { refresh() }
    fun refresh() { load(_state.value.selected) }
    fun selectTerm(term: AcademicTerm?) { if (term != _state.value.selected) load(term) }
    fun search(value: String) { _state.update { it.copy(search = value) } }
    private fun load(term: AcademicTerm?) {
        job?.cancel()
        val sameTerm = term == _state.value.selected
        _state.update { it.copy(selected = term, data = if (sameTerm) it.data else null, loading = true, error = null) }
        job = viewModelScope.launch {
            try {
                val cached = repository.cachedGrades(term)
                if (cached != null) _state.update { it.copy(data = cached, cached = true) }
                if (_state.value.terms.isEmpty()) {
                    val options = repository.options()
                    _state.update { it.copy(terms = options.terms) }
                }
                val data = repository.grades(term)
                _state.update { it.copy(data = data, loading = false, cached = false) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { _state.update { it.copy(loading = false, cached = it.data != null,
                error = AppErrorText.toUiError(e).message) } }
        }
    }
}
