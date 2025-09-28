package com.pessdes.lyrics.components.lrclib;

import com.pessdes.lyrics.components.lrclib.dto.Lyrics;
import com.pessdes.lyrics.components.lrclib.dto.SyncedLyricsLine;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;

import java.io.BufferedReader;
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
    private static LyricsController instance;
    public static LyricsController getInstance() {
        return instance == null ? new LyricsController() : instance;
    }
    public LyricsController() {
        instance = this;
    }
    public List<SyncedLyricsLine> parseSyncedLyrics(String plainSyncedLyrics) {
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

    private static final Map<String, Lyrics> cachedLyrics = new HashMap<>();

    private String getCacheKey(String trackName, String artistName, int trackDuration) {
        return trackName + "|" + artistName + "|" + trackDuration;
    }
    private Lyrics getCachedLyrics(String trackName, String artistName, int trackDuration) {
        return cachedLyrics.get(getCacheKey(trackName, artistName, trackDuration));
    }
    private void cacheLyrics(String trackName, String artistName, int trackDuration, Lyrics lyrics) {
        cachedLyrics.put(getCacheKey(trackName, artistName, trackDuration), lyrics);
    }
    private final String BASE_URL = "https://lrclib.net/api/search";

    private URL getRequestUrl(String trackName, String artistName) throws UnsupportedEncodingException, MalformedURLException {

        return new URL(BASE_URL + "?track_name=" + URLEncoder.encode(trackName, "UTF-8") + "&artist_name=" + URLEncoder.encode(artistName, "UTF-8"));
    }
    private Lyrics getLyricsInternal(String trackName, String artistName, int trackDuration) {
        try {
            HttpURLConnection con = (HttpURLConnection) getRequestUrl(trackName, artistName).openConnection();
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

            if (json.length() == 0) {
                return null;
            }

            var preResult = new ArrayList<Lyrics>();
            for (int i = 0; i < json.length(); i++) {
                var item = json.getJSONObject(i);
                if (item.getBoolean("instrumental")) continue;
                if (trackDuration > 0 && item.getInt("duration") != trackDuration) return null;
                preResult.add(new Lyrics(
                        item.getInt("id"),
                        item.getString("trackName"),
                        item.getString("artistName"),
                        item.getString("albumName"),
                        item.getInt("duration"),
                        item.getBoolean("instrumental"),
                        item.getString("plainLyrics"),
                        item.getString("syncedLyrics")
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
            return null;
        }
    }

    @Nullable
    public Lyrics getLyrics(@NotNull String trackName, String artistName, int trackDuration) {
        return new Lyrics(0, "test", "test", "test", 0, false, "test", null);
        /*var cachedLyrics = getCachedLyrics(trackName, artistName, trackDuration);
        if (cachedLyrics != null) {
            return cachedLyrics;
        }
        return getLyricsInternal(trackName, artistName, trackDuration);*/
    }
}
