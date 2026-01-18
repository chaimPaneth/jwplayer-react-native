package com.jwplayer.rnjwplayer.utils;

import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableType;

import android.util.Log;

/**
 * Centralized logging utility for JWPlayer React Native module.
 * Allows enabling/disabling of verbose diagnostic logs without removing code.
 * Usage:
 *   JWLog.setLoggingMode(JWLog.Mode.ALL);    // Logs all levels
 *   JWLog.setLoggingMode(JWLog.Mode.ERROR);  // Only error logs
 *   JWLog.setLoggingMode(JWLog.Mode.DISABLED); // No logs (except force)
 *   JWLog.v("MediaSession", "Sequence advanced: " + seq);
 */
public final class JWLog {
    public enum Mode { ALL, ERROR, DISABLED }

    // Default ON for easier debugging; toggle in app init for prod if desired
    private static volatile Mode MODE = Mode.ERROR;

    static { Log.i("JWLog", "JWLog initialized (mode=" + MODE + ")"); }

    private JWLog() {}

    // Backward-compat: map legacy boolean to new modes (true=ALL, false=DISABLED)
    @Deprecated
    public static void setExtraLoggingEnabled(boolean enabled) { MODE = enabled ? Mode.ALL : Mode.DISABLED; }

    @Deprecated
    public static boolean isExtraLoggingEnabled() { return MODE == Mode.ALL; }

    public static void setLoggingMode(Mode mode) { MODE = (mode != null ? mode : Mode.DISABLED); }
    public static Mode getLoggingMode() { return MODE; }

    private static boolean allowAll() { return MODE == Mode.ALL; }
    private static boolean allowErrors() { return MODE == Mode.ALL || MODE == Mode.ERROR; }

    private static String fullTag(String rawTag) { return (rawTag != null ? rawTag : "JWLog"); }

    public static void d(String tag, String msg) { if (allowAll()) Log.d(fullTag(tag), msg); }
    public static void d(String tag, String msg, boolean showCaller) {
        if (allowAll()) Log.d(fullTag(tag), showCaller ? (msg + " caller=" + callerInfo(null)) : msg);
    }
    public static void e(String tag, String msg) { if (allowErrors()) Log.e(fullTag(tag), msg); }
    public static void e(String tag, String msg, boolean showCaller) { if (allowErrors()) Log.e(fullTag(tag), showCaller ? (msg + " caller=" + callerInfo(null)) : msg); }
    public static void e(String tag, String msg, Throwable t) { if (allowErrors()) Log.e(fullTag(tag), msg, t); }
    public static void e(String tag, String msg, Throwable t, boolean showCaller) { if (allowErrors()) Log.e(fullTag(tag), showCaller ? (msg + " caller=" + callerInfo(null)) : msg, t); }
    public static void i(String tag, String msg) { if (allowAll()) Log.i(fullTag(tag), msg); }
    public static void i(String tag, String msg, boolean showCaller) { if (allowAll()) Log.i(fullTag(tag), showCaller ? (msg + " caller=" + callerInfo(null)) : msg); }
    public static void v(String tag, String msg) { if (allowAll()) Log.v(fullTag(tag), msg); }
    public static void v(String tag, String msg, boolean showCaller) { if (allowAll()) Log.v(fullTag(tag), showCaller ? (msg + " caller=" + callerInfo(null)) : msg); }
    public static void w(String tag, String msg) { if (allowAll()) Log.w(fullTag(tag), msg); }
    public static void w(String tag, String msg, boolean showCaller) { if (allowAll()) Log.w(fullTag(tag), showCaller ? (msg + " caller=" + callerInfo(null)) : msg); }
    public static void w(String tag, String msg, Throwable t) { if (allowAll()) Log.w(fullTag(tag), msg, t); }
    public static void w(String tag, String msg, Throwable t, boolean showCaller) { if (allowAll()) Log.w(fullTag(tag), showCaller ? (msg + " caller=" + callerInfo(null)) : msg, t); }

    // Always log ignoring EXTRA_ENABLED (for critical diagnostics / verifying visibility)
    public static void force(String tag, String msg) { Log.i(fullTag(tag), "[FORCE] " + msg); }

    // ---- Shared helper formatting utilities (moved from RNJWMediaSessionHelper) ----
    public static String safe(Object o) {
        if (o == null) return "null";

        // Special-case React Native types so logs don't leak secrets (license, tokens, auth urls, etc.)
        try {
            if (o instanceof ReadableMap) {
                return safeReadableMap((ReadableMap) o);
            }
            if (o instanceof ReadableArray) {
                return safeReadableArray((ReadableArray) o);
            }
        } catch (Throwable ignore) {
            // fall through to default
        }

        try { return String.valueOf(o); }
        catch (Throwable t) { return o.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(o)); }
    }

    private static boolean isSensitiveKey(String key) {
        if (key == null) return false;
        String k = key.toLowerCase();
        return k.equals("license")
                || k.contains("token")
                || k.contains("secret")
                || k.contains("password")
                || k.contains("authorization")
                || k.contains("authurl")
                || k.equals("authurl")
                || k.contains("apikey")
                || k.contains("api_key")
                || k.contains("client_secret")
                || k.contains("refresh")
                || k.contains("session");
    }

    private static String redact(String value) {
        if (value == null) return "REDACTED";
        try {
            int n = value.length();
            if (n <= 6) return "REDACTED";
            // Keep only a tiny tail for debugging uniqueness without leaking the value
            String tail = value.substring(Math.max(0, n - 4));
            return "REDACTED(*" + n + ",.." + tail + ")";
        } catch (Throwable t) {
            return "REDACTED";
        }
    }

    private static String safeReadableMap(ReadableMap map) {
        if (map == null) return "ReadableMap{null}";
        StringBuilder sb = new StringBuilder("ReadableMap{");
        try {
            java.util.HashMap<String, Object> hm = map.toHashMap();
            int count = 0;
            for (String key : hm.keySet()) {
                if (count++ > 0) sb.append(", ");
                Object v = hm.get(key);
                sb.append(key).append("=");

                if (isSensitiveKey(key)) {
                    sb.append(redact(v != null ? String.valueOf(v) : null));
                    continue;
                }

                if (v == null) {
                    sb.append("null");
                } else if (v instanceof String) {
                    String s = (String) v;
                    // Keep strings short to avoid log spam
                    if (s.length() > 120) s = s.substring(0, 120) + "…";
                    sb.append("\"").append(s).append("\"");
                } else if (v instanceof Number || v instanceof Boolean) {
                    sb.append(String.valueOf(v));
                } else if (v instanceof java.util.Map) {
                    sb.append("{…}");
                } else if (v instanceof java.util.List) {
                    sb.append("[…]");
                } else {
                    sb.append(v.getClass().getSimpleName());
                }
            }
        } catch (Throwable t) {
            sb.append("error=").append(t.getClass().getSimpleName());
        }
        sb.append("}");
        return sb.toString();
    }

    private static String safeReadableArray(ReadableArray arr) {
        if (arr == null) return "ReadableArray{null}";
        try {
            int n = arr.size();
            // Don't dump contents; just size + a small peek at first element type
            String first = "empty";
            if (n > 0) {
                ReadableType t = arr.getType(0);
                first = (t != null ? t.name() : "unknown");
            }
            return "ReadableArray{size=" + n + ", firstType=" + first + "}";
        } catch (Throwable t) {
            return "ReadableArray{error}";
        }
    }

    public static String id(Object o) {
        if (o == null) return "null";
        try { return "0x" + Integer.toHexString(System.identityHashCode(o)); } catch (Throwable t) { return "id{error}"; }
    }

    public static String callerInfo(Class<?> selfClass) {
        try {
            StackTraceElement[] st = new Throwable().getStackTrace();
            String jwLogClass = JWLog.class.getName();
            String self = (selfClass != null ? selfClass.getName() : null);
            String calleeClass = null; // the class that called JWLog

            for (StackTraceElement e : st) {
                String cls = e.getClassName();
                if (cls == null) continue;

                // Skip frames from JWLog itself
                if (cls.equals(jwLogClass)) continue;

                // If a selfClass is provided, we want the first external, non-synthetic frame
                if (self != null) {
                    if (cls.equals(self)) continue; // skip frames from the class doing the logging
                    if (isSyntheticFrame(e)) continue; // skip synthetic accessors/lambdas/line=0
                    return cls + "." + e.getMethodName() + ":" + e.getLineNumber();
                }

                // When no selfClass provided (overloads):
                // Find the callee class (first non-synthetic frame after JWLog),
                // then return the first non-synthetic frame with a different class (the real caller).
                if (calleeClass == null) {
                    if (isSyntheticFrame(e)) continue; // ignore synthetic as callee too
                    calleeClass = cls;
                    continue;
                }

                if (isSyntheticFrame(e)) continue; // skip synthetic (e.g., -$$Nest$, access$, lambda$)
                if (!cls.equals(calleeClass)) {
                    return cls + "." + e.getMethodName() + ":" + e.getLineNumber();
                }
            }
        } catch (Throwable t) { }
        return "unknown";
    }

    private static boolean isSyntheticFrame(StackTraceElement e) {
        try {
            String m = e.getMethodName();
            String c = e.getClassName();
            if (e.getLineNumber() <= 0) return true; // often synthetic or optimized frames
            if (m == null) return true;
            if (m.startsWith("-$$Nest$")) return true; // desugared nestmate accessor
            if (m.startsWith("access$")) return true;  // synthetic accessor for inner classes
            if (m.contains("lambda$")) return true;    // lambda synthetic method
            if (c != null && c.contains("$$")) return true; // other synthetic/generated classes
        } catch (Throwable ignore) {}
        return false;
    }

    public static String newReqId(String mediaId) {
        try {
            long ts = android.os.SystemClock.uptimeMillis();
            return (mediaId != null ? mediaId : "null") + "#" + Long.toHexString(ts);
        } catch (Throwable t) {
            return (mediaId != null ? mediaId : "null") + "#na";
        }
    }

    public static String bundleInfo(android.os.Bundle b) {
        if (b == null) return "null";
        try {
            StringBuilder sb = new StringBuilder("Bundle{");
            for (String key : b.keySet()) {
                Object value = b.get(key);
                sb.append(key).append("=").append(value).append(", ");
            }
            if (sb.length() > 7) sb.setLength(sb.length() - 2);
            sb.append("}");
            return sb.toString();
        } catch (Throwable t) { return "Bundle{error}"; }
    }

    public static String intentInfo(android.content.Intent intent) {
        if (intent == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("Intent{");
        try {
            sb.append("action=").append(intent.getAction());
            if (intent.getData() != null) sb.append(", data=").append(intent.getData());
            if (intent.getCategories() != null) sb.append(", categories=").append(intent.getCategories());
            if (intent.getExtras() != null) sb.append(", extrasKeys=").append(intent.getExtras().keySet());
        } catch (Throwable t) { sb.append("error"); }
        sb.append("}");
        return sb.toString();
    }

    public static String playlistItemInfo(com.jwplayer.pub.api.media.playlists.PlaylistItem item) {
        if (item == null) return "null";
        try {
            String title = item.getTitle();
            String mediaId = item.getMediaId();
            Integer duration = item.getDuration();
            var external = item.getExternalMetadata();
            String description = item.getDescription();
            return "{title=" + (title != null ? title : "") + ", mediaId=" + (mediaId != null ? mediaId : "") + ", duration=" + (duration != null ? duration : 0) + ", external=" + (external != null ? external.toString() : "null") + ", description=" + (description != null ? description : "null") + "}";
        } catch (Throwable t) { return "PlaylistItem{error}"; }
    }

    public static String bitmapInfo(android.graphics.Bitmap bmp) {
        if (bmp == null) return "null";
        try { return bmp.getWidth() + "x" + bmp.getHeight(); } catch (Throwable t) { return "Bitmap{error}"; }
    }
}
