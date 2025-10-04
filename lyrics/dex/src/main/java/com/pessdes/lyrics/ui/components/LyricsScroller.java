package com.pessdes.lyrics.ui.components;

import static com.pessdes.lyrics.components.lrclib.LyricsController.log;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pessdes.lyrics.components.lrclib.dto.Lyrics;
import com.pessdes.lyrics.components.lrclib.dto.SyncedLyricsLine;
import com.pessdes.lyrics.ui.LyricsActivity;
import com.pessdes.lyrics.ui.components.cells.SyncedLyricsCell;
import com.pessdes.lyrics.ui.components.cells.TimerCell;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.RecyclerListView;

import java.util.List;

public class LyricsScroller extends RecyclerListView {
    private int itemHeight = 0;
    private final LyricsActivity lyricsActivity;

    public LyricsScroller(Context context, LyricsActivity lyricsActivity) {
        super(context);
        this.lyricsActivity = lyricsActivity;
        this.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        setClipToPadding(false);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);
        int h = getMeasuredHeight();
        if (h > 0) {
            if (itemHeight == 0 && getAdapter() != null && getAdapter().getItemCount() > 0) {
                LyricsAdapter adapter = (LyricsAdapter) getAdapter();
                if (adapter == null) return;

                RecyclerView.ViewHolder holder = adapter.createViewHolder(this, 0);
                adapter.onBindViewHolder(holder, 0);
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
            smoothScrollToPosition(line);
        } else {
            LinearLayoutManager layoutManager = (LinearLayoutManager) getLayoutManager();
            if (layoutManager != null) {
                int offset = getHeight() / 2 - itemHeight;
                layoutManager.scrollToPositionWithOffset(line, offset);
            }
        }
    }

    private static class LyricsAdapter extends RecyclerListView.SelectionAdapter {
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
            return false;
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
            }
            else {
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
                }
                else {
                    timerCell.setVisibility(View.GONE);
                }
            } else if (viewType == TYPE_TEXT) {
                SyncedLyricsCell lyricsCell = (SyncedLyricsCell) holder.itemView;

                int lineIndex = position - 1;
                SyncedLyricsLine line = lyricsLines.get(lineIndex);
                if (line != null) {
                    lyricsCell.setText(line.text);
                }

                int currentActiveLine = lyricsActivity.getCurrentLineIndex();

                if (lineIndex < currentActiveLine) {
                    lyricsCell.setState(SyncedLyricsCell.State.DEACTIVATED);
                } else if (lineIndex == currentActiveLine) {
                    lyricsCell.setState(SyncedLyricsCell.State.ACTIVATED);
                } else {
                    lyricsCell.setState(SyncedLyricsCell.State.NORMAL);
                }
            }
        }

        @Override
        public int getItemCount() {
            // +1 для таймера
            return lyricsLines != null ? lyricsLines.size() + 1 : 0;
        }
    }
}
