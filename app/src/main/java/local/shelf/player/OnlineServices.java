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
    private static final String TMDB = "https://api.tmdb.org/3";
    private static final String OPEN_SUBTITLES = "https://api.opensubtitles.com/api/v1";

    record Metadata(String name,String overview,String poster,String backdrop,int id,String type,String year,double rating,String genres,int minutes) {
        JSONObject json() throws org.json.JSONException {
            return new JSONObject().put("name",name).put("overview",overview).put("poster",poster).put("backdrop",backdrop)
                .put("id",id).put("type",type).put("year",year).put("rating",rating).put("genres",genres).put("minutes",minutes);
        }
        static Metadata fromJson(JSONObject o){return new Metadata(o.optString("name"),o.optString("overview"),nullable(o,"poster"),nullable(o,"backdrop"),o.optInt("id"),o.optString("type"),o.optString("year"),o.optDouble("rating",0),o.optString("genres"),o.optInt("minutes"));}
        private static String nullable(JSONObject o,String k){return o.isNull(k)||o.optString(k).isEmpty()?null:o.optString(k);}
        String facts(){List<String> parts=new ArrayList<>();if(!year.isBlank())parts.add(year);if(rating>0)parts.add(String.format(Locale.ROOT,"TMDB %.1f",rating));if(!genres.isBlank())parts.add(genres);if(minutes>0)parts.add(minutes+" 分钟");return String.join(" · ",parts);}
    }
    record Subtitle(int fileId, String name, String language, boolean exact) {}

    static List<Metadata> search(String query,boolean series,String token) throws Exception {
        String category=series?"tv":"movie";
        JSONArray results=request(TMDB+"/search/"+category+"?language=zh-CN&include_adult=false&query="+Uri.encode(query),token,"",null).optJSONArray("results");
        List<Metadata> found=new ArrayList<>();if(results!=null)for(int i=0;i<results.length();i++)found.add(parse(results.getJSONObject(i),category));return found;
    }
    static Metadata movie(Catalog.Title title,String token) throws Exception {
        String category=title.series()?"tv":"movie";
        for (String name : Catalog.lookupNames(title.name())) {
            Catalog.Title query = new Catalog.Title(name, title.year(), title.season(), title.episode());
            JSONArray results = request(TMDB+"/search/"+category+"?language=zh-CN&include_adult=false&query="+Uri.encode(name)
                    +(title.year().isEmpty()?"":(title.series()?"&first_air_date_year=":"&year=")+title.year()),token,"",null)
                    .optJSONArray("results");
            Metadata match = select(query, results);
            if (match != null) return details(match, token);
        }
        return null;
    }
    static Metadata select(Catalog.Title title,JSONArray results) throws Exception {
        String category=title.series()?"tv":"movie";
        if(results==null)return null;Metadata match=null;
        String target=TitleMatching.literal(title.name());if(target.isEmpty())return null;
        for(int i=0;i<results.length();i++){
            JSONObject raw=results.getJSONObject(i);Metadata m=parse(raw,category);
            String original=raw.optString(title.series()?"original_name":"original_title");
            if(!target.equals(TitleMatching.literal(m.name()))&&!target.equals(TitleMatching.literal(original)))continue;
            if(!title.year().isEmpty()&&!title.year().equals(m.year()))continue;
            if(match!=null)return null;match=m;
        }
        return match;
    }
    static Metadata details(Metadata candidate,String token) throws Exception {
        if(candidate.id()<=0||!(candidate.type().equals("movie")||candidate.type().equals("tv")))throw new IOException("Invalid title identifier");
        return parse(request(TMDB+"/"+candidate.type()+"/"+candidate.id()+"?language=zh-CN",token,"",null),candidate.type());
    }
    private static Metadata parse(JSONObject o,String type){
        String date=o.optString(type.equals("tv")?"first_air_date":"release_date");
        List<String> genres=new ArrayList<>();JSONArray g=o.optJSONArray("genres");if(g!=null)for(int i=0;i<g.length();i++){JSONObject item=g.optJSONObject(i);if(item!=null)genres.add(item.optString("name"));}
        int minutes=o.optInt("runtime");JSONArray runtime=o.optJSONArray("episode_run_time");if(minutes==0&&runtime!=null&&runtime.length()>0)minutes=runtime.optInt(0);
        return new Metadata(o.optString(type.equals("tv")?"name":"title"),o.optString("overview"),imageUrl(o.optString("poster_path"),"w500"),imageUrl(o.optString("backdrop_path"),"w1280"),o.optInt("id"),type,date.length()>=4?date.substring(0,4):"",o.optDouble("vote_average",0),String.join(" / ",genres),minutes);
    }
    private static String imageUrl(String path,String size){return path.matches("/[A-Za-z0-9_.-]+")?"https://image.tmdb.org/t/p/"+size+path:null;}

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
            connection.setRequestProperty("User-Agent", "ShelfPlayer v0.4.5");
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
