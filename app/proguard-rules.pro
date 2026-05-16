# Keep serializable classes
-keep,allowobfuscation,allowshrinking class kotlinx.serialization.Serializable
-keep,allowobfuscation,allowshrinking @kotlinx.serialization.Serializable class **
-keepclassmembers class * implements kotlinx.serialization.KSerializer { *; }
-keep class dev.jtapzg.manjiro.data.** { *; }

# libsu uses reflection internally
-keep class com.topjohnwu.superuser.** { *; }

# Compose
-keep class androidx.compose.** { *; }
