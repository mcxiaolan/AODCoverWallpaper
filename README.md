# 外屏 AOD 壁纸 / Cover AOD Wallpaper

开启「息屏提醒」时，允许在任意「何时显示」模式下开启「在外屏上显示壁纸」。

- 目标设备：Galaxy Z Flip5 (SM-F731N)，Android 17 / One UI 9.0.0
- 目标应用：AlwaysOnDisplay 8.9.66.1 (`com.samsung.android.app.aodservice`)
- 模块形态：libxposed Modern API 102（LSPosed 2.2.x）
- 构建产物：`build/AODCoverWallpaper.apk`

## 问题

「在外屏上显示壁纸」只在「何时显示 = 点击来显示」时可用；选「始终显示 / 自动 / 按计划」时，
外屏息屏后只有时钟和通知，背景纯黑。

## 根因

外屏 AOD 界面在 display 1 的 `SubScreenPresentation` 窗口里（标题 `SubScreenLockWindow`，
type `NOTIFICATION_SHADE`）。壁纸图层 `Wallpaper BBQ wrapper 17_sub|system` 存在，
但 SurfaceFlinger 报告：

```
invisible reason=hidden by parent or layer flag
```

因为该窗口从未带上 `FLAG_SHOW_WALLPAPER (0x100000)`。这条链路有三处断点：

### 断点 1：设置项被隐藏

`aod.g7#H()` 决定 `prefsShowCoverWallpaper` 等三行的可见性：

```java
switchPreference.D(ha6.R && i0().l());
```

而 `SettingsHelper#l()` 只判断 `aod_tap_to_show_mode == 1`。

### 断点 2：壁纸标志不会被添加

`aod.PluginAODImpl#applyAODFlags` 只在 AOD policy 认为「环境壁纸已启用」时才加标志：

```java
z5 z5Var = q6Var.a;
if (z5Var != null && z5Var.f) {
    layoutParams.flags |= 0x100000;
}
```

`aod.z5#f` 来自 `aod.a6#d`：

```java
z10 = aODConfiguration.x && iB != 2 && iB != 4;
z5Var.f = z10;
```

其中 `aODConfiguration.x = uICallback.isWonderLandAmbientWallpaperEnabled()`，
只有壁纸组件是 WonderLand 服务时才为 true。本机壁纸是
`LayeredWallpaperService`，所以恒为 false。

### 断点 3：AOD 容器涂黑背景

`com.samsung.android.app.aodservice.AlwaysOnDisplayEx` 在构建外屏 UI 时：

```java
FrameLayout c = (FrameLayout) root.findViewWithTag("aod_overlay_container");
if (c != null) {
    c.setBackgroundColor(0xFF000000);   // 黑底
}
```

这层黑底盖在壁纸图层之上。

### 断点 4：外屏息屏亮度和遮罩动画

外屏息屏亮度应由息屏提醒原生策略控制；前景 `aod_front_scrim` 使用黑色渐变，
但不应完全遮黑壁纸。此前强制最高亮度并将遮罩全透明，破坏了点击模式原有的闲置调暗行为。

## 实现

### 代码结构

```
com.lantianya.aodcoverwallpaper
├── hook/AodHook.java        模块入口与全部 hook
├── compat/Compat.java       兼容性判定、基线与状态存取
└── ui/SettingsActivity.java One UI 风格设置界面
```

### hook 清单

| 标识 | Hook 点 | 作用 | 关键 |
|---|---|---|---|
| `row_visible` | `Preference#D(boolean)` | 设置项始终可见 | ✓ |
| `decision` | `aod.yv7#invokeSuspend` | 强制外屏渲染决策 | |
| `cover_apply` | `aod.xv7#invokeSuspend` | 把决策应用到窗口 | |
| `policy_flag` | `PluginAODImpl#applyAODFlags` | 强制 AOD policy 标志 | |
| `window_flag` | `ViewRootImpl#setView` | 补 `FLAG_SHOW_WALLPAPER` | ✓ |
| `backdrop` | `View#setBackgroundColor` | 壁纸底层黑色透明化，前景调暗遮罩限制为最高 60% 不透明度 | ✓ |

`aod.*` 是插件类，由 SystemUI 通过独立 classloader 加载，
因此后三个通过监听 `BaseDexClassLoader#findClass` 在插件出现后补挂。

全部 hook 以「外屏壁纸开关已打开」为前提，关掉即回到官方行为；
所有改动都在内存中进行，不写系统设置、不修改系统分区。
外屏实际亮度继续使用三星原生息屏策略，因此自动亮度和闲置降亮度不被覆盖；受限幅的壁纸调暗遮罩也适用于任意「何时显示」模式。

### 设置界面

One UI 风格（原生 Android View，无第三方 UI 依赖），使用大标题、分组圆角卡片和深浅色主题，包含：

- 基线匹配状态和设备信息：型号、Android 版本、One UI 版本、息屏提醒版本、外屏支持
- **与验证基线的差异提示** —— 换机型、换系统版本、换 One UI 版本或换息屏提醒版本时，
  明确列出哪一项偏离基线，提示可能不适配
- 关于：模块版本、验证基线、作用范围

### 验证基线

设置页只比较设备环境，不判断模块是否已生效。验证基线如下：

| 项目 | 基线值 |
|---|---|
| 设备型号 | SM-F731N |
| Android API | 37 |
| One UI | 9.0.0 (90000) |
| 息屏提醒 | 8.9.66.1 |

安装到其它环境时，界面会逐条列出偏离项。这些 hook 目标都是被混淆的私有实现，
跨版本很可能失效，因此提示而非静默。

### 国际化

简体中文（`values-zh/`，并作为默认文案）+ 英文（`values-en/`），语言资源键保持一致；系统首选语言为中文时，即使后续语言包含英语也会优先使用中文。

## 构建

Linux（x86_64）和 WSL：

```bash
bash build.sh
```

首次运行会将 `tools/dl/` 中的 Linux JDK 17 与 Android Build Tools 34.0.0
解压到 `tools/linux/`，之后可离线重复构建。Android 34 平台和 libxposed API 沿用项目内文件。

Windows：

```
build.bat
```

依赖（全部位于 `tools/`，不污染系统）：

- `tools/jdk17/jdk-17.0.21+9` — Temurin JDK 17
- `tools/sdk/` — Android SDK（platforms;android-34, build-tools;34.0.0）
- `tools/libxposed-ext/classes.jar` — libxposed API 102

`build.sh` 与 `build.bat` 都用 aapt2 → javac → d8 → zipalign → apksigner 直接产出签名 APK，不依赖 Gradle。
本地签名密钥 `tools/keystore.jks` 不纳入版本控制；公开发布前应换成自己的私钥。
Linux/WSL 可通过 `OUT_DIR=/tmp/aod-build bash build.sh` 将产物写到指定目录，避免 Windows 文件权限阻挡清理 `build/`。

## 安装

```bash
adb install -r build/AODCoverWallpaper.apk
lspctl module enable --user=0 com.lantianya.aodcoverwallpaper
lspctl scope add --user=0 com.lantianya.aodcoverwallpaper com.android.systemui
lspctl scope add --user=0 com.lantianya.aodcoverwallpaper com.samsung.android.app.aodservice
adb shell "kill -9 $(pidof com.android.systemui)"
```

启用后重启 SystemUI 使 hook 注入；AOD 应用重启后设置项才会出现。

## 验证结果

既有壁纸显示功能均在**合盖 + 息屏**状态下用 `screencap -d <外屏 displayId>` 截图确认。
4.1 新增的遮罩限幅及原生亮度恢复尚未在设备上验证：

| 模式 | 结果 | 截图 |
|---|---|---|
| 始终显示 | 壁纸 + 时钟 + 天气 + 电量 正常 | `work/verify_final.png` |
| 自动 | 壁纸正常 | `work/verify_auto.png` |
| 按计划（时段内） | 壁纸正常 | `work/mode_schedule2.png` |
| 按计划（时段外） | 全黑（官方预期，AOD 不在计划时段） | — |
| 新通知（无通知） | 黑屏（官方预期） | — |

## 踩坑记录

1. **`Preference#D` 的 key 字段要沿继承链查**
   实际对象是 `SwitchPreference` / `PreferenceCategory` / `UnClickablePreference` 等子类，
   `getDeclaredField("z")` 在子类上找不到（字段声明在 `Preference` 基类），
   必须用 `getSuperclass()` 向上遍历。否则 hook 装上了却对目标行完全不生效。

2. **`Window.setAttributes` 与 `SubScreenPresentation#onCreate` 都不是有效拦截点**
   窗口属性只在创建时提交一次，Presentation 是长期复用实例，两个 hook 都不会再触发。
   有效的是 `ViewRootImpl#setView`。

3. **合盖才能截外屏**
   展开状态下外屏本身关闭，`screencap -d <外屏 id>` 必然是全黑，不要据此判断模块失效。
   外屏 displayId 用 `dumpsys SurfaceFlinger --display-id` 查询（本机为 `4630947181303254916`）。
