package app.hushgram.extension.instagram.settings;

import static org.junit.Assert.*;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.View;
import android.widget.EditText;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 37})
public class SettingsPageStyleTest {
    @Test public void recycledRowsFollowFilteredEdgesAndKeepBoundedRipples() {
        try (var host = Robolectric.buildActivity(Activity.class).setup()) {
            View row = new View(host.get());
            ScreenColors colors = new ScreenColors(false);
            float density = row.getResources().getDisplayMetrics().density;
            SettingsPageStyle.row(row, colors, true, false);
            GradientDrawable top = surface(row);
            assertEquals(12 * density, top.getCornerRadii()[0], .01f);
            assertEquals(4 * density, top.getCornerRadii()[4], .01f);
            assertEquals(colors.card, top.getColor().getDefaultColor());
            assertNotNull(((RippleDrawable) row.getBackground()).findDrawableByLayerId(android.R.id.mask));
            SettingsPageStyle.row(row, colors, false, true);
            assertEquals(4 * density, surface(row).getCornerRadii()[0], .01f);
            assertEquals(12 * density, surface(row).getCornerRadii()[4], .01f);
            SettingsPageStyle.row(row, colors, true, true);
            assertEquals(12 * density, surface(row).getCornerRadii()[0], .01f);
            assertEquals(12 * density, surface(row).getCornerRadii()[4], .01f);
        }
    }

    private static GradientDrawable surface(View row) {
        return (GradientDrawable) ((InsetDrawable) ((RippleDrawable) row.getBackground()).getDrawable(0)).getDrawable();
    }

    @Test public void windowsUseThePageBackgroundAndDisableContrastScrims() {
        try (var host = Robolectric.buildActivity(Activity.class).setup().visible()) {
            ScreenColors colors = new ScreenColors(false);
            SettingsPageStyle.window(host.get().getWindow(), colors);
            assertEquals(Build.VERSION.SDK_INT >= 35 ? Color.TRANSPARENT : colors.background,
                    host.get().getWindow().getStatusBarColor());
            if (Build.VERSION.SDK_INT >= 29) assertFalse(host.get().getWindow().isStatusBarContrastEnforced());
        }
    }

    @Test public void searchStillHasReadableTextCursorAndTonalBackground() {
        try (var host = Robolectric.buildActivity(Activity.class).setup()) {
            EditText search = new EditText(host.get());
            ScreenColors.styleSearchField(search);
            assertTrue(search.getBackground() instanceof GradientDrawable);
            int width = org.robolectric.util.ReflectionHelpers.getField(search.getBackground().getConstantState(), "mStrokeWidth");
            assertEquals("search has no border", 0, width);
            assertEquals(ScreenColors.forScreen(host.get()).title, search.getCurrentTextColor());
            assertTrue(search.getMinHeight() > 0);
        }
    }
}
