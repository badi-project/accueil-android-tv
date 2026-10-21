package app.accueil.tv;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
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
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Locale;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Écran d'accueil minimaliste façon Apple TV pour Android TV.
 * Aucune permission réseau, aucune recommandation, aucune pub.
 */
public class HomeActivity extends Activity {

    private static final int COLS = 5;

    /** Ordre par défaut au premier lancement (les autres applis suivent par ordre alphabétique). */
    private static final String[] PRIORITY = {
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
            "com.amazon.amazonvideo.livingroom",
            "com.disney.disneyplus",
            "com.apple.atve.androidtv.appletv",
            "com.crunchyroll.crunchyroid",
            "com.canal.android.canal",
            "fr.francetv.pluzz",
            "fr.tf1.mytf1",
            "fr.m6.m6replay",
            "tv.molotov.app",
            "com.spotify.tv.android",
            "com.google.android.youtube.tvmusic",
            "com.plexapp.android",
            "org.jellyfin.androidtv",
            "org.xbmc.kodi",
    };

    private static final Interpolator EASE = new DecelerateInterpolator(2f);

    static final class App {
        String key, pkg, cls, label;
        boolean leanback;
        Drawable banner, icon;
        int tint;
        Bitmap ambient;
    }

    final class Tile {
        App app;
        LinearLayout cell;
        FrameLayout card;
        TextView label;
        View sheen;
        float baseAlpha = 1f;
        ObjectAnimator wiggle;
    }

    /** ScrollView dont on pilote nous-mêmes le défilement, pour un rendu doux et centré. */
    static final class HomeScroll extends ScrollView {
        HomeScroll(Context c) { super(c); }
        @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) { return 0; }
    }

    private PackageManager pm;
    private SharedPreferences prefs;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private int screenW, screenH, side, gap, tileW, tileH, rowGap, headerH;
    private float u; // 1 % de la hauteur d'écran, en pixels
    private float radius;

    private ImageView bgA, bgB;
    private boolean bgAFront = true;
    private String ambientKey;
    private TextView title;
    private View clockBox;
    private HomeScroll scroll;
    private LinearLayout content;
    private FrameLayout header;
    private LinearLayout pillRow;
    private TextView pillSettings, pillHidden;
    private View firstRow;

    private final List<App> apps = new ArrayList<>();
    private final Map<String, Tile> tiles = new HashMap<>();
    private final Set<String> hidden = new HashSet<>();
    private String focusedKey;
    private String movingKey;
    private boolean showHidden;
    private boolean foreground;
    private int loadGeneration;
    private AlertDialog dialog;
    private BroadcastReceiver pkgReceiver;
    private Typeface medium, light;

    private final Runnable reload = new Runnable() {
        @Override public void run() { loadApps(); }
    };

    // Horaires de prière (ville réglée dans Config)
    private LinearLayout prayerBox;
    private TextView prayerCaption;
    private final LinearLayout[] pCol = new LinearLayout[6];
    private final TextView[] pName = new TextView[6];
    private final TextView[] pTime = new TextView[6];
    private final TextView[] pLeft = new TextView[6];
    private GradientDrawable[] pPill = new GradientDrawable[6];
    private Map<String, int[]> gmCache;
    private boolean gmLoading, usingFallback;
    private long gmLastAttempt;
    private final Runnable prayerTick = new Runnable() {
        @Override public void run() {
            updatePrayers();
            ui.postDelayed(this, 15000);
        }
    };

    // ------------------------------------------------------------------ cycle de vie

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pm = getPackageManager();
        prefs = getSharedPreferences("accueil", MODE_PRIVATE);
        hidden.addAll(prefs.getStringSet("hidden", Collections.<String>emptySet()));
        try { gmCache = GuideMusulman.load(prefs); } catch (Exception e) { gmCache = new HashMap<>(); }
        medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        light = Typeface.create("sans-serif-light", Typeface.NORMAL);
        computeSizes();
        buildChrome();
        registerPackageReceiver();
        loadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        foreground = true;
        ui.removeCallbacks(prayerTick);
        prayerTick.run();
        View f = getCurrentFocus();
        if (f == null || f == scroll) restoreFocus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        foreground = false;
        ui.removeCallbacks(prayerTick);
        endMove();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pkgReceiver != null) {
            try { unregisterReceiver(pkgReceiver); } catch (Exception ignored) { }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Bouton Accueil pressé alors qu'on est déjà sur l'accueil : retour en haut.
        if (foreground) goTop();
    }

    @Override
    public void onBackPressed() {
        if (movingKey != null) { endMove(); return; }
        if (showHidden) { toggleHidden(); return; }
        goTop(); // l'accueil ne se ferme jamais
    }

    // ------------------------------------------------------------------ mise en page

    private void computeSizes() {
        DisplayMetrics m = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(m);
        screenW = Math.max(m.widthPixels, m.heightPixels);
        screenH = Math.min(m.widthPixels, m.heightPixels);
        u = screenH / 100f;
        side = Math.round(screenW * 0.066f);
        gap = Math.round(screenW * 0.026f);
        tileW = (screenW - 2 * side - (COLS - 1) * gap) / COLS;
        tileH = Math.round(tileW * 9f / 16f);
        rowGap = Math.round(3.4f * u);
        headerH = Math.round(44f * u);
        radius = tileW * 0.045f;
    }

    private void buildChrome() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF2C2C2E, 0xFF0B0B0C}));

        ColorMatrix sat = new ColorMatrix();
        sat.setSaturation(1.35f);
        ColorMatrixColorFilter satFilter = new ColorMatrixColorFilter(sat);
        bgA = new ImageView(this);
        bgB = new ImageView(this);
        for (ImageView iv : new ImageView[]{bgA, bgB}) {
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setColorFilter(satFilter);
            root.addView(iv, match());
        }
        bgA.setAlpha(0f);
        bgB.setAlpha(0f);

        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x40000000, 0x99000000, 0xEB000000}));
        root.addView(shade, match());

        scroll = new HomeScroll(this);
        scroll.setFocusable(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        content.setPadding(0, 0, 0, Math.round(14 * u));
        scroll.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, match());

        // Zone haute (« top shelf ») : nom de l'appli sélectionnée, en grand.
        header = new FrameLayout(this);
        title = new TextView(this);
        title.setTypeface(medium);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, 5.4f * u);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setShadowLayer(1.4f * u, 0, 0.2f * u, 0x80000000);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START);
        tlp.leftMargin = side;
        tlp.rightMargin = side;
        tlp.bottomMargin = Math.round(4.2f * u);
        header.addView(title, tlp);

        // Heure + date, en haut à droite.
        LinearLayout clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.VERTICAL);
        clock.setGravity(Gravity.END);
        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("HH:mm");
        time.setTypeface(light);
        time.setTextColor(0xFFFFFFFF);
        time.setTextSize(TypedValue.COMPLEX_UNIT_PX, 4.4f * u);
        time.setShadowLayer(1f * u, 0, 0.2f * u, 0x80000000);
        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE d MMMM");
        date.setFormat12Hour("EEEE d MMMM");
        date.setTypeface(medium);
        date.setTextColor(0xB3FFFFFF);
        date.setTextSize(TypedValue.COMPLEX_UNIT_PX, 1.9f * u);
        date.setShadowLayer(1f * u, 0, 0.2f * u, 0x80000000);
        clock.addView(time);
        clock.addView(date);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        clp.topMargin = Math.round(5.5f * u);
        clp.rightMargin = side;
        root.addView(clock, clp);
        clockBox = clock;
        if (Config.SHOW_PRAYER_TIMES) buildPrayers(root);

        scroll.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int x, int y, int ox, int oy) {
                float a = 1f - Math.min(1f, y / (headerH * 0.45f));
                clockBox.setAlpha(a);
                if (prayerBox != null) prayerBox.setAlpha(a);
            }
        });

        // Boutons discrets en bas de page.
        pillRow = new LinearLayout(this);
        pillRow.setOrientation(LinearLayout.HORIZONTAL);
        pillRow.setClipChildren(false);
        pillRow.setClipToPadding(false);
        pillSettings = makePill("Réglages", new Runnable() {
            @Override public void run() {
                safeStart(new Intent(Settings.ACTION_SETTINGS));
            }
        });
        pillHidden = makePill("", new Runnable() {
            @Override public void run() { toggleHidden(); }
        });
        pillRow.addView(pillSettings);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.leftMargin = Math.round(1.6f * u);
        pillRow.addView(pillHidden, plp);

        setContentView(root);
    }

    // ------------------------------------------------------------------ horaires de prière

    private void buildPrayers(FrameLayout root) {
        prayerBox = new LinearLayout(this);
        prayerBox.setOrientation(LinearLayout.VERTICAL);
        final int padH = Math.round(1.4f * u), padV = Math.round(0.7f * u);

        prayerCaption = new TextView(this);
        prayerCaption.setTypeface(medium);
        prayerCaption.setTextColor(0x99FFFFFF);
        prayerCaption.setTextSize(TypedValue.COMPLEX_UNIT_PX, 1.55f * u);
        prayerCaption.setLetterSpacing(0.12f);
        prayerCaption.setShadowLayer(1f * u, 0, 0.2f * u, 0x80000000);
        LinearLayout.LayoutParams cap = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cap.leftMargin = padH;
        cap.bottomMargin = Math.round(0.3f * u);
        prayerBox.addView(prayerCaption, cap);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 6; i++) {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(padH, padV, padH, padV);
            pPill[i] = new GradientDrawable();
            pPill[i].setCornerRadius(1.3f * u);
            pPill[i].setColor(0x00FFFFFF);
            col.setBackground(pPill[i]);

            TextView n = new TextView(this);
            n.setText(PrayerTimes.NAMES[i].toUpperCase(Locale.FRENCH));
            n.setTypeface(medium);
            n.setTextColor(0xFFFFFFFF);
            n.setTextSize(TypedValue.COMPLEX_UNIT_PX, 1.45f * u);
            n.setLetterSpacing(0.1f);
            TextView t = new TextView(this);
            t.setTypeface(light);
            t.setTextColor(0xFFFFFFFF);
            t.setTextSize(TypedValue.COMPLEX_UNIT_PX, 3.5f * u);
            t.setIncludeFontPadding(false);
            TextView l = new TextView(this);
            l.setTypeface(medium);
            l.setTextColor(0xD9FFFFFF);
            l.setTextSize(TypedValue.COMPLEX_UNIT_PX, 1.45f * u);
            l.setVisibility(View.INVISIBLE);
            for (TextView tv : new TextView[]{n, t, l}) tv.setShadowLayer(1f * u, 0, 0.2f * u, 0x66000000);
            col.addView(n);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.topMargin = Math.round(0.4f * u);
            tlp.bottomMargin = Math.round(0.3f * u);
            col.addView(t, tlp);
            col.addView(l);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.leftMargin = Math.round(0.5f * u);
            row.addView(col, lp);
            pCol[i] = col; pName[i] = n; pTime[i] = t; pLeft[i] = l;
        }
        prayerBox.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        plp.leftMargin = side - padH;
        plp.topMargin = Math.round(5.0f * u);
        root.addView(prayerBox, plp);
    }

    /** Met à jour les horaires du jour (ou de demain après Icha) et met en avant la prochaine prière. */
    private void updatePrayers() {
        if (prayerBox == null) return;
        Calendar now = Calendar.getInstance(PrayerTimes.TZ);
        maybeFetchPrayers(now);
        int nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int[] t = timesFor(now);

        boolean tomorrow = false;
        int current = -1, next = -1;
        for (int i = 0; i < 6; i++) {
            if (i == PrayerTimes.SUNRISE) continue;
            if (nowMin >= t[i] && nowMin < t[i] + 10) current = i; // l'heure vient d'arriver
            if (next < 0 && t[i] > nowMin) next = i;
        }
        if (next < 0) {
            Calendar tm = (Calendar) now.clone();
            tm.add(Calendar.DAY_OF_MONTH, 1);
            if (current < 0) {
                t = timesFor(tm);
                tomorrow = true;
            }
            next = 0;
        }
        int hl = current >= 0 ? current : next;
        int left = tomorrow ? t[next] + 1440 - nowMin : t[next] - nowMin;

        prayerCaption.setText((tomorrow ? "DEMAIN · " : "PRIÈRES · ")
                + PrayerTimes.CITY.toUpperCase(Locale.FRENCH)
                + (usingFallback ? " · CALCULÉ HORS LIGNE" : ""));
        for (int i = 0; i < 6; i++) {
            boolean on = i == hl;
            boolean past = !tomorrow && !on && t[i] <= nowMin;
            int m = t[i];
            pTime[i].setText(String.format(Locale.FRENCH, "%02d:%02d", (m / 60) % 24, m % 60));
            pTime[i].setTypeface(on ? medium : light);
            pPill[i].setColor(on ? 0x33FFFFFF : 0x00FFFFFF);
            float alpha = on ? 1f : past ? 0.35f : (i == PrayerTimes.SUNRISE ? 0.5f : 0.75f);
            pName[i].setAlpha(alpha);
            pTime[i].setAlpha(alpha);
            if (on) {
                pLeft[i].setText(current >= 0 ? "maintenant" : "dans " + formatLeft(left));
                pLeft[i].setVisibility(View.VISIBLE);
            } else {
                pLeft[i].setVisibility(View.INVISIBLE);
            }
        }
    }

    /** Horaires du Guide Musulman si on les a pour ce jour, sinon le calcul hors ligne. */
    private int[] timesFor(Calendar day) {
        int[] t = gmCache == null ? null : gmCache.get(GuideMusulman.dayKey(day));
        usingFallback = t == null;
        if (t != null) return t;
        return PrayerTimes.forDay(day.get(Calendar.YEAR), day.get(Calendar.MONTH) + 1,
                day.get(Calendar.DAY_OF_MONTH));
    }

    /** Rafraîchit la copie locale du Guide Musulman toutes les 6 h (ou s'il manque aujourd'hui / demain). */
    private void maybeFetchPrayers(final Calendar now) {
        long t = System.currentTimeMillis();
        if (gmLoading || t - gmLastAttempt < 15 * 60 * 1000L) return;
        Calendar tm = (Calendar) now.clone();
        tm.add(Calendar.DAY_OF_MONTH, 1);
        boolean missing = !gmCache.containsKey(GuideMusulman.dayKey(now))
                || !gmCache.containsKey(GuideMusulman.dayKey(tm));
        boolean stale = t - prefs.getLong("gm_fetched", 0) > 6 * 3600 * 1000L;
        if (!missing && !stale) return;
        gmLoading = true;
        gmLastAttempt = t;
        final Calendar today = (Calendar) now.clone();
        new Thread(new Runnable() {
            @Override public void run() {
                Map<String, int[]> fresh = null;
                try {
                    fresh = GuideMusulman.parse(GuideMusulman.download(), today);
                    android.util.Log.i("Accueil", "Guide Musulman : " + fresh.size() + " jours reçus");
                } catch (Throwable e) {
                    android.util.Log.w("Accueil", "Guide Musulman indisponible : " + e);
                }
                final Map<String, int[]> got = fresh;
                ui.post(new Runnable() {
                    @Override public void run() {
                        gmLoading = false;
                        if (got != null && !got.isEmpty()) {
                            GuideMusulman.save(prefs, gmCache, got, GuideMusulman.dayKey(today));
                            updatePrayers();
                        }
                    }
                });
            }
        }, "accueil-prieres").start();
    }

    private static String formatLeft(int min) {
        if (min < 60) return min + " min";
        return String.format(Locale.FRENCH, "%d h %02d", min / 60, min % 60);
    }

    private void layoutGrid() {
        for (Tile t : tiles.values()) detach(t.cell);
        detach(pillRow);
        detach(header);
        content.removeAllViews();

        content.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, headerH));

        List<App> vis = visibleApps();
        List<App> hid = hiddenApps();
        firstRow = addRows(vis, 1f);

        pillHidden.setText(showHidden ? "Fermer les applis masquées"
                : "Applis masquées (" + hid.size() + ")");
        pillHidden.setVisibility(hid.isEmpty() ? View.GONE : View.VISIBLE);
        LinearLayout.LayoutParams prl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        prl.leftMargin = side;
        prl.topMargin = Math.round(1.5f * u);
        content.addView(pillRow, prl);

        if (showHidden && !hid.isEmpty()) {
            TextView sec = new TextView(this);
            sec.setText("Applis masquées");
            sec.setTypeface(medium);
            sec.setTextColor(0x99FFFFFF);
            sec.setTextSize(TypedValue.COMPLEX_UNIT_PX, 2.4f * u);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            slp.leftMargin = side;
            slp.topMargin = Math.round(6 * u);
            slp.bottomMargin = Math.round(2.4f * u);
            content.addView(sec, slp);
            addRows(hid, 0.5f);
        }
    }

    private View addRows(List<App> list, float alpha) {
        View first = null;
        for (int i = 0; i < list.size(); i += COLS) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setClipChildren(false);
            row.setClipToPadding(false);
            for (int k = i; k < Math.min(i + COLS, list.size()); k++) {
                Tile t = tiles.get(list.get(k).key);
                if (t == null) continue;
                t.baseAlpha = alpha;
                t.cell.setAlpha(t.card.isFocused() ? 1f : alpha);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        tileW, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (k > i) lp.leftMargin = gap;
                row.addView(t.cell, lp);
            }
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.leftMargin = side;
            rlp.rightMargin = side;
            rlp.bottomMargin = rowGap;
            content.addView(row, rlp);
            if (first == null) first = row;
        }
        return first;
    }

    private Tile makeTile(final App a) {
        final Tile t = new Tile();
        t.app = a;

        t.cell = new LinearLayout(this);
        t.cell.setOrientation(LinearLayout.VERTICAL);
        t.cell.setClipChildren(false);
        t.cell.setClipToPadding(false);

        t.card = new FrameLayout(this);
        t.card.setFocusable(true);
        t.card.setClickable(true);
        noDefaultHighlight(t.card);
        t.card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        t.card.setClipToOutline(true);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{blend(a.tint, 0xFFFFFFFF, 0.10f), blend(a.tint, 0xFF000000, 0.30f)});
        bg.setCornerRadius(radius);
        t.card.setBackground(bg);

        ImageView iv = new ImageView(this);
        if (a.banner != null) {
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageDrawable(a.banner);
            t.card.addView(iv, match());
        } else {
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setImageDrawable(a.icon);
            int s = Math.round(tileH * 0.56f);
            t.card.addView(iv, new FrameLayout.LayoutParams(s, s, Gravity.CENTER));
        }

        // Reflet léger, visible quand la vignette est sélectionnée.
        t.sheen = new View(this);
        t.sheen.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0x40FFFFFF, 0x0DFFFFFF, 0x00FFFFFF}));
        t.sheen.setAlpha(0f);
        t.card.addView(t.sheen, match());

        t.cell.addView(t.card, new LinearLayout.LayoutParams(tileW, tileH));

        t.label = new TextView(this);
        t.label.setText(a.label);
        t.label.setTypeface(medium);
        t.label.setTextColor(0xFFFFFFFF);
        t.label.setTextSize(TypedValue.COMPLEX_UNIT_PX, 2.1f * u);
        t.label.setSingleLine(true);
        t.label.setEllipsize(TextUtils.TruncateAt.END);
        t.label.setGravity(Gravity.CENTER_HORIZONTAL);
        t.label.setAlpha(0f);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                tileW, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = Math.round(tileH * 0.1f);
        t.cell.addView(t.label, llp);

        t.card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                float s = has ? 1.12f : 1f;
                v.animate().scaleX(s).scaleY(s).translationZ(has ? 1.8f * u : 0f)
                        .setDuration(has ? 240 : 180).setInterpolator(EASE).start();
                t.sheen.animate().alpha(has ? 1f : 0f).setDuration(240).start();
                t.label.animate().alpha(has ? 1f : 0f).translationY(has ? tileH * 0.06f : 0f)
                        .setDuration(220).setInterpolator(EASE).start();
                t.cell.animate().alpha(has ? 1f : t.baseAlpha).setDuration(200).start();
                if (has) onTileFocused(t);
            }
        });
        t.card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (movingKey != null) { endMove(); return; }
                launch(a);
            }
        });
        t.card.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                if (movingKey == null) showOptions(a);
                return true;
            }
        });
        t.card.setOnKeyListener(new View.OnKeyListener() {
            @Override public boolean onKey(View v, int code, KeyEvent e) {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (movingKey != null && movingKey.equals(a.key)) {
                    switch (code) {
                        case KeyEvent.KEYCODE_DPAD_LEFT: moveBy(-1); return true;
                        case KeyEvent.KEYCODE_DPAD_RIGHT: moveBy(1); return true;
                        case KeyEvent.KEYCODE_DPAD_UP: moveBy(-COLS); return true;
                        case KeyEvent.KEYCODE_DPAD_DOWN: moveBy(COLS); return true;
                        default: return false;
                    }
                }
                if (code == KeyEvent.KEYCODE_MENU && movingKey == null) {
                    showOptions(a);
                    return true;
                }
                return false;
            }
        });
        return t;
    }

    private TextView makePill(String text, final Runnable action) {
        final TextView p = new TextView(this);
        p.setText(text);
        p.setTypeface(medium);
        p.setTextSize(TypedValue.COMPLEX_UNIT_PX, 2.0f * u);
        p.setTextColor(0xD9FFFFFF);
        int ph = Math.round(2.2f * u), pv = Math.round(1.0f * u);
        p.setPadding(ph, pv, ph, pv);
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(10 * u);
        bg.setColor(0x24FFFFFF);
        p.setBackground(bg);
        p.setFocusable(true);
        p.setClickable(true);
        noDefaultHighlight(p);
        p.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                bg.setColor(has ? 0xFFFFFFFF : 0x24FFFFFF);
                p.setTextColor(has ? 0xFF000000 : 0xD9FFFFFF);
                v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f)
                        .setDuration(180).setInterpolator(EASE).start();
                if (has) {
                    final int target = Math.max(0, pillRow.getTop() - Math.round(screenH * 0.55f));
                    scroll.smoothScrollTo(0, target);
                }
            }
        });
        p.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { action.run(); }
        });
        return p;
    }

    // ------------------------------------------------------------------ focus, fond, défilement

    private void onTileFocused(final Tile t) {
        focusedKey = t.app.key;
        setAmbient(t.app);
        if (!t.app.label.contentEquals(title.getText())) {
            title.setText(t.app.label);
            title.setAlpha(0.25f);
            title.animate().alpha(1f).setDuration(260).start();
        }
        scroll.post(new Runnable() {
            @Override public void run() {
                View row = (View) t.cell.getParent();
                if (row == null) return;
                int target = (row == firstRow) ? 0 : Math.max(0, row.getTop() - Math.round(screenH * 0.30f));
                scroll.smoothScrollTo(0, target);
            }
        });
    }

    private void setAmbient(App a) {
        if (a.key.equals(ambientKey) || a.ambient == null) return;
        ambientKey = a.key;
        ImageView front = bgAFront ? bgA : bgB;
        ImageView back = bgAFront ? bgB : bgA;
        back.setImageBitmap(a.ambient);
        back.animate().alpha(1f).setDuration(520).setInterpolator(EASE).start();
        front.animate().alpha(0f).setDuration(520).setInterpolator(EASE).start();
        bgAFront = !bgAFront;
    }

    private void restoreFocus() {
        Tile t = focusedKey != null ? tiles.get(focusedKey) : null;
        if (t == null || t.cell.getParent() == null) t = firstVisibleTile();
        if (t != null) t.card.requestFocus();
        else if (pillSettings.getParent() != null) pillSettings.requestFocus();
    }

    private Tile firstVisibleTile() {
        for (App a : apps) {
            if (!hidden.contains(a.key)) {
                Tile t = tiles.get(a.key);
                if (t != null && t.cell.getParent() != null) return t;
            }
        }
        return null;
    }

    private void goTop() {
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
        endMove();
        if (showHidden) { showHidden = false; layoutGrid(); }
        scroll.smoothScrollTo(0, 0);
        Tile t = firstVisibleTile();
        if (t != null) t.card.requestFocus();
    }

    // ------------------------------------------------------------------ actions

    private void launch(App a) {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(a.leanback ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER)
                .setComponent(new ComponentName(a.pkg, a.cls))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        safeStart(i);
    }

    private void safeStart(Intent i) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Impossible d'ouvrir cette appli", Toast.LENGTH_SHORT).show();
        }
    }

    private void showOptions(final App a) {
        final boolean isHidden = hidden.contains(a.key);
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        labels.add("Ouvrir");
        actions.add(new Runnable() { @Override public void run() { launch(a); } });
        if (!isHidden) {
            labels.add("Déplacer");
            actions.add(new Runnable() { @Override public void run() { startMove(a); } });
            labels.add("Masquer de l'accueil");
            actions.add(new Runnable() { @Override public void run() { setHidden(a, true); } });
        } else {
            labels.add("Remettre sur l'accueil");
            actions.add(new Runnable() { @Override public void run() { setHidden(a, false); } });
        }
        labels.add("Infos sur l'application");
        actions.add(new Runnable() {
            @Override public void run() {
                safeStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", a.pkg, null)));
            }
        });
        dialog = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(a.label)
                .setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        actions.get(which).run();
                    }
                })
                .create();
        dialog.show();
    }

    private void setHidden(App a, boolean hide) {
        if (hide) {
            List<App> vis = visibleApps();
            int i = indexOf(vis, a.key);
            hidden.add(a.key);
            vis.remove(i);
            focusedKey = vis.isEmpty() ? null : vis.get(Math.min(i, vis.size() - 1)).key;
        } else {
            hidden.remove(a.key);
            focusedKey = a.key;
        }
        prefs.edit().putStringSet("hidden", new HashSet<>(hidden)).apply();
        layoutGrid();
        ui.post(new Runnable() { @Override public void run() { restoreFocus(); } });
    }

    private void toggleHidden() {
        showHidden = !showHidden;
        layoutGrid();
        ui.post(new Runnable() {
            @Override public void run() {
                if (pillHidden.getVisibility() == View.VISIBLE) pillHidden.requestFocus();
                else restoreFocus();
            }
        });
    }

    private void startMove(App a) {
        Tile t = tiles.get(a.key);
        if (t == null) return;
        movingKey = a.key;
        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(radius);
        ring.setStroke(Math.max(2, Math.round(0.35f * u)), 0xFFFFFFFF);
        t.card.setForeground(ring);
        t.label.setText("‹   déplacer   ›");
        t.wiggle = ObjectAnimator.ofFloat(t.card, "rotation", -1.1f, 1.1f);
        t.wiggle.setDuration(150);
        t.wiggle.setRepeatMode(ValueAnimator.REVERSE);
        t.wiggle.setRepeatCount(ValueAnimator.INFINITE);
        t.wiggle.start();
        t.card.requestFocus();
    }

    private void endMove() {
        if (movingKey == null) return;
        Tile t = tiles.get(movingKey);
        movingKey = null;
        if (t == null) return;
        if (t.wiggle != null) t.wiggle.cancel();
        t.card.setRotation(0f);
        t.card.setForeground(null);
        t.label.setText(t.app.label);
    }

    private void moveBy(int delta) {
        List<App> vis = visibleApps();
        int i = indexOf(vis, movingKey);
        if (i < 0) return;
        int j = i + delta;
        if (j < 0) return;
        if (j >= vis.size()) {
            if (i / COLS == (vis.size() - 1) / COLS) return;
            j = vis.size() - 1;
        }
        App m = vis.remove(i);
        vis.add(j, m);
        List<App> order = new ArrayList<>(vis);
        for (App a : apps) if (hidden.contains(a.key)) order.add(a);
        apps.clear();
        apps.addAll(order);
        saveOrder();
        layoutGrid();
        final Tile t = tiles.get(m.key);
        if (t != null) t.card.requestFocus();
    }

    // ------------------------------------------------------------------ données

    private List<App> visibleApps() {
        List<App> out = new ArrayList<>();
        for (App a : apps) if (!hidden.contains(a.key)) out.add(a);
        return out;
    }

    private List<App> hiddenApps() {
        List<App> out = new ArrayList<>();
        for (App a : apps) if (hidden.contains(a.key)) out.add(a);
        return out;
    }

    private static int indexOf(List<App> list, String key) {
        for (int i = 0; i < list.size(); i++) if (list.get(i).key.equals(key)) return i;
        return -1;
    }

    private void saveOrder() {
        StringBuilder sb = new StringBuilder();
        for (App a : apps) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(a.key);
        }
        prefs.edit().putString("order", sb.toString()).apply();
    }

    private void applyOrder() {
        String saved = prefs.getString("order", "");
        final Map<String, Integer> idx = new HashMap<>();
        if (!saved.isEmpty()) {
            String[] keys = saved.split("\n");
            for (int i = 0; i < keys.length; i++) idx.put(keys[i], i);
        }
        final Map<String, Integer> prio = new HashMap<>();
        for (int i = 0; i < PRIORITY.length; i++) prio.put(PRIORITY[i], i);
        Collections.sort(apps, new Comparator<App>() {
            @Override public int compare(App x, App y) {
                Integer ix = idx.get(x.key), iy = idx.get(y.key);
                if (ix != null && iy != null) return ix - iy;
                if (ix != null) return -1;
                if (iy != null) return 1;
                Integer px = prio.get(x.pkg), py = prio.get(y.pkg);
                if (px != null && py != null) return px - py;
                if (px != null) return -1;
                if (py != null) return 1;
                return x.label.compareToIgnoreCase(y.label);
            }
        });
        saveOrder();
    }

    private void loadApps() {
        final int gen = ++loadGeneration;
        new Thread(new Runnable() {
            @Override public void run() {
                final List<App> list = queryApps();
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (gen != loadGeneration || isDestroyed()) return;
                        endMove();
                        apps.clear();
                        apps.addAll(list);
                        applyOrder();
                        tiles.clear();
                        for (App a : apps) tiles.put(a.key, makeTile(a));
                        layoutGrid();
                        ui.post(new Runnable() { @Override public void run() { restoreFocus(); } });
                    }
                });
            }
        }, "accueil-load").start();
    }

    private List<App> queryApps() {
        Map<String, App> map = new LinkedHashMap<>();
        Set<String> pkgs = new HashSet<>();
        Intent lb = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(lb, 0)) {
            App a = build(ri, true);
            if (a != null) { map.put(a.key, a); pkgs.add(a.pkg); }
        }
        // Applis installées à la main qui n'ont pas d'icône TV.
        Intent ln = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(ln, 0)) {
            if ((ri.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            if (pkgs.contains(ri.activityInfo.packageName)) continue;
            App a = build(ri, false);
            if (a != null) { map.put(a.key, a); pkgs.add(a.pkg); }
        }
        return new ArrayList<>(map.values());
    }

    private App build(ResolveInfo ri, boolean leanback) {
        try {
            if (getPackageName().equals(ri.activityInfo.packageName)) return null;
            App a = new App();
            a.pkg = ri.activityInfo.packageName;
            a.cls = ri.activityInfo.name;
            a.key = a.pkg + "/" + a.cls;
            a.leanback = leanback;
            CharSequence l = ri.loadLabel(pm);
            a.label = l == null ? a.pkg : l.toString().trim();

            Drawable icon = loadBestIcon(ri);
            a.tint = dominantColor(icon);
            int is = Math.round(tileH * 0.56f * 1.15f);
            a.icon = rasterize(icon, is, is, false);

            Drawable banner = null;
            try { banner = ri.activityInfo.loadBanner(pm); } catch (Throwable ignored) { }
            if (banner != null) {
                a.banner = rasterize(banner, Math.round(tileW * 1.15f), Math.round(tileH * 1.15f), true);
            }
            a.ambient = makeAmbient(a);
            return a;
        } catch (Throwable t) {
            return null;
        }
    }

    private Drawable loadBestIcon(ResolveInfo ri) {
        try {
            int res = ri.getIconResource();
            if (res != 0) {
                Resources r = pm.getResourcesForApplication(ri.activityInfo.applicationInfo);
                Drawable d = r.getDrawableForDensity(res, DisplayMetrics.DENSITY_XXXHIGH, null);
                if (d != null) return d;
            }
        } catch (Throwable ignored) { }
        return ri.loadIcon(pm);
    }

    private BitmapDrawable rasterize(Drawable d, int w, int h, boolean crop) {
        Bitmap b = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        int iw = d.getIntrinsicWidth(), ih = d.getIntrinsicHeight();
        if (iw > 0 && ih > 0) {
            float s = crop ? Math.max(w / (float) iw, h / (float) ih)
                    : Math.min(w / (float) iw, h / (float) ih);
            int dw = Math.round(iw * s), dh = Math.round(ih * s);
            int l = (w - dw) / 2, t = (h - dh) / 2;
            d.setBounds(l, t, l + dw, t + dh);
        } else {
            d.setBounds(0, 0, w, h);
        }
        d.draw(c);
        BitmapDrawable bd = new BitmapDrawable(getResources(), b);
        bd.setFilterBitmap(true);
        return bd;
    }

    /** Fond d'ambiance : halo sombre de la couleur dominante de l'appli, très flouté. */
    private Bitmap makeAmbient(App a) {
        final int w = 64, h = 36;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(0xFF000000);
        int dom = a.banner != null ? dominantColor(a.banner) : a.tint;
        Drawable art = a.banner != null ? a.banner : a.icon;
        if (art != null) {
            if (a.banner != null) art.setBounds(0, 0, w, h); else art.setBounds(19, 5, 45, 31);
            art.setAlpha(45);
            art.draw(c);
            art.setAlpha(255);
        }
        Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        glow.setShader(new RadialGradient(w * 0.5f, h * 0.28f, w * 0.8f,
                new int[]{(dom & 0x00FFFFFF) | 0xE6000000, (dom & 0x00FFFFFF) | 0x59000000, 0x00000000},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, glow);
        int[] p = new int[w * h];
        b.getPixels(p, 0, w, 0, 0, w, h);
        int[] tmp = new int[w * h];
        for (int pass = 0; pass < 3; pass++) {
            boxH(p, tmp, w, h, 3);
            boxV(tmp, p, w, h, 3);
        }
        b.setPixels(p, 0, w, 0, 0, w, h);
        return b;
    }

    private static void boxH(int[] in, int[] out, int w, int h, int r) {
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int rr = 0, gg = 0, bb = 0, n = 0;
                for (int k = -r; k <= r; k++) {
                    int xx = Math.min(w - 1, Math.max(0, x + k));
                    int c = in[row + xx];
                    rr += (c >> 16) & 255; gg += (c >> 8) & 255; bb += c & 255; n++;
                }
                out[row + x] = 0xFF000000 | ((rr / n) << 16) | ((gg / n) << 8) | (bb / n);
            }
        }
    }

    private static void boxV(int[] in, int[] out, int w, int h, int r) {
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int rr = 0, gg = 0, bb = 0, n = 0;
                for (int k = -r; k <= r; k++) {
                    int yy = Math.min(h - 1, Math.max(0, y + k));
                    int c = in[yy * w + x];
                    rr += (c >> 16) & 255; gg += (c >> 8) & 255; bb += c & 255; n++;
                }
                out[y * w + x] = 0xFF000000 | ((rr / n) << 16) | ((gg / n) << 8) | (bb / n);
            }
        }
    }

    /** Couleur dominante d'une icône, assombrie pour servir de fond de vignette. */
    private static int dominantColor(Drawable d) {
        final int fallback = 0xFF3A3A3C;
        try {
            Bitmap b = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            d.setBounds(0, 0, 24, 24);
            d.draw(c);
            int[] px = new int[24 * 24];
            b.getPixels(px, 0, 24, 0, 0, 24, 24);
            float[] hsv = new float[3];
            float[] weight = new float[12];
            float[][] acc = new float[12][3];
            for (int p : px) {
                if ((p >>> 24) < 160) continue;
                Color.colorToHSV(p, hsv);
                float wt = hsv[1] * hsv[2];
                if (wt < 0.12f) continue;
                int bin = ((int) (hsv[0] / 30f)) % 12;
                weight[bin] += wt;
                acc[bin][0] += Color.red(p) * wt;
                acc[bin][1] += Color.green(p) * wt;
                acc[bin][2] += Color.blue(p) * wt;
            }
            int best = -1;
            float bw = 0f;
            for (int i = 0; i < 12; i++) if (weight[i] > bw) { bw = weight[i]; best = i; }
            if (best < 0 || bw < 2f) return fallback;
            int col = Color.rgb((int) (acc[best][0] / bw), (int) (acc[best][1] / bw), (int) (acc[best][2] / bw));
            Color.colorToHSV(col, hsv);
            hsv[1] = Math.min(hsv[1], 0.8f);
            hsv[2] = Math.max(0.32f, Math.min(hsv[2], 0.6f));
            return Color.HSVToColor(hsv);
        } catch (Throwable t) {
            return fallback;
        }
    }

    // ------------------------------------------------------------------ utilitaires

    private void registerPackageReceiver() {
        pkgReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                ui.removeCallbacks(reload);
                ui.postDelayed(reload, 700);
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        registerReceiver(pkgReceiver, f);
    }

    private static void detach(View v) {
        ViewGroup p = (ViewGroup) v.getParent();
        if (p != null) p.removeView(v);
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static int blend(int a, int b, float t) {
        int r = Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t);
        int g = Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t);
        int bl = Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t);
        return Color.rgb(r, g, bl);
    }

    /** Retire le voile gris qu'Android 8+ ajoute par défaut sur les éléments sélectionnés. */
    private static void noDefaultHighlight(View v) {
        try {
            View.class.getMethod("setDefaultFocusHighlightEnabled", boolean.class).invoke(v, false);
        } catch (Throwable ignored) { }
    }
}
