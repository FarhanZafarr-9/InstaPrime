package app.hushgram.extension.instagram.stories;

import static org.junit.Assert.*;
import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import app.hushgram.extension.shared.SettingsContextRule;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 37})
public class StoryMentionRowStyleTest {
    @Rule public final SettingsContextRule settings = new SettingsContextRule();
    @Test public void normalAndGlassAccountRowsKeepRoundedRipplesWithoutBorders() throws Exception {
        try (var host = Robolectric.buildActivity(Activity.class).setup()) {
            java.lang.reflect.Method method = StoryMentions.class.getDeclaredMethod("row", Context.class,
                    StoryMentions.Mention.class, boolean.class, Runnable.class);
            method.setAccessible(true);
            for (boolean glass : new boolean[]{false, true}) {
                View row = (View) method.invoke(null, host.get(), new StoryMentions.Mention("someone", "Someone", null), glass, (Runnable) () -> { });
                assertTrue(row.getBackground() instanceof RippleDrawable);
                GradientDrawable surface = (GradientDrawable) ((RippleDrawable) row.getBackground()).getDrawable(0);
                int width = ReflectionHelpers.getField(surface.getConstantState(), "mStrokeWidth");
                assertTrue("no account-row outline", width <= 0);
                assertTrue("rounded corners remain", surface.getCornerRadius() > 0);
                assertTrue(row.isFocusable());
            }
        }
    }
}
