package app.hushgram.extension.instagram.settings;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/** Keeps long dialog lists/messages from taking over the title and action area. */
final class DialogContentBounds extends FrameLayout {
    private final Rect visibleFrame = new Rect();

    private DialogContentBounds(View content) {
        super(content.getContext());
        if (content instanceof android.widget.ListView) {
            int titleGap = Math.round(8 * getResources().getDisplayMetrics().density);
            setPadding(0, titleGap, 0, 0);
        }
    }

    static void limit(View content) {
        if (content == null || content.getVisibility() == View.GONE || !(content.getParent() instanceof ViewGroup)
                || content.getParent() instanceof DialogContentBounds) return;
        ViewGroup parent = (ViewGroup) content.getParent();
        int at = parent.indexOfChild(content);
        ViewGroup.LayoutParams size = content.getLayoutParams();
        parent.removeViewAt(at);
        DialogContentBounds bounded = new DialogContentBounds(content);
        parent.addView(bounded, at, size);
        bounded.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        getWindowVisibleDisplayFrame(visibleFrame);
        int available = visibleFrame.height();
        if (available <= 0) available = getResources().getDisplayMetrics().heightPixels;
        int maximum = Math.max(1, Math.round(available * 0.55f));
        if (MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED) {
            maximum = Math.min(maximum, MeasureSpec.getSize(heightSpec));
        }
        super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(maximum, MeasureSpec.AT_MOST));
    }
}
