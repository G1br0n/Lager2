package views

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import models.Material
import models.MaterialLog
import java.time.LocalDateTime

/**
 * DetailDialog zeigt die Details eines Materials und dessen Log-Verlauf.
 *
 * @param material Das Material-Objekt (ohne Logs).
 *                 Die Logs werden separat über den Parameter 'logs' übergeben.
 * @param logs     Die aktuell geladenen Log-Einträge für dieses Material.
 * @param onDismiss   Callback, wenn der Dialog geschlossen wird.
 * @param onSave      Callback, wenn Änderungen abgespeichert werden.
 * @param onDelete    Callback, wenn das Material gelöscht werden soll.
 * @param readOnly    Wenn true, ist nur Anzeige (kein Edit-Modus).
 */
@Composable
fun DetailDialog(
    material: Material,
    logs: List<MaterialLog>,
    onDismiss: () -> Unit,
    onSave: (Material) -> Unit,
    onDelete: (Material) -> Unit,
    readOnly: Boolean = false
) {
    var showEditMode by remember { mutableStateOf(!readOnly) }
    var showPasswordDialog by remember { mutableStateOf(false) }

    // Passwortabfrage, um Edit-Modus zu aktivieren
    if (showPasswordDialog) {
        PasswordPrompt(
            onConfirm = {
                if (it == "löschen") {
                    showEditMode = true
                }
                showPasswordDialog = false
            },
            onCancel = { showPasswordDialog = false }
        )
    }

    DetailContent(
        material = material,
        logs = logs,
        readOnly = !showEditMode,
        onDismiss = onDismiss,
        onEditRequest = { showPasswordDialog = true },
        onSave = onSave,
        onDelete = onDelete
    )
}

@Composable
private fun DetailContent(
    material: Material,
    logs: List<MaterialLog>,
    readOnly: Boolean,
    onDismiss: () -> Unit,
    onEditRequest: () -> Unit = {},
    onSave: (Material) -> Unit,
    onDelete: (Material) -> Unit
) {
    var bezeichnung by remember { mutableStateOf(material.bezeichnung ?: "") }
    var seriennummer by remember { mutableStateOf(material.seriennummer ?: "") }
    var inLager by remember { mutableStateOf(material.inLager) }
    var notiz by remember { mutableStateOf(material.notiz ?: "") }
    var position by remember { mutableStateOf(material.position ?: "") }

    var showDeletePasswordDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Passwortabfrage vor Löschen
    if (showDeletePasswordDialog) {
        PasswordPrompt(
            onConfirm = {
                if (it == "test") {
                    showDeletePasswordDialog = false
                    showDeleteConfirmDialog = true
                } else {
                    showDeletePasswordDialog = false
                }
            },
            onCancel = { showDeletePasswordDialog = false }
        )
    }

    // Bestätigungsdialog nach erfolgreicher Passwortabfrage
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Löschen bestätigen") },
            text = { Text("Möchtest du dieses Material wirklich löschen?") },
            confirmButton = {
                Button(onClick = {
                    onDelete(material)
                    showDeleteConfirmDialog = false
                }) {
                    Text("Löschen")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    Row(
        modifier = Modifier
            .padding(16.dp)
            .wrapContentSize()
    ) {
        // Linke Spalte: Material-Felder
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
                        if ((material.bezeichnung ?: "").trim() != bezeichnung.trim())
                            changes += "Bezeichnung geändert"
                        if ((material.seriennummer ?: "").trim() != seriennummer.trim())
                            changes += "Seriennummer geändert"
                        if ((material.position ?: "").trim() != position.trim())
                            changes += "Position geändert"
                        if (material.inLager != inLager)
                            changes += if (inLager) "ins Lager gelegt" else "aus Lager entfernt"
                        if ((material.notiz ?: "").trim() != notiz.trim()) {
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
                        // Füge den neuen Log ans Ende der bisherigen Logs
                        val updatedLogs = material.verlaufLog + newLog
                        val updated = material.copy(
                            bezeichnung = bezeichnung.trim().ifBlank { null },
                            seriennummer = seriennummer.trim().ifBlank { null },
                            position = position.trim().ifBlank { null },
                            inLager = inLager,
                            notiz = notiz.trim().ifBlank { null },
                            verlaufLog = updatedLogs
                        )
                        onSave(updated)
                    }) {
                        Text("Speichern")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Geschützter Lösch-Button
                    Button(
                        onClick = { showDeletePasswordDialog = true },
                        colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error)
                    ) {
                        Text("Löschen", color = MaterialTheme.colors.onError)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Rechte Spalte: Log-Verlauf
        Column(modifier = Modifier.weight(1f)) {
            Text("Verlauf", style = MaterialTheme.typography.subtitle1)
            Spacer(modifier = Modifier.height(4.dp))

            // Anzeigen der aktuellen Logs (übergeben in 'logs')
            val reversedLogs = logs.reversed()

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
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Passwort") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.onPreviewKeyEvent {
                            if (it.key == Key.Enter && it.type == KeyEventType.KeyUp) {
                                onConfirm(password)
                                true
                            } else false
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onConfirm(password) }) {
                        Text("OK")
                    }
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onCancel) {
                Text("Abbrechen")
            }
        },
        confirmButton = {} // leer, da OK-Button im Text‐Bereich
    )
}
