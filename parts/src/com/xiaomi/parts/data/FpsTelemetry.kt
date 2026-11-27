package com.xiaomi.parts.data

import android.app.ActivityManager
import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Lightweight zero-allocation hardware telemetry reader with automatic
 * simulated mock fallback for unprivileged / non-root preview testing.
 */
object FpsHardwareMonitor {
    private const val CPU_STAT_PATH = "/proc/stat"
    private const val MEMINFO_PATH = "/proc/meminfo"
    private const val CPU_TEMP_PATH = "/sys/class/thermal/thermal_zone0/temp"
    private const val GPU_USAGE_PATH = "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage"
    private const val GPU_CLOCK_PATH = "/sys/class/kgsl/kgsl-3d0/gpuclk"
    private const val GPU_TEMP_PATH = "/sys/class/kgsl/kgsl-3d0/temp"
    private const val FPS_PATH = "/sys/class/drm/sde-crtc-0/measured_fps"

    private val cachedReaders = HashMap<String, RandomAccessFile>(8)
    private val readBuffer = ByteArray(512)

    private var prevIdle = -1L
    private var prevTotal = -1L
    private var tick = 0

    fun isHardwareAvailable(): Boolean =
        runCatching { File(FPS_PATH).canRead() || File(GPU_USAGE_PATH).canRead() }.getOrDefault(false)

    fun pollFpsOnly(config: FpsConfigSnapshot): String {
        if (!config.showFps) return "0"
        if (!isHardwareAvailable()) {
            tick++
            val wave = sin(tick * 0.45)
            return (116 + (wave * 3.5) + Random.nextInt(-2, 3)).roundToInt().coerceIn(58, 120).toString()
        }
        return readFps()
    }

    fun pollFastMetrics(config: FpsConfigSnapshot, previous: FpsStatsSnapshot): FpsStatsSnapshot {
        if (!isHardwareAvailable()) {
            val wave = sin(tick * 0.45)
            val cpuVal = (31 + (wave * 9) + Random.nextInt(-3, 4)).roundToInt().coerceIn(12, 85)
            val gpuVal = (42 + (wave * 14) + Random.nextInt(-4, 5)).roundToInt().coerceIn(15, 92)
            val gpuMHz = listOf(315, 427, 510, 585, 680)[(tick / 4) % 5]
            return previous.copy(
                cpuTotal = if (config.showCpuUsage) "$cpuVal%" else previous.cpuTotal,
                gpuUsage = if (config.showGpuUsage) "$gpuVal%" else previous.gpuUsage,
                gpuSpeed = if (config.showGpuSpeed) "$gpuMHz MHz" else previous.gpuSpeed,
            )
        }

        return previous.copy(
            cpuTotal = if (config.showCpuUsage) readCpuUsage() else previous.cpuTotal,
            gpuUsage = if (config.showGpuUsage) readGpuUsage() else previous.gpuUsage,
            gpuSpeed = if (config.showGpuSpeed) "${readGpuClock()} MHz" else previous.gpuSpeed,
        )
    }

    fun pollSlowMetrics(
        context: Context,
        config: FpsConfigSnapshot,
        previous: FpsStatsSnapshot,
    ): FpsStatsSnapshot {
        val ramUsage = if (config.showRam) readRamUsage(context) else previous.ramUsage
        if (!isHardwareAvailable()) {
            val wave = sin(tick * 0.45)
            val cpuTempVal = (41.2 + (wave * 1.6) + Random.nextDouble(-0.3, 0.4)).roundToInt()
            val gpuTempVal = (38.8 + (wave * 1.4) + Random.nextDouble(-0.2, 0.3)).roundToInt()
            return previous.copy(
                cpuTemp = if (config.showCpuTemp) "$cpuTempVal°C" else previous.cpuTemp,
                gpuTemp = if (config.showGpuTemp) "$gpuTempVal°C" else previous.gpuTemp,
                ramUsage = ramUsage,
            )
        }

        return previous.copy(
            cpuTemp = if (config.showCpuTemp) readTemp(CPU_TEMP_PATH) else previous.cpuTemp,
            gpuTemp = if (config.showGpuTemp) readTemp(GPU_TEMP_PATH) else previous.gpuTemp,
            ramUsage = ramUsage,
        )
    }

    private fun readFps(): String {
        val len = readRawLine(FPS_PATH)
        if (len <= 0) return "N/A"
        // Format: "fps: 119.8 duration: ..." -> find "fps:" in readBuffer without regex
        var idx = -1
        for (i in 0 until (len - 4)) {
            if (readBuffer[i] == 'f'.code.toByte() &&
                readBuffer[i + 1] == 'p'.code.toByte() &&
                readBuffer[i + 2] == 's'.code.toByte() &&
                readBuffer[i + 3] == ':'.code.toByte()
            ) {
                idx = i + 4
                break
            }
        }
        if (idx == -1) return "N/A"
        while (idx < len && readBuffer[idx] == ' '.code.toByte()) idx++
        var intVal = 0
        var decDigit = -1
        var seenDigit = false
        var afterDot = false
        while (idx < len) {
            val b = readBuffer[idx].toInt() and 0xFF
            when {
                b in '0'.code..'9'.code -> {
                    seenDigit = true
                    when {
                        !afterDot -> intVal = intVal * 10 + (b - '0'.code)
                        decDigit == -1 -> decDigit = b - '0'.code
                    }
                }
                b == '.'.code && !afterDot -> afterDot = true
                else -> break
            }
            idx++
        }
        if (!seenDigit) return "N/A"
        if (decDigit >= 5) intVal++
        return intVal.toString()
    }

    private fun readCpuUsage(): String {
        val len = readRawLine(CPU_STAT_PATH)
        if (len < 5) return "N/A"
        // Skip "cpu" prefix and spaces, parse 7 columns: user, nice, system, idle, iowait, irq, softirq
        var i = 0
        while (i < len && readBuffer[i] != ' '.code.toByte()) i++
        var total = 0L
        var idle = 0L
        var col = 0
        while (i < len && col < 7) {
            while (i < len && readBuffer[i] == ' '.code.toByte()) i++
            if (i >= len) break
            var num = 0L
            var hasDigit = false
            while (i < len) {
                val b = readBuffer[i].toInt() and 0xFF
                if (b in '0'.code..'9'.code) {
                    num = num * 10L + (b - '0'.code)
                    hasDigit = true
                    i++
                } else {
                    break
                }
            }
            if (!hasDigit) break
            total += num
            if (col == 3) idle = num
            col++
        }
        if (col < 5) return "N/A"
        if (prevTotal == -1L) {
            prevTotal = total
            prevIdle = idle
            return "0%"
        }
        val diffTotal = total - prevTotal
        val diffIdle = idle - prevIdle
        prevTotal = total
        prevIdle = idle
        if (diffTotal <= 0L) return "0%"
        val pct = ((100f * (diffTotal - diffIdle)) / diffTotal).roundToInt().coerceIn(0, 100)
        return "$pct%"
    }

    private fun readGpuUsage(): String {
        val v = readFirstLong(GPU_USAGE_PATH)
        return if (v < 0) "N/A" else "${v.coerceIn(0, 100)}%"
    }

    private fun readGpuClock(): String {
        val hz = readFirstLong(GPU_CLOCK_PATH)
        return if (hz < 0) "0" else (hz / 1_000_000L).toString()
    }

    private fun readTemp(path: String): String {
        val raw = readFirstLong(path)
        if (raw < 0) return "N/A"
        val celsius = if (raw > 1000L) (raw / 1000f).roundToInt() else raw.toInt()
        return "$celsius°C"
    }

    private fun readRamUsage(context: Context): String {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (am != null) {
            val memInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            val usedGb = (memInfo.totalMem - memInfo.availMem) / (1024.0 * 1024.0 * 1024.0)
            return String.format(Locale.US, "%.1f GB", usedGb)
        }
        return runCatching {
            val reader = cachedReaders.getOrPut(MEMINFO_PATH) { RandomAccessFile(MEMINFO_PATH, "r") }
            reader.seek(0)
            var memTotal = 0L
            var memAvailable = 0L
            repeat(10) {
                val line = reader.readLine() ?: return@repeat
                when {
                    line.startsWith("MemTotal:") -> memTotal = extractDigits(line)
                    line.startsWith("MemAvailable:") -> memAvailable = extractDigits(line)
                }
                if (memTotal != 0L && memAvailable != 0L) {
                    val usedGb = (memTotal - memAvailable) / (1024.0 * 1024.0)
                    return String.format(Locale.US, "%.1f GB", usedGb)
                }
            }
            "N/A"
        }.getOrDefault("N/A")
    }

    private fun extractDigits(line: String): Long {
        var acc = 0L
        for (ch in line) {
            if (ch in '0'..'9') acc = acc * 10L + (ch - '0')
        }
        return acc
    }

    private fun readFirstLong(path: String): Long {
        val len = readRawLine(path)
        if (len <= 0) return -1L
        var acc = 0L
        var found = false
        for (i in 0 until len) {
            val b = readBuffer[i].toInt() and 0xFF
            when {
                b in '0'.code..'9'.code -> {
                    acc = acc * 10L + (b - '0'.code)
                    found = true
                }
                found -> break
            }
        }
        return if (found) acc else -1L
    }

    private fun readRawLine(path: String): Int = synchronized(readBuffer) {
        runCatching {
            val reader = cachedReaders.getOrPut(path) { RandomAccessFile(path, "r") }
            reader.seek(0)
            val bytesRead = reader.read(readBuffer)
            if (bytesRead <= 0) return 0
            for (i in 0 until bytesRead) {
                if (readBuffer[i] == '\n'.code.toByte()) return i
            }
            bytesRead
        }.getOrDefault(0)
    }

    fun closeAll() {
        cachedReaders.values.forEach { runCatching { it.close() } }
        cachedReaders.clear()
    }
}
