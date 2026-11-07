# FCM 推送分支：构建与交付说明

基线：`zh-cn` 分支 `0f53f83`（feat: FCM push while keep-alive is off，12 个文件）。
本次只做构建可用性与去重加固，未重写 FCM 业务逻辑。

## 本次改动（3 个文件，均未提交）

| 文件 | 改动 | 原因 |
| --- | --- | --- |
| `app/build.gradle.kts` | google-services 插件改为 `apply false` + 文件存在时才 apply；`versionCode` 下限 6 | 本地缺 `google-services.json` 时仍能编译；保证 `versionCode > 5` |
| `.github/workflows/build-apk.yml` | keystore 解码增加空值/非空文件校验；新增 `GOOGLE_SERVICES_JSON_BASE64` 还原步骤 | CI 缺 secret 时立即失败并报出名字；公开仓库不提交 Firebase 配置 |
| `app/src/main/java/com/gotify/client/service/MessageIngestor.kt` | `ingest()` 加 `Mutex` 串行化「查重-入库-发通知」 | WebSocket / 周期同步 / FCM 同步并发时同一消息最多通知一次 |

## CI 构建（GitHub Actions）

`build-apk.yml` 现在读取 5 个 secret：

- `ANDROID_KEYSTORE_BASE64`：v1.1.0 同一把 keystore 的 base64（解码到 `$RUNNER_TEMP/release.keystore`）
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`
- `GOOGLE_SERVICES_JSON_BASE64`：`app/google-services.json` 的 base64（单行）

`versionCode` 取 `GITHUB_RUN_NUMBER`，并强制不低于 6；`versionName` 仍为 `1.1.0`。
下一次 `zh-cn` 推送对应 run 7，即 versionCode 7。

## 本地构建（已验证成功）

```powershell
$env:JAVA_HOME='E:\tools\jdk'            # JDK 17
$env:ANDROID_HOME='E:\tools\android-sdk' # 需 platforms;android-37.0 + build-tools;36.0.0
& 'E:\tools\gradle-9.5.0\bin\gradle.bat' :app:assembleRelease --no-daemon
```

- `app/google-services.json` 必须存在，否则 Firebase 配置为空（本次构建已放入，且被 `.gitignore` 忽略）。
- 本机访问 `api.foojay.io` 受限，Gradle 的 `gradle-daemon-jvm.properties`（JETBRAINS 21）会自动下载 610MB JBR 并卡住；本次本地构建临时移开该文件、改用 JDK 17 完成。CI 网络正常，不需要该操作。
- 产物：`app/build/outputs/apk/release/app-release-unsigned.apk`
  - package `com.gotify.client`，versionCode 6，versionName 1.1.0，minSdk 26，targetSdk 37
  - SHA-256 `400B672F2EC2A066106D88B1E19A955AC752BC0E6C978156C578B0E0387366D`
  - 未签名（本机没有 v1.1.0 keystore，`assembleRelease` 不产生签名配置）

## 还差什么

1. **v1.1.0 keystore**：本机与仓库内都没有。需要提供 keystore 文件路径 + 3 个口令，或允许推送后由 CI 产出签名包。
2. **S10 验证**：需要签名后的可安装 APK（或授权用临时测试 key 签名安装），以及用户/服务端触发一条真实测试消息。
3. CI 的 5 个 secret 是否已全部写入仓库设置，需要登录 `gh` 或网页确认（本轮未登录）。
