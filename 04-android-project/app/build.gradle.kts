import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// Supabase project credentials (T7). Never committed: read from local.properties, which is
// git-ignored. Ahmed creates the Supabase project and puts these two lines in
// 04-android-project/local.properties:
//   supabaseUrl=https://xxxx.supabase.co
//   supabaseAnonKey=xxxx
// Missing file/keys build fine but every network call in data/Auth.kt fails at runtime with
// "تعذر الاتصال" until they are set.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val supabaseUrl: String = (localProps.getProperty("supabaseUrl") ?: "").trimEnd('/')
val supabaseAnonKey: String = localProps.getProperty("supabaseAnonKey") ?: ""

android {
    namespace = "com.superdriver.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.superdriver.app" // assumption: not decided by the client
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":engine"))
    // Room 2.6.1 / coroutines 1.8.1 / OkHttp 4.12.0: not verified here (Maven blocked); standard releases as far as I know.
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // Added for T7 (Supabase email login) — the only networking dependency in the project.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
