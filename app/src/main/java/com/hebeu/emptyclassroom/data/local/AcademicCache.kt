package com.hebeu.emptyclassroom.data.local

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.security.MessageDigest

interface AcademicCache {
    fun read(account: String, key: String): String?
    fun write(account: String, key: String, value: String)
    fun clear()
}

/** 只缓存解析后的展示字段，账号与学期隔离；不进入系统备份。 */
class FileAcademicCache(context: Context) : AcademicCache {
    private val directory = File(context.noBackupFilesDir, "academic-cache").apply { mkdirs() }
    private fun file(account: String, key: String): AtomicFile {
        val hash = MessageDigest.getInstance("SHA-256").digest("$account/$key".toByteArray())
            .joinToString("") { "%02x".format(it) }
        return AtomicFile(File(directory, "$hash.json"))
    }
    @Synchronized override fun read(account: String, key: String): String? = runCatching {
        file(account, key).readFully().toString(Charsets.UTF_8)
    }.getOrNull()
    @Synchronized override fun write(account: String, key: String, value: String) {
        val file = file(account, key)
        val stream = file.startWrite()
        try { stream.write(value.toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }
    @Synchronized override fun clear() { directory.listFiles()?.filter { it.isFile }?.forEach { it.delete() } }
}
