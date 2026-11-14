package app.hushgram.extension.instagram.settings;

import static org.junit.Assert.*;
import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import app.hushgram.extension.shared.SettingsContextRule;

/** The retained tile component's appearance, independent of the retired flat grid navigation. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 37})
public class CategoryGridStyleTest {
    @Rule public final SettingsContextRule settings = new SettingsContextRule();
    @Test public void selectionUsesFillWithoutAnOutlineAndKeepsItsAction() {
        try (var host = Robolectric.buildActivity(Activity.class).setup()) {
            HushgramPreferenceFragment.Heading heading = new HushgramPreferenceFragment.Heading(host.get());
            heading.setKey("feed"); heading.setTitle("Feed"); heading.intro = "Feed appearance";
            boolean[] tapped = {false};
            CategoryGrid grid = new CategoryGrid(host.get(), row -> tapped[0] = true);
            grid.show(java.util.List.of(heading));
            View first = grid.getView(null, new FrameLayout(host.get())).findViewWithTag(CategoryGrid.TILE_TAG);
            GradientDrawable idle = (GradientDrawable) ((RippleDrawable) first.getBackground()).getDrawable(0);
            assertTrue((int) ReflectionHelpers.getField(idle.getConstantState(), "mStrokeWidth") <= 0);
            heading.expanded = true;
            grid.show(java.util.List.of(heading));
            View selected = grid.getView(null, new FrameLayout(host.get())).findViewWithTag(CategoryGrid.TILE_TAG);
            GradientDrawable active = (GradientDrawable) ((RippleDrawable) selected.getBackground()).getDrawable(0);
            assertNotEquals(idle.getColor().getDefaultColor(), active.getColor().getDefaultColor());
            assertTrue((int) ReflectionHelpers.getField(active.getConstantState(), "mStrokeWidth") <= 0);
            selected.performClick();
            assertTrue(tapped[0]);
            AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
            selected.onInitializeAccessibilityNodeInfo(info);
            assertEquals(android.widget.Button.class.getName(), info.getClassName().toString());
        }
    }
}
