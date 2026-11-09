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
    private static final Pattern SEASON_PACK = Pattern.compile("(?i)^(.+?)[ ._-]+S([0-9]{1,2})(?:[ ._-]|$)");
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
            if (!bluRay()) return "";
            Matcher disc = DISC_NUMBER.matcher(lastComponent(discFolder));
            return disc.find() ? disc.group(1) : "";
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
        return new Title(clean.isEmpty()?value:clean,year,season,episode);
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
