package com.mag.anyauto;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** The weather card: the weather now, the next three hours, the day's range and the sunset. */
final class WeatherCard extends FrameLayout {
    private final LinearLayout content;
    private final TextView message, temp, condition, range, sunset;
    private final DashViews.WeatherIcon icon;
    private final TextView[] hourLabel = new TextView[3], hourTemp = new TextView[3];
    private final DashViews.WeatherIcon[] hourIcon = new DashViews.WeatherIcon[3];
    private final LinearLayout[] hourBox = new LinearLayout[3];

    WeatherCard(Context c, int radiusPx) {
        super(c);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF4F5F94, 0xFF38426E});
        bg.setCornerRadius(radiusPx);
        setBackground(bg);
        setClipToOutline(true);

        content = new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(12), dp(14), dp(12));

        LinearLayout top = new LinearLayout(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        icon = new DashViews.WeatherIcon(c);
        top.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));
        temp = text(c, 30, true, Color.WHITE);
        temp.setPadding(dp(6), 0, 0, 0);
        top.addView(temp);
        content.addView(top);

        LinearLayout hours = new LinearLayout(c);
        hours.setPadding(0, dp(10), 0, 0);
        for (int i = 0; i < 3; i++) {
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            hourLabel[i] = text(c, 10, false, 0xCCFFFFFF);
            hourIcon[i] = new DashViews.WeatherIcon(c);
            hourTemp[i] = text(c, 11, false, Color.WHITE);
            col.addView(hourLabel[i]);
            col.addView(hourIcon[i], new LinearLayout.LayoutParams(dp(22), dp(22)));
            col.addView(hourTemp[i]);
            hourBox[i] = col;
            hours.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        content.addView(hours);

        content.addView(new android.view.View(c), new LinearLayout.LayoutParams(1, 0, 1f)); // spacer
        condition = text(c, 13, true, Color.WHITE);
        condition.setMaxLines(2);
        condition.setEllipsize(TextUtils.TruncateAt.END);
        content.addView(condition);
        range = text(c, 11, false, 0xCCFFFFFF);
        range.setSingleLine(true);
        content.addView(range);
        sunset = text(c, 10, false, 0xCCFFFFFF);
        sunset.setSingleLine(true);
        sunset.setEllipsize(TextUtils.TruncateAt.END);
        sunset.setPadding(0, dp(2), 0, 0);
        content.addView(sunset);
        addView(content, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        message = text(c, 13, false, 0xDDFFFFFF);
        message.setGravity(Gravity.CENTER);
        message.setPadding(dp(14), dp(14), dp(14), dp(14));
        addView(message, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        message("");
    }

    /** Shows a short message instead of the weather (waiting for the GPS, location not allowed, ...). */
    void message(String text) {
        message.setText(text);
        message.setVisibility(text.isEmpty() ? GONE : VISIBLE);
        content.setVisibility(text.isEmpty() ? VISIBLE : GONE);
    }

    void show(Weather.Data d) {
        message("");
        icon.setKind(d.kind);
        temp.setText(d.temp + "°");
        for (int i = 0; i < 3; i++) {
            boolean has = i < d.hours.size();
            hourBox[i].setVisibility(has ? VISIBLE : INVISIBLE);
            if (!has) continue;
            Weather.Hour h = d.hours.get(i);
            hourLabel[i].setText(hourLabel(h.hour));
            hourIcon[i].setKind(h.kind);
            hourTemp[i].setText(h.temp + "°");
        }
        condition.setText(Weather.describe(d.code));
        range.setText(Weather.tr("Max " + d.high + "° · Min " + d.low + "°", "H " + d.high + "° L " + d.low + "°"));
        sunset.setText(Weather.tr("Coucher ", "Sunset ") + time(d.sunsetHour, d.sunsetMinute));
    }

    private String hourLabel(int hour) {
        if (DateFormat.is24HourFormat(getContext())) return String.format(Locale.getDefault(), "%d h", hour);
        return (hour % 12 == 0 ? 12 : hour % 12) + (hour < 12 ? " AM" : " PM");
    }

    private String time(int hour, int minute) {
        if (DateFormat.is24HourFormat(getContext())) return String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
        return String.format(Locale.getDefault(), "%d:%02d %s", hour % 12 == 0 ? 12 : hour % 12, minute, hour < 12 ? "AM" : "PM");
    }

    private static TextView text(Context c, int sp, boolean medium, int color) {
        return Ui.text(c, "", sp, medium, color);
    }

    private int dp(float v) {
        return Ui.dp(getContext(), v);
    }
}
