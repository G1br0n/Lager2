package views


import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import models.Material
import viewModels.MaterialViewModel

@Composable
fun MonitorView(
    viewModel: MaterialViewModel,
    onMaterialSelected: (Material) -> Unit
) {
    val ausgegebeneMaterials = viewModel.materials.filter { !it.inLager }
    val groupedByBezeichnung = viewModel.materials
        .groupBy { it.bezeichnung ?: "Unbekannt" }
        .toSortedMap()

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
            // ⛔ BezeichnungCheckboxList entfernt aus MainView!

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
                            .width(200.dp)
                            .border(1.dp, Color.LightGray),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        item {
                            Text(bezeichnung, style = MaterialTheme.typography.h5)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("$totalCount", color = darkGreen, style = MaterialTheme.typography.h5)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("$inLagerCount", color = blue, style = MaterialTheme.typography.h5)
                                Text(" / ", style = MaterialTheme.typography.h5)
                                Text("$notInLagerCount", color = red, style = MaterialTheme.typography.h5)
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
                                        .background(positionColor.copy(alpha = 0.3f), shape = MaterialTheme.shapes.small),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        position,
                                        modifier = Modifier.padding(2.dp),
                                        style = MaterialTheme.typography.subtitle2
                                    )
                                }
                                Spacer(modifier = Modifier.height(1.dp))
                            }

                            items(posMaterials) { material ->
                                val color = positionColors[material.position] ?: Color.LightGray
                                MaterialBox(material, color, onMaterialSelected)
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
            val longestPositionText = allPositions.maxByOrNull { it.length } ?: ""
            val textLengthInDp = longestPositionText.length * 11
            val buttonWidth = textLengthInDp.dp + 32.dp

            Column(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Positionen", style = MaterialTheme.typography.h5)
                Spacer(modifier = Modifier.height(4.dp))
                allPositions.forEach { position ->
                    val color = positionColors[position] ?: Color.LightGray
                    Button(
                        onClick = { selectedPositionForDialog = position },
                        colors = ButtonDefaults.buttonColors(backgroundColor = color),
                        modifier = Modifier
                            .padding(horizontal = 2.dp, vertical = 1.dp)
                            .width(buttonWidth)
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
fun MaterialBox(material: Material, color: Color, onClick: (Material) -> Unit) {
    val cleanedSerial = material.seriennummer?.trimEnd() ?: ""
    val displaySerial = if (cleanedSerial.length > 6) cleanedSerial.takeLast(6) else cleanedSerial

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .padding(horizontal = 3.dp, vertical = 2.dp)
            .clickable { onClick(material) }
            .background(color, shape = MaterialTheme.shapes.medium)
            .border(2.dp, Color.Gray, shape = MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center
    ) {
        Text(displaySerial, style = MaterialTheme.typography.h5)
    }

}

fun generatePositionColors(positions: List<String>): Map<String, Color> {
    val colors = listOf(
        Color(0xFFB39DDB), Color(0xFFFFF176), Color(0xFFFFAB91), Color(0xFFAED581),
        Color(0xFFFF8A65), Color(0xFF81D4FA), Color(0xFFFFECB3), Color(0xFFCE93D8),
        Color(0xFFF8BBD0), Color(0xFFBBDEFB), Color(0xFFFFF59D), Color(0xFFE1BEE7),
        Color(0xFFFFCCBC), Color(0xFFA5D6A7), Color(0xFFB2EBF2), Color(0xFFFFE082),
        Color(0xFF4DD0E1), Color(0xFFB2DFDB), Color(0xFFD1C4E9), Color(0xFFF0F4C3),
        Color(0xFF80DEEA), Color(0xFFFFCDD2), Color(0xFFD7CCC8), Color(0xFF4DB6AC),
        Color(0xFFDCEDC8), Color(0xFFB388FF), Color(0xFFFFA726), Color(0xFFC8E6C9),
        Color(0xFFC5CAE9), Color(0xFFFFCC80), Color(0xFF81C784), Color(0xFFB3E5FC),
        Color(0xFFF0F4C3), Color(0xFFFFE0B2), Color(0xFFF8BBD0), Color(0xFFE1BEE7)
    ).shuffled()

    val specialRed = Color(0xFFD32F2F) // Klarer Rotton für spezielle Positionen

    return positions.mapIndexed { index, pos ->
        val lower = pos.lowercase()
        val color = if (lower == "zöllner" || lower == "reparatur") {
            specialRed
        } else {
            colors[index % colors.size]
        }
        pos to color
    }.toMap()
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
                CooldownCheckbox(
                    checked = isSelected,
                    onCheckedChange = { checked ->
                        onSelectionChange(
                            if (checked) selectedBezeichnungen + bezeichnung
                            else selectedBezeichnungen - bezeichnung
                        )
                    }
                )

            }
        }
    }
}


@Composable
fun CooldownCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    cooldownMillis: Long = 2000
) {
    var enabled by remember { mutableStateOf(true) }
    var lastToggleTime by remember { mutableStateOf(0L) }

    Checkbox(
        checked = checked,
        onCheckedChange = {
            if (enabled) {
                onCheckedChange(it)
                lastToggleTime = System.currentTimeMillis()
                enabled = false
            }
        },
        enabled = enabled,
        modifier = modifier,
        colors = CheckboxDefaults.colors(checkedColor = Color.Black)
    )

    // Cooldown re-aktivieren
    LaunchedEffect(lastToggleTime) {
        if (!enabled) {
            delay(cooldownMillis)
            enabled = true
        }
    }
}
