// Datei: repositorys/SQLiteMaterialRepository.kt
package repositorys

import models.Material
import models.MaterialLog
import java.sql.Connection
import java.sql.DriverManager
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * SQLite-Implementierung für Desktop/CLI-Variante.
 * Tabellen:
 *   • materials      – Basisinformationen
 *   • material_log   – Verlaufseinträge
 */
class SQLiteMaterialRepository : FirebaseMaterialRepository() {

    private val connection: Connection = DriverManager.getConnection("jdbc:sqlite:materials.db")

    init {
        /* -------------------------------------------------------- *
         * 1) Tabellen anlegen, falls sie fehlen
         * -------------------------------------------------------- */
        val createMaterials = """
            CREATE TABLE IF NOT EXISTS materials (
                id              TEXT PRIMARY KEY,
                seriennummer    TEXT,
                bezeichnung     TEXT,
                inLager         INTEGER,
                notiz           TEXT,
                position        TEXT,
                tuevPlakette    INTEGER   DEFAULT 0,
                tuevAblaufDatum TEXT       DEFAULT ''
            );
        """.trimIndent()

        val createLogs = """
            CREATE TABLE IF NOT EXISTS material_log (
                id           INTEGER PRIMARY KEY AUTOINCREMENT,
                material_id  TEXT,
                timestamp    TEXT,
                user         TEXT,
                event        TEXT
            );
        """.trimIndent()

        connection.createStatement().use { st ->
            st.execute(createMaterials)
            st.execute(createLogs)
        }

        /* -------------------------------------------------------- *
         * 2) Schema-Migration – fehlende Spalten nachrüsten
         * -------------------------------------------------------- */
        fun columnExists(col: String): Boolean = connection.metaData
            .getColumns(null, null, "materials", col).next()

        if (!columnExists("tuevPlakette")) {
            connection.createStatement()
                .execute("ALTER TABLE materials ADD COLUMN tuevPlakette INTEGER DEFAULT 0")
        }
        if (!columnExists("tuevAblaufDatum")) {
            connection.createStatement()
                .execute("ALTER TABLE materials ADD COLUMN tuevAblaufDatum TEXT DEFAULT ''")
        }
    }

    /* ------------------------------------------------------------ *
     *  READ – alle Materialien inkl. Logs
     * ------------------------------------------------------------ */
    override fun getAllMaterials(): List<Material> {
        val list = mutableListOf<Material>()
        val sql = "SELECT * FROM materials"
        connection.prepareStatement(sql).use { st ->
            val rs = st.executeQuery()
            while (rs.next()) {
                val id       = UUID.fromString(rs.getString("id"))
                val serien   = rs.getString("seriennummer")
                val bez      = rs.getString("bezeichnung")
                val inLager  = rs.getInt("inLager") == 1
                val notiz    = rs.getString("notiz")
                val pos      = rs.getString("position")
                val plakette = rs.getInt("tuevPlakette") == 1
                val ablauf   = rs.getString("tuevAblaufDatum")
                    .takeIf { !it.isNullOrBlank() }?.let { LocalDate.parse(it) }

                /* Logs */
                val logs = mutableListOf<MaterialLog>()
                val logSql = "SELECT * FROM material_log WHERE material_id = ? ORDER BY timestamp ASC"
                connection.prepareStatement(logSql).use { logSt ->
                    logSt.setString(1, id.toString())
                    val lrs = logSt.executeQuery()
                    while (lrs.next()) {
                        logs += MaterialLog(
                            timestamp = LocalDateTime.parse(lrs.getString("timestamp")),
                            user      = lrs.getString("user"),
                            event     = lrs.getString("event")
                        )
                    }
                }

                list += Material(
                    id               = id,
                    seriennummer     = serien,
                    bezeichnung      = bez,
                    inLager          = inLager,
                    notiz            = notiz,
                    position         = pos,
                    tuevPlakette     = plakette,
                    tuevAblaufDatum  = ablauf,
                    verlaufLog       = logs
                )
            }
        }
        return list
    }

    /* ------------------------------------------------------------ *
     *  CREATE
     * ------------------------------------------------------------ */
    override fun addMaterial(material: Material) {
        val sql = """
            INSERT INTO materials
            (id, seriennummer, bezeichnung, inLager, notiz, position, tuevPlakette, tuevAblaufDatum)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { st ->
            st.setString(1, material.id.toString())
            st.setString(2, material.seriennummer)
            st.setString(3, material.bezeichnung)
            st.setInt   (4, if (material.inLager) 1 else 0)
            st.setString(5, material.notiz)
            st.setString(6, material.position)
            st.setInt   (7, if (material.tuevPlakette) 1 else 0)
            st.setString(8, material.tuevAblaufDatum?.toString() ?: "")
            st.executeUpdate()
        }

        /* Logs */
        val logSql = "INSERT INTO material_log (material_id, timestamp, user, event) VALUES (?, ?, ?, ?)"
        connection.prepareStatement(logSql).use { st ->
            material.verlaufLog.forEach { log ->
                st.setString(1, material.id.toString())
                st.setString(2, log.timestamp.toString())
                st.setString(3, log.user)
                st.setString(4, log.event)
                st.addBatch()
            }
            st.executeBatch()
        }
    }

    /* ------------------------------------------------------------ *
     *  UPDATE
     * ------------------------------------------------------------ */
    override fun updateMaterial(material: Material) {
        val sql = """
            UPDATE materials SET
                seriennummer    = ?,
                bezeichnung     = ?,
                inLager         = ?,
                notiz           = ?,
                position        = ?,
                tuevPlakette    = ?,
                tuevAblaufDatum = ?
            WHERE id = ?
        """.trimIndent()
        connection.prepareStatement(sql).use { st ->
            st.setString(1, material.seriennummer)
            st.setString(2, material.bezeichnung)
            st.setInt   (3, if (material.inLager) 1 else 0)
            st.setString(4, material.notiz)
            st.setString(5, material.position)
            st.setInt   (6, if (material.tuevPlakette) 1 else 0)
            st.setString(7, material.tuevAblaufDatum?.toString() ?: "")
            st.setString(8, material.id.toString())
            st.executeUpdate()
        }

        /* Logs: komplett ersetzen (einfach) */
        connection.prepareStatement("DELETE FROM material_log WHERE material_id = ?").use {
            it.setString(1, material.id.toString()); it.executeUpdate()
        }
        val logSql = "INSERT INTO material_log (material_id, timestamp, user, event) VALUES (?, ?, ?, ?)"
        connection.prepareStatement(logSql).use { st ->
            material.verlaufLog.forEach { log ->
                st.setString(1, material.id.toString())
                st.setString(2, log.timestamp.toString())
                st.setString(3, log.user)
                st.setString(4, log.event)
                st.addBatch()
            }
            st.executeBatch()
        }
    }

    /* ------------------------------------------------------------ *
     *  DELETE
     * ------------------------------------------------------------ */
    override fun deleteMaterial(material: Material) {
        connection.prepareStatement("DELETE FROM material_log WHERE material_id = ?").use {
            it.setString(1, material.id.toString()); it.executeUpdate()
        }
        connection.prepareStatement("DELETE FROM materials WHERE id = ?").use {
            it.setString(1, material.id.toString()); it.executeUpdate()
        }
        println("✅ Material gelöscht: ${material.bezeichnung} (${material.seriennummer})")
    }
}
