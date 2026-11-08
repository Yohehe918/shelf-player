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

final class PlaybackHud {
    private final Activity context;
    private final Player player;
    private final PlayerView surface;
    private final FrameLayout frame;
    private final LinearLayout controls;
    private final SeekBar bar;
    private final TextView clock;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hide = () -> setVisible(false);
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            update();
            handler.postDelayed(this, 450);
        }
    };

    PlaybackHud(Activity context, PlayerView surface, Player player, String title, Runnable subtitles) {
        this.context = context;
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
        controls.addView(tabs);
        add(tabs, "信息", () -> new AlertDialog.Builder(context).setTitle(title)
                .setMessage(streamInfo()).setPositiveButton("关闭", null).show());
        add(tabs, "视频", this::videoOptions);
        add(tabs, "音频", () -> tracks(C.TRACK_TYPE_AUDIO, null));
        add(tabs, "字幕", () -> tracks(C.TRACK_TYPE_TEXT, subtitles));
        setVisible(true);
        handler.post(refresh);
    }

    View root() { return frame; }

    void close() { handler.removeCallbacksAndMessages(null); }

    boolean key(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        int key = event.getKeyCode();
        boolean visible = controls.getVisibility() == View.VISIBLE;
        if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER ||
                key == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            if (!visible || bar.hasFocus() || key == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                if (player.isPlaying()) player.pause();
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
        controls.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (surface.getSubtitleView() != null) surface.getSubtitleView().setBottomPaddingFraction(visible ? .18f : .08f);
        if (visible && !controls.hasFocus()) bar.requestFocus();
        if (visible) refreshTimeout();
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
        clock.setText(time(position) + "    /    " + time(duration));
        if (player.isPlaying() && controls.getVisibility() == View.VISIBLE && !handler.hasCallbacks(hide))
            refreshTimeout();
    }

    private String streamInfo() {
        List<String> current = new ArrayList<>();
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            for (int i = 0; i < group.length; i++) {
                if (group.isTrackSelected(i)) current.add(format(group.getTrackFormat(i)));
            }
        }
        return current.isEmpty() ? "等待读取影片信息" : String.join("\n", current);
    }

    private void tracks(int type, Runnable more) {
        List<String> names = new ArrayList<>();
        List<TrackSelectionOverride> selections = new ArrayList<>();
        names.add(type == C.TRACK_TYPE_TEXT ? "关闭字幕" : "自动");
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            if (group.getType() != type) continue;
            for (int index = 0; index < group.length; index++) {
                if (!group.isTrackSupported(index)) continue;
                names.add((group.isTrackSelected(index) ? "✓  " : "") + format(group.getTrackFormat(index)));
                selections.add(new TrackSelectionOverride(group.getMediaTrackGroup(), index));
            }
        }
        if (more != null) names.add("查找在线字幕");
        new AlertDialog.Builder(context).setTitle(type == C.TRACK_TYPE_TEXT ? "字幕" : "音频")
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
        new AlertDialog.Builder(context).setTitle("视频").setItems(names, (dialog, which) -> {
            if (which == 0) surface.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
            else if (which == 1) surface.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
            else player.setPlaybackSpeed(speeds[which - 2]);
        }).show();
    }

    private String format(Format format) {
        List<String> parts = new ArrayList<>();
        if (format.label != null) parts.add(format.label);
        if (format.language != null) parts.add(format.language);
        if (format.sampleMimeType != null) parts.add(format.sampleMimeType);
        if (format.width > 0) parts.add(format.width + "×" + format.height);
        if (format.channelCount > 0) parts.add(format.channelCount + " 声道");
        return parts.isEmpty() ? "轨道" : String.join("  ·  ", parts);
    }

    private void add(LinearLayout row, String text, Runnable action) {
        Button button = new Button(context);
        button.setText(text);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setPadding(dp(5), 0, dp(5), 0);
        button.setTextColor(Color.WHITE);
        StateListDrawable background = new StateListDrawable();
        GradientDrawable focus = new GradientDrawable();
        focus.setColor(0x62ffffff);
        focus.setCornerRadius(dp(5));
        background.addState(new int[]{android.R.attr.state_focused}, focus);
        background.addState(new int[]{}, new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        button.setBackground(background);
        button.setOnClickListener(v -> action.run());
        row.addView(button, new LinearLayout.LayoutParams(dp(65), dp(32)));
    }

    private String time(long ms) {
        long seconds = ms / 1000;
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
