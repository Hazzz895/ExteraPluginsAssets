package com.pessdes.bokeh;

import android.content.Context;

import androidx.camera.core.CameraProvider;
import androidx.camera.core.CameraSelector;
import androidx.camera.extensions.ExtensionsManager;

import java.util.concurrent.ExecutionException;

public class Main {
    public ExtensionsManager getExtensionsManager(Context ctx, CameraProvider provider) throws ExecutionException, InterruptedException {
        return ExtensionsManager.getInstanceAsync(ctx, provider).get();
    }
}
