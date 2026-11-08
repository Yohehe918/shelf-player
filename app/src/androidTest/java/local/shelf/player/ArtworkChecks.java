package local.shelf.player;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.SystemClock;
import android.widget.ImageView;
import android.widget.LinearLayout;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

/** Exercises real disk-cache decode and sharing without network credentials. QA package only. */
final class ArtworkChecks {
    static void run(Instrumentation test) throws Exception {
        if(!test.getTargetContext().getPackageName().endsWith(".qa"))throw new AssertionError("QA only");
        var prefs=test.getTargetContext().getSharedPreferences("shelf",0);
        String address=prefs.getString("address","");prefs.edit().remove("address").commit();
        MainActivity activity=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        SmbMedia.Source source=new SmbMedia.Source("artwork.invalid",445,"QA","","",new char[0]);
        String asset="artwork-check.png",key=source.identity()+"|"+asset;
        Bitmap bitmap=Bitmap.createBitmap(64,96,Bitmap.Config.ARGB_8888);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,bytes);bitmap.recycle();
        ArtCache.write(activity.getCacheDir(),key,bytes.toByteArray());
        var sourceField=MainActivity.class.getDeclaredField("source");sourceField.setAccessible(true);
        var pending=MainActivity.class.getDeclaredField("pendingArt");pending.setAccessible(true);
        var loader=MainActivity.class.getDeclaredMethod("loadArt",Catalog.Movie.class,boolean.class,ImageView.class);loader.setAccessible(true);
        var movie=new Catalog.Movie("test.mkv",new Catalog.Title("Test","",0,0),asset,asset,List.of());
        ImageView[] views=new ImageView[3];Throwable[] failure=new Throwable[1];
        try {
            test.runOnMainSync(()->{try{
                sourceField.set(activity,source);LinearLayout row=new LinearLayout(activity);
                for(int i=0;i<views.length;i++){views[i]=new ImageView(activity);row.addView(views[i],new LinearLayout.LayoutParams(100,150));}
                activity.setContentView(row);
                loader.invoke(activity,movie,false,views[0]);loader.invoke(activity,movie,false,views[1]);
                if(((Map<?,?>)pending.get(activity)).size()!=1)throw new AssertionError("Duplicate poster jobs");
                loader.invoke(activity,movie,true,views[2]);
            }catch(Throwable error){failure[0]=error;}});
            if(failure[0]!=null)throw new AssertionError(failure[0]);
            boolean[] done={false};
            for(int i=0;i<50&&!done[0];i++){SystemClock.sleep(100);test.runOnMainSync(()->done[0]=views[0].getDrawable()!=null&&views[1].getDrawable()!=null&&views[2].getDrawable()!=null);}
            test.runOnMainSync(()->{try{
                if(!done[0])throw new AssertionError("Cached artwork did not reach attached views");
                Bitmap first=((BitmapDrawable)views[0].getDrawable()).getBitmap();
                if(first!=((BitmapDrawable)views[1].getDrawable()).getBitmap())throw new AssertionError("Poster decoded twice");
                if(first==((BitmapDrawable)views[2].getDrawable()).getBitmap())throw new AssertionError("Wide decode reused poster variant");
                if(!((Map<?,?>)pending.get(activity)).isEmpty())throw new AssertionError("Completed request retained views");
            }catch(Throwable error){failure[0]=error;}});
            if(failure[0]!=null)throw new AssertionError(failure[0]);
        }finally{test.runOnMainSync(activity::finish);prefs.edit().putString("address",address).commit();}
    }
}
