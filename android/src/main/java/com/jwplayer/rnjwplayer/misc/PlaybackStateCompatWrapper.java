//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.jwplayer.rnjwplayer.misc;

import android.support.v4.media.session.PlaybackStateCompat;

public final class PlaybackStateCompatWrapper {
    public PlaybackStateCompat playbackStateCompat;

    public PlaybackStateCompatWrapper(PlaybackStateCompat stateCompat) {
        this.playbackStateCompat = stateCompat;
    }

    public static class Builder {
        public PlaybackStateCompat.Builder builder;

        public Builder(PlaybackStateCompatWrapper playbackStateCompatWrapper) {
            if (playbackStateCompatWrapper != null && playbackStateCompatWrapper.playbackStateCompat != null) {
                this.builder = new PlaybackStateCompat.Builder(playbackStateCompatWrapper.playbackStateCompat);
            } else {
                this.builder = new PlaybackStateCompat.Builder();
            }
        }
    }
}
