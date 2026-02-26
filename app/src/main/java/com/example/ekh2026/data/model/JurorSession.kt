package com.example.ekh2026.data.model

data class JurorSession(
    val accessToken: String,
    val refreshToken: String,
    val role: String = "JUROR",
    val jurorName: String,
    val jurorSurname: String
)
