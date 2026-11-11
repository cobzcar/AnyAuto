package com.mag.anyauto;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The widgets shown in the car. AnyAuto hosts them itself: {@link WidgetActivity} (one widget,
 * full size) and {@link DashboardActivity} (weather and Poweramp side by side with other tiles)
 * are ordinary activities that DisplayService starts on the car display like any other app. A
 * widget is chosen and bound once on the phone, in {@link WidgetSetupActivity}.
 */
public final class Widgets {
    /** Poweramp's package, and its separate widget packs (com.maxmpz.audioplayer.widgetpack1, ...). */
    static final String PACKAGE_PREFIX = "com.maxmpz.audioplayer";
    /** The host id AnyAuto's widgets live under: the saved widget ids belong to it. */
    static final int HOST_ID = 0x414E5941;
    /** The two widget slots: Poweramp's (also the full-screen tile) and the dashboard's weather. */
    static final String MUSIC = "music", WEATHER = "weather";

    private Widgets() {
    }

    /** Poweramp's widgets for the music slot; every widget on the phone, by name, for the weather. */
    static List<AppWidgetProviderInfo> providers(Context c, String slot) {
        boolean any = WEATHER.equals(slot);
        List<AppWidgetProviderInfo> out = new ArrayList<>();
        for (AppWidgetProviderInfo i : AppWidgetManager.getInstance(c).getInstalledProviders()) {
            if (any || i.provider.getPackageName().startsWith(PACKAGE_PREFIX)) out.add(i);
        }
        if (any) {
            Collator col = Collator.getInstance();
            Collections.sort(out, (a, b) -> col.compare(name(c, a), name(c, b)));
        }
        return out;
    }

    /** "App · widget", so a list of every widget on the phone reads clearly. */
    static String name(Context c, AppWidgetProviderInfo info) {
        PackageManager pm = c.getPackageManager();
        String pkg = info.provider.getPackageName();
        String app;
        try {
            app = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (PackageManager.NameNotFoundException e) {
            app = pkg;
        }
        return app + " · " + info.loadLabel(pm);
    }

    /** The activity that shows the music widget alone on the car display. */
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
