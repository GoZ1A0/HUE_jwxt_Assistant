package com.hebeu.emptyclassroom.data.local

import android.content.Context
import android.util.AtomicFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class SavedCredentials(val username: String, val password: String)

interface CredentialStore {
    fun load(): SavedCredentials?
    fun save(credentials: SavedCredentials)
    fun clear()
}

class EncryptedCredentialStore(context: Context, private val cipher: SecretCipher) : CredentialStore {
    private val file = AtomicFile(File(context.noBackupFilesDir, "auto-login.enc"))
    @Synchronized override fun load(): SavedCredentials? = runCatching {
        Json.decodeFromString<SavedCredentials>(cipher.decrypt(file.readFully().toString(Charsets.UTF_8)))
    }.getOrElse { clear(); null }
    @Synchronized override fun save(credentials: SavedCredentials) {
        val bytes = cipher.encrypt(Json.encodeToString(credentials)).toByteArray(Charsets.UTF_8)
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }
    @Synchronized override fun clear() { file.delete() }
}
