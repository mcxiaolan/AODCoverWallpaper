package com.lantianya.aodcoverwallpaper.compat;

import android.content.Context;
import android.os.Build;

import com.lantianya.aodcoverwallpaper.R;

/** 读取设备信息并与验证基线比较。 */
public final class Compat {

    private static final String PKG_AOD = "com.samsung.android.app.aodservice";

    // ---------------------------------------------------------------- 基线
    //
    // 开发设备实测：
    //   SM-F731N / Galaxy Z Flip5
    //   Android 17 (API 37)
    //   One UI 9.0.0 (ro.build.version.oneui = 90000)
    //   息屏提醒 8.9.66.1
    //
    public static final String BASELINE_MODEL = "SM-F731N";
    public static final int BASELINE_SDK = 37;
    public static final int BASELINE_ONEUI = 90000;
    public static final String BASELINE_AOD_VERSION = "8.9.66.1";

    private Compat() {
    }

    // ---------------------------------------------------------------- 数据

    public static final class Report {
        public String model = "";
        public String androidVersion = "";
        public String oneUiVersion = "";
        public String aodVersion = "";
        public boolean coverDisplay;
        /** 与基线的差异，空表示一致。 */
        public String deviation = "";

        public boolean isExactBaseline() {
            return deviation.isEmpty();
        }
    }

    /** One UI 版本号格式化为 x.y.z。 */
    public static String formatOneUi(int code) {
        if (code <= 0) {
            return "?";
        }
        return (code / 10000) + "." + ((code / 100) % 100) + "." + (code % 100);
    }

    // ---------------------------------------------------------------- 检测

    public static Report read(Context ctx) {
        Report r = new Report();
        r.model = Build.MODEL == null ? "" : Build.MODEL;
        r.androidVersion = Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
        int oneUi = readOneUiCode();
        r.oneUiVersion = String.valueOf(oneUi);
        r.aodVersion = packageVersion(ctx, PKG_AOD);
        r.coverDisplay = hasCoverDisplay(ctx);

        r.deviation = deviationOf(ctx, r.model, oneUi, r.aodVersion);
        return r;
    }

    private static String deviationOf(Context ctx, String model, int oneUi,
            String aodVersion) {
        StringBuilder sb = new StringBuilder();
        if (!BASELINE_MODEL.equalsIgnoreCase(model)) {
            add(sb, ctx.getString(R.string.compat_dev_model, BASELINE_MODEL));
        }
        if (Build.VERSION.SDK_INT != BASELINE_SDK) {
            add(sb, ctx.getString(R.string.compat_dev_sdk,
                    String.valueOf(BASELINE_SDK)));
        }
        if (oneUi != BASELINE_ONEUI) {
            add(sb, ctx.getString(R.string.compat_dev_oneui,
                    formatOneUi(BASELINE_ONEUI)));
        }
        if (!BASELINE_AOD_VERSION.equals(aodVersion)) {
            add(sb, ctx.getString(R.string.compat_dev_aod, BASELINE_AOD_VERSION));
        }
        return sb.toString();
    }

    private static void add(StringBuilder sb, String line) {
        if (sb.length() > 0) {
            sb.append('\n');
        }
        sb.append(line);
    }

    /** One UI 版本号来自系统属性 ro.build.version.oneui（90000 = 9.0.0）。 */
    public static int readOneUiCode() {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Object v = sp.getMethod("get", String.class)
                    .invoke(null, "ro.build.version.oneui");
            if (v instanceof String) {
                return Integer.parseInt(((String) v).trim());
            }
        } catch (Throwable ignored) {
            // 非三星设备或属性缺失
        }
        return 0;
    }

    private static String packageVersion(Context ctx, String pkg) {
        try {
            return ctx.getPackageManager().getPackageInfo(pkg, 0).versionName;
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * 是否带外屏。
     *
     * <p>可行的判据都不完美，按可靠性依次尝试：
     * <ol>
     *   <li>{@code SEC_FLOATING_FEATURE_FRAMEWORK_SUPPORT_LARGE_COVER_SCREEN}
     *       三星浮动特性，Flip 系列为 TRUE（本机验证）；</li>
     *   <li>系统存在多个 display（合盖时外屏才可见，展开时拿不到，故只作兜底）。</li>
     * </ol>
     */
    public static boolean hasCoverDisplay(Context ctx) {
        if (readFloatingFeatureBool(
                "SEC_FLOATING_FEATURE_FRAMEWORK_SUPPORT_LARGE_COVER_SCREEN")) {
            return true;
        }
        try {
            android.hardware.display.DisplayManager dm =
                    (android.hardware.display.DisplayManager) ctx.getSystemService(
                            Context.DISPLAY_SERVICE);
            if (dm != null) {
                android.view.Display[] displays = dm.getDisplays();
                if (displays != null) {
                    for (android.view.Display d : displays) {
                        if (d != null
                                && d.getDisplayId() != android.view.Display.DEFAULT_DISPLAY) {
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // 降级
        }
        return false;
    }

    /** 读取三星 floating_feature.xml 里的布尔项（通过 SemFloatingFeature 反射）。 */
    private static boolean readFloatingFeatureBool(String key) {
        try {
            Class<?> cls = Class.forName("com.samsung.android.feature.SemFloatingFeature");
            Object inst = cls.getMethod("getInstance").invoke(null);
            Object v = cls.getMethod("getBoolean", String.class).invoke(inst, key);
            return Boolean.TRUE.equals(v);
        } catch (Throwable ignored) {
            return false;
        }
    }

}
