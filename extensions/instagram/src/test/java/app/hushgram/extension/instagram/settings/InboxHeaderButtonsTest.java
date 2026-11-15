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

    @Test public void onlyCopiesOfTheComposeConfigurationGetTheShortcut() {
        try (var host = Robolectric.buildActivity(Activity.class).setup().visible()) {
            LinearLayout row = new LinearLayout(host.get());
            ImageView compose = new ImageView(host.get());
            row.addView(compose, new LinearLayout.LayoutParams(48, 48));
            host.get().setContentView(row);
            Settings.SETTINGS_BUTTON_IN_DM_HEADER.save(true);
            Object builder = new Object(), model = new Object();
            InboxHeaderButtons.mark(builder);
            InboxHeaderButtons.copied(model, builder);
            InboxHeaderButtons.bind(compose, model);
            ShadowLooper.idleMainLooper();
            assertEquals(2, row.getChildCount());
            assertSame(compose, row.getChildAt(1));
            Object unrelated = new Object();
            InboxHeaderButtons.copied(unrelated, new Object());
            InboxHeaderButtons.bind(compose, unrelated);
            assertEquals(1, row.getChildCount());
        }
    }

    @Test public void settingsDoesNotCopyTheFullWidthComposeSlot() {
        try (var host = Robolectric.buildActivity(Activity.class).setup().visible()) {
            LinearLayout row = new LinearLayout(host.get());
            ImageView compose = new ImageView(host.get());
            row.addView(compose, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 48));
            host.get().setContentView(row);
            Object action = new Object();
            InboxHeaderButtons.mark(action);
            Settings.SETTINGS_BUTTON_IN_DM_HEADER.save(true);
            InboxHeaderButtons.bind(compose, action);
            ShadowLooper.idleMainLooper();
            View gear = row.getChildAt(0);
            assertEquals(LinearLayout.LayoutParams.WRAP_CONTENT, gear.getLayoutParams().width);
            int width = Math.round(240 * host.get().getResources().getDisplayMetrics().density);
            row.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(48, View.MeasureSpec.EXACTLY));
            row.layout(0, 0, width, 48);
            assertEquals(View.VISIBLE, compose.getVisibility());
            assertTrue("Settings leaves room for Compose", gear.getWidth() < width);
            assertTrue("Compose stays inside the row", compose.getWidth() > 0 && compose.getRight() <= width);
        }
    }

    @Test public void fixedNativeContainerReservesSpaceForBothButtonsAndRestoresIt() {
        try (var host = Robolectric.buildActivity(Activity.class).setup().visible()) {
            LinearLayout row = new LinearLayout(host.get());
            row.setLayoutParams(new android.widget.FrameLayout.LayoutParams(48, 48));
            ImageView compose = new ImageView(host.get());
            row.addView(compose, new LinearLayout.LayoutParams(48, 48));
            host.get().setContentView(row);
            Object action = new Object();
            InboxHeaderButtons.mark(action);
            Settings.SETTINGS_BUTTON_IN_DM_HEADER.save(true);
            InboxHeaderButtons.bind(compose, action);
            ShadowLooper.idleMainLooper();
            int both = row.getLayoutParams().width;
            assertTrue("the fixed slot grows for Settings and Compose", both > 48);
            compose.getViewTreeObserver().dispatchOnPreDraw();
            assertEquals("space is reserved once", both, row.getLayoutParams().width);
            assertEquals(View.VISIBLE, compose.getVisibility());
            Settings.HIDE_DM_COMPOSE_BUTTON.save(true);
            compose.getViewTreeObserver().dispatchOnPreDraw();
            assertEquals(48, row.getLayoutParams().width);
            Settings.HIDE_DM_COMPOSE_BUTTON.save(false);
            compose.getViewTreeObserver().dispatchOnPreDraw();
            assertEquals(both, row.getLayoutParams().width);
            Settings.SETTINGS_BUTTON_IN_DM_HEADER.save(false);
            compose.getViewTreeObserver().dispatchOnPreDraw();
            assertEquals(48, row.getLayoutParams().width);
            assertEquals(View.VISIBLE, compose.getVisibility());
        }
    }
}
