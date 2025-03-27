package views


import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import viewModels.MaterialViewModel

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ToolbarView(
    viewModel: MaterialViewModel,
    onMissingName: () -> Unit,
    onNewMaterialClick: () -> Unit
) {
    val scannerFocusRequester = remember { FocusRequester() }
    val empfaengerFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { scannerFocusRequester.requestFocus() }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(modifier = Modifier.weight(1f)) {
            TextField(
                value = viewModel.scannerInput,
                onValueChange = { viewModel.scannerInput = it },
                label = { Text("Seriennummer scannen") },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(scannerFocusRequester)
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyUp && keyEvent.key == Key.Enter) {
                            if (viewModel.selectedMode == "Ausgabe" && viewModel.empfaengerName.isBlank()) {
                                onMissingName()
                                true
                            } else {
                                viewModel.processScan(viewModel.scannerInput)
                                viewModel.scannerInput = ""
                                scannerFocusRequester.requestFocus()
                                true
                            }
                        } else false
                    }
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (viewModel.selectedMode == "Ausgabe") {
                TextField(
                    value = viewModel.empfaengerName,
                    onValueChange = { viewModel.empfaengerName = it },
                    label = { Text("Empfänger") },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(empfaengerFocusRequester)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyUp && keyEvent.key == Key.Enter) {
                                scannerFocusRequester.requestFocus()
                                true
                            } else false
                        }
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Modus:", style = MaterialTheme.typography.subtitle1)
            Spacer(modifier = Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = (viewModel.selectedMode == "Empfang"),
                    onClick = {
                        viewModel.selectedMode = "Empfang"
                        viewModel.empfaengerName = ""
                    }
                )
                Text("Empfang")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = (viewModel.selectedMode == "Ausgabe"),
                    onClick = { viewModel.selectedMode = "Ausgabe" }
                )
                Text("Ausgabe")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(onClick = onNewMaterialClick) { Text("Neu") }
        }
    }
}