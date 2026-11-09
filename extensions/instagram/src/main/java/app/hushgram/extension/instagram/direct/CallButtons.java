/*
 * Copyright 2026 HushGram contributors
 * https://github.com/SysAdminDoc/HushGram
 */
package app.hushgram.extension.instagram.direct;

import android.view.View;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

import app.hushgram.extension.instagram.settings.FamilyNames;
import app.hushgram.extension.instagram.settings.Settings;
import app.hushgram.extension.shared.Utils;
import app.hushgram.extension.shared.diagnostics.HookStatus;
import app.hushgram.extension.shared.settings.HushgramPause;

/**
 * Hides selected call buttons and Blend from the chat header using the "Ask before a call" patch.
 *
 * <p>Instagram 452 draws the buttons at the right of a chat's header from a list of button models,
 * each with a kind (an enum: {@code VIDEO_CALL}, {@code AUDIO_CALL}, the {@code COMBINED_CALL_*}
 * pills, {@code COLLAPSED_CALL} and the two AI call kinds). Two methods of the header's binder fill
 * a view from its model, one for the pill buttons and one for the icon buttons, and the patch hands
 * each of them the view and the model through {@link #bound} first thing. A view whose model is
 * hidden by its setting goes GONE, giving its room to the name. A button
 * that's on a call still in progress is another kind and stays.
 *
 * <p>A view hidden here is remembered, weakly, so that the next bind with the switch off (or HushGram
 * paused) gives it back as Instagram had it: visible. A kind Instagram adds later that isn't named
 * here is never touched.
 *
 * <p>Fails open: with the switch off, HushGram paused, the settings not read yet or anything thrown,
 * the view is left as Instagram binds it.
 */
public final class CallButtons {
    /** The model kinds that start a call, as the names of Instagram's button enum. */
    public static final Set<String> CALL_KINDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "VIDEO_CALL", "AUDIO_CALL",
            "COMBINED_CALL_V1", "COMBINED_CALL_V2", "COMBINED_CALL_V3", "COMBINED_CALL_V4",
            "COLLAPSED_CALL", "AI_VIDEO_CALL", "AI_AUDIO_CALL")));

    /** What's counted for each call button taken off a header. */
    static final String HIDDEN = "call buttons hidden";

    /** The one stub, which tests swap for a stand-in. */
    interface Kinds {
        String kindOf(Object model);
    }

    static final Kinds PATCHED = CallButtons::kindOf;
    static volatile Kinds access = PATCHED;

    /** The views this hid, so a later bind with the switch off can show them again. Main thread only. */
    private static final WeakHashMap<View, Boolean> hidden = new WeakHashMap<>();

    private CallButtons() {
    }

    /**
     * Rewritten by the patch to answer the name of the kind of Instagram's button model [model].
     * Never called as written.
     */
    public static String kindOf(Object model) {
        return null;
    }

    /**
     * Injected at the start of both methods of the header's binder that fill a button from its
     * model. Never throws, and never waits for the settings.
     */
    public static void bound(View view, Object model) {
        try {
            HookStatus.invoked(FamilyNames.ASK_BEFORE_CALL);
            if (view == null) return;
            String kind = model == null ? null : access.kindOf(model);
            // A recycled view this hid can come back bound to another kind of button, or none, which shows again.
            boolean hide = kind != null && Utils.settingsReady() && !HushgramPause.isPaused() &&
                    (hideCall(kind) ||
                            (("BLEND".equals(kind) || "BLEND_ADD".equals(kind)) && Settings.HIDE_BLEND_BUTTON.get()));
            if (hide) {
                view.setVisibility(View.GONE);
                hidden.put(view, Boolean.TRUE);
                HookStatus.counted(FamilyNames.ASK_BEFORE_CALL, HIDDEN);
            } else if (hidden.remove(view) != null) {
                view.setVisibility(View.VISIBLE);
            }
        } catch (Throwable failure) {
            HookStatus.threw(FamilyNames.ASK_BEFORE_CALL, "call buttons", failure);
        }
    }

    private static boolean hideCall(String kind) {
        if (!CALL_KINDS.contains(kind)) return false;
        if (Settings.HIDE_CALL_BUTTONS.get()) return true;
        switch (kind) {
            case "VIDEO_CALL":
            case "AI_VIDEO_CALL":
                return Settings.HIDE_VIDEO_CALL_BUTTON.get();
            case "AUDIO_CALL":
            case "AI_AUDIO_CALL":
                return Settings.HIDE_AUDIO_CALL_BUTTON.get();
            default:
                // A combined or collapsed control can still offer the other call type.
                return Settings.HIDE_VIDEO_CALL_BUTTON.get() && Settings.HIDE_AUDIO_CALL_BUTTON.get();
        }
    }
}
