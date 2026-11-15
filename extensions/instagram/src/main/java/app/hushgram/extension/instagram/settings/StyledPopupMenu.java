/*
 * Copyright 2026 InstaPrime contributors
 * HushGram foundation: https://github.com/SysAdminDoc/HushGram
 */
package app.hushgram.extension.instagram.settings;

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

/** Shared anchored menu with rounded, keyboard-accessible options and native action semantics. */
public class StyledPopupMenu extends PopupMenu {
    private static WeakReference<StyledPopupMenu> latest = new WeakReference<>(null);
    private static final java.util.Map<View, NativeStyle> nativeStyles = new java.util.WeakHashMap<>();
    private final View anchor;
    private final ListPopupWindow window;
    private OnDismissListener dismissal;
    private boolean open;
    private int iconSizeDp = 24;

    /** Applies to both row rendering and content-width measurement. */
    public void setIconSizeDp(int size) { iconSizeDp = Math.max(1, size); }

    public StyledPopupMenu(Context context, View anchor) { this(context, anchor, Gravity.END); }

    public StyledPopupMenu(Context context, View anchor, int gravity) {
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
        StyledPopupMenu previous = latest.get();
        if (previous != null && previous != this) previous.dismiss();
        latest = new WeakReference<>(this);
        Context context = anchor.getContext();
        ScreenColors colors = ScreenColors.forScreen(context);
        boolean accent = Settings.THEME_NATIVE_POPUPS.get();
        boolean black = SettingsStatus.pureBlack();
        int text = accent || black ? colors.title : themeColor(context, android.R.attr.textColorPrimary, colors.title);
        int surface = popupSurface(context, colors, black, accent);
        List<MenuItem> items = new ArrayList<>();
        for (int i = 0; i < getMenu().size(); i++) {
            MenuItem item = getMenu().getItem(i);
            if (item.isVisible()) items.add(item);
        }
        if (items.isEmpty()) { dismiss(); return; }
        window.setBackgroundDrawable(surface(surface));
        Rect frame = new Rect();
        anchor.getWindowVisibleDisplayFrame(frame);
        int available = frame.width() > 0 ? frame.width() : context.getResources().getDisplayMetrics().widthPixels;
        int maxWidth = Math.max(1, available);
        int gutter = Math.min(dp(8), Math.max(0, (maxWidth - 1) / 2));
        int maxRowWidth = Math.max(1, maxWidth - gutter * 2);
        int contentPadding = dp(12), iconSize = dp(iconSizeDp), iconGap = dp(12), checkWidth = dp(24);
        window.setListSelector(new ColorDrawable(Color.TRANSPARENT));
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
                row.setCheckMarkDrawable(null);
                row.setMinWidth(0);
                row.setMinimumWidth(0);
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
                row.setMinHeight(dp(48));
                row.setPaddingRelative(contentPadding, dp(6), contentPadding, dp(6));
                row.setEnabled(item.isEnabled());
                row.setBackground(null);
                row.setForeground(null);
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
                    row.setCheckMarkDrawable(new CheckMark(colors.accent, checkWidth));
                    row.setChecked(item.isChecked());
                }
                Drawable icon = item.getIcon();
                if (icon != null) {
                    icon = icon.mutate();
                    icon.setTint(text);
                    icon.setBounds(0, 0, iconSize, iconSize);
                    row.setCompoundDrawablesRelative(icon, null, null, null);
                    row.setCompoundDrawablePadding(iconGap);
                }
                container.setBackground(optionBackground(colors, context.getResources().getDisplayMetrics().density, false));
                container.setPadding(0, 0, 0, dp(2));
                container.addView(row, new android.widget.FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return container;
            }
        };
        int width = 0;
        android.widget.FrameLayout measuringParent = new android.widget.FrameLayout(context);
        for (int i = 0; i < adapter.getCount(); i++) {
            View option = adapter.getView(i, null, measuringParent);
            CheckedTextView label = option.findViewById(android.R.id.text1);
            label.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int natural = (int) Math.ceil(android.text.Layout.getDesiredWidth(label.getText(), label.getPaint()))
                    + contentPadding * 2
                    + (items.get(i).getIcon() != null ? iconSize + iconGap : 0)
                    + (items.get(i).isCheckable() ? checkWidth : 0)
                    + option.getPaddingLeft() + option.getPaddingRight();
            width = Math.max(width, Math.min(maxRowWidth, natural));
        }
        width = Math.max(1, Math.min(maxWidth, width + gutter * 2));
        window.setWidth(width);
        window.setAdapter(adapter);
        // Measure wrapped options at their real width before opening the window. A fixed row
        // estimate cuts off the last option at larger font sizes or with longer translations.
        int contentHeight = dp(12);
        for (int i = 0; i < adapter.getCount(); i++) {
            View option = adapter.getView(i, null, measuringParent);
            option.measure(View.MeasureSpec.makeMeasureSpec(Math.max(1, width - gutter * 2), View.MeasureSpec.EXACTLY),
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
            dismiss();
            getMenu().performIdentifierAction(items.get(position).getItemId(), 0);
        });
        // A recycled or closing chat has no window to attach a popup to.
        if (anchor.getWindowToken() == null) return;
        open = true;
        anchor.addOnAttachStateChangeListener(attachment);
        window.show();
        window.getListView().setBackground(surface(surface));
        window.getListView().setLayoutDirection(anchor.getLayoutDirection());
        window.getListView().setClipToOutline(true);
        window.getListView().setDivider(null);
        window.getListView().setPadding(gutter, dp(6), gutter, dp(6));
        window.getListView().setClipToPadding(true);
        window.getListView().setSelector(new ColorDrawable(Color.TRANSPARENT));
        window.getListView().setElevation(dp(8));
    }

    private GradientDrawable surface(int color) {
        return panel(color, anchor.getResources().getDisplayMetrics().density);
    }

    static int popupSurface(Context context, ScreenColors colors, boolean black, boolean accent) {
        if (accent) return colors.popupSurface();
        if (black) return ScreenColors.BLACK.popupSurface();
        return themeColor(context, android.R.attr.colorBackgroundFloating, colors.dialog);
    }

    private static int themeColor(Context context, int attribute, int fallback) {
        android.content.res.TypedArray values = context.obtainStyledAttributes(new int[]{attribute});
        try { return values.getColor(0, fallback); }
        finally { values.recycle(); }
    }

    private static GradientDrawable panel(int color, float density) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(16 * density);
        return background;
    }

    private static Drawable optionBackground(ScreenColors colors, float density, boolean inset) {
        GradientDrawable fill = new GradientDrawable();
        float r = 12 * density;
        float[] corners = {r, r, r, r, r, r, r, r};
        fill.setCornerRadii(corners);
        int focus = colors.choiceSelected();
        fill.setColor(new ColorStateList(new int[][]{{-android.R.attr.state_enabled},
                {android.R.attr.state_selected}, {android.R.attr.state_focused}, {android.R.attr.state_hovered}, {}},
                new int[]{Color.TRANSPARENT, focus, focus, focus, Color.TRANSPARENT}));
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadii(corners);
        Drawable ripple = new RippleDrawable(ColorStateList.valueOf((colors.accent & 0x00ffffff) | 0x33000000), fill, mask);
        return inset ? new InsetDrawable(ripple, Math.round(6 * density), 0, Math.round(6 * density), Math.round(2 * density)) : ripple;
    }

    /** Styles Instagram's existing profile popup without rebuilding its models or callbacks. */
    public static void styleNative(android.widget.PopupWindow popup) {
        try {
            View content = popup.getContentView();
            if (content == null) return;
            boolean accent = Settings.THEME_NATIVE_POPUPS.get(), black = SettingsStatus.pureBlack();
            ScreenColors colors = accent ? ScreenColors.forScreen(content.getContext())
                    : black ? ScreenColors.BLACK : ScreenColors.forScreen(content.getContext());
            Integer tint = null;
            if (accent) tint = colors.popupSurface();
            else if (black) tint = ScreenColors.BLACK.popupSurface();
            NativeStyle style = nativeStyles.get(content);
            if (style == null) {
                style = new NativeStyle(content);
                nativeStyles.put(content, style);
                content.getViewTreeObserver().addOnPreDrawListener(style);
                content.addOnAttachStateChangeListener(style);
            }
            style.colors = colors;
            style.surfaceTint = tint;
            style.textColors = tint == null ? null : ScreenColors.dimmedWhenDisabled(colors.title);
            // Keep native shapes, transparent insets and padding. Replacing these with a solid
            // panel changes message-menu placement and can paint a viewport after its rows hide.
            if (tint != null) {
                if (popup.getBackground() != null) popup.getBackground().mutate().setTint(tint);
                if (content.getBackground() != null) content.getBackground().mutate().setTint(tint);
            }
            style.painted.clear();
            style.paint();
        } catch (Throwable failure) {
            app.hushgram.extension.shared.Logger.printException(() -> "Could not style profile popup", failure);
        }
    }

    private static void styleNativeContent(View view, NativeStyle style) {
        ScreenColors colors = style.colors;
        if (view instanceof android.widget.ListView) {
            android.widget.ListView list = (android.widget.ListView) view;
            if (list.getDivider() != null && (!(list.getDivider() instanceof ColorDrawable)
                    || ((ColorDrawable) list.getDivider()).getColor() != Color.TRANSPARENT)) {
                int height = list.getDividerHeight();
                list.setDivider(new ColorDrawable(Color.TRANSPARENT));
                list.setDividerHeight(height);
            }
            if (!(list.getSelector() instanceof ColorDrawable) || ((ColorDrawable) list.getSelector()).getColor() != Color.TRANSPARENT)
                list.setSelector(new ColorDrawable(Color.TRANSPARENT));
            for (int i = 0; i < list.getChildCount(); i++) style.option(list.getChildAt(i));
        }
        if (!(view instanceof android.widget.AdapterView) && (view.isClickable() || view.hasOnClickListeners())
                && labels(view) > 0 && !actionInside(view)) {
            style.option(view);
        }
        if (view instanceof android.widget.TextView) {
            android.widget.TextView text = (android.widget.TextView) view;
            int color = text.getCurrentTextColor();
            // Keep native destructive actions visibly red.
            if (style.textColors != null && !(Color.red(color) > 120 && Color.red(color) > Color.green(color) * 1.4f
                    && Color.red(color) > Color.blue(color) * 1.4f) && !style.textColors.equals(text.getTextColors()))
                text.setTextColor(style.textColors);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) styleNativeContent(group.getChildAt(i), style);
        }
    }

    private static int labels(View view) {
        if (view instanceof android.widget.TextView) return ((android.widget.TextView) view).getText().length() == 0 ? 0 : 1;
        int count = 0;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) count += labels(((ViewGroup) view).getChildAt(i));
        return count;
    }

    private static boolean actionInside(View view) {
        if (!(view instanceof ViewGroup)) return false;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if ((child.isClickable() || child.hasOnClickListeners()) && labels(child) > 0 || actionInside(child)) return true;
        }
        return false;
    }

    /** Paint bound native views; adapters, item identities and listeners remain untouched. */
    private static final class NativeStyle implements android.view.ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {
        final WeakReference<View> content;
        final java.util.Map<View, WeakReference<Drawable>> painted = new java.util.WeakHashMap<>();
        ScreenColors colors;
        ColorStateList textColors;
        Integer surfaceTint;
        NativeStyle(View content) { this.content = new WeakReference<>(content); }
        void paint() {
            View root = content.get();
            if (root == null) return;
            styleNativeContent(root, this);
        }
        void option(View view) {
            WeakReference<Drawable> previous = painted.get(view);
            if (previous != null && previous.get() == view.getBackground()) return;
            int start = view.getPaddingStart(), top = view.getPaddingTop(), end = view.getPaddingEnd(), bottom = view.getPaddingBottom();
            Drawable background = optionBackground(colors, view.getResources().getDisplayMetrics().density, true);
            view.setBackgroundTintList(null);
            Drawable foreground = view.getForeground();
            if (foreground instanceof RippleDrawable || foreground instanceof android.graphics.drawable.StateListDrawable)
                view.setForeground(null);
            view.setBackground(background);
            view.setPaddingRelative(start, top, end, bottom);
            painted.put(view, new WeakReference<>(background));
            if (surfaceTint != null) {
                View root = content.get();
                for (android.view.ViewParent parent = view.getParent(); parent instanceof View && parent != root; parent = parent.getParent()) {
                    Drawable original = ((View) parent).getBackground();
                    if (original != null) original.mutate().setTint(surfaceTint);
                }
            }
        }
        @Override public boolean onPreDraw() {
            try { paint(); }
            catch (Throwable failure) {
                View root = content.get();
                if (root != null) onViewDetachedFromWindow(root);
                app.hushgram.extension.shared.Logger.printException(() -> "Could not refresh profile popup style", failure);
            }
            return true;
        }
        @Override public void onViewAttachedToWindow(View view) { }
        @Override public void onViewDetachedFromWindow(View view) {
            if (view.getViewTreeObserver().isAlive()) view.getViewTreeObserver().removeOnPreDrawListener(this);
            view.removeOnAttachStateChangeListener(this);
            nativeStyles.remove(view);
            painted.clear();
        }
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

    public static StyledPopupMenu latestForTests() { return latest.get(); }
    public ListPopupWindow windowForTests() { return window; }
    public static void resetForTests() {
        StyledPopupMenu popup = latest.get();
        if (popup != null) popup.dismiss();
        latest = new WeakReference<>(null);
    }
}
