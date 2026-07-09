package com.pessdes.bottomfolders;

import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
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

    private static int calculateTargetTop(DialogsActivity activity, View recycler) throws InvocationTargetException, IllegalAccessException {
        var actionBar = activity.getActionBar();
        if (actionBar == null) {
            return recycler.getPaddingTop();
        }

        int top = ActionBar.getCurrentActionBarHeight();
        if (actionBar.getOccupyStatusBar()) {
            top += AndroidUtilities.statusBarHeight;
        }

        boolean hasStories = getBooleanField(activity, "hasStories", false);
        boolean actionModeFullyShowed = getBooleanField(activity, "actionModeFullyShowed", false);

        if (hasStories && !actionModeFullyShowed) {
            top += AndroidUtilities.dp(85);
        }

        if (!actionModeFullyShowed) {
            top += AndroidUtilities.dp(48);
        }

        var topPanel = (View) getPrivateField(activity, "topPanelLayout");
        if (topPanel != null && topPanel.getVisibility() == View.VISIBLE) {
            int dp14 = AndroidUtilities.dp(14);
            Object heightObj = callPrivateMethod(topPanel, "getAnimatedHeightWithPadding", dp14);
            if (heightObj instanceof Number) {
                top += ((Number) heightObj).intValue();
            }

            Object metadata = callPrivateMethod(topPanel, "getMetadata");
            if (metadata == null) {
                metadata = getPrivateField(topPanel, "metadata");
            }
            if (metadata != null) {
                Object visObj = callPrivateMethod(metadata, "getTotalVisibility");
                if (visObj instanceof Number) {
                    float topPanelsVisibility = ((Number) visObj).floatValue();
                    top -= (int) (AndroidUtilities.dp(5) * topPanelsVisibility);
                }
            }
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
                Object pbObj = callPrivateMethod(activity, "calculateListViewPaddingBottom");
                int paddingBottom = pbObj instanceof Number ? ((Number) pbObj).intValue() : 0;
                var barHeight = filterTabsView.getMeasuredHeight();
                var slideOffset = (1.0f - filterTabsView.getAlpha()) * barHeight;
                float translationY = height - paddingBottom - filterTabsView.getTop();
                filterTabsView.setTranslationY(translationY);
            }

            var topPanel = (View) getPrivateField(activity, "topPanelLayout");
            if (topPanel != null) {
                float filtersTabHeight = AndroidUtilities.dp(43) * filterTabsView.getAlpha();
                var animatorSearchVisible = getPrivateField(activity, "animatorSearchVisible");
                float searchVisibleFactor = 0.0f;
                if (animatorSearchVisible != null) {
                    Object factorObj = callPrivateMethod(animatorSearchVisible, "getFloatValue");
                    if (factorObj instanceof Number) {
                        searchVisibleFactor = ((Number) factorObj).floatValue();
                    }
                }

                float correction = filtersTabHeight * (1.0f - searchVisibleFactor);
                topPanel.setTranslationY(topPanel.getTranslationY() - correction);
            }

            var topBubblesFade = getPrivateField(activity, "topBubblesFadeView");
            if (topBubblesFade != null) {
                float topPanelsVisibility = 0.0f;
                if (topPanel != null) {
                    Object metadata = callPrivateMethod(topPanel, "getMetadata");
                    if (metadata == null) {
                        metadata = getPrivateField(topPanel, "metadata");
                    }
                    if (metadata != null) {
                        Object visObj = callPrivateMethod(metadata, "getTotalVisibility");
                        if (visObj instanceof Number) {
                            topPanelsVisibility = ((Number) visObj).floatValue();
                        }
                    }
                }
                float filtersTabVisibility = filterTabsView.getAlpha();

                float dp7 = AndroidUtilities.dp(7);
                float dp50 = AndroidUtilities.dp(50);
                float dp40 = AndroidUtilities.dp(40);

                float s = dp7 + (dp50 - dp7) * Math.min(topPanelsVisibility, filtersTabVisibility);

                float topPanelsHeight = 0.0f;
                if (topPanel != null) {
                    Object heightObj = callPrivateMethod(topPanel, "getAnimatedHeightWithPadding", 0);
                    if (heightObj instanceof Number) {
                        topPanelsHeight = ((Number) heightObj).floatValue();
                    }
                }

                float hParam = Math.min(dp40, topPanelsHeight - s);
                callPrivateMethod(topBubblesFade, "setPosition", s, hParam);
            }
        }
    }

    private static class FabOffsetHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            var filterTabsView = getFilterTabsView(param.thisObject);
            if (filterTabsView == null) return;

            int adjustment = (int) (filterTabsView.getMeasuredHeight() * filterTabsView.getAlpha());
            var fab = (View) getPrivateField(param.thisObject, "floatingButton3");
            if (fab != null) {
                fab.setTranslationY(fab.getTranslationY() - adjustment);
            }
        }
    }

    private static class PaddingBottomHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            var activity = param.thisObject;
            var filterTabsView = getFilterTabsView(activity);
            if (filterTabsView != null) {
                Object result = param.getResult();
                if (result instanceof Number) {
                    int val = ((Number) result).intValue();
                    int barHeight = filterTabsView.getMeasuredHeight();
                    val += (int) (barHeight * filterTabsView.getAlpha());
                    param.setResult(val);
                }
            }
        }
    }

    private static class OnLayoutHook extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            var recycler = (View) param.thisObject;

            var mainTabsActivity = LaunchActivity.findFragment(MainTabsActivity.class);
            if (mainTabsActivity != null) {
                var activity = mainTabsActivity.getDialogsActivity();
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
    }

    public static void unhook() {
        for (var unhook : unhooks) {
            if (unhook != null) unhook.unhook();
        }
        unhooks.clear();
    }
}