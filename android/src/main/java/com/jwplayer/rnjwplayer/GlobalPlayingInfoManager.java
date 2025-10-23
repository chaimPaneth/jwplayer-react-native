package com.jwplayer.rnjwplayer;

import android.content.SharedPreferences;
import android.content.Context;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import com.jwplayer.rnjwplayer.utils.JWLog;

/**
 * Global manager for storing and retrieving current playing media info
 * Used for headless mode restoration when app is opened after media selection
 */
public class GlobalPlayingInfoManager {
    private static final String TAG = "GlobalPlayingInfo";
    private static final String PREFS_NAME = "global_playing_info";
    private static final String KEY_CURRENT_PLAYING = "current_playing";
    private static final String KEY_IS_PLAYING = "is_playing";
    private static final String KEY_POSITION = "position";
    private static final String KEY_TIMESTAMP = "timestamp";
    
    private static GlobalPlayingInfoManager instance;
    private final SharedPreferences prefs;
    private final Gson gson;
    
    private GlobalPlayingInfoManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }
    
    public static synchronized GlobalPlayingInfoManager getInstance(Context context) {
        JWLog.d(TAG, "getInstance(context=" + context + ")");
        if (instance == null) {
            instance = new GlobalPlayingInfoManager(context);
        }
        return instance;
    }
    
    /**
     * Store current playing media info from headless mode
     */
    public void setCurrentPlayingInfo(String mediaId, String title, String subtitle, 
                                     String icon, Map<String, Object> extras) {
        try {
            JWLog.d(TAG, "setCurrentPlayingInfo(mediaId=" + JWLog.safe(mediaId) + ", title=" + JWLog.safe(title) + ")");
            Map<String, Object> playingInfo = new HashMap<>();
            playingInfo.put("mediaId", mediaId);
            playingInfo.put("title", title);
            playingInfo.put("subtitle", subtitle);
            playingInfo.put("icon", icon);
            playingInfo.put("extras", extras);
            
            String json = gson.toJson(playingInfo);
            prefs.edit()
                .putString(KEY_CURRENT_PLAYING, json)
                .putBoolean(KEY_IS_PLAYING, true)
                .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
                .apply();
                
            JWLog.d(TAG, "Stored playing info: " + mediaId);
        } catch (Exception e) {
            JWLog.e(TAG, "Error storing playing info", e);
        }
    }
    
    /**
     * Get current playing media info for app restoration
     */
    public Map<String, Object> getCurrentPlayingInfo() {
        try {
            JWLog.d(TAG, "getCurrentPlayingInfo()");
            String json = prefs.getString(KEY_CURRENT_PLAYING, null);
            if (json != null) {
                Type type = new TypeToken<Map<String, Object>>(){}.getType();
                Map<String, Object> playingInfo = gson.fromJson(json, type);
                return playingInfo;
            }
        } catch (Exception e) {
            JWLog.e(TAG, "Error retrieving playing info", e);
        }
        return null;
    }
    
    /**
     * Check if there's pending media from headless mode
     */
    public boolean hasPendingMedia() {
        JWLog.d(TAG, "hasPendingMedia()");
        String json = prefs.getString(KEY_CURRENT_PLAYING, null);
        boolean isPlaying = prefs.getBoolean(KEY_IS_PLAYING, false);
        long timestamp = prefs.getLong(KEY_TIMESTAMP, 0);
        
        // Consider media pending if set within last 5 minutes
        long fiveMinutes = 5 * 60 * 1000;
        boolean isRecent = (System.currentTimeMillis() - timestamp) < fiveMinutes;
        
        return json != null && isPlaying && isRecent;
    }
    
    /**
     * Clear playing info when media is handled
     */
    public void clearPlayingInfo() {
        JWLog.d(TAG, "clearPlayingInfo()");
        prefs.edit()
            .remove(KEY_CURRENT_PLAYING)
            .putBoolean(KEY_IS_PLAYING, false)
            .apply();
        JWLog.d(TAG, "Cleared playing info");
    }
    
    /**
     * Update playback position
     */
    public void updatePosition(long position) {
        JWLog.d(TAG, "updatePosition(position=" + position + ")");
        prefs.edit().putLong(KEY_POSITION, position).apply();
    }
    
    /**
     * Get last playback position
     */
    public long getPosition() {
        JWLog.d(TAG, "getPosition()");
        return prefs.getLong(KEY_POSITION, 0);
    }
}
