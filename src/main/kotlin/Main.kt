    // main.kt

    // ----------------------------
    // Importe für Compose Desktop, JLayer und weitere benötigte Klassen
    // ----------------------------
    import androidx.compose.desktop.ui.tooling.preview.Preview
    import androidx.compose.foundation.clickable
    import androidx.compose.foundation.layout.*
    import androidx.compose.foundation.lazy.LazyColumn
    import androidx.compose.foundation.lazy.items
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
    /**
     * Datenklasse für ein Material.
     */
    data class Material(
        val id: UUID = UUID.randomUUID(),
        val seriennummer: String? = null,
        val bezeichnung: String? = null,
        val inLager: Boolean,
        val notiz: String? = null,
        val verlaufLog: List<MaterialLog> = emptyList()
    )

    /**
     * Datenklasse für einen Logeintrag.
     */
    data class MaterialLog(
        val timestamp: LocalDateTime,
        val user: String,
        val event: String
    )

    // ----------------------------
    // Repository und Persistenz (In-Memory-Implementierung)
    // ----------------------------
    interface MaterialRepository {
        fun getAllMaterials(): List<Material>
        fun addMaterial(material: Material)
        fun updateMaterial(material: Material)
    }

    class InMemoryMaterialRepository : MaterialRepository {
        private val materials = mutableListOf<Material>()
        override fun getAllMaterials(): List<Material> = materials
        override fun addMaterial(material: Material) {
            materials.add(material)
        }
        override fun updateMaterial(material: Material) {
            val index = materials.indexOfFirst { it.id == material.id }
            if (index >= 0) {
                materials[index] = material
            }
        }
    }

    // ----------------------------
    // ViewModel (MVVM)
    // ----------------------------
    class MaterialViewModel(private val repository: MaterialRepository) {
        // Observable Liste der Materialien
        var materials = mutableStateListOf<Material>()
            private set

        // Aktuell gewählter Modus ("Empfang" oder "Ausgabe")
        var selectedMode by mutableStateOf("Empfang")

        // Scanner-Eingabe (Seriennummer)
        var scannerInput by mutableStateOf("")

        // Empfängername (nur bei Ausgabe)
        var empfaengerName by mutableStateOf("")

        init {
            materials.addAll(repository.getAllMaterials())
        }

        /**
         * Verarbeitet den Scan einer Seriennummer.
         * Wird ein Material gefunden, wird es gemäß Modus aktualisiert (inklusive Logeintrag).
         * Bei "Empfang" wird inLager auf true gesetzt.
         * Bei "Ausgabe" (sofern ein Empfängername vorliegt) wird inLager auf false gesetzt.
         * Bei Erfolg oder Fehler wird ein akustisches Feedback ausgegeben.
         */
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
                materials.forEach {
                    println("Material mit Seriennummer ${it.seriennummer}")
                }
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

        // Hilfsfunktion: Spielt eine MP3 aus den Ressourcen ab
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


        // Spielt den Erfolgston ab, indem ok.mp3 abgespielt wird.
        private fun playSuccessTone() {
            Thread { playMp3FromResource("mp3/ok.mp3") }.start()
        }

        // Spielt den Fehler-Ton ab, indem error.mp3 abgespielt wird.
        private fun playErrorTone() {
            Thread { playMp3FromResource("mp3/error.mp3") }.start()
        }
    }

    // ----------------------------
    // UI / View (Compose Desktop)
    // ----------------------------
    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    @Preview
    fun App(viewModel: MaterialViewModel = MaterialViewModel(InMemoryMaterialRepository())) {
        // FocusRequester für Scanner-Eingabe und Empfängerfeld
        val scannerFocusRequester = remember { FocusRequester() }
        val empfaengerFocusRequester = remember { FocusRequester() }

        // Standardmäßig soll der Fokus immer auf das Scannerfeld gesetzt werden.
        LaunchedEffect(Unit) {
            scannerFocusRequester.requestFocus()
        }

        var showNewMaterialDialog by remember { mutableStateOf(false) }
        var showDetailDialog by remember { mutableStateOf(false) }
        var selectedMaterial by remember { mutableStateOf<Material?>(null) }

        MaterialTheme(colors = GrayColorPalette) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                // --- Steuerleiste: Eine Row mit zwei Bereichen ---
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    // Linker Bereich: Zwei Eingabefelder (nebeneinander)
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
                                        viewModel.processScan(viewModel.scannerInput)
                                        viewModel.scannerInput = ""
                                        // Fokus bleibt immer auf Scanner
                                        scannerFocusRequester.requestFocus()
                                        true
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
                                            // Nach Enter im Empfängerfeld wieder den Scanner fokussieren
                                            scannerFocusRequester.requestFocus()
                                            true
                                        } else false
                                    }
                            )
                        }
                    }
                    // Rechter Bereich: Modus-Auswahl (RadioButtons) und "Neu"-Button
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Modus:", style = MaterialTheme.typography.subtitle1)
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = (viewModel.selectedMode == "Empfang"),
                                onClick = {
                                    viewModel.selectedMode = "Empfang"
                                    // Beim Umschalten immer den Fokus auf Scanner setzen
                                    scannerFocusRequester.requestFocus()
                                }
                            )
                            Text("Empfang")
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = (viewModel.selectedMode == "Ausgabe"),
                                onClick = {
                                    viewModel.selectedMode = "Ausgabe"
                                    // Beim Umschalten bleibt der Fokus standardmäßig im Scannerfeld
                                    scannerFocusRequester.requestFocus()
                                }
                            )
                            Text("Ausgabe")
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(onClick = { showNewMaterialDialog = true }) {
                            Text("Neu")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                // --- Materialübersicht als einfache Tabelle ---
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("Bezeichnung", modifier = Modifier.weight(1f), style = MaterialTheme.typography.subtitle1)
                    Text("Seriennummer", modifier = Modifier.weight(1f), style = MaterialTheme.typography.subtitle1)
                    Text("Status", modifier = Modifier.weight(1f), style = MaterialTheme.typography.subtitle1)
                    Text("Notiz", modifier = Modifier.weight(1f), style = MaterialTheme.typography.subtitle1)
                }
                Divider()
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(viewModel.materials) { material ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedMaterial = material
                                    showDetailDialog = true
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Text(material.bezeichnung ?: "–", modifier = Modifier.weight(1f))
                            Text(material.seriennummer ?: "–", modifier = Modifier.weight(1f))
                            Text(if (material.inLager) "Lager" else "Ausgegeben", modifier = Modifier.weight(1f))
                            Text(material.notiz ?: "–", modifier = Modifier.weight(1f))
                        }
                        Divider()
                    }
                }
            }
            // --- Dialog zum Hinzufügen eines neuen Materials ---
            if (showNewMaterialDialog) {
                Dialog(onCloseRequest = { showNewMaterialDialog = false }) {
                    Surface(
                        modifier = Modifier.wrapContentSize(),
                        shape = MaterialTheme.shapes.medium,
                        elevation = 8.dp
                    ) {
                        var seriennummer by remember { mutableStateOf("") }
                        var bezeichnung by remember { mutableStateOf("") }
                        var notiz by remember { mutableStateOf("") }
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Neues Material hinzufügen", style = MaterialTheme.typography.h6)
                            Spacer(modifier = Modifier.height(8.dp))
                            TextField(
                                value = seriennummer,
                                onValueChange = { seriennummer = it },
                                label = { Text("Seriennummer") }
                            )
                            TextField(
                                value = bezeichnung,
                                onValueChange = { bezeichnung = it },
                                label = { Text("Bezeichnung") }
                            )
                            TextField(
                                value = notiz,
                                onValueChange = { notiz = it },
                                label = { Text("Notiz") }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                Button(onClick = { showNewMaterialDialog = false }) {
                                    Text("Abbrechen")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = {
                                    val newMaterial = Material(
                                        seriennummer = seriennummer,
                                        bezeichnung = bezeichnung,
                                        inLager = viewModel.selectedMode == "Empfang", // Empfang: im Lager, Ausgabe: nicht im Lager
                                        notiz = notiz
                                    ).copy(
                                        verlaufLog = listOf(
                                            MaterialLog(LocalDateTime.now(), "System", "Material erstellt")
                                        )
                                    )
                                    viewModel.addNewMaterial(newMaterial)
                                    showNewMaterialDialog = false
                                }) {
                                    Text("Speichern")
                                }
                            }
                        }
                    }
                }
            }
            // --- Detaildialog: Material bearbeiten und Logs anzeigen ---
            if (showDetailDialog && selectedMaterial != null) {
                Dialog(onCloseRequest = { showDetailDialog = false }) {
                    Surface(
                        modifier = Modifier.wrapContentSize(),
                        shape = MaterialTheme.shapes.medium,
                        elevation = 8.dp
                    ) {
                        // Lokale Zustände für die Bearbeitung
                        var bezeichnung by remember { mutableStateOf(selectedMaterial?.bezeichnung ?: "") }
                        var seriennummer by remember { mutableStateOf(selectedMaterial?.seriennummer ?: "") }
                        var inLager by remember { mutableStateOf(selectedMaterial?.inLager ?: true) }
                        var notiz by remember { mutableStateOf(selectedMaterial?.notiz ?: "") }

                        // Aufteilung in zwei Spalten: Links Details, rechts Logs
                        Row(modifier = Modifier.padding(16.dp).wrapContentSize()) {
                            // Linke Spalte: Editierbare Felder
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
                                // Checkbox für den Lagerstatus
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
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    Button(onClick = { showDetailDialog = false }) {
                                        Text("Abbrechen")
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(onClick = {
                                        val updated = selectedMaterial!!.copy(
                                            bezeichnung = if (bezeichnung.isNotBlank()) bezeichnung else null,
                                            seriennummer = if (seriennummer.isNotBlank()) seriennummer else null,
                                            inLager = inLager,
                                            notiz = if (notiz.isNotBlank()) notiz else null,
                                            verlaufLog = selectedMaterial!!.verlaufLog + MaterialLog(
                                                timestamp = LocalDateTime.now(),
                                                user = "Editor",
                                                event = "Material aktualisiert"
                                            )
                                        )
                                        viewModel.updateMaterial(updated)
                                        showDetailDialog = false
                                    }) {
                                        Text("Speichern")
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            // Rechte Spalte: Logs (vertikal)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Verlauf:", style = MaterialTheme.typography.subtitle1)
                                LazyColumn(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
                                    items(selectedMaterial?.verlaufLog ?: emptyList()) { log ->
                                        Card(modifier = Modifier.padding(4.dp)) {
                                            Column(modifier = Modifier.padding(4.dp)) {
                                                Text(log.timestamp.toString(), style = MaterialTheme.typography.caption)
                                                Text(log.event, style = MaterialTheme.typography.body2)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ----------------------------
    // Einstiegspunkt der Anwendung
    // ----------------------------
    fun main() = application {
        Window(onCloseRequest = ::exitApplication, title = "Lagerverwaltung (MVVM & Grautöne)") {
            App()
        }
    }
