package views

import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun MissingNameDialog(onDismiss: () -> Unit) {
    Dialog(onCloseRequest = onDismiss) {
        Surface(
            modifier = Modifier.wrapContentSize(),
            shape = MaterialTheme.shapes.medium,
            elevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Empfängername fehlt!", style = MaterialTheme.typography.h6)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Bitte gib einen Empfängernamen ein, um im Ausgabe-Modus fortzufahren.")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("OK")
                }
            }
        }
    }
}