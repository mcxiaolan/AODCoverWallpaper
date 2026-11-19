package com.lantianya.aodcoverwallpaper.ui;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.LocaleManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.LocaleList;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.lantianya.aodcoverwallpaper.R;
import com.lantianya.aodcoverwallpaper.compat.Compat;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * One UI 风格设置界面，使用原生 View，不依赖额外 UI 库。
 */
public final class SettingsActivity extends Activity {

    private static final String STATE_SCROLL_Y = "scroll_y";

    private Compat.Report report;
    private ScrollView pageScroll;
    private FrameLayout titleSlot;
    private TextView pageTitle;
    private boolean hasResumedOnce;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (resetPerAppLocale()) {
            return;
        }
        report = Compat.read(this);
        int scrollY = savedInstanceState == null ? dp(180)
                : savedInstanceState.getInt(STATE_SCROLL_Y, dp(180));
        setContentView(build(scrollY));
        // 必须在 setContentView 之后调用：DecorView 那时才存在，
        // 否则 Window#getInsetsController() 会抛 NullPointerException。
        applySystemBars();
    }

    private boolean resetPerAppLocale() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return false;
        }
        LocaleManager localeManager = getSystemService(LocaleManager.class);
        if (localeManager == null || localeManager.getApplicationLocales().isEmpty()) {
            return false;
        }
        localeManager.setApplicationLocales(LocaleList.getEmptyLocaleList());
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pageScroll == null) {
            return;
        }
        if (!hasResumedOnce) {
            hasResumedOnce = true;
            return;
        }
        int scrollY = pageScroll.getScrollY();
        report = Compat.read(this);
        setContentView(build(scrollY));
        applySystemBars();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (pageScroll != null) {
            outState.putInt(STATE_SCROLL_Y, pageScroll.getScrollY());
        }
    }

    /**
     * 状态栏/导航栏跟随页面底色，图标明暗按<b>实际底色</b>决定。
     *
     * <p>不能只看 uiMode：三星的 DayNight 主题在深色模式下仍可能解析出浅色资源，
     * 直接按主题实际背景色判断，避免背景和系统栏图标明暗不匹配。
     */
    private void applySystemBars() {
        Window w = getWindow();
        if (w == null) {
            return;
        }
        int surface = themeColor(android.R.attr.colorBackground);
        w.setStatusBarColor(surface);
        w.setNavigationBarColor(surface);

        View decor = w.getDecorView();
        if (decor == null) {
            return;
        }
        boolean lightBackground = isColorLight(surface);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController c = w.getInsetsController();
            if (c == null) {
                return;
            }
            // 浅色背景 -> 需要深色图标（LIGHT_* 标志置位）
            c.setSystemBarsAppearance(
                    lightBackground ? WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS : 0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
            c.setSystemBarsAppearance(
                    lightBackground ? WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS : 0,
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        } else {
            int flags = decor.getSystemUiVisibility();
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            if (lightBackground) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            decor.setSystemUiVisibility(flags);
        }
    }

    /** 颜色是否偏亮（决定系统栏图标该用深色还是浅色）。 */
    private static boolean isColorLight(int color) {
        double luminance = 0.299 * ((color >> 16) & 0xFF)
                + 0.587 * ((color >> 8) & 0xFF)
                + 0.114 * (color & 0xFF);
        return luminance > 160;
    }

    private int themeColor(int attribute) {
        android.util.TypedValue value = new android.util.TypedValue();
        if (!getTheme().resolveAttribute(attribute, value, true)) {
            return 0xFF000000;
        }
        if (value.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT
                && value.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT) {
            return value.data;
        }
        return getColor(value.resourceId);
    }

    // ---------------------------------------------------------------- 布局

    private View build(int scrollY) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, dp(8), 0, dp(32));

        int headerTravel = dp(180);
        root.addView(buildHeader(headerTravel));
        root.addView(buildStatusCard());
        root.addView(buildDeviceCard());
        root.addView(buildAboutCard());

        pageScroll = new HeaderScrollView(root, headerTravel, scrollY);
        return pageScroll;
    }

    private View buildHeader(int headerTravel) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(28), dp(24), dp(16));

        titleSlot = new FrameLayout(this);
        titleSlot.setPadding(0, headerTravel, 0, 0);
        titleSlot.setMinimumHeight(dp(56) + headerTravel);
        titleSlot.setClipToPadding(false);
        box.addView(titleSlot, matchWrap());

        pageTitle = new TextView(this);
        pageTitle.setText(R.string.app_name);
        pageTitle.setTextAppearance(this, R.style.TextAppearance_M3_Headline);
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.START | Gravity.CENTER_VERTICAL);
        titleSlot.addView(pageTitle, titleParams);

        TextView sub = new TextView(this);
        sub.setText(R.string.app_subtitle);
        sub.setTextAppearance(this, R.style.TextAppearance_M3_BodyMedium);
        box.addView(sub, matchWrap());

        Button restart = new Button(this);
        restart.setText(R.string.restart_button);
        restart.setOnClickListener(view -> confirmRestart());
        LinearLayout.LayoutParams restartParams = matchWrap();
        restartParams.topMargin = dp(16);
        box.addView(restart, restartParams);
        return box;
    }

    private final class HeaderScrollView extends ScrollView {
        private final LinearLayout content;
        private final FrameLayout slot = titleSlot;
        private final TextView title = pageTitle;
        private final int headerTravel;
        private final Runnable snapHeader = this::settleHeader;
        private int initialScrollY;
        private boolean touching;
        private boolean scrolledDuringTouch;
        private ValueAnimator snapAnimator;

        HeaderScrollView(LinearLayout content, int headerTravel, int scrollY) {
            super(SettingsActivity.this);
            this.content = content;
            this.headerTravel = headerTravel;
            initialScrollY = scrollY;
            setOverScrollMode(View.OVER_SCROLL_NEVER);
            setVerticalScrollBarEnabled(false);
            addView(content, matchWrap());
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int minHeight = MeasureSpec.getSize(heightSpec) + headerTravel;
            if (content.getMinimumHeight() != minHeight) {
                content.setMinimumHeight(minHeight);
            }
            super.onMeasure(widthSpec, heightSpec);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            if (initialScrollY >= 0) {
                scrollTo(0, initialScrollY);
                initialScrollY = -1;
            }
            updateTitlePosition(getScrollY());
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                stopHeaderSnap();
                touching = true;
                scrolledDuringTouch = false;
            }
            boolean handled;
            if (action == MotionEvent.ACTION_UP && scrolledDuringTouch && isBetweenStops()) {
                MotionEvent cancel = MotionEvent.obtain(event);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                handled = super.dispatchTouchEvent(cancel);
                cancel.recycle();
            } else {
                handled = super.dispatchTouchEvent(event);
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                touching = false;
                settleHeader();
            }
            return handled;
        }

        @Override
        protected void onScrollChanged(int x, int y, int oldX, int oldY) {
            super.onScrollChanged(x, y, oldX, oldY);
            updateTitlePosition(y);
            if (touching) {
                scrolledDuringTouch |= y != oldY;
            } else if (snapAnimator == null || !snapAnimator.isRunning()) {
                removeCallbacks(snapHeader);
                if (isBetweenStops()) {
                    postDelayed(snapHeader, 100);
                }
            }
        }

        private void updateTitlePosition(int scrollY) {
            float progress = 1f - Math.min(1f, scrollY / (float) headerTravel);
            float centerOffset = (slot.getWidth() - title.getWidth()) * 0.5f;
            if (title.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
                centerOffset = -centerOffset;
            }
            title.setTranslationX(centerOffset * progress);
            title.setTranslationY(-headerTravel * 0.5f * progress);
        }

        private boolean isBetweenStops() {
            return getScrollY() > 0 && getScrollY() < headerTravel;
        }

        private void settleHeader() {
            if (touching || !isBetweenStops()) {
                return;
            }
            stopHeaderSnap();
            int scrollY = getScrollY();
            int target = headerTravel - scrollY >= dp(72) ? 0 : headerTravel;
            snapAnimator = ValueAnimator.ofInt(scrollY, target);
            snapAnimator.setDuration(240);
            snapAnimator.setInterpolator(new DecelerateInterpolator());
            snapAnimator.addUpdateListener(animation -> scrollTo(0, (int) animation.getAnimatedValue()));
            snapAnimator.start();
        }

        private void stopHeaderSnap() {
            removeCallbacks(snapHeader);
            if (snapAnimator != null) {
                snapAnimator.cancel();
                snapAnimator = null;
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            stopHeaderSnap();
            super.onDetachedFromWindow();
        }
    }

    private void confirmRestart() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.restart_title)
                .setMessage(R.string.restart_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.restart_button, (dialog, which) -> restartServices())
                .show();
    }

    private void restartServices() {
        Toast.makeText(this, R.string.restart_waiting_root, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            int exitCode = -1;
            boolean completed = false;
            Process process = null;
            try {
                process = new ProcessBuilder("su", "-c",
                        "am force-stop com.samsung.android.app.aodservice"
                                + " && killall com.android.systemui"
                                + " >/dev/null 2>&1")
                        .start();
                completed = process.waitFor(15, TimeUnit.SECONDS);
                if (completed) {
                    exitCode = process.exitValue();
                } else {
                    process.destroyForcibly();
                }
            } catch (IOException | InterruptedException exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
            boolean success = completed && exitCode == 0;
            runOnUiThread(() -> Toast.makeText(this,
                    success ? R.string.restart_success : R.string.restart_failed,
                    Toast.LENGTH_LONG).show());
        }, "aod-root-restart").start();
    }

    private View buildStatusCard() {
        LinearLayout card = card();

        boolean matchesBaseline = report.isExactBaseline();
        TextView badge = new TextView(this);
        badge.setText(matchesBaseline ? R.string.baseline_match : R.string.baseline_diff);
        badge.setTextSize(15);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(14), dp(7), dp(14), dp(7));
        badge.setTextColor(getColor(matchesBaseline ? R.color.state_ok : R.color.state_warn));
        badge.setBackground(badgeBackground(matchesBaseline
                ? R.color.state_ok_container : R.color.state_warn_container));
        LinearLayout.LayoutParams blp = wrapWrap();
        blp.bottomMargin = dp(12);
        card.addView(badge, blp);

        TextView desc = new TextView(this);
        desc.setText(matchesBaseline ? getString(R.string.compat_baseline_ok)
                : getString(R.string.compat_baseline_diff) + "\n" + report.deviation);
        desc.setTextAppearance(this, R.style.TextAppearance_M3_BodyMedium);
        if (!matchesBaseline) {
            desc.setTextColor(getColor(R.color.state_warn));
        }
        card.addView(desc, matchWrap());

        TextView hint = new TextView(this);
        hint.setText(R.string.compat_hint_generic);
        hint.setTextAppearance(this, R.style.TextAppearance_M3_BodyMedium);
        LinearLayout.LayoutParams hlp = matchWrap();
        hlp.topMargin = dp(12);
        card.addView(hint, hlp);
        return card;
    }

    private View buildDeviceCard() {
        LinearLayout card = card();
        card.addView(sectionTitle(R.string.title_device));

        card.addView(row(R.string.label_model, report.model));
        card.addView(row(R.string.label_android, report.androidVersion));
        card.addView(row(R.string.label_oneui,
                Compat.formatOneUi(parseInt(report.oneUiVersion))));
        card.addView(row(R.string.label_aod,
                TextUtils.isEmpty(report.aodVersion) ? "?" : report.aodVersion));
        card.addView(row(R.string.label_cover, getString(report.coverDisplay
                ? R.string.value_yes : R.string.value_no)));
        return card;
    }

    private View buildAboutCard() {
        LinearLayout card = card();
        card.addView(sectionTitle(R.string.title_about));

        card.addView(row(null, getString(R.string.about_version, versionName())));
        card.addView(row(null, getString(R.string.about_baseline,
                Compat.BASELINE_MODEL,
                String.valueOf(Compat.BASELINE_SDK),
                Compat.formatOneUi(Compat.BASELINE_ONEUI),
                Compat.BASELINE_AOD_VERSION)));
        card.addView(row(null, getString(R.string.about_scope)));
        return card;
    }

    // ---------------------------------------------------------------- 组件

    private TextView sectionTitle(int textRes) {
        TextView title = new TextView(this);
        title.setText(textRes);
        title.setTextAppearance(this, R.style.TextAppearance_M3_SectionTitle);
        LinearLayout.LayoutParams params = matchWrap();
        params.bottomMargin = dp(8);
        title.setLayoutParams(params);
        return title;
    }

    private View row(Integer labelRes, String value) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setPadding(0, dp(10), 0, dp(10));

        if (labelRes != null) {
            TextView label = new TextView(this);
            label.setText(labelRes);
            label.setTextAppearance(this, R.style.TextAppearance_M3_BodyLarge);
            box.addView(label, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }

        TextView val = new TextView(this);
        val.setText(value);
        val.setTextAppearance(this, R.style.TextAppearance_M3_BodyMedium);
        val.setGravity(labelRes == null ? Gravity.START : Gravity.END);
        box.addView(val, labelRes == null ? matchWrap() : wrapWrap());
        return box;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(16), dp(8), dp(16), dp(8));
        card.setLayoutParams(lp);
        card.setPadding(dp(20), dp(16), dp(20), dp(16));
        return card;
    }

    private GradientDrawable badgeBackground(int colorRes) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(dp(10));
        d.setColor(getColor(colorRes));
        return d;
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable t) {
            return "?";
        }
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Throwable t) {
            return 0;
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
