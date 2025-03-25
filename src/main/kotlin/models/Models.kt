package models

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class Material(
    val id: UUID = UUID.randomUUID(),
    val seriennummer: String? = null,
    val artikelnummer: String? = null,

    val hersteller: String? = null,
    val modell: String? = null,

    val kaufdatum: LocalDate? = null ,
    val archivierungsdatum: LocalDate? = null, // Null, falls noch nicht archiviert (ausser Betrieb genommen)
    val tuvDatum: LocalDate? = null,          // TÜV-Datum, kann auch null sein
    val tuvGueltigkeitTage: Int? = null,        // Anzahl der Tage, für die TÜV gültig ist
    val aktuellePosition: String? = null,       // z. B. Lagerort oder Standortbeschreibung
    val aktuellerStatus: String? = null,        // z. B. "in Betrieb", "defekt", "wartet auf Reparatur"
    val inLager: Boolean,                       // Gibt an, ob das Material im Lager ist

    val verlaufLog: List<MaterialLog> = emptyList(),

    val notizen: String? = null,

    val garantieBis: LocalDate? = null
)

data class MaterialLog(
    val timestamp: LocalDateTime,  // Zeitpunkt des Ereignisses
    val user: String,              // Wer hat das Ereignis ausgelöst oder dokumentiert
    val event: String              // Beschreibung des Ereignisses, z.B. "Empfangen", "Verkauft", "In Wartung"
)

