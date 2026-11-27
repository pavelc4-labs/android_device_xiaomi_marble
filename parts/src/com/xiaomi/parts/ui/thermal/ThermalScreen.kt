package com.xiaomi.parts.ui.thermal

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.xiaomi.parts.R
import com.xiaomi.parts.data.ThermalProfile
import com.xiaomi.parts.data.ThermalRepository
import com.xiaomi.parts.ui.components.AppIconCache
import com.xiaomi.parts.ui.components.ExpScreenScaffold
import com.xiaomi.parts.ui.components.SectionLabel
import com.xiaomi.parts.ui.components.bouncyClickable
import com.xiaomi.parts.ui.components.expressiveItemShape

data class ThermalAppItem(val packageName: String, val label: String)

enum class SortMode { NAME_ASC, NAME_DESC, PACKAGE_ASC }

@Composable
fun rememberAppIcon(packageName: String, packageManager: PackageManager): ImageBitmap? =
    produceState<ImageBitmap?>(initialValue = AppIconCache.get(packageName), packageName, packageManager) {
        if (value == null && !AppIconCache.isMiss(packageName)) {
            value = withContext(Dispatchers.IO) {
                AppIconCache.load(packageManager, packageName)
            }
        }
    }.value

@Composable
fun ThermalScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val pm = remember(context) { context.packageManager }
    val repo = remember(context) { ThermalRepository(context) }

    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(SortMode.NAME_ASC) }
    var installedApps by remember { mutableStateOf<List<ThermalAppItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val activeProfiles by repo.profilesFlow.collectAsState()

    var selectedAppForSheet by remember { mutableStateOf<ThermalAppItem?>(null) }

    LaunchedEffect(pm) {
        val loaded = withContext(Dispatchers.IO) {
            val userApps = runCatching {
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
                    .map { appInfo ->
                        val label = runCatching { pm.getApplicationLabel(appInfo).toString() }
                            .getOrDefault(appInfo.packageName)
                        ThermalAppItem(packageName = appInfo.packageName, label = label)
                    }
            }.getOrDefault(emptyList())

            if (userApps.isNotEmpty()) {
                userApps.sortedBy { it.label.lowercase() }
            } else {
                listOf(
                    ThermalAppItem("com.mobile.legends", "Mobile Legends"),
                    ThermalAppItem("com.miHoYo.GenshinImpact", "Genshin Impact"),
                    ThermalAppItem("com.android.chrome", "Google Chrome"),
                    ThermalAppItem("com.google.android.youtube", "YouTube"),
                    ThermalAppItem("org.telegram.messenger", "Telegram"),
                    ThermalAppItem("com.spotify.music", "Spotify"),
                )
            }
        }
        installedApps = loaded
        isLoading = false
    }

    val (activeList, inactiveList) = remember(installedApps, activeProfiles, searchQuery, sortMode) {
        val (active, inactive) = installedApps.partition {
            val profile = activeProfiles[it.packageName]
            profile != null && profile != ThermalProfile.DEFAULT
        }

        val comparator: Comparator<ThermalAppItem> = when (sortMode) {
            SortMode.NAME_ASC -> compareBy { it.label.lowercase() }
            SortMode.NAME_DESC -> compareByDescending { it.label.lowercase() }
            SortMode.PACKAGE_ASC -> compareBy { it.packageName.lowercase() }
        }

        val matchesQuery: (ThermalAppItem) -> Boolean = {
            searchQuery.isEmpty() ||
                it.label.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
        }

        active.filter(matchesQuery).sortedWith(comparator) to
            inactive.filter(matchesQuery).sortedWith(comparator)
    }

    ExpScreenScaffold(
        title = stringResource(R.string.feat_thermal_title),
        subtitle = stringResource(R.string.thermal_subtitle),
        onBack = onBack,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Search & Sort Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            stringResource(R.string.thermal_search_placeholder),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(26.dp),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.action_clear),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                )

                val sortActive = sortMode != SortMode.NAME_ASC
                FilledTonalIconButton(
                    onClick = {
                        sortMode = when (sortMode) {
                            SortMode.NAME_ASC -> SortMode.NAME_DESC
                            SortMode.NAME_DESC -> SortMode.PACKAGE_ASC
                            SortMode.PACKAGE_ASC -> SortMode.NAME_ASC
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (sortActive) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (sortActive) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    modifier = Modifier.size(52.dp),
                ) {
                    val sortIcon = when (sortMode) {
                        SortMode.NAME_ASC -> Icons.AutoMirrored.Filled.Sort
                        SortMode.NAME_DESC -> Icons.Outlined.SwapVert
                        SortMode.PACKAGE_ASC -> Icons.Outlined.Apps
                    }
                    Icon(
                        imageVector = sortIcon,
                        contentDescription = stringResource(R.string.action_sort),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // App List
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
            ) {
                if (isLoading) {
                    item(key = "loading", contentType = "loading") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                if (activeList.isNotEmpty()) {
                    item(key = "header_active_profiles", contentType = "section_header") {
                        SectionLabel(
                            label = stringResource(R.string.thermal_active_profiles),
                            count = activeList.size,
                            modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                        )
                    }

                    itemsIndexed(
                        items = activeList,
                        key = { _, app -> "active-${app.packageName}" },
                        contentType = { _, _ -> "active_app" },
                    ) { index, app ->
                        ThermalAppRow(
                            app = app,
                            packageManager = pm,
                            shape = expressiveItemShape(index, activeList.size),
                            badgeText = activeProfiles[app.packageName]?.badge,
                            onClick = { selectedAppForSheet = app },
                        )
                    }
                }

                if (inactiveList.isNotEmpty()) {
                    item(key = "header_installed_apps", contentType = "section_header") {
                        SectionLabel(
                            label = stringResource(R.string.thermal_installed_apps),
                            count = inactiveList.size,
                            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
                        )
                    }

                    itemsIndexed(
                        items = inactiveList,
                        key = { _, app -> "installed-${app.packageName}" },
                        contentType = { _, _ -> "installed_app" },
                    ) { index, app ->
                        ThermalAppRow(
                            app = app,
                            packageManager = pm,
                            shape = expressiveItemShape(index, inactiveList.size),
                            onClick = { selectedAppForSheet = app },
                        )
                    }
                }

                if (!isLoading && activeList.isEmpty() && inactiveList.isEmpty()) {
                    item(key = "empty_search", contentType = "empty") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.thermal_no_apps_found, searchQuery),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    selectedAppForSheet?.let { targetApp ->
        ThermalProfilePickerSheet(
            targetApp = targetApp,
            currentProfile = activeProfiles[targetApp.packageName] ?: ThermalProfile.DEFAULT,
            onSelectProfile = { profile ->
                repo.setProfile(targetApp.packageName, profile)
                selectedAppForSheet = null
            },
            onDismiss = { selectedAppForSheet = null },
        )
    }
}

@Composable
private fun ThermalAppRow(
    app: ThermalAppItem,
    packageManager: PackageManager,
    shape: RoundedCornerShape,
    badgeText: String? = null,
    onClick: () -> Unit,
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .bouncyClickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AppIconItem(packageName = app.packageName, packageManager = packageManager)

            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (badgeText != null) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 15.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            if (badgeText != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AppIconItem(packageName: String, packageManager: PackageManager) {
    val bitmap = rememberAppIcon(packageName, packageManager)
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Icon(
                imageVector = Icons.Outlined.Android,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
