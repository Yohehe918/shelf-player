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

@androidx.media3.common.util.UnstableApi
public final class MainActivity extends Activity {
    private static final int GRID_PAGE_SIZE = 24;
    private static final int INK = 0xff101621;
    private static final int CARD = 0xff1b2431;
    private static final int PAPER = 0xfff3eee8;
    private static final int QUIET = 0xffaeb9cb;
    private static final int AMBER = 0xffe7be8e;
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final ExecutorService metadataWorker = Executors.newSingleThreadExecutor();
    private java.util.concurrent.Future<?> metadataRefresh;
    private final ExecutorService artwork = Executors.newFixedThreadPool(3);
    private final LruCache<String, Bitmap> images = new LruCache<>(24 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };
    // Main-thread waiters share a decode; weak references do not retain discarded pages.
    private final Map<String, ArtworkLoad> pendingArt = new HashMap<>();

    private static final class ArtworkLoad {
        final List<ArtworkTarget> targets = new ArrayList<>();
        volatile boolean wanted = true;
    }

    private final class ArtworkTarget implements View.OnAttachStateChangeListener {
        final java.lang.ref.WeakReference<ImageView> view;
        final ArtworkLoad load;
        final String key;

        ArtworkTarget(ImageView view, ArtworkLoad load, String key) {
            this.view = new java.lang.ref.WeakReference<>(view);
            this.load = load;
            this.key = key;
        }

        @Override public void onViewAttachedToWindow(View view) {}

        @Override public void onViewDetachedFromWindow(View view) {
            view.removeOnAttachStateChangeListener(this);
            load.targets.remove(this);
            if (load.targets.isEmpty()) {
                load.wanted = false;
                pendingArt.remove(key, load);
            }
        }
    }
    private volatile boolean destroyed;
    private final Map<String, OnlineServices.Metadata> metadata = new HashMap<>();
    private final Map<String, String> normalizedSeriesNames = new HashMap<>();
    private final Map<String, List<MediaItem.SubtitleConfiguration>> extraSubtitles = new HashMap<>();
    private List<Catalog.Movie> movies = List.of();
    private long libraryRevision;
    private GridSession activeGrid;
    private int gridOffset;
    private int pendingGridOffset;
    private GridSession pendingGrid;
    private final Runnable flushGridPage = () -> {
        GridSession requested = pendingGrid;
        pendingGrid = null;
        if (requested == activeGrid && requested != null && this.page.equals("grid")) {
            requested.show(pendingGridOffset);
        }
    };
    private AlertDialog playbackHelp;
    private SharedPreferences saved;
    private LibraryStore store;
    private Runnable pageBack;
    private final android.os.Handler progressHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable progressTick=new Runnable(){public void run(){if(player!=null){if(player.isPlaying())savePosition();progressHandler.postDelayed(this,5000);}}};
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
    private SceneHost scenes;
    private String playbackKey;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        saved = getSharedPreferences("shelf", MODE_PRIVATE);
        secrets = new Secrets(saved);
        store=new LibraryStore(this);
        scenes=new SceneHost(this);
        setContentView(scenes);
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
        scroll.setSmoothScrollingEnabled(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(panel);
        status = label("", 12, AMBER);
        status.setPadding(dp(5), dp(4), dp(5), 0);
        status.setVisibility(View.GONE);
        content.addView(status);
        mount(screen, page.equals("files")?"files:"+folder:page, page.equals("settings")?1:page.equals("files")?1+folder.split("\\\\").length:2);
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
                List<Catalog.Movie> cached=store.library(next.identity());
                Map<String,OnlineServices.Metadata> remembered=store.metadata(next.identity());
                if(!cached.isEmpty())postUi(()->{if(isDestroyed())return;if(source==null||!source.identity().equals(next.identity())){source=next;movies=cached;libraryRevision++;normalizedSeriesNames.clear();metadata.clear();metadata.putAll(remembered);if(page.equals("home"))showHome();}});
                List<Catalog.Movie> indexed = SmbMedia.index(next);
                store.saveLibrary(next.identity(),indexed);
                postUi(() -> {
                    if (isDestroyed()) return;
                    if (source != null && !source.identity().equals(next.identity())) {
                        metadata.clear();
                        extraSubtitles.clear();
                    }
                    source = next;
                    movies = indexed;
                    libraryRevision++;
                    normalizedSeriesNames.clear();
                    remembered.forEach(metadata::putIfAbsent);
                    loading = false;
                    libraryMessage = "";
                    if (connected != null) connected.run();
                    if(player==null&&(page.equals("home")||page.equals("connect")))showHome();
                });
                if (!tmdb.isEmpty()) postUi(() -> {
                    if (metadataRefresh != null) metadataRefresh.cancel(true);
                    metadataRefresh = metadataWorker.submit(() -> {
                        fetchMetadata(next, indexed.stream().filter(Catalog.Movie::bluRay).toList(), tmdb);
                        if (!Thread.currentThread().isInterrupted()) {
                            fetchMetadata(next, indexed.stream().filter(movie -> !movie.bluRay()).toList(), tmdb);
                        }
                    });
                });
            } catch (Exception error) {
                Log.w("ShelfPlayer", "SMB indexing failed: " + error.getClass().getSimpleName());
                postUi(() -> {
                    if (isDestroyed()) return;
                    loading = false;
                    libraryMessage = movies.isEmpty()?"连接失败。请检查共享名、账号权限和 NAS 网络。":"共享暂不可用，正在显示上次保存的片库。";
                    if (page.equals("connect")) message(libraryMessage);
                    else if (page.equals("home")) showHome();
                });
            }
        });
    }

    private void fetchMetadata(SmbMedia.Source current, List<Catalog.Movie> indexed, String token) {
        if (indexed.isEmpty()) return;
        Map<String,OnlineServices.Metadata> existing=store.metadata(current.identity());
        Map<String,OnlineServices.Metadata> found=new HashMap<>(),lookups=new HashMap<>();
        for(Catalog.Movie movie:indexed){
            if(Thread.currentThread().isInterrupted())return;
            if(existing.containsKey(movie.metadataKey()))continue;
            try{
                Catalog.Title lookupTitle=movie.metadataTitle();
                String lookup=(lookupTitle.series()?"tv:":"movie:")
                        +TitleMatching.literal(lookupTitle.name())+"|"+lookupTitle.year();
                if(!lookups.containsKey(lookup)&&!(movie.title().series()&&existing.containsKey("@series:"+SeriesIndex.group(movie))))lookups.put(lookup,OnlineServices.movie(lookupTitle,token));
                OnlineServices.Metadata result=movie.title().series()&&existing.containsKey("@series:"+SeriesIndex.group(movie))?existing.get("@series:"+SeriesIndex.group(movie)):lookups.get(lookup);
                if(result!=null && !Thread.currentThread().isInterrupted()){store.put(current.identity(),movie.metadataKey(),result,false);found.put(movie.metadataKey(),result);}
            }catch(Exception error){Log.w("ShelfPlayer","TMDB request failed: "+error.getClass().getSimpleName());break;}
        }
        postUi(() -> {
            if (current != source || found.isEmpty()) return;
            found.forEach(metadata::putIfAbsent);
            libraryRevision++;
            if (page.equals("home")) showHome();
        });
    }

    private void showHome() {
        scenes.removeCallbacks(flushGridPage);
        pendingGrid = null;
        activeGrid = null;
        pageBack = null;
        leaveDetails = null;
        page = "home";
        LibraryView view = new LibraryView(this,this::displayName);
        view.navigation(this::search, () -> {
            if (source == null) showConnection();
            else scan(source, null);
        }, this::showSettings);
        List<Catalog.Movie> watching = filmRepresentatives(movies.stream().filter(movie -> resume(movie) > 0)
                .sorted(Comparator.comparingLong((Catalog.Movie movie) -> saved.getLong("recent:" + key(movie), 0)).reversed())
                .toList());
        if(!libraryMessage.isEmpty()&&!movies.isEmpty())view.heading(libraryMessage);
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
            view.heading("浏览影片");
            view.railMovies(collapsed(movies), false, movie -> browse(movie, this::showHome), this::loadArt, this::progress);
        }
        mount(view.root());
    }

    private void grid(String heading, Predicate<Catalog.Movie> filter, int index) {
        new GridSession(heading, filter).show(index);
    }

    private final class GridSession {
        final String heading;
        final Predicate<Catalog.Movie> filter;
        final List<Catalog.Movie> items;
        final long revision = libraryRevision;
        final java.util.LinkedHashMap<Integer, View> pages = new java.util.LinkedHashMap<>(4, .75f, true);

        GridSession(String heading, Predicate<Catalog.Movie> filter) {
            this.heading = heading;
            this.filter = filter;
            items = collapsed(movies.stream().filter(filter).toList()).stream()
                    .sorted(Comparator.comparing(movie -> movie.title().name(), String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }

        void show(int requested) {
            if (revision != libraryRevision) {
                grid(heading, filter, requested);
                return;
            }
            int last = Math.max(0, items.size() - 1) / GRID_PAGE_SIZE * GRID_PAGE_SIZE;
            int index = Math.min(last, Math.max(0, requested));
            if (page.equals("grid") && activeGrid == this && gridOffset == index) return;
            View screen = pages.get(index);
            if (screen == null) {
                LibraryView view = new LibraryView(MainActivity.this, MainActivity.this::displayName);
                view.gridStyle();
                view.heading(heading);
                if (items.isEmpty()) view.empty("这里还没有影片", "连接共享或切换片库分类。", "返回首页", MainActivity.this::showHome);
                if (index > 0) view.nextPage("上一页", () -> show(index - GRID_PAGE_SIZE));
                view.gridMovies(items.subList(index, Math.min(items.size(), index + GRID_PAGE_SIZE)),
                        movie -> browse(movie, () -> show(index)), MainActivity.this::loadArt, MainActivity.this::progress);
                if (index + GRID_PAGE_SIZE < items.size()) view.nextPage("下一页", () -> show(index + GRID_PAGE_SIZE));
                screen = view.root();
                pages.put(index, screen);
                if (pages.size() > 3) pages.remove(pages.keySet().iterator().next());
            }
            activeGrid = this;
            gridOffset = index;
            pageBack = null;
            leaveDetails = null;
            page = "grid";
            mount(screen, "grid:" + heading + ":" + index, 1);
        }
    }

    private long resume(Catalog.Movie m){return ResumePolicy.resume(saved.getLong(key(m),0),saved.getLong("duration:"+key(m),0),watched(m));}
    private boolean watched(Catalog.Movie m){return saved.getBoolean("watched:"+key(m),false);}
    private List<Catalog.Movie> collapsed(List<Catalog.Movie> all) {
        List<Catalog.Movie> out = new ArrayList<>(filmRepresentatives(all.stream()
                .filter(movie -> !movie.title().series()).toList()));
        out.addAll(SeriesIndex.representatives(all, this::seriesKey));
        return out;
    }

    private int filmId(Catalog.Movie movie) {
        if (movie.title().series()) return 0;
        OnlineServices.Metadata info = matched(movie);
        return info != null && "movie".equals(info.type()) ? info.id() : 0;
    }

    private List<Catalog.Movie> filmRepresentatives(List<Catalog.Movie> items) {
        return MovieVersions.collapse(items, this::filmId, this::compareVersions);
    }

    private int compareVersions(Catalog.Movie first, Catalog.Movie second) {
        int resumed = Boolean.compare(resume(second) > 0, resume(first) > 0);
        if (resumed != 0) return resumed;
        if (resume(first) > 0) {
            int recent = Long.compare(saved.getLong("recent:" + key(second), 0),
                    saved.getLong("recent:" + key(first), 0));
            if (recent != 0) return recent;
        }
        int favored = Boolean.compare(favorite(second), favorite(first));
        if (favored != 0) return favored;
        int disc = Boolean.compare(first.bluRay(), second.bluRay());
        return disc != 0 ? disc : first.path().compareTo(second.path());
    }

    private List<Catalog.Movie> versions(Catalog.Movie movie) {
        int id = filmId(movie);
        return id <= 0 ? List.of(movie) : movies.stream().filter(item -> filmId(item) == id)
                .sorted(this::compareVersions).toList();
    }

    private void showVersions(Catalog.Movie movie, Runnable back) {
        List<Catalog.Movie> options = versions(movie);
        if (options.size() < 2) return;
        String[] labels = options.stream().map(item -> {
            String file = item.filename();
            String suffix = file.substring(file.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT);
            String kind = item.bluRay() ? "蓝光原盘" : suffix;
            if (!item.discNumber().isEmpty()) kind += " · 碟 " + item.discNumber();
            return kind + " · " + file + (item.path().equals(movie.path()) ? " · 当前" : "");
        }).toArray(String[]::new);
        new MotionDialog(this).setTitle("选择版本").setItems(labels, (dialog, index) ->
                showDetails(options.get(index), back)).setNegativeButton("取消", null).show();
    }
    private void browse(Catalog.Movie movie,Runnable back){if(movie.title().series())showSeries(movie,back);else showDetails(movie,back);}
    private void showSeries(Catalog.Movie movie,Runnable back){
        page="series";pageBack=back;LibraryView view=new LibraryView(this,this::displayName);view.heading(displayName(movie));
        List<Catalog.Movie> episodes=SeriesIndex.episodes(movies,movie,this::seriesKey);
        Catalog.Movie next=episodes.stream().filter(m->resume(m)>0).findFirst()
                .orElseGet(()->episodes.stream().filter(m->!watched(m)).findFirst().orElse(movie));
        view.nextPage((resume(next)>0?"继续观看":"播放")+" · "+next.title().caption(),()->{leaveDetails=()->showSeries(movie,back);startMovie(next);});
        for(int season:episodes.stream().map(m->m.title().season()).distinct().toList()){
            List<Catalog.Movie> group=episodes.stream().filter(m->m.title().season()==season).toList();
            view.heading((season==0?"特别篇":"第 "+season+" 季")+" · "+group.size()+" 个文件 · 已看 "+group.stream().filter(this::watched).count());
            view.nextPage("查看全部集数",()->showSeason(movie,season,back,0));
            view.railMovies(group,false,m->showDetails(m,()->showSeries(movie,back)),this::loadArt,this::progress);
        }
        mount(view.root(),"series:"+seriesKey(movie),2);
    }
    private void showSeason(Catalog.Movie series,int season,Runnable back,int start){
        page="season";pageBack=()->showSeries(series,back);LibraryView view=new LibraryView(this,this::displayName);
        List<Catalog.Movie> items=SeriesIndex.episodes(movies,series,this::seriesKey).stream().filter(m->m.title().season()==season).toList();
        view.heading(displayName(series)+" · "+(season==0?"特别篇":"第 "+season+" 季"));
        if(start>0)view.nextPage("上一页",()->showSeason(series,season,back,Math.max(0,start-GRID_PAGE_SIZE)));
        view.gridMovies(items.subList(Math.min(start,items.size()),Math.min(start+GRID_PAGE_SIZE,items.size())),m->showDetails(m,()->showSeason(series,season,back,start)),this::loadArt,this::progress);
        if(start+GRID_PAGE_SIZE<items.size())view.nextPage("下一页",()->showSeason(series,season,back,start+GRID_PAGE_SIZE));mount(view.root(),"season:"+seriesKey(series)+":"+season+":"+start,3);
    }
    private void stepEpisode(Catalog.Movie movie,boolean forward){Catalog.Movie next=SeriesIndex.adjacent(movies,movie,forward,this::seriesKey);if(next==null)return;Runnable back=leaveDetails;releasePlayer();showDetails(next,back==null?this::showHome:back);startMovie(next);}
    private void detailActions(Catalog.Movie movie,Runnable back){
        String[] choices={"从头播放",watched(movie)?"标记为未看":"标记为已看","修正影片匹配",movie.title().series()?"恢复整部剧的自动匹配":"恢复自动匹配","修改显示标题"};
        new MotionDialog(this).setTitle("影片管理").setItems(choices,(d,which)->{
            if(which==0){libraryRevision++;saved.edit().putLong(key(movie),0).apply();startMovie(movie);}
            else if(which==1){libraryRevision++;saved.edit().putBoolean("watched:"+key(movie),!watched(movie)).putLong(key(movie),0).apply();showDetails(movie,back);}
            else if(which==2)correctMatch(movie,back);
            else if(which==4)renameDisplayTitle(movie,back);
            else{libraryRevision++;List<Catalog.Movie> targets=matchingTargets(movie);SmbMedia.Source origin=source;String id=origin.identity();targets.forEach(m->metadata.remove(m.metadataKey()));if(movie.title().series())metadata.remove("@series:"+SeriesIndex.group(movie));metadataWorker.execute(()->{List<String> resetPaths=new ArrayList<>(targets.stream().map(Catalog.Movie::metadataKey).toList());if(movie.title().series())resetPaths.add("@series:"+SeriesIndex.group(movie));store.forget(id,resetPaths);String token=secrets.read("tmdb");if(!token.isEmpty())fetchMetadata(origin,targets,token);});showDetails(movie,back);}
        }).show();
    }
    private void renameDisplayTitle(Catalog.Movie movie, Runnable back) {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        String preference = "display:" + key(movie);
        field.setText(saved.getString(preference, displayName(movie)));
        field.setSelectAllOnFocus(true);
        new MotionDialog(this).setTitle("修改显示标题").setView(field)
                .setPositiveButton("保存", (dialog, which) -> {
                    libraryRevision++;
                    String name = field.getText().toString().trim();
                    if (name.isEmpty()) saved.edit().remove(preference).apply();
                    else saved.edit().putString(preference, name).apply();
                    showDetails(movie, back);
                }).setNeutralButton("恢复自动标题", (dialog, which) -> {
                    libraryRevision++;
                    saved.edit().remove(preference).apply();
                    showDetails(movie, back);
                }).setNegativeButton("取消", null).show();
    }

    private List<Catalog.Movie> matchingTargets(Catalog.Movie movie){return movie.title().series()?SeriesIndex.episodes(movies,movie):List.of(movie);}
    private void correctMatch(Catalog.Movie movie,Runnable back){
        String token=secrets.read("tmdb");if(token.isEmpty()){new MotionDialog(this).setMessage("请先在在线匹配设置中填写自己的 TMDB Token。").setPositiveButton("设置",(d,w)->showServices()).setNegativeButton("取消",null).show();return;}
        EditText query=new EditText(this);query.setSingleLine();query.setText(movie.metadataTitle().name());
        new MotionDialog(this).setTitle(movie.title().series()?"修正整部剧的匹配":"修正电影匹配").setView(query).setPositiveButton("搜索",(d,w)->{
            SmbMedia.Source origin=source;String text=query.getText().toString().trim();if(text.isEmpty())return;
            AlertDialog waiting=new MotionDialog(this).setMessage("正在搜索影片资料…").setNegativeButton("取消",null).show();
            artwork.execute(()->{try{List<OnlineServices.Metadata> found=OnlineServices.search(text,movie.metadataTitle().series(),token);postUi(()->{
                if(isDestroyed()||source!=origin||!waiting.isShowing())return;waiting.dismiss();
                if(found.isEmpty()){new MotionDialog(this).setMessage("没有找到结果，请换一个片名。").setPositiveButton("关闭",null).show();return;}
                new MotionDialog(this).setTitle("选择正确的影片").setItems(found.stream().map(m->m.name()+" · "+m.year()+" · TMDB "+m.id()).toArray(String[]::new),(choice,i)->applyMatch(movie,back,origin,found.get(i),token)).show();
            });}catch(Exception e){postUi(()->{if(waiting.isShowing()){waiting.dismiss();new MotionDialog(this).setMessage("搜索失败，请检查 Token 和网络。").setPositiveButton("关闭",null).show();}});}});
        }).setNegativeButton("取消",null).show();
    }
    private void applyMatch(Catalog.Movie movie,Runnable back,SmbMedia.Source origin,OnlineServices.Metadata candidate,String token){
        List<Catalog.Movie> targets=matchingTargets(movie);AlertDialog waiting=new MotionDialog(this).setMessage("正在保存匹配结果…").setCancelable(false).show();
        artwork.execute(()->{try{OnlineServices.Metadata result=OnlineServices.details(candidate,token);if(movie.title().series())store.put(origin.identity(),"@series:"+SeriesIndex.group(movie),result,true);for(Catalog.Movie target:targets)store.put(origin.identity(),target.metadataKey(),result,true);
            postUi(()->{waiting.dismiss();if(isDestroyed()||source!=origin)return;libraryRevision++;if(movie.title().series())metadata.put("@series:"+SeriesIndex.group(movie),result);targets.forEach(m->metadata.put(m.metadataKey(),result));showDetails(movie,back);});
        }catch(Exception e){postUi(()->{waiting.dismiss();if(!isDestroyed())new MotionDialog(this).setMessage("资料暂时无法保存，请稍后重试。").setPositiveButton("关闭",null).show();});}});
    }
    private void licenses(){
        String[] files={"THIRD-PARTY-NOTICES.txt","GPL-3.0.txt","Just-Player-Pro-COPYRIGHT.txt","Kinema-MIT.txt","NOVA-Apache-2.0.txt"};
        new MotionDialog(this).setTitle("Shelf 0.4.5 · 开源许可").setItems(files,(d,w)->{try(var in=getAssets().open("licenses/"+files[w])){new MotionDialog(this).setTitle(files[w]).setMessage(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).setPositiveButton("关闭",null).show();}catch(Exception e){android.widget.Toast.makeText(this,"许可文本暂不可用",android.widget.Toast.LENGTH_SHORT).show();}}).show();
    }

    private void search() {
        EditText field = new EditText(this);
        field.setSingleLine();
        field.setHint("输入片名");
        new MotionDialog(this).setTitle("搜索片库").setView(field)
                .setPositiveButton("搜索", (dialog, which) -> {
                    String query = field.getText().toString().trim().toLowerCase(Locale.ROOT);
                    grid("搜索结果", movie -> displayName(movie).toLowerCase(Locale.ROOT).contains(query)
                            || movie.title().name().toLowerCase(Locale.ROOT).contains(query)
                            || movie.filename().toLowerCase(Locale.ROOT).contains(query), 0);
                }).setNegativeButton("取消", null).show();
    }

    private void showSettings() {
        page = "settings";
        shell("设置", "共享、影片资料与字幕。");
        panel.addView(button("SMB 共享", this::showConnection));
        panel.addView(button("在线匹配", this::showServices));
        panel.addView(button("连续播放下一集："+(saved.getBoolean("autoplay",false)?"开启":"关闭"),()->{saved.edit().putBoolean("autoplay",!saved.getBoolean("autoplay",false)).apply();showSettings();}));
        panel.addView(button("减少动态效果："+(saved.getBoolean("reduceMotion",false)?"开启":"关闭"),()->{saved.edit().putBoolean("reduceMotion",!saved.getBoolean("reduceMotion",false)).apply();showSettings();}));
        panel.addView(button("开源许可与版本 0.4.5",this::licenses));
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
        long resume = resume(movie);
        OnlineServices.Metadata match = matched(movie);
        LibraryView view = new LibraryView(this,this::displayName);
        view.details(movie, displayName(movie), match == null ? "" : match.facts()+"\n\n"+match.overview(),
                resume > 0 ? "继续播放  " + formatTime(resume) : "播放",
                () -> startMovie(movie), () -> {
                    libraryRevision++;
                    saved.edit().putBoolean("favorite:" + key(movie), !favorite(movie)).apply();
                    showDetails(movie, back);
                }, favorite(movie), () -> subtitleDialog(movie), ()->detailActions(movie,back),
                versions(movie).size(), () -> showVersions(movie, back), this::loadArt);
        mount(view.root(),"details:"+key(movie),4);
    }

    private String key(Catalog.Movie movie) {
        return source.identity() + "/" + movie.path();
    }

    private OnlineServices.Metadata matched(Catalog.Movie movie){OnlineServices.Metadata exact=metadata.get(movie.metadataKey());return exact!=null?exact:movie.title().series()?metadata.get("@series:"+SeriesIndex.group(movie)):null;}

    private String seriesKey(Catalog.Movie movie) {
        OnlineServices.Metadata match = matched(movie);
        if (match != null && "tv".equals(match.type()) && match.id() > 0) return "tmdb:" + match.id();
        return normalizedSeriesNames.computeIfAbsent(movie.title().name(), TitleMatching::literal)
                + "|" + movie.title().year();
    }

    private String displayName(Catalog.Movie movie) {
        String custom = saved.getString("display:" + key(movie), "");
        if (!custom.isBlank()) return custom;
        OnlineServices.Metadata match = matched(movie);
        String name = match != null && !match.name().isBlank() ? match.name() : Catalog.cleanDisplayName(movie.title().name());
        String disc = movie.discNumber();
        return disc.isEmpty() ? name : name + " · 碟 " + disc;
    }

    private boolean favorite(Catalog.Movie movie) {
        return saved.getBoolean("favorite:" + key(movie), false);
    }

    private int progress(Catalog.Movie movie) {
        if(watched(movie))return 100;
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
        OnlineServices.Metadata match = matched(movie);
        String local = wide ? (movie.backdrop() == null ? movie.poster() : movie.backdrop()) : movie.poster();
        String url = match == null ? null : (wide ? match.backdrop() : match.poster());
        if (local == null && url == null) return;
        String assetKey = current.identity() + "|" + (local != null ? local : url);
        int edge = Math.max(view.getWidth(), view.getHeight());
        int decodeEdge = edge == 0 ? (wide ? 1280 : 384) : Math.max(320, Math.min(edge, 1280));
        String cacheKey = assetKey + "|" + decodeEdge;
        Bitmap cached = images.get(cacheKey);
        if (cached != null) {
            applyArt(view, cached);
            return;
        }
        ArtworkLoad existing = pendingArt.get(cacheKey);
        if (existing != null) {
            if (existing.targets.stream().noneMatch(target -> target.view.get() == view)) {
                observeArtwork(view, existing, cacheKey);
            }
            return;
        }
        ArtworkLoad request = new ArtworkLoad();
        observeArtwork(view, request, cacheKey);
        pendingArt.put(cacheKey, request);
        artwork.execute(() -> {
            Bitmap decoded = null;
            try {
                if (destroyed || !request.wanted || Thread.currentThread().isInterrupted()) return;
                byte[] bytes = ArtCache.read(getCacheDir(),assetKey);
                boolean downloaded=bytes==null;
                if (downloaded && request.wanted) {
                    bytes = local != null ? SmbMedia.readAsset(current, local) : OnlineServices.image(url);
                }
                if (!request.wanted || bytes == null) return;
                BitmapFactory.Options size = new BitmapFactory.Options();
                size.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, size);
                BitmapFactory.Options decode = new BitmapFactory.Options();
                decode.inSampleSize = 1;
                while (Math.max(size.outWidth, size.outHeight) / (decode.inSampleSize * 2) >= decodeEdge) {
                    decode.inSampleSize *= 2;
                }
                Bitmap result = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, decode);
                if (result == null) {
                    Log.w("ShelfPlayer", "Artwork bytes could not be decoded");
                    return;
                }
                if(downloaded)ArtCache.write(getCacheDir(),assetKey,bytes);
                decoded = result;
            } catch (Exception error) {
                Log.w("ShelfPlayer", "Artwork read failed: " + error.getClass().getSimpleName());
            } finally {
                Bitmap result = decoded;
                postUi(() -> {
                    if (!pendingArt.remove(cacheKey, request)) return;
                    if (result != null) images.put(cacheKey, result);
                    for (ArtworkTarget target : request.targets) {
                        ImageView image = target.view.get();
                        if (image == null) continue;
                        image.removeOnAttachStateChangeListener(target);
                        if (result != null && image.isAttachedToWindow()) applyArt(image, result);
                    }
                });
            }
        });
    }

    private void observeArtwork(ImageView view, ArtworkLoad request, String cacheKey) {
        ArtworkTarget target = new ArtworkTarget(view, request, cacheKey);
        request.targets.add(target);
        view.addOnAttachStateChangeListener(target);
    }

    private void applyArt(ImageView view, Bitmap bitmap) {
        view.setImageBitmap(bitmap);
        if (view.getTag() instanceof View label) label.setVisibility(View.GONE);
    }

    private void mount(View screen) { mount(screen,page,0); }

    private void mount(View screen,String route,int depth) {
        scenes.show(screen,route,depth,false);
        screen.post(this::fullScreen);
    }

    private String subtitleLanguages() {
        String lang = saved.getString("subLang", "");
        return lang.isBlank() ? "zh-cn,zh-tw,en" : lang;
    }

    private void subtitleDialog(Catalog.Movie movie) {
        String apiKey = secrets.read("openSubKey");
        if (apiKey.isEmpty()) {
            new MotionDialog(this).setTitle("字幕").setMessage(movie.subtitles().isEmpty()
                    ? "同目录字幕会自动匹配。可在设置中接入 OpenSubtitles 搜索在线字幕。"
                    : "已匹配同目录字幕 " + movie.subtitles().size() + " 个。播放时可切换字幕。")
                    .setPositiveButton("关闭", null).setNeutralButton("在线匹配设置", (dialog, which) -> showServices()).show();
            return;
        }
        findSubtitles(movie, apiKey, false);
    }

    private void startMovie(Catalog.Movie movie) {
        restoreSubtitle(movie);
        String key = secrets.read("openSubKey");
        if (!key.isEmpty() && movie.subtitles().isEmpty() && !extraSubtitles.containsKey(movie.path()))
            findSubtitles(movie, key, true);
        else play(movie);
    }

    private void findSubtitles(Catalog.Movie movie, String apiKey, boolean automatic) {
        SmbMedia.Source current = source;
        String lang = subtitleLanguages();
        AlertDialog waiting = new MotionDialog(this).setMessage("正在匹配字幕…").setNegativeButton("取消", null).show();
        artwork.execute(() -> {
            try {
                String hash = SmbMedia.hash(current, movie.path());
                List<OnlineServices.Subtitle> matches = hash.isEmpty() ? List.of()
                        : OnlineServices.subtitles(movie.title(), hash, apiKey, lang);
                if (automatic) {
                    OnlineServices.Subtitle match = matches.stream().filter(OnlineServices.Subtitle::exact).findFirst().orElse(null);
                    if (match != null) fetchSubtitle(movie, match, apiKey);
                    postUi(() -> {
                        if (waiting.isShowing()) { waiting.dismiss(); play(movie); }
                    });
                    return;
                }
                if (matches.isEmpty()) matches = OnlineServices.subtitles(movie.title(), "", apiKey, lang);
                List<OnlineServices.Subtitle> results = matches;
                postUi(() -> {
                    if (!waiting.isShowing()) return;
                    waiting.dismiss();
                    if (results.isEmpty()) {
                        new MotionDialog(this).setMessage("暂时找不到匹配的在线字幕。")
                                .setPositiveButton("关闭", null).show();
                        return;
                    }
                    String[] names = results.stream().map(item -> (item.exact() ? "指纹匹配 · " : "")
                            + item.language() + " · " + item.name()).toArray(String[]::new);
                    new MotionDialog(this).setTitle("选择字幕").setItems(names,
                            (dialog, which) -> downloadAndPlay(movie, results.get(which), apiKey)).show();
                });
            } catch (Exception error) {
                postUi(() -> {
                    if (!waiting.isShowing()) return;
                    waiting.dismiss();
                    if (automatic) play(movie);
                    else new MotionDialog(this).setMessage("字幕服务暂时不可用。检查 API Key 和电视网络。")
                            .setPositiveButton("关闭", null).show();
                });
            }
        });
    }

    private void downloadAndPlay(Catalog.Movie movie, OnlineServices.Subtitle item, String apiKey) {
        AlertDialog waiting = new MotionDialog(this).setMessage("正在下载字幕…").setNegativeButton("取消", null).show();
        artwork.execute(() -> {
            try {
                fetchSubtitle(movie, item, apiKey);
                postUi(() -> {
                    if (waiting.isShowing()) {
                        waiting.dismiss();
                        if (player != null) releasePlayer();
                        play(movie);
                    }
                });
            } catch (Exception error) {
                postUi(() -> {
                    if (waiting.isShowing()) {
                        waiting.dismiss();
                        new MotionDialog(this).setMessage("字幕下载失败。检查账号、可用配额和网络。")
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
        saved.edit().putString("downloaded:"+key(movie),item.fileId()+"|"+item.language()).apply();
        postUi(() -> extraSubtitles.put(movie.path(), List.of(subtitle)));
    }

    private void restoreSubtitle(Catalog.Movie movie){
        if(extraSubtitles.containsKey(movie.path()))return;
        String[] cached=saved.getString("downloaded:"+key(movie),"").split("[|]",2);
        if(cached.length!=2||!cached[0].matches("[0-9]+"))return;
        File file=new File(getCacheDir(),"subtitle-"+cached[0]+".srt");if(!file.isFile())return;
        extraSubtitles.put(movie.path(),List.of(new MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(file)).setMimeType(MimeTypes.APPLICATION_SUBRIP).setLanguage(cached[1].startsWith("zh")?"zh":cached[1]).setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()));
    }

    private void load(SmbMedia.Source next, String path, Runnable connected) {
        if (loading) return;
        loading = true;
        message("正在读取共享文件夹…");
        network.execute(() -> {
            try {
                List<SmbMedia.Entry> files = SmbMedia.list(next, path);
                postUi(() -> {
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
                postUi(() -> {
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
        selected=movie;
        playbackKey = key(movie);
        duration = 0;
        playerView = (PlayerView) getLayoutInflater().inflate(R.layout.player, null);
        SmbMedia.Source playingSource=source;
        DefaultDataSource.Factory data = new DefaultDataSource.Factory(this, () -> new SmbMedia.Input(playingSource));
        androidx.media3.exoplayer.DefaultLoadControl loadControl = new androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMsForStreaming(15000, 35000, 750, 2500).build();
        player = new ExoPlayer.Builder(this).setMediaSourceFactory(new DefaultMediaSourceFactory(this)
                .setDataSourceFactory(data)).setLoadControl(loadControl).build();
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true);
        playerView.setPlayer(player);
        hud = new PlaybackHud(this, playerView, player, displayName(movie), () -> subtitleDialog(movie),
            SeriesIndex.adjacent(movies,movie,false,this::seriesKey)==null?null:()->stepEpisode(movie,false),
            SeriesIndex.adjacent(movies,movie,true,MainActivity.this::seriesKey)==null?null:()->stepEpisode(movie,true));
        scenes.show(hud.root(),"playback:"+key(movie),5,true);
        hud.root().post(this::fullScreen);

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
        long resume = resume(movie);
        if (resume > 0) player.seekTo(resume);
        ExoPlayer activePlayer = player;
        long startup = android.os.SystemClock.elapsedRealtime();
        Runnable showSlowStart = () -> {
            if (player == activePlayer && getWindow().getDecorView().isShown()) showPlaybackHelp(movie);
        };
        player.addListener(new Player.Listener() {
            @Override public void onRenderedFirstFrame() {
                if (destroyed || player != activePlayer) return;
                progressHandler.removeCallbacks(showSlowStart);
                dismissPlaybackHelp();
                scenes.revealVideo();
                Log.i("ShelfPlayer", "First video frame rendered in "
                        + (android.os.SystemClock.elapsedRealtime() - startup) + " ms");
            }
            @Override public void onTracksChanged(androidx.media3.common.Tracks tracks) {
                if (player == activePlayer && !tracks.isEmpty()) {
                    scenes.describeVideoLoading("已打开片源，正在缓冲画面…");
                }
            }

            @Override public void onCues(androidx.media3.common.text.CueGroup group) {
                if (!group.cues.isEmpty()) Log.i("ShelfPlayer", "Subtitle cues rendered: " + group.cues.size());
            }
            @Override public void onPlaybackStateChanged(int state) {
                if (destroyed || player != activePlayer) return;
                if (player != null && player.getDuration() > 0) duration = player.getDuration();
                if (state == Player.STATE_READY && !activePlayer.getCurrentTracks().containsType(C.TRACK_TYPE_VIDEO)) {
                    progressHandler.removeCallbacks(showSlowStart);
                    dismissPlaybackHelp();
                    scenes.revealVideo();
                }
                if(state==Player.STATE_ENDED){
                    savePosition();
                    if(saved.getBoolean("autoplay",false)&&SeriesIndex.adjacent(movies,movie,true,MainActivity.this::seriesKey)!=null)
                        progressHandler.post(()->{if(player!=null&&player.getPlaybackState()==Player.STATE_ENDED&&selected==movie)stepEpisode(movie,true);});
                }
            }
            @Override public void onPlayerError(PlaybackException error) {
                if (destroyed || player != activePlayer) return;
                progressHandler.removeCallbacks(showSlowStart);
                dismissPlaybackHelp();
                Log.w("ShelfPlayer", "Playback failed: " + error.getErrorCodeName(), error);
                new MotionDialog(MainActivity.this).setTitle("播放失败")
                        .setMessage("电视未能读取或解码这段影片。请检查网络，或试试带 AAC / Dolby Digital 音轨的文件。\n"
                                + error.getErrorCodeName())
                        .setPositiveButton("返回片库", (dialog, which) -> stopPlayback())
                        .setOnCancelListener(dialog -> stopPlayback()).show();
            }
        });
        progressHandler.postDelayed(showSlowStart, 15000);
        player.prepare();
        player.play();
        progressHandler.postDelayed(progressTick,5000);
    }

    private void showPlaybackHelp(Catalog.Movie movie) {
        scenes.describeVideoLoading("片源读取较慢");
        var dialog = new MotionDialog(this).setTitle("还未开始播放")
                .setMessage("15 秒内没有出画。可以返回影片，或继续等待。")
                .setPositiveButton("返回影片", (d, which) -> stopPlayback())
                .setNegativeButton("继续等待", null)
                .setOnCancelListener(d -> stopPlayback());
        if (versions(movie).size() > 1) dialog.setNeutralButton("换版本", (d, which) -> {
            Runnable back = leaveDetails;
            stopPlayback();
            showVersions(movie, back);
        });
        playbackHelp = dialog.show();
    }

    private void dismissPlaybackHelp() {
        if (playbackHelp != null) playbackHelp.dismiss();
        playbackHelp = null;
    }

    private void diagnostics() {
        String details = Capabilities.read(this);
        Log.i("ShelfCapabilities", details);
        new MotionDialog(this).setTitle("播放诊断").setMessage(details)
                .setPositiveButton("关闭", null).show();
    }

    private void savePosition() {
        if (player == null || player.getPlaybackState() == Player.STATE_IDLE) return;
        // A resume seek may still be buffering before STATE_READY. Keep known duration
        // so leaving during that seek doesn't erase a completed title's rewatch progress.
        long knownDuration=player.getDuration();
        if(knownDuration>0)duration=knownDuration;
        else if(duration<=0)duration=saved.getLong("duration:"+playbackKey,0);
        long pos = player.getCurrentPosition();
        libraryRevision++;
        boolean completed=player.getPlaybackState()==Player.STATE_ENDED||ResumePolicy.complete(pos,duration);
        if(completed){saved.edit().putBoolean("watched:"+playbackKey,true).apply();pos=0;}
        saved.edit().putLong(playbackKey, pos).putLong("recent:" + playbackKey, System.currentTimeMillis())
                .putLong("duration:" + playbackKey, duration).apply();
    }

    private void stopPlayback() {
        releasePlayer();
        if (selected != null) showDetails(selected, leaveDetails);
        else showHome();
    }

    private void releasePlayer() {
        dismissPlaybackHelp();
        if (player == null) return;
        savePosition();
        progressHandler.removeCallbacksAndMessages(null);
        hud.close();
        hud = null;
        playerView.setPlayer(null);
        player.release();
        player = null;
        playerView = null;
    }

    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        int key = event.getKeyCode();
        if (page.equals("grid") && activeGrid != null
                && (key == android.view.KeyEvent.KEYCODE_PAGE_UP || key == android.view.KeyEvent.KEYCODE_PAGE_DOWN)) {
            if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                int delta = key == android.view.KeyEvent.KEYCODE_PAGE_UP ? -GRID_PAGE_SIZE : GRID_PAGE_SIZE;
                int base = pendingGrid == activeGrid ? pendingGridOffset : gridOffset;
                int last = Math.max(0, activeGrid.items.size() - 1) / GRID_PAGE_SIZE * GRID_PAGE_SIZE;
                pendingGridOffset = Math.min(last, Math.max(0, base + delta));
                if (pendingGrid != activeGrid) {
                    scenes.removeCallbacks(flushGridPage);
                    pendingGrid = activeGrid;
                    scenes.postOnAnimation(flushGridPage);
                }
            }
            return true;
        }
        return hud != null && hud.key(event) || super.dispatchKeyEvent(event);
    }

    @Override public void onBackPressed() {
        if (player != null) stopPlayback();
        else if((page.equals("series")||page.equals("season"))&&pageBack!=null)pageBack.run();
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
        dismissPlaybackHelp();
        super.onPause();
    }

    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_RUNNING_LOW) images.evictAll();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        releasePlayer();
        scenes.removeCallbacks(flushGridPage);
        scenes.close();
        network.shutdownNow();
        metadataWorker.shutdownNow();
        artwork.shutdownNow();
        pendingArt.clear();
        images.evictAll();
        store.close();
        if (source != null) Arrays.fill(source.password(), '\0');
        super.onDestroy();
    }

    private void postUi(Runnable action) {
        runOnUiThread(() -> { if (!destroyed && !isFinishing()) action.run(); });
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
        background.setEnterFadeDuration(Motion.enabled(this)?140:0);
        background.setExitFadeDuration(Motion.enabled(this)?180:0);
        Motion.focus(button,button,null,.018f,false);
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
