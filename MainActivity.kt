package com.example.chargingmonitor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
    var isCharging by remember { mutableStateOf(false) }
    
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

    return BatteryState(
        wattage = exactWattage,
        currentmA = currentmA,
        voltageV = voltageV,
        temperatureC = temperatureC,
        batteryLevel = batteryLevel,
        isCharging = isCharging,
        minCurrent = minCurrent,
        maxCurrent = maxCurrent,
        avgCurrent = avgCurrent
    )
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
    val avgCurrent: Int
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

    // Persistent battery level color regardless of charging state
    val batteryLevelColor = when (battery.batteryLevel) {
        in 1..15 -> Color(0xFFEF4444)   // Red
        in 16..20 -> Color(0xFFF97316)  // Orange
        in 21..80 -> Color(0xFF10B981)  // Green
        else -> Color(0xFF3B82F6)       // Blue (81 to 100)
    }

    // Current flow animation speed
    val animDuration = when (speedLabel) {
        "Super Fast" -> 400
        "Fast" -> 700
        "Normal" -> 1100
        "Slow" -> 1600
        else -> 3000
    }

    val infiniteTransition = rememberInfiniteTransition(label = "currentFlow")
    val flowProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(animDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "flowStep"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = backgroundColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar: Theme Switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { isDarkMode = !isDarkMode },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(cardColor)
                ) {
                    Icon(
                        imageVector = if (isDarkMode) Icons.Rounded.WbSunny else Icons.Rounded.DarkMode,
                        contentDescription = "Toggle Theme",
                        tint = if (isDarkMode) Color(0xFFFBBF24) else Color(0xFF475569)
                    )
                }
            }

            // Center Section: Charger Plug + Flow Wire Animation + Animated Battery Icon
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Temperature Pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(cardColor)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Thermostat,
                            contentDescription = "Temperature",
                            tint = if (battery.temperatureC > 40.0) Color(0xFFEF4444) else Color(0xFF38BDF8),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = String.format(Locale.US, "%.1f°C", battery.temperatureC),
                            color = textColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Charging Speed Pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(statusColor.copy(alpha = 0.15f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
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
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Charger Plug & Wire Current Flow Animation Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Charger Plug Icon (Drawn via Canvas)
                    Canvas(modifier = Modifier.size(36.dp)) {
                        val plugColor = if (battery.isCharging) Color(0xFF10B981) else subTextColor
                        // Plug Body
                        drawRoundRect(
                            color = plugColor,
                            topLeft = Offset(size.width * 0.3f, size.height * 0.2f),
                            size = Size(size.width * 0.4f, size.height * 0.6f),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                        // Prongs
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

                    // Current passing through wire line
                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .height(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Base wire
                            drawLine(
                                color = subTextColor.copy(alpha = 0.3f),
                                start = Offset(0f, size.height / 2),
                                end = Offset(size.width, size.height / 2),
                                strokeWidth = 3.dp.toPx()
                            )
                            // Moving pulse current if charging
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

                    // Huge Animated Battery Icon
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(width = 90.dp, height = 45.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 4.dp.toPx()
                            val cornerRadius = 8.dp.toPx()
                            
                            // Battery Outline
                            drawRoundRect(
                                color = batteryLevelColor,
                                size = Size(size.width - 8.dp.toPx(), size.height),
                                cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                                style = Stroke(width = strokeWidth)
                            )
                            
                            // Terminal Tip
                            drawRoundRect(
                                color = batteryLevelColor,
                                topLeft = Offset(size.width - 6.dp.toPx(), size.height / 3f),
                                size = Size(6.dp.toPx(), size.height / 3f),
                                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                            )

                            // Inner Level Fill
                            val fillWidthMargin = 6.dp.toPx()
                            val maxFillWidth = size.width - 14.dp.toPx() - fillWidthMargin
                            val currentFillWidth = maxFillWidth * (battery.batteryLevel / 100f)
                            
                            drawRoundRect(
                                color = batteryLevelColor,
                                topLeft = Offset(fillWidthMargin, fillWidthMargin),
                                size = Size(currentFillWidth.coerceAtLeast(4.dp.toPx()), size.height - (fillWidthMargin * 2)),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                                style = Fill
                            )
                        }

                        // Percentage Text inside battery icon
                        Text(
                            text = "${battery.batteryLevel}%",
                            color = if (battery.batteryLevel > 50 && isDarkMode) Color.Black else textColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Giant Whole Watts Display
                Text(
                    text = "$wholeWatts",
                    style = TextStyle(
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                )
                Text(
                    text = if (battery.isCharging) "WATTS (CHARGING)" else "WATTS (DISCHARGING)",
                    style = TextStyle(
                        fontSize = 13.sp,
                        letterSpacing = 2.sp,
                        color = subTextColor
                    )
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Sub-metrics showing mA and Voltage
                Text(
                    text = "${battery.currentmA} mA • ${String.format(Locale.US, "%.1f", battery.voltageV)}V",
                    color = subTextColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Bottom Section: Stats Card & Gentle Tip
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = cardColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        StatItem(label = "Lowest", value = "${battery.minCurrent} mA", labelColor = subTextColor, valueColor = textColor)
                        StatItem(label = "Average", value = "${battery.avgCurrent} mA", labelColor = subTextColor, valueColor = textColor)
                        StatItem(label = "Highest", value = "${battery.maxCurrent} mA", labelColor = subTextColor, valueColor = textColor)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Reminder / Notice Message
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
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Tip: Charging speeds slow down automatically at higher battery percentages.",
                        color = subTextColor,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String, labelColor: Color, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = labelColor, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, color = valueColor, fontWeight = FontWeight.Medium, fontSize = 15.sp)
    }
}
