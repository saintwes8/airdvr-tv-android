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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.airdvr.tv.data.repository.AuthRepository
import kotlinx.coroutines.launch

private val Bg = Color(0xFF000000)
private val FieldBg = Color(0xFF161B22)
private val FieldBorder = Color(0xFF30363D)
private val TextW = Color(0xFFE6EDF3)
private val TextMuted = Color(0xFF484F58)
private val TextGray = Color(0xFF8B949E)
private val BtnBg = Color(0xFF21262D)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit
) {
    val authRepo = remember { AuthRepository() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun doLogin() {
        if (email.isBlank() || password.isBlank()) { error = "Please enter email and password"; return }
        isLoading = true; error = null
        scope.launch {
            authRepo.login(email, password)
                .onSuccess { isLoading = false; onLoginSuccess() }
                .onFailure { isLoading = false; error = it.message }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Bg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(360.dp)
        ) {
            // Logo
            androidx.tv.material3.Text(
                "AirDVR", fontSize = 52.sp, fontWeight = FontWeight.Bold, color = TextW
            )
            Spacer(Modifier.height(8.dp))
            androidx.tv.material3.Text(
                "Cloud DVR for cord-cutters", fontSize = 16.sp, color = TextGray
            )

            Spacer(Modifier.height(48.dp))

            // Email
            OutlinedTextField(
                value = email, onValueChange = { email = it },
                placeholder = { Text("Email", color = TextMuted, fontSize = 16.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(0.dp),
                textStyle = LocalTextStyle.current.copy(color = TextW, fontSize = 16.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TextW.copy(alpha = 0.4f),
                    unfocusedBorderColor = FieldBorder,
                    focusedTextColor = TextW, unfocusedTextColor = TextW,
                    cursorColor = TextW,
                    focusedContainerColor = FieldBg, unfocusedContainerColor = FieldBg
                )
            )

            Spacer(Modifier.height(12.dp))

            // Password
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                placeholder = { Text("Password", color = TextMuted, fontSize = 16.sp) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); doLogin() }),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(0.dp),
                textStyle = LocalTextStyle.current.copy(color = TextW, fontSize = 16.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TextW.copy(alpha = 0.4f),
                    unfocusedBorderColor = FieldBorder,
                    focusedTextColor = TextW, unfocusedTextColor = TextW,
                    cursorColor = TextW,
                    focusedContainerColor = FieldBg, unfocusedContainerColor = FieldBg
                )
            )

            Spacer(Modifier.height(16.dp))

            // Sign In button
            OutlinedButton(
                onClick = { doLogin() },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(0.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = BtnBg, contentColor = TextW),
                border = androidx.compose.foundation.BorderStroke(1.dp, FieldBorder)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = TextW, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Sign In", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextW)
                }
            }

            // Error
            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(error ?: "", color = Color(0xFFEF4444), fontSize = 14.sp)
            }

            Spacer(Modifier.height(24.dp))
            Text("Enter PIN code instead", color = TextMuted, fontSize = 14.sp)
        }

        // Version
        Text(
            "v1.0.0", color = FieldBorder, fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
        )
    }
}
