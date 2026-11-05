
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

abstract class ExportTestApkTask : org.gradle.api.DefaultTask() {
    @get:org.gradle.api.tasks.InputFile
    @get:org.gradle.api.tasks.PathSensitive(org.gradle.api.tasks.PathSensitivity.RELATIVE)
    abstract val inputApk: org.gradle.api.file.RegularFileProperty

    @get:org.gradle.api.tasks.OutputFile
    abstract val outputApk: org.gradle.api.file.RegularFileProperty

    @org.gradle.api.tasks.TaskAction
    fun export() {
        inputApk.get().asFile.copyTo(outputApk.get().asFile, overwrite = true)
    }
}

android {
    namespace = "com.eyeguardian.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.eyeguardian.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 7
        versionName = "0.1.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystorePath = providers.gradleProperty("releaseStoreFile").orNull
                ?: System.getenv("ANDROID_KEYSTORE_PATH")
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storePassword = providers.gradleProperty("releaseStorePassword").orNull
                    ?: System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = providers.gradleProperty("releaseKeyAlias").orNull
                    ?: System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = providers.gradleProperty("releaseKeyPassword").orNull
                    ?: System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    val localReleaseKeystorePath = providers.gradleProperty("releaseStoreFile").orNull
        ?: System.getenv("ANDROID_KEYSTORE_PATH")

    buildTypes {
        create("installable") {
            // A directly installable development build: unlike the IDE-injected
            // debug APK, this variant is not marked testOnly, and unlike release
            // it keeps runtime classes for camera/ML Kit diagnostics.
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
            isDebuggable = true
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
        create("local") {
            // A development build that can update the GitHub release install
            // when the maintainer supplies the same release keystore locally.
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
            isDebuggable = true
            isMinifyEnabled = false
            signingConfig = if (localReleaseKeystorePath.isNullOrBlank()) {
                signingConfigs.getByName("debug")
            } else {
                signingConfigs.getByName("release")
            }
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            // Keep the first public release unminified until the R8 rules are
            // verified against AndroidX Startup and Kotlin on physical devices.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "src/main/keepRules/rules.keep"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

tasks.register<ExportTestApkTask>("exportTestApk") {
    dependsOn("assembleLocal")
    inputApk.set(layout.buildDirectory.file("outputs/apk/local/app-local.apk"))
    outputApk.set(layout.projectDirectory.file("../eye-guardian-test.apk"))
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.face.detection)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
