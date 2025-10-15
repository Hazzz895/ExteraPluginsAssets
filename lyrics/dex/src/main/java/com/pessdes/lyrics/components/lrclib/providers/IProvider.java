package com.pessdes.lyrics.components.lrclib.providers;

import com.pessdes.lyrics.components.lrclib.dto.Lyrics;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IProvider {
    public @Nullable Lyrics seachLyrics(@NotNull String trackName, String artistName, double trackDuration);
    public @Nullable String getName();
}
