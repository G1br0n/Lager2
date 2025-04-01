package config

import androidx.compose.ui.graphics.Color

object APPConfig {
    //Farbe zuweisung
    val customColors = mapOf(
        //-------------Sonder Position
        "zöllner" to Color(0xFFD32F2F),   // Rot
        "reparatur" to Color(0xFFD32F2F), // Rot

        //--------------Mitarbeiter vordefeniert
        "makhach" to Color(0xFF4CAF50),     // Grün
        "walter" to Color(0xFF00897B),    // Türkisgrün

        //--------------Andere
        "hdi" to Color(0xFF9C27B0),       // Lila


        //--------------Beispiele
        "max" to Color(0xFF388E3C),       // Dunkelgrün
        "lisa" to Color(0xFFFBC02D),      // Sattes Gelb
        "jonas" to Color(0xFF1976D2),     // Mittelblau
        "alex" to Color(0xFF303F9F),      // Nachtblau
        "maria" to Color(0xFFFFA000),     // Orange-Gelb
        "tom" to Color(0xFF455A64),       // Dunkelgrau
        "eva" to Color(0xFF689F38),       // Moosgrün
        "felix" to Color(0xFFFF7043),     // Orange-Rot
        "nora" to Color(0xFF7E57C2)       // Lavendel/Dunkelviolett
    )

    val bezeichnungsReihenfolge = listOf(
        "ZPW-12",
        "ZPW126-10",
        "ZFS-10",
        "F500-SEN",
        "F500-AB",
        "ZRC-10",
        "ZRC-V"
    )

    //Sortirt butons bei position am ende in der rein folge
    val specialPositions = listOf(
        "Reparatur",
        "Zöllner")


    //random farben für nicht zugewisene positionen
    val colors = listOf(
        Color(0xFFB39DDB), Color(0xFFFFF176), Color(0xFFFFAB91), Color(0xFFAED581),
        Color(0xFFFF8A65), Color(0xFF81D4FA), Color(0xFFFFECB3), Color(0xFFCE93D8),
        Color(0xFFF8BBD0), Color(0xFFBBDEFB), Color(0xFFFFF59D), Color(0xFFE1BEE7),
        Color(0xFFFFCCBC), Color(0xFFA5D6A7), Color(0xFFB2EBF2), Color(0xFFFFE082),
        Color(0xFF4DD0E1), Color(0xFFB2DFDB), Color(0xFFD1C4E9), Color(0xFFF0F4C3),
        Color(0xFF80DEEA), Color(0xFFFFCDD2), Color(0xFFD7CCC8), Color(0xFF4DB6AC),
        Color(0xFFDCEDC8), Color(0xFFB388FF), Color(0xFFC8E6C9), Color(0xFFC5CAE9),
        Color(0xFFFFCC80), Color(0xFF81C784), Color(0xFFB3E5FC),
        Color(0xFFFFE0B2)
    ).shuffled()

}
