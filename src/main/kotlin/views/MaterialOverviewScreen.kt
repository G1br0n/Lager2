package views

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import kotlinx.coroutines.delay
import models.Material
import viewModels.MaterialViewModel
import kotlin.math.abs
import kotlin.random.Random

/* ------------------------------------------------------------------
   Konfigurierbare Konstanten
------------------------------------------------------------------- */
private val CARD_HEIGHT                   = 154.dp
private val POSITION_BUTTON_HEIGHT        = 28.dp
private val POSITION_BUTTON_CORNER_RADIUS = 4.dp
private val CARD_CORNER_RADIUS            = 12.dp
private val CARD_BORDER_WIDTH             = 1.dp
private val BUTTON_BORDER_WIDTH           = 1.dp

/* ------------------------------------------------------------------
   Übersicht
------------------------------------------------------------------- */
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
            val others     = (groups.keys - APPConfig.bezeichnungsReihenfolge).sorted()
            predefined + others
        }
    }

    val usageMap = remember { viewModel.getPositionLastUsedMap() }

    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement   = Arrangement.spacedBy(8.dp)
    ) {
        items(orderedKeys) { key ->
            groups[key]?.let { mats ->
                MaterialCard(
                    title           = key,
                    materials       = mats,
                    usageMap        = usageMap,
                    onPositionClick = onPositionClick
                )
            }
        }
    }
}


/* ------------------------------------------------------------------
   Wiederverwendbares Innenleben
   horizontalNumbers = true  → Zahlen nebeneinander (Dialog)
   horizontalNumbers = false → Zahlen untereinander  (Karte)
------------------------------------------------------------------- */
@Composable
private fun MaterialCardBody(
    title: String,
    total: Int,
    inLager: Int,
    outOfLager: Int,
    posCounts: List<Pair<String, Int>>,
    horizontalNumbers: Boolean,
    onPositionClick: (String) -> Unit,
    headerColor: Color,
    /* --- NEU --- */
    highlightPositions: Set<String> = emptySet(),
    flashOn: Boolean = false
) {
    Column(modifier = Modifier.fillMaxSize()) {

        /* Header */
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerColor)
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

        /* ----------------------------------------------------------
           (1) Lagerzahlen – abhängig von horizontalNumbers
        ---------------------------------------------------------- */
        if (horizontalNumbers) {
            /* ➜ Dialog: Zahlen als eine Zeile, gleichmäßig verteilt  */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Lagerzahl(inLager, if (inLager > 0) Color(0xFF4CAF50) else Color.Red)
                Lagerzahl(outOfLager, Color(0xFF2196F3))
                Lagerzahl(total,      Color.Black)
            }
        }

        /* ----------------------------------------------------------
           (2) Haupt-Row – enthält Zahlen (falls vertikal) + Buttons
        ---------------------------------------------------------- */
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (!horizontalNumbers) {
                /* ➜ Karte: Zahlen untereinander links */
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.Start
                ) {
                    Lagerzahl(inLager, if (inLager > 0) Color(0xFF4CAF50) else Color.Red)
                    Lagerzahl(outOfLager, Color(0xFF2196F3))
                    Lagerzahl(total,      Color.Black)
                }
            }

            if (posCounts.isNotEmpty()) {
                /* Buttons-Liste */
                if (!horizontalNumbers) Spacer(Modifier.width(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    items(posCounts) { (pos, qty) ->
                        PositionButton(
                            pos      = pos,
                            qty      = qty,
                            flashing = highlightPositions.contains(pos),
                            flashOn  = flashOn,
                            onClick  = { onPositionClick(pos) }
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

/* Ein einzelner Lagerwert als Text */
@Composable
private fun Lagerzahl(value: Int, color: Color) {
    Text(
        text      = "$value",
        fontSize  = 22.sp,
        color     = color,
        textAlign = TextAlign.Center
    )
}

/* ------------------------------------------------------------------
   Material-Karte (klickbar)
------------------------------------------------------------------- */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialCard(
    title: String,
    materials: List<Material>,
    usageMap: Map<String, Long>,
    onPositionClick: (String) -> Unit = {}
) {
    val total      = materials.size
    val inLager    = materials.count { it.inLager }
    val outOfLager = total - inLager

    var showDialog by remember { mutableStateOf(false) }

    val baseCardColor = if (inLager == 0) Color(0xFFEF9A9A) else MaterialTheme.colorScheme.surface

    var prevMaterials by remember { mutableStateOf(materials) }
    var prevInLager   by remember { mutableStateOf(inLager) }

    var cardHighlightColor   by remember { mutableStateOf<Color?>(null) }
    var headerHighlightColor by remember { mutableStateOf<Color?>(null) }

    var flashingPositions by remember { mutableStateOf<Set<String>>(emptySet()) }
    var flashOn           by remember { mutableStateOf(false) }
    var prevPosCounts     by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    LaunchedEffect(materials) {
        val currentPosCounts = materials
            .filterNot { it.inLager }
            .groupingBy { it.position ?: "Unbekannt" }
            .eachCount()

        val qtyChangedPositions = (currentPosCounts.keys + prevPosCounts.keys).filter { pos ->
            prevPosCounts[pos] != currentPosCounts[pos]
        }.toSet()

        // Vergleiche Bestand
        val diffInLager = inLager - prevInLager

        // Vergleiche Positionen unabhängig vom Bestand
        val prevPositions = prevMaterials.mapNotNull { it.position }
        val currentPositions = materials.mapNotNull { it.position }

        val positionChanged = prevPositions != currentPositions

        // Setze Header-Farbe je nach Änderung
        headerHighlightColor = when {
            diffInLager > 0  -> Color(0xFFC8E6C9) // sanftes Grün (Green 100)
            diffInLager < 0  -> Color(0xFFFFCDD2) // sanftes Rot   (Red 100)
            positionChanged  -> Color(0xFFFFF59D) // sanftes Gelb  (Yellow 100)
            else             -> null
        }

        // Optional: Karte hellblau hervorheben
        if (headerHighlightColor != null) {
            cardHighlightColor = Color(0xFF64B5F6) // Light Blue 300
        }

        prevPosCounts = currentPosCounts
        prevMaterials = materials
        prevInLager   = inLager

        // Flimmern bei Positionsänderung
        if (qtyChangedPositions.isNotEmpty()) {
            flashingPositions = qtyChangedPositions
            repeat(16) {
                flashOn = it % 2 == 0
                delay(250)
            }
            flashOn = false
            flashingPositions = emptySet()
        }

        delay(4000)
        cardHighlightColor   = null
        headerHighlightColor = null
    }


    val targetCardColor     = cardHighlightColor ?: baseCardColor
    val animatedCardColor   by animateColorAsState(targetCardColor, tween(1000))
    val targetHeaderColor   = headerHighlightColor ?: Color.Black
    val animatedHeaderColor by animateColorAsState(targetHeaderColor, tween(1000))

    val posCounts = remember(materials, usageMap) {
        materials.filterNot { it.inLager }
            .groupingBy { it.position ?: "Unbekannt" }
            .eachCount()
            .toList()
            .sortedWith(compareByDescending<Pair<String, Int>> { (pos, _) ->
                usageMap[pos] ?: 0L
            }.thenByDescending { it.second })
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(CARD_HEIGHT),
        onClick = { showDialog = true },
        shape   = RoundedCornerShape(CARD_CORNER_RADIUS),
        border  = BorderStroke(CARD_BORDER_WIDTH, Color.Black),
        colors  = CardDefaults.cardColors(
            containerColor = animatedCardColor,
            contentColor   = contentColorFor(animatedCardColor)
        )
    ) {
        MaterialCardBody(
            title             = title,
            total             = total,
            inLager           = inLager,
            outOfLager        = outOfLager,
            posCounts         = posCounts,
            horizontalNumbers = false,
            onPositionClick   = onPositionClick,
            headerColor       = animatedHeaderColor,
            highlightPositions = flashingPositions,
            flashOn            = flashOn
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text("Schließen") }
            },
            shape          = RoundedCornerShape(CARD_CORNER_RADIUS),
            containerColor = animatedCardColor,
            title          = {},
            text = {
                Box(modifier = Modifier.fillMaxWidth()) {
                    MaterialCardBody(
                        title             = title,
                        total             = total,
                        inLager           = inLager,
                        outOfLager        = outOfLager,
                        posCounts         = posCounts,
                        horizontalNumbers = true,
                        onPositionClick   = onPositionClick,
                        headerColor       = animatedHeaderColor,
                        highlightPositions = flashingPositions,
                        flashOn            = flashOn
                    )
                }
            }
        )
    }
}

/* ------------------------------------------------------------------
   Position-Button
------------------------------------------------------------------- */
@Composable
private fun PositionButton(
    pos: String,
    qty: Int,
    /* --- NEU --- */
    flashing: Boolean = false,
    flashOn: Boolean  = false,
    onClick: () -> Unit
) {
    val baseColor = remember(pos) {
        APPConfig.customColors[pos.lowercase()]
            ?: APPConfig.colors.getOrNull(abs(pos.hashCode()) % APPConfig.colors.size)
            ?: APPConfig.colors.random(Random(abs(pos.hashCode())))
    }

    val containerColor = if (flashing && flashOn) Color.Black else baseColor
    val contentColor   = if (flashing && flashOn) Color.White else contentColorFor(containerColor)

    Button(
        onClick         = onClick,
        shape           = RoundedCornerShape(POSITION_BUTTON_CORNER_RADIUS),
        colors          = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor   = contentColor
        ),
        border          = BorderStroke(BUTTON_BORDER_WIDTH, Color.Black),
        modifier        = Modifier
            .fillMaxWidth()
            .height(POSITION_BUTTON_HEIGHT),
        contentPadding  = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
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
                text = "– $qty",
                fontSize = 12.sp
            )
        }
    }
}
