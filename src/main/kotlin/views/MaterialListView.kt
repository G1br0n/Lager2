package views


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import models.Material
import viewModels.MaterialViewModel
@Composable
fun MaterialListView(
    viewModel: MaterialViewModel,
    onMaterialSelected: (Material) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(viewModel.filteredMaterials) { material ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (material.inLager) Color(0xFFB9F6CA) else Color(0xFFFFCDD2)
                    )
                    .clickable { onMaterialSelected(material) }
                    .padding(vertical = 4.dp)
            ) {
                Text(material.bezeichnung ?: "–", modifier = Modifier.weight(1f))
                Text(material.seriennummer?.trimEnd() ?: "", modifier = Modifier.weight(1f))
                Text(if (material.inLager) "Verfügbar" else "Ausgegeben", modifier = Modifier.weight(1f))
                Text(material.position ?: "–", modifier = Modifier.weight(1f)) // ✅ NEU: Position
                Text(material.notiz ?: "–", modifier = Modifier.weight(1f))
            }
            Divider()
        }
    }
}
