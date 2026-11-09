package local.shelf.player;
import java.util.*;
import java.util.function.Function;
/** Local SMB equivalent of Kinema's title/season/episode hierarchy. */
final class SeriesIndex {
    static String group(Catalog.Movie movie){return TitleMatching.literal(movie.title().name())+"|"+movie.title().year();}
    static final Comparator<Catalog.Movie> ORDER=Comparator.comparingInt((Catalog.Movie m)->m.title().season()).thenComparingInt(m->m.title().episode()).thenComparing(Catalog.Movie::path);
    static List<Catalog.Movie> episodes(List<Catalog.Movie> all,Catalog.Movie movie) {
        return episodes(all, movie, defaultGrouping());
    }

    static List<Catalog.Movie> episodes(List<Catalog.Movie> all, Catalog.Movie movie,
                                        Function<Catalog.Movie, String> grouping) {
        String target = grouping.apply(movie);
        return all.stream().filter(m -> m.title().series() && grouping.apply(m).equals(target))
                .sorted(ORDER).toList();
    }

    static List<Catalog.Movie> representatives(List<Catalog.Movie> all,
                                               Function<Catalog.Movie, String> grouping) {
        Map<String, Catalog.Movie> groups = new LinkedHashMap<>();
        for (Catalog.Movie movie : all) {
            if (!movie.title().series()) continue;
            groups.merge(grouping.apply(movie), movie,
                    (first, second) -> ORDER.compare(first, second) <= 0 ? first : second);
        }
        return groups.values().stream().sorted(ORDER).toList();
    }

    static List<Catalog.Movie> representatives(List<Catalog.Movie> all) {
        return representatives(all, defaultGrouping());
    }

    private static Function<Catalog.Movie, String> defaultGrouping() {
        Map<String, String> normalized = new HashMap<>();
        return movie -> normalized.computeIfAbsent(movie.title().name(), TitleMatching::literal)
                + "|" + movie.title().year();
    }

    static Catalog.Movie adjacent(List<Catalog.Movie> all, Catalog.Movie current, boolean next) {
        return adjacent(all, current, next, defaultGrouping());
    }

    static Catalog.Movie adjacent(List<Catalog.Movie> all, Catalog.Movie current, boolean next,
                                  Function<Catalog.Movie, String> grouping) {
        if (!current.title().series()) return null;
        // Alternate encodes of one episode are not another episode. Keep specials separate.
        var candidates = episodes(all, current, grouping).stream()
                .filter(movie -> (movie.title().season() == 0) == (current.title().season() == 0))
                .filter(movie -> next ? numberCompare(movie, current) > 0 : numberCompare(movie, current) < 0);
        return candidates.min(next ? ORDER : ORDER.reversed()).orElse(null);
    }
    static int numberCompare(Catalog.Movie a,Catalog.Movie b){int season=Integer.compare(a.title().season(),b.title().season());return season!=0?season:Integer.compare(a.title().episode(),b.title().episode());}
}
