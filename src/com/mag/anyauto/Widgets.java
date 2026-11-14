package com.mag.anyauto;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/**
 * The Poweramp widget shown in the car. AnyAuto hosts it itself: {@link WidgetActivity} (the
 * widget alone, full size) and {@link DashboardActivity} (the widget beside the map and the cards)
 * are ordinary activities that DisplayService starts on the car display like any other app. The
 * widget is chosen and bound once on the phone, in {@link WidgetSetupActivity}.
 */
public final class Widgets {
    /** Poweramp's package, and its separate widget packs (com.maxmpz.audioplayer.widgetpack1, ...). */
    static final String PACKAGE_PREFIX = "com.maxmpz.audioplayer";
    /** The host id AnyAuto's widgets live under: the saved widget id belongs to it. */
    static final int HOST_ID = 0x414E5941;
    /** The widget's slot in {@link Prefs}. */
    static final String MUSIC = "music";

    private Widgets() {
    }

    /** Poweramp's widgets on this phone. */
    static List<AppWidgetProviderInfo> providers(Context c) {
        List<AppWidgetProviderInfo> out = new ArrayList<>();
        for (AppWidgetProviderInfo i : AppWidgetManager.getInstance(c).getInstalledProviders()) {
            if (i.provider.getPackageName().startsWith(PACKAGE_PREFIX)) out.add(i);
        }
        return out;
    }

    /** The activity that shows the Poweramp widget alone on the car display. */
    public static ComponentName component(Context c) {
        return new ComponentName(c, WidgetActivity.class);
    }

    /** The activity that shows the dashboard on the car display. */
    public static ComponentName dashboard(Context c) {
        return new ComponentName(c, DashboardActivity.class);
    }

    /** True when {@code cn} is the widget activity and a widget is chosen. */
    public static boolean isWidget(Context c, ComponentName cn) {
        return cn != null && cn.equals(component(c)) && Prefs.widgetId(c) != -1;
    }

    /** True when {@code cn} is the dashboard activity. */
    public static boolean isDashboard(Context c, ComponentName cn) {
        return cn != null && cn.equals(dashboard(c));
    }
}
