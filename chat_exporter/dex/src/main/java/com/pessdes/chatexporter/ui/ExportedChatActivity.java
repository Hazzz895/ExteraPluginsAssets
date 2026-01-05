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
import android.os.Bundle;
import android.text.style.CharacterStyle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pessdes.chatexporter.tgnet.exported_Chat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotInlineKeyboard;
import org.telegram.messenger.ChatMessageSharedResources;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLObject;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.ChatAvatarContainer;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerAnimationScrollHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.PhotoViewer;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.PinchToZoomHelper;

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
            name = user.first_name;
            if (user.last_name != null) {
                name += " " + user.last_name;
            }
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
        chatListView.setClipToPadding(false);
        var topPad = ActionBar.getCurrentActionBarHeight() + AndroidUtilities.getStatusBarHeight(context);
        chatListView.setPadding(0, topPad, 0,0);
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
                if (scrollingFloatingDate) {
                    if (floatingDateView.getTag() == null) {
                        if (floatingDateAnimation != null) {
                            floatingDateAnimation.cancel();
                        }
                        floatingDateView.setTag(1);
                        floatingDateAnimation = new AnimatorSet();
                        floatingDateAnimation.setDuration(150);
                        floatingDateAnimation.playTogether(ObjectAnimator.ofFloat(floatingDateView, "alpha", 0.75f));
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
        frameLayout.addView(floatingDateView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 8 + topPad, 0, 0));
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
                cell.setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {
                    @Override
                    public boolean isReplyOrSelf() {
                        return ChatMessageCell.ChatMessageCellDelegate.super.isReplyOrSelf();
                    }

                    @Override
                    public void didPressExtendedMediaPreview(ChatMessageCell cell, TLRPC.KeyboardButton button) {
                        ChatMessageCell.ChatMessageCellDelegate.super.didPressExtendedMediaPreview(cell, button);
                    }

                    @Override
                    public void didPressUserStatus(ChatMessageCell cell, TLRPC.User user, TLRPC.Document document, String giftSlug) {
                        ChatMessageCell.ChatMessageCellDelegate.super.didPressUserStatus(cell, user, document, giftSlug);
                    }

                    @Override
                    public void didPressUserAvatar(ChatMessageCell cell, TLRPC.User user, float touchX, float touchY, boolean asForward) {
                        ChatMessageCell.ChatMessageCellDelegate.super.didPressUserAvatar(cell, user, touchX, touchY, asForward);
                    }

                    @Override
                    public boolean didLongPressUserAvatar(ChatMessageCell cell, TLRPC.User user, float touchX, float touchY) {
                        return ChatMessageCell.ChatMessageCellDelegate.super.didLongPressUserAvatar(cell, user, touchX, touchY);
                    }

                    @Override
                    public void didPressHiddenForward(ChatMessageCell cell) {
                        ChatMessageCell.ChatMessageCellDelegate.super.didPressHiddenForward(cell);
                    }

                    @Override
                    public void didPressViaBotNotInline(ChatMessageCell cell, long botId) {
                        ChatMessageCell.ChatMessageCellDelegate.super.didPressViaBotNotInline(cell, botId);
                    }

                    @Override
                    public void didPressViaBot(ChatMessageCell cell, String username) {
                        ChatMessageCell.ChatMessageCellDelegate.super.didPressViaBot(cell, username);
                    }

                    @Override
                    public void didPressBoostCounter(ChatMessageCell cell) {
                        ChatMessageCell.ChatMessageCellDelegate.super.didPressBoostCounter(cell);
                    }

                    @Override
                    public void didPressChannelAvatar(ChatMessageCell cell, TLRPC.Chat chat, int postId, float touchX, float touchY, boolean asForward) {
                        ChatMessageCell.ChatMessageCellDelegate.super.didPressChannelAvatar(cell, chat, postId, touchX, touchY, asForward);
                    }

                    @Override
                    public boolean didLongPressChannelAvatar(ChatMessageCell cell, TLRPC.Chat chat, int postId, float touchX, float touchY) {
                        return ChatMessageCell.ChatMessageCellDelegate.super.didLongPressChannelAvatar(cell, chat, postId, touchX, touchY);
                    }

                    @Override
                    public void didPressCancelSendButton(ChatMessageCell cell) {
                        ChatMessageCell.ChatMessageCellDelegate.super.didPressCancelSendButton(cell);
                    }

                    @Override
                    public void didLongPress(ChatMessageCell cell, float x, float y) {
                        ChatMessageCell.ChatMessageCellDelegate.super.didLongPress(cell, x, y);
                    }

                    @Override
                    public void didPressReplyMessage(ChatMessageCell cell, int id, float x, float y, boolean longpress) {
                        ChatMessageCell.ChatMessageCellDelegate.super.didPressReplyMessage(cell, id, x, y, longpress);
                    }

                    @Override
                    public boolean isProgressLoading(ChatMessageCell cell, int type) {
                        return ChatMessageCell.ChatMessageCellDelegate.super.isProgressLoading(cell, type);
                    }

                    @Override
                    public String getProgressLoadingBotButtonUrl(ChatMessageCell cell) {
                        return ChatMessageCell.ChatMessageCellDelegate.super.getProgressLoadingBotButtonUrl(cell);
                    }

                    @Override
                    public CharacterStyle getProgressLoadingLink(ChatMessageCell cell) {
                        return ChatMessageCell.ChatMessageCellDelegate.super.getProgressLoadingLink(cell);
                    }

                    @Override
                    public void didPressUrl(ChatMessageCell cell, CharacterStyle url, boolean longPress) {
                        ChatMessageCell.ChatMessageCellDelegate.super.didPressUrl(cell, url, longPress);
                    }

                    @Override
                    public void didPressCodeCopy(ChatMessageCell cell, MessageObject.TextLayoutBlock block) {
                        ChatMessageCell.ChatMessageCellDelegate.super.didPressCodeCopy(cell, block);
                    }

                    @Override
                    public void didPressChannelRecommendation(ChatMessageCell cell, TLObject chat, boolean longPress) {
                        ChatMessageCell.ChatMessageCellDelegate.super.didPressChannelRecommendation(cell, chat, longPress);
                    }

                    @Override
                    public void didPressMoreChannelRecommendations(ChatMessageCell cell) {
                        ChatMessageCell.ChatMessageCellDelegate.super.didPressMoreChannelRecommendations(cell);
                    }

                    @Override
                    public void didPressChannelRecommendationsClose(ChatMessageCell cell) {
                        ChatMessageCell.ChatMessageCellDelegate.super.didPressChannelRecommendationsClose(cell);
                    }

                    @Override
                    public void needOpenWebView(MessageObject message, String url, String title, String description, String originalUrl, int w, int h) {
                        ChatMessageCell.ChatMessageCellDelegate.super.needOpenWebView(message, url, title, description, originalUrl, w, h);
                    }

                    @Override
                    public void didPressWebPage(ChatMessageCell cell, TLRPC.WebPage webpage, String url, boolean safe) {
                        ChatMessageCell.ChatMessageCellDelegate.super.didPressWebPage(cell, webpage, url, safe);
                    }

                    @Override
                    public void didPressImage(ChatMessageCell cell, float x, float y, boolean fullPreview) {
                        ChatMessageCell.ChatMessageCellDelegate.super.didPressImage(cell, x, y, fullPreview);
                    }

                    @Override
                    public void didPressGroupImage(ChatMessageCell cell, ImageReceiver imageReceiver, TLRPC.MessageExtendedMedia media, float x, float y) {
                        ChatMessageCell.ChatMessageCellDelegate.super.didPressGroupImage(cell, imageReceiver, media, x, y);
                    }

                    @Override
                    public void didPressSideButton(ChatMessageCell cell) {
                        ChatMessageCell.ChatMessageCellDelegate.super.didPressSideButton(cell);
                    }

                    @Override
                    public void didQuickShareStart(ChatMessageCell cell, float x, float y) {
                        ChatMessageCell.ChatMessageCellDelegate.super.didQuickShareStart(cell, x, y);
                    }

                    @Override
                    public void didQuickShareMove(ChatMessageCell cell, float x, float y) {
                        ChatMessageCell.ChatMessageCellDelegate.super.didQuickShareMove(cell, x, y);
                    }

                    @Override
                    public void didQuickShareEnd(ChatMessageCell cell, float x, float y) {
                        ChatMessageCell.ChatMessageCellDelegate.super.didQuickShareEnd(cell, x, y);
                    }

                    @Override
                    public void didPressOther(ChatMessageCell cell, float otherX, float otherY) {
                        ChatMessageCell.ChatMessageCellDelegate.super.didPressOther(cell, otherX, otherY);
                    }

                    @Override
                    public void didPressSponsoredClose(ChatMessageCell cell) {
                        ChatMessageCell.ChatMessageCellDelegate.super.didPressSponsoredClose(cell);
                    }

                    @Override
                    public void didPressSponsoredInfo(ChatMessageCell cell, float x, float y) {
                        ChatMessageCell.ChatMessageCellDelegate.super.didPressSponsoredInfo(cell, x, y);
                    }

                    @Override
                    public void didPressTime(ChatMessageCell cell) {
                        ChatMessageCell.ChatMessageCellDelegate.super.didPressTime(cell);
                    }

                    @Override
                    public void didPressBotButton(ChatMessageCell cell, TLRPC.KeyboardButton button) {
                        ChatMessageCell.ChatMessageCellDelegate.super.didPressBotButton(cell, button);
                    }

                    @Override
                    public void didLongPressBotButton(ChatMessageCell cell, TLRPC.KeyboardButton button) {
                        ChatMessageCell.ChatMessageCellDelegate.super.didLongPressBotButton(cell, button);
                    }

                    @Override
                    public void didPressCustomBotButton(ChatMessageCell cell, BotInlineKeyboard.ButtonCustom button) {
                        ChatMessageCell.ChatMessageCellDelegate.super.didPressCustomBotButton(cell, button);
                    }

                    @Override
                    public void didLongPressCustomBotButton(ChatMessageCell cell, BotInlineKeyboard.ButtonCustom button) {
                        ChatMessageCell.ChatMessageCellDelegate.super.didLongPressCustomBotButton(cell, button);
                    }

                    @Override
                    public void didPressReaction(ChatMessageCell cell, TLRPC.ReactionCount reaction, boolean longpress, float x, float y) {
                        ChatMessageCell.ChatMessageCellDelegate.super.didPressReaction(cell, reaction, longpress, x, y);
                    }

                    @Override
                    public void didPressVoteButtons(ChatMessageCell cell, ArrayList<TLRPC.PollAnswer> buttons, int showCount, int x, int y) {
                        ChatMessageCell.ChatMessageCellDelegate.super.didPressVoteButtons(cell, buttons, showCount, x, y);
                    }

                    @Override
                    public boolean didPressToDoButton(ChatMessageCell cell, TLRPC.TodoItem task, boolean enable) {
                        return ChatMessageCell.ChatMessageCellDelegate.super.didPressToDoButton(cell, task, enable);
                    }

                    @Override
                    public boolean didLongPressToDoButton(ChatMessageCell cell, TLRPC.TodoItem task) {
                        return ChatMessageCell.ChatMessageCellDelegate.super.didLongPressToDoButton(cell, task);
                    }

                    @Override
                    public void didPressInstantButton(ChatMessageCell cell, int type) {
                        ChatMessageCell.ChatMessageCellDelegate.super.didPressInstantButton(cell, type);
                    }

                    @Override
                    public void didPressGiveawayChatButton(ChatMessageCell cell, int pressedPos) {
                        ChatMessageCell.ChatMessageCellDelegate.super.didPressGiveawayChatButton(cell, pressedPos);
                    }

                    @Override
                    public void didPressCommentButton(ChatMessageCell cell) {
                        ChatMessageCell.ChatMessageCellDelegate.super.didPressCommentButton(cell);
                    }

                    @Override
                    public void didPressHint(ChatMessageCell cell, int type) {
                        ChatMessageCell.ChatMessageCellDelegate.super.didPressHint(cell, type);
                    }

                    @Override
                    public void needShowPremiumFeatures(String source) {
                        ChatMessageCell.ChatMessageCellDelegate.super.needShowPremiumFeatures(source);
                    }

                    @Override
                    public void needShowPremiumBulletin(int type) {
                        ChatMessageCell.ChatMessageCellDelegate.super.needShowPremiumBulletin(type);
                    }

                    @Override
                    public String getAdminRank(long uid) {
                        return ChatMessageCell.ChatMessageCellDelegate.super.getAdminRank(uid);
                    }

                    @Override
                    public boolean needPlayMessage(ChatMessageCell cell, MessageObject messageObject, boolean muted) {
                        return ChatMessageCell.ChatMessageCellDelegate.super.needPlayMessage(cell, messageObject, muted);
                    }

                    @Override
                    public boolean drawingVideoPlayerContainer() {
                        return ChatMessageCell.ChatMessageCellDelegate.super.drawingVideoPlayerContainer();
                    }

                    @Override
                    public boolean canPerformActions() {
                        return ChatMessageCell.ChatMessageCellDelegate.super.canPerformActions();
                    }

                    @Override
                    public boolean canPerformReply() {
                        return ChatMessageCell.ChatMessageCellDelegate.super.canPerformReply();
                    }

                    @Override
                    public boolean onAccessibilityAction(int action, Bundle arguments) {
                        return ChatMessageCell.ChatMessageCellDelegate.super.onAccessibilityAction(action, arguments);
                    }

                    @Override
                    public void videoTimerReached() {
                        ChatMessageCell.ChatMessageCellDelegate.super.videoTimerReached();
                    }

                    @Override
                    public void didStartVideoStream(MessageObject message) {
                        ChatMessageCell.ChatMessageCellDelegate.super.didStartVideoStream(message);
                    }

                    @Override
                    public boolean shouldRepeatSticker(MessageObject message) {
                        return ChatMessageCell.ChatMessageCellDelegate.super.shouldRepeatSticker(message);
                    }

                    @Override
                    public void setShouldNotRepeatSticker(MessageObject message) {
                        ChatMessageCell.ChatMessageCellDelegate.super.setShouldNotRepeatSticker(message);
                    }

                    @Override
                    public TextSelectionHelper.ChatListTextSelectionHelper getTextSelectionHelper() {
                        return ChatMessageCell.ChatMessageCellDelegate.super.getTextSelectionHelper();
                    }

                    @Override
                    public boolean hasSelectedMessages() {
                        return ChatMessageCell.ChatMessageCellDelegate.super.hasSelectedMessages();
                    }

                    @Override
                    public void needReloadPolls() {
                        ChatMessageCell.ChatMessageCellDelegate.super.needReloadPolls();
                    }

                    @Override
                    public void onDiceFinished() {
                        ChatMessageCell.ChatMessageCellDelegate.super.onDiceFinished();
                    }

                    @Override
                    public boolean shouldDrawThreadProgress(ChatMessageCell cell, boolean delayed) {
                        return ChatMessageCell.ChatMessageCellDelegate.super.shouldDrawThreadProgress(cell, delayed);
                    }

                    @Override
                    public PinchToZoomHelper getPinchToZoomHelper() {
                        return ChatMessageCell.ChatMessageCellDelegate.super.getPinchToZoomHelper();
                    }

                    @Override
                    public boolean keyboardIsOpened() {
                        return ChatMessageCell.ChatMessageCellDelegate.super.keyboardIsOpened();
                    }

                    @Override
                    public boolean isLandscape() {
                        return ChatMessageCell.ChatMessageCellDelegate.super.isLandscape();
                    }

                    @Override
                    public void invalidateBlur() {
                        ChatMessageCell.ChatMessageCellDelegate.super.invalidateBlur();
                    }

                    @Override
                    public boolean canDrawOutboundsContent() {
                        return ChatMessageCell.ChatMessageCellDelegate.super.canDrawOutboundsContent();
                    }

                    @Override
                    public boolean didPressAnimatedEmoji(ChatMessageCell cell, AnimatedEmojiSpan span) {
                        return ChatMessageCell.ChatMessageCellDelegate.super.didPressAnimatedEmoji(cell, span);
                    }

                    @Override
                    public void didPressTopicButton(ChatMessageCell cell) {
                        ChatMessageCell.ChatMessageCellDelegate.super.didPressTopicButton(cell);
                    }

                    @Override
                    public boolean shouldShowTopicButton(ChatMessageCell cell) {
                        return ChatMessageCell.ChatMessageCellDelegate.super.shouldShowTopicButton(cell);
                    }

                    @Override
                    public void didPressDialogButton(ChatMessageCell cell) {
                        ChatMessageCell.ChatMessageCellDelegate.super.didPressDialogButton(cell);
                    }

                    @Override
                    public boolean shouldShowDialogButton(ChatMessageCell cell) {
                        return ChatMessageCell.ChatMessageCellDelegate.super.shouldShowDialogButton(cell);
                    }

                    @Override
                    public void didPressEmojiStatus() {
                        ChatMessageCell.ChatMessageCellDelegate.super.didPressEmojiStatus();
                    }

                    @Override
                    public boolean doNotShowLoadingReply(MessageObject msg) {
                        return ChatMessageCell.ChatMessageCellDelegate.super.doNotShowLoadingReply(msg);
                    }

                    @Override
                    public void didPressAboutRevenueSharingAds() {
                        ChatMessageCell.ChatMessageCellDelegate.super.didPressAboutRevenueSharingAds();
                    }

                    @Override
                    public void didPressRevealSensitiveContent(ChatMessageCell cell) {
                        ChatMessageCell.ChatMessageCellDelegate.super.didPressRevealSensitiveContent(cell);
                    }

                    @Override
                    public void didPressEffect(ChatMessageCell cell) {
                        ChatMessageCell.ChatMessageCellDelegate.super.didPressEffect(cell);
                    }

                    @Override
                    public void didPressFactCheckWhat(ChatMessageCell cell, int cx, int cy) {
                        ChatMessageCell.ChatMessageCellDelegate.super.didPressFactCheckWhat(cell, cx, cy);
                    }

                    @Override
                    public void didPressFactCheck(ChatMessageCell cell) {
                        ChatMessageCell.ChatMessageCellDelegate.super.didPressFactCheck(cell);
                    }

                    @Override
                    public void forceUpdate(ChatMessageCell cell, boolean anchorScroll) {
                        ChatMessageCell.ChatMessageCellDelegate.super.forceUpdate(cell, anchorScroll);
                    }
                });
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