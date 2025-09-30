package com.pessdes.lyrics.components.lrclib.dto;

import android.view.View;

import org.jetbrains.annotations.Nullable;

public class SyncedLyricsLine {
    public int timestamp;
    public String text;
    public SyncedLyricsLine(int timestamp, String text) {
        this.timestamp = timestamp;
        this.text = text;
    }
}
