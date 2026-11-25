package com.mag.anyauto;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The weather card's data, from Open-Meteo (free, no account, no key). The only thing sent is the
 * phone's position rounded to two decimals (about a kilometre). The last answer is kept, so the
 * card has something to show at once while a new one loads.
 */
final class Weather {
    static final int CLEAR = 0, PARTLY = 1, CLOUDY = 2, FOG = 3, RAIN = 4, SNOW = 5, STORM = 6;
    private static final String PREFS = "anyauto", JSON = "weather_json", TIME = "weather_time";
    /** A cached answer younger than this is not fetched again. */
    static final long FRESH_MS = 20 * 60 * 1000L;

    private Weather() {
    }

    static final class Hour {
        final int hour, kind, temp;

        Hour(int hour, int kind, int temp) {
            this.hour = hour;
            this.kind = kind;
            this.temp = temp;
        }
    }

    static final class Data {
        int temp, code, kind, high, low, sunsetHour, sunsetMinute;
        final List<Hour> hours = new ArrayList<>();
        long fetched;
    }

    /** French when the phone is, English otherwise. */
    static String tr(String fr, String en) {
        return "fr".equals(Locale.getDefault().getLanguage()) ? fr : en;
    }

    static String fetch(double lat, double lon) throws IOException {
        URL url = new URL("https://api.open-meteo.com/v1/forecast"
                + "?latitude=" + String.format(Locale.US, "%.2f", lat)
                + "&longitude=" + String.format(Locale.US, "%.2f", lon)
                + "&current=temperature_2m,weather_code"
                + "&hourly=temperature_2m,weather_code"
                + "&daily=temperature_2m_max,temperature_2m_min,sunset"
                + "&timezone=auto&forecast_days=2");
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        try {
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
                char[] buf = new char[4096];
                int n;
                while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
            }
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }

    static Data parse(String json) throws JSONException {
        JSONObject o = new JSONObject(json);
        JSONObject cur = o.getJSONObject("current");
        Data d = new Data();
        d.temp = (int) Math.round(cur.getDouble("temperature_2m"));
        d.code = cur.getInt("weather_code");
        d.kind = kind(d.code);

        // the next three whole hours; times are local, "2026-11-25T11:00"
        String nowHour = cur.getString("time").substring(0, 13);
        JSONObject h = o.getJSONObject("hourly");
        JSONArray times = h.getJSONArray("time"), temps = h.getJSONArray("temperature_2m"),
                codes = h.getJSONArray("weather_code");
        int start = -1;
        for (int i = 0; i < times.length(); i++) {
            if (times.getString(i).substring(0, 13).compareTo(nowHour) > 0) {
                start = i;
                break;
            }
        }
        if (start >= 0) {
            for (int i = start; i < Math.min(start + 3, times.length()); i++) {
                d.hours.add(new Hour(Integer.parseInt(times.getString(i).substring(11, 13)),
                        kind(codes.getInt(i)), (int) Math.round(temps.getDouble(i))));
            }
        }

        JSONObject day = o.getJSONObject("daily");
        d.high = (int) Math.round(day.getJSONArray("temperature_2m_max").getDouble(0));
        d.low = (int) Math.round(day.getJSONArray("temperature_2m_min").getDouble(0));
        String sun = day.getJSONArray("sunset").getString(0);
        d.sunsetHour = Integer.parseInt(sun.substring(11, 13));
        d.sunsetMinute = Integer.parseInt(sun.substring(14, 16));
        return d;
    }

    /** WMO weather code to one of the icon kinds. */
    static int kind(int code) {
        if (code == 0) return CLEAR;
        if (code <= 2) return PARTLY;
        if (code == 3) return CLOUDY;
        if (code == 45 || code == 48) return FOG;
        if (code >= 51 && code <= 67) return RAIN;
        if (code >= 71 && code <= 77) return SNOW;
        if (code >= 80 && code <= 82) return RAIN;
        if (code == 85 || code == 86) return SNOW;
        if (code >= 95) return STORM;
        return CLOUDY;
    }

    static String describe(int code) {
        if (code == 0) return tr("Ciel dégagé", "Clear");
        if (code == 1) return tr("Peu nuageux", "Mostly clear");
        if (code == 2) return tr("Partiellement nuageux", "Partly cloudy");
        if (code == 3) return tr("Nuageux", "Cloudy");
        if (code == 45 || code == 48) return tr("Brouillard", "Fog");
        if (code >= 51 && code <= 57) return tr("Bruine", "Drizzle");
        if (code == 66 || code == 67) return tr("Pluie verglaçante", "Freezing rain");
        if (code >= 61 && code <= 65) return tr("Pluie", "Rain");
        if (code >= 71 && code <= 77) return tr("Neige", "Snow");
        if (code >= 80 && code <= 82) return tr("Averses", "Showers");
        if (code == 85 || code == 86) return tr("Averses de neige", "Snow showers");
        if (code >= 95) return tr("Orage", "Thunderstorm");
        return tr("Nuageux", "Cloudy");
    }

    static Data cached(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String json = p.getString(JSON, null);
        if (json == null) return null;
        try {
            Data d = parse(json);
            d.fetched = p.getLong(TIME, 0);
            return d;
        } catch (JSONException | RuntimeException e) {
            return null;
        }
    }

    static void save(Context c, String json) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(JSON, json).putLong(TIME, System.currentTimeMillis()).apply();
    }
}
