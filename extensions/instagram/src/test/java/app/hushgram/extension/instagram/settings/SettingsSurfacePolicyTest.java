/*
 * Copyright 2026 InstaPrime contributors
 * HushGram foundation: https://github.com/SysAdminDoc/HushGram
 */
package app.hushgram.extension.instagram.settings;

import android.graphics.Color;
import app.hushgram.extension.shared.SettingsContextRule;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 37}, shadows = SettingsSurfacePolicyTest.AppPureBlack.class)
public class SettingsSurfacePolicyTest {
    @Rule public final SettingsContextRule settings = new SettingsContextRule();

    @Implements(value = SettingsStatus.class, isInAndroidSdk = false)
    public static class AppPureBlack {
        @Implementation public static boolean pureBlack() { return true; }
    }

    @After public void reset() {
        Settings.SETTINGS_BLACK_LOOK.resetToDefault();
        ScreenColors.shown = null;
    }

    @Test public void onlyTheSettingsSwitchCanBlackenSettingsSurfaces() {
        assertTrue("the installed app-wide patch is present", SettingsStatus.pureBlack());
        ScreenColors.shown = null;
        Settings.SETTINGS_BLACK_LOOK.save(false);
        ScreenColors tonal = ScreenColors.forScreen(RuntimeEnvironment.getApplication());
        assertNotEquals(Color.BLACK, tonal.card);
        assertNotEquals(Color.BLACK, tonal.dialog);
        assertNotEquals(Color.BLACK, ScreenColors.current().card);
        Settings.SETTINGS_BLACK_LOOK.save(true);
        ScreenColors black = ScreenColors.forScreen(RuntimeEnvironment.getApplication());
        assertEquals(Color.BLACK, black.card);
        assertEquals(Color.BLACK, black.dialog);
        assertEquals(Color.BLACK, ScreenColors.current().card);
    }

    @Test public void runtimePalettesReadTheWallpapersColorsWithoutAnOpenSettingsPage() {
        ScreenColors.shown = null;
        Settings.SETTINGS_BLACK_LOOK.save(false);
        android.content.Context context = RuntimeEnvironment.getApplication();
        ScreenColors palette = ScreenColors.forScreen(context);
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            assertEquals(context.getColor(android.R.color.system_neutral1_900), palette.dialog);
            assertEquals(context.getColor(android.R.color.system_accent1_200), palette.accent);
        }
        assertEquals(palette.card, ScreenColors.current().card);
    }
}
