package tools

import org.apache.pdfbox.pdmodel.*
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.common.PDRectangle
import java.awt.Desktop
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun generateUebergabePdf(empfaenger: String, log: List<String>, modus: String = "Ausgabe") {
    val doc = PDDocument()
    val contentStreams = mutableListOf<PDPageContentStream>()

    val margin = 50f
    val lineHeight = 16f
    val usableHeight = PDRectangle.A4.height - 2 * margin
    val maxLinesPerPage = ((usableHeight - 160f) / lineHeight).toInt()

    val isAusgabe = modus.lowercase() == "ausgabe"
    val protokollTitel = if (isAusgabe) "Ausgabeprotokoll" else "Empfangsprotokoll"

    // Gruppieren wie im ScanDialog – UND Seriennummern sortieren
    val groupedLog = log
        .filter { it.contains("SN") }
        .mapNotNull { entry ->
            val parts = entry.split(" SN ")
            if (parts.size == 2) parts[0] to parts[1] else null
        }
        .groupBy({ it.first }, { it.second })
        .toSortedMap()

    // Für PDF: ["Bezeichnung - Anzahl", "1. Seriennummer", ...]
    val formattedLogLines = groupedLog.flatMap { (material, seriennummern) ->
        val sortedSns = seriennummern.sorted()
        listOf("$material - ${sortedSns.size}") +
                sortedSns.mapIndexed { index, sn -> "${index + 1}. $sn" } +
                listOf("") // Leerzeile dazwischen
    }

    // Gesamtanzahl aller Seriennummern
    val totalItems = groupedLog.values.sumOf { it.size }

    // Seitenweise Inhalte
    val pagesContent = formattedLogLines.chunked(maxLinesPerPage)

    pagesContent.forEach { pageEntries ->
        val page = PDPage(PDRectangle.A4)
        doc.addPage(page)

        val content = PDPageContentStream(doc, page)
        contentStreams.add(content)

        var y = page.mediaBox.height - margin

        // Titel
        content.beginText()
        content.setFont(PDType1Font.HELVETICA_BOLD, 18f)
        content.newLineAtOffset(margin, y)
        content.showText(protokollTitel)
        content.endText()
        y -= 30f

        // Name
        content.beginText()
        content.setFont(PDType1Font.HELVETICA, 12f)
        content.newLineAtOffset(margin, y)
        content.showText("Name: $empfaenger")
        content.endText()
        y -= 20f

        // Gesamtanzahl
        content.beginText()
        content.setFont(PDType1Font.HELVETICA, 12f)
        content.newLineAtOffset(margin, y)
        content.showText("Gesamtanzahl: $totalItems")
        content.endText()
        y -= 30f

        // Einträge (Materialgruppen & SNs)
        pageEntries.forEach { line ->
            content.beginText()
            content.setFont(
                if (!line.contains(".")) PDType1Font.HELVETICA_BOLD else PDType1Font.HELVETICA,
                10f
            )
            content.newLineAtOffset(margin, y)
            content.showText(line)
            content.endText()
            y -= lineHeight
        }
    }

    // Hinweis & Unterschrift
    val lastStream = contentStreams.last()
    val sigY = margin + 80f

    val regelTextLines = if (isAusgabe)
        listOf(
            "Ich verpflichte mich, das mir übergebene Material sorgfältig zu behandeln,",
            "und bei Verlust oder Beschädigung unverzüglich Meldung zu machen."
        )
    else
        listOf(
            "Ich bestätige, dass ich das Material vollständig und unbeschädigt zurückgegeben habe."
        )

    var regelY = sigY + 40f
    regelTextLines.forEach { line ->
        lastStream.beginText()
        lastStream.setFont(PDType1Font.HELVETICA_OBLIQUE, 10f)
        lastStream.newLineAtOffset(margin, regelY)
        lastStream.showText(line)
        lastStream.endText()
        regelY -= 14f
    }

    lastStream.beginText()
    lastStream.setFont(PDType1Font.HELVETICA, 12f)
    lastStream.newLineAtOffset(margin, sigY)
    lastStream.showText("Unterschrift: _________________________")
    lastStream.endText()

    lastStream.beginText()
    lastStream.setFont(PDType1Font.HELVETICA, 12f)
    lastStream.newLineAtOffset(margin, sigY - 20f)
    val date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
    lastStream.showText("Datum: $date")
    lastStream.endText()

    contentStreams.forEach { it.close() }

    // Speicherort
    val desktop = System.getProperty("user.home") + "/Desktop"
    val folder = File(desktop, "Uebergabeprotokolle")
    if (!folder.exists()) folder.mkdirs()

    val cleanName = empfaenger.replace(" ", "_")
        .replace("[^a-zA-Z0-9_.-]".toRegex(), "_")
    val fileName = "${protokollTitel}_${cleanName}_$date.pdf"
    val outputFile = File(folder, fileName)

    doc.save(outputFile)
    doc.close()

    println("✅ $protokollTitel gespeichert unter: ${outputFile.absolutePath}")

    if (Desktop.isDesktopSupported()) {
        try {
            Desktop.getDesktop().open(outputFile)
        } catch (e: Exception) {
            println("⚠️ PDF konnte nicht geöffnet werden: ${e.message}")
        }
    }
}

