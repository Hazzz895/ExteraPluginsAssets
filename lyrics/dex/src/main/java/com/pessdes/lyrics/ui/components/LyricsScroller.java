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
import com.pessdes.lyrics.ui.components.cells.SyncedLyricsCell;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.RecyclerListView;

import java.util.List;

public class LyricsScroller extends RecyclerListView {
    private int itemHeight = 0;

    public LyricsScroller(Context context) {
        super(context);
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
        this.setAdapter(new LyricsAdapter(getContext(), lyrics.syncedLyrics));
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
                layoutManager.scrollToPositionWithOffset(line + 5, offset);
            }
        }
    }

    private static class LyricsAdapter extends RecyclerListView.SelectionAdapter {
        private Context mContext;
        private List<SyncedLyricsLine> lyricsLines;

        public LyricsAdapter(Context context, List<SyncedLyricsLine> lines) {
            mContext = context;
            lyricsLines = lines;
            log("ok");
        }

        @Override
        public boolean isEnabled(@NonNull RecyclerView.ViewHolder holder) {
            return false;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            log("creating view holder " + viewType);
            SyncedLyricsCell lyricsCell = new SyncedLyricsCell(mContext);
            lyricsCell.setGravity(Gravity.CENTER);
            lyricsCell.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));

            lyricsCell.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));

            return new RecyclerListView.Holder(lyricsCell);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            log("binding view holder " + position);
            SyncedLyricsCell lyricsCell = (SyncedLyricsCell) holder.itemView;
            SyncedLyricsLine line = lyricsLines.get(position);
            if (line != null) {
                lyricsCell.setText(line.text);
            }
        }

        @Override
        public int getItemCount() {
            return lyricsLines != null ? lyricsLines.size() : 0;
        }
    }
}
