package io.github.xieincz.homevisibility;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Bundle;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.Toast;

public final class SettingsActivity extends Activity {
    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppLanguage.wrap(base));
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.settings);
        LinearLayout content = findViewById(R.id.content);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        content.setOnApplyWindowInsetsListener((view, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(padding + bars.left, padding + bars.top,
                    padding + bars.right, padding + bars.bottom);
            return insets;
        });
        bind(R.id.logs, ModuleSettings.LOGS, true);
        bind(R.id.buttons, ModuleSettings.BUTTONS, false);
        bind(R.id.gestures, ModuleSettings.GESTURES, false);
        findViewById(R.id.language).setOnClickListener(view -> showLanguagePicker());
        Button about = findViewById(R.id.about);
        about.setOnClickListener(view -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/xieincz/Home_Visibility_Fix")));
            } catch (ActivityNotFoundException error) {
                Toast.makeText(this, R.string.no_browser, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        // Preferences, rather than an older Activity/View instance, own toggle state.
        SharedPreferences prefs = ModuleSettings.preferences(this);
        ((Switch) findViewById(R.id.logs)).setChecked(prefs.getBoolean(ModuleSettings.LOGS, true));
        ((Switch) findViewById(R.id.buttons)).setChecked(prefs.getBoolean(ModuleSettings.BUTTONS, false));
        ((Switch) findViewById(R.id.gestures)).setChecked(prefs.getBoolean(ModuleSettings.GESTURES, false));
    }

    private void showLanguagePicker() {
        String[] tags = {"", "zh-CN", "en"};
        String[] labels = {getString(R.string.language_system),
                getString(R.string.language_chinese), getString(R.string.language_english)};
        String current = AppLanguage.selected(this);
        int selected = current.startsWith("zh") ? 1 : current.startsWith("en") ? 2 : 0;
        new AlertDialog.Builder(this).setTitle(R.string.language)
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    dialog.dismiss();
                    if (!AppLanguage.select(this, tags[which])) {
                        Toast.makeText(this, R.string.save_failed, Toast.LENGTH_LONG).show();
                    }
                }).setNegativeButton(R.string.cancel, null).show();
    }

    private void bind(int id, String key, boolean defaultValue) {
        Switch toggle = findViewById(id);
        SharedPreferences prefs = ModuleSettings.preferences(this);
        toggle.setSaveEnabled(false);
        toggle.setChecked(prefs.getBoolean(key, defaultValue));
        boolean[] restoring = {false};
        toggle.setOnCheckedChangeListener((button, checked) -> {
            if (restoring[0] || prefs.getBoolean(key, defaultValue) == checked) return;
            if (prefs.edit().putBoolean(key, checked).commit()) {
                getContentResolver().notifyChange(ModuleSettings.URI, null);
            } else {
                // A failed commit may still change SharedPreferences in memory.
                prefs.edit().putBoolean(key, !checked).commit();
                restoring[0] = true;
                toggle.setChecked(!checked);
                restoring[0] = false;
                Toast.makeText(this, R.string.save_failed, Toast.LENGTH_LONG).show();
            }
        });
    }
}
