package com.pessdes.chatexporter.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pessdes.chatexporter.tgnet.exported_Chat;
import com.pessdes.chatexporter.tgnet.exported_Chats;

import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.MessageObject;
import android.util.Log;

import java.util.ArrayList;
public class ExportedChatsActivity extends BaseFragment {

    private RecyclerListView listView;
    private ChatListAdapter adapter;
    private LinearLayoutManager layoutManager;

    private exported_Chats exportData;

    private OnChatClickListener onChatClickListener;

    public interface OnChatClickListener {
        void onChatClicked(exported_Chat chat, ExportedChatsActivity fragment);
    }

    public ExportedChatsActivity(exported_Chats data) {
        super();
        exportData = data;
    }

    public ArrayList<exported_Chat> getChats() {
        return exportData.chats;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonDrawable(new BackDrawable(true));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Экспортированные чаты");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        listView = new RecyclerListView(context);
        listView.setVerticalScrollBarEnabled(true);
        layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);
        listView.setLayoutManager(layoutManager);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        adapter = new ChatListAdapter(context, getChats());
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((view, position) -> {
            if (onChatClickListener != null && position >= 0 && position < exportData.chats.size()) {
                exported_Chat clickedChat = exportData.chats.get(position);
                onChatClickListener.onChatClicked(clickedChat, this);
            }
        });

        setOnChatClickListener((chat, fragment) -> {
            fragment.presentFragment(new ExportedChatActivity(chat, false));
        });
        return frameLayout;
    }

    public void setOnChatClickListener(OnChatClickListener listener) {
        this.onChatClickListener = listener;
    }

    private class ChatListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;
        private ArrayList<exported_Chat> chats;

        public ChatListAdapter(Context context, ArrayList<exported_Chat> chats) {
            mContext = context;
            this.chats = chats;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = new DialogCell(null, mContext, false, true);
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            DialogCell dialogCell = (DialogCell) holder.itemView;
            exported_Chat chat = chats.get(position);

            if (chat.peer == null) {
                return;
            }

            TLRPC.Dialog dialog = new TLRPC.TL_dialog();
            long id;

            if (chat.peer instanceof TLRPC.User) {
                id = ((TLRPC.User) chat.peer).id;
            } else if (chat.peer instanceof TLRPC.Chat) {
                id = -((TLRPC.Chat) chat.peer).id;
            } else {
                return;
            }

            dialog.id = id;

            if (chat.messages != null && !chat.messages.isEmpty()) {
                TLRPC.Message lastMessage = (TLRPC.Message) chat.messages.get(chat.messages.size()-1);
                dialog.top_message = lastMessage.id;
                dialog.last_message_date = lastMessage.date;
                ArrayList<MessageObject> messageObjects = new ArrayList<>();
                messageObjects.add(new MessageObject(getCurrentAccount(), lastMessage, true, false));
                getMessagesController().dialogMessage.put(dialog.id, messageObjects);
            }

            dialog.notify_settings = new TLRPC.TL_peerNotifySettings();

            dialogCell.setDialog(dialog, position, 0);
        }

        @Override
        public int getItemCount() {
            return chats.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }
    }
}
