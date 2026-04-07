package com.airdvr.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
            item { Divider() }

            // ── Tuner Status ──
            item { SectionLabel("TUNER STATUS") }
            if (uiState.tuners.isEmpty()) {
                item { SettingsRow("Tuners", "No tuners found") }
            } else {
                uiState.tuners.forEachIndexed { i, tuner ->
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Tuner ${i + 1}",
                                fontSize = 15.sp, color = PlexTextPrimary
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (tuner.connected == true) Color(0xFF22C55E) else Color(0xFFEF4444)
                                        )
                                )
                                Text(
                                    if (tuner.connected == true) "Connected" else "Offline",
                                    fontSize = 14.sp, color = PlexTextSecondary
                                )
                            }
                        }
                    }
                }
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
            item { SettingsRow("Quality", uiState.selectedQuality) }
            item { SettingsRow("Closed Captions", if (uiState.ccEnabled) "On" else "Off") }
            item { Divider() }

            // ── About ──
            item { SectionLabel("ABOUT") }
            item { SettingsRow("App Version", uiState.appVersion.ifBlank { "1.0.0" }) }
            item { Divider() }

            // Sign Out
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Sign Out",
                    fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    color = Color(0xFFEF4444),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                )
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
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
private fun SettingsRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp, color = PlexTextPrimary)
        Text(value, fontSize = 14.sp, color = PlexTextSecondary)
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
