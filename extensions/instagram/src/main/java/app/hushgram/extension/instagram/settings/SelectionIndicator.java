/*
 * Copyright 2026 HushGram contributors
 * https://github.com/SysAdminDoc/HushGram
 */
package app.hushgram.extension.instagram.settings;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/** State follows CheckedTextView, keeping native single- and multi-choice behavior intact. */
final class SelectionIndicator extends Drawable {
    private final ScreenColors colors;
    private final boolean multiple;
    private final float density;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean checked;
    private boolean enabled = true;
    private int alpha = 255;

    SelectionIndicator(Context context, ScreenColors colors, boolean multiple) {
        this.colors = colors;
        this.multiple = multiple;
        density = context.getResources().getDisplayMetrics().density;
    }

    @Override public boolean isStateful() { return true; }
    @Override protected boolean onStateChange(int[] states) {
        boolean nextChecked = false, nextEnabled = false;
        for (int state : states) {
            if (state == android.R.attr.state_checked) nextChecked = true;
            if (state == android.R.attr.state_enabled) nextEnabled = true;
        }
        if (checked == nextChecked && enabled == nextEnabled) return false;
        checked = nextChecked;
        enabled = nextEnabled;
        invalidateSelf();
        return true;
    }

    @Override public void draw(Canvas canvas) {
        float x = getBounds().exactCenterX(), y = getBounds().exactCenterY();
        float radius = 9 * density;
        paint.setAlpha(enabled ? alpha : Math.round(alpha * 0.38f));
        paint.setStrokeWidth(2 * density);
        paint.setColor(checked ? colors.accent : colors.outline);
        paint.setAlpha(enabled ? alpha : Math.round(alpha * 0.38f));
        paint.setStyle(multiple && checked ? Paint.Style.FILL : Paint.Style.STROKE);
        if (multiple) {
            canvas.drawRoundRect(x - radius, y - radius, x + radius, y + radius, 5 * density, 5 * density, paint);
            if (checked) {
                paint.setColor(colors.onAccent);
                paint.setAlpha(enabled ? alpha : Math.round(alpha * 0.38f));
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
                Path tick = new Path();
                tick.moveTo(x - 4 * density, y);
                tick.lineTo(x - density, y + 3 * density);
                tick.lineTo(x + 5 * density, y - 4 * density);
                canvas.drawPath(tick, paint);
            }
        } else {
            canvas.drawCircle(x, y, radius, paint);
            if (checked) { paint.setStyle(Paint.Style.FILL); canvas.drawCircle(x, y, 4 * density, paint); }
        }
    }

    @Override public int getIntrinsicWidth() { return Math.round(32 * density); }
    @Override public int getIntrinsicHeight() { return Math.round(24 * density); }
    @Override public void setAlpha(int alpha) { this.alpha = alpha; invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
