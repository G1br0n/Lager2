package viewModels


import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import javazoom.jl.player.advanced.AdvancedPlayer
import models.Material
import models.MaterialLog
import repositorys.MaterialRepository
import java.awt.event.KeyEvent
import java.time.LocalDateTime

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
    var showAlreadyInLagerWarning by mutableStateOf(false)
    var lastScannedBezeichnung by mutableStateOf<String?>(null)
    var showPopupWarning by mutableStateOf(false)
    var popupWarningText by mutableStateOf("")
    var filterText by mutableStateOf("")
    var filterActive by mutableStateOf(false)
    private var globalScannerBuffer by mutableStateOf("")
    val filteredMaterials: List<Material>
        get() = if (!filterActive || filterText.isBlank()) {
            materials
        } else {
            materials.filter {
                it.bezeichnung?.contains(filterText, ignoreCase = true) == true ||
                        it.seriennummer?.contains(filterText, ignoreCase = true) == true ||
                        it.position?.contains(filterText, ignoreCase = true) == true
            }
        }
    var focusModeActive by mutableStateOf(false)
        private set

    fun toggleFocusMode() {
        focusModeActive = !focusModeActive
    }


    // Buffer für Scanner-Eingaben


    /**
     * Wird aus dem AWTEventListener aufgerufen.
     */
    fun onGlobalKey(event: KeyEvent) {
        val c = event.keyChar
        when {
            c in ' '..'~' -> {
                // druckbares Zeichen → anpuffern
                globalScannerBuffer += c
            }
            event.keyCode == KeyEvent.VK_ENTER -> {
                // Enter → verarbeiten und leeren
                handleGlobalScan(globalScannerBuffer.trim())
                globalScannerBuffer = ""
            }
        }
    }

    private fun handleGlobalScan(code: String) {
        // hier kannst du processScan oder eigene Logik nutzen
        if (code.isNotEmpty()) {
            processScan(code)
        }
    }
    fun processScan(scannedCode: String): String? {
        val code = scannedCode.trim()
        val found = materials.find { it.seriennummer?.trim()?.startsWith(code) == true }

        if (found != null) {
            // Fall 1: Empfang & schon im Lager
            if (selectedMode == "Empfang" && found.inLager) {
                popupWarningText = "„${found.bezeichnung ?: "Unbekannt"}“ ist berets im Lager."
                showPopupWarning = true
                playErrorTone()
                playImLagerTone()
                return null
            }

            // Fall 2: Ausgabe & nicht im Lager
            if (selectedMode == "Ausgabe" && !found.inLager) {
                popupWarningText = "„${found.bezeichnung ?: "Unbekannt"}“ NICHT im Lager. \n Befindet sich bei ${found.position}"
                showPopupWarning = true
                playErrorTone()
                playNichtImLagerTone()
                return null
            }

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

            updateMaterial(updated)
            playSuccessTone()
            return found.bezeichnung ?: ""
        } else {
            popupWarningText = "Material mit Seriennummer $code nicht gefunden."
            showPopupWarning = true
            playErrorTone()
            playNichtErkanntTone()
            return null
        }
    }


    fun getMaterialNameBySerial(serial: String): String {
        val cleanedSerial = serial.trim()
        return materials
            .firstOrNull { it.seriennummer?.trim()?.startsWith(cleanedSerial) == true }
            ?.bezeichnung ?: "Unbekanntes Material"
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

    fun deleteMaterial(material: Material) {
        materials.removeIf { it.id == material.id }
        repository.deleteMaterial(material)

        popupWarningText = "„${material.bezeichnung ?: "Unbekannt"}“ wurde gelöscht."
        showPopupWarning = true
        playSuccessTone()
    }


    fun updatePosition(serial: String, newPosition: String) {
        val material = materials.find { it.seriennummer?.trim()?.startsWith(serial.trim()) == true }
        if (material != null) {
            val updated = material.copy(position = newPosition)
            updateMaterial(updated)
        } else {
            popupWarningText = "Material mit Seriennummer $serial nicht gefunden."
            showPopupWarning = true
            playErrorTone()
            playNichtErkanntTone()
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

  fun undoMaterialBySerial(serial: String): Boolean {
        val material = materials.find { it.seriennummer?.trim()?.startsWith(serial.trim()) == true }

        return if (material != null) {
            val letzteAktion = material.verlaufLog.lastOrNull()

            if (letzteAktion == null) {
                popupWarningText = "Keine vorherige Aktion für Seriennummer $serial gefunden."
                showPopupWarning = true
                playErrorTone()
                return false
            }

            val updated = when {
                letzteAktion.event.contains("ausgegeben an") -> {
                    // Material wurde ausgegeben – wir machen es rückgängig (zurück ins Lager)
                    material.copy(
                        inLager = true,
                        position = "Lager",
                        verlaufLog = material.verlaufLog + MaterialLog(
                            timestamp = LocalDateTime.now(),
                            user = "System",
                            event = "Ausgabe rückgängig gemacht – zurück ins Lager"
                        )
                    )
                }

                letzteAktion.event.contains("empfangen von") -> {
                    // Material wurde empfangen – wir machen es rückgängig (zurück zur Person)
                    val name = letzteAktion.event.substringAfter("empfangen von").trim()
                    material.copy(
                        inLager = false,
                        position = name,
                        verlaufLog = material.verlaufLog + MaterialLog(
                            timestamp = LocalDateTime.now(),
                            user = "System",
                            event = "Empfang rückgängig gemacht – zurück zu $name"
                        )
                    )
                }

                else -> {
                    popupWarningText = "Letzte Aktion kann nicht rückgängig gemacht werden."
                    showPopupWarning = true
                    playErrorTone()
                    return false
                }
            }

            updateMaterial(updated)
            playSuccessTone()
            return true

        } else {
            popupWarningText = "Material mit Seriennummer $serial nicht gefunden."
            showPopupWarning = true
            playErrorTone()
            return false
        }
    }



    fun playSuccessTone() { Thread { playMp3FromResource("/mp3/ok.mp3") }.start() }
    fun playErrorTone() { Thread { playMp3FromResource("/mp3/error.mp3") }.start() }
    fun playEmpfangModusTone() {
        Thread { playMp3FromResource("/mp3/empfangsmodus.mp3") }.start()
    }

    fun playAusgabeModusTone() {
        Thread { playMp3FromResource("/mp3/ausgabemodus.mp3") }.start()
    }

    fun playNameErkanntTone() {
        Thread { playMp3FromResource("/mp3/nameerkant.mp3") }.start()
    }

    fun playNichtErkanntTone() {
        Thread { playMp3FromResource("/mp3/nicherkant.mp3") }.start()
    }

    fun playImLagerTone() {
        Thread { playMp3FromResource("/mp3/imlager.mp3") }.start()
    }
    fun playNichtImLagerTone() {
        Thread { playMp3FromResource("/mp3/nichimlager.mp3") }.start()
    }
}