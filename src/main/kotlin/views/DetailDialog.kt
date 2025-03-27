package views


import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import models.Material
import models.MaterialLog
import java.time.LocalDateTime

@Composable
fun DetailDialog(
    material: Material,
    onDismiss: () -> Unit,
    onSave: (Material) -> Unit
) {
    var bezeichnung by remember { mutableStateOf(material.bezeichnung ?: "") }
    var seriennummer by remember { mutableStateOf(material.seriennummer ?: "") }
    var inLager by remember { mutableStateOf(material.inLager) }
    var notiz by remember { mutableStateOf(material.notiz ?: "") }

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
                    Text("Material Details bearbeiten", style = MaterialTheme.typography.h6)
                    Spacer(modifier = Modifier.height(8.dp))

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

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(onClick = onDismiss) { Text("Abbrechen") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            val updated = material.copy(
                                bezeichnung = bezeichnung.ifBlank { null },
                                seriennummer = seriennummer.ifBlank { null },
                                inLager = inLager,
                                notiz = notiz.ifBlank { null },
                                verlaufLog = material.verlaufLog + MaterialLog(
                                    timestamp = LocalDateTime.now(),
                                    user = "Editor",
                                    event = "Material aktualisiert"
                                )
                            )
                            onSave(updated)
                        }) { Text("Speichern") }
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
    }
}