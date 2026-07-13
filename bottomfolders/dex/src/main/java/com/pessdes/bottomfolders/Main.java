package com.pessdes.bottomfolders;

import android.graphics.RectF;
import android.view.View;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import com.exteragram.messenger.config.BottomNavigationBar;
import com.exteragram.messenger.utils.ui.MainTabsUiHelper;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.DialogsActivityTopBubblesFadeView;
import org.telegram.ui.Components.DialogsActivityTopPanelLayout;
import org.telegram.ui.Components.FilterTabsView;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.MainTabsActivity;
import org.telegram.ui.MainTabsLayout;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import me.vkryl.android.animator.BoolAnimator;

public abstract class Main {
    private static Utilities.Callback<Object[]> logger = null;

    private static final ArrayList<XC_MethodHook.Unhook> unhooks = new ArrayList<>();

    private static final Map<String, Field> fieldCache = new HashMap<>();
    private static final Map<String, Method> methodCache = new HashMap<>();

    @Nullable
    private static Field getPrivateFieldField(Object obj, String fieldName) {
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
        return field;
    }

    @Nullable
    private static Object getPrivateField(Object obj, String fieldName) {
        var field = getPrivateFieldField(obj, fieldName);
        if (field != null) {
            try {
                return field.get(obj instanceof Class ? null : obj);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static void setPrivateField(Object obj, String fieldName, Object value) {
        var field = getPrivateFieldField(obj, fieldName);
        if (field != null) {
            try {
                field.set(obj, value);
            } catch (Exception ignored) { }
        }
    }

    @Nullable
    private static Object callPrivateMethod(Object obj, String methodName, Object... args) throws Exception {
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

    private static void log(Object... messages) {
        if (logger == null) return;
        logger.run(messages);
    }

    private static void Hook(Class<?> clazz, String methodName, XC_MethodHook hook) {
        unhooks.addAll(XposedBridge.hookAllMethods(clazz, methodName, hook));
    }

    private static boolean measuringDialogsRecycler = false;

    /** @noinspection ConfusingMainMethod*/
    @Keep
    public static void main(Utilities.Callback<Object[]> logger)  {
        Main.logger = logger;
        if (!unhooks.isEmpty()) return;
        log("Initializing: Hooking methods...");

        try {
            Hook(DialogsActivity.class, "updateContextViewPosition", new DialogsActivityCalculationsHook());
            Hook(DialogsActivity.class, "updateFloatingButtonOffset", new FabOffsetHook());
            Hook(DialogsActivity.class, "calculateListViewPaddingBottom", new PaddingBottomHook());
            Hook(DialogsActivity.DialogsRecyclerView.class, "onMeasure", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    measuringDialogsRecycler = true;
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    measuringDialogsRecycler = false;
                }
            });
            Hook(BoolAnimator.class, "getFloatValue", new IgnoreFilterTabsWhileMeasuring());
            Hook(DialogsActivity.class, "blur3_InvalidateBlur", new InvalidateBlurHook());
        } catch (Throwable t) {
            log("Failed to init:", t);
            throw t;
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
    private static FilterTabsView getFilterTabsView(DialogsActivity activity) {
        return (FilterTabsView) getPrivateField(activity, "filterTabsView");
    }

    private static boolean usesFloatingNavbar() {
        try {
            return BottomNavigationBar.floating();
        } catch (Throwable ignored) { }
        return false;
    }

    @Nullable
    public static MainTabsActivity findMainTabsActivity() {
        return LaunchActivity.findFragment(MainTabsActivity.class);
    }

    @Nullable
    private static View getTabsTranslationView(MainTabsActivity mainTabsActivity) {
        if (mainTabsActivity == null) {
            mainTabsActivity = findMainTabsActivity();
        }

        View tabsViewWrapper = (View) getPrivateField(mainTabsActivity, "tabsViewWrapper");
        if (tabsViewWrapper != null) {
            return tabsViewWrapper;
        }
        return (View) getPrivateField(mainTabsActivity, "tabsView");
    }

    private static int getFloatingNavbarHeight(DialogsActivity activity, MainTabsActivity mainTabsActivity) {
        int navBarHeight = 0;

        if (mainTabsActivity == null) {
            mainTabsActivity = findMainTabsActivity();
        }

        if (activity != null) {
            var tempNavBarHeight = getPrivateField(activity, "navigationBarHeight");
            if (tempNavBarHeight != null) {
                navBarHeight = (int) tempNavBarHeight;
            }

            if (!activity.hasMainTabs) {
                return navBarHeight;
            }
        }
        int tabsHeightPx = 0;
        try {
            tabsHeightPx = AndroidUtilities.dp(MainTabsUiHelper.getTabsViewHeightDp());
        } catch (Throwable ignored) {}

        if (tabsHeightPx <= 0) {
            try {
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

        return tabsHeightPx + navBarHeight;
    }

    private static class DialogsActivityCalculationsHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            try {
                var activity = (DialogsActivity) param.thisObject;
                var filterTabsView = getFilterTabsView(activity);
                if (filterTabsView == null) return;

                var p = filterTabsView.getParent();
                if (p != null) {
                    var parent = (View) p;
                    var height = parent.getMeasuredHeight();

                    float translationY;
                    if (activity.hasMainTabs && usesFloatingNavbar()) {
                        var mainTabsActivity = findMainTabsActivity();

                        View translationView = mainTabsActivity != null ? getTabsTranslationView(mainTabsActivity) : null;
                        float tabsTranslationY = translationView != null ? translationView.getTranslationY() : 0f;

                        int tabsHeight = getFloatingNavbarHeight(activity, mainTabsActivity);

                        translationY = ((height - tabsHeight + tabsTranslationY) - filterTabsView.getTop() - filterTabsView.getMeasuredHeight());
                        if (mainTabsActivity != null) {
                            View tabsView;
                            if (translationView instanceof MainTabsLayout) {
                                tabsView = translationView;
                            }
                            else {
                                tabsView = (View) getPrivateField(mainTabsActivity, "tabsView");
                            }

                            if (tabsView != null) {
                                translationY += ((float) tabsView.getMeasuredHeight() / 2 * (1 - tabsView.getAlpha()));
                            }
                        }
                    } else {
                        int paddingBottom = (int) Optional.ofNullable(callPrivateMethod(activity, "calculateListViewPaddingBottom")).orElse(0);
                        translationY = height - paddingBottom - filterTabsView.getTop();
                    }
                    filterTabsView.setTranslationY(translationY);
                }

                var topPanel = (DialogsActivityTopPanelLayout) getPrivateField(activity, "topPanelLayout");
                if (topPanel != null) {
                    float filtersTabHeight = AndroidUtilities.dp(43) * filterTabsView.getAlpha();
                    var animatorSearchVisible = (BoolAnimator) getPrivateField(activity, "animatorSearchVisible");
                    if (animatorSearchVisible != null) {
                        topPanel.setTranslationY(topPanel.getTranslationY() - filtersTabHeight * (1 - animatorSearchVisible.getFloatValue()));
                    }
                }

                var topBubblesFade = (DialogsActivityTopBubblesFadeView) getPrivateField(activity, "topBubblesFadeView");
                if (topBubblesFade != null) {
                    float topPanelsVisibility = 0.0f;
                    if (topPanel != null) {
                        topPanelsVisibility = topPanel.getMetadata().getTotalVisibility();
                    }
                    float filtersTabVisibility = filterTabsView.getAlpha();

                    float s = AndroidUtilities.dp(7) + (AndroidUtilities.dp(50) - AndroidUtilities.dp(7)) * Math.min(topPanelsVisibility, filtersTabVisibility);

                    float topPanelsHeight = topPanel != null ? topPanel.getAnimatedHeightWithPadding(0) : 0;

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
        protected void beforeHookedMethod(MethodHookParam param) {
            try {
                var activity = (DialogsActivity) param.thisObject;
                var filterTabsView = getFilterTabsView(activity);
                if (filterTabsView == null) return;

                setPrivateField(activity, "floatingButtonPanOffset", filterTabsView.getMeasuredHeight() * filterTabsView.getAlpha());
            }
            catch (Exception e) {
                log("Error adjusting FAB offset", e);
            }
        }
    }

    private static class PaddingBottomHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            try {
                var activity = (DialogsActivity) param.thisObject;
                var filterTabsView = getFilterTabsView(activity);
                if (filterTabsView != null) {
                    var result = (int) param.getResult();

                    if (usesFloatingNavbar()) {
                        int tabsHeight = getFloatingNavbarHeight(activity, null);
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

    private static class InvalidateBlurHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) {
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
                    var mainTabsActivity = findMainTabsActivity();
                    View translationView = getTabsTranslationView(mainTabsActivity);
                    float tabsTranslationY = translationView != null ? translationView.getTranslationY() : 0f;
                    int tabsHeight = getFloatingNavbarHeight(activity, mainTabsActivity);

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

    @Keep
    public static void unhook() {
        for (var unhook : unhooks) {
            if (unhook != null) unhook.unhook();
        }
        unhooks.clear();
        fieldCache.clear();
        methodCache.clear();
        logger = null;
    }
}