# Keep the accessibility service reachable by the system.
-keepclassmembers class com.superdriver.app.service.RideAccessibilityService {
    public *;
}

# ML Kit / Google Play services internals are used through reflection by some
# versions of the task API.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.** { *; }
-dontwarn com.google.android.gms.**
