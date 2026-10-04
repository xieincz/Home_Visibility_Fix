package io.github.xieincz.homevisibility;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

/** Three public, non-sensitive flags. Only this application's UI can write them. */
public final class ModuleSettings {
    public static final Uri URI = Uri.parse("content://io.github.xieincz.homevisibility.settings");
    public static final String LOGS = "logs";
    public static final String BUTTONS = "skip_buttons";
    public static final String GESTURES = "skip_gestures";

    private ModuleSettings() {}

    public static SharedPreferences preferences(Context context) {
        return context.createDeviceProtectedStorageContext()
                .getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    public static Bundle read(Context context) {
        SharedPreferences prefs = preferences(context);
        Bundle result = new Bundle();
        result.putBoolean(LOGS, prefs.getBoolean(LOGS, true));
        result.putBoolean(BUTTONS, prefs.getBoolean(BUTTONS, false));
        result.putBoolean(GESTURES, prefs.getBoolean(GESTURES, false));
        return result;
    }
}
