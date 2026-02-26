package com.example.ekh2026.data.storage

import com.example.ekh2026.data.model.JurorSession

interface SessionStorage {
    suspend fun saveSession(session: JurorSession)
    suspend fun readSession(): JurorSession?
    suspend fun updateAccessToken(accessToken: String)
    suspend fun clear()
}
