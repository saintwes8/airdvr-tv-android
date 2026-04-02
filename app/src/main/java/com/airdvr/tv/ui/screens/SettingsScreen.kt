package com.airdvr.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.airdvr.tv.BuildConfig
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
    val context = LocalContext.current
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showQualityMenu by remember { mutableStateOf(false) }

    val qualityOptions = listOf("Auto", "1080p", "720p", "480p", "360p")

    LaunchedEffect(Unit) {
        viewModel.setAppVersion(BuildConfig.VERSION_NAME)
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Sign Out", color = AirDVRTextPrimary) },
            text = { Text("Are you sure you want to sign out?", color = AirDVRTextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logout()
                        onLogout()
                    }
                ) {
                    Text("Sign Out", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = AirDVRTextSecondary)
                }
            },
            containerColor = AirDVRCard
        )
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(AirDVRNavy)
    ) {
        // Back button column
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(60.dp)
                .background(AirDVRCard)
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = AirDVRTextPrimary
                )
            }
        }

        // Main settings content
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "Settings",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = AirDVRTextPrimary
            )

            // Account section
            SettingsSection(title = "Account", icon = Icons.Filled.AccountCircle) {
                SettingsRow(label = "Email", value = uiState.userEmail.ifBlank { "Not signed in" })
                SettingsRow(label = "Plan", value = uiState.userPlan)
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showLogoutDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Logout,
                        contentDescription = "Sign Out",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out", color = Color.White, fontSize = 15.sp)
                }
            }

            // Agent / Tuner Status section
            SettingsSection(title = "Tuner Status", icon = Icons.Filled.Router) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = AirDVRBlue,
                        strokeWidth = 2.dp
                    )
                } else if (uiState.tuners.isEmpty()) {
                    SettingsRow(label = "Status", value = "No tuners detected")
                } else {
                    uiState.tuners.forEachIndexed { idx, tuner ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AirDVRCard, RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Connection indicator dot
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(
                                        color = if (tuner.connected) AirDVRGreen else Color.Red,
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    )
                            )
                            Column {
                                Text(
                                    text = tuner.modelNumber ?: "Tuner ${idx + 1}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AirDVRTextPrimary
                                )
                                if (tuner.localIp != null) {
                                    Text(
                                        text = tuner.localIp,
                                        fontSize = 12.sp,
                                        color = AirDVRTextSecondary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = if (tuner.connected) "Connected" else "Disconnected",
                                fontSize = 13.sp,
                                color = if (tuner.connected) AirDVRGreen else Color.Red
                            )
                        }
                    }
                }
            }

            // Playback section
            SettingsSection(title = "Playback", icon = Icons.Filled.PlayCircle) {
                // Quality selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Stream Quality", fontSize = 15.sp, color = AirDVRTextPrimary)
                    Box {
                        TextButton(onClick = { showQualityMenu = !showQualityMenu }) {
                            Text(uiState.selectedQuality, color = AirDVRBlue)
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = AirDVRBlue)
                        }
                        DropdownMenu(
                            expanded = showQualityMenu,
                            onDismissRequest = { showQualityMenu = false },
                            modifier = Modifier.background(AirDVRCard)
                        ) {
                            qualityOptions.forEach { q ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            q,
                                            color = if (uiState.selectedQuality == q) AirDVRBlue else AirDVRTextPrimary
                                        )
                                    },
                                    onClick = {
                                        viewModel.setQuality(q)
                                        showQualityMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                // CC toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Closed Captions", fontSize = 15.sp, color = AirDVRTextPrimary)
                    Switch(
                        checked = uiState.ccEnabled,
                        onCheckedChange = { viewModel.toggleCC() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AirDVRBlue
                        )
                    )
                }
            }

            // Storage section
            SettingsSection(title = "Storage", icon = Icons.Filled.Storage) {
                val storage = uiState.storageInfo
                if (storage != null) {
                    val usedGb = storage.used / 1_073_741_824f
                    val totalGb = storage.total / 1_073_741_824f
                    val fraction = if (storage.total > 0) storage.used.toFloat() / storage.total.toFloat() else 0f

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "%.1f GB used".format(usedGb),
                                fontSize = 14.sp,
                                color = AirDVRTextPrimary
                            )
                            Text(
                                text = "%.1f GB total".format(totalGb),
                                fontSize = 14.sp,
                                color = AirDVRTextSecondary
                            )
                        }
                        LinearProgressIndicator(
                            progress = { fraction.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = when {
                                fraction > 0.9f -> Color.Red
                                fraction > 0.75f -> AirDVROrange
                                else -> AirDVRBlue
                            },
                            trackColor = Color.White.copy(alpha = 0.15f)
                        )
                        Text(
                            text = "%.1f GB free".format(storage.free / 1_073_741_824f),
                            fontSize = 12.sp,
                            color = AirDVRTextSecondary
                        )
                    }
                } else if (!uiState.isLoading) {
                    Text(
                        text = "Storage information unavailable",
                        fontSize = 14.sp,
                        color = AirDVRTextSecondary
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = AirDVRBlue,
                        strokeWidth = 2.dp
                    )
                }
            }

            // About section
            SettingsSection(title = "About", icon = Icons.Filled.Info) {
                SettingsRow(label = "Version", value = uiState.appVersion.ifBlank { BuildConfig.VERSION_NAME })
                SettingsRow(label = "Platform", value = BuildConfig.PLATFORM)
                SettingsRow(label = "Package", value = "com.airdvr.tv")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AirDVRCard, RoundedCornerShape(12.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = AirDVRBlue,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = AirDVRTextPrimary
            )
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
        content()
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            color = AirDVRTextSecondary
        )
        Text(
            text = value,
            fontSize = 15.sp,
            color = AirDVRTextPrimary,
            fontWeight = FontWeight.Medium
        )
    }
}

