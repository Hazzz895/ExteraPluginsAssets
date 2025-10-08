package com.pessdes.lyrics.ui;

import static com.pessdes.lyrics.components.lrclib.LyricsController.log;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.pessdes.lyrics.components.lrclib.LyricsController;
import com.pessdes.lyrics.components.lrclib.dto.Lyrics;
import com.pessdes.lyrics.components.lrclib.dto.SyncedLyricsLine;
import com.pessdes.lyrics.ui.components.LyricsScroller;
import com.pessdes.lyrics.ui.components.cells.PlainLyricsCell;

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

    private FrameLayout lyricsLayout;
    private LayerDrawable gradient;
    private LyricsScroller lyricsScroller;
    private ScrollView plainLyricsScroller;
    private TextView plainLyricsView;

    private Lyrics lastLyrics;
    private MessageObject currentMessageObject;
    private int currentLyricsLineIndex = -1;
    private boolean isBrowsing = false;
    private final Handler browsingHandler = new Handler(Looper.getMainLooper());
    private final Runnable browsingTimeoutRunnable = () -> {
        isBrowsing = false;
        if (lyricsScroller.getAdapter() != null) {
            lyricsScroller.getAdapter().notifyDataSetChanged();
        }
        lyricsScroller.scrollToLine(currentLyricsLineIndex, true);
    };
    private static final long BROWSING_TIMEOUT = 3000;

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

        fragmentView = new FrameLayout(context);
        FrameLayout layout = (FrameLayout) fragmentView;
        layout.setBackgroundColor(bgColor);

        lyricsLayout = new FrameLayout(context);
        lyricsLayout.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
        gradient = getLayerDrawable(bgColor);
        lyricsLayout.setForeground(gradient);

        lyricsScroller = new LyricsScroller(context, this);
        lyricsLayout.addView(lyricsScroller, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        plainLyricsScroller = new ScrollView(context);
        plainLyricsScroller.setVisibility(View.GONE);
        plainLyricsScroller.setPadding(0, AndroidUtilities.dp(32), 0, AndroidUtilities.dp(32));
        plainLyricsScroller.setClipToPadding(false);
        plainLyricsView = new PlainLyricsCell(context);

        plainLyricsScroller.addView(plainLyricsView);
        lyricsLayout.addView(plainLyricsScroller, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        layout.addView(lyricsLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        onMusicLoad();

        return fragmentView;
    }

    @NonNull
    private static LayerDrawable getLayerDrawable(int bgColor) {
        final int gradientHeight = AndroidUtilities.dp(32);

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

    private void onMusicLoad() {
        boolean loaded = MediaController.getInstance().getPlayingMessageObject() != null;
        String title = null;
        String subTitle = null;
        if (!loaded) {
            title = "Загрузка...";
        }
        else {
            MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
            boolean isNew = currentMessageObject != messageObject;
            currentMessageObject = messageObject;
            title = messageObject.getMusicTitle();
            var authors = messageObject.getMusicAuthor();
            subTitle = authors;
            if (isNew) {
                currentLyricsLineIndex = -1;
                var duration = MediaController.getInstance().getPlayingMessageObject().getDuration();
                final String finalTitle = title;
                Utilities.globalQueue.postRunnable(() -> {
                    lastLyrics = LyricsController.getInstance().getLyrics(finalTitle, authors, duration);
                    AndroidUtilities.runOnUIThread(() -> {
                        if (lastLyrics != null && (lastLyrics.syncedLyrics != null || lastLyrics.plainLyrics != null)) {
                            if (lastLyrics.syncedLyrics != null && !lastLyrics.syncedLyrics.isEmpty()) {
                                lyricsScroller.setVisibility(View.VISIBLE);
                                plainLyricsScroller.setVisibility(View.GONE);
                                lyricsScroller.setLyrics(lastLyrics);
                                lyricsScroller.post(this::onMusicProgressChanged);
                            } else if (lastLyrics.plainLyrics != null) {
                                lyricsScroller.setVisibility(View.GONE);
                                plainLyricsScroller.setVisibility(View.VISIBLE);
                                plainLyricsView.setText(lastLyrics.plainLyrics);
                            }
                        }
                        else {
                            lyricsScroller.setVisibility(View.GONE);
                            plainLyricsScroller.setVisibility(View.GONE);
                        }
                    });
                });
            }
            else {
                onMusicProgressChanged();
            }
        }
        actionBar.setTitle(title);
        actionBar.setSubtitle(subTitle);
    }

    public void setBrowsing(boolean browsing) {
        isBrowsing = browsing;
        browsingHandler.removeCallbacks(browsingTimeoutRunnable);
        if (isBrowsing) {
            browsingHandler.postDelayed(browsingTimeoutRunnable, BROWSING_TIMEOUT);
        }
        if (lyricsScroller.getAdapter() != null) {
            lyricsScroller.getAdapter().notifyDataSetChanged();
        }
    }

    public boolean isBrowsing() {
        return isBrowsing;
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
        if (lyricsScroller.getAdapter() != null) {
            lyricsScroller.getAdapter().notifyItemChanged(0);
        }
    }

    private void onMusicPlay() {
        if (lyricsScroller.getAdapter() != null) {
            lyricsScroller.getAdapter().notifyItemChanged(0);
        }
    }


    private void onMusicProgressChanged() {
        if (lyricsScroller == null || lyricsScroller.getVisibility() != View.VISIBLE || lastLyrics == null) {
            return;
        }

        MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
        if (messageObject == null) {
            return;
        }

        float progress = messageObject.audioProgress;
        double duration = messageObject.getDuration();
        double progressSeconds = progress * duration;

        int newLineIndex = findCurrentLineIndex(lastLyrics, progressSeconds);

        if (newLineIndex != currentLyricsLineIndex) {
            currentLyricsLineIndex = newLineIndex;

            if (lyricsScroller.getAdapter() != null) {
                lyricsScroller.getAdapter().notifyItemRangeChanged(Math.max(0, currentLyricsLineIndex - 3), 7);
            }

            if (!isBrowsing) {
                lyricsScroller.scrollToLine(currentLyricsLineIndex, true);
            }
        }
    }

    private int findCurrentLineIndex(Lyrics lyrics, double progressSeconds) {
        if (lyrics == null || lyrics.syncedLyrics == null || lyrics.syncedLyrics.isEmpty()) {
            return -1;
        }

        long progressMillis = (long) (progressSeconds * 1000);

        int currentLine = -1;
        for (int i = 0; i < lyrics.syncedLyrics.size(); i++) {
            SyncedLyricsLine line = lyrics.syncedLyrics.get(i);
            if (line.timestamp <= progressMillis) {
                currentLine = i;
            } else {
                break;
            }
        }
        return currentLine;
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
            onMusicLoad();
            if (id == NotificationCenter.messagePlayingPlayStateChanged) {
                onMusicStateChanged();
            }
            else {
                onMusicPlay();
            }
        }
        else if (id == NotificationCenter.messagePlayingDidSeek) {
            onMusicProgressChanged();
        }
        else if (id == NotificationCenter.messagePlayingProgressDidChanged) {
            onMusicProgressChanged();
        }
        else if (id == NotificationCenter.messagePlayingSpeedChanged) {
            //lyricsScroller.setSpeed(MediaController.getInstance().getPlaybackSpeed(true));
        }
    }

    public int getCurrentLineIndex() {
        return currentLyricsLineIndex;
    }
}
