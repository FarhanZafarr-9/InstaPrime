/*
 * Copyright 2026 Hushfacebook contributors
 * https://github.com/SysAdminDoc/Hushfacebook
 *
 * Modified for HushGram (Instagram), 2026.
 */
package app.hushgram.extension.instagram.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import app.hushgram.extension.shared.L10n;
import app.hushgram.extension.shared.settings.Setting;

/**
 * Badges the top-left of icons for unseen settings and their navigation ancestors.
 * A first open seeds the seen set. Leaving a leaf marks only that leaf's rows;
 * visiting a category overview never clears unseen settings in its children.
 * Seen keys stay local and are excluded from settings backups.
 */
final class NewRowDots {
    static final String FILE = "hushgram_new_dots";
    static final String SEEN = "seen";
    /** Identifies a badged icon without sharing a tag with the title or info button. */
    static final String TAG = "hushgram_new_dot";

    /** The page showing now, which the rows ask while they bind. Null when there is none. */
    @Nullable private static volatile NewRowDots current;

    private final SharedPreferences store;
    private final Set<String> seen = new HashSet<>();
    /** Each navigation opener and all saved rows below that node. */
    private final Map<Preference, List<Preference>> openers = new HashMap<>();
    private final Map<PreferenceCategory, List<Preference>> pages;
    /** A search lists rows from every category, so none of them is shown as new there. */
    private boolean searching;

    /** Reads what's been seen, or marks everything in [pages] seen when nothing is stored yet. */
    private NewRowDots(Context context, Map<PreferenceCategory, List<Preference>> pages) {
        this.pages = pages;
        store = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
        Set<String> stored = null;
        try {
            stored = store.getStringSet(SEEN, null);
        } catch (RuntimeException unreadable) {
            // A value of another kind is no set: start again, as on a first open.
        }
        if (stored != null) {
            seen.addAll(stored);
            return;
        }
        for (List<Preference> rows : pages.values()) addKeys(rows);
        save();
    }

    /** Starts the dots for a page built from [pages], or none when that can't be done. */
    static void start(Context context, Map<PreferenceCategory, List<Preference>> pages) {
        try {
            current = new NewRowDots(context, pages);
        } catch (RuntimeException unavailable) {
            current = null;
        }
    }

    /** The page is gone: its rows no longer ask. */
    static void stop() {
        current = null;
    }

    /** Remembers which category [opener] opens, so it shows a dot while that category has a new row. */
    static void opens(Preference opener, PreferenceCategory category) {
        NewRowDots dots = current;
        if (dots != null) dots.openers.put(opener, dots.pages.get(category));
    }

    static void opens(Preference opener, SettingsNavigation.Node node) {
        NewRowDots dots = current;
        if (dots != null) {
            java.util.ArrayList<Preference> rows = new java.util.ArrayList<>();
            descendants(node, rows);
            dots.openers.put(opener, rows);
        }
    }

    private static void descendants(SettingsNavigation.Node node, List<Preference> rows) {
        rows.addAll(node.rows);
        for (SettingsNavigation.Node child : node.children) descendants(child, rows);
    }

    static void leftRows(List<Preference> rows) {
        try {
            NewRowDots dots = current;
            if (dots != null && !dots.searching) dots.leave(rows);
        } catch (RuntimeException ignored) { }
    }

    static boolean freshRow(Preference row) {
        NewRowDots dots = current;
        return dots != null && dots.fresh(row);
    }

    /** Whether a search is on, which shows no dots. */
    static void searching(boolean on) {
        NewRowDots dots = current;
        if (dots != null) dots.searching = on;
    }

    /** The person has been through [category]'s page: its rows are no longer new. */
    static void left(PreferenceCategory category) {
        try {
            NewRowDots dots = current;
            if (dots == null || dots.searching) return;
            List<Preference> rows = dots.pages.get(category);
            if (rows != null) dots.leave(rows);
        } catch (RuntimeException ignored) {
            // The dot stays a visit longer.
        }
    }

    /** The settings are closing with every row on one list (or none open): all that was listed is seen. */
    static void leftEverything() {
        try {
            NewRowDots dots = current;
            if (dots == null || dots.searching) return;
            for (List<Preference> rows : dots.pages.values()) dots.leave(rows);
        } catch (RuntimeException ignored) {
            // The dots stay a visit longer.
        }
    }

    private static boolean counts(Preference row) {
        String key = row.getKey();
        return key != null && Setting.getSettingFromPath(key) != null;
    }

    private void addKeys(List<Preference> rows) {
        for (Preference row : rows) if (counts(row)) seen.add(row.getKey());
    }

    private void leave(List<Preference> rows) {
        int before = seen.size();
        addKeys(rows);
        if (seen.size() != before) save();
    }

    private void save() {
        try {
            store.edit().putStringSet(SEEN, new HashSet<>(seen)).apply();
        } catch (RuntimeException ignored) {
            // The dot comes back next time.
        }
    }

    private boolean isNew(Preference row) {
        return counts(row) && !seen.contains(row.getKey());
    }

    private boolean fresh(Preference row) {
        if (searching) return false;
        if (row instanceof HushgramPreferenceFragment.Heading
                && ((HushgramPreferenceFragment.Heading) row).expanded) return false;
        List<Preference> rows = openers.get(row);
        if (rows != null) {
            if (rows == null) return false;
            for (Preference each : rows) if (isNew(each)) return true;
            return false;
        }
        return isNew(row);
    }

    /** Paints [row]'s dot, or takes the old one off, as the page that is showing says. */
    static void paint(View row, Preference preference, int accent) {
        boolean fresh = false;
        try {
            NewRowDots dots = current;
            fresh = dots != null && dots.fresh(preference);
        } catch (RuntimeException failed) {
            // No dot.
        }
        paint(row, fresh, accent);
    }

    /** The badge belongs to the icon; title actions remain independent. */
    private static void paint(View row, boolean fresh, int accent) {
        android.widget.ImageView icon = row.findViewById(android.R.id.icon);
        TextView title = row.findViewById(android.R.id.title);
        if (icon == null) return;
        android.graphics.drawable.Drawable base = icon.getDrawable();
        if (base instanceof BadgedIcon) base = ((BadgedIcon) base).base;
        if (base != null) icon.setImageDrawable(fresh ? new BadgedIcon(base, accent) : base);
        icon.setTag(fresh ? TAG : null);
        if (title != null) title.setContentDescription(fresh ? title.getText() + ", " + L10n.t("New") : null);
    }

    static final class BadgedIcon extends android.graphics.drawable.Drawable {
        final android.graphics.drawable.Drawable base;
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final int accent;
        BadgedIcon(android.graphics.drawable.Drawable base, int accent) { this.base = base; this.accent = accent; }
        @Override public void draw(android.graphics.Canvas canvas) {
            base.setBounds(getBounds());
            base.draw(canvas);
            android.graphics.Rect b = getBounds();
            float radius = Math.min(b.width(), b.height()) / 6f;
            paint.setColor(ScreenColors.current().card);
            canvas.drawCircle(b.left + radius, b.top + radius, radius + radius / 3f, paint);
            paint.setColor(accent);
            canvas.drawCircle(b.left + radius, b.top + radius, radius, paint);
        }
        @Override public int getIntrinsicWidth() { return base.getIntrinsicWidth(); }
        @Override public int getIntrinsicHeight() { return base.getIntrinsicHeight(); }
        @Override public void setAlpha(int alpha) { base.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { base.setColorFilter(filter); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }
}
