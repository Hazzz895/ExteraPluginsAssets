package com.pessdes.lyrics.ui.components;

import static com.pessdes.lyrics.components.lrclib.LyricsController.log;

import android.content.Context;
import android.view.Gravity;
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
    public LyricsScroller(Context context) {
        super(context);
        this.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
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
                int offset = getHeight() / 2;
                layoutManager.scrollToPositionWithOffset(line, offset);
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
