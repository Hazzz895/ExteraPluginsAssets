package com.pessdes.lyrics.components;

import static com.pessdes.lyrics.components.lrclib.LyricsController.log;

import com.chaquo.python.PyInvocationHandler;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

public class PluginController {
    public static class Constants {
        public static final int ONE_ZERO = PluginController.parseVersion("1.0");
        public static final int ONE_TWO = PluginController.parseVersion("1.2");

        public static final int TWO_ZERO = PluginController.parseVersion("2.0");

        public static final int TARGET_VERSION = TWO_ZERO;
    }

    private static PluginController instance;
    public static PluginController getInstance() {
        return instance;
    }

    private PyObject plugin;
    private PyObject localeController;
    private PyObject pluginInstance; // ≥ 2.0

    private final String moduleName;
    private String id;
    private int versionCode = -1;
    private PluginController(String moduleName) {
        this.moduleName = moduleName;
    }
    public static void initPluginController(String moduleName) {
        instance = new PluginController(moduleName);
    }

    public PyObject getPlugin() {
        if (plugin == null) {
            plugin = Python.getInstance().getModule(moduleName);
        }
        return plugin;
    }
    public PyObject getLocaleController() {
        if (localeController == null) {
            localeController = getPlugin().get("locale_controller");
        }
        return localeController;
    }
    public String locale(String key) {
        return getLocaleController().callAttr("get_string", key).toString();
    }
    public int getVersionCode() {
        if (versionCode == -1) {
            versionCode = parseVersion(getPlugin().get("__version__").toString());
        }
        return versionCode;
    }
    public PyObject getPluginInstance() {
        if (pluginInstance == null) {
            if (getVersionCode() < Constants.ONE_ZERO) {
                return null;
            }
            pluginInstance = getPlugin().callAttr("get_plugin_instance");
        }
        return pluginInstance;
    }
    public String getModuleName() {
        return moduleName;
    }
    public static int parseVersion(String version) {
        String[] parts = version.split("\\.");
        int result = 0;
        int multiplier = (int) Math.pow(1000, parts.length - 1);

        for (String part : parts) {
            int value = Integer.parseInt(part);
            result += value * multiplier;
            multiplier /= 1000;
        }

        return result;
    }

    /*private static Class<?> pluginsController;
    {
        try {
            pluginsController = Class.forName("com.exteragram.messenger.plugins.PluginController");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private Object getPluginsControllerInstance() {
        try {
            return pluginsController.getMethod("getInstance").invoke(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String getId() {
        if (id == null) {
            id = Objects.requireNonNull(getPlugin().get("__id__")).toString();
        }
        return id;
    }
    public boolean getPluginSettingBoolean(String key, boolean defaultValue){
        try {
            Object result = pluginsController
                    .getDeclaredMethod("getPluginSettingBoolean", String.class, String.class, boolean.class)
                    .invoke(getPluginsControllerInstance(), getId(), key, defaultValue);
            if (result == null) {
                return defaultValue;
            }
            else {
                return (boolean) result;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public String getPluginSettingString(String key, String defaultValue){
        try {
            Object result = pluginsController
                    .getDeclaredMethod("getPluginSettingString", String.class, String.class, String.class)
                    .invoke(getPluginsControllerInstance(), getId(), key, defaultValue);
            if (result == null) {
                return defaultValue;
            }
            else {
                return (String) result;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public int getPluginSettingInt(String key, int defaultValue){
        try {
            Object result = pluginsController
                    .getDeclaredMethod("getPluginSettingInt", String.class, String.class, int.class)
                    .invoke(getPluginsControllerInstance(), getId(), key, defaultValue);
            if (result == null) {
                return defaultValue;
            }
            else {
                return (int) result;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public void setPluginSettingBoolean(String key, boolean value){
        try {
            pluginsController
                    .getDeclaredMethod("setPluginSettingBoolean", String.class, String.class, boolean.class)
                    .invoke(getPluginsControllerInstance(), getId(), key, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public void setPluginSettingString(String key, String value){
        try {
            pluginsController
                    .getDeclaredMethod("setPluginSettingString", String.class, String.class, String.class)
                    .invoke(getPluginsControllerInstance(), getId(), key, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public void setPluginSettingInt(String key, int value){
        try {
            pluginsController
                    .getDeclaredMethod("setPluginSettingInt", String.class, String.class, int.class)
                    .invoke(getPluginsControllerInstance(), getId(), key, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    private static final String PROVIDER_ENABLE_TAMPLATE = "__%s_enabled__";
    public boolean isProviderEnabled(String providerId) {
        return getPluginSettingBoolean(String.format(PROVIDER_ENABLE_TAMPLATE, providerId), true);
    }
    public void setProviderEnabled(String providerId, boolean enable) {
        setPluginSettingBoolean(String.format(PROVIDER_ENABLE_TAMPLATE, providerId), enable);
    }*/
}
