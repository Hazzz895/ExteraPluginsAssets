package com.pessdes.lyrics.ui.components.cells;

import android.content.Context;
import android.graphics.Color;

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
        this.setTextSize(TEXT_SIZE);
        setState(state);
    }

    public SyncedLyricsCell(Context context) {
        super(context);
        this.setTextSize(TEXT_SIZE);
        this.setTypeface(LyricsController.getInstance().getTypeface());
    }

    static int TEXT_SIZE = 36;

    static final float DEACTIVATED_ALPHA = 0.3F;
    static final float NEXT_ALPHA = (DEACTIVATED_ALPHA + 1) / 3;
    public void setState(State state) {
        switch (state) {
            case DEACTIVATED:
            case NEXT:
                this.setTextColor(Util.applyAlpha(Theme.getColor(Theme.key_dialogTextGray4), DEACTIVATED_ALPHA));
                break;
            case BROWSING:
            case ACTIVATED:
                this.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                break;
            /*case NEXT:
                this.setTextColor(Util.applyAlpha(Util.mixColors(Theme.getColor(Theme.key_dialogTextGray4), Theme.getColor(Theme.key_dialogTextBlack)), NEXT_ALPHA));
                break;*/ // # TODO: fix "Next" state
        }
    }
}
