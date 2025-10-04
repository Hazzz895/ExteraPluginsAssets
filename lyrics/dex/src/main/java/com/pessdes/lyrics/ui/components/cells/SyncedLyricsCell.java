package com.pessdes.lyrics.ui.components.cells;

import android.content.Context;

import com.pessdes.lyrics.components.lrclib.LyricsController;

import org.telegram.ui.ActionBar.Theme;

public class SyncedLyricsCell extends LyricsCell {
    public enum State {
        DEACTIVATED,
        NORMAL,
        ACTIVATED,
    }

    public SyncedLyricsCell(Context context) {
        super(context);
        this.setTypeface(LyricsController.getInstance().getTypeface());
        setState(State.NORMAL);
    }

    public void setState(State state) {
        if (state == State.DEACTIVATED || state == State.NORMAL) {
            this.setTextSize(40);
        }
        else if (state == State.ACTIVATED) {
            this.setTextSize(48);
        }

        if (state == State.DEACTIVATED) {
            this.setTextColor(Theme.getColor(Theme.key_dialogTextGray));
        }
        else {
            this.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        }
    }
}
