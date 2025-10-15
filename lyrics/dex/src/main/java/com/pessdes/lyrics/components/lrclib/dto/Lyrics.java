package com.pessdes.lyrics.components.lrclib.dto;

import androidx.annotation.Nullable;

import com.pessdes.lyrics.components.lrclib.LyricsController;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class Lyrics {
    public double duration;
    public @Nullable String plainLyrics;
    public @Nullable String plainSyncedLyrics;
    public @Nullable List<SyncedLyricsLine> syncedLyrics;
    public Lyrics(
            double duration,
            @Nullable String plainLyrics,
            @Nullable String plainSyncedLyrics
    ) {
        this.duration = duration;
        this.plainLyrics = plainLyrics;
        this.plainSyncedLyrics = plainSyncedLyrics;
    }
}
