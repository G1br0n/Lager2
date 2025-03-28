package viewModels


import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import javazoom.jl.player.advanced.AdvancedPlayer
import models.Material
import models.MaterialLog
import repositorys.MaterialRepository
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

    fun processScan(scannedCode: String): String? {
        val code = scannedCode.trim()
        val found = materials.find { it.seriennummer?.trim()?.startsWith(code) == true }

        if (found != null) {
            // Fall 1: Empfang & schon im Lager
            if (selectedMode == "Empfang" && found.inLager) {
                popupWarningText = "\u201e${found.bezeichnung ?: "Unbekannt"}\u201c ist bereits im Lager."
                showPopupWarning = true
                playErrorTone()
                return null
            }

            // Fall 2: Ausgabe & nicht im Lager
            if (selectedMode == "Ausgabe" && !found.inLager) {
                popupWarningText = "\u201e${found.bezeichnung ?: "Unbekannt"}\u201c ist nicht im Lager."
                showPopupWarning = true
                playErrorTone()
                return null
            }

            // Normale Verarbeitung
            val updated = when (selectedMode) {
                "Empfang" -> found.copy(
                    inLager = true,
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