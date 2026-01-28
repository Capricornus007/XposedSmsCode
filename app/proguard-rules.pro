-keepclasseswithmembers class * implements de.robv.android.xposed.IXposedHookLoadPackage {
    public void handleLoadPackage(...);
}

-keepclasseswithmembers class * implements de.robv.android.xposed.IXposedHookZygoteInit {
    public void initZygote(...);
}

-keep class com.tianma.xsmscode.common.utils.ModuleUtils {
    int getModuleVersion();
}


# ==========================
# jsoup proguard start
-keeppackagenames org.jsoup.nodes
# jsoup proguard end
# ==========================


# ==========================
# okhttp3 start
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
# okhttp3 end
# ==========================


# ==========================
# okio start
-dontwarn okio.**
# okio end
# ==========================

# ==========================
# retrofit2 start
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions
# retrofit2 end
# ==========================

# ==========================
# Kotlin Serialization start
-keepattributes *Annotation*
-keepclassmembers class **$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class **$Companion$* {
    ** INSTANCE;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class * { *; }
-dontwarn kotlinx.serialization.**
# Kotlin Serialization end
# ==========================

# ==========================
# Room start
-keep class androidx.room.RoomDatabase
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class com.tianma.xsmscode.data.db.AppDatabase_Impl {
    public <init>();
}
# Room end
# ==========================

# ==========================
# Xposed start
-keep class com.tianma.xsmscode.xp.HookEntry { *; }
-keep class com.tianma.xsmscode.xp.hook.** { *; }
# Xposed end
# ==========================
