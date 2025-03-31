package views.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import models.Material
import models.MaterialLog
import java.time.LocalDateTime

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NewMaterialDialog(
    onDismiss: () -> Unit,
    onSave: (Material) -> Unit,
    onCheckSerialExists: (String) -> Boolean,
    playErrorTone: () -> Unit
) {
    var seriennummer by remember { mutableStateOf("") }
    var bezeichnung by remember { mutableStateOf("") }
    var notiz by remember { mutableStateOf("") }

    val createdMaterials = remember { mutableStateListOf<Material>() }

    val focusRequester = remember { FocusRequester() }

    Dialog(onCloseRequest = onDismiss) {
        Surface(
            modifier = Modifier.wrapContentSize(),
            shape = MaterialTheme.shapes.medium,
            elevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .wrapContentSize()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Neues Material hinzufügen", style = MaterialTheme.typography.h6)
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = seriennummer,
                        onValueChange = { seriennummer = it },
                        label = { Text("Seriennummer") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                    TextField(
                        value = bezeichnung,
                        onValueChange = { bezeichnung = it },
                        label = { Text("Bezeichnung") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextField(
                        value = notiz,
                        onValueChange = { notiz = it },
                        label = { Text("Notiz") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(onClick = onDismiss) { Text("Schließen") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            val trimmedSerial = seriennummer.trim()
                            if (trimmedSerial.isBlank()) {
                                playErrorTone()
                                seriennummer = ""
                                focusRequester.requestFocus()
                                return@Button
                            }

                            if (onCheckSerialExists(trimmedSerial)) {
                                playErrorTone()
                                seriennummer = ""

                                // Füge Hinweis in Verlaufsliste ein (als Fake-Material mit Hinweistext)
                                val logEntry = Material(
                                    seriennummer = trimmedSerial,
                                    bezeichnung = "⚠ Bereits vorhanden",
                                    inLager = false,
                                    notiz = null,
                                    verlaufLog = listOf(
                                        MaterialLog(LocalDateTime.now(), "System", "Seriennummer bereits in der Liste")
                                    )
                                )
                                createdMaterials.add(logEntry)

                                focusRequester.requestFocus()
                            } else {
                                val newMaterial = Material(
                                    seriennummer = trimmedSerial,
                                    bezeichnung = bezeichnung,
                                    inLager = true,
                                    notiz = notiz
                                ).copy(
                                    verlaufLog = listOf(
                                        MaterialLog(LocalDateTime.now(), "System", "Material erstellt")
                                    )
                                )
                                onSave(newMaterial)
                                createdMaterials.add(newMaterial)

                                // Nur Seriennummer zurücksetzen – Bezeichnung & Notiz bleiben erhalten
                                seriennummer = ""

                                // Fokus zurück auf Seriennummer
                                focusRequester.requestFocus()
                            }

                        })
                        { Text("Hinzufügen") }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text("Zuletzt hinzugefügt", style = MaterialTheme.typography.subtitle1)
                    Spacer(modifier = Modifier.height(4.dp))

                    LazyColumn {
                        items(createdMaterials.reversed()) { material ->
                            val timestamp = material.verlaufLog.firstOrNull()?.timestamp
                                ?.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
                                ?: ""
                            Text(
                                text = "• ${material.bezeichnung ?: "?"} (${material.seriennummer ?: "-"}) – $timestamp",
                                style = MaterialTheme.typography.body2,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // Initialer Fokus setzen
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }
}