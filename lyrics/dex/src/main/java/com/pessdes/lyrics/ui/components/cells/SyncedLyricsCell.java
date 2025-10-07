package com.pessdes.lyrics.ui.components.cells;

import android.content.Context;
import android.util.TypedValue;

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

    final int NORMAL_SIZE = 18;
    final int ACTIVATED_SIZE = 21;
    final int NEXT_SIZE = (NORMAL_SIZE + ACTIVATED_SIZE) / 2;

    public void setState(State state) {
        switch (state) {
            case DEACTIVATED:
                this.setTextSize(TypedValue.COMPLEX_UNIT_SP, NORMAL_SIZE);
                this.setTextColor(Theme.getColor(Theme.key_dialogTextGray));
                break;
            case BROWSING:
                this.setTextSize(TypedValue.COMPLEX_UNIT_SP, NORMAL_SIZE);
                this.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                break;
            case NEXT:
                this.setTextSize(TypedValue.COMPLEX_UNIT_SP, NEXT_SIZE);
                this.setTextColor(Util.mixColors(Theme.getColor(Theme.key_dialogTextGray), Theme.getColor(Theme.key_dialogTextBlack)));
                break;
            case ACTIVATED:
                this.setTextSize(TypedValue.COMPLEX_UNIT_SP, ACTIVATED_SIZE);
                this.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                break;
        }
    }
}
