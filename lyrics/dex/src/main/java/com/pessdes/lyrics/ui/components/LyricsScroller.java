package com.pessdes.lyrics.ui.components;

import static com.pessdes.lyrics.components.lrclib.LyricsController.log;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pessdes.lyrics.components.lrclib.dto.Lyrics;
import com.pessdes.lyrics.ui.components.cells.LyricsCell;
import com.pessdes.lyrics.ui.components.cells.SyncedLyricsCell;
import com.pessdes.lyrics.ui.components.cells.TimerCell;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.RecyclerListView;

public class LyricsScroller extends RecyclerListView {
    public final static int TYPE_TIMER = 0;
    public final static int TYPE_LYRICS = 1;
    public final static int TYPE_FOOTER = 2;

    private Lyrics lyrics;
    private float speed;

    private final int verticalSpaceHeight = AndroidUtilities.dp(36);

    public LyricsScroller(Context context, Lyrics lyrics) {
        super(context);
        this.lyrics = lyrics;
        setAdapter(new Adapter(context));
        addItemDecoration(new ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull State state) {
                outRect.bottom = verticalSpaceHeight;
            }
        });
    }

    public Lyrics getLyrics() {
        return lyrics;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setLyrics(Lyrics lyrics) {
        boolean isNew = this.lyrics != lyrics;
        this.lyrics = lyrics;
        if (isNew && getAdapter() != null) {
            log("updating");
            getAdapter().notifyDataSetChanged();
        }
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public float getSpeed() {
        return speed;
    }

    public class Adapter extends RecyclerView.Adapter {
        private final Context context;

        public Adapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0) return TYPE_TIMER;
            else if (position > 0) return TYPE_LYRICS;
                //else if (position == getItemCount()) return TYPE_FOOTER; # TODO: implement footer
            else return -1;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            if (viewType == TYPE_LYRICS) {
                view = new SyncedLyricsCell(context);
            }
            else if (viewType == TYPE_TIMER) {
                view = new TimerCell(context, false);
            }
            else if (viewType == TYPE_FOOTER) {
                view = null;
            }
            else {
                view = null;
            }
            log("creating: " + viewType);
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            log("binding: " + position);
            if (holder.getItemViewType() == TYPE_LYRICS) {
                assert lyrics.syncedLyrics != null;
                LyricsCell cell = (LyricsCell) holder.itemView;
                cell.setText(lyrics.syncedLyrics.get(position-1).text);
            }
            else if (holder.getItemViewType() == TYPE_TIMER) {
                TimerCell cell = (TimerCell) holder.itemView;
                cell.startAnimation();
            }
        }

        @Override
        public int getItemCount() {
            if (lyrics.syncedLyrics == null) return 0;
            else return lyrics.syncedLyrics.size() + 1;
        }
    }
}
