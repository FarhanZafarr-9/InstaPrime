package app.hushgram.extension.instagram.settings;

import android.app.Activity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import app.hushgram.extension.shared.SettingsContextRule;
import app.hushgram.extension.shared.settings.BaseSettings;
import app.hushgram.extension.shared.settings.PauseForTests;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 37})
public class InboxHeaderButtonsTest {
    @Rule public final SettingsContextRule settings = new SettingsContextRule();
    @Before public void ready() {
        BaseSettings.PAUSED.save(false);
        PauseForTests.resume();
        reset();
    }
    @After public void reset() {
        Settings.SETTINGS_BUTTON_IN_DM_HEADER.resetToDefault();
        Settings.HIDE_DM_COMPOSE_BUTTON.resetToDefault();
        BaseSettings.PAUSED.save(false);
        PauseForTests.resume();
    }

    @Test public void settingsPrecedesComposeAndItsHideToggleIsIndependent() {
        try (var host = Robolectric.buildActivity(Activity.class).setup().visible()) {
            LinearLayout row = new LinearLayout(host.get());
            ImageView compose = new ImageView(host.get());
            row.addView(compose, new LinearLayout.LayoutParams(48, 48));
            host.get().setContentView(row);
            int[] nativeActions = {0, 0};
            compose.setOnClickListener(view -> nativeActions[0]++);
            compose.setOnLongClickListener(view -> { nativeActions[1]++; return true; });
            Object action = new Object();
            InboxHeaderButtons.mark(action);
            Settings.SETTINGS_BUTTON_IN_DM_HEADER.save(true);
            InboxHeaderButtons.bind(compose, action);
            ShadowLooper.idleMainLooper();
            assertEquals(2, row.getChildCount());
            assertSame(compose, row.getChildAt(1));
            assertEquals(View.VISIBLE, compose.getVisibility());
            compose.performClick();
            compose.performLongClick();
            assertArrayEquals(new int[]{1, 1}, nativeActions);

            Settings.HIDE_DM_COMPOSE_BUTTON.save(true);
            compose.getViewTreeObserver().dispatchOnPreDraw();
            assertEquals(View.GONE, compose.getVisibility());
            assertEquals(2, row.getChildCount());
            assertEquals(View.VISIBLE, row.getChildAt(0).getVisibility());
            Settings.SETTINGS_BUTTON_IN_DM_HEADER.save(false);
            compose.getViewTreeObserver().dispatchOnPreDraw();
            assertEquals(1, row.getChildCount());
            assertEquals(View.GONE, compose.getVisibility());
            Settings.HIDE_DM_COMPOSE_BUTTON.save(false);
            compose.getViewTreeObserver().dispatchOnPreDraw();
            assertEquals(View.VISIBLE, compose.getVisibility());

            Settings.SETTINGS_BUTTON_IN_DM_HEADER.save(true);
            compose.getViewTreeObserver().dispatchOnPreDraw();
            InboxHeaderButtons.bind(compose, new Object());
            assertEquals("a recycled non-compose action loses the extra gear", 1, row.getChildCount());
        }
    }
}
