package com.mag.anyauto;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.util.Calendar;

/** The drawn parts of the dashboard cards: the analog clock, the speed ring and the weather icons. */
final class DashViews {
    private DashViews() {
    }

    /**
     * The clock card's face: a soft-lobed disc a little lighter than the card, twelve dots (a blue
     * one marks the seconds) and two thick white hands. Made to be big, and cut by the card's edge.
     */
    static final class AnalogClock extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path body = new Path();
        private final Path lobe = new Path();

        AnalogClock(Context c) {
            super(c);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            float r = Math.min(w, h) / 2f, cx = w / 2f, cy = h / 2f;
            float d = r * 0.80f, lr = r * 0.20f;
            // one shape, so the see-through fill has no darker overlaps
            body.reset();
            body.addCircle(cx, cy, d, Path.Direction.CW);
            for (int i = 0; i < 12; i++) {
                double a = Math.toRadians(i * 30);
                lobe.reset();
                lobe.addCircle(cx + (float) Math.sin(a) * d, cy - (float) Math.cos(a) * d, lr, Path.Direction.CW);
                body.op(lobe, Path.Op.UNION);
            }
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight();
            float r = Math.min(w, h) / 2f, cx = w / 2f, cy = h / 2f;

            p.setStyle(Paint.Style.FILL);
            p.setColor(0x26FFFFFF);
            cv.drawPath(body, p);

            Calendar t = Calendar.getInstance();
            float sec = t.get(Calendar.SECOND);
            float min = t.get(Calendar.MINUTE) + sec / 60f;
            float hr = t.get(Calendar.HOUR) + min / 60f; // Calendar.HOUR is 0 to 11

            float ring = r * 0.70f;
            p.setColor(0x99FFFFFF);
            for (int i = 0; i < 12; i++) {
                double a = Math.toRadians(i * 30);
                cv.drawCircle(cx + (float) Math.sin(a) * ring, cy - (float) Math.cos(a) * ring, r * 0.035f, p);
            }
            p.setColor(0xFF4DA3FF);
            double sa = Math.toRadians(sec * 6);
            cv.drawCircle(cx + (float) Math.sin(sa) * ring, cy - (float) Math.cos(sa) * ring, r * 0.065f, p);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(r * 0.12f);
            p.setColor(0xFFFFFFFF);
            hand(cv, cx, cy, hr * 30f, r * 0.10f, r * 0.38f);
            hand(cv, cx, cy, min * 6f, r * 0.10f, r * 0.60f);

            postInvalidateDelayed(1000);
        }

        private void hand(Canvas cv, float cx, float cy, float deg, float from, float to) {
            double a = Math.toRadians(deg);
            float s = (float) Math.sin(a), c = (float) Math.cos(a);
            cv.drawLine(cx + s * from, cy - c * from, cx + s * to, cy - c * to, p);
        }
    }

    /** The speed card: a 270 degree ring (in the accent colour) that fills with the speed, the number and "km/h" inside. */
    static final class SpeedGauge extends View {
        private static final float MAX = 180f; // km/h at a full ring
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private float kmh = -1; // below 0: no speed known

        SpeedGauge(Context c) {
            super(c);
        }

        void setSpeed(float v) {
            int old = kmh < 0 ? -1 : Math.round(kmh);
            kmh = v;
            if (old != (v < 0 ? -1 : Math.round(v))) invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            float s = Math.min(getWidth(), getHeight());
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float sw = s * 0.075f, rad = s / 2f - sw;
            rect.set(cx - rad, cy - rad, cx + rad, cy + rad);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(sw);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(0x26FFFFFF);
            cv.drawArc(rect, 135, 270, false, p);
            if (kmh > 0) {
                p.setColor(Dash.ACCENT);
                cv.drawArc(rect, 135, 270f * Math.min(1f, kmh / MAX), false, p);
            }

            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(0xFFFFFFFF);
            p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            p.setTextSize(s * 0.30f);
            cv.drawText(kmh < 0 ? "--" : String.valueOf(Math.round(kmh)), cx, cy + s * 0.05f, p);
            p.setColor(0xB3FFFFFF);
            p.setTypeface(Typeface.DEFAULT);
            p.setTextSize(s * 0.11f);
            cv.drawText("km/h", cx, cy + s * 0.22f, p);
        }
    }

    /** A flat white weather icon: sun, clouds, rain, snow, storm or fog. */
    static final class WeatherIcon extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rect = new RectF();
        private int kind = Weather.CLOUDY;

        WeatherIcon(Context c) {
            super(c);
        }

        void setKind(int k) {
            if (k != kind) {
                kind = k;
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas cv) {
            float u = Math.min(getWidth(), getHeight());
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            switch (kind) {
                case Weather.CLEAR:
                    sun(cv, cx, cy, u * 0.19f, u);
                    break;
                case Weather.PARTLY:
                    sun(cv, cx + u * 0.14f, cy - u * 0.14f, u * 0.14f, u * 0.8f);
                    cloud(cv, cx - u * 0.04f, cy + u * 0.08f, u);
                    break;
                case Weather.FOG:
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeCap(Paint.Cap.ROUND);
                    p.setStrokeWidth(u * 0.06f);
                    p.setColor(0xCCFFFFFF);
                    for (int i = -1; i <= 1; i++) {
                        cv.drawLine(cx - u * 0.30f, cy + i * u * 0.17f, cx + u * 0.30f, cy + i * u * 0.17f, p);
                    }
                    break;
                case Weather.RAIN:
                    cloud(cv, cx, cy - u * 0.12f, u);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeCap(Paint.Cap.ROUND);
                    p.setStrokeWidth(u * 0.05f);
                    p.setColor(0xFF8CCBFF);
                    for (int i = -1; i <= 1; i++) {
                        float x = cx + i * u * 0.2f;
                        cv.drawLine(x + u * 0.03f, cy + u * 0.22f, x - u * 0.03f, cy + u * 0.36f, p);
                    }
                    break;
                case Weather.SNOW:
                    cloud(cv, cx, cy - u * 0.12f, u);
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(0xFFFFFFFF);
                    for (int i = -1; i <= 1; i++) cv.drawCircle(cx + i * u * 0.2f, cy + u * 0.32f, u * 0.04f, p);
                    break;
                case Weather.STORM:
                    cloud(cv, cx, cy - u * 0.12f, u);
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(0xFFFFD54F);
                    path.reset();
                    path.moveTo(cx + u * 0.04f, cy + u * 0.12f);
                    path.lineTo(cx - u * 0.07f, cy + u * 0.32f);
                    path.lineTo(cx, cy + u * 0.32f);
                    path.lineTo(cx - u * 0.05f, cy + u * 0.48f);
                    path.lineTo(cx + u * 0.10f, cy + u * 0.25f);
                    path.lineTo(cx + u * 0.02f, cy + u * 0.25f);
                    path.close();
                    cv.drawPath(path, p);
                    break;
                default:
                    cloud(cv, cx, cy, u);
            }
        }

        private void sun(Canvas cv, float cx, float cy, float r, float u) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFD54F);
            cv.drawCircle(cx, cy, r, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(u * 0.05f);
            for (int i = 0; i < 8; i++) {
                double a = Math.toRadians(i * 45);
                float s = (float) Math.sin(a), c = (float) Math.cos(a);
                cv.drawLine(cx + s * r * 1.45f, cy - c * r * 1.45f, cx + s * r * 1.95f, cy - c * r * 1.95f, p);
            }
        }

        private void cloud(Canvas cv, float cx, float cy, float u) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFFFFF);
            rect.set(cx - u * 0.40f, cy - u * 0.02f, cx + u * 0.40f, cy + u * 0.26f);
            cv.drawRoundRect(rect, u * 0.13f, u * 0.13f, p);
            cv.drawCircle(cx - u * 0.14f, cy - u * 0.04f, u * 0.20f, p);
            cv.drawCircle(cx + u * 0.12f, cy - u * 0.09f, u * 0.25f, p);
        }
    }
}
