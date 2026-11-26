package com.mag.anyauto;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * The dashboard's music card, drawn by us: the cover fills the card, the title and artist sit at
 * the bottom with a wavy progress line and three glass buttons. It follows the phone's active
 * media session (Poweramp first), so it needs Notification access ({@link MediaListener}). When
 * it has nothing to show it hides itself and the Poweramp widget under it shows instead.
 * <p>
 * The buttons can be your own pictures: put transparent PNGs named btn_prev, btn_play,
 * btn_pause and btn_next in res/drawable (any of them; the others stay drawn).
 */
final class MusicCard extends FrameLayout {
    private static final String POWERAMP = "com.maxmpz.audioplayer";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ImageView cover;
    private final TextView title, artist;
    private final Wave wave;
    private final Btn prev, play, next;
    private MediaController ctl;
    private MediaSessionManager msm;
    private ComponentName listener;
    private boolean started;
    private Bitmap shownArt;

    private final MediaSessionManager.OnActiveSessionsChangedListener sessions = list -> pick(list);

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState s) {
            refresh();
        }

        @Override
        public void onMetadataChanged(MediaMetadata m) {
            refresh();
        }

        @Override
        public void onSessionDestroyed() {
            reload();
        }
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            wave.invalidate();
            if (started) ui.postDelayed(this, 50L);
        }
    };

    MusicCard(Context c) {
        super(c);
        setBackgroundColor(0xFF26222E);
        setVisibility(GONE);

        cover = new ImageView(c);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(cover, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // a dark fade from the bottom keeps the text readable on any cover
        View scrim = new View(c);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xE6000000, 0x99000000, 0x00000000}));
        addView(scrim, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), dp(8), dp(14), dp(12));
        title = Ui.text(c, "", 17, true, Color.WHITE);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        artist = Ui.text(c, "", 13, false, 0xCCFFFFFF);
        artist.setSingleLine(true);
        artist.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(title);
        col.addView(artist);

        wave = new Wave(c);
        col.addView(wave, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28)));

        LinearLayout buttons = new LinearLayout(c);
        buttons.setGravity(Gravity.CENTER);
        prev = new Btn(c, Btn.PREV, "btn_prev");
        play = new Btn(c, Btn.PLAY, "btn_play");
        next = new Btn(c, Btn.NEXT, "btn_next");
        for (Btn b : new Btn[]{prev, play, next}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f);
            lp.setMargins(dp(3), 0, dp(3), 0);
            buttons.addView(b, lp);
        }
        col.addView(buttons, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(col, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM));

        prev.setOnClickListener(v -> {
            if (ctl != null) ctl.getTransportControls().skipToPrevious();
        });
        next.setOnClickListener(v -> {
            if (ctl != null) ctl.getTransportControls().skipToNext();
        });
        play.setOnClickListener(v -> {
            if (ctl == null) return;
            PlaybackState s = ctl.getPlaybackState();
            if (s != null && s.getState() == PlaybackState.STATE_PLAYING) ctl.getTransportControls().pause();
            else ctl.getTransportControls().play();
        });
        wave.onSeek = f -> {
            if (ctl == null || ctl.getMetadata() == null) return;
            long d = ctl.getMetadata().getLong(MediaMetadata.METADATA_KEY_DURATION);
            if (d > 0) ctl.getTransportControls().seekTo((long) (d * f));
        };
    }

    /** True when Android lets this app read the media sessions (Notification access is on). */
    static boolean allowed(Context c) {
        String s = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains(c.getPackageName() + "/");
    }

    void start() {
        if (started) return;
        started = true;
        reload();
        ui.post(tick);
    }

    void stop() {
        started = false;
        ui.removeCallbacks(tick);
        unregister();
        setVisibility(GONE);
    }

    private void unregister() {
        if (ctl != null) {
            ctl.unregisterCallback(callback);
            ctl = null;
        }
        if (msm != null) {
            msm.removeOnActiveSessionsChangedListener(sessions);
            msm = null;
        }
    }

    private void reload() {
        unregister();
        if (!allowed(getContext())) {
            setVisibility(GONE);
            return;
        }
        try {
            listener = new ComponentName(getContext(), MediaListener.class);
            msm = (MediaSessionManager) getContext().getSystemService(Context.MEDIA_SESSION_SERVICE);
            msm.addOnActiveSessionsChangedListener(sessions, listener);
            pick(msm.getActiveSessions(listener));
        } catch (SecurityException | NullPointerException e) {
            setVisibility(GONE);
        }
    }

    /** Poweramp if it has a session, else the one playing, else the first. */
    private void pick(List<MediaController> list) {
        MediaController best = null;
        if (list != null) {
            for (MediaController m : list) {
                if (POWERAMP.equals(m.getPackageName())) {
                    best = m;
                    break;
                }
            }
            if (best == null) {
                for (MediaController m : list) {
                    PlaybackState s = m.getPlaybackState();
                    if (s != null && s.getState() == PlaybackState.STATE_PLAYING) {
                        best = m;
                        break;
                    }
                }
            }
            if (best == null && !list.isEmpty()) best = list.get(0);
        }
        if (ctl != null) ctl.unregisterCallback(callback);
        ctl = best;
        if (ctl != null) ctl.registerCallback(callback, ui);
        refresh();
    }

    private void refresh() {
        MediaMetadata m = ctl == null ? null : ctl.getMetadata();
        if (m == null) {
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);
        CharSequence t = m.getText(MediaMetadata.METADATA_KEY_TITLE);
        CharSequence a = m.getText(MediaMetadata.METADATA_KEY_ARTIST);
        if (a == null) a = m.getText(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        title.setText(t == null ? "" : t);
        artist.setText(a == null ? "" : a);
        Bitmap art = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (art == null) art = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (art != shownArt) {
            shownArt = art;
            cover.setImageBitmap(art);
        }
        PlaybackState s = ctl.getPlaybackState();
        play.playing = s != null && s.getState() == PlaybackState.STATE_PLAYING;
        play.refresh();
        wave.playing = play.playing;
        wave.update(position(s), m.getLong(MediaMetadata.METADATA_KEY_DURATION));
    }

    private static long position(PlaybackState s) {
        if (s == null) return 0;
        long p = s.getPosition();
        if (s.getState() == PlaybackState.STATE_PLAYING) {
            p += (long) ((SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime()) * s.getPlaybackSpeed());
        }
        return Math.max(0, p);
    }

    private int dp(float v) {
        return Ui.dp(getContext(), v);
    }

    // ------------------------------------------------------------------ parts

    /** The progress line: a wave behind the played part, a flat faint line after it, a dot at the point. */
    private final class Wave extends View {
        interface Seek {
            void to(float fraction);
        }

        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        Seek onSeek;
        boolean playing;
        private long pos, dur;
        private long at;

        Wave(Context c) {
            super(c);
        }

        void update(long position, long duration) {
            pos = position;
            dur = duration;
            at = SystemClock.elapsedRealtime();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), cy = h / 2f;
            float pad = h * 0.3f;
            long now = playing ? pos + (SystemClock.elapsedRealtime() - at) : pos;
            float f = dur > 0 ? Math.max(0f, Math.min(1f, (float) now / dur)) : 0f;
            float x = pad + (w - 2 * pad) * f;

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(h * 0.12f);
            p.setColor(0x66FFFFFF);
            cv.drawLine(x, cy, w - pad, cy, p);

            float amp = h * 0.22f, len = h * 1.1f;
            float phase = playing ? (SystemClock.uptimeMillis() % 1600) / 1600f * (float) (2 * Math.PI) : 0f;
            path.reset();
            path.moveTo(pad, cy);
            for (float px = pad; px <= x; px += 2f) {
                path.lineTo(px, cy + (float) Math.sin((px - pad) / len * 2 * Math.PI - phase) * amp);
            }
            p.setColor(Color.WHITE);
            cv.drawPath(path, p);

            p.setStyle(Paint.Style.FILL);
            cv.drawCircle(x, cy, h * 0.17f, p);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_UP && onSeek != null && getWidth() > 0) {
                float pad = getHeight() * 0.3f;
                onSeek.to(Math.max(0f, Math.min(1f, (e.getX() - pad) / (getWidth() - 2 * pad))));
            }
            return true;
        }
    }

    /** A round glass button: a faint disc with a white symbol, or the PNG from res/drawable when there is one. */
    private static final class Btn extends View {
        static final int PREV = 0, PLAY = 1, NEXT = 2;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final int kind;
        private final String name;
        private Drawable image, imagePause;
        boolean playing;

        Btn(Context c, int kind, String name) {
            super(c);
            this.kind = kind;
            this.name = name;
            image = load(name);
            if (kind == PLAY) imagePause = load("btn_pause");
        }

        private Drawable load(String n) {
            int id = getResources().getIdentifier(n, "drawable", getContext().getPackageName());
            return id == 0 ? null : getResources().getDrawable(id, null);
        }

        void refresh() {
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight();
            Drawable d = kind == PLAY && playing && imagePause != null ? imagePause : image;
            if (d != null) {
                float s = Math.min(w, h);
                int l = Math.round((w - s) / 2f), t = Math.round((h - s) / 2f);
                d.setBounds(l, t, l + Math.round(s), t + Math.round(s));
                d.draw(cv);
                return;
            }
            float r = Math.min(w, h) / 2f, cx = w / 2f, cy = h / 2f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x40FFFFFF);
            cv.drawRoundRect(cx - w / 2f, cy - r, cx + w / 2f, cy + r, r, r, p);
            p.setColor(Color.WHITE);
            float u = r * 0.42f;
            path.reset();
            if (kind == PLAY && !playing) {
                path.moveTo(cx - u * 0.7f, cy - u);
                path.lineTo(cx + u * 1.1f, cy);
                path.lineTo(cx - u * 0.7f, cy + u);
                path.close();
                cv.drawPath(path, p);
            } else if (kind == PLAY) {
                cv.drawRoundRect(cx - u * 0.8f, cy - u, cx - u * 0.2f, cy + u, u * 0.15f, u * 0.15f, p);
                cv.drawRoundRect(cx + u * 0.2f, cy - u, cx + u * 0.8f, cy + u, u * 0.15f, u * 0.15f, p);
            } else {
                float dir = kind == NEXT ? 1f : -1f;
                for (int i = 0; i < 2; i++) {
                    float o = (i - 0.5f) * u * 1.1f * dir;
                    path.reset();
                    path.moveTo(cx + o - dir * u * 0.55f, cy - u * 0.8f);
                    path.lineTo(cx + o + dir * u * 0.55f, cy);
                    path.lineTo(cx + o - dir * u * 0.55f, cy + u * 0.8f);
                    path.close();
                    cv.drawPath(path, p);
                }
            }
        }
    }
}
