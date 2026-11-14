package app.hushgram.extension.instagram.settings;

import static org.junit.Assert.*;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
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
public class StyledPopupNativeTest {
    @Rule public final SettingsContextRule settings = new SettingsContextRule();

    @Test public void nativePopupThemeMatchingIsOptInAndPreservesItsWidth() {
        try (var host = Robolectric.buildActivity(Activity.class).setup()) {
            LinearLayout root = new LinearLayout(host.get());
            PopupWindow popup = new PopupWindow(root, 231, 400);
            Settings.THEME_NATIVE_POPUPS.save(false);
            StyledPopupMenu.styleNative(popup);
            assertEquals(ScreenColors.DEFAULT.dialog, ((GradientDrawable) popup.getBackground()).getColor().getDefaultColor());
            Settings.SETTINGS_BLACK_LOOK.save(true);
            Settings.THEME_NATIVE_POPUPS.save(true);
            StyledPopupMenu.styleNative(popup);
            assertEquals(ScreenColors.forScreen(host.get()).dialog, ((GradientDrawable) popup.getBackground()).getColor().getDefaultColor());
            assertEquals(231, popup.getWidth());
        } finally {
            Settings.THEME_NATIVE_POPUPS.resetToDefault();
            Settings.SETTINGS_BLACK_LOOK.resetToDefault();
        }
    }

    @Test public void nativeFlatRowsKeepTheirActionsAndDestructiveColor() {
        try (var host = Robolectric.buildActivity(Activity.class).setup()) {
            LinearLayout root = new LinearLayout(host.get());
            TextView action = new TextView(host.get());
            action.setText("Report"); action.setTextColor(0xffcc1111);
            action.setPadding(17, 9, 17, 9);
            int[] clicks = {0}; action.setOnClickListener(v -> clicks[0]++);
            root.addView(action);
            PopupWindow popup = new PopupWindow(root, 320, 400);
            StyledPopupMenu.styleNative(popup);
            assertTrue(popup.getBackground() instanceof GradientDrawable);
            assertTrue(action.getBackground() instanceof android.graphics.drawable.InsetDrawable);
            assertEquals(17, action.getPaddingLeft());
            assertEquals(0xffcc1111, action.getCurrentTextColor());
            RippleDrawable ripple = (RippleDrawable) ((android.graphics.drawable.InsetDrawable) action.getBackground()).getDrawable();
            assertEquals(Color.TRANSPARENT, ((GradientDrawable) ripple.getDrawable(0)).getColor().getDefaultColor());
            action.performClick(); assertEquals(1, clicks[0]);
        }
    }

    @Test public void nativeAdapterRetainsItemsEnabledStatesIdsAndCallbacks() {
        try (var host = Robolectric.buildActivity(Activity.class).setup()) {
            ListView list = new ListView(host.get());
            ArrayAdapter<String> original = new ArrayAdapter<String>(host.get(), android.R.layout.simple_list_item_1,
                    new String[]{"Reply", "Unavailable"}) {
                @Override public boolean areAllItemsEnabled() { return false; }
                @Override public boolean isEnabled(int position) { return position == 0; }
                @Override public long getItemId(int position) { return 40 + position; }
                @Override public boolean hasStableIds() { return true; }
            };
            list.setAdapter(original);
            host.get().setContentView(list);
            host.visible();
            list.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY));
            list.layout(0, 0, 320, 400);
            long[] picked = {-1}; list.setOnItemClickListener((parent, view, position, id) -> picked[0] = id);
            PopupWindow popup = new PopupWindow(list, 320, 400);
            StyledPopupMenu.styleNative(popup);
            assertTrue("the list keeps its panel surface", list.getBackground() instanceof GradientDrawable);
            assertEquals(2, list.getAdapter().getCount());
            assertEquals("Reply", list.getAdapter().getItem(0));
            assertFalse(list.getAdapter().isEnabled(1));
            assertTrue(list.getAdapter().hasStableIds());
            assertSame("native adapter identity remains intact", original, list.getAdapter());
            View first = list.getChildAt(0);
            assertNotNull(first);
            assertTrue(first.getBackground() instanceof android.graphics.drawable.InsetDrawable);
            list.performItemClick(first, 0, list.getAdapter().getItemId(0));
            assertEquals(40, picked[0]);
            StyledPopupMenu.styleNative(popup);
            assertEquals("restyling never duplicates options", 2, list.getAdapter().getCount());
        }
    }
}
