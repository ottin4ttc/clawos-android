package com.clawos.app;

import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * Capacitor Plugin: Native WebSocket via OkHttp.
 *
 * OkHttp WebSocket runs on its own JVM thread, independent of WebView JS engine.
 * Combined with ForegroundService (WsKeepAliveService), the connection survives
 * when the app goes to background and WebView JS is frozen by Android.
 */
@CapacitorPlugin(name = "NativeSocket")
public class NativeSocketPlugin extends Plugin {

    private static final String TAG = "NativeSocket";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .pingInterval(30, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build();

    private final ConcurrentHashMap<String, WebSocket> connections = new ConcurrentHashMap<>();

    @PluginMethod
    public void connect(PluginCall call) {
        String url = call.getString("url");
        if (url == null || url.isEmpty()) {
            call.reject("url is required");
            return;
        }

        String socketId = call.getString("socketId");
        if (socketId == null || socketId.isEmpty()) {
            call.reject("socketId is required");
            return;
        }

        String protocols = call.getString("protocols", "");
        String origin = call.getString("origin", "");

        Log.d(TAG, "connect: id=" + socketId + " url=" + url);

        Request.Builder reqBuilder = new Request.Builder().url(url);
        if (origin != null && !origin.isEmpty()) {
            reqBuilder.header("Origin", origin);
        }
        if (protocols != null && !protocols.isEmpty()) {
            reqBuilder.header("Sec-WebSocket-Protocol", protocols);
        }

        // Use AtomicReference to avoid holding the PluginCall beyond its resolve/reject lifecycle
        AtomicReference<PluginCall> pendingCall = new AtomicReference<>(call);

        WebSocket ws = client.newWebSocket(reqBuilder.build(), new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                Log.d(TAG, "onOpen: id=" + socketId);
                String protocol = response.header("Sec-WebSocket-Protocol", "");

                // Resolve the connect() call with the socketId, then release the reference
                PluginCall c = pendingCall.getAndSet(null);
                if (c != null) {
                    JSObject result = new JSObject();
                    result.put("socketId", socketId);
                    result.put("protocol", protocol);
                    c.resolve(result);
                }

                JSObject evt = new JSObject();
                evt.put("socketId", socketId);
                evt.put("protocol", protocol);
                notifyListeners("open", evt);
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                JSObject evt = new JSObject();
                evt.put("socketId", socketId);
                evt.put("data", text);
                evt.put("binary", false);
                notifyListeners("message", evt);
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                JSObject evt = new JSObject();
                evt.put("socketId", socketId);
                evt.put("data", bytes.base64());
                evt.put("binary", true);
                notifyListeners("message", evt);
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                Log.d(TAG, "onClosing: id=" + socketId + " code=" + code);
                webSocket.close(code, reason);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                Log.d(TAG, "onClosed: id=" + socketId + " code=" + code);
                connections.remove(socketId);

                JSObject evt = new JSObject();
                evt.put("socketId", socketId);
                evt.put("code", code);
                evt.put("reason", reason);
                notifyListeners("close", evt);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                Log.e(TAG, "onFailure: id=" + socketId, t);
                connections.remove(socketId);

                String message = t.getMessage() != null ? t.getMessage() : "Unknown error";

                // Reject the connect() call if it hasn't been resolved yet
                PluginCall c = pendingCall.getAndSet(null);
                if (c != null) {
                    c.reject("WebSocket connection failed: " + message);
                }

                JSObject errorEvt = new JSObject();
                errorEvt.put("socketId", socketId);
                errorEvt.put("message", message);
                notifyListeners("error", errorEvt);

                JSObject closeEvt = new JSObject();
                closeEvt.put("socketId", socketId);
                closeEvt.put("code", 1006);
                closeEvt.put("reason", message);
                notifyListeners("close", closeEvt);
            }
        });

        connections.put(socketId, ws);
    }

    @PluginMethod
    public void send(PluginCall call) {
        String socketId = call.getString("socketId");
        String data = call.getString("data");
        if (socketId == null || data == null) {
            call.reject("socketId and data are required");
            return;
        }

        WebSocket ws = connections.get(socketId);
        if (ws != null) {
            ws.send(data);
            call.resolve();
        } else {
            call.reject("Socket not found: " + socketId);
        }
    }

    @PluginMethod
    public void sendBinary(PluginCall call) {
        String socketId = call.getString("socketId");
        String base64Data = call.getString("data");
        if (socketId == null || base64Data == null) {
            call.reject("socketId and data are required");
            return;
        }

        WebSocket ws = connections.get(socketId);
        if (ws != null) {
            byte[] bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT);
            ws.send(ByteString.of(bytes));
            call.resolve();
        } else {
            call.reject("Socket not found: " + socketId);
        }
    }

    @PluginMethod
    public void close(PluginCall call) {
        String socketId = call.getString("socketId");
        if (socketId == null) {
            call.reject("socketId is required");
            return;
        }

        int code = call.getInt("code", 1000);
        String reason = call.getString("reason", "");

        WebSocket ws = connections.get(socketId);
        if (ws != null) {
            ws.close(code, reason);
            call.resolve();
        } else {
            call.resolve(); // Already closed, not an error
        }
    }

    @Override
    protected void handleOnDestroy() {
        for (WebSocket ws : connections.values()) {
            try {
                ws.close(1001, "Plugin destroyed");
            } catch (Exception e) {
                // ignore
            }
        }
        connections.clear();
        client.dispatcher().executorService().shutdown();
    }
}
