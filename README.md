# 外屏 AOD 壁纸

LSPosed 模块：让 Galaxy Z Flip5 在任意「何时显示」模式下启用「在外屏上显示壁纸」。

## 功能

- 在「始终显示」「自动」「按计划」等模式下显示外屏壁纸。
- 保留「点击来显示」模式官方的壁纸压暗效果，并使常规压暗适用于其他模式。
- 可关闭模块添加的常规壁纸压暗。
- 可设置周期性壁纸变黑：仅息屏时运行，在壁纸层覆盖黑色约 2 秒；亮屏立即清除覆盖和当前倒计时，重新息屏后从完整间隔计时。时钟等内容不受该覆盖层影响。
- 设置界面提供中文、英文及深浅色样式。

息屏期间的定时任务可正常触发，但壁纸何时实际刷新由系统/AOD 显示管线控制，变黑效果可能延迟出现或不可见。此选项不能替代系统像素位移，也不保证防止烧屏。

## 兼容性

验证基线：Galaxy Z Flip5 `SM-F731N`、Android API 37、One UI 9.0.0、息屏提醒 `8.9.66.1`。实现依赖三星私有类和方法，其他设备或版本可能不适配；设置页会显示设备与基线的差异，但匹配基线不代表模块必定生效。

- 包名：`com.lantianya.aodcoverwallpaper`
- LSPosed 作用域：`com.android.systemui`、`com.samsung.android.app.aodservice`
- 最低 Android 版本：Android 10（API 29）

## GitHub Actions 构建

仓库包含 `.github/workflows/build.yml`。推送、Pull Request 或手动运行 `Build APK` 会准备 JDK 17、Android SDK 34 和 libxposed API 102，执行 `build.sh`，并上传 `AODCoverWallpaper.apk` Artifact。

未配置密钥时，Actions 每次运行都会生成临时自签名证书，因此不需要预先上传 keystore 即可构建和安装测试。每次签名不同，后续 APK 不能直接覆盖安装前一次构建。若要发布可持续升级的版本，在仓库 Actions Secrets 配置以下值，使用同一份私钥：

- `ANDROID_KEYSTORE_BASE64`：keystore 文件的 Base64 内容
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_PASSWORD`
- `ANDROID_KEY_ALIAS`

### 创建并配置持久签名密钥

在本地 Linux 或 WSL 终端创建 keystore。请把示例别名和密码换成自己的值；`keytool` 会交互式询问 keystore 密码及证书信息。提示输入密钥密码时，按回车可让它与 keystore 密码相同：

```bash
keytool -genkeypair -v \
  -keystore aod-release.jks \
  -alias aodcoverwallpaper \
  -keyalg RSA -keysize 2048 -validity 10000
```

妥善备份 `aod-release.jks` 和对应密码。丢失 keystore 后，无法再用同一签名发布可覆盖安装的升级版。不要把 keystore、密码或 Base64 文件提交到 GitHub 仓库，也不要把它们发给他人。

在创建 keystore 的同一终端目录中，将其编码为单行 Base64：

```bash
base64 -w 0 aod-release.jks > aod-release.jks.base64
```

打开 `aod-release.jks.base64`，复制全部内容作为 `ANDROID_KEYSTORE_BASE64` 的值。之后在 GitHub 仓库打开 **Settings → Secrets and variables → Actions**，点击 **New repository secret**，逐个创建以下四个 Repository Secrets：

| Secret 名称 | Secret 值 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | `aod-release.jks.base64` 文件中的全部内容 |
| `ANDROID_KEYSTORE_PASSWORD` | 创建 keystore 时设置的 keystore 密码 |
| `ANDROID_KEY_PASSWORD` | 创建证书时设置的密钥密码；若按回车使用同一密码，则填相同值 |
| `ANDROID_KEY_ALIAS` | 上例为 `aodcoverwallpaper`，需与创建时的 `-alias` 一致 |

配置完成后，Actions 会将 keystore 写入临时 runner 目录并用它签名 APK。正式发布后应始终保留并使用同一 keystore、别名和密码。已安装由临时证书签名的测试版，不能直接覆盖安装持久密钥签名的正式版；需要先卸载测试版（应用数据也会被删除）。来自 fork 的 Pull Request 无法读取仓库 Secrets，会使用临时密钥构建。

## 本地构建

Linux / WSL：

```bash
bash build.sh
```

Windows：

```bat
build.bat
```

Linux 脚本使用 `tools/dl/` 中的 JDK 17 和 Android Build Tools 归档，并需要 Android 34 平台、libxposed API 102 及本地签名密钥。默认 APK 输出到 `build/AODCoverWallpaper.apk`。可用 `OUT_DIR` 指定输出目录；可用 `KEYSTORE_PATH`、`KEYSTORE_PASSWORD`、`KEY_PASSWORD`、`KEY_ALIAS` 指定签名配置。

若 WSL 项目位于 `/mnt/c/` 等 Windows 挂载目录，建议将构建输出放到 Linux 文件系统，再复制 APK：

```bash
OUT_DIR=/tmp/aod-build bash build.sh
cp /tmp/aod-build/AODCoverWallpaper.apk dist/AODCoverWallpaper.apk
```

## 安装与启用

1. 安装构建出的 APK，在 LSPosed 中启用模块。
2. 将 SystemUI 和「息屏提醒」加入模块作用域。
3. 重启 SystemUI 与「息屏提醒」。
4. 在三星 AOD 设置中打开「在外屏上显示壁纸」，选择所需显示模式。

## 上传到 GitHub

上传源码与构建入口：`.github/`、`module/`、`build.sh`、`build.bat`、`README.md`、`.gitignore`。`AGENTS.md` 可选。

不要上传签名密钥、密码、三星系统 APK、设备日志、反编译/验证目录、本地工具链下载包或构建输出。通过 Actions Artifact 分发测试 APK；正式版可将 APK 附加到 GitHub Release。
