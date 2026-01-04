package com.pessdes.chatexporter.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pessdes.chatexporter.tgnet.exported_Chat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatMessageSharedResources;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;

public class ExportedChatActivity extends BaseFragment {
    private int rowCount;
    private RecyclerListView listView;
    private exported_Chat exported;
    private ChatMessageSharedResources sharedResources;

    private final LongSparseArray<MessageObject.GroupedMessages> groupedMessagesMap = new LongSparseArray<>();
    public ExportedChatActivity(@NonNull exported_Chat exported) {
        this.exported = exported;
        rowCount = exported.messages.size();
    }

    @Override
    public View createView(Context context) {
        sharedResources = new ChatMessageSharedResources(context);
        var chat = exported.peer;
        String name;
        if (chat == null) {
            name = "?";
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
        actionBar.setSubtitle(LocaleController.formatPluralString("messages", exported.messages.size()));
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
        public static final int MESSAGE_TYPE_UNKNOWN = 0;
        public static final int MESSAGE_TYPE_MESSAGE = 1;
        public static final int MESSAGE_TYPE_SERVICE = 2;
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
            if (viewType == MESSAGE_TYPE_MESSAGE) {
                view = new ChatMessageCell(context, getCurrentAccount(), false, sharedResources, getResourceProvider());
            } else if (viewType == MESSAGE_TYPE_SERVICE) {
                view = new ChatActionCell(context, false, getResourceProvider());
            } else {
                view = null;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            var tl_message = exported.messages.get(position);
            var msg = createMessageObject(tl_message);

            if (holder.getItemViewType() == MESSAGE_TYPE_MESSAGE) {
                ChatMessageCell messageCell = (ChatMessageCell) holder.itemView;

                boolean pinnedTop = false;
                boolean pinnedBottom = false;
                boolean firstInChat = true;

                if (exported.peer instanceof TLRPC.Chat) {
                    if (position > 0) {
                        MessageObject prevMessage = createMessageObject(exported.messages.get(position - 1));
                        if (prevMessage.isOutOwner() == msg.isOutOwner() && Math.abs(prevMessage.messageOwner.date - msg.messageOwner.date) <= 300 && prevMessage.getFromChatId() == msg.getFromChatId()) {
                            pinnedTop = true;
                            firstInChat = false;
                        }
                    }
                    if (position < rowCount - 1) {
                        MessageObject nextMessage = createMessageObject(exported.messages.get(position + 1));
                        if (nextMessage.isOutOwner() == msg.isOutOwner() && Math.abs(nextMessage.messageOwner.date - msg.messageOwner.date) <= 300 && nextMessage.getFromChatId() == msg.getFromChatId()) {
                            pinnedBottom = true;
                        }
                    }
                }

                MessageObject.GroupedMessages groupedMessages = null;
                if (msg.hasValidGroupId()) {
                    groupedMessages = groupedMessagesMap.get(msg.getGroupIdForUse());
                    if (groupedMessages == null) {
                        groupedMessages = new MessageObject.GroupedMessages();
                        groupedMessages.reversed = false;
                        groupedMessages.groupId = msg.getGroupId();
                        groupedMessagesMap.put(groupedMessages.groupId, groupedMessages);
                    }
                    if (groupedMessages.getPosition(msg) == null) {
                        boolean found = false;
                        for (int j = 0; j < groupedMessages.messages.size(); ++j) {
                            if (groupedMessages.messages.get(j).getId() == msg.getId()) {
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            groupedMessages.messages.add(msg);
                        }
                    }
                }
                messageCell.setMessageObject(msg, groupedMessages, pinnedBottom, pinnedTop, firstInChat);
                messageCell.setId(position);
            }
            else if (holder.getItemViewType() == MESSAGE_TYPE_SERVICE) {
                ChatActionCell actionCell = (ChatActionCell) holder.itemView;
                actionCell.setMessageObject(msg);
                actionCell.setId(position);
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (!(position >= 0 && position < exported.messages.size())) return MESSAGE_TYPE_UNKNOWN;
            else if (exported.messages.get(position) instanceof TLRPC.TL_messageService) return MESSAGE_TYPE_SERVICE;
            else return MESSAGE_TYPE_MESSAGE;
        }

        private MessageObject createMessageObject(TLRPC.Message message) {
            return new MessageObject(getCurrentAccount(), message, false, true);
        }

    }
}