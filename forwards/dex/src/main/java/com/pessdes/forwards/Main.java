package com.pessdes.forwards;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import org.telegram.tgnet.TLRPC;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.spoilers.SpoilerEffect;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

public class Main {
    public static final String DrawableKey = "mini_forwarded";
    public static Drawable ForwardsDrawable = null;

    private static XC_MethodHook.Unhook measureHook = null;
    private static XC_MethodHook.Unhook drawHook = null;
    private static Utilities.Callback<Object[]> logger = null;

    private static void log(Object... messages) {
        if (logger == null) return;
        logger.run(messages);
    }

    public static void main() throws NoSuchMethodException {
        main(null);
    }

    public static void main(Utilities.Callback<Object[]> logger) throws NoSuchMethodException {
        Main.logger = logger;
        if (measureHook != null || drawHook != null) return;
        log("Initializing: Hooking methods...");

        if (ForwardsDrawable == null) {
            var context = ApplicationLoader.applicationContext;
            int resId = context.getResources().getIdentifier(DrawableKey, "drawable", context.getPackageName());
            if (resId != 0) {
                ForwardsDrawable = context.getResources().getDrawable(resId).mutate();
            }
        }

        measureHook = XposedBridge.hookMethod(ChatMessageCell.class.getDeclaredMethod("measureTime", MessageObject.class), new OnMeasure());
        drawHook = XposedBridge.hookMethod(ChatMessageCell.class.getDeclaredMethod(
                "drawViewsAndRepliesLayout",
                Canvas.class, float.class, float.class, float.class, float.class, float.class, boolean.class
        ), new OnDraw());
        log("Initialized!");
    }

    public static void unhook() {
        if (measureHook != null) measureHook.unhook();
        if (drawHook != null) drawHook.unhook();
    }

    private static Object getPrivateField(Object obj, String fieldName) {
        if (obj == null) return null;
        Class<?> clazz = obj instanceof Class ? (Class<?>) obj : obj.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(obj instanceof Class ? null : obj);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private static boolean getBooleanField(Object obj, String fieldName, boolean def) {
        Object val = getPrivateField(obj, fieldName);
        return val instanceof Boolean ? (Boolean) val : def;
    }

    private static int getIntField(Object obj, String fieldName, int def) {
        Object val = getPrivateField(obj, fieldName);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        return def;
    }

    private static class MeasureData {
        public String forwardsString;
        public int forwardsTextWidth;
        public float drawableWidth;

        public MeasureData(String forwardsString, int forwardsTextWidth, float drawableWidth) {
            this.forwardsString = forwardsString;
            this.forwardsTextWidth = forwardsTextWidth;
            this.drawableWidth = drawableWidth;
        }

        public static MeasureData create(int forwards) {
            var f = String.format("%s", LocaleController.formatShortNumber(Math.max(1, forwards), null));
            var s = (int) Math.ceil(Theme.chat_timePaint.measureText(f));
            var t = Main.ForwardsDrawable.getIntrinsicWidth() * (Theme.chat_timePaint.getTextSize() - dp(2)) / Main.ForwardsDrawable.getIntrinsicHeight();
            return new MeasureData(f, s, t);
        }
    }

    private static class OnMeasure extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            try {
                var cell = ((ChatMessageCell) param.thisObject);
                var mobj = ((MessageObject) param.args[0]);
                if (mobj == null || mobj.messageOwner == null) return;

                int forwards = mobj.messageOwner.forwards;
                if (forwards > 0) {
                    var data = MeasureData.create(forwards);
                    cell.timeWidth += (int) (data.forwardsTextWidth + data.drawableWidth + dp(10));
                }
            } catch (Throwable t) {
                log("Measure", t);
            }
        }
    }

    private static class OnDraw extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            super.afterHookedMethod(param);
            try {
                ChatMessageCell cell = (ChatMessageCell) param.thisObject;
                MessageObject mobj = (MessageObject) getPrivateField(cell, "currentMessageObject");
                if (mobj == null || mobj.messageOwner == null) return;

                int forwards = mobj.messageOwner.forwards;
                if (forwards <= 0) return;

                MeasureData data = MeasureData.create(forwards);

                StaticLayout forwardLayout = new StaticLayout(
                        data.forwardsString,
                        Theme.chat_timePaint,
                        data.forwardsTextWidth,
                        Layout.Alignment.ALIGN_NORMAL,
                        1.0f,
                        0.0f,
                        false
                );

                Canvas canvas = (Canvas) param.args[0];
                float alpha = (Float) param.args[2];
                float timeYOffset = (Float) param.args[3];
                float timeX = (Float) param.args[4];

                var transitionParams = cell.transitionParams;

                var currentChat = cell.getCurrentChat();

                boolean isChannel = currentChat != null && currentChat instanceof TLRPC.TL_channel && !currentChat.megagroup;
                boolean hasViews = (mobj.messageOwner.flags & 1024) != 0;
                boolean animateReplies = getBooleanField(transitionParams, "animateReplies", false);
                boolean hasReplies = getPrivateField(cell, "repliesLayout") != null || animateReplies;
                boolean animatePinned = getBooleanField(transitionParams, "animatePinned", false);
                boolean isPinned = cell.isPinned || animatePinned;

                boolean timeTextIsRightAligned = isChannel || hasViews || hasReplies || isPinned;

                float forwardX;
                if (timeTextIsRightAligned) {
                    float offsetX = 0;

                    if (cell.reactionsLayoutInBubble.isSmall) {
                        offsetX += cell.reactionsLayoutInBubble.getCurrentWidth(1.0f);
                    }

                    if (hasReplies) {
                        int repliesTextWidth = getIntField(cell, "repliesTextWidth", 0);
                        Drawable drawable = Theme.chat_msgInRepliesDrawable;
                        float drawableWidth = drawable.getIntrinsicWidth() * (Theme.chat_timePaint.getTextSize() - dp(2)) / drawable.getIntrinsicHeight();
                        offsetX += drawableWidth + repliesTextWidth + dp(10);
                    }

                    boolean hasViewsLayout = getPrivateField(cell, "viewsLayout") != null;
                    boolean animateViewsLayout = getPrivateField(transitionParams, "animateViewsLayout") != null;
                    if (hasViewsLayout || animateViewsLayout) {
                        int viewsTextWidth = getIntField(cell, "viewsTextWidth", 0);
                        Drawable drawable = Theme.chat_msgInViewsDrawable;
                        float drawableWidth = drawable.getIntrinsicWidth() * (Theme.chat_timePaint.getTextSize() - dp(2)) / drawable.getIntrinsicHeight();
                        offsetX += drawableWidth + viewsTextWidth + dp(10);
                    }

                    if (isPinned) {
                        offsetX += Theme.chat_msgInPinnedDrawable.getIntrinsicWidth() + dp(3);
                    }
                    forwardX = timeX + offsetX;
                } else {
                    int timeTextWidth = getIntField(cell, "timeTextWidth", 0);
                    forwardX = timeX + timeTextWidth + dp(4);
                }

                var currentMessagesGroup = cell.getCurrentMessagesGroup();
                if (currentMessagesGroup != null) {
                    if (currentMessagesGroup.transitionParams.backgroundChangeBounds) {
                        forwardX += currentMessagesGroup.transitionParams.offsetRight;
                    }
                }

                if (transitionParams.animateBackgroundBoundsInner) {
                    forwardX += cell.getAnimationOffsetX();
                }

                float y = cell.getTimeY(timeYOffset);

                Main.ForwardsDrawable.setColorFilter(new PorterDuffColorFilter(Theme.chat_timePaint.getColor(), PorterDuff.Mode.SRC_IN));

                float w;
                try {
                    Method setDrawableBounds = cell.getClass().getDeclaredMethod("setDrawableBounds", Drawable.class, float.class, float.class, float.class);
                    setDrawableBounds.setAccessible(true);
                    Object res = setDrawableBounds.invoke(cell, Main.ForwardsDrawable, forwardX, y + dp(1.5f), Theme.chat_timePaint.getTextSize() - dp(2));
                    w = res instanceof Number ? ((Number) res).floatValue() : 0;
                } catch (Throwable ignored) {
                    w = Main.ForwardsDrawable.getIntrinsicWidth() * (Theme.chat_timePaint.getTextSize() - dp(2)) / Main.ForwardsDrawable.getIntrinsicHeight();
                    Main.ForwardsDrawable.setBounds(
                            (int) forwardX,
                            (int) (y + dp(1.5f)),
                            (int) (forwardX + w),
                            (int) (y + dp(1.5f) + Theme.chat_timePaint.getTextSize() - dp(2))
                    );
                }

                Main.ForwardsDrawable.setAlpha((int) (255 * alpha));
                Main.ForwardsDrawable.draw(canvas);
                Main.ForwardsDrawable.setAlpha(255);

                canvas.save();
                canvas.translate(forwardX + w + dp(3), y);

                SpoilerEffect.layoutDrawMaybe(forwardLayout, canvas);

                canvas.restore();

            } catch (Throwable t) {
                log("Draw", t);
            }
        }
    }
}