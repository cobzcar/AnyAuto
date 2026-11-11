package com.mag.anyauto;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** What the dashboard needs besides its widgets: the images of its two cards and its map app. */
final class Dash {
    /** The key of the navigation tile's app (the cards are "card1" and "card2"). */
    static final String MAP = "map";
    // the cards are small on the car screen: no point in keeping a photo's full size
    private static final int MAX_PX = 800;

    private Dash() {
    }

    static File cardImage(Context c, int n) {
        return new File(c.getFilesDir(), "dash_card_" + n + ".jpg");
    }

    /** The card's image, or null when none is set. */
    static Bitmap loadCardImage(Context c, int n) {
        File f = cardImage(c, n);
        return f.exists() ? BitmapFactory.decodeFile(f.getPath()) : null;
    }

    static void removeCardImage(Context c, int n) {
        cardImage(c, n).delete();
    }

    /** Copies the picked image into AnyAuto's own storage, scaled down and upright. Slow: not on the main thread. */
    static boolean saveCardImage(Context c, int n, Uri uri) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, o);
            }
            if (o.outWidth <= 0 || o.outHeight <= 0) return false;
            int sample = 1;
            while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= MAX_PX) sample *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            Bitmap bmp;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                bmp = BitmapFactory.decodeStream(in, null, o);
            }
            if (bmp == null) return false;
            bmp = upright(c, uri, bmp);
            File out = cardImage(c, n);
            File tmp = new File(out.getPath() + ".tmp");
            try (FileOutputStream fo = new FileOutputStream(tmp)) {
                bmp.compress(Bitmap.CompressFormat.JPEG, 88, fo);
            }
            return tmp.renameTo(out);
        } catch (Exception e) {
            return false;
        }
    }

    /** Photos carry their rotation in the EXIF data: turn the pixels, or they show sideways. */
    private static Bitmap upright(Context c, Uri uri, Bitmap bmp) {
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            int o = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            int deg = o == ExifInterface.ORIENTATION_ROTATE_90 ? 90
                    : o == ExifInterface.ORIENTATION_ROTATE_180 ? 180
                    : o == ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
            if (deg == 0) return bmp;
            Matrix m = new Matrix();
            m.postRotate(deg);
            return Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
        } catch (Exception e) {
            return bmp;
        }
    }

    /** The navigation tile's app: the one chosen, else Google Maps, else Waze; null when none is installed. */
    static ComponentName mapApp(Context c) {
        ComponentName saved = Prefs.dashApp(c, MAP);
        if (saved != null) return saved;
        for (String pkg : new String[]{"com.google.android.apps.maps", "com.waze"}) {
            Intent i = c.getPackageManager().getLaunchIntentForPackage(pkg);
            if (i != null && i.getComponent() != null) return i.getComponent();
        }
        return null;
    }
}
