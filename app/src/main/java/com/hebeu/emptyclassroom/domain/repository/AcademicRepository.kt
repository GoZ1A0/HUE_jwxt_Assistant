package com.hebeu.emptyclassroom.domain.repository

import com.hebeu.emptyclassroom.domain.model.*

interface AcademicRepository {
    suspend fun options(): AcademicOptions
    suspend fun schedule(term: AcademicTerm): ScheduleData
    suspend fun grades(term: AcademicTerm?): GradeData
    suspend fun cachedSchedule(term: AcademicTerm): ScheduleData?
    suspend fun cachedGrades(term: AcademicTerm?): GradeData?
    suspend fun exams(term: AcademicTerm): List<ExamInfo> = emptyList()
    suspend fun notices(): List<SchoolNotice> = emptyList()
}
