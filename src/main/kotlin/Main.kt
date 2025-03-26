// main.kt

// ----------------------------
// Importe für Compose Desktop, JLayer, SQLite und weitere benötigte Klassen
// ----------------------------
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import javazoom.jl.player.advanced.AdvancedPlayer
import java.sql.Connection
import java.sql.DriverManager
import java.time.LocalDateTime
import java.util.UUID

// ----------------------------
// Eigene Color-Palette in Grautönen
// ----------------------------
private val GrayColorPalette = lightColors(
    primary = Color(0xFF616161),
    primaryVariant = Color(0xFF424242),
    secondary = Color(0xFF9E9E9E),
    background = Color(0xFFE0E0E0),
    surface = Color(0xFFBDBDBD),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color.Black,
    onSurface = Color.Black,
)

// ----------------------------
// Datenmodell
// ----------------------------
data class Material(
    val id: UUID = UUID.randomUUID(),
    val seriennummer: String? = null,
    val bezeichnung: String? = null,
    val inLager: Boolean,
    val notiz: String? = null,
    val verlaufLog: List<MaterialLog> = emptyList()
)

data class MaterialLog(
    val timestamp: LocalDateTime,
    val user: String,
    val event: String
)

// ----------------------------
// Repository und Persistenz
// ----------------------------
interface MaterialRepository {
    fun getAllMaterials(): List<Material>
    fun addMaterial(material: Material)
    fun updateMaterial(material: Material)
}

/**
 * SQLite-Implementierung des MaterialRepository.
 * Es werden zwei Tabellen verwendet:
 * - materials: speichert die Basisinformationen.
 * - material_log: speichert die Verlaufslogs, verknüpft über material_id.
 */
class SQLiteMaterialRepository : MaterialRepository {
    private val connection: Connection

    init {
        // Verbindung zur SQLite-Datenbank (Datei "materials.db")
        connection = DriverManager.getConnection("jdbc:sqlite:materials.db")
        // Erstelle Tabellen, falls sie noch nicht existieren:
        val createMaterialsTable = """
            CREATE TABLE IF NOT EXISTS materials (
                id TEXT PRIMARY KEY,
                seriennummer TEXT,
                bezeichnung TEXT,
                inLager INTEGER,
                notiz TEXT
            );
        """.trimIndent()
        val createMaterialLogTable = """
            CREATE TABLE IF NOT EXISTS material_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                material_id TEXT,
                timestamp TEXT,
                user TEXT,
                event TEXT
            );
        """.trimIndent()
        connection.createStatement().use { stmt ->
            stmt.execute(createMaterialsTable)
            stmt.execute(createMaterialLogTable)
        }
    }

    override fun getAllMaterials(): List<Material> {
        val materials = mutableListOf<Material>()
        val query = "SELECT * FROM materials"
        connection.prepareStatement(query).use { stmt ->
            val rs = stmt.executeQuery()
            while (rs.next()) {
                val id = UUID.fromString(rs.getString("id"))
                val seriennummer = rs.getString("seriennummer")
                val bezeichnung = rs.getString("bezeichnung")
                val inLager = rs.getInt("inLager") != 0
                val notiz = rs.getString("notiz")
                // Lese zugehörige Logs
                val logs = mutableListOf<MaterialLog>()
                val logQuery = "SELECT * FROM material_log WHERE material_id = ? ORDER BY timestamp ASC"
                connection.prepareStatement(logQuery).use { logStmt ->
                    logStmt.setString(1, id.toString())
                    val logRs = logStmt.executeQuery()
                    while (logRs.next()) {
                        val timestamp = LocalDateTime.parse(logRs.getString("timestamp"))
                        val user = logRs.getString("user")
                        val event = logRs.getString("event")
                        logs.add(MaterialLog(timestamp, user, event))
                    }
                }
                materials.add(Material(id, seriennummer, bezeichnung, inLager, notiz, logs))
            }
        }
        return materials
    }

    override fun addMaterial(material: Material) {
        val insertMaterial = "INSERT INTO materials (id, seriennummer, bezeichnung, inLager, notiz) VALUES (?, ?, ?, ?, ?)"
        connection.prepareStatement(insertMaterial).use { stmt ->
            stmt.setString(1, material.id.toString())
            stmt.setString(2, material.seriennummer)
            stmt.setString(3, material.bezeichnung)
            stmt.setInt(4, if (material.inLager) 1 else 0)
            stmt.setString(5, material.notiz)
            stmt.executeUpdate()
        }
        // Füge die Log-Einträge hinzu
        val insertLog = "INSERT INTO material_log (material_id, timestamp, user, event) VALUES (?, ?, ?, ?)"
        connection.prepareStatement(insertLog).use { stmt ->
            for (log in material.verlaufLog) {
                stmt.setString(1, material.id.toString())
                stmt.setString(2, log.timestamp.toString())
                stmt.setString(3, log.user)
                stmt.setString(4, log.event)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    override fun updateMaterial(material: Material) {
        val updateMaterial = "UPDATE materials SET seriennummer = ?, bezeichnung = ?, inLager = ?, notiz = ? WHERE id = ?"
        connection.prepareStatement(updateMaterial).use { stmt ->
            stmt.setString(1, material.seriennummer)
            stmt.setString(2, material.bezeichnung)
            stmt.setInt(3, if (material.inLager) 1 else 0)
            stmt.setString(4, material.notiz)
            stmt.setString(5, material.id.toString())
            stmt.executeUpdate()
        }
        // Alte Logs löschen und alle neuen Logs einfügen
        val deleteLogs = "DELETE FROM material_log WHERE material_id = ?"
        connection.prepareStatement(deleteLogs).use { stmt ->
            stmt.setString(1, material.id.toString())
            stmt.executeUpdate()
        }
        val insertLog = "INSERT INTO material_log (material_id, timestamp, user, event) VALUES (?, ?, ?, ?)"
        connection.prepareStatement(insertLog).use { stmt ->
            for (log in material.verlaufLog) {
                stmt.setString(1, material.id.toString())
                stmt.setString(2, log.timestamp.toString())
                stmt.setString(3, log.user)
                stmt.setString(4, log.event)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }
}

// ----------------------------
// ViewModel (MVVM)
// ----------------------------
class MaterialViewModel(private val repository: MaterialRepository) {
    // Beachte: Wir laden hier initial alle Materialien aus der DB.
    var materials = mutableStateListOf<Material>().apply { addAll(repository.getAllMaterials()) }
        private set

    // "Empfang" oder "Ausgabe"
    var selectedMode by mutableStateOf("Empfang")
    var scannerInput by mutableStateOf("")
    var empfaengerName by mutableStateOf("")

    fun processScan(scannedCode: String) {
        val code = scannedCode.trim()
        val found = materials.find { it.seriennummer?.trim()?.startsWith(code) == true }
        if (found != null) {
            val updated = when (selectedMode) {
                "Empfang" -> found.copy(
                    inLager = true,
                    verlaufLog = found.verlaufLog + MaterialLog(
                        timestamp = LocalDateTime.now(),
                        user = "System",
                        event = "Material per Scan empfangen"
                    )
                )
                "Ausgabe" -> {
                    if (empfaengerName.isNotBlank()) {
                        found.copy(
                            inLager = false,
                            verlaufLog = found.verlaufLog + MaterialLog(
                                timestamp = LocalDateTime.now(),
                                user = "System",
                                event = "Material per Scan ausgegeben an $empfaengerName"
                            )
                        )
                    } else found
                }
                else -> found
            }
            updateMaterial(updated)
            playSuccessTone()
        } else {
            println("Material mit Seriennummer $scannedCode nicht gefunden!")
            playErrorTone()
        }
    }

    fun addNewMaterial(material: Material) {
        materials.add(material)
        repository.addMaterial(material)
    }

    fun updateMaterial(updated: Material) {
        val index = materials.indexOfFirst { it.id == updated.id }
        if (index >= 0) {
            materials[index] = updated
            repository.updateMaterial(updated)
        }
    }

    // Abspielen von MP3s mit JLayer
    private fun playMp3FromResource(resourcePath: String) {
        try {
            val inputStream = javaClass.getResourceAsStream(resourcePath)
            if (inputStream != null) {
                AdvancedPlayer(inputStream).play()
            } else {
                println("Resource $resourcePath nicht gefunden!")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playSuccessTone() { Thread { playMp3FromResource("/mp3/ok.mp3") }.start() }
    fun playErrorTone() { Thread { playMp3FromResource("/mp3/error.mp3") }.start() }
}

// ----------------------------
// UI – separate Views
// ----------------------------
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

@Composable
fun MaterialListView(
    viewModel: MaterialViewModel,
    onMaterialSelected: (Material) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(viewModel.materials) { material ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (material.inLager) Color(0xFFB9F6CA) else Color(0xFFFFCDD2)
                    )
                    .clickable { onMaterialSelected(material) }
                    .padding(vertical = 4.dp)
            ) {
                Text(material.bezeichnung ?: "–", modifier = Modifier.weight(1f))
                Text(material.seriennummer?.trimEnd() ?: "", modifier = Modifier.weight(1f))
                Text(if (material.inLager) "Lager" else "Ausgegeben", modifier = Modifier.weight(1f))
                Text(material.notiz ?: "–", modifier = Modifier.weight(1f))
            }
            Divider()
        }
    }
}

/**
 * MonitorView zeigt die Materialien gruppiert nach Bezeichnung in Spalten.
 * Pro Spalte werden maximal 10 Einträge angezeigt, wobei Materialien im Lager
 * zuerst angezeigt werden.
 */
@Composable
fun MonitorView(
    viewModel: MaterialViewModel,
    onMaterialSelected: (Material) -> Unit
) {
    // Gruppiere Materialien nach Bezeichnung und sortiere alphabetisch
    val groupedMaterials = viewModel.materials.groupBy { it.bezeichnung ?: "Unbekannt" }
        .toSortedMap()
    val columns = groupedMaterials.toList()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(rememberScrollState())
            .padding(8.dp)
    ) {
        columns.forEachIndexed { index, (bezeichnung, materials) ->
            Column(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Spaltenüberschrift in größerer Schrift
                Text(text = bezeichnung, style = MaterialTheme.typography.h4)

                // Zusammenfassungszeile: Links grün (im Lager), rechts rot (nicht im Lager)
                val countInLager = materials.count { it.inLager }
                val countNotInLager = materials.count { !it.inLager }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "$countInLager",
                        color = Color.Green,
                        style = MaterialTheme.typography.h4
                    )
                    Text(
                        text = " / ",
                        style = MaterialTheme.typography.h4
                    )
                    Text(
                        text = "$countNotInLager",
                        color = Color.Red,
                        style = MaterialTheme.typography.h4
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Untere Übersicht: Aufteilung in zwei Gruppen – grüne (im Lager) und rote (nicht im Lager)
                val greenMaterials = materials.filter { it.inLager }
                val redMaterials = materials.filter { !it.inLager }

                Column {
                    // Anzeige der grünen Materialien
                    greenMaterials.forEachIndexed { idx, material ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(Color(0xFFB9F6CA))
                                .clickable { onMaterialSelected(material) },
                            contentAlignment = Alignment.Center
                        ) {
                            val cleanedSerial = material.seriennummer?.trimEnd() ?: ""
                            val displaySerial = if (cleanedSerial.length > 10) cleanedSerial.takeLast(10) else cleanedSerial
                            Text(
                                text = displaySerial,
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.h5
                            )
                        }
                        if (idx < greenMaterials.size - 1) {
                            Divider(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color.Gray,
                                thickness = 1.dp
                            )
                        }
                    }

                    // Trennlinie zwischen den Gruppen nur anzeigen, wenn beide Gruppen vorhanden sind
                    if (greenMaterials.isNotEmpty() && redMaterials.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(Color.Black)
                        )
                    }

                    Divider(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.Gray,
                        thickness = 1.dp
                    )

                    // Anzeige der roten Materialien
                    redMaterials.forEachIndexed { idx, material ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(Color(0xFFFFCDD2))
                                .clickable { onMaterialSelected(material) },
                            contentAlignment = Alignment.Center
                        ) {
                            val cleanedSerial = material.seriennummer?.trimEnd() ?: ""
                            val displaySerial = if (cleanedSerial.length > 10) cleanedSerial.takeLast(10) else cleanedSerial
                            Text(
                                text = displaySerial,
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.h5
                            )
                        }



                    }
                }
            }
            // Vertikaler Divider zwischen den Spalten
            if (index < columns.size - 1) {
                Divider(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(2.dp),
                    color = Color.Gray
                )
            }
        }
    }
}








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



@Composable
fun MissingNameDialog(onDismiss: () -> Unit) {
    Dialog(onCloseRequest = onDismiss) {
        Surface(
            modifier = Modifier.wrapContentSize(),
            shape = MaterialTheme.shapes.medium,
            elevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Empfängername fehlt!", style = MaterialTheme.typography.h6)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Bitte gib einen Empfängernamen ein, um im Ausgabe-Modus fortzufahren.")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("OK")
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
@Preview
fun App(viewModel: MaterialViewModel) {
    var showNewMaterialDialog by remember { mutableStateOf(false) }
    var showDetailDialog by remember { mutableStateOf(false) }
    var selectedMaterial by remember { mutableStateOf<Material?>(null) }
    var showMissingNameDialog by remember { mutableStateOf(false) }

    MaterialTheme(colors = GrayColorPalette) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            ToolbarView(
                viewModel = viewModel,
                onMissingName = { showMissingNameDialog = true },
                onNewMaterialClick = { showNewMaterialDialog = true }
            )
            Spacer(modifier = Modifier.height(16.dp))
            MaterialListView(viewModel = viewModel) { material ->
                selectedMaterial = material
                showDetailDialog = true
            }
        }

        if (showNewMaterialDialog) {
            NewMaterialDialog(
                onDismiss = { showNewMaterialDialog = false },
                onSave = { newMat -> viewModel.addNewMaterial(newMat) },
                onCheckSerialExists = { serial ->
                    viewModel.materials.any { it.seriennummer?.trim() == serial.trim() }
                },
                playErrorTone = { viewModel.playErrorTone() }
            )
        }

        if (showDetailDialog && selectedMaterial != null) {
            DetailDialog(
                material = selectedMaterial!!,
                onDismiss = { showDetailDialog = false },
                onSave = { updated ->
                    viewModel.updateMaterial(updated)
                    showDetailDialog = false
                }
            )
        }

        if (showMissingNameDialog) {
            MissingNameDialog(onDismiss = { showMissingNameDialog = false })
        }
    }
}


/**
 * In der main-Funktion wird nun ein einziger Repository- und ViewModel-Instanz erzeugt,
 * die in beiden Fenstern (Lagerverwaltung und Monitor) geteilt werden.
 */
fun main() = application {
    val repository = SQLiteMaterialRepository()
    val viewModel = MaterialViewModel(repository)

    Window(onCloseRequest = ::exitApplication, title = "Lagerverwaltung (MVVM & Grautöne)") {
        App(viewModel)
    }

    Window(onCloseRequest = {}, title = "Material Monitor") {
        var selectedMaterialForMonitor by remember { mutableStateOf<Material?>(null) }

        MonitorView(viewModel) { selected ->
            selectedMaterialForMonitor = selected
        }

        if (selectedMaterialForMonitor != null) {
            DetailDialog(
                material = selectedMaterialForMonitor!!,
                onDismiss = { selectedMaterialForMonitor = null },
                onSave = { updated ->
                    viewModel.updateMaterial(updated)
                    selectedMaterialForMonitor = null
                }
            )
        }
    }
}
