package com.xiaomi.parts.ui.doze

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PhonelinkRing
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xiaomi.parts.R
import com.xiaomi.parts.data.DozeRepository
import com.xiaomi.parts.ui.components.ExpScreenScaffold
import com.xiaomi.parts.ui.components.ExpressiveList
import com.xiaomi.parts.ui.components.SectionLabel
import com.xiaomi.parts.ui.components.SwitchSettingItem

@Composable
fun DozeScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { DozeRepository(context) }
    val state by repo.stateFlow.collectAsState()

    ExpScreenScaffold(
        title = stringResource(R.string.feat_doze_title),
        onBack = onBack,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        ) {
            item(key = "doze_master", contentType = "switch_group") {
                SectionLabel(
                    label = stringResource(R.string.doze_master_section),
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                )
                ExpressiveList(count = 1) {
                    SwitchSettingItem(
                        title = stringResource(R.string.doze_enable_title),
                        subtitle = stringResource(R.string.doze_enable_desc),
                        checked = state.dozeEnabled,
                        onCheckedChange = { repo.setBoolean(DozeRepository.KEY_DOZE_ENABLED, it) },
                        icon = Icons.Default.DisplaySettings,
                        showDivider = false,
                    )
                }
            }

            item(key = "doze_gestures", contentType = "switch_group") {
                SectionLabel(
                    label = stringResource(R.string.doze_gestures_section),
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                )
                ExpressiveList(count = 3) { index ->
                    when (index) {
                        0 -> SwitchSettingItem(
                            title = stringResource(R.string.doze_hand_wave_title),
                            subtitle = stringResource(R.string.doze_hand_wave_desc),
                            checked = state.handWaveEnabled,
                            onCheckedChange = { repo.setBoolean(DozeRepository.KEY_HAND_WAVE, it) },
                            icon = Icons.Default.PanTool,
                            enabled = state.dozeEnabled,
                        )
                        1 -> SwitchSettingItem(
                            title = stringResource(R.string.doze_pocket_title),
                            subtitle = stringResource(R.string.doze_pocket_desc),
                            checked = state.pocketEnabled,
                            onCheckedChange = { repo.setBoolean(DozeRepository.KEY_POCKET, it) },
                            icon = Icons.Default.Smartphone,
                            enabled = state.dozeEnabled,
                        )
                        2 -> SwitchSettingItem(
                            title = stringResource(R.string.doze_pickup_title),
                            subtitle = stringResource(R.string.doze_pickup_desc),
                            checked = state.pickUpEnabled,
                            onCheckedChange = { repo.setBoolean(DozeRepository.KEY_PICK_UP, it) },
                            icon = Icons.Default.PhonelinkRing,
                            enabled = state.dozeEnabled,
                            showDivider = false,
                        )
                    }
                }
            }
        }
    }
}
