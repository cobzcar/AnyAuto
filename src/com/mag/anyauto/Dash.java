package com.mag.anyauto;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;

/** What the dashboard needs besides its widget: the map app and the phone's location. */
final class Dash {
    /** The key of the map app in {@link Prefs#dashApp}. */
    static final String MAP = "map";

    private Dash() {
    }

    /** The map app: the one chosen, else Google Maps, else Waze; null when none is installed. */
    static ComponentName mapApp(Context c) {
        ComponentName saved = Prefs.dashApp(c, MAP);
        if (saved != null) return saved;
        for (String pkg : new String[]{"com.google.android.apps.maps", "com.waze"}) {
            Intent i = c.getPackageManager().getLaunchIntentForPackage(pkg);
            if (i != null && i.getComponent() != null) return i.getComponent();
        }
        return null;
    }

    /** True when the weather and speed cards may use the phone's position (approximate or precise). */
    static boolean locationAllowed(Context c) {
        return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /** True when the GPS speed is allowed (the precise location). */
    static boolean preciseAllowed(Context c) {
        return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /** The newest position Android has, or null (not allowed, or none yet). */
    static Location lastLocation(Context c) {
        if (!locationAllowed(c)) return null;
        LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return null;
        Location best = null;
        try {
            for (String provider : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(provider);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
        } catch (SecurityException ignore) {
            // the permission was taken back meanwhile
        }
        return best;
    }
}
