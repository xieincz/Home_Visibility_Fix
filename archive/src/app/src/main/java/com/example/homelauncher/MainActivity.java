package com.example.homelauncher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "HomeLauncherPrefs";
    private static final String KEY_PACKAGE = "selected_launcher_package";
    private static final String KEY_CLASS = "selected_launcher_class";
    private static final String DEFAULT_VALUE = "system_default";

    private ListView launcherListView;
    private TextView currentLauncherText;
    private List<LauncherInfo> launchers;
    private LauncherAdapter adapter;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE);

        // 确保 SharedPreferences 文件权限正确（让 Xposed 模块能读取）
        makePrefsWorldReadable();

        currentLauncherText = findViewById(R.id.currentLauncherText);
        launcherListView = findViewById(R.id.launcherList);

        loadLaunchers();
        setupListView();
        updateCurrentLauncherDisplay();
    }

    /**
     * 设置 SharedPreferences 文件为全局可读
     * 这样 Xposed 模块才能从 system_server 进程读取
     */
    private void makePrefsWorldReadable() {
        try {
            // 获取 SharedPreferences 文件路径
            java.io.File prefsDir = new java.io.File(getApplicationInfo().dataDir, "shared_prefs");
            java.io.File prefsFile = new java.io.File(prefsDir, PREFS_NAME + ".xml");

            if (prefsFile.exists()) {
                // 设置文件权限为 0644 (rw-r--r--)
                prefsFile.setReadable(true, false);
                // android.util.Log.d("HomeLauncherRedirect", "Set prefs file world readable: " + prefsFile.getAbsolutePath());
            }

            // 同时设置目录权限
            if (prefsDir.exists()) {
                prefsDir.setReadable(true, false);
                prefsDir.setExecutable(true, false);
            }
        } catch (Exception e) {
            // android.util.Log.e("HomeLauncherRedirect", "Failed to set prefs world readable", e);
        }
    }

    /**
     * 加载所有可用的启动器
     */
    private void loadLaunchers() {
        launchers = new ArrayList<>();

        // 添加"系统默认"选项
        LauncherInfo systemDefault = new LauncherInfo();
        systemDefault.name = getString(R.string.system_default);
        systemDefault.packageName = DEFAULT_VALUE;
        systemDefault.className = DEFAULT_VALUE;
        systemDefault.isSystemDefault = true;
        launchers.add(systemDefault);

        // 查询所有启动器
        PackageManager pm = getPackageManager();
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);

        // 不使用限制性标志，获取所有有 HOME intent filter 的应用
        // MATCH_ALL 在 API 23+ 可用，这里用 0 表示获取所有
        int flags = 0;
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(homeIntent, flags);

        // android.util.Log.d("HomeLauncherRedirect", "Found " + resolveInfos.size() + " potential launchers");

        for (ResolveInfo resolveInfo : resolveInfos) {
            String packageName = resolveInfo.activityInfo.packageName;
            String activityName = resolveInfo.activityInfo.name;

            // android.util.Log.d("HomeLauncherRedirect", "Checking: " + packageName + "/" + activityName);

            // 过滤掉不是真正启动器的应用
            if (shouldFilterOut(packageName, activityName, resolveInfo)) {
                // android.util.Log.d("HomeLauncherRedirect", "Filtered out: " + packageName);
                continue;
            }

            LauncherInfo info = new LauncherInfo();
            info.name = resolveInfo.loadLabel(pm).toString();
            info.packageName = packageName;
            info.className = activityName;
            info.icon = resolveInfo.loadIcon(pm);
            info.isSystemDefault = false;

            launchers.add(info);
            // android.util.Log.d("HomeLauncherRedirect", "Added launcher: " + info.name + " (" + packageName + ")");
        }

        // 按名称排序（系统默认除外）
        Collections.sort(launchers, new Comparator<LauncherInfo>() {
            @Override
            public int compare(LauncherInfo o1, LauncherInfo o2) {
                if (o1.isSystemDefault) return -1;
                if (o2.isSystemDefault) return 1;
                return o1.name.compareToIgnoreCase(o2.name);
            }
        });

        // android.util.Log.d("HomeLauncherRedirect", "Total launchers loaded: " + (launchers.size() - 1));

        if (launchers.size() <= 1) {
            Toast.makeText(this, R.string.no_launchers_found, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 判断是否应该过滤掉该应用
     */
    private boolean shouldFilterOut(String packageName, String activityName, ResolveInfo resolveInfo) {
        String lowerPackageName = packageName.toLowerCase();
        String lowerActivityName = activityName.toLowerCase();

        // 过滤掉系统 resolver
        if (packageName.equals("android") || lowerPackageName.contains("resolver")) {
            // android.util.Log.d("HomeLauncherRedirect", "Filtering: resolver - " + packageName);
            return true;
        }

        // 过滤掉设置应用（各种可能的包名）
        if (lowerPackageName.contains("settings") ||
            lowerPackageName.contains("setting") ||
            lowerPackageName.equals("com.android.settings")) {
            // android.util.Log.d("HomeLauncherRedirect", "Filtering: settings app - " + packageName);
            return true;
        }

        // 过滤掉壁纸选择器
        if (lowerPackageName.contains("wallpaper") || lowerPackageName.contains("livepicker")) {
            // android.util.Log.d("HomeLauncherRedirect", "Filtering: wallpaper - " + packageName);
            return true;
        }

        // 检查 activity 名称，过滤掉明显不是启动器的
        if (lowerActivityName.contains("settings") ||
            lowerActivityName.contains("config") ||
            lowerActivityName.contains("setup") ||
            lowerActivityName.contains("preference")) {
            // android.util.Log.d("HomeLauncherRedirect", "Filtering: non-launcher activity - " + activityName);
            return true;
        }

        // 检查是否是合法的启动器（必须有 CATEGORY_HOME）
        if (resolveInfo.activityInfo == null) {
            // android.util.Log.d("HomeLauncherRedirect", "Filtering: no activity info - " + packageName);
            return true;
        }

        // 过滤掉本应用自己
        if (packageName.equals(getPackageName())) {
            // android.util.Log.d("HomeLauncherRedirect", "Filtering: self - " + packageName);
            return true;
        }

        return false;
    }

    /**
     * 设置 ListView
     */
    private void setupListView() {
        adapter = new LauncherAdapter(this, launchers);
        launcherListView.setAdapter(adapter);

        launcherListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                LauncherInfo selected = launchers.get(position);
                saveLauncherPreference(selected);
                updateCurrentLauncherDisplay();
                adapter.notifyDataSetChanged();

                Toast.makeText(MainActivity.this,
                        getString(R.string.launcher_selected, selected.name),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 保存用户选择的启动器
     */
    private void saveLauncherPreference(LauncherInfo launcher) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_PACKAGE, launcher.packageName);
        editor.putString(KEY_CLASS, launcher.className);
        editor.commit(); // 使用 commit 而不是 apply 确保立即写入

        // android.util.Log.d("HomeLauncherRedirect", "Saved preference: " + launcher.packageName + "/" + launcher.className);

        // 保存后立即设置文件权限
        makePrefsWorldReadable();
    }

    /**
     * 获取当前选择的启动器包名
     */
    private String getCurrentSelectedPackage() {
        return prefs.getString(KEY_PACKAGE, DEFAULT_VALUE);
    }

    /**
     * 更新当前选择的显示
     */
    private void updateCurrentLauncherDisplay() {
        String selectedPackage = getCurrentSelectedPackage();

        for (LauncherInfo launcher : launchers) {
            if (launcher.packageName.equals(selectedPackage)) {
                currentLauncherText.setText(launcher.name);
                if (launcher.icon != null) {
                    currentLauncherText.setCompoundDrawablesWithIntrinsicBounds(
                            launcher.icon, null, null, null);
                }
                return;
            }
        }

        currentLauncherText.setText(R.string.system_default);
    }

    /**
     * ListView 适配器
     */
    private class LauncherAdapter extends ArrayAdapter<LauncherInfo> {
        private final Context context;
        private final List<LauncherInfo> items;

        public LauncherAdapter(Context context, List<LauncherInfo> items) {
            super(context, R.layout.launcher_item, items);
            this.context = context;
            this.items = items;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;

            if (convertView == null) {
                LayoutInflater inflater = LayoutInflater.from(context);
                convertView = inflater.inflate(R.layout.launcher_item, parent, false);

                holder = new ViewHolder();
                holder.icon = convertView.findViewById(R.id.launcherIcon);
                holder.name = convertView.findViewById(R.id.launcherName);
                holder.packageName = convertView.findViewById(R.id.launcherPackage);
                holder.indicator = convertView.findViewById(R.id.selectedIndicator);

                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            LauncherInfo launcher = items.get(position);

            // 设置图标
            if (launcher.icon != null) {
                holder.icon.setImageDrawable(launcher.icon);
            } else {
                holder.icon.setImageResource(R.drawable.ic_launcher);
            }

            // 设置名称
            holder.name.setText(launcher.name);

            // 设置包名
            if (launcher.isSystemDefault) {
                holder.packageName.setText(R.string.settings_description);
            } else {
                holder.packageName.setText(launcher.packageName);
            }

            // 设置选中标识
            String currentPackage = getCurrentSelectedPackage();
            if (launcher.packageName.equals(currentPackage)) {
                holder.indicator.setVisibility(View.VISIBLE);
            } else {
                holder.indicator.setVisibility(View.INVISIBLE);
            }

            return convertView;
        }
    }

    /**
     * ViewHolder 模式优化
     */
    private static class ViewHolder {
        ImageView icon;
        TextView name;
        TextView packageName;
        ImageView indicator;
    }

    /**
     * 启动器信息类
     */
    private static class LauncherInfo {
        String name;
        String packageName;
        String className;
        Drawable icon;
        boolean isSystemDefault;
    }
}
