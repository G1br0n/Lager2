package views

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import models.MaterialLog
import viewModels.MaterialViewModel
import java.time.LocalDateTime

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ToolbarView(viewModel: MaterialViewModel, onNewMaterialClick: () -> Unit) {
    var showDialog by remember { mutableStateOf<String?>(null) }

    // Starte das Overlay-Fenster, wenn ein Fehler angezeigt werden soll.
    if (viewModel.showPopupWarning) {
        ErrorOverlayWindow(
            errorText = viewModel.popupWarningText,
            onDismiss = { viewModel.showPopupWarning = false }
        )
    }

    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = {
                println("ToolbarView: Empfang-Button geklickt")
                viewModel.selectedMode = "Empfang"
                showDialog = "Empfang"
            }) {
                Text("Empfang")
            }
            Button(onClick = {
                println("ToolbarView: Ausgabe-Button geklickt")
                viewModel.selectedMode = "Ausgabe"
                showDialog = "Ausgabe"
            }) {
                Text("Ausgabe")
            }
            Button(onClick = onNewMaterialClick) {
                Text("Neu")
            }
        }

        showDialog?.let { mode ->
            ScanDialog(
                mode = mode,
                viewModel = viewModel,
                onDismiss = {
                    println("ToolbarView: Dialog wird geschlossen")
                    showDialog = null
                }
            )
        }
    }
}


@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ScanDialog(mode: String, viewModel: MaterialViewModel, onDismiss: () -> Unit) {
    // Setze den Modus im ViewModel auf den übergebenen Wert,
    // sodass processScan den korrekten Modus verwendet.
    LaunchedEffect(mode) {
        println("ScanDialog: Setting viewModel.selectedMode = $mode")
        viewModel.selectedMode = mode
    }

    var empfaenger by remember { mutableStateOf(viewModel.empfaengerName) }
    var abgeberName by remember { mutableStateOf("") }
    var seriennummer by remember { mutableStateOf("") }
    var notiz by remember { mutableStateOf("") }

    var showEmpfaengerWarning by remember { mutableStateOf(false) }
    var showAbgeberWarning by remember { mutableStateOf(false) }

    val focusSerial = remember { FocusRequester() }
    val focusEmpfaenger = remember { FocusRequester() }
    val focusAbgeber = remember { FocusRequester() }

    val log = remember { mutableStateListOf<MaterialLog>() }

    // Erstelle einen DialogState mit fester Größe
    val dialogState = rememberDialogState(width = 1200.dp, height = 500.dp)

    // Verwende DialogWindow anstatt Dialog, um die Größe zu erzwingen
    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = ""
    ) {
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
                                .onPreviewKeyEvent { keyEvent ->
                                    println("ScanDialog: Abgegeben-TextField: KeyEvent ${keyEvent.key} ${keyEvent.type}")
                                    if (keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyUp) {
                                        if (abgeberName.isBlank()) {
                                            println("ScanDialog: Abgegeben-TextField: Name ist leer")
                                            showAbgeberWarning = true
                                        } else {
                                            println("ScanDialog: Abgegeben-TextField: Name eingegeben, Fokuswechsel zu Seriennummer")
                                            focusSerial.requestFocus()
                                        }
                                        true
                                    } else false
                                }
                        )
                        if (showAbgeberWarning) {
                            Text("Bitte Name der abgebenden Person eingeben.", color = MaterialTheme.colors.error)
                        }
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
                                .onPreviewKeyEvent { keyEvent ->
                                    println("ScanDialog: Empfänger-TextField: KeyEvent ${keyEvent.key} ${keyEvent.type}")
                                    if (keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyUp) {
                                        if (empfaenger.isBlank()) {
                                            println("ScanDialog: Empfänger-TextField: Empfänger ist leer")
                                            showEmpfaengerWarning = true
                                        } else {
                                            println("ScanDialog: Empfänger-TextField: Empfänger eingegeben, Fokuswechsel zu Seriennummer")
                                            viewModel.empfaengerName = empfaenger
                                            focusSerial.requestFocus()
                                        }
                                        true
                                    } else false
                                }
                        )
                        if (showEmpfaengerWarning) {
                            Text("Empfänger darf nicht leer sein.", color = MaterialTheme.colors.error)
                        }
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
                            .onPreviewKeyEvent { keyEvent ->
                                println("ScanDialog: Seriennummer-TextField: KeyEvent ${keyEvent.key} ${keyEvent.type} - aktueller Wert: $seriennummer")
                                if (keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyUp) {
                                    if (seriennummer.isNotBlank()) {
                                        if (mode == "Ausgabe" && empfaenger.isBlank()) {
                                            println("ScanDialog: Seriennummer: Ausgabe-Modus, aber Empfänger leer")
                                            showEmpfaengerWarning = true
                                            focusEmpfaenger.requestFocus()
                                        } else if (mode == "Empfang" && abgeberName.isBlank()) {
                                            println("ScanDialog: Seriennummer: Empfang-Modus, aber Abgeber leer")
                                            showAbgeberWarning = true
                                            focusAbgeber.requestFocus()
                                        } else {
                                            println("ScanDialog: Seriennummer: Verarbeite Scan für $seriennummer")
                                            viewModel.empfaengerName = empfaenger
                                            viewModel.processScan(seriennummer)
                                            log.add(
                                                MaterialLog(
                                                    LocalDateTime.now(),
                                                    if (mode == "Empfang") abgeberName else empfaenger,
                                                    "Scan $seriennummer verarbeitet ($mode)"
                                                )
                                            )
                                            seriennummer = ""
                                            focusSerial.requestFocus()
                                        }
                                    } else {
                                        println("ScanDialog: Seriennummer: Enter gedrückt, aber Seriennummer ist leer")
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
                        Button(onClick = {
                            println("ScanDialog: PDF-Log anzeigen Button geklickt")
                            println("PDF-Log anzeigen")
                        }) {
                            Text("Verlauf drucken")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            println("ScanDialog: $mode beenden Button geklickt")
                            if (mode == "Ausgabe" && empfaenger.isBlank()) {
                                println("ScanDialog: Beenden: Ausgabe-Modus, Empfänger ist leer")
                                showEmpfaengerWarning = true
                                focusEmpfaenger.requestFocus()
                            } else if (mode == "Empfang" && abgeberName.isBlank()) {
                                println("ScanDialog: Beenden: Empfang-Modus, Abgeber ist leer")
                                showAbgeberWarning = true
                                focusAbgeber.requestFocus()
                            } else {
                                println("ScanDialog: $mode abgeschlossen, Dialog wird geschlossen")
                                log.add(
                                    MaterialLog(
                                        LocalDateTime.now(),
                                        if (mode == "Empfang") abgeberName else empfaenger,
                                        "$mode abgeschlossen"
                                    )
                                )
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
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                        items(log.reversed()) {
                            Text("${it.timestamp} - ${it.event}", style = MaterialTheme.typography.body2)
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(100)
        if (mode == "Ausgabe" && empfaenger.isBlank()) {
            println("ScanDialog: LaunchedEffect: Ausgabe-Modus, Empfänger ist leer -> Fokus auf Empfänger")
            focusEmpfaenger.requestFocus()
        } else if (mode == "Empfang" && abgeberName.isBlank()) {
            println("ScanDialog: LaunchedEffect: Empfang-Modus, Abgeber ist leer -> Fokus auf Abgeber")
            focusAbgeber.requestFocus()
        } else {
            println("ScanDialog: LaunchedEffect: Standardfall -> Fokus auf Seriennummer")
            focusSerial.requestFocus()
        }
    }
}


@Composable
fun ErrorOverlayWindow(errorText: String, onDismiss: () -> Unit) {
    val windowState = rememberWindowState(
        width = 1200.dp,
        height = 500.dp,
        position = WindowPosition(Alignment.TopCenter)
    )
    // Starte eine einfache Fade-in-Animation
    var startAnimation by remember { mutableStateOf(false) }
    val alphaAnim by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 500)
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(5000) // 5 Sekunden anzeigen
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
            modifier = Modifier
                .fillMaxSize()
                .alpha(alphaAnim),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = errorText,
                color = MaterialTheme.colors.error,
                style = MaterialTheme.typography.h3 // Größerer, mutiger Text
            )
        }
    }
}