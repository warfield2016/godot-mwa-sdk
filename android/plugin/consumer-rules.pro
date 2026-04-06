# Consumer ProGuard rules for SolanaMWA Godot plugin
# These rules are applied to any app that consumes this AAR library.

# Keep all @UsedByGodot methods - required for Godot to discover plugin methods via JNI.
# Without these keep rules, R8 will strip methods Godot tries to call, causing runtime failures.
-keepclasseswithmembers class * {
    @org.godotengine.godot.plugin.UsedByGodot <methods>;
}

# Keep the Godot plugin entry class (instantiated by Godot via Class.forName()).
# Scoped to specific class, not wildcard — internal helpers should be shrinkable.
-keep class com.solanamwa.godot.SolanaMWAPlugin { *; }

# Keep the MWA clientlib entry points used via reflection / coroutines.
-keep class com.solana.mobilewalletadapter.** { *; }

# Keep SignalInfo and the signals system used by Godot plugins.
-keep class org.godotengine.godot.plugin.SignalInfo { *; }

# Keep Kotlin metadata for coroutines stack traces.
-keep class kotlin.Metadata { *; }

# Keep kotlinx.coroutines internals needed at runtime.
-keep class kotlinx.coroutines.** { *; }
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler { *; }
