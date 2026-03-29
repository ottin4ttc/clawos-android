import type { CapacitorConfig } from '@capacitor/cli'

/**
 * Shell configuration.
 *
 * Set WEBVIEW_URL env var before running `npx cap sync` to point
 * the shell at your own web app, e.g.:
 *
 *   WEBVIEW_URL=https://your-app.com npx cap sync android
 *
 * If WEBVIEW_URL is not set, the shell loads the local www/index.html fallback.
 */
const serverUrl = process.env.WEBVIEW_URL

const config: CapacitorConfig = {
  appId: 'com.clawos.app',
  appName: 'ClawOS Android Shell',
  webDir: 'www',
  ...(serverUrl
    ? {
        server: {
          url: serverUrl,
          allowNavigation: ['*'],
        },
      }
    : {}),
  android: {
    allowMixedContent: true,
  },
}

export default config
