import com.google.cloud.firestore.Firestore
import com.google.firebase.cloud.FirestoreClient
import com.google.cloud.firestore.WriteBatch
import com.google.cloud.firestore.QueryDocumentSnapshot
import com.google.cloud.firestore.QuerySnapshot
import com.google.cloud.firestore.DocumentReference
import models.Material
import repositorys.MaterialRepository
import java.util.concurrent.ExecutionException

fun syncSqliteToFirestore(repository: MaterialRepository) {
    // 1. Hole alle Einträge aus SQLite
    val allMaterials: List<Material> = repository.getAllMaterials()
    if (allMaterials.isEmpty()) {
        println("Keine Materialien in SQLite gefunden – nichts zu synchronisieren.")
        return
    }

    // 2. Firestore-Instanz
    val firestore: Firestore = FirestoreClient.getFirestore()

    try {
        // ────────────────────────────────────────────────────────
        // 3. Alte Firestore-Daten inkl. Subcollections (logs) löschen
        // ────────────────────────────────────────────────────────

        val collectionRef = firestore.collection("materials")
        val querySnapshot: QuerySnapshot = collectionRef.get().get()

        if (!querySnapshot.isEmpty) {
            // Für jedes Material-Dokument:
            for (doc: QueryDocumentSnapshot in querySnapshot.documents) {
                val docRef: DocumentReference = doc.reference

                // 3.1 Zuerst Untersammlung "logs" vollständig löschen
                val logsSnapshot = docRef.collection("logs").get().get()
                if (!logsSnapshot.isEmpty) {
                    val batchLogs: WriteBatch = firestore.batch()
                    for (logDoc in logsSnapshot.documents) {
                        batchLogs.delete(logDoc.reference)
                    }
                    batchLogs.commit().get()
                    println("  → Alte Logs für Material ${doc.id} gelöscht.")
                }

                // 3.2 Dann das Parent-Dokument löschen
                docRef.delete().get()
                println("  → Altes Material-Dokument ${doc.id} gelöscht.")
            }
            println("Alle alten Dokumente und deren Logs wurden gelöscht.")
        } else {
            println("Keine alten Dokumente zum Löschen in der Collection 'materials'.")
        }

    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        println("Sync abgebrochen (Interrupted): ${e.localizedMessage}")
        return
    } catch (e: ExecutionException) {
        println("Fehler beim Löschen alter Dokumente oder Logs: ${e.cause?.localizedMessage ?: e.localizedMessage}")
        // Trotzdem fortfahren, um neue Daten zu schreiben
    }

    // ────────────────────────────────────────────────────────
    // 4. Neue Daten aus SQLite hochladen
    // ────────────────────────────────────────────────────────
    uploadMaterialsToFirestore(allMaterials, firestore)
}

/**
 * Schreibt die Liste von Materialien in Firestore.
 * Jeder Material-Eintrag wird unter "materials/{id}" angelegt,
 * und die Logs in Unter-Collection "materials/{id}/logs".
 */
private fun uploadMaterialsToFirestore(
    materials: List<Material>,
    firestore: Firestore
) {
    val dtf = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME

    for (material in materials) {
        try {
            // 4.1 Basisdaten-Mapping
            val materialData = mapOf(
                "seriennummer" to material.seriennummer,
                "bezeichnung" to material.bezeichnung,
                "inLager" to material.inLager,
                "notiz" to material.notiz,
                "position" to material.position
            )

            // 4.2 Document-Referenz unter "materials/{UUID}"
            val docRef: DocumentReference = firestore
                .collection("materials")
                .document(material.id.toString())

            // 4.3 Basisdaten setzen
            docRef.set(materialData).get()
            println("Material ${material.id} geschrieben.")

            // 4.4 Logs in Unter-Collection "logs" anlegen
            val logsCollection = docRef.collection("logs")
            for (log in material.verlaufLog) {
                val logData = mapOf(
                    "timestamp" to log.timestamp.format(dtf),
                    "user" to log.user,
                    "event" to log.event
                )
                logsCollection.add(logData).get()
            }
            println("  → ${material.verlaufLog.size} Logs hochgeladen.")

        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            println("Upload abgebrochen für Material ${material.id}: Interrupted")
        } catch (e: ExecutionException) {
            println("Fehler beim Schreiben Material ${material.id}: ${e.cause?.localizedMessage ?: e.localizedMessage}")
        }
    }
}
