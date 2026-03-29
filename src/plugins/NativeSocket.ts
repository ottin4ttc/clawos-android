import { registerPlugin } from '@capacitor/core'
import type { Plugin, PluginListenerHandle } from '@capacitor/core'

// ─── Types ───────────────────────────────────────────────────────────

export interface NativeSocketConnectOptions {
  /** WebSocket URL (wss:// or ws://) */
  url: string
  /** Unique identifier for this connection */
  socketId: string
  /** Comma-separated sub-protocols (optional) */
  protocols?: string
  /** Origin header (optional) */
  origin?: string
}

export interface NativeSocketSendOptions {
  /** Socket identifier from connect() */
  socketId: string
  /** Text data to send */
  data: string
}

export interface NativeSocketSendBinaryOptions {
  /** Socket identifier from connect() */
  socketId: string
  /** Base64-encoded binary data */
  data: string
}

export interface NativeSocketCloseOptions {
  /** Socket identifier from connect() */
  socketId: string
  /** Close code (default: 1000) */
  code?: number
  /** Close reason (default: "") */
  reason?: string
}

export interface NativeSocketConnectResult {
  socketId: string
  protocol: string
}

export interface NativeSocketOpenEvent {
  socketId: string
  protocol: string
}

export interface NativeSocketMessageEvent {
  socketId: string
  /** Text content or base64-encoded binary */
  data: string
  /** true if data is base64-encoded binary */
  binary: boolean
}

export interface NativeSocketCloseEvent {
  socketId: string
  code: number
  reason: string
}

export interface NativeSocketErrorEvent {
  socketId: string
  message: string
}

// ─── Plugin Interface ────────────────────────────────────────────────

export interface NativeSocketPlugin extends Plugin {
  /**
   * Open a native WebSocket connection.
   * The connection runs on a native thread and survives when the app is backgrounded.
   */
  connect(options: NativeSocketConnectOptions): Promise<NativeSocketConnectResult>

  /** Send text data through an open socket. */
  send(options: NativeSocketSendOptions): Promise<void>

  /** Send base64-encoded binary data through an open socket. */
  sendBinary(options: NativeSocketSendBinaryOptions): Promise<void>

  /** Close a socket connection. */
  close(options: NativeSocketCloseOptions): Promise<void>

  /** Fired when the WebSocket connection opens. */
  addListener(
    eventName: 'open',
    handler: (event: NativeSocketOpenEvent) => void,
  ): Promise<PluginListenerHandle>

  /** Fired when a message is received. */
  addListener(
    eventName: 'message',
    handler: (event: NativeSocketMessageEvent) => void,
  ): Promise<PluginListenerHandle>

  /** Fired when the WebSocket connection closes. */
  addListener(
    eventName: 'close',
    handler: (event: NativeSocketCloseEvent) => void,
  ): Promise<PluginListenerHandle>

  /** Fired when a WebSocket error occurs. */
  addListener(
    eventName: 'error',
    handler: (event: NativeSocketErrorEvent) => void,
  ): Promise<PluginListenerHandle>
}

// ─── Plugin Instance ─────────────────────────────────────────────────

export const NativeSocket = registerPlugin<NativeSocketPlugin>('NativeSocket')
