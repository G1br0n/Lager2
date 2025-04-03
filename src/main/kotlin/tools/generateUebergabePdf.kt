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

    // Gruppieren wie im ScanDialog
    val groupedLog = log.filter { it.contains("SN") }
        .groupBy { it.substringBefore(" SN") }
        .toSortedMap()

    val sortedAndFormattedLog = groupedLog.flatMap { (materialArt, eintraege) ->
        listOf("$materialArt") + eintraege.asReversed().mapIndexed { index, eintrag ->
            "${eintraege.size - index}. $eintrag"
        } + ""
    }

    // Gesamtanzahl
    val totalItems = log.count { it.contains("SN") }

    // Seitenweise Inhalte aufteilen
    val pagesContent = sortedAndFormattedLog.chunked(maxLinesPerPage)

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

        // Anzahl
        content.beginText()
        content.setFont(PDType1Font.HELVETICA, 12f)
        content.newLineAtOffset(margin, y)
        content.showText("Gesamtanzahl: $totalItems")
        content.endText()
        y -= 30f

        // Einträge
        pageEntries.forEach { entry ->
            content.beginText()
            content.setFont(
                if (!entry.contains("SN")) PDType1Font.HELVETICA_BOLD else PDType1Font.HELVETICA,
                10f
            )
            content.newLineAtOffset(margin, y)
            content.showText(entry)
            content.endText()
            y -= lineHeight
        }
    }

    // Hinweis & Unterschrift auf letzter Seite
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

    // Unterschrift + Datum
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

    // Zielordner & Dateiname
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
