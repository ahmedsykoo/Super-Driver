plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.superdriver.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.superdriver.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        // The app ships in Arabic and English only. Everything else is stripped
        // from the APK, and the system is told the same through @xml/locales_config.
        resourceConfigurations += setOf("ar", "en")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")

    // On-device OCR with the model statically linked in the APK (bundled
    // variant): works offline, no Play Services, no internet, no server.
    // Used only as a fallback when the accessibility tree cannot be read.
    // Note: ML Kit ships Latin/Chinese/Devanagari/Japanese/Korean scripts only,
    // so Arabic offers are read through the accessibility tree.
    implementation("com.google.mlkit:text-recognition:16.0.1")

    testImplementation("junit:junit:4.13.2")
}
