package local.shelf.player;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Consumer;

final class LibraryView {
    private static final int WHITE = 0xfff5f4f5;
    private static final int DIM = 0xffb8b9bc;
    private static final int ORANGE = 0xffff8916;
    private final Activity context;
    private final java.util.function.Function<Catalog.Movie,String> displayName;
    private final LinearLayout column;
    private final FrameLayout frame;
    private final ScrollView scroll;
    private final List<PendingArt> artworkSlots = new ArrayList<>();
    private final List<PendingArt> pendingArt = new ArrayList<>();
    private final int[] viewport = new int[2];
    private final int[] location = new int[2];
    private final Runnable loadVisible = this::loadVisibleArtwork;
    private final ViewTreeObserver.OnScrollChangedListener artScroll = this::scheduleArtwork;
    private final ViewTreeObserver.OnGlobalLayoutListener artLayout = this::scheduleArtwork;
    private ViewTreeObserver artworkObserver;

    LibraryView(Activity context,java.util.function.Function<Catalog.Movie,String> displayName) {
        this.displayName=displayName;
        this.context = context;
        frame = new FrameLayout(context);
        frame.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xff142b3c, 0xff192933, 0xff251f1a, 0xff2d2820}));
        scroll = new ScrollView(context);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setSmoothScrollingEnabled(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        frame.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        column = vertical();
        column.setClipChildren(false);
        column.setClipToPadding(false);
        column.setPadding(0, dp(20), 0, dp(30));
        scroll.addView(column);
        frame.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {
                artworkObserver = frame.getViewTreeObserver();
                artworkObserver.addOnScrollChangedListener(artScroll);
                artworkObserver.addOnGlobalLayoutListener(artLayout);
                scheduleArtwork();
            }

            @Override public void onViewDetachedFromWindow(View view) {
                frame.removeCallbacks(loadVisible);
                if (artworkObserver != null && artworkObserver.isAlive()) {
                    artworkObserver.removeOnScrollChangedListener(artScroll);
                    artworkObserver.removeOnGlobalLayoutListener(artLayout);
                }
                artworkObserver = null;
                pendingArt.clear();
                for (PendingArt item : artworkSlots) {
                    if (item.image().getDrawable() == null) pendingArt.add(item);
                }
            }
        });
    }

    View root() { return frame; }

    void gridStyle() {
        frame.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xff262525, 0xff252526, 0xff232829}));
    }

    void navigation(Runnable search, Runnable refresh, Runnable settings) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(44), 0, dp(46), 0);
        row.addView(action("搜索", search), new LinearLayout.LayoutParams(dp(100), dp(48)));
        row.addView(new View(context), new LinearLayout.LayoutParams(0, 1, 1));
        row.addView(action("刷新", refresh), new LinearLayout.LayoutParams(dp(69), dp(48)));
        row.addView(action("设置", settings), new LinearLayout.LayoutParams(dp(69), dp(48)));
        column.addView(row);
    }

    void heading(String title) {
        TextView heading = label(title, 18, DIM);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heading.setPadding(dp(50), dp(9), 0, dp(9));
        column.addView(heading);
    }

    void empty(String title, String description, String actionLabel, Runnable action) {
        LinearLayout empty = vertical();
        empty.setPadding(dp(52), dp(48), dp(52), dp(43));
        TextView header = label(title, 35, WHITE);
        header.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        empty.addView(header);
        TextView message = label(description, 17, DIM);
        message.setPadding(0, dp(9), 0, dp(18));
        empty.addView(message);
        Button button = action(actionLabel, action);
        button.setBackground(buttonBackground(true));
        empty.addView(button, new LinearLayout.LayoutParams(dp(182), dp(43)));
        column.addView(empty);
        button.requestFocus();
    }

    void favorites(List<String> labels, List<Runnable> actions) {
        LinearLayout row = rail();
        int[][] colors = {{0xffff5500, 0xffff9800}, {0xffff282e, 0xffff8f52},
                {0xff074cf1, 0xff00c7e8}, {0xffec1d6a, 0xffdf32ee},
                {0xff119611, 0xff50bf16}};
        int[] icons = {R.drawable.shelf_library, R.drawable.shelf_bookmark, R.drawable.shelf_movie,
                R.drawable.shelf_tv, R.drawable.shelf_folder};
        for (int i = 0; i < labels.size(); i++) {
            LinearLayout card = vertical();
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            FrameLayout tile = new FrameLayout(context);
            tile.setBackground(gradient(colors[i], 10));
            ImageView icon = new ImageView(context);
            icon.setImageResource(icons[i]);
            FrameLayout.LayoutParams iconSize = new FrameLayout.LayoutParams(dp(50), dp(50), Gravity.CENTER);
            tile.addView(icon, iconSize);
            card.addView(tile, new LinearLayout.LayoutParams(dp(190), dp(100)));
            TextView text = label(labels.get(i), 16, DIM);
            text.setGravity(Gravity.CENTER);
            card.addView(text, new LinearLayout.LayoutParams(-1, dp(29)));
            Runnable action = actions.get(i);
            focus(card, tile, text, action);
            Motion.identity(card,"collection:"+labels.get(i));
            addCard(row, card, 190, 20);
        }
    }

    interface ArtLoader { void load(Catalog.Movie movie, boolean wide, ImageView view); }

    private record PendingArt(Catalog.Movie movie, boolean wide, ImageView image, ArtLoader loader) {}

    private void scheduleArtwork() {
        if (pendingArt.isEmpty()) return;
        frame.removeCallbacks(loadVisible);
        frame.postOnAnimation(loadVisible);
    }

    private void loadVisibleArtwork() {
        if (!frame.isShown() || pendingArt.isEmpty()) return;
        frame.getLocationInWindow(viewport);
        int preload = dp(60);
        for (var iterator = pendingArt.iterator(); iterator.hasNext();) {
            PendingArt item = iterator.next();
            ImageView image = item.image();
            if (image.getWidth() == 0 || image.getHeight() == 0) continue;
            image.getLocationInWindow(location);
            if (location[0] + image.getWidth() <= viewport[0] - preload
                    || location[0] >= viewport[0] + frame.getWidth() + preload
                    || location[1] + image.getHeight() <= viewport[1] - preload
                    || location[1] >= viewport[1] + frame.getHeight() + preload) continue;
            iterator.remove();
            item.loader().load(item.movie(), item.wide(), image);
        }
    }

    void railMovies(List<Catalog.Movie> movies, boolean wide, Consumer<Catalog.Movie> click,
                    ArtLoader artwork, java.util.function.ToIntFunction<Catalog.Movie> progress) {
        LinearLayout row = rail();
        int max = Math.min(24, movies.size());
        for (int i = 0; i < max; i++) {
            Catalog.Movie movie = movies.get(i);
            addCard(row, movieCard(movie, wide, click, artwork, progress.applyAsInt(movie)),
                    wide ? 241 : 126, wide ? 22 : 18);
        }
    }

    void gridMovies(List<Catalog.Movie> movies, Consumer<Catalog.Movie> click, ArtLoader artwork,
                    java.util.function.ToIntFunction<Catalog.Movie> progress) {
        int screenWidth = Math.round(context.getResources().getDisplayMetrics().widthPixels
                / context.getResources().getDisplayMetrics().density);
        int columns = Math.max(3, Math.min(6, (screenWidth - 78) / 140));
        for (int start = 0; start < movies.size(); start += columns) {
            LinearLayout row = horizontal();
            row.setClipChildren(false);
            row.setClipToPadding(false);
            row.setPadding(dp(50), dp(5), dp(20), dp(7));
            column.addView(row);
            for (int i = start; i < Math.min(start + columns, movies.size()); i++) {
                Catalog.Movie movie = movies.get(i);
                addCard(row, movieCard(movie, false, click, artwork, progress.applyAsInt(movie)), 126, 18);
            }
        }
    }

    private LinearLayout movieCard(Catalog.Movie movie, boolean wide, Consumer<Catalog.Movie> click,
                                   ArtLoader artwork, int progress) {
        int width = wide ? 241 : 126;
        int height = wide ? 136 : 184;
        LinearLayout card = vertical();
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout tile = new FrameLayout(context);
        tile.setBackground(gradient(new int[]{0xff263c4d, 0xff2b2624}, 9));
        tile.setClipToOutline(true);
        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        tile.addView(image, new FrameLayout.LayoutParams(-1, -1));
        String name = displayName.apply(movie);
        TextView fallback = label(name, 18, WHITE);
        fallback.setPadding(dp(10), dp(10), dp(10), dp(10));
        fallback.setGravity(Gravity.CENTER);
        fallback.setMaxLines(3);
        tile.addView(fallback, new FrameLayout.LayoutParams(-1, -1));
        image.setTag(fallback);
        if (progress > 0) {
            View line = new View(context);
            line.setBackground(gradient(new int[]{ORANGE, ORANGE}, 3));
            FrameLayout.LayoutParams lineSize = new FrameLayout.LayoutParams(dp(width * Math.min(100, progress) / 100),
                    dp(5), Gravity.BOTTOM | Gravity.START);
            tile.addView(line, lineSize);
        }
        card.addView(tile, new LinearLayout.LayoutParams(dp(width), dp(height)));
        TextView title = label(name, 15, DIM);
        title.setSingleLine();
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER);
        card.addView(title, new LinearLayout.LayoutParams(-1, dp(24)));
        String caption = movie.title().caption();
        if (!caption.isEmpty()) {
            TextView sub = label(caption, 12, DIM);
            sub.setGravity(Gravity.CENTER);
            card.addView(sub);
        }
        focus(card, tile, title, () -> click.accept(movie));
        Motion.identity(card,"movie:"+movie.path());
        PendingArt item = new PendingArt(movie, wide, image, artwork);
        artworkSlots.add(item);
        pendingArt.add(item);
        return card;
    }

    void details(Catalog.Movie movie, String title, String overview, String playLabel,
                 Runnable play, Runnable favorite, boolean isFavorite, Runnable subtitles, Runnable manage,
                 ArtLoader artwork) {
        ImageView backdrop = new ImageView(context);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        frame.addView(backdrop, 0, new FrameLayout.LayoutParams(-1, -1));
        artwork.load(movie, true, backdrop);
        View left = new View(context);
        left.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xf3131a21, 0xa5121820, 0x02121820}));
        frame.addView(left, 1, new FrameLayout.LayoutParams(-1, -1));
        View bottom = new View(context);
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xff131a21, 0x49131a21, 0x00131a21}));
        frame.addView(bottom, 2, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout info = vertical();
        info.setPadding(dp(50), dp(48), dp(20), 0);
        column.addView(info, new LinearLayout.LayoutParams(dp(474), -2));
        TextView name = label(title, 51, WHITE);
        name.setMaxLines(2);
        name.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        info.addView(name);
        TextView badges = label((movie.title().caption() + "     " + (movie.bluRay() ? "蓝光原盘" : movie.filename().substring(
                movie.filename().lastIndexOf('.') + 1).toUpperCase(java.util.Locale.ROOT))).trim(), 16, WHITE);
        badges.setPadding(0, dp(13), 0, dp(14));
        info.addView(badges);
        TextView summary = label(overview.isEmpty() ? (movie.bluRay() ? "蓝光原盘 · 播放最大的影片分段" : movie.filename()) : overview, 16, WHITE);
        summary.setMaxLines(4);
        summary.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(summary);
        LinearLayout secondary = horizontal();
        secondary.setPadding(0, dp(17), 0, dp(9));
        secondary.addView(action(isFavorite ? "已收藏" : "收藏", favorite), new LinearLayout.LayoutParams(dp(88), dp(40)));
        secondary.addView(action("字幕", subtitles), new LinearLayout.LayoutParams(dp(88), dp(40)));
        secondary.addView(action("更多",manage),new LinearLayout.LayoutParams(dp(88),dp(40)));
        info.addView(secondary);
        Button start = action(playLabel, play);
        start.setBackground(buttonBackground(true));
        info.addView(start, new LinearLayout.LayoutParams(dp(192), dp(43)));
        start.requestFocus();
    }

    void nextPage(String title, Runnable action) {
        Button button = action(title, action);
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(160), dp(40));
        size.leftMargin = dp(50);
        size.topMargin = dp(8);
        column.addView(button, size);
    }

    private LinearLayout rail() {
        HorizontalScrollView horizontal = new HorizontalScrollView(context);
        horizontal.setClipChildren(false);
        horizontal.setClipToPadding(false);
        horizontal.setHorizontalScrollBarEnabled(false);
        horizontal.setSmoothScrollingEnabled(true);
        horizontal.setOverScrollMode(View.OVER_SCROLL_NEVER);
        column.addView(horizontal);
        LinearLayout row = horizontal();
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setPadding(dp(50), dp(6), dp(35), dp(8));
        horizontal.addView(row, new HorizontalScrollView.LayoutParams(-2, -2));
        return row;
    }

    private void addCard(LinearLayout row, View card, int width, int gap) {
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(width), -2);
        size.rightMargin = dp(gap);
        row.addView(card, size);
    }

    private void focus(View card, View art, TextView title, Runnable action) {
        card.setContentDescription(title.getText());
        card.setFocusable(true);
        card.setClickable(true);
        card.setOnClickListener(v -> action.run());
        Motion.focus(card,art,title,.065f,true);
    }

    private Button action(String text, Runnable action) {
        Button button = new Button(context);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(17);
        button.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}},
                new int[]{WHITE, DIM}));
        button.setBackground(buttonBackground(false));
        Motion.focus(button,button,null,.025f,false);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private StateListDrawable buttonBackground(boolean primary) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, gradient(primary
                ? new int[]{0xffee7d08, 0xffa65400} : new int[]{0xff6f7781, 0xff454951}, 9));
        states.addState(new int[]{}, gradient(primary
                ? new int[]{0xffa85b09, 0xff75440e} : new int[]{0x26545862, 0x09545862}, 9));
        states.setEnterFadeDuration(Motion.enabled(context)?140:0);
        states.setExitFadeDuration(Motion.enabled(context)?180:0);
        return states;
    }

    private GradientDrawable gradient(int[] colors, int radius) {
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors);
        background.setCornerRadius(dp(radius));
        return background;
    }

    private TextView label(String text, int size, int color) {
        TextView result = new TextView(context);
        result.setText(text);
        result.setTextSize(size);
        result.setTextColor(color);
        return result;
    }

    private LinearLayout horizontal() {
        LinearLayout row=new LinearLayout(context);
        row.setClipChildren(false);row.setClipToPadding(false);
        return row;
    }

    private LinearLayout vertical() {
        LinearLayout result = new LinearLayout(context);
        result.setClipChildren(false);
        result.setClipToPadding(false);
        result.setOrientation(LinearLayout.VERTICAL);
        return result;
    }

    private int dp(int size) {
        return Math.round(size * context.getResources().getDisplayMetrics().density);
    }
}
