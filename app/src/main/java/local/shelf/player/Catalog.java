package local.shelf.player;

import android.net.Uri;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Catalog {
    private static final Pattern FOLDER_EPISODE = Pattern.compile("(?i)^(?:e|ep|episode|第)\\s*(\\d{1,3})(?:集)?(?:\\b|[._ -]).*");
    private static final Pattern FOLDER_SEASON = Pattern.compile("(?i)^(?:season|s|第)\\s*(\\d{1,3})(?:季)?$");
    private static final Pattern BLURAY_STREAM = Pattern.compile("(?i)(?:^|\\\\)BDMV\\\\STREAM\\\\[^\\\\]+$");
    private static final Pattern DISC_NUMBER =
            Pattern.compile("(?i)(?:^|[ ._-])(?:disc|disk|cd|bd|d)[ ._-]*([0-9]+)$");
    private static final Pattern SEASON_PACK = Pattern.compile("(?i)^(.+?)[ ._-]+S([0-9]{1,2})(?=E[0-9]|[ ._-]|$)");
    private static final String CHINESE_NUMBERS = "零一二三四五六七八九";
    private static final Pattern CHINESE_SEASON = Pattern.compile(
            "^(.+?)[ ._-]*第([0-9]{1,2}|[零一二三四五六七八九]|[一二三四五六七八九]?十[一二三四五六七八九]?)季");
    private static final Pattern FILE_EPISODE = Pattern.compile("(?i)(?:^|[ ._-])EP[ ._-]*([0-9]{1,3})(?=[ ._-]|$)");
    private static final Pattern LEADING_RELEASE_TAG = Pattern.compile("(?i)^(?:\\[(?:[0-9]+k世界|BT世界网|[0-9]+p|www\\.[^\\]]+)\\]\\s*)+");
    private static final Pattern HAN_FIRST = Pattern.compile("^(.+?\\p{IsHan})\\s+([A-Za-z][^\\p{IsHan}]+)$");
    private static final Pattern LATIN_FIRST = Pattern.compile("^([A-Za-z][^\\p{IsHan}]+?)\\s+(\\p{IsHan}.*)$");
    private static final Pattern YEAR = Pattern.compile("(?<!\\d)((?:19|20)\\d{2})(?!\\d)");
    private static final Pattern RELEASE = Pattern.compile(
            "(?i)[. _-]+(?:2160p|1080p|720p|4k|uhd|bluray|blu-ray|webrip|web-dl|hdtv|remux|hdr10|hevc|x264|x265)(?:[. _-]|$)");

    record Title(String name, String year, int season, int episode) {
        boolean series() { return episode > 0; }
        String caption() {
            return series() ? "S" + season + " · E" + episode : year;
        }
    }

    record Movie(String path, Title title, String poster, String backdrop, List<String> subtitles, String discFolder) {
        Movie(String path, Title title, String poster, String backdrop, List<String> subtitles) {
            this(path, title, poster, backdrop, subtitles, null);
        }
        boolean bluRay() { return discFolder != null; }
        String metadataKey() { return bluRay() ? "@bluray:" + discFolder : path; }
        Title metadataTitle() {
            Title show = bluRay() && !title.series() ? discSeries(discFolder) : null;
            return show == null ? title : show;
        }
        String discNumber() {
            if (bluRay()) {
                Matcher disc = DISC_NUMBER.matcher(lastComponent(discFolder));
                return disc.find() ? disc.group(1) : "";
            }
            Matcher disc = DISC_NUMBER.matcher(basename(filename()));
            return disc.matches() ? disc.group(1) : "";
        }
        String filename() { return path.substring(path.lastIndexOf('\\') + 1); }
    }

    static String bluRayFolder(String path) {
        Matcher stream = BLURAY_STREAM.matcher(path);
        return stream.find() ? path.substring(0, stream.start()) : null;
    }

    static Title discTitle(String folder) {
        if (folder.isEmpty()) return new Title("蓝光原盘", "", 0, 0);
        String name = lastComponent(folder);
        Title show = discSeries(folder);
        if (show != null) {
            Matcher episode = FOLDER_EPISODE.matcher(name + " ");
            int number = episode.matches() ? Integer.parseInt(episode.group(1)) : 0;
            return new Title(show.name(), show.year(), show.season(), number);
        }
        int separator = folder.lastIndexOf('\\');
        if (separator >= 0 && DISC_NUMBER.matcher(name).matches()) {
            name = lastComponent(folder.substring(0, separator));
        }
        Title parsed = title(name + ".m2ts");
        return new Title(parsed.name(), parsed.year(), 0, 0);
    }

    // A season disc can use TV metadata without claiming that its playable clip is a specific episode.
    private static Title discSeries(String folder) {
        int separator = folder.lastIndexOf('\\');
        if (separator < 0) return null;
        String child = lastComponent(folder);
        if (!DISC_NUMBER.matcher(child).find() && !FOLDER_EPISODE.matcher(child + " ").matches()) {
            return null;
        }
        Matcher season = SEASON_PACK.matcher(lastComponent(folder.substring(0, separator)));
        if (!season.find()) return null;
        Title show = title(season.group(1) + ".m2ts");
        return new Title(show.name(), show.year(), Integer.parseInt(season.group(2)), 1);
    }

    private static String lastComponent(String path) {
        return path.substring(path.lastIndexOf('\\') + 1);
    }

    static String basename(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    static Title title(String filename) { return titleInPath(filename); }

    static Title titleInPath(String path) {
        String[] parts=path.replace((char)92, '/').split("/");
        String value=basename(parts[parts.length-1]);
        EpisodeNames.Result ep=EpisodeNames.parse(value);
        boolean prefix=false;
        if(ep==null){ep=EpisodeNames.parse("Folder "+value);prefix=ep!=null;}
        int season=ep==null?0:ep.season(),episode=ep==null?0:ep.episode();
        String name=ep==null?value:prefix?"":ep.name();
        String parent=parts.length>1?parts[parts.length-2]:"";
        if (ep == null && !parent.isBlank()) {
            Matcher pack = SEASON_PACK.matcher(parent);
            Matcher fileEpisode = FILE_EPISODE.matcher(value);
            if (fileEpisode.find()) {
                int episodeNumber = Integer.parseInt(fileEpisode.group(1));
                if (pack.find()) {
                    Title show = title(pack.group(1) + ".mkv");
                    return new Title(show.name(), show.year(), Integer.parseInt(pack.group(2)), episodeNumber);
                }
                Matcher chinese = CHINESE_SEASON.matcher(parent);
                if (chinese.find()) {
                    Title show = title(chinese.group(1) + ".mkv");
                    return new Title(show.name(), show.year(), seasonNumber(chinese.group(2)), episodeNumber);
                }
            }
            Matcher disc = DISC_NUMBER.matcher(value);
            if (disc.matches()) {
                Title container = title(parent + ".mkv");
                return new Title(container.name(), container.year(), 0, 0);
            }
        }
        Matcher folderSeason=FOLDER_SEASON.matcher(parent);
        if(folderSeason.matches()){
            if(episode==0){Matcher e=FOLDER_EPISODE.matcher(value+" ");if(e.matches()){season=Integer.parseInt(folderSeason.group(1));episode=Integer.parseInt(e.group(1));name="";}}
            parent=parts.length>2?parts[parts.length-3]:"";
        }
        if(name.isBlank() && !parent.isBlank())name=parent;
        int cut=name.length();String year="";
        Matcher releases=RELEASE.matcher(" "+name);if(releases.find())cut=Math.max(0,releases.start()-1);
        String candidate=name.substring(0,cut);
        Matcher years=YEAR.matcher(candidate);int yearAt=-1;
        while(years.find())if(years.start()>0&&Integer.parseInt(years.group(1))<=java.time.Year.now().getValue()+1){year=years.group(1);yearAt=years.start();}
        if(yearAt>=0)cut=Math.min(cut,yearAt);
        String clean=name.substring(0,cut).replaceAll("[._]+"," ").replaceAll("[\\s(\\[\\-]+$","").trim();
        if(clean.isEmpty()&&!parent.isBlank()&&!parent.equals(value)){Title parentTitle=title(parent+".mkv");clean=parentTitle.name();if(year.isEmpty())year=parentTitle.year();}
        if(episode>0&&year.isEmpty()&&!parent.isBlank()){Title container=title(parent+".mkv");if(TitleMatching.literal(container.name()).equals(TitleMatching.literal(clean)))year=container.year();}
        return new Title(cleanDisplayName(clean.isEmpty()?value:clean),year,season,episode);
    }

    private static int seasonNumber(String text) {
        if (text.charAt(0) >= '0' && text.charAt(0) <= '9') return Integer.parseInt(text);
        int tens = text.indexOf('十');
        if (tens == -1) return CHINESE_NUMBERS.indexOf(text.charAt(0));
        int first = tens == 0 ? 1 : CHINESE_NUMBERS.indexOf(text.charAt(0));
        int last = tens + 1 == text.length() ? 0 : CHINESE_NUMBERS.indexOf(text.charAt(tens + 1));
        return first * 10 + last;
    }

    static String cleanDisplayName(String name) {
        String clean = LEADING_RELEASE_TAG.matcher(name).replaceFirst("")
                .replace("&#39;", "'").replace("&#x27;", "'").replace("&apos;", "'")
                .replace("&amp;", "&").trim();
        return clean.isEmpty() ? name : clean;
    }

    static List<String> lookupNames(String name) {
        String clean = cleanDisplayName(name);
        Matcher split = HAN_FIRST.matcher(clean);
        if (!split.matches()) split = LATIN_FIRST.matcher(clean);
        return split.matches() ? List.of(split.group(1).trim(), split.group(2).trim(), clean) : List.of(clean);
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
