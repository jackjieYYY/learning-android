# 英语听力 Android App

Kotlin + Compose + Media3 的英语听力学习 App。教材来自 Cloudflare Workers 静态教材库，下载后可离线学习。

## 开发

```bash
./gradlew testDebugUnitTest assembleDebug
```

本地 debug 包使用 debug 签名，App 内更新功能关闭（`BuildConfig.UPDATES_ENABLED = false`）。

## 发布（GitHub Actions）

推送 `vMAJOR.MINOR.PATCH` 形式的 annotated tag，就会触发 `.github/workflows/release.yml`：

```bash
git tag -a v1.4.1 -m "本次更新说明（会显示在 App 的更新卡片里）"
git push origin v1.4.1
```

工作流依次执行：单元测试 → 用 release key 签名构建（R8）→ 校验证书指纹、包名和版本 → 创建 GitHub Release，附件为 APK、`.sha256` 和 `latest.json`。

- 版本号由 tag 决定：`versionCode = major*10000 + minor*100 + patch`，minor 和 patch 都必须小于 100。
- App 读取 `https://github.com/jackjieYYY/learning-android/releases/latest/download/latest.json`，只有用户点击后才下载；下载后校验大小、SHA-256、包名和版本，再通过 PackageInstaller 安装。Android 会额外校验签名必须一致。
- 草稿和 prerelease 不会被 `latest` 选中。

## 签名密钥

- release keystore **不在仓库中**。它以 Secrets 形式存放在 GitHub Environment `release` 中（`KEYSTORE_BASE64`、`STORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`），该环境只允许 `v*` tag 使用。
- 证书 SHA-256：`56:0F:63:4E:F5:52:C5:7B:67:0E:65:C7:D9:E2:B3:98:A9:18:FB:44:22:B7:34:7A:DF:AF:AA:EC:26:9A:2E:A1`
- **keystore 和密码必须离线备份**。密钥丢失后，已安装的 App 将无法再覆盖更新，也无法在 Android 开发者验证中登记包名。

本地构建 release 包时，需设置环境变量 `SIGNING_STORE_FILE`、`SIGNING_STORE_PASSWORD`、`SIGNING_KEY_ALIAS`、`SIGNING_KEY_PASSWORD`。
