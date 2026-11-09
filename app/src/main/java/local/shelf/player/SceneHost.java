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

/** A visible scene with bounded, hidden grid pages and an optional outgoing transition. */
final class SceneHost extends FrameLayout {
    private Layer current, outgoing;
    private Motion.Spring transition;
    private int generation;
    private final Map<String,Place> places=new LinkedHashMap<>();
    private final Map<View,Layer> parkedGrids = new LinkedHashMap<>();

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
        boolean keepGrid = gridRoute(route) || route.startsWith("details:")
                || route.startsWith("series:") || video;
        if (!keepGrid) clearParkedGrids();
        Layer cached = parkedGrids.remove(screen);
        // A changed library revision can create new content for an existing route.
        var hidden = parkedGrids.values().iterator();
        while (hidden.hasNext()) {
            Layer layer = hidden.next();
            if (layer.route.equals(route)) {
                hidden.remove();
                removeView(layer);
            }
        }
        if (cached == null && screen.getParent() instanceof ViewGroup parent) parent.removeView(screen);
        Layer next = cached == null ? new Layer(getContext(),screen,route,depth,video) : cached;
        current=next;
        View initialFocus=screen.findFocus();
        boolean same=old!=null&&old.route.equals(route);
        boolean playback=old!=null&&(old.video||video);
        boolean gridSwitch=old!=null&&!playback&&(gridRoute(route)||gridRoute(old.route));
        boolean animate=old!=null&&!same&&!gridSwitch&&(Motion.enabled(getContext())||video);
        if (animate) {
            Motion.settle(old.content,true);
            deactivate(old);
        }
        boolean back=old!=null&&depth<old.depth;
        boolean modal=isModal(route)||(old!=null&&isModal(old.route)&&back);
        Rect origin=new Rect();
        View focused=animate?old.content.findFocus():null;
        if(focused!=null)focused.getGlobalVisibleRect(origin);

        if (cached == null) addView(next,new FrameLayout.LayoutParams(-1,-1));
        else {
            next.active = true;
            next.setDescendantFocusability(FOCUS_BEFORE_DESCENDANTS);
            next.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_AUTO);
            next.setVisibility(VISIBLE);
            next.bringToFront();
        }
        if(animate){
            outgoing=old;
            if(playback){next.setAlpha(1);if(video&&!old.video)old.bringToFront();}
            else next.setAlpha(0);
        } else if (old != null) {
            if (gridSwitch && keepGrid && !same && gridRoute(old.route)) parkGrid(old);
            else removeView(old);
        }
        Place restore=places.get(route);
        ViewTreeObserver observer = screen.getViewTreeObserver();
        observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){
            @Override public boolean onPreDraw(){
                if (observer.isAlive()) observer.removeOnPreDrawListener(this);
                if(ticket!=generation)return true;
                if(restore!=null)restore.apply(screen);
                else if(initialFocus!=null)initialFocus.requestFocus();
                else screen.requestFocus(FOCUS_DOWN);
                if (!gridSwitch) Motion.settle(screen,false);
                if(animate)start(old,next,back,modal,playback,origin,ticket);
                return true;
            }
        });
    }
    private void deactivate(Layer layer) {
        layer.active = false;
        layer.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        layer.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }

    private void parkGrid(Layer layer) {
        deactivate(layer);
        layer.setVisibility(INVISIBLE);
        parkedGrids.put(layer.content, layer);
        if (parkedGrids.size() > 2) removeView(parkedGrids.remove(parkedGrids.keySet().iterator().next()));
    }

    private void clearParkedGrids() {
        parkedGrids.values().forEach(this::removeView);
        parkedGrids.clear();
    }

    private static boolean gridRoute(String route) {
        return route.startsWith("grid:") || route.startsWith("season:");
    }

    void describeVideoLoading(String message) {
        if (current == null || !current.video || current.ready || outgoing == null) return;
        if (outgoing.getChildCount() > 1 && outgoing.getChildAt(1) instanceof android.widget.TextView label) {
            label.setText(message);
            return;
        }
        android.widget.TextView label = new android.widget.TextView(getContext());
        label.setText(message);
        label.setTextColor(0xfff5f4f5);
        label.setTextSize(15);
        label.setGravity(android.view.Gravity.CENTER);
        label.setBackgroundColor(0xdb121821);
        int padding = (int) Motion.dp(this, 12);
        label.setPadding(padding, padding, padding, padding);
        FrameLayout.LayoutParams size = new FrameLayout.LayoutParams(-2, -2,
                android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
        size.bottomMargin = (int) Motion.dp(this, 28);
        outgoing.addView(label, size);
    }

    void revealVideo(){
        if(current==null||!current.video)return;
        current.ready=true;
        if(outgoing!=null)start(outgoing,current,false,false,true,new Rect(),generation);
    }
    private void start(Layer old,Layer next,boolean back,boolean modal,boolean video,Rect origin,int ticket){
        if(next.started)return;
        if(next.video&&!next.ready){
            describeVideoLoading("正在读取 NAS 片源…");
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
    void close(){++generation;if(transition!=null)transition.stop();removeAllViews();current=null;outgoing=null;places.clear();parkedGrids.clear();}
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
        static Place capture(View root) {
            Place place = new Place();
            View focus = root.findFocus();
            if (focus != null) {
                place.focusPath = path(root, focus);
                place.focusKey = key(focus);
            }
            place.readScrolls(root, root);
            return place;
        }

        private void readScrolls(View root, View view) {
            if (view instanceof ScrollView || view instanceof HorizontalScrollView) {
                scrolls.put(path(root, view), new int[]{view.getScrollX(), view.getScrollY()});
            }
            if (view instanceof ViewGroup group) {
                for (int i = 0; i < group.getChildCount(); i++) readScrolls(root, group.getChildAt(i));
            }
        }

        private static String path(View root, View view) {
            StringBuilder result = new StringBuilder();
            while (view != root && view.getParent() instanceof ViewGroup group) {
                result.insert(0, "/" + group.indexOfChild(view));
                view = group;
            }
            return result.toString();
        }

        void apply(View root){
            View focus = focusPath == null ? null : at(root, focusPath);
            if (focusKey != null && (focus == null || !focusKey.equals(key(focus)))) {
                View matching = find(root, focusKey);
                if (matching != null) focus = matching;
            }
            if(focus!=null&&focus.isFocusable())focus.requestFocus();else root.requestFocus(FOCUS_DOWN);
            scrolls.forEach((path,point)->{View v=at(root,path);if(v instanceof ScrollView||v instanceof HorizontalScrollView)v.scrollTo(point[0],point[1]);});
        }
        static String key(View v){Object id=v.getTag(R.id.motion_identity);if(id!=null)return id.toString();if(v.getContentDescription()!=null)return "description:"+v.getContentDescription();return v instanceof TextView t?"text:"+t.getText():null;}
        static View find(View root,String key){if(key.equals(key(root))&&root.isFocusable())return root;if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View match=find(group.getChildAt(i),key);if(match!=null)return match;}return null;}
        static View at(View root,String path){try{View v=root;for(String part:path.split("/"))if(!part.isEmpty())v=((ViewGroup)v).getChildAt(Integer.parseInt(part));return v;}catch(RuntimeException e){return null;}}
    }
}
