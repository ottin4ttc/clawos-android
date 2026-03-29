import { registerPlugin } from '@capacitor/core'
import type { Plugin } from '@capacitor/core'

// ─── Types ───────────────────────────────────────────────────────────

export interface TaskNotificationNotifyOptions {
  /** Notification title (default: "Task Complete") */
  title?: string
  /** Notification body (default: "Your request has been processed.") */
  body?: string
}

export interface TaskNotificationUpdateStatusOptions {
  /** Text shown in the foreground service notification (default: "Keeping connection alive...") */
  text?: string
}

// ─── Plugin Interface ────────────────────────────────────────────────

export interface TaskNotificationPlugin extends Plugin {
  /**
   * Show a heads-up notification with vibration and sound.
   * Useful for alerting the user when a background task completes.
   */
  notify(options?: TaskNotificationNotifyOptions): Promise<void>

  /**
   * Update the text of the persistent foreground-service notification.
   * Only takes effect while the keep-alive service is running (app in background).
   */
  updateServiceStatus(options?: TaskNotificationUpdateStatusOptions): Promise<void>
}

// ─── Plugin Instance ─────────────────────────────────────────────────

export const TaskNotification = registerPlugin<TaskNotificationPlugin>('TaskNotification')
