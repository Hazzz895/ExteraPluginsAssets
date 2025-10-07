package com.pessdes.lyrics.ui.components.cells;

import android.content.Context;

import com.pessdes.lyrics.Util;
import com.pessdes.lyrics.components.lrclib.LyricsController;

import org.telegram.ui.ActionBar.Theme;

public class SyncedLyricsCell extends LyricsCell {
    public enum State {
        DEACTIVATED,
        BROWSING,
        NEXT,
        ACTIVATED,
    }

    public SyncedLyricsCell(Context context, State state) {
        super(context);
        this.setTypeface(LyricsController.getInstance().getTypeface());
        setState(state);
    }

    public SyncedLyricsCell(Context context) {
        super(context);
        this.setTypeface(LyricsController.getInstance().getTypeface());
    }

    final int NORMAL_SIZE = 36;
    final int ACTIVATED_SIZE = 42;
    final int NEXT_SIZE = (NORMAL_SIZE + ACTIVATED_SIZE) / 2;

    public void setState(State state) {
        switch (state) {
            case DEACTIVATED:
                this.setTextSize(NORMAL_SIZE);
                this.setTextColor(Theme.getColor(Theme.key_dialogTextGray4));
                break;
            case BROWSING:
                this.setTextSize(NORMAL_SIZE);
                this.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                break;
            case NEXT:
                this.setTextSize(NEXT_SIZE);
                this.setTextColor(Util.mixColors(Theme.getColor(Theme.key_dialogTextGray4), Theme.getColor(Theme.key_dialogTextBlack)));
                break;
            case ACTIVATED:
                this.setTextSize(ACTIVATED_SIZE);
                this.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                break;
        }
    }
}
