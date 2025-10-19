package com.pessdes.lyrics.ui;

import static com.pessdes.lyrics.components.lrclib.LyricsController.log;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.pessdes.lyrics.components.PluginController;
import com.pessdes.lyrics.components.lrclib.LyricsController;
import com.pessdes.lyrics.components.lrclib.dto.Lyrics;
import com.pessdes.lyrics.components.lrclib.dto.SyncedLyricsLine;
import com.pessdes.lyrics.ui.components.LyricsScroller;
import com.pessdes.lyrics.ui.components.cells.PlainLyricsCell;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Charts.data.ChartData;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.StickerEmptyView;
import org.telegram.ui.Components.StickerImageView;
import org.telegram.ui.Stories.recorder.HintView2;

import java.util.ArrayList;
import java.util.List;

public class LyricsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    private final int[] notificationIds = new int[]{
            NotificationCenter.messagePlayingDidReset,
            NotificationCenter.messagePlayingDidStart,
            NotificationCenter.messagePlayingDidSeek,
            NotificationCenter.messagePlayingPlayStateChanged,
            NotificationCenter.messagePlayingProgressDidChanged,
    };

    private ViewPager viewPager;
    private LyricsPagerAdapter pagerAdapter;
    private StickerEmptyView statusStickerView;
    private FrameLayout lyricsScrollerLayout;
    private LyricsScroller lyricsScroller;
    private ScrollView plainLyricsScroller;
    private TextView plainLyricsView;
    private ActionBarMenuItem swapButton;

    private Lyrics lastLyrics;
    private MessageObject currentMessageObject;
    private int currentLyricsLineIndex = -1;
    private boolean isBrowsing = false;
    private final Handler browsingHandler = new Handler(Looper.getMainLooper());

    private static final long BROWSING_TIMEOUT = 3000;
    private static final int SWAP_BUTTON_ID = 1;

    @Override
    public View createView(Context context) {
        log("creatinjjjjg view");
        log("try below");
        try {
            log("tringg");
            actionBar.setBackButtonImage(R.drawable.ic_close_white);
            actionBar.setAllowOverlayTitle(true);
            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        finishFragment();
                    } else if (id == SWAP_BUTTON_ID) {
                        var currentItem = viewPager.getCurrentItem();
                        var pageCount = 1;

                        if (viewPager.getAdapter() != null) {
                            pageCount = viewPager.getAdapter().getCount();
                        }

                        if (pageCount == 1) {
                            AndroidUtilities.shakeView(getFragmentView());
                        } else {
                            var newItem = currentItem == pageCount - 1 ? 0 : currentItem + 1;
                            viewPager.setCurrentItem(newItem, true);
                        }
                    }
                }
            });
            log("menu");
            var menu = actionBar.createMenu();
            swapButton = menu.addItem(SWAP_BUTTON_ID, R.drawable.msg_photo_text_framed3);
            swapButton.setVisibility(View.GONE);

            log("bgcolor");
            final int bgColor = Theme.getColor(Theme.key_windowBackgroundWhite);

            log("frgrgment view");
            fragmentView = new FrameLayout(context);
            FrameLayout layout = (FrameLayout) fragmentView;
            layout.setBackgroundColor(bgColor);

            log("statusstickerview");
            statusStickerView = new StickerEmptyView(context, null, StickerEmptyView.STICKER_TYPE_SEARCH, resourceProvider);
            statusStickerView.setVisibility(View.GONE, false);
            layout.addView(statusStickerView);

            log("creating viewpager");
            viewPager = new ViewPager(context);
            pagerAdapter = new LyricsPagerAdapter();
            viewPager.setAdapter(pagerAdapter);
            layout.addView(viewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            log("creating lyrciscrolelr");
            lyricsScroller = new LyricsScroller(context, this);
            lyricsScrollerLayout = new FrameLayout(context);
            lyricsScrollerLayout.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
            lyricsScrollerLayout.addView(lyricsScroller, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            plainLyricsScroller = new ScrollView(context);
            plainLyricsScroller.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(32), AndroidUtilities.dp(16), AndroidUtilities.dp(32));
            plainLyricsScroller.setClipToPadding(false);
            plainLyricsView = new PlainLyricsCell(context);
            plainLyricsView.setTextIsSelectable(true);
            plainLyricsScroller.addView(plainLyricsView);

            layout.setForeground(getLayerDrawable(bgColor));

            configureNotifications(true);
            log("onmusicload");
            onMusicLoad();

            return fragmentView;
        } catch (Exception e) {
            log("Error while creating view: " + e);
            throw e;
        }
    }

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
        boolean loaded = MediaController.getInstance().getPlayingMessageObject() != null && fragmentView != null;
        String title = null;
        String subTitle = null;

        if (!loaded) {
            title = LocaleController.getString(R.string.Loading);
        } else {
            if (lyricsScroller != null && lyricsScroller.getAdapter() != null) {
                lyricsScroller.getAdapter().notifyItemChanged(0);
            }
            MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
            boolean isNew = currentMessageObject != messageObject;
            currentMessageObject = messageObject;
            title = messageObject.getMusicTitle();
            var authors = messageObject.getMusicAuthor();
            subTitle = authors;
            if (isNew) {
                setStatus(LocaleController.getString(R.string.Gift2ResaleFiltersSearch), PluginController.getInstance().locale("FetchingLyrics"));
                viewPager.setVisibility(View.GONE);
                swapButton.setVisibility(View.GONE);

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
                                pages.add(lyricsScrollerLayout);
                            }
                            if (lastLyrics.plainLyrics != null) {
                                plainLyricsView.setText(lastLyrics.plainLyrics);
                                pages.add(plainLyricsScroller);
                            }
                            if (lastLyrics.plainLyrics != null && lastLyrics.syncedLyrics != null) {
                                swapButton.setVisibility(View.VISIBLE);
                            }
                            else {
                                swapButton.setVisibility(View.GONE);
                            }
                        }

                        if (pages.isEmpty()) {
                            setStatus(LocaleController.getString(R.string.NoResult), String.format(LocaleController.getString(R.string.NoResultFoundForTag), String.format("«%s - %s»", finalTitle, authors)));
                            viewPager.setVisibility(View.GONE);
                        } else {
                            hideStatus();
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

    private void hideStatus() {
        setStatus(null, null);
    }

    private void setStatus(String title, String subtitle) {
        if (title == null && subtitle == null) {
            statusStickerView.setVisibility(View.GONE);
            return;
        }

        statusStickerView.setVisibility(View.VISIBLE);
        statusStickerView.title.setVisibility(title == null ? View.GONE : View.VISIBLE);
        statusStickerView.subtitle.setVisibility(title == null ? View.GONE : View.VISIBLE);

        if (title != null) {
            statusStickerView.title.setText(title);
        }
        if (subtitle != null) {
            statusStickerView.subtitle.setText(subtitle);
        }

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
    public void onFragmentDestroy() {
        configureNotifications(false);
        super.onFragmentDestroy();
    }

    private void onMusicProgressChanged(boolean animated) {
        if (lyricsScroller == null || viewPager.getVisibility() != View.VISIBLE || lastLyrics == null || isBrowsing) {
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

            lyricsScroller.scrollToLine(lineIndex, animated);
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
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (fragmentView == null) {
            return;
        }
        if (id == NotificationCenter.messagePlayingDidStart || id == NotificationCenter.messagePlayingPlayStateChanged || id == NotificationCenter.messagePlayingDidReset) {
            onMusicLoad();
        } else if (id == NotificationCenter.messagePlayingDidSeek || id == NotificationCenter.messagePlayingProgressDidChanged) {
            onMusicProgressChanged(true);
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
            if (view.getParent() != null) {
                ((ViewGroup) view.getParent()).removeView(view);
            }
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
