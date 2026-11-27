package com.xiaomi.parts.ui.fps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaomi.parts.R
import com.xiaomi.parts.data.FpsConfigSnapshot
import com.xiaomi.parts.data.FpsStatsSnapshot

private val OverlayShape = RoundedCornerShape(14.dp)

@Composable
fun FpsOverlayContent(
    stats: FpsStatsSnapshot,
    config: FpsConfigSnapshot,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = config.alpha)

    Column(
        modifier = modifier
            .background(backgroundColor, OverlayShape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (config.showCompact) {
            CompactHud(stats, config)
        } else {
            ExpandedHud(stats, config)
        }
    }
}

@Composable
private fun CompactHud(
    stats: FpsStatsSnapshot,
    config: FpsConfigSnapshot,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (config.showFps) {
            CompactItem(stringResource(R.string.fps_hud_fps), stats.fps, MaterialTheme.colorScheme.primary)
        }
        if (config.showCpuUsage) {
            val cpuText = remember(stats.cpuTotal, stats.cpuTemp, config.showCpuTemp) {
                buildString {
                    append(stats.cpuTotal)
                    if (config.showCpuTemp) {
                        append(" · ")
                        append(stats.cpuTemp)
                    }
                }
            }
            CompactItem(stringResource(R.string.fps_hud_cpu), cpuText, MaterialTheme.colorScheme.secondary)
        }
    }

    if (config.showGpuUsage || config.showRam) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (config.showGpuUsage) {
                val gpuText = remember(stats.gpuUsage, stats.gpuTemp, config.showGpuTemp) {
                    buildString {
                        append(stats.gpuUsage)
                        if (config.showGpuTemp) {
                            append(" · ")
                            append(stats.gpuTemp)
                        }
                    }
                }
                CompactItem(stringResource(R.string.fps_hud_gpu), gpuText, MaterialTheme.colorScheme.tertiary)
            }
            if (config.showRam) {
                CompactItem(stringResource(R.string.fps_hud_ram), stats.ramUsage, MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ExpandedHud(
    stats: FpsStatsSnapshot,
    config: FpsConfigSnapshot,
) {
    val textSize = config.textSize.sp
    val secondarySize = (config.textSize * 0.85f).sp

    if (config.showFps) {
        MetricRow(stringResource(R.string.fps_hud_fps), stats.fps, MaterialTheme.colorScheme.primary, textSize)
    }

    if (config.showCpuUsage) {
        val cpuText = remember(stats.cpuTotal, stats.cpuTemp, config.showCpuTemp) {
            buildString {
                append(stats.cpuTotal)
                if (config.showCpuTemp) {
                    append(" | ")
                    append(stats.cpuTemp)
                }
            }
        }
        MetricRow(stringResource(R.string.fps_hud_cpu), cpuText, MaterialTheme.colorScheme.secondary, textSize)
    }

    if (config.showGpuUsage) {
        val gpuText = remember(stats.gpuUsage, stats.gpuTemp, config.showGpuTemp) {
            buildString {
                append(stats.gpuUsage)
                if (config.showGpuTemp) {
                    append(" | ")
                    append(stats.gpuTemp)
                }
            }
        }
        MetricRow(stringResource(R.string.fps_hud_gpu), gpuText, MaterialTheme.colorScheme.tertiary, textSize)

        if (config.showGpuSpeed) {
            MetricRow(
                label = stringResource(R.string.fps_hud_gpu_clock),
                value = stats.gpuSpeed,
                labelColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f),
                textSize = secondarySize,
            )
        }
    }

    if (config.showRam) {
        MetricRow(stringResource(R.string.fps_hud_ram), stats.ramUsage, MaterialTheme.colorScheme.onSurfaceVariant, textSize)
    }
}

@Composable
private fun CompactItem(label: String, value: String, labelColor: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            color = labelColor,
        )
        Text(
            text = value,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun MetricRow(label: String, value: String, labelColor: Color, textSize: TextUnit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = textSize,
            fontWeight = FontWeight.ExtraBold,
            color = labelColor,
        )
        Text(
            text = value,
            fontSize = textSize,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
