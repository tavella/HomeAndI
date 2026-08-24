package com.example.lmstudioclient.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lmstudioclient.ui.theme.ErrorRed
import com.example.lmstudioclient.ui.theme.SuccessGreen
import com.example.lmstudioclient.ui.theme.WarningAmber

sealed class ConnectionState {
    object Idle : ConnectionState()
    object Testing : ConnectionState()
    data class Connected(val modelCount: Int = 0) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

@Composable
fun ConnectionStatusBadge(
    state: ConnectionState,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, text, icon) = when (state) {
        is ConnectionState.Connected -> Quadruple(
            SuccessGreen.copy(alpha = 0.15f),
            SuccessGreen,
            "Online (${state.modelCount} models)",
            Icons.Rounded.CheckCircle
        )
        is ConnectionState.Error -> Quadruple(
            ErrorRed.copy(alpha = 0.15f),
            ErrorRed,
            "Offline",
            Icons.Rounded.Error
        )
        is ConnectionState.Testing -> Quadruple(
            WarningAmber.copy(alpha = 0.15f),
            WarningAmber,
            "Testing...",
            Icons.Rounded.Refresh
        )
        is ConnectionState.Idle -> Quadruple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Not Checked",
            null
        )
    }

    val animatedBg by animateColorAsState(targetValue = bgColor, label = "badgeBg")

    Surface(
        modifier = modifier.clip(RoundedCornerShape(16.dp)),
        color = animatedBg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(textColor)
            )
            Text(
                text = text,
                color = textColor,
                fontSize = 12.sp,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
