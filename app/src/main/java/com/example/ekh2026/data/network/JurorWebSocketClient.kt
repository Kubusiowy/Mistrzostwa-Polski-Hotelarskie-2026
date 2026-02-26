package com.example.ekh2026.data.network

import com.example.ekh2026.data.model.CriterionDto
import com.example.ekh2026.data.model.JurorSnapshot
import com.example.ekh2026.data.model.ParticipantDto
import com.example.ekh2026.data.model.ScoreDto
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

class JurorWebSocketClient(
    private val httpClient: OkHttpClient,
    private val baseUrl: String,
    private val listener: Listener
) {

    interface Listener {
        fun onSocketConnected()
        fun onSnapshot(snapshot: JurorSnapshot)
        fun onAck(action: String, message: String)
        fun onError(action: String, message: String)
        fun onDisconnected(reason: String, httpCode: Int?)
    }

    private var webSocket: WebSocket? = null
    private val manualClose = AtomicBoolean(false)

    fun connect(accessToken: String) {
        disconnect()
        manualClose.set(false)

        val wsUrl = runCatching { buildWebSocketUrl(accessToken) }.getOrNull() ?: run {
            listener.onDisconnected("Nieprawidłowy adres WebSocket.", null)
            return
        }

        val request = runCatching {
            Request.Builder()
                .url(wsUrl)
                .build()
        }.getOrElse {
            listener.onDisconnected("Błędny URL WebSocket: ${it.message}", null)
            return
        }

        webSocket = runCatching {
            httpClient.newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        listener.onSocketConnected()
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        handleIncomingMessage(text)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        if (!manualClose.get()) {
                            listener.onDisconnected(
                                reason.ifBlank { "Połączenie zamknięte." },
                                null
                            )
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        if (!manualClose.get()) {
                            val message = response?.message ?: t.message ?: "Błąd połączenia WebSocket."
                            listener.onDisconnected(message, response?.code)
                        }
                    }
                }
            )
        }.getOrElse {
            listener.onDisconnected("Nie udało się uruchomić WebSocket: ${it.message}", null)
            null
        }
    }

    fun sendInit(): Boolean {
        return sendAction(
            action = "init",
            data = JSONObject()
        )
    }

    fun sendUpsertMyScore(
        participantId: String,
        criterionId: String,
        point: Int
    ): Boolean {
        return sendAction(
            action = "upsertMyScore",
            data = JSONObject().apply {
                put("participantId", participantId)
                put("criterionId", criterionId)
                put("point", point)
            }
        )
    }

    fun disconnect() {
        manualClose.set(true)
        webSocket?.close(1000, "manual")
        webSocket = null
    }

    private fun sendAction(action: String, data: JSONObject): Boolean {
        val payload = JSONObject().apply {
            put("action", action)
            put("data", data)
        }

        return webSocket?.send(payload.toString()) == true
    }

    private fun handleIncomingMessage(text: String) {
        val message = runCatching { JSONObject(text) }.getOrNull() ?: return
        when (message.optString("type")) {
            "connected" -> listener.onSocketConnected()
            "snapshot" -> {
                val payload = message.optJSONObject("payload") ?: JSONObject()
                listener.onSnapshot(parseSnapshot(payload))
            }

            "ack" -> {
                listener.onAck(
                    action = message.optString("action"),
                    message = message.optString("message")
                )
            }

            "error" -> {
                listener.onError(
                    action = message.optString("action"),
                    message = message.optString("message")
                )
            }
        }
    }

    private fun parseSnapshot(payload: JSONObject): JurorSnapshot {
        return JurorSnapshot(
            participants = parseParticipants(payload.optJSONArray("participants") ?: JSONArray()),
            criteria = parseCriteria(payload.optJSONArray("criteria") ?: JSONArray()),
            scores = parseScores(payload.optJSONArray("scores") ?: JSONArray())
        )
    }

    private fun parseParticipants(array: JSONArray): List<ParticipantDto> {
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    ParticipantDto(
                        id = item.optString("id"),
                        name = item.optString("name"),
                        surname = item.optString("surname"),
                        schoolName = item.optString("schoolName")
                    )
                )
            }
        }
    }

    private fun parseCriteria(array: JSONArray): List<CriterionDto> {
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    CriterionDto(
                        id = item.optString("id"),
                        categoryId = item.optString("categoryId"),
                        categoryName = item.optString("categoryName"),
                        name = item.optString("name"),
                        maxPoints = item.optInt("maxPoints", 0)
                    )
                )
            }
        }
    }

    private fun parseScores(array: JSONArray): List<ScoreDto> {
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    ScoreDto(
                        id = item.optString("id"),
                        jurorId = item.optString("jurorId"),
                        participantId = item.optString("participantId"),
                        criterionId = item.optString("criterionId"),
                        point = item.optInt("point", 0)
                    )
                )
            }
        }
    }

    private fun buildWebSocketUrl(accessToken: String): String? {
        val httpUrl = baseUrl.toHttpUrlOrNull() ?: return null
        val endpointHttpUrl = httpUrl.newBuilder()
            .addPathSegments("ws/juror/live")
            .addQueryParameter("token", accessToken)
            .build()

        val endpoint = endpointHttpUrl.toString()
        return when (endpointHttpUrl.scheme) {
            "https" -> endpoint.replaceFirst("https://", "wss://")
            "http" -> endpoint.replaceFirst("http://", "ws://")
            else -> null
        }
    }
}
