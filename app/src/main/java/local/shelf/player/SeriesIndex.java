package local.shelf.player;
import java.util.*;
import java.util.function.Predicate;
/** Local SMB equivalent of Kinema's title/season/episode hierarchy. */
final class SeriesIndex {
    static String group(Catalog.Movie movie){return TitleMatching.literal(movie.title().name())+"|"+movie.title().year();}
    static final Comparator<Catalog.Movie> ORDER=Comparator.comparingInt((Catalog.Movie m)->m.title().season()).thenComparingInt(m->m.title().episode()).thenComparing(Catalog.Movie::path);
    static List<Catalog.Movie> episodes(List<Catalog.Movie> all,Catalog.Movie movie){return all.stream().filter(m->m.title().series()&&group(m).equals(group(movie))).sorted(ORDER).toList();}
    static List<Catalog.Movie> representatives(List<Catalog.Movie> all){Map<String,Catalog.Movie> groups=new LinkedHashMap<>();for(Catalog.Movie m:all.stream().sorted(ORDER).toList())if(m.title().series())groups.putIfAbsent(group(m),m);return new ArrayList<>(groups.values());}
    static Catalog.Movie adjacent(List<Catalog.Movie> all,Catalog.Movie current,boolean next){
        if(!current.title().series())return null;
        // Alternate encodes of one episode are not another episode. Specials don't auto-queue into regular seasons.
        return episodes(all,current).stream().filter(m->(m.title().season()==0)==(current.title().season()==0))
            .filter(m->next?numberCompare(m,current)>0:numberCompare(m,current)<0)
            .sorted(next?ORDER:ORDER.reversed()).findFirst().orElse(null);
    }
    static int numberCompare(Catalog.Movie a,Catalog.Movie b){int season=Integer.compare(a.title().season(),b.title().season());return season!=0?season:Integer.compare(a.title().episode(),b.title().episode());}
    static Catalog.Movie firstUnwatched(List<Catalog.Movie> all,Catalog.Movie current,Predicate<Catalog.Movie> watched){return episodes(all,current).stream().filter(m->!watched.test(m)).findFirst().orElse(current);}
}
