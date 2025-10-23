package com.jwplayer.rnjwplayer;


import android.content.Context;
import android.content.res.Configuration;
import android.view.KeyEvent;

import com.jwplayer.pub.view.JWPlayerView;
import com.jwplayer.rnjwplayer.utils.JWLog;

public class RNJWPlayer extends JWPlayerView {
    private static final String TAG = "RNJWPlayer";
    public Boolean fullScreenOnLandscape = false;
    public Boolean exitFullScreenOnPortrait = false;

    public RNJWPlayer(Context var1) {
        super(var1);
        JWLog.d(TAG, "<init>(context=" + var1 + ")");
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        JWLog.d(TAG, "dispatchKeyEvent(keyCode=" + event.getKeyCode() + ", action=" + event.getAction() + ", fullscreen=" + this.getPlayer().getFullscreen() + ")");
        // Exit fullscreen or perform the action requested
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && this.getPlayer().getFullscreen()) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                this.getPlayer().setFullscreen(false,false);
                JWLog.d(TAG, "dispatchKeyEvent: exit fullscreen on BACK up");
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void requestLayout() {
        JWLog.d(TAG, "requestLayout()");
        super.requestLayout();

        // The spinner relies on a measure + layout pass happening after it calls requestLayout().
        // Without this, the widget never actually changes the selection and doesn't call the
        // appropriate listeners. Since we override onLayout in our ViewGroups, a layout pass never
        // happens after a call to requestLayout, so we simulate one here.
        post(measureAndLayout);
    }


    private final Runnable measureAndLayout = new Runnable() {
        @Override
        public void run() {
            JWLog.d(TAG, "measureAndLayout.run()");
            measure(
                    MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.EXACTLY));
            layout(getLeft(), getTop(), getRight(), getBottom());
        }
    };

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        JWLog.d(TAG, "onConfigurationChanged(orientation=" + newConfig.orientation + ", fullScreenOnLandscape=" + fullScreenOnLandscape + ", exitFullScreenOnPortrait=" + exitFullScreenOnPortrait + ")");

        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (fullScreenOnLandscape) {
                this.getPlayer().setFullscreen(true,true);
                JWLog.d(TAG, "onConfigurationChanged: setFullscreen(true) due to landscape");
            }
        } else if (newConfig.orientation==Configuration.ORIENTATION_PORTRAIT) {
            if (exitFullScreenOnPortrait) {
                this.getPlayer().setFullscreen(false,false);
                JWLog.d(TAG, "onConfigurationChanged: setFullscreen(false) due to portrait");
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        JWLog.d(TAG, "onKeyDown(keyCode=" + keyCode + ", action=" + event.getAction() + ", fullscreen=" + this.getPlayer().getFullscreen() + ")");
        if (keyCode == KeyEvent.KEYCODE_BACK && this.getPlayer().getFullscreen()) {
            this.getPlayer().setFullscreen(false,false);
            JWLog.d(TAG, "onKeyDown: exit fullscreen on BACK down");
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }
}