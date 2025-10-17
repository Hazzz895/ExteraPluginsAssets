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
    private LinearLayout statusLayout;
    private StickerImageView statusStickerImageView;
    private TextView statusTitle;
    private TextView statusSubtitle;
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
        actionBar.setBackButtonImage(R.drawable.ic_close_white);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
                else if (id == SWAP_BUTTON_ID) {
                    var currentItem = viewPager.getCurrentItem();
                    var pageCount = 1;

                    if (viewPager.getAdapter() != null) {
                        pageCount = viewPager.getAdapter().getCount();
                    }

                    if (pageCount == 1) {
                        AndroidUtilities.shakeView(getFragmentView());
                    }
                    else {
                        var newItem = currentItem == pageCount - 1 ? 0 : currentItem + 1;
                        viewPager.setCurrentItem(newItem, true);
                    }
                }
            }
        });
        var menu = actionBar.createMenu();
        swapButton = menu.addItem(SWAP_BUTTON_ID, R.drawable.msg_photo_text_framed3);
        swapButton.setVisibility(View.GONE);

        final int bgColor = Theme.getColor(Theme.key_windowBackgroundWhite);

        fragmentView = new FrameLayout(context);
        FrameLayout layout = (FrameLayout) fragmentView;
        layout.setBackgroundColor(bgColor);

        statusLayout = new LinearLayout(context);
        statusLayout.setOrientation(LinearLayout.VERTICAL);
        statusLayout.setGravity(Gravity.CENTER);

        statusStickerImageView = new StickerImageView(context, currentAccount);
        statusStickerImageView.setStickerPackName("Alegquin109"); // крутой пак с уточками (https://t.me/addstickers/Alegquin109)
        statusLayout.addView(statusStickerImageView, LayoutHelper.createLinear(117, 117, Gravity.CENTER_HORIZONTAL));

        statusTitle = new TextView(context);
        statusTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        statusTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        statusLayout.addView(statusTitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 12, 0, 0));

        statusSubtitle = new TextView(context);
        statusTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        statusTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        statusLayout.addView(statusTitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 8, 0, 0));
        layout.addView(statusLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 46, 0, 46, 30));

        viewPager = new ViewPager(context);
        pagerAdapter = new LyricsPagerAdapter();
        viewPager.setAdapter(pagerAdapter);
        layout.addView(viewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

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
        onMusicLoad();

        return fragmentView;
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
        boolean loaded = MediaController.getInstance().getPlayingMessageObject() != null;
        String title = null;
        String subTitle = null;

        if (!loaded) {
            title = LocaleController.getString(R.string.Loading);
        } else {
            MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
            boolean isNew = currentMessageObject != messageObject;
            currentMessageObject = messageObject;
            title = messageObject.getMusicTitle();
            var authors = messageObject.getMusicAuthor();
            subTitle = authors;
            if (isNew) {
                setStatus(110, LocaleController.getString(R.string.Gift2ResaleFiltersSearch), PluginController.getInstance().locale("FetchingLyrics"));
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
                            setStatus(66, LocaleController.getString(R.string.NoResult), String.format(LocaleController.getString(R.string.NoResultFoundForTag), String.format("«%s - %s»", finalTitle, authors)));
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
        setStatus(-1, null, null);
    }

    private void setStatus(int strickerNum, String title, String subtitle) {
        if (title == null && subtitle == null && strickerNum < 0) {
            statusLayout.setVisibility(View.GONE);
            return;
        }

        statusLayout.setVisibility(View.VISIBLE);
        statusStickerImageView.setVisibility(strickerNum < 0 ? View.GONE : View.VISIBLE);
        statusTitle.setVisibility(title == null ? View.GONE : View.VISIBLE);
        statusSubtitle.setVisibility(title == null ? View.GONE : View.VISIBLE);

        if (title != null) {
            statusTitle.setText(title);
        }
        if (subtitle != null) {
            statusSubtitle.setText(subtitle);
        }

        statusLayout.setAlpha(0);
        statusLayout.animate()
                .alpha(1)
                .setDuration(300)
                .start();
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
