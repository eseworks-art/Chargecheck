package com.example.chargingmonitor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.BatteryManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChargingDashboardApp()
        }
    }
}

@Composable
fun rememberBatteryState(context: Context): BatteryState {
    var currentmA by remember { mutableStateOf(0) }
    var voltageV by remember { mutableStateOf(0.0) }
    var temperatureC by remember { mutableStateOf(0.0) }
    var batteryLevel by remember { mutableStateOf(50) }
    var batteryCapacitymAh by remember { mutableStateOf(4500) } // Standard baseline capacity
    var isCharging by remember { mutableStateOf(false) }
    var batteryHealth by remember { mutableStateOf("Good") }
    
    val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
    val model = Build.MODEL

    var minCurrent by remember { mutableStateOf(0) }
    var maxCurrent by remember { mutableStateOf(0) }
    var avgCurrent by remember { mutableStateOf(0) }
    
    var sampleCount by remember { mutableStateOf(0) }
    var totalCurrentSum by remember { mutableStateOf(0L) }
    var lastChargingState by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        while (true) {
            val rawValue = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            val absRaw = abs(rawValue)
            
            val mA = if (absRaw > 10000) absRaw / 1000 else absRaw
            currentmA = mA

            val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
                context.registerReceiver(null, filter)
            }
            
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val plugged = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
            val nowCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || plugged > 0

            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) {
                batteryLevel = ((level.toFloat() / scale.toFloat()) * 100).toInt()
            }

            val healthCode = batteryStatus?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN) ?: BatteryManager.BATTERY_HEALTH_UNKNOWN
            batteryHealth = when (healthCode) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
                BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                else -> "Normal"
            }

            if (lastChargingState != null && lastChargingState != nowCharging) {
                minCurrent = 0
                maxCurrent = 0
                avgCurrent = 0
                sampleCount = 0
                totalCurrentSum = 0L
            }
            lastChargingState = nowCharging
            isCharging = nowCharging

            val voltageMv = batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: 0
            voltageV = if (voltageMv > 100) voltageMv / 1000.0 else voltageMv.toDouble()

            val tempInt = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            temperatureC = tempInt / 10.0

            if (mA > 0) {
                if (minCurrent == 0 || mA < minCurrent) minCurrent = mA
                if (mA > maxCurrent) maxCurrent = mA
                sampleCount++
                totalCurrentSum += mA
                avgCurrent = (totalCurrentSum / sampleCount).toInt()
            }

            delay(1000L)
        }
    }

    val exactWattage = (currentmA * voltageV) / 1000.0
    val availableCapacitymAh = ((batteryCapacitymAh * batteryLevel) / 100)

    val timeString = if (isCharging) {
        if (currentmA > 50 && batteryLevel < 100) {
            val remainingPercent = 100 - batteryLevel
            val remainingmAh = (batteryCapacitymAh * (remainingPercent / 100.0))
            val hours = remainingmAh / currentmA
            formatTime(hours)
        } else if (batteryLevel >= 100) {
            "Fully Charged"
        } else {
            "Calculating..."
        }
    } else {
        if (currentmA > 50) {
            val currentmAh = (batteryCapacitymAh * (batteryLevel / 100.0))
            val hours = currentmAh / currentmA
            formatTime(hours) + " until shutdown"
        } else {
            "Idle"
        }
    }

    return BatteryState(
        wattage = exactWattage,
        currentmA = currentmA,
        voltageV = voltageV,
        temperatureC = temperatureC,
        batteryLevel = batteryLevel,
        isCharging = isCharging,
        minCurrent = minCurrent,
        maxCurrent = maxCurrent,
        avgCurrent = avgCurrent,
        timeEstimate = timeString,
        batteryHealth = batteryHealth,
        deviceInfo = "$manufacturer $model",
        totalCapacity = batteryCapacitymAh,
        availableCapacity = availableCapacitymAh
    )
}

fun formatTime(decimalHours: Double): String {
    val totalMinutes = (decimalHours * 60).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        "$hours hr $minutes min"
    } else {
        "$minutes min"
    }
}

data class BatteryState(
    val wattage: Double,
    val currentmA: Int,
    val voltageV: Double,
    val temperatureC: Double,
    val batteryLevel: Int,
    val isCharging: Boolean,
    val minCurrent: Int,
    val maxCurrent: Int,
    val avgCurrent: Int,
    val timeEstimate: String,
    val batteryHealth: String,
    val deviceInfo: String,
    val totalCapacity: Int,
    val availableCapacity: Int
)

@Composable
fun ChargingDashboardApp() {
    val context = LocalContext.current
    val battery = rememberBatteryState(context = context)

    var isDarkMode by remember { mutableStateOf(true) }

    val backgroundColor = if (isDarkMode) Color(0xFF0F172A) else Color(0xFFF8FAFC)
    val cardColor = if (isDarkMode) Color(0xFF1E293B) else Color(0xFFFFFFFF)
    val textColor = if (isDarkMode) Color.White else Color(0xFF0F172A)
    val subTextColor = if (isDarkMode) Color.Gray else Color(0xFF64748B)

    val speedLabel = when {
        !battery.isCharging -> "Discharging"
        battery.currentmA < 500 -> "Slow"
        battery.currentmA in 500..899 -> "Normal"
        battery.currentmA in 900..1998 -> "Fast"
        else -> "Super Fast"
    }

    val statusColor = if (battery.isCharging) Color(0xFF10B981) else Color(0xFFF59E0B)
    val wholeWatts = battery.wattage.roundToInt()

    val batteryLevelColor = when (battery.batteryLevel) {
        in 1..15 -> Color(0xFFEF4444)
        in 16..20 -> Color(0xFFF97316)
        in 21..80 -> Color(0xFF10B981)
        else -> Color(0xFF3B82F6)
    }

    val animDuration = when (speedLabel) {
        "Super Fast" -> 400
        "Fast" -> 700
        "Normal" -> 1100
        "Slow" -> 1600
        else -> 3000
    }

    val infiniteTransition = rememberInfiniteTransition(label = "animations")
    val flowProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(animDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "flowStep"
    )

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = backgroundColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Bar: Device Info & Theme Switcher
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = battery.deviceInfo,
                        color = subTextColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    IconButton(
                        onClick = { isDarkMode = !isDarkMode },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(cardColor)
                    ) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Rounded.WbSunny else Icons.Rounded.DarkMode,
                            contentDescription = "Toggle Theme",
                            tint = if (isDarkMode) Color(0xFFFBBF24) else Color(0xFF475569),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Status Pills Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(cardColor)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Thermostat,
                            contentDescription = "Temperature",
                            tint = if (battery.temperatureC > 40.0) Color(0xFFEF4444) else Color(0xFF38BDF8),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = String.format(Locale.US, "%.1f°C", battery.temperatureC),
                            color = textColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(statusColor.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = speedLabel,
                            color = statusColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Charger Plug & Wire Current Flow Animation Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Canvas(modifier = Modifier.size(34.dp)) {
                        val plugColor = if (battery.isCharging) Color(0xFF10B981) else subTextColor
                        drawRoundRect(
                            color = plugColor,
                            topLeft = Offset(size.width * 0.3f, size.height * 0.2f),
                            size = Size(size.width * 0.4f, size.height * 0.6f),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                        drawRect(
                            color = plugColor,
                            topLeft = Offset(size.width * 0.4f, size.height * 0.05f),
                            size = Size(size.width * 0.08f, size.height * 0.15f)
                        )
                        drawRect(
                            color = plugColor,
                            topLeft = Offset(size.width * 0.52f, size.height * 0.05f),
                            size = Size(size.width * 0.08f, size.height * 0.15f)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(45.dp)
                            .height(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawLine(
                                color = subTextColor.copy(alpha = 0.3f),
                                start = Offset(0f, size.height / 2),
                                end = Offset(size.width, size.height / 2),
                                strokeWidth = 3.dp.toPx()
                            )
                            if (battery.isCharging) {
                                val pulseX = size.width * flowProgress
                                drawCircle(
                                    color = Color(0xFF10B981),
                                    radius = 4.dp.toPx(),
                                    center = Offset(pulseX, size.height / 2)
                                )
                            }
                        }
                    }

                    // Doubled-Size Fluid Animated Battery Icon (150 x 76 dp)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(width = 150.dp, height = 76.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 6.dp.toPx()
                            val cornerRadius = 12.dp.toPx()
                            
                            drawRoundRect(
                                color = batteryLevelColor,
                                size = Size(size.width - 12.dp.toPx(), size.height),
                                cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                                style = Stroke(width = strokeWidth)
                            )
                            
                            drawRoundRect(
                                color = batteryLevelColor,
                                topLeft = Offset(size.width - 10.dp.toPx(), size.height / 3f),
                                size = Size(10.dp.toPx(), size.height / 3f),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )

                            val fillWidthMargin = 10.dp.toPx()
                            val maxFillWidth = size.width - 22.dp.toPx() - fillWidthMargin
                            val targetFillWidth = maxFillWidth * (battery.batteryLevel / 100f)
                            val innerHeight = size.height - (fillWidthMargin * 2)

                            if (battery.isCharging) {
                                val wavePath = Path().apply {
                                    moveTo(fillWidthMargin, size.height - fillWidthMargin)
                                    val waveHeight = 5.dp.toPx()
                                    val frequency = 0.04f
                                    for (x in 0..targetFillWidth.toInt()) {
                                        val angle = (x + wavePhase) * frequency
                                        val y = (size.height - fillWidthMargin) - (innerHeight * (battery.batteryLevel / 100f)) + (sin(angle.toDouble()) * waveHeight).toFloat()
                                        lineTo(fillWidthMargin + x, y.coerceIn(fillWidthMargin, size.height - fillWidthMargin))
                                    }
                                    lineTo(fillWidthMargin + targetFillWidth, size.height - fillWidthMargin)
                                    close()
                                }
                                drawPath(path = wavePath, color = batteryLevelColor.copy(alpha = 0.85f))
                            } else {
                                drawRoundRect(
                                    color = batteryLevelColor.copy(alpha = 0.6f),
                                    topLeft = Offset(fillWidthMargin, fillWidthMargin),
                                    size = Size(targetFillWidth.coerceAtLeast(8.dp.toPx()), innerHeight),
                                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                                    style = Fill
                                )
                            }
                        }

                        Text(
                            text = "${battery.batteryLevel}%",
                            color = textColor,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Giant Whole Watts Display
                Text(
                    text = "$wholeWatts",
                    style = TextStyle(
                        fontSize = 62.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                )
                Text(
                    text = if (battery.isCharging) "WATTS (CHARGING)" else "WATTS (DISCHARGING)",
                    style = TextStyle(
                        fontSize = 11.sp,
                        letterSpacing = 2.sp,
                        color = subTextColor
                    )
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "$wholeWatts W • ${battery.currentmA} mA • ${String.format(Locale.US, "%.1f", battery.voltageV)}V",
                    color = subTextColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = battery.timeEstimate,
                    color = statusColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Min / Max / Avg Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = cardColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        StatItem(label = "Lowest", value = "${battery.minCurrent} mA", labelColor = subTextColor, valueColor = textColor)
                        StatItem(label = "Average", value = "${battery.avgCurrent} mA", labelColor = subTextColor, valueColor = textColor)
                        StatItem(label = "Highest", value = "${battery.maxCurrent} mA", labelColor = subTextColor, valueColor = textColor)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Battery Health & Full Capacity Breakdown Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = cardColor)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "Health", color = subTextColor, fontSize = 10.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = battery.batteryHealth, color = textColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = cardColor)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "Capacity (Avail / Full)", color = subTextColor, fontSize = 10.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${battery.availableCapacity} / ${battery.totalCapacity} mAh",
                                color = textColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = "Tip",
                        tint = subTextColor,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Tip: Charging speeds slow down at higher battery percentages.",
                        color = subTextColor,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Ad Banner Placeholder Container (Reserved for future AdMob Banner View)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(cardColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Ad Banner Space (Maintenance Fund)",
                        color = subTextColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String, labelColor: Color, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = labelColor, fontSize = 10.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, color = valueColor, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    }
}
