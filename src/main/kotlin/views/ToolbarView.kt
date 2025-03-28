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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import kotlinx.coroutines.delay
import viewModels.MaterialViewModel



@Composable
fun ToolbarView(viewModel: MaterialViewModel, onNewMaterialClick: () -> Unit) {
    var showDialog by remember { mutableStateOf<String?>(null) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                viewModel.selectedMode = "Empfang"
                showDialog = "Empfang"
            }) { Text("Empfang") }

            Spacer(modifier = Modifier.width(8.dp))

            Button(onClick = {
                viewModel.selectedMode = "Ausgabe"
                showDialog = "Ausgabe"
            }) { Text("Ausgabe") }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = viewModel.filterText,
                onValueChange = { viewModel.filterText = it },
                label = { Text("Filter") },
                modifier = Modifier.width(250.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = viewModel.filterActive,
                    onCheckedChange = { viewModel.filterActive = it }
                )
                Text("Filter an")
            }
        }

        Button(onClick = onNewMaterialClick) { Text("Neu") }
    }

    showDialog?.let { mode ->
        ScanDialog(
            mode = mode,
            viewModel = viewModel,
            onDismiss = { showDialog = null }
        )
    }
}


@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ScanDialog(mode: String, viewModel: MaterialViewModel, onDismiss: () -> Unit) {
    LaunchedEffect(mode) { viewModel.selectedMode = mode }

    var empfaenger by remember { mutableStateOf(viewModel.empfaengerName) }
    var abgeberName by remember { mutableStateOf("") }
    var seriennummer by remember { mutableStateOf("") }
    var notiz by remember { mutableStateOf("") }

    var showEmpfaengerWarning by remember { mutableStateOf(false) }
    var showAbgeberWarning by remember { mutableStateOf(false) }

    val focusSerial = remember { FocusRequester() }
    val focusEmpfaenger = remember { FocusRequester() }
    val focusAbgeber = remember { FocusRequester() }

    val log = remember { mutableStateListOf<String>() }

    val dialogState = rememberDialogState(width = 1200.dp, height = 500.dp)

    DialogWindow(onCloseRequest = onDismiss, state = dialogState, title = "") {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .focusable()
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.weight(1f)) {

                    if (mode == "Empfang") {
                        OutlinedTextField(
                            value = abgeberName,
                            onValueChange = {
                                abgeberName = it
                                showAbgeberWarning = false
                            },
                            label = { Text("Abgegeben von") },
                            singleLine = true,
                            isError = showAbgeberWarning,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusAbgeber)
                                .onPreviewKeyEvent {
                                    if (it.key == Key.Enter && it.type == KeyEventType.KeyUp) {
                                        if (abgeberName.isBlank()) showAbgeberWarning = true
                                        else focusSerial.requestFocus()
                                        true
                                    } else false
                                }
                        )
                        if (showAbgeberWarning) Text("Bitte Name der abgebenden Person eingeben.", color = MaterialTheme.colors.error)
                        Spacer(modifier = Modifier.height(8.dp))
                    }

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
                                        if (empfaenger.isBlank()) showEmpfaengerWarning = true
                                        else {
                                            viewModel.empfaengerName = empfaenger
                                            focusSerial.requestFocus()
                                        }
                                        true
                                    } else false
                                }
                        )
                        if (showEmpfaengerWarning) Text("Empfänger darf nicht leer sein.", color = MaterialTheme.colors.error)
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
                                            focusEmpfaenger.requestFocus()
                                        } else if (mode == "Empfang" && abgeberName.isBlank()) {
                                            showAbgeberWarning = true
                                            focusAbgeber.requestFocus()
                                        } else {
                                            viewModel.empfaengerName = empfaenger
                                            val result = viewModel.processScan(seriennummer)
                                            if (result != null && result.toString().trim().isNotEmpty()) {
                                                log.add("$result SN $seriennummer")
                                            } else {
                                                // Fehler wurde im ViewModel behandelt (z.B. Warnung anzeigen)
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
                            if ((mode == "Ausgabe" && empfaenger.isBlank()) || (mode == "Empfang" && abgeberName.isBlank())) {
                                if (mode == "Ausgabe") showEmpfaengerWarning = true else showAbgeberWarning = true
                            } else {
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

    LaunchedEffect(Unit) {
        delay(100)
        if (mode == "Ausgabe" && empfaenger.isBlank()) focusEmpfaenger.requestFocus()
        else if (mode == "Empfang" && abgeberName.isBlank()) focusAbgeber.requestFocus()
        else focusSerial.requestFocus()
    }
}

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