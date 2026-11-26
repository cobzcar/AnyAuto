package com.mag.anyauto;

import android.app.Activity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The dashboard on the car display, laid out like Android Auto's own: the live map on the left,
 * the clock over the Poweramp widget in the middle, the weather over the speed on the right. It
 * is an ordinary activity: DisplayService starts it on the virtual display, the display draws it
 * into the car, and the car's taps reach it as touches.
 * <p>
 * The map is not a picture of an app: the service makes a second virtual display ("map display",
 * {@link IDisplayService#showMap}) that draws into the {@link SurfaceView} of the left card, and
 * the map app runs on it. The system composes that surface into this window, so the car sees it
 * with no further work; touches on the surface are passed on to the map display, which makes the
 * map pan and zoom. The speed comes from the phone's GPS, the weather from Open-Meteo for the
 * phone's position. Everything is chosen on the phone, in {@link DashboardSetupActivity}.
 * Exported because the shell user starts it (a non-exported activity would be refused).
 */
public final class DashboardActivity extends Activity {
    private static final int TEXT2 = Color.rgb(170, 176, 188);
    private static final long WEATHER_EVERY_MS = 30 * 60 * 1000L;

    private AppWidgetHost host;
    private FrameLayout musicBox, mapBox;
    private AppWidgetHostView musicView;
    private SurfaceView map;
    private TextView mapNote;
    private TextView timeText, ampmText, dayText, dateText;
    private WeatherCard weatherCard;
    private DashViews.SpeedGauge gauge;
    private TextView tripText;
    // the distance driven since the car session started (Prefs.resetTrip), counted while the
    // dashboard is on the car screen; saved now and then so it outlives the activity
    private float tripMeters;
    private Location prevFix;
    private long lastTripSave;

    private final Handler ui = new Handler(Looper.getMainLooper());
    // calls to the display service: slow and blocking, so off the main thread, in order; touches
    // have a queue of their own so a slow launch never holds a finger back
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService touchIo = Executors.newSingleThreadExecutor();
    private final ExecutorService weatherIo = Executors.newSingleThreadExecutor();
    private boolean weatherLoading, weatherShown;
    private boolean locating;

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location l) {
            float kmh = l.hasSpeed() ? l.getSpeed() * 3.6f : 0f;
            gauge.setSpeed(kmh < 1f ? 0f : kmh);
            // the distance: only fixes that are precise and moving, and no jump from a lost signal
            if (prevFix != null && l.hasAccuracy() && l.getAccuracy() <= 40f && kmh >= 3f) {
                float d = prevFix.distanceTo(l);
                if (d > 1f && d < 300f) tripMeters += d;
            }
            prevFix = l;
            tripText.setText(tripLabel());
            long now = System.currentTimeMillis();
            if (now - lastTripSave > 10_000L) {
                lastTripSave = now;
                Prefs.setTripMeters(DashboardActivity.this, tripMeters);
            }
            // the first fix, when there was no position for the weather yet
            if (!weatherShown) loadWeather();
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
            gauge.setSpeed(-1f);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }
    };

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            updateClockText();
            // the next minute, so the time never lags
            ui.postDelayed(this, 60_000L - System.currentTimeMillis() % 60_000L + 50L);
        }
    };

    private final Runnable weatherTick = new Runnable() {
        @Override
        public void run() {
            loadWeather();
            ui.postDelayed(this, WEATHER_EVERY_MS);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        host = new AppWidgetHost(this, Widgets.HOST_ID);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundColor(Color.BLACK);
        row.setPadding(dp(10), dp(10), dp(10), dp(10));

        // left: the live map, the whole column
        mapBox = card(0xFF111418);
        buildMap();
        LinearLayout left = column();
        left.addView(mapBox, cell());
        row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 56f));

        // middle: the clock over the Poweramp widget
        musicBox = card(0xFF26222E);
        LinearLayout mid = column();
        mid.addView(clockCard(), cell());
        mid.addView(musicBox, cell());
        row.addView(mid, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 27.3f));

        // right: the weather over the speed
        weatherCard = new WeatherCard(this, radius());
        LinearLayout right = column();
        right.addView(weatherCard, cell());
        right.addView(speedCard(), cell());
        row.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 14.7f));

        setContentView(row);

        // the widget gets the room it has here, so it picks the layout that fits
        musicBox.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> size(musicBox, musicView));
    }

    @Override
    protected void onStart() {
        super.onStart();
        host.startListening(); // without it the widget never updates
        musicView = placeWidget(musicBox); // also when coming back: what was changed on the phone shows
        ui.post(clockTick);
        tripMeters = Prefs.tripMeters(this);
        prevFix = null; // nothing was counted while the dashboard was away
        tripText.setText(tripLabel());
        startLocation();
        ui.post(weatherTick);
    }

    @Override
    protected void onStop() {
        host.stopListening();
        ui.removeCallbacks(clockTick);
        ui.removeCallbacks(weatherTick);
        stopLocation();
        Prefs.setTripMeters(this, tripMeters);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        io.shutdown();
        touchIo.shutdown();
        weatherIo.shutdown();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ the live map

    private void buildMap() {
        map = new SurfaceView(this);
        map.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius());
            }
        });
        map.setClipToOutline(true);
        map.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
                startMap(holder.getSurface(), w, h);
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                stopMap();
            }
        });
        // the map display has the size of this surface, so a touch here is a touch there
        map.setOnTouchListener((v, e) -> {
            forwardTouch(e);
            return true;
        });
        mapBox.addView(map, matchParent());

        // above the surface (a view under it would be cut out), shown only when the map can't be
        mapNote = Ui.text(this, "", 14, false, TEXT2);
        mapNote.setGravity(Gravity.CENTER);
        mapNote.setPadding(dp(16), dp(16), dp(16), dp(16));
        mapNote.setVisibility(View.GONE);
        mapBox.addView(mapNote, matchParent());
    }

    /** Gives the map display this surface and starts the map app on it (an app already there just comes to the front). */
    private void startMap(Surface surface, int w, int h) {
        ComponentName app = Dash.mapApp(this);
        if (app == null) {
            note("No navigation app found.\nInstall Google Maps, or choose one:\n⋮ → Dashboard");
            return;
        }
        int dpi = getResources().getDisplayMetrics().densityDpi;
        io.execute(() -> {
            IDisplayService s = Shell.service();
            if (s == null) {
                note("The map needs the display service.\nOpen AnyAuto on the phone.");
                return;
            }
            try {
                if (s.showMap(surface, w, h, dpi) < 0) {
                    note("Can't show the map.\n" + s.lastError());
                    return;
                }
                String r = s.launchOnMap(app);
                note("ok".equals(r) ? null : "Can't open the map app.\n" + r);
            } catch (RemoteException e) {
                note("Can't reach the display service.");
            }
        });
    }

    /** The dashboard leaves the screen: the map stops drawing, the app on it keeps running (a route keeps guiding). */
    private void stopMap() {
        io.execute(() -> {
            IDisplayService s = Shell.service();
            if (s == null) return;
            try {
                s.showMap(null, 0, 0, 0);
            } catch (RemoteException ignore) {
                // the service is gone: nothing is drawing any more
            }
        });
    }

    private void forwardTouch(MotionEvent e) {
        MotionEvent copy = MotionEvent.obtain(e);
        copy.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        touchIo.execute(() -> {
            try {
                IDisplayService s = Shell.service();
                if (s != null) s.touchMap(copy);
            } catch (RemoteException ignore) {
                // the service is gone: the touch is lost
            } finally {
                copy.recycle();
            }
        });
    }

    /** A message over the map (null hides it). */
    private void note(String text) {
        runOnUiThread(() -> {
            mapNote.setText(text);
            mapNote.setVisibility(text == null ? View.GONE : View.VISIBLE);
        });
    }

    // ------------------------------------------------------------------ the Poweramp widget

    private AppWidgetHostView placeWidget(FrameLayout box) {
        box.removeAllViews();
        int id = Prefs.widgetId(this, Widgets.MUSIC);
        AppWidgetProviderInfo info = id == -1 ? null : AppWidgetManager.getInstance(this).getAppWidgetInfo(id);
        if (info == null) {
            box.addView(hintView("Poweramp widget"), matchParent());
            return null;
        }
        try {
            AppWidgetHostView v = host.createView(this, id, info);
            box.addView(v, matchParent());
            box.post(() -> size(box, v));
            return v;
        } catch (RuntimeException e) {
            box.addView(hintView("Can't show the Poweramp widget"), matchParent());
            return null;
        }
    }

    private void size(FrameLayout box, AppWidgetHostView v) {
        if (v == null || box.getWidth() <= 0 || box.getHeight() <= 0) return;
        float d = getResources().getDisplayMetrics().density;
        int w = Math.round(box.getWidth() / d), h = Math.round(box.getHeight() / d);
        v.updateAppWidgetSize(null, w, h, w, h);
    }

    // ------------------------------------------------------------------ the clock

    /** The violet clock card: the time and the date on the left, the analog face on the right. */
    private FrameLayout clockCard() {
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Dash.CLOCK_FROM, Dash.CLOCK_TO});
        bg.setCornerRadius(radius());
        FrameLayout box = new FrameLayout(this);
        box.setBackground(bg);
        box.setClipToOutline(true);

        DashViews.AnalogClock face = new DashViews.AnalogClock(this);
        box.addView(face, new FrameLayout.LayoutParams(dp(120), dp(120), Gravity.END | Gravity.CENTER_VERTICAL));
        // a big face: nearly the card's width, a little taller than the card, and pushed past its
        // right edge so the card cuts it, as on Android Auto's own clock
        box.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> {
            int s = Math.round(Math.min((r - l) * 0.95f, (bt - t) * 1.12f));
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) face.getLayoutParams();
            if (s > 0 && lp.width != s) {
                lp.width = s;
                lp.height = s;
                lp.rightMargin = -Math.round(s * 0.14f);
                face.setLayoutParams(lp);
            }
        });

        LinearLayout time = new LinearLayout(this);
        time.setGravity(Gravity.BOTTOM);
        timeText = Ui.text(this, "", 40, true, Color.WHITE);
        timeText.setIncludeFontPadding(false);
        ampmText = Ui.text(this, "", 13, false, 0xCCFFFFFF);
        ampmText.setPadding(dp(4), 0, 0, dp(4));
        time.addView(timeText);
        time.addView(ampmText);
        box.addView(time, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START));
        time.setPadding(dp(18), dp(14), 0, 0);

        LinearLayout date = new LinearLayout(this);
        date.setOrientation(LinearLayout.VERTICAL);
        dayText = Ui.text(this, "", 14, true, Color.WHITE);
        dateText = Ui.text(this, "", 12, false, 0xCCFFFFFF);
        date.addView(dayText);
        date.addView(dateText);
        date.setPadding(dp(18), 0, 0, dp(14));
        box.addView(date, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START));
        updateClockText();
        return box;
    }

    private void updateClockText() {
        Date now = new Date();
        boolean h24 = DateFormat.is24HourFormat(this);
        Locale l = Locale.getDefault();
        timeText.setText(new SimpleDateFormat(h24 ? "HH:mm" : "h:mm", l).format(now));
        ampmText.setText(h24 ? "" : new SimpleDateFormat("a", l).format(now));
        dayText.setText(capital(new SimpleDateFormat("EEEE", l).format(now)));
        dateText.setText(capital(new SimpleDateFormat("d MMMM", l).format(now)));
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ------------------------------------------------------------------ the speed

    /** The speed ring, and under it the distance driven since the car started. */
    private FrameLayout speedCard() {
        FrameLayout box = card(0xFF1B1D22);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(10), dp(10), dp(10), dp(12));
        gauge = new DashViews.SpeedGauge(this);
        col.addView(gauge, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        tripText = Ui.text(this, "", 13, false, 0xCCFFFFFF);
        tripText.setGravity(Gravity.CENTER);
        tripText.setSingleLine(true);
        col.addView(tripText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(col, matchParent());
        tripText.setText(tripLabel());
        return box;
    }

    private String tripLabel() {
        return String.format(Locale.getDefault(), "%.1f km", tripMeters / 1000f);
    }

    private void startLocation() {
        gauge.setSpeed(-1f);
        if (!Dash.preciseAllowed(this)) return; // the card shows "--"; the phone's setup asks for it
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm == null) return;
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener,
                    Looper.getMainLooper());
            locating = true;
        } catch (SecurityException | IllegalArgumentException e) {
            locating = false;
        }
    }

    private void stopLocation() {
        if (!locating) return;
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm != null) lm.removeUpdates(locationListener);
        locating = false;
    }

    // ------------------------------------------------------------------ the weather

    /** Shows the last answer at once, and fetches a new one when it is old and a position is known. */
    private void loadWeather() {
        Weather.Data cached = Weather.cached(this);
        if (cached != null) {
            weatherShown = true;
            weatherCard.show(cached);
            if (System.currentTimeMillis() - cached.fetched < Weather.FRESH_MS) return;
        }
        if (!Dash.locationAllowed(this)) {
            if (cached == null) {
                weatherCard.message(Weather.tr("Autorise la localisation sur le téléphone",
                        "Allow location on the phone"));
            }
            return;
        }
        Location l = Dash.lastLocation(this);
        if (l == null) {
            if (cached == null) weatherCard.message(Weather.tr("En attente du GPS…", "Waiting for the GPS…"));
            return;
        }
        if (weatherLoading) return;
        weatherLoading = true;
        double lat = l.getLatitude(), lon = l.getLongitude();
        weatherIo.execute(() -> {
            try {
                String json = Weather.fetch(lat, lon);
                Weather.Data d = Weather.parse(json);
                Weather.save(this, json);
                runOnUiThread(() -> {
                    weatherLoading = false;
                    weatherShown = true;
                    weatherCard.show(d);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    weatherLoading = false;
                    if (!weatherShown) weatherCard.message(Weather.tr("Météo indisponible", "Weather unavailable"));
                });
            }
        });
    }

    // ------------------------------------------------------------------ layout helpers

    private int radius() {
        return dp(26);
    }

    /** A rounded card that clips what it holds (a widget's corners included). */
    private FrameLayout card(int color) {
        FrameLayout f = new FrameLayout(this);
        f.setBackground(Ui.round(color, radius()));
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
        lp.setMargins(dp(5), dp(5), dp(5), dp(5));
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
