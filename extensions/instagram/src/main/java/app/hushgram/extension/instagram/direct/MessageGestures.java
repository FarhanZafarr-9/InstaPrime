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
        boolean built;
        Scope(int mode) { this.mode = mode; }
    }
    private static final ThreadLocal<Scope> opening = new ThreadLocal<>();
    @Nullable static volatile Boolean inBuildForTests;
    @Nullable static volatile Bridge bridgeForTests;

    interface Bridge {
        boolean open(Object delegate);
    }

    private MessageGestures() { }

    /** Returns false for normal taps, pause, unsupported messages or an unavailable native menu. */
    public static boolean tap(@Nullable Object delegate) {
        if (delegate == null) return false;
        try {
            if (!Utils.settingsReady() || HushgramPause.isPaused()
                    || !(inBuildForTests != null ? inBuildForTests : SettingsStatus.messageGestures())) return false;
            int mode = (Settings.MESSAGE_TAP_ACTIONS.get() ? 1 : 0)
                    | (Settings.MESSAGE_TAP_REACTIONS.get() ? 2 : 0);
            if (mode == 0) return false;
            Scope previous = opening.get();
            Scope scope = new Scope(mode);
            opening.set(scope);
            try {
                Bridge bridge = bridgeForTests;
                boolean opened = bridge != null ? bridge.open(delegate) : openNative(delegate);
                return opened && scope.built;
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
        if (scope != null) scope.built = true;
        return available && (scope == null || (scope.mode & 2) != 0);
    }

    /** Called by the native bridge without mutating Instagram's original list. */
    public static List<?> actions(List<?> nativeActions) {
        Scope scope = opening.get();
        if (scope != null) scope.built = true;
        return scope == null || (scope.mode & 1) != 0 ? nativeActions : Collections.emptyList();
    }

    // Replaced by the bytecode patch with verified native fields and methods.
    private static boolean openNative(Object delegate) { return false; }
}
