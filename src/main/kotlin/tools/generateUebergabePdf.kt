package tools

import org.apache.pdfbox.pdmodel.*
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.common.PDRectangle
import java.awt.Color
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

        // Empfänger-Label
        cs.beginText()
        cs.setFont(PDType1Font.HELVETICA, 12f)
        cs.newLineAtOffset(margin, y)
        cs.showText("Empfänger: ")
        cs.endText()
        // Gelber Hintergrund NUR hinter dem Namen
        val labelWidth = PDType1Font.HELVETICA.getStringWidth("Empfänger: ") / 1000 * 12f
        val nameWidth = PDType1Font.HELVETICA.getStringWidth(empfaenger) / 1000 * 12f
        cs.setNonStrokingColor(Color.YELLOW)
        cs.addRect(margin + labelWidth, y - 2f, nameWidth, 14f)
        cs.fill()
        // Name selbst
        cs.setNonStrokingColor(Color.BLACK)
        cs.beginText()
        cs.setFont(PDType1Font.HELVETICA, 12f)
        cs.newLineAtOffset(margin + labelWidth, y)
        cs.showText(empfaenger)
        cs.endText()
        y -= 20f

        // Datum/Uhrzeit-Label
        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
        cs.beginText()
        cs.setFont(PDType1Font.HELVETICA, 12f)
        cs.newLineAtOffset(margin, y)
        cs.showText("Datum/Uhrzeit: ")
        cs.endText()
        // Gelber Hintergrund NUR hinter dem Timestamp
        val dateLabelWidth = PDType1Font.HELVETICA.getStringWidth("Datum/Uhrzeit: ") / 1000 * 12f
        val timeText = now
        val timeWidth = PDType1Font.HELVETICA.getStringWidth(timeText) / 1000 * 12f
        cs.setNonStrokingColor(Color.YELLOW)
        cs.addRect(margin + dateLabelWidth, y - 2f, timeWidth, 14f)
        cs.fill()
        // Timestamp selbst
        cs.setNonStrokingColor(Color.BLACK)
        cs.beginText()
        cs.setFont(PDType1Font.HELVETICA, 12f)
        cs.newLineAtOffset(margin + dateLabelWidth, y)
        cs.showText(timeText)
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
            if (y < margin) return@use
        }

        // Gesamtanzahl
        cs.beginText()
        cs.setFont(PDType1Font.HELVETICA_BOLD, 12f)
        cs.newLineAtOffset(margin, y - 10f)
        cs.showText("Gesamtanzahl: $totalItems")
        cs.endText()
    }

    // — Inhalt-Seiten ab Seite 2 —
    val contentPages = mutableListOf<PDPage>()
    val contentStreams = mutableListOf<PDPageContentStream>()
    val contentPageRefs = mutableListOf<PDPage>()
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

    // Header mit getrennten Label und Highlight nur für Werte
    val headerTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
    val prefix = "$titel an "
    val name = empfaenger
    val timestamp = headerTime

    // Prefix
    content.beginText()
    content.setFont(PDType1Font.HELVETICA_BOLD, 16f)
    content.newLineAtOffset(margin, y)
    content.showText(prefix)
    content.endText()
    // Highlight Name
    val prefixW = PDType1Font.HELVETICA_BOLD.getStringWidth(prefix) / 1000 * 16f
    val nameW = PDType1Font.HELVETICA_BOLD.getStringWidth(name) / 1000 * 16f
    content.setNonStrokingColor(Color.YELLOW)
    content.addRect(margin + prefixW, y - 2f, nameW, 18f)
    content.fill()
    // Name
    content.setNonStrokingColor(Color.BLACK)
    content.beginText()
    content.setFont(PDType1Font.HELVETICA_BOLD, 16f)
    content.newLineAtOffset(margin + prefixW, y)
    content.showText(name)
    content.endText()
    // Highlight Timestamp
    val gap = 8f
    val tx = margin + prefixW + nameW + gap
    val tW = PDType1Font.HELVETICA_BOLD.getStringWidth(timestamp) / 1000 * 16f
    content.setNonStrokingColor(Color.YELLOW)
    content.addRect(tx, y - 2f, tW, 18f)
    content.fill()
    // Timestamp
    content.setNonStrokingColor(Color.BLACK)
    content.beginText()
    content.setFont(PDType1Font.HELVETICA_BOLD, 16f)
    content.newLineAtOffset(tx, y)
    content.showText(timestamp)
    content.endText()
    y -= 24f

    // Material-Listen
    groupedLog.forEach { (material, seriennummern) ->
        writeLine("$material – ${seriennummern.size}", bold = true)
        seriennummern.sorted().forEachIndexed { idx, sn ->
            writeLine("${idx + 1}. $sn")
        }
        writeLine("")
    }

    // Unterschriften‐Block auf der letzten Inhaltsseite
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
        sig.beginText()
        sig.setFont(PDType1Font.HELVETICA, 12f)
        sig.newLineAtOffset(margin, sigY)
        sig.showText("Unterschrift: _________________________")
        sig.endText()
        sig.beginText()
        sig.setFont(PDType1Font.HELVETICA, 12f)
        sig.newLineAtOffset(margin, sigY - 20f)
        val now2 = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
        sig.showText("Datum: $now2")
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
