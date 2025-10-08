package com.pessdes.lyrics.components.lrclib;

import android.annotation.SuppressLint;
import android.graphics.Typeface;

import com.pessdes.lyrics.components.PluginController;
import com.pessdes.lyrics.components.lrclib.dto.Lyrics;
import com.pessdes.lyrics.components.lrclib.dto.SyncedLyricsLine;
import com.pessdes.lyrics.ui.LyricsActivity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.ui.ActionBar.BaseFragment;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        return instance == null ? new LyricsController() : instance;
    }
    private LyricsController() {}

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
    private final String BASE_URL = "https://lrclib.net/api/search";

    private URL getRequestUrl(String trackName, String artistName) throws UnsupportedEncodingException, MalformedURLException {
        return new URL(BASE_URL + "?track_name=" + URLEncoder.encode(trackName, "UTF-8") + "&artist_name=" + URLEncoder.encode(artistName, "UTF-8"));
    }
    @SuppressLint("DefaultLocale")
    private Lyrics getLyricsInternal(String trackName, String artistName, double trackDuration) {
        try {
            HttpURLConnection con = (HttpURLConnection) getRequestUrl(trackName, artistName).openConnection();
            log("Sending request to: " + con.getURL());
            con.setRequestMethod("GET");
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(con.getInputStream())
            );
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            con.disconnect();
            var json = new JSONArray(String.valueOf(response));
            log(String.format("Got %d results", json.length()));
            if (json.length() == 0) {
                return null;
            }

            Lyrics firstNonInstrumental = null;
            for (int i = 0; i < json.length(); i++) {
                var item = json.getJSONObject(i);

                if (item.optBoolean("instrumental", false)) {
                    continue;
                }

                Lyrics currentLyrics = new Lyrics(
                        item.optString("trackName", null),
                        item.optString("artistName", null),
                        item.optDouble("duration", 0),
                        item.optString("plainLyrics", null),
                        item.optString("syncedLyrics", null)
                );

                if (firstNonInstrumental == null) {
                    firstNonInstrumental = currentLyrics;
                }

                if (trackDuration > 0 && Math.round(currentLyrics.duration) == Math.round(trackDuration)) {
                    return currentLyrics;
                }
            }

            return firstNonInstrumental;

        } catch (Exception ex) {
            log("Exception in getLyricsInternal: " + ex.getMessage());
            return null;
        }
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
        getInstance().logInternal("[lyrics] " + message);
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
}
