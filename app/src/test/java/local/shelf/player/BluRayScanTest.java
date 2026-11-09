package local.shelf.player;

import org.junit.Test;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class BluRayScanTest {
    @Test public void oneDiscCardUsesFolderTitleAndMainStream() {
        String folder = "Inception (2010) 2160p";
        String bdmv = folder + "\\bdmv";
        String stream = bdmv + "\\StReAm";
        Map<String, List<SmbMedia.Entry>> tree = Map.of(
                "", List.of(dir(folder), file("Standalone.m2ts", 100)),
                folder, List.of(dir("bdmv"), file("poster.jpg", 5), file("fanart.webp", 5)),
                bdmv, List.of(dir("StReAm"), dir("BACKUP")),
                stream, List.of(file("00001.m2ts", 20), file("00003.M2TS", 1000),
                        file("00002.m2ts", 1000), file("00002.zh.srt", 10)));

        List<Catalog.Movie> movies = SmbMedia.index(tree::get);
        assertEquals(2, movies.size());
        Catalog.Movie movie = movies.get(0);
        assertEquals(new Catalog.Title("Inception", "2010", 0, 0), movie.title());
        assertEquals(stream + "\\00002.m2ts", movie.path());
        assertEquals(folder + "\\poster.jpg", movie.poster());
        assertEquals(folder + "\\fanart.webp", movie.backdrop());
        assertEquals(List.of(stream + "\\00002.zh.srt"), movie.subtitles());
        assertEquals("@bluray:" + folder, movie.metadataKey());
        assertFalse(movies.get(1).bluRay());
    }

    @Test public void identifiesOldDiscClipsWithoutHidingStandaloneM2ts() {
        assertEquals("Films\\Title", Catalog.bluRayFolder("Films\\Title\\BdMv\\Stream\\00001.m2ts"));
        assertEquals("", Catalog.bluRayFolder("BDMV\\STREAM\\00001.m2ts"));
        assertNull(Catalog.bluRayFolder("Films\\00001.m2ts"));
        assertNull(Catalog.bluRayFolder("MYBDMV\\STREAM\\00001.m2ts"));
        assertEquals("Title", Catalog.discTitle("Title (2021)\\DISC 2").name());
    }

    @Test public void seasonDiscsUseShowMetadataWithoutInventingEpisodes() {
        String folder = "Halo S01 光环 2160p\\DISC2";
        Catalog.Movie disc = new Catalog.Movie(folder + "\\BDMV\\STREAM\\00001.m2ts",
                Catalog.discTitle(folder), null, null, List.of(), folder);
        assertEquals("Halo", disc.title().name());
        assertFalse(disc.title().series());
        assertEquals("2", disc.discNumber());
        assertEquals(new Catalog.Title("Halo", "", 1, 1), disc.metadataTitle());
        assertEquals(new Catalog.Title("The Americans", "", 1, 13),
                Catalog.discTitle("The Americans S01 美国谍梦 1080p\\E13-The Colonel"));
    }

    private static SmbMedia.Entry dir(String name) { return new SmbMedia.Entry(name, true, 0); }
    private static SmbMedia.Entry file(String name, long size) { return new SmbMedia.Entry(name, false, size); }
}
