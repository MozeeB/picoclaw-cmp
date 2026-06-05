import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.shared)

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)

    // Needed because the desktop entry point constructs a shared ServiceViewModel
    // (tray + window) and resolves singletons from Koin directly.
    implementation(libs.koin.core)
    implementation(libs.androidx.lifecycle.viewmodelCompose)
}

compose.desktop {
    application {
        mainClass = "com.mozeeb.picoclaw.cmp.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.mozeeb.picoclaw.cmp"
            packageVersion = "1.0.0"
        }
    }
}