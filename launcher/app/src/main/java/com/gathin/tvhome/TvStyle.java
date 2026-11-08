package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextClock;
import android.widget.TextView;

/** Shared television spacing, typography, focus surfaces and motion. */
final class TvStyle {
    static final int INK = 0xFFF5F5F7;
    static final int SECONDARY = 0xFFB9BDC7;
    static final int BASE = 0xFF11151D;
    static int dp(Context c, float n) { return Math.round(n*c.getResources().getDisplayMetrics().density); }
    static GradientDrawable surface(int fill, float radius, Context c) {
        GradientDrawable d = new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(c,radius)); return d;
    }
    static TextView text(Context c, String label, int size, int color) {
        TextView t = new TextView(c); t.setText(label); t.setTextSize(size); t.setTextColor(color);
        t.setTypeface(Typeface.create(size >= 28 ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        t.setIncludeFontPadding(false); return t;
    }
    static ColorStateList ink(int normal) { return new ColorStateList(new int[][]{new int[]{android.R.attr.state_focused},new int[]{}},new int[]{0xFF15171C,normal}); }
    static void focusText(TextView t) {
        t.setTextColor(new ColorStateList(new int[][]{new int[]{android.R.attr.state_focused}, new int[]{}},new int[]{0xFF15171C,INK}));
        t.setBackgroundResource(R.drawable.settings_choice_bg); t.setFocusable(true); t.setClickable(true);
    }
    static void applyHome(Activity a, FrameLayout root, Config cfg) {
        // Bundled scene is a fallback only; a user's theme wallpaper still takes precedence.
        if (cfg.wallpapers.isEmpty() && !a.getSharedPreferences(Config.SETTINGS_NAME,0).getBoolean("scene_off",false)) {
            ImageView scene = new ImageView(a); scene.setImageResource(R.drawable.tvos_landscape);
            scene.setScaleType(ImageView.ScaleType.CENTER_CROP); scene.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            root.addView(scene,0,new FrameLayout.LayoutParams(-1,-1));
        }
        a.findViewById(R.id.leftBlock).setVisibility(View.GONE);
        LinearLayout controls = a.findViewById(R.id.topButtons);
        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) controls.getLayoutParams();
        lp.removeRule(RelativeLayout.ALIGN_BOTTOM); lp.removeRule(RelativeLayout.ALIGN_TOP);
        lp.addRule(RelativeLayout.ALIGN_PARENT_TOP); lp.bottomMargin=0; controls.setLayoutParams(lp);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        TextClock time = new TextClock(a); time.setFormat12Hour("h:mm a"); time.setFormat24Hour("HH:mm");
        ClockFormat.apply(time,cfg.clockFormat); time.setTextSize(15); time.setTextColor(INK);
        time.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); time.setPadding(0,0,dp(a,8),0);
        controls.addView(time,0,new LinearLayout.LayoutParams(-2,-2));
        controls.setBackground(surface(0x3D1A2431,24,a)); controls.setPadding(dp(a,18),dp(a,4),dp(a,4),dp(a,4));
        for(int id:new int[]{R.id.btnLibraryTop,R.id.btnSettings}) {
            TextView button=a.findViewById(id); LinearLayout.LayoutParams bp=(LinearLayout.LayoutParams)button.getLayoutParams();
            bp.width=dp(a,36);bp.height=dp(a,36);bp.setMargins(dp(a,4),0,0,0);button.setLayoutParams(bp);
            button.setText("");button.setPadding(dp(a,8),dp(a,8),dp(a,8),dp(a,8));
            button.setCompoundDrawablesRelativeWithIntrinsicBounds(id==R.id.btnSettings?R.drawable.ic_tv_control:R.drawable.ic_tv_apps,0,0,0);
            button.setCompoundDrawableTintList(ink(INK));button.setBackgroundResource(R.drawable.tv_status_button);
        }
        TextView control = a.findViewById(R.id.btnSettings);
        control.setContentDescription("控制中心"); control.setOnLongClickListener(v -> {control.performClick(); return true;});
        // Existing listeners retain D-pad edge handling and remembered return focus.
        a.findViewById(R.id.hero).setVisibility(View.GONE);

    }
    static View controlTile(Activity a,android.graphics.Bitmap backdrop,int icon,String title,boolean large,Runnable action) {
        LinearLayout tile=new LinearLayout(a); tile.setOrientation(large?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);
        tile.setGravity(Gravity.CENTER);tile.setPadding(dp(a,8),dp(a,8),dp(a,8),dp(a,8));tile.setFocusable(true);tile.setClickable(true);tile.setId(View.generateViewId());
        tile.setBackground(new FrostedSurface(tile,backdrop,large?28:30,true));
        ImageView symbol=new ImageView(a);symbol.setImageResource(icon);symbol.setImageTintList(ink(INK));symbol.setDuplicateParentStateEnabled(true);
        tile.addView(symbol,new LinearLayout.LayoutParams(dp(a,large?38:20),dp(a,large?38:20)));
        TextView text=text(a,title,large?16:13,INK);text.setTextColor(ink(INK));text.setDuplicateParentStateEnabled(true);text.setGravity(Gravity.CENTER);text.setSingleLine(true);
        LinearLayout.LayoutParams label=new LinearLayout.LayoutParams(-2,-2);if(large)label.topMargin=dp(a,12);else label.leftMargin=dp(a,8);tile.addView(text,label);
        tile.setOnClickListener(v->action.run());tile.setContentDescription(title);
        Config config=Config.load(a);tile.setOnFocusChangeListener((v,f)->{float scale=f&&!config.reducedMotion&&config.animations?1.035f:1;v.animate().scaleX(scale).scaleY(scale).setDuration(config.animations?160:0).start();});
        return tile;
    }
    static void sceneBackground(Activity a,View root){
        android.util.DisplayMetrics m=a.getResources().getDisplayMetrics();
        android.graphics.drawable.BitmapDrawable art=new android.graphics.drawable.BitmapDrawable(a.getResources(),FrostedSurface.scene(a,m.widthPixels,m.heightPixels));art.setGravity(Gravity.FILL);
        root.setBackground(new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{art,new android.graphics.drawable.ColorDrawable(0xC411141A)}));
    }
    static void panelHeading(Context c, View panel, String title, String subtitle) {
        LinearLayout container=(LinearLayout)panel;
        LinearLayout heading=new LinearLayout(c); heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(c,12),dp(c,8),dp(c,12),dp(c,16));
        heading.addView(text(c,title,24,INK));
        TextView detail=text(c,subtitle,13,SECONDARY); detail.setPadding(0,dp(c,10),0,0); heading.addView(detail);
        container.addView(heading,0,new LinearLayout.LayoutParams(-1,-2));
    }
    static void dimPopup(Activity activity, View panel,float amount) {
        View container=panel.getRootView();
        if(container.getLayoutParams() instanceof WindowManager.LayoutParams) {
            WindowManager.LayoutParams lp=(WindowManager.LayoutParams)container.getLayoutParams();
            lp.flags|=WindowManager.LayoutParams.FLAG_DIM_BEHIND; lp.dimAmount=amount;
            activity.getWindowManager().updateViewLayout(container,lp);
        }
    }
}
