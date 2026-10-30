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

/** Картинки в подсказках: недавний скриншот и скопированное изображение. Ничего не сохраняется: ни в историю буфера, ни на диск. */
final class Pics {
    private Pics() { }

    /** Поле принимает изображения (приложение объявило image/* в EditorInfo). */
    static boolean accepts(EditorInfo e) {
        if (e == null || Build.VERSION.SDK_INT < 25) return false;
        String[] t = null;
        if (Build.VERSION.SDK_INT >= 33) {
            try { t = (String[]) EditorInfo.class.getMethod("getContentMimeTypes").invoke(e); } catch (Exception ignored) { }
        }
        if (t == null && e.extras != null) {
            t = e.extras.getStringArray("androidx.core.view.inputmethod.EditorInfoCompat.CONTENT_MIME_TYPES");
            if (t == null) t = e.extras.getStringArray("android.support.v13.view.inputmethod.EditorInfoCompat.CONTENT_MIME_TYPES");
        }
        if (t == null) return false;
        for (String m : t) if (m != null && m.startsWith("image/")) return true;
        return false;
    }

    /** Самый свежий скриншот за последние две минуты (папка с «screenshot»/«скриншот» в пути). Вызывать не из главного потока. */
    static Uri latestScreenshot(Context c) {
        String col = Build.VERSION.SDK_INT >= 29 ? "relative_path" : "_data";
        try (Cursor cur = c.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, new String[]{"_id"},
                "date_added>=? AND (" + col + " LIKE ? OR " + col + " LIKE ?)",
                new String[]{String.valueOf(System.currentTimeMillis() / 1000 - 120), "%creenshot%", "%криншот%"}, "date_added DESC")) {
            if (cur != null && cur.moveToFirst()) return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cur.getLong(0));
        } catch (RuntimeException ignored) { }
        return null;
    }

    /** Маленькая копия (около 100 px по меньшей стороне): большая картинка в память не загружается. Не из главного потока. */
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
