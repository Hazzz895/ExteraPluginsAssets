package com.pessdes.bottomfolders;

import android.graphics.RectF;
import android.view.View;

import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
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
import java.util.HashMap;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import me.vkryl.android.animator.BoolAnimator;

public class Main {
    private static Utilities.Callback<Object[]> logger = null;

    private static final ArrayList<XC_MethodHook.Unhook> unhooks = new ArrayList<>();

    private static final Map<String, Field> fieldCache = new HashMap<>();
    private static final Map<String, Method> methodCache = new HashMap<>();

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
        String key = clazz.getName() + "#" + fieldName;
        Field field = fieldCache.get(key);
        if (field == null) {
            Class<?> current = clazz;
            while (current != null) {
                try {
                    field = current.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    fieldCache.put(key, field);
                    break;
                } catch (NoSuchFieldException e) {
                    current = current.getSuperclass();
                } catch (Exception e) {
                    return null;
                }
            }
        }
        if (field != null) {
            try {
                return field.get(obj instanceof Class ? null : obj);
            } catch (Exception ignored) {}
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
            Hook(DialogsActivity.class, "blur3_InvalidateBlur", new InvalidateBlurHook());
        } catch (Throwable t) {
            log("Failed to hook methods", t);
        }
        log("Initialized!");
    }

    private static FilterTabsView getFilterTabsView(Object obj) {
        return (FilterTabsView) getPrivateField(obj, "filterTabsView");
    }

    static Class<?> MainTabsUiHelper = null;
    static Class<?> BottomNavigationBar = null;

    static {
        try {
            MainTabsUiHelper = Class.forName("com.exteragram.messenger.utils.ui.MainTabsUiHelper");
        } catch (Throwable ignored) {}
        try {
            BottomNavigationBar = Class.forName("com.exteragram.messenger.config.BottomNavigationBar");
        } catch (Throwable ignored) {}
    }
    private static boolean usesFloatingNavbar() {
        try {
            return (Boolean) callPrivateMethod(BottomNavigationBar, "floating");
        }
        catch (Throwable ignored) {}
        return false;
    }

    private static Object callPrivateMethod(Object obj, String methodName, Object... args) throws InvocationTargetException, IllegalAccessException {
        if (obj == null) return null;
        Class<?> clazz = obj instanceof Class ? (Class<?>) obj : obj.getClass();
        String key = clazz.getName() + "#" + methodName + "#" + args.length;
        Method method = methodCache.get(key);
        if (method == null) {
            Class<?> current = clazz;
            while (current != null) {
                for (Method m : current.getDeclaredMethods()) {
                    if (m.getName().equals(methodName) && m.getParameterCount() == args.length) {
                        m.setAccessible(true);
                        method = m;
                        methodCache.put(key, method);
                        break;
                    }
                }
                if (method != null) break;
                current = current.getSuperclass();
            }
        }
        if (method != null) {
            return method.invoke(obj instanceof Class ? null : obj, args);
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

    private static int getFloatingNavbarHeight(DialogsActivity activity) {
        int tabsHeightDp = 40;
        try {
            tabsHeightDp = (int) callPrivateMethod(MainTabsUiHelper, "getTabsViewHeightDp");
        } catch (Throwable ignored) {}

        int navBarHeight = 0;
        if (activity != null) {
            Object heightVal = getPrivateField(activity, "navigationBarHeight");
            if (heightVal instanceof Integer) {
                navBarHeight = (int) heightVal;
            }
        }
        return AndroidUtilities.dp(tabsHeightDp) + navBarHeight;
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

                float translationY;
                if (usesFloatingNavbar()) {
                    var mainTabsActivity = LaunchActivity.findFragment(MainTabsActivity.class);
                    View tabsViewWrapper = null;
                    if (mainTabsActivity != null) {
                        tabsViewWrapper = (View) getPrivateField(mainTabsActivity, "tabsViewWrapper");
                    }

                    float tabsTranslationY = 0f;
                    int tabsHeight = getFloatingNavbarHeight(activity);
                    if (tabsViewWrapper != null) {
                        tabsTranslationY = tabsViewWrapper.getTranslationY();
                    }

                    translationY = (height - tabsHeight + tabsTranslationY) - filterTabsView.getTop() - filterTabsView.getMeasuredHeight();
                } else {
                    int paddingBottom = (int) callPrivateMethod(activity, "calculateListViewPaddingBottom");
                    translationY = height - paddingBottom - filterTabsView.getTop();
                }
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

                if (usesFloatingNavbar()) {
                    int tabsHeight = getFloatingNavbarHeight((DialogsActivity) activity);
                    if (tabsHeight > 0) {
                        result = tabsHeight;
                    }
                }

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

    private static class InvalidateBlurHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            var activity = (DialogsActivity) param.thisObject;

            FilterTabsView filterTabsView = getFilterTabsView(activity);
            if (filterTabsView == null || filterTabsView.getVisibility() != View.VISIBLE || filterTabsView.getAlpha() <= 0f) {
                return;
            }

            View fragmentView = activity.fragmentView;
            if (fragmentView == null) return;

            int fragmentHeight = fragmentView.getMeasuredHeight();

            float top;
            float bottom = fragmentHeight;

            if (usesFloatingNavbar()) {
                var mainTabsActivity = LaunchActivity.findFragment(MainTabsActivity.class);
                View tabsViewWrapper = null;
                if (mainTabsActivity != null) {
                    tabsViewWrapper = (View) getPrivateField(mainTabsActivity, "tabsViewWrapper");
                }
                float tabsTranslationY = 0f;
                int tabsHeight = getFloatingNavbarHeight(activity);
                if (tabsViewWrapper != null) {
                    tabsTranslationY = tabsViewWrapper.getTranslationY();
                }

                top = fragmentHeight - tabsHeight + tabsTranslationY - (filterTabsView.getMeasuredHeight() * filterTabsView.getAlpha());
            } else {
                int paddingBottom = (int) callPrivateMethod(activity, "calculateListViewPaddingBottom");
                top = fragmentHeight - paddingBottom;
                if (activity.hasMainTabs) {
                    int navigationBarHeight = (int) getPrivateField(activity, "navigationBarHeight");
                    bottom = fragmentHeight - navigationBarHeight - AndroidUtilities.dp(8);
                }
            }

            RectF iBlur3PositionMainTabs = (RectF) getPrivateField(activity, "iBlur3PositionMainTabs");
            if (iBlur3PositionMainTabs != null) {
                iBlur3PositionMainTabs.set(0, top, fragmentView.getMeasuredWidth(), bottom);
                iBlur3PositionMainTabs.inset(0, LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS) ? 0 : -AndroidUtilities.dp(48));
            }

            Object scrollableViewNoiseSuppressor = getPrivateField(activity, "scrollableViewNoiseSuppressor");
            if (scrollableViewNoiseSuppressor != null) {
                Object iBlur3Positions = getPrivateField(activity, "iBlur3Positions");
                Object iBlur3Capture = getPrivateField(activity, "iBlur3Capture");

                if (iBlur3Positions != null && iBlur3Capture != null) {
                    callPrivateMethod(scrollableViewNoiseSuppressor, "setupRenderNodes", iBlur3Positions, 2);
                    callPrivateMethod(scrollableViewNoiseSuppressor, "invalidateResultRenderNodes", iBlur3Capture, fragmentView.getMeasuredWidth(), fragmentHeight);
                }
            }
        }
    }

    public static void unhook() {
        for (var unhook : unhooks) {
            if (unhook != null) unhook.unhook();
        }
        unhooks.clear();
        fieldCache.clear();
        methodCache.clear();
    }
}