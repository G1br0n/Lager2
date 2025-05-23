package tools

import org.apache.pdfbox.pdmodel.*
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.common.PDRectangle
import java.awt.Desktop
import java.io.File
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
    val minY = margin + 100f // Platz für Fußzeile

    // 🧭 Modus prüfen
    val isAusgabe = modus.lowercase() == "ausgabe"
    val protokollTitel = if (isAusgabe) "Ausgabeprotokoll" else "Empfangsprotokoll"

    // 🔢 Log gruppieren: Material -> Seriennummern
    val groupedLog = log
        .filter { it.contains("SN") }
        .mapNotNull { entry ->
            entry.split(" SN ").takeIf { it.size == 2 }?.let { it[0] to it[1] }
        }
        .groupBy({ it.first }, { it.second })
        .toSortedMap()

    val totalItems = groupedLog.values.sumOf { it.size }

    // 📄 Canvas vorbereiten
    val pages = mutableListOf<PDPage>()
    val streams = mutableListOf<PDPageContentStream>()
    val pageRefs = mutableListOf<Pair<PDPage, Int>>()
    var currentPageNum = 1
    var x = margin
    var y = startY
    var content: PDPageContentStream? = null

    fun newPage() {
        val page = PDPage(PDRectangle.A4)
        doc.addPage(page)
        pages += page
        content = PDPageContentStream(doc, page)
        streams += content!!
        pageRefs += page to currentPageNum
        x = margin; y = startY
    }

    fun writeLine(text: String, bold: Boolean = false) {
        if (y - lineHeight <= minY) {
            if (x == margin) {
                x += columnWidth + columnGap
                y = startY
            } else {
                content?.close()
                currentPageNum++
                newPage()
            }
        }
        content!!.beginText()
        content!!.setFont(if (bold) PDType1Font.HELVETICA_BOLD else PDType1Font.HELVETICA, 10f)
        content!!.newLineAtOffset(x, y)
        content!!.showText(text)
        content!!.endText()
        y -= lineHeight
    }

    // — Erste Seite starten —
    newPage()

    // === Kopfzeile links ===
    content!!.apply {
        // Titel
        beginText()
        setFont(PDType1Font.HELVETICA_BOLD, 18f)
        newLineAtOffset(margin, y)
        showText(protokollTitel)
        endText()
        y -= 30f

        // Empfänger
        beginText()
        setFont(PDType1Font.HELVETICA, 12f)
        newLineAtOffset(margin, y)
        showText("Name: $empfaenger")
        endText()
        y -= 20f

        // Gesamtanzahl
        beginText()
        setFont(PDType1Font.HELVETICA, 12f)
        newLineAtOffset(margin, y)
        showText("Gesamtanzahl: $totalItems")
        endText()
        y -= 30f
    }

    // === Geräteübersicht rechts im Kopfbereich ===
    content!!.apply {
        val overviewX = margin + columnWidth + columnGap
        var overviewY = startY - 10f  // leicht unterhalb der Seitenoberkante

        // Überschrift
        beginText()
        setFont(PDType1Font.HELVETICA_BOLD, 12f)
        newLineAtOffset(overviewX, overviewY)
        showText("Geräteübersicht")
        endText()
        overviewY -= 16f

        // Geräte und Anzahl
        groupedLog.forEach { (material, serien) ->
            beginText()
            setFont(PDType1Font.HELVETICA, 10f)
            newLineAtOffset(overviewX, overviewY)
            showText("$material: ${serien.size}×")
            endText()
            overviewY -= 14f
        }
    }

    // === Inhalt: Material + Seriennummern ===
    groupedLog.forEach { (material, seriennummern) ->
        writeLine("$material – ${seriennummern.size}", bold = true)
        seriennummern.sorted().forEachIndexed { i, sn ->
            writeLine("${i + 1}. $sn")
        }
        writeLine("") // Leerzeile
    }

    // ✍️ Fußzeile mit Unterschrift und Datum
    streams.last().apply {
        val sigY = margin + 80f
        val texte = if (isAusgabe) listOf(
            "Ich verpflichte mich, das Material sorgfältig zu behandeln,",
            "und bei Verlust oder Beschädigung unverzüglich Meldung zu machen."
        ) else listOf(
            "Ich bestätige, dass ich das Material unbeschädigt zurückgegeben habe."
        )
        var ty = sigY + 40f
        texte.forEach { line ->
            beginText()
            setFont(PDType1Font.HELVETICA_OBLIQUE, 10f)
            newLineAtOffset(margin, ty)
            showText(line)
            endText()
            ty -= 14f
        }
        beginText()
        setFont(PDType1Font.HELVETICA, 12f)
        newLineAtOffset(margin, sigY)
        showText("Unterschrift: _________________________")
        endText()

        beginText()
        setFont(PDType1Font.HELVETICA, 12f)
        newLineAtOffset(margin, sigY - 20f)
        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
        showText("Datum: $now")
        endText()
    }

    // Seitenzahlen
    streams.forEach { it.close() }
    val totalPages = pageRefs.size
    pageRefs.forEachIndexed { idx, (page, _) ->
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true).use { footer ->
            footer.beginText()
            footer.setFont(PDType1Font.HELVETICA_OBLIQUE, 10f)
            footer.newLineAtOffset(PDRectangle.A4.width - margin - 100f, margin / 2)
            footer.showText("Seite ${idx + 1} von $totalPages")
            footer.endText()
        }
    }

    // 💾 Speichern
    val desktop = System.getProperty("user.home") + "/Desktop"
    val outDir = File(desktop, "Uebergabeprotokolle/${if (isAusgabe) "Ausgabe" else "Empfang"}")
    if (!outDir.exists()) outDir.mkdirs()
    val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"))
    val clean = empfaenger.replace(" ", "_").replace("[^A-Za-z0-9_.-]".toRegex(), "_")
    val file = File(outDir, "${ts}_${protokollTitel}_$clean.pdf")
    doc.save(file)
    doc.close()

    println("✅ $protokollTitel gespeichert unter: ${file.absolutePath}")
    if (Desktop.isDesktopSupported()) {
        //try { Desktop.getDesktop().open(file) } catch (_: Exception) {}
    }
}
