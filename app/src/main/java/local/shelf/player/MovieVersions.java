package local.shelf.player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

final class MovieVersions {
    private MovieVersions() {}

    static List<Catalog.Movie> collapse(List<Catalog.Movie> movies,
                                        ToIntFunction<Catalog.Movie> filmId,
                                        Comparator<Catalog.Movie> preference) {
        List<Catalog.Movie> cards = new ArrayList<>(movies.size());
        Map<Integer, Integer> positions = new HashMap<>();
        for (Catalog.Movie movie : movies) {
            int id = filmId.applyAsInt(movie);
            Integer position = id > 0 ? positions.putIfAbsent(id, cards.size()) : null;
            if (position == null) cards.add(movie);
            else if (preference.compare(movie, cards.get(position)) < 0) cards.set(position, movie);
        }
        return cards;
    }
}
