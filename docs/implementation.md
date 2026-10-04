# 修复原理与验证

分析与验证环境：Android 16 / ColorOS，系统桌面 16.4.5（160040005），LSPosed 2.2.0（7854），第三方默认桌面为 Nova。其他系统桌面版本需另行验证。

## 四个修复点

### 从应用返回桌面：补齐图层显示操作

第三方桌面作为默认 Home 时，`OverviewComponentObserver` 选用 `FallbackActivityInterface`，返回动画由 `FallbackSwipeHandler` 处理。该版本的三键 Home 也会进入此动画路径。

`FallbackSwipeHandler.handleTaskAppeared()` 将 Home target 交给 `FallbackHomeAnimationFactory` 后返回 false。Oplus 父类 `OplusBaseSwipeUpHandler.onTasksAppeared()` 因此提前返回，跳过原本执行的 `setAlpha(1).setShow()`。

Fallback 分支的 `setHomeScaleAndAlpha(SurfaceProperties, RemoteAnimationTargetCompat, float, float)` 只设置 matrix 和 alpha，没有 show。Frida 跟踪显示 alpha 已升至 1，画面仍只有壁纸，直到整个返回动画结束才显示桌面。

模块在该方法成功返回后调用参数的 `setShow()`，把操作追加到**原来的同一笔 SurfaceControl 事务**。不强制结束动画，不改变时长、透明度、缩放或层级，也不另建事务。

### 桌面内再按 Home：不把桌面自身做成退出动画

只补图层可见性，无法解决这个场景：三键模式已经位于 Nova，按 Home 时 ColorOS 的 `OplusBaseTouchInteractionService.simulateUpSlide()` 仍会生成模拟退出动画。其自身的“系统桌面已恢复”检查不能正确覆盖第三方默认桌面。

模块在满足以下条件时，派发系统原本的 Home 意图并跳过模拟退出：

- 调用来自 Home（`simulateUpSlide(true)`）；
- 设备已解锁，处于三键导航；
- Home 与系统最近任务组件不同；
- 没有正在运行的最近任务动画；
- 实时查询到的前台 Activity **完整组件名**等于当前默认 Home，且不是系统桌面。

仍然调用 `startActivity(new Intent(homeIntent))`，让第三方桌面收到正常的 `onNewIntent`，保留预览、返回主屏、关闭文件夹等用户配置动作。没有硬编码 Nova 包名，没有常驻轮询，也不需要开机主动启动系统桌面。

### 从 LSPosed 返回：排除误归类的 Home target

LSPosed 内嵌管理器运行于 `com.android.shell/.BugreportWarningActivity`。从该界面返回时，动画目标里除了应用，还有 `activityType=HOME`、`mode=CLOSING` 的 Nova 图层。厂商 `RectTransformHelper.isAppTarget()` 仅按 mode 判断，使 Home 与退出应用一起缩小、淡出；单独补 show 无法避免这一点。

新增 hook 仅在 `SWIPE_TO_HOME`、第三方桌面、原判断为 true 且 target 的 activityType 为 HOME 时，返回 false，让它走原本的非退出应用分支。没有按 LSPosed 或 Nova 包名特判，也不影响打开应用的动画。

### 切换导航后的首次 Home：保留跨配置重建的手势

导航模式变化使后台 `RecentsActivity` 挂起一次配置重建。首次 Home 创建的 `FallbackSwipeHandler` 先绑定了旧 Activity；旧 Activity 随后因 `handleRelaunchActivityInner` 被销毁。`AbsSwipeUpHandler$1.onActivityDestroyed()` 无条件注销 init listener、取消动画并 reset handler。随后控制器才到达，`FinishImmediatelyHandler` 将动画结束到最近任务界面；新 Activity 的初始状态 `BG_LAUNCHER` 只有壁纸，没有启动第三方 Home。

新增 hook 只在以下条件全部满足时保留手势：

- Activity 正因配置变化销毁，且是该 handler 当前绑定的 Activity；
- handler 是 `FallbackSwipeHandler`，尚未收到 RecentsAnimationController；
- handler 没有失效，ActivityInitListener 仍然注册。

清理旧 Activity 的界面监听、解除旧 Activity 引用，按厂商 `onActivityInit()` 的重绑定路径重建状态回调并去掉 `LAUNCHER_UI_STATES`，保留手势状态及 ActivityTracker 注册。新 Activity 创建时，通过原本的 init listener 自然接续同一次手势。没有定时器、二次 Home、开机启动桌面或修改导航设置的运行时代码。其他销毁原因和已有控制器的手势仍由厂商逻辑处理。

静态状态字段只在生命周期回调执行时读取，安装 hook 时仅解析字段，避免 Application 尚未初始化便触发厂商静态初始化。

## 兼容与失败处理

只加载到 `com.android.launcher` 主进程。精确匹配厂商类和方法，找不到时记录日志并保留对应原行为。原图层方法抛异常时不补操作；`setShow()` 自身检查图层有效性。

Home 检查在服务初始化未完成时直接放行；临时查询失败时运行原实现，并在下一次 Home 重新尝试。正在进行的动画和锁屏场景交还给原逻辑。系统默认 Home 的意图由系统桌面 observer 动态提供，不保存第二份启动器选择。

## 1.3 验证结果与范围

验证使用版本 1.3（versionCode 4）的 LSPosed 模块，未同时运行 Frida hook。测试前确认实际导航模式和前台界面，排除切换未成功或管理器未打开的无效轮次。

| 场景 | 结果 |
| --- | --- |
| 导航双向切换后首次 Home | 9 轮通过，包含正常重启后的首次操作及实际点击设置界面的操作 |
| 从 LSPosed 管理器返回 | 4 轮双模式通过，图标和小组件未闪回纯壁纸 |
| Nova 桌面内再次 Home | 保留预览/编辑动作，无纯壁纸空窗 |
| 切换后先进入最近任务 | 手势概览及三键概览正常，随后 Home 正常 |
| 重启后模块自动加载 | 四个 hook 自动安装并执行 |
| 构建与签名 | assembleDebug / lintDebug 成功，lint 为 0 errors / 5 warnings；APK v2 签名验证通过 |

测试结论限于上述设备环境和复现场景，未穷举所有应用、启动器、分屏、画中画及交互时序。模块针对系统返回动画中的问题，不保证解决应用自身冷启动或资源加载造成的等待。

## 1.4 设置与动画控制

新增原生设置 Activity，可从桌面图标或 LSPosed 模块设置入口打开。日志默认开启，按键、手势两个动画开关默认关闭。设置保存于模块私有的设备保护存储，避免系统桌面在解锁前读取到不同的配置。

`SettingsProvider` 只提供三个非敏感布尔配置的读取，不接受外部写入。系统桌面进程在 Application attach 后读取配置，并通过 ContentObserver 接收更新；动画逐帧 hook 只读内存快照，不进行跨进程查询或文件读取。日志统一经过 `HookSettings.log()`，关闭后包括错误日志在内均停止输出；不控制 LSPosed 框架和厂商日志。

按键开关开启时，在第三方默认桌面、解锁、三键模式且无在途最近任务动画的条件下，直接派发带 `FLAG_ACTIVITY_NO_ANIMATION` 和零动画 ActivityOptions 的 Home 意图，跳过模拟退出动画。关闭时保留原有仅针对桌面内 Home 的修复。

手势开关仅处理 `FallbackSwipeHandler`、全面屏手势模式、目标为 HOME 的松手收尾路径：将动画时长设为零，调用厂商 `endRunningWindowAnim(false)` 成功结束窗口动画，并完成并行动画。Fallback 独立的 Home 透明度和缩放动画也直接设到终值，避免桌面内返回的弹簧动画忽略零时长。仍执行正常的 Home 创建、图层与结束回调；不以取消动画代替完成，不改变 RECENTS / LAST_TASK 等目标。

界面支持跟随系统、简体中文和英语。Android 13 及以上使用系统的应用语言 API，Android 11–12 使用界面 Configuration 覆盖；语言选择不改动三个功能开关。模块描述使用本地化字符串资源，由展示它的宿主按资源配置加载。关于按钮以 ACTION_VIEW 打开项目的 GitHub 地址。

1.4 已在上述 Android 16 / ColorOS 环境验证：三个开关的默认值、配置即时生效及进程重启后的持久化；关闭日志后连续 Home 和重启系统桌面不产生模块日志，重新开启后恢复；按键与手势开关分别触发对应路径；开启手势跳过时从应用、LSPosed 返回及进入最近任务正常。语言可切换为英语、简体中文或跟随系统，英语选择在应用重启后保留；关于按钮已确认向浏览器发送指定 GitHub 地址。构建、lint 和 APK v2 签名验证通过。Android 11–12 的语言兼容分支尚未进行真机验证。
