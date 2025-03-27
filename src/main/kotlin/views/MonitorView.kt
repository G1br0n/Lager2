package views



import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import models.Material
import viewModels.MaterialViewModel

/**
 * MonitorView zeigt die Materialien gruppiert nach Bezeichnung in Spalten.
 * Pro Spalte werden maximal 10 Einträge angezeigt, wobei Materialien im Lager
 * zuerst angezeigt werden.
 */
@Composable
fun MonitorView(
    viewModel: MaterialViewModel,
    onMaterialSelected: (Material) -> Unit
) {
    // Gruppiere Materialien nach Bezeichnung und sortiere alphabetisch
    val groupedMaterials = viewModel.materials.groupBy { it.bezeichnung ?: "Unbekannt" }
        .toSortedMap()
    val columns = groupedMaterials.toList()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(rememberScrollState())
            .padding(8.dp)
    ) {
        columns.forEachIndexed { index, (bezeichnung, materials) ->
            Column(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Spaltenüberschrift in größerer Schrift
                Text(text = bezeichnung, style = MaterialTheme.typography.h4)

                // Zusammenfassungszeile: Links grün (im Lager), rechts rot (nicht im Lager)
                val countInLager = materials.count { it.inLager }
                val countNotInLager = materials.count { !it.inLager }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "$countInLager",
                        color = Color.Blue,
                        style = MaterialTheme.typography.h4
                    )
                    Text(
                        text = " / ",
                        style = MaterialTheme.typography.h4
                    )
                    Text(
                        text = "$countNotInLager",
                        color = Color.Red,
                        style = MaterialTheme.typography.h4
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Untere Übersicht: Aufteilung in zwei Gruppen – grüne (im Lager) und rote (nicht im Lager)
                val greenMaterials = materials.filter { it.inLager }
                val redMaterials = materials.filter { !it.inLager }

                Column {
                    // Anzeige der grünen Materialien
                   /* greenMaterials.forEachIndexed { idx, material ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(Color(0xFFB9F6CA))
                                .clickable { onMaterialSelected(material) },
                            contentAlignment = Alignment.Center
                        ) {
                            val cleanedSerial = material.seriennummer?.trimEnd() ?: ""
                            val displaySerial = if (cleanedSerial.length > 10) cleanedSerial.takeLast(10) else cleanedSerial
                            Text(
                                text = displaySerial,
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.h5
                            )
                        }
                        if (idx < greenMaterials.size - 1) {
                            Divider(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color.Gray,
                                thickness = 1.dp
                            )
                        }
                    }*/

                    // Trennlinie zwischen den Gruppen nur anzeigen, wenn beide Gruppen vorhanden sind
                    if (greenMaterials.isNotEmpty() && redMaterials.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(Color.Black)
                        )
                    }


                    // Anzeige der roten Materialien
                    redMaterials.forEachIndexed { idx, material ->
                        Box(
                            modifier = Modifier
                                .width(140.dp) // feste Breite für gleiche Größe
                                .height(50.dp) // feste Höhe
                                .padding(vertical = 4.dp)
                                .clickable { onMaterialSelected(material) }
                                .background(Color(0xFFFFCDD2), shape = MaterialTheme.shapes.medium)
                                .border(2.dp, Color.Gray, shape = MaterialTheme.shapes.medium),
                            contentAlignment = Alignment.Center
                        ) {
                            val cleanedSerial = material.seriennummer?.trimEnd() ?: ""
                            val displaySerial = if (cleanedSerial.length > 6) cleanedSerial.takeLast(6) else cleanedSerial
                            Text(
                                text = displaySerial,
                                style = MaterialTheme.typography.h5
                            )
                        }



                    }
                }
            }
            // Vertikaler Divider zwischen den Spalten
            if (index < columns.size - 1) {
                Divider(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(2.dp),
                    color = Color.Gray
                )
            }
        }
    }
}