package io.github.xieincz.homevisibility;

import android.app.Activity;
import android.app.LocaleManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;

/** Use Android's per-app language setting where available, with an API 30–32 fallback. */
final class AppLanguage {
    private static final String KEY = "language";
    static String selected(Context context) {
        if (Build.VERSION.SDK_INT >= 33) {
            return context.getSystemService(LocaleManager.class).getApplicationLocales().toLanguageTags();
        }
        return ModuleSettings.preferences(context).getString(KEY, "");
    }

    static Context wrap(Context context) {
        if (Build.VERSION.SDK_INT >= 33) return context;
        String language = selected(context);
        if (language.isEmpty()) return context;
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.setLocales(LocaleList.forLanguageTags(language));
        return context.createConfigurationContext(configuration);
    }

    static boolean select(Activity activity, String language) {
        if (Build.VERSION.SDK_INT >= 33) {
            // Android persists this selection and recreates the Activity as needed.
            activity.getSystemService(LocaleManager.class)
                    .setApplicationLocales(LocaleList.forLanguageTags(language));
            return true;
        }
        if (!ModuleSettings.preferences(activity).edit().putString(KEY, language).commit()) return false;
        activity.recreate();
        return true;
    }
}
