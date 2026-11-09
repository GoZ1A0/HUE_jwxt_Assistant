package com.hebeu.emptyclassroom.ui.query

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hebeu.emptyclassroom.domain.model.CampusDetail
import com.hebeu.emptyclassroom.domain.model.QueryOptions
import com.hebeu.emptyclassroom.domain.model.SavedQuery
import com.hebeu.emptyclassroom.domain.repository.ClassroomRepository
import com.hebeu.emptyclassroom.domain.repository.PreferencesRepository
import com.hebeu.emptyclassroom.ui.common.AppErrorText
import com.hebeu.emptyclassroom.ui.common.ErrorKind
import com.hebeu.emptyclassroom.ui.common.UiError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * 查询页 ViewModel。
 *
 * 职责（architecture.md §5）：
 * - 加载查询选项（学期/校区/教学楼/类别/周次/节次），失败展示错误与重试；
 * - 维护可编辑草稿，校区变化时清空非法教学楼并按需加载该校区楼号（[ClassroomRepository.loadCampusDetail]）；
 * - 查询前校验周次/星期/节次各至少 1 个 + 座位区间；先调 [ClassroomRepository.checkTermOpen] 预检；
 * - 查询取消旧请求 + 请求序号防止旧响应覆盖新结果；重查保留旧结果（仅顶部进度条）；
 * - 分页 loadMore 不并发、失败可重试；
 * - 自动保存最近一次查询条件（不保存密码），下次启动恢复；
 * - 事件 channel 发送一次性 Snackbar 文案。
 */
class QueryViewModel(
    private val classroomRepository: ClassroomRepository,
    private val preferencesRepository: PreferencesRepository,
    private val todayWeekdayProvider: () -> Int = { com.hebeu.emptyclassroom.ui.common.Formatters.todayWeekday() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(QueryUiState())
    val uiState: StateFlow<QueryUiState> = _uiState.asStateFlow()

    private val _events = Channel<QueryEvent>(capacity = Channel.BUFFERED)
    val events: Flow<QueryEvent> = _events.receiveAsFlow()

    private val queryGeneration = AtomicInteger(0)
    private var queryJob: Job? = null
    private var loadMoreJob: Job? = null
    private var optionsJob: Job? = null
    private var campusDetailJob: Job? = null
    private var saveJob: Job? = null
    private var started = false

    /** 由 QueryScreen 首次组合时调用（登录后才进入本页，避免未登录时请求选项）。 */
    fun start() {
        if (started) return
        started = true
        optionsJob = viewModelScope.launch {
            val saved = runCatching { preferencesRepository.savedQuery.first() }.getOrNull()
            loadOptionsInternal(saved, isRefresh = false)
        }
    }

    // ---------------------------------------------------------------- 选项加载

    fun refreshOptions() {
        if (_uiState.value.isLoadingOptions) return
        optionsJob?.cancel()
        optionsJob = viewModelScope.launch {
            val current = _uiState.value
            val saved = if (current.options != null) {
                QueryStateFactory.buildSavedQuery(current.draft)
            } else {
                runCatching { preferencesRepository.savedQuery.first() }.getOrNull()
            }
            loadOptionsInternal(saved, isRefresh = current.options != null)
        }
    }

    private suspend fun loadOptionsInternal(saved: SavedQuery?, isRefresh: Boolean) {
        _uiState.update {
            it.copy(
                isLoadingOptions = true,
                optionsError = null,
                campusDetailError = null,
                // 刷新选项时不清空旧选项，避免整页闪烁；首次加载才显示骨架/进度。
                options = if (isRefresh) it.options else null,
            )
        }
        try {
            val options = classroomRepository.loadOptions()
            val draft = QueryStateFactory.buildDraft(
                options = options,
                saved = saved,
                todayWeekday = todayWeekdayProvider(),
            )
            _uiState.update {
                it.copy(
                    isLoadingOptions = false,
                    options = options,
                    optionsError = null,
                    draft = draft,
                )
            }
            ensureCampusDetail(options.defaultCampusId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            val error = AppErrorText.toUiError(t)
            _uiState.update {
                it.copy(
                    isLoadingOptions = false,
                    optionsError = error,
                    // 刷新失败时保留旧选项，仍可继续查询。
                    options = if (isRefresh) it.options else null,
                )
            }
            if (isRefresh) _events.send(QueryEvent.ShowMessage("选项刷新失败：${error.message}"))
        }
    }

    /** 切换校区后若没有预取到该校区楼号，按需加载（cxXqjc）。 */
    private fun ensureCampusDetail(campusId: String) {
        if (campusId.isBlank()) return
        val state = _uiState.value
        val buildings = QueryStateFactory.buildingsFor(state.options ?: return, campusId, state.campusDetails)
        if (buildings.isEmpty()) loadCampusDetail(campusId)
    }

    fun retryLoadCampusDetail() {
        val campusId = _uiState.value.draft.campusId ?: return
        loadCampusDetail(campusId)
    }

    private fun loadCampusDetail(campusId: String) {
        campusDetailJob?.cancel()
        campusDetailJob = viewModelScope.launch {
            _uiState.update { it.copy(isCampusDetailLoading = true, campusDetailError = null) }
            try {
                val detail = classroomRepository.loadCampusDetail(campusId)
                _uiState.update {
                    it.copy(
                        isCampusDetailLoading = false,
                        campusDetailError = null,
                        campusDetails = it.campusDetails + (campusId to detail),
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                val error = AppErrorText.toUiError(t)
                _uiState.update {
                    it.copy(isCampusDetailLoading = false, campusDetailError = error.message)
                }
            }
        }
    }

    // ---------------------------------------------------------------- 条件编辑

    private fun updateDraft(transform: (QueryDraft) -> QueryDraft) {
        _uiState.update { state ->
            state.copy(draft = transform(state.draft), validationMessage = null)
        }
        scheduleSave()
    }

    fun setTerm(termKey: String) {
        // 说明：代码层只保存 key；周次/节次数值来自当前 loadOptions() 返回的学期。
        // 若未来 data 层支持 loadOptions(termKey) 多学期选项刷新，可在此处追加调用。
        updateDraft { it.copy(termKey = termKey) }
    }

    fun setCampus(campusId: String) {
        updateDraft { draft ->
            if (draft.campusId == campusId) draft
            // 校区变化必须重置教学楼（旧楼号可能不属于新校区）。
            else draft.copy(campusId = campusId, buildingId = null)
        }
        ensureCampusDetail(campusId)
    }

    fun setBuilding(buildingId: String?) {
        updateDraft { it.copy(buildingId = buildingId?.takeIf { id -> id.isNotBlank() }) }
    }

    fun setCategory(categoryId: String?) {
        updateDraft { draft ->
            val normalized = categoryId?.takeIf { it.isNotBlank() }
            if (draft.categoryId == normalized) {
                draft
            } else {
                // 一级类别变化时清空二级类别，避免非法联动值。
                draft.copy(categoryId = normalized, subCategoryId = null)
            }
        }
    }

    fun setSubCategory(subCategoryId: String?) {
        updateDraft { it.copy(subCategoryId = subCategoryId?.takeIf { id -> id.isNotBlank() }) }
    }

    fun toggleWeek(week: Int) {
        val option = _uiState.value.weekOptions.firstOrNull { it.week == week }
        if (option != null && !option.selectable) return
        updateDraft { draft ->
            draft.copy(weeks = if (week in draft.weeks) draft.weeks - week else draft.weeks + week)
        }
    }

    fun toggleWeekday(weekday: Int) {
        if (weekday !in 1..7) return
        updateDraft { draft ->
            draft.copy(
                weekdays = if (weekday in draft.weekdays) draft.weekdays - weekday else draft.weekdays + weekday,
            )
        }
    }

    fun togglePeriod(period: Int) {
        updateDraft { draft ->
            draft.copy(periods = if (period in draft.periods) draft.periods - period else draft.periods + period)
        }
    }

    fun setMinSeats(text: String) {
        updateDraft { it.copy(minSeatsText = text.filter { ch -> ch.isDigit() }.take(4)) }
    }

    fun setMaxSeats(text: String) {
        updateDraft { it.copy(maxSeatsText = text.filter { ch -> ch.isDigit() }.take(4)) }
    }

    fun setRoomName(text: String) {
        updateDraft { it.copy(roomName = text.take(40)) }
    }

    fun setMoreExpanded(expanded: Boolean) {
        _uiState.update { it.copy(isMoreExpanded = expanded) }
    }

    fun toggleMoreExpanded() {
        _uiState.update { it.copy(isMoreExpanded = !it.isMoreExpanded) }
    }

    /** 清除「更多条件」（类别/二级类别/座位区间/场地名称），保留常用条件。 */
    fun clearMoreConditions() {
        updateDraft {
            it.copy(
                categoryId = null,
                subCategoryId = null,
                minSeatsText = "",
                maxSeatsText = "",
                roomName = "",
            )
        }
    }

    // ---------------------------------------------------------------- 查询

    fun query() {
        val state = _uiState.value
        val options = state.options ?: return
        validateOrExplain(state)?.let { message ->
            _uiState.update { it.copy(validationMessage = message) }
            return
        }
        val query = QueryStateFactory.buildQuery(
            draft = state.draft,
            options = options,
            periodsForCampus = state.periodsForSelectedCampus,
        ) ?: run {
            _uiState.update { it.copy(validationMessage = "查询条件不完整，请检查后重试") }
            return
        }

        val generation = queryGeneration.incrementAndGet()
        queryJob?.cancel()
        _uiState.update {
            it.copy(
                isQuerying = true,
                validationMessage = null,
                resultError = null,
                emptyResult = false,
                sessionExpired = false,
                loadMoreError = false,
                hasMore = false,
                activeQuery = query,
            )
        }
        persistDraft()

        queryJob = viewModelScope.launch {
            try {
                val termOpen = classroomRepository.checkTermOpen(query.term)
                if (!termOpen) {
                    if (generation == queryGeneration.get()) {
                        _uiState.update {
                            it.copy(
                                isQuerying = false,
                                result = null,
                                resultError = UiError(
                                    kind = ErrorKind.TERM_CLOSED,
                                    title = "场地预约未开放",
                                    message = AppErrorText.TERM_CLOSED,
                                    retryable = false,
                                ),
                                emptyResult = false,
                                hasMore = false,
                            )
                        }
                    }
                    return@launch
                }
                val result = classroomRepository.query(
                    query = query,
                    page = 1,
                    pageSize = PAGE_SIZE,
                )
                if (generation != queryGeneration.get()) return@launch
                val applied = QueryStateFactory.buildAppliedConditions(
                    query = query,
                    options = _uiState.value.options ?: options,
                    campusDetails = _uiState.value.campusDetails,
                )
                _uiState.update {
                    it.copy(
                        isQuerying = false,
                        result = result,
                        applied = applied,
                        resultError = null,
                        emptyResult = result.items.isEmpty(),
                        hasMore = result.page < result.totalPages,
                        loadMoreError = false,
                        activeQuery = query,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                if (generation != queryGeneration.get()) return@launch
                handleQueryFailure(t)
            }
        }
    }

    private fun handleQueryFailure(t: Throwable) {
        val error = AppErrorText.toUiError(t)
        if (error.kind == ErrorKind.SESSION_EXPIRED) {
            _uiState.update {
                it.copy(
                    isQuerying = false,
                    sessionExpired = true,
                    resultError = error,
                    emptyResult = false,
                    hasMore = false,
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                isQuerying = false,
                result = null,
                applied = it.applied,
                resultError = error,
                emptyResult = false,
                hasMore = false,
                loadMoreError = false,
            )
        }
    }

    /** 错误面板「重试」：用当前草稿重新查询；若草稿被改动则按新条件查询。 */
    fun retry() {
        query()
    }

    // ---------------------------------------------------------------- 分页

    fun loadMore() {
        val state = _uiState.value
        val result = state.result ?: return
        val query = state.activeQuery ?: return
        if (state.isLoadingMore || state.isQuerying || !state.hasMore) return
        val nextPage = result.page + 1
        // 同步置位，避免两次快速滚动/点击在协程启动前都通过检查造成并发分页请求。
        _uiState.update { it.copy(isLoadingMore = true, loadMoreError = false) }
        loadMoreJob = viewModelScope.launch {
            try {
                val pageResult = classroomRepository.query(
                    query = query,
                    page = nextPage,
                    pageSize = PAGE_SIZE,
                )
                _uiState.update { current ->
                    val base = current.result ?: return@update current
                    current.copy(
                        isLoadingMore = false,
                        result = base.copy(
                            items = base.items + pageResult.items,
                            total = pageResult.total,
                            page = pageResult.page,
                            totalPages = pageResult.totalPages,
                        ),
                        hasMore = pageResult.page < pageResult.totalPages,
                        loadMoreError = false,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                val error = AppErrorText.toUiError(t)
                if (error.kind == ErrorKind.SESSION_EXPIRED) {
                    _uiState.update { it.copy(isLoadingMore = false, sessionExpired = true) }
                } else {
                    _uiState.update { it.copy(isLoadingMore = false, loadMoreError = true) }
                    _events.send(QueryEvent.ShowMessage("加载更多失败，请重试"))
                }
            }
        }
    }

    fun retryLoadMore() {
        if (_uiState.value.isLoadingMore) return
        loadMore()
    }

    // ---------------------------------------------------------------- 会话/提示

    fun dismissSessionExpired() {
        _uiState.update { state ->
            val clearSessionError = state.resultError?.kind == ErrorKind.SESSION_EXPIRED
            state.copy(
                sessionExpired = false,
                resultError = if (clearSessionError) null else state.resultError,
            )
        }
    }

    /** 退出登录后重置一次性会话状态（保留选项与上次结果，回登录页不丢弃用户条件）。 */
    fun onLoggedOut() {
        queryJob?.cancel()
        loadMoreJob?.cancel()
        _uiState.update {
            it.copy(isQuerying = false, isLoadingMore = false, sessionExpired = false)
        }
    }

    fun consumeMessage() {
        // 一次性 Snackbar 由 QueryScreen 的 LaunchedEffect 收集 events 消费；此方法保留给
        // 未来需要主动清空提示的调用方（当前无状态可清），避免调用点破坏封装。
    }

    // ---------------------------------------------------------------- 校验/持久化

    private fun validateOrExplain(state: QueryUiState): String? {
        if (state.missingSelectionLabels.isNotEmpty()) {
            return "请至少选择 1 个" + state.missingSelectionLabels.joinToString("、")
        }
        if (state.hasInvalidSeatRange) {
            return "座位数下限不能大于上限"
        }
        if (state.periodsForSelectedCampus.isEmpty()) {
            return "节次选项未加载成功，请刷新选项后重试"
        }
        return null
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            persistDraft()
        }
    }

    private fun persistDraft() {
        val draft = _uiState.value.draft
        viewModelScope.launch {
            runCatching { preferencesRepository.saveQuery(QueryStateFactory.buildSavedQuery(draft)) }
        }
    }

    override fun onCleared() {
        queryJob?.cancel()
        loadMoreJob?.cancel()
        optionsJob?.cancel()
        campusDetailJob?.cancel()
        saveJob?.cancel()
        super.onCleared()
    }

    companion object {
        /** 首版每页 100 条（site-contract §5.1：showCount ≤100 被接受）。 */
        const val PAGE_SIZE = 100
        private const val SAVE_DEBOUNCE_MS = 400L

        fun factory(
            classroomRepository: ClassroomRepository,
            preferencesRepository: PreferencesRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { QueryViewModel(classroomRepository, preferencesRepository) }
        }
    }
}
