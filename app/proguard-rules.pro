-keepattributes *Annotation*
# Kotlinx Serialization
-keepclassmembers class kotlinx.serialization.json.** { *** *; }
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclasseswithmembers class * { @kotlinx.serialization.* <methods>; }
# Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * { @retrofit2.http.* <methods>; }
# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
