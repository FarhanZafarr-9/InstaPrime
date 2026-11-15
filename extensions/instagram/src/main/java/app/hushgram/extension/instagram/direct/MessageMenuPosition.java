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
                x = Math.max(frame.left, Math.min(anchor.bounds.left, frame.right - width)) - frame.left;
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
