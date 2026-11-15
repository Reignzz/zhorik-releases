plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val vc = (System.getenv("VERSION_CODE") ?: "1").toInt()

android {
    namespace = "com.beamox.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.beamox.launcher"
        minSdk = 26
        targetSdk = 35
        versionCode = vc
        versionName = "1.0.$vc"
        buildConfigField("String", "UPDATE_BASE",
            "\"https://github.com/reignzz/zhorik-releases/releases/download/beamox-launcher\"")
    }

    // Подпись. Надёжный вариант: приватный ключ в секретах GitHub (BEAMOX_KEYSTORE_* — см. README).
    // Если секретов нет — используется ключ из репозитория (подходит для теста, но он публичный).
    signingConfigs {
        create("beamox") {
            val ks = System.getenv("BEAMOX_KEYSTORE_FILE")
            if (!ks.isNullOrBlank() && file(ks).exists()) {
                storeFile = file(ks)
                storePassword = System.getenv("BEAMOX_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("BEAMOX_KEY_ALIAS") ?: "beamox"
                keyPassword = System.getenv("BEAMOX_KEY_PASSWORD") ?: System.getenv("BEAMOX_KEYSTORE_PASSWORD")
            } else {
                storeFile = rootProject.file("keystore/beamox.jks")
                storePassword = "beamox-launcher"
                keyAlias = "beamox"
                keyPassword = "beamox-launcher"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("beamox")
        }
        debug {
            signingConfig = signingConfigs.getByName("beamox")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = false   // отчёт lint публикуется в Actions, сборку не валит
        checkReleaseBuilds = true
    }

    packaging {
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/INDEX.LIST",
                "META-INF/versions/**",
                "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA",
            )
            pickFirsts += listOf("META-INF/javamail.*", "META-INF/mailcap*", "META-INF/mimetypes.default")
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.anthropic:anthropic-java:3.8.0")
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")
}
