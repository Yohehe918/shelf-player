package local.shelf.player;

import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class OnlineServices {
    private static final String TMDB = "https://api.themoviedb.org/3";
    private static final String OPEN_SUBTITLES = "https://api.opensubtitles.com/api/v1";

    record Metadata(String name, String overview, String poster, String backdrop) {}
    record Subtitle(int fileId, String name, String language, boolean exact) {}

    static Metadata movie(Catalog.Title title, String token) throws Exception {
        String category = title.series() ? "tv" : "movie";
        String yearKey = title.series() ? "&first_air_date_year=" : "&year=";
        JSONObject payload = request(TMDB + "/search/" + category + "?language=zh-CN&include_adult=false&query="
                + Uri.encode(title.name()) + (title.year().isEmpty() ? "" : yearKey + title.year()), token, "", null);
        JSONArray results = payload.optJSONArray("results");
        if (results == null) return null;
        String target = normalize(title.name());
        JSONObject match = null;
        for (int index = 0; index < results.length(); index++) {
            JSONObject candidate = results.getJSONObject(index);
            String display = candidate.optString(title.series() ? "name" : "title");
            String original = candidate.optString(title.series() ? "original_name" : "original_title");
            if (!target.equals(normalize(display)) && !target.equals(normalize(original))) continue;
            if (match != null) return null;
            match = candidate;
        }
        if (match == null) return null;
        return new Metadata(match.optString(title.series() ? "name" : "title"), match.optString("overview"),
                imageUrl(match.optString("poster_path"), "w500"), imageUrl(match.optString("backdrop_path"), "w1280"));
    }

    private static String normalize(String title) {
        return Normalizer.normalize(title, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", "");
    }

    private static String imageUrl(String path, String size) {
        return path.matches("/[A-Za-z0-9_.-]+") ? "https://image.tmdb.org/t/p/" + size + path : null;
    }

    static List<Subtitle> subtitles(Catalog.Title title, String hash, String key, String languages) throws Exception {
        String endpoint = OPEN_SUBTITLES + "/subtitles?languages=" + Uri.encode(languages)
                + "&order_by=download_count&order_direction=desc"
                + (hash.isEmpty() ? "&query=" + Uri.encode(title.name()) : "&moviehash=" + hash);
        if (hash.isEmpty() && title.series()) endpoint += "&season_number=" + title.season() + "&episode_number=" + title.episode();
        if (hash.isEmpty() && !title.year().isEmpty()) endpoint += "&year=" + title.year();
        JSONArray records = request(endpoint, "", key, null).optJSONArray("data");
        List<Subtitle> matches = new ArrayList<>();
        if (records == null) return matches;
        for (int index = 0; index < records.length(); index++) {
            JSONObject attributes = records.getJSONObject(index).optJSONObject("attributes");
            if (attributes == null) continue;
            JSONArray files = attributes.optJSONArray("files");
            if (files == null || files.length() == 0) continue;
            JSONObject file = files.getJSONObject(0);
            int fileId = file.optInt("file_id");
            if (fileId > 0) matches.add(new Subtitle(fileId, file.optString("file_name", "字幕 " + fileId),
                    attributes.optString("language", "und"), attributes.optBoolean("moviehash_match")));
        }
        return matches;
    }

    static byte[] download(Subtitle subtitle, String key, String user, String password) throws Exception {
        String token = "";
        if (!user.isEmpty() && !password.isEmpty()) {
            JSONObject login = new JSONObject().put("username", user).put("password", password);
            token = request(OPEN_SUBTITLES + "/login", "", key, login).getString("token");
        }
        JSONObject response = request(OPEN_SUBTITLES + "/download", token, key,
                new JSONObject().put("file_id", subtitle.fileId()).put("sub_format", "srt"));
        return fetch(response.getString("link"), "", "", null, 2 * 1024 * 1024);
    }

    static byte[] image(String url) throws IOException {
        return fetch(url, "", "", null, 6 * 1024 * 1024);
    }

    private static JSONObject request(String endpoint, String bearer, String key, JSONObject body) throws Exception {
        return new JSONObject(new String(fetch(endpoint, bearer, key, body, 2 * 1024 * 1024), StandardCharsets.UTF_8));
    }

    private static byte[] fetch(String endpoint, String bearer, String key, JSONObject body, int limit) throws IOException {
        URL url = new URL(endpoint);
        if (!url.getProtocol().equals("https")) throw new IOException("HTTPS is required");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setConnectTimeout(7000);
            connection.setReadTimeout(10000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "ShelfPlayer v0.2");
            if (!bearer.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + bearer);
            if (!key.isEmpty()) connection.setRequestProperty("Api-Key", key);
            if (body != null) {
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                try (var stream = connection.getOutputStream()) {
                    stream.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = connection.getResponseCode();
            if (code != 200 && code != 201) throw new IOException("HTTP " + code);
            try (InputStream stream = connection.getInputStream()) {
                byte[] result = stream.readNBytes(limit + 1);
                if (result.length > limit) throw new IOException("Response exceeds size limit");
                return result;
            }
        } finally {
            connection.disconnect();
        }
    }
}
