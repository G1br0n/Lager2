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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.*
import kotlinx.coroutines.delay
import tools.generateUebergabePdf
import viewModels.MaterialViewModel


// TODO --------------------------------- ToolbarView ---------------------------------
@Composable
fun ToolbarView(viewModel: MaterialViewModel, onNewMaterialClick: () -> Unit) {
    var showDialog by remember { mutableStateOf<String?>(null) }
    var showPasswordDialog by remember { mutableStateOf(false) }


    // TODO --------------------------------- Empfang/Ausgabe Button ---------------------------------
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

        // TODO --------------------------------- FILTER ---------------------------------
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
        // TODO --------------------------------- Neu ---------------------------------
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

    // TODO --------------------------------- PASSWORT ---------------------------------
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
fun ScanDialog(
    mode: String,
    viewModel: MaterialViewModel,
    onDismiss: () -> Unit
) {
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
    var showUndoDialog by remember { mutableStateOf(false) }

    // Hintergrundfarbe wählen
    val backgroundColor = when (mode) {
        "Ausgabe" -> Color(0xFFFFCDD2) // Hellrot
        "Empfang" -> Color(0xFFC8E6C9) // Hellgrün
        else -> MaterialTheme.colors.surface
    }

    DialogWindow(onCloseRequest = onDismiss, state = dialogState, title = "") {
        Surface(
            color = backgroundColor,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .focusable()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Große Überschrift
                Text(
                    text = mode.uppercase(),
                    style = MaterialTheme.typography.h4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )

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
                            if (showAbgeberWarning) Text(
                                "Bitte Name der abgebenden Person eingeben.",
                                color = MaterialTheme.colors.error
                            )
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
                            if (showEmpfaengerWarning) Text(
                                "Empfänger darf nicht leer sein.",
                                color = MaterialTheme.colors.error
                            )
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
                                                if (!result.isNullOrBlank()) {
                                                    log.add("$result SN $seriennummer")
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
                            maxLines = 3
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (mode == "Ausgabe") {
                                Button(onClick = { showUndoDialog = true }) {
                                    Text("RückScan")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            Button(onClick = {
                                val name = if (mode == "Ausgabe") empfaenger else abgeberName
                                if (name.isBlank()) {
                                    if (mode == "Ausgabe") showEmpfaengerWarning = true
                                    else showAbgeberWarning = true
                                } else {
                                    generateUebergabePdf(empfaenger = name, log = log.toList(), modus = mode)
                                }
                            }) {
                                Text("Verlauf drucken")
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(onClick = {
                                val name = if (mode == "Ausgabe") empfaenger else abgeberName
                                if (name.isBlank()) {
                                    if (mode == "Ausgabe") showEmpfaengerWarning = true
                                    else showAbgeberWarning = true
                                } else {
                                    onDismiss()
                                }
                            }) {
                                Text("$mode beenden")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
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

        LaunchedEffect(Unit) {
            delay(100)
            when {
                mode == "Ausgabe" && empfaenger.isBlank() -> focusEmpfaenger.requestFocus()
                mode == "Empfang" && abgeberName.isBlank() -> focusAbgeber.requestFocus()
                else -> focusSerial.requestFocus()
            }
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