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
