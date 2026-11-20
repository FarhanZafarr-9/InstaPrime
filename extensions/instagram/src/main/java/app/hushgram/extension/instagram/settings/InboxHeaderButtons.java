package app.hushgram.extension.instagram.settings;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.LinearLayout;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import app.hushgram.extension.shared.L10n;
import app.hushgram.extension.shared.Utils;
import app.hushgram.extension.shared.settings.HushgramPause;

/** Separate inbox Settings and Compose controls; native Compose gestures stay on their own view. */
public final class InboxHeaderButtons {
    private static final Map<Object, Boolean> actions = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Binding> bindings = new WeakHashMap<>();
    private InboxHeaderButtons() { }

    public static void mark(Object action) { if (action != null) actions.put(action, true); }

    /** The legacy header freezes its builder into a separate model before binding a view. */
    public static void copied(Object action, Object source) {
        if (action != null && actions.containsKey(source)) mark(action);
    }

    public static void bind(View button, Object action) {
        if (button == null) return;
        if (!actions.containsKey(action)) {
            Binding previous = bindings.remove(button);
            if (previous != null) previous.remove(button);
            return;
        }
        Binding binding = bindings.get(button);
        if (binding == null) {
            binding = new Binding(button);
            bindings.put(button, binding);
            button.addOnAttachStateChangeListener(binding);
            button.getViewTreeObserver().addOnPreDrawListener(binding);
        }
        button.post(binding::update);
    }

    private static final class Binding implements ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {
        final WeakReference<View> compose;
        WeakReference<ImageView> gear = new WeakReference<>(null);
        boolean hidden;
        int visibility;
        WeakReference<LinearLayout> expandedRow = new WeakReference<>(null);
        int originalWidth, expandedWidth;
        Binding(View view) { compose = new WeakReference<>(view); }

        void update() {
            View view = compose.get();
            if (view == null) return;
            boolean active = Utils.settingsReady() && !HushgramPause.isPaused();
            boolean showSettings = active && Settings.SETTINGS_BUTTON_IN_DM_HEADER.get();
            boolean hideCompose = active && Settings.HIDE_DM_COMPOSE_BUTTON.get();
            ImageView icon = gear.get();
            if (icon != null && (!showSettings || icon.getParent() != view.getParent())) {
                if (icon.getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup) icon.getParent()).removeView(icon);
                icon = null;
                gear.clear();
            }
            if (showSettings && icon == null && view.getParent() instanceof LinearLayout) {
                LinearLayout row = (LinearLayout) view.getParent();
                icon = new ImageView(view.getContext());
                icon.setImageDrawable(SettingsIcons.icon(view.getContext(), SettingsIcons.SETTINGS,
                        app.hushgram.extension.instagram.download.FeedDownloadButton.iconColor(view.getContext())));
                icon.setScaleType(ImageView.ScaleType.CENTER);
                icon.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), view.getPaddingBottom());
                icon.setMinimumWidth(view.getMinimumWidth());
                icon.setMinimumHeight(view.getMinimumHeight());
                if (view.getBackground() != null && view.getBackground().getConstantState() != null)
                    icon.setBackground(view.getBackground().getConstantState().newDrawable().mutate());
                icon.setContentDescription(L10n.t("InstaPrime settings"));
                icon.setFocusable(true);
                icon.setOnClickListener(InboxHeaderButtons::open);
                LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(view.getLayoutParams());
                if (view.getLayoutParams() instanceof LinearLayout.LayoutParams)
                    size = new LinearLayout.LayoutParams((LinearLayout.LayoutParams) view.getLayoutParams());
                size.weight = 0;
                // Compose may fill (or be weighted across) its native slot. The added
                // gear needs its own touch target, not that slot's entire width.
                if (size.width <= 0) {
                    size.width = LinearLayout.LayoutParams.WRAP_CONTENT;
                    icon.setMinimumWidth(Math.max(view.getMinimumWidth(),
                            Math.round(48 * view.getResources().getDisplayMetrics().density)));
                }
                row.addView(icon, row.indexOfChild(view), size);
                gear = new WeakReference<>(icon);
            }
            reserveSpace(view, icon, !hideCompose);
            if (hideCompose) {
                if (!hidden || view.getVisibility() != View.GONE) {
                    visibility = view.getVisibility();
                    // The parent's New message binder may already hide this same button.
                    // Both controls share a setting, so unchecking it must restore the button.
                    if (!hidden && visibility == View.GONE) visibility = View.VISIBLE;
                }
                hidden = true;
                if (view.getVisibility() != View.GONE) view.setVisibility(View.GONE);
            } else if (hidden) {
                hidden = false;
                if (view.getVisibility() == View.GONE) view.setVisibility(visibility);
            }
        }

        private void reserveSpace(View view, ImageView icon, boolean composeVisible) {
            LinearLayout previous = expandedRow.get();
            LinearLayout row = icon != null && composeVisible && view.getParent() instanceof LinearLayout
                    ? (LinearLayout) view.getParent() : null;
            if (previous != null && previous != row) restoreWidth();
            if (row == null || row.getLayoutParams() == null) return;
            android.view.ViewGroup.LayoutParams params = row.getLayoutParams();
            // Some native headers give the whole action container one fixed-width slot.
            // A compact gear alone still pushes Compose outside that slot.
            if (params.width <= 0) return;
            if (expandedRow.get() != row || params.width != expandedWidth) originalWidth = params.width;
            icon.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            LinearLayout.LayoutParams size = (LinearLayout.LayoutParams) icon.getLayoutParams();
            expandedWidth = originalWidth + icon.getMeasuredWidth() + size.leftMargin + size.rightMargin;
            expandedRow = new WeakReference<>(row);
            if (params.width != expandedWidth) {
                params.width = expandedWidth;
                row.setLayoutParams(params);
            }
        }

        private void restoreWidth() {
            LinearLayout row = expandedRow.get();
            if (row != null && row.getLayoutParams() != null && row.getLayoutParams().width == expandedWidth) {
                android.view.ViewGroup.LayoutParams params = row.getLayoutParams();
                params.width = originalWidth;
                row.setLayoutParams(params);
            }
            expandedRow.clear();
        }

        void remove(View view) {
            restoreWidth();
            ImageView icon = gear.get();
            if (icon != null && icon.getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup) icon.getParent()).removeView(icon);
            if (hidden && view.getVisibility() == View.GONE) view.setVisibility(visibility);
            hidden = false;
            gear.clear();
            if (view.getViewTreeObserver().isAlive()) view.getViewTreeObserver().removeOnPreDrawListener(this);
            view.removeOnAttachStateChangeListener(this);
        }
        @Override public boolean onPreDraw() { update(); return true; }
        @Override public void onViewAttachedToWindow(View view) {
            view.getViewTreeObserver().removeOnPreDrawListener(this);
            view.getViewTreeObserver().addOnPreDrawListener(this);
            update();
        }
        @Override public void onViewDetachedFromWindow(View view) {
            restoreWidth();
            if (view.getViewTreeObserver().isAlive()) view.getViewTreeObserver().removeOnPreDrawListener(this);
            ImageView icon = gear.get();
            if (icon != null && icon.getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup) icon.getParent()).removeView(icon);
            gear.clear();
        }
    }

    private static void open(View view) {
        if (!Utils.settingsReady() || HushgramPause.isPaused()) return;
        Context context = view.getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) { SettingsEntry.requestOpen((Activity) context); return; }
            Context next = ((ContextWrapper) context).getBaseContext();
            if (context == next) return;
            context = next;
        }
    }
}
