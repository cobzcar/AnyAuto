package com.mag.anyauto;

import android.app.Activity;
import android.app.ActivityOptions;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * The dashboard on the car display, three columns: the weather widget over Poweramp's on the left,
 * a wide navigation tile in the middle, the clock over two picture cards on the right. It is an
 * ordinary activity: DisplayService starts it on the virtual display, the display draws it into the
 * car, and the car's taps reach it as touches. A tile starts its app on the same display; Back from
 * that app returns here. Everything is chosen on the phone, in {@link DashboardSetupActivity}.
 * Exported because the shell user starts it (a non-exported activity would be refused).
 */
public final class DashboardActivity extends Activity {
    private static final int BG = Color.rgb(12, 13, 17), TILE = Color.rgb(32, 34, 42),
            TEXT = Color.WHITE, TEXT2 = Color.rgb(170, 176, 188);

    private AppWidgetHost host;
    private FrameLayout weatherBox, musicBox, mapBox, card1Box, card2Box;
    private AppWidgetHostView weatherView, musicView;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        host = new AppWidgetHost(this, Widgets.HOST_ID);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(8), dp(8), dp(8), dp(8));

        // left: weather over Poweramp
        weatherBox = box();
        musicBox = box();
        LinearLayout left = column();
        left.addView(weatherBox, cell());
        left.addView(musicBox, cell());
        root.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 3f));

        // middle: the navigation tile
        mapBox = box();
        LinearLayout middle = column();
        middle.addView(mapBox, cell());
        root.addView(middle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 3f));

        // right: the clock over two cards
        card1Box = box();
        card2Box = box();
        LinearLayout right = column();
        right.addView(clock(), cell());
        right.addView(card1Box, cell());
        right.addView(card2Box, cell());
        root.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 2.4f));

        setContentView(root);

        // a widget gets the room it has here, so it picks the layout that fits
        weatherBox.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> size(weatherBox, weatherView));
        musicBox.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> size(musicBox, musicView));
    }

    @Override
    protected void onStart() {
        super.onStart();
        host.startListening(); // without it the widgets never update
        show(); // also when coming back from an app: what was changed on the phone shows
    }

    @Override
    protected void onStop() {
        host.stopListening();
        super.onStop();
    }

    private void show() {
        weatherView = placeWidget(weatherBox, Widgets.WEATHER, "Weather widget");
        musicView = placeWidget(musicBox, Widgets.MUSIC, "Poweramp widget");
        List<Apps.App> apps = Apps.launchable(this);
        fillTile(mapBox, "Navigation", find(apps, Dash.mapApp(this)), null);
        fillTile(card1Box, null, find(apps, Prefs.dashApp(this, "card1")), Dash.loadCardImage(this, 1));
        fillTile(card2Box, null, find(apps, Prefs.dashApp(this, "card2")), Dash.loadCardImage(this, 2));
    }

    private static Apps.App find(List<Apps.App> apps, ComponentName cn) {
        if (cn == null) return null;
        for (Apps.App a : apps) {
            if (a.component.equals(cn)) return a;
        }
        return null;
    }

    // ------------------------------------------------------------------ widgets

    private AppWidgetHostView placeWidget(FrameLayout box, String slot, String name) {
        box.removeAllViews();
        int id = Prefs.widgetId(this, slot);
        AppWidgetProviderInfo info = id == -1 ? null : AppWidgetManager.getInstance(this).getAppWidgetInfo(id);
        if (info == null) {
            box.addView(hintView(name), matchParent());
            return null;
        }
        try {
            AppWidgetHostView v = host.createView(this, id, info);
            box.addView(v, matchParent());
            box.post(() -> size(box, v));
            return v;
        } catch (RuntimeException e) {
            box.addView(hintView("Can't show the " + name.toLowerCase()), matchParent());
            return null;
        }
    }

    private void size(FrameLayout box, AppWidgetHostView v) {
        if (v == null || box.getWidth() <= 0 || box.getHeight() <= 0) return;
        float d = getResources().getDisplayMetrics().density;
        int w = Math.round(box.getWidth() / d), h = Math.round(box.getHeight() / d);
        v.updateAppWidgetSize(null, w, h, w, h);
    }

    // ------------------------------------------------------------------ tiles

    /** A tile: the picture or the app's icon, the app's name at the bottom; a tap starts the app. */
    private void fillTile(FrameLayout box, String title, Apps.App app, Bitmap image) {
        box.removeAllViews();
        if (image != null) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageBitmap(image);
            box.addView(iv, matchParent());
        } else if (app != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(app.icon(getPackageManager()));
            box.addView(iv, new FrameLayout.LayoutParams(dp(72), dp(72), Gravity.CENTER));
        }
        if (app != null) {
            TextView t = Ui.text(this, title == null ? app.label : title + " · " + app.label, 16, true, TEXT);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            t.setPadding(dp(8), dp(6), dp(8), dp(6));
            t.setBackgroundColor(Color.argb(150, 0, 0, 0));
            box.addView(t, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        } else if (image == null) {
            box.addView(hintView(title == null ? "Card" : title), matchParent());
        }
        ComponentName target = app == null ? null : app.component;
        box.setOnClickListener(v -> launch(target));
    }

    /**
     * Starts the app on this display through the same service that started the dashboard, so an
     * app already open on the phone moves here. Without the service, from this activity.
     */
    private void launch(ComponentName cn) {
        if (cn == null) {
            Toast.makeText(this, "Choose it on the phone: ⋮ → Dashboard", Toast.LENGTH_LONG).show();
            return;
        }
        new Thread(() -> {
            IDisplayService s = Shell.service();
            if (s != null) {
                try {
                    if ("ok".equals(s.launch(cn))) return;
                } catch (RemoteException ignore) {
                    // fall through to the plain start
                }
            }
            runOnUiThread(() -> startHere(cn));
        }, "anyauto-dash-launch").start();
    }

    private void startHere(ComponentName cn) {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(cn)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            int display = getWindowManager().getDefaultDisplay().getDisplayId();
            startActivity(i, ActivityOptions.makeBasic().setLaunchDisplayId(display).toBundle());
        } catch (RuntimeException e) {
            Toast.makeText(this, "Can't open it here", Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------------ clock and layout helpers

    /** The time, big, and the date under it; both follow the room they have. */
    private FrameLayout clock() {
        FrameLayout box = box();
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setPadding(dp(8), dp(4), dp(8), dp(4));

        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("h:mm a");
        time.setTextColor(TEXT);
        time.setTypeface(Typeface.DEFAULT_BOLD);
        time.setGravity(Gravity.CENTER);
        time.setMaxLines(1);
        time.setIncludeFontPadding(false);
        time.setAutoSizeTextTypeUniformWithConfiguration(16, 80, 2, TypedValue.COMPLEX_UNIT_SP);
        col.addView(time, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 2f));

        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE d MMMM");
        date.setFormat12Hour("EEEE d MMMM");
        date.setTextColor(TEXT2);
        date.setGravity(Gravity.CENTER);
        date.setMaxLines(1);
        date.setAutoSizeTextTypeUniformWithConfiguration(10, 24, 2, TypedValue.COMPLEX_UNIT_SP);
        col.addView(date, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        box.addView(col, matchParent());
        return box;
    }

    /** A rounded tile that clips what it holds (a widget's corners included). */
    private FrameLayout box() {
        FrameLayout f = new FrameLayout(this);
        f.setBackground(Ui.round(TILE, dp(18)));
        f.setClipToOutline(true);
        return f;
    }

    private LinearLayout column() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        return c;
    }

    private LinearLayout.LayoutParams cell() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        return lp;
    }

    private FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private TextView hintView(String what) {
        TextView t = Ui.text(this, what + "\nChoose it on the phone:\n⋮ → Dashboard", 14, false, TEXT2);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12), dp(12), dp(12), dp(12));
        return t;
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
