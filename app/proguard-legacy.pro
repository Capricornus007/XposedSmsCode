-keepclasseswithmembers class * implements de.robv.android.xposed.IXposedHookLoadPackage {
    public void handleLoadPackage(...);
}

-keepclasseswithmembers class * implements de.robv.android.xposed.IXposedHookZygoteInit {
    public void initZygote(...);
}

-keep class com.github.magisk317.smscode.xp.HookEntry { *; }
-keep class com.github.magisk317.smscode.xp.hook.** { *; }
-keep class io.github.magisk317.smscode.xposed.hook.** { *; }
-keep class io.github.magisk317.smscode.xposed.hookapi.** { *; }
