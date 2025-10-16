package com.pessdes.lyrics.components.lrclib.providers;

import com.pessdes.lyrics.components.PluginController;
import com.pessdes.lyrics.components.lrclib.dto.Lyrics;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IProvider {
    @Nullable Lyrics seachLyrics(@NotNull String trackName, String artistName, double trackDuration);
    @Nullable String getName();
    @Nullable String getId();
    default int getDefaultPriority() {
        return 0;
    }
    default int getPriority() {
        return PluginController.getInstance().getPluginSettingInt(
                    String.format("__%s_priority__", getId()),
                    getDefaultPriority());
    }
}
