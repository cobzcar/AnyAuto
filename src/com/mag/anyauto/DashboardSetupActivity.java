package com.mag.anyauto;

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * Sets up the dashboard on the phone: the Poweramp widget, the app that runs live on the left
 * (the map), and the location the speed and weather cards need. Everything saves at once;
 * {@link DashboardActivity} shows it in the car.
 */
public final class DashboardSetupActivity extends Activity {
    private static final int REQ_LOCATION = 20;

    private TextView music, cover, map, location;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setTitle("Dashboard");
        ActionBar ab = getActionBar();
        if (ab != null) ab.setDisplayHomeAsUpEnabled(true);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        TextView hint = Ui.text(this, "The dashboard for the car: the live map on the left, the clock "
                + "over the Poweramp widget in the middle, the weather over the speed on the right. "
                + "Open it from the car's apps.", 14, false, Ui.text2(this));
        hint.setPadding(dp(16), dp(16), dp(16), dp(8));
        list.addView(hint);

        music = row(list, "Poweramp widget", v -> pickWidget());
        cover = row(list, "Music card (cover, title, buttons)", v -> startActivity(
                new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        map = row(list, "Map (runs live on the left)", v -> chooseApp());
        location = row(list, "Location (speed and weather)", v -> askLocation());

        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            int top, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                top = insets.getInsets(WindowInsets.Type.systemBars()).top;
                bottom = insets.getInsets(WindowInsets.Type.systemBars()).bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(0, top, 0, bottom);
            return insets;
        });
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** A tappable row, title over summary; returns the summary to fill in. */
    private TextView row(LinearLayout parent, String title, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        TypedArray a = obtainStyledAttributes(new int[]{android.R.attr.selectableItemBackground});
        row.setForeground(a.getDrawable(0));
        a.recycle();
        row.setOnClickListener(click);
        row.addView(Ui.text(this, title, 16, false, Ui.text(this)));
        TextView summary = Ui.text(this, "", 14, false, Ui.text2(this));
        summary.setPadding(0, dp(2), 0, 0);
        row.addView(summary);
        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return summary;
    }

    private void refresh() {
        music.setText(Prefs.widgetId(this, Widgets.MUSIC) == -1 ? "Not chosen"
                : Prefs.widgetLabel(this, Widgets.MUSIC));
        cover.setText(MusicCard.allowed(this) ? "On: shows the playing track (Poweramp first)"
                : "Off. Tap, then allow AnyAuto under Notification access. Until then the Poweramp widget shows.");
        ComponentName m = Dash.mapApp(this);
        map.setText(m == null ? "Not chosen (no Google Maps or Waze found)"
                : appName(m) + (Prefs.dashApp(this, Dash.MAP) == null ? " (automatic)" : ""));
        location.setText(Dash.preciseAllowed(this) ? "Allowed"
                : Dash.locationAllowed(this) ? "Approximate only: the speed needs the precise location. Tap to change."
                : "Not allowed. Tap to allow: the speed and weather cards need it.");
    }

    private String appName(ComponentName cn) {
        try {
            PackageManager pm = getPackageManager();
            return String.valueOf(pm.getActivityInfo(cn, 0).loadLabel(pm));
        } catch (PackageManager.NameNotFoundException e) {
            return cn.getPackageName();
        }
    }

    private void pickWidget() {
        startActivity(new Intent(this, WidgetSetupActivity.class));
    }

    private void askLocation() {
        if (Dash.preciseAllowed(this)) return;
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        refresh();
        if (request == REQ_LOCATION && !Dash.preciseAllowed(this)) {
            // Android stops asking after a refusal or an approximate choice: the app's settings are the way
            new AlertDialog.Builder(this)
                    .setTitle("Precise location needed")
                    .setMessage("The speed card uses the GPS. Open AnyAuto's settings and allow the precise "
                            + "location while the app is in use.")
                    .setPositiveButton("Open settings", (d, w) -> startActivity(
                            new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", getPackageName(), null))))
                    .setNegativeButton("Close", null)
                    .show();
        }
    }

    /** Every app with a launcher icon, and "automatic" (Google Maps, else Waze). */
    private void chooseApp() {
        new Thread(() -> {
            List<Apps.App> apps = Apps.launchable(this);
            runOnUiThread(() -> {
                String[] labels = new String[apps.size() + 1];
                labels[0] = "Automatic (Google Maps, else Waze)";
                for (int i = 0; i < apps.size(); i++) labels[i + 1] = apps.get(i).label;
                new AlertDialog.Builder(this)
                        .setTitle("Map app")
                        .setItems(labels, (d, which) -> {
                            Prefs.setDashApp(this, Dash.MAP, which == 0 ? null : apps.get(which - 1).component);
                            refresh();
                        })
                        .setNegativeButton("Close", null)
                        .show();
            });
        }, "anyauto-apps").start();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
