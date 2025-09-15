/*
* Some of the code was copied from https://github.com/AyuGram/AyuGram4A/blob/rewrite/TMessagesProj/src/main/java/com/radolyn/ayugram/ui/AyuMessageHistory.java
* */

package com.pessdes.chatexporter.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pessdes.chatexporter.tgnet.exported_Chat;

import org.telegram.messenger.MessageObject;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;

public class ExportedChatActivity extends BaseFragment {
    private int rowCount;
    private RecyclerListView listView;
    private exported_Chat exported;

    public ExportedChatActivity(@NonNull exported_Chat exported) {
        this.exported = exported;
        rowCount = exported.messages.size();
    }

    @Override
    public View createView(Context context) {
        var chat = exported.peer;
        String name;
        if (chat == null) {
            name = "?"; // wtf
        } else if (chat instanceof TLRPC.User) {
            name = ((TLRPC.User) chat).first_name;
        } else if (chat instanceof TLRPC.Chat) {
            name = ((TLRPC.Chat) chat).title;
        } else {
            name = "Просмотр экспорта";
        }
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(name);
        actionBar.setSubtitle(exported.messages.size() + " сообщений");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        fragmentView.setBackground(Theme.getCachedWallpaper());

        listView = new RecyclerListView(context);
        listView.setItemAnimator(null);
        listView.setLayoutAnimation(null);
        LinearLayoutManager manager;
        listView.setLayoutManager(manager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        });
        manager.setStackFromEnd(true);
        listView.setVerticalScrollBarEnabled(true);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(new ExportedChatActivity.ListAdapter(context));

        return fragmentView;
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context context;

        public ListAdapter(Context context) {
            this.context = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            if (viewType == 1) {
                view = new ChatMessageCell(context, getCurrentAccount());
            } else {
                view = null;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == 1) {
                var ayuMessageDetailCell = (ChatMessageCell) holder.itemView;

                var tl_message = exported.messages.get(position);
                var msg = createMessageObject(tl_message);

                ayuMessageDetailCell.setMessageObject(msg, null, false, false, false);
                ayuMessageDetailCell.setId(position);
            }
        }

        @Override
        public int getItemViewType(int position) {
            return position >= 0 && position < exported.messages.size() ? 1 : 0;
        }

        private MessageObject createMessageObject(TLRPC.Message message) {
            return new MessageObject(getCurrentAccount(), message, false, true);
        }

    }
}