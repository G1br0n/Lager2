// Datei: repositorys/FirebaseMaterialRepository.kt
package repositorys

import com.google.cloud.firestore.*
import com.google.firebase.cloud.FirestoreClient
import kotlinx.coroutines.*
import models.Material
import models.MaterialLog
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ExecutionException

/**
 * Firestore‐Implementierung von MaterialRepository, erweitert um:
 * 1) Einen SnapshotListener auf die Top‐Level‐Collection "materials".
 * 2) Eine Methode getLogsForMaterial(...) zum Nachladen der Logs.
 * 3) CRUD‐Methoden (add/update/delete) patchen nur veränderte Felder.
 */
class FirebaseMaterialRepository : MaterialRepository {

    private val firestore: Firestore = FirestoreClient.getFirestore()
    private val dtf: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME


    private fun humanReadable(bytes: Int): String =
        when {
            bytes >= 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024))
            bytes >= 1024        -> "%.2f KB".format(bytes / 1024.0)
            else                 -> "$bytes B"
        }

    override fun getAllMaterials(): List<Material> {
        // In der Desktop‐Variante verzichten wir hier auf eine synchrone Abfrage,
        // da wir im ViewModel per Listener ohnehin laufend aktualisieren.
        return emptyList()
    }

    override fun addMaterial(material: Material) {
        try {
            val materialData = mapOf(
                "seriennummer" to material.seriennummer,
                "bezeichnung" to material.bezeichnung,
                "inLager" to material.inLager,
                "notiz" to material.notiz,
                "position" to material.position
            )
            val docRef: DocumentReference = firestore
                .collection("materials")
                .document(material.id.toString())
            // neu anlegen
            docRef.set(materialData).get()

            // Falls beim Anlegen schon Logs vorhanden sind, hänge sie einmalig an
            if (material.verlaufLog.isNotEmpty()) {
                val logsCollection = docRef.collection("logs")
                for (log in material.verlaufLog) {
                    val logData = mapOf(
                        "timestamp" to log.timestamp.format(dtf),
                        "user" to log.user,
                        "event" to log.event
                    )
                    logsCollection.add(logData).get()
                }
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            println("addMaterial unterbrochen: ${e.localizedMessage}")
        } catch (e: ExecutionException) {
            println("Fehler beim addMaterial: ${e.cause?.localizedMessage ?: e.localizedMessage}")
        }
    }

    override fun updateMaterial(material: Material) {
        try {
            val docRef = firestore.collection("materials").document(material.id.toString())

            // 1) Nur geänderte Top-Level-Felder patchen
            val updateData = mapOf<String, Any?>(
                "seriennummer" to material.seriennummer,
                "bezeichnung" to material.bezeichnung,
                "inLager" to material.inLager,
                "notiz" to material.notiz,
                "position" to material.position
            )
            docRef.update(updateData).get()

            // 2) Nur neue Log‐Einträge anhängen (append-only)
            if (material.verlaufLog.isNotEmpty()) {
                val logsCollection = docRef.collection("logs")
                for (log in material.verlaufLog) {
                    val logData = mapOf(
                        "timestamp" to log.timestamp.format(dtf),
                        "user" to log.user,
                        "event" to log.event
                    )
                    logsCollection.add(logData).get()
                }
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            println("updateMaterial unterbrochen: ${e.localizedMessage}")
        } catch (e: ExecutionException) {
            println("Fehler beim updateMaterial: ${e.cause?.localizedMessage ?: e.localizedMessage}")
        }
    }

    override fun deleteMaterial(material: Material) {
        try {
            val docRef = firestore.collection("materials").document(material.id.toString())

            // Zuerst alle Logs in "logs" löschen
            val logsSnapshot: QuerySnapshot = docRef.collection("logs").get().get()
            if (logsSnapshot.documents.isNotEmpty()) {
                val batch: WriteBatch = firestore.batch()
                for (logDoc in logsSnapshot.documents) {
                    batch.delete(logDoc.reference)
                }
                batch.commit().get()
            }

            // Dann Material selbst löschen
            docRef.delete().get()
            println("Material ${material.id} gelöscht.")
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            println("deleteMaterial unterbrochen: ${e.localizedMessage}")
        } catch (e: ExecutionException) {
            println("Fehler beim deleteMaterial: ${e.cause?.localizedMessage ?: e.localizedMessage}")
        }
    }

    fun getLogsForMaterial(materialId: UUID): List<MaterialLog> {
        val logs = mutableListOf<MaterialLog>()
        try {
            val docRef = firestore.collection("materials").document(materialId.toString())
            // Hier sortieren wir gleich in der Abfrage nach Timestamp (ISO-Strings sortieren lexikografisch korrekt)
            val logsSnapshot: QuerySnapshot = docRef
                .collection("logs")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get()
                .get()

            for (logDoc in logsSnapshot.documents) {
                val tsString = logDoc.getString("timestamp")
                val timestamp = tsString?.let { LocalDateTime.parse(it, dtf) } ?: LocalDateTime.now()
                val user = logDoc.getString("user") ?: "Unbekannt"
                val event = logDoc.getString("event") ?: ""
                logs.add(MaterialLog(timestamp, user, event))
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            println("getLogsForMaterial unterbrochen: ${e.localizedMessage}")
        } catch (e: ExecutionException) {
            println("Fehler bei getLogsForMaterial: ${e.cause?.localizedMessage ?: e.localizedMessage}")
        }
        return logs
    }

    /**
     * Registriert einen Firestore‐SnapshotListener auf die Collection "materials".
     * Jedes Mal, wenn sich Top‐Level‐Dokumente ändern, wird `onUpdate(...)` mit
     * der neuen Liste aller Materials aufgerufen. Gibt ein AutoCloseable zurück,
     * damit der Aufrufer später `close()` aufrufen kann.
     */
    fun listenToMaterials(onUpdate: (List<Material>) -> Unit): AutoCloseable {
        val registration = firestore.collection("materials")
            .addSnapshotListener { snapshots: QuerySnapshot?, error: FirestoreException? ->
                if (error != null) {
                    println("Listener-Fehler in listenToMaterials: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshots == null) {
                    println("listenToMaterials: snapshot ist null")
                    return@addSnapshotListener
                }

                val totalBytes = snapshots.documents.sumOf { doc ->
                    doc.id.toByteArray().size +
                            (doc.data?.toString()?.toByteArray()?.size ?: 0)
                }
                println("ListenToMaterials: geladen ${humanReadable(totalBytes)}")

                val tempList = mutableListOf<Material>()
                for (doc in snapshots.documents) {
                    try {
                        val id = UUID.fromString(doc.id)
                        val seriennr = doc.getString("seriennummer")
                        val bez = doc.getString("bezeichnung")
                        val inLagerFlag = doc.getBoolean("inLager") ?: false
                        val notiz = doc.getString("notiz")
                        val pos = doc.getString("position")
                        val logs = emptyList<MaterialLog>() // Logs werden nur bei Bedarf geladen
                        tempList.add(Material(id, seriennr, bez, inLagerFlag, notiz, pos, logs))
                    } catch (_: Exception) {
                        // Ungültige UUID oder Mapping-Fehler → Dokument überspringen
                    }
                }
                onUpdate(tempList)
            }

        return object : AutoCloseable {
            override fun close() {
                registration.remove()
            }
        }
    }
}
