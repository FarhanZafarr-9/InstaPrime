package app.hushgram.extension.instagram.settings;

import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowInsetsController;
import android.view.animation.DecelerateInterpolator;

/** Common shell and list surfaces for settings' full-screen auxiliary pages. */
final class SettingsPageStyle {
    private SettingsPageStyle() { }

    static void window(Window window, ScreenColors colors) {
        if (window == null) return;
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        if (Build.VERSION.SDK_INT >= 35) {
            window.setDecorFitsSystemWindows(false);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
        } else {
            window.setStatusBarColor(colors.background);
            window.setNavigationBarColor(colors.background);
        }
        if (Build.VERSION.SDK_INT >= 29) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        View decor = window.getDecorView();
        if (Build.VERSION.SDK_INT >= 30 && decor.isAttachedToWindow()) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) controller.setSystemBarsAppearance(0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        } else {
            decor.setSystemUiVisibility(decor.getSystemUiVisibility()
                    & ~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR));
        }
    }

    static void transition(View view, boolean back) {
        view.animate().cancel();
        view.setAlpha(1f);
        view.setTranslationX(0f);
        if (!ValueAnimator.areAnimatorsEnabled() || !view.isLaidOut()) return;
        float direction = back ? -1f : 1f;
        if (view.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) direction = -direction;
        view.setAlpha(0.65f);
        view.setTranslationX(direction * 16 * view.getResources().getDisplayMetrics().density);
        view.animate().alpha(1f).translationX(0f).setDuration(180)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    static Dialog dialog(Context context, ScreenColors colors) {
        return new PageDialog(context, colors);
    }

    static void dismissForLink(Dialog dialog) {
        if (dialog instanceof PageDialog) ((PageDialog) dialog).dismissImmediately();
        else dialog.dismiss();
    }

    private static final class PageDialog extends Dialog {
            private final ScreenColors colors;
            private boolean closing;

            PageDialog(Context context, ScreenColors colors) {
                super(context, android.R.style.Theme_Material_NoActionBar);
                this.colors = colors;
            }

            void dismissImmediately() {
                View content = findViewById(android.R.id.content);
                if (content != null) content.animate().cancel();
                closing = false;
                super.dismiss();
            }

            private void finishClosing() {
                if (!closing) return;
                closing = false;
                super.dismiss();
            }

            @Override protected void onStart() {
                super.onStart();
                window(getWindow(), colors);
                View content = findViewById(android.R.id.content);
                content.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    @Override public boolean onPreDraw() {
                        content.getViewTreeObserver().removeOnPreDrawListener(this);
                        transition(content, false);
                        return true;
                    }
                });
            }

            @Override public void dismiss() {
                if (closing) return;
                View content = findViewById(android.R.id.content);
                if (!isShowing() || content == null || !content.isLaidOut() || !content.isAttachedToWindow()
                        || !ValueAnimator.areAnimatorsEnabled()) {
                    super.dismiss();
                    return;
                }
                closing = true;
                content.animate().cancel();
                float direction = content.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? 1 : -1;
                content.animate().alpha(0.65f).translationX(direction * 16 * content.getResources().getDisplayMetrics().density)
                        .setDuration(180).withEndAction(this::finishClosing).start();
                // Detaching or losing the activity can cancel an animation before its end action.
                content.postDelayed(this::finishClosing, 220);
            }
    }

    /** Bind every time, so recycled first/last rows follow the filtered list's current edges. */
    static void row(View row, ScreenColors colors, boolean first, boolean last) {
        float density = row.getResources().getDisplayMetrics().density;
        float top = (first ? 12 : 4) * density, bottom = (last ? 12 : 4) * density;
        GradientDrawable surface = new GradientDrawable();
        surface.setColor(colors.card);
        surface.setCornerRadii(new float[]{top, top, top, top, bottom, bottom, bottom, bottom});
        int side = Math.round(18 * density), gap = Math.round(density);
        Drawable fill = new InsetDrawable(surface, side, 0, side, gap);
        Drawable mask = new InsetDrawable(surface.getConstantState().newDrawable().mutate(), side, 0, side, gap);
        row.setBackground(new RippleDrawable(ScreenColors.interactionRipple(colors.heading), fill, mask));
        row.setPaddingRelative(Math.round(34 * density), Math.round(10 * density), Math.round(22 * density), Math.round(11 * density));
    }
}
