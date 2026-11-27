package com.xiaomi.parts.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.DisplaySettings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaomi.parts.R
import com.xiaomi.parts.ui.components.ExpScreenScaffold
import com.xiaomi.parts.ui.components.ExpressiveList
import com.xiaomi.parts.ui.components.bouncyClickable

data class FeatureItemData(
    val icon: ImageVector,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val onClick: () -> Unit,
)

data class FeatureCategory(
    @StringRes val titleRes: Int,
    val items: List<FeatureItemData>,
)

@Composable
fun HomeScreen(
    onNavigateToThermal: () -> Unit,
    onNavigateToFps: () -> Unit,
    onNavigateToSaturation: () -> Unit,
    onNavigateToSpeaker: () -> Unit,
    onNavigateToDoze: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val categories = remember {
        listOf(
            FeatureCategory(
                titleRes = R.string.category_display_audio,
                items = listOf(
                    FeatureItemData(Icons.Outlined.ColorLens, R.string.feat_saturation_title, R.string.feat_saturation_desc, onNavigateToSaturation),
                    FeatureItemData(Icons.AutoMirrored.Outlined.VolumeUp, R.string.feat_speaker_title, R.string.feat_speaker_desc, onNavigateToSpeaker),
                ),
            ),
            FeatureCategory(
                titleRes = R.string.category_performance,
                items = listOf(
                    FeatureItemData(Icons.Outlined.LocalFireDepartment, R.string.feat_thermal_title, R.string.feat_thermal_desc, onNavigateToThermal),
                    FeatureItemData(Icons.Outlined.Speed, R.string.feat_fps_title, R.string.feat_fps_desc, onNavigateToFps),
                ),
            ),
            FeatureCategory(
                titleRes = R.string.category_gestures_ambient,
                items = listOf(
                    FeatureItemData(Icons.Outlined.DisplaySettings, R.string.feat_doze_title, R.string.feat_doze_desc, onNavigateToDoze),
                ),
            ),
            FeatureCategory(
                titleRes = R.string.category_general_about,
                items = listOf(
                    FeatureItemData(Icons.Outlined.Info, R.string.feat_about_title, R.string.feat_about_desc, onNavigateToAbout),
                ),
            ),
        )
    }

    ExpScreenScaffold(
        title = stringResource(R.string.app_title),
        onBack = onBack,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        ) {
            items(
                items = categories,
                key = { it.titleRes },
                contentType = { "feature_category" },
            ) { category ->
                Column {
                    Text(
                        text = stringResource(category.titleRes).uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
                    )

                    ExpressiveList(
                        count = category.items.size,
                        cornerLarge = 20.dp,
                        cornerSmall = 4.dp,
                        gap = 2.dp,
                    ) { index ->
                        val item = category.items[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bouncyClickable(onClick = item.onClick)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(42.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(item.titleRes),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(item.subtitleRes),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
