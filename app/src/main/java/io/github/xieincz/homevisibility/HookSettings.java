package io.github.xieincz.homevisibility;

import android.app.Application;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import de.robv.android.xposed.XposedBridge;

/** In-memory snapshot: no file or Binder access in animation-frame hooks. */
final class HookSettings {
    private static volatile Bundle snapshot = defaults();
    private static Application context;

    private static Bundle defaults() {
        Bundle result = new Bundle();
        result.putBoolean(ModuleSettings.LOGS, true);
        return result;
    }

    static void initialize(Application application) {
        context = application;
        // Subscribe before reading so a concurrent save cannot be missed.
        try {
            context.getContentResolver().registerContentObserver(ModuleSettings.URI, false,
                    new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override public void onChange(boolean selfChange) { reload(); }
            });
        } catch (RuntimeException error) { log(error); }
        reload();
    }

    static void reload() {
        if (context == null) return;
        try {
            Bundle values = context.getContentResolver().call(ModuleSettings.URI, "read", null, null);
            if (values != null) snapshot = values;
        } catch (RuntimeException error) {
            // Preserve the last successful snapshot if the provider is temporarily unavailable.
            log(error);
        }
    }

    static boolean skipButtons() { return snapshot.getBoolean(ModuleSettings.BUTTONS, false); }
    static boolean skipGestures() { return snapshot.getBoolean(ModuleSettings.GESTURES, false); }
    static void log(String message) {
        if (snapshot.getBoolean(ModuleSettings.LOGS, true)) XposedBridge.log(message);
    }
    static void log(Throwable error) {
        if (snapshot.getBoolean(ModuleSettings.LOGS, true)) XposedBridge.log(error);
    }
}
