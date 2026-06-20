package com.example.fitassist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.Instant

class MainActivity : ComponentActivity() {

    companion object {
        // No Android, localhost é o próprio Android.
        // Troque pelo IP do Mac/Raspberry rodando Node-RED.
        private const val NODE_RED_URL = "http://192.168.2.83:1880/dashboard/current"

        // Se ainda não criou /dashboard/state, use:
        // private const val NODE_RED_URL = "http://192.168.0.23:1880/dashboard/current"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            FitAssistApp(
                nodeRedUrl = NODE_RED_URL
            )
        }
    }
}

data class DashboardState(
    val timestamp: String? = null,
    val bodyBattery: Int? = null,
    val charged: Int? = null,
    val drained: Int? = null,
    val feedbackLevel: String? = null,
    val lastEventImpact: Int? = null,
    val lastEventFeedback: String? = null,
    val heartRate: Int? = null,
    val power: Int? = null,
    val cadence: Int? = null,
    val coachMessage: String? = null,
    val error: String? = null
)

@Composable
fun FitAssistApp(nodeRedUrl: String) {
    MaterialTheme(
        colorScheme = darkColorScheme()
    ) {
        var state by remember { mutableStateOf(DashboardState()) }
        var loading by remember { mutableStateOf(true) }

        LaunchedEffect(Unit) {
            while (true) {
                loading = true
                state = fetchDashboardState(nodeRedUrl)
                loading = false
                delay(5000)
            }
        }

        DashboardScreen(
            state = state,
            loading = loading,
            nodeRedUrl = nodeRedUrl
        )
    }
}

suspend fun fetchDashboardState(url: String): DashboardState {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext DashboardState(
                        error = "HTTP ${response.code}"
                    )
                }

                val body = response.body?.string()
                    ?: return@withContext DashboardState(error = "Empty response")

                val json = JSONObject(body)

                DashboardState(
                    timestamp = json.optString("timestamp", json.optString("date", null)),
                    bodyBattery = json.optNullableInt("body_battery")
                        ?: json.optNullableInt("current_body_battery"),
                    charged = json.optNullableInt("charged"),
                    drained = json.optNullableInt("drained"),
                    feedbackLevel = json.optStringOrNull("feedback_level"),
                    lastEventImpact = json.optNullableInt("last_event_impact"),
                    lastEventFeedback = json.optStringOrNull("last_event_feedback"),
                    heartRate = json.optNullableInt("heart_rate"),
                    power = json.optNullableInt("power"),
                    cadence = json.optNullableInt("cadence"),
                    coachMessage = json.optStringOrNull("coach_message")
                        ?: generateFallbackCoachMessage(json),
                    error = null
                )
            }
        } catch (e: Exception) {
            DashboardState(
                error = e.message ?: "Unknown error"
            )
        }
    }
}

fun JSONObject.optNullableInt(key: String): Int? {
    return if (has(key) && !isNull(key)) optInt(key) else null
}

fun JSONObject.optStringOrNull(key: String): String? {
    return if (has(key) && !isNull(key)) optString(key) else null
}

fun generateFallbackCoachMessage(json: JSONObject): String {
    val bodyBattery = json.optNullableInt("body_battery")
        ?: json.optNullableInt("current_body_battery")

    val hr = json.optNullableInt("heart_rate")
    val power = json.optNullableInt("power")

    return when {
        bodyBattery != null && bodyBattery < 30 && hr != null && hr > 140 ->
            "Energia baixa e frequência cardíaca elevada. Reduza a intensidade."

        bodyBattery != null && bodyBattery < 30 ->
            "Body Battery baixo. Faça um treino leve e priorize recuperação."

        power != null && power > 200 ->
            "Potência alta. Controle o ritmo para evitar desgaste precoce."

        else ->
            "Dados recebidos. Mantenha o plano de treino."
    }
}

@Composable
fun DashboardScreen(
    state: DashboardState,
    loading: Boolean,
    nodeRedUrl: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            HeaderCard(loading = loading)

            if (state.error != null) {
                ErrorCard(
                    message = state.error,
                    nodeRedUrl = nodeRedUrl
                )
            }

            CoachCard(
                message = state.coachMessage ?: "Aguardando dados do Node-RED..."
            )

            BodyBatteryCard(
                bodyBattery = state.bodyBattery,
                charged = state.charged,
                drained = state.drained,
                feedback = state.feedbackLevel
            )

            MetricsRow(
                heartRate = state.heartRate,
                power = state.power,
                cadence = state.cadence
            )

            LastEventCard(
                impact = state.lastEventImpact,
                feedback = state.lastEventFeedback,
                timestamp = state.timestamp
            )
        }
    }
}

@Composable
fun HeaderCard(loading: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "FitAssist",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Dashboard embarcado",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 3.dp
            )
        } else {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    imageVector = Icons.Rounded.FitnessCenter,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
fun CoachCard(message: String) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Rounded.SmartToy,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = "Coach",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun BodyBatteryCard(
    bodyBattery: Int?,
    charged: Int?,
    drained: Int?,
    feedback: String?
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BatteryGauge(
                value = bodyBattery ?: 0,
                modifier = Modifier.size(120.dp)
            )

            Spacer(modifier = Modifier.width(20.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.BatteryChargingFull,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Body Battery",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "${bodyBattery ?: "--"} / 100",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Charged: ${charged ?: "--"}   Drained: ${drained ?: "--"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = feedback ?: "Sem feedback",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun BatteryGauge(
    value: Int,
    modifier: Modifier = Modifier
) {
    val progress = value.coerceIn(0, 100) / 100f

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(
                width = 14.dp.toPx(),
                cap = StrokeCap.Round
            )

            drawArc(
                color = androidx.compose.ui.graphics.Color.DarkGray,
                startAngle = -220f,
                sweepAngle = 260f,
                useCenter = false,
                style = stroke
            )

            drawArc(
                color = androidx.compose.ui.graphics.Color.White,
                startAngle = -220f,
                sweepAngle = 260f * progress,
                useCenter = false,
                style = stroke
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "BB",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun MetricsRow(
    heartRate: Int?,
    power: Int?,
    cadence: Int?
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        MetricCard(
            title = "HR",
            value = heartRate?.toString() ?: "--",
            unit = "bpm",
            icon = Icons.Rounded.Favorite,
            modifier = Modifier.weight(1f)
        )

        MetricCard(
            title = "Power",
            value = power?.toString() ?: "--",
            unit = "W",
            icon = Icons.Rounded.Speed,
            modifier = Modifier.weight(1f)
        )

        MetricCard(
            title = "Cadence",
            value = cadence?.toString() ?: "--",
            unit = "rpm",
            icon = Icons.Rounded.FitnessCenter,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    unit: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )

            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = unit,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun LastEventCard(
    impact: Int?,
    feedback: String?,
    timestamp: String?
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Último evento",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Impacto: ${impact ?: "--"}",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = feedback ?: "Sem feedback",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Atualizado: ${timestamp ?: "--"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ErrorCard(
    message: String,
    nodeRedUrl: String
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Text(
                text = "Erro ao ler Node-RED",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            Text(
                text = nodeRedUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}