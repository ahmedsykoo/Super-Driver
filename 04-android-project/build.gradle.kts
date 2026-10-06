// Plugin versions: Kotlin 2.0.21 is confirmed (bundled with local Gradle 8.14.3 used for engine verification).
// AGP 8.5.2 and KSP 2.0.21-1.0.28 are NOT verified in this environment (Maven is blocked here); adjust in Android Studio if sync complains.
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.jvm") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}
