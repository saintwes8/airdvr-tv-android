package com.airdvr.tv.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.airdvr.tv.ui.theme.PlexTextPrimary
import com.airdvr.tv.ui.theme.PlexTextSecondary

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LoadingSpinner(
    message: String = "",
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = PlexTextPrimary,
                modifier = Modifier.size(32.dp),
                strokeWidth = 2.dp
            )
            if (message.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(message, color = PlexTextSecondary, fontSize = 14.sp)
            }
        }
    }
}
