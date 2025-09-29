package com.pessdes.lyrics.components.lrclib;

import android.annotation.SuppressLint;

import com.pessdes.lyrics.components.lrclib.dto.Lyrics;
import com.pessdes.lyrics.components.lrclib.dto.SyncedLyricsLine;
import com.pessdes.lyrics.ui.LyricsActivity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LyricsController {
    private static final LyricsController instance = new LyricsController();
    private final Map<String, Lyrics> cachedLyrics = new HashMap<>();
    public static LyricsController getInstance() {
        return instance == null ? new LyricsController() : instance;
    }
    private LyricsController() {}
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

    private Lyrics getCachedLyrics(String trackName, String artistName, double trackDuration) {
        return cachedLyrics.get(getCacheKey(trackName, artistName, trackDuration));
    }

    private void cacheLyrics(String trackName, String artistName, double trackDuration, Lyrics lyrics) {
        cachedLyrics.put(getCacheKey(trackName, artistName, trackDuration), lyrics);
    }

    private final String BASE_URL = "https://lrclib.net/api/search";

    private URL getRequestUrl(String trackName, String artistName) throws UnsupportedEncodingException, MalformedURLException {
        return new URL(BASE_URL + "?track_name=" + URLEncoder.encode(trackName, "UTF-8") + "&artist_name=" + URLEncoder.encode(artistName, "UTF-8"));
    }
    @SuppressLint("DefaultLocale")
    private Lyrics getLyricsInternal(String trackName, String artistName, double trackDuration) {
        try {
            HttpURLConnection con = (HttpURLConnection) getRequestUrl(trackName, artistName).openConnection();
            LyricsController.log("Sending request to: " + con.getURL());
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
            LyricsController.log(String.format("Got %d results", json.length()));
            if (json.length() == 0) {
                return null;
            }

            var preResult = new ArrayList<Lyrics>();
            for (int i = 0; i < json.length(); i++) {
                var item = json.getJSONObject(i);
                if (item.optBoolean("instrumental", false)) continue;
                if (trackDuration > 0 && item.optDouble("duration", 0) != trackDuration && item.optString("syncedLyrics", null) != null) return null;
                preResult.add(new Lyrics(
                        item.optInt("id", 0),
                        item.optString("trackName", null),
                        item.optString("artistName", null),
                        item.optString("albumName", null),
                        item.optDouble("duration", 0),
                        item.optBoolean("instrumental", false),
                        item.optString("plainLyrics", null),
                        item.optString("syncedLyrics", null)
                ));
            }

            Lyrics result = preResult.get(0);
            for (int i = 0; i < preResult.size() && result.syncedLyrics == null; i++) {
                if (preResult.get(i).syncedLyrics != null) {
                    result = preResult.get(i);
                    break;
                }
            }

            return result;
        }
        catch (Exception ex) {
            log("Exception in getLyricsInternal: " + ex.getMessage());
            return null;
        }
    }

    @Nullable
    public Lyrics getLyrics(@NotNull String trackName, String artistName, double trackDuration) {
        return getLyrics(trackName, artistName, trackDuration, true);
    }

    @Nullable
    public Lyrics getLyrics(@NotNull String trackName, String artistName, double trackDuration, boolean fromCache) {
        try {
            log("Getting lyrics for: " + trackName + " - " + artistName + "(" + trackDuration + ")");
            Lyrics result = null;
            if (fromCache) {
                var cachedLyrics = getCachedLyrics(trackName, artistName, trackDuration);
                if (cachedLyrics != null) {
                    result = cachedLyrics;
                }
            }

            if (!fromCache || result == null) {
                result = getLyricsInternal(trackName, artistName, trackDuration);
                if (result != null) {
                    cacheLyrics(trackName, artistName, trackDuration, result);
                }
            }
            log(result != null ? "Got lyrics: " + result : "Lyrics not found");
            return result;
        }
        catch (Exception ex) {
            log("Exception in getLyrics: " + ex.getMessage());
            return null;
        }
    }

    public LyricsActivity presentLyricsActivity(BaseFragment baseFragment) {
        var activity = new LyricsActivity();
        baseFragment.presentFragment(activity);
        return activity;
    }

    private Utilities.Callback<Object> loggingBridge = null;

    public void setLoggingBridge(Utilities.Callback<Object> loggingBridge) {
        this.loggingBridge = loggingBridge;
    }

    private void logInternal(Object message) {
        if (loggingBridge != null) {
            loggingBridge.run(message);
        }
    }

    public static void log(Object message) {
        getInstance().logInternal(message);
    }
}
