// Datei: repositorys/FirebaseMaterialRepository.kt
package repositorys

import com.google.cloud.firestore.*
import com.google.firebase.cloud.FirestoreClient
import kotlinx.coroutines.*
import models.Material
import models.MaterialLog
import java.time.LocalDate                    // ← NEU
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ExecutionException

/**
 * Firestore-Implementierung von MaterialRepository.
 *  • SnapshotListener auf „materials“
 *  • getLogsForMaterial(...)
 *  • CRUD (add/update/delete) – patcht nur veränderte Felder
 *  • Unterstützt die neuen Felder  tuevPlakette (Boolean)  +  tuevAblaufDatum (String→LocalDate)
 */
open class FirebaseMaterialRepository : MaterialRepository {

    private val firestore: Firestore = FirestoreClient.getFirestore()
    private val dtf: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME   // für Logs

    /* Hilfs-Funktion: Byte-Größe schön */
    private fun humanReadable(bytes: Int) =
        when {
            bytes >= 1_048_576 -> "%.2f MB".format(bytes / 1_048_576.0)
            bytes >= 1_024     -> "%.2f KB".format(bytes / 1_024.0)
            else               -> "$bytes B"
        }

    /* ------------------------------------------------------------
     *  Desktop-Variante: ViewModel aktualisiert per Listener
     * ------------------------------------------------------------ */
    override fun getAllMaterials(): List<Material> = emptyList()

    /* ------------------------------------------------------------
     *  1)  ADD
     * ------------------------------------------------------------ */
    override fun addMaterial(material: Material) {
        try {
            val data = mapOf(
                "seriennummer"    to material.seriennummer,
                "bezeichnung"     to material.bezeichnung,
                "inLager"         to material.inLager,
                "notiz"           to material.notiz,
                "position"        to material.position,
                /* NEU */
                "tuevPlakette"    to material.tuevPlakette,
                "tuevAblaufDatum" to material.tuevAblaufDatum?.toString()   // yyyy-MM-dd oder null
            )

            val docRef = firestore.collection("materials")
                .document(material.id.toString())

            docRef.set(data).get()

            /* vorhandene Logs anhängen */
            material.verlaufLog.forEach { log ->
                docRef.collection("logs").add(
                    mapOf(
                        "timestamp" to log.timestamp.format(dtf),
                        "user"      to log.user,
                        "event"     to log.event
                    )
                ).get()
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            println("addMaterial unterbrochen: ${e.localizedMessage}")
        } catch (e: ExecutionException) {
            println("Fehler beim addMaterial: ${e.cause?.localizedMessage ?: e.localizedMessage}")
        }
    }

    /* ------------------------------------------------------------
     *  2)  UPDATE
     * ------------------------------------------------------------ */
    override fun updateMaterial(material: Material) {
        try {
            val docRef = firestore.collection("materials")
                .document(material.id.toString())

            val updates = mapOf<String, Any?>(
                "seriennummer"    to material.seriennummer,
                "bezeichnung"     to material.bezeichnung,
                "inLager"         to material.inLager,
                "notiz"           to material.notiz,
                "position"        to material.position,
                /* NEU */
                "tuevPlakette"    to material.tuevPlakette,
                "tuevAblaufDatum" to material.tuevAblaufDatum?.toString()
            )

            docRef.update(updates).get()

            /* neue Logs anhängen (append-only) */
            material.verlaufLog.forEach { log ->
                docRef.collection("logs").add(
                    mapOf(
                        "timestamp" to log.timestamp.format(dtf),
                        "user"      to log.user,
                        "event"     to log.event
                    )
                ).get()
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            println("updateMaterial unterbrochen: ${e.localizedMessage}")
        } catch (e: ExecutionException) {
            println("Fehler beim updateMaterial: ${e.cause?.localizedMessage ?: e.localizedMessage}")
        }
    }

    /* ------------------------------------------------------------
     *  3)  DELETE (unverändert)
     * ------------------------------------------------------------ */
    override fun deleteMaterial(material: Material) {
        try {
            val doc = firestore.collection("materials").document(material.id.toString())

            /* erst alle Logs löschen */
            val logs = doc.collection("logs").get().get()
            if (!logs.isEmpty) {
                val batch = firestore.batch()
                logs.documents.forEach { batch.delete(it.reference) }
                batch.commit().get()
            }

            /* dann das Material selbst */
            doc.delete().get()
            println("Material ${material.id} gelöscht.")
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            println("deleteMaterial unterbrochen: ${e.localizedMessage}")
        } catch (e: ExecutionException) {
            println("Fehler beim deleteMaterial: ${e.cause?.localizedMessage ?: e.localizedMessage}")
        }
    }

    /* ------------------------------------------------------------
     *  4)  Logs eines Materials nachladen
     * ------------------------------------------------------------ */
    override fun getLogsForMaterial(materialId: UUID): List<MaterialLog> {
        val out = mutableListOf<MaterialLog>()
        try {
            val snap = firestore.collection("materials")
                .document(materialId.toString())
                .collection("logs")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get().get()

            snap.documents.forEach { d ->
                val ts = d.getString("timestamp")
                val time = ts?.let { LocalDateTime.parse(it, dtf) } ?: LocalDateTime.now()
                out.add(
                    MaterialLog(
                        timestamp = time,
                        user      = d.getString("user") ?: "Unbekannt",
                        event     = d.getString("event") ?: ""
                    )
                )
            }
        } catch (e: Exception) {
            println("Fehler bei getLogsForMaterial: ${e.localizedMessage}")
        }
        return out
    }

    /* ------------------------------------------------------------
     *  5)  Live-Listener (Top-Level-Daten)
     * ------------------------------------------------------------ */
    fun listenToMaterials(onUpdate: (List<Material>) -> Unit): AutoCloseable {
        val registration = firestore.collection("materials")
            .addSnapshotListener { snaps, err ->
                if (err != null || snaps == null) {
                    println("Listener-Fehler: ${err?.localizedMessage}")
                    return@addSnapshotListener
                }

                println("ListenToMaterials: geladen ${humanReadable(snaps.size())}")

                val list = snaps.documents.mapNotNull { doc ->
                    kotlin.runCatching {
                        Material(
                            id               = UUID.fromString(doc.id),
                            seriennummer     = doc.getString("seriennummer"),
                            bezeichnung      = doc.getString("bezeichnung"),
                            inLager          = doc.getBoolean("inLager") ?: false,
                            notiz            = doc.getString("notiz"),
                            position         = doc.getString("position"),
                            tuevPlakette     = doc.getBoolean("tuevPlakette") ?: false,
                            tuevAblaufDatum  = doc.getString("tuevAblaufDatum")
                                ?.takeIf { it.isNotBlank() }
                                ?.let { LocalDate.parse(it) },
                            verlaufLog       = emptyList()
                        )
                    }.getOrNull()  // ungültige UUID → null
                }

                onUpdate(list)
            }

        return AutoCloseable { registration.remove() }
    }
}




/**
 * Gemeinsames Contract-Interface für alle Material-Repositories
 * (Firebase, SQLite, Mock …).
 */
interface MaterialRepository {

    /** Alle Materialien zurückgeben – inkl. Logs, falls vorhanden. */
    fun getAllMaterials(): List<Material>

    /** Neues Material anlegen. */
    fun addMaterial(material: Material)

    /** Bestehendes Material (Top-Level + Logs) komplett ersetzen/patchen. */
    fun updateMaterial(material: Material)

    /** Material + Logs löschen. */
    fun deleteMaterial(material: Material)

    /** Optional: nur die TÜV-Daten via Serien-Nr. ändern. */
    fun updateTuevBySerial(
        serial: String,
        hatPlakette: Boolean,
        ablaufDatum: java.time.LocalDate? = null
    ): Boolean = false          // Default-Implementierung (nicht jede DB braucht das)

    /** Optional: nur Logs eines Materials nachladen. */
    fun getLogsForMaterial(id: UUID): List<models.MaterialLog> = emptyList()
}
