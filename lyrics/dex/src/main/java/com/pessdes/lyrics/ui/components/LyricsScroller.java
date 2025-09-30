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
import com.pessdes.lyrics.ui.components.cells.TimerCell;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.RecyclerListView;

public class LyricsScroller extends RecyclerListView {
    public final static int TYPE_TIMER = 0;
    public final static int TYPE_LYRICS = 1;
    public final static int TYPE_FOOTER = 2;

    private Lyrics lyrics;
    private float speed;
    private final Adapter adapter;

    private final int verticalSpaceHeight = AndroidUtilities.dp(36);

    private boolean hasPendingLyricsUpdate = false;

    public LyricsScroller(Context context, Lyrics lyrics) {
        super(context);
        this.lyrics = lyrics;
        adapter = new Adapter(context);
        setAdapter(adapter);
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

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        log("onMeasure: width=" + width + ", height=" + height);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setLyrics(Lyrics lyrics) {
        boolean isNew = this.lyrics != lyrics;
        this.lyrics = lyrics;
        if (isNew) {
            log("setLyrics called. Checking if attached to window...");
            if (isAttachedToWindow()) {
                // Если View уже на экране, обновляем сразу
                log("Is attached. Updating adapter now.");
                adapter.setData(lyrics);
            } else {
                // Если еще нет, просто ставим флаг
                log("Is NOT attached. Setting pending update flag.");
                hasPendingLyricsUpdate = true;
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        log("onAttachedToWindow called.");
        // Когда View прикрепилось, проверяем, не ждет ли нас отложенное обновление
        if (hasPendingLyricsUpdate) {
            log("Pending update found. Updating adapter now.");
            hasPendingLyricsUpdate = false; // Сбрасываем флаг
            adapter.setData(this.lyrics); // Выполняем обновление
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
        private Lyrics adapterLyrics;

        public Adapter(Context context) {
            this.context = context;
            this.adapterLyrics = LyricsScroller.this.lyrics;
        }

        @SuppressLint("NotifyDataSetChanged")
        public void setData(Lyrics newLyrics) {
            this.adapterLyrics = newLyrics;
            log("Adapter received new data, notifying change.");
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0) return TYPE_TIMER;
            else if (position > 0) return TYPE_LYRICS;
            else return -1;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            if (viewType == TYPE_LYRICS) {
                view = new LyricsCell(context);
            }
            else if (viewType == TYPE_TIMER) {
                view = new TimerCell(context, false);
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
                assert adapterLyrics.syncedLyrics != null;
                LyricsCell cell = (LyricsCell) holder.itemView;
                cell.setText(adapterLyrics.syncedLyrics.get(position - 1).text, false);
            }
            else if (holder.getItemViewType() == TYPE_TIMER) {
                TimerCell cell = (TimerCell) holder.itemView;
                cell.startAnimation();
            }
        }

        @Override
        public int getItemCount() {
            if (adapterLyrics == null) {
                log("getItemCount: adapterLyrics is NULL. Returning 0.");
                return 0;
            }
            if (adapterLyrics.syncedLyrics == null) {
                log("getItemCount: adapterLyrics.syncedLyrics is NULL. Returning 0.");
                return 0;
            }

            int count = adapterLyrics.syncedLyrics.size() + 1;
            log("getItemCount: adapterLyrics.syncedLyrics.size() = " + adapterLyrics.syncedLyrics.size() + ". Returning " + count);
            return count;
        }
    }
}
