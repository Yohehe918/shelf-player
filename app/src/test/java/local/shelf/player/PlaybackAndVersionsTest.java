package local.shelf.player;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.*;

public class PlaybackAndVersionsTest {
    @Test public void confirmedVersionsCollapseButDifferentIdsAndUnknownFilesRemain() {
        var disc = new Catalog.Movie("Film\\BDMV\\STREAM\\00001.m2ts", Catalog.title("Film.mkv"),
                null, null, List.of(), "Film");
        var file = movie("Film.mkv");
        var remake = movie("Elsewhere\\Film.mkv");
        var unknown = movie("Other\\Film.mp4");
        var ids = Map.of(disc, 101, file, 101, remake, 102);
        assertEquals(List.of(file, remake, unknown),
                MovieVersions.collapse(List.of(disc, file, remake, unknown),
                        m -> ids.getOrDefault(m, 0), Comparator.comparing(Catalog.Movie::bluRay)));
    }

    private static Catalog.Movie movie(String path) {
        return new Catalog.Movie(path, Catalog.title("Film.mkv"), null, null, List.of());
    }
}
