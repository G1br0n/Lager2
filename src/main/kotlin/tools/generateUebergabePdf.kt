package tools

import org.apache.pdfbox.pdmodel.*
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.common.PDRectangle
import java.awt.Desktop
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun generateUebergabePdf(empfaenger: String, log: List<String>, modus: String = "Ausgabe") {
    // 📄 Neues PDF-Dokument erstellen
    val doc = PDDocument()
    val margin = 50f
    val lineHeight = 16f
    val columnGap = 20f
    val columnWidth = (PDRectangle.A4.width - 2 * margin - columnGap) / 2
    val startY = PDRectangle.A4.height - margin
    val minY = margin + 100f // Platz für Fußzeile und Unterschrift

    // 🧭 Modus prüfen: Ausgabe oder Empfang
    val isAusgabe = modus.lowercase() == "ausgabe"
    val protokollTitel = if (isAusgabe) "Ausgabeprotokoll" else "Empfangsprotokoll"

    // 🧪 Beispiel-Log erzeugen (kann durch echten 'log' ersetzt werden)
    val testLog = (1..15).flatMap { i ->
        val material = "Material $i"
        (1..(5 + (i % 4))).map { sn -> "$material SN ${material.takeLast(1)}00$sn" }
    }

    // 🔢 Log in Map gruppieren: Material -> Seriennummern
    val groupedLog = testLog.filter { it.contains("SN") }
        .mapNotNull { entry ->
            val parts = entry.split(" SN ")
            if (parts.size == 2) parts[0] to parts[1] else null
        }
        .groupBy({ it.first }, { it.second })
        .toSortedMap()

    val totalItems = groupedLog.values.sumOf { it.size }

    // 📄 PDF-Seiten vorbereiten
    val pages = mutableListOf<PDPage>()
    val pageStreams = mutableListOf<PDPageContentStream>()
    val pageNumberRefs = mutableListOf<Pair<PDPage, Int>>()

    var currentPage: PDPage? = null
    var content: PDPageContentStream? = null
    var x = margin
    var y = startY
    var currentPageNum = 1

    fun newPage() {
        currentPage = PDPage(PDRectangle.A4)
        doc.addPage(currentPage)
        pages.add(currentPage!!)
        content = PDPageContentStream(doc, currentPage)
        pageStreams.add(content!!)
        pageNumberRefs.add(currentPage!! to currentPageNum)
        x = margin
        y = startY
    }

    fun writeLine(text: String, isBold: Boolean = false) {
        val spacing = lineHeight
        if (y - spacing <= minY) {
            if (x == margin) {
                x += columnWidth + columnGap
                y = startY
            } else {
                content?.close()
                currentPageNum++
                newPage()
            }
        }
        content?.beginText()
        content?.setFont(
            if (isBold) PDType1Font.HELVETICA_BOLD else PDType1Font.HELVETICA,
            10f
        )
        content?.newLineAtOffset(x, y)
        content?.showText(text)
        content?.endText()
        y -= spacing
    }

    // 📄 Erste Seite starten
    newPage()

    // 🧾 Kopfzeile schreiben
    content?.apply {
        beginText()
        setFont(PDType1Font.HELVETICA_BOLD, 18f)
        newLineAtOffset(margin, y)
        showText(protokollTitel)
        endText()
        y -= 30f

        beginText()
        setFont(PDType1Font.HELVETICA, 12f)
        newLineAtOffset(margin, y)
        showText("Name: $empfaenger")
        endText()
        y -= 20f

        beginText()
        setFont(PDType1Font.HELVETICA, 12f)
        newLineAtOffset(margin, y)
        showText("Gesamtanzahl: $totalItems")
        endText()
        y -= 30f
    }

    // 🧾 Inhalt schreiben (Material + Seriennummern)
    groupedLog.forEach { (material, seriennummern) ->
        val header = "$material - ${seriennummern.size}"
        val snLines = seriennummern.sorted().mapIndexed { i, sn -> "${i + 1}. $sn" }

        fun spaceNeeded(): Float {
            return (1 + snLines.size) * lineHeight + lineHeight
        }

        if (y - spaceNeeded() <= minY) {
            if (x == margin) {
                x += columnWidth + columnGap
                y = startY
            } else {
                content?.close()
                currentPageNum++
                newPage()
            }
        }

        writeLine(header, isBold = true)

        snLines.forEach { sn ->
            if (y - lineHeight <= minY) {
                if (x == margin) {
                    x += columnWidth + columnGap
                    y = startY
                } else {
                    content?.close()
                    currentPageNum++
                    newPage()
                }
                writeLine(header, isBold = true) // Header erneut bei Umbruch
            }
            writeLine(sn)
        }

        writeLine("") // Abstand nach jedem Block
    }

    // ✍️ Fußzeile mit Unterschrift und Datum/Uhrzeit
    val lastStream = pageStreams.last()
    lastStream.apply {
        val sigY = margin + 80f
        val regelTextLines = if (isAusgabe)
            listOf(
                "Ich verpflichte mich, das mir übergebene Material sorgfältig zu behandeln,",
                "und bei Verlust oder Beschädigung unverzüglich Meldung zu machen."
            ) else listOf("Ich bestätige, dass ich das Material vollständig und unbeschädigt zurückgegeben habe.")

        var regelY = sigY + 40f
        regelTextLines.forEach { line ->
            beginText()
            setFont(PDType1Font.HELVETICA_OBLIQUE, 10f)
            newLineAtOffset(margin, regelY)
            showText(line)
            endText()
            regelY -= 14f
        }

        beginText()
        setFont(PDType1Font.HELVETICA, 12f)
        newLineAtOffset(margin, sigY)
        showText("Unterschrift: _________________________")
        endText()

        beginText()
        setFont(PDType1Font.HELVETICA, 12f)
        newLineAtOffset(margin, sigY - 20f)
        val now = LocalDateTime.now()
        val dateTime = now.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
        showText("Datum: $dateTime")
        endText()
    }

    // 📄 Seitenzahlen einfügen
    pageStreams.forEach { it.close() }
    val totalPages = pageNumberRefs.size
    pageNumberRefs.forEachIndexed { index, (page, _) ->
        val footer = PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true)
        footer.beginText()
        footer.setFont(PDType1Font.HELVETICA_OBLIQUE, 10f)
        footer.newLineAtOffset(PDRectangle.A4.width - margin - 100f, margin / 2)
        footer.showText("Seite ${index + 1} von $totalPages")
        footer.endText()
        footer.close()
    }

    // 💾 PDF-Datei speichern mit strukturierter Ordnerstruktur und Zeitstempel
    val desktop = System.getProperty("user.home") + "/Desktop"
    val baseFolder = File(desktop, "Uebergabeprotokolle")
    val unterordner = if (isAusgabe) "Ausgabe" else "Empfang"
    val zielOrdner = File(baseFolder, unterordner)
    if (!zielOrdner.exists()) zielOrdner.mkdirs()

    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"))
    val cleanName = empfaenger.replace(" ", "_").replace("[^a-zA-Z0-9_.-]".toRegex(), "_")
    val fileName = "${timestamp}_${protokollTitel}_${cleanName}.pdf"
    val outputFile = File(zielOrdner, fileName)

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