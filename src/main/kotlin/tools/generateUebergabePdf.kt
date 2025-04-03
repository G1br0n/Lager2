import org.apache.pdfbox.pdmodel.*
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.common.PDRectangle
import java.awt.Desktop
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun generateUebergabePdf(empfaenger: String, log: List<String>, modus: String = "Ausgabe") {
    val doc = PDDocument()
    val margin = 50f
    val lineHeight = 16f
    val columnGap = 20f
    val columnWidth = (PDRectangle.A4.width - 2 * margin - columnGap) / 2
    val startY = PDRectangle.A4.height - margin
    val minY = margin + 60f // Platz für Fußzeile

    val isAusgabe = modus.lowercase() == "ausgabe"
    val protokollTitel = if (isAusgabe) "Ausgabeprotokoll" else "Empfangsprotokoll"

    val groupedLog = log.filter { it.contains("SN") }
        .mapNotNull { entry ->
            val parts = entry.split(" SN ")
            if (parts.size == 2) parts[0] to parts[1] else null
        }
        .groupBy({ it.first }, { it.second })
        .toSortedMap()

    val formattedLogLines = groupedLog.flatMap { (material, seriennummern) ->
        val sortedSns = seriennummern.sorted()
        listOf("$material - ${sortedSns.size}") +
                sortedSns.mapIndexed { index, sn -> "${index + 1}. $sn" } +
                listOf("")
    }

    val totalItems = groupedLog.values.sumOf { it.size }

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

    newPage()

    fun writeLine(text: String, isBold: Boolean = false) {
        if (y <= minY) {
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
        content?.setFont(if (isBold) PDType1Font.HELVETICA_BOLD else PDType1Font.HELVETICA, 10f)
        content?.newLineAtOffset(x, y)
        content?.showText(text)
        content?.endText()
        y -= lineHeight
    }

    // Kopfzeile auf erster Seite
    content?.beginText()
    content?.setFont(PDType1Font.HELVETICA_BOLD, 18f)
    content?.newLineAtOffset(margin, y)
    content?.showText(protokollTitel)
    content?.endText()
    y -= 30f

    content?.beginText()
    content?.setFont(PDType1Font.HELVETICA, 12f)
    content?.newLineAtOffset(margin, y)
    content?.showText("Name: $empfaenger")
    content?.endText()
    y -= 20f

    content?.beginText()
    content?.setFont(PDType1Font.HELVETICA, 12f)
    content?.newLineAtOffset(margin, y)
    content?.showText("Gesamtanzahl: $totalItems")
    content?.endText()
    y -= 30f

    formattedLogLines.forEach { line ->
        writeLine(line, isBold = !line.contains("."))
    }

    // Hinweis und Unterschrift auf letzter Seite
    val lastStream = pageStreams.last()
    val sigY = margin + 80f
    val regelTextLines = if (isAusgabe)
        listOf(
            "Ich verpflichte mich, das mir übergebene Material sorgfältig zu behandeln,",
            "und bei Verlust oder Beschädigung unverzüglich Meldung zu machen."
        ) else listOf("Ich bestätige, dass ich das Material vollständig und unbeschädigt zurückgegeben habe.")

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

    pageStreams.forEach { it.close() }

    // Seitenzahlen einfügen
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

    val desktop = System.getProperty("user.home") + "/Desktop"
    val folder = File(desktop, "Uebergabeprotokolle")
    if (!folder.exists()) folder.mkdirs()

    val cleanName = empfaenger.replace(" ", "_").replace("[^a-zA-Z0-9_.-]".toRegex(), "_")
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
