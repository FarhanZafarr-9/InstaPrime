/*
 * Copyright 2026 HushGram contributors
 * https://github.com/SysAdminDoc/HushGram
 */
package app.hushgram.extension.instagram.direct;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

import app.hushgram.extension.instagram.settings.Settings;
import app.hushgram.extension.instagram.settings.SettingsStatus;
import app.hushgram.extension.shared.Utils;
import app.hushgram.extension.shared.settings.HushgramPause;

/** Opt-in message taps reuse the native long-press menu, including its availability checks. */
public final class MessageGestures {
    private static final class Scope {
        final int mode;
        final float x, y;
        boolean built;
        boolean visible;
        Scope(int mode) { this.mode = mode; x = touchX; y = touchY; }
    }
    private static float touchX, touchY;
    private static long touchTime;
    private static final class EmptyMenu extends RuntimeException {
        EmptyMenu() { super(null, null, false, false); }
    }
    private static final ThreadLocal<Scope> opening = new ThreadLocal<>();
    @Nullable static volatile Boolean inBuildForTests;
    @Nullable static volatile Bridge bridgeForTests;

    interface Bridge {
        boolean open(Object delegate);
    }

    private MessageGestures() { }

    /** Media cards keep their native tap target, including the space surrounding a shared reel. */
    public static boolean intercepts(@Nullable String messageType) {
        return "text".equals(messageType);
    }

    /** Passive observation: never consumes an event or replaces a native touch listener. */
    public static void observe(android.view.MotionEvent event) {
        if (event == null) return;
        int action = event.getActionMasked();
        if (action == android.view.MotionEvent.ACTION_UP && event.getPointerCount() == 1) {
            touchX = event.getRawX(); touchY = event.getRawY();
            touchTime = android.os.SystemClock.uptimeMillis();
        } else if (action == android.view.MotionEvent.ACTION_DOWN || action == android.view.MotionEvent.ACTION_CANCEL
                || action == android.view.MotionEvent.ACTION_POINTER_DOWN) touchTime = 0;
    }

    public static float x() { Scope scope = opening.get(); return scope == null ? 0 : scope.x; }
    public static float y() { Scope scope = opening.get(); return scope == null ? 0 : scope.y; }

    /** Runs before the completed native model leaves its factory, before any backdrop is attached. */
    public static void requireVisible() {
        Scope scope = opening.get();
        if (scope != null && !scope.visible) throw new EmptyMenu();
    }

    /** Returns false for normal taps, pause, unsupported messages or an unavailable native menu. */
    public static boolean tap(@Nullable Object delegate) {
        if (delegate == null) return false;
        try {
            if (!Utils.settingsReady() || HushgramPause.isPaused()
                    || !(inBuildForTests != null ? inBuildForTests : SettingsStatus.messageGestures())) return false;
            int mode = (Settings.MESSAGE_TAP_ACTIONS.get() ? 1 : 0)
                    | (Settings.MESSAGE_TAP_REACTIONS.get() ? 2 : 0);
            if (mode == 0) return false;
            if (bridgeForTests == null && (touchTime == 0
                    || android.os.SystemClock.uptimeMillis() - touchTime > 700)) return false;
            Scope previous = opening.get();
            Scope scope = new Scope(mode);
            opening.set(scope);
            try {
                Bridge bridge = bridgeForTests;
                boolean opened = bridge != null ? bridge.open(delegate) : openNative(delegate);
                return opened && scope.built;
            } catch (EmptyMenu unavailable) {
                return true; // Consume the requested tap without opening a blank native backdrop.
            } finally {
                if (previous == null) opening.remove(); else opening.set(previous);
            }
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Scoped to menu creation; long presses retain their native reactions and availability. */
    public static boolean reactions(boolean available) {
        Scope scope = opening.get();
        boolean shown = available && (scope == null || (scope.mode & 2) != 0);
        if (scope != null) { scope.built = true; scope.visible |= shown; }
        return shown;
    }

    /** Called by the native bridge without mutating Instagram's original list. */
    public static List<?> actions(List<?> nativeActions) {
        Scope scope = opening.get();
        boolean shown = scope == null || (scope.mode & 1) != 0;
        if (scope != null) { scope.built = true; scope.visible |= shown && !nativeActions.isEmpty(); }
        return shown ? nativeActions : Collections.emptyList();
    }

    // Replaced by the bytecode patch with verified native fields and methods.
    private static boolean openNative(Object delegate) { return false; }
}
