# --- ML Kit face detection --------------------------------------------------
# ML Kit builds its internal Dagger graph by reflecting over renamed classes;
# R8 renaming breaks dependency resolution at MlKitInitProvider startup
# ("Unsatisfied dependency ... class ue0"). Prevent renaming of all ML Kit
# internals; obfuscation of our own code is unaffected.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit.** { *; }
-keep class com.google.android.datatransport.** { *; }
-keep class com.google.firebase.** { *; }
-keepclasseswithmembernames class * {
    @javax.inject.Inject <fields>;
    @javax.inject.Inject <init>(...);
}
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**
-dontwarn com.google.firebase.**

# --- Room -------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# --- Coroutines debug metadata (avoids R8 warnings) --------------------------
-dontwarn kotlinx.coroutines.debug.**

# --- General hardening -------------------------------------------------------
-dontwarn java.lang.invoke.StringConcatFactory
-renamesourcefileattribute SourceFile
