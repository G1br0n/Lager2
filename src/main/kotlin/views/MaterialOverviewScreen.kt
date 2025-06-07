package views

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import config.APPConfig
import models.Material
import viewModels.MaterialViewModel
import kotlin.math.abs
import kotlin.random.Random

/* ------------------------------------------------------------------
   Konfigurierbare Konstanten
------------------------------------------------------------------- */
private val CARD_HEIGHT                    = 154.dp
private val POSITION_BUTTON_HEIGHT         = 28.dp   // kleiner
private val POSITION_BUTTON_CORNER_RADIUS  = 4.dp
private val CARD_CORNER_RADIUS             = 12.dp
private val CARD_BORDER_WIDTH              = 1.dp
private val BUTTON_BORDER_WIDTH            = 1.dp

@Composable
fun MaterialOverviewScreen(
    viewModel: MaterialViewModel,
    onPositionClick: (String) -> Unit = {}
) {
    val materials by remember { derivedStateOf { viewModel.materials.toList() } }

    val groups by remember(materials) {
        mutableStateOf(materials.groupBy { it.bezeichnung ?: "Unbekannt" })
    }

    val orderedKeys by remember(groups) {
        derivedStateOf {
            val predefined = APPConfig.bezeichnungsReihenfolge.filter { groups.containsKey(it) }
            val others = (groups.keys - APPConfig.bezeichnungsReihenfolge).sorted()
            predefined + others
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement    = Arrangement.spacedBy(8.dp)
    ) {
        items(orderedKeys) { key ->
            groups[key]?.let { mats ->
                MaterialCard(
                    title           = key,
                    materials       = mats,
                    onPositionClick = onPositionClick
                )
            }
        }
    }
}

/* ------------------------------------------------------------------
   Material-Karte
------------------------------------------------------------------- */
@Composable
fun MaterialCard(
    title: String,
    materials: List<Material>,
    onPositionClick: (String) -> Unit = {}
) {
    val total      = materials.size
    val inLager    = materials.count { it.inLager }
    val outOfLager = total - inLager

    /* -------- Hintergrundfarbe je nach Bestand -------- */
    val cardBgColor = if (inLager == 0)
        Color(0xFFFF8A80)           // leicht rötlich   (Material-3 “Red 100”)
    else
        MaterialTheme.colorScheme.surface   // Standard

    val posCounts = remember(materials) {
        materials.filterNot { it.inLager }
            .groupingBy { it.position ?: "Unbekannt" }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(CARD_HEIGHT),
        shape  = RoundedCornerShape(CARD_CORNER_RADIUS),
        border = BorderStroke(CARD_BORDER_WIDTH, Color.Black),

        /* -------- NEU: Farben setzen -------- */
        colors = CardDefaults.cardColors(containerColor = cardBgColor)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            /* Header */
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text       = title,
                    color      = Color.White,
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign  = TextAlign.Center,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
            }

            /* Body */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                /* Drei Zahlen gleichmäßig über die Höhe verteilt */
                Column(
                    modifier = Modifier
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.Start
                ) {
                    // Geräte im Lager – grün, außer wenn 0 → rot
                    Text(
                        text      = "$inLager",
                        fontSize  = 22.sp,
                        color     = if (inLager > 0) Color(0xFF4CAF50) else Color.Red,
                        textAlign = TextAlign.Start
                    )
                    // Nicht im Lager – blau
                    Text(
                        text      = "$outOfLager",
                        fontSize  = 22.sp,
                        color     = Color(0xFF2196F3),
                        textAlign = TextAlign.Start
                    )
                    // Gesamt – schwarz
                    Text(
                        text      = "$total",
                        fontSize  = 22.sp,
                        color     = Color.Black,
                        textAlign = TextAlign.Start
                    )
                }

                if (posCounts.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        items(posCounts) { (pos, qty) ->
                            PositionButton(
                                pos     = pos,
                                qty     = qty,
                                onClick = { onPositionClick(pos) }
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------
   Position-Button – nutzt vollen Platz, leicht abgerundet, mit Rahmen
------------------------------------------------------------------- */
@Composable
private fun PositionButton(
    pos: String,
    qty: Int,
    onClick: () -> Unit
) {
    val color = remember(pos) {
        APPConfig.customColors[pos.lowercase()] ?: APPConfig.colors.getOrNull(abs(pos.hashCode()) % APPConfig.colors.size) ?: APPConfig.colors.random(Random(abs(pos.hashCode())))
    }

    Button(
        onClick = onClick,
        shape   = RoundedCornerShape(POSITION_BUTTON_CORNER_RADIUS),
        colors  = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor   = contentColorFor(color)
        ),
        border = BorderStroke(BUTTON_BORDER_WIDTH, Color.Black),
        modifier = Modifier
            .fillMaxWidth()
            .height(POSITION_BUTTON_HEIGHT),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text       = pos,
                fontSize   = 12.sp,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
                modifier   = Modifier.weight(1f)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text     = "– $qty",
                fontSize = 12.sp
            )
        }
    }
}
