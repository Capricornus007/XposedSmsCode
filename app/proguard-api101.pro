-keep class com.github.magisk317.smscode.xp.LibXposedEntry { *; }
-keep class com.github.magisk317.smscode.xp.hook.** { *; }
-keep class io.github.magisk317.smscode.core.hook.** { *; }
-keep class io.github.magisk317.smscode.core.hookapi.** { *; }
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowobfuscation,allowoptimization public class * extends io.github.libxposed.api.XposedModule
-keep,allowoptimization,allowobfuscation @io.github.libxposed.api.annotations.* class * {
    @io.github.libxposed.api.annotations.BeforeInvocation <methods>;
    @io.github.libxposed.api.annotations.AfterInvocation <methods>;
}
