package com.lantianya.aodcoverwallpaper.settings;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

public final class ModuleSettings {

    public static final String AUTHORITY = "com.lantianya.aodcoverwallpaper.settings";
    public static final String URI = "content://" + AUTHORITY;
    public static final String PREFERENCES = "module_settings";
    public static final String KEY_KEEP_WALLPAPER_BRIGHT = "keep_wallpaper_bright";
    public static final String KEY_PERIODIC_DIM = "periodic_dim";
    public static final String KEY_DIM_INTERVAL_SECONDS = "dim_interval_seconds";
    public static final String EXTRA_KEEP_WALLPAPER_BRIGHT = "keep_wallpaper_bright";
    public static final String EXTRA_PERIODIC_DIM = "periodic_dim";
    public static final String EXTRA_DIM_INTERVAL_SECONDS = "dim_interval_seconds";
    public static final int DEFAULT_INTERVAL_SECONDS = 600;
    public static final int MIN_INTERVAL_SECONDS = 1;
    public static final int MAX_INTERVAL_SECONDS = 86400;
    public static final long DIM_DURATION_MILLIS = 2000L;

    private ModuleSettings() {
    }

    public static Options read(Context context) {
        try {
            ContentResolver resolver = context.getContentResolver();
            Bundle values = resolver.call(Uri.parse(URI), "read", null, null);
            if (values != null) {
                return new Options(
                        values.getBoolean(EXTRA_KEEP_WALLPAPER_BRIGHT, false),
                        values.getBoolean(EXTRA_PERIODIC_DIM, false),
                        values.getInt(EXTRA_DIM_INTERVAL_SECONDS, DEFAULT_INTERVAL_SECONDS));
            }
        } catch (Throwable ignored) {
        }
        return new Options(false, false, DEFAULT_INTERVAL_SECONDS);
    }

    public static final class Options {
        public final boolean keepWallpaperBright;
        public final boolean periodicDim;
        public final int dimIntervalSeconds;

        public Options(boolean keepWallpaperBright, boolean periodicDim,
                int dimIntervalSeconds) {
            this.keepWallpaperBright = keepWallpaperBright;
            this.periodicDim = periodicDim;
            this.dimIntervalSeconds = Math.max(MIN_INTERVAL_SECONDS,
                    Math.min(MAX_INTERVAL_SECONDS, dimIntervalSeconds));
        }
    }
}
