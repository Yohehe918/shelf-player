package local.shelf.player;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class LibraryRulesTest {
    @Test public void namedEpisode(){var t=Catalog.title("Dark.S02E03.1080p.mkv");assertEquals("Dark",t.name());assertEquals(2,t.season());assertEquals(3,t.episode());}
    @Test public void mixedEpisodeNamesKeepOneSeries(){assertEquals(SeriesIndex.group(new Catalog.Movie("a",Catalog.titleInPath("Show (2026)/Season 1/Show.S01E01.mkv"),null,null,List.of())),SeriesIndex.group(new Catalog.Movie("b",Catalog.titleInPath("Show (2026)/Season 1/S01E02.mkv"),null,null,List.of())));}
    @Test public void xEpisode(){var t=Catalog.title("Dark.2x03.mkv");assertEquals(2,t.season());assertEquals(3,t.episode());}
    @Test public void bracketEpisode(){assertEquals(7,Catalog.title("Show.[13x07].mkv").episode());}
    @Test public void noCodecEpisode(){assertFalse(Catalog.title("Film.2020.5.1x264.mkv").series());assertFalse(Catalog.title("Film.2020.x265.mkv").series());}
    @Test public void filenameWithoutShowUsesFolder(){var t=Catalog.titleInPath("TV\\Dark (2017)\\Season 02\\S02E03.mkv");assertEquals("Dark",t.name());assertEquals("2017",t.year());assertEquals(3,t.episode());}
    @Test public void episodeOnlyInSeason(){var t=Catalog.titleInPath("TV\\Dark\\Season 02\\E03.mkv");assertEquals("Dark",t.name());assertEquals(2,t.season());assertEquals(3,t.episode());}
    @Test public void chineseTitle(){assertEquals("漫长的季节",Catalog.title("漫长的季节.S01E02.1080p.mkv").name());}
    @Test public void yearInNameIsNotRelease(){var t=Catalog.title("Blade.Runner.2049.(2017).1080p.mkv");assertEquals("Blade Runner 2049",t.name());assertEquals("2017",t.year());}
    @Test public void futureNumberInTitle(){assertEquals("Blade Runner 2049",Catalog.title("Blade.Runner.2049.mkv").name());}
    @Test public void numericFilmTitle(){assertEquals("1917",Catalog.title("1917.(2019).mkv").name());assertFalse(Catalog.title("2001.A.Space.Odyssey.(1968).mkv").series());}
    @Test public void releaseOnlyUsesParent(){assertEquals("Dune",Catalog.titleInPath("Dune (2021)\\1080p.BluRay.x264-GROUP.mkv").name());}
    @Test public void rejectsBareNumberAsEpisode(){assertFalse(Catalog.title("127.Hours.2010.mkv").series());}
    @Test public void resumeBoundaries(){assertEquals(0,ResumePolicy.resume(29999,1000000,false));assertEquals(30000,ResumePolicy.resume(30000,1000000,false));assertEquals(0,ResumePolicy.resume(940000,1000000,false));assertEquals(939999,ResumePolicy.resume(939999,1000000,false));}
    @Test public void rewatchIsResumable(){assertEquals(40000,ResumePolicy.resume(40000,1000000,true));assertEquals(0,ResumePolicy.resume(800000,1000000,true));}
    @Test public void unknownDuration(){assertEquals(45000,ResumePolicy.resume(45000,0,false));assertEquals(0,ResumePolicy.resume(45000,0,true));assertFalse(ResumePolicy.complete(45000,0));}
    @Test public void normalizesWithoutLosingOtherScripts(){assertEquals("dune part 2",TitleMatching.literal("Dune: Part II"));assertEquals(TitleMatching.literal("Amélie"),TitleMatching.literal("Amelie"));assertEquals("你好 世界",TitleMatching.literal("你好·世界"));assertNotEquals(TitleMatching.literal("The Strays"),TitleMatching.literal("Strays"));}
    private Catalog.Movie m(String name){return new Catalog.Movie(name,Catalog.title(name),null,null,List.of());}
    @Test public void oneSeriesCardPerShow(){assertEquals(2,SeriesIndex.representatives(List.of(m("A.S01E01.mkv"),m("A.S01E02.mkv"),m("A.S02E01.mkv"),m("B.S01E01.mkv"))).size());}
    @Test public void nextSkipsAlternateEncode(){var first=m("A.S01E01.mkv");var next=m("A.S01E02.mkv");assertEquals(next,SeriesIndex.adjacent(List.of(first,m("A.S01E01.1080p.mkv"),next),first,true));}
    @Test public void nextCrossesSeasonsButNotShows(){var first=m("A.S01E10.mkv");var next=m("A.S02E01.mkv");assertEquals(next,SeriesIndex.adjacent(List.of(next,m("B.S01E11.mkv"),first),first,true));assertNull(SeriesIndex.adjacent(List.of(first,m("B.S01E11.mkv")),first,true));}
    @Test public void specialsAreNotAutoQueued(){var special=m("A.S00E01.mkv");assertNull(SeriesIndex.adjacent(List.of(special,m("A.S01E01.mkv")),special,true));}
    @Test public void previousNumericallySorted(){var last=m("A.S01E10.mkv");assertEquals(2,SeriesIndex.adjacent(List.of(last,m("A.S01E02.mkv"),m("A.S01E01.mkv")),last,false).title().episode());}
    @Test public void subtitleColorsSafeFallback(){assertEquals(SubtitleColors.argb("white"),SubtitleColors.argb("unknown"));assertEquals("white",SubtitleColors.keyAt(-1));assertEquals(8,SubtitleColors.count());}
}
