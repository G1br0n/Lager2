package tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ToggleButtonBox(
    isChecked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(20.dp)
            .background(
                color = if (isChecked) Color(0xFF4CAF50) else Color.LightGray,
                shape = MaterialTheme.shapes.small
            )
            .border(1.dp, Color.DarkGray, shape = MaterialTheme.shapes.small)
            .clickable {
                onToggle()
            },
        contentAlignment = Alignment.Center
    ) {
        // optional: checkmark
        if (isChecked) {
            Text("✓", fontSize = 12.sp, color = Color.White)
        }
    }
}