// Datei: views/MonitorView.kt
package views

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import config.APPConfig
import kotlinx.coroutines.delay
import models.Material
import tools.ToggleButtonBox
import viewModels.MaterialViewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * MonitorView zeigt:
 * - Linke Spalte: pro Bezeichnung eine „Spalte“ (LazyRow), darin:
 *     • Überschrift (Bezeichnung)
 *     • Gesamt / Im Lager / Ausgelagert
 *     • Für jede Position (nur Ausgelagert) eine Box pro Material
 * - Rechte Spalte: Buttons aller Positionen (Sonder-Positionen unten)
 * - Beim Klick auf Material-Box: loadLogsForMaterial(mat) und onMaterialSelected(mat)
 */
@Composable
fun MonitorView(
    viewModel: MaterialViewModel,
    onMaterialSelected: (Material) -> Unit
) {
    // 1) Hole (ggf. gefilterte) Liste aller Materialien
    val materials = viewModel.filteredMaterials

    // 2) Gruppiere alle Materialien (unabhängig von inLager) nach Bezeichnung
    val bezeichnungen = materials
        .groupBy { it.bezeichnung ?: "Unbekannt" }
        .toList()
        // Sortiere nach externer Reihenfolge (APPConfig.bezeichnungsReihenfolge)
        .sortedWith(compareBy { (bez, _) ->
            val idx = (APPConfig.bezeichnungsReihenfolge).indexOf(bez)
            if (idx >= 0) idx else Int.MAX_VALUE
        })
        .toMap()

    // 3) Filtere alle aktuell ausgelagerten Materialien
    val ausgegebeneMaterials = materials.filter { !it.inLager }

    // 4) Sammle alle Positionen (nur ausgelagerte), sortiere alphabetisch
    val allPositions = ausgegebeneMaterials.mapNotNull { it.position }.distinct().sorted()

    // 5) Erzeuge Farbzuordnung pro Position
    val positionColors = remember(allPositions) { generatePositionColors(allPositions) }

    // 6) Unterteile in reguläre und Sonder-Positionen
    val lowerSpecials = APPConfig.specialPositions.map { it.lowercase() }
    val sortedPositions = allPositions.sortedWith(compareBy { pos ->
        val lower = pos.lowercase()
        val idx = lowerSpecials.indexOf(lower)
        if (idx >= 0) "zzzz_$idx" else lower
    })
    val regularPositions = sortedPositions.filter { it.lowercase() !in lowerSpecials }
    val specialPositions = sortedPositions.filter { it.lowercase() in lowerSpecials }

    // 7) State: gerade angeklickte Position (für den Positions-Dialog)
    var selectedPositionForDialog by remember { mutableStateOf<String?>(null) }

    // 8) State: Filter-Dialog (Bezeichnungsauswahl)
    var showFilterDialog by remember { mutableStateOf(false) }



    Column{
        Row{
            //Material carden mit informationen über das material, wo man als Überschrift die bezeichnung siht
            // dadrunter di informationen In Lager(wen es als tru ist) und gesamt anzahl
            // daneben in der card sol lazy colum sein mit positionen und anzahl der geräte bei position(nur zeigen wen es nicht im lager ist )
        }
        Row {
            //wen eine row vol ist sol sich die nächste öfnen
        }
    }
 //zu dem soll es eine sortir funktion geben für die carde reinfolge , die reinfolge exsestirt bereits und du kans die daraus nehemen





    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier
            .fillMaxSize()
            .padding(4.dp)
        ) {
            // 9) **Linke Spalte**: pro Bezeichnung eine LazyRow
            LazyRow(modifier = Modifier.weight(1f)) {
                val groups = bezeichnungen.entries.toList()
                items(groups.size) { index ->
                    val (bezeichnung, mats) = groups[index]
                    val gesamtCount = mats.size
                    val inLagerCount = mats.count { it.inLager }
                    val notInLagerCount = mats.count { !it.inLager }

                    Column(
                        modifier = Modifier
                            .padding(2.dp)
                            .fillMaxHeight()
                            .width(140.dp)
                            .border(1.dp, Color.LightGray),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 9a) Überschrift der Spalte
                        Text(bezeichnung, style = MaterialTheme.typography.body1)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("$gesamtCount", color = Color(0xFF2E7D32), style = MaterialTheme.typography.h3)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("$inLagerCount", color = Color(0xFF1976D2), style = MaterialTheme.typography.h3)
                            Text(" / ", style = MaterialTheme.typography.h4)
                            Text("$notInLagerCount", color = Color(0xFFD32F2F), style = MaterialTheme.typography.h3)
                        }
                        Spacer(modifier = Modifier.height(4.dp))

                        // 9b) Innerhalb dieser Bezeichnung: nur ausgelagerte Materialien, gruppiert nach Position
                        val outOfStock = mats.filter { !it.inLager }
                        val groupedByPos = outOfStock.groupBy { it.position ?: "Unbekannt" }

                        groupedByPos.forEach { (pos, posMats) ->
                            // Positions-Kopfzeile (mit Hintergrundfarbe)
                            val posColor = positionColors[pos] ?: Color.LightGray
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 1.dp)
                                    .background(
                                        posColor.copy(alpha = 0.3f),
                                        shape = MaterialTheme.shapes.small
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(pos, modifier = Modifier.padding(2.dp), style = MaterialTheme.typography.caption)
                            }
                            Spacer(modifier = Modifier.height(1.dp))

                            // Alle Materialien an dieser Position
                            posMats.forEach { mat ->
                                val bgColor = positionColors[mat.position] ?: Color.LightGray
                                AnimatedVisibility(
                                    visible = true,
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically()
                                ) {
                                    MaterialBox(mat, bgColor) {
                                        // 1) Logs für dieses Material nachladen
                                        viewModel.loadLogsForMaterial(mat)
                                        // 2) Callback ans übergeordnete Level (z.B. App)
                                        onMaterialSelected(mat)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                    }

                    // Trenner zwischen den „Spalten“, außer nach der letzten
                    if (index < groups.size - 1) {
                        this@LazyRow.item {
                            Spacer(
                                modifier = Modifier
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .background(Color.Gray)
                            )
                        }
                    }
                }
            }

            // 10) **Rechte Spalte**: Buttons für alle Positionen
            val longestPos = allPositions.maxByOrNull { it.length } ?: ""
            val textWidth = longestPos.length * 11
            val buttonWidth = textWidth.dp + 32.dp

            Column(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .fillMaxHeight()
                    .width(buttonWidth),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Positionen", style = MaterialTheme.typography.h5)
                Spacer(modifier = Modifier.height(4.dp))

                // Reguläre Positionen oben
                regularPositions.forEach { pos ->
                    val color = positionColors[pos] ?: Color.LightGray
                    Button(
                        onClick = { selectedPositionForDialog = pos },
                        colors = ButtonDefaults.buttonColors(backgroundColor = color),
                        modifier = Modifier
                            .padding(horizontal = 2.dp, vertical = 1.dp)
                            .fillMaxWidth()
                    ) {
                        Text(pos, color = Color.Black)
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Divider(color = Color.Gray, thickness = 1.dp)
                Spacer(modifier = Modifier.height(4.dp))

                // Sonder-Positionen unten
                specialPositions.forEach { pos ->
                    val color = positionColors[pos] ?: Color.LightGray
                    Button(
                        onClick = { selectedPositionForDialog = pos },
                        colors = ButtonDefaults.buttonColors(backgroundColor = color),
                        modifier = Modifier
                            .padding(horizontal = 2.dp, vertical = 1.dp)
                            .fillMaxWidth()
                    ) {
                        Text(pos, color = Color.Black)
                    }
                }
            }
        }

        // 11) Filter-Button rechts unten
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Button(onClick = { showFilterDialog = true }) {
                Text("Filter")
            }
        }

        // 12) Filter-Dialog zur Auswahl der Bezeichnungen
        if (showFilterDialog) {
            Dialog(onDismissRequest = { showFilterDialog = false }) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colors.background,
                    tonalElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .width(300.dp)
                            .heightIn(max = 500.dp)
                    ) {
                        Text("Filter: Bezeichnungen", style = MaterialTheme.typography.h6)
                        Spacer(modifier = Modifier.height(8.dp))

                        BezeichnungCheckboxList(
                            allBezeichnungen = bezeichnungen.keys.toList(),
                            selectedBezeichnungen = bezeichnungen.keys.toList(),
                            onSelectionChange = { newList ->
                                viewModel.filterText = ""
                                viewModel.filterActive = newList.size < bezeichnungen.size
                                // Wir setzen filterText simpel auf ein OR-getrenntes Muster
                                viewModel.filterText = newList.joinToString(separator = "|") { it }
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { showFilterDialog = false },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Übernehmen")
                        }
                    }
                }
            }
        }

        // 13) Positions-Dialog: Alle Materialien an der angeklickten Position
        if (selectedPositionForDialog != null) {
            Dialog(onDismissRequest = { selectedPositionForDialog = null }) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colors.background,
                    tonalElevation = 8.dp
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Materialien bei: ${selectedPositionForDialog}",
                                style = MaterialTheme.typography.h5,
                                modifier = Modifier.weight(1f)
                            )
                            Button(onClick = { selectedPositionForDialog = null }) {
                                Text("Schließen")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val items = ausgegebeneMaterials
                            .filter { it.position == selectedPositionForDialog }

                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(items) { material ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        .border(1.dp, Color.LightGray)
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        material.bezeichnung ?: "Unbekannt",
                                        style = MaterialTheme.typography.body1,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        material.seriennummer ?: "-",
                                        style = MaterialTheme.typography.body2
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Einzelne Box für ein Material in der Spaltenansicht:
 * - Visible mit Fade-In/Slide-In
 * - Flash-Animation (Gelb kurz)
 * - Klickbar → onClick(material)
 */
@Composable
fun MaterialBox(
    material: Material,
    color: Color,
    onClick: (Material) -> Unit
) {
    val cleanedSerial = material.seriennummer?.trimEnd() ?: ""
    // Sonderregel: wenn "BüP" oder "HFE" in der Seriennummer, zeige erstes 3 Zeichen, sonst letzte 6
    val displaySerial = when {
        cleanedSerial.contains("BüP") || cleanedSerial.contains("HFE") ->
            cleanedSerial.take(3)
        cleanedSerial.length > 6 ->
            cleanedSerial.takeLast(6)
        else -> cleanedSerial
    }

    var isFlashing by remember { mutableStateOf(true) }
    val backgroundColor by animateColorAsState(
        targetValue = if (isFlashing) Color.Yellow.copy(alpha = 0.7f) else color,
        animationSpec = tween(durationMillis = 700)
    )
    LaunchedEffect(Unit) {
        delay(700)
        isFlashing = false
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(35.dp)
            .padding(horizontal = 3.dp, vertical = 2.dp)
            .clickable { onClick(material) }
            .background(backgroundColor, shape = MaterialTheme.shapes.medium)
            .border(2.dp, Color.Gray, shape = MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center
    ) {
        Text(displaySerial, style = MaterialTheme.typography.body1)
    }
}

/**
 * Liste aller Bezeichnungen mit Checkboxen zum Filtern.
 * selectedBezeichnungen enthält initial alle Bezeichnungen; onSelectionChange
 * gibt die neu ausgewählten Bezeichnungen zurück.
 */
@Composable
fun BezeichnungCheckboxList(
    allBezeichnungen: List<String>,
    selectedBezeichnungen: List<String>,
    onSelectionChange: (List<String>) -> Unit
) {
    Column(
        modifier = Modifier
            .padding(end = 4.dp, start = 1.dp)
            .fillMaxHeight()
            .width(200.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start
    ) {
        allBezeichnungen.forEach { bez ->
            val isSelected = selectedBezeichnungen.contains(bez)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 1.dp, vertical = 2.dp)
            ) {
                Text(
                    text = bez,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp),
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.End
                )
                ToggleButtonBox(
                    isChecked = isSelected,
                    onToggle = {
                        onSelectionChange(
                            if (!isSelected) selectedBezeichnungen + bez
                            else selectedBezeichnungen - bez
                        )
                    }
                )
            }
        }
    }
}


/**
 * Generiert für jede Position eine Farbe. Zuerst wird in APPConfig
 * nach einer `customColors`‐Zuordnung gesucht (key=Positionsname lowercase).
 * Falls keine Custom‐Farbe existiert, wird aus einer List<Color> zyklisch gewählt.
 */
// Ersatz für das bisherige generatePositionColors
fun generatePositionColors(positions: List<String>): Map<String, Color> {
    val colors       = (APPConfig.colors ?: listOf(Color.LightGray)).ifEmpty { listOf(Color.LightGray) }
    val customColors = APPConfig.customColors ?: emptyMap()

    val lowerCustom = customColors.mapKeys { it.key.lowercase() }
    return positions.mapIndexed { idx, pos ->
        val key   = pos.lowercase()
        val color = lowerCustom[key] ?: colors[idx % colors.size]
        pos to color
    }.toMap()
}
