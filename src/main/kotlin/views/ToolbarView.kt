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




@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ScanDialog(mode: String, viewModel: MaterialViewModel, onDismiss: () -> Unit) {
    LaunchedEffect(mode) { viewModel.selectedMode = mode }

    var empfaenger by remember { mutableStateOf(viewModel.empfaengerName) }
    var seriennummer by remember { mutableStateOf("") }
    var notiz by remember { mutableStateOf("") }
    val log = remember { mutableStateListOf<String>() }

    val focusSerial = remember { FocusRequester() }
    val focusEmpfaenger = remember { FocusRequester() }

    val dialogState = rememberDialogState(width = 1200.dp, height = 500.dp)

    var showEmpfaengerWarning by remember { mutableStateOf(false) }
    var errorOverlayText by remember { mutableStateOf<String?>(null) }

    DialogWindow(onCloseRequest = onDismiss, state = dialogState, title = "") {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .focusable()
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.weight(1f)) {

                    if (mode == "Ausgabe") {
                        OutlinedTextField(
                            value = empfaenger,
                            onValueChange = {
                                empfaenger = it
                                showEmpfaengerWarning = false
                            },
                            label = { Text("Empfänger") },
                            singleLine = true,
                            isError = showEmpfaengerWarning,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusEmpfaenger)
                                .onPreviewKeyEvent {
                                    if (it.key == Key.Enter && it.type == KeyEventType.KeyUp) {
                                        if (empfaenger.isBlank()) {
                                            showEmpfaengerWarning = true
                                            errorOverlayText = "Empfänger darf nicht leer sein"
                                        } else {
                                            focusSerial.requestFocus()
                                        }
                                        true
                                    } else false
                                }
                        )
                        if (showEmpfaengerWarning)
                            Text("Empfänger darf nicht leer sein.", color = MaterialTheme.colors.error)

                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    OutlinedTextField(
                        value = seriennummer,
                        onValueChange = { seriennummer = it },
                        label = { Text("Seriennummer") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusSerial)
                            .onPreviewKeyEvent {
                                if (it.key == Key.Enter && it.type == KeyEventType.KeyUp) {
                                    if (seriennummer.isNotBlank()) {
                                        if (mode == "Ausgabe" && empfaenger.isBlank()) {
                                            showEmpfaengerWarning = true
                                            errorOverlayText = "Empfänger darf nicht leer sein"
                                            focusEmpfaenger.requestFocus()
                                        } else {
                                            // Position hier ggf. automatisch auf "Lager" setzen
                                            viewModel.tempPosition = "Lager"

                                            val result = viewModel.processScan(seriennummer)
                                            if (result != null && result.toString().trim().isNotEmpty()) {
                                                log.add("$result SN $seriennummer")
                                            } else {
                                                errorOverlayText = "Unbekannte Seriennummer oder Fehler"
                                            }
                                            seriennummer = ""
                                            focusSerial.requestFocus()
                                        }
                                    }
                                    true
                                } else false
                            }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = notiz,
                        onValueChange = { notiz = it },
                        label = { Text("Notiz") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 3
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { println("PDF-Log anzeigen") }) {
                            Text("Verlauf drucken")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            if (mode == "Ausgabe" && empfaenger.isBlank()) {
                                showEmpfaengerWarning = true
                                errorOverlayText = "Empfänger darf nicht leer sein"
                            } else {
                                // Jetzt wird alles dauerhaft ins ViewModel übernommen
                                viewModel.empfaengerName = empfaenger
                                viewModel.tempLog = log.toList() // falls du speichern willst
                                onDismiss()
                            }
                        }) {
                            Text("$mode beenden")
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text("Verlauf", style = MaterialTheme.typography.subtitle1)
                    Spacer(modifier = Modifier.height(8.dp))
                    val grouped = log.filter { it.contains("SN") }
                        .groupBy { it.substringBefore(" SN") }
                        .toSortedMap()

                    LazyColumn(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                        grouped.forEach { (material, entries) ->
                            item {
                                Text(material, style = MaterialTheme.typography.subtitle1)
                            }
                            entries.asReversed().forEachIndexed { index, entry ->
                                item {
                                    Text("${entries.size - index}. $entry", style = MaterialTheme.typography.body2)
                                }
                            }
                            item { Spacer(modifier = Modifier.height(8.dp)) }
                        }
                    }
                }
            }
        }
    }

    // Initial-Fokus
    LaunchedEffect(Unit) {
        delay(100)
        if (mode == "Ausgabe" && empfaenger.isBlank()) focusEmpfaenger.requestFocus()
        else focusSerial.requestFocus()
    }

    // Fehleranzeige (Overlay)
    errorOverlayText?.let { text ->
        ErrorOverlayWindow(errorText = text) {
            errorOverlayText = null
        }
    }
}


