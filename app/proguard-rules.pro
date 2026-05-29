# ===========================================================================
# Annotations & generic
# ===========================================================================
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations

# ===========================================================================
# Kotlinx Serialization
# ===========================================================================
-keepclassmembers class kotlinx.serialization.json.** { *** *; }
-keep,includedescriptorclasses class com.tarnlabs.allergybuster.**$$serializer { *; }
-keepclassmembers class com.tarnlabs.allergybuster.** {
    *** Companion;
}
-keepclasseswithmembers class com.tarnlabs.allergybuster.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclasseswithmembers class * { @kotlinx.serialization.* <methods>; }

# ===========================================================================
# Ktor
# ===========================================================================
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# ===========================================================================
# SQLDelight
# ===========================================================================
-keep class app.cash.sqldelight.** { *; }
-dontwarn app.cash.sqldelight.**

# ===========================================================================
# Coroutines
# ===========================================================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ===========================================================================
# Hilt / Dagger
# ===========================================================================
-keep class dagger.hilt.** { *; }
-keep,allowobfuscation @interface dagger.hilt.android.HiltAndroidApp
-keep,allowobfuscation @interface dagger.hilt.android.AndroidEntryPoint
-keep,allowobfuscation @interface dagger.hilt.android.lifecycle.HiltViewModel
-keep @dagger.hilt.android.HiltAndroidApp class *
-keep @dagger.hilt.android.AndroidEntryPoint class *
-keep @dagger.hilt.android.lifecycle.HiltViewModel class *

# Hilt-generated wiring
-keep class **_HiltModules { *; }
-keep class **_HiltModules$* { *; }
-keep class **_Factory { *; }
-keep class **_MembersInjector { *; }

# ===========================================================================
# WorkManager + Hilt-Work
# ===========================================================================
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker
-keep,allowobfuscation @interface androidx.hilt.work.HiltWorker
-keep @androidx.hilt.work.HiltWorker class * extends androidx.work.ListenableWorker {
    <init>(...);
}

# ===========================================================================
# Navigation-Compose
# ===========================================================================
-keep class androidx.navigation.** { *; }
-dontwarn androidx.navigation.**

# ===========================================================================
# DataStore
# ===========================================================================
-keep class androidx.datastore.*.** { *; }
-dontwarn androidx.datastore.**

# ===========================================================================
# Compose
# ===========================================================================
-dontwarn androidx.compose.**
