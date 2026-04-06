# ProGuard rules for SolanaMWA plugin library itself (release builds).

# Preserve line numbers for useful stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Do not obfuscate anything reachable by Godot via @UsedByGodot reflection.
-keepclasseswithmembers class * {
    @org.godotengine.godot.plugin.UsedByGodot <methods>;
}

# Keep the plugin's entry class so Godot can instantiate it by name.
-keep class com.solanamwa.godot.SolanaMWAPlugin { *; }

# Keep clientlib-ktx public API surface.
-keep class com.solana.mobilewalletadapter.** { *; }
-keep class com.solana.publickey.** { *; }

# Suppress warnings for transitive deps that reference missing symbols safely.
-dontwarn kotlinx.coroutines.**
-dontwarn com.solana.**
