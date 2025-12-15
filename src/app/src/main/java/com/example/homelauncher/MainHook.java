package com.example.homelauncher;

import android.content.ComponentName;
import android.content.Intent;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "HomeLauncherRedirect";
    private static final String MODULE_PACKAGE = "com.example.homelauncher";
    private static final String PREFS_NAME = "HomeLauncherPrefs";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // Hook 系统桌面（com.android.launcher）
        if (lpparam.packageName.equals("com.android.launcher")) {
            // 安全检查：如果用户选择的启动器就是 com.android.launcher，则不进行 hook
            // 避免无限递归重定向
            XSharedPreferences prefs = new XSharedPreferences(MODULE_PACKAGE, PREFS_NAME);
            prefs.makeWorldReadable();
            prefs.reload();

            String selectedPackage = prefs.getString("selected_launcher_package", "system_default");

            if (selectedPackage.equals("com.android.launcher")) {
                XposedBridge.log(TAG + ": Selected launcher is com.android.launcher, skipping hook to avoid infinite recursion");
                return;
            }

            // XposedBridge.log(TAG + ": Hooking system launcher (com.android.launcher)...");
            try {
                hookSystemLauncher(lpparam);
            } catch (Throwable t) {
                // XposedBridge.log(TAG + ": Error hooking system launcher: " + t.getMessage());
                t.printStackTrace();
            }
            return;
        }
    }

    /**
     * Hook 系统桌面 - 当它要显示时重定向到用户选择的启动器
     */
    private void hookSystemLauncher(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // 查找系统桌面的 Launcher Activity
            Class<?> launcherClass = XposedHelpers.findClass(
                "com.android.launcher.Launcher",
                lpparam.classLoader
            );

            // XposedBridge.log(TAG + ": Found system Launcher class");

            // Hook onCreate - 首次创建时（最早的拦截点）
            try {
                XposedHelpers.findAndHookMethod(
                    launcherClass,
                    "onCreate",
                    android.os.Bundle.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            // XposedBridge.log(TAG + ": System launcher onCreate called");
                            redirectToUserLauncher((android.app.Activity) param.thisObject);
                        }
                    }
                );
                // XposedBridge.log(TAG + ": Successfully hooked system launcher onCreate");
            } catch (Throwable t) {
                // XposedBridge.log(TAG + ": Failed to hook onCreate: " + t.getMessage());
            }

            // Hook onStart - Activity 即将可见时
            try {
                XposedHelpers.findAndHookMethod(
                    launcherClass,
                    "onStart",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            // XposedBridge.log(TAG + ": System launcher onStart called");
                            redirectToUserLauncher((android.app.Activity) param.thisObject);
                        }
                    }
                );
                // XposedBridge.log(TAG + ": Successfully hooked system launcher onStart");
            } catch (Throwable t) {
                // XposedBridge.log(TAG + ": Failed to hook onStart: " + t.getMessage());
            }

            // Hook onResume - Activity 获得焦点时
            try {
                XposedHelpers.findAndHookMethod(
                    launcherClass,
                    "onResume",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            // XposedBridge.log(TAG + ": System launcher onResume called");
                            redirectToUserLauncher((android.app.Activity) param.thisObject);
                        }
                    }
                );
                // XposedBridge.log(TAG + ": Successfully hooked system launcher onResume");
            } catch (Throwable t) {
                // XposedBridge.log(TAG + ": Failed to hook onResume: " + t.getMessage());
            }

            // Hook onWindowFocusChanged - 窗口获得焦点时（额外保险）
            try {
                XposedHelpers.findAndHookMethod(
                    launcherClass,
                    "onWindowFocusChanged",
                    boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            boolean hasFocus = (boolean) param.args[0];
                            if (hasFocus) {
                                // XposedBridge.log(TAG + ": System launcher gained window focus");
                                redirectToUserLauncher((android.app.Activity) param.thisObject);
                            }
                        }
                    }
                );
                // XposedBridge.log(TAG + ": Successfully hooked system launcher onWindowFocusChanged");
            } catch (Throwable t) {
                // XposedBridge.log(TAG + ": Failed to hook onWindowFocusChanged: " + t.getMessage());
            }

        } catch (Throwable t) {
            // XposedBridge.log(TAG + ": Failed to hook system launcher: " + t.getMessage());
            t.printStackTrace();
        }
    }

    /**
     * 重定向到用户选择的启动器
     */
    private void redirectToUserLauncher(android.app.Activity activity) {
        try {
            // 读取用户偏好
            XSharedPreferences prefs = new XSharedPreferences(MODULE_PACKAGE, PREFS_NAME);
            prefs.makeWorldReadable();
            prefs.reload();

            String selectedPackage = prefs.getString("selected_launcher_package", "system_default");
            String selectedClass = prefs.getString("selected_launcher_class", "system_default");

            // 如果用户选择了特定启动器，且不是系统桌面
            if (!selectedPackage.equals("system_default") &&
                !selectedPackage.equals("com.android.launcher")) {

                // XposedBridge.log(TAG + ": Redirecting from system launcher to: " + selectedPackage);

                // 启动用户选择的启动器
                Intent launchIntent = new Intent(Intent.ACTION_MAIN);
                launchIntent.addCategory(Intent.CATEGORY_HOME);
                launchIntent.setComponent(new ComponentName(selectedPackage, selectedClass));
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

                try {
                    activity.startActivity(launchIntent);
                    // XposedBridge.log(TAG + ": Started user selected launcher");

                    // 立即结束系统桌面，防止显示
                    activity.finish();
                    // XposedBridge.log(TAG + ": Finished system launcher");
                } catch (Exception e) {
                    // XposedBridge.log(TAG + ": Failed to redirect: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            // XposedBridge.log(TAG + ": Error in redirectToUserLauncher: " + e.getMessage());
        }
    }
}
