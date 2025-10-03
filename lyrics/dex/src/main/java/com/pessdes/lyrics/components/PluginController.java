package com.pessdes.lyrics.components;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;

public class PluginController {
    private static final PluginController instance = new PluginController();

    private final PyObject plugin;

    private PyObject pluginInstance;

    private PluginController() {
        plugin = Python.getInstance().getModule("lyrics");
    }

    public static PluginController getInstance() {
        return instance;
    }

    public PyObject getPlugin() {
        return plugin;
    }

    public PyObject getPluginInstance() {
        if (pluginInstance == null) {
            pluginInstance = plugin.get("Plugin").callAttr("get_instance");
        }
        return pluginInstance;
    }

    public void log(Object message) {
        getPluginInstance().callAttr("log", message);
    }
}
