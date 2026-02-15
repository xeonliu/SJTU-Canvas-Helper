# Add project specific ProGuard rules here.
-keep class com.sjtu.canvas.helper.data.** { *; }
-keep class com.sjtu.canvas.helper.CanvasHelperApplication { *; }
-keep class com.sjtu.canvas.helper.MainActivity { *; }

# Keep Retrofit interfaces and method/parameter annotations
-keep interface com.sjtu.canvas.helper.data.api.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Gson: keep model classes and generic signatures are already kept above
# Optionally keep gson runtime to avoid reflection issues
-dontwarn com.google.gson.**

# Dagger/Hilt keep rules (generally provided by libraries, but reinforced here)
-dontwarn dagger.**
-dontwarn javax.inject.**
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep class dagger.hilt.** { *; }
-keep class dagger.hilt.android.internal.managers.** { *; }
-keep class dagger.hilt.internal.** { *; }

# OkHttp/Retrofit warnings are suppressed already
-dontwarn okhttp3.**
-dontwarn retrofit2.**
