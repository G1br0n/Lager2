// main.kt

// ----------------------------
// Importe für Compose Desktop, JLayer, SQLite und weitere benötigte Klassen
// ----------------------------
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import javazoom.jl.player.advanced.AdvancedPlayer
import models.Material
import repositorys.SQLiteMaterialRepository
import viewModels.MaterialViewModel
import views.*
import java.sql.Connection
import java.sql.DriverManager
import java.time.LocalDateTime
import java.util.UUID









@OptIn(ExperimentalComposeUiApi::class)
@Composable
@Preview
fun App(viewModel: MaterialViewModel) {

     val GrayColorPalette = lightColors(
        primary = Color(0xFF616161),
        primaryVariant = Color(0xFF424242),
        secondary = Color(0xFF9E9E9E),
        background = Color(0xFFE0E0E0),
        surface = Color(0xFFBDBDBD),
        onPrimary = Color.White,
        onSecondary = Color.Black,
        onBackground = Color.Black,
        onSurface = Color.Black,
    )

    var showNewMaterialDialog by remember { mutableStateOf(false) }
    var showDetailDialog by remember { mutableStateOf(false) }
    var selectedMaterial by remember { mutableStateOf<Material?>(null) }
    var showMissingNameDialog by remember { mutableStateOf(false) }

    MaterialTheme(colors = GrayColorPalette) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            ToolbarView(
                viewModel = viewModel,
                onNewMaterialClick = { showNewMaterialDialog = true }
            )
            Spacer(modifier = Modifier.height(16.dp))
            MaterialListView(viewModel = viewModel) { material ->
                selectedMaterial = material
                showDetailDialog = true
            }
        }

        if (showNewMaterialDialog) {
            NewMaterialDialog(
                onDismiss = { showNewMaterialDialog = false },
                onSave = { newMat -> viewModel.addNewMaterial(newMat) },
                onCheckSerialExists = { serial ->
                    viewModel.materials.any { it.seriennummer?.trim() == serial.trim() }
                },
                playErrorTone = { viewModel.playErrorTone() }
            )
        }

        if (showDetailDialog && selectedMaterial != null) {
            DialogWindow(
                onCloseRequest = { showDetailDialog = false },
                title = "Materialdetails",
                state = rememberDialogState(width = 1200.dp, height = 450.dp),
                resizable = true,
                alwaysOnTop = true
            ) {
                DetailDialog(
                    material = selectedMaterial!!,
                    onDismiss = { showDetailDialog = false },
                    onSave = { updated ->
                        viewModel.updateMaterial(updated)
                        showDetailDialog = false
                    }
                )
            }
        }


        if (showMissingNameDialog) {
            MissingNameDialog(onDismiss = { showMissingNameDialog = false })
        }
    }
}


/**
 * In der main-Funktion wird nun ein einziger Repository- und ViewModel-Instanz erzeugt,
 * die in beiden Fenstern (Lagerverwaltung und Monitor) geteilt werden.
 */
fun main() = application {
    val repository = SQLiteMaterialRepository()
    val viewModel = MaterialViewModel(repository)
    var selectedMaterialForMonitor by remember { mutableStateOf<Material?>(null) }

    Window(onCloseRequest = ::exitApplication, title = "Lagerverwaltung (MVVM & Grautöne)") {
        App(viewModel)
    }

    Window(onCloseRequest = {}, title = "Material Monitor") {
        MonitorView(viewModel) { selected ->
            selectedMaterialForMonitor = selected
        }
    }

    if (selectedMaterialForMonitor != null) {
        DialogWindow(
            onCloseRequest = { selectedMaterialForMonitor = null },
            title = "Materialdetails",
            state = rememberDialogState(width = 1200.dp, height = 450.dp),
            resizable = true,
            alwaysOnTop = true // <- Damit es im Vordergrund bleibt
        ) {
            DetailDialog(
                material = selectedMaterialForMonitor!!,
                onDismiss = { selectedMaterialForMonitor = null },
                onSave = { updated ->
                    viewModel.updateMaterial(updated)
                    selectedMaterialForMonitor = null
                }
            )
        }
    }



}
