package com.pessdes.lyrics.components.lrclib.providers;

import static com.pessdes.lyrics.components.lrclib.LyricsController.log;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;

import com.pessdes.lyrics.components.lrclib.LyricsController;
import com.pessdes.lyrics.components.lrclib.dto.Lyrics;

import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;

public class LrclibProvider implements IProvider {
    private final String BASE_URL = "https://lrclib.net/api/search";

    private URL getRequestUrl(String trackName, String artistName) throws UnsupportedEncodingException, MalformedURLException {
        return new URL(BASE_URL + "?track_name=" + URLEncoder.encode(trackName, "UTF-8") + "&artist_name=" + URLEncoder.encode(artistName, "UTF-8"));
    }

    @SuppressLint("DefaultLocale")
    @Override
    public @Nullable Lyrics seachLyrics(@NonNull String trackName, String artistName, double trackDuration) {
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

            Lyrics match = null;
            for (int i = 0; i < json.length(); i++) {
                var item = json.getJSONObject(i);

                if (item.optBoolean("instrumental", false)) {
                    continue;
                }

                Lyrics currentLyrics = new Lyrics(
                        item.optDouble("duration", 0),
                        item.optString("plainLyrics", null),
                        item.optString("syncedLyrics", null)
                );

                if (match == null) {
                    match = currentLyrics;
                }

                if (trackDuration > 0 && Math.round(currentLyrics.duration) == Math.round(trackDuration)) {
                    match = currentLyrics;
                    break;
                }
            }
            
            if (match != null && match.plainSyncedLyrics != null) {
                match.parseSyncedLyricsAsLrc();
            }

            return match;
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    public @Nullable String getName() {
        return "LRCLib.net";
    }

    @Override
    public @Nullable String getId() {
        return "lrclib";
    }
}
