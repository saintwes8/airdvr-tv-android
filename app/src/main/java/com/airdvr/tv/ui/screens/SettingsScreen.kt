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

    Column(
        modifier = Modifier.fillMaxSize().background(PlexBg)
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
                    uiState.userZipCode.ifBlank { "Not set" },
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

            // ── Storage ──
            if (uiState.storageInfo != null) {
                item { SectionLabel("STORAGE") }
                item {
                    val info = uiState.storageInfo!!
                    SettingsRow("Used", "${info.used ?: "?"} / ${info.total ?: "?"}")
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
