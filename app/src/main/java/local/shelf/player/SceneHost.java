package local.shelf.player;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.LinkedHashMap;
import java.util.Map;

/** Two live layers at most; navigation remains interactive while the transition settles. */
final class SceneHost extends FrameLayout {
    private Layer current, outgoing;
    private Motion.Spring transition;
    private int generation;
    private final Map<String,Place> places=new LinkedHashMap<>();

    SceneHost(Context context){
        super(context);setId(R.id.motion_host);setBackgroundColor(0xff14212c);
        setClipChildren(false);setClipToPadding(false);
    }
    String route(){return current==null?"":current.route;}
    void show(View screen,String route,int depth,boolean video){
        Layer old=current;
        if(old!=null){
            places.put(old.route,Place.capture(old.content));
            if(places.size()>32)places.remove(places.keySet().iterator().next());
        }
        int ticket=++generation;
        if(transition!=null){transition.stop();transition=null;}
        if(outgoing!=null){removeView(outgoing);outgoing=null;}
        Layer next=new Layer(getContext(),screen,route,depth,video);
        current=next;
        View initialFocus=screen.findFocus();
        boolean same=old!=null&&old.route.equals(route);
        boolean animate=old!=null&&!same&&(Motion.enabled(getContext())||video);
        boolean playback=old!=null&&(old.video||video);
        boolean gridTransition=animate&&!playback&&(gridRoute(route)||gridRoute(old.route));
        if(old!=null&&!gridTransition){Motion.settle(old.content,true);old.active=false;old.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);old.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);}
        boolean back=old!=null&&depth<old.depth;
        boolean modal=isModal(route)||(old!=null&&isModal(old.route)&&back);
        Rect origin=new Rect();
        View focused=old==null?null:old.content.findFocus();
        if(focused!=null)focused.getGlobalVisibleRect(origin);
        addView(next,new FrameLayout.LayoutParams(-1,-1));
        if(gridTransition){
            removeView(old);
            next.setTranslationX(Motion.dp(this,back?-12:12));
        }else if(animate){
            outgoing=old;
            if(playback){next.setAlpha(1);if(video&&!old.video)old.bringToFront();}
            else next.setAlpha(0);
        }else if(old!=null)removeView(old);
        Place restore=places.get(route);
        screen.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){
            @Override public boolean onPreDraw(){
                screen.getViewTreeObserver().removeOnPreDrawListener(this);
                if(ticket!=generation)return true;
                if(restore!=null)restore.apply(screen);
                else if(initialFocus!=null)initialFocus.requestFocus();
                else screen.requestFocus(FOCUS_DOWN);
                Motion.settle(screen,false);
                if(old!=null&&!gridTransition)old.content.jumpDrawablesToCurrentState();
                if(gridTransition)startGrid(next,back,ticket);
                else if(animate)start(old,next,back,modal,playback,origin,ticket);
                return true;
            }
        });
    }
    private static boolean gridRoute(String route) {
        return route.startsWith("grid:") || route.startsWith("season:");
    }

    private void startGrid(Layer next, boolean back, int ticket) {
        float distance = Motion.dp(this, back ? -12 : 12);
        transition = new Motion.Spring(0, 340, 1f, fraction -> {
            if (ticket != generation) return;
            float progress = Math.min(1, fraction);
            next.setTranslationX(distance * (1 - progress));
        });
        transition.to(1, true);
    }

    void revealVideo(){
        if(current==null||!current.video)return;
        current.ready=true;
        if(outgoing!=null)start(outgoing,current,false,false,true,new Rect(),generation);
    }
    private void start(Layer old,Layer next,boolean back,boolean modal,boolean video,Rect origin,int ticket){
        if(next.started)return;
        if(next.video&&!next.ready){
            if(old.getChildCount()==1){
                android.widget.ProgressBar loading=new android.widget.ProgressBar(getContext(),null,android.R.attr.progressBarStyleSmall);
                loading.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(0xfff5f4f5));
                FrameLayout.LayoutParams size=new FrameLayout.LayoutParams((int)Motion.dp(this,24),(int)Motion.dp(this,24),android.view.Gravity.BOTTOM|android.view.Gravity.CENTER_HORIZONTAL);
                size.bottomMargin=(int)Motion.dp(this,28);old.addView(loading,size);
            }
            return;
        }
        next.started=true;
        if(!Motion.enabled(getContext())){finish(old,next,ticket);return;}
        final float oldAlpha=old.getAlpha(),oldX=old.getTranslationX(),oldY=old.getTranslationY(),oldScale=old.getScaleX();
        if(video){
            // Never transform a SurfaceView, including on exit after the player is released.
            if(old.video){removeView(old);outgoing=null;}
            transition=new Motion.Spring(0,300,1f,p->{
                if(ticket!=generation)return;
                float t=Math.max(0,Math.min(1,p));
                if(!old.video)old.setAlpha(oldAlpha*(1-t));
                else if(!next.video){next.setAlpha(.8f+.2f*t);next.setScaleX(.992f+.008f*t);next.setScaleY(next.getScaleX());}
                if(t>=.999f)finish(old,next,ticket);
            });
        }else{
            int[] location=new int[2];getLocationOnScreen(location);
            float pivotX=origin.isEmpty()?getWidth()*.5f:origin.exactCenterX()-location[0];
            float pivotY=origin.isEmpty()?getHeight()*.5f:origin.exactCenterY()-location[1];
            next.setPivotX(pivotX);next.setPivotY(pivotY);
            float distance=Motion.dp(this,modal?24:28);
            float initialScale=back?1.018f:.965f;
            next.setScaleX(initialScale);next.setScaleY(initialScale);
            if(modal)next.setTranslationY(back?-distance*.35f:distance);
            else next.setTranslationX(back?-distance:distance);
            next.setLayerType(LAYER_TYPE_HARDWARE,null);
            if(!old.video)old.setLayerType(LAYER_TYPE_HARDWARE,null);
            transition=new Motion.Spring(0,230,1f,p->{
                if(ticket!=generation)return;
                float t=Math.max(0,Math.min(1,p)),remain=1-t;
                next.setAlpha(Math.min(1,t*1.5f));
                next.setScaleX(1+(initialScale-1)*remain);next.setScaleY(next.getScaleX());
                next.setTranslationX(modal?0:(back?-distance:distance)*remain);
                next.setTranslationY(modal?(back?-distance*.35f:distance)*remain:0);
                old.setAlpha(oldAlpha);
                old.setTranslationX(oldX*(1-t)+(modal?0:(back?distance:-distance*.45f))*t);
                old.setTranslationY(oldY*(1-t)+(modal?(back?distance:-distance*.25f):0)*t);
                old.setScaleX(oldScale+((back?.98f:.99f)-oldScale)*t);old.setScaleY(old.getScaleX());
                // Once the new page is opaque, stop blending two full TV-sized textures.
                if(t>=.70f){
                    if(outgoing==old){removeView(old);outgoing=null;}
                    next.setLayerType(LAYER_TYPE_NONE,null);
                }
                if(t>=.999f)finish(old,next,ticket);
            });
        }
        transition.to(1,true);
    }
    private void finish(Layer old,Layer next,int ticket){
        if(ticket!=generation)return;
        next.setAlpha(1);next.setScaleX(1);next.setScaleY(1);next.setTranslationX(0);next.setTranslationY(0);
        next.setLayerType(LAYER_TYPE_NONE,null);
        if(outgoing==old){removeView(old);outgoing=null;}
        // Keep only the live page; stale callbacks cannot remove a later page.
    }
    void close(){++generation;if(transition!=null)transition.stop();removeAllViews();current=null;outgoing=null;places.clear();}
    @Override protected void onDetachedFromWindow(){if(transition!=null)transition.stop();super.onDetachedFromWindow();}
    private static boolean isModal(String route){return route.equals("settings")||route.equals("connect")||route.equals("services");}
    private static final class Layer extends FrameLayout {
        final View content;final String route;final int depth;final boolean video;boolean active=true,ready,started;
        @Override public boolean dispatchTouchEvent(android.view.MotionEvent event){return active&&super.dispatchTouchEvent(event);}
        Layer(Context c,View content,String route,int depth,boolean video){super(c);this.content=content;this.route=route;this.depth=depth;this.video=video;setClickable(true);setFocusable(false);setClipChildren(false);setClipToPadding(false);addView(content,new LayoutParams(-1,-1));}
    }
    private static final class Place {
        String focusPath,focusKey;
        final Map<String,int[]> scrolls=new LinkedHashMap<>();
        static Place capture(View root){Place place=new Place();place.read(root,"");return place;}
        private void read(View v,String path){
            if(v.isFocused()){focusPath=path;focusKey=key(v);}
            if(v instanceof ScrollView||v instanceof HorizontalScrollView)scrolls.put(path,new int[]{v.getScrollX(),v.getScrollY()});
            if(v instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)read(group.getChildAt(i),path+"/"+i);
        }
        void apply(View root){
            View focus=focusKey==null?null:find(root,focusKey);
            if(focus==null&&focusPath!=null)focus=at(root,focusPath);
            if(focus!=null&&focus.isFocusable())focus.requestFocus();else root.requestFocus(FOCUS_DOWN);
            scrolls.forEach((path,point)->{View v=at(root,path);if(v instanceof ScrollView||v instanceof HorizontalScrollView)v.scrollTo(point[0],point[1]);});
        }
        static String key(View v){Object id=v.getTag(R.id.motion_identity);if(id!=null)return id.toString();if(v.getContentDescription()!=null)return "description:"+v.getContentDescription();return v instanceof TextView t?"text:"+t.getText():null;}
        static View find(View root,String key){if(key.equals(key(root))&&root.isFocusable())return root;if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View match=find(group.getChildAt(i),key);if(match!=null)return match;}return null;}
        static View at(View root,String path){try{View v=root;for(String part:path.split("/"))if(!part.isEmpty())v=((ViewGroup)v).getChildAt(Integer.parseInt(part));return v;}catch(RuntimeException e){return null;}}
    }
}
