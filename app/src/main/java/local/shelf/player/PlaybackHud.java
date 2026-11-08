package local.shelf.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.ui.PlayerView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@androidx.media3.common.util.UnstableApi
final class PlaybackHud {
    private final Activity context;
    private final Player player;
    private final android.content.SharedPreferences preferences;
    private final PlayerView surface;
    private final FrameLayout frame;
    private final LinearLayout controls;
    private final SeekBar bar;
    private final TextView clock;
    private Motion.Spring visibilityMotion;
    private boolean controlsShown;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hide = () -> setVisible(false);
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            update();
            handler.postDelayed(this, 450);
        }
    };

    PlaybackHud(Activity context, PlayerView surface, Player player, String title, Runnable subtitles,Runnable previous,Runnable next) {
        this.context = context;
        preferences=context.getSharedPreferences("shelf",0);
        this.player = player;
        this.surface = surface;
        frame = new FrameLayout(context);
        frame.addView(surface, new FrameLayout.LayoutParams(-1, -1));

        controls = new LinearLayout(context);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(38), dp(34), dp(38), dp(19));
        controls.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xcf101112, 0xa8101112, Color.TRANSPARENT}));
        frame.addView(controls, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        bar = new SeekBar(context);
        bar.setId(View.generateViewId());
        bar.setMax(10000);
        bar.setPadding(0, 0, 0, 0);
        bar.setKeyProgressIncrement(300);
        bar.setProgressTintList(ColorStateList.valueOf(Color.WHITE));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(0x7cffffff));
        bar.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
        controls.addView(bar, new LinearLayout.LayoutParams(-1, dp(27)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar view, int progress, boolean fromUser) {
                long duration = player.getDuration();
                if (fromUser && duration > 0) {
                    player.seekTo(duration * progress / 10000);
                    refreshTimeout();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar view) { handler.removeCallbacks(hide); }
            @Override public void onStopTrackingTouch(SeekBar view) { refreshTimeout(); }
        });
        clock = new TextView(context);
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(12);
        controls.addView(clock);
        LinearLayout tabs = new LinearLayout(context);
        tabs.setClipChildren(false);tabs.setClipToPadding(false);
        controls.setClipChildren(false);controls.setClipToPadding(false);
        controls.addView(tabs);
        add(tabs, "信息", () -> new MotionDialog(context).setTitle(title)
                .setMessage(streamInfo()).setPositiveButton("关闭", null).show());
        add(tabs, "视频", this::videoOptions);
        add(tabs, "音频", () -> tracks(C.TRACK_TYPE_AUDIO, null));
        add(tabs, "字幕", () -> tracks(C.TRACK_TYPE_TEXT, subtitles));
        add(tabs,"字幕外观",this::subtitleAppearance);
        if(previous!=null)add(tabs,"上一集",previous);
        if(next!=null)add(tabs,"下一集",next);
        applySubtitleAppearance();
        controls.setAlpha(0);
        visibilityMotion=new Motion.Spring(0,340,1f,this::renderVisibility);
        setVisible(true);
        handler.post(refresh);
    }

    View root() { return frame; }

    void close() { handler.removeCallbacksAndMessages(null); visibilityMotion.stop(); }

    boolean key(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        int key = event.getKeyCode();
        boolean visible = controlsShown;
        if(key==KeyEvent.KEYCODE_MEDIA_PAUSE || key==KeyEvent.KEYCODE_MEDIA_PLAY){
            if(key==KeyEvent.KEYCODE_MEDIA_PAUSE)player.pause();
            else{if(player.getPlaybackState()==Player.STATE_ENDED)player.seekTo(0);player.play();}
            setVisible(true);
            return true;
        }
        if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER ||
                key == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            if (!visible || bar.hasFocus() || key == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                if(player.getPlaybackState()==Player.STATE_ENDED){player.seekTo(0);player.play();}
                else if (player.getPlayWhenReady()) player.pause();
                else player.play();
                setVisible(true);
                return true;
            }
        }
        if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (!visible || bar.hasFocus()) {
                long duration = player.getDuration();
                if (duration > 0) player.seekTo(Math.max(0, Math.min(duration, player.getCurrentPosition()
                        + (key == KeyEvent.KEYCODE_DPAD_LEFT ? -10000 : 10000))));
                setVisible(true);
                return true;
            }
        }
        if (!visible && (key == KeyEvent.KEYCODE_DPAD_DOWN || key == KeyEvent.KEYCODE_DPAD_UP)) {
            setVisible(true);
            return true;
        }
        if (visible) refreshTimeout();
        return false;
    }

    private void setVisible(boolean visible) {
        if(controlsShown!=visible){
            controlsShown=visible;
            controls.setDescendantFocusability(visible?android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS:android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            if(visible)controls.setVisibility(View.VISIBLE);
            visibilityMotion.to(visible?1:0,Motion.enabled(context));
        }
        if (visible && !controls.hasFocus()) bar.requestFocus();
        if (visible) refreshTimeout();
    }

    private void renderVisibility(float value){
        float fraction=Math.max(0,Math.min(1,value));
        controls.setAlpha(fraction);
        controls.setTranslationY(Motion.enabled(context)?dp(18)*(1-fraction):0);
        if(surface.getSubtitleView()!=null)surface.getSubtitleView().setBottomPaddingFraction(.08f+.10f*fraction);
        if(!controlsShown&&fraction<.001f)controls.setVisibility(View.INVISIBLE);
    }

    private void refreshTimeout() {
        handler.removeCallbacks(hide);
        if (player.isPlaying()) handler.postDelayed(hide, 4500);
    }

    private void update() {
        long duration = player.getDuration();
        if (duration <= 0) return;
        long position = player.getCurrentPosition();
        bar.setProgress((int) Math.min(10000, 10000L * position / duration));
        String value=time(position)+"    /    "+time(duration);
        if(!value.contentEquals(clock.getText()))clock.setText(value);
        if (player.isPlaying() && controlsShown && !handler.hasCallbacks(hide))
            refreshTimeout();
    }

    private String streamInfo() {
        List<String> current = new ArrayList<>();
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            for (int i = 0; i < group.length; i++) {
                if (group.isTrackSelected(i)) {
                    Format f=group.getTrackFormat(i);String line=format(f);String range=TrackNames.dynamicRange(f);
                    if(range!=null)line+=" · "+range;if(f.frameRate>0)line+=" · "+TrackNames.frameRate(f.frameRate);current.add(line);
                }
            }
        }
        return current.isEmpty() ? "等待读取影片信息" : String.join("\n", current)+"\n\n以上是片源与所选轨道信息；回音壁实际接收格式请查看音响显示。";
    }

    private void tracks(int type, Runnable more) {
        List<String> names = new ArrayList<>();
        List<TrackSelectionOverride> selections = new ArrayList<>();
        names.add(type == C.TRACK_TYPE_TEXT ? "关闭字幕" : "自动");
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            if (group.getType() != type) continue;
            for (int index = 0; index < group.length; index++) {
                if (!group.isTrackSupported(index)) continue;
                Format f=group.getTrackFormat(index);
                String detail=TrackNames.detail(context,f,group.isTrackSelected(index));
                names.add(TrackNames.title(context,f,index,type)+(detail==null?"":" · "+detail));
                selections.add(new TrackSelectionOverride(group.getMediaTrackGroup(), index));
            }
        }
        if (more != null) names.add("查找在线字幕");
        new MotionDialog(context).setTitle(type == C.TRACK_TYPE_TEXT ? "字幕" : "音频")
                .setItems(names.toArray(new String[0]), (dialog, which) -> {
                    if (more != null && which == names.size() - 1) {
                        more.run();
                        return;
                    }
                    var parameters = player.getTrackSelectionParameters().buildUpon().clearOverridesOfType(type)
                            .setTrackTypeDisabled(type, which == 0 && type == C.TRACK_TYPE_TEXT);
                    if (which > 0) parameters.setOverrideForType(selections.get(which - 1));
                    player.setTrackSelectionParameters(parameters.build());
                }).setNegativeButton("关闭", null).show();
    }

    private void videoOptions() {
        String[] names = {"原始比例", "填满屏幕", "0.75× 速度", "1× 速度", "1.25× 速度", "1.5× 速度", "2× 速度"};
        float[] speeds = {0.75f, 1f, 1.25f, 1.5f, 2f};
        new MotionDialog(context).setTitle("视频").setItems(names, (dialog, which) -> {
            if (which == 0) surface.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
            else if (which == 1) surface.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
            else player.setPlaybackSpeed(speeds[which - 2]);
        }).show();
    }

    private String format(Format format) {
        String detail=TrackNames.detail(context,format,false);return detail==null?"轨道":detail;
    }
    private void applySubtitleAppearance(){
        androidx.media3.ui.SubtitleView view=surface.getSubtitleView();if(view==null)return;
        if(!preferences.getBoolean("customSubStyle",false)){view.setApplyEmbeddedStyles(true);view.setApplyEmbeddedFontSizes(true);view.setUserDefaultStyle();view.setUserDefaultTextSize();return;}
        view.setApplyEmbeddedStyles(false);view.setApplyEmbeddedFontSizes(false);
        int color=SubtitleColors.argb(preferences.getString("subtitleColor",SubtitleColors.DEFAULT));
        view.setStyle(new androidx.media3.ui.CaptionStyleCompat(color,Color.TRANSPARENT,Color.TRANSPARENT,androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,Color.BLACK,android.graphics.Typeface.DEFAULT));
        view.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP,preferences.getInt("subtitleSize",24));
    }
    private void subtitleAppearance(){
        new MotionDialog(context).setTitle("字幕外观").setItems(new String[]{"文字大小","文字颜色","恢复字幕原有样式"},(d,w)->{
            if(w==0)new MotionDialog(context).setTitle("文字大小").setSingleChoiceItems(new String[]{"小 · 20","标准 · 24","大 · 30","特大 · 36"},java.util.Arrays.asList(20,24,30,36).indexOf(preferences.getInt("subtitleSize",24)),(choice,i)->{preferences.edit().putBoolean("customSubStyle",true).putInt("subtitleSize",new int[]{20,24,30,36}[i]).apply();applySubtitleAppearance();choice.dismiss();}).show();
            else if(w==1)new MotionDialog(context).setTitle("文字颜色").setSingleChoiceItems(new String[]{"白色","米色","黄色","琥珀色","绿色","青色","天蓝","粉色"},SubtitleColors.indexOf(preferences.getString("subtitleColor",SubtitleColors.DEFAULT)),(choice,i)->{preferences.edit().putBoolean("customSubStyle",true).putString("subtitleColor",SubtitleColors.keyAt(i)).apply();applySubtitleAppearance();choice.dismiss();}).show();
            else{preferences.edit().remove("customSubStyle").remove("subtitleSize").remove("subtitleColor").apply();applySubtitleAppearance();}
        }).show();
    }

    private void add(LinearLayout row, String text, Runnable action) {
        Button button = new Button(context);
        button.setId(View.generateViewId());
        button.setNextFocusUpId(bar.getId());
        if(row.getChildCount()==0)bar.setNextFocusDownId(button.getId());
        Motion.identity(button,"playback:"+text);
        button.setText(text);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setSingleLine(true);
        button.setPadding(dp(5), 0, dp(5), 0);
        button.setTextColor(Color.WHITE);
        StateListDrawable background = new StateListDrawable();
        GradientDrawable focus = new GradientDrawable();
        focus.setColor(0x62ffffff);
        focus.setCornerRadius(dp(5));
        background.addState(new int[]{android.R.attr.state_focused}, focus);
        background.addState(new int[]{}, new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        background.setEnterFadeDuration(Motion.enabled(context)?120:0);
        background.setExitFadeDuration(Motion.enabled(context)?160:0);
        button.setBackground(background);
        Motion.focus(button,button,null,.03f,false);
        button.setOnClickListener(v -> action.run());
        row.addView(button, new LinearLayout.LayoutParams(dp(text.length()>3?90:65), dp(36)));
    }

    private String time(long ms) {
        long seconds = ms / 1000;
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
