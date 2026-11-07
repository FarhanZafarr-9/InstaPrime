/*
 * Copyright 2026 InstaPrime contributors
 * HushGram foundation: https://github.com/SysAdminDoc/HushGram
 */
package app.hushgram.extension.instagram.direct;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
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
import app.hushgram.extension.instagram.download.FeedDownloadButton;

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
        int text = FeedDownloadButton.iconColor(context);
        boolean dark = Color.luminance(text) > 0.5;
        int surface = dark ? 0xFF26242A : 0xFFFAF8FC;
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
        window.setWidth(Math.max(1, Math.min(dp(300), available - dp(24))));
        window.setAdapter(new BaseAdapter() {
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
                row.setPaddingRelative(dp(16), dp(12), dp(16), dp(12));
                row.setEnabled(item.isEnabled());
                container.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                    @Override public void onInitializeAccessibilityNodeInfo(View host,
                            android.view.accessibility.AccessibilityNodeInfo info) {
                        super.onInitializeAccessibilityNodeInfo(host, info);
                        info.setCheckable(item.isCheckable());
                        info.setChecked(item.isChecked());
                        info.setClassName(item.isCheckable() ? "android.widget.RadioButton" : "android.widget.TextView");
                    }
                });
                if (item.isCheckable()) {
                    row.setCheckMarkDrawable(android.R.drawable.btn_radio);
                    row.setCheckMarkTintList(ColorStateList.valueOf(text));
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
                int tint = (text & 0x00FFFFFF) | 0x14000000;
                int focus = (text & 0x00FFFFFF) | 0x28000000;
                GradientDrawable fill = new GradientDrawable();
                fill.setColor(new ColorStateList(new int[][]{{-android.R.attr.state_enabled},
                        {android.R.attr.state_selected}, {android.R.attr.state_focused},
                        {android.R.attr.state_hovered}, {}}, new int[]{Color.TRANSPARENT, focus, focus,
                        focus, item.isChecked() ? tint : Color.TRANSPARENT}));
                container.setBackground(new RippleDrawable(ColorStateList.valueOf(tint),
                        fill, new ColorDrawable(Color.WHITE)));
                container.addView(row, new android.widget.FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return container;
            }
        });
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
        window.getListView().setPadding(0, dp(6), 0, dp(6));
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

    static ChatPopupMenu latestForTests() { return latest.get(); }
    ListPopupWindow windowForTests() { return window; }
    static void resetForTests() {
        ChatPopupMenu popup = latest.get();
        if (popup != null) popup.dismiss();
        latest = new WeakReference<>(null);
    }
}
