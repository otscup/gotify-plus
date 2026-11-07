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

## 真机验证方法：`am force-stop` 与 `am kill` 不等价

验证「进程不在 → FCM 拉起」时，**不要用 `am force-stop`**：

- `am force-stop <pkg>` 会把包置为 `stopped=true`（`dumpsys package` 可见）。Android 只向 stopped 包丢弃普通广播，而 GMS 投递 `com.google.android.c2dm.intent.RECEIVE` 不带 `FLAG_INCLUDE_STOPPED_PACKAGES`，因此 FCM 消息永远进不来：
  ```
  W/GCM: broadcast intent callback: result=CANCELLED for Intent { act=com.google.android.c2dm.intent.RECEIVE ... pkg=com.gotify.client }
  ```
  进程不会被拉起、也不会有通知。这是平台既定行为，不是 App 缺陷；用 force-stop 做唤醒验证会得出错误结论（2026-11-08 已在 S10/Android 12 上复现）。
- `am kill <pkg>`：只杀该包的后台进程，不改 `stopped` 标志，等价于「划掉最近任务 / 系统低内存回收」，才是验证 FCM 唤醒的正确手段。执行后应满足 `stopped=false` 且 `pidof` 为空。

验证前置条件（缺一项结果就无法归因）：

- `cmd appops get <pkg> RUN_ANY_IN_BACKGROUND` → `allow`
- 包在 `dumpsys deviceidle whitelist` 中，`am get-standby-bucket <pkg>` → `5`（EXEMPTED）
- `dumpsys notification` 中 `AppSettings: <pkg> ... allowNoti=true`；Samsung 等 ROM 还需关闭自启限制/电池优化

成功唤醒的判读依据：logcat 出现 `ActivityManager: Start proc <pid>:<pkg>`，随后有 `GotifyFirebaseMessagingService` 执行痕迹；通知栏内容与消息原文逐字一致。

## 已知缺陷与根因：FCM 后台同步被取消（versionCode 7）

真机实测（S10 / Android 12，keep-alive 关闭，`am kill` 后发消息）：

- 进程被拉起：`03:41:37.708 I/ActivityManager: Start proc 25169:com.gotify.client/u0a891 for broadcast {com.gotify.client/com.google.firebase.iid.FirebaseInstanceIdReceiver}`
- 307ms 后同步被取消：
  ```
  W/GotifyFcm(25169): FCM-triggered sync failed
  W/GotifyFcm(25169): Job was cancelled
  ```
- `logcat -b events` 中 `notification_enqueue` 在 03:41:37 之后没有任何 `com.gotify.client` 记录
- 同一进程 03:48 仍在（存活 6 分钟以上），排除「进程被回收导致协程取消」

根因：`GotifyFirebaseMessagingService` 把 `sync()` 跑在 service 私有的 `CoroutineScope`，`onDestroy()` 里 `scope.cancel()`；FCM service 在 `onMessageReceived` 返回后立即被 framework 销毁，HTTP 请求还没回来就被取消。用户随后打开 App，App 自身启动同步才把消息入库，因此出现「列表有消息、通知没弹」。

修复（versionCode 8 起）：`onMessageReceived` 不再自己起协程，改为 `SyncJobService.scheduleImmediate()` 投递一次性 job（`NETWORK_TYPE_ANY` + `setOverrideDeadline(0)`），由系统在 job 执行期间保活；`lastKnownId` 过滤与 `MessageIngestor` 的 Mutex 去重不变。keep-alive 开启时 `sync()` 原样 early-return。

## 还差什么

1. **v1.1.0 keystore**：本机与仓库内都没有。需要提供 keystore 文件路径 + 3 个口令，或允许推送后由 CI 产出签名包。
2. **S10 验证**：需要签名后的可安装 APK（或授权用临时测试 key 签名安装），以及用户/服务端触发一条真实测试消息。
3. CI 的 5 个 secret 是否已全部写入仓库设置，需要登录 `gh` 或网页确认（本轮未登录）。
