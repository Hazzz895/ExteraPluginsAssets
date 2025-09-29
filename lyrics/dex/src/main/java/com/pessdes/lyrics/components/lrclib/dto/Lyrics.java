package com.pessdes.lyrics.components.lrclib.dto;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;

import com.pessdes.lyrics.Util;
import com.pessdes.lyrics.components.lrclib.LyricsController;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class Lyrics {
    public int id;
    public String trackName;
    public String artistName;
    public String albumName;
    public int duration;
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
            int duration,
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

    @SuppressLint("DefaultLocale")
    @NonNull
    @Override
    public String toString() {
        return String.format("Lyrics{id=%d,\ntrackName='%s',\nartistName='%s',\nalbumName='%s',\nduration=%d,\ninstrumental=%b,\nplainLyrics='%s',\nplainSyncedLyrics='%s'}", id, trackName, artistName, albumName, duration, instrumental, Util.trimStringWithEllipsis(plainLyrics, 25), Util.trimStringWithEllipsis(plainSyncedLyrics, 25));
    }
}
