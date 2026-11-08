package local.shelf.player;

import android.app.Activity;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioProfile;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.view.Display;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

final class Capabilities {
    static String read(Activity activity) {
        StringBuilder text = new StringBuilder();
        Display display = activity.getDisplay();
        if (display != null) {
            Display.Mode mode = display.getMode();
            text.append("Android 显示模式  ").append(mode.getPhysicalWidth()).append(" × ")
                    .append(mode.getPhysicalHeight()).append("  /  ").append(Math.round(mode.getRefreshRate()))
                    .append(" Hz\n");
            text.append("屏幕申报 HDR  ").append(String.join(" / ", Arrays.stream(mode.getSupportedHdrTypes())
                    .mapToObj(Capabilities::hdrName).collect(java.util.stream.Collectors.toList()))).append("\n\n");
        }
        AudioManager manager = activity.getSystemService(AudioManager.class);
        text.append("已连接音频输出\n");
        for (AudioDeviceInfo device : manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            text.append("  ").append(deviceName(device.getType())).append("  ")
                    .append(device.getProductName()).append("\n");
        }
        AudioAttributes attributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build();
        text.append("\n当前媒体路径申报的直出格式\n");
        Set<String> profiles = new LinkedHashSet<>();
        for (AudioProfile profile : manager.getDirectProfilesForAttributes(attributes)) {
            profiles.add(audioName(profile.getFormat()));
        }
        text.append(profiles.isEmpty() ? "  系统未返回直出格式" : "  " + String.join(" / ", profiles));
        text.append("\n\nAndroid 硬件视频解码 MIME\n");
        Set<String> codecs = new LinkedHashSet<>();
        for (MediaCodecInfo codec : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
            if (!codec.isEncoder() && codec.isHardwareAccelerated()) {
                for (String type : codec.getSupportedTypes()) if (type.startsWith("video/")) codecs.add(type);
            }
        }
        return text.append("  ").append(String.join(" / ", codecs))
                .append("\n\n原始音轨以播放时回音壁显示的信号为准。").toString();
    }

    private static String hdrName(int value) {
        return switch (value) {
            case 1 -> "Dolby Vision";
            case 2 -> "HDR10";
            case 3 -> "HLG";
            case 4 -> "HDR10+";
            default -> "类型 " + value;
        };
    }

    private static String deviceName(int value) {
        return switch (value) {
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "电视扬声器";
            case AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI ARC";
            case AudioDeviceInfo.TYPE_HDMI_EARC -> "HDMI eARC";
            case AudioDeviceInfo.TYPE_HDMI -> "HDMI";
            default -> "设备类型 " + value;
        };
    }

    private static String audioName(int value) {
        return switch (value) {
            case AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_24BIT_PACKED,
                    AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> "PCM";
            case AudioFormat.ENCODING_AC3 -> "Dolby Digital";
            case AudioFormat.ENCODING_E_AC3 -> "Dolby Digital Plus";
            case AudioFormat.ENCODING_E_AC3_JOC -> "Dolby Digital Plus / Atmos";
            case AudioFormat.ENCODING_DOLBY_TRUEHD -> "Dolby TrueHD";
            case AudioFormat.ENCODING_DTS -> "DTS";
            case AudioFormat.ENCODING_DTS_HD -> "DTS-HD";
            case AudioFormat.ENCODING_DTS_UHD -> "DTS-UHD";
            case AudioFormat.ENCODING_IEC61937 -> "IEC 61937";
            default -> "编码 " + value;
        };
    }
}
