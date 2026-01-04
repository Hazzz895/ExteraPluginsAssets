package com.pessdes.chatexporter.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.URLSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.collection.LongSparseArray;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pessdes.chatexporter.tgnet.exported_Chat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatMessageSharedResources;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.AvatarPreviewer;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.ChannelAdminLogActivity;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.ChatAvatarContainer;
import org.telegram.ui.Components.EmbedBottomSheet;
import org.telegram.ui.Components.Forum.ForumUtilities;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerAnimationScrollHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.ShareAlert;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.StickersAlert;
import org.telegram.ui.Components.URLSpanMono;
import org.telegram.ui.Components.URLSpanNoUnderline;
import org.telegram.ui.Components.URLSpanReplacement;
import org.telegram.ui.Components.URLSpanUserMention;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.LocationActivity;
import org.telegram.ui.PhotoViewer;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.ThemePreviewActivity;

import java.io.File;
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
    private ArrayList<MessageObject> messageObjects = new ArrayList<>();
    private ChatMessageCell dummyMessageCell;

    private final LongSparseArray<MessageObject.GroupedMessages> groupedMessagesMap = new LongSparseArray<>();
    private RecyclerAnimationScrollHelper chatScrollHelper;

    public ExportedChatActivity(@NonNull exported_Chat exported) {
        this.exported = exported;
        rowCount = exported.messages.size();
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

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        actionBar.setAllowOverlayTitle(true);
        actionBar.setAddToContainer(false);

        avatarContainer = new ChatAvatarContainer(context, null, false, getResourceProvider());
        avatarContainer.setTitle(name);
        avatarContainer.setSubtitle(LocaleController.formatPluralString("messages", exported.messages.size()));
        if (exported.peer instanceof TLRPC.Chat) avatarContainer.setChatAvatar((TLRPC.Chat) exported.peer);
        else if (exported.peer instanceof TLRPC.User) avatarContainer.setUserAvatar((TLRPC.User) exported.peer);

        actionBar.addView(avatarContainer, 0, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 56, 0, 40, 0));

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
                        if (chatMessageCell.drawPinnedBottom()) {
                            int p;

                            ViewHolder holder = chatListView.getChildViewHolder(child);
                            p = holder.getAdapterPosition();


                            if (p >= 0) {
                                int nextPosition;

                                nextPosition = p + 1;

                                holder = chatListView.findViewHolderForAdapterPosition(nextPosition);
                                if (holder != null) {
                                    imageReceiver.setVisible(false, false);
                                    return result;
                                }
                            }
                        }
                        float tx = chatMessageCell.getSlidingOffsetX() + chatMessageCell.getCheckBoxTranslation();


                        int y = (int) child.getY() + chatMessageCell.getLayoutHeight();
                        int maxY = chatListView.getMeasuredHeight() - chatListView.getPaddingBottom();
                        if (y > maxY) {
                            y = maxY;
                        }

                        if (chatMessageCell.drawPinnedTop()) {
                            int p;

                            ViewHolder holder = chatListView.getChildViewHolder(child);
                            p = holder.getAdapterPosition();

                            if (p >= 0) {
                                int tries = 0;
                                while (true) {
                                    if (tries >= 20) {
                                        break;
                                    }
                                    tries++;

                                    int prevPosition;

                                    prevPosition = p - 1;


                                    holder = chatListView.findViewHolderForAdapterPosition(prevPosition);
                                    if (holder != null) {
                                        top = holder.itemView.getTop();
                                        if (holder.itemView instanceof ChatMessageCell) {
                                            chatMessageCell = (ChatMessageCell) holder.itemView;
                                            if (!chatMessageCell.drawPinnedTop()) {
                                                break;
                                            } else {
                                                p = prevPosition;
                                            }
                                        } else {
                                            break;
                                        }
                                    } else {
                                        break;
                                    }
                                }
                            }
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
        chatScrollHelper = new RecyclerAnimationScrollHelper(chatListView, manager);
        chatListView.setLayoutManager(manager = new LinearLayoutManager(context) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return true;
            }
        });

        chatListView.setOnScrollListener(new RecyclerView.OnScrollListener() {

            private float totalDy = 0;
            private final int scrollValue = dp(100);

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
                    }
                }
            }
        });

        floatingDateView = new ChatActionCell(context);
        floatingDateView.setAlpha(0.0f);
        floatingDateView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        frameLayout.addView(floatingDateView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 4, 0, 0));

        manager.setStackFromEnd(true);
        chatListView.setVerticalScrollBarEnabled(true);
        frameLayout.addView(chatListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        chatListView.setAdapter(adapter = new ExportedChatActivity.ListAdapter(context));

        return fragmentView;
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
                    public TextSelectionHelper.ChatListTextSelectionHelper getTextSelectionHelper() {
                        return ChatMessageCell.ChatMessageCellDelegate.super.getTextSelectionHelper();
                    }

                    @Override
                    public boolean shouldShowTopicButton(ChatMessageCell cell) {
                        MessageObject message = cell.getMessageObject();
                        if (message == null || message.currentEvent == null || !(
                                message.currentEvent.action instanceof TLRPC.TL_channelAdminLogEventActionEditMessage ||
                                        message.currentEvent.action instanceof TLRPC.TL_channelAdminLogEventActionDeleteMessage
                        )) {
                            return false;
                        }
                        return !(exported.peer instanceof TLRPC.Chat) || ChatObject.isForum((TLRPC.Chat)exported.peer);
                    }

                    @Override
                    public void didPressTopicButton(ChatMessageCell cell) {
                        MessageObject message = cell.getMessageObject();
                        if (message != null) {
                            Bundle args = new Bundle();
                            args.putLong("chat_id", -message.getDialogId());
                            ChatActivity chatActivity = new ChatActivity(args);
                            ForumUtilities.applyTopic(chatActivity, MessagesStorage.TopicKey.of(message.getDialogId(), MessageObject.getTopicId(currentAccount, message.messageOwner, true)));
                            presentFragment(chatActivity);
                        }
                    }

                    @Override
                    public boolean canDrawOutboundsContent() {
                        return true;
                    }

                    @Override
                    public void didPressSideButton(ChatMessageCell cell) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        showDialog(ShareAlert.createShareAlert(context, cell.getMessageObject(), null, !(exported.peer instanceof TLRPC.Chat) || (ChatObject.isChannel((TLRPC.Chat)exported.peer) && !((TLRPC.Chat)exported.peer).megagroup), null, false));
                    }

                    @Override
                    public boolean needPlayMessage(ChatMessageCell cell, MessageObject messageObject, boolean muted) {
                        if (messageObject.isVoice() || messageObject.isRoundVideo()) {
                            boolean result = MediaController.getInstance().playMessage(messageObject, muted);
                            MediaController.getInstance().setVoiceMessagesPlaylist(null, false);
                            return result;
                        } else if (messageObject.isMusic()) {
                            return MediaController.getInstance().setPlaylist(messageObjects, messageObject, 0);
                        }
                        return false;
                    }

                    @Override
                    public void didPressChannelAvatar(ChatMessageCell cell, TLRPC.Chat chat, int postId, float touchX, float touchY, boolean asForward) {
                        if (chat != null && chat != exported.peer) {
                            Bundle args = new Bundle();
                            args.putLong("chat_id", chat.id);
                            if (postId != 0) {
                                args.putInt("message_id", postId);
                            }
                            if (MessagesController.getInstance(currentAccount).checkCanOpenChat(args, ExportedChatActivity.this)) {
                                presentFragment(new ChatActivity(args), true);
                            }
                        }
                    }

                    @Override
                    public void didPressUserAvatar(ChatMessageCell cell, TLRPC.User user, float touchX, float touchY, boolean asForward) {
                        if (user != null && user.id != UserConfig.getInstance(currentAccount).getClientUserId()) {
                            openProfile(user);
                        }
                    }

                    @Override
                    public boolean didLongPressUserAvatar(ChatMessageCell cell, TLRPC.User user, float touchX, float touchY) {
                        if (user != null && user.id != UserConfig.getInstance(currentAccount).getClientUserId()) {
                            final AvatarPreviewer.MenuItem[] menuItems = {AvatarPreviewer.MenuItem.OPEN_PROFILE, AvatarPreviewer.MenuItem.SEND_MESSAGE};
                            final TLRPC.UserFull userFull = getMessagesController().getUserFull(user.id);
                            final AvatarPreviewer.Data data;
                            if (userFull != null) {
                                data = AvatarPreviewer.Data.of(user, userFull, menuItems);
                            } else {
                                data = AvatarPreviewer.Data.of(user, classGuid, menuItems);
                            }
                            if (AvatarPreviewer.canPreview(data)) {
                                AvatarPreviewer.getInstance().show((ViewGroup) fragmentView, getResourceProvider(), data, item -> {
                                    switch (item) {
                                        case SEND_MESSAGE:
                                            openDialog(cell, user);
                                            break;
                                        case OPEN_PROFILE:
                                            openProfile(user);
                                            break;
                                    }
                                });
                                return true;
                            }
                        }
                        return false;
                    }

                    private void openProfile(TLRPC.User user) {
                        Bundle args = new Bundle();
                        args.putLong("user_id", user.id);
                        ProfileActivity fragment = new ProfileActivity(args);
                        fragment.setPlayProfileAnimation(0);
                        presentFragment(fragment);
                    }

                    private void openDialog(ChatMessageCell cell, TLRPC.User user) {
                        if (user != null) {
                            Bundle args = new Bundle();
                            args.putLong("user_id", user.id);
                            if (getMessagesController().checkCanOpenChat(args, ExportedChatActivity.this)) {
                                presentFragment(new ChatActivity(args));
                            }
                        }
                    }

                    @Override
                    public void didPressCancelSendButton(ChatMessageCell cell) {

                    }


                    @Override
                    public boolean canPerformActions() {
                        return true;
                    }

                    @Override
                    public void didPressUrl(ChatMessageCell cell, final CharacterStyle url, boolean longPress) {
                        if (url == null) {
                            return;
                        }
                        MessageObject messageObject = cell.getMessageObject();
                        if (url instanceof URLSpanMono) {
                            ((URLSpanMono) url).copyToClipboard();
                            if (AndroidUtilities.shouldShowClipboardToast()) {
                                Toast.makeText(getParentActivity(), getString("TextCopied", R.string.TextCopied), Toast.LENGTH_SHORT).show();
                            }
                        } else if (url instanceof URLSpanUserMention) {
                            long peerId = Utilities.parseLong(((URLSpanUserMention) url).getURL());
                            if (peerId > 0) {
                                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(peerId);
                                if (user != null) {
                                    MessagesController.getInstance(currentAccount).openChatOrProfileWith(user, null, ExportedChatActivity.this, 0, false);
                                }
                            } else {
                                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-peerId);
                                if (chat != null) {
                                    MessagesController.getInstance(currentAccount).openChatOrProfileWith(null, chat, ExportedChatActivity.this, 0, false);
                                }
                            }
                        } else if (url instanceof URLSpanNoUnderline) {
                            String str = ((URLSpanNoUnderline) url).getURL();
                            if (str.startsWith("@")) {
                                MessagesController.getInstance(currentAccount).openByUserName(str.substring(1), ExportedChatActivity.this, 0);
                            } else if (str.startsWith("#")) {
                                DialogsActivity fragment = new DialogsActivity(null);
                                fragment.setSearchString(str);
                                presentFragment(fragment);
                            }
                        } else {
                            final String urlFinal = ((URLSpan) url).getURL();
                            if (longPress) {
                                BottomSheet.Builder builder = new BottomSheet.Builder(getParentActivity());
                                builder.setTitle(urlFinal);
                                builder.setItems(new CharSequence[]{getString("Open", R.string.Open), getString("Copy", R.string.Copy)}, (dialog, which) -> {
                                    if (which == 0) {
                                        Browser.openUrl(getParentActivity(), urlFinal, true);
                                    } else if (which == 1) {
                                        String url1 = urlFinal;
                                        if (url1.startsWith("mailto:")) {
                                            url1 = url1.substring(7);
                                        } else if (url1.startsWith("tel:")) {
                                            url1 = url1.substring(4);
                                        }
                                        AndroidUtilities.addToClipboard(url1);
                                    }
                                });
                                showDialog(builder.create());
                            } else {
                                if (url instanceof URLSpanReplacement) {
                                    AlertsCreator.showOpenUrlAlert(ExportedChatActivity.this, ((URLSpanReplacement) url).getURL(), false, true);
                                } else {
                                    if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && messageObject.messageOwner.media.webpage != null && messageObject.messageOwner.media.webpage.cached_page != null) {
                                        String lowerUrl = urlFinal.toLowerCase();
                                        String lowerUrl2 = messageObject.messageOwner.media.webpage.url.toLowerCase();
                                        if ((Browser.isTelegraphUrl(lowerUrl, false) || lowerUrl.contains("t.me/iv")) && (lowerUrl.contains(lowerUrl2) || lowerUrl2.contains(lowerUrl))) {
                                            if (LaunchActivity.instance != null && LaunchActivity.instance.getBottomSheetTabs() != null && LaunchActivity.instance.getBottomSheetTabs().tryReopenTab(messageObject) != null) {
                                                return;
                                            }
                                            ExportedChatActivity.this.createArticleViewer(false).open(messageObject);
                                            return;
                                        }
                                    }
                                    Browser.openUrl(getParentActivity(), urlFinal, true);
                                }
                            }
                        }
                    }

                    @Override
                    public void needOpenWebView(MessageObject message, String url, String title, String description, String originalUrl, int w, int h) {
                        EmbedBottomSheet.show(ExportedChatActivity.this, message, provider, title, description, originalUrl, url, w, h, false);
                    }

                    @Override
                    public void didPressReplyMessage(ChatMessageCell cell, int id, float x, float y, boolean longpress) {
                        MessageObject messageObject = cell.getMessageObject();
                        MessageObject reply = messageObject.replyMessageObject;
                        if (reply.getDialogId() == -getChatId()) {
                            for (int i = 0; i < messageObjects.size(); ++i) {
                                MessageObject msg = messageObjects.get(i);
                                if (msg != null && msg.contentType != 1 && msg.getRealId() == reply.getRealId()) {
                                    scrollToMessage(msg);
                                    return;
                                }
                            }
                        }
                        Bundle args = new Bundle();
                        args.putLong("chat_id", getChatId());
                        args.putInt("message_id", reply.getRealId());
                        presentFragment(new ChatActivity(args));
                    }

                    @Override
                    public void didPressViaBot(ChatMessageCell cell, String username) {

                    }

                    @Override
                    public void didPressImage(ChatMessageCell cell, float x, float y, boolean fullPreview) {
                        MessageObject message = cell.getMessageObject();
                        if (message.getInputStickerSet() != null) {
                            showDialog(new StickersAlert(getParentActivity(), ExportedChatActivity.this, message.getInputStickerSet(), null, null, false));
                        } else if (message.isVideo() || message.type == MessageObject.TYPE_PHOTO || message.type == MessageObject.TYPE_TEXT && !message.isWebpageDocument() || message.isGif()) {
                            PhotoViewer.getInstance().setParentActivity(ExportedChatActivity.this);
                            PhotoViewer.getInstance().openPhoto(message, null, 0, 0, 0, provider);
                        } else if (message.type == MessageObject.TYPE_VIDEO) {
                            try {
                                File f = null;
                                if (message.messageOwner.attachPath != null && message.messageOwner.attachPath.length() != 0) {
                                    f = new File(message.messageOwner.attachPath);
                                }
                                if (f == null || !f.exists()) {
                                    f = getFileLoader().getPathToMessage(message.messageOwner);
                                }
                                Intent intent = new Intent(Intent.ACTION_VIEW);
                                if (Build.VERSION.SDK_INT >= 24) {
                                    intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                    intent.setDataAndType(FileProvider.getUriForFile(getParentActivity(), ApplicationLoader.getApplicationId() + ".provider", f), "video/mp4");
                                } else {
                                    intent.setDataAndType(Uri.fromFile(f), "video/mp4");
                                }
                                getParentActivity().startActivityForResult(intent, 500);
                            } catch (Exception ignored) {}
                        } else if (message.type == MessageObject.TYPE_GEO) {
                            if (!AndroidUtilities.isMapsInstalled(ExportedChatActivity.this)) {
                                return;
                            }
                            LocationActivity fragment = new LocationActivity(0);
                            fragment.setMessageObject(message);
                            presentFragment(fragment);
                        } else if (message.type == MessageObject.TYPE_FILE || message.type == MessageObject.TYPE_TEXT) {
                            try {
                                AndroidUtilities.openForView(message, getParentActivity(), null, false);
                            } catch (Exception ignored) {}
                        }
                    }
                });
                view = cell;
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
            var messageObject = new MessageObject(getCurrentAccount(), message, false, true);
            messageObjects.add(messageObject);
            return messageObject;
        }

    }

    public void scrollToMessage(MessageObject object) {
        int scrollDirection = RecyclerAnimationScrollHelper.SCROLL_DIRECTION_UNSET;
        int scrollFromIndex = 0;
        if (!exported.messages.isEmpty()) {
            int end = manager.findLastVisibleItemPosition();
            for (int i = manager.findFirstVisibleItemPosition(); i <= end; i++) {
                MessageObject messageObject = messageObjects.get(i);
                if (messageObject.contentType == 1 || messageObject.getRealId() == 0 || messageObject.isSponsored()) {
                    continue;
                }
                scrollFromIndex = i;
                boolean scrollDown = messageObject.getRealId() < object.getRealId();
                scrollDirection = scrollDown ? RecyclerAnimationScrollHelper.SCROLL_DIRECTION_DOWN : RecyclerAnimationScrollHelper.SCROLL_DIRECTION_UP;
                break;
            }
        }

        chatScrollHelper.setScrollDirection(scrollDirection);
        if (object != null) {
            int index = messageObjects.indexOf(object);
            if (index != -1) {
                if (scrollFromIndex > 0) {
                    scrollDirection = scrollFromIndex > index ? RecyclerAnimationScrollHelper.SCROLL_DIRECTION_DOWN : RecyclerAnimationScrollHelper.SCROLL_DIRECTION_UP;
                    chatScrollHelper.setScrollDirection(scrollDirection);
                }

                boolean found = false;
                int offsetY = 0;
                int count = chatListView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View view = chatListView.getChildAt(a);
                    if (view instanceof ChatMessageCell) {
                        ChatMessageCell cell = (ChatMessageCell) view;
                        MessageObject messageObject = cell.getMessageObject();
                        if (messageObject != null && messageObject.getRealId() == object.getRealId()) {
                            found = true;
                            view.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
                        }
                    } else if (view instanceof ChatActionCell) {
                        ChatActionCell cell = (ChatActionCell) view;
                        MessageObject messageObject = cell.getMessageObject();
                        if (messageObject != null && messageObject.getRealId() == object.getRealId()) {
                            found = true;
                            view.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
                        }
                    }

                    if (found) {
                        int yOffset = getScrollOffsetForMessage(view.getHeight()) - offsetY;
                        int scrollY = view.getTop() - yOffset;
                        int maxScrollOffset = chatListView.computeVerticalScrollRange() - chatListView.computeVerticalScrollOffset() - chatListView.computeVerticalScrollExtent();
                        if (maxScrollOffset < 0) {
                            maxScrollOffset = 0;
                        }
                        if (scrollY > maxScrollOffset) {
                            scrollY = maxScrollOffset;
                        }
                        if (scrollY != 0) {
                            chatListView.smoothScrollBy(0, scrollY);
                            chatListView.setOverScrollMode(RecyclerListView.OVER_SCROLL_NEVER);
                        }
                        break;
                    }
                }
                if (!found) {
                    int yOffset = getScrollOffsetForMessage(object);
                    chatScrollHelper.setScrollDirection(scrollDirection);
                    chatScrollHelper.scrollToPosition(index, yOffset, false, true);
                }
            }
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
        return (int) Math.max(-dp(2), (chatListView.getMeasuredHeight() - /*blurredViewBottomOffset - chatListViewPaddingTop - */messageHeight) / 2);
    }
}