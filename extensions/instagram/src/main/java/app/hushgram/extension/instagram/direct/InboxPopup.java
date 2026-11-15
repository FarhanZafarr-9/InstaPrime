package app.hushgram.extension.instagram.direct;

import android.graphics.Rect;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.widget.PopupWindow;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/** Shows the existing native inbox menu without the thread-preview overlay. */
public final class InboxPopup {
    private static final Map<View, Entry> pending = new WeakHashMap<>();
    private InboxPopup() { }

    private static final class Entry {
        final WeakReference<PopupWindow> popup;
        final RectF bounds;
        Entry(PopupWindow popup, RectF bounds) {
            this.popup = new WeakReference<>(popup);
            this.bounds = new RectF(bounds);
        }
    }

    public static void prepare(PopupWindow popup, RectF bounds) {
        View content = popup.getContentView();
        if (content == null) return;
        pending.remove(content);
        if (bounds != null && !bounds.isEmpty()) pending.put(content, new Entry(popup, bounds));
    }

    /** False leaves the original native overlay available as a fallback. */
    public static boolean show(View anchor, View content) {
        Entry entry = pending.remove(content);
        PopupWindow popup = entry == null ? null : entry.popup.get();
        if (popup == null || anchor == null || !anchor.isAttachedToWindow()
                || popup.getContentView() != content || content.getParent() != null) return false;
        Rect frame = new Rect();
        anchor.getWindowVisibleDisplayFrame(frame);
        if (frame.isEmpty()) return false;
        int gap = Math.round(8 * content.getResources().getDisplayMetrics().density);
        content.measure(View.MeasureSpec.makeMeasureSpec(Math.max(1, frame.width() - 2 * gap), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        Rect padding = new Rect();
        if (popup.getBackground() != null) popup.getBackground().getPadding(padding);
        int width = popup.getWidth() > 0 ? popup.getWidth() : content.getMeasuredWidth() + padding.left + padding.right;
        int height = popup.getHeight() > 0 ? popup.getHeight() : content.getMeasuredHeight() + padding.top + padding.bottom;
        if (width <= 0 || height <= 0) return false;
        int[] point = position(entry.bounds, frame, width, height, gap);
        View root = anchor.getRootView();
        int[] screen = new int[2], window = new int[2];
        root.getLocationOnScreen(screen);
        root.getLocationInWindow(window);
        try {
            if (popup.getWidth() == 0) popup.setWidth(width);
            if (popup.getHeight() == 0) popup.setHeight(height);
            popup.setFocusable(true);
            popup.setOutsideTouchable(true);
            popup.showAtLocation(anchor, Gravity.TOP | Gravity.LEFT,
                    point[0] - screen[0] + window[0], point[1] - screen[1] + window[1]);
            return true;
        } catch (RuntimeException failure) {
            app.hushgram.extension.shared.Logger.printException(() -> "Could not show inbox popup", failure);
            return false;
        }
    }

    static int[] position(RectF row, Rect frame, int width, int height, int gap) {
        int x = Math.max(frame.left + gap, Math.min(Math.round(row.left), frame.right - width - gap));
        int below = Math.round(row.bottom) + gap;
        int above = Math.round(row.top) - height - gap;
        int y = below + height <= frame.bottom - gap ? below
                : above >= frame.top + gap ? above
                : Math.max(frame.top + gap, Math.min(below, frame.bottom - height - gap));
        return new int[]{x, y};
    }
}
