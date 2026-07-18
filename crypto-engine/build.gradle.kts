plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.zilch.crypto"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // ═══ Criptografía Ed25519 — Bouncy Castle (100% open source) ═══
    // Bouncy Castle es la única librería de crypto madura que ofrece
    // Ed25519 sin dependencias de Google ni del Android Keystore.
    // Licencia: MIT
    implementation("org.bouncycastle:bcprov-jdk18on:1.78")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78")

    // ═══ QR Code — ZXing (Apache 2.0, NO es Google Play Service) ═══
    // ZXing es un proyecto open source de Google pero NO requiere
    // Play Services. Es solo una librería de código de barras.
    implementation("com.google.zxing:core:3.5.3")

    // ═══ Almacenamiento cifrado ═══
    // AndroidX Security — usa el Android Keystore, NO Play Services
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // ═══ SQLCipher — Base de datos cifrada ═══
    // AES-256-CBC para Zero-Knowledge storage en disco
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
    implementation("androidx.sqlite:sqlite-framework:2.4.0")

    // ═══ Coroutines ═══
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // ═══ AndroidX ═══
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.annotation:annotation:1.7.1")

    // ═══ JSON — used by QrDecoder and QrEncoder ═══
    implementation("org.json:json:20231013")

    // ═══ Testing ═══
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}
