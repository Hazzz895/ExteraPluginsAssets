package com.pessdes.bottomfolders;

import android.graphics.RectF;
import android.view.View;

import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.DialogsActivityTopBubblesFadeView;
import org.telegram.ui.Components.DialogsActivityTopPanelLayout;
import org.telegram.ui.Components.FilterTabsView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.MainTabsActivity;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import me.vkryl.android.animator.BoolAnimator;

public abstract class Main {
    private static Utilities.Callback<Object[]> logger = null;

    private static final ArrayList<XC_MethodHook.Unhook> unhooks = new ArrayList<>();

    private static final Map<String, Field> fieldCache = new HashMap<>();
    private static final Map<String, Method> methodCache = new HashMap<>();

    private static void log(Object... messages) {
        if (logger == null) return;
        logger.run(messages);
    }

    @Nullable
    static Class<?> MainTabsUiHelper = null;

    @Nullable
    static Class<?> BottomNavigationBar = null;

    public static void main() throws NoSuchMethodException {
        main(null);
    }

    @Nullable
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

    private static void Hook(Class<?> clazz, String methodName, XC_MethodHook hook) {
        unhooks.addAll(XposedBridge.hookAllMethods(clazz, methodName, hook));
    }

    private static boolean measuringDialogsRecycler = false;

    /** @noinspection ConfusingMainMethod*/
    public static void main(Utilities.Callback<Object[]> logger)  {
        Main.logger = logger;
        if (!unhooks.isEmpty()) return;
        log("Initializing: Hooking methods...");

        try {
            MainTabsUiHelper = Class.forName("com.exteragram.messenger.utils.ui.MainTabsUiHelper");
        } catch (Throwable ignored) {}
        try {
            BottomNavigationBar = Class.forName("com.exteragram.messenger.config.BottomNavigationBar");
        } catch (Throwable ignored) {}

        if (MainTabsUiHelper == null) {
            log("[WARN]", "MainTabsUiHelper not found!");
        }
        if (BottomNavigationBar == null) {
            log("[WARN]", "BottomNavigationBar not found!");
        }

        try {
            Hook(DialogsActivity.class, "updateContextViewPosition", new DialogsActivityCalculationsHook());
            Hook(DialogsActivity.class, "updateFloatingButtonOffset", new FabOffsetHook());
            Hook(DialogsActivity.class, "calculateListViewPaddingBottom", new PaddingBottomHook());
            Hook(DialogsActivity.DialogsRecyclerView.class, "onMeasure", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    measuringDialogsRecycler = true;
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    measuringDialogsRecycler = false;
                }
            });
            Hook(BoolAnimator.class, "getFloatValue", new IgnoreFilterTabsWhileMeasuring());
            Hook(DialogsActivity.class, "blur3_InvalidateBlur", new InvalidateBlurHook());
        } catch (Throwable t) {
            log("Failed to init:", t);
        }
        log("Initialized!");
    }

    private static class IgnoreFilterTabsWhileMeasuring extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            if (measuringDialogsRecycler &&
                    (int) Optional.ofNullable(getPrivateField(param.thisObject, "id")).orElse(-1) == 8) {
                param.setResult(0.0f);
            }
        }
    }

    @Nullable
    private static FilterTabsView getFilterTabsView(Object obj) {
        return (FilterTabsView) getPrivateField(obj, "filterTabsView");
    }

    private static boolean usesFloatingNavbar() {
        try {
            return (boolean) Optional.ofNullable(callPrivateMethod(BottomNavigationBar, "floating")).orElse(false);
        }
        catch (Throwable ignored) {}
        return false;
    }

    @Nullable
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

    @Nullable
    public static <T extends BaseFragment> T findFragment(Class<T> clazz) {
        return LaunchActivity.findFragment(clazz);
    }

    @Nullable
    private static DialogsActivity findDialogsActivity() {
        var mainTabsActivity = LaunchActivity.findFragment(MainTabsActivity.class);
        if (mainTabsActivity == null) {
            return null;
        }
        return mainTabsActivity.getDialogsActivity();
    }

    @Nullable
    private static View getTabsTranslationView(Object mainTabsActivity) {
        if (mainTabsActivity == null) return null;
        View tabsViewWrapper = (View) getPrivateField(mainTabsActivity, "tabsViewWrapper");
        if (tabsViewWrapper != null) {
            return tabsViewWrapper;
        }
        return (View) getPrivateField(mainTabsActivity, "tabsView");
    }

    private static int getFloatingNavbarHeight(DialogsActivity activity) {
        int tabsHeightPx = 0;
        try {
            if (MainTabsUiHelper != null) {
                int tabsHeightDp = (int) callPrivateMethod(MainTabsUiHelper, "getTabsViewHeightDp");
                tabsHeightPx = AndroidUtilities.dp(tabsHeightDp);
            }
        } catch (Throwable ignored) {}

        if (tabsHeightPx <= 0) {
            try {
                var mainTabsActivity = findFragment(MainTabsActivity.class);
                if (mainTabsActivity != null) {
                    View tabsView = (View) getPrivateField(mainTabsActivity, "tabsView");
                    if (tabsView != null) {
                        tabsHeightPx = tabsView.getMeasuredHeight();
                        if (tabsHeightPx <= 0) {
                            var lp = tabsView.getLayoutParams();
                            if (lp != null && lp.height > 0) {
                                tabsHeightPx = lp.height;
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        if (tabsHeightPx <= 0) {
            tabsHeightPx = AndroidUtilities.dp(72);
        }

        int navBarHeight = 0;
        if (activity != null) {
            navBarHeight = (int) Optional.ofNullable(getPrivateField(activity, "navigationBarHeight")).orElse(0);
        }
        return tabsHeightPx + navBarHeight;
    }

    private static class DialogsActivityCalculationsHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            try {
                var activity = (DialogsActivity) param.thisObject;
                var filterTabsView = getFilterTabsView(activity);
                if (filterTabsView == null) return;

                var p = filterTabsView.getParent();
                if (p != null) {
                    var parent = (View) p;
                    var height = parent.getMeasuredHeight();

                    float translationY;
                    if (usesFloatingNavbar()) {
                        var mainTabsActivity = findFragment(MainTabsActivity.class);
                        View translationView = getTabsTranslationView(mainTabsActivity);

                        float tabsTranslationY = 0f;
                        int tabsHeight = getFloatingNavbarHeight(activity);
                        if (translationView != null) {
                            tabsTranslationY = translationView.getTranslationY();
                        }

                        translationY = ((height - tabsHeight + tabsTranslationY) - filterTabsView.getTop() - filterTabsView.getMeasuredHeight());
                        if (mainTabsActivity != null) {
                            var tabsView = (View) getPrivateField(mainTabsActivity, "tabsView");
                            if (tabsView != null) {
                                translationY += ((float) tabsView.getMeasuredHeight() / 2 * (1 - tabsView.getAlpha()));
                            }
                        }
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
                    assert animatorSearchVisible != null;
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
            catch (Throwable t) {
                log("Error calculating", t);
            }
        }
    }

    private static class FabOffsetHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            try {
                var filterTabsView = getFilterTabsView(param.thisObject);
                if (filterTabsView == null) return;

                var fab = (View) getPrivateField(param.thisObject, "floatingButton3");
                if (fab != null) {
                    fab.setTranslationY(fab.getTranslationY() - filterTabsView.getMeasuredHeight() * filterTabsView.getAlpha());
                }
            }
            catch (Exception e) {
                log("Error adjusting FAB offset", e);
            }
        }
    }

    private static class PaddingBottomHook extends XC_MethodHook {
        @Override //
        protected void afterHookedMethod(MethodHookParam param) {
            try {
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
            } catch (Exception e) {
                log("Error adjusting padding bottom", e);
            }
        }
    }

    private static class OnLayoutHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) {

        }
    }

    private static class InvalidateBlurHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            try {
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
                    var mainTabsActivity = findFragment(MainTabsActivity.class);
                    View translationView = getTabsTranslationView(mainTabsActivity);
                    float tabsTranslationY = 0f;
                    int tabsHeight = getFloatingNavbarHeight(activity);
                    if (translationView != null) {
                        tabsTranslationY = translationView.getTranslationY();
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
            } catch (Exception e) {
                log("Error invalidation blur", e);
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