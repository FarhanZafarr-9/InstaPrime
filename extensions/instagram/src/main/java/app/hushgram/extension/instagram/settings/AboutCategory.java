/*
 * Copyright 2026 Hushfacebook contributors
 * https://github.com/SysAdminDoc/Hushfacebook
 *
 * Modified for HushGram (Instagram), 2026.
 */
package app.hushgram.extension.instagram.settings;

import static app.hushgram.extension.instagram.settings.HushgramPreferenceFragment.SOURCE_ADDRESS;
import static app.hushgram.extension.instagram.settings.HushgramPreferenceFragment.SOURCE_URL;
import static app.hushgram.extension.instagram.settings.HushgramPreferenceFragment.category;
import static app.hushgram.extension.instagram.settings.HushgramPreferenceFragment.info;
import static app.hushgram.extension.instagram.settings.HushgramPreferenceFragment.mark;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;

import java.util.Set;

import app.hushgram.extension.instagram.settings.HushgramPreferenceFragment.Row;
import app.hushgram.extension.shared.L10n;
import app.hushgram.extension.shared.Logger;
import app.hushgram.extension.shared.Utils;

/** Builds the About category of HushGram settings. */
final class AboutCategory {
    private AboutCategory() { }

    static void add(HushgramPreferenceFragment page, PreferenceScreen screen, Context context, Set<PatchFamily> build) {
        PreferenceCategory about = category(screen, L10n.t("Fork info"), SettingsIcons.ABOUT,
                L10n.t("InstaPrime, its developer, the original project and support."));
        page.about = about;
        Preference version = info(context, L10n.t("Version"), L10n.f("InstaPrime %1$s on Instagram %2$s",
                L10n.isolate(Utils.getPatchesReleaseVersion()), L10n.isolate(Utils.getAppVersionName()))
                + "\n" + L10n.isolate(Utils.getSourceBuildIdentity()));
        version.setKey("primegram_version");
        about.addPreference(mark(version, SettingsIcons.ABOUT));
        about.addPreference(link(page, context, "primegram_developer", "FarhanZafarr-9",
                "InstaPrime developer", "https://github.com/FarhanZafarr-9"));
        about.addPreference(link(page, context, "primegram_source", "InstaPrime on GitHub",
                "Source code for this fork", "https://github.com/FarhanZafarr-9/HushGram"));
        about.addPreference(link(page, context, "primegram_upstream_developer", "Original developer",
                "SysAdminDoc · HushGram", "https://github.com/SysAdminDoc"));

        Preference source = new Row(context);
        source.setKey("primegram_upstream");
        source.setTitle(L10n.t("Original project and issues"));
        source.setSummary(SOURCE_ADDRESS);
        source.setPersistent(false);
        source.setOnPreferenceClickListener(p -> {
            try {
                page.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL)));
            } catch (ActivityNotFoundException | SecurityException missing) {
                // No browser, or none switched on. Uncaught, Android's exception closed Instagram.
                Logger.printInfo(() -> "No app opened the source code link");
                Utils.showToastLong(L10n.f("No app on this phone can open the link. The address is %1$s.",
                        L10n.isolate(SOURCE_ADDRESS)));
            }
            return true;
        });
        about.addPreference(mark(source, SettingsIcons.OPENING));

        // Section 7b asks that its notice reach the person using the software, and a file in the
        // repository does not reach them.
        Preference licenses = new Row(context);
        licenses.setKey("primegram_licenses");
        licenses.setTitle(L10n.t("Licenses"));
        licenses.setSummary(L10n.t("GPL-3.0, with the notices of the projects this is built on"));
        licenses.setPersistent(false);
        licenses.setOnPreferenceClickListener(p -> {
            page.showNotice(context);
            return true;
        });
        about.addPreference(mark(licenses, SettingsIcons.LICENSE));
    }
    private static Preference link(HushgramPreferenceFragment page, Context context, String key,
                                   String title, String summary, String url) {
        Row row = new Row(context);
        row.setKey(key);
        row.setPersistent(false);
        row.setTitle(L10n.t(title));
        row.setSummary(summary.startsWith("SysAdminDoc") ? summary : L10n.t(summary));
        row.setOnPreferenceClickListener(clicked -> {
            try { page.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
            catch (ActivityNotFoundException | SecurityException unavailable) {
                Utils.showToastLong(L10n.f("No app on this phone can open the link. The address is %1$s.", url));
            }
            return true;
        });
        return mark(row, SettingsIcons.OPENING);
    }

}
