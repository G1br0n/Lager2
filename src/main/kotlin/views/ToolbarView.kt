package views

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.*
import kotlinx.coroutines.delay
import tools.ErrorOverlayWindow
import views.dialogs.PasswordPrompt
import viewModels.MaterialViewModel
import views.dialogs.ScanDialog

@Composable
fun ToolbarView(viewModel: MaterialViewModel, onNewMaterialClick: () -> Unit) {
    var showDialog by remember { mutableStateOf<String?>(null) }
    var showPasswordDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    viewModel.selectedMode = "Empfang"
                    showDialog = "Empfang"
                },
                modifier = Modifier.height(48.dp).padding(horizontal = 4.dp)
            ) {
                Text("Empfang", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Button(
                onClick = {
                    viewModel.selectedMode = "Ausgabe"
                    showDialog = "Ausgabe"
                },
                modifier = Modifier.height(48.dp).padding(horizontal = 4.dp)
            ) {
                Text("Ausgabe", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = viewModel.filterText,
                onValueChange = { viewModel.filterText = it },
                label = { Text("Filter", fontSize = 16.sp) },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.wrapContentWidth()
            ) {
                Checkbox(
                    checked = viewModel.filterActive,
                    onCheckedChange = {
                        viewModel.filterActive = it
                    }
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    if (viewModel.filterActive) "Filter AN" else "Filter AUS",
                    fontSize = 16.sp
                )
            }
        }

        Button(
            onClick = { showPasswordDialog = true },
            modifier = Modifier.height(48.dp).padding(horizontal = 4.dp)
        ) {
            Text("Neu", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }

    showDialog?.let { mode ->
        ScanDialog(
            mode = mode,
            viewModel = viewModel,
            onDismiss = { showDialog = null }
        )
    }

    if (showPasswordDialog) {
        PasswordPrompt(
            onConfirm = {
                if (it == "test") {
                    onNewMaterialClick()
                }
                showPasswordDialog = false
            },
            onCancel = { showPasswordDialog = false }
        )
    }
}







