# Add project specific ProGuard rules here.

# 保留 Xposed 相关类
-keep class de.robv.android.xposed.** { *; }
-keep class com.example.homelauncher.MainHook { *; }

# 保留所有使用 @XposedHook 注解的方法
-keepclassmembers class * {
    @de.robv.android.xposed.* *;
}
