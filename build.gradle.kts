import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    // Für Desktop‐Projekte braucht man kein com.google.gms‐Plugin –
    // wir initialisieren das Firebase Admin SDK später direkt im Code.
}

group = "com.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    google()                                    // Hier liegen u. a. die Firebase-Admin-Artefakte
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    // ─────────────────────────────────────────────────────────────────────
    // Compose-Desktop-Bibliotheken
    // ─────────────────────────────────────────────────────────────────────
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material3:material3:1.5.2")

    // ─────────────────────────────────────────────────────────────────────
    // Lokale/JVM-Bibliotheken (SQLite, PDF, Audio etc.)
    // ─────────────────────────────────────────────────────────────────────
    implementation("org.xerial:sqlite-jdbc:3.41.2.2")
    implementation("javazoom:jlayer:1.0.1")
    implementation("org.apache.pdfbox:pdfbox:2.0.30")

    // ─────────────────────────────────────────────────────────────────────
    // Firebase Admin SDK (Java) für Desktop
    // ─────────────────────────────────────────────────────────────────────
    // (Stand Juni 2025: Version 9.0.0 – prüfe ggf. auf MavenCentral, ob es eine neuere gibt)
    implementation("com.google.firebase:firebase-admin:9.0.0")

    // Falls du den Service-Account-Key explizit lädst, brauchst du diese Google-Auth-Libs:
    implementation("com.google.auth:google-auth-library-credentials:1.24.1")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.24.1")
    implementation("org.slf4j:slf4j-simple:1.7.36")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    implementation("org.burnoutcrew.composereorderable:reorderable:0.9.6")

}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Lager"
            packageVersion = "1.0.0"
        }
    }
}
