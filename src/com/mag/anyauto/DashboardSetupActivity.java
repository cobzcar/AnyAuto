package com.mag.anyauto;

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
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * Sets up the dashboard on the phone: the weather and Poweramp widgets, the app behind the
 * navigation tile, and the picture and app of each of the two cards. Everything saves at once;
 * {@link DashboardActivity} shows it in the car.
 */
public final class DashboardSetupActivity extends Activity {
    private static final int REQ_IMAGE = 10; // + the card's number

    private TextView weather, music, map, card1, card2;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setTitle("Dashboard");
        ActionBar ab = getActionBar();
        if (ab != null) ab.setDisplayHomeAsUpEnabled(true);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        TextView hint = Ui.text(this, "The dashboard for the car: weather and Poweramp widgets on the left, "
                + "a navigation tile in the middle, the clock and two cards on the right. "
                + "Open it from the car's apps.", 14, false, Ui.text2(this));
        hint.setPadding(dp(16), dp(16), dp(16), dp(8));
        list.addView(hint);

        weather = row(list, "Weather widget", v -> pickWidget(Widgets.WEATHER));
        music = row(list, "Poweramp widget", v -> pickWidget(Widgets.MUSIC));
        map = row(list, "Navigation tile", v -> chooseApp(Dash.MAP, true));
        card1 = row(list, "Card 1", v -> cardMenu(1));
        card2 = row(list, "Card 2", v -> cardMenu(2));

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
        weather.setText(Prefs.widgetId(this, Widgets.WEATHER) == -1 ? "Not chosen"
                : Prefs.widgetLabel(this, Widgets.WEATHER));
        music.setText(Prefs.widgetId(this, Widgets.MUSIC) == -1 ? "Not chosen"
                : Prefs.widgetLabel(this, Widgets.MUSIC));
        ComponentName m = Dash.mapApp(this);
        map.setText(m == null ? "Not chosen (no Google Maps or Waze found)"
                : appName(m) + (Prefs.dashApp(this, Dash.MAP) == null ? " (automatic)" : ""));
        card1.setText(cardSummary(1));
        card2.setText(cardSummary(2));
    }

    private String cardSummary(int n) {
        ComponentName app = Prefs.dashApp(this, "card" + n);
        return (Dash.cardImage(this, n).exists() ? "Picture set" : "No picture")
                + " · " + (app == null ? "no app" : appName(app));
    }

    private String appName(ComponentName cn) {
        try {
            PackageManager pm = getPackageManager();
            return String.valueOf(pm.getActivityInfo(cn, 0).loadLabel(pm));
        } catch (PackageManager.NameNotFoundException e) {
            return cn.getPackageName();
        }
    }

    private void pickWidget(String slot) {
        startActivity(new Intent(this, WidgetSetupActivity.class).putExtra(WidgetSetupActivity.EXTRA_SLOT, slot));
    }

    /** A card: its picture, its app, or both removed. */
    private void cardMenu(int n) {
        String[] items = {"Choose picture", "Choose app", "Remove picture and app"};
        new AlertDialog.Builder(this)
                .setTitle("Card " + n)
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                                .addCategory(Intent.CATEGORY_OPENABLE)
                                .setType("image/*");
                        startActivityForResult(pick, REQ_IMAGE + n);
                    } else if (which == 1) {
                        chooseApp("card" + n, false);
                    } else {
                        Dash.removeCardImage(this, n);
                        Prefs.setDashApp(this, "card" + n, null);
                        refresh();
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    /** Every app with a launcher icon; for the navigation tile, also "automatic" (Google Maps, else Waze). */
    private void chooseApp(String key, boolean automatic) {
        new Thread(() -> {
            List<Apps.App> apps = Apps.launchable(this);
            runOnUiThread(() -> {
                int offset = automatic ? 1 : 0;
                String[] labels = new String[apps.size() + offset];
                if (automatic) labels[0] = "Automatic (Google Maps, else Waze)";
                for (int i = 0; i < apps.size(); i++) labels[i + offset] = apps.get(i).label;
                new AlertDialog.Builder(this)
                        .setTitle(Dash.MAP.equals(key) ? "Navigation tile" : "Card app")
                        .setItems(labels, (d, which) -> {
                            Prefs.setDashApp(this, key, automatic && which == 0 ? null
                                    : apps.get(which - offset).component);
                            refresh();
                        })
                        .setNegativeButton("Close", null)
                        .show();
            });
        }, "anyauto-apps").start();
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        int n = request - REQ_IMAGE;
        if ((n != 1 && n != 2) || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        new Thread(() -> {
            boolean ok = Dash.saveCardImage(this, n, uri);
            runOnUiThread(() -> {
                if (!ok) Toast.makeText(this, "Can't read this picture", Toast.LENGTH_SHORT).show();
                refresh();
            });
        }, "anyauto-image").start();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
