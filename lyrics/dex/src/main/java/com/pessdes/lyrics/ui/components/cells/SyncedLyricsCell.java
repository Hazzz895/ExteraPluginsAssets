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
    static int TEXT_SIZE = 42;

    static final float DEACTIVATED_RATIO = 1F;
    static final float NEXT_RATIO = (DEACTIVATED_RATIO + 1) / 2;
    public void setState(State state) {
        switch (state) {
            case DEACTIVATED:
                this.setTextColor(Theme.getColor(Theme.key_dialogTextGray4));
                this.setAlpha(DEACTIVATED_RATIO);
                this.setScaleXAndY(DEACTIVATED_RATIO);
                break;
            case BROWSING:
                this.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                this.setAlpha(1F);
                this.setScaleXAndY(NEXT_RATIO);
                break;
            case NEXT:
                this.setTextColor(Util.mixColors(Theme.getColor(Theme.key_dialogTextGray4), Theme.getColor(Theme.key_dialogTextBlack)));
                this.setAlpha(NEXT_RATIO);
                this.setScaleXAndY(NEXT_RATIO);
                break;
            case ACTIVATED:
                this.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                this.setAlpha(1F);
                this.setScaleXAndY(1F);
                break;
        }
    }

    private void setScaleXAndY(float scale) {
        this.setScaleX(scale);
        this.setScaleY(scale);
    }
}
