package com.lightboard.keyboard;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

public final class DictPageProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    @Override public String getType(Uri uri) { return "text/html"; }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String n = uri.getLastPathSegment();
        if (!"r".equals(mode) || n == null || !n.matches("dict_[a-z]{2,3}(_[a-z]{2})?\\.html") || getContext() == null) throw new FileNotFoundException();
        File f = new File(new File(getContext().getCacheDir(), "dict"), n);
        if (!f.isFile()) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) { return null; }

    @Override public Uri insert(Uri uri, ContentValues v) { return null; }

    @Override public int delete(Uri uri, String s, String[] a) { return 0; }

    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
