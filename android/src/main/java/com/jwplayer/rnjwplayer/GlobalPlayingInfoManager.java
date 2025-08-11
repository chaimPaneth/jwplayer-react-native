package com.jwplayer.rnjwplayer;

import android.content.SharedPreferences;
import android.content.Context;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

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
                
            Log.d(TAG, "Stored playing info: " + mediaId);
        } catch (Exception e) {
            Log.e(TAG, "Error storing playing info", e);
        }
    }
    
    /**
     * Get current playing media info for app restoration
     */
    public Map<String, Object> getCurrentPlayingInfo() {
        try {
            String json = prefs.getString(KEY_CURRENT_PLAYING, null);
            if (json != null) {
                Type type = new TypeToken<Map<String, Object>>(){}.getType();
                Map<String, Object> playingInfo = gson.fromJson(json, type);
                return playingInfo;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving playing info", e);
        }
        return null;
    }
    
    /**
     * Check if there's pending media from headless mode
     */
    public boolean hasPendingMedia() {
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
        prefs.edit()
            .remove(KEY_CURRENT_PLAYING)
            .putBoolean(KEY_IS_PLAYING, false)
            .apply();
        Log.d(TAG, "Cleared playing info");
    }
    
    /**
     * Update playback position
     */
    public void updatePosition(long position) {
        prefs.edit().putLong(KEY_POSITION, position).apply();
    }
    
    /**
     * Get last playback position
     */
    public long getPosition() {
        return prefs.getLong(KEY_POSITION, 0);
    }
}
