package models

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class Material(
    val id: UUID = UUID.randomUUID(),
    val seriennummer: String? = null,
    val bezeichnung: String? = null,
    val inLager: Boolean,
    val notiz: String? = null,
    val position: String? = null,
    val tuevPlakette: Boolean = false,          // ← JETZT BOOLEAN
    val tuevAblaufDatum: LocalDate? = null,
    val verlaufLog: List<MaterialLog> = emptyList()
)


data class MaterialLog(
    val timestamp: LocalDateTime,
    val user: String,
    val event: String
)
