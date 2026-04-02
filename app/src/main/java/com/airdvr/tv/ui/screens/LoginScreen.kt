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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airdvr.tv.data.repository.AuthRepository
import com.airdvr.tv.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showPinDialog by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val authRepo = remember { AuthRepository() }

    if (showPinDialog) {
        Dialog(onDismissRequest = { showPinDialog = false }) {
            Card(
                modifier = Modifier
                    .width(400.dp)
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = AirDVRCard)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "PIN Login",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = AirDVRTextPrimary
                    )
                    Text(
                        text = "Coming soon — PIN-based authentication will let you sign in without a keyboard.",
                        fontSize = 14.sp,
                        color = AirDVRTextSecondary
                    )
                    Button(
                        onClick = { showPinDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = AirDVRBlue)
                    ) {
                        Text("Close", color = Color.White)
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AirDVRNavy),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left branding panel
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .background(AirDVRCard),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "AirDVR",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = AirDVRBlue
                    )
                    Text(
                        text = "Your personal TV, anywhere.",
                        fontSize = 18.sp,
                        color = AirDVRTextSecondary
                    )
                }
            }

            // Right sign-in form
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .padding(horizontal = 64.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Sign In",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = AirDVRTextPrimary
                    )

                    // Email field
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = {
                            Text("Email", color = AirDVRTextSecondary, fontSize = 16.sp)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AirDVRBlue,
                            unfocusedBorderColor = AirDVRTextSecondary,
                            focusedTextColor = AirDVRTextPrimary,
                            unfocusedTextColor = AirDVRTextPrimary,
                            cursorColor = AirDVRBlue,
                            focusedContainerColor = AirDVRCard,
                            unfocusedContainerColor = AirDVRCard
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontSize = 18.sp)
                    )

                    // Password field
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = {
                            Text("Password", color = AirDVRTextSecondary, fontSize = 16.sp)
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                if (email.isNotBlank() && password.isNotBlank() && !isLoading) {
                                    scope.launch {
                                        isLoading = true
                                        errorMessage = null
                                        authRepo.login(email, password)
                                            .onSuccess { onLoginSuccess() }
                                            .onFailure { e -> errorMessage = e.message }
                                        isLoading = false
                                    }
                                }
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AirDVRBlue,
                            unfocusedBorderColor = AirDVRTextSecondary,
                            focusedTextColor = AirDVRTextPrimary,
                            unfocusedTextColor = AirDVRTextPrimary,
                            cursorColor = AirDVRBlue,
                            focusedContainerColor = AirDVRCard,
                            unfocusedContainerColor = AirDVRCard
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontSize = 18.sp)
                    )

                    // Error message
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = Color.Red,
                            fontSize = 14.sp
                        )
                    }

                    // Sign In button
                    Button(
                        onClick = {
                            scope.launch {
                                if (email.isBlank() || password.isBlank()) {
                                    errorMessage = "Please enter your email and password."
                                    return@launch
                                }
                                isLoading = true
                                errorMessage = null
                                authRepo.login(email, password)
                                    .onSuccess { onLoginSuccess() }
                                    .onFailure { e -> errorMessage = e.message }
                                isLoading = false
                            }
                        },
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AirDVRBlue,
                            disabledContainerColor = AirDVRBlue.copy(alpha = 0.5f)
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Sign In",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }

                    // PIN login option
                    TextButton(
                        onClick = { showPinDialog = true },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(
                            text = "Enter PIN instead",
                            color = AirDVRTextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
