package repositorys


import models.Material
import models.MaterialLog
import java.sql.Connection
import java.sql.DriverManager
import java.time.LocalDateTime
import java.util.*




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



