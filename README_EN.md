English | [中文](./README.md)

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
