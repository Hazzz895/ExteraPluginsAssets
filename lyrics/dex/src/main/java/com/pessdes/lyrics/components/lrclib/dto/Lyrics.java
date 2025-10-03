package com.pessdes.lyrics.components.lrclib.dto;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;

import com.pessdes.lyrics.Util;
import com.pessdes.lyrics.components.lrclib.LyricsController;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

import java.util.List;

public class Lyrics {
    public int id;
    public String trackName;
    public String artistName;
    public String albumName;
    public double duration;
    public boolean instrumental;
    public String plainLyrics;
    public String plainSyncedLyrics;
    @Nullable
    public List<SyncedLyricsLine> syncedLyrics;

    public Lyrics(
            int id,
            @NotNull String trackName,
            String artistName,
            String albumName,
            double duration,
            boolean instrumental,
            String plainLyrics,
            String plainSyncedLyrics
    ) {
        this.id = id;
        this.trackName = trackName;
        this.artistName = artistName;
        this.albumName = albumName;
        this.duration = duration;
        this.instrumental = instrumental;
        this.plainLyrics = plainLyrics;
        this.plainSyncedLyrics = plainSyncedLyrics;
        if (plainSyncedLyrics != null) {
            this.syncedLyrics = LyricsController.getInstance().parseSyncedLyrics(plainSyncedLyrics);
        }
    }

    public static Lyrics fromJson(JSONObject item) {
        return new Lyrics(
                item.optInt("id", 0),
                item.optString("trackName", null),
                item.optString("artistName", null),
                item.optString("albumName", null),
                item.optDouble("duration", 0),
                item.optBoolean("instrumental", false),
                item.optString("plainLyrics", null),
                item.optString("syncedLyrics", null)
        );
    }
}
