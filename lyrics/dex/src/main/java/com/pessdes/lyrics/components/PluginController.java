package com.pessdes.lyrics.components;

import static com.pessdes.lyrics.components.lrclib.LyricsController.log;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;

public class PluginController {
    private static PluginController instance;

    public static PluginController getInstance() {
        return instance;
    }

    private final String moduleName;
    private PyObject plugin;
    private PyObject localeController;

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
}
