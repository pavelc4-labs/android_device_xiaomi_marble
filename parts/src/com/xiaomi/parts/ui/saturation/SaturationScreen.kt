package com.xiaomi.parts.ui.saturation

import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Flare
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.xiaomi.parts.R
import com.xiaomi.parts.data.SaturationRepository
import com.xiaomi.parts.ui.components.ExpScreenScaffold
import com.xiaomi.parts.ui.components.ExpressiveSelectableCard
import com.xiaomi.parts.ui.components.SectionLabel
import com.xiaomi.parts.ui.components.SliderSettingItem
import com.xiaomi.parts.ui.components.animatedItemShapeFor

data class ColorPreset(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val targetValue: Float,
    val icon: ImageVector,
)

private val PreviewBitmapCache = HashMap<Int, ImageBitmap>(3)

@Composable
private fun rememberAsyncPreviewBitmap(@DrawableRes resId: Int): ImageBitmap? {
    val resources = LocalContext.current.resources
    return produceState<ImageBitmap?>(initialValue = PreviewBitmapCache[resId], resId) {
        if (value == null) {
            val decoded = withContext(Dispatchers.IO) {
                runCatching {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeResource(resources, resId, opts)
                    val targetW = 900
                    var sample = 1
                    while (opts.outWidth / (sample * 2) >= targetW) sample *= 2
                    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                    BitmapFactory.decodeResource(resources, resId, decodeOpts)?.asImageBitmap()
                }.getOrNull()
            }
            if (decoded != null) {
                PreviewBitmapCache[resId] = decoded
                value = decoded
            }
        }
    }.value
}

@Composable
fun SaturationScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { SaturationRepository(context) }
    val saturationValue by repo.saturationFlow.collectAsState()
    val selectedPresetId = remember(saturationValue) {
        when (saturationValue.toInt()) {
            0 -> "natural"
            50 -> "standard"
            75 -> "boosted"
            100 -> "vivid"
            else -> null
        }
    }

    val previewImages = remember {
        listOf(
            R.drawable.image_preview1,
            R.drawable.image_preview2,
            R.drawable.image_preview3,
        )
    }
    val pagerState = rememberPagerState { previewImages.size }

    val presets = remember {
        listOf(
            ColorPreset(
                id = "natural",
                titleRes = R.string.preset_natural_title,
                subtitleRes = R.string.preset_natural_desc,
                targetValue = 0f,
                icon = Icons.Default.Spa,
            ),
            ColorPreset(
                id = "standard",
                titleRes = R.string.preset_standard_title,
                subtitleRes = R.string.preset_standard_desc,
                targetValue = 50f,
                icon = Icons.Default.AutoAwesome,
            ),
            ColorPreset(
                id = "boosted",
                titleRes = R.string.preset_boosted_title,
                subtitleRes = R.string.preset_boosted_desc,
                targetValue = 75f,
                icon = Icons.Default.Flare,
            ),
            ColorPreset(
                id = "vivid",
                titleRes = R.string.preset_vivid_title,
                subtitleRes = R.string.preset_vivid_desc,
                targetValue = 100f,
                icon = Icons.Default.Palette,
            ),
        )
    }

    ExpScreenScaffold(
        title = stringResource(R.string.feat_saturation_title),
        subtitle = stringResource(R.string.saturation_subtitle),
        onBack = onBack,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        ) {
            // Photo Asset Live Preview Card
            item(key = "sat_preview", contentType = "preview") {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                            .clip(RoundedCornerShape(18.dp)),
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            key = { previewImages[it] },
                            modifier = Modifier.fillMaxSize(),
                        ) { page ->
                            val previewBitmap = rememberAsyncPreviewBitmap(previewImages[page])
                            if (previewBitmap != null) {
                                Image(
                                    bitmap = previewBitmap,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                )
                            }
                        }

                        // Page indicator dots
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 12.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            repeat(previewImages.size) { index ->
                                val isCurrent = pagerState.currentPage == index
                                Box(
                                    modifier = Modifier
                                        .size(if (isCurrent) 16.dp else 6.dp, 6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(
                                            if (isCurrent) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                        ),
                                )
                            }
                        }
                    }
                }
            }

            // Slider Component
            item(key = "sat_slider", contentType = "slider") {
                SliderSettingItem(
                    title = stringResource(R.string.saturation_slider_title),
                    value = saturationValue,
                    onValueChange = { repo.setSaturation(it) },
                    valueRange = 0f..100f,
                    steps = 19,
                    unit = "%",
                    defaultValue = 50f,
                    onReset = { repo.setSaturation(50f) },
                )
            }

            // Preset List Section
            item(key = "sat_presets_label", contentType = "section_header") {
                SectionLabel(
                    label = stringResource(R.string.saturation_presets_title),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            item(key = "sat_presets", contentType = "presets_group") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    presets.forEachIndexed { index, preset ->
                        val isSelected = selectedPresetId == preset.id
                        val shape = animatedItemShapeFor(
                            index = index,
                            total = presets.size,
                            isSelected = isSelected,
                            outerCorner = 24.dp,
                            innerCorner = 6.dp,
                        )

                        ExpressiveSelectableCard(
                            selected = isSelected,
                            onClick = { repo.setSaturation(preset.targetValue) },
                            title = stringResource(preset.titleRes),
                            subtitle = stringResource(preset.subtitleRes),
                            leadingIcon = preset.icon,
                            shape = shape,
                        )
                    }
                }
            }

            item(key = "sat_spacer", contentType = "spacer") {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
