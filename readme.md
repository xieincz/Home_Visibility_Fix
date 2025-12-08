# Home Launcher Redirect

## 📖 项目简介 | Introduction

**Home Launcher Redirect** 旨在修复 ColorOS 系统在使用第三方启动器（Third-party Launcher）作为默认桌面时存在的一个已知 Bug。

Bug 现象：

在 ColorOS 上将第三方启动器设置为默认桌面后，每次返回桌面（Home）时，系统会先显示约 1 秒钟的纯壁纸，随后才加载桌面图标和组件，造成明显的视觉割裂和延迟感。

本模块作用：

消除上述延迟。

------

## ⚙️ 测试环境 | Tested Environment

本模块已在以下环境中通过测试并确认有效：

- **系统版本**：ColorOS 16.0.1
- **Root 管理**：SukiSU Ultra 4.1.0
- **Xposed 框架**：LSPosed 1.10.2
- **导航方式**：虚拟按键（屏幕底部的三个按钮）

> **注意**：虽然本模块针对上述环境开发，但可能适用于相近版本的 ColorOS 或其他基于 Android 的定制系统，请自行测试。

------

## 🚀 安装与使用 | Installation & Usage

请严格按照以下步骤顺序进行操作，以确保模块生效：

### 0. 下载文件

1.  `app-release.apk` ： 
2.  `home_launcher_redirect.zip` ： 

### 1. 安装应用

下载并安装 `app-release.apk` 到您的手机。

### 2. 配置 LSPosed

1. 打开 LSPosed 管理器。
2. 找到并启用 **"Home Launcher Redirect"** 模块。
3. 勾选推荐的作用域：**系统桌面**

### 3. 配置模块设置

1. 打开刚刚安装的 "Home Launcher Redirect" 应用。
2. 在“可用启动器”列表中，**勾选您当前已经设置为系统默认**的第三方启动器（例如 Nova Launcher, Niagara Launcher 等）。

### 4. 刷入系统模块

1. 打开 **SukiSU Ultra**。
2. 刷入 `home_launcher_redirect.zip` 模块包。
3. 确保模块已启用。
4. **重启手机**。

------

## 🔗 背景信息 | References

关于 ColorOS 该 Bug 的详细讨论和相关信息，可参考以下链接：

- [Reddit: Anyone have found any work around for this Nova Launcher lag?](https://www.reddit.com/r/Oppo/comments/1kpg6cp/anyone_have_found_any_work_around_for_this_nova/)
- [Reddit: How to use third party launchers without lag?](https://www.reddit.com/r/oneplus/comments/1p33lfj/how_to_use_third_party_launchers_without_lag/)
- [Reddit: Fix for home button lag on third-party launchers](https://www.reddit.com/r/OppoFindN5Phone/comments/1p0ceio/fix_for_home_button_lag_on_thirdparty_launchers/?share_id=B7GmDDiIK6NjlnfMYExJ0)

------

## ⚠️ 免责声明 | Disclaimer

- 本模块涉及系统底层修改（Root/Xposed），刷入模块存在一定的风险（如无限循环启动动画、系统不稳等）。
- 请在操作前备份重要数据。
- 开发者不对因使用本模块导致的任何设备损坏或数据丢失负责。