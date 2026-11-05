# ============================================================
# cycani-tv R8 规则
# 目标设备：32 位 ART（armeabi-v7a），Android 12
# ============================================================

# 保留行号，崩溃堆栈才有意义
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions

# ---------- kotlinx.serialization ----------
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.cycitv.**$$serializer { *; }
-keepclassmembers class com.cycitv.** {
    *** Companion;
}
-keepclasseswithmembers class com.cycitv.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---------- Room ----------
# Room 自带 consumer rules，但实体/DAO 通过反射按类名查找，需保留
-keep class com.cycitv.data.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ---------- NanoHTTPD ----------
# ServerSocketFactory 通过反射按类名实例化
-keep class fi.iki.elonen.** { *; }
-keepclassmembers class fi.iki.elonen.** { *; }

# ---------- ZXing ----------
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# ---------- OkHttp / Coil ----------
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okio.** { *; }
-dontwarn coil3.**

# ---------- Kotlin 协程 / 反射 ----------
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlin.** { *; }
-dontwarn kotlin.**

# ---------- Media3 ----------
-dontwarn androidx.media3.**
