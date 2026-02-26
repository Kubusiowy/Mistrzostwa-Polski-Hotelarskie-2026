package com.example.ekh2026.data

import com.example.ekh2026.data.model.ApiErrorResponse
import com.example.ekh2026.data.model.CriterionDto
import com.example.ekh2026.data.model.JurorLoginRequest
import com.example.ekh2026.data.model.JurorSession
import com.example.ekh2026.data.model.JurorSnapshot
import com.example.ekh2026.data.model.ParticipantDto
import com.example.ekh2026.data.model.RefreshRequest
import com.example.ekh2026.data.model.ScoreDto
import com.example.ekh2026.data.model.UpsertMyScoreRequest
import com.example.ekh2026.data.network.AuthApi
import com.example.ekh2026.data.network.JurorApi
import com.example.ekh2026.data.storage.SessionStorage
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.Response

sealed interface RepoResult<out T> {
    data class Ok<T>(val data: T) : RepoResult<T>
    data class Err(val code: Int, val message: String) : RepoResult<Nothing>
}

class JurorRepository(
    private val authApi: AuthApi,
    private val jurorApi: JurorApi,
    private val sessionStorage: SessionStorage,
    private val moshi: Moshi = Moshi.Builder().build()
) {

    private val refreshMutex = Mutex()

    suspend fun checkJurorLoginStatus(): RepoResult<Boolean> {
        return safeApiCall {
            val response = authApi.jurorLoginStatus()
            if (response.isSuccessful) {
                RepoResult.Ok(response.body()?.enabled ?: true)
            } else {
                RepoResult.Err(response.code(), mapApiError(response))
            }
        }
    }

    suspend fun login(
        firstName: String,
        surName: String,
        adminPassword: String
    ): RepoResult<JurorSession> {
        return safeApiCall {
            val response = authApi.jurorLogin(
                JurorLoginRequest(
                    firstName = firstName,
                    surName = surName,
                    adminPassword = adminPassword
                )
            )

            if (!response.isSuccessful) {
                return@safeApiCall RepoResult.Err(response.code(), mapApiError(response))
            }

            val body = response.body()
                ?: return@safeApiCall RepoResult.Err(500, "Brak danych logowania z serwera.")

            val session = JurorSession(
                accessToken = body.accessToken,
                refreshToken = body.refreshToken,
                jurorName = body.jurorName,
                jurorSurname = body.jurorSurname,
                role = "JUROR"
            )

            sessionStorage.saveSession(session)
            RepoResult.Ok(session)
        }
    }

    suspend fun readSession(): JurorSession? = sessionStorage.readSession()

    suspend fun clearSession() {
        sessionStorage.clear()
    }

    suspend fun refreshAccessToken(): RepoResult<Unit> {
        return refreshMutex.withLock {
            val session = try {
                sessionStorage.readSession()
            } catch (_: Exception) {
                return@withLock RepoResult.Err(
                    0,
                    "Nie udało się odczytać zapisanej sesji."
                )
            } ?: return@withLock RepoResult.Err(
                401,
                "Brak aktywnej sesji jurora."
            )

            val response = try {
                authApi.refreshAccessToken(
                    authorization = bearer(session.refreshToken),
                    body = RefreshRequest(refreshToken = session.refreshToken)
                )
            } catch (_: Exception) {
                return@withLock RepoResult.Err(
                    0,
                    "Brak połączenia. Nie udało się odświeżyć sesji."
                )
            }

            if (!response.isSuccessful) {
                return@withLock RepoResult.Err(response.code(), mapApiError(response))
            }

            val newAccessToken = response.body()?.accessToken
                ?: return@withLock RepoResult.Err(
                    500,
                    "Brak nowego tokena dostępu w odpowiedzi od serwera."
                )

            try {
                sessionStorage.updateAccessToken(newAccessToken)
            } catch (_: Exception) {
                return@withLock RepoResult.Err(
                    0,
                    "Nie udało się zapisać nowego tokena."
                )
            }

            RepoResult.Ok(Unit)
        }
    }

    suspend fun loadAllData(): RepoResult<JurorSnapshot> {
        return safeAuthorizedCall { token ->
            val participantsResponse = jurorApi.getParticipants(token)
            if (!participantsResponse.isSuccessful) {
                return@safeAuthorizedCall RepoResult.Err(
                    participantsResponse.code(),
                    mapApiError(participantsResponse)
                )
            }

            val criteriaResponse = jurorApi.getCriteria(token)
            if (!criteriaResponse.isSuccessful) {
                return@safeAuthorizedCall RepoResult.Err(
                    criteriaResponse.code(),
                    mapApiError(criteriaResponse)
                )
            }

            val scoresResponse = jurorApi.getMyScores(token)
            if (!scoresResponse.isSuccessful) {
                return@safeAuthorizedCall RepoResult.Err(
                    scoresResponse.code(),
                    mapApiError(scoresResponse)
                )
            }

            RepoResult.Ok(
                JurorSnapshot(
                    participants = participantsResponse.body().orEmpty(),
                    criteria = criteriaResponse.body().orEmpty(),
                    scores = scoresResponse.body().orEmpty()
                )
            )
        }
    }

    suspend fun upsertMyScore(request: UpsertMyScoreRequest): RepoResult<ScoreDto> {
        return safeAuthorizedCall { token ->
            val response = jurorApi.upsertMyScore(token, request)
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return@safeAuthorizedCall RepoResult.Err(500, "Brak odpowiedzi po zapisie punktów.")
                RepoResult.Ok(body)
            } else {
                RepoResult.Err(response.code(), mapApiError(response))
            }
        }
    }

    private suspend fun <T> safeAuthorizedCall(
        apiCall: suspend (authorizationHeader: String) -> RepoResult<T>
    ): RepoResult<T> {
        return safeApiCall {
            val session = sessionStorage.readSession()
                ?: return@safeApiCall RepoResult.Err(401, "Brak aktywnej sesji jurora.")

            val firstTry = apiCall(bearer(session.accessToken))
            if (firstTry !is RepoResult.Err || firstTry.code != 401) {
                return@safeApiCall firstTry
            }

            when (val refreshResult = refreshAccessToken()) {
                is RepoResult.Ok -> {
                    val updatedSession = sessionStorage.readSession()
                        ?: return@safeApiCall RepoResult.Err(401, "Sesja wygasła. Zaloguj się ponownie.")
                    apiCall(bearer(updatedSession.accessToken))
                }

                is RepoResult.Err -> {
                    if (refreshResult.code == 401) {
                        sessionStorage.clear()
                        RepoResult.Err(401, "Sesja wygasła. Zaloguj się ponownie.")
                    } else {
                        RepoResult.Err(refreshResult.code, refreshResult.message)
                    }
                }
            }
        }
    }

    private suspend fun <T> safeApiCall(
        block: suspend () -> RepoResult<T>
    ): RepoResult<T> {
        return try {
            block()
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (_: Exception) {
            RepoResult.Err(0, "Brak połączenia z serwerem. Sprawdź sieć i adres API.")
        }
    }

    private fun bearer(token: String): String = "Bearer $token"

    private fun mapApiError(response: Response<*>): String {
        val parsedMessage = try {
            val adapter = moshi.adapter(ApiErrorResponse::class.java)
            val errorText = response.errorBody()?.string()
            adapter.fromJson(errorText.orEmpty())?.message
        } catch (_: Exception) {
            null
        }

        if (!parsedMessage.isNullOrBlank()) {
            return parsedMessage
        }

        return when (response.code()) {
            400 -> "Błędne dane. Sprawdź formularz i zakres punktów."
            401 -> "Niepoprawne dane logowania lub token."
            403 -> "Logowanie jurorów przez stronę WWW jest wyłączone."
            404 -> "Nie znaleziono wymaganych danych (uczestnik, kryterium lub juror)."
            429 -> "Zbyt wiele błędnych prób logowania. Odczekaj 60 sekund."
            else -> "Wystąpił błąd serwera (${response.code()})."
        }
    }
}
