/* Copyright 2026 InstaPrime contributors. Based on https://github.com/SysAdminDoc/HushGram */
package app.hushgram.extension.instagram.settings;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.view.View;
import android.widget.Switch;

/** Keeps preference rebinding from snapping the visible switch to its destination. */
final class AnimatedSettingsSwitch extends Switch {
    private Motion motion;
    private ScreenColors colors;
    private SwitchThumb thumb;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    AnimatedSettingsSwitch(Context context) {
        super(context);
        setShowText(false);
        setBackground(null);
        setColors(ScreenColors.current());
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    void setColors(ScreenColors palette) {
        if (colors == palette) return;
        colors = palette;
        thumb = new SwitchThumb(getContext(), palette);
        setThumbDrawable(new InsetDrawable(thumb, 0, dp(6), 0, dp(6)));
        GradientDrawable track = new GradientDrawable() {
            @Override public boolean getPadding(Rect out) { out.set(dp(6), dp(6), dp(6), dp(6)); return true; }
        };
        track.setSize(dp(52), dp(32));
        setTrackDrawable(track);
        setSwitchMinWidth(dp(52));
        setThumbTextPadding(0);
        invalidate();
    }

    /** Owned by the preference, so replacing/recycling its view does not lose the transition. */
    static final class Motion {
        float progress;
        boolean initialized;
        boolean checked;
        ValueAnimator transition;
        private java.lang.ref.WeakReference<AnimatedSettingsSwitch> host = new java.lang.ref.WeakReference<>(null);

        void bind(AnimatedSettingsSwitch view) {
            host = new java.lang.ref.WeakReference<>(view);
            update(view.isChecked());
        }

        void update(boolean next) {
            if (initialized && checked == next) return;
            if (transition != null) transition.cancel();
            checked = next;
            float target = next ? 1f : 0f;
            if (!initialized || !ValueAnimator.areAnimatorsEnabled()) {
                progress = target;
            } else {
                transition = ValueAnimator.ofFloat(progress, target);
                transition.setDuration(220);
                transition.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
                transition.addUpdateListener(frame -> {
                    progress = (float) frame.getAnimatedValue();
                    AnimatedSettingsSwitch view = host.get();
                    if (view != null) view.invalidate();
                });
                transition.start();
            }
            initialized = true;
        }
    }

    void unbindMotion() { motion = null; }

    void bindMotion(Motion value) {
        motion = value;
        value.bind(this);
        invalidate();
    }

    @Override public void setChecked(boolean checked) {
        super.setChecked(checked);
        if (motion != null) motion.update(checked);
    }

    @Override protected void onDraw(Canvas canvas) {
        if (colors == null) return;
        float progress = motion == null ? (isChecked() ? 1f : 0f) : motion.progress;
        float left = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? getPaddingLeft()
                : getWidth() - getPaddingRight() - dp(52);
        float top = (getHeight() - dp(32)) / 2f;
        paint.setColor(isEnabled() ? blend(colors.switchOff, colors.accent, progress) : colors.switchOff);
        paint.setAlpha(255);
        canvas.drawRoundRect(left, top, left + dp(52), top + dp(32), dp(16), dp(16), paint);
        float slide = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? 1f - progress : progress;
        int x = Math.round(left + dp(6) + dp(20) * slide);
        thumb.setBounds(x, Math.round(top) + dp(6), x + dp(20), Math.round(top) + dp(26));
        thumb.setVisualProgress(progress);
        thumb.draw(canvas);
    }

    private static int blend(int from, int to, float p) {
        return Color.rgb(Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * p),
                Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * p),
                Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * p));
    }
}
