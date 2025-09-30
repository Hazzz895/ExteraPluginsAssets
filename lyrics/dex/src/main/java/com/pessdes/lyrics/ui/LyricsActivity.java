package com.pessdes.lyrics.ui;

import static com.pessdes.lyrics.components.lrclib.LyricsController.log;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.pessdes.lyrics.components.lrclib.LyricsController;
import com.pessdes.lyrics.components.lrclib.dto.Lyrics;
import com.pessdes.lyrics.ui.components.LyricsScroller;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class LyricsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    private final int[] notificationIds = new int[] {
            NotificationCenter.messagePlayingDidReset,
            NotificationCenter.messagePlayingDidStart,
            NotificationCenter.messagePlayingDidSeek,
            NotificationCenter.messagePlayingPlayStateChanged,
            NotificationCenter.messagePlayingSpeedChanged,
            NotificationCenter.messagePlayingProgressDidChanged,
            NotificationCenter.messagePlayingGoingToStop
    };

    private LayerDrawable gradient;
    private LyricsScroller lyricsScroller;

    private Lyrics lastLyrics;
    private MessageObject lastMessageObject;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_close_white);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        final int bgColor = Theme.getColor(Theme.key_windowBackgroundWhite);

        FrameLayout layout;
        fragmentView = layout = new FrameLayout(context);
        layout.setBackgroundColor(bgColor);

        gradient = getLayerDrawable(bgColor);
        layout.setForeground(gradient);

        lyricsScroller = new LyricsScroller(context);
        layout.addView(lyricsScroller, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        updateTitle();

        return fragmentView;
    }

    @NonNull
    private static LayerDrawable getLayerDrawable(int bgColor) {
        final int gradientHeight = 36;

        GradientDrawable topGradient = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{bgColor, Color.TRANSPARENT}
        );
        GradientDrawable bottomGradient = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.TRANSPARENT, bgColor}
        );

        Drawable[] layers = new Drawable[]{topGradient, bottomGradient};
        LayerDrawable layerDrawable = new LayerDrawable(layers);

        layerDrawable.setLayerGravity(0, Gravity.TOP | Gravity.FILL_HORIZONTAL);
        layerDrawable.setLayerSize(0, -1, gradientHeight);

        layerDrawable.setLayerGravity(1, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL);
        layerDrawable.setLayerSize(1, -1, gradientHeight);

        return layerDrawable;
    }

    private void updateTitle() {
        updateTitle(MediaController.getInstance().getPlayingMessageObject() != null);
    }

    private void updateTitle(boolean loaded) {
        String title = null;
        String subTitle = null;
        if (!loaded) {
            title = "Загрузка...";
        }
        else {
            MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
            boolean isNew = lastMessageObject != messageObject;
            lastMessageObject = messageObject;
            title = messageObject.getMusicTitle();
            subTitle = messageObject.getMusicAuthor();
            if (isNew) {
                onMusicLoad();
            }
        }
        actionBar.setTitle(title);
        actionBar.setSubtitle(subTitle);
    }

    private void configureNotifications(boolean enable) {
        for (int id : notificationIds) {
            if (enable) {
                NotificationCenter.getInstance(currentAccount).addObserver(this, id);
            }
            else {
                NotificationCenter.getInstance(currentAccount).removeObserver(this, id);
            }
        }
    }

    @Override
    public boolean onFragmentCreate() {
        configureNotifications(true);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        configureNotifications(false);
        super.onFragmentDestroy();
    }

    private void onMusicPause() {

    }

    private void onMusicPlay() {

    }

    private void onMusicProgressChanged() {

    }

    private void onMusicLoad() {
        MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
        var title = messageObject.getMusicTitle();
        var authors = messageObject.getMusicAuthor();
        var duration = MediaController.getInstance().getPlayingMessageObject().getDuration();
        Utilities.globalQueue.postRunnable(() -> {
            lastLyrics = LyricsController.getInstance().getLyrics(title, authors, duration);
            log("got lyrics: " + (lastLyrics != null));
            if (lastLyrics != null) {
                AndroidUtilities.runOnUIThread(() -> {
                    lyricsScroller.setLyrics(lastLyrics); // Этот метод нужно вернуть/создать

                // Теперь решаем, что делать с адаптером
                if (lyricsScroller.getAdapter() == null) {
                    // Адаптера еще нет. Это первая загрузка.
                    // Создаем и устанавливаем его С УЖЕ ГОТОВЫМИ ДАННЫМИ.
                    log("Adapter is null. Creating and setting a new one.");
                    lyricsScroller.setAdapter(lyricsScroller.new Adapter(getParentActivity()));
                } else {
                    // Адаптер уже есть (сменился трек), просто уведомляем его.
                    log("Adapter exists. Notifying data set changed.");
                    lyricsScroller.getAdapter().notifyDataSetChanged();
                }
            });
            }
        });
    }

    private void onMusicStateChanged() {
        if (MediaController.getInstance().isMessagePaused()) {
            onMusicPause();
        }
        else {
            onMusicPlay();
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.messagePlayingDidStart || id == NotificationCenter.messagePlayingPlayStateChanged || id == NotificationCenter.messagePlayingDidReset) {
            MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
            updateTitle();
            if (id == NotificationCenter.messagePlayingPlayStateChanged) {
                onMusicStateChanged();
            }
            else {
                onMusicPlay();
            }
        }
        else if (id == NotificationCenter.messagePlayingProgressDidChanged) {
            onMusicProgressChanged();
        }
        else if (id == NotificationCenter.messagePlayingSpeedChanged) {
            lyricsScroller.setSpeed(MediaController.getInstance().getPlaybackSpeed(true));
        }
    }
}
