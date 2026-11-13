package com.jwplayer.rnjwplayer;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.ReactActivity;
import com.jwplayer.rnjwplayer.utils.JWLog;

/**
 * Closes a Picture-in-Picture window that Android restored after it stopped the app's process.
 *
 * <p>The PiP window outlives the process. When Android stops the process while the app is in PiP
 * (for a backup or to free memory, usually with the screen off), it restarts the app inside that
 * window once the window is shown again. Nothing in the new process recreates the player, so the
 * window shows only the app's start screen and cannot play. Only that restart creates the first
 * activity of a process already in PiP mode; that activity is finished, the same as the user
 * closing the window, and the app then opens normally from the launcher.
 *
 * <p>Declared as a provider in this library's manifest only so that it starts with the app's
 * process, before the first activity is created. That also covers a process that a media service
 * restarted before the window was shown again. It serves no data.
 */
public class PipRestoreGuard extends ContentProvider implements Application.ActivityLifecycleCallbacks {
    private static final String TAG = "PipRestoreGuard";

    @Override
    public boolean onCreate() {
        Context context = getContext();
        if (context != null && context.getApplicationContext() instanceof Application) {
            ((Application) context.getApplicationContext()).registerActivityLifecycleCallbacks(this);
        }
        return true;
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        // Only the first activity of the process can be a restored PiP window.
        activity.getApplication().unregisterActivityLifecycleCallbacks(this);
        // isInPictureInPictureMode() is set before onCreate runs (Activity.performCreate).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && activity instanceof ReactActivity
                && activity.isInPictureInPictureMode()) {
            JWLog.i(TAG, "PiP window restored without a player; closing it");
            activity.finish();
        }
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {}

    @Override
    public void onActivityResumed(@NonNull Activity activity) {}

    @Override
    public void onActivityPaused(@NonNull Activity activity) {}

    @Override
    public void onActivityStopped(@NonNull Activity activity) {}

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {}

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                        @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        return 0;
    }
}
