package views

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.*
import kotlinx.coroutines.delay
import tools.generateUebergabePdf
import viewModels.MaterialViewModel


// TODO --------------------------------- ToolbarView ---------------------------------

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ToolbarView(
    viewModel: MaterialViewModel,
    onNewMaterialClick: () -> Unit
) {
    var showDialog by remember { mutableStateOf<String?>(null) }
    var showPasswordDialog by remember { mutableStateOf(false) }

    // Scanner-Puffer und FocusRequester
    var scannerBuffer by remember { mutableStateOf("") }
    val toolbarFocusRequester = remember { FocusRequester() }

    // Fokus auf Toolbar, wenn keine Dialoge offen
    LaunchedEffect(showDialog, showPasswordDialog) {
        if (showDialog == null && !showPasswordDialog) {
            toolbarFocusRequester.requestFocus()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .focusRequester(toolbarFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) {
                    when {
                        // Enter schließt Puffer ab und öffnet ggf. Dialog
                        event.key == Key.Enter -> {
                            when (scannerBuffer.trim().uppercase()) {
                                "EMPFANG" -> {
                                    viewModel.selectedMode = "Empfang"
                                    showDialog = "Empfang"
                                    viewModel.playEmpfangModusTone()
                                }
                                "AUSGABE" -> {
                                    viewModel.selectedMode = "Ausgabe"
                                    showDialog = "Ausgabe"
                                    viewModel.playAusgabeModusTone()
                                }
                                else -> {
                                    // Fallback: könnte als Seriennummer interpretiert werden
                                }
                            }
                            scannerBuffer = ""
                            true
                        }
                        else -> {
                            // Nur druckbare Zeichen anpuffern
                            event.utf16CodePoint.takeIf { it in 32..126 }
                                ?.toChar()
                                ?.also { scannerBuffer += it }
                                ?.let { return@onPreviewKeyEvent true }
                            false
                        }
                    }
                } else false
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Empfang / Ausgabe Buttons
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    viewModel.selectedMode = "Ausgabe"
                    showDialog = "Ausgabe"
                    viewModel.playAusgabeModusTone()
                },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFFFFCDD2),  // rötlich
                    contentColor = Color.Black
                ),
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Text("Ausgabe", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    viewModel.selectedMode = "Empfang"
                    showDialog = "Empfang"
                    viewModel.playEmpfangModusTone()
                },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFFC8E6C9),  // grünlich
                    contentColor = Color.Black
                ),
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Text("Empfang", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }


        }

        // Filter-Bereich
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            // Filter-Textfeld
            val filterFocusRequester = remember { FocusRequester() }
            OutlinedTextField(
                value = viewModel.filterText,
                onValueChange = { viewModel.filterText = it },
                label = { Text("Filter", fontSize = 16.sp) },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(filterFocusRequester)
                    .focusable(),
                keyboardOptions = KeyboardOptions.Default,
                keyboardActions = KeyboardActions(onDone = { /* optional */ })
            )
            Spacer(Modifier.width(8.dp))
            Checkbox(
                checked = viewModel.filterActive,
                onCheckedChange = { viewModel.filterActive = it }
            )
            Text(
                text = if (viewModel.filterActive) "Filter AN" else "Filter AUS",
                fontSize = 16.sp,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        // "Neu"-Button mit Passwort-Prompt
        Button(
            onClick = { showPasswordDialog = true },
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Text("Neu", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }

    // Dialog-Fenster für Empfang/Ausgabe
    showDialog?.let { mode ->
        ScanDialog(
            mode = mode,
            viewModel = viewModel,
            onDismiss = { showDialog = null }
        )
    }

    // Passwort-Abfrage für "Neu"
    if (showPasswordDialog) {
        PasswordPrompt(
            onConfirm = { pwd ->
                if (pwd == "test") onNewMaterialClick()
                showPasswordDialog = false
            },
            onCancel = { showPasswordDialog = false }
        )
    }
}






@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ScanDialog(
    mode: String,
    viewModel: MaterialViewModel,
    onDismiss: () -> Unit
) {
    LaunchedEffect(mode) { viewModel.selectedMode = mode }

    // State for Empfänger/Abgeber-Feld mit TextFieldValue
    var empfaengerState by remember {
        mutableStateOf(
            TextFieldValue(
                text = viewModel.empfaengerName,
                selection = TextRange(0, viewModel.empfaengerName.length)
            )
        )
    }
    var seriennummer by remember { mutableStateOf("") }
    var notiz by remember { mutableStateOf("") }
    var showEmpfaengerWarning by remember { mutableStateOf(false) }
    var showAbgeberWarning by remember { mutableStateOf(false) }
    var showUndoDialog by remember { mutableStateOf(false) }

    val focusSerial = remember { FocusRequester() }
    val focusEmpfaenger = remember { FocusRequester() }
    val focusAbgeber = remember { FocusRequester() }

    val log = remember { mutableStateListOf<String>() }
    val dialogState = rememberDialogState(width = 1200.dp, height = 500.dp)

    // Initial focus & selection
    LaunchedEffect(Unit) {
        delay(100)
        when (mode) {
            "Ausgabe" -> focusEmpfaenger.requestFocus()
            "Empfang" -> focusAbgeber.requestFocus()
            else -> focusSerial.requestFocus()
        }
    }

    DialogWindow(onCloseRequest = onDismiss, state = dialogState, title = "") {
        Surface(
            color = if (mode == "Ausgabe") Color(0xFFFFCDD2) else Color(0xFFC8E6C9),
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .focusable()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(text = mode.uppercase(), style = MaterialTheme.typography.h4, modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp))

                Row(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.weight(1f)) {
                        // Empfänger / Abgeber Field
                        OutlinedTextField(
                            value = empfaengerState,
                            onValueChange = { newValue ->
                                empfaengerState = newValue
                                // Aktualisiere ViewModel sofort
                                viewModel.empfaengerName = newValue.text
                                showEmpfaengerWarning = false
                                showAbgeberWarning = false
                            },
                            label = { Text(if (mode == "Ausgabe") "Empfänger" else "Abgegeben von") },
                            singleLine = true,
                            isError = if (mode == "Ausgabe") showEmpfaengerWarning else showAbgeberWarning,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(if (mode == "Ausgabe") focusEmpfaenger else focusAbgeber)
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        empfaengerState = empfaengerState.copy(
                                            selection = TextRange(0, empfaengerState.text.length)
                                        )
                                    }
                                }
                                .onPreviewKeyEvent { event ->
                                    if (event.key == Key.Enter && event.type == KeyEventType.KeyUp) {
                                        if (empfaengerState.text.isNotBlank()) {
                                            viewModel.playNameErkanntTone()   // <<< hier
                                            focusSerial.requestFocus()
                                        } else {
                                            if (mode == "Ausgabe") showEmpfaengerWarning = true else showAbgeberWarning = true
                                        }
                                        true
                                    } else false
                                }
                        )
                        if ((mode == "Ausgabe" && showEmpfaengerWarning) || (mode == "Empfang" && showAbgeberWarning)) {
                            Text(
                                text = if (mode == "Ausgabe") "Empfänger darf nicht leer sein." else "Bitte Name der abgebenden Person eingeben.",
                                color = MaterialTheme.colors.error
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Seriennummer Field
                        OutlinedTextField(
                            value = seriennummer,
                            onValueChange = { seriennummer = it },
                            label = { Text("Seriennummer") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusSerial)
                                .onPreviewKeyEvent { event ->
                                    if (event.key == Key.Enter && event.type == KeyEventType.KeyUp) {
                                        val snTrimmed = seriennummer.trim()
                                        val name = empfaengerState.text
                                        if (snTrimmed.equals("ende", ignoreCase = true)) {
                                            // "Ende" erkannt: Dialog schließen

                                            generateUebergabePdf(
                                                empfaenger = name,
                                                log = log.toList(),
                                                modus = mode
                                            )
                                            viewModel.playScanEndeTone()
                                            onDismiss()

                                        } else if (snTrimmed.isNotBlank()) {
                                            // ganz normale Verarbeitung
                                            val result = viewModel.processScan(snTrimmed)
                                            if (!result.isNullOrBlank()) {
                                                log.add("$result SN $snTrimmed")
                                            }
                                            seriennummer = ""
                                            focusSerial.requestFocus()
                                        }
                                        true
                                    } else false
                                }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Notiz Field
                        OutlinedTextField(
                            value = notiz,
                            onValueChange = { notiz = it },
                            label = { Text("Notiz") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (mode == "Ausgabe") {
                                Button(onClick = { showUndoDialog = true }) { Text("RückScan") }
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Button(onClick = {
                                val name = empfaengerState.text
                                if (name.isBlank()) {
                                    if (mode == "Ausgabe") showEmpfaengerWarning = true else showAbgeberWarning = true
                                } else {
                                    generateUebergabePdf(empfaenger = name, log = log.toList(), modus = mode)
                                }
                            }) { Text("Verlauf drucken") }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(onClick = {
                                val name = empfaengerState.text
                                if (name.isBlank()) {
                                    if (mode == "Ausgabe") showEmpfaengerWarning = true else showAbgeberWarning = true
                                } else {
                                    onDismiss()
                                }
                            }) { Text("$mode beenden") }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        Text("Verlauf", style = MaterialTheme.typography.subtitle1)
                        Spacer(modifier = Modifier.height(8.dp))
                        val grouped = log
                            .filter { it.contains("SN") }
                            .groupBy { it.substringBefore(" SN") }
                            .toSortedMap()

                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            grouped.forEach { (material, entries) ->
                                item { Text(material, style = MaterialTheme.typography.subtitle1) }
                                entries.asReversed().forEachIndexed { index, entry ->
                                    item { Text("${entries.size - index}. $entry", style = MaterialTheme.typography.body2) }
                                }
                                item { Spacer(modifier = Modifier.height(8.dp)) }
                            }
                        }
                    }
                }
            }
        }

        if (viewModel.showPopupWarning) {
            ErrorOverlayWindow(
                errorText = viewModel.popupWarningText,
                onDismiss = {
                    viewModel.showPopupWarning = false
                    viewModel.popupWarningText = ""
                }
            )
        }

        if (showUndoDialog) {
            UndoScanDialog(
                onDismiss = { showUndoDialog = false },
                onUndoSuccess = { sn ->
                    val index = log.indexOfLast { it.contains("SN $sn") }
                    if (index != -1) {
                        log.removeAt(index)
                        val success = viewModel.undoMaterialBySerial(sn)
                        if (!success) {
                            viewModel.popupWarningText = "Material mit Seriennummer '$sn' nicht gefunden."
                            viewModel.playNichtErkanntTone()
                            viewModel.showPopupWarning = true
                        }
                    } else {
                        viewModel.popupWarningText = "Eintrag mit Seriennummer '$sn' nicht im Verlauf gefunden."
                        viewModel.showPopupWarning = true
                    }
                    showUndoDialog = false
                }
            )
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

@Composable
fun UndoScanDialog(
    onDismiss: () -> Unit,
    onUndoSuccess: (String) -> Unit
) {
    var seriennummer by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val dialogState = rememberDialogState(width = 500.dp, height = 250.dp)

    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = "Rücknahme"
    ) {
        Surface(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize()
            ) {
                Text("Seriennummer eingeben", style = MaterialTheme.typography.h6)

                OutlinedTextField(
                    value = seriennummer,
                    onValueChange = {
                        seriennummer = it
                        error = null
                    },
                    label = { Text("Seriennummer") },
                    isError = error != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colors.error)
                }

                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onDismiss) {
                        Text("Abbrechen")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        if (seriennummer.isBlank()) {
                            error = "Bitte Seriennummer eingeben"
                        } else {
                            onUndoSuccess(seriennummer)
                        }
                    }) {
                        Text("Rücknahme durchführen")
                    }
                }
            }
        }
    }
}