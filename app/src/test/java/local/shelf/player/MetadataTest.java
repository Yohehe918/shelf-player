package local.shelf.player;
import org.junit.Test;
import org.json.*;
import static org.junit.Assert.*;
public class MetadataTest {
    private JSONObject movie(int id,String title,String year)throws Exception{return new JSONObject().put("id",id).put("title",title).put("original_title",title).put("release_date",year+"-01-01").put("poster_path","/poster.jpg");}
    @Test public void rejectsAmbiguousMatches()throws Exception {assertNull(OnlineServices.select(new Catalog.Title("Dune","",0,0),new JSONArray().put(movie(1,"Dune","1984")).put(movie(2,"Dune","2021"))));}
    @Test public void yearResolvesRemakes()throws Exception {assertEquals(2,OnlineServices.select(new Catalog.Title("Dune","2021",0,0),new JSONArray().put(movie(1,"Dune","1984")).put(movie(2,"Dune","2021"))).id());}
    @Test public void keepsArticlesToAvoidFalseMatch()throws Exception {assertNull(OnlineServices.select(new Catalog.Title("Strays","",0,0),new JSONArray().put(movie(1,"The Strays","2023"))));}
    @Test public void handlesOriginalTitle()throws Exception {assertEquals(1,OnlineServices.select(new Catalog.Title("Dune","",0,0),new JSONArray().put(movie(1,"沙丘","2021").put("original_title","Dune"))).id());}
    @Test public void chineseShowAndDate()throws Exception {var m=OnlineServices.select(new Catalog.Title("漫长的季节","2023",1,1),new JSONArray().put(new JSONObject().put("id",1).put("name","漫长的季节").put("first_air_date","2023-04-22")));assertEquals("tv",m.type());assertEquals("2023",m.year());}
    @Test public void preservesNullImagesInCache()throws Exception {var m=new OnlineServices.Metadata("title","plot",null,null,12,"movie","2024",8,"剧情",90);assertEquals(m,OnlineServices.Metadata.fromJson(m.json()));}
    @Test public void emptyResultIsNotAMatch()throws Exception {assertNull(OnlineServices.select(new Catalog.Title("Missing","",0,0),new JSONArray()));}
}
