package com.lantianya.aodcoverwallpaper.hook;

import android.content.Context;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;


import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * 让「在外屏上显示壁纸」在任意「何时显示」模式下都能开启并生效。
 *
 * <p>目标：三星 One UI 的「息屏提醒」（com.samsung.android.app.aodservice）。
 *
 * <h3>为什么原版不行</h3>
 * <ol>
 *   <li><b>设置项被隐藏</b>：{@code aod.g7#H()} 用
 *       {@code ha6.R && SettingsHelper#l()} 决定可见性，而 {@code l()} 只读
 *       {@code aod_tap_to_show_mode}。</li>
 *   <li><b>外屏窗口缺少 FLAG_SHOW_WALLPAPER</b>：外屏 AOD 界面在
 *       {@code SubScreenPresentation}（title=SubScreenLockWindow，type=NOTIFICATION_SHADE）
 *       里，壁纸图层只有在窗口带该 flag 时才可见。
 *       而 {@code PluginAODImpl#applyAODFlags} 只在 AOD policy 认为
 *       「环境壁纸已启用」时加 flag，该标志又要求 WonderLand 壁纸；
 *       普通壁纸（LayeredWallpaperService）永远拿不到。</li>
 *   <li><b>AOD 遮罩涂黑</b>：{@code AlwaysOnDisplayEx} 会涂黑壁纸容器，
 *       外屏 {@code aod_front_scrim} 还会播放黑色渐变。</li>
 * </ol>
 *
 * <h3>hook 清单</h3>
 * <table>
 *   <tr><td>row_visible</td><td>Preference#D(boolean)</td><td>设置项可见</td></tr>
 *   <tr><td>decision</td><td>aod.yv7#invokeSuspend</td><td>外屏渲染决策</td></tr>
 *   <tr><td>cover_apply</td><td>aod.xv7#invokeSuspend</td><td>决策落到窗口</td></tr>
 *   <tr><td>policy_flag</td><td>PluginAODImpl#applyAODFlags</td><td>AOD policy 标志</td></tr>
 *   <tr><td>window_flag</td><td>ViewRootImpl#setView</td><td>补 FLAG_SHOW_WALLPAPER</td></tr>
 *   <tr><td>backdrop</td><td>View#setBackgroundColor</td><td>壁纸背景透明、前景遮罩限幅</td></tr>
 * </table>
 *
 * <p>所有改动都只在内存中进行：不写系统设置、不修改系统分区。
 * 全部 hook 以「外屏壁纸开关已打开」为前提，关掉即回到官方行为。
 */
public final class AodHook extends XposedModule {

    private static final String TAG = "AODCoverWallpaper";

    private static final String PKG_SYSTEMUI = "com.android.systemui";
    private static final String PKG_AOD = "com.samsung.android.app.aodservice";

    private static final String CLS_PREFERENCE = "androidx.preference.Preference";
    private static final String CLS_DECISION = "aod.yv7";
    private static final String CLS_COVER_APPLY = "aod.xv7";
    private static final String CLS_AOD_POLICY = "aod.z5";
    private static final String CLS_AOD_POLICY_HOLDER = "aod.q6";
    private static final String CLS_PLUGIN_AOD =
            "com.samsung.android.app.aodservice.plugin.PluginAODImpl";
    private static final String CLS_ALWAYS_ON_DISPLAY =
            "com.samsung.android.app.aodservice.AlwaysOnDisplayEx";
    private static final String CLS_VIEW_ROOT = "android.view.ViewRootImpl";

    private static final String SETTING_COVER_WALLPAPER = "aod_show_cover_screen_wallpaper";

    private static final int FLAG_SHOW_WALLPAPER = 0x100000;
    private static final int MAX_FRONT_SCRIM_ALPHA = 0x99;
    private static final String TITLE_LOCK_WINDOW = "SubScreenLockWindow";
    private static final String TAG_FRONT_SCRIM = "aod_front_scrim";

    private static final String[] COVER_WALLPAPER_KEYS = {
            "prefsShowCoverWallpaper",
            "prefsShowCoverWallpaperSummary",
            "prefsShowCoverWallpaperSummaryTopMargin"};

    private final AtomicBoolean pluginHooked = new AtomicBoolean(false);

    private volatile Context appContext;

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        log(4, TAG, "loaded in " + param.getProcessName());
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        String pkg = param.getPackageName();
        if (!PKG_SYSTEMUI.equals(pkg) && !PKG_AOD.equals(pkg)) {
            return;
        }
        ClassLoader cl = param.getClassLoader();
        hookRowVisible(cl);
        hookDecision(cl);
        hookPolicyFlag(cl);
        hookWindowFlag(cl);
        hookBackdrop(cl);
        if (PKG_SYSTEMUI.equals(pkg)) {
            hookPluginLoader();
        }
    }

    // ------------------------------------------------------------- row_visible
    /** Preference#setVisible(boolean) 在本版本为 final 的 D(boolean)。 */
    private void hookRowVisible(ClassLoader cl) {
        Class<?> cls = load(CLS_PREFERENCE, cl);
        if (cls == null) {
            return;
        }
        Method target = null;
        for (Method m : cls.getDeclaredMethods()) {
            if ("D".equals(m.getName()) && m.getParameterCount() == 1
                    && m.getParameterTypes()[0] == boolean.class) {
                target = m;
                break;
            }
        }
        if (target == null) {
            markFailed("row_visible", "Preference#D(boolean) not found");
            return;
        }
        if (install(target, "row:visible", chain -> {
            if (Boolean.FALSE.equals(chain.getArg(0))
                    && isCoverWallpaperRow(chain.getThisObject())) {
                return chain.proceed(new Object[]{Boolean.TRUE});
            }
            return chain.proceed();
        })) {
            markOk("row_visible");
        }
    }

    private static boolean isCoverWallpaperRow(Object pref) {
        Object key = readKey(pref);
        if (!(key instanceof String)) {
            return false;
        }
        for (String k : COVER_WALLPAPER_KEYS) {
            if (k.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * "z" 声明在 Preference 基类，实际对象通常是 SwitchPreference /
     * PreferenceCategory 等子类，必须沿继承链向上查找。
     */
    private static Object readKey(Object pref) {
        if (pref == null) {
            return null;
        }
        for (Class<?> c = pref.getClass(); c != null && c != Object.class;
                c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField("z");
                f.setAccessible(true);
                return f.get(pref);
            } catch (NoSuchFieldException ignored) {
                // 继续向上
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- decision
    /** 强制外屏壁纸渲染决策为 true。 */
    private void hookDecision(ClassLoader cl) {
        Class<?> cls = load(CLS_DECISION, cl);
        if (cls == null) {
            return;
        }
        Method m = method(cls, "invokeSuspend", Object.class);
        if (m == null) {
            markFailed("decision", "aod.yv7#invokeSuspend not found");
            return;
        }
        if (install(m, "cover:decision", chain -> {
            Object r = chain.proceed();
            if (Boolean.FALSE.equals(r) && isCoverWallpaperEnabled()) {
                return Boolean.TRUE;
            }
            return r;
        })) {
            markOk("decision");
        }
    }

    // ------------------------------------------------------------ cover_apply
    /** aod.xv7 把决策落到窗口；在方法体执行前把决策字段 p 置 true。 */
    private void hookCoverApply(ClassLoader cl) {
        Class<?> cls = load(CLS_COVER_APPLY, cl);
        if (cls == null) {
            return;
        }
        final Field decision = field(cls, "p");
        final Field variant = field(cls, "o");
        Method m = method(cls, "invokeSuspend", Object.class);
        if (decision == null || variant == null || m == null
                || decision.getType() != boolean.class) {
            markFailed("cover_apply", "aod.xv7 layout changed");
            return;
        }
        if (install(m, "cover:apply", chain -> {
            Object self = chain.getThisObject();
            if (self != null && isCoverWallpaperEnabled()) {
                try {
                    // o==1 才是真正的渲染分支
                    if (variant.getInt(self) == 1 && !decision.getBoolean(self)) {
                        decision.setBoolean(self, true);
                    }
                } catch (Throwable ignored) {
                    // 继续原逻辑
                }
            }
            return chain.proceed();
        })) {
            markOk("cover_apply");
        }
    }

    // ------------------------------------------------------------ policy_flag
    /** PluginAODImpl#applyAODFlags 只在 aod.z5#f 为 true 时补 FLAG_SHOW_WALLPAPER。 */
    private void hookPolicyFlag(ClassLoader cl) {
        Class<?> policyCls = load(CLS_AOD_POLICY, cl);
        Class<?> pluginCls = load(CLS_PLUGIN_AOD, cl);
        if (policyCls == null || pluginCls == null) {
            return;
        }
        final Field flag = field(policyCls, "f");
        Method m = method(pluginCls, "applyAODFlags",
                WindowManager.LayoutParams.class, boolean.class, boolean.class);
        if (flag == null || m == null || flag.getType() != boolean.class) {
            markFailed("policy_flag", "aod.z5#f or applyAODFlags changed");
            return;
        }
        if (install(m, "aod:policy-flag", chain -> {
            if (chain.getArg(0) instanceof WindowManager.LayoutParams
                    && isCoverWallpaperEnabled()) {
                Object policy = findPolicy(chain.getThisObject(), cl);
                if (policy != null) {
                    try {
                        if (!flag.getBoolean(policy)) {
                            flag.setBoolean(policy, true);
                        }
                    } catch (Throwable ignored) {
                        // 继续原逻辑
                    }
                }
            }
            return chain.proceed();
        })) {
            markOk("policy_flag");
        }
    }

    /** PluginAODImpl -> AlwaysOnDisplayEx -> aod.q6 -> aod.z5 */
    private Object findPolicy(Object plugin, ClassLoader cl) {
        Object aod = byType(plugin, load(CLS_ALWAYS_ON_DISPLAY, cl));
        Object holder = byType(aod, load(CLS_AOD_POLICY_HOLDER, cl));
        return byType(holder, load(CLS_AOD_POLICY, cl));
    }

    private static Object byType(Object owner, Class<?> type) {
        if (owner == null || type == null) {
            return null;
        }
        for (Class<?> c = owner.getClass(); c != null && c != Object.class;
                c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (type.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        return f.get(owner);
                    } catch (Throwable ignored) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------ window_flag
    /** 外屏窗口首次提交属性时补 FLAG_SHOW_WALLPAPER，让壁纸图层可见。 */
    private void hookWindowFlag(ClassLoader cl) {
        Class<?> cls = load(CLS_VIEW_ROOT, cl);
        if (cls == null) {
            return;
        }
        int installed = 0;
        for (Method cand : cls.getDeclaredMethods()) {
            if (!"setView".equals(cand.getName())) {
                continue;
            }
            final Method target = cand;
            if (install(target, "vri:setView" + cand.getParameterCount(), chain -> {
                for (int i = 0; i < target.getParameterCount(); i++) {
                    Object arg = chain.getArg(i);
                    if (arg instanceof WindowManager.LayoutParams
                            && isCoverWallpaperEnabled()) {
                        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) arg;
                        CharSequence title = lp.getTitle();
                        if (title != null && TITLE_LOCK_WINDOW.contentEquals(title)) {
                            lp.flags |= FLAG_SHOW_WALLPAPER;
                        }
                    }
                }
                return chain.proceed();
            })) {
                installed++;
            }
        }
        if (installed > 0) {
            markOk("window_flag");
        } else {
            markFailed("window_flag", "ViewRootImpl#setView not hookable");
        }
    }

    // --------------------------------------------------------------- backdrop
    /** AOD 把外屏容器涂黑，这里改透明，让壁纸透出来。 */
    /**
     * AOD 给外屏容器涂的黑底会盖住壁纸。
     *
     * <p>必须严格限定作用范围：只处理 AOD 覆盖容器
     * （tag = {@code aod_overlay_container}）、外屏前景遮罩及覆盖容器的子视图。
     * 早期版本在这里无条件拦截所有黑色背景，结果把 SystemUI 的启动遮罩、
     * 充电界面等无关视图也改成了透明，导致整个系统被冲淡成白色。
     */
    private void hookBackdrop(ClassLoader cl) {
        Method m = method(View.class, "setBackgroundColor", int.class);
        if (m == null) {
            markFailed("backdrop", "View#setBackgroundColor not found");
            return;
        }
        if (install(m, "aod:black-bg", chain -> {
            Object arg = chain.getArg(0);
            Object self = chain.getThisObject();
            if (arg instanceof Integer
                    && (((Integer) arg) & 0x00FFFFFF) == 0
                    && self instanceof View) {
                View v = (View) self;
                boolean match = isAodBackdrop(v);
                log(4, TAG, "black bg on " + describeView(v)
                        + " match=" + match
                        + " enabled=" + isCoverWallpaperEnabled());
                if (match && isCoverWallpaperEnabled()) {
                    if (isFrontScrim(v) || isClockItemBackdrop(v)) {
                        int color = (Integer) arg;
                        int alpha = color >>> 24;
                        if (alpha > MAX_FRONT_SCRIM_ALPHA) {
                            int dimmed = (MAX_FRONT_SCRIM_ALPHA << 24)
                                    | (color & 0x00FFFFFF);
                            return chain.proceed(new Object[]{Integer.valueOf(dimmed)});
                        }
                    } else {
                        return chain.proceed(new Object[]{Integer.valueOf(0)});
                    }
                }
            }
            return chain.proceed();
        })) {
            markOk("backdrop");
        }
    }

    private static String describeView(View v) {
        StringBuilder sb = new StringBuilder(v.getClass().getSimpleName());
        Object tag = v.getTag();
        if (tag instanceof String) {
            sb.append("[tag=").append(tag).append(']');
        }
        int id = v.getId();
        if (id != View.NO_ID) {
            sb.append("[id=0x").append(Integer.toHexString(id)).append(']');
        }
        android.view.ViewParent p = v.getParent();
        if (p instanceof View) {
            Object pt = ((View) p).getTag();
            sb.append("[parent=").append(((View) p).getClass().getSimpleName());
            if (pt instanceof String) {
                sb.append(" tag=").append(pt);
            }
            sb.append(']');
        }
        return sb.toString();
    }

    private static final String TAG_OVERLAY_CONTAINER = "aod_overlay_container";
    private static final String CLS_CLOCK_ITEM_CONTAINER = "SubLauncherClockItemView";

    /**
     * 判断某个 View 是否属于「外屏 AOD 的背景层」。
     *
     * <p>只修改 AOD 覆盖容器和外屏前景遮罩：
     * <ol>
     *   <li>{@code AODOverlayContainer}（tag = aod_overlay_container）—— AOD 覆盖容器；</li>
     *   <li>{@code aod_front_scrim} —— 外屏前景遮罩。</li>
     * </ol>
     * 时钟项中的匿名视图是三星原生壁纸压暗层，只限制其黑色遮罩不全黑。
     * 这样也避免误伤 SystemUI 的其它黑色背景（启动遮罩、充电界面等）。
     */
    private static boolean isAodBackdrop(View v) {
        Object tag = v.getTag();
        if ((tag instanceof String && (TAG_OVERLAY_CONTAINER.equals(tag)
                || TAG_FRONT_SCRIM.equals(tag))) || hasResourceName(v, TAG_FRONT_SCRIM)) {
            return true;
        }
        android.view.ViewParent parent = v.getParent();
        if (parent instanceof View) {
            View p = (View) parent;
            Object parentTag = p.getTag();
            if ((parentTag instanceof String && (TAG_OVERLAY_CONTAINER.equals(parentTag)
                    || TAG_FRONT_SCRIM.equals(parentTag)))
                    || hasResourceName(p, TAG_FRONT_SCRIM)) {
                return true;
            }
        }
        return isClockItemBackdrop(v);
    }

    private static boolean isClockItemBackdrop(View view) {
        android.view.ViewParent parent = view.getParent();
        if (!(parent instanceof View)) {
            return false;
        }
        for (Class<?> type = parent.getClass(); type != null && type != Object.class;
                type = type.getSuperclass()) {
            if (CLS_CLOCK_ITEM_CONTAINER.equals(type.getSimpleName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFrontScrim(View view) {
        if (TAG_FRONT_SCRIM.equals(view.getTag()) || hasResourceName(view, TAG_FRONT_SCRIM)) {
            return true;
        }
        android.view.ViewParent parent = view.getParent();
        if (parent instanceof View) {
            View parentView = (View) parent;
            return TAG_FRONT_SCRIM.equals(parentView.getTag())
                    || hasResourceName(parentView, TAG_FRONT_SCRIM);
        }
        return false;
    }

    private static boolean hasResourceName(View view, String name) {
        if (view.getId() == View.NO_ID) {
            return false;
        }
        try {
            return name.equals(view.getResources().getResourceEntryName(view.getId()));
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ----------------------------------------------------------- plugin loader
    /**
     * aod.* 是插件类，由 SystemUI 用独立 classloader 加载，
     * 所以 decision / cover_apply / policy_flag 需要在插件出现后补挂。
     */
    private void hookPluginLoader() {
        try {
            Method m = Class.forName("dalvik.system.BaseDexClassLoader")
                    .getDeclaredMethod("findClass", String.class);
            install(m, "plugin:loader", chain -> {
                Object r = chain.proceed();
                if (!pluginHooked.get() && r instanceof Class) {
                    String name = ((Class<?>) r).getName();
                    if (name != null && name.startsWith("aod.")) {
                        ClassLoader loader = ((Class<?>) r).getClassLoader();
                        if (loader != null
                                && loader != ClassLoader.getSystemClassLoader()
                                && pluginHooked.compareAndSet(false, true)) {
                            hookDecision(loader);
                            hookCoverApply(loader);
                            hookPolicyFlag(loader);
                        }
                    }
                }
                return r;
            });
        } catch (Throwable t) {
            log(5, TAG, "plugin loader hook failed", t);
        }
    }

    // -------------------------------------------------------------- hook 状态记录

    /** 记录每个 hook 是否装载成功，仅用于日志排查。 */
    private void markOk(String id) {
        log(3, TAG, id + " ready");
    }

    private void markFailed(String id, String why) {
        log(4, TAG, id + " unavailable: " + why);
    }

    // ------------------------------------------------------------------ utils

    /** 外屏壁纸开关，默认 1，与官方 SettingsHelper#i() 一致。 */
    private boolean isCoverWallpaperEnabled() {
        Context ctx = resolveContext();
        if (ctx == null) {
            return true;
        }
        try {
            return Settings.System.getInt(ctx.getContentResolver(),
                    SETTING_COVER_WALLPAPER, 1) != 0;
        } catch (Throwable t) {
            return true;
        }
    }

    private Context resolveContext() {
        Context ctx = appContext;
        if (ctx != null) {
            return ctx;
        }
        try {
            Method m = Class.forName("android.app.ActivityThread")
                    .getDeclaredMethod("currentApplication");
            m.setAccessible(true);
            Object app = m.invoke(null);
            if (app instanceof Context) {
                appContext = (Context) app;
                return appContext;
            }
        } catch (Throwable ignored) {
            // 稍后重试
        }
        return null;
    }

    private static Class<?> load(String name, ClassLoader cl) {
        try {
            return Class.forName(name, false, cl);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Method method(Class<?> cls, String name, Class<?>... params) {
        try {
            return cls.getDeclaredMethod(name, params);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Field field(Class<?> cls, String name) {
        try {
            Field f = cls.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (Throwable t) {
            return null;
        }
    }

    private interface Interceptor {
        Object intercept(XposedInterface.Chain chain) throws Throwable;
    }

    private boolean install(Method m, String id, Interceptor interceptor) {
        try {
            hook(m).setId(id)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(interceptor::intercept);
            return true;
        } catch (Throwable t) {
            log(5, TAG, "hook " + id + " failed", t);
            return false;
        }
    }
}
