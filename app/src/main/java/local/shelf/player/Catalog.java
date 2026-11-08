package local.shelf.player;

import android.net.Uri;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Catalog {
    private static final Pattern EPISODE = Pattern.compile("(?i)[. _-]+S(\\d{1,2})E(\\d{1,3})");
    private static final Pattern YEAR = Pattern.compile("(?<!\\d)((?:19|20)\\d{2})(?!\\d)");
    private static final Pattern RELEASE = Pattern.compile(
            "(?i)[. _-]+(?:2160p|1080p|720p|4k|uhd|bluray|blu-ray|webrip|web-dl|hdr10|x264|x265)(?:[. _-]|$)");

    record Title(String name, String year, int season, int episode) {
        boolean series() { return episode > 0; }
        String caption() {
            return series() ? "S" + season + " · E" + episode : year;
        }
    }

    record Movie(String path, Title title, String poster, String backdrop, List<String> subtitles) {
        String filename() { return path.substring(path.lastIndexOf('\\') + 1); }
    }

    static String basename(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    static Title title(String filename) {
        String value = basename(filename);
        int cut = value.length();
        int season = 0;
        int episode = 0;
        String year = "";
        Matcher episodes = EPISODE.matcher(value);
        if (episodes.find()) {
            cut = episodes.start();
            season = Integer.parseInt(episodes.group(1));
            episode = Integer.parseInt(episodes.group(2));
        }
        Matcher years = YEAR.matcher(value);
        while (years.find() && years.start() < cut) {
            if (years.start() > 0) {
                year = years.group(1);
                cut = years.start();
            }
        }
        Matcher releases = RELEASE.matcher(value);
        if (releases.find()) cut = Math.min(cut, releases.start());
        String name = value.substring(0, cut).replaceAll("[._]+", " ")
                .replaceAll("[\\s(\\[\\-]+$", "").trim();
        return new Title(name.isEmpty() ? value : name, year, season, episode);
    }

    static Uri uri(String path) {
        Uri.Builder uri = new Uri.Builder().scheme("smb").authority("library");
        for (String component : path.split("\\\\")) uri.appendPath(component);
        return uri.build();
    }

    static String language(String subtitle, String video) {
        String name = basename(subtitle.substring(subtitle.lastIndexOf('\\') + 1)).toLowerCase(Locale.ROOT);
        int baseLength = basename(video).length();
        if (name.length() <= baseLength) return "und";
        String suffix = name.substring(baseLength + 1);
        if (suffix.matches("^(zh|chs|cht|chi|zho|sc|tc)([._-].*)?$") || suffix.startsWith("简") || suffix.startsWith("繁")) return "zh";
        if (suffix.matches("^(en|eng)([._-].*)?$")) return "en";
        if (suffix.matches("^(ja|jpn)([._-].*)?$")) return "ja";
        return suffix.length() == 2 ? suffix : "und";
    }
}
