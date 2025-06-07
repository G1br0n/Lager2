package viewModels

import androidx.compose.runtime.*
import kotlinx.coroutines.*
import models.Material
import models.MaterialLog
import repositorys.FirebaseMaterialRepository
import repositorys.MaterialRepository
import java.awt.event.KeyEvent
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.logging.Level
import java.util.logging.Logger
import javax.swing.SwingUtilities
import javazoom.jl.player.advanced.AdvancedPlayer

/**
 * ViewModel (Desktop‐Kompilierung), das:
 * 1) per Listener alle Top-Level‐Materialien asynchron lädt.
 * 2) CRUD-Aufrufe (add/update/delete) im Hintergrund ausführt.
 * 3) Logs für ein einzelnes Material bei Bedarf nachlädt (selectedLogs).
 * 4) Filter‐Logik bereitstellt (filteredMaterials).
 * 5) Scanner‐Eingaben abarbeitet und Materialstatus aktualisiert.
 * 6) Audio‐Feedback gibt (MP3‐Töne).
 */
class MaterialViewModel(private val repository: MaterialRepository) {
    companion object {
        private val LOG = Logger.getLogger("MaterialViewModel")
    }

    // ------------------------------------------------------------------------
    // CoroutineScope für Hintergrundarbeit. Wird bei dispose() gecancelt.
    // ------------------------------------------------------------------------
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ------------------------------------------------------------------------
    // Top-Level‐Materialien (ohne Logs). Wird per Listener aktualisiert.
    // ------------------------------------------------------------------------
    var materials = mutableStateListOf<Material>()
        private set

    // ------------------------------------------------------------------------
    // Lookup-Map: Seriennummer (trimmed) → Material. Für Scanner-Schnellsuche.
    // ------------------------------------------------------------------------
    private val materialBySerial = mutableMapOf<String, Material>()

    // ------------------------------------------------------------------------
    // Filter‐Zustände und gefilterte Liste
    // ------------------------------------------------------------------------
    var filterText by mutableStateOf("")
    var filterActive by mutableStateOf(false)

    /**
     * Gibt entweder die volle Liste zurück (wenn kein Filter aktiv ist)
     * oder alle Materialien, deren Bezeichnung, Seriennummer oder Position
     * den filterText enthält (ignoreCase).
     */
    val filteredMaterials: List<Material>
        get() = if (!filterActive || filterText.isBlank()) {
            materials
        } else {
            materials.filter { mat ->
                listOfNotNull(
                    mat.bezeichnung,
                    mat.seriennummer,
                    mat.position
                ).any { field ->
                    field.contains(filterText, ignoreCase = true)
                }
            }
        }

    // ------------------------------------------------------------------------
    // Scanner-Buffer (für externe Scanner, die Tastenanschläge emulieren)
    // ------------------------------------------------------------------------
    private var globalScannerBuffer by mutableStateOf("")

    // ------------------------------------------------------------------------
    // UI‐Zustände für Modus („Empfang“ / „Ausgabe“) und Empfängername
    // ------------------------------------------------------------------------
    var selectedMode by mutableStateOf("Empfang")  // "Empfang" oder "Ausgabe"
    var empfaengerName by mutableStateOf("")

    // ------------------------------------------------------------------------
    // Popup‐Warn‐Zustände (z.B. „Material nicht gefunden“, „bereits im Lager“)
    // ------------------------------------------------------------------------
    var showPopupWarning by mutableStateOf(false)
    var popupWarningText by mutableStateOf("")

    // ------------------------------------------------------------------------
    // Neu: State für die aktuell ausgewählten Logs (Detail‐Dialog)
    // ------------------------------------------------------------------------
    var selectedLogs by mutableStateOf<List<MaterialLog>>(emptyList())
        private set

    // ------------------------------------------------------------------------
    // Neu: State für das aktuell ausgewählte Material (Detail‐Dialog)
    // ------------------------------------------------------------------------
    var selectedMaterial by mutableStateOf<Material?>(null)
        private set

    // ------------------------------------------------------------------------
    // Formatter für Log‐Timestamps (ISO_LOCAL_DATE_TIME)
    // ------------------------------------------------------------------------
    private val dtf: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    init {
        // 1) Initial: Einmalig alle Top-Level‐Materialien laden
        viewModelScope.launch {
            try {
                val initialList = repository.getAllMaterials()
                SwingUtilities.invokeLater {
                    materials.clear()
                    materials.addAll(initialList)
                    reloadMaterialMap()
                }
            } catch (e: Exception) {
                LOG.log(Level.SEVERE, "Fehler beim initialen Laden: ${e.localizedMessage}")
            }
        }

        // 2) Realtime‐Listener registrieren, der nur Top-Level‐Daten liefert
        if (repository is FirebaseMaterialRepository) {
            repository.listenToMaterials { newList ->
                SwingUtilities.invokeLater {
                    materials.clear()
                    materials.addAll(newList)
                    reloadMaterialMap()
                }
            }
        }
    }

    // ------------------------------------------------------------------------
    // Scanner- und CRUD‐Operationen
    // ------------------------------------------------------------------------

    /**
     * Wird vom UI aufgerufen, wenn ein KeyEvent (Scan‐Gerät) ankommt.
     * Baut den globalScannerBuffer auf, bis ENTER → processScan aufrufen.
     */
    fun onGlobalKey(event: KeyEvent) {
        val c = event.keyChar
        when {
            c in ' '..'~' -> {
                globalScannerBuffer += c
            }
            event.keyCode == KeyEvent.VK_ENTER -> {
                val code = globalScannerBuffer.trim()
                globalScannerBuffer = ""
                if (code.isNotEmpty()) processScan(code)
            }
        }
    }

    /**
     * Verarbeitet den Scan‐Code (Seriennummer). Findet Material per exaktem
     * Lookup oder Prefix‐Match, aktualisiert In‐Lager/Position, hängt Log an.
     */
    fun processScan(scannedCode: String): String? {
        val code = scannedCode.trim()
        // 1) Exakter Lookup
        val exactMatch = materialBySerial[code]
        // 2) Falls kein exakter Match, Prefix‐Suche
        val found = exactMatch ?: run {
            val key = materialBySerial.keys.firstOrNull { it.startsWith(code) }
            key?.let { materialBySerial[it] }
        }

        if (found != null) {
            // Empfang & schon im Lager?
            if (selectedMode == "Empfang" && found.inLager) {
                popupWarningText = "„${found.bezeichnung ?: "Unbekannt"}“ ist bereits im Lager."
                showPopupWarning = true
                playErrorTone(); playImLagerTone()
                return null
            }
            // Ausgabe & nicht im Lager?
            if (selectedMode == "Ausgabe" && !found.inLager) {
                popupWarningText =
                    "„${found.bezeichnung ?: "Unbekannt"}“ NICHT im Lager.\nBefindet sich bei ${found.position}"
                showPopupWarning = true
                playErrorTone(); playNichtImLagerTone()
                return null
            }

            // Erstelle aktualisierte Kopie inklusive neuem Log‐Eintrag
            val updated = when (selectedMode) {
                "Empfang" -> found.copy(
                    inLager = true,
                    position = "Lager",
                    verlaufLog = found.verlaufLog + MaterialLog(
                        timestamp = LocalDateTime.now(),
                        user = "System",
                        event = "Material per Scan empfangen von $empfaengerName"
                    )
                )
                "Ausgabe" -> {
                    if (empfaengerName.isNotBlank()) {
                        found.copy(
                            inLager = false,
                            position = empfaengerName,
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

            // asynchrones Update in Firestore
            viewModelScope.launch {
                try {
                    repository.updateMaterial(updated)
                    // Realtime‐Listener sorgt dafür, dass `materials` aktualisiert wird
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    LOG.log(Level.SEVERE, "Fehler bei updateMaterial: ${t.localizedMessage}")
                }
            }

            playSuccessTone()
            return found.bezeichnung ?: ""
        } else {
            popupWarningText = "Material mit Seriennummer $code nicht gefunden."
            showPopupWarning = true
            playErrorTone(); playNichtErkanntTone()
            return null
        }
    }

    /**
     * Gibt den Namen eines Materials anhand der Seriennummer zurück (exact oder Prefix).
     */
    fun getMaterialNameBySerial(serial: String): String {
        val cleaned = serial.trim()
        val exact = materialBySerial[cleaned]
        val found = exact ?: materialBySerial.keys
            .firstOrNull { it.startsWith(cleaned) }
            ?.let { materialBySerial[it] }
        return found?.bezeichnung ?: "Unbekanntes Material"
    }

    /**
     * Fügt ein neues Material hinzu. Realtime‐Listener aktualisiert `materials`.
     */
    fun addNewMaterial(material: Material) {
        viewModelScope.launch {
            try {
                repository.addMaterial(material)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                LOG.log(Level.SEVERE, "Fehler beim addMaterial: ${t.localizedMessage}")
            }
        }
    }

    /**
     * Aktualisiert ein Material. Realtime‐Listener aktualisiert `materials`.
     */
    fun updateMaterial(updated: Material) {
        viewModelScope.launch {
            try {
                repository.updateMaterial(updated)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                LOG.log(Level.SEVERE, "Fehler beim updateMaterial: ${t.localizedMessage}")
            }
        }
    }

    /**
     * Löscht ein Material (inkl. Logs). Realtime‐Listener entfernt es aus `materials`.
     */
    fun deleteMaterial(material: Material) {
        viewModelScope.launch {
            try {
                repository.deleteMaterial(material)
                SwingUtilities.invokeLater {
                    popupWarningText = "„${material.bezeichnung ?: "Unbekannt"}“ wurde gelöscht."
                    showPopupWarning = true
                    playSuccessTone()
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                LOG.log(Level.SEVERE, "Fehler beim deleteMaterial: ${t.localizedMessage}")
            }
        }
    }

    /**
     * Ändert nur die Position (ohne Log) eines Materials, gefunden per Seriennummer.
     */
    fun updatePosition(serial: String, newPosition: String) {
        val cleaned = serial.trim()
        val exact = materialBySerial[cleaned]
        val mat = exact ?: materialBySerial.keys
            .firstOrNull { it.startsWith(cleaned) }
            ?.let { materialBySerial[it] }

        if (mat != null) {
            val updated = mat.copy(position = newPosition)
            updateMaterial(updated)
        } else {
            popupWarningText = "Material mit Seriennummer $serial nicht gefunden."
            showPopupWarning = true
            playErrorTone(); playNichtErkanntTone()
        }
    }

    /**
     * Macht die letzte Aktion rückgängig (empfangen/ausgeben) für Material per Seriennummer.
     */
    fun undoMaterialBySerial(serial: String): Boolean {
        val cleaned = serial.trim()
        val exact = materialBySerial[cleaned]
        val mat = exact ?: materialBySerial.keys
            .firstOrNull { it.startsWith(cleaned) }
            ?.let { materialBySerial[it] }

        return if (mat != null) {
            val letzteAktion = mat.verlaufLog.lastOrNull()
            if (letzteAktion == null) {
                popupWarningText = "Keine vorherige Aktion für Seriennummer $serial gefunden."
                showPopupWarning = true; playErrorTone()
                return false
            }
            val updated = when {
                letzteAktion.event.contains("ausgegeben an") -> {
                    mat.copy(
                        inLager = true,
                        position = "Lager",
                        verlaufLog = mat.verlaufLog + MaterialLog(
                            timestamp = LocalDateTime.now(),
                            user = "System",
                            event = "Ausgabe rückgängig gemacht – zurück ins Lager"
                        )
                    )
                }
                letzteAktion.event.contains("empfangen von") -> {
                    val name = letzteAktion.event.substringAfter("empfangen von").trim()
                    mat.copy(
                        inLager = false,
                        position = name,
                        verlaufLog = mat.verlaufLog + MaterialLog(
                            timestamp = LocalDateTime.now(),
                            user = "System",
                            event = "Empfang rückgängig gemacht – zurück zu $name"
                        )
                    )
                }
                else -> {
                    popupWarningText = "Letzte Aktion kann nicht rückgängig gemacht werden."
                    showPopupWarning = true; playErrorTone()
                    return false
                }
            }
            updateMaterial(updated)
            playSuccessTone()
            true
        } else {
            popupWarningText = "Material mit Seriennummer $serial nicht gefunden."
            showPopupWarning = true; playErrorTone()
            false
        }
    }

    // ----------------------------------------------------------------------------
    // Log‐Ladefunktion für Detail‐Dialog
    // ----------------------------------------------------------------------------
    fun getPositionLastUsedMap(): Map<String, Long> {
        val map = mutableMapOf<String, Long>()
        for (mat in materials) {
            if (!mat.inLager) {
                val pos = mat.position ?: continue
                val lastTimestamp = mat.verlaufLog
                    .mapNotNull { it.timestamp?.atZone(java.time.ZoneId.systemDefault())?.toInstant()?.toEpochMilli() }
                    .maxOrNull() ?: continue
                map[pos] = maxOf(map[pos] ?: 0, lastTimestamp)
            }
        }
        return map
    }


    /**
     * Lädt alle Logs für das angegebene Material und füllt selectedLogs.
     * Setzt außerdem selectedMaterial (ohne Logs), um z.B. im UI den Titel anzuzeigen.
     */
    fun loadLogsForMaterial(material: Material) {
        selectedMaterial = material.copy(verlaufLog = emptyList())
        selectedLogs = emptyList()

        viewModelScope.launch {
            try {
                val logs = when (repository) {
                    is FirebaseMaterialRepository -> {
                        repository.getLogsForMaterial(material.id)
                    }
                    else -> emptyList()
                }
                SwingUtilities.invokeLater {
                    selectedLogs = logs
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                LOG.log(Level.SEVERE, "Fehler beim Laden der Logs: ${t.localizedMessage}")
                SwingUtilities.invokeLater {
                    selectedLogs = emptyList()
                }
            }
        }
    }

    /**
     * Setzt ausgewähltes Material und Logs zurück (z.B. nach Schließen des Dialogs).
     */
    fun clearSelection() {
        selectedMaterial = null
        selectedLogs = emptyList()
    }

    // ----------------------------------------------------------------------------
    // Hilfsfunktion: Map nach Seriennummer neu aufbauen, wenn materials aktualisiert
    // ----------------------------------------------------------------------------
    private fun reloadMaterialMap() {
        materialBySerial.clear()
        for (mat in materials) {
            mat.seriennummer?.trim()?.let { sn ->
                materialBySerial[sn] = mat
            }
        }
    }

    // ----------------------------------------------------------------------------
    // Audio-Funktionen für Töne (optional)
    // ----------------------------------------------------------------------------
    private fun playMp3FromResource(resourcePath: String) {
        try {
            val inputStream = javaClass.getResourceAsStream(resourcePath)
            if (inputStream != null) {
                AdvancedPlayer(inputStream).play()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playSuccessTone()        { Thread { playMp3FromResource("/mp3/ok.mp3") }.start() }
    fun playErrorTone()          { Thread { playMp3FromResource("/mp3/error.mp3") }.start() }
    fun playEmpfangModusTone()   { Thread { playMp3FromResource("/mp3/empfangsmodus_2.mp3") }.start() }
    fun playAusgabeModusTone()   { Thread { playMp3FromResource("/mp3/ausgabemodus_2.mp3") }.start() }
    fun playNameErkanntTone()    { Thread { playMp3FromResource("/mp3/nameerkant_2.mp3") }.start() }
    fun playNichtErkanntTone()   { Thread { playMp3FromResource("/mp3/nicherkant.mp3") }.start() }
    fun playImLagerTone()        { Thread { playMp3FromResource("/mp3/imlager.mp3") }.start() }
    fun playNichtImLagerTone()   { Thread { playMp3FromResource("/mp3/nichimlager_2.mp3") }.start() }
    fun playScanEndeTone()       { Thread { playMp3FromResource("/mp3/scanende.mp3") }.start() }

    /**
     * Entfernt den Firestore‐Listener und bricht alle Coroutines ab.
     * Sollte aufgerufen werden, wenn das ViewModel endgültig nicht mehr benötigt wird.
     */

}
