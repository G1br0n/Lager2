// main.kt

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import models.Material
import repositorys.FirebaseMaterialRepository
import repositorys.MaterialRepository
import viewModels.MaterialViewModel
import views.DetailDialog
import views.MonitorView
import views.MaterialListView
import views.MaterialOverviewScreen
import views.MissingNameDialog
import views.NewMaterialDialog
import views.ToolbarView
import java.util.logging.Level
import java.util.logging.Logger

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

    // ausgewähltes Material aus ViewModel (nur lesen)
    val logs by remember { derivedStateOf { viewModel.selectedLogs } }

    MaterialTheme(colors = GrayColorPalette) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            ToolbarView(
                viewModel = viewModel,
                onNewMaterialClick = { showNewMaterialDialog = true }
            )
            Spacer(modifier = Modifier.height(16.dp))
            MaterialListView(viewModel = viewModel) { material ->
                // Logs laden, dann Dialog anzeigen
                viewModel.loadLogsForMaterial(material)
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
                onCloseRequest = {
                    showDetailDialog = false
                    selectedMaterial = null
                    viewModel.clearSelection()
                },
                title = "Materialdetails",
                state = rememberDialogState(width = 1200.dp, height = 450.dp),
                resizable = true,
                alwaysOnTop = true
            ) {
                DetailDialog(
                    material = selectedMaterial!!,
                    logs = logs,
                    onDismiss = {
                        showDetailDialog = false
                        selectedMaterial = null
                        viewModel.clearSelection()
                    },
                    onSave = {
                        viewModel.updateMaterial(it)
                        showDetailDialog = false
                        selectedMaterial = null
                        viewModel.clearSelection()
                    },
                    onDelete = {
                        viewModel.deleteMaterial(it)
                        showDetailDialog = false
                        selectedMaterial = null
                        viewModel.clearSelection()
                    },
                    readOnly = true
                )
            }
        }

        if (showMissingNameDialog) {
            MissingNameDialog(onDismiss = { showMissingNameDialog = false })
        }
    }
}

fun configurePdfBoxLogging() {
    // Alle PDFBox-Logger auf SEVERE setzen, damit nur Fehler erscheinen
    Logger.getLogger("org.apache.pdfbox").level = Level.SEVERE
    Logger.getLogger("org.apache.pdfbox.pdmodel.font.FileSystemFontProvider").level = Level.SEVERE
}

fun main() = application {
    configurePdfBoxLogging()
    initFirebaseAdmin()
    val repository: MaterialRepository = FirebaseMaterialRepository()
    val viewModel = MaterialViewModel(repository)

    // Zweites Fenster: MonitorView
    var selectedMaterialForMonitor by remember { mutableStateOf<Material?>(null) }
    val monitorLogs by remember { derivedStateOf { viewModel.selectedLogs } }
// Drittfenster: Overview
    var showOverviewWindow by remember { mutableStateOf(true) }   // true = gleich beim Start öffnen

    // 🪟 Hauptfenster
    Window(onCloseRequest = ::exitApplication, title = "Lagerverwaltung") {
        App(viewModel)
    }

    // 🖥️ Zweitfenster: Monitor

// 📊 Drittfenster: Material-Übersicht
    if (showOverviewWindow) {
        Window(
            onCloseRequest = { showOverviewWindow = false },
            title = "Material Übersicht"
        ) {
            MaterialOverviewScreen(viewModel)
        }
    }

    // 🔍 Detailansicht im Monitor mit Read-Only
    if (selectedMaterialForMonitor != null) {
        DialogWindow(
            onCloseRequest = {
                selectedMaterialForMonitor = null
                viewModel.clearSelection()
            },
            title = "Materialdetails",
            state = rememberDialogState(width = 1200.dp, height = 450.dp),
            resizable = true,
            alwaysOnTop = true
        ) {
            DetailDialog(
                material = selectedMaterialForMonitor!!,
                logs = monitorLogs,
                onDismiss = {
                    selectedMaterialForMonitor = null
                    viewModel.clearSelection()
                },
                onSave = {
                    viewModel.updateMaterial(it)
                    selectedMaterialForMonitor = null
                    viewModel.clearSelection()
                },
                onDelete = {
                    viewModel.deleteMaterial(it)
                    selectedMaterialForMonitor = null
                    viewModel.clearSelection()
                },
                readOnly = true
            )
        }
    }
}
