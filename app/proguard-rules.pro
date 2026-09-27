# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

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
