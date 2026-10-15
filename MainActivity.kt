package com.example.chargingmonitor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs

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
    var isCharging by remember { mutableStateOf(false) }
    
    var minCurrent by remember { mutableStateOf(0) }
    var maxCurrent by remember { mutableStateOf(0) }
    var avgCurrent by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        
        while (true) {
            val microAmps = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            val rawmA = microAmps / 1000
            currentmA = rawmA

            val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
                context.registerReceiver(null, filter)
            }
            
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

            val voltageMv = batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: 0
            voltageV = voltageMv / 1000.0

            val tempInt = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            temperatureC = tempInt / 10.0

            val absCurrent = abs(rawmA)
            if (absCurrent > 0) {
                if (minCurrent == 0 || absCurrent < minCurrent) minCurrent = absCurrent
                if (absCurrent > maxCurrent) maxCurrent = absCurrent
                avgCurrent = if (avgCurrent == 0) absCurrent else (avgCurrent + absCurrent) / 2
            }

            // 1-second update interval to prevent battery drain
            delay(1000L)
        }
    }

    val wattage = abs(currentmA * voltageV) / 1000.0

    return BatteryState(
        wattage = wattage,
        currentmA = currentmA,
        voltageV = voltageV,
        temperatureC = temperatureC,
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
        battery.wattage < 5.0 -> "Slow"
        battery.wattage <= 10.0 -> "Normal"
        battery.wattage <= 25.0 -> "Fast"
        else -> "Super Fast"
    }

    val statusColor = if (battery.isCharging) Color(0xFF10B981) else Color(0xFFF59E0B)

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
            // Top Bar: Sunlight / Moon Theme Switcher
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

            // Center Section: Temp, Speed Badge, and Wattage
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

                Spacer(modifier = Modifier.height(48.dp))

                // Main Wattage Display
                Text(
                    text = String.format(Locale.US, "%.2f", battery.wattage),
                    style = TextStyle(
                        fontSize = 64.sp,
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
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "${abs(battery.currentmA)} mA • ${String.format(Locale.US, "%.1f", battery.voltageV)}V",
                    color = subTextColor,
                    fontSize = 15.sp
                )
            }

            // Bottom Card: Lowest, Average, Highest Current
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
