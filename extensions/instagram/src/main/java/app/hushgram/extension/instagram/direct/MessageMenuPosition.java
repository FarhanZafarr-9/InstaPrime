package app.hushgram.extension.instagram.direct;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.PopupWindow;
import java.lang.ref.WeakReference;

/** Carries the primary message menu's measured position into its More submenu. */
public final class MessageMenuPosition {
    private static Anchor latest;
    private MessageMenuPosition() { }

    public static void remember(PopupWindow popup) {
        View content = popup.getContentView();
        if (content == null) return;
        Anchor anchor = new Anchor(content);
        latest = anchor;
        content.getViewTreeObserver().addOnPreDrawListener(anchor);
        content.addOnAttachStateChangeListener(anchor);
    }

    /** Native X can be an offset from either edge, depending on message direction. */
    public static int primaryX(int nativeX) {
        Anchor anchor = latest;
        if (anchor == null || anchor.detached != 0) return nativeX;
        View content = anchor.content.get();
        Activity host = anchor.activity.get();
        if (content == null || host == null || host.isDestroyed()) return nativeX;
        Rect frame = new Rect();
        View decor = host.getWindow().getDecorView();
        decor.getWindowVisibleDisplayFrame(frame);
        if (frame.isEmpty()) return nativeX;
        int width = content.getMeasuredWidth();
        if (width <= 0) {
            content.measure(View.MeasureSpec.makeMeasureSpec(frame.width(), View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            width = content.getMeasuredWidth();
        }
        int[] origin = new int[2];
        decor.getLocationOnScreen(origin);
        return insetPrimary(nativeX, width, frame.right - origin[0], edgeGap(content));
    }

    static int insetPrimary(int offset, int width, int right, int gap) {
        return width > 0 && width + 2 * gap <= right
                ? Math.max(gap, insetRight(offset, width, right, gap)) : offset;
    }

    static int insetRight(int x, int width, int right, int gap) {
        return width > 0 && width + gap <= right ? Math.min(x, right - width - gap) : x;
    }

    private static int edgeGap(View view) {
        // Extra edge gap disabled while validating message-anchored placement.
        // return Math.round(12 * view.getResources().getDisplayMetrics().density);
        return 0;
    }

    /** Align the tray to the held bubble, retaining native avatar space on incoming messages. */
    static int reactionLeft(Rect bubble, Rect viewport, int nativeLeft, int width) {
        if (bubble == null || bubble.isEmpty() || viewport.isEmpty() || width <= 0 || width > viewport.width()
                || bubble.width() >= viewport.width() || bubble.right <= viewport.left || bubble.left >= viewport.right) {
            return nativeLeft;
        }
        int right = Math.min(viewport.right, Math.max(nativeLeft + width, bubble.right));
        return Math.max(viewport.left, Math.min(bubble.left, right - width));
    }

    /** Called from the verified native actions fragment, once its own view has been created. */
    public static void actionsCreated(Object fragment, View root) {
        try {
            TrayAnchor anchor = new TrayAnchor(root, fragment);
            root.getViewTreeObserver().addOnPreDrawListener(anchor);
            root.addOnAttachStateChangeListener(anchor);
            app.hushgram.extension.shared.diagnostics.HookStatus.counted("Message reaction tray", "native actions root captured");
        } catch (RuntimeException ignored) {
            // Unknown model/layout variants keep native placement.
        }
    }

    private static final class TrayAnchor implements ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {
        final WeakReference<View> root;
        WeakReference<View> tray = new WeakReference<>(null);
        final WeakReference<Object> fragment;
        Rect bubble;
        float shifted, lastTranslation;
        TrayAnchor(View root, Object fragment) {
            this.root = new WeakReference<>(root); this.fragment = new WeakReference<>(fragment);
        }
        @Override public boolean onPreDraw() {
            try { align(); } catch (RuntimeException ignored) { }
            return true;
        }
        private void align() {
            View holder = root.get(), row = tray.get();
            if (holder == null) return;
            if (bubble == null) bubble = HeldMessageBlur.argumentsRect(fragment.get());
            if (row == null) {
                int id = GlassChat.id(holder.getContext(), "reactions_container");
                row = id == 0 ? null : holder.findViewById(id);
                if (row != null) { tray = new WeakReference<>(row); lastTranslation = row.getTranslationX(); }
            }
            if (bubble == null || row == null || !row.isShown() || row.getWidth() <= 0) return;
            if (Math.abs(row.getTranslationX() - lastTranslation) > 0.5f) shifted = 0f;
            Rect viewport = new Rect();
            holder.getWindowVisibleDisplayFrame(viewport);
            int[] location = new int[2]; row.getLocationOnScreen(location);
            int nativeLeft = Math.round(location[0] - shifted);
            int desired = reactionLeft(bubble, viewport, nativeLeft, row.getWidth());
            float delta = desired - location[0];
            if (Math.abs(delta) > 0.5f) {
                row.setTranslationX(row.getTranslationX() + delta);
                app.hushgram.extension.shared.diagnostics.HookStatus.counted("Message reaction tray", "tray aligned to held bubble");
            }
            shifted = desired - nativeLeft;
            lastTranslation = row.getTranslationX();
        }
        @Override public void onViewAttachedToWindow(View view) { }
        @Override public void onViewDetachedFromWindow(View view) {
            if (view.getViewTreeObserver().isAlive()) view.getViewTreeObserver().removeOnPreDrawListener(this);
            view.removeOnAttachStateChangeListener(this);
            View row = tray.get();
            if (row != null && Math.abs(row.getTranslationX() - lastTranslation) <= 0.5f) {
                row.setTranslationX(lastTranslation - shifted);
            }
        }
    }

    static int submenuLeft(Rect anchor, Rect frame, int width, int gap) {
        boolean rightAligned = frame.right - anchor.right < anchor.left - frame.left;
        int left = rightAligned ? anchor.right - width : anchor.left;
        return Math.max(frame.left, insetRight(left, width, frame.right, gap));
    }

    public static void showMore(PopupWindow popup, View host, int gravity, int x, int y) {
        Anchor anchor = latest;
        latest = null;
        Activity activity = activity(host.getContext());
        if (anchor != null && anchor.activity.get() == activity && activity != null && !anchor.bounds.isEmpty()
                && (anchor.detached == 0 || SystemClock.uptimeMillis() - anchor.detached < 1000)) {
            Rect frame = new Rect();
            host.getWindowVisibleDisplayFrame(frame);
            View content = popup.getContentView();
            if (content != null && frame.width() > 0) {
                content.measure(View.MeasureSpec.makeMeasureSpec(frame.width(), View.MeasureSpec.AT_MOST),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                int width = popup.getWidth() > 0 ? popup.getWidth() : content.getMeasuredWidth();
                x = submenuLeft(anchor.bounds, frame, width, edgeGap(content)) - frame.left;
                y = Math.max(0, anchor.bounds.top - frame.top);
                gravity = Gravity.TOP | Gravity.LEFT;
            }
        }
        // Keep native dimensions, animations and overflow behavior; do not cap menu height.
        popup.showAtLocation(host, gravity, x, y);
    }

    private static Activity activity(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            Context next = ((ContextWrapper) context).getBaseContext();
            if (next == context) break;
            context = next;
        }
        return null;
    }

    private static final class Anchor implements ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {
        final WeakReference<View> content;
        final WeakReference<Activity> activity;
        final Rect bounds = new Rect();
        long detached;
        Anchor(View view) {
            content = new WeakReference<>(view);
            activity = new WeakReference<>(activity(view.getContext()));
        }
        @Override public boolean onPreDraw() {
            View view = content.get();
            if (view != null && view.isShown() && view.getWidth() > 0 && view.getHeight() > 0) {
                int[] location = new int[2];
                view.getLocationOnScreen(location);
                bounds.set(location[0], location[1], location[0] + view.getWidth(), location[1] + view.getHeight());
            }
            return true;
        }
        @Override public void onViewAttachedToWindow(View view) { detached = 0; }
        @Override public void onViewDetachedFromWindow(View view) {
            detached = SystemClock.uptimeMillis();
            if (view.getViewTreeObserver().isAlive()) view.getViewTreeObserver().removeOnPreDrawListener(this);
            view.removeOnAttachStateChangeListener(this);
        }
    }
}
