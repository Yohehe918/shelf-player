package local.shelf.player;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.TextView;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/** Shelf motion language: continuous focus springs, restrained travel and interruptible feedback. */
final class Motion {
    static final PathInterpolator EASE = new PathInterpolator(.2f, .8f, .2f, 1f);
    private Motion() {}

    static boolean enabled(Context context) {
        return ValueAnimator.areAnimatorsEnabled()
                && !context.getSharedPreferences("shelf", 0).getBoolean("reduceMotion", false);
    }
    static float dp(View view, float value) { return value * view.getResources().getDisplayMetrics().density; }
    static int color(int from, int to, float fraction) {
        float t = Math.max(0, Math.min(1, fraction));
        return Color.argb(Math.round(Color.alpha(from)+(Color.alpha(to)-Color.alpha(from))*t),
                Math.round(Color.red(from)+(Color.red(to)-Color.red(from))*t),
                Math.round(Color.green(from)+(Color.green(to)-Color.green(from))*t),
                Math.round(Color.blue(from)+(Color.blue(to)-Color.blue(from))*t));
    }
    interface Update { void apply(float value); }
    static final class Spring {
        private final SpringAnimation animation;
        private final Update update;
        float value;
        Spring(float initial, float stiffness, float damping, Update update) {
            this.value=initial; this.update=update;
            animation=new SpringAnimation(new FloatValueHolder(initial));
            animation.setSpring(new SpringForce(initial).setStiffness(stiffness).setDampingRatio(damping));
            animation.setMinimumVisibleChange(.001f);
            animation.addUpdateListener((a,v,velocity)->{value=v;update.apply(v);});
        }
        void to(float target, boolean animate) {
            if(animate) animation.animateToFinalPosition(target); // Retains current velocity on reversals.
            else { animation.cancel(); value=target; animation.setStartValue(target); update.apply(target); }
        }
        void stop() { animation.cancel(); }
    }
    static void identity(View view,String key) { view.setTag(R.id.motion_identity,key); }

    static void focus(View target, View visual, TextView caption, float enlargement, boolean outline) {
        new Focus(target,visual,caption,enlargement,outline);
    }
    static void settle(View root,boolean freeze) {
        if(root.getTag(R.id.motion_binding) instanceof Focus binding){
            binding.frozen=freeze;
            binding.focus.to(root.hasFocus()?1:0,false);
            binding.press.to(0,false);
        }
        root.jumpDrawablesToCurrentState();
        if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)settle(group.getChildAt(i),freeze);
    }
    private static final class Focus implements View.OnAttachStateChangeListener {
        final View target, visual;
        final TextView caption;
        final float enlargement;
        final GradientDrawable ring;
        final Spring focus, press;
        boolean frozen;
        Focus(View target,View visual,TextView caption,float enlargement,boolean outline) {
            this.target=target;this.visual=visual;this.caption=caption;this.enlargement=enlargement;
            ring=outline?new GradientDrawable():null;
            if(ring!=null){ring.setColor(Color.TRANSPARENT);ring.setCornerRadius(dp(visual,10));ring.setStroke(Math.round(dp(visual,1.4f)),Color.WHITE);ring.setAlpha(0);visual.setForeground(ring);}
            focus=new Spring(0,440,.92f,v->render());
            press=new Spring(0,750,1f,v->render());
            target.setTag(R.id.motion_binding,this);
            target.setStateListAnimator(null);
            target.setOnFocusChangeListener((v,hasFocus)->{
                if(frozen)return;
                if(!hasFocus)press.to(0,enabled(target.getContext()));
                focus.to(hasFocus?1:0,enabled(target.getContext()));
            });
            target.setOnKeyListener((v,key,event)->{
                if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER)
                    press.to(event.getAction()==KeyEvent.ACTION_DOWN?1:0,enabled(v.getContext()));
                return false;
            });
            target.addOnAttachStateChangeListener(this);
        }
        private void render() {
            if(focus==null||press==null)return;
            float f=Math.max(0,Math.min(1.02f,focus.value)),p=Math.max(0,press.value);
            boolean moving=enabled(target.getContext());
            float scale=moving?1+enlargement*f-.025f*p:1;
            visual.setScaleX(scale);visual.setScaleY(scale);
            visual.setTranslationY(moving?-dp(visual,2.5f)*f:0);
            visual.setElevation(dp(visual,7)*f);
            target.setTranslationZ(dp(target,9)*f);
            if(ring!=null)ring.setAlpha(Math.round(180*Math.min(1,f)));
            if(caption!=null)caption.setTextColor(color(0xffb8b9bc,0xfff5f4f5,f));
        }
        @Override public void onViewAttachedToWindow(View v){focus.to(v.hasFocus()?1:0,false);press.to(0,false);}
        @Override public void onViewDetachedFromWindow(View v){focus.stop();press.stop();}
    }
}
