package com.hebeu.emptyclassroom.data.local

import android.content.Context
import android.util.AtomicFile
import com.hebeu.emptyclassroom.domain.model.TimetableWorkspace
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

interface TimetableWorkspaceStore {
    fun load(account: String): TimetableWorkspace
    fun save(account: String, workspace: TimetableWorkspace)
}

class FileTimetableWorkspaceStore(context: Context) : TimetableWorkspaceStore {
    private val directory = File(context.noBackupFilesDir, "timetables").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun file(account: String): AtomicFile {
        val id = MessageDigest.getInstance("SHA-256").digest(account.toByteArray()).joinToString("") { "%02x".format(it) }
        return AtomicFile(File(directory, "$id.json"))
    }
    @Synchronized override fun load(account: String): TimetableWorkspace = runCatching {
        json.decodeFromString<TimetableWorkspace>(file(account).readFully().toString(Charsets.UTF_8))
    }.getOrElse { TimetableWorkspace() }
    @Synchronized override fun save(account: String, workspace: TimetableWorkspace) {
        val value = json.encodeToString(workspace).toByteArray(Charsets.UTF_8)
        val file = file(account)
        val stream = file.startWrite()
        try { stream.write(value); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }
}
