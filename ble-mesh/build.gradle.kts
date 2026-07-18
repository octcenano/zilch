plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.zilch.blemesh"
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
    // ═══ AndroidX ═══
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.annotation:annotation:1.7.1")

    // ═══ Lifecycle (para CoroutineScope y Lifecycle-aware coroutines) ═══
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // ═══ Coroutines ═══
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // ═══ Cifrado — solo Bouncy Castle (ya incluido en crypto-engine) ═══
    // Bouncy Castle se provee como transitive dependency desde crypto-engine.
    // Aquí solo lo declaramos para compilación independiente.
    implementation("org.bouncycastle:bcprov-jdk18on:1.78")

    // ═══ Testing ═══
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}
