package com.airdvr.tv.ui.screens

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.airdvr.tv.ui.theme.*
import com.airdvr.tv.ui.viewmodels.SettingsViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(
    onLogout: () -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showZipDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.load() }

    Box(modifier = Modifier.fillMaxSize().background(PlexBg)) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = PlexTextPrimary)
            }
            Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = PlexTextPrimary)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // ── Account ──
            item { SectionLabel("ACCOUNT") }
            item { SettingsRow("Email", uiState.userEmail.ifBlank { "Not set" }) }
            item { SettingsRow("Plan", uiState.userPlan.ifBlank { "Free" }) }
            item {
                SettingsRow(
                    "Location",
                    uiState.userZipCode.ifBlank { "Auto-detected" },
                    onClick = { showZipDialog = true }
                )
            }
            item { Divider() }

            // ── Tuner Status ──
            item { SectionLabel("TUNER STATUS") }
            item {
                SettingsRow(
                    "Tuner",
                    "HDHomeRun FLEX DUO \u00B7 ${uiState.tunerTotal} tuners \u00B7 ${uiState.tunersInUse} in use"
                )
            }
            item { Divider() }

            // ── Recording Storage ──
            item { SectionLabel("RECORDING STORAGE") }
            item {
                val location = if (uiState.uploadToCloud) {
                    "Cloud"
                } else if (uiState.deviceName.isNotBlank()) {
                    uiState.deviceName
                } else {
                    "Local device"
                }
                SettingsRow("Recordings saved on", location)
            }
            item {
                val usedText = formatStorageUsage(uiState.storageInfo, uiState.recordingsUsedMb)
                SettingsRow("Storage used", usedText)
            }
            item {
                ToggleRow(
                    label = "Upload to Cloud",
                    isEnabled = uiState.isPro,
                    isOn = uiState.uploadToCloud,
                    trailingHint = if (!uiState.isPro) "Requires Pro subscription" else null,
                    onToggle = { viewModel.toggleUploadToCloud() }
                )
            }
            if (uiState.uploadToCloud) {
                item {
                    ToggleRow(
                        label = "Keep local copy after cloud upload",
                        isEnabled = true,
                        isOn = uiState.keepLocalCopyAfterCloud,
                        trailingHint = null,
                        onToggle = { viewModel.toggleKeepLocalCopy() }
                    )
                }
            }
            item { Divider() }

            // ── Cloud Storage ──
            if (uiState.cloudStorageUsage != null) {
                item { SectionLabel("CLOUD STORAGE") }
                item {
                    CloudUsageRow(
                        used = uiState.cloudStorageUsage!!.usedBytes,
                        total = uiState.cloudStorageUsage!!.totalBytes,
                        percent = uiState.cloudStorageUsage!!.percentUsed
                    )
                }
                item {
                    val days = uiState.cloudRetentionDays
                    val label = days?.let { "$it days" } ?: "Never"
                    SettingsRow(
                        label = "Auto-delete after",
                        value = label,
                        onClick = {
                            val cycle = listOf<Int?>(7, 14, 30, 60, 90, null)
                            val current = days
                            val nextIdx = (cycle.indexOf(current) + 1) % cycle.size
                            viewModel.setCloudRetentionDays(cycle[nextIdx])
                        }
                    )
                }
                item { Divider() }
            }

            // ── Disk Storage ──
            if (uiState.storageInfo != null) {
                item { SectionLabel("DISK STORAGE") }
                item {
                    val info = uiState.storageInfo!!
                    SettingsRow("Capacity", "${formatBytes(info.used)} / ${formatBytes(info.total)}")
                }
                item { Divider() }
            }

            // ── Playback ──
            item { SectionLabel("PLAYBACK") }
            item {
                SettingsRow(
                    "Quality",
                    uiState.selectedQuality,
                    onClick = { viewModel.cycleQuality() }
                )
            }
            item { Divider() }

            // ── Guide Appearance ──
            item { SectionLabel("GUIDE APPEARANCE") }
            // Opacity slider
            item {
                var isFocused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .onFocusChanged { isFocused = it.isFocused }
                        .focusable()
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.nativeKeyEvent.keyCode) {
                                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                                        viewModel.setGuideOpacity((uiState.guideOpacity - 0.05f).coerceIn(0f, 1f))
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                        viewModel.setGuideOpacity((uiState.guideOpacity + 0.05f).coerceIn(0f, 1f))
                                        true
                                    }
                                    else -> false
                                }
                            } else false
                        }
                        .background(if (isFocused) PlexCard else Color.Transparent),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.width(2.dp).fillMaxHeight()
                            .background(if (isFocused) PlexTextPrimary else Color.Transparent)
                    )
                    Row(
                        modifier = Modifier.weight(1f).padding(vertical = 10.dp, horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Guide Opacity", fontSize = 15.sp, color = PlexTextPrimary)
                        Slider(
                            value = uiState.guideOpacity,
                            onValueChange = { viewModel.setGuideOpacity(it) },
                            modifier = Modifier.weight(1f).height(20.dp),
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = PlexTextPrimary,
                                activeTrackColor = PlexTextPrimary,
                                inactiveTrackColor = PlexBorder
                            )
                        )
                        Text(
                            "${(uiState.guideOpacity * 100).toInt()}%",
                            fontSize = 14.sp, color = PlexTextSecondary,
                            modifier = Modifier.width(40.dp),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
            // Color picker
            item {
                val colorOptions = listOf(
                    "#21262D" to Color(0xFF21262D),
                    "#1B2838" to Color(0xFF1B2838),
                    "#1B3228" to Color(0xFF1B3228),
                    "#28183B" to Color(0xFF28183B),
                    "#000000" to Color(0xFF000000)
                )
                var isFocused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .onFocusChanged { isFocused = it.isFocused }
                        .focusable()
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                val hexList = colorOptions.map { it.first }
                                val currentIdx = hexList.indexOf(uiState.guideColor).coerceAtLeast(0)
                                when (keyEvent.nativeKeyEvent.keyCode) {
                                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                                        if (currentIdx > 0) viewModel.setGuideColor(hexList[currentIdx - 1])
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                        if (currentIdx < hexList.size - 1) viewModel.setGuideColor(hexList[currentIdx + 1])
                                        true
                                    }
                                    else -> false
                                }
                            } else false
                        }
                        .background(if (isFocused) PlexCard else Color.Transparent),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.width(2.dp).fillMaxHeight()
                            .background(if (isFocused) PlexTextPrimary else Color.Transparent)
                    )
                    Row(
                        modifier = Modifier.weight(1f).padding(vertical = 10.dp, horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Guide Color", fontSize = 15.sp, color = PlexTextPrimary)
                        Spacer(Modifier.weight(1f))
                        colorOptions.forEach { (hex, color) ->
                            val selected = hex == uiState.guideColor
                            Box(
                                Modifier.size(28.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        if (selected) 2.dp else 1.dp,
                                        if (selected) PlexTextPrimary else PlexBorder,
                                        CircleShape
                                    )
                            )
                        }
                    }
                }
            }
            item { Divider() }

            // ── About ──
            item { SectionLabel("ABOUT") }
            item { SettingsRow("App Version", uiState.appVersion.ifBlank { "1.0.0" }) }
            item { Divider() }

            // Sign Out
            item {
                Spacer(Modifier.height(16.dp))
                var isFocused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .onFocusChanged { isFocused = it.isFocused }
                        .focusable()
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown &&
                                (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                 keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)
                            ) { onLogout(); true } else false
                        }
                        .background(if (isFocused) PlexCard else Color.Transparent),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.width(2.dp).fillMaxHeight()
                            .background(if (isFocused) PlexTextPrimary else Color.Transparent)
                    )
                    Text(
                        "Sign Out",
                        fontSize = 15.sp, fontWeight = FontWeight.Medium,
                        color = Color(0xFFEF4444),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).padding(vertical = 12.dp)
                    )
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    // Toast
    if (uiState.toastMessage != null) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .background(PlexCard, RoundedCornerShape(8.dp))
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text(uiState.toastMessage ?: "", fontSize = 14.sp, color = PlexTextPrimary)
        }
    }
    } // end Box

    if (showZipDialog) {
        ZipCodeDialog(
            currentZip = uiState.userZipCode,
            onDismiss = { showZipDialog = false },
            onSave = { zip ->
                viewModel.updateZipCode(zip)
                showZipDialog = false
            }
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SectionLabel(label: String) {
    Text(
        label, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        color = PlexTextTertiary, letterSpacing = 1.2.sp,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    var isFocused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .then(
                if (onClick != null) Modifier.onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown &&
                        (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                         keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)
                    ) { onClick(); true } else false
                } else Modifier
            )
            .background(if (isFocused) PlexCard else Color.Transparent),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.width(2.dp).fillMaxHeight()
                .background(if (isFocused) PlexTextPrimary else Color.Transparent)
        )
        Row(
            modifier = Modifier.weight(1f).padding(vertical = 10.dp, horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 15.sp, color = PlexTextPrimary)
            Text(value, fontSize = 14.sp, color = PlexTextSecondary)
        }
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = PlexBorder,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ToggleRow(
    label: String,
    isEnabled: Boolean,
    isOn: Boolean,
    trailingHint: String?,
    onToggle: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val rowAlpha = if (isEnabled) 1f else 0.55f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(enabled = true)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                     keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)
                ) {
                    if (isEnabled) onToggle()
                    true
                } else false
            }
            .background(if (isFocused) PlexCard else Color.Transparent),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.width(2.dp).fillMaxHeight()
                .background(if (isFocused) PlexTextPrimary else Color.Transparent)
        )
        Row(
            modifier = Modifier.weight(1f).padding(vertical = 10.dp, horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    fontSize = 15.sp,
                    color = if (isEnabled) PlexTextPrimary else PlexTextSecondary.copy(alpha = rowAlpha)
                )
                if (!trailingHint.isNullOrBlank()) {
                    Text(
                        trailingHint,
                        fontSize = 12.sp,
                        color = PlexTextTertiary
                    )
                }
            }
            ToggleSwitch(isOn = isOn, isEnabled = isEnabled)
        }
    }
}

@Composable
private fun ToggleSwitch(isOn: Boolean, isEnabled: Boolean) {
    val trackColor = when {
        !isEnabled -> PlexBorder
        isOn -> Color(0xFF22C55E)
        else -> PlexTextTertiary.copy(alpha = 0.5f)
    }
    Box(
        modifier = Modifier
            .width(40.dp).height(22.dp)
            .background(trackColor, RoundedCornerShape(11.dp))
            .padding(2.dp),
        contentAlignment = if (isOn) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            Modifier.size(18.dp).clip(CircleShape).background(Color.White)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CloudUsageRow(used: Long, total: Long, percent: Float) {
    val barColor = when {
        percent >= 1.0f -> Color(0xFFEF4444) // red
        percent >= 0.8f -> Color(0xFFF59E0B) // amber
        else -> Color(0xFF3B82F6) // blue
    }
    val pct = percent.coerceIn(0f, 1f)
    val totalLabel = if (total > 0L) formatGb(total) else "—"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${formatGb(used)} / $totalLabel Cloud Storage",
                fontSize = 15.sp, color = PlexTextPrimary
            )
            Text(
                "${(percent * 100).toInt()}%",
                fontSize = 14.sp, color = PlexTextSecondary
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { pct },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = barColor,
            trackColor = PlexBorder
        )
    }
}

private fun formatGb(bytes: Long): String {
    if (bytes <= 0L) return "0 GB"
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    return if (gb >= 10) "%.0f GB".format(gb) else "%.1f GB".format(gb)
}

private fun formatStorageUsage(info: com.airdvr.tv.data.models.StorageInfo?, recordingsUsedMb: Float): String {
    val totalFromRecordings = if (recordingsUsedMb > 0f) {
        if (recordingsUsedMb >= 1024f) "%.1f GB".format(recordingsUsedMb / 1024f)
        else "%.0f MB".format(recordingsUsedMb)
    } else null
    val totalFromStorage = info?.used?.takeIf { it > 0L }?.let { formatBytes(it) }
    return totalFromStorage ?: totalFromRecordings ?: "0 MB"
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    if (gb >= 1.0) return if (gb >= 10) "%.0f GB".format(gb) else "%.1f GB".format(gb)
    val mb = bytes / (1024.0 * 1024.0)
    return "%.0f MB".format(mb)
}

@Composable
private fun ZipCodeDialog(
    currentZip: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var zip by remember { mutableStateOf(currentZip) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PlexCard,
        title = {
            androidx.compose.material3.Text(
                "Change Location",
                color = PlexTextPrimary, fontWeight = FontWeight.Bold
            )
        },
        text = {
            OutlinedTextField(
                value = zip,
                onValueChange = { if (it.length <= 5 && it.all { c -> c.isDigit() }) zip = it },
                placeholder = {
                    androidx.compose.material3.Text("Zip Code", color = PlexTextTertiary)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { if (zip.length == 5) onSave(zip) }),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                textStyle = LocalTextStyle.current.copy(color = PlexTextPrimary, fontSize = 16.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PlexTextPrimary.copy(alpha = 0.4f),
                    unfocusedBorderColor = PlexBorder,
                    focusedTextColor = PlexTextPrimary, unfocusedTextColor = PlexTextPrimary,
                    cursorColor = PlexTextPrimary,
                    focusedContainerColor = PlexSurface, unfocusedContainerColor = PlexSurface
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (zip.length == 5) onSave(zip) },
                enabled = zip.length == 5
            ) {
                androidx.compose.material3.Text("Save", color = PlexTextPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                androidx.compose.material3.Text("Cancel", color = PlexTextSecondary)
            }
        }
    )
}
