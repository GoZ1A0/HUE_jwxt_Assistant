package com.hebeu.emptyclassroom.data.repository

import com.hebeu.emptyclassroom.data.local.AcademicCache
import com.hebeu.emptyclassroom.data.remote.AcademicApi
import com.hebeu.emptyclassroom.data.remote.CdjyApi
import com.hebeu.emptyclassroom.data.session.SessionGuard
import com.hebeu.emptyclassroom.data.session.SessionStateHolder
import com.hebeu.emptyclassroom.domain.model.*
import com.hebeu.emptyclassroom.domain.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import com.hebeu.emptyclassroom.domain.error.AppError

class AcademicRepositoryImpl(
    private val api: AcademicApi,
    private val calendarApi: CdjyApi,
    private val classroom: ClassroomRepository,
    private val holder: SessionStateHolder,
    private val guard: SessionGuard,
    private val cache: AcademicCache,
) : AcademicRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private fun account() = (holder.current as? SessionState.LoggedIn)?.username
        ?: throw CancellationException("No account")

    override suspend fun options(): AcademicOptions = withContext(Dispatchers.IO) {
        try {
            guard.run { api.options() }.also { cache.write(account(), "options", json.encodeToString(it)) }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            if (e is AppError.SessionExpired) throw e
            val saved = cache.read(account(), "options")?.let {
                runCatching { json.decodeFromString<AcademicOptions>(it) }.getOrNull()
            }
            saved ?: throw e
        }
    }

    override suspend fun schedule(term: AcademicTerm): ScheduleData = withContext(Dispatchers.IO) {
        val owner = account()
        val data = guard.run {
            val schedule = api.schedule(term)
            val opts = classroom.loadOptions()
            val campus = opts.campuses.firstOrNull { it.name.contains("新校区") }?.id ?: opts.defaultCampusId
            val calendar = calendarApi.fetchCalendar(term.asQueryTerm(), campus, null)
            val detail = calendarApi.fetchCampusDetail(term.asQueryTerm(), campus)
            schedule.copy(startDate = calendar.termInfo.startDate?.toString(), totalWeeks = calendar.termInfo.totalWeeks,
                periodTimes = detail.periods.mapNotNull { p -> p.timeRange?.let { p.number to it } }.toMap())
        }
        if (owner != account()) throw CancellationException("Account changed")
        cache.write(owner, "schedule-${term.key}", json.encodeToString(data))
        data
    }

    override suspend fun grades(term: AcademicTerm?): GradeData = withContext(Dispatchers.IO) {
        val owner = account()
        val data = guard.run {
            val overview = api.overview()
            val records = api.grades(term)
            val now = System.currentTimeMillis()
            GradeData(records, overview.first, overview.second, now, now)
        }
        if (owner != account()) throw CancellationException("Account changed")
        cache.write(owner, "grades-${term?.key ?: "all"}", json.encodeToString(data))
        data
    }

    override suspend fun cachedSchedule(term: AcademicTerm): ScheduleData? = withContext(Dispatchers.IO) {
        cache.read(account(), "schedule-${term.key}")?.let { runCatching { json.decodeFromString<ScheduleData>(it) }.getOrNull() }
    }
    override suspend fun exams(term: AcademicTerm): List<ExamInfo> = withContext(Dispatchers.IO) {
        val owner = account(); val data = guard.run { api.exams(term) }
        if (owner != account()) throw CancellationException("Account changed")
        data
    }
    override suspend fun notices(): List<SchoolNotice> = withContext(Dispatchers.IO) {
        val owner = account(); val data = guard.run { api.notices() }
        if (owner != account()) throw CancellationException("Account changed")
        data
    }
    override suspend fun cachedGrades(term: AcademicTerm?): GradeData? = withContext(Dispatchers.IO) {
        cache.read(account(), "grades-${term?.key ?: "all"}")?.let { runCatching { json.decodeFromString<GradeData>(it) }.getOrNull() }
    }
}
