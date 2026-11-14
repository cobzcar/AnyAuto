package com.mag.anyauto;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The apps chosen for the car and the one shown last. */
public final class Prefs {
    private static final String CAR_APPS = "car_apps";
    private static final String LAST = "last";
    private static final String KEYBOARD_ON_CAR = "keyboard_on_car";
    private static final String FULL_SCREEN = "full_screen";
    private static final String KEEP_PHONE_ON = "keep_phone_on";
    private static final String YOUTUBE_SHORTCUT = "youtube_shortcut";

    private Prefs() {
    }

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("anyauto", Context.MODE_PRIVATE);
    }

    static Set<String> carApps(Context c) {
        return new HashSet<>(p(c).getStringSet(CAR_APPS, new HashSet<>()));
    }

    static boolean isCarApp(Context c, ComponentName cn) {
        return carApps(c).contains(cn.flattenToString());
    }

    static void setCarApp(Context c, ComponentName cn, boolean on) {
        Set<String> s = carApps(c);
        if (on) s.add(cn.flattenToString());
        else s.remove(cn.flattenToString());
        p(c).edit().putStringSet(CAR_APPS, s).apply();
    }

    static List<ComponentName> carAppComponents(Context c) {
        List<ComponentName> out = new ArrayList<>();
        for (String f : carApps(c)) {
            ComponentName cn = ComponentName.unflattenFromString(f);
            if (cn != null) out.add(cn);
        }
        return out;
    }

    // the last car surface size: the phone preview takes the car's shape
    public static void setCarSize(Context c, int w, int h) {
        if (w > 0 && h > 0) p(c).edit().putInt("car_w", w).putInt("car_h", h).apply();
    }

    /** {width, height} of the last car surface, or null before the first car. */
    static int[] carSize(Context c) {
        int w = p(c).getInt("car_w", 0), h = p(c).getInt("car_h", 0);
        return w > 0 && h > 0 ? new int[]{w, h} : null;
    }

    // the install-for-the-car card was hidden for this version
    static boolean installCardHidden(Context c, long versionCode) {
        return p(c).getLong("install_card_hidden", -1) == versionCode;
    }

    static void hideInstallCard(Context c, long versionCode) {
        p(c).edit().putLong("install_card_hidden", versionCode).apply();
    }

    public static ComponentName last(Context c) {
        String f = p(c).getString(LAST, null);
        return f == null ? null : ComponentName.unflattenFromString(f);
    }

    public static void setLast(Context c, ComponentName cn) {
        p(c).edit().putString(LAST, cn.flattenToString()).apply();
    }

    /** Where the keyboard opens while the car shows an app: on the car screen (default) or the phone. */
    public static boolean keyboardOnCar(Context c) {
        return p(c).getBoolean(KEYBOARD_ON_CAR, true);
    }

    public static void setKeyboardOnCar(Context c, boolean onCar) {
        p(c).edit().putBoolean(KEYBOARD_ON_CAR, onCar).apply();
    }

    /** The app also uses the band Android Auto keeps for Apps and Back (default on). */
    public static boolean fullScreen(Context c) {
        return p(c).getBoolean(FULL_SCREEN, true);
    }

    public static void setFullScreen(Context c, boolean on) {
        p(c).edit().putBoolean(FULL_SCREEN, on).apply();
    }

    /** The phone stays awake with its screen on while the car shows AnyAuto (default off). */
    public static boolean keepPhoneOn(Context c) {
        return p(c).getBoolean(KEEP_PHONE_ON, false);
    }

    public static void setKeepPhoneOn(Context c, boolean on) {
        p(c).edit().putBoolean(KEEP_PHONE_ON, on).apply();
    }

    /** A YouTube tile stays first in the car's apps grid, ticked or not (default on). */
    public static boolean youtubeShortcut(Context c) {
        return p(c).getBoolean(YOUTUBE_SHORTCUT, true);
    }

    public static void setYoutubeShortcut(Context c, boolean on) {
        p(c).edit().putBoolean(YOUTUBE_SHORTCUT, on).apply();
    }

    /** The id of the Poweramp widget chosen for the car (Widgets), or -1 when there is none. */
    public static int widgetId(Context c) {
        return widgetId(c, Widgets.MUSIC);
    }

    /** The Poweramp widget's name, for its tile in the car's apps grid. */
    public static String widgetLabel(Context c) {
        return widgetLabel(c, Widgets.MUSIC);
    }

    /** The id of the widget in a slot (Widgets.MUSIC), or -1 when there is none. */
    static int widgetId(Context c, String slot) {
        return p(c).getInt("widget_" + slot + "_id", -1);
    }

    static String widgetLabel(Context c, String slot) {
        return p(c).getString("widget_" + slot + "_label", "Widget");
    }

    static void setWidget(Context c, String slot, int id, String label) {
        p(c).edit().putInt("widget_" + slot + "_id", id).putString("widget_" + slot + "_label", label).apply();
    }

    /** An app of the dashboard: its navigation tile (Dash.MAP) or one of its cards ("card1", "card2"). */
    static ComponentName dashApp(Context c, String key) {
        String f = p(c).getString("dash_" + key + "_app", null);
        return f == null ? null : ComponentName.unflattenFromString(f);
    }

    /** Sets the app of a dashboard tile; null clears it. */
    static void setDashApp(Context c, String key, ComponentName cn) {
        SharedPreferences.Editor e = p(c).edit();
        if (cn == null) e.remove("dash_" + key + "_app");
        else e.putString("dash_" + key + "_app", cn.flattenToString());
        e.apply();
    }

    /** True once something is set up for the dashboard: its tile then shows in the car's apps. */
    public static boolean hasDashboard(Context c) {
        return widgetId(c, Widgets.MUSIC) != -1
                || dashApp(c, Dash.MAP) != null || dashApp(c, "card1") != null || dashApp(c, "card2") != null
                || Dash.cardImage(c, 1).exists() || Dash.cardImage(c, 2).exists();
    }
}
