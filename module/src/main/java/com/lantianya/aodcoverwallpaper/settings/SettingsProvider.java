package com.lantianya.aodcoverwallpaper.settings;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

public final class SettingsProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!"read".equals(method) || getContext() == null) {
            return Bundle.EMPTY;
        }
        android.content.SharedPreferences preferences = getContext().getSharedPreferences(
                ModuleSettings.PREFERENCES, android.content.Context.MODE_PRIVATE);
        Bundle values = new Bundle();
        values.putBoolean(ModuleSettings.EXTRA_KEEP_WALLPAPER_BRIGHT,
                preferences.getBoolean(ModuleSettings.KEY_KEEP_WALLPAPER_BRIGHT, false));
        values.putBoolean(ModuleSettings.EXTRA_PERIODIC_DIM,
                preferences.getBoolean(ModuleSettings.KEY_PERIODIC_DIM, false));
        values.putInt(ModuleSettings.EXTRA_DIM_INTERVAL_SECONDS,
                preferences.getInt(ModuleSettings.KEY_DIM_INTERVAL_SECONDS,
                        ModuleSettings.DEFAULT_INTERVAL_SECONDS));
        return values;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
