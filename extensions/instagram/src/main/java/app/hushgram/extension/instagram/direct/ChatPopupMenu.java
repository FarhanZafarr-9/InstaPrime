/*
 * Copyright 2026 InstaPrime contributors
 * HushGram foundation: https://github.com/SysAdminDoc/HushGram
 */
package app.hushgram.extension.instagram.direct;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckedTextView;
import android.widget.ListPopupWindow;
import android.widget.PopupMenu;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import app.hushgram.extension.instagram.settings.ScreenColors;

/** The chat's native menu actions, presented in one rounded, keyboard-accessible anchored list. */
final class ChatPopupMenu extends PopupMenu {
    private static WeakReference<ChatPopupMenu> latest = new WeakReference<>(null);
    private final View anchor;
    private final ListPopupWindow window;
    private OnDismissListener dismissal;
    private boolean open;

    ChatPopupMenu(Context context, View anchor) { this(context, anchor, Gravity.END); }

    ChatPopupMenu(Context context, View anchor, int gravity) {
        super(context, anchor, gravity);
        this.anchor = anchor;
        window = new ListPopupWindow(context);
        window.setAnchorView(anchor);
        window.setDropDownGravity(gravity);
        window.setModal(true);
        window.setInputMethodMode(ListPopupWindow.INPUT_METHOD_NOT_NEEDED);
        window.setOnDismissListener(this::closed);
    }

    private final View.OnAttachStateChangeListener attachment = new View.OnAttachStateChangeListener() {
        @Override public void onViewAttachedToWindow(View view) { }
        @Override public void onViewDetachedFromWindow(View view) { dismiss(); }
    };

    @Override public void setOnDismissListener(OnDismissListener listener) { dismissal = listener; }

    @Override public void show() {
        ChatPopupMenu previous = latest.get();
        if (previous != null && previous != this) previous.dismiss();
        latest = new WeakReference<>(this);
        Context context = anchor.getContext();
        ScreenColors colors = ScreenColors.forScreen(context);
        int text = colors.title;
        int surface = colors.dialog;
        List<MenuItem> items = new ArrayList<>();
        for (int i = 0; i < getMenu().size(); i++) {
            MenuItem item = getMenu().getItem(i);
            if (item.isVisible()) items.add(item);
        }
        if (items.isEmpty()) return;
        window.setBackgroundDrawable(surface(surface));
        Rect frame = new Rect();
        anchor.getWindowVisibleDisplayFrame(frame);
        int available = frame.width() > 0 ? frame.width() : context.getResources().getDisplayMetrics().widthPixels;
        int width = Math.max(1, Math.min(dp(300), available - dp(24)));
        window.setWidth(width);
        BaseAdapter adapter = new BaseAdapter() {
            @Override public int getCount() { return items.size(); }
            @Override public MenuItem getItem(int position) { return items.get(position); }
            @Override public long getItemId(int position) { return getItem(position).getItemId(); }
            @Override public boolean areAllItemsEnabled() { return false; }
            @Override public boolean isEnabled(int position) { return getItem(position).isEnabled(); }
            @Override public View getView(int position, View recycled, ViewGroup parent) {
                MenuItem item = getItem(position);
                // DropDownListView forces a direct TextView child to scroll horizontally.
                // A container lets translated menu labels wrap without losing native list navigation.
                android.widget.FrameLayout container = new android.widget.FrameLayout(context);
                container.setLayoutParams(new android.widget.AbsListView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                container.setEnabled(item.isEnabled());
                container.setContentDescription(item.getTitle());
                container.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
                CheckedTextView row = new CheckedTextView(context);
                row.setId(android.R.id.text1);
                row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                row.setText(item.getTitle());
                row.setTextSize(16);
                row.setSingleLine(false);
                row.setMaxLines(Integer.MAX_VALUE);
                row.setEllipsize(null);
                row.setHorizontallyScrolling(false);
                row.setTextColor(new ColorStateList(new int[][]{{-android.R.attr.state_enabled}, {}},
                        new int[]{(text & 0x00FFFFFF) | 0x61000000, text}));
                row.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                row.setMinHeight(dp(52));
                row.setPaddingRelative(dp(16), dp(8), dp(16), dp(8));
                row.setEnabled(item.isEnabled());
                container.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                    @Override public void onInitializeAccessibilityNodeInfo(View host,
                            android.view.accessibility.AccessibilityNodeInfo info) {
                        super.onInitializeAccessibilityNodeInfo(host, info);
                        info.setCheckable(item.isCheckable());
                        info.setChecked(item.isChecked());
                        info.setClassName(item.isCheckable() ? CheckedTextView.class.getName() : "android.widget.TextView");
                    }
                });
                if (item.isCheckable()) {
                    row.setCheckMarkTintList(null);
                    row.setCheckMarkDrawable(new CheckMark(colors.accent, dp(24)));
                    row.setChecked(item.isChecked());
                }
                Drawable icon = item.getIcon();
                if (icon != null) {
                    icon = icon.mutate();
                    icon.setTint(text);
                    icon.setBounds(0, 0, dp(24), dp(24));
                    row.setCompoundDrawablesRelative(icon, null, null, null);
                    row.setCompoundDrawablePadding(dp(12));
                }
                int tint = (colors.accent & 0x00FFFFFF) | 0x33000000;
                int focus = colors.choiceSelected();
                float top = dp(position == 0 ? 12 : 4);
                float bottom = dp(position == getCount() - 1 ? 12 : 4);
                float[] corners = {top, top, top, top, bottom, bottom, bottom, bottom};
                GradientDrawable fill = new GradientDrawable();
                fill.setCornerRadii(corners);
                fill.setColor(new ColorStateList(new int[][]{{-android.R.attr.state_enabled},
                        {android.R.attr.state_selected}, {android.R.attr.state_focused},
                        {android.R.attr.state_hovered}, {}}, new int[]{colors.iconSurface, focus, focus,
                        focus, item.isChecked() ? focus : colors.iconSurface}));
                GradientDrawable mask = new GradientDrawable();
                mask.setColor(Color.WHITE);
                mask.setCornerRadii(corners);
                container.setBackground(new InsetDrawable(new RippleDrawable(ColorStateList.valueOf(tint),
                        fill, mask), dp(10), 0, dp(10), position == getCount() - 1 ? 0 : dp(4)));
                container.setPadding(dp(10), 0, dp(10), position == getCount() - 1 ? 0 : dp(4));
                container.addView(row, new android.widget.FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return container;
            }
        };
        window.setAdapter(adapter);
        // Measure wrapped options at their real width before opening the window. A fixed row
        // estimate cuts off the last option at larger font sizes or with longer translations.
        int contentHeight = dp(20);
        android.widget.FrameLayout measuringParent = new android.widget.FrameLayout(context);
        for (int i = 0; i < adapter.getCount(); i++) {
            View option = adapter.getView(i, null, measuringParent);
            option.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            contentHeight += option.getMeasuredHeight();
        }
        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        int room = Math.max(frame.bottom - location[1] - anchor.getHeight(), location[1] - frame.top);
        if (room <= 0) room = context.getResources().getDisplayMetrics().heightPixels;
        window.setHeight(Math.min(contentHeight, Math.max(dp(60), room - dp(12))));
        window.setOnItemClickListener((parent, view, position, id) -> {
            if (!items.get(position).isEnabled()) return;
            try { getMenu().performIdentifierAction(items.get(position).getItemId(), 0); }
            finally { dismiss(); }
        });
        open = true;
        anchor.addOnAttachStateChangeListener(attachment);
        // A recycled or closing chat has no window to attach a popup to.
        if (anchor.getWindowToken() == null) return;
        window.show();
        window.getListView().setBackground(surface(surface));
        window.getListView().setLayoutDirection(anchor.getLayoutDirection());
        window.getListView().setClipToOutline(true);
        window.getListView().setDivider(null);
        window.getListView().setPadding(0, dp(10), 0, dp(10));
        window.getListView().setSelector(new ColorDrawable(Color.TRANSPARENT));
        window.getListView().setElevation(dp(8));
    }

    private GradientDrawable surface(int color) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(16));
        return background;
    }

    @Override public void dismiss() { window.dismiss(); closed(); }

    private void closed() {
        if (!open) return;
        open = false;
        anchor.removeOnAttachStateChangeListener(attachment);
        if (dismissal != null) dismissal.onDismiss(this);
    }

    private int dp(int value) { return Math.round(value * anchor.getResources().getDisplayMetrics().density); }

    /** A quiet trailing tick, with a reserved slot so selecting another option cannot shift text. */
    private static final class CheckMark extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int size;

        CheckMark(int color, int size) {
            this.size = size;
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override public boolean isStateful() { return true; }
        @Override protected boolean onStateChange(int[] state) { invalidateSelf(); return true; }
        @Override public void draw(Canvas canvas) {
            boolean checked = false;
            for (int state : getState()) checked |= state == android.R.attr.state_checked;
            if (!checked) return;
            Rect bounds = getBounds();
            float unit = Math.min(bounds.width(), bounds.height()) / 24f;
            float left = bounds.exactCenterX() - 12 * unit;
            float top = bounds.exactCenterY() - 12 * unit;
            Path tick = new Path();
            tick.moveTo(left + 5 * unit, top + 12 * unit);
            tick.lineTo(left + 10 * unit, top + 17 * unit);
            tick.lineTo(left + 19 * unit, top + 7 * unit);
            paint.setStrokeWidth(2 * unit);
            canvas.drawPath(tick, paint);
        }
        @Override public int getIntrinsicWidth() { return size; }
        @Override public int getIntrinsicHeight() { return size; }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    static ChatPopupMenu latestForTests() { return latest.get(); }
    ListPopupWindow windowForTests() { return window; }
    static void resetForTests() {
        ChatPopupMenu popup = latest.get();
        if (popup != null) popup.dismiss();
        latest = new WeakReference<>(null);
    }
}
