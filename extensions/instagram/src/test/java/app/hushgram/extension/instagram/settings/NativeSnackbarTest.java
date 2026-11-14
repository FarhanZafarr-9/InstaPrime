/* Copyright 2026 InstaPrime contributors */
package app.hushgram.extension.instagram.settings;

import static org.junit.Assert.*;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import app.hushgram.extension.shared.SettingsContextRule;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 37})
public class NativeSnackbarTest {
    @Rule public final SettingsContextRule settings = new SettingsContextRule();

    @Test public void nativeColorStaysButCornersAreLessRoundedWithoutAnOverride() {
        try (var host = Robolectric.buildActivity(android.app.Activity.class).setup()) {
            LinearLayout surface = new LinearLayout(host.get());
            GradientDrawable fill = new GradientDrawable();
            fill.setColor(0xff294836); fill.setCornerRadius(100);
            surface.setBackground(fill);
            NativeSnackbar.style(surface, false, false);
            assertEquals(0xff294836, fill.getColor().getDefaultColor());
            assertEquals(8 * surface.getResources().getDisplayMetrics().density, fill.getCornerRadius(), 0.01f);
        }
    }

    @Test public void blackAndAccentPreserveInsetsContentAndNativeActions() {
        try (var host = Robolectric.buildActivity(android.app.Activity.class).setup()) {
            LinearLayout surface = new LinearLayout(host.get());
            GradientDrawable fill = new GradientDrawable(); fill.setColor(Color.RED);
            InsetDrawable original = new InsetDrawable(fill, 11, 7, 13, 9);
            surface.setBackground(original);
            Rect padding = new Rect(); original.getPadding(padding);
            TextView message = new TextView(host.get()); message.setText("Chat pinned");
            surface.addView(message);
            Button undo = new Button(host.get()); undo.setText("Undo");
            int[] actions = {0}; undo.setOnClickListener(v -> actions[0]++);
            surface.addView(undo);
            NativeSnackbar.style(surface, true, false);
            assertEquals(ScreenColors.BLACK.snackbarSurface(), fill.getColor().getDefaultColor());
            assertSame(original, surface.getBackground());
            Rect after = new Rect(); original.getPadding(after); assertEquals(padding, after);
            assertEquals("Chat pinned", message.getText().toString());
            undo.performClick(); assertEquals(1, actions[0]);
            NativeSnackbar.style(surface, true, true);
            assertEquals(ScreenColors.forScreen(host.get()).snackbarSurface(), fill.getColor().getDefaultColor());
            assertEquals(8 * surface.getResources().getDisplayMetrics().density, fill.getCornerRadius(), 0.01f);
            undo.performClick(); assertEquals(2, actions[0]);
            assertEquals(2, surface.getChildCount());
        }
    }
}
