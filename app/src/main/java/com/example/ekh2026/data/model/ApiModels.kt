package com.example.ekh2026.data.model

data class JurorLoginStatusResponse(
    val enabled: Boolean
)

data class JurorLoginRequest(
    val firstName: String,
    val surName: String,
    val adminPassword: String
)

data class JurorLoginResponse(
    val jurorName: String,
    val jurorSurname: String,
    val accessToken: String,
    val refreshToken: String
)

data class RefreshRequest(
    val refreshToken: String
)

data class RefreshResponse(
    val accessToken: String
)

data class ParticipantDto(
    val id: String,
    val name: String,
    val surname: String,
    val schoolName: String
)

data class CriterionDto(
    val id: String,
    val categoryId: String,
    val categoryName: String,
    val name: String,
    val maxPoints: Int
)

data class ScoreDto(
    val id: String,
    val jurorId: String,
    val participantId: String,
    val criterionId: String,
    val point: Int
)

data class UpsertMyScoreRequest(
    val participantId: String,
    val criterionId: String,
    val point: Int
)

data class ApiErrorResponse(
    val code: String?,
    val message: String?,
    val path: String?,
    val timestamp: Long?
)

data class JurorSnapshot(
    val participants: List<ParticipantDto>,
    val criteria: List<CriterionDto>,
    val scores: List<ScoreDto>
)

fun scoreKey(participantId: String, criterionId: String): String = "$participantId|$criterionId"
