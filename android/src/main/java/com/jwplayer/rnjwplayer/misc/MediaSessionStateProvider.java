//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.jwplayer.rnjwplayer.misc;

import android.support.v4.media.session.MediaSessionCompat;
import androidx.annotation.Nullable;

public final class MediaSessionStateProvider {
    public final MediaSessionCompat mediaSessionCompat;

    public MediaSessionStateProvider(MediaSessionCompat sessionCompat) {
        this.mediaSessionCompat = sessionCompat;
    }

    @Nullable
    public final PlaybackStateCompatWrapper getPlaybackState() {
        return this.mediaSessionCompat.getController().getPlaybackState() == null ? null : new PlaybackStateCompatWrapper(this.mediaSessionCompat.getController().getPlaybackState());
    }
}
