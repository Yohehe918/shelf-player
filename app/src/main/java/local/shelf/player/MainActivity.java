package local.shelf.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.common.MimeTypes;
import androidx.media3.ui.PlayerView;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Comparator;
import java.io.File;
import java.nio.file.Files;
import java.util.function.Predicate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int INK = 0xff101621;
    private static final int CARD = 0xff1b2431;
    private static final int PAPER = 0xfff3eee8;
    private static final int QUIET = 0xffaeb9cb;
    private static final int AMBER = 0xffe7be8e;
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final ExecutorService artwork = Executors.newFixedThreadPool(3);
    private final LruCache<String, Bitmap> images = new LruCache<>(24 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };
    private final Map<String, OnlineServices.Metadata> metadata = new HashMap<>();
    private final Map<String, List<MediaItem.SubtitleConfiguration>> extraSubtitles = new HashMap<>();
    private List<Catalog.Movie> movies = List.of();
    private SharedPreferences saved;
    private Secrets secrets;
    private String page = "home";
    private Catalog.Movie selected;
    private Runnable leaveDetails;
    private long duration;
    private String libraryMessage = "";
    private SmbMedia.Source source;
    private String folder = "";
    private List<SmbMedia.Entry> entries = List.of();
    private LinearLayout panel;
    private TextView status;
    private boolean loading;
    private ExoPlayer player;
    private PlayerView playerView;
    private PlaybackHud hud;
    private String playbackKey;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        saved = getSharedPreferences("shelf", MODE_PRIVATE);
        secrets = new Secrets(saved);
        showHome();
        if (!saved.getString("address", "").isEmpty() && !saved.getString("share", "").isEmpty()) {
            SmbMedia.Source previous = parseSource(saved.getString("address", ""), saved.getString("share", ""),
                    saved.getString("user", ""), secrets.read("smbSecret").toCharArray());
            if (previous != null) scan(previous, null);
        }
    }

    private void fullScreen() {
        WindowInsetsController controller = getWindow().getDecorView().getWindowInsetsController();
        if (controller != null) {
            controller.hide(WindowInsets.Type.systemBars());
            controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    private void shell(String caption, String message) {
        LinearLayout screen = new LinearLayout(this);
        screen.setBackgroundColor(INK);
        screen.setPadding(dp(32), dp(22), dp(32), dp(22));
        screen.setGravity(Gravity.CENTER_VERTICAL);
        screen.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setPadding(0, dp(8), dp(22), 0);
        screen.addView(rail, new LinearLayout.LayoutParams(dp(176), -1));
        TextView marker = label("S H E L F    /    S M B", 12, AMBER);
        rail.addView(marker);
        TextView title = label("片库", 36, PAPER);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setPadding(0, dp(5), 0, dp(12));
        rail.addView(title);
        rail.addView(label(caption, 17, PAPER));
        TextView detail = label(message, 12, QUIET);
        detail.setPadding(0, dp(5), 0, 0);
        rail.addView(detail);
        rail.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));
        Button diagnostics = button("播放诊断", this::diagnostics);
        rail.addView(diagnostics);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackground(rounded(CARD, 15));
        content.setPadding(dp(14), dp(9), dp(14), dp(10));
        screen.addView(content, new LinearLayout.LayoutParams(0, -1, 1));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(panel);
        status = label("", 12, AMBER);
        status.setPadding(dp(5), dp(4), dp(5), 0);
        status.setVisibility(View.GONE);
        content.addView(status);
        setContentView(screen);
        screen.post(this::fullScreen);
    }

    private void showConnection() {
        page = "connect";
        shell("连接共享", "打开 NAS 或电脑里的影片。\n支持 SMB 2 / 3。");
        EditText address = field("服务器地址，例如 192.168.31.20", saved.getString("address", ""), false);
        EditText share = field("共享文件夹名，例如 Movies", saved.getString("share", ""), false);
        EditText user = field("用户名，可留空；域账号用 DOMAIN\\user", saved.getString("user", ""), false);
        EditText password = field("密码，可留空", secrets.read("smbSecret"), true);
        Button connect = button("打开共享", () -> {
            if (loading) return;
            String host = address.getText().toString().trim();
            String shareName = share.getText().toString().trim();
            String login = user.getText().toString().trim();
            String pass = password.getText().toString();
            SmbMedia.Source next = parseSource(host, shareName, login, pass.toCharArray());
            if (next == null) {
                message("请输入服务器地址和 SMB 共享名。");
                return;
            }
            getSystemService(InputMethodManager.class).hideSoftInputFromWindow(password.getWindowToken(), 0);
            scan(next, () -> {
                saved.edit().putString("address", host).putString("share", shareName).putString("user", login).apply();
                if (!secrets.write("smbSecret", pass)) libraryMessage = "电视未能保存密码，下次打开时可重新输入。";
            });
        });
        panel.addView(connect);
        connect.requestFocus();
    }

    private SmbMedia.Source parseSource(String address, String share, String login, char[] secret) {
        Uri endpoint = Uri.parse("smb://" + address);
        String host = endpoint.getHost();
        String path = endpoint.getPath();
        int port = endpoint.getPort() == -1 ? 445 : endpoint.getPort();
        if (host == null || host.isBlank() || endpoint.getUserInfo() != null || endpoint.getQuery() != null ||
                endpoint.getFragment() != null || (path != null && !path.isEmpty()) || port < 1 || port > 65535 ||
                share.isEmpty() || share.contains("/") || share.contains("\\")) {
            Arrays.fill(secret, '\0');
            return null;
        }
        String[] parts = login.split("\\\\", 2);
        return new SmbMedia.Source(host, port, share, parts[parts.length - 1],
                parts.length == 2 ? parts[0] : "", secret);
    }

    private void scan(SmbMedia.Source next, Runnable connected) {
        if (loading) return;
        loading = true;
        if (page.equals("connect")) message("正在扫描共享文件夹…");
        else if (page.equals("home")) showHome();
        String tmdb = secrets.read("tmdb");
        network.execute(() -> {
            try {
                List<Catalog.Movie> indexed = SmbMedia.index(next);
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    if (source != null && !source.identity().equals(next.identity())) {
                        metadata.clear();
                        extraSubtitles.clear();
                    }
                    source = next;
                    movies = indexed;
                    loading = false;
                    libraryMessage = "";
                    if (connected != null) connected.run();
                    showHome();
                });
                if (!tmdb.isEmpty()) fetchMetadata(next, indexed, tmdb);
            } catch (Exception error) {
                Log.w("ShelfPlayer", "SMB indexing failed: " + error.getClass().getSimpleName());
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    loading = false;
                    libraryMessage = "连接失败。请检查共享名、账号权限和 NAS 网络。";
                    if (page.equals("connect")) message(libraryMessage);
                    else if (page.equals("home")) showHome();
                });
            }
        });
    }

    private void fetchMetadata(SmbMedia.Source current, List<Catalog.Movie> indexed, String token) {
        Map<String, OnlineServices.Metadata> found = new HashMap<>();
        for (Catalog.Movie movie : indexed) {
            if (Thread.currentThread().isInterrupted()) return;
            try {
                OnlineServices.Metadata result = OnlineServices.movie(movie.title(), token);
                if (result != null) found.put(movie.path(), result);
            } catch (Exception error) {
                Log.w("ShelfPlayer", "TMDB request failed: " + error.getClass().getSimpleName());
                break;
            }
        }
        runOnUiThread(() -> {
            if (isDestroyed() || current != source) return;
            metadata.putAll(found);
            if (page.equals("home") && !found.isEmpty()) showHome();
        });
    }

    private void showHome() {
        page = "home";
        LibraryView view = new LibraryView(this);
        view.navigation(this::search, () -> {
            if (source == null) showConnection();
            else scan(source, null);
        }, this::showSettings);
        List<Catalog.Movie> watching = movies.stream().filter(movie -> saved.getLong(key(movie), 0) > 0)
                .sorted(Comparator.comparingLong((Catalog.Movie movie) -> saved.getLong("recent:" + key(movie), 0)).reversed())
                .collect(java.util.stream.Collectors.toList());
        if (!watching.isEmpty()) {
            view.heading("继续观看");
            view.railMovies(watching, true, movie -> showDetails(movie, this::showHome), this::loadArt, this::progress);
        }
        if (movies.isEmpty()) view.empty(loading ? "正在扫描片库" : "你的影片库",
                libraryMessage.isEmpty() ? "连接 NAS 或电脑共享，影片会自动加入片库。" : libraryMessage,
                "添加 SMB 共享", this::showConnection);
        view.heading("收藏");
        view.favorites(List.of("影片库", "我的收藏", "电影", "剧集", "文件"),
                List.of(() -> grid("影片库", movie -> true, 0),
                        () -> grid("我的收藏", this::favorite, 0),
                        () -> grid("电影", movie -> !movie.title().series(), 0),
                        () -> grid("剧集", movie -> movie.title().series(), 0),
                        this::openFolders));
        if (!movies.isEmpty()) {
            view.heading("最近加入");
            view.railMovies(movies, false, movie -> showDetails(movie, this::showHome), this::loadArt, this::progress);
        }
        mount(view.root());
    }

    private void grid(String heading, Predicate<Catalog.Movie> filter, int index) {
        page = "grid";
        List<Catalog.Movie> filtered = movies.stream().filter(filter)
                .sorted(Comparator.comparing(movie -> movie.title().name(), String.CASE_INSENSITIVE_ORDER))
                .collect(java.util.stream.Collectors.toList());
        LibraryView view = new LibraryView(this);
        view.gridStyle();
        view.heading(heading);
        if (filtered.isEmpty()) view.empty("这里还没有影片", "连接共享或切换片库分类。", "返回首页", this::showHome);
        if (index > 0) view.nextPage("上一页", () -> grid(heading, filter, index - 48));
        view.gridMovies(filtered.subList(Math.min(filtered.size(), index), Math.min(filtered.size(), index + 48)),
                movie -> showDetails(movie, () -> grid(heading, filter, index)), this::loadArt, this::progress);
        if (filtered.size() > index + 48) view.nextPage("下一页", () -> grid(heading, filter, index + 48));
        mount(view.root());
    }

    private void search() {
        EditText field = new EditText(this);
        field.setSingleLine();
        field.setHint("输入片名");
        new AlertDialog.Builder(this).setTitle("搜索片库").setView(field)
                .setPositiveButton("搜索", (dialog, which) -> {
                    String query = field.getText().toString().trim().toLowerCase(Locale.ROOT);
                    grid("搜索结果", movie -> displayName(movie).toLowerCase(Locale.ROOT).contains(query)
                            || movie.filename().toLowerCase(Locale.ROOT).contains(query), 0);
                }).setNegativeButton("取消", null).show();
    }

    private void showSettings() {
        page = "settings";
        shell("设置", "共享、影片资料与字幕。");
        panel.addView(button("SMB 共享", this::showConnection));
        panel.addView(button("在线匹配", this::showServices));
        panel.addView(button("返回首页", this::showHome));
    }

    private void showServices() {
        page = "services";
        shell("在线匹配", "设置后，TMDB 会接收待匹配的片名。字幕下载使用 OpenSubtitles。");
        EditText tmdb = field("TMDB API Read Access Token", secrets.read("tmdb"), true);
        EditText key = field("OpenSubtitles 应用 API Key", secrets.read("openSubKey"), true);
        EditText user = field("OpenSubtitles 用户名，可留空", saved.getString("openSubUser", ""), false);
        EditText pass = field("OpenSubtitles 密码，可留空", secrets.read("openSubPass"), true);
        EditText lang = field("字幕语言，例如 zh-cn,zh-tw,en", subtitleLanguages(), false);
        panel.addView(button("保存并刷新片库", () -> {
            if (!secrets.write("tmdb", tmdb.getText().toString().trim()) ||
                    !secrets.write("openSubKey", key.getText().toString().trim()) ||
                    !secrets.write("openSubPass", pass.getText().toString())) {
                message("电视无法安全保存这些凭据。");
                return;
            }
            saved.edit().putString("openSubUser", user.getText().toString().trim())
                    .putString("subLang", lang.getText().toString().trim()).apply();
            showHome();
            if (source != null) scan(source, null);
        }));
        panel.addView(label("This product uses the TMDB API but is not endorsed or certified by TMDB.", 12, QUIET));
    }

    private void openFolders() {
        if (source == null) showConnection();
        else load(source, "", () -> {});
    }

    private void showDetails(Catalog.Movie movie, Runnable back) {
        selected = movie;
        leaveDetails = back;
        page = "details";
        long resume = saved.getLong(key(movie), 0);
        OnlineServices.Metadata match = metadata.get(movie.path());
        LibraryView view = new LibraryView(this);
        view.details(movie, displayName(movie), match == null ? "" : match.overview(),
                resume > 0 ? "继续播放  " + formatTime(resume) : "播放",
                () -> startMovie(movie), () -> {
                    saved.edit().putBoolean("favorite:" + key(movie), !favorite(movie)).apply();
                    showDetails(movie, back);
                }, favorite(movie), () -> subtitleDialog(movie), this::loadArt);
        mount(view.root());
    }

    private String key(Catalog.Movie movie) {
        return source.identity() + "/" + movie.path();
    }

    private String displayName(Catalog.Movie movie) {
        OnlineServices.Metadata match = metadata.get(movie.path());
        return match != null && !match.name().isBlank() ? match.name() : movie.title().name();
    }

    private boolean favorite(Catalog.Movie movie) {
        return saved.getBoolean("favorite:" + key(movie), false);
    }

    private int progress(Catalog.Movie movie) {
        long total = saved.getLong("duration:" + key(movie), 0);
        return total <= 0 ? 0 : (int) Math.min(100, 100 * saved.getLong(key(movie), 0) / total);
    }

    private String formatTime(long millis) {
        long seconds = millis / 1000;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    private void loadArt(Catalog.Movie movie, boolean wide, ImageView view) {
        if (source == null) return;
        SmbMedia.Source current = source;
        OnlineServices.Metadata match = metadata.get(movie.path());
        String local = wide ? (movie.backdrop() == null ? movie.poster() : movie.backdrop()) : movie.poster();
        String url = match == null ? null : (wide ? match.backdrop() : match.poster());
        if (local == null && url == null) return;
        String cacheKey = current.identity() + "|" + (local != null ? local : url);
        Bitmap cached = images.get(cacheKey);
        if (cached != null) {
            applyArt(view, cached);
            return;
        }
        artwork.execute(() -> {
            try {
                byte[] bytes = local != null ? SmbMedia.readAsset(current, local) : OnlineServices.image(url);
                BitmapFactory.Options size = new BitmapFactory.Options();
                size.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, size);
                int max = wide ? 1280 : 500;
                BitmapFactory.Options decode = new BitmapFactory.Options();
                decode.inSampleSize = 1;
                while (Math.max(size.outWidth, size.outHeight) / decode.inSampleSize > max * 2) decode.inSampleSize *= 2;
                Bitmap result = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, decode);
                if (result == null) {
                    Log.w("ShelfPlayer", "Artwork bytes could not be decoded");
                    return;
                }
                images.put(cacheKey, result);
                runOnUiThread(() -> applyArt(view, result));
            } catch (Exception error) {
                Log.w("ShelfPlayer", "Artwork read failed: " + error.getClass().getSimpleName());
            }
        });
    }

    private void applyArt(ImageView view, Bitmap bitmap) {
        view.setImageBitmap(bitmap);
        if (view.getTag() instanceof View label) label.setVisibility(View.GONE);
    }

    private void mount(View screen) {
        setContentView(screen);
        screen.post(this::fullScreen);
    }

    private String subtitleLanguages() {
        String lang = saved.getString("subLang", "");
        return lang.isBlank() ? "zh-cn,zh-tw,en" : lang;
    }

    private void subtitleDialog(Catalog.Movie movie) {
        String apiKey = secrets.read("openSubKey");
        if (apiKey.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("字幕").setMessage(movie.subtitles().isEmpty()
                    ? "同目录字幕会自动匹配。可在设置中接入 OpenSubtitles 搜索在线字幕。"
                    : "已匹配同目录字幕 " + movie.subtitles().size() + " 个。播放时可切换字幕。")
                    .setPositiveButton("关闭", null).setNeutralButton("在线匹配设置", (dialog, which) -> showServices()).show();
            return;
        }
        findSubtitles(movie, apiKey, false);
    }

    private void startMovie(Catalog.Movie movie) {
        String key = secrets.read("openSubKey");
        if (!key.isEmpty() && movie.subtitles().isEmpty() && !extraSubtitles.containsKey(movie.path()))
            findSubtitles(movie, key, true);
        else play(movie);
    }

    private void findSubtitles(Catalog.Movie movie, String apiKey, boolean automatic) {
        SmbMedia.Source current = source;
        String lang = subtitleLanguages();
        AlertDialog waiting = new AlertDialog.Builder(this).setMessage("正在匹配字幕…").setNegativeButton("取消", null).show();
        artwork.execute(() -> {
            try {
                String hash = SmbMedia.hash(current, movie.path());
                List<OnlineServices.Subtitle> matches = hash.isEmpty() ? List.of()
                        : OnlineServices.subtitles(movie.title(), hash, apiKey, lang);
                if (automatic) {
                    OnlineServices.Subtitle match = matches.stream().filter(OnlineServices.Subtitle::exact).findFirst().orElse(null);
                    if (match != null) fetchSubtitle(movie, match, apiKey);
                    runOnUiThread(() -> {
                        if (waiting.isShowing()) { waiting.dismiss(); play(movie); }
                    });
                    return;
                }
                if (matches.isEmpty()) matches = OnlineServices.subtitles(movie.title(), "", apiKey, lang);
                List<OnlineServices.Subtitle> results = matches;
                runOnUiThread(() -> {
                    if (!waiting.isShowing()) return;
                    waiting.dismiss();
                    if (results.isEmpty()) {
                        new AlertDialog.Builder(this).setMessage("暂时找不到匹配的在线字幕。")
                                .setPositiveButton("关闭", null).show();
                        return;
                    }
                    String[] names = results.stream().map(item -> (item.exact() ? "指纹匹配 · " : "")
                            + item.language() + " · " + item.name()).toArray(String[]::new);
                    new AlertDialog.Builder(this).setTitle("选择字幕").setItems(names,
                            (dialog, which) -> downloadAndPlay(movie, results.get(which), apiKey)).show();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (!waiting.isShowing()) return;
                    waiting.dismiss();
                    if (automatic) play(movie);
                    else new AlertDialog.Builder(this).setMessage("字幕服务暂时不可用。检查 API Key 和电视网络。")
                            .setPositiveButton("关闭", null).show();
                });
            }
        });
    }

    private void downloadAndPlay(Catalog.Movie movie, OnlineServices.Subtitle item, String apiKey) {
        AlertDialog waiting = new AlertDialog.Builder(this).setMessage("正在下载字幕…").setNegativeButton("取消", null).show();
        artwork.execute(() -> {
            try {
                fetchSubtitle(movie, item, apiKey);
                runOnUiThread(() -> {
                    if (waiting.isShowing()) {
                        waiting.dismiss();
                        if (player != null) releasePlayer();
                        play(movie);
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (waiting.isShowing()) {
                        waiting.dismiss();
                        new AlertDialog.Builder(this).setMessage("字幕下载失败。检查账号、可用配额和网络。")
                                .setPositiveButton("关闭", null).show();
                    }
                });
            }
        });
    }

    private void fetchSubtitle(Catalog.Movie movie, OnlineServices.Subtitle item, String apiKey) throws Exception {
        File file = new File(getCacheDir(), "subtitle-" + item.fileId() + ".srt");
        if (!file.isFile()) {
            byte[] bytes = OnlineServices.download(item, apiKey, saved.getString("openSubUser", ""), secrets.read("openSubPass"));
            File temporary = File.createTempFile("subtitle", ".tmp", getCacheDir());
            try {
                Files.write(temporary.toPath(), bytes);
                Files.move(temporary.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary.toPath());
            }
        }
        MediaItem.SubtitleConfiguration subtitle = new MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(file))
                .setMimeType(MimeTypes.APPLICATION_SUBRIP).setLanguage(item.language().startsWith("zh") ? "zh" : item.language())
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build();
        runOnUiThread(() -> extraSubtitles.put(movie.path(), List.of(subtitle)));
    }

    private void load(SmbMedia.Source next, String path, Runnable connected) {
        if (loading) return;
        loading = true;
        message("正在读取共享文件夹…");
        network.execute(() -> {
            try {
                List<SmbMedia.Entry> files = SmbMedia.list(next, path);
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    loading = false;
                    if (source != null && source != next) Arrays.fill(source.password(), '\0');
                    source = next;
                    folder = path;
                    entries = files;
                    connected.run();
                    showLibrary();
                });
            } catch (Exception error) {
                Log.w("ShelfPlayer", "SMB directory request failed: " + error.getClass().getSimpleName());
                if (next != source) Arrays.fill(next.password(), '\0');
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    loading = false;
                    message("打开共享失败。检查服务器地址、共享名、账号权限及局域网连接。");
                });
            }
        });
    }

    private void showLibrary() {
        page = "files";
        shell(source.share(), folder.isEmpty() ? "共享根目录" : folder.replace('\\', '/'));
        Button back = button(folder.isEmpty() ? "更换共享" : "上一级", this::up);
        panel.addView(back);
        Button first = null;
        for (SmbMedia.Entry entry : entries) {
            String path = folder.isEmpty() ? entry.name() : folder + "\\" + entry.name();
            Button row = button((entry.directory() ? "›   " : "▷   ") + entry.name(), () -> {
                if (loading) return;
                if (entry.directory()) load(source, path, () -> {});
                else movies.stream().filter(movie -> movie.path().equals(path)).findFirst()
                        .ifPresent(movie -> showDetails(movie, this::showLibrary));
            });
            panel.addView(row);
            if (first == null) first = row;
        }
        if (first != null) first.requestFocus();
        else {
            message("此目录没有可显示的影片。");
            back.requestFocus();
        }
    }

    private void up() {
        if (loading) return;
        if (folder.isEmpty()) {
            showHome();
        } else {
            int separator = folder.lastIndexOf('\\');
            load(source, separator == -1 ? "" : folder.substring(0, separator), () -> {});
        }
    }

    private void play(Catalog.Movie movie) {
        if (isDestroyed() || player != null) return;
        playbackKey = key(movie);
        duration = 0;
        playerView = (PlayerView) getLayoutInflater().inflate(R.layout.player, null);
        DefaultDataSource.Factory data = new DefaultDataSource.Factory(this, () -> new SmbMedia.Input(source));
        player = new ExoPlayer.Builder(this).setMediaSourceFactory(new DefaultMediaSourceFactory(this)
                .setDataSourceFactory(data)).build();
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true);
        playerView.setPlayer(player);
        hud = new PlaybackHud(this, playerView, player, displayName(movie), () -> subtitleDialog(movie));
        mount(hud.root());

        List<MediaItem.SubtitleConfiguration> subtitles = new ArrayList<>();
        for (String path : movie.subtitles()) {
            String suffix = path.substring(path.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            String mime = switch (suffix) {
                case "ass", "ssa" -> MimeTypes.TEXT_SSA;
                case "vtt" -> MimeTypes.TEXT_VTT;
                default -> MimeTypes.APPLICATION_SUBRIP;
            };
            subtitles.add(new MediaItem.SubtitleConfiguration.Builder(Catalog.uri(path)).setMimeType(mime)
                    .setLanguage(Catalog.language(path, movie.filename())).build());
        }
        subtitles.addAll(extraSubtitles.getOrDefault(movie.path(), List.of()));
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                .setPreferredTextLanguages("zh", "zho", "chi", "en").setSelectUndeterminedTextLanguage(true).build());
        player.setMediaItem(new MediaItem.Builder().setUri(Catalog.uri(movie.path()))
                .setSubtitleConfigurations(subtitles).build());
        long resume = saved.getLong(playbackKey, 0);
        if (resume > 0) player.seekTo(resume);
        player.addListener(new Player.Listener() {
            @Override public void onRenderedFirstFrame() {
                Log.i("ShelfPlayer", "First video frame rendered");
            }
            @Override public void onCues(androidx.media3.common.text.CueGroup group) {
                if (!group.cues.isEmpty()) Log.i("ShelfPlayer", "Subtitle cues rendered: " + group.cues.size());
            }
            @Override public void onPlaybackStateChanged(int state) {
                if (player != null && player.getDuration() > 0) duration = player.getDuration();
                if (state == Player.STATE_ENDED) savePosition();
            }
            @Override public void onPlayerError(PlaybackException error) {
                Log.w("ShelfPlayer", "Playback failed: " + error.getErrorCodeName(), error);
                new AlertDialog.Builder(MainActivity.this).setTitle("播放失败")
                        .setMessage("电视未能读取或解码这段影片。请检查网络，或试试带 AAC / Dolby Digital 音轨的文件。\n"
                                + error.getErrorCodeName())
                        .setPositiveButton("返回片库", (dialog, which) -> stopPlayback())
                        .setOnCancelListener(dialog -> stopPlayback()).show();
            }
        });
        player.prepare();
        player.play();
    }

    private void diagnostics() {
        String details = Capabilities.read(this);
        Log.i("ShelfCapabilities", details);
        new AlertDialog.Builder(this).setTitle("播放诊断").setMessage(details)
                .setPositiveButton("关闭", null).show();
    }

    private void savePosition() {
        if (player == null || player.getPlaybackState() == Player.STATE_IDLE) return;
        long pos = player.getCurrentPosition();
        if (player.getPlaybackState() == Player.STATE_ENDED) pos = 0;
        saved.edit().putLong(playbackKey, pos).putLong("recent:" + playbackKey, System.currentTimeMillis())
                .putLong("duration:" + playbackKey, duration).apply();
    }

    private void stopPlayback() {
        releasePlayer();
        if (selected != null) showDetails(selected, leaveDetails);
        else showHome();
    }

    private void releasePlayer() {
        if (player == null) return;
        savePosition();
        hud.close();
        hud = null;
        playerView.setPlayer(null);
        player.release();
        player = null;
        playerView = null;
    }

    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        return hud != null && hud.key(event) || super.dispatchKeyEvent(event);
    }

    @Override public void onBackPressed() {
        if (player != null) stopPlayback();
        else if (page.equals("files")) up();
        else if (page.equals("details") && leaveDetails != null) leaveDetails.run();
        else if (!page.equals("home")) showHome();
        else if (!loading) super.onBackPressed();
    }

    @Override protected void onPause() {
        if (player != null) {
            player.pause();
            savePosition();
        }
        super.onPause();
    }

    @Override protected void onDestroy() {
        releasePlayer();
        network.shutdownNow();
        artwork.shutdownNow();
        if (source != null) Arrays.fill(source.password(), '\0');
        super.onDestroy();
    }

    private void message(String text) {
        status.setText(text);
        status.setVisibility(View.VISIBLE);
    }

    private EditText field(String hint, String value, boolean secret) {
        EditText input = new EditText(this);
        input.setSingleLine();
        input.setTextSize(15);
        input.setTextColor(PAPER);
        input.setHintTextColor(QUIET);
        input.setHint(hint);
        input.setText(value);
        input.setPadding(dp(12), 0, dp(12), 0);
        input.setInputType(InputType.TYPE_CLASS_TEXT | (secret ? InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
        StateListDrawable background = new StateListDrawable();
        GradientDrawable focused = rounded(INK, 6);
        focused.setStroke(dp(2), AMBER);
        background.addState(new int[]{android.R.attr.state_focused}, focused);
        background.addState(new int[]{}, rounded(INK, 6));
        input.setBackground(background);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, dp(40));
        layout.bottomMargin = dp(4);
        panel.addView(input, layout);
        return input;
    }

    private Button button(String title, Runnable action) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setPadding(dp(12), 0, dp(10), 0);
        button.setSingleLine();
        button.setEllipsize(android.text.TextUtils.TruncateAt.END);
        button.setFocusable(true);
        int[][] states = {{android.R.attr.state_focused}, {}};
        button.setTextColor(new ColorStateList(states, new int[]{INK, PAPER}));
        StateListDrawable background = new StateListDrawable();
        background.addState(states[0], rounded(AMBER, 7));
        background.addState(states[1], rounded(Color.TRANSPARENT, 7));
        button.setBackground(background);
        button.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(37)));
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private TextView label(String content, int size, int color) {
        TextView label = new TextView(this);
        label.setText(content);
        label.setTextSize(size);
        label.setTextColor(color);
        return label;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
