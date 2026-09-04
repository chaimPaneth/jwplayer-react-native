package com.jwplayer.rnjwplayer.session;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.support.v4.media.session.MediaSessionCompat;

import com.jwplayer.rnjwplayer.utils.JWLog;

import java.lang.reflect.Method;

/**
 * Resolves the app-wide {@link MediaSessionCompat}.
 *
 * <p>Shared with {@code react-native-android-media-browser} when that library is installed;
 * otherwise a fork-private session. This keeps the module free of a compile-time dependency on the
 * media-browser lib, so an app that ships the player without Android Auto no longer has to install
 * the car library to make Gradle sync.
 *
 * <p><b>Ownership:</b> media-browser owns the shared session ({@code MediaSessionSingleton}),
 * because {@code MediaBrowserServiceCompat} must publish a session token in {@code onCreate()} or
 * Android Auto refuses the connection. This class is a consumer.
 *
 * <p><b>Never cache the returned instance in a field</b> — media-browser may legitimately release
 * and recreate the session (see {@code MediaBrowserService.onDestroy}), and a cached reference then
 * publishes state into a dead session.
 *
 * <p><b>Invariant:</b> when media-browser is present in the APK this class must never create its
 * private fallback session. Two live sessions make the phone and the head unit disagree about what
 * is playing, which is the failure class this indirection exists to avoid. "Present but unreachable"
 * is therefore treated as a packaging error and fails loudly rather than falling back.
 */
public final class RNJWSharedMediaSession {

    private static final String TAG = "RNJWSharedMediaSession";

    /**
     * Presence probe. Declared as a {@code <service>} in media-browser's own AndroidManifest, which
     * merges into every consuming app, so AGP's manifest-derived keep rules pin the class and its
     * binary name; media-browser's consumer rules also {@code -keepnames} it. A
     * ClassNotFoundException here therefore means the library is genuinely absent from the APK,
     * never a minification artifact — which is why the probe is not the singleton itself.
     */
    private static final String PROBE_CLASS = "com.mediabrowser.MediaBrowserService";

    private static final String SINGLETON_CLASS = "com.mediabrowser.MediaSessionSingleton";

    /** Fixed for the process lifetime: classpath contents cannot change at runtime. */
    private static volatile Boolean sMediaBrowserPresent;

    /** Cached after the first successful resolve; the lookup itself runs once. */
    private static volatile Method sGetInstance;

    /** ERROR once per process, not once per second — the hottest caller runs every second. */
    private static volatile boolean sIntegrationErrorLogged;

    /** Fallback session; torn down only by process death. */
    private static volatile MediaSessionCompat sOwnSession;

    private RNJWSharedMediaSession() { }

    /**
     * @return the shared session when media-browser is installed, a fork-private session when it is
     *         not, or {@code null} on a release build whose media-browser integration is broken
     *         (present but unreachable). Callers must tolerate {@code null}.
     */
    public static MediaSessionCompat get(Context context) {
        if (!isMediaBrowserPresent()) {
            return ownSession(context);
        }

        try {
            Method getInstance = sGetInstance;
            if (getInstance == null) {
                getInstance = Class.forName(SINGLETON_CLASS).getMethod("getInstance", Context.class);
                sGetInstance = getInstance;
            }
            return (MediaSessionCompat) getInstance.invoke(null, context.getApplicationContext());
        } catch (ReflectiveOperationException | SecurityException | ClassCastException | LinkageError e) {
            // The library IS present but its contract is unreachable — a packaging/build error such
            // as a missing keep rule or an incompatible pin. Creating a private session here would
            // produce the dual-session state this class exists to prevent, so degrade instead and
            // retry on the next call.
            if (!sIntegrationErrorLogged) {
                sIntegrationErrorLogged = true;
                JWLog.e(TAG, "media-browser present but MediaSessionSingleton unreachable"
                        + " — refusing to create a fallback session", e);
            }
            if (isDebuggable(context)) {
                throw new IllegalStateException(
                        "media-browser/JWPlayer session contract broken", e);
            }
            return null;
        }
        // OutOfMemoryError, StackOverflowError and friends are deliberately not caught: building
        // another MediaSession under VM failure is meaningless. Let them propagate.
    }

    private static boolean isMediaBrowserPresent() {
        Boolean present = sMediaBrowserPresent;
        if (present == null) {
            try {
                // initialize=false: answer "does this class exist?" without running its static init.
                Class.forName(PROBE_CLASS, false, RNJWSharedMediaSession.class.getClassLoader());
                present = Boolean.TRUE;
            } catch (ClassNotFoundException e) {
                present = Boolean.FALSE;
            }
            sMediaBrowserPresent = present;
            JWLog.e(TAG, "source=" + (present ? "media-browser" : "fallback"));
        }
        return present;
    }

    private static MediaSessionCompat ownSession(Context context) {
        MediaSessionCompat own = sOwnSession;
        if (own == null) {
            synchronized (RNJWSharedMediaSession.class) {
                if (sOwnSession == null) {
                    sOwnSession = new MediaSessionCompat(
                            context.getApplicationContext(), "RNJWMediaSession");
                }
                own = sOwnSession;
            }
        }
        return own;
    }

    private static boolean isDebuggable(Context context) {
        try {
            return (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        } catch (Exception e) {
            return false;
        }
    }
}
