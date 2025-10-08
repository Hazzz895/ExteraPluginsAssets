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
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

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

import java.util.ArrayList;
import java.util.List;

public class LyricsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    private final int[] notificationIds = new int[]{
            NotificationCenter.messagePlayingDidReset,
            NotificationCenter.messagePlayingDidStart,
            NotificationCenter.messagePlayingDidSeek,
            NotificationCenter.messagePlayingPlayStateChanged,
            NotificationCenter.messagePlayingSpeedChanged,
            NotificationCenter.messagePlayingProgressDidChanged,
            NotificationCenter.messagePlayingGoingToStop
    };

    private ViewPager viewPager;
    private LyricsPagerAdapter pagerAdapter;
    private TextView statusTextView;

    private LyricsScroller lyricsScroller;
    private ScrollView plainLyricsScroller;
    private TextView plainLyricsView;

    private Lyrics lastLyrics;
    private MessageObject currentMessageObject;
    private int currentLyricsLineIndex = -1;
    private boolean isBrowsing = false;
    private final Handler browsingHandler = new Handler(Looper.getMainLooper());

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

        statusTextView = new TextView(context);
        statusTextView.setTextSize(16);
        statusTextView.setGravity(Gravity.CENTER);
        statusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        layout.addView(statusTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        viewPager = new ViewPager(context);
        pagerAdapter = new LyricsPagerAdapter();
        viewPager.setAdapter(pagerAdapter);
        layout.addView(viewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        lyricsScroller = new LyricsScroller(context, this);
        plainLyricsScroller = new ScrollView(context);
        plainLyricsScroller.setPadding(0, AndroidUtilities.dp(32), 0, AndroidUtilities.dp(32));
        plainLyricsScroller.setClipToPadding(false);
        plainLyricsView = new PlainLyricsCell(context);
        plainLyricsScroller.addView(plainLyricsView);

        onMusicLoad();

        return fragmentView;
    }

    private void onMusicLoad() {
        boolean loaded = MediaController.getInstance().getPlayingMessageObject() != null;
        String title = null;
        String subTitle = null;

        statusTextView.setText("получение данных");
        statusTextView.setVisibility(View.VISIBLE);
        viewPager.setVisibility(View.GONE);

        if (!loaded) {
            title = "Загрузка...";
        } else {
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
                        List<View> pages = new ArrayList<>();
                        if (lastLyrics != null) {
                            if (lastLyrics.syncedLyrics != null && !lastLyrics.syncedLyrics.isEmpty()) {
                                lyricsScroller.setLyrics(lastLyrics);
                                pages.add(lyricsScroller);
                            }
                            if (lastLyrics.plainLyrics != null) {
                                plainLyricsView.setText(lastLyrics.plainLyrics);
                                pages.add(plainLyricsScroller);
                            }
                        }

                        if (pages.isEmpty()) {
                            statusTextView.setText("не найден текст");
                            statusTextView.setVisibility(View.VISIBLE);
                            viewPager.setVisibility(View.GONE);
                        } else {
                            statusTextView.setVisibility(View.GONE);
                            viewPager.setVisibility(View.VISIBLE);
                            pagerAdapter.setPages(pages);
                            if (lastLyrics.syncedLyrics != null && !lastLyrics.syncedLyrics.isEmpty()) {
                                lyricsScroller.post(() -> onMusicProgressChanged(false));
                            }
                        }
                    });
                });
            } else {
                onMusicProgressChanged(false);
            }
        }
        actionBar.setTitle(title);
        actionBar.setSubtitle(subTitle);
    }

    public void setBrowsing(boolean browsing) {
        isBrowsing = browsing;
        browsingHandler.removeCallbacks(this::leaveBrowseMode);
        if (isBrowsing) {
            browsingHandler.postDelayed(this::leaveBrowseMode, BROWSING_TIMEOUT);
        }
        if (lyricsScroller.getAdapter() != null) {
            lyricsScroller.getAdapter().notifyDataSetChanged();
        }
    }

    public void leaveBrowseMode() {
        isBrowsing = false;
        if (lyricsScroller.getAdapter() != null) {
            lyricsScroller.getAdapter().notifyDataSetChanged();
        }
        lyricsScroller.scrollToLine(currentLyricsLineIndex + LyricsScroller.shift, true);
    }

    public boolean isBrowsing() {
        return isBrowsing;
    }

    private void configureNotifications(boolean enable) {
        for (int id : notificationIds) {
            if (enable) {
                NotificationCenter.getInstance(currentAccount).addObserver(this, id);
            } else {
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

    private void onMusicProgressChanged(boolean animated) {
        if (lyricsScroller == null || viewPager.getVisibility() != View.VISIBLE || lastLyrics == null) {
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
            int oldLineIndex = currentLyricsLineIndex;
            currentLyricsLineIndex = newLineIndex;

            final int lineIndex = currentLyricsLineIndex + LyricsScroller.shift;
            if (lyricsScroller.getAdapter() != null) {
                lyricsScroller.getAdapter().notifyItemChanged(oldLineIndex + LyricsScroller.shift);
                lyricsScroller.getAdapter().notifyItemChanged(lineIndex);
                lyricsScroller.getAdapter().notifyItemChanged(lineIndex + 1);
                if (oldLineIndex == -1 || newLineIndex == -1) {
                    lyricsScroller.getAdapter().notifyItemChanged(0);
                }
            }

            if (!isBrowsing) {
                lyricsScroller.scrollToLine(lineIndex, animated);
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
        } else {
            onMusicPlay();
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.messagePlayingDidStart || id == NotificationCenter.messagePlayingPlayStateChanged || id == NotificationCenter.messagePlayingDidReset) {
            onMusicLoad();
            if (id == NotificationCenter.messagePlayingPlayStateChanged) {
                onMusicStateChanged();
            } else {
                onMusicPlay();
            }
        } else if (id == NotificationCenter.messagePlayingDidSeek) {
            onMusicProgressChanged(true);
        } else if (id == NotificationCenter.messagePlayingProgressDidChanged) {
            onMusicProgressChanged(true);
        } else if (id == NotificationCenter.messagePlayingSpeedChanged) {
            //lyricsScroller.setSpeed(MediaController.getInstance().getPlaybackSpeed(true));
        }
    }

    public int getCurrentLineIndex() {
        return currentLyricsLineIndex;
    }

    private class LyricsPagerAdapter extends PagerAdapter {
        private List<View> pages = new ArrayList<>();

        public void setPages(List<View> pages) {
            this.pages = pages;
            notifyDataSetChanged();
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            View view = pages.get(position);
            container.addView(view);
            return view;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View) object);
        }

        @Override
        public int getCount() {
            return pages.size();
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }
    }
}
