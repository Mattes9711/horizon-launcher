package app.horizon.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Horizon - a minimal, ad-free Android TV home screen.
 * Big blurred background of the focused app, one favourites row, all other apps below, clock top right.
 * Long-press OK (or the Menu key) on a tile to add/remove/move favourites.
 */
public class MainActivity extends Activity {

    static final class App {
        String pkg;
        String label;
        Intent launch;
        Drawable banner;
        Drawable icon;
    }

    private static final String SETTINGS_ID = "__settings";
    private static final String[] DEFAULT_FAVS = {
            "com.google.android.youtube.tv", "com.netflix.ninja", "com.disney.disneyplus",
            "com.amazon.amazonvideo.livingroom", "com.apple.atve.androidtv.appletv",
            "com.spotify.tv.android", "de.swr.avp.ard.tv", "com.google.android.youtube.tvmusic"
    };

    private final Map<String, App> apps = new LinkedHashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private List<String> favs = new ArrayList<>();

    private ImageView bgA, bgB;
    private boolean showingA = true;
    private TextView title;
    private LinearLayout favRow, allRow;
    private HorizontalScrollView favScroll, allScroll;
    private ScrollView rowsScroll;
    private String lastFocusPkg;
    private String bgPkg;
    private Runnable pendingBg;
    private BroadcastReceiver pkgReceiver;

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("horizon", MODE_PRIVATE);
        setContentView(buildUi());

        pkgReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                reload();
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(pkgReceiver, f);

        reload();
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    @Override
    protected void onDestroy() {
        if (pkgReceiver != null) unregisterReceiver(pkgReceiver);
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while already home: jump back to the first favourite
        rowsScroll.smoothScrollTo(0, 0);
        favScroll.smoothScrollTo(0, 0);
        if (favRow.getChildCount() > 0) favRow.getChildAt(0).requestFocus();
    }

    @Override
    public void onBackPressed() {
        // A home screen never closes; Back returns to the first favourite instead.
        onNewIntent(getIntent());
    }

    // ---------------------------------------------------------------- UI

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B0D14);

        bgA = newBackground();
        bgB = newBackground();
        bgB.setAlpha(0f);
        root.addView(bgA, new FrameLayout.LayoutParams(-1, -1));
        root.addView(bgB, new FrameLayout.LayoutParams(-1, -1));

        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x66000000, 0x10000000, 0x40000000, 0xE6000000}));
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        title = new TextView(this);
        title.setTextColor(Color.WHITE);
        title.setTextSize(46);
        title.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        title.setShadowLayer(dp(8), 0, dp(2), 0xAA000000);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(dp(560), -2, Gravity.TOP | Gravity.START);
        tl.setMargins(dp(56), dp(40), 0, 0);
        root.addView(title, tl);

        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("h:mm a");
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(16);
        clock.setTypeface(Typeface.DEFAULT_BOLD);
        clock.setPadding(dp(14), dp(6), dp(14), dp(6));
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(0x59000000);
        pill.setCornerRadius(dp(20));
        clock.setBackground(pill);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        cl.setMargins(0, dp(32), dp(48), 0);
        root.addView(clock, cl);

        // Two rows; the second one peeks in at the bottom edge until you move down to it.
        rowsScroll = new ScrollView(this);
        rowsScroll.setVerticalScrollBarEnabled(false);
        rowsScroll.setClipChildren(false);
        rowsScroll.setClipToPadding(false);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setClipChildren(false);
        column.setClipToPadding(false);

        favScroll = newRowScroll();
        favRow = newRow();
        favScroll.addView(favRow);
        column.addView(favScroll, new LinearLayout.LayoutParams(-1, -2));

        allScroll = newRowScroll();
        allRow = newRow();
        allScroll.addView(allRow);
        column.addView(allScroll, new LinearLayout.LayoutParams(-1, -2));

        // padding at the bottom so the second row can scroll fully into view
        View spacer = new View(this);
        column.addView(spacer, new LinearLayout.LayoutParams(-1, dp(40)));

        rowsScroll.addView(column);
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(-1, dp(250), Gravity.BOTTOM);
        root.addView(rowsScroll, rl);
        return root;
    }

    private ImageView newBackground() {
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return iv;
    }

    private HorizontalScrollView newRowScroll() {
        HorizontalScrollView s = new HorizontalScrollView(this);
        s.setHorizontalScrollBarEnabled(false);
        s.setClipChildren(false);
        s.setClipToPadding(false);
        s.setFillViewport(false);
        return s;
    }

    private LinearLayout newRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setPadding(dp(56), dp(22), dp(56), dp(22));
        return row;
    }

    private View newTile(final App app, final boolean isFav) {
        final int w = dp(isFav ? 224 : 176), h = dp(isFav ? 126 : 99);
        final FrameLayout tile = new FrameLayout(this);
        tile.setFocusable(true);
        tile.setFocusableInTouchMode(true);
        tile.setClickable(true);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF232634);
        bg.setCornerRadius(dp(16));
        tile.setBackground(bg);
        tile.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        tile.setClipToOutline(true);

        if (app.banner != null) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageDrawable(app.banner);
            tile.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        } else {
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER);
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(app.icon);
            box.addView(iv, new LinearLayout.LayoutParams(dp(isFav ? 52 : 40), dp(isFav ? 52 : 40)));
            TextView tv = new TextView(this);
            tv.setText(app.label);
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(isFav ? 14 : 12);
            tv.setSingleLine(true);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(dp(8), dp(6), dp(8), 0);
            box.addView(tv, new LinearLayout.LayoutParams(-1, -2));
            tile.addView(box, new FrameLayout.LayoutParams(-1, -1));
        }

        // white frame when focused
        final GradientDrawable frame = new GradientDrawable();
        frame.setColor(Color.TRANSPARENT);
        frame.setCornerRadius(dp(16));
        frame.setStroke(dp(3), Color.WHITE);
        tile.setForeground(null);

        tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                float s = hasFocus ? 1.12f : 1f;
                v.animate().scaleX(s).scaleY(s).translationZ(hasFocus ? dp(12) : 0).setDuration(160).start();
                tile.setForeground(hasFocus ? frame : null);
                if (hasFocus) onAppFocused(app);
            }
        });
        tile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launch(app);
            }
        });
        tile.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showOptions(app, isFav);
                return true;
            }
        });
        tile.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent e) {
                if (keyCode == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP) {
                    showOptions(app, isFav);
                    return true;
                }
                return false;
            }
        });
        tile.setTag(app.pkg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.setMargins(0, 0, dp(isFav ? 26 : 20), 0);
        tile.setLayoutParams(lp);
        return tile;
    }

    // ---------------------------------------------------------------- data

    private void reload() {
        loadApps();
        loadFavs();
        render();
    }

    private void loadApps() {
        apps.clear();
        PackageManager pm = getPackageManager();
        List<ResolveInfo> list = new ArrayList<>();
        list.addAll(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
        list.addAll(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
        for (ResolveInfo ri : list) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || apps.containsKey(pkg)) continue;
            App a = new App();
            a.pkg = pkg;
            a.label = String.valueOf(ri.loadLabel(pm));
            Intent li = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (li == null) li = pm.getLaunchIntentForPackage(pkg);
            if (li == null) li = new Intent(Intent.ACTION_MAIN).setClassName(pkg, ri.activityInfo.name);
            li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            a.launch = li;
            try {
                a.banner = ri.activityInfo.loadBanner(pm);
                if (a.banner == null) a.banner = ri.activityInfo.applicationInfo.loadBanner(pm);
            } catch (Exception ignored) {
                a.banner = null;
            }
            a.icon = ri.loadIcon(pm);
            apps.put(pkg, a);
        }
        App s = new App();
        s.pkg = SETTINGS_ID;
        s.label = getString(R.string.settings);
        s.launch = new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        s.icon = getDrawable(android.R.drawable.ic_menu_preferences);
        apps.put(SETTINGS_ID, s);
    }

    private void loadFavs() {
        favs = new ArrayList<>();
        String saved = prefs.getString("favs", null);
        List<String> wanted = saved == null ? Arrays.asList(DEFAULT_FAVS)
                : (saved.isEmpty() ? new ArrayList<String>() : Arrays.asList(saved.split(",")));
        for (String p : wanted) if (apps.containsKey(p) && !favs.contains(p)) favs.add(p);
        if (saved == null && favs.isEmpty()) {
            for (String p : apps.keySet()) {
                if (favs.size() >= 6) break;
                if (!SETTINGS_ID.equals(p)) favs.add(p);
            }
        }
    }

    private void saveFavs() {
        prefs.edit().putString("favs", TextUtils.join(",", favs)).apply();
    }

    private void render() {
        String keep = lastFocusPkg;
        favRow.removeAllViews();
        allRow.removeAllViews();
        for (String p : favs) favRow.addView(newTile(apps.get(p), true));

        List<App> others = new ArrayList<>();
        for (App a : apps.values()) if (!favs.contains(a.pkg) && !SETTINGS_ID.equals(a.pkg)) others.add(a);
        Collections.sort(others, (x, y) -> x.label.compareToIgnoreCase(y.label));
        for (App a : others) allRow.addView(newTile(a, false));
        if (apps.containsKey(SETTINGS_ID)) allRow.addView(newTile(apps.get(SETTINGS_ID), false));

        View target = keep != null ? findTile(keep) : null;
        if (target == null) target = favRow.getChildCount() > 0 ? favRow.getChildAt(0)
                : (allRow.getChildCount() > 0 ? allRow.getChildAt(0) : null);
        if (target != null) {
            final View t = target;
            t.post(new Runnable() {
                @Override
                public void run() {
                    t.requestFocus();
                }
            });
        }
    }

    private View findTile(String pkg) {
        for (LinearLayout row : new LinearLayout[]{favRow, allRow})
            for (int i = 0; i < row.getChildCount(); i++)
                if (pkg.equals(row.getChildAt(i).getTag())) return row.getChildAt(i);
        return null;
    }

    // ---------------------------------------------------------------- actions

    private void onAppFocused(final App app) {
        lastFocusPkg = app.pkg;
        title.setText(app.label);
        if (app.pkg.equals(bgPkg)) return;
        if (pendingBg != null) handler.removeCallbacks(pendingBg);
        pendingBg = new Runnable() {
            @Override
            public void run() {
                showBackground(app);
            }
        };
        handler.postDelayed(pendingBg, 180);
    }

    private void showBackground(App app) {
        bgPkg = app.pkg;
        Drawable src = app.banner != null ? app.banner : app.icon;
        Bitmap blurred = blur(src);
        ImageView in = showingA ? bgB : bgA;
        ImageView out = showingA ? bgA : bgB;
        in.setImageBitmap(blurred);
        in.animate().alpha(1f).setDuration(450).start();
        out.animate().alpha(0f).setDuration(450).start();
        showingA = !showingA;
    }

    /** Cheap, dependency-free blur: shrink to a few pixels, then scale up with filtering (twice). */
    private Bitmap blur(Drawable d) {
        if (d == null) return null;
        Drawable copy = d.getConstantState() != null ? d.getConstantState().newDrawable().mutate() : d;
        Bitmap tiny = Bitmap.createBitmap(32, 18, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(tiny);
        c.drawColor(0xFF1A1D28);
        copy.setBounds(0, 0, 32, 18);
        copy.draw(c);
        Bitmap mid = Bitmap.createScaledBitmap(tiny, 96, 54, true);
        Bitmap small = Bitmap.createScaledBitmap(mid, 24, 14, true);
        return Bitmap.createScaledBitmap(small, 640, 360, true);
    }

    private void launch(App app) {
        try {
            startActivity(app.launch);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, getString(R.string.cannot_open, app.label), Toast.LENGTH_SHORT).show();
        }
    }

    private void showOptions(final App app, final boolean isFav) {
        final List<String> items = new ArrayList<>();
        if (isFav) {
            items.add(getString(R.string.remove_fav));
            items.add(getString(R.string.move_left));
            items.add(getString(R.string.move_right));
        } else {
            items.add(getString(R.string.add_fav));
        }
        if (!SETTINGS_ID.equals(app.pkg)) items.add(getString(R.string.app_info));

        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(app.label)
                .setItems(items.toArray(new String[0]), (dialog, which) -> {
                    String choice = items.get(which);
                    int i = favs.indexOf(app.pkg);
                    if (choice.equals(getString(R.string.add_fav))) {
                        favs.add(app.pkg);
                    } else if (choice.equals(getString(R.string.remove_fav))) {
                        favs.remove(app.pkg);
                    } else if (choice.equals(getString(R.string.move_left)) && i > 0) {
                        Collections.swap(favs, i, i - 1);
                    } else if (choice.equals(getString(R.string.move_right)) && i >= 0 && i < favs.size() - 1) {
                        Collections.swap(favs, i, i + 1);
                    } else if (choice.equals(getString(R.string.app_info))) {
                        try {
                            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:" + app.pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        } catch (ActivityNotFoundException ignored) {
                        }
                        return;
                    }
                    saveFavs();
                    lastFocusPkg = app.pkg;
                    render();
                })
                .show();
    }
}
