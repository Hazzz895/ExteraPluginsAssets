package com.pessdes.lyrics.components.lrclib;

import android.graphics.Typeface;

import com.pessdes.lyrics.components.PluginController;
import com.pessdes.lyrics.components.lrclib.dto.Lyrics;
import com.pessdes.lyrics.components.lrclib.dto.SyncedLyricsLine;
import com.pessdes.lyrics.components.lrclib.providers.IProvider;
import com.pessdes.lyrics.components.lrclib.providers.LrclibProvider;
import com.pessdes.lyrics.ui.LyricsActivity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LyricsController {
    private static final LyricsController instance = new LyricsController();

    private final Map<String, CompletableFuture<Lyrics>> lyricsFutures = new ConcurrentHashMap<>();

    private final ExecutorService networkExecutor = Executors.newCachedThreadPool();

    public static LyricsController getInstance() {
        return instance;
    }
    private LyricsController() {
        addLrclibProvider();
    }

    /**
     * Парсит LRC содержимое в список
     * @param plainSyncedLyrics LRC
     */
    public List<SyncedLyricsLine> parseSyncedLyrics(String plainSyncedLyrics) {
        if (plainSyncedLyrics == null) return null;

        List<SyncedLyricsLine> result = new ArrayList<>();
        String[] lines = plainSyncedLyrics.split("\n");

        Pattern pattern = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2})] (.*)");

        for (String line : lines) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.matches()) {
                int minutes = Integer.parseInt(matcher.group(1));
                int seconds = Integer.parseInt(matcher.group(2));
                int milliseconds = Integer.parseInt(matcher.group(3));
                int totalMilliseconds = ((60 * minutes + seconds) * 100 + milliseconds) * 10;

                String text = matcher.group(4);
                result.add(new SyncedLyricsLine(totalMilliseconds, text));
            }
        }

        return result;
    }

    private String getCacheKey(String trackName, String artistName, double trackDuration) {
        return trackName + "|" + artistName + "|" + trackDuration;
    }

    private final List<IProvider> providers = new ArrayList<>();

    public void addProvider(IProvider provider) {
        if (
                provider != null &&
                providers.stream().noneMatch(x -> Objects.equals(x.getId(), provider.getId()))
            )
        {
            lyricsFutures.entrySet().removeIf(entry -> {
                CompletableFuture<Lyrics> future = entry.getValue();
                return future.isDone() && future.getNow(null) == null;
            });
            providers.add(provider);
        }
    }

    public void removeProvider(IProvider provider) {
        if (provider != null) {
            removeProvider(provider.getId());
        }
    }

    public void removeProvider(String id) {
        providers.removeIf(x -> Objects.equals(x.getId(), id));
    }

    private Lyrics getLyricsInternal(String trackName, String artistName, double trackDuration) {
        try {
            providers.sort(Comparator.comparingInt(IProvider::getPriority));

            for (var provider : providers) {
                Lyrics result = provider.seachLyrics(trackName, artistName, trackDuration);
                if (result != null) {
                    return result;
                }
            }
        } catch (Exception ex) {
            log("Exception in getLyricsInternal: " + ex.getMessage());
        }

        return null;
    }

    @Nullable
    public Lyrics getLyrics(@NotNull String trackName, String artistName, double trackDuration) {
        String key = getCacheKey(trackName, artistName, trackDuration);

        CompletableFuture<Lyrics> future = lyricsFutures.computeIfAbsent(key, k -> CompletableFuture.supplyAsync(() ->
                getLyricsInternal(trackName, artistName, trackDuration), networkExecutor));

        try {
            Lyrics result = future.get();
            log(result != null ? "Got lyrics for: " + key : "Lyrics not found for: " + key);
            return result;
        } catch (Exception e) {
            log("Exception while waiting for lyrics future: " + e.getMessage());
            lyricsFutures.remove(key, future);
            return null;
        }
    }

    public Lyrics createLyrics(double trackDuration, String plainLyrics, String plainSyncedLyrics) {
        return new Lyrics(
                trackDuration,
                plainLyrics,
                plainSyncedLyrics
        );
    }

    public LyricsActivity presentLyricsActivity(BaseFragment baseFragment) {
        var activity = new LyricsActivity();
        baseFragment.presentFragment(activity);
        return activity;
    }

    final private Class<?> AppUtils;
    {
        try {
            AppUtils = Class.forName("com.exteragram.messenger.utils.AppUtils");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    final private Method logMethod;
    {
        try {
            logMethod = AppUtils.getDeclaredMethod("log", String.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private void logInternal(String message) {
        try {
            logMethod.invoke(null, message);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public static void log(String message) {
        getInstance().logInternal(String.format("[%s] %s", PluginController.getInstance().getModuleName(), message));
    }

    public static void log(Object object) {
        log(object.toString());
    }

    private Typeface typeface;

    public void setTypeface(Typeface typeface) {
        this.typeface = typeface;
    }

    public Typeface getTypeface() {
        return typeface;
    }

    public void initPluginController(String moduleName) {
        PluginController.initPluginController(moduleName);
    }

    public int getCurrentVersionCode() {
        return PluginController.getInstance().getVersionCode();
    }

    public int parseVersionCode(String version) {
        return PluginController.parseVersion(version);
    }

    public IProvider createSimpleProvider(String name, String id, Utilities.Callback3Return<String, String, Double, Lyrics> onSearchLyrics, int defaultPriority) {
        return new IProvider() {
            @Override
            public @Nullable Lyrics seachLyrics(@NotNull String trackName, String artistName, double trackDuration) {
                return onSearchLyrics.run(trackName, artistName, trackDuration);
            }

            @Override
            public @Nullable String getName() {
                return name;
            }

            @Override
            public @Nullable String getId() {
                return id;
            }

            @Override
            public int getDefaultPriority() {
                return defaultPriority;
            }
        };
    }

    public void addLrclibProvider() {
        var provider = new LrclibProvider();
        addProvider(provider);
    }
}
