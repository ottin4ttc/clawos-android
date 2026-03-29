# ClawOS Android Shell

A generic Android WebView shell built on [Capacitor](https://capacitorjs.com/). Point it at **any** web URL and get native superpowers:

- **Native WebSocket** — runs on a native thread, survives app backgrounding
- **Background Keep-Alive** — foreground service + silent audio + WakeLock keeps your connection alive
- **Push Notifications** — heads-up banners with vibration & sound
- **Barcode Scanner** — camera-based QR / barcode scanning
- **Native HTTP** — bypass CORS with native HTTP requests (Capacitor built-in)

## Quick Start

```bash
# 1. Clone & install
git clone https://github.com/ottin4ttc/clawos-android.git
cd clawos-android
npm install

# 2. Point at your web app
WEBVIEW_URL=https://your-app.com npx cap sync android

# 3. Open in Android Studio & run
npx cap open android
```

If `WEBVIEW_URL` is not set, the shell loads a local fallback page (`www/index.html`).

See [.env.example](.env.example) for configuration options.

## Build APK

```bash
# Debug APK
cd android && ./gradlew assembleDebug
# Output: android/app/build/outputs/apk/debug/app-debug.apk

# Or via Android Studio:
# Build → Build Bundle(s) / APK(s) → Build APK(s)
```

---

## JavaScript API

Your web app communicates with the native shell through Capacitor's `registerPlugin`:

```ts
import { registerPlugin } from '@capacitor/core'

const NativeSocket = registerPlugin('NativeSocket')
const TaskNotification = registerPlugin('TaskNotification')
```

---

### NativeSocket

Native WebSocket powered by OkHttp. Connections run on a JVM thread — they stay alive when the app goes to background (combined with the built-in keep-alive service).

#### `connect(options)` → `Promise<{ socketId, protocol }>`

```ts
const result = await NativeSocket.connect({
  url: 'wss://api.example.com/ws',
  socketId: 'main',
  protocols: 'graphql-ws',  // optional
  origin: 'https://example.com', // optional
})
```

#### `send(options)` → `Promise<void>`

```ts
await NativeSocket.send({
  socketId: 'main',
  data: JSON.stringify({ type: 'ping' }),
})
```

#### `sendBinary(options)` → `Promise<void>`

```ts
await NativeSocket.sendBinary({
  socketId: 'main',
  data: btoa('binary data here'), // base64-encoded
})
```

#### `close(options)` → `Promise<void>`

```ts
await NativeSocket.close({
  socketId: 'main',
  code: 1000,    // optional, default: 1000
  reason: 'bye', // optional
})
```

#### Events

```ts
NativeSocket.addListener('open', (event) => {
  // { socketId: string, protocol: string }
})

NativeSocket.addListener('message', (event) => {
  // { socketId: string, data: string, binary: boolean }
  // If binary=true, data is base64-encoded
})

NativeSocket.addListener('close', (event) => {
  // { socketId: string, code: number, reason: string }
})

NativeSocket.addListener('error', (event) => {
  // { socketId: string, message: string }
})
```

#### Gotchas

1. **Register listeners BEFORE `connect()`** — The server may send messages immediately after connection. If you call `connect()` first, early messages will be lost.

2. **Filter events by `socketId`** — `addListener()` is global and fires for ALL connections. Always check `event.socketId` matches your connection.

3. **Never return `registerPlugin()` from an async function** — The Capacitor proxy object has a `.then` property. JS treats it as thenable when returned from `async`, triggering a native call that crashes the app. Store it in a module-level variable instead.

#### Complete Example

```ts
import { registerPlugin } from '@capacitor/core'

// Store at module level — NEVER return from async function (see gotcha #3)
const NativeSocket = registerPlugin('NativeSocket')

async function connectWs(url: string) {
  const socketId = `ws_${Date.now()}`

  // 1. Register listeners FIRST (see gotcha #1)
  const listeners = []

  listeners.push(await NativeSocket.addListener('message', (event) => {
    if (event.socketId !== socketId) return  // filter (see gotcha #2)
    console.log('received:', event.data)
  }))

  listeners.push(await NativeSocket.addListener('close', (event) => {
    if (event.socketId !== socketId) return
    listeners.forEach(l => l.remove())
  }))

  listeners.push(await NativeSocket.addListener('error', (event) => {
    if (event.socketId !== socketId) return
    console.error('ws error:', event.message)
  }))

  // 2. Now connect — listeners are already in place
  await NativeSocket.connect({ url, socketId })

  // 3. Use send / close
  NativeSocket.send({ socketId, data: JSON.stringify({ type: 'ping' }) })
  // NativeSocket.close({ socketId })
}
```

---

### TaskNotification

Show native notifications and control the foreground service status text.

#### `notify(options?)` → `Promise<void>`

Shows a heads-up notification with vibration and sound.

```ts
await TaskNotification.notify({
  title: 'Download Complete',       // default: "Task Complete"
  body: 'Your file is ready.',      // default: "Your request has been processed."
})
```

#### `updateServiceStatus(options?)` → `Promise<void>`

Updates the persistent foreground-service notification text (only visible when app is backgrounded).

```ts
await TaskNotification.updateServiceStatus({
  text: 'Processing 3/10 items...', // default: "Keeping connection alive..."
})
```

---

### CapacitorBarcodeScanner

Third-party plugin (`@capacitor/barcode-scanner`). Opens the camera to scan barcodes and QR codes.

```ts
import { CapacitorBarcodeScanner } from '@capacitor/barcode-scanner'

const result = await CapacitorBarcodeScanner.scanBarcode({
  hint: 17,              // 17 = ALL formats
  cameraDirection: 1,    // 1 = BACK, 2 = FRONT
  scanOrientation: 1,    // 1 = PORTRAIT, 2 = LANDSCAPE, 3 = ADAPTIVE
  scanInstructions: 'Point camera at barcode',
})

console.log(result.ScanResult) // scanned content
console.log(result.format)     // barcode format number
```

---

### Capacitor Built-in Plugins

These are available out of the box from Capacitor:

| Plugin | Usage | Docs |
|--------|-------|------|
| **CapacitorHttp** | Native HTTP requests (bypass CORS) | [Docs](https://capacitorjs.com/docs/apis/http) |
| **CapacitorCookies** | Native cookie management | [Docs](https://capacitorjs.com/docs/apis/cookies) |
| **SystemBars** | Control status bar / navigation bar | [Docs](https://capacitorjs.com/docs/apis/status-bar) |

---

## Background Keep-Alive

When the app goes to background, the shell automatically:

1. Starts a **foreground service** with a persistent notification
2. Plays **silent audio** to maintain audio focus and prevent the OS from killing the process
3. Holds a **partial WakeLock** to prevent CPU sleep
4. Advertises a **MediaSession** to appear as an active media app

This keeps your native WebSocket connections alive even when the user switches away.

The keep-alive service is managed automatically — no JS code needed. It starts on `onPause` and stops on `onResume`.

## Android Permissions

The shell declares these permissions in `AndroidManifest.xml`:

| Permission | Purpose |
|-----------|---------|
| `INTERNET` | Network access |
| `CAMERA` | Barcode scanner |
| `WAKE_LOCK` | Prevent CPU sleep in background |
| `FOREGROUND_SERVICE` | Background keep-alive service |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Silent audio for keep-alive |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Prevent battery optimization killing the service |
| `POST_NOTIFICATIONS` | Show notifications |
| `USE_FULL_SCREEN_INTENT` | Heads-up notification banners |
| `VIBRATE` | Notification vibration |

## Customization

| What | Where |
|------|-------|
| App name | `capacitor.config.ts` → `appName` |
| App ID | `capacitor.config.ts` → `appId` |
| Web URL | `WEBVIEW_URL` env var |
| Allowed origins | `capacitor.config.ts` → `server.allowNavigation` |
| App icon | `android/app/src/main/res/mipmap-*` |
| Splash screen | Standard Capacitor splash screen plugin |

### Allow Navigation (Origin Whitelist)

When `WEBVIEW_URL` is set, the shell configures `server.allowNavigation` to control which origins the WebView is allowed to navigate to. This also affects whether Capacitor plugin bridges (including the barcode scanner) are available on a given origin.

Default: `['*']` (all origins allowed).

To restrict to specific domains, edit `capacitor.config.ts`:

```ts
server: {
  url: serverUrl,
  allowNavigation: ['your-app.com', '*.your-app.com'],
},
```

> **Note:** If `WEBVIEW_URL` is not set, the `server` block is omitted entirely and the shell loads local files — `allowNavigation` does not apply in that case.

After changes, run `npx cap sync android` to apply.

## License

MIT

---

# ClawOS Android Shell（中文文档）

基于 [Capacitor](https://capacitorjs.com/) 的通用 Android WebView 壳子。指向**任意** Web URL，即可获得原生能力：

- **原生 WebSocket** — 运行在原生线程，App 切后台也不断连
- **后台保活** — 前台服务 + 静音音频 + WakeLock，保持连接存活
- **推送通知** — 横幅通知，支持振动和声音
- **扫码** — 摄像头扫描二维码 / 条形码
- **原生 HTTP** — 绕过 CORS 的原生 HTTP 请求（Capacitor 内置）

## 快速开始

```bash
# 1. 克隆 & 安装
git clone https://github.com/ottin4ttc/clawos-android.git
cd clawos-android
npm install

# 2. 指向你的 Web 应用
WEBVIEW_URL=https://your-app.com npx cap sync android

# 3. 用 Android Studio 打开并运行
npx cap open android
```

不设置 `WEBVIEW_URL` 时，壳子会加载本地的 `www/index.html` 兜底页面。

配置选项见 [.env.example](.env.example)。

## 打包 APK

```bash
# Debug APK
cd android && ./gradlew assembleDebug
# 输出: android/app/build/outputs/apk/debug/app-debug.apk

# 或通过 Android Studio:
# Build → Build Bundle(s) / APK(s) → Build APK(s)
```

---

## JavaScript API

你的 Web 应用通过 Capacitor 的 `registerPlugin` 与原生壳子通信：

```ts
import { registerPlugin } from '@capacitor/core'

const NativeSocket = registerPlugin('NativeSocket')
const TaskNotification = registerPlugin('TaskNotification')
```

---

### NativeSocket

基于 OkHttp 的原生 WebSocket。连接运行在 JVM 线程上——配合内置保活服务，App 切后台也不断连。

#### `connect(options)` → `Promise<{ socketId, protocol }>`

```ts
const result = await NativeSocket.connect({
  url: 'wss://api.example.com/ws',
  socketId: 'main',
  protocols: 'graphql-ws',  // 可选
  origin: 'https://example.com', // 可选
})
```

#### `send(options)` → `Promise<void>`

```ts
await NativeSocket.send({
  socketId: 'main',
  data: JSON.stringify({ type: 'ping' }),
})
```

#### `sendBinary(options)` → `Promise<void>`

```ts
await NativeSocket.sendBinary({
  socketId: 'main',
  data: btoa('binary data here'), // base64 编码
})
```

#### `close(options)` → `Promise<void>`

```ts
await NativeSocket.close({
  socketId: 'main',
  code: 1000,    // 可选，默认 1000
  reason: 'bye', // 可选
})
```

#### 事件监听

```ts
NativeSocket.addListener('open', (event) => {
  // { socketId: string, protocol: string }
})

NativeSocket.addListener('message', (event) => {
  // { socketId: string, data: string, binary: boolean }
  // binary=true 时，data 为 base64 编码
})

NativeSocket.addListener('close', (event) => {
  // { socketId: string, code: number, reason: string }
})

NativeSocket.addListener('error', (event) => {
  // { socketId: string, message: string }
})
```

#### 注意事项

1. **必须先注册监听再调 `connect()`** — 服务端可能在连接建立后立即发消息，先 connect 再注册监听会丢消息。

2. **用 `socketId` 过滤事件** — `addListener()` 是全局的，会收到所有连接的事件，必须检查 `event.socketId` 是否匹配。

3. **不要从 async 函数 return `registerPlugin()` 的结果** — Capacitor 代理对象有 `.then` 属性，JS 会把它当 thenable 处理，触发原生调用导致崩溃。应存到模块级变量。

#### 完整示例

```ts
import { registerPlugin } from '@capacitor/core'

// 存到模块级变量 — 不要从 async 函数返回（见注意事项 3）
const NativeSocket = registerPlugin('NativeSocket')

async function connectWs(url: string) {
  const socketId = `ws_${Date.now()}`

  // 1. 先注册监听（见注意事项 1）
  const listeners = []

  listeners.push(await NativeSocket.addListener('message', (event) => {
    if (event.socketId !== socketId) return  // 过滤（见注意事项 2）
    console.log('收到消息:', event.data)
  }))

  listeners.push(await NativeSocket.addListener('close', (event) => {
    if (event.socketId !== socketId) return
    listeners.forEach(l => l.remove())
  }))

  listeners.push(await NativeSocket.addListener('error', (event) => {
    if (event.socketId !== socketId) return
    console.error('连接错误:', event.message)
  }))

  // 2. 监听就位后再连接
  await NativeSocket.connect({ url, socketId })

  // 3. 发消息 / 关连接
  NativeSocket.send({ socketId, data: JSON.stringify({ type: 'ping' }) })
  // NativeSocket.close({ socketId })
}
```

---

### TaskNotification

显示原生通知，控制前台服务的状态文字。

#### `notify(options?)` → `Promise<void>`

弹出横幅通知，带振动和声音。

```ts
await TaskNotification.notify({
  title: '下载完成',       // 默认: "Task Complete"
  body: '文件已准备好。',  // 默认: "Your request has been processed."
})
```

#### `updateServiceStatus(options?)` → `Promise<void>`

更新前台服务常驻通知的文字（仅 App 在后台时可见）。

```ts
await TaskNotification.updateServiceStatus({
  text: '正在处理 3/10 项...', // 默认: "Keeping connection alive..."
})
```

---

### CapacitorBarcodeScanner

第三方插件（`@capacitor/barcode-scanner`），打开摄像头扫描条形码和二维码。

```ts
import { CapacitorBarcodeScanner } from '@capacitor/barcode-scanner'

const result = await CapacitorBarcodeScanner.scanBarcode({
  hint: 17,              // 17 = 全格式
  cameraDirection: 1,    // 1 = 后置, 2 = 前置
  scanOrientation: 1,    // 1 = 竖屏, 2 = 横屏, 3 = 自适应
  scanInstructions: '将摄像头对准条码',
})

console.log(result.ScanResult) // 扫码内容
console.log(result.format)     // 条码格式编号
```

---

### Capacitor 内置插件

以下插件由 Capacitor 框架自带，开箱即用：

| 插件 | 用途 | 文档 |
|------|------|------|
| **CapacitorHttp** | 原生 HTTP 请求（绕过 CORS） | [文档](https://capacitorjs.com/docs/apis/http) |
| **CapacitorCookies** | 原生 Cookie 管理 | [文档](https://capacitorjs.com/docs/apis/cookies) |
| **SystemBars** | 控制状态栏 / 导航栏 | [文档](https://capacitorjs.com/docs/apis/status-bar) |

---

## 后台保活机制

App 切到后台时，壳子自动执行：

1. 启动**前台服务**，显示常驻通知
2. 播放**静音音频**，保持音频焦点，防止系统杀进程
3. 持有**部分唤醒锁（Partial WakeLock）**，防止 CPU 休眠
4. 注册**MediaSession**，让系统认为是活跃的媒体应用

这套组合拳确保原生 WebSocket 在用户切走后依然保持连接。

保活服务由壳子自动管理，无需 JS 代码介入。`onPause` 时启动，`onResume` 时停止。

## Android 权限

壳子在 `AndroidManifest.xml` 中声明了以下权限：

| 权限 | 用途 |
|------|------|
| `INTERNET` | 网络访问 |
| `CAMERA` | 扫码 |
| `WAKE_LOCK` | 后台防止 CPU 休眠 |
| `FOREGROUND_SERVICE` | 后台保活服务 |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | 静音音频保活 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 防止电池优化杀服务 |
| `POST_NOTIFICATIONS` | 显示通知 |
| `USE_FULL_SCREEN_INTENT` | 横幅通知 |
| `VIBRATE` | 通知振动 |

## 自定义

| 配置项 | 位置 |
|--------|------|
| 应用名称 | `capacitor.config.ts` → `appName` |
| 应用 ID | `capacitor.config.ts` → `appId` |
| Web 地址 | `WEBVIEW_URL` 环境变量 |
| 允许的域名 | `capacitor.config.ts` → `server.allowNavigation` |
| 应用图标 | `android/app/src/main/res/mipmap-*` |
| 启动页 | 使用 Capacitor 标准 splash screen 插件 |

### 域名白名单（allowNavigation）

设置了 `WEBVIEW_URL` 后，壳子通过 `server.allowNavigation` 控制 WebView 允许导航到哪些域名。这也影响 Capacitor 插件桥（包括扫码插件）是否在对应 origin 上可用。

默认值：`['*']`（允许所有域名）。

如需限制为特定域名，编辑 `capacitor.config.ts`：

```ts
server: {
  url: serverUrl,
  allowNavigation: ['your-app.com', '*.your-app.com'],
},
```

> **注意：** 未设置 `WEBVIEW_URL` 时，`server` 配置块不会生成，壳子加载本地文件，`allowNavigation` 不生效。

修改后执行 `npx cap sync android` 同步到 Android 项目。

## License

MIT
