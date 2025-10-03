package com.pessdes.lyrics.ui.components.cells;

import android.content.Context;

import org.telegram.ui.ActionBar.Theme;

public class PlainLyricsCell extends LyricsCell {
    public PlainLyricsCell(Context context) {
        super(context);
        this.setTextSize(16);
        this.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
    }
}
