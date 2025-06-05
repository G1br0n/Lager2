import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import java.io.InputStream

fun initFirebaseAdmin() {
    // Nur initialisieren, wenn noch keine Default-App existiert
    if (FirebaseApp.getApps().isEmpty()) {
        val stream: InputStream? = Thread.currentThread()
            .contextClassLoader
            .getResourceAsStream("vrs-app-79a8e-firebase-adminsdk-fbsvc-f681d34ab2.json")
        if (stream == null) {
            throw RuntimeException("Service-Account-Key nicht gefunden")
        }
        val credentials = GoogleCredentials.fromStream(stream)
            .createScoped(
                listOf(
                    "https://www.googleapis.com/auth/firebase.database",
                    "https://www.googleapis.com/auth/cloud-platform"
                )
            )
        val options = FirebaseOptions.builder()
            .setCredentials(credentials)
            .setProjectId("vrs-app-79a8e")
            .setDatabaseUrl("https://vrs-app-79a8e-default-rtdb.europe-west1.firebasedatabase.app")
            .build()
        FirebaseApp.initializeApp(options)
        println("✅ FirebaseApp initialisiert.")
    } else {
        println("✅ FirebaseApp bereits vorhanden, Initialisierung übersprungen.")
    }
}
