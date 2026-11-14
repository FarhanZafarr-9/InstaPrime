/* Copyright 2026 HushGram contributors */
package app.hushgram.extension.instagram.direct;

import android.content.Context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import app.hushgram.extension.instagram.settings.FamilyNames;
import app.hushgram.extension.instagram.settings.Settings;
import app.hushgram.extension.shared.Utils;
import app.hushgram.extension.shared.L10n;
import app.hushgram.extension.shared.diagnostics.HookStatus;
import app.hushgram.extension.shared.settings.HushgramPause;

/** Repositions Instagram's own available action objects, retaining their native handlers. */
public final class MessageActions {
    public static final List<String> OFFERED = Collections.unmodifiableList(Arrays.asList(
            "REPLY", "FORWARD", "COPY_TEXT", "TRANSLATE_MESSAGE", "REPORT", "UNSEND", "EDIT",
            "PIN", "UNPIN", "SAVE_MEDIA", "SHARE", "SELECT_TEXT", "REIMAGINE", "IMAGINE_THIS",
            "EDIT_WITH_AI_FOR_IMAGE", "RESTYLE", "ASK_META_AI", "SUMMARIZE_WITH_AI", "META_AI", "MORE"));

    private MessageActions() { }

    /** Runs only after the native factory has finished determining availability and submenus. */
    public static void menu(Object model, Context context) {
        try {
            if (!Utils.settingsReady() || HushgramPause.isPaused()) return;
            Set<String> hidden = parse(Settings.HIDE_MESSAGE_ACTIONS.get());
            Set<String> moved = parse(Settings.MORE_MESSAGE_ACTIONS.get());
            moved.remove("MORE");
            moved.removeAll(hidden);
            List<?> first = primary(model), overflow = more(model), third = ai(model);
            if (first == null || overflow == null || third == null) return;
            HookStatus.invoked(FamilyNames.MESSAGE_ACTIONS);
            Lists rows = rearrange(first, overflow, third, hidden, moved, MessageActions::action);
            if (!rows.more.isEmpty() && !hidden.contains("MORE") && !has(rows.primary, "MORE", MessageActions::action)) {
                Object button = makeMore(context, null, null, null, null, null, null, null, null, null, null, null, null);
                if (button == null) return; // Never strand an action if the native bridge is unavailable.
                rows.primary.add(button);
            }
            replace(model, rows.primary, rows.more, rows.ai);
        } catch (Throwable failure) {
            HookStatus.threw(FamilyNames.MESSAGE_ACTIONS, "message actions", failure);
        }
    }

    interface Kind { Enum<?> of(Object row); }
    static final class Lists {
        final List<Object> primary, more, ai;
        Lists(List<Object> primary, List<Object> more, List<Object> ai) {
            this.primary = primary; this.more = more; this.ai = ai;
        }
    }

    /** Copies first; no change reaches the native model until every operation succeeds. */
    static Lists rearrange(List<?> first, List<?> overflow, List<?> third,
                           Set<String> hidden, Set<String> moved, Kind kind) {
        List<Object> main = new ArrayList<>(), extra = new ArrayList<>(), ai = new ArrayList<>();
        for (Object row : overflow) if (!hidden.contains(name(row, kind))) extra.add(row);
        for (Object row : first) {
            String name = name(row, kind);
            if (hidden.contains(name)) continue;
            if (!"MORE".equals(name) && moved.contains(name)) extra.add(row);
            else main.add(row);
        }
        for (Object row : third) {
            String name = name(row, kind);
            if (hidden.contains(name)) continue;
            if (moved.contains(name)) extra.add(row);
            else ai.add(row);
        }
        if (ai.isEmpty()) {
            main.removeIf(row -> "META_AI".equals(name(row, kind)));
            extra.removeIf(row -> "META_AI".equals(name(row, kind)));
        }
        if (extra.isEmpty()) main.removeIf(row -> "MORE".equals(name(row, kind)));
        return new Lists(main, extra, ai);
    }

    private static String name(Object row, Kind kind) {
        Enum<?> value = kind.of(row);
        return value == null ? "" : value.name();
    }
    private static boolean has(List<?> rows, String wanted, Kind kind) {
        for (Object row : rows) if (wanted.equals(name(row, kind))) return true;
        return false;
    }
    public static Set<String> parse(String saved) {
        Set<String> names = new LinkedHashSet<>();
        if (saved != null) for (String part : saved.split(",")) if (OFFERED.contains(part.trim())) names.add(part.trim());
        return names;
    }
    public static String join(Set<String> selected) {
        List<String> names = new ArrayList<>();
        for (String name : OFFERED) if (selected.contains(name)) names.add(name);
        return String.join(",", names);
    }

    public static String label(String name) {
        switch (name) {
            case "REPLY": return L10n.t("Reply");
            case "FORWARD": return L10n.t("Forward");
            case "COPY_TEXT": return L10n.t("Copy text");
            case "TRANSLATE_MESSAGE": return L10n.t("Translate");
            case "REPORT": return L10n.t("Report");
            case "UNSEND": return L10n.t("Unsend");
            case "EDIT": return L10n.t("Edit");
            case "PIN": return L10n.t("Pin");
            case "UNPIN": return L10n.t("Unpin");
            case "SAVE_MEDIA": return L10n.t("Save media");
            case "SHARE": return L10n.t("Share");
            case "SELECT_TEXT": return L10n.t("Select text");
            case "REIMAGINE": return L10n.t("Reimagine");
            case "IMAGINE_THIS": return L10n.t("Make AI image");
            case "EDIT_WITH_AI_FOR_IMAGE": return L10n.t("Edit image with AI");
            case "RESTYLE": return L10n.t("Restyle");
            case "ASK_META_AI": return L10n.t("Ask Meta AI");
            case "SUMMARIZE_WITH_AI": return L10n.t("Summarize with AI");
            case "META_AI": return L10n.t("Meta AI menu");
            case "MORE": return L10n.t("More");
            default: return name;
        }
    }

    // The patch fills these readers with typed native field accesses, never reflective name guesses.
    private static List<?> primary(Object model) { return null; }
    private static List<?> more(Object model) { return null; }
    private static List<?> ai(Object model) { return null; }
    private static Enum<?> action(Object row) { return null; }
    private static void replace(Object model, List<?> first, List<?> overflow, List<?> third) { }

    /** Parameters reserve contiguous registers for the exact native constructor inserted by the patch. */
    private static Object makeMore(Context context, Object a, Object b, Object c, Object d, Object e,
                                   Object f, Object g, Object h, Object i, Object j, Object k, Object l) {
        return null;
    }
}
