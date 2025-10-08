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
        this.setTextSize(TEXT_SIZE);
        setState(state);
    }

    public SyncedLyricsCell(Context context) {
        super(context);
        this.setTextSize(TEXT_SIZE);
        this.setTypeface(LyricsController.getInstance().getTypeface());
    }

    // Специально не сделал final, изменяйте это значение через set_private_field если необходимо
    static int TEXT_SIZE = 36;

    static final float DEACTIVATED_ALPHA = 0.3F;
    static final float NEXT_ALPHA = (DEACTIVATED_ALPHA + 1) / 3;
    public void setState(State state) {
        switch (state) {
            case DEACTIVATED:
                this.setTextColor(Theme.getColor(Theme.key_dialogTextGray4));
                this.setAlpha(DEACTIVATED_ALPHA);
                break;
            case BROWSING:
                this.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                this.setAlpha(1F);
                break;
            case NEXT:
                this.setTextColor(Util.mixColors(Theme.getColor(Theme.key_dialogTextGray4), Theme.getColor(Theme.key_dialogTextBlack)));
                this.setAlpha(NEXT_ALPHA);
                break;
            case ACTIVATED:
                this.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                this.setAlpha(1F);
                break;
        }
    }
}
