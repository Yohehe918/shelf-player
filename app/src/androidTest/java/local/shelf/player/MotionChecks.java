package local.shelf.player;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/** Device contracts for interrupted transitions and focus/scroll continuity. Uses QA only. */
final class MotionChecks {
    static void run(Instrumentation test){
        if(!test.getTargetContext().getPackageName().endsWith(".qa"))throw new AssertionError("Motion checks require QA identity");
        var prefs=test.getTargetContext().getSharedPreferences("shelf",0);
        boolean reduced=prefs.getBoolean("reduceMotion",false);
        prefs.edit().putBoolean("reduceMotion",false).commit();
        MainActivity activity=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        test.setInTouchMode(false);
        SceneHost[] host=new SceneHost[1];ScrollView[] original=new ScrollView[1];int[] scroll=new int[1];
        try{
            main(test,()->{host[0]=new SceneHost(activity);activity.setContentView(host[0]);original[0]=page(activity);host[0].show(original[0],"home",0,false);});
            test.waitForIdleSync();SystemClock.sleep(180);
            main(test,()->{LinearLayout rows=(LinearLayout)original[0].getChildAt(0);rows.getChildAt(15).requestFocus();original[0].scrollTo(0,900);scroll[0]=original[0].getScrollY();host[0].show(page(activity),"details",4,false);});
            SystemClock.sleep(950);test.waitForIdleSync();
            ScrollView[] returned=new ScrollView[1];
            main(test,()->{returned[0]=page(activity);host[0].show(returned[0],"home",0,false);});
            SystemClock.sleep(950);test.waitForIdleSync();
            main(test,()->{
                require(host[0].getChildCount()==1,"Transition leaked layers");
                require(returned[0].findFocus()!=null&&"row:15".equals(returned[0].findFocus().getTag(R.id.motion_identity)),"Back lost focused row");
                require(returned[0].getScrollY()==scroll[0],"Back lost scroll position");
                host[0].show(page(activity),"a",1,false);
            });
            SystemClock.sleep(80);
            main(test,()->{host[0].show(page(activity),"b",2,false);host[0].show(page(activity),"c",1,false);require(host[0].getChildCount()<=2,"Interrupted transitions accumulated layers");});
            SystemClock.sleep(950);test.waitForIdleSync();
            main(test,()->{require("c".equals(host[0].route())&&host[0].getChildCount()==1,"Stale completion removed live page");require(host[0].getChildAt(0).getAlpha()==1f&&host[0].getChildAt(0).getScaleX()==1f,"Page did not settle");});
            prefs.edit().putBoolean("reduceMotion",true).commit();
            main(test,()->{host[0].show(page(activity),"reduced",2,false);require(host[0].getChildCount()==1&&host[0].getChildAt(0).getAlpha()==1f,"Reduced motion animated page");});
            prefs.edit().putBoolean("reduceMotion",false).commit();
            main(test,()->host[0].show(page(activity),"playback",5,true));
            SystemClock.sleep(100);
            main(test,()->{View video=host[0].getChildAt(0);require(video.getAlpha()==1f&&video.getScaleX()==1f&&video.getTranslationX()==0,"Video surface was transformed");});
            main(test,()->{require(host[0].getChildCount()==2,"Loading video lost its cover");host[0].revealVideo();});
            SystemClock.sleep(950);test.waitForIdleSync();
            main(test,()->{require(host[0].getChildCount()==1,"Playback transition leaked old layer");host[0].show(page(activity),"exitVideo",4,false);});
            SystemClock.sleep(180);test.waitForIdleSync();
            main(test,()->require(host[0].getChildCount()==1&&"exitVideo".equals(host[0].route()),"Released video surface remained in exit transition"));
        }finally{prefs.edit().putBoolean("reduceMotion",reduced).commit();main(test,()->{if(host[0]!=null)host[0].close();activity.finish();});}
    }
    private static ScrollView page(MainActivity context){ScrollView scroll=new ScrollView(context);LinearLayout rows=new LinearLayout(context);rows.setOrientation(LinearLayout.VERTICAL);scroll.addView(rows);for(int n=0;n<30;n++){Button button=new Button(context);button.setText("Row "+n);Motion.identity(button,"row:"+n);rows.addView(button,new LinearLayout.LayoutParams(-1,100));}return scroll;}
    private static void main(Instrumentation test,Runnable work){Throwable[] error=new Throwable[1];test.runOnMainSync(()->{try{work.run();}catch(Throwable t){error[0]=t;}});if(error[0]!=null)throw new AssertionError(error[0]);}
    private static void require(boolean condition,String why){if(!condition)throw new AssertionError(why);}
}
