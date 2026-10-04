package io.github.xieincz.homevisibility;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/** Read-only bridge from the launcher process; never accepts external writes. */
public final class SettingsProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        if (!"read".equals(method)) throw new UnsupportedOperationException("Read only");
        return ModuleSettings.read(getContext());
    }
    @Override public String getType(Uri uri) { return "vnd.android.cursor.item/vnd.homevisibility.settings"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] args, String order) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw readOnly(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw readOnly(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw readOnly(); }
    private static UnsupportedOperationException readOnly() {
        return new UnsupportedOperationException("Read only");
    }
}
