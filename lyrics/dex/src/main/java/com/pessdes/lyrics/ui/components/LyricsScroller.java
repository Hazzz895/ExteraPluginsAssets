package com.pessdes.lyrics.ui.components;

import static com.pessdes.lyrics.components.lrclib.LyricsController.log;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import com.pessdes.lyrics.components.lrclib.dto.Lyrics;
import com.pessdes.lyrics.components.lrclib.dto.SyncedLyricsLine;
import com.pessdes.lyrics.ui.LyricsActivity;
import com.pessdes.lyrics.ui.components.cells.SyncedLyricsCell;
import com.pessdes.lyrics.ui.components.cells.TimerCell;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MediaController;
import org.telegram.ui.Components.RecyclerListView;

import java.util.List;

public class LyricsScroller extends RecyclerListView {
    private int itemHeight = 0;
    private final LyricsActivity lyricsActivity;
    public static final int shift = 1;

    private static final int MAX_SMOOTH_SCROLL_TIME_MS = 400;

    public LyricsScroller(Context context, LyricsActivity lyricsActivity) {
        super(context);
        this.lyricsActivity = lyricsActivity;
        this.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        setClipToPadding(false);

        addOnScrollListener(new OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    lyricsActivity.setBrowsing(true);
                }
            }
        });
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);
        int h = getMeasuredHeight();
        if (h > 0) {
            if (itemHeight == 0 && getAdapter() != null && getAdapter().getItemCount() > 0) {
                LyricsAdapter adapter = (LyricsAdapter) getAdapter();
                if (adapter == null) return;

                RecyclerView.ViewHolder holder = adapter.createViewHolder(this, adapter.TYPE_TEXT);
                adapter.onBindViewHolder(holder, 1);
                View itemView = holder.itemView;

                itemView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                int heightMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
                itemView.measure(widthSpec, heightMeasureSpec);

                itemHeight = itemView.getMeasuredHeight();
            }
            if (itemHeight > 0) {
                int verticalPadding = h / 2 - itemHeight / 2;
                if (getPaddingTop() != verticalPadding) {
                    setPadding(0, verticalPadding, 0, verticalPadding);
                }
            }
        }
    }

    private Lyrics lyrics;

    public void setLyrics(Lyrics lyrics) {
        if (this.lyrics == lyrics) {
            return;
        }
        this.lyrics = lyrics;
        this.setAdapter(new LyricsAdapter(getContext(), lyrics.syncedLyrics, lyricsActivity));
    }

    public void scrollToLine(int line, boolean smooth) {
        if (line < 0 || getAdapter() == null || line >= getAdapter().getItemCount()) {
            return;
        }

        if (smooth) {
            RecyclerView.SmoothScroller smoothScroller = new LinearSmoothScroller(getContext()) {
                @Override
                protected int getVerticalSnapPreference() {
                    return LinearSmoothScroller.SNAP_TO_START;
                }

                @Override
                protected float calculateSpeedPerPixel(DisplayMetrics displayMetrics) {
                    return 100f / displayMetrics.densityDpi;
                }

                @Override
                protected int calculateTimeForScrolling(int dx) {
                    int standardTime = super.calculateTimeForScrolling(dx);
                    return Math.min(standardTime, MAX_SMOOTH_SCROLL_TIME_MS);
                }
            };
            smoothScroller.setTargetPosition(line);
            getLayoutManager().startSmoothScroll(smoothScroller);
        } else {
            LinearLayoutManager layoutManager = (LinearLayoutManager) getLayoutManager();
            if (layoutManager != null) {
                layoutManager.scrollToPositionWithOffset(line, 0);
            }
        }
    }

    private class LyricsAdapter extends RecyclerListView.SelectionAdapter {
        private Context mContext;
        private List<SyncedLyricsLine> lyricsLines;
        private final LyricsActivity lyricsActivity;

        public int TYPE_TIMER = 0;
        public int TYPE_TEXT = 1;

        public LyricsAdapter(Context context, List<SyncedLyricsLine> lines, LyricsActivity lyricsActivity) {
            mContext = context;
            lyricsLines = lines;
            this.lyricsActivity = lyricsActivity;
        }

        @Override
        public boolean isEnabled(@NonNull RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public int getItemViewType(int position) {
            return position == 0 ? TYPE_TIMER : TYPE_TEXT;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            if (viewType == TYPE_TIMER) {
                view = new TimerCell(mContext);
            } else {
                SyncedLyricsCell lyricsCell = new SyncedLyricsCell(mContext);
                lyricsCell.setGravity(Gravity.CENTER);
                lyricsCell.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
                lyricsCell.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));

                view = lyricsCell;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            int viewType = holder.getItemViewType();

            if (viewType == TYPE_TIMER) {
                TimerCell timerCell = (TimerCell) holder.itemView;
                if (lyricsActivity.getCurrentLineIndex() == -1) {
                    timerCell.setVisibility(View.VISIBLE);
                    timerCell.startAnimation();
                } else {
                    timerCell.setVisibility(View.GONE);
                }
            } else if (viewType == TYPE_TEXT) {
                SyncedLyricsCell lyricsCell = (SyncedLyricsCell) holder.itemView;

                int lineIndex = position - shift;
                if (lineIndex < 0 || lineIndex >= lyricsLines.size()) {
                    return;
                }

                SyncedLyricsLine line = lyricsLines.get(lineIndex);
                if (line != null) {
                    lyricsCell.setText(line.text);
                }

                var cellState = getState(lineIndex);
                lyricsCell.setState(cellState);

                holder.itemView.setOnClickListener(v -> {
                    if (lyricsActivity.isBrowsing() && line != null) {
                        MediaController.getInstance().seekToProgressMs(MediaController.getInstance().getPlayingMessageObject(), line.timestamp);
                        lyricsActivity.leaveBrowseMode();
                    }
                });
            }
        }

        private SyncedLyricsCell.State getState(int lineIndex) {
            int currentActiveLine = lyricsActivity.getCurrentLineIndex();

            var cellState = SyncedLyricsCell.State.DEACTIVATED;
            if (lyricsActivity.isBrowsing()) {
                cellState = SyncedLyricsCell.State.BROWSING;
            } else {
                if (lineIndex == currentActiveLine) {
                    cellState = SyncedLyricsCell.State.ACTIVATED;
                } else if (lineIndex == currentActiveLine + 1) {
                    cellState = SyncedLyricsCell.State.NEXT;
                }
            }
            return cellState;
        }

        @Override
        public int getItemCount() {
            return lyricsLines != null ? lyricsLines.size() + shift : 0;
        }
    }
}
