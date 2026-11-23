/* Copyright 2026 InstaPrime contributors */
package app.hushgram.extension.instagram.direct;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import app.hushgram.extension.instagram.download.TextGrab;
import app.hushgram.extension.instagram.misc.AccentTheme;
import app.hushgram.extension.shared.diagnostics.HookStatus;

/** Scope recoloring to an opened chat's native pinned strip and solid input pill. */
public final class ChatAccentSurfaces {
    static final String PINNED = "pinned_message_container";
    private static final Map<View, WeakReference<Watcher>> watchers = new WeakHashMap<>();
    private static final Map<View, Tint> tints = new WeakHashMap<>();
    private static final Map<ImageView, ImageTint> imageTints = new WeakHashMap<>();
    private ChatAccentSurfaces() { }

    public static void follow(View header) {
        WeakReference<Watcher> previous = watchers.get(header);
        if (previous != null && previous.get() != null) return;
        Watcher watcher = new Watcher(header);
        watchers.put(header, new WeakReference<>(watcher));
        HookStatus.counted("Chat accent surfaces", "header watcher attached");
        header.addOnAttachStateChangeListener(watcher);
        if (header.isAttachedToWindow()) watcher.onViewAttachedToWindow(header);
    }

    private static Integer solid(Drawable drawable) {
        if (drawable instanceof ColorDrawable) return ((ColorDrawable) drawable).getColor();
        if (drawable instanceof GradientDrawable) {
            GradientDrawable shape = (GradientDrawable) drawable;
            return shape.getColors() == null && shape.getColor() != null && !shape.getColor().isStateful()
                    ? shape.getColor().getDefaultColor() : null;
        }
        if (drawable instanceof ShapeDrawable && ((ShapeDrawable) drawable).getPaint().getShader() == null) {
            return ((ShapeDrawable) drawable).getPaint().getColor();
        }
        return null;
    }

    private static Drawable fill(Drawable drawable, int depth) {
        if (depth > 4) return null;
        if (solid(drawable) != null) return drawable;
        if (drawable instanceof InsetDrawable) return fill(((InsetDrawable) drawable).getDrawable(), depth + 1);
        if (drawable instanceof LayerDrawable) {
            LayerDrawable layers = (LayerDrawable) drawable;
            for (int i = 0; i < layers.getNumberOfLayers(); i++) {
                if (layers.getId(i) == android.R.id.mask) continue;
                Drawable candidate = fill(layers.getDrawable(i), depth + 1);
                Integer color = solid(candidate);
                if (color != null && color >>> 24 == 0xff) return candidate;
            }
        }
        return null;
    }

    public static void paint(View view, boolean raised) {
        if (view == null) return;
        int color = AccentTheme.chatSurfaceShade(view.getContext(), raised);
        if (raised && view instanceof ImageView && ((ImageView) view).getDrawable() != null) {
            paintImage((ImageView) view, color);
            return;
        }
        Tint previous = tints.get(view);
        if (previous != null && view.getBackground() == previous.painted
                && !Integer.valueOf(previous.color).equals(solid(fill(previous.painted, 0)))) {
            tints.remove(view);
            previous = null; // Instagram changed this drawable in place for a different chat theme.
        }
        Drawable original = previous != null && view.getBackground() == previous.painted
                ? previous.original : view.getBackground();
        Integer nativeColor = solid(fill(original, 0));
        if (nativeColor == null || nativeColor >>> 24 != 0xff) return;
        if (color == 0 || color == nativeColor) {
            if (previous != null && view.getBackground() == previous.painted) view.setBackground(original);
            tints.remove(view);
            return;
        }
        if (previous != null && view.getBackground() == previous.painted && previous.color == color) return;
        Drawable.ConstantState state = original.getConstantState();
        if (state == null) return;
        Drawable copy = state.newDrawable(view.getResources()).mutate();
        if (copy == original) return;
        Drawable content = fill(copy, 0);
        if (content instanceof ColorDrawable) ((ColorDrawable) content).setColor(color);
        else if (content instanceof GradientDrawable) ((GradientDrawable) content).setColor(color);
        else if (content instanceof ShapeDrawable) ((ShapeDrawable) content).getPaint().setColor(color);
        else return;
        tints.put(view, new Tint(original, copy, color));
        view.setBackground(copy);
        HookStatus.counted("Chat accent surfaces", raised ? "input background recolored" : "pinned strip recolored");
    }

    private static void restore(View view) {
        if (view instanceof ImageView) paintImage((ImageView) view, 0);
        Tint tint = tints.remove(view);
        if (tint != null && view.getBackground() == tint.painted) view.setBackground(tint.original);
    }

    /** The live input background is an IgImageView's image; tint only that dedicated surface. */
    private static void paintImage(ImageView view, int color) {
        ImageTint prior = imageTints.get(view);
        if (color == 0 || app.hushgram.extension.instagram.feed.GlassTopBar.boxShowing(view)) {
            if (prior != null) {
                if (view.getImageTintList() == prior.applied) {
                    view.setImageTintList(prior.original);
                    view.setImageTintMode(prior.mode);
                }
                imageTints.remove(view);
            }
            return;
        }
        if (prior != null && prior.color == color && view.getImageTintList() == prior.applied) return;
        ColorStateList original = prior == null ? view.getImageTintList() : prior.original;
        PorterDuff.Mode mode = prior == null ? view.getImageTintMode() : prior.mode;
        ColorStateList applied = ColorStateList.valueOf(color);
        imageTints.put(view, new ImageTint(original, mode, applied, color));
        view.setImageTintMode(PorterDuff.Mode.SRC_IN);
        view.setImageTintList(applied);
        HookStatus.counted("Chat accent surfaces", "input image tinted");
    }

    private static final class ImageTint {
        final ColorStateList original, applied;
        final PorterDuff.Mode mode;
        final int color;
        ImageTint(ColorStateList original, PorterDuff.Mode mode, ColorStateList applied, int color) {
            this.original = original; this.mode = mode; this.applied = applied; this.color = color;
        }
    }

    private static final class Tint {
        final Drawable original, painted;
        final int color;
        Tint(Drawable original, Drawable painted, int color) {
            this.original = original; this.painted = painted; this.color = color;
        }
    }

    private static final class Watcher implements View.OnAttachStateChangeListener, ViewTreeObserver.OnPreDrawListener {
        final WeakReference<View> header;
        WeakReference<View> root = new WeakReference<>(null), pinned = new WeakReference<>(null), pill = new WeakReference<>(null);
        long scannedAt = -250L;
        Watcher(View header) { this.header = new WeakReference<>(header); }
        @Override public void onViewAttachedToWindow(View view) {
            View decor = view.getRootView();
            root = new WeakReference<>(decor);
            decor.getViewTreeObserver().addOnPreDrawListener(this);
        }
        @Override public void onViewDetachedFromWindow(View view) {
            View decor = root.get();
            if (decor != null && decor.getViewTreeObserver().isAlive()) decor.getViewTreeObserver().removeOnPreDrawListener(this);
            if (pinned.get() != null) restore(pinned.get());
            if (pill.get() != null) restore(pill.get());
            pinned.clear(); pill.clear(); root.clear(); scannedAt = -250L;
        }
        @Override public boolean onPreDraw() {
            View owner = header.get(), decor = root.get();
            if (owner == null || decor == null || !owner.isShown()) return true;
            try {
                long now = SystemClock.uptimeMillis();
                if (now - scannedAt >= 250L) {
                    scannedAt = now;
                    Activity activity = TextGrab.ownerOf(owner, null);
                    if (activity == null) return true;
                    View host = GlassChat.messageHost(activity, decor);
                    if (host == null) return true;
                    if (host.getId() == GlassChat.id(activity, GlassChat.COMPOSE_MESSAGES)) {
                        HookStatus.counted("Chat accent surfaces", "visible Compose message host");
                    }
                    int pinId = GlassChat.id(activity, PINNED);
                    View nextPin = pinId == 0 ? null : decor.findViewById(pinId);
                    View nextPill = GlassChat.findMessageBox(activity);
                    if (pinned.get() != null && pinned.get() != nextPin) restore(pinned.get());
                    if (pill.get() != null && pill.get() != nextPill) restore(pill.get());
                    pinned = new WeakReference<>(nextPin); pill = new WeakReference<>(nextPill);
                }
                View pin = pinned.get(), input = pill.get();
                if (pin != null && pin.isShown()) paint(pin, false);
                if (input != null && input.isShown()) paint(input, true);
            } catch (RuntimeException failure) {
                HookStatus.threw("Chat accent surfaces", "surface refresh", failure);
            }
            return true;
        }
    }
}
