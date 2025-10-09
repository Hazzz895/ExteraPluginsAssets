package com.pessdes.lyrics.components.lrclib.dto;

import androidx.annotation.Nullable;

import com.pessdes.lyrics.components.lrclib.LyricsController;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class Lyrics {
    public double duration;
    public String plainLyrics;
    public String plainSyncedLyrics;
    @Nullable
    public List<SyncedLyricsLine> syncedLyrics;

    public Lyrics(
            double duration,
            String plainLyrics,
            String plainSyncedLyrics
    ) {
        this.duration = duration;
        this.plainLyrics = plainLyrics;
        this.plainSyncedLyrics = plainSyncedLyrics;
        if (plainSyncedLyrics != null) {
            this.syncedLyrics = LyricsController.getInstance().parseSyncedLyrics(plainSyncedLyrics);
        }
    }
}
