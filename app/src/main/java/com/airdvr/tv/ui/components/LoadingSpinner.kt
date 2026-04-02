package com.airdvr.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.airdvr.tv.ui.theme.AirDVRBlue
import com.airdvr.tv.ui.theme.AirDVRNavy
import com.airdvr.tv.ui.theme.AirDVRTextPrimary
import com.airdvr.tv.ui.theme.AirDVRTextSecondary

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LoadingSpinner(
    message: String = "Tuning...",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(AirDVRNavy.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = message,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = AirDVRTextPrimary
            )
            CircularProgressIndicator(
                color = AirDVRBlue,
                trackColor = Color.White.copy(alpha = 0.2f),
                strokeWidth = 4.dp,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}
