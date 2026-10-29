/*
 * Copyright 2026 HushGram contributors
 * https://github.com/SysAdminDoc/HushGram
 */
package app.hushgram.extension.instagram.settings;

import android.content.Context;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/** A circular thumb with a checked glyph, driven by the native Switch state. */
final class SwitchThumb extends Drawable {
    private final ScreenColors colors;
    private final float density;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int alpha = 255;
    private float progress;
    private boolean initialized;
    private boolean checked;
    private ValueAnimator transition;

    SwitchThumb(Context context, ScreenColors colors) {
        this.colors = colors;
        density = context.getResources().getDisplayMetrics().density;
    }

    @Override public boolean isStateful() { return true; }
    boolean uses(ScreenColors palette) { return colors == palette; }

    @Override protected boolean onStateChange(int[] state) {
        boolean next = false;
        for (int value : state) next |= value == android.R.attr.state_checked;
        boolean changed = !initialized || checked != next;
        checked = next;
        if (changed) {
            if (transition != null) transition.cancel();
            float target = checked ? 1f : 0f;
            if (!initialized || getCallback() == null || !ValueAnimator.areAnimatorsEnabled()) {
                progress = target;
            } else {
                transition = ValueAnimator.ofFloat(progress, target);
                transition.setDuration(180);
                transition.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
                transition.addUpdateListener(frame -> {
                    progress = (float) frame.getAnimatedValue();
                    invalidateSelf();
                });
                transition.start();
            }
            initialized = true;
        }
        invalidateSelf();
        return true;
    }

    @Override public void jumpToCurrentState() {
        if (transition != null) { transition.cancel(); transition = null; }
        progress = checked ? 1f : 0f;
        invalidateSelf();
    }

    void setVisualProgress(float value) { progress = value; }

    @Override public void draw(Canvas canvas) {
        boolean enabled = false;
        for (int state : getState()) {
            enabled |= state == android.R.attr.state_enabled;
        }
        int fill = mix(colors.title, colors.onAccent, progress);
        // A disabled checked thumb must not inherit onAccent: it blends into the muted track.
        if (!enabled) fill = colors.title;
        paint.setColor(fill);
        paint.setAlpha(alpha);
        paint.setStyle(Paint.Style.FILL);
        float x = getBounds().exactCenterX(), y = getBounds().exactCenterY();
        float radius = Math.min(getBounds().width(), getBounds().height()) / 2f;
        canvas.drawCircle(x, y, radius, paint);
        if (progress <= 0) return;
        paint.setColor(enabled ? colors.accent : colors.switchOff);
        paint.setAlpha(Math.round(alpha * progress));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * density);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        Path tick = new Path();
        tick.moveTo(x - 4 * density, y);
        tick.lineTo(x - density, y + 3 * density);
        tick.lineTo(x + 5 * density, y - 4 * density);
        canvas.drawPath(tick, paint);
    }

    private static int mix(int from, int to, float amount) {
        return Color.rgb(Math.round(Color.red(from) * (1 - amount) + Color.red(to) * amount),
                Math.round(Color.green(from) * (1 - amount) + Color.green(to) * amount),
                Math.round(Color.blue(from) * (1 - amount) + Color.blue(to) * amount));
    }

    @Override public int getIntrinsicWidth() { return Math.round(20 * density); }
    @Override public int getIntrinsicHeight() { return Math.round(20 * density); }
    @Override public void setAlpha(int alpha) { this.alpha = alpha; invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
