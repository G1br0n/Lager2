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
import androidx.compose.ui.unit.dp
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

    val allPositions = ausgegebeneMaterials.mapNotNull { it.position }.distinct().sorted()
    val positionColors = remember(allPositions) { generatePositionColors(allPositions) }

    val darkGreen = Color(0xFF2E7D32)
    val red = Color(0xFFD32F2F)
    val blue = Color(0xFF1976D2)

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp)
    ) {
        LazyRow(modifier = Modifier.weight(1f)) {
            val bezeichnungList = groupedByBezeichnung.toList()
            items(bezeichnungList.size) { columnIndex ->
                val (bezeichnung, materials) = bezeichnungList[columnIndex]

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
                        Text(
                            text = bezeichnung,
                            style = MaterialTheme.typography.h5,
                            modifier = Modifier.align(Alignment.Top)
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text("$totalCount", color = darkGreen, style = MaterialTheme.typography.h5)

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("$inLagerCount", color = blue, style = MaterialTheme.typography.h5)
                            Text(" / ", style = MaterialTheme.typography.h5)
                            Text("$notInLagerCount", color = red, style = MaterialTheme.typography.h5)
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    val groupedByPosition = materials.filter { !it.inLager }.groupBy { it.position ?: "Unbekannt" }
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

                if (columnIndex < bezeichnungList.size - 1) {
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

        // Rechte Zusatzspalte: Positionen als Buttons mit Farben
        Column(
            modifier = Modifier
                .padding(start = 2.dp, top = 4.dp, bottom = 4.dp, end = 2.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Positionen", style = MaterialTheme.typography.h5)
            Spacer(modifier = Modifier.height(4.dp))
            allPositions.forEach { position ->
                val color = positionColors[position] ?: Color.LightGray
                Button(
                    onClick = {},
                    colors = ButtonDefaults.buttonColors(backgroundColor = color),
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp)
                ) {
                    Text(position, color = Color.Black)
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
            .width(140.dp)
            .height(50.dp)
            .padding(vertical = 2.dp)
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
