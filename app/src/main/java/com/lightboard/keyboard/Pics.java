package com.lightboard.keyboard;

import android.content.ClipDescription;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputContentInfo;
import java.io.InputStream;

final class Pics {
    private Pics() { }

    static boolean accepts(EditorInfo e) {
        if (e == null || Build.VERSION.SDK_INT < 25) return false;
        String[] t = null;
        try { t = (String[]) EditorInfo.class.getField("contentMimeTypes").get(e); } catch (Exception | LinkageError ignored) { }
        if (t == null && Build.VERSION.SDK_INT >= 33) {
            try { t = (String[]) EditorInfo.class.getMethod("getContentMimeTypes").invoke(e); } catch (Exception | LinkageError ignored) { }
        }
        if (t == null && e.extras != null) {
            t = e.extras.getStringArray("androidx.core.view.inputmethod.EditorInfoCompat.CONTENT_MIME_TYPES");
            if (t == null) t = e.extras.getStringArray("android.support.v13.view.inputmethod.EditorInfoCompat.CONTENT_MIME_TYPES");
        }
        if (t == null) return false;
        for (String m : t) if (m != null && m.startsWith("image/")) return true;
        return false;
    }

    /** Images only on Android 13+ (the old storage permission no longer covers media there). */
    static String imagesPermission() {
        return Build.VERSION.SDK_INT >= 33 ? "android.permission.READ_MEDIA_IMAGES" : "android.permission.READ_EXTERNAL_STORAGE";
    }

    static Uri latestScreenshot(Context c) {
        String col = Build.VERSION.SDK_INT >= 29 ? "relative_path" : "_data";
        long since = System.currentTimeMillis() / 1000 - 120;
        try (Cursor cur = c.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, new String[]{"_id"},
                "date_added>=" + since + " AND (" + col + " LIKE ? OR " + col + " LIKE ? OR _display_name LIKE ? OR _display_name LIKE ?)",
                new String[]{"%creenshot%", "%криншот%", "Screenshot%", "Скриншот%"}, "date_added DESC")) {
            if (cur != null && cur.moveToFirst()) return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cur.getLong(0));
        } catch (RuntimeException ignored) { }
        return null;
    }

    static Bitmap thumb(Context c, Uri u) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = c.getContentResolver().openInputStream(u)) { BitmapFactory.decodeStream(in, null, o); }
            int s = 1, m = Math.min(o.outWidth, o.outHeight);
            while (m / (s * 2) >= 96) s *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = s;
            try (InputStream in = c.getContentResolver().openInputStream(u)) { return BitmapFactory.decodeStream(in, null, o); }
        } catch (Exception | OutOfMemoryError e) { return null; }
    }

    static void commit(Context c, InputConnection ic, Uri u) {
        if (Build.VERSION.SDK_INT < 25) return;
        try {
            String mime = c.getContentResolver().getType(u);
            if (mime == null || !mime.startsWith("image/")) mime = "image/png";
            ic.commitContent(new InputContentInfo(u, new ClipDescription("image", new String[]{mime})), InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null);
        } catch (RuntimeException ignored) { }
    }
}
