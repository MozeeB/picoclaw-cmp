import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    // Re-apply the default KMP hierarchy template explicitly. Creating a manual
    // intermediate source set (jvmCommonMain, below) otherwise disables it, which
    // would break iosMain → iosArm64/iosSimulatorArm64 actual wiring.
    applyDefaultHierarchyTemplate()

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    jvm()

    js {
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    androidLibrary {
        namespace = "com.mozeeb.picoclaw.cmp.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        // Intermediate source set shared by JVM (Desktop) + Android — both are JVM-based
        // and share the binary-download + archive-extract logic (java.net / java.util.zip).
        // Manual wiring: the new android KMP plugin isn't matched by the hierarchy template's
        // withAndroidTarget(), so we wire dependsOn() directly.
        val jvmCommonMain by creating { dependsOn(commonMain.get()) }
        jvmMain.get().dependsOn(jvmCommonMain)
        androidMain.get().dependsOn(jvmCommonMain)

        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.kotlinx.serializationJson)
            // Material Icons Extended (Dashboard, Language, Article, etc.)
            implementation(compose.materialIconsExtended)
            // Koin 4.1.0 — koin-core supports all KMP targets including iOS
            implementation(libs.koin.core)
            // koin-compose-viewmodel provides koinViewModel() for all KMP targets
            implementation(libs.koin.compose.viewmodel)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.kotlinx.coroutinesTest)
        }
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.koin.android)
            implementation(libs.kotlinx.coroutinesCore)
            // AndroidX DataStore — supported on Android/JVM/iOS only
            implementation(libs.datastore.preferences)
        }
        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutinesSwing)
            // AndroidX DataStore for Desktop
            implementation(libs.datastore.preferences)
        }
        iosMain.dependencies {
            // AndroidX DataStore for iOS
            implementation(libs.datastore.preferences)
        }
        jsMain.dependencies {
            implementation(libs.wrappers.browser)
            // No DataStore on JS — uses InMemorySettings
        }
        // wasmJsMain uses InMemorySettings — no DataStore dependency needed
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
