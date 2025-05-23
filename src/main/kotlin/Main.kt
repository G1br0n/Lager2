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
import repositorys.SQLiteMaterialRepository
import viewModels.MaterialViewModel
import views.*
import java.awt.AWTEvent
import java.awt.Toolkit
import java.awt.event.KeyEvent
import java.util.logging.Level
import java.util.logging.Logger
import javax.swing.SwingUtilities

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

    val selected = selectedMaterial

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

        if (showDetailDialog && selected != null) {
            DialogWindow(
                onCloseRequest = {
                    showDetailDialog = false
                    selectedMaterial = null
                },
                title = "Materialdetails",
                state = rememberDialogState(width = 1200.dp, height = 450.dp),
                resizable = true,
                alwaysOnTop = true
            ) {
                DetailDialog(
                    material = selected,
                    onDismiss = {
                        showDetailDialog = false
                        selectedMaterial = null
                    },
                    onSave = {
                        viewModel.updateMaterial(it)
                        showDetailDialog = false
                        selectedMaterial = null
                    },
                    onDelete = {
                        viewModel.deleteMaterial(it)
                        showDetailDialog = false
                        selectedMaterial = null
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
    // alle PDFBox-Logger auf SEVERE setzen, damit nur Fehler (und keine WARNINGS) erscheinen
    Logger.getLogger("org.apache.pdfbox").level = Level.SEVERE
    Logger.getLogger("org.apache.pdfbox.pdmodel.font.FileSystemFontProvider").level = Level.SEVERE
}

fun main() = application {
    configurePdfBoxLogging()
    val repository = SQLiteMaterialRepository()
    val viewModel = MaterialViewModel(repository)

    var selectedMaterialForMonitor by remember { mutableStateOf<Material?>(null) }
    val selectedMonitor = selectedMaterialForMonitor


    // 🪟 Hauptfenster
    Window(onCloseRequest = ::exitApplication, title = "Lagerverwaltung (MVVM & Grautöne)") {
        App(viewModel)
    }

    // 🖥️ Zweitfenster: Monitor
    Window(onCloseRequest = {}, title = "Material Monitor") {
        MonitorView(viewModel) { selected ->
            selectedMaterialForMonitor = selected
        }
    }

    // 🔍 Detailansicht im Monitor mit Read-Only
    if (selectedMonitor != null) {
        DialogWindow(
            onCloseRequest = { selectedMaterialForMonitor = null },
            title = "Materialdetails",
            state = rememberDialogState(width = 1200.dp, height = 450.dp),
            resizable = true,
            alwaysOnTop = true
        ) {
            DetailDialog(
                material = selectedMonitor,
                onDismiss = { selectedMaterialForMonitor = null },
                onSave = {
                    viewModel.updateMaterial(it)
                    selectedMaterialForMonitor = null
                },
                onDelete = {
                    viewModel.deleteMaterial(it)
                    selectedMaterialForMonitor = null
                },
                readOnly = true
            )
        }
    }
}
