package com.gathin.tvhome;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.View;

/** A single low-resolution snapshot per presentation, shared by all controls.
 * Samples screen-aligned backdrop pixels; no live blur or per-frame allocation. */
final class FrostedSurface extends Drawable {
    private final View owner;
    private final Bitmap backdrop;
    private final float radius;
    private final boolean selectable;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final RectF bounds=new RectF();
    private final Rect crop=new Rect();
    private final Path clip=new Path();
    private final int[] location=new int[2];
    private boolean focused;
    FrostedSurface(View owner,Bitmap backdrop,float radius,boolean selectable){
        this.owner=owner;this.backdrop=backdrop;this.radius=TvStyle.dp(owner.getContext(),radius);this.selectable=selectable;
    }
    static Bitmap capture(View root){
        int w=Math.max(1,root.getWidth()/8),h=Math.max(1,root.getHeight()/8);
        Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(bitmap);c.scale(w/(float)Math.max(1,root.getWidth()),h/(float)Math.max(1,root.getHeight()));root.draw(c);
        blur(bitmap,3);return bitmap;
    }
    static Bitmap scene(android.content.Context context,int width,int height){
        int w=Math.max(1,width/8),h=Math.max(1,height/8);Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);
        if(context.getSharedPreferences(Config.SETTINGS_NAME,0).getBoolean("scene_off",false)){c.drawColor(0xFF111319);return b;}
        BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=4;
        Bitmap source=BitmapFactory.decodeResource(context.getResources(),R.drawable.tvos_landscape,o);
        float scale=Math.max(w/(float)source.getWidth(),h/(float)source.getHeight());float x=(w-source.getWidth()*scale)/2,y=(h-source.getHeight()*scale)/2;
        c.drawBitmap(source,null,new RectF(x,y,x+source.getWidth()*scale,y+source.getHeight()*scale),new Paint(Paint.FILTER_BITMAP_FLAG));source.recycle();blur(b,5);return b;
    }
    /** Two separable box passes. O(width*height*radius), on a 240x135 snapshot only. */
    private static void blur(Bitmap b,int r){
        int w=b.getWidth(),h=b.getHeight();int[] a=new int[w*h],tmp=new int[a.length];b.getPixels(a,0,w,0,0,w,h);
        for(int pass=0;pass<2;pass++){
            for(int y=0;y<h;y++)for(int x=0;x<w;x++){int red=0,green=0,blue=0;for(int i=-r;i<=r;i++){int p=a[y*w+Math.max(0,Math.min(w-1,x+i))];red+=(p>>16)&255;green+=(p>>8)&255;blue+=p&255;}int n=2*r+1;tmp[y*w+x]=0xff000000|(red/n)<<16|(green/n)<<8|blue/n;}
            for(int y=0;y<h;y++)for(int x=0;x<w;x++){int red=0,green=0,blue=0;for(int i=-r;i<=r;i++){int p=tmp[Math.max(0,Math.min(h-1,y+i))*w+x];red+=(p>>16)&255;green+=(p>>8)&255;blue+=p&255;}int n=2*r+1;a[y*w+x]=0xff000000|(red/n)<<16|(green/n)<<8|blue/n;}
        }b.setPixels(a,0,w,0,0,w,h);
    }
    @Override public boolean isStateful(){return selectable;}
    @Override protected boolean onStateChange(int[] states){boolean next=false;for(int s:states)if(s==android.R.attr.state_focused)next=true;if(next==focused)return false;focused=next;invalidateSelf();return true;}
    @Override public void draw(Canvas c){
        bounds.set(getBounds());clip.reset();clip.addRoundRect(bounds,radius,radius,Path.Direction.CW);int save=c.save();c.clipPath(clip);
        if(backdrop!=null){owner.getLocationOnScreen(location);float sx=backdrop.getWidth()/(float)owner.getResources().getDisplayMetrics().widthPixels,sy=backdrop.getHeight()/(float)owner.getResources().getDisplayMetrics().heightPixels;
            crop.set(Math.max(0,Math.round((location[0]+bounds.left)*sx)),Math.max(0,Math.round((location[1]+bounds.top)*sy)),Math.min(backdrop.getWidth(),Math.round((location[0]+bounds.right)*sx)),Math.min(backdrop.getHeight(),Math.round((location[1]+bounds.bottom)*sy)));
            if(crop.width()>0&&crop.height()>0)c.drawBitmap(backdrop,crop,bounds,paint);
        }
        paint.setColor(focused?0xEDF6F7FA:0x591E2935);c.drawRect(bounds,paint);c.restoreToCount(save);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(TvStyle.dp(owner.getContext(),.65f));paint.setColor(focused?0xFFFFFFFF:0x55FFFFFF);bounds.inset(1,1);c.drawRoundRect(bounds,radius,radius,paint);paint.setStyle(Paint.Style.FILL);
    }
    @Override public void setAlpha(int a){} @Override public void setColorFilter(ColorFilter f){} @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
