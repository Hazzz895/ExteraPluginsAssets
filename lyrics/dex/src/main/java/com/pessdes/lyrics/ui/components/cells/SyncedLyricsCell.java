package com.pessdes.lyrics.ui.components.cells;

import android.content.Context;

import com.pessdes.lyrics.Util;
import com.pessdes.lyrics.components.lrclib.LyricsController;

import org.telegram.ui.ActionBar.Theme;

public class SyncedLyricsCell extends LyricsCell {
    public enum State {
        DEACTIVATED,
        NORMAL,
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
        if (state.ordinal() <= State.NORMAL.ordinal()) {
            this.setTextSize(NORMAL_SIZE);
        }
        else if (state == State.NEXT) {
            this.setTextSize(NEXT_SIZE);
        }
        else if (state == State.ACTIVATED) {
            this.setTextSize(ACTIVATED_SIZE);
        }

        if (state == State.DEACTIVATED) {
            this.setTextColor(Theme.getColor(Theme.key_dialogTextGray));
        }
        else if (state == State.NEXT) {
            this.setTextColor(Util.mixColors(Theme.getColor(Theme.key_dialogTextGray), Theme.getColor(Theme.key_dialogTextBlack)));
        }
        else {
            this.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        }
    }
}
