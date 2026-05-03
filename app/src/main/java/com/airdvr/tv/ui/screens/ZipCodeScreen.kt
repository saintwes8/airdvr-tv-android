package com.airdvr.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.airdvr.tv.ui.viewmodels.ZipCodeViewModel

private val Bg = Color(0xFF0D1117)
private val FieldBg = Color(0xFF161B22)
private val FieldBorder = Color(0xFF30363D)
private val TextW = Color(0xFFE6EDF3)
private val TextMuted = Color(0xFF484F58)
private val TextGray = Color(0xFF8B949E)
private val BtnBg = Color(0xFF21262D)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ZipCodeScreen(
    onContinue: () -> Unit,
    viewModel: ZipCodeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(uiState.success) {
        if (uiState.success) onContinue()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Bg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(360.dp)
        ) {
            androidx.tv.material3.Text(
                "Set Your Location",
                fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextW
            )
            Spacer(Modifier.height(8.dp))
            androidx.tv.material3.Text(
                "Enter your zip code to get local channels and guide data",
                fontSize = 14.sp, color = TextGray, textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(40.dp))

            OutlinedTextField(
                value = uiState.zipCode,
                onValueChange = viewModel::onZipChange,
                placeholder = { Text("Zip Code", color = TextMuted, fontSize = 18.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    viewModel.submit()
                }),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(0.dp),
                textStyle = LocalTextStyle.current.copy(
                    color = TextW, fontSize = 18.sp, textAlign = TextAlign.Center
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TextW.copy(alpha = 0.4f),
                    unfocusedBorderColor = FieldBorder,
                    focusedTextColor = TextW, unfocusedTextColor = TextW,
                    cursorColor = TextW,
                    focusedContainerColor = FieldBg, unfocusedContainerColor = FieldBg
                )
            )

            Spacer(Modifier.height(20.dp))

            OutlinedButton(
                onClick = { viewModel.submit() },
                enabled = !uiState.isLoading && uiState.zipCode.length == 5,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(0.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = BtnBg, contentColor = TextW
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, FieldBorder)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        color = TextW, modifier = Modifier.size(20.dp), strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        "Continue", fontSize = 16.sp,
                        fontWeight = FontWeight.Medium, color = TextW
                    )
                }
            }

            if (uiState.error != null) {
                Spacer(Modifier.height(12.dp))
                Text(uiState.error ?: "", color = Color(0xFFEF4444), fontSize = 14.sp)
            }
        }
    }
}
