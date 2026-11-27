package com.xiaomi.parts.ui.fps

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xiaomi.parts.R
import com.xiaomi.parts.data.FpsHardwareMonitor
import com.xiaomi.parts.data.FpsPrefs
import com.xiaomi.parts.data.FpsStatsSnapshot
import com.xiaomi.parts.ui.components.ExpScreenScaffold
import com.xiaomi.parts.ui.components.ExpressiveList
import com.xiaomi.parts.ui.components.SectionLabel
import com.xiaomi.parts.ui.components.SliderSettingItem
import com.xiaomi.parts.ui.components.SwitchSettingItem
import com.xiaomi.parts.ui.theme.XiaomiPartsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private data class MetricToggleSpec(
    val icon: ImageVector,
    val titleRes: Int,
    val subtitleRes: Int,
    val checked: Boolean,
    val enabled: Boolean = true,
    val onChange: (Boolean) -> Unit,
)

@Composable
fun FpsMeterScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember { FpsPrefs.getInstance(context) }

    val config by prefs.configFlow.collectAsState()
    val latestConfig by rememberUpdatedState(config)
    val isEnabled by prefs.isEnabledFlow.collectAsState()
    var hasOverlayPerm by remember { mutableStateOf(prefs.hasOverlayPermission()) }
    var liveStats by remember { mutableStateOf(FpsStatsSnapshot()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPerm = prefs.hasOverlayPermission()
                when {
                    prefs.isEnabled && hasOverlayPerm -> prefs.startService()
                    prefs.isEnabled && !hasOverlayPerm -> prefs.isEnabled = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Split-tier lightweight polling for live preview; never restarted when sliders drag
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            var lastFpsPoll = 0L
            var lastFastPoll = 0L
            var lastSlowPoll = 0L
            var current = FpsStatsSnapshot()

            while (isActive) {
                val cfg = latestConfig
                val now = SystemClock.elapsedRealtime()
                val step = cfg.fpsIntervalMs.coerceIn(250L, 2000L)

                if (cfg.showFps && now - lastFpsPoll >= step) {
                    current = current.copy(fps = FpsHardwareMonitor.pollFpsOnly(cfg))
                    lastFpsPoll = now
                }
                if (now - lastFastPoll >= maxOf(step, 1000L)) {
                    current = FpsHardwareMonitor.pollFastMetrics(cfg, current)
                    lastFastPoll = now
                }
                if (now - lastSlowPoll >= maxOf(step, 3000L)) {
                    current = FpsHardwareMonitor.pollSlowMetrics(context, cfg, current)
                    lastSlowPoll = now
                }

                withContext(Dispatchers.Main) { liveStats = current }
                delay(step)
            }
        }
    }

    val metricToggles = remember(config) {
        buildList {
            add(MetricToggleSpec(Icons.Outlined.ViewCompact, R.string.fps_compact_mode, R.string.fps_compact_mode_desc, config.showCompact) { prefs.showCompact = it })
            add(MetricToggleSpec(Icons.Outlined.TouchApp, R.string.fps_touch_passthrough, R.string.fps_touch_passthrough_desc, config.touchPassthrough) { prefs.touchPassthrough = it })
            add(MetricToggleSpec(Icons.Outlined.Speed, R.string.fps_show_fps, R.string.fps_show_fps_desc, config.showFps) { prefs.showFps = it })
            add(MetricToggleSpec(Icons.Outlined.Memory, R.string.fps_show_cpu, R.string.fps_show_cpu_desc, config.showCpuUsage) { prefs.showCpuUsage = it })
            add(MetricToggleSpec(Icons.Outlined.Thermostat, R.string.fps_show_cpu_temp, R.string.fps_show_cpu_temp_desc, config.showCpuTemp, enabled = config.showCpuUsage) { prefs.showCpuTemp = it })
            add(MetricToggleSpec(Icons.Outlined.GraphicEq, R.string.fps_show_gpu, R.string.fps_show_gpu_desc, config.showGpuUsage) { prefs.showGpuUsage = it })
            add(MetricToggleSpec(Icons.Outlined.DeviceThermostat, R.string.fps_show_gpu_temp, R.string.fps_show_gpu_temp_desc, config.showGpuTemp, enabled = config.showGpuUsage) { prefs.showGpuTemp = it })
            if (!config.showCompact) {
                add(MetricToggleSpec(Icons.Outlined.Bolt, R.string.fps_show_gpu_speed, R.string.fps_show_gpu_speed_desc, config.showGpuSpeed, enabled = config.showGpuUsage) { prefs.showGpuSpeed = it })
            }
            add(MetricToggleSpec(Icons.Outlined.SdStorage, R.string.fps_show_ram, R.string.fps_show_ram_desc, config.showRam) { prefs.showRam = it })
        }
    }

    ExpScreenScaffold(
        title = stringResource(R.string.feat_fps_title),
        subtitle = stringResource(R.string.fps_subtitle),
        onBack = onBack,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        ) {
            // 1. Live Preview Card
            item(key = "fps_preview", contentType = "preview") {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.fps_preview_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 128.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            XiaomiPartsTheme(darkTheme = true) {
                                FpsOverlayContent(stats = liveStats, config = config)
                            }
                        }
                    }
                }
            }

            // 2. Master & Testing Section
            item(key = "fps_master", contentType = "control") {
                SectionLabel(
                    label = stringResource(R.string.fps_section_control),
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                )
                ExpressiveList(count = 2) { index ->
                    when (index) {
                        0 -> SwitchSettingItem(
                            title = stringResource(R.string.fps_enable_title),
                            subtitle = stringResource(
                                if (hasOverlayPerm) R.string.fps_enable_desc
                                else R.string.fps_permission_needed
                            ),
                            checked = isEnabled,
                            onCheckedChange = { checked ->
                                if (checked && !prefs.hasOverlayPermission()) {
                                    prefs.isEnabled = true
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}"),
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                } else {
                                    prefs.isEnabled = checked
                                }
                            },
                            icon = Icons.Outlined.PictureInPictureAlt,
                        )
                        1 -> SwitchSettingItem(
                            title = stringResource(R.string.fps_mock_mode_title),
                            subtitle = stringResource(R.string.fps_mock_mode_desc),
                            checked = config.mockData,
                            onCheckedChange = { prefs.mockData = it },
                            icon = Icons.Outlined.Science,
                            showDivider = false,
                        )
                    }
                }
            }

            // 3. Appearance & Interval Section
            item(key = "fps_appearance", contentType = "appearance") {
                SectionLabel(
                    label = stringResource(R.string.fps_section_appearance),
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SliderSettingItem(
                        title = stringResource(R.string.fps_poll_interval_title),
                        value = config.fpsIntervalMs.toFloat(),
                        onValueChange = { raw ->
                            val snapped = ((raw / 250f).roundToInt() * 250L).coerceIn(250L, 2000L)
                            prefs.fpsIntervalMs = snapped
                        },
                        valueRange = 250f..2000f,
                        steps = 6,
                        unit = " ms",
                        defaultValue = 500f,
                        onReset = { prefs.fpsIntervalMs = 500L },
                    )

                    SliderSettingItem(
                        title = stringResource(R.string.fps_bg_opacity),
                        value = config.alpha * 100f,
                        onValueChange = { prefs.overlayAlpha = (it / 100f).coerceIn(0.2f, 1f) },
                        valueRange = 20f..100f,
                        steps = 15,
                        unit = "%",
                        defaultValue = 78f,
                        onReset = { prefs.overlayAlpha = 0.78f },
                    )
                    if (!config.showCompact) {
                        SliderSettingItem(
                            title = stringResource(R.string.fps_text_size),
                            value = config.textSize,
                            onValueChange = { prefs.textSizeSp = it },
                            valueRange = 10f..22f,
                            steps = 11,
                            unit = "sp",
                            defaultValue = 13f,
                            onReset = { prefs.textSizeSp = 13f },
                        )
                    }
                }
            }

            // 4. Metrics & Behavior Section
            item(key = "fps_metrics", contentType = "metrics") {
                SectionLabel(
                    label = stringResource(R.string.fps_section_metrics),
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                )
                ExpressiveList(count = metricToggles.size) { index ->
                    val item = metricToggles[index]
                    SwitchSettingItem(
                        title = stringResource(item.titleRes),
                        subtitle = stringResource(item.subtitleRes),
                        checked = item.checked,
                        onCheckedChange = item.onChange,
                        icon = item.icon,
                        enabled = item.enabled,
                        showDivider = index != metricToggles.lastIndex,
                    )
                }
            }
        }
    }
}
