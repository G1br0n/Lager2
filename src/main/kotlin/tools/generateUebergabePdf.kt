package tools

import org.apache.pdfbox.pdmodel.*
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.common.PDRectangle
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun generateUebergabePdf(empfaenger: String, log: List<String>, modus: String = "Ausgabe") {
    // — Vorbereitung —
    val doc = PDDocument()
    val margin = 50f
    val lineHeight = 16f
    val columnGap = 20f
    val columnWidth = (PDRectangle.A4.width - 2 * margin - columnGap) / 2
    val startY = PDRectangle.A4.height - margin
    val minYContent = margin + 100f

    val isAusgabe = modus.equals("Ausgabe", ignoreCase = true)
    val titel = if (isAusgabe) "Ausgabeprotokoll" else "Empfangsprotokoll"

    // Gruppiere log nach Material → Seriennummern
    val groupedLog = log
        .filter { it.contains("SN") }
        .mapNotNull { it.split(" SN ").takeIf { it.size == 2 }?.let { it[0] to it[1] } }
        .groupBy({ it.first }, { it.second })
        .toSortedMap()
    val totalItems = groupedLog.values.sumOf { it.size }

    // — Deckblatt Seite 1 —
    val coverPage = PDPage(PDRectangle.A4)
    doc.addPage(coverPage)
    PDPageContentStream(doc, coverPage).use { cs ->
        var y = startY

        // Titel
        cs.beginText()
        cs.setFont(PDType1Font.HELVETICA_BOLD, 24f)
        cs.newLineAtOffset(margin, y)
        cs.showText(titel)
        cs.endText()
        y -= 40f

        // Empfänger & Datum
        cs.beginText()
        cs.setFont(PDType1Font.HELVETICA, 12f)
        cs.newLineAtOffset(margin, y)
        cs.showText("Empfänger: $empfaenger")
        cs.endText()
        y -= 20f

        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
        cs.beginText()
        cs.setFont(PDType1Font.HELVETICA, 12f)
        cs.newLineAtOffset(margin, y)
        cs.showText("Datum/Uhrzeit: $now")
        cs.endText()
        y -= 30f

        // Geräteübersicht
        cs.beginText()
        cs.setFont(PDType1Font.HELVETICA_BOLD, 14f)
        cs.newLineAtOffset(margin, y)
        cs.showText("Geräteübersicht:")
        cs.endText()
        y -= 20f

        groupedLog.forEach { (material, serien) ->
            cs.beginText()
            cs.setFont(PDType1Font.HELVETICA, 12f)
            cs.newLineAtOffset(margin, y)
            cs.showText("$material: ${serien.size} Stück")
            cs.endText()
            y -= 16f
            if (y < margin) return@use  // Sicherheit
        }
    }

    // — Inhalt-Seiten ab Seite 2 —
    val contentPages = mutableListOf<PDPage>()
    val contentStreams = mutableListOf<PDPageContentStream>()
    val contentPageRefs = mutableListOf<PDPage>()
    var currentPageNum = 2
    var x = margin
    var y = startY
    lateinit var content: PDPageContentStream

    fun newPage() {
        val p = PDPage(PDRectangle.A4)
        doc.addPage(p)
        contentPages += p
        content = PDPageContentStream(doc, p)
        contentStreams += content
        contentPageRefs += p
        x = margin
        y = startY
    }

    fun writeLine(text: String, bold: Boolean = false) {
        if (y - lineHeight <= minYContent) {
            if (x == margin) {
                x += columnWidth + columnGap
                y = startY
            } else {
                content.close()
                currentPageNum++
                newPage()
            }
        }
        content.beginText()
        content.setFont(if (bold) PDType1Font.HELVETICA_BOLD else PDType1Font.HELVETICA, 10f)
        content.newLineAtOffset(x, y)
        content.showText(text)
        content.endText()
        y -= lineHeight
    }

    // erste Inhaltsseite
    newPage()

    // Kopf auf jeder Inhaltsseite
    content.beginText()
    content.setFont(PDType1Font.HELVETICA_BOLD, 16f)
    content.newLineAtOffset(margin, y)
    content.showText(titel)
    content.endText()
    y -= 24f

    // Material-Listen
    groupedLog.forEach { (material, seriennummern) ->
        writeLine("$material – ${seriennummern.size}", bold = true)
        seriennummern.sorted().forEachIndexed { idx, sn ->
            writeLine("${idx + 1}. $sn")
        }
        writeLine("")  // Leerzeile
    }

    // — Unterschriften‐Block auf der letzten Inhaltsseite —
    // ACHTUNG: contentPageRefs enthält alle Inhaltsseiten, die letzte ist contentPageRefs.last()
    val lastPage = contentPageRefs.last()
    PDPageContentStream(doc, lastPage, PDPageContentStream.AppendMode.APPEND, true).use { sig ->
        val sigY = margin + 80f
        val regeln = if (isAusgabe) listOf(
            "Ich verpflichte mich, das Material sorgfältig zu behandeln,",
            "und bei Verlust oder Beschädigung unverzüglich Meldung zu machen."
        ) else listOf(
            "Ich bestätige, dass ich das Material unbeschädigt zurückgegeben habe."
        )
        var ty = sigY + 40f
        regeln.forEach { line ->
            sig.beginText()
            sig.setFont(PDType1Font.HELVETICA_OBLIQUE, 10f)
            sig.newLineAtOffset(margin, ty)
            sig.showText(line)
            sig.endText()
            ty -= 14f
        }
        // Unterschrift
        sig.beginText()
        sig.setFont(PDType1Font.HELVETICA, 12f)
        sig.newLineAtOffset(margin, sigY)
        sig.showText("Unterschrift: _________________________")
        sig.endText()
        // Datum/Uhrzeit
        sig.beginText()
        sig.setFont(PDType1Font.HELVETICA, 12f)
        sig.newLineAtOffset(margin, sigY - 20f)
        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
        sig.showText("Datum: $now")
        sig.endText()
    }

    // Fußzeile mit Seitenzahlen (nur auf Inhaltsseiten)
    contentStreams.forEach { it.close() }
    val totalContentPages = contentPageRefs.size
    contentPageRefs.forEachIndexed { idx, page ->
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true).use { footer ->
            footer.beginText()
            footer.setFont(PDType1Font.HELVETICA_OBLIQUE, 10f)
            footer.newLineAtOffset(PDRectangle.A4.width - margin - 100f, margin / 2)
            footer.showText("Seite ${idx + 2} von ${totalContentPages + 1}")
            footer.endText()
        }
    }

    // — Speichern —
    val desktop = System.getProperty("user.home") + "/Desktop"
    val outDir = File(desktop, "Uebergabeprotokolle/${if (isAusgabe) "Ausgabe" else "Empfang"}")
    if (!outDir.exists()) outDir.mkdirs()
    val tsFile = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"))
    val cleanName = empfaenger
        .replace(" ", "_")
        .replace("[^A-Za-z0-9_.-]".toRegex(), "_")
    val outFile = File(outDir, "${tsFile}_${titel}_$cleanName.pdf")
    doc.save(outFile)
    doc.close()

    println("✅ $titel gespeichert unter: ${outFile.absolutePath}")
}
