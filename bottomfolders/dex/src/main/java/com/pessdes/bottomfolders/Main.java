package com.pessdes.bottomfolders;

import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.Components.DialogsActivityTopBubblesFadeView;
import org.telegram.ui.Components.DialogsActivityTopPanelLayout;
import org.telegram.ui.Components.FilterTabsView;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.MainTabsActivity;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import me.vkryl.android.animator.BoolAnimator;

public class Main {
    private static Utilities.Callback<Object[]> logger = null;

    private static final ArrayList<XC_MethodHook.Unhook> unhooks = new ArrayList<>();

    private static void log(Object... messages) {
        if (logger == null) return;
        logger.run(messages);
    }

    public static void main() throws NoSuchMethodException {
        main(null);
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

    private static void Hook(Class<?> clazz, String methodName, XC_MethodHook hook) {
        unhooks.addAll(XposedBridge.hookAllMethods(clazz, methodName, hook));
    }

    public static void main(Utilities.Callback<Object[]> logger) throws NoSuchMethodException {
        Main.logger = logger;
        if (!unhooks.isEmpty()) return;
        log("Initializing: Hooking methods...");

        try {
            Hook(DialogsActivity.class, "updateContextViewPosition", new DialogsActivityCalculationsHook());
            Hook(DialogsActivity.class, "updateFloatingButtonOffset", new FabOffsetHook());
            Hook(DialogsActivity.class, "calculateListViewPaddingBottom", new PaddingBottomHook());
            Hook(DialogsActivity.DialogsRecyclerView.class, "onLayout", new OnLayoutHook());
        } catch (Throwable t) {
            log("Failed to hook methods", t);
        }
        log("Initialized!");
    }

    private static FilterTabsView getFilterTabsView(Object obj) {
        return (FilterTabsView) getPrivateField(obj, "filterTabsView");
    }

    private static Object callPrivateMethod(Object obj, String methodName, Object... args) throws InvocationTargetException, IllegalAccessException {
        if (obj == null) return null;
        Class<?> clazz = obj.getClass();
        while (clazz != null) {
            for (Method m : clazz.getDeclaredMethods()) {
                if (m.getName().equals(methodName) && m.getParameterCount() == args.length) {
                    m.setAccessible(true);
                    return m.invoke(obj, args);
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    private static DialogsActivity findDialogsActivity(View recycler) {
        try {
            var mainTabsActivity = LaunchActivity.findFragment(MainTabsActivity.class);
            if (mainTabsActivity != null) {
                return mainTabsActivity.getDialogsActivity();
            }
        } catch (Throwable ignored) {}

        if (recycler != null) {
            Object parentPage = getPrivateField(recycler, "parentPage");
            if (parentPage != null) {
                Object outer = getPrivateField(parentPage, "this$0");
                if (outer instanceof DialogsActivity) {
                    return (DialogsActivity) outer;
                }
            }

            Object outerDirect = getPrivateField(recycler, "this$0");
            if (outerDirect instanceof DialogsActivity) {
                return (DialogsActivity) outerDirect;
            }
        }

        return LaunchActivity.findFragment(DialogsActivity.class);
    }

    private static int calculateTargetTop(DialogsActivity activity, View recycler) throws InvocationTargetException, IllegalAccessException {
        var actionBar = activity.getActionBar();
        if (actionBar == null) {
            return recycler.getPaddingTop();
        }

        int top = ActionBar.getCurrentActionBarHeight();
        if (actionBar.getOccupyStatusBar()) {
            top += AndroidUtilities.statusBarHeight;
        }

        boolean actionModeFullyShowed = getBooleanField(activity, "actionModeFullyShowed", false);

        if (activity.hasStories && !actionModeFullyShowed) {
            top += AndroidUtilities.dp(85);
        }

        if (!actionModeFullyShowed) {
            top += AndroidUtilities.dp(48);
        }

        var topPanel = (DialogsActivityTopPanelLayout) getPrivateField(activity, "topPanelLayout");
        if (topPanel != null && topPanel.getVisibility() == View.VISIBLE) {
            top += (int) (topPanel.getAnimatedHeightWithPadding(AndroidUtilities.dp(14)) - (AndroidUtilities.dp(5) * topPanel.getMetadata().getTotalVisibility()));
        }
        return top;
    }

    private static class DialogsActivityCalculationsHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            var activity = (DialogsActivity) param.thisObject;
            var filterTabsView = getFilterTabsView(activity);
            if (filterTabsView == null) return;

            var p = filterTabsView.getParent();
            if (p != null) {
                var parent = (View) p;
                var height = parent.getMeasuredHeight();

                int paddingBottom = (int) callPrivateMethod(activity, "calculateListViewPaddingBottom");

                float translationY = height - paddingBottom - filterTabsView.getTop();
                filterTabsView.setTranslationY(translationY);
            }

            var topPanel = (DialogsActivityTopPanelLayout) getPrivateField(activity, "topPanelLayout");
            if (topPanel != null) {
                float filtersTabHeight = AndroidUtilities.dp(43) * filterTabsView.getAlpha();
                var animatorSearchVisible = (BoolAnimator) getPrivateField(activity, "animatorSearchVisible");
                float correction = filtersTabHeight * (1.0f - animatorSearchVisible.getFloatValue());
                topPanel.setTranslationY(topPanel.getTranslationY() - correction);
            }

            var topBubblesFade = (DialogsActivityTopBubblesFadeView) getPrivateField(activity, "topBubblesFadeView");
            if (topBubblesFade != null) {
                float topPanelsVisibility = 0.0f;
                if (topPanel != null) {
                    topPanelsVisibility = topPanel.getMetadata().getTotalVisibility();
                }
                float filtersTabVisibility = filterTabsView.getAlpha();

                float s = AndroidUtilities.dp(7) + (AndroidUtilities.dp(50) - AndroidUtilities.dp(7)) * Math.min(topPanelsVisibility, filtersTabVisibility);

                float topPanelsHeight = topPanel == null ? 0 : topPanel.getAnimatedHeightWithPadding(0);

                float hParam = Math.min(AndroidUtilities.dp(40), topPanelsHeight - s);
                topBubblesFade.setPosition(s, hParam);
            }
        }
    }

    private static class FabOffsetHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            var filterTabsView = getFilterTabsView(param.thisObject);
            if (filterTabsView == null) return;

            var fab = (View) getPrivateField(param.thisObject, "floatingButton3");
            if (fab != null) {
                fab.setTranslationY(fab.getTranslationY() - filterTabsView.getMeasuredHeight() * filterTabsView.getAlpha());
            }
        }
    }

    private static class PaddingBottomHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            var activity = param.thisObject;
            var filterTabsView = getFilterTabsView(activity);
            if (filterTabsView != null) {
                var result = (int) param.getResult();
                result += (int) (filterTabsView.getMeasuredHeight() * filterTabsView.getAlpha());
                param.setResult(result);
            }
        }
    }

    private static class OnLayoutHook extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            var recycler = (View) param.thisObject;

            DialogsActivity activity = findDialogsActivity(recycler);
            if (activity != null) {
                var foldersBar = getFilterTabsView(activity);
                if (foldersBar != null && foldersBar.getVisibility() == View.VISIBLE) {
                    try {
                        int targetTop = calculateTargetTop(activity, recycler);
                        recycler.setPadding(0, targetTop, 0, recycler.getPaddingBottom());
                        try {
                            callPrivateMethod(recycler, "setTopGlowOffset", targetTop);
                        } catch (Exception ignored) {}
                    } catch (Exception e) {
                        log("Error adjusting layout top padding", e);
                    }
                }
            }
        }
    }

    public static void unhook() {
        for (var unhook : unhooks) {
            if (unhook != null) unhook.unhook();
        }
        unhooks.clear();
    }
}