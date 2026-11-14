/*
 * Copyright 2026 HushGram contributors
 * https://github.com/SysAdminDoc/HushGram
 */
package app.hushgram.extension.instagram.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.preference.SwitchPreference;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.Arrays;
import java.util.EnumSet;

import app.hushgram.extension.shared.SettingsContextRule;
import app.hushgram.extension.shared.L10n;
import app.hushgram.extension.shared.Utils;
import app.hushgram.extension.shared.settings.BaseSettings;
import app.hushgram.extension.shared.settings.PauseForTests;

/** HushGram launch screen's switch: under Layout after the accent rows, off to start, backed up. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 37})
@SuppressWarnings("deprecation")
public class LaunchScreenSettingsTest {
    @Rule public final SettingsContextRule settings = new SettingsContextRule();
    private ActivityController<Activity> controller;
    private HushgramPreferenceFragment page;

    @Before public void prepare() {
        RuntimeEnvironment.getApplication().getApplicationInfo().targetSdkVersion = 37;
        Settings.LAUNCH_SCREEN.resetToDefault();
        Settings.LAUNCH_SCREEN_ACCENT.resetToDefault();
        BaseSettings.PAUSED.save(false);
        PauseForTests.resume();
        BaseSettings.SAFE_MODE.save(false);
        Settings.SIGN_IN_NOTICE_HIDDEN.save(true);
    }

    @After public void close() throws Exception {
        if (controller != null) controller.close();
        Utils.awaitBackgroundTasksForTests();
        PatchFamily.inBuildForTests = null;
        Settings.LAUNCH_SCREEN.resetToDefault();
        Settings.LAUNCH_SCREEN_ACCENT.resetToDefault();
        Settings.SIGN_IN_NOTICE_HIDDEN.resetToDefault();
    }

    private void open(EnumSet<PatchFamily> build) throws Exception {
        PatchFamily.inBuildForTests = build;
        controller = Robolectric.buildActivity(Activity.class).setup();
        page = new HushgramPreferenceFragment();
        controller.get().getFragmentManager().beginTransaction().add(android.R.id.content, page).commitNow();
        Utils.awaitBackgroundTasksForTests();
    }

    @Test public void missingPatchHasNoSwitch() throws Exception {
        open(EnumSet.of(PatchFamily.ACCENT_COLOR));
        assertNull(page.findPreference(Settings.LAUNCH_SCREEN.key));
        assertFalse(ConfigurationBackup.eligible().containsKey(Settings.LAUNCH_SCREEN.key));
    }

    /** Layout opens for the launch screen alone. */
    @Test public void launchScreenAloneStillGetsLayout() throws Exception {
        open(EnumSet.of(PatchFamily.LAUNCH_SCREEN));
        SwitchPreference row = (SwitchPreference) page.findPreference(Settings.LAUNCH_SCREEN.key);
        assertNotNull(row);
        assertEquals("Layout", destination(row).category.getTitle().toString());
    }

    @Test public void switchStartsOffOnItsSettingsPageAndIsBackedUp() throws Exception {
        open(EnumSet.of(PatchFamily.EMOJI_STYLE, PatchFamily.ACCENT_COLOR, PatchFamily.LAUNCH_SCREEN));
        SwitchPreference row = (SwitchPreference) page.findPreference(Settings.LAUNCH_SCREEN.key);
        assertNotNull(row);
        assertEquals(L10n.t("HushGram launch screen"), row.getTitle().toString());
        assertEquals(L10n.t("Shows HushGram's logo instead of Instagram's on the screen you see while Instagram starts, "
                + "on Android 13 and newer. It shows from the next start."), row.getSummary().toString());
        SettingsNavigation.Node destination = destination(row);
        android.preference.Preference fill = page.findPreference(Settings.LAUNCH_SCREEN_ACCENT.key);
        assertTrue(destination.rows.indexOf(row) < destination.rows.indexOf(fill));
        assertEquals(destination, destination(fill));
        assertFalse(row.isChecked());
        assertFalse(Settings.LAUNCH_SCREEN.get());
        assertEquals(Arrays.asList(Settings.LAUNCH_SCREEN, Settings.LAUNCH_SCREEN_ACCENT), PatchFamily.LAUNCH_SCREEN.switches);
        assertTrue(ConfigurationBackup.eligible().containsKey(Settings.LAUNCH_SCREEN.key));

        row.setChecked(true);
        assertTrue(Settings.LAUNCH_SCREEN.savedValue());
    }

    /** #528: the accent fill sits under the launch screen switch and waits for it. */
    @Test public void accentFillWaitsForTheLaunchScreen() throws Exception {
        open(EnumSet.of(PatchFamily.ACCENT_COLOR, PatchFamily.LAUNCH_SCREEN));
        SwitchPreference launch = (SwitchPreference) page.findPreference(Settings.LAUNCH_SCREEN.key);
        SwitchPreference fill = (SwitchPreference) page.findPreference(Settings.LAUNCH_SCREEN_ACCENT.key);
        assertNotNull(fill);
        assertEquals("Launch screen in the accent color", fill.getTitle().toString());
        assertEquals("Fills that screen with the accent color, or Instagram's blue while Accent color is off. "
                + "A custom color shows as the closest one in the list. It shows from the next start.", fill.getSummary().toString());
        assertFalse(fill.isChecked());
        assertFalse(fill.isEnabled());
        assertTrue(ConfigurationBackup.eligible().containsKey(Settings.LAUNCH_SCREEN_ACCENT.key));

        launch.setChecked(true);
        ShadowLooper.idleMainLooper();
        assertTrue(fill.isEnabled());
        fill.setChecked(true);
        assertTrue(Settings.LAUNCH_SCREEN_ACCENT.get());
    }

    private SettingsNavigation.Node destination(android.preference.Preference row) throws Exception {
        java.lang.reflect.Field field = HushgramPreferenceFragment.class.getDeclaredField("navigation");
        field.setAccessible(true);
        return ((SettingsNavigation) field.get(page)).destination(row);
    }
}
