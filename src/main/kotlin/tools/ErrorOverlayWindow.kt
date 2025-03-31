package tools

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay

@Composable
fun ErrorOverlayWindow(errorText: String, onDismiss: () -> Unit) {
    val windowState = rememberWindowState(
        width = 1200.dp,
        height = 500.dp,
        position = WindowPosition(Alignment.TopCenter)
    )
    var startAnimation by remember { mutableStateOf(false) }
    val alphaAnim by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 500)
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(5000)
        onDismiss()
    }

    Window(
        onCloseRequest = onDismiss,
        title = "",
        state = windowState,
        undecorated = true,
        alwaysOnTop = true
    ) {
        Box(
            modifier = Modifier.fillMaxSize().alpha(alphaAnim),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = errorText,
                color = MaterialTheme.colors.error,
                style = MaterialTheme.typography.h3
            )
        }
    }
}