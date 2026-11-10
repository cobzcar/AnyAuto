package com.mag.anyauto;

import android.app.Activity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Shows the widget chosen on the phone (Poweramp's) full size on the car display. It is an
 * ordinary activity: DisplayService starts it on the virtual display, the display draws it into
 * the car, and the car's taps reach the widget as touches. Exported because the shell user starts
 * it (a non-exported activity would be refused).
 */
public final class WidgetActivity extends Activity {
    private AppWidgetHost host;
    private FrameLayout root;
    private AppWidgetHostView view;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);
        host = new AppWidgetHost(this, Widgets.HOST_ID);
        // the widget gets the room it has here, so it picks the layout that fits (dp)
        root.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> sizeWidget());
    }

    @Override
    protected void onStart() {
        super.onStart();
        host.startListening(); // without it the widget never updates
        show();
    }

    @Override
    protected void onStop() {
        host.stopListening();
        super.onStop();
    }

    private void show() {
        root.removeAllViews();
        view = null;
        int id = Prefs.widgetId(this);
        AppWidgetProviderInfo info = id == -1 ? null : AppWidgetManager.getInstance(this).getAppWidgetInfo(id);
        if (info == null) {
            message("No widget chosen.\nOpen AnyAuto on the phone: ⋮ → Poweramp widget.");
            return;
        }
        try {
            view = host.createView(this, id, info);
        } catch (RuntimeException e) {
            message("Can't show the widget.\nChoose it again on the phone: ⋮ → Poweramp widget.");
            return;
        }
        root.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        sizeWidget();
    }

    private void sizeWidget() {
        if (view == null || root.getWidth() <= 0 || root.getHeight() <= 0) return;
        float d = getResources().getDisplayMetrics().density;
        int w = Math.round(root.getWidth() / d), h = Math.round(root.getHeight() / d);
        view.updateAppWidgetSize(null, w, h, w, h);
    }

    private void message(String text) {
        TextView t = Ui.text(this, text, 18, false, Color.WHITE);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(this, 32), Ui.dp(this, 32), Ui.dp(this, 32), Ui.dp(this, 32));
        root.addView(t, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
    }
}
