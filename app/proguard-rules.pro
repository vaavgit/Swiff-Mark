# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

# App Data Models — prevent R8 from stripping Room @Entity / JSON fields
-keep class com.vaibhav.facialattendancesystem.data.** { *; }
-keepclassmembers class com.vaibhav.facialattendancesystem.data.** { *; }

# App Utilities — SessionManager, NotificationHelper, etc.
-keep class com.vaibhav.facialattendancesystem.util.** { *; }
-keepclassmembers class com.vaibhav.facialattendancesystem.util.** { *; }

# ML Kit & TensorFlow Lite
-keep class com.google.mlkit.** { *; }
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# OpenCV
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# Compose
-keepclassmembers class * extends androidx.compose.ui.node.RootForTest { *; }

# Supabase / JSON
-keep class org.json.** { *; }

# Kotlin Coroutines & Serialization
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**
-keepclassmembers class ** {
    @kotlinx.serialization.SerialName <fields>;
}

# Prevent stripping of Parcelable implementations
-keep class * implements android.os.Parcelable { *; }
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep enum values
-keepclassmembers enum * { *; }

# Kotlin metadata
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
