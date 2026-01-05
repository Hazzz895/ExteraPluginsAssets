package com.pessdes.chatexporter.ui;

import static com.pessdes.chatexporter.Util.log;
import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pessdes.chatexporter.tgnet.exported_Chat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatMessageSharedResources;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.ChatAvatarContainer;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerAnimationScrollHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.PhotoViewer;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

public class ExportedChatActivity extends BaseFragment {
    private int rowCount;
    private RecyclerListView chatListView;
    private ChatAvatarContainer avatarContainer;
    private exported_Chat exported;
    private ChatMessageSharedResources sharedResources;
    private ChatActionCell floatingDateView;
    private AnimatorSet floatingDateAnimation;
    private boolean scrollingFloatingDate;
    private LinearLayoutManager manager;
    private ListAdapter adapter;

    private final ArrayList<MessageObject> messageObjects = new ArrayList<>();
    private final LongSparseArray<MessageObject> messageObjectDict = new LongSparseArray<>();
    private final LongSparseArray<TLRPC.Message> messageDict = new LongSparseArray<>();

    private ChatMessageCell dummyMessageCell;
    private final LongSparseArray<MessageObject.GroupedMessages> groupedMessagesMap = new LongSparseArray<>();
    private RecyclerAnimationScrollHelper chatScrollHelper;
    private boolean singleFragment;

    public ExportedChatActivity(@NonNull exported_Chat exported) {
        this(exported, true);
    }

    public ExportedChatActivity(@NonNull exported_Chat exported, boolean singleFragment) {
        this.exported = exported;
        this.singleFragment = singleFragment;
        rowCount = exported.messages.size();

        for (TLRPC.Message msg : exported.messages) {
            messageDict.put(msg.id, msg);
        }
    }

    private PhotoViewer.PhotoViewerProvider provider = new PhotoViewer.EmptyPhotoViewerProvider() {
        @Override
        public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index, boolean needPreview, boolean closing) {
            int count = chatListView.getChildCount();
            for (int a = 0; a < count; a++) {
                ImageReceiver imageReceiver = null;
                View view = chatListView.getChildAt(a);
                if (view instanceof ChatMessageCell) {
                    if (messageObject != null) {
                        ChatMessageCell cell = (ChatMessageCell) view;
                        MessageObject message = cell.getMessageObject();
                        if (message != null && message.getId() == messageObject.getId()) {
                            imageReceiver = cell.getPhotoImage();
                        }
                    }
                } else if (view instanceof ChatActionCell) {
                    ChatActionCell cell = (ChatActionCell) view;
                    MessageObject message = cell.getMessageObject();
                    if (message != null) {
                        if (messageObject != null) {
                            if (message.getId() == messageObject.getId()) {
                                imageReceiver = cell.getPhotoImage();
                            }
                        } else if (fileLocation != null && message.photoThumbs != null) {
                            for (int b = 0; b < message.photoThumbs.size(); b++) {
                                TLRPC.PhotoSize photoSize = message.photoThumbs.get(b);
                                if (photoSize.location.volume_id == fileLocation.volume_id && photoSize.location.local_id == fileLocation.local_id) {
                                    imageReceiver = cell.getPhotoImage();
                                    break;
                                }
                            }
                        }
                    }
                }

                if (imageReceiver != null) {
                    int[] coords = new int[2];
                    view.getLocationInWindow(coords);
                    PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
                    object.viewX = coords[0];
                    object.viewY = coords[1] - (Build.VERSION.SDK_INT >= 21 ? 0 : AndroidUtilities.statusBarHeight);
                    object.parentView = chatListView;
                    object.imageReceiver = imageReceiver;
                    object.thumb = imageReceiver.getBitmapSafe();
                    object.radius = imageReceiver.getRoundRadius(true);
                    object.isEvent = true;
                    return object;
                }
            }
            return null;
        }
    };

    public String getChatName() {
        var chat = exported.peer;
        String name;
        if (chat == null) {
            name = "?";
        } else if (chat instanceof TLRPC.User) {
            var user = ((TLRPC.User) chat);
            name = user.first_name + " " + user.last_name;
        } else if (chat instanceof TLRPC.Chat) {
            name = ((TLRPC.Chat) chat).title;
        } else {
            name = "Просмотр экспорта";
        }
        return name;
    }

    public long getChatId() {
        var chat = exported.peer;
        long id;
        if (chat instanceof TLRPC.User) {
            id = ((TLRPC.User) chat).id;
        } else if (chat instanceof TLRPC.Chat) {
            id = ((TLRPC.Chat) chat).id;
        } else {
            id = 0;
        }
        return id;
    }

    @Override
    public View createView(Context context) {
        sharedResources = new ChatMessageSharedResources(context);
        var name = getChatName();

        Theme.createChatResources(context, false);

        actionBar.setBackButtonDrawable(new BackDrawable(singleFragment));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        actionBar.setAddToContainer(false);
        actionBar.setOccupyStatusBar(!AndroidUtilities.isTablet());

        avatarContainer = new ChatAvatarContainer(context, null, false);
        avatarContainer.setOccupyStatusBar(!AndroidUtilities.isTablet());
        actionBar.addView(avatarContainer, 0, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 56, 0, 40, 0));

        avatarContainer.setTitle(name);
        avatarContainer.setEnabled(true);
        avatarContainer.setSubtitle(LocaleController.formatPluralString("messages", exported.messages.size()));
        if (exported.peer instanceof TLRPC.Chat) avatarContainer.setChatAvatar((TLRPC.Chat) exported.peer);
        else if (exported.peer instanceof TLRPC.User) avatarContainer.setUserAvatar((TLRPC.User) exported.peer);

        fragmentView = new SizeNotifierFrameLayout(context);

        SizeNotifierFrameLayout frameLayout = (SizeNotifierFrameLayout) fragmentView;
        frameLayout.setBackgroundImage(Theme.getCachedWallpaper(), Theme.isWallpaperMotion());

        chatListView = new RecyclerListView(context) {
            @Override
            public boolean drawChild(Canvas canvas, View child, long drawingTime) {
                boolean result = super.drawChild(canvas, child, drawingTime);
                if (child instanceof ChatMessageCell) {
                    ChatMessageCell chatMessageCell = (ChatMessageCell) child;
                    ImageReceiver imageReceiver = chatMessageCell.getAvatarImage();
                    if (imageReceiver != null) {
                        boolean updateVisibility = !chatMessageCell.getMessageObject().deleted && chatListView.getChildAdapterPosition(chatMessageCell) != RecyclerView.NO_POSITION;
                        if (chatMessageCell.getMessageObject().deleted) {
                            imageReceiver.setVisible(false, false);
                            return result;
                        }

                        int top = (int) child.getY();

                        float tx = chatMessageCell.getSlidingOffsetX() + chatMessageCell.getCheckBoxTranslation();
                        int y = (int) child.getY() + chatMessageCell.getLayoutHeight();
                        int maxY = chatListView.getMeasuredHeight() - chatListView.getPaddingBottom();
                        if (y > maxY) {
                            y = maxY;
                        }

                        if (y - dp(48) < top) {
                            y = top + dp(48);
                        }
                        if (!chatMessageCell.drawPinnedBottom()) {
                            int cellBottom = (int) (chatMessageCell.getY() + chatMessageCell.getMeasuredHeight());
                            if (y > cellBottom) {
                                y = cellBottom;
                            }
                        }
                        canvas.save();
                        if (tx != 0) {
                            canvas.translate(tx, 0);
                        }
                        if (chatMessageCell.getCurrentMessagesGroup() != null) {
                            if (chatMessageCell.getCurrentMessagesGroup().transitionParams.backgroundChangeBounds) {
                                y -= (int) chatMessageCell.getTranslationY();
                            }
                        }
                        if (updateVisibility) {
                            imageReceiver.setImageY(y - dp(44));
                        }
                        if (chatMessageCell.shouldDrawAlphaLayer()) {
                            imageReceiver.setAlpha(chatMessageCell.getAlpha());
                            canvas.scale(
                                    chatMessageCell.getScaleX(), chatMessageCell.getScaleY(),
                                    chatMessageCell.getX() + chatMessageCell.getPivotX(), chatMessageCell.getY() + (chatMessageCell.getHeight() >> 1)
                            );
                        } else {
                            imageReceiver.setAlpha(1f);
                        }
                        if (updateVisibility) {
                            imageReceiver.setVisible(true, false);
                        }
                        imageReceiver.draw(canvas);
                        canvas.restore();
                    }
                }
                return result;
            }
        };

        chatListView.setLayoutAnimation(null);
        manager = new LinearLayoutManager(context);
        manager.setStackFromEnd(true);
        chatListView.setLayoutManager(manager);

        chatScrollHelper = new RecyclerAnimationScrollHelper(chatListView, manager);

        chatListView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    scrollingFloatingDate = true;
                } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    scrollingFloatingDate = false;
                    hideFloatingDateView(true);
                }
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                chatListView.invalidate();
                log("scrolled", dy != 0, scrollingFloatingDate, floatingDateView.getTag());
                if (dy != 0 && scrollingFloatingDate) {
                    if (floatingDateView.getTag() == null) {
                        if (floatingDateAnimation != null) {
                            floatingDateAnimation.cancel();
                        }
                        floatingDateView.setTag(1);
                        floatingDateAnimation = new AnimatorSet();
                        floatingDateAnimation.setDuration(150);
                        floatingDateAnimation.playTogether(ObjectAnimator.ofFloat(floatingDateView, "alpha", 1.0f));
                        floatingDateAnimation.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (animation.equals(floatingDateAnimation)) {
                                    floatingDateAnimation = null;
                                }
                            }
                        });
                        floatingDateAnimation.start();
                        log("animation started");
                    }
                }
                updateFloatingDate();
            }
        });

        floatingDateView = new ChatActionCell(context);
        floatingDateView.setAlpha(0.0f);
        floatingDateView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        chatListView.setVerticalScrollBarEnabled(true);
        chatListView.setAdapter(adapter = new ExportedChatActivity.ListAdapter(context));

        frameLayout.addView(chatListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        frameLayout.addView(floatingDateView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 4 + (AndroidUtilities.isTablet() ? 0 : AndroidUtilities.statusBarHeight), 0, 0));
        frameLayout.addView(actionBar);

        return fragmentView;
    }

    private void updateFloatingDate() {
        View child = chatListView.getChildAt(0);
        if (child instanceof ChatMessageCell) {
            MessageObject messageObject = ((ChatMessageCell) child).getMessageObject();
            if (messageObject != null) {
                floatingDateView.setCustomDate(messageObject.messageOwner.date, false, true);
            }
        } else if (child instanceof ChatActionCell) {
            MessageObject messageObject = ((ChatActionCell) child).getMessageObject();
            if (messageObject != null) {
                floatingDateView.setCustomDate(messageObject.messageOwner.date, false, true);
            }
        }
    }

    private void hideFloatingDateView(boolean animated) {
        if (floatingDateView.getTag() != null) {
            floatingDateView.setTag(null);
            if (animated) {
                floatingDateAnimation = new AnimatorSet();
                floatingDateAnimation.setDuration(150);
                floatingDateAnimation.playTogether(ObjectAnimator.ofFloat(floatingDateView, "alpha", 0.0f));
                floatingDateAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (animation.equals(floatingDateAnimation)) {
                            floatingDateAnimation = null;
                        }
                    }
                });
                floatingDateAnimation.setStartDelay(500);
                floatingDateAnimation.start();
            } else {
                if (floatingDateAnimation != null) {
                    floatingDateAnimation.cancel();
                    floatingDateAnimation = null;
                }
                floatingDateView.setAlpha(0.0f);
            }
        }
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
                var cell = new ChatMessageCell(context, getCurrentAccount(), false, sharedResources, getResourceProvider());
                view = cell;
            } else if (viewType == MESSAGE_TYPE_SERVICE) {
                view = new ChatActionCell(context, false, getResourceProvider());
            } else {
                view = new View(context);
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

                if (exported.peer instanceof TLRPC.Chat) {
                    if (position > 0) {
                        MessageObject prevMessage = createMessageObject(exported.messages.get(position - 1));
                        if (prevMessage.isOutOwner() == msg.isOutOwner() && Math.abs(prevMessage.messageOwner.date - msg.messageOwner.date) <= 300 && prevMessage.getFromChatId() == msg.getFromChatId()) {
                            pinnedTop = true;
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
                msg.forceAvatar = !msg.isOutOwner() && (msg.isFromUser() || msg.isFromGroup());
                messageCell.setMessageObject(msg, groupedMessages, pinnedBottom, pinnedTop, false);
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
            if (message == null) return null;

            MessageObject existing = messageObjectDict.get(message.id);
            if (existing != null) {
                return existing;
            }

            MessageObject replyTo = null;
            if (message.reply_to != null) {
                long replyId = message.reply_to.reply_to_msg_id;

                replyTo = messageObjectDict.get(replyId);

                if (replyTo == null) {
                    TLRPC.Message rawReply = messageDict.get(replyId);
                    if (rawReply != null) {
                        replyTo = new MessageObject(getCurrentAccount(), rawReply, false, false);
                        messageObjectDict.put(rawReply.id, replyTo);
                        messageObjects.add(replyTo);
                    }
                }
            }

            var messageObject = new MessageObject(getCurrentAccount(), message, replyTo, false, false);

            messageObjectDict.put(message.id, messageObject);
            messageObjects.add(messageObject);

            return messageObject;
        }

    }

    public void scrollToMessage(int id) {
        int index = -1;
        for(int i=0; i<exported.messages.size(); i++) {
            if(exported.messages.get(i).id == id) {
                index = i;
                break;
            }
        }

        if (index != -1) {
            manager.scrollToPositionWithOffset(index, dp(50));
        }
    }

    private int getScrollOffsetForMessage(MessageObject object) {
        return getScrollOffsetForMessage(getHeightForMessage(object));
    }

    private int getHeightForMessage(MessageObject object) {
        if (getParentActivity() == null) {
            return 0;
        }
        if (dummyMessageCell == null) {
            dummyMessageCell = new ChatMessageCell(getParentActivity(), currentAccount);
        }
        dummyMessageCell.isChat = exported.peer instanceof TLRPC.Chat;
        return dummyMessageCell.computeHeight(object, null, false);
    }

    private int getScrollOffsetForMessage(int messageHeight) {
        return (int) Math.max(-dp(2), (chatListView.getMeasuredHeight() - messageHeight) / 2);
    }
}