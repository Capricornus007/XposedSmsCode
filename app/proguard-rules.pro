-keep class io.github.capricornus007.smscode.common.utils.ModuleUtils {
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
-keep class io.github.capricornus007.smscode.data.db.AppDatabase_Impl {
    public <init>();
}
# Room end
# ==========================

# ==========================
# Xposed start
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}
-keep class io.github.capricornus007.smscode.xp.hook.** { *; }
-adaptresourcefilecontents META-INF/xposed/java_init.list
-dontwarn io.github.libxposed.annotation.**
# libxposed-service 是模組 app 與 LSPosed 管理器之間的 binder 介面：管理器用介面描述子
# marshal IXposedService/IXposedScopeCallback，R8 一改名牌服務就綁不上（2026-10-31
# upstream 實測 IXposedScopeCallback 變成 R8$$、app 卡在未激活）。移植自 upstream cf2850c9。
-keep class io.github.libxposed.service.** { *; }
# Xposed end
# ==========================

# Xposed entry point: LSPosed loads this class by the exact name listed in
# META-INF/xposed/java_init.list — R8 must not rename or strip it.
-keep class io.github.capricornus007.smscode.xp.LibXposedEntry { *; }
