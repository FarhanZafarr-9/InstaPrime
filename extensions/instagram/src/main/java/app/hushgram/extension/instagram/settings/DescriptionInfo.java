/*
 * Copyright 2026 HushGram contributors
 * https://github.com/SysAdminDoc/HushGram
 */
package app.hushgram.extension.instagram.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.TextView;

import androidx.annotation.Nullable;

import app.hushgram.extension.shared.L10n;

/**
 * A row's description behind a trailing info button, before its switch or chevron, for a shorter
 * page. A tap on the "i" shows the description; the rest of the row still flips the switch. Off,
 * or with a screen reader exploring by touch, the description stays under the title as before.
 */
final class DescriptionInfo {
    /** Identifies the independent info button when a row is recycled. */
    static final String TAG = "hushgram_description_info";
    /** Set while a search is on: a row found by a word of its description shows that word. */
    static volatile boolean searching;

    private DescriptionInfo() {
    }

    /** A separate trailing action; it never shares the title or switch's drawable. */
    static void bind(View row, @Nullable CharSequence description) {
        TextView title = row.findViewById(android.R.id.title);
        TextView summary = row.findViewById(android.R.id.summary);
        View found = row.findViewById(android.R.id.widget_frame);
        if (title == null || summary == null || !(found instanceof android.widget.LinearLayout)) return;
        android.widget.LinearLayout frame = (android.widget.LinearLayout) found;
        View old = frame.findViewWithTag(TAG);
        boolean wanted = row.isEnabled() && wanted(row.getContext(), description);
        if (!wanted) {
            if (old != null) frame.removeView(old);
            return;
        }
        summary.setVisibility(View.GONE);
        android.widget.ImageButton button;
        if (old instanceof android.widget.ImageButton) button = (android.widget.ImageButton) old;
        else {
            button = new android.widget.ImageButton(row.getContext());
            button.setTag(TAG);
            android.widget.LinearLayout.LayoutParams size = new android.widget.LinearLayout.LayoutParams(dp(row, 32), dp(row, 32));
            size.setMarginEnd(dp(row, 8));
            frame.addView(button, 0, size);
        }
        ScreenColors colors = ScreenColors.current();
        Mark mark = new Mark(colors.heading, dp(row, 18));
        // Focusable children make ListView suppress its preference-row click.
        // Keep the secondary action touch-clickable without taking list-item focus.
        button.setFocusable(false);
        button.setFocusableInTouchMode(false);
        button.setImageDrawable(mark);
        button.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        button.setPadding(0, 0, 0, 0);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        android.graphics.drawable.GradientDrawable surface = new android.graphics.drawable.GradientDrawable();
        surface.setColor(colors.iconSurface);
        surface.setCornerRadius(dp(row, 6));
        surface.setStroke(dp(row, 2), colors.interactionOutline(android.graphics.Color.TRANSPARENT));
        button.setBackground(ScreenColors.boundedRipple(ScreenColors.interactionRipple(colors.heading), surface, null));
        button.setContentDescription(L10n.t("Description") + ": " + title.getText());
        button.setOnClickListener(view -> show(view.getContext(), title.getText(), description));
        frame.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        frame.setGravity(android.view.Gravity.CENTER_VERTICAL);
        frame.setVisibility(View.VISIBLE);
        frame.setMinimumHeight(dp(row, 32));
        android.view.ViewGroup.LayoutParams frameSize = frame.getLayoutParams();
        frameSize.width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
        frameSize.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
        frame.setLayoutParams(frameSize);
        frame.setClipChildren(false);
        frame.setClipToPadding(false);
    }

    /** Whether a row with [description] shows it behind the "i" now. */
    static boolean wanted(Context context, @Nullable CharSequence description) {
        if (TextUtils.isEmpty(description) || searching || !Settings.DESCRIPTIONS_BEHIND_INFO.savedValue()) return false;
        AccessibilityManager accessibility = context.getSystemService(AccessibilityManager.class);
        return accessibility == null || !accessibility.isTouchExplorationEnabled();
    }

    static AlertDialog show(Context context, CharSequence title, CharSequence description) {
        AlertDialog dialog = new AlertDialog.Builder(HushgramPreferenceFragment.themed(context))
                .setTitle(title)
                .setMessage(description)
                .setPositiveButton(L10n.t("OK"), null)
                .show();
        ScreenColors.dialog(dialog);
        return dialog;
    }

    private static int dp(View view, int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                view.getResources().getDisplayMetrics()));
    }

    /** A ring with an "i" in it, in the title's colour. */
    static final class Mark extends Drawable {
        private final int dpSize;
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint letter = new Paint(Paint.ANTI_ALIAS_FLAG);

        Mark(int color, int size) {
            dpSize = size;
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(size / 12f);
            ring.setColor(color);
            letter.setColor(color);
            letter.setTextAlign(Paint.Align.CENTER);
            letter.setTextSize(size * 0.62f);
            letter.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }

        @Override public int getIntrinsicWidth() { return dpSize; }
        @Override public int getIntrinsicHeight() { return dpSize; }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            float x = bounds.exactCenterX();
            float y = bounds.exactCenterY();
            canvas.drawCircle(x, y, bounds.width() / 2f - ring.getStrokeWidth(), ring);
            Paint.FontMetrics metrics = letter.getFontMetrics();
            canvas.drawText("i", x, y - (metrics.ascent + metrics.descent) / 2, letter);
        }

        @Override
        public void setAlpha(int alpha) {
            ring.setAlpha(alpha);
            letter.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter filter) {
            ring.setColorFilter(filter);
            letter.setColorFilter(filter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
