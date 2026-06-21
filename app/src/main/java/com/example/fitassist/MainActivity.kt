package com.example.fitassist

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.*

data class LiveSensorState(
    val heartRate: Int? = null,
    val power: Int? = null,
    val cadence: Int? = null,
    val error: String? = null,
    val statusMessage: String = "Iniciando..."
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        if (permissions.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) {
            requestPermissions(permissions.toTypedArray(), 1001)
        }

        setContent {
            FitAssistApp(nodeRedUrl = "http://192.168.2.83:1880/dashboard/current")
        }
    }
}

@Composable
fun FitAssistApp(nodeRedUrl: String) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    
    MaterialTheme(colorScheme = darkColorScheme()) {
        var dashboardState by remember { mutableStateOf(DashboardState()) }
        var loading by remember { mutableStateOf(true) }
        var liveState by remember { mutableStateOf(LiveSensorState()) }

        val sensorManager = remember {
            BleSensorManager(
                context = context,
                onHeartRate = { hr: Int -> mainHandler.post { liveState = liveState.copy(heartRate = hr, error = null) } },
                onPower = { pwr: Int -> mainHandler.post { liveState = liveState.copy(power = pwr, error = null) } },
                onCadence = { cad: Int -> mainHandler.post { liveState = liveState.copy(cadence = cad, error = null) } },
                onError = { err: String -> mainHandler.post { liveState = liveState.copy(error = err, statusMessage = "Erro: $err") } },
                onStatus = { msg: String -> mainHandler.post { liveState = liveState.copy(statusMessage = msg) } }
            )
        }

        LaunchedEffect(Unit) {
            while (true) {
                val hasScan = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val hasBleS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                } else true

                if (hasScan && hasBleS) {
                    sensorManager.startScan()
                    break
                }
                delay(3000)
            }
        }

        LaunchedEffect(Unit) {
            while (true) {
                loading = true
                dashboardState = fetchDashboardState(nodeRedUrl)
                loading = false
                delay(5000)
            }
        }

        DashboardScreen(
            state = dashboardState.copy(
                heartRate = liveState.heartRate ?: dashboardState.heartRate,
                power = liveState.power ?: dashboardState.power,
                cadence = liveState.cadence ?: dashboardState.cadence,
                error = dashboardState.error ?: liveState.error
            ),
            loading = loading,
            nodeRedUrl = nodeRedUrl,
            bleStatus = liveState.statusMessage
        )
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

suspend fun fetchDashboardState(url: String): DashboardState {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext DashboardState(error = "HTTP ${response.code}")
                val body = response.body?.string() ?: return@withContext DashboardState(error = "Empty response")
                val json = JSONObject(body)
                DashboardState(
                    timestamp = json.optStringOrNull("timestamp") ?: json.optStringOrNull("date"),
                    bodyBattery = json.optNullableInt("body_battery") ?: json.optNullableInt("current_body_battery"),
                    charged = json.optNullableInt("charged"),
                    drained = json.optNullableInt("drained"),
                    feedbackLevel = json.optStringOrNull("feedback_level"),
                    lastEventImpact = json.optNullableInt("last_event_impact"),
                    lastEventFeedback = json.optStringOrNull("last_event_feedback"),
                    heartRate = json.optNullableInt("heart_rate"),
                    power = json.optNullableInt("power"),
                    cadence = json.optNullableInt("cadence"),
                    coachMessage = json.optStringOrNull("coach_message") ?: generateFallbackCoachMessage(json),
                    error = null
                )
            }
        } catch (e: Exception) {
            DashboardState(error = e.message ?: "Unknown error")
        }
    }
}

fun JSONObject.optNullableInt(key: String): Int? = if (has(key) && !isNull(key)) optInt(key) else null
fun JSONObject.optStringOrNull(key: String): String? = if (has(key) && !isNull(key)) optString(key) else null

fun generateFallbackCoachMessage(json: JSONObject): String {
    val bb = json.optNullableInt("body_battery") ?: json.optNullableInt("current_body_battery")
    return if (bb != null && bb < 30) "Body Battery baixo. Priorize recuperação." else "Mantenha o plano de treino."
}

@Composable
fun DashboardScreen(state: DashboardState, loading: Boolean, nodeRedUrl: String, bleStatus: String) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize()) {
            HeaderCard(loading = loading, bleStatus = bleStatus)
            if (state.error != null) ErrorCard(message = state.error, nodeRedUrl = nodeRedUrl)
            CoachCard(message = state.coachMessage ?: "Aguardando dados...")
            BodyBatteryCard(bodyBattery = state.bodyBattery, charged = state.charged, drained = state.drained, feedback = state.feedbackLevel)
            MetricsRow(heartRate = state.heartRate, power = state.power, cadence = state.cadence)
            LastEventCard(impact = state.lastEventImpact, feedback = state.lastEventFeedback, timestamp = state.timestamp)
        }
    }
}

@Composable
fun HeaderCard(loading: Boolean, bleStatus: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(text = "FitAssist", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(text = bleStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        if (loading) CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
        else Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(imageVector = Icons.Rounded.FitnessCenter, contentDescription = null, modifier = Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
fun CoachCard(message: String) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                Icon(imageVector = Icons.Rounded.SmartToy, contentDescription = null, modifier = Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(text = "Coach", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun BodyBatteryCard(bodyBattery: Int?, charged: Int?, drained: Int?, feedback: String?) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            BatteryGauge(value = bodyBattery ?: 0, modifier = Modifier.size(120.dp))
            Spacer(modifier = Modifier.width(20.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Rounded.BatteryChargingFull, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Body Battery", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Text(text = "${bodyBattery ?: "--"} / 100", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(text = "C: ${charged ?: "--"}  D: ${drained ?: "--"}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = feedback ?: "Sem feedback", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun BatteryGauge(value: Int, modifier: Modifier = Modifier) {
    val progress = value.coerceIn(0, 100) / 100f
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
            drawArc(color = androidx.compose.ui.graphics.Color.DarkGray, startAngle = -220f, sweepAngle = 260f, useCenter = false, style = stroke)
            drawArc(color = androidx.compose.ui.graphics.Color.White, startAngle = -220f, sweepAngle = 260f * progress, useCenter = false, style = stroke)
        }
        Text(text = value.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MetricsRow(heartRate: Int?, power: Int?, cadence: Int?) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        MetricCard(title = "HR", value = heartRate?.toString() ?: "--", unit = "bpm", icon = Icons.Rounded.Favorite, modifier = Modifier.weight(1f))
        MetricCard(title = "Power", value = power?.toString() ?: "--", unit = "W", icon = Icons.Rounded.Speed, modifier = Modifier.weight(1f))
        MetricCard(title = "Cadence", value = cadence?.toString() ?: "--", unit = "rpm", icon = Icons.Rounded.FitnessCenter, modifier = Modifier.weight(1f))
    }
}

@Composable
fun MetricCard(title: String, value: String, unit: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(text = title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(text = unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun LastEventCard(impact: Int?, feedback: String?, timestamp: String?) {
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(text = "Último evento", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(text = "Impacto: ${impact ?: "--"}", style = MaterialTheme.typography.bodyMedium)
            Text(text = feedback ?: "Sem feedback", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = "Atualizado: ${timestamp ?: "--"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ErrorCard(message: String, nodeRedUrl: String) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = "Erro ao ler Node-RED", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(text = message, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

class BleSensorManager(
    private val context: Context,
    private val onHeartRate: (Int) -> Unit,
    private val onPower: (Int) -> Unit,
    private val onCadence: (Int) -> Unit,
    private val onError: (String) -> Unit,
    private val onStatus: (String) -> Unit
) {
    private val HR_SERVICE = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    private val HR_MEAS = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    private val CP_SERVICE = UUID.fromString("00001818-0000-1000-8000-00805f9b34fb")
    private val CP_MEAS = UUID.fromString("00002a63-0000-1000-8000-00805f9b34fb")
    private val CSC_SERVICE = UUID.fromString("00001816-0000-1000-8000-00805f9b34fb")
    private val CSC_MEAS = UUID.fromString("00002a5b-0000-1000-8000-00805f9b34fb")
    private val CONFIG_DESC = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter = bluetoothManager.adapter
    private val scanner get() = adapter?.bluetoothLeScanner
    
    private val activeGatts = mutableMapOf<String, BluetoothGatt>()
    private val subscriptionQueues = mutableMapOf<String, MutableList<BluetoothGattDescriptor>>()
    private val calcState = mutableMapOf<String, Pair<Int, Int>>() // Key = addr+uuid

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (adapter == null || !adapter.isEnabled) return onStatus("Bluetooth desligado")
        onStatus("Buscando sensores...")
        try {
            scanner?.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback)
        } catch (e: Exception) { Log.e("FitAssistBLE", "Scan error", e) }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (activeGatts.containsKey(device.address)) return
            val name = try { device.name ?: "" } catch (e: Exception) { "" }
            val uuids = result.scanRecord?.serviceUuids?.map { it.uuid } ?: emptyList()
            
            val isHr = uuids.contains(HR_SERVICE) || name.contains("Heart", true) || name.contains("HRM", true)
            val isTrainer = uuids.contains(CP_SERVICE) || uuids.contains(CSC_SERVICE) || 
                             name.contains("Trainer", true) || name.contains("KICKR", true) || 
                             name.contains("Tacx", true) || name.contains("Think", true) || name.contains("Elite", true)
            
            if (isHr || isTrainer) {
                Log.i("FitAssistBLE", "Connecting to: $name [${device.address}]")
                activeGatts[device.address] = device.connectGatt(context, false, gattCallback)
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.requestMtu(512)
                gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                val addr = gatt.device.address
                activeGatts.remove(addr)
                subscriptionQueues.remove(addr)
                gatt.close()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            gatt.discoverServices()
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val addr = gatt.device.address
            val queue = mutableListOf<BluetoothGattDescriptor>()
            subscriptionQueues[addr] = queue
            addSubscription(gatt, HR_SERVICE, HR_MEAS, queue)
            addSubscription(gatt, CP_SERVICE, CP_MEAS, queue)
            addSubscription(gatt, CSC_SERVICE, CSC_MEAS, queue)
            processNextSub(gatt)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, char: BluetoothGattCharacteristic, value: ByteArray) {
            val addr = gatt.device.address
            when (char.uuid) {
                HR_MEAS -> onHeartRate(parseHR(value))
                CP_MEAS -> {
                    val power = parseCP(value)
                    Log.d("FitAssistBLE", "Power: $power W | raw: ${value.joinToString(" ") { String.format("%02x", it) }}")
                    onPower(power)
                    parseCPCadence(addr, value)?.let { onCadence(it) }
                }
                CSC_MEAS -> parseCSCCadence(addr, value)?.let { onCadence(it) }
            }
        }

        @Deprecated("Deprecated")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, char: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION") onCharacteristicChanged(gatt, char, char.value)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, d: BluetoothGattDescriptor, s: Int) {
            processNextSub(gatt)
        }
    }

    @SuppressLint("MissingPermission")
    private fun addSubscription(gatt: BluetoothGatt, s: UUID, c: UUID, q: MutableList<BluetoothGattDescriptor>) {
        val char = gatt.getService(s)?.getCharacteristic(c) ?: return
        gatt.setCharacteristicNotification(char, true)
        char.getDescriptor(CONFIG_DESC)?.let { q.add(it) }
    }

    @SuppressLint("MissingPermission")
    private fun processNextSub(gatt: BluetoothGatt) {
        val q = subscriptionQueues[gatt.device.address] ?: return
        if (q.isNotEmpty()) {
            val d = q.removeAt(0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION") d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION") gatt.writeDescriptor(d)
            }
        } else onStatus("Sensores ativos")
    }

    private fun parseHR(v: ByteArray): Int {
        if (v.isEmpty()) return 0
        return if ((v[0].toInt() and 0x01) != 0) {
            (v[1].toInt() and 0xFF) or ((v[2].toInt() and 0xFF) shl 8)
        } else v[1].toInt() and 0xFF
    }

    private fun parseCP(v: ByteArray): Int {
        if (v.size < 4) return 0
        // Signed 16-bit at offset 2
        val pwr = (v[2].toInt() and 0xFF) or ((v[3].toInt() and 0xFF) shl 8)
        return if (pwr > 32767) pwr - 65536 else pwr
    }

    private fun parseCPCadence(addr: String, v: ByteArray): Int? {
        if (v.size < 4) return null
        val flags = (v[0].toInt() and 0xFF) or ((v[1].toInt() and 0xFF) shl 8)
        if ((flags and 0x20) == 0) return null // Crank Revolution Data bit
        var o = 4
        if ((flags and 0x01) != 0) o += 1
        if ((flags and 0x04) != 0) o += 2
        if ((flags and 0x10) != 0) o += 6
        if (v.size < o + 4) return null
        val revs = (v[o].toInt() and 0xFF) or ((v[o+1].toInt() and 0xFF) shl 8)
        val time = (v[o+2].toInt() and 0xFF) or ((v[o+3].toInt() and 0xFF) shl 8)
        return calcCadence(addr + "CP", revs, time)
    }

    private fun parseCSCCadence(addr: String, v: ByteArray): Int? {
        if (v.size < 1 || (v[0].toInt() and 0x02) == 0) return null // Crank Rev bit
        val wheelPresent = (v[0].toInt() and 0x01) != 0
        val o = if (wheelPresent) 7 else 1
        if (v.size < o + 4) return null
        val revs = (v[o].toInt() and 0xFF) or ((v[o+1].toInt() and 0xFF) shl 8)
        val time = (v[o+2].toInt() and 0xFF) or ((v[o+3].toInt() and 0xFF) shl 8)
        return calcCadence(addr + "CSC", revs, time)
    }

    private fun calcCadence(key: String, rev: Int, time: Int): Int? {
        val prev = calcState[key]; calcState[key] = Pair(rev, time)
        if (prev == null) return null
        val dr = if (rev >= prev.first) rev - prev.first else (65536 - prev.first) + rev
        val dt = if (time >= prev.second) time - prev.second else (65536 - prev.second) + time
        if (dt <= 0 || dr <= 0) return null
        return ((dr.toDouble() * 1024.0 * 60.0) / dt.toDouble()).toInt()
    }
}
