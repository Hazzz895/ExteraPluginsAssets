package com.pessdes.lyrics.components;

import static com.pessdes.lyrics.components.lrclib.LyricsController.log;

import com.chaquo.python.PyInvocationHandler;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;

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
}
