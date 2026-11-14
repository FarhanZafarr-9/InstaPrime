package app.hushgram.extension.instagram.misc;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowInsets;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import app.hushgram.extension.instagram.settings.SettingsStatus;
import app.hushgram.extension.shared.Utils;
import app.hushgram.extension.shared.Logger;

/** Keeps the native profile's status inset in its accent background, even after a same-activity deep link. */
public final class ProfileStatusBar {
    private static final Map<View, Cover> covers = new WeakHashMap<>();
    static Boolean inBuildForTests;
    static Integer profileIdForTests, heightForTests, colorForTests;
    private ProfileStatusBar() { }

    public static void watch(Activity activity) {
        try {
            if (!(inBuildForTests != null ? inBuildForTests : SettingsStatus.accentColor())) return;
            View decor = activity.getWindow().getDecorView();
            Cover cover = covers.get(decor);
            if (cover == null) {
                cover = new Cover(activity);
                covers.put(decor, cover);
                decor.getViewTreeObserver().addOnGlobalLayoutListener(cover);
                decor.getViewTreeObserver().addOnWindowFocusChangeListener(cover);
            }
            cover.update();
        } catch (Throwable failure) {
            Logger.printException(() -> "Could not follow profile status bar", failure);
        }
    }

    public static void release(Activity activity) {
        View decor = activity.getWindow().getDecorView();
        Cover cover = covers.remove(decor);
        if (cover == null) return;
        cover.hide(activity.getWindow());
        if (decor.getViewTreeObserver().isAlive()) {
            decor.getViewTreeObserver().removeOnGlobalLayoutListener(cover);
            decor.getViewTreeObserver().removeOnWindowFocusChangeListener(cover);
        }
    }

    static final class Cover implements ViewTreeObserver.OnGlobalLayoutListener, ViewTreeObserver.OnWindowFocusChangeListener {
        final WeakReference<Activity> activity;
        final ColorDrawable strip = new ColorDrawable();
        boolean shown;
        boolean originalContrast;
        int original, applied;
        Cover(Activity activity) { this.activity = new WeakReference<>(activity); }
        @Override public void onGlobalLayout() { update(); }
        @Override public void onWindowFocusChanged(boolean focused) { update(); }

        void update() {
            Activity host = activity.get();
            if (host == null || host.isDestroyed()) return;
            try {
                Window window = host.getWindow();
                View decor = window.getDecorView();
                int id = profileIdForTests != null ? profileIdForTests : host.getResources().getIdentifier(
                        "profile_action_bar", "id", Utils.resourcePackage(host));
                View bar = id == 0 ? null : decor.findViewById(id);
                Rect visible = new Rect();
                int color = colorForTests != null ? colorForTests : AccentTheme.profileBackground(host);
                WindowInsets insets = decor.getRootWindowInsets();
                int height = heightForTests != null ? heightForTests : insets == null ? 0 : Build.VERSION.SDK_INT >= 30
                        ? insets.getInsets(WindowInsets.Type.statusBars()).top : insets.getSystemWindowInsetTop();
                if (!decor.hasWindowFocus() || bar == null || !bar.isShown() || !bar.getGlobalVisibleRect(visible)
                        || Color.alpha(color) != 255 || height == 0) {
                    hide(window);
                    return;
                }
                if (!shown) {
                    original = window.getStatusBarColor();
                    if (Build.VERSION.SDK_INT >= 29) originalContrast = window.isStatusBarContrastEnforced();
                }
                if (Build.VERSION.SDK_INT >= 29) window.setStatusBarContrastEnforced(false);
                if (Build.VERSION.SDK_INT < 35) {
                    window.setStatusBarColor(color);
                    applied = color;
                } else {
                    strip.setColor(color);
                    strip.setBounds(0, 0, decor.getWidth(), height);
                    if (!shown) decor.getOverlay().add(strip);
                }
                shown = true;
            } catch (Throwable failure) {
                hide(host.getWindow());
                Logger.printException(() -> "Could not paint profile status bar", failure);
            }
        }

        void hide(Window window) {
            if (!shown) return;
            window.getDecorView().getOverlay().remove(strip);
            if (Build.VERSION.SDK_INT < 35 && window.getStatusBarColor() == applied) window.setStatusBarColor(original);
            if (Build.VERSION.SDK_INT >= 29 && !window.isStatusBarContrastEnforced()) window.setStatusBarContrastEnforced(originalContrast);
            shown = false;
        }
    }
}
