package com.mag.anyauto;

import android.app.ActionBar;
import android.app.Activity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Choose the Poweramp widget for the car, once, on the phone: pick it in the list, allow AnyAuto
 * to hold it (Android asks the first time), and finish the widget's own setup screen if it has
 * one. The widget id is saved in {@link Prefs}; {@link WidgetActivity} and
 * {@link DashboardActivity} show it in the car.
 */
public final class WidgetSetupActivity extends Activity {
    private static final int REQ_BIND = 1, REQ_CONFIG = 2;

    private AppWidgetHost host;
    private List<AppWidgetProviderInfo> providers = new ArrayList<>();
    // the widget being set up: allocated, not yet saved
    private int pendingId = -1;
    private String pendingLabel;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setTitle("Poweramp widget");
        ActionBar ab = getActionBar();
        if (ab != null) ab.setDisplayHomeAsUpEnabled(true);
        host = new AppWidgetHost(this, Widgets.HOST_ID);
        providers = Widgets.providers(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        TextView hint = Ui.text(this, providers.isEmpty()
                ? "No Poweramp widget found.\nInstall Poweramp (and its widgets), then open this screen again."
                : "Choose the Poweramp widget. It shows in the dashboard and as a tile in the car's apps.",
                14, false, Ui.text2(this));
        hint.setPadding(dp(16), dp(16), dp(16), dp(8));
        root.addView(hint);

        ListView list = new ListView(this);
        list.setAdapter(new Adapter());
        list.setDivider(null);
        list.setOnItemClickListener((parent, view, pos, id) -> choose(providers.get(pos)));
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        root.setOnApplyWindowInsetsListener((v, insets) -> {
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
        setContentView(root);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void choose(AppWidgetProviderInfo info) {
        dropPending();
        pendingId = host.allocateAppWidgetId();
        pendingLabel = String.valueOf(info.loadLabel(getPackageManager()));
        if (AppWidgetManager.getInstance(this).bindAppWidgetIdIfAllowed(pendingId, info.provider)) {
            configure();
        } else {
            // not allowed yet: Android asks the person once
            Intent ask = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider);
            startActivityForResult(ask, REQ_BIND);
        }
    }

    /** The widget's own setup screen, when it has one; otherwise it is done. */
    private void configure() {
        AppWidgetProviderInfo info = AppWidgetManager.getInstance(this).getAppWidgetInfo(pendingId);
        if (info != null && info.configure != null) {
            host.startAppWidgetConfigureActivityForResult(this, pendingId, 0, REQ_CONFIG, null);
        } else {
            done();
        }
    }

    private void done() {
        int old = Prefs.widgetId(this, Widgets.MUSIC);
        if (old != -1 && old != pendingId) host.deleteAppWidgetId(old);
        Prefs.setWidget(this, Widgets.MUSIC, pendingId, pendingLabel);
        pendingId = -1;
        Toast.makeText(this, "Widget saved", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void dropPending() {
        if (pendingId != -1) {
            host.deleteAppWidgetId(pendingId);
            pendingId = -1;
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        if (request == REQ_BIND) {
            if (result == RESULT_OK) configure();
            else dropPending();
        } else if (request == REQ_CONFIG) {
            if (result == RESULT_OK) done();
            else dropPending();
        }
    }

    private final class Adapter extends BaseAdapter {
        @Override
        public int getCount() {
            return providers.size();
        }

        @Override
        public AppWidgetProviderInfo getItem(int i) {
            return providers.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View v, ViewGroup parent) {
            Row r;
            if (v == null) {
                r = new Row();
                LinearLayout row = new LinearLayout(WidgetSetupActivity.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(16), dp(8), dp(16), dp(8));
                r.preview = new ImageView(WidgetSetupActivity.this);
                r.preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
                row.addView(r.preview, new LinearLayout.LayoutParams(dp(96), dp(56)));
                r.label = Ui.text(WidgetSetupActivity.this, "", 16, false, Ui.text(WidgetSetupActivity.this));
                r.label.setPadding(dp(16), 0, dp(8), 0);
                row.addView(r.label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                row.setTag(r);
                v = row;
            } else {
                r = (Row) v.getTag();
            }
            AppWidgetProviderInfo info = providers.get(i);
            Drawable d = info.loadPreviewImage(WidgetSetupActivity.this, 0);
            if (d == null) d = info.loadIcon(WidgetSetupActivity.this, 0);
            r.preview.setImageDrawable(d);
            r.label.setText(info.loadLabel(getPackageManager()));
            return v;
        }
    }

    private static final class Row {
        ImageView preview;
        TextView label;
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
