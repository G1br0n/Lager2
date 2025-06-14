package views

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.window.Dialog
import config.APPConfig
import kotlinx.coroutines.delay
import models.Material
import viewModels.MaterialViewModel
import kotlin.math.abs
import kotlin.random.Random

/* ------------------------------------------------------------------
   Konfigurierbare Konstanten
------------------------------------------------------------------- */
private val CARD_HEIGHT                   = 240.dp
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

    /* 1) Gruppierung nach Bezeichnung ------------------------------------ */
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

    /* 2) Positions-Aggregat --------------------------------------------- */
    val positionMap by remember(materials) {
        derivedStateOf {
            materials.filterNot { it.inLager }
                .groupBy { it.position ?: "Unbekannt" }
        }
    }
    val usageMap = remember { viewModel.getPositionLastUsedMap() }

    /* 3) Layout: Grid + feste Zusatz-Spalte ----------------------------- */
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        /* --- Linke Seite: 6-Spalten-Grid -------------------------------- */
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            modifier = Modifier
                .weight(1f)                 // nimmt den ganzen übrigen Platz
                .fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement   = Arrangement.spacedBy(8.dp)
        ) {
            // Material-Cards
            items(orderedKeys) { key ->
                groups[key]?.let { mats ->
                    MaterialCard(
                        viewModel       = viewModel,
                        title           = key,
                        materials       = mats,
                        usageMap        = usageMap,
                        onPositionClick = onPositionClick
                    )
                }
            }
        }

        /* --- Rechte Seite: feste Spalte für die Positions-Card ----------- */
        Column(modifier = Modifier.width(200.dp)) {
            PositionOverviewCard(
                viewModel   = viewModel,
                positionMap = positionMap,
                usageMap    = usageMap,
            )
        }

    }
}


/* ------------------------------------------------------------------
   Positions‑Übersichts‑Karte
------------------------------------------------------------------- */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PositionOverviewCard(
    viewModel: MaterialViewModel,
    positionMap: Map<String, List<Material>>, // Position → Materialien
    usageMap: Map<String, Long>
) {
    val totalPositions = positionMap.size

    /* --------------------------------------------------------------
       Sortieren: zuerst nach "zuletzt benutzt", dann alphabetisch.
    -------------------------------------------------------------- */
    val sortedPositions by remember(positionMap, usageMap) {
        derivedStateOf {
            positionMap.keys.sortedWith(
                compareByDescending<String> { usageMap[it] ?: 0L }
                    .thenBy { it }
            )
        }
    }

    /* --------------------------------------------------------------
       State für Dialog‑Kette
    -------------------------------------------------------------- */
    var selectedPosition by remember { mutableStateOf<String?>(null) }
    var selectedBezeichnung by remember { mutableStateOf<String?>(null) }

    /* --------------------------------------------------------------
       Karte selbst
    -------------------------------------------------------------- */
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        shape  = RoundedCornerShape(CARD_CORNER_RADIUS),
        border = BorderStroke(CARD_BORDER_WIDTH, Color.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .padding(8.dp)
            ) {
                Text(
                    text       = "Positionen ($totalPositions)",
                    color      = Color.White,
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier.align(Alignment.CenterStart)
                )
            }

            // Positionsliste
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(sortedPositions) { pos ->
                    val qty = positionMap[pos]?.size ?: 0
                    PositionButton(
                        pos      = pos,
                        qty      = qty,
                        onClick  = { selectedPosition = pos },
                    )
                }
            }
        }
    }

    /* --------------------------------------------------------------
       Dialog 1 – Bezeichnungen an gewählter Position
    -------------------------------------------------------------- */
    if (selectedPosition != null) {
        val pos = selectedPosition!!
        val matForPos = positionMap[pos] ?: emptyList()
        val bezeichCounts = remember(matForPos) {
            matForPos.groupingBy { it.bezeichnung ?: "?" }.eachCount()
                .toList()
                .sortedByDescending { it.second }
        }

        Dialog(onDismissRequest = { selectedPosition = null }) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f)
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // --- Titelzeile ---
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                APPConfig.customColors[pos.lowercase()] ?: APPConfig.colors.getOrNull(
                                    abs(pos.hashCode()) % APPConfig.colors.size
                                ) ?: Color.Gray
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            text       = "Position: $pos",
                            color      = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 20.sp,
                            modifier   = Modifier.align(Alignment.Center)
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // --- Liste der Bezeichnungen ---
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(bezeichCounts) { (bez, qty) ->
                            Button(
                                onClick = { selectedBezeichnung = bez },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor   = contentColorFor(MaterialTheme.colorScheme.primary)
                                ),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, Color.Black),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text       = bez,
                                        fontSize   = 15.sp,
                                        maxLines   = 1,
                                        overflow   = TextOverflow.Ellipsis,
                                        modifier   = Modifier.weight(1f)
                                    )
                                    Text(text = "– $qty", fontSize = 15.sp)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = { selectedPosition = null },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Schließen") }
                }
            }
        }
    }

    /* --------------------------------------------------------------
       Dialog 2 – Geräte (Materialien) der gewählten Bezeichnung
    -------------------------------------------------------------- */
    if (selectedPosition != null && selectedBezeichnung != null) {
        val pos = selectedPosition!!
        val bez = selectedBezeichnung!!
        val mats = positionMap[pos].orEmpty()
            .filter { (it.bezeichnung ?: "?") == bez }
            .sortedBy { it.seriennummer ?: "" }

        Dialog(onDismissRequest = { selectedBezeichnung = null }) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f)
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "$bez – Position $pos",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(mats) { index, mat ->
                            Button(
                                onClick = {
                                    viewModel.loadLogsForMaterial(mat)
                                    // Öffnet später Log‑Dialog
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Black,
                                    contentColor   = Color.White
                                ),
                                shape  = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, Color.Black),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text(
                                    text       = "${index + 1}. SN: ${mat.seriennummer ?: "-"}",
                                    fontSize   = 15.sp,
                                    maxLines   = 1,
                                    overflow   = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { selectedBezeichnung = null },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Schließen") }
                }
            }
        }
    }

    /* --------------------------------------------------------------
       Dialog 3 – Logs des selektierten Geräts (wird von ViewModel
       gesteuert). Dieser Teil entspricht exakt dem bestehenden
       Log‑Dialog in MaterialCard und wird hier nur eingeblendet,
       wenn das ViewModel ein selektiertes Material enthält.
    -------------------------------------------------------------- */
    if (viewModel.selectedMaterial != null) {
        Dialog(onDismissRequest = { viewModel.clearSelection() }) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Verlauf – ${viewModel.selectedMaterial?.bezeichnung ?: "?"}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(viewModel.selectedLogs.asReversed()) { log ->
                            Text(
                                text = "${log.timestamp?.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))} – ${log.event}",
                                fontSize = 14.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.clearSelection() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Schließen") }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------
   Wiederverwendbares Innenleben
   Lagerzahlen jetzt im Header: Bezeichnung links, Zahlen rechts
------------------------------------------------------------------- */
@Composable
private fun MaterialCardBody(
    title: String,
    total: Int,
    inLager: Int,
    outOfLager: Int,
    posCounts: List<Pair<String, Int>>,
    onPositionClick: (String) -> Unit,
    headerColor: Color,

    highlightPositions: Set<String> = emptySet(),
    flashOn: Boolean = false
) {
    Column(modifier = Modifier.fillMaxSize()) {

        /* ----------------------------------------------------------
           Header – Titel links, Lagerzahlen rechts
        ---------------------------------------------------------- */
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerColor)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text       = title,
                color      = Color.White,
                fontSize   = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
                modifier   = Modifier.weight(1f)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Lagerzahl(inLager, if (inLager > 0) Color.White else Color.Red)
                Spacer(Modifier.width(10.dp))
                //Lagerzahl(outOfLager, Color(0xFF2196F3))
                //Lagerzahl(total,      Color.White)
            }
        }

        /* ----------------------------------------------------------
           Positionsliste
        ---------------------------------------------------------- */
        if (posCounts.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(posCounts) { (pos, qty) ->
                    PositionButton(
                        pos      = pos,
                        qty      = qty,
                        flashing = highlightPositions.contains(pos),
                        flashOn  = flashOn,
                        onClick  = { onPositionClick(pos) }
                    )
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
    viewModel: MaterialViewModel,
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

    var selectedPosition by remember { mutableStateOf<String?>(null) }
    var showPositionDialog by remember { mutableStateOf(false) }

    var showLogsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(materials) {
        val currentPosCounts = materials
            .filterNot { it.inLager }
            .groupingBy { it.position ?: "Unbekannt" }
            .eachCount()

        val qtyChangedPositions = (currentPosCounts.keys + prevPosCounts.keys).filter { pos ->
            prevPosCounts[pos] != currentPosCounts[pos]
        }.toSet()

        val diffInLager = inLager - prevInLager

        val prevPositions = prevMaterials.mapNotNull { it.position }
        val currentPositions = materials.mapNotNull { it.position }

        val positionChanged = prevPositions != currentPositions

        headerHighlightColor = when {
            diffInLager > 0  -> Color(0xFFC8E6C9)
            diffInLager < 0  -> Color(0xFFFFCDD2)
            positionChanged  -> Color(0xFFFFF59D)
            else             -> null
        }

        if (headerHighlightColor != null) {
            cardHighlightColor = Color(0xFF64B5F6)
        }

        prevPosCounts = currentPosCounts
        prevMaterials = materials
        prevInLager   = inLager

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
            title              = title,
            total              = total,
            inLager            = inLager,
            outOfLager         = outOfLager,
            posCounts          = posCounts,
            onPositionClick    = {
                selectedPosition = it
                showPositionDialog = true
            },
            headerColor        = animatedHeaderColor,
            highlightPositions = flashingPositions,
            flashOn            = flashOn
        )
    }

    // === Dialog 1: Haupt-Dialog ===
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
                        title              = title,
                        total              = total,
                        inLager            = inLager,
                        outOfLager         = outOfLager,
                        posCounts          = posCounts,
                        onPositionClick    = {
                            selectedPosition = it
                            showPositionDialog = true
                        },
                        headerColor        = animatedHeaderColor,
                        highlightPositions = flashingPositions,
                        flashOn            = flashOn
                    )
                }
            }
        )
    }

    // === Dialog 2: Materialien pro Position ===
    if (showPositionDialog && selectedPosition != null) {
        val pos = selectedPosition!!
        val materialsForPosition = materials
            .filter { it.position == pos }
            .sortedBy { it.bezeichnung ?: "" }

        val buttonColor = APPConfig.customColors[pos.lowercase()]
            ?: APPConfig.colors.getOrNull(abs(pos.hashCode()) % APPConfig.colors.size)
            ?: APPConfig.colors.random(Random(abs(pos.hashCode())))

        Dialog(onDismissRequest = {
            selectedPosition = null
            showPositionDialog = false
        }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(buttonColor)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Position: $pos",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(materialsForPosition) { index, mat ->
                            Button(
                                onClick = {
                                    viewModel.loadLogsForMaterial(mat)
                                    showLogsDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Black,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, Color.Black),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text(
                                    text = "${index + 1}. ${mat.bezeichnung ?: "?"} – ${mat.seriennummer ?: "-"}",
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = {
                            selectedPosition = null
                            showPositionDialog = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Schließen")
                    }
                }
            }
        }
    }

    // === Dialog 3: Logs ===
    if (showLogsDialog && viewModel.selectedMaterial != null) {
        Dialog(onDismissRequest = {
            showLogsDialog = false
            viewModel.clearSelection()
        }) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Verlauf – ${viewModel.selectedMaterial?.bezeichnung ?: "?"}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(viewModel.selectedLogs.asReversed()) { log ->
                            Text(
                                text = "${log.timestamp?.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))} – ${log.event}",
                                fontSize = 14.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            showLogsDialog = false
                            viewModel.clearSelection()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Schließen")
                    }
                }
            }
        }
    }
}


/* ------------------------------------------------------------------
   Position-Button
------------------------------------------------------------------- */
@Composable
private fun PositionButton(
    pos: String,
    qty: Int,
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
