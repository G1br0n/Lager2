package views

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import models.Material
import models.MaterialLog
import java.time.LocalDateTime

@Composable
fun DetailDialog(
    material: Material,
    onDismiss: () -> Unit,
    onSave: (Material) -> Unit,
    readOnly: Boolean = false // 🟢 Jetzt steuerbar!
) {
    var showEditMode by remember { mutableStateOf(!readOnly) }
    var showPasswordDialog by remember { mutableStateOf(false) }

    if (showPasswordDialog) {
        PasswordPrompt(
            onConfirm = {
                if (it == "test") {
                    showEditMode = true
                    showPasswordDialog = false
                } else {
                    showPasswordDialog = false
                }
            },
            onCancel = { showPasswordDialog = false }
        )
    }

    DetailContent(
        material = material,
        readOnly = !showEditMode,
        onDismiss = onDismiss,
        onEditRequest = { showPasswordDialog = true },
        onSave = onSave
    )
}

@Composable
fun DetailContent(
    material: Material,
    readOnly: Boolean,
    onDismiss: () -> Unit,
    onEditRequest: () -> Unit = {},
    onSave: (Material) -> Unit
) {
    var bezeichnung by remember { mutableStateOf(material.bezeichnung ?: "") }
    var seriennummer by remember { mutableStateOf(material.seriennummer ?: "") }
    var inLager by remember { mutableStateOf(material.inLager) }
    var notiz by remember { mutableStateOf(material.notiz ?: "") }
    var position by remember { mutableStateOf(material.position ?: "") }

    Row(
        modifier = Modifier
            .padding(16.dp)
            .wrapContentSize()
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Material Details", style = MaterialTheme.typography.h6)
            Spacer(modifier = Modifier.height(8.dp))

            if (readOnly) {
                Text("Bezeichnung: $bezeichnung")
                Text("Seriennummer: $seriennummer")
                Text("Position: $position")
                Text("Im Lager: ${if (inLager) "Ja" else "Nein"}")
                Text("Notiz: $notiz")
            } else {
                TextField(
                    value = bezeichnung,
                    onValueChange = { bezeichnung = it },
                    label = { Text("Bezeichnung") },
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = seriennummer,
                    onValueChange = { seriennummer = it },
                    label = { Text("Seriennummer") },
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = position,
                    onValueChange = { position = it },
                    label = { Text("Position") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = inLager,
                        onCheckedChange = { inLager = it }
                    )
                    Text("Im Lager")
                }

                TextField(
                    value = notiz,
                    onValueChange = { notiz = it },
                    label = { Text("Notiz") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(onClick = onDismiss) { Text("Schließen") }
                Spacer(modifier = Modifier.width(8.dp))
                if (readOnly) {
                    Button(onClick = onEditRequest) { Text("Bearbeiten") }
                } else {
                    Button(onClick = {
                        val changes = mutableListOf<String>()

                        if (material.bezeichnung != bezeichnung) changes += "Bezeichnung geändert"
                        if (material.seriennummer != seriennummer) changes += "Seriennummer geändert"
                        if (material.position != position) changes += "Position geändert"
                        if (material.inLager != inLager) changes += if (inLager) "ins Lager gelegt" else "aus Lager entfernt"
                        if (material.notiz != notiz) {
                            if (material.notiz.isNullOrBlank() && notiz.isNotBlank()) {
                                changes += "Notiz hinzugefügt"
                            } else {
                                changes += "Notiz geändert"
                            }
                        }

                        if (changes.isEmpty()) {
                            onDismiss()
                            return@Button
                        }

                        val newLog = MaterialLog(
                            timestamp = LocalDateTime.now(),
                            user = "Editor",
                            event = "Änderungen: ${changes.joinToString(", ")}"
                        )

                        val updated = material.copy(
                            bezeichnung = bezeichnung.ifBlank { null },
                            seriennummer = seriennummer.ifBlank { null },
                            position = position.ifBlank { null },
                            inLager = inLager,
                            notiz = notiz.ifBlank { null },
                            verlaufLog = material.verlaufLog + newLog
                        )

                        onSave(updated)
                    }) {
                        Text("Speichern")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text("Verlauf", style = MaterialTheme.typography.subtitle1)
            Spacer(modifier = Modifier.height(4.dp))

            val reversedLogs = material.verlaufLog.reversed()

            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                items(reversedLogs) { log ->
                    val timeFormatted = log.timestamp.format(
                        java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                    )
                    Text(
                        text = "$timeFormatted – ${log.event}",
                        style = MaterialTheme.typography.body2,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PasswordPrompt(onConfirm: (String) -> Unit, onCancel: () -> Unit) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Passwort erforderlich") },
        text = {
            Column {
                Text("Bitte Passwort eingeben, um Bearbeitungsmodus zu aktivieren.")
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Passwort") }
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(password) }) {
                Text("Bestätigen")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onCancel) {
                Text("Abbrechen")
            }
        }
    )
}
