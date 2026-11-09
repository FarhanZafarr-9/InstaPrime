/*
 * Copyright 2026 HushGram contributors
 * https://github.com/SysAdminDoc/HushGram
 */
package app.hushgram.extension.instagram.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.preference.Preference;
import android.text.TextUtils;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import app.hushgram.extension.shared.L10n;
import app.hushgram.extension.shared.settings.BooleanSetting;
import java.util.LinkedHashMap;
import java.util.Map;

/** A snapshot of the saved settings reachable from a category. */
final class SettingsOverview {
    private static final class Counts {
        int on;
        int off;
        int other;

        void add(Preference row) {
            BooleanSetting setting = SettingsNavigation.savedToggle(row);
            if (setting == null) other++;
            else if (setting.savedValue()) on++;
            else off++;
        }

        void add(Counts counts) {
            on += counts.on;
            off += counts.off;
            other += counts.other;
        }

        CharSequence summary() {
            return L10n.f("%1$d settings · %2$d on · %3$d off · %4$d other", on + off + other, on, off, other);
        }
    }

    static AlertDialog.Builder dialog(Context context, SettingsNavigation.Node node) {
        ScreenColors colors = ScreenColors.forScreen(context);
        ScrollView scroll = new ScrollView(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(context, 24), dp(context, 8), dp(context, 24), dp(context, 12));
        scroll.addView(content);

        CharSequence description = node.parent == null
                ? ((HushgramPreferenceFragment.Heading) node.category).intro
                : SettingsNavigation.preview(node.rows);
        if (!TextUtils.isEmpty(description)) {
            TextView detail = text(context, description, 14, colors.summary);
            detail.setPadding(0, 0, 0, dp(context, 12));
            content.addView(detail);
        }
        addCard(content, colors, L10n.t("Overview"), totals(node), true, true);

        Map<CharSequence, Counts> breakdown = new LinkedHashMap<>();
        if (!node.children.isEmpty()) {
            for (SettingsNavigation.Node child : node.children) breakdown.put(child.title, totals(child));
            if (!node.rows.isEmpty()) {
                Counts direct = new Counts();
                for (Preference row : node.rows) direct.add(row);
                breakdown.put(L10n.t("This page"), direct);
            }
        } else if (!node.sectionHeadings.isEmpty()) {
            Counts section = null;
            for (Preference row : node.rows) {
                Preference heading = node.sectionHeadings.get(row);
                if (heading != null) {
                    section = new Counts();
                    breakdown.put(heading.getTitle(), section);
                }
                if (section != null) section.add(row);
            }
        }
        if (!breakdown.isEmpty()) {
            TextView label = text(context, L10n.t("Breakdown"), 13, colors.heading);
            label.setAccessibilityHeading(true);
            label.setPadding(0, dp(context, 16), 0, dp(context, 8));
            content.addView(label);
            int index = 0;
            for (Map.Entry<CharSequence, Counts> entry : breakdown.entrySet()) {
                addCard(content, colors, entry.getKey(), entry.getValue(), index == 0, ++index == breakdown.size());
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(node.title)
                .setView(scroll)
                .setPositiveButton(L10n.t("Close"), null);
        if (!TextUtils.isEmpty(node.icon)) builder.setIcon(SettingsIcons.icon(context, node.icon, colors.heading));
        return builder;
    }

    private static Counts totals(SettingsNavigation.Node node) {
        Counts counts = new Counts();
        for (Preference row : node.rows) counts.add(row);
        for (SettingsNavigation.Node child : node.children) counts.add(totals(child));
        return counts;
    }

    private static void addCard(LinearLayout content, ScreenColors colors, CharSequence title,
                                Counts counts, boolean first, boolean last) {
        Context context = content.getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 10));
        GradientDrawable surface = new GradientDrawable();
        surface.setColor(colors.card);
        float top = dp(context, first ? 12 : 4);
        float bottom = dp(context, last ? 12 : 4);
        surface.setCornerRadii(new float[]{top, top, top, top, bottom, bottom, bottom, bottom});
        row.setBackground(surface);
        row.addView(text(context, title, 15, colors.title));
        TextView summary = text(context, counts.summary(), 13, colors.summary);
        summary.setPadding(0, dp(context, 3), 0, 0);
        row.addView(summary);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        if (!first) params.topMargin = dp(context, 2);
        content.addView(row, params);
    }

    private static TextView text(Context context, CharSequence label, int size, int color) {
        TextView view = new TextView(context);
        view.setText(label);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTextAlignment(TextView.TEXT_ALIGNMENT_VIEW_START);
        return view;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
