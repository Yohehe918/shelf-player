package local.shelf.player;
import android.app.Instrumentation;
import android.app.Activity;
import android.os.Bundle;
import java.util.*;
/** Runs real SQLite/URI/Media3 contract checks on the television, without touching its library. */
public final class ShelfInstrumentation extends Instrumentation {
    private boolean motionChecks;
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);motionChecks="true".equals(arguments.getString("motion"));start();}
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    @Override public void onStart(){Bundle result=new Bundle();String db="shelf-verification-"+System.nanoTime()+".db";LibraryStore store=new LibraryStore(getTargetContext(),db);
        try{
            if(motionChecks){MotionChecks.run(this);ArtworkChecks.run(this);}
            var title=new Catalog.Title("测试剧集","2026",1,2);
            var movie=new Catalog.Movie("中文\\S01E02.mkv",title,"中文\\poster.jpg",null,List.of("中文\\S01E02.zh.srt"));
            store.saveLibrary("one",List.of(movie));require(store.library("one").equals(List.of(movie)),"Library and subtitles round trip");
            require(store.library("two").isEmpty(),"Source isolation");
            try{store.saveLibrary("one",Arrays.asList(movie,null));throw new AssertionError("Expected failed transaction");}catch(NullPointerException expected){}
            require(store.library("one").size()==1,"Failed scan must not wipe library");
            var automatic=new OnlineServices.Metadata("自动","简介",null,null,10,"tv","2026",8.2,"剧情",45);
            var corrected=new OnlineServices.Metadata("手动修正","准确简介","https://image.tmdb.org/t/p/w500/abc.jpg",null,20,"tv","2025",8.7,"科幻",50);
            store.put("one",movie.path(),automatic,false);store.put("one",movie.path(),corrected,true);store.put("one",movie.path(),automatic,false);
            require(store.metadata("one").get(movie.path()).equals(corrected),"Automatic lookup overwrote manual match");
            store.close();
            store.put("one",movie.path(),automatic,true);
            require(store.library("one").isEmpty() && store.metadata("one").isEmpty(),"Closed store reopened by late work");
            store=new LibraryStore(getTargetContext(),db);require(store.metadata("one").get(movie.path()).equals(corrected),"Correction did not survive reopen");
            require(store.library("one").equals(List.of(movie)),"Library did not survive reopen");
            require(store.metadata("two").isEmpty(),"Metadata source isolation");
            store.forget("one",List.of(movie.path()));require(store.metadata("one").isEmpty(),"Reset match failed");
            require(String.join("\\",Catalog.uri(movie.path()).getPathSegments()).equals(movie.path()),"SMB URI Unicode round trip");
            var atmos=new androidx.media3.common.Format.Builder().setSampleMimeType(androidx.media3.common.MimeTypes.AUDIO_E_AC3_JOC).build();
            require("E-AC-3 Atmos".equals(TrackNames.codec(atmos)),"Media3 format integration");
            if(getTargetContext().getPackageName().endsWith(".qa"))getTargetContext().getSharedPreferences("shelf",0).edit().putString("address","127.0.0.1:1446").putString("share","QA").putString("user","").commit();
            result.putString("stream",(motionChecks?"PASS: 11 motion/navigation checks and artwork coalescing, variant isolation, completion cleanup.\n":"")+"PASS: 12 device checks; SQLite transactions, restart, source isolation, manual-match protection, URI and Media3.\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable e){result.putString("stream","FAIL: "+e+"\n");finish(Activity.RESULT_CANCELED,result);}
        finally{store.close();getTargetContext().deleteDatabase(db);}
    }
}
