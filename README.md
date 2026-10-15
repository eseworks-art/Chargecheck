# Charging Power Monitor

A lightweight Android app built with Jetpack Compose to test and compare charger speeds without draining the battery.

## Features
- **Live Power Calculation:** Displays real-time charging and discharging power in Watts (`W`), current (`mA`), and voltage (`V`).
- **Speed Indicator:** Automatically classifies charging speed into **Slow**, **Normal**, **Fast**, and **Super Fast**.
- **Current Statistics:** Tracks the **Lowest**, **Average**, and **Highest** charging/discharging current (`mA`) during a session.
- **Battery Temperature:** Monitors live battery thermal sensor data in Celsius (`°C`).
- **Dark & Light Mode:** Built-in sunlight/moon theme toggle using an AMOLED-friendly dark palette.
- **Zero Background Drain:** Polls at 1Hz only while the screen is active so Android battery management never flags it.# Chargecheck
Measure the current charging your phone
