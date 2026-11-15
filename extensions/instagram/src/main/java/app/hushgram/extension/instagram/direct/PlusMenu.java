/*
 * Copyright 2026 HushGram contributors
 * https://github.com/SysAdminDoc/HushGram
 */
package app.hushgram.extension.instagram.direct;

import androidx.annotation.Nullable;
import android.view.View;
import android.view.ViewTreeObserver;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import app.hushgram.extension.instagram.settings.FamilyNames;
import app.hushgram.extension.instagram.settings.Settings;
import app.hushgram.extension.instagram.settings.SettingsStatus;
import app.hushgram.extension.shared.Utils;
import app.hushgram.extension.shared.diagnostics.HookStatus;
import app.hushgram.extension.shared.settings.HushgramPause;

/**
 * Helper for the "Chat plus menu" patch (#448): takes Saved, AI images and Draw off the menu the plus
 * button of a chat's message bar opens, each by its own switch, and can move the gallery button
 * into that menu. The microphone remains on the message bar.
 *
 * <p>Instagram builds a map from each button's enum constant to the item that draws it, then fills
 * the plus menu's list with the items of the constants the menu shows. The patch hands this the
 * finished menu list and that map. A row is told apart by its enum constant's name, read from the
 * map's keys, so no obfuscated name is written here. A moved button is the bar's own item, so a tap
 * runs exactly what the bar's button runs.
 *
 * <p>Fails open: paused, settings not read, a list that can't change or anything thrown leaves the
 * menu as Instagram built it.
 */
public final class PlusMenu {
    /** Instagram's enum names of the menu rows the switches take off. */
    static final String SAVED = "CROSS_APP_SAVES";
    static final String AI_IMAGES = "IMAGINE";
    static final String DRAW = "DOODLES";
    static final String GALLERY = "GALLERY";
    static final String VOICE = "VOICE_MESSAGE";
    static final String LOCATION = "LOCATION";
    /** Verified against 454's native composer enum, separately from IMAGINE and DOODLES. */
    private static final Set<String> META_AI = new HashSet<>(java.util.Arrays.asList(
            "META_AI_DISCOVERY", "META_AI_INVOCATION", "META_AI_VOICE"));

    /** What's counted for each row taken out of, or put into, a menu. */
    static final String REMOVED = "plus menu rows removed";
    static final String MOVED = "plus menu buttons moved";

    /** Whether the patch is in this build, when a test says so instead of {@link SettingsStatus}. */
    @Nullable
    static volatile Boolean inBuildForTests;

    private PlusMenu() {
    }

    private static final Map<View, ButtonBinding> buttons = new WeakHashMap<>();

    /** Filled by the patch with the composer's verified button field chain. */
    public static View button(Object builder) { return null; }

    /** Snapshot names before filtering, so visibility follows settings without retaining native items. */
    public static void configure(@Nullable View view, @Nullable List<?> overflow, @Nullable Map<?, ?> byButton) {
        if (overflow == null || byButton == null) return;
        Set<String> names = new HashSet<>();
        int unknown = 0;
        boolean gallery = false;
        for (Map.Entry<?, ?> entry : byButton.entrySet()) {
            if (entry.getKey() instanceof Enum<?> && GALLERY.equals(((Enum<?>) entry.getKey()).name())
                    && entry.getValue() != null) gallery = true;
        }
        for (Object item : overflow) {
            String name = null;
            for (Map.Entry<?, ?> entry : byButton.entrySet()) {
                if (entry.getValue() == item && entry.getKey() instanceof Enum<?>) {
                    name = ((Enum<?>) entry.getKey()).name();
                    break;
                }
            }
            if (name == null) unknown++; else names.add(name);
        }
        menu(overflow, byButton);
        bind(view, overflow);
        ButtonBinding binding = view == null ? null : buttons.get(view);
        if (binding != null) {
            binding.optionNames = names;
            binding.unknownOptions = unknown;
            binding.galleryAvailable = gallery;
            binding.onPreDraw();
        }
    }

    /** The native plus view is separate from the MORE item in the composer's list. */
    public static void bind(@Nullable View view, @Nullable List<?> overflow) {
        if (view == null || overflow == null) return;
        ButtonBinding binding = buttons.get(view);
        if (binding == null) {
            binding = new ButtonBinding(view);
            buttons.put(view, binding);
            view.addOnAttachStateChangeListener(binding);
            view.getViewTreeObserver().addOnPreDrawListener(binding);
        }
        // Keep only a count: retaining native menu items leaks their controller, while a weak
        // list may be collected after the native builder copies it and loses the empty state.
        binding.optionCount = overflow.size();
        binding.optionNames = null;
        binding.onPreDraw();
    }

    private static final class ButtonBinding implements ViewTreeObserver.OnPreDrawListener,
            View.OnAttachStateChangeListener {
        final WeakReference<View> button;
        int optionCount = -1;
        Set<String> optionNames;
        int unknownOptions;
        boolean galleryAvailable;
        boolean hidden;
        int nativeVisibility;

        ButtonBinding(View view) { button = new WeakReference<>(view); }

        @Override public boolean onPreDraw() {
            View view = button.get();
            if (view == null) return true;
            int remaining = optionCount;
            if (Utils.settingsReady() && !HushgramPause.isPaused() && optionNames != null) {
                remaining = unknownOptions;
                for (String name : optionNames) if (!hiddenOption(name)) remaining++;
                if (galleryAvailable && Settings.MOVE_TO_PLUS_MENU.get() && !optionNames.contains(GALLERY)) remaining++;
            }
            if (hideButton(remaining)) {
                if (!hidden || view.getVisibility() != View.GONE) nativeVisibility = view.getVisibility();
                hidden = true;
                view.setVisibility(View.GONE);
            } else if (hidden) {
                hidden = false;
                if (view.getVisibility() == View.GONE) view.setVisibility(nativeVisibility);
            }
            return true;
        }

        @Override public void onViewAttachedToWindow(View view) {
            ViewTreeObserver observer = view.getViewTreeObserver();
            observer.removeOnPreDrawListener(this);
            observer.addOnPreDrawListener(this);
            onPreDraw();
        }

        @Override public void onViewDetachedFromWindow(View view) {
            if (view.getViewTreeObserver().isAlive()) view.getViewTreeObserver().removeOnPreDrawListener(this);
        }
    }

    static boolean inBuild() {
        Boolean forced = inBuildForTests;
        return forced != null ? forced : SettingsStatus.plusMenu();
    }

    private static boolean hiddenOption(String name) {
        return SAVED.equals(name) && Settings.HIDE_PLUS_SAVED.get()
                || AI_IMAGES.equals(name) && Settings.HIDE_PLUS_AI_IMAGES.get()
                || DRAW.equals(name) && Settings.HIDE_PLUS_DRAW.get()
                || META_AI.contains(name) && Settings.HIDE_PLUS_META_AI.get()
                || LOCATION.equals(name) && Settings.HIDE_PLUS_LOCATION.get();
    }

    /**
     * Injected where the loop that fills the plus menu's list exits, handed that list and the map from
     * button to item. Changes the list in place. Never throws, and never waits for the settings.
     */
    public static void menu(@Nullable List<?> menu, @Nullable Map<?, ?> byButton) {
        if (menu == null || byButton == null) return;
        try {
            if (!Utils.settingsReady() || HushgramPause.isPaused()) return;
            Set<String> out = new HashSet<>();
            if (Settings.HIDE_PLUS_SAVED.get()) out.add(SAVED);
            if (Settings.HIDE_PLUS_AI_IMAGES.get()) out.add(AI_IMAGES);
            if (Settings.HIDE_PLUS_DRAW.get()) out.add(DRAW);
            if (Settings.HIDE_PLUS_META_AI.get()) out.addAll(META_AI);
            if (Settings.HIDE_PLUS_LOCATION.get()) out.add(LOCATION);
            boolean move = Settings.MOVE_TO_PLUS_MENU.get();
            if (out.isEmpty() && !move) return;
            HookStatus.invoked(FamilyNames.PLUS_MENU);
            @SuppressWarnings("unchecked")
            List<Object> rows = (List<Object>) menu;
            Object gallery = null;
            for (Map.Entry<?, ?> entry : byButton.entrySet()) {
                if (!(entry.getKey() instanceof Enum<?>)) continue;
                String name = ((Enum<?>) entry.getKey()).name();
                if (out.contains(name)) {
                    takeOut(rows, entry.getValue());
                } else if (GALLERY.equals(name)) {
                    gallery = entry.getValue();
                }
            }
            if (move) {
                putIn(rows, gallery);
                // The native voice recorder is absent from 454's overflow map and needs its
                // message-bar gestures. Do not advertise a non-working menu replacement.
            }
        } catch (Throwable failure) {
            HookStatus.threw(FamilyNames.PLUS_MENU, "plus menu", failure);
        }
    }

    /**
     * Whether Gallery is meant to be in the menu, so the bar leaves it
     * out. A build without Chat plus menu has no menu to hold them, so they stay on the bar there.
     */
    public static boolean moved() {
        try {
            return Utils.settingsReady() && !HushgramPause.isPaused() && inBuild() && Settings.MOVE_TO_PLUS_MENU.get();
        } catch (Throwable failure) {
            return false;
        }
    }

    /** Native composer availability asks this about this button's own filtered overflow list. */
    public static boolean empty(@Nullable List<?> menu) {
        return hideButton(menu == null ? -1 : menu.size());
    }

    private static boolean hideButton(int optionCount) {
        try {
            return Utils.settingsReady() && !HushgramPause.isPaused() &&
                    (Settings.HIDE_PLUS_BUTTON.get() ||
                    (Settings.HIDE_EMPTY_PLUS_MENU.get() && optionCount == 0));
        } catch (Throwable failure) {
            return false;
        }
    }

    /** Remove the empty MORE item itself, rather than only disabling its availability check. */
    public static List<?> composer(List<?> items) {
        if (!Utils.settingsReady() || HushgramPause.isPaused() || items == null) return items;
        try {
            List<Object> kept = new ArrayList<>(items.size());
            for (Object item : items) if (!emptyMore(item, null)) kept.add(item);
            return kept.size() == items.size() ? items : kept;
        } catch (Throwable failure) {
            return items;
        }
    }

    // Filled by the patch using the verified MORE type and its own overflow field.
    private static boolean emptyMore(Object item, Object scratch) { return false; }

    private static void takeOut(List<Object> rows, @Nullable Object item) {
        if (item == null) return;
        for (Iterator<Object> it = rows.iterator(); it.hasNext(); ) {
            if (it.next() == item) {
                it.remove();
                HookStatus.counted(FamilyNames.PLUS_MENU, REMOVED);
            }
        }
    }

    private static void putIn(List<Object> rows, @Nullable Object item) {
        if (item == null) return;
        for (Object row : rows) {
            if (row == item) return;
        }
        rows.add(item);
        HookStatus.counted(FamilyNames.PLUS_MENU, MOVED);
    }
}
