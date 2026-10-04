# Home Visibility Fix

修复 ColorOS 第三方默认桌面返回时先显示纯壁纸、随后才出现图标和小组件的问题。

## 安装

1. 按下方构建说明生成并安装模块 APK。
2. 在 LSPosed 中启用 **Home Visibility Fix**，作用域只选 **系统桌面（com.android.launcher）**。
3. 禁用旧 Home Launcher Redirect 的 LSPosed 和 KernelSU 模块，保留自己原本的第三方默认桌面。
4. 重启手机，或重启系统桌面进程，使 hook 加载。后续开机自动生效。

已在 Android 16 / ColorOS 系统桌面 16.4.5 / LSPosed 2.2.0 / Nova Launcher 环境验证三键及全面屏手势返回。其他桌面版本需另行验证。

## 设置

在应用列表打开 **Home Visibility Fix**，或从 LSPosed 的模块设置入口进入。设置保存后立即生效，无需再次重启系统桌面。

| 设置 | 默认值 | 行为 |
| --- | --- | --- |
| 记录模块日志 | 开启 | 控制本模块的修复信息和错误日志，不影响 LSPosed 或系统自身的日志 |
| 跳过虚拟按键 Home 动画 | 关闭 | 开启后，按键 Home 直接返回第三方默认桌面 |
| 跳过全面屏手势 Home 动画 | 关闭 | 保留松手前的跟手操作，确认返回 Home 后立即完成收尾动画；不跳过进入最近任务的动画 |

两个动画开关相互独立，关闭时保留原有动画及壁纸空窗修复。桌面内 Home 的预览、返回主屏等动作仍由第三方桌面处理。

通过 **语言 / Language** 可选择跟随系统、简体中文或 English。设置文字、提示和 LSPosed 模块描述均提供中英文资源；模块名称保留 Home Visibility Fix。“关于”按钮打开[源代码仓库](https://github.com/xieincz/Home_Visibility_Fix)。

## 原理

模块针对四个问题做局部修复：在第三方 Home 返回动画的原图层事务中补上遗漏的 `setShow()`；在三键模式下已经位于第三方默认桌面时，正常派发 Home 意图，避免系统把桌面自身当成应用再做一次退出动画。另外，在返回动画中排除被误归类为退出应用的 Home 图层，并在导航切换触发最近任务界面重建时保留尚未收到动画控制器的手势，让它绑定到新界面。Nova 设置的 Home 预览、关闭文件夹等动作仍由 Nova 处理。详见 [实现分析与验证记录](docs/implementation.md)。

源码：[HomeVisibilityHook.java](app/src/main/java/io/github/xieincz/homevisibility/HomeVisibilityHook.java)。旧方案保存在 `archive/`，新模块使用不同包名，不覆盖旧应用或其数据。

## 构建

需要 JDK 17 或更高版本、Android SDK Platform 35 / Build Tools 35.0.0。

```sh
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleDebug :app:lintDebug
```

构建后生成 `app/build/outputs/apk/debug/app-debug.apk`，使用构建环境的 Android 调试密钥签名。安装命令：

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

更新已有安装时需要保持签名一致；不同签名的同包名 APK 不能直接覆盖安装。

## 日志与卸载

LSPosed 模块日志中检索 `HomeVisibilityFix`：

- `installed surface hook v1.4` / `installed home key hook v1.4`：对应 hook 已安装。
- `installed home target hook` / `installed configuration hook v1.4`：新增的两个 hook 已安装。
- `preserved fallback gesture across configuration recreation`：导航配置重建时保留手势。
- `excluded underlying Home from closing-app animation`：已纠正 Home 图层分类。
- `fallback home surface visibility restored`：已经实际执行过修复，每进程只记录一次。
- `dispatched Home without simulated exit animation`：已执行桌面内 Home 修复。
- `installed gesture animation hook v1.4` / `completed gesture Home animation immediately`：手势动画开关 hook 已安装 / 已执行跳过。
- `unsupported ... API`：对应桌面 API 不符合签名，该部分保持原行为。

在 LSPosed 禁用本模块后重启系统桌面进程或手机即可恢复原行为，随后可卸载模块。
