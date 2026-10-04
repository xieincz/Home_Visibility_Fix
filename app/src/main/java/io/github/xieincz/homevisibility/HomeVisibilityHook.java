package io.github.xieincz.homevisibility;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.Application;
import android.animation.Animator;
import android.graphics.PointF;
import android.view.animation.Interpolator;
import java.lang.reflect.Field;
import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** ColorOS fallback-home visibility and redundant Home-to-Home animation fixes. */
public final class HomeVisibilityHook implements IXposedHookLoadPackage {
    private static final String TAG = "HomeVisibilityFix: ";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam pkg) {
        if (!"com.android.launcher".equals(pkg.packageName)
                || !"com.android.launcher".equals(pkg.processName)) return;
        AtomicBoolean initialized = new AtomicBoolean();
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (!initialized.compareAndSet(false, true)) return;
                HookSettings.initialize((Application) param.thisObject);
                installSurfaceHook(pkg.classLoader);
                installHomeTargetHook(pkg.classLoader);
                installConfigurationHook(pkg.classLoader);
                installHomeKeyHook(pkg.classLoader);
                installGestureAnimationHook(pkg.classLoader);
            }
        });
    }

    private static void installGestureAnimationHook(ClassLoader loader) {
        try {
            Class<?> base = XposedHelpers.findClass(
                    "com.oplus.quickstep.gesture.OplusBaseSwipeUpHandler", loader);
            Class<?> fallback = XposedHelpers.findClass("com.android.quickstep.FallbackSwipeHandler", loader);
            Class<?> endTarget = XposedHelpers.findClass(
                    "com.android.quickstep.GestureState$GestureEndTarget", loader);
            Class<?>[] signature = {float.class, float.class, long.class, Interpolator.class,
                    endTarget, PointF.class, boolean.class, boolean.class};
            Method animate;
            try {
                animate = XposedHelpers.findMethodExact(base, "animateToProgressInternal", signature);
            } catch (NoSuchMethodError error) {
                animate = XposedHelpers.findMethodExact(base, "lambda$animateToProgress$26", signature);
            }
            Method finish = XposedHelpers.findMethodExact(base, "endRunningWindowAnim", boolean.class);
            Field deviceField = XposedHelpers.findField(base, "mDeviceState");
            Field parallelField = XposedHelpers.findField(base, "mParallelRunningAnim");
            AtomicBoolean failed = new AtomicBoolean();
            XposedBridge.hookMethod(animate, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (failed.get() || !HookSettings.skipGestures()
                            || !fallback.isInstance(param.thisObject)
                            || !"HOME".equals(String.valueOf(param.args[4]))) return;
                    try {
                        Object device = deviceField.get(param.thisObject);
                        if (!(boolean) XposedHelpers.callMethod(device, "isFullyGesturalNavMode")) return;
                        // Keep the gesture decision and all normal Home setup/completion callbacks.
                        // Only the post-release HOME animation is shortened, never RECENTS/LAST_TASK.
                        param.setObjectExtra("skipHome", true);
                        param.args[2] = 0L;
                    } catch (Throwable error) { reportFailure(failed, "gesture animation", error); }
                }
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.hasThrowable() || !Boolean.TRUE.equals(param.getObjectExtra("skipHome"))) return;
                    try {
                        // OEM uses this same successful-end path for zero-duration animations.
                        // Capture parallel animation before end callbacks release handler references.
                        Animator parallel = (Animator) parallelField.get(param.thisObject);
                        finish.invoke(param.thisObject, false);
                        if (parallel != null && parallel.isStarted()) parallel.end();
                        HookSettings.log(TAG + "completed gesture Home animation immediately");
                    } catch (Throwable error) { reportFailure(failed, "gesture animation", error); }
                }
            });
            // Fallback's atomic Home alpha/scale animation is independent of the window
            // animator. In particular, Home-to-Home uses a spring that ignores duration=0.
            Class<?> factory = XposedHelpers.findClass(
                    "com.android.quickstep.FallbackSwipeHandler$FallbackHomeAnimationFactory", loader);
            Field owner = XposedHelpers.findField(factory, "this$0");
            Field alpha = XposedHelpers.findField(factory, "mHomeAlpha");
            Field shift = XposedHelpers.findField(factory, "mVerticalShiftForScale");
            XposedHelpers.findAndHookMethod(factory, "playAtomicAnimation", float.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (failed.get() || !HookSettings.skipGestures()) return;
                    try {
                        Object handler = owner.get(param.thisObject);
                        if (!(boolean) XposedHelpers.callMethod(deviceField.get(handler),
                                "isFullyGesturalNavMode")) return;
                        XposedHelpers.callMethod(alpha.get(param.thisObject), "updateValue", 1.0f);
                        XposedHelpers.callMethod(shift.get(param.thisObject), "updateValue", 0.0f);
                        param.setResult(null);
                    } catch (Throwable error) { reportFailure(failed, "gesture animation", error); }
                }
            });
            HookSettings.log(TAG + "installed gesture animation hook v1.4");
        } catch (Throwable error) {
            HookSettings.log(TAG + "unsupported gesture animation API; no gesture animation hook installed");
            HookSettings.log(error);
        }
    }

    private static void installConfigurationHook(ClassLoader loader) {
        try {
            Class<?> base = XposedHelpers.findClass("com.android.quickstep.AbsSwipeUpHandler", loader);
            Class<?> fallback = XposedHelpers.findClass("com.android.quickstep.FallbackSwipeHandler", loader);
            Class<?> lifecycle = XposedHelpers.findClass("com.android.quickstep.AbsSwipeUpHandler$1", loader);
            Class<?> states = XposedHelpers.findClass("com.android.quickstep.MultiStateCallback", loader);
            Field owner = XposedHelpers.findField(lifecycle, "this$0");
            Field activityField = XposedHelpers.findField(base, "mActivity");
            Field recentsField = XposedHelpers.findField(base, "mRecentsView");
            Field stateField = XposedHelpers.findField(base, "mStateCallback");
            Field controllerField = XposedHelpers.findField(base, "mRecentsAnimationController");
            Field initField = XposedHelpers.findField(base, "mActivityInitListener");
            Field registered = XposedHelpers.findField(initField.getType(), "mIsRegistered");
            Field uiStatesField = XposedHelpers.findField(base, "LAUNCHER_UI_STATES");
            Field invalidatedField = XposedHelpers.findField(base, "STATE_HANDLER_INVALIDATED");
            Method getState = XposedHelpers.findMethodExact(states, "getState");
            Method setState = XposedHelpers.findMethodExact(states, "setStateOnUiThread", int.class);
            Method resetListeners = XposedHelpers.findMethodExact(base, "resetLauncherListeners");
            Method initStates = XposedHelpers.findMethodExact(base, "initStateCallbacks");
            AtomicBoolean reported = new AtomicBoolean();
            AtomicBoolean failed = new AtomicBoolean();
            XposedHelpers.findAndHookMethod(lifecycle, "onActivityDestroyed", Activity.class,
                    new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (failed.get()) return;
                    Object handler = null, oldState = null, oldRecents = null;
                    Activity activity = (Activity) param.args[0];
                    boolean detached = false;
                    try {
                        if (!activity.isChangingConfigurations()) return;
                        handler = owner.get(param.thisObject);
                        if (!fallback.isInstance(handler) || activityField.get(handler) != activity
                                || controllerField.get(handler) != null) return;
                        Object init = initField.get(handler);
                        if (init == null || !registered.getBoolean(init)) return;
                        oldState = stateField.get(handler);
                        int state = (int) getState.invoke(oldState);
                        // Read values only after launcher initialization; resolving fields earlier
                        // must not run OEM static initializers before Application exists.
                        int uiStates = uiStatesField.getInt(null);
                        int invalidated = invalidatedField.getInt(null);
                        if ((state & invalidated) != 0) return;
                        oldRecents = recentsField.get(handler);
                        // A pending navigation-mode relaunch destroys the stale RecentsActivity
                        // after a new gesture binds to it, but before its controller arrives.
                        // Keep that gesture and ActivityTracker registration alive. Rebuild only
                        // launcher UI readiness, as OEM onActivityInit does for a replacement.
                        resetListeners.invoke(handler);
                        detached = true;
                        activityField.set(handler, null);
                        recentsField.set(handler, null);
                        initStates.invoke(handler);
                        setState.invoke(stateField.get(handler), state & ~uiStates);
                        activity.unregisterActivityLifecycleCallbacks(
                                (android.app.Application.ActivityLifecycleCallbacks) param.thisObject);
                        param.setResult(null);
                        if (reported.compareAndSet(false, true)) {
                            HookSettings.log(TAG + "preserved fallback gesture across configuration recreation");
                        }
                    } catch (Throwable error) {
                        // Restore references so OEM destruction can still cancel and clean up.
                        if (detached) {
                            activityField.set(handler, activity);
                            recentsField.set(handler, oldRecents);
                            stateField.set(handler, oldState);
                        }
                        reportFailure(failed, "configuration", error);
                    }
                }
            });
            HookSettings.log(TAG + "installed configuration hook v1.4");
        } catch (Throwable error) {
            HookSettings.log(TAG + "unsupported configuration API; no configuration hook installed");
            HookSettings.log(error);
        }
    }

    private static void installHomeTargetHook(ClassLoader loader) {
        try {
            Class<?> helper = XposedHelpers.findClass(
                    "com.android.quickstep.util.animation.RectTransformHelper", loader);
            Class<?> target = XposedHelpers.findClass(
                    "com.android.systemui.shared.system.RemoteAnimationTargetCompat", loader);
            Class<?> observer = XposedHelpers.findClass(
                    "com.android.quickstep.OverviewComponentObserver", loader);
            Method isOtherDesk = observer.getDeclaredMethod("isOtherDesk");
            java.lang.reflect.Field animation = helper.getDeclaredField("mAnimType");
            java.lang.reflect.Field activityType = target.getDeclaredField("activityType");
            animation.setAccessible(true);
            activityType.setAccessible(true);
            AtomicBoolean reported = new AtomicBoolean();
            AtomicBoolean failed = new AtomicBoolean();
            XposedHelpers.findAndHookMethod(helper, "isAppTarget", int.class, target,
                    new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (failed.get() || param.hasThrowable() || !Boolean.TRUE.equals(param.getResult())) return;
                    try {
                        // Translucent/finishing activities can include the underlying Home
                        // in the CLOSING targets. Home must not shrink/fade with that app.
                        if (activityType.getInt(param.args[1]) == 2
                                && "SWIPE_TO_HOME".equals(String.valueOf(animation.get(param.thisObject)))
                                && Boolean.TRUE.equals(isOtherDesk.invoke(null))) {
                            param.setResult(false);
                            if (reported.compareAndSet(false, true)) {
                                HookSettings.log(TAG + "excluded underlying Home from closing-app animation");
                            }
                        }
                    } catch (Throwable error) {
                        reportFailure(failed, "home target", error);
                    }
                }
            });
            HookSettings.log(TAG + "installed home target hook");
        } catch (Throwable error) {
            HookSettings.log(TAG + "unsupported home target API; no home target hook installed");
            HookSettings.log(error);
        }
    }

    private static void installSurfaceHook(ClassLoader loader) {
        try {
            Class<?> handler = XposedHelpers.findClass(
                    "com.android.quickstep.FallbackSwipeHandler", loader);
            Class<?> properties = XposedHelpers.findClass(
                    "com.android.quickstep.util.SurfaceTransaction$SurfaceProperties", loader);
            Class<?> target = XposedHelpers.findClass(
                    "com.android.systemui.shared.system.RemoteAnimationTargetCompat", loader);
            Method show = properties.getDeclaredMethod("setShow");
            show.setAccessible(true);
            Method transform = handler.getDeclaredMethod("setHomeScaleAndAlpha",
                    properties, target, float.class, float.class);
            AtomicBoolean reported = new AtomicBoolean();
            AtomicBoolean failed = new AtomicBoolean();
            XposedBridge.hookMethod(transform, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.hasThrowable() || failed.get()) return;
                    try {
                        // Same leash and transaction as the original alpha/matrix update.
                        // ColorOS's setShow() checks surface validity before appending show.
                        show.invoke(param.args[0]);
                        if (reported.compareAndSet(false, true)) {
                            HookSettings.log(TAG + "fallback home surface visibility restored");
                        }
                    } catch (Throwable error) {
                        reportFailure(failed, "surface", error);
                    }
                }
            });
            HookSettings.log(TAG + "installed surface hook v1.4");
        } catch (Throwable error) {
            HookSettings.log(TAG + "unsupported surface API; no surface hook installed");
            HookSettings.log(error);
        }
    }

    private static void installHomeKeyHook(ClassLoader loader) {
        try {
            Class<?> service = XposedHelpers.findClass(
                    "com.android.quickstep.OplusBaseTouchInteractionService", loader);
            Class<?> manager = XposedHelpers.findClass(
                    "com.android.systemui.shared.system.ActivityManagerWrapper", loader);
            Method getInstance = manager.getDeclaredMethod("getInstance");
            Method getRunningTask = manager.getDeclaredMethod("getRunningTask");
            AtomicBoolean reported = new AtomicBoolean();
            AtomicBoolean failed = new AtomicBoolean();
            XposedHelpers.findAndHookMethod(service, "simulateUpSlide", boolean.class,
                    new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!Boolean.TRUE.equals(param.args[0])) return;
                    try {
                        Context context = (Context) param.thisObject;
                        KeyguardManager keyguard = context.getSystemService(KeyguardManager.class);
                        if (keyguard == null || keyguard.isKeyguardLocked()) return;
                        Object observer = XposedHelpers.getObjectField(param.thisObject,
                                "mOverviewComponentObserver");
                        if (observer == null) return; // Service initialization is not complete.
                        if ((boolean) XposedHelpers.callMethod(observer, "isHomeAndOverviewSame")) return;
                        Object device = XposedHelpers.callMethod(param.thisObject, "getMDeviceState");
                        if (!(boolean) XposedHelpers.callMethod(device, "isButtonNavMode")) return;
                        Object animations = XposedHelpers.callMethod(param.thisObject,
                                "getMTaskAnimationManager");
                        // Leave in-flight animation completion/interruption to the OEM.
                        if ((boolean) XposedHelpers.callMethod(animations, "isRecentsAnimationRunning")) return;
                        Intent home = (Intent) XposedHelpers.callMethod(observer, "getHomeIntent");
                        ComponentName component = home.getComponent();
                        ActivityManager.RunningTaskInfo task = (ActivityManager.RunningTaskInfo)
                                getRunningTask.invoke(getInstance.invoke(null));
                        if (component == null || "com.android.launcher".equals(component.getPackageName())) return;
                        boolean skipAnimation = HookSettings.skipButtons();
                        if (!skipAnimation && (task == null || !component.equals(task.topActivity))) return;
                        // Default: preserve the existing Home-to-Home fix and launcher actions.
                        // Optional: bypass the simulated app-exit animation for button Home too.
                        Intent intent = new Intent(home);
                        if (skipAnimation) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                            context.startActivity(intent,
                                    ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle());
                        } else {
                            context.startActivity(intent);
                        }
                        param.setResult(null);
                        if (reported.compareAndSet(false, true)) {
                            HookSettings.log(TAG + "dispatched Home without simulated exit animation");
                        }
                    } catch (Throwable error) {
                        // No successful dispatch: run the unmodified OEM implementation.
                        if (failed.compareAndSet(false, true)) {
                            HookSettings.log(TAG + "home key check failed; using OEM behavior and retrying next time");
                            HookSettings.log(error);
                        }
                    }
                }
            });
            HookSettings.log(TAG + "installed home key hook v1.4");
        } catch (Throwable error) {
            HookSettings.log(TAG + "unsupported home key API; no home key hook installed");
            HookSettings.log(error);
        }
    }

    private static void reportFailure(AtomicBoolean failed, String hook, Throwable error) {
        if (failed.compareAndSet(false, true)) {
            HookSettings.log(TAG + "disabled " + hook + " patch after incompatible API");
            HookSettings.log(error);
        }
    }
}
