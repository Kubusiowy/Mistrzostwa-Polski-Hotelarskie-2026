package com.example.ekh2026.data.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.ekh2026.data.model.JurorSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SecureSessionStorage(context: Context) : SessionStorage {

    private val preferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override suspend fun saveSession(session: JurorSession) = withContext(Dispatchers.IO) {
        preferences.edit()
            .putString(KEY_ACCESS_TOKEN, session.accessToken)
            .putString(KEY_REFRESH_TOKEN, session.refreshToken)
            .putString(KEY_ROLE, session.role)
            .putString(KEY_JUROR_NAME, session.jurorName)
            .putString(KEY_JUROR_SURNAME, session.jurorSurname)
            .apply()
    }

    override suspend fun readSession(): JurorSession? = withContext(Dispatchers.IO) {
        val accessToken = preferences.getString(KEY_ACCESS_TOKEN, null)
        val refreshToken = preferences.getString(KEY_REFRESH_TOKEN, null)
        val role = preferences.getString(KEY_ROLE, null)
        val jurorName = preferences.getString(KEY_JUROR_NAME, null)
        val jurorSurname = preferences.getString(KEY_JUROR_SURNAME, null)

        if (
            accessToken.isNullOrBlank() ||
            refreshToken.isNullOrBlank() ||
            role.isNullOrBlank() ||
            jurorName.isNullOrBlank() ||
            jurorSurname.isNullOrBlank()
        ) {
            return@withContext null
        }

        JurorSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            role = role,
            jurorName = jurorName,
            jurorSurname = jurorSurname
        )
    }

    override suspend fun updateAccessToken(accessToken: String) = withContext(Dispatchers.IO) {
        preferences.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .apply()
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        preferences.edit().clear().apply()
    }

    private companion object {
        private const val PREFS_FILE = "juror_secure_session"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_ROLE = "role"
        private const val KEY_JUROR_NAME = "juror_name"
        private const val KEY_JUROR_SURNAME = "juror_surname"
    }
}
