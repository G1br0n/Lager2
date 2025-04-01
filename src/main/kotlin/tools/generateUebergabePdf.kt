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
    val pages = mutableListOf<PDPage>()
    val contentStreams = mutableListOf<PDPageContentStream>()

    val margin = 50f
    val lineHeight = 16f
    val usableHeight = PDRectangle.A4.height - 2 * margin
    val maxLinesPerPage = ((usableHeight - 160f) / lineHeight).toInt()

    val pagesContent = log.chunked(maxLinesPerPage)

    val isAusgabe = modus.lowercase() == "ausgabe"
    val protokollTitel = if (isAusgabe) "Ausgabeprotokoll" else "Empfangsprotokoll"

    // Inhalt pro Seite
    pagesContent.forEach { pageEntries ->
        val page = PDPage(PDRectangle.A4)
        pages.add(page)
        doc.addPage(page)

        val content = PDPageContentStream(doc, page)
        contentStreams.add(content)

        var y = page.mediaBox.height - margin

        content.beginText()
        content.setFont(PDType1Font.HELVETICA_BOLD, 18f)
        content.newLineAtOffset(margin, y)
        content.showText(protokollTitel)
        content.endText()

        y -= 30f

        content.beginText()
        content.setFont(PDType1Font.HELVETICA, 12f)
        content.newLineAtOffset(margin, y)
        content.showText("Name: $empfaenger")
        content.endText()

        y -= 30f

        pageEntries.forEachIndexed { index, entry ->
            content.beginText()
            content.setFont(PDType1Font.HELVETICA, 10f)
            content.newLineAtOffset(margin, y)
            content.showText("${index + 1}. $entry")
            content.endText()
            y -= lineHeight
        }
    }

    // Hinweis + Unterschrift nur auf der letzten Seite
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

    // Unterschrift
    lastStream.beginText()
    lastStream.setFont(PDType1Font.HELVETICA, 12f)
    lastStream.newLineAtOffset(margin, sigY)
    lastStream.showText("Unterschrift: _________________________")
    lastStream.endText()

    // Datum
    lastStream.beginText()
    lastStream.setFont(PDType1Font.HELVETICA, 12f)
    lastStream.newLineAtOffset(margin, sigY - 20f)
    val date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
    lastStream.showText("Datum: $date")
    lastStream.endText()

    // Stream schließen
    contentStreams.forEach { it.close() }

    // Zielordner auf Desktop
    val desktop = System.getProperty("user.home") + "/Desktop"
    val folder = File(desktop, "Uebergabeprotokolle")
    if (!folder.exists()) folder.mkdirs()

    val cleanName = empfaenger.replace(" ", "_")
    // Nutze:
    val outputFile = File.createTempFile("${protokollTitel}_${cleanName}_$date", ".pdf")

    doc.save(outputFile)
    doc.close()

    println("✅ $protokollTitel erstellt: ${outputFile.absolutePath}")

    if (Desktop.isDesktopSupported()) {
        try {
            Desktop.getDesktop().open(outputFile)

            // Optional: Temp-Datei beim JVM-Exit löschen
            outputFile.deleteOnExit()
        } catch (e: Exception) {
            println("⚠️ PDF konnte nicht geöffnet werden: ${e.message}")
        }
    }
}
