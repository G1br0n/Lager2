package views

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
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

@Composable
fun MonitorView(
    viewModel: MaterialViewModel,
    onMaterialSelected: (Material) -> Unit
) {

    val config = APPConfig

    val ausgegebeneMaterials = viewModel.materials.filter { !it.inLager }

    val bezeichnungsReihenfolge = config.bezeichnungsReihenfolge

    val groupedByBezeichnung = viewModel.materials
        .groupBy { it.bezeichnung ?: "Unbekannt" }
        .toList()
        .sortedWith(compareBy { (bezeichnung, _) ->
            val index = bezeichnungsReihenfolge.indexOf(bezeichnung)
            if (index != -1) index else Int.MAX_VALUE
        })
        .toMap()

    val allBezeichnungen = groupedByBezeichnung.keys.toList()

    val allPositions = ausgegebeneMaterials.mapNotNull { it.position }.distinct().sorted()
    val positionColors = remember(allPositions) { generatePositionColors(allPositions) }

    val darkGreen = Color(0xFF2E7D32)
    val red = Color(0xFFD32F2F)
    val blue = Color(0xFF1976D2)

    val selectedBezeichnungenState = remember(allBezeichnungen) {
        mutableStateOf(allBezeichnungen)
    }
    val selectedBezeichnungen = selectedBezeichnungenState.value

    var selectedPositionForDialog by remember { mutableStateOf<String?>(null) }
    var showFilterDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
        ) {

            // Mittlere Anzeige-Spalten (LazyRow), nur für ausgewählte Bezeichnungen
            LazyRow(modifier = Modifier.weight(1f)) {
                val selectedGroups = selectedBezeichnungen.mapNotNull { bezeichnung ->
                    groupedByBezeichnung[bezeichnung]?.let { bezeichnung to it }
                }

                items(selectedGroups.size) { index ->
                    val (bezeichnung, materials) = selectedGroups[index]
                    val inLagerCount = materials.count { it.inLager }
                    val notInLagerCount = materials.count { !it.inLager }
                    val totalCount = materials.size

                    LazyColumn(
                        modifier = Modifier
                            .padding(2.dp)
                            .fillMaxHeight()
                            .width(140.dp)
                            .border(1.dp, Color.LightGray),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        item {
                            Text(bezeichnung, style = MaterialTheme.typography.body1)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("$totalCount", color = darkGreen, style = MaterialTheme.typography.h3)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("$inLagerCount", color = blue, style = MaterialTheme.typography.h3)
                                Text(" / ", style = MaterialTheme.typography.h4)
                                Text("$notInLagerCount", color = red, style = MaterialTheme.typography.h3)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        val groupedByPosition = materials.filter { !it.inLager }
                            .groupBy { it.position ?: "Unbekannt" }

                        groupedByPosition.forEach { (position, posMaterials) ->
                            item {
                                val positionColor = positionColors[position] ?: Color.LightGray
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 1.dp)
                                        .background(
                                            positionColor.copy(alpha = 0.3f),
                                            shape = MaterialTheme.shapes.small
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        position,
                                        modifier = Modifier.padding(2.dp),
                                        style = MaterialTheme.typography.caption
                                    )
                                }
                                Spacer(modifier = Modifier.height(1.dp))
                            }
                            items(posMaterials, key = { it.id }) { material ->
                                val color = positionColors[material.position] ?: Color.LightGray

                                AnimatedVisibility(
                                    visible = true,
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically()
                                ) {
                                    MaterialBox(material, color, onMaterialSelected)
                                }
                            }



                            item { Spacer(modifier = Modifier.height(2.dp)) }
                        }
                    }

                    if (index < selectedGroups.size - 1) {
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

            // Rechte Spalte: Positionen
            // Sonder-Positionen sortieren


            val specialPositions = config.specialPositions.map { it.lowercase() }

            val sortedPositions = allPositions.sortedWith(compareBy { position ->
                val lower = position.lowercase()
                val specialIndex = specialPositions.indexOf(lower)
                if (specialIndex >= 0) {
                    // Spezialposition: ans Ende, in definierter Reihenfolge
                    "zzzz_$specialIndex"
                } else {
                    lower
                }
            })


            val longestPositionText = sortedPositions.maxByOrNull { it.length } ?: ""
            val textLengthInDp = longestPositionText.length * 11
            val buttonWidth = textLengthInDp.dp + 32.dp



            val regularPositions = sortedPositions.filterNot { it in specialPositions }
            val specialPositionButtons = sortedPositions.filter { it in specialPositions }






            Column(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .fillMaxHeight()
                    .width(buttonWidth),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Positionen", style = MaterialTheme.typography.h5)
                Spacer(modifier = Modifier.height(4.dp))

                // Normale Positionen oben
                regularPositions.forEach { position ->
                    val color = positionColors[position] ?: Color.LightGray
                    Button(
                        onClick = { selectedPositionForDialog = position },
                        colors = ButtonDefaults.buttonColors(backgroundColor = color),
                        modifier = Modifier
                            .padding(horizontal = 2.dp, vertical = 1.dp)
                            .fillMaxWidth()
                    ) {
                        Text(position, color = Color.Black)
                    }
                }

                // Platzfüller, damit die Sonder-Buttons nach unten wandern
                Spacer(modifier = Modifier.weight(1f))

                // Optional: Trennlinie
                Divider(color = Color.Gray, thickness = 1.dp)
                Spacer(modifier = Modifier.height(4.dp))

                // Sonder-Positionen ganz unten
                specialPositionButtons.forEach { position ->
                    val color = positionColors[position] ?: Color.LightGray
                    Button(
                        onClick = { selectedPositionForDialog = position },
                        colors = ButtonDefaults.buttonColors(backgroundColor = color),
                        modifier = Modifier
                            .padding(horizontal = 2.dp, vertical = 1.dp)
                            .fillMaxWidth()
                    ) {
                        Text(position, color = Color.Black)
                    }
                }
            }


        }

            // Filter-Button rechts unten
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Button(onClick = { showFilterDialog = true }) {
                Text("Filter")
            }
        }

        // Bezeichnungsauswahl als Dialog
        if (showFilterDialog) {
            Dialog(onDismissRequest = { showFilterDialog = false }) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colors.surface,
                    elevation = 8.dp
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
                            allBezeichnungen = allBezeichnungen,
                            selectedBezeichnungen = selectedBezeichnungen,
                            onSelectionChange = {
                                selectedBezeichnungenState.value = it
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




        // Positions-Dialog
        if (selectedPositionForDialog != null) {
            Dialog(onDismissRequest = { selectedPositionForDialog = null }) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colors.background,
                    elevation = 8.dp
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


@Composable
fun MaterialBox(
    material: Material,
    color: Color,
    onClick: (Material) -> Unit
) {
    val cleanedSerial = material.seriennummer?.trimEnd() ?: ""

    // Sonderregel: wenn "BüP" oder "HFE" in der Seriennummer steckt,
    // dann die ersten drei Zeichen anzeigen, sonst wie gehabt die letzten 6
    val displaySerial = when {
        cleanedSerial.contains("BüP") || cleanedSerial.contains("HFE") ->
            cleanedSerial.take(3)
        cleanedSerial.length > 6 ->
            cleanedSerial.takeLast(6)
        else ->
            cleanedSerial
    }

    // ✨ Flash-Animation bei Neuerscheinung
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
        allBezeichnungen.forEach { bezeichnung ->
            val isSelected = selectedBezeichnungen.contains(bezeichnung)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 1.dp, vertical = 2.dp)
            ) {
                Text(
                    text = bezeichnung,
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
                            if (!isSelected) selectedBezeichnungen + bezeichnung
                            else selectedBezeichnungen - bezeichnung
                        )
                    }
                )
            }
        }
    }
}


fun generatePositionColors(positions: List<String>): Map<String, Color> {
   val config = APPConfig

    val customColors = config.customColors
    val colors = config.colors

    val lowerCustomColors = customColors.mapKeys { it.key.lowercase() }

    return positions.mapIndexed { index, pos ->
        val key = pos.lowercase()
        val color = lowerCustomColors[key] ?: colors[index % colors.size]
        pos to color
    }.toMap()
}

