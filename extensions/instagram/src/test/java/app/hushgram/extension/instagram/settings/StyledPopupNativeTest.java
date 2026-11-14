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

    @Test public void nativePopupThemeMatchingKeepsNativeInsetsAndDoesNotPaintAnOverlay() {
        try (var host = Robolectric.buildActivity(Activity.class).setup()) {
            LinearLayout root = new LinearLayout(host.get());
            PopupWindow popup = new PopupWindow(root, 231, 400);
            GradientDrawable fill = new GradientDrawable();
            fill.setColor(0xff123456);
            android.graphics.drawable.InsetDrawable nativeBackground = new android.graphics.drawable.InsetDrawable(fill, 11, 7, 13, 9);
            popup.setBackgroundDrawable(nativeBackground);
            android.graphics.Rect before = new android.graphics.Rect();
            nativeBackground.getPadding(before);
            Settings.THEME_NATIVE_POPUPS.save(false);
            StyledPopupMenu.styleNative(popup);
            assertSame(nativeBackground, popup.getBackground());
            assertEquals(0xff123456, fill.getColor().getDefaultColor());
            Settings.THEME_NATIVE_POPUPS.save(true);
            StyledPopupMenu.styleNative(popup);
            android.graphics.Rect after = new android.graphics.Rect();
            popup.getBackground().getPadding(after);
            assertEquals(before, after);
            assertNull("never leave a painted full-window backdrop behind hidden menu rows", root.getBackground());
            assertEquals(231, popup.getWidth());
        } finally {
            Settings.THEME_NATIVE_POPUPS.resetToDefault();
        }
    }

    @Test public void popupBackgroundPriorityIsAccentThenInjectedBlackThenNativeTheme() {
        try (var host = Robolectric.buildActivity(Activity.class).setup()) {
            ScreenColors colors = ScreenColors.forScreen(host.get());
            assertEquals(ScreenColors.BLACK.popupSurface(), StyledPopupMenu.popupSurface(host.get(), colors, true, false));
            assertEquals(colors.popupSurface(), StyledPopupMenu.popupSurface(host.get(), colors, true, true));
            android.content.res.TypedArray nativeTheme = host.get().obtainStyledAttributes(new int[]{android.R.attr.colorBackgroundFloating});
            try { assertEquals(nativeTheme.getColor(0, colors.dialog), StyledPopupMenu.popupSurface(host.get(), colors, false, false)); }
            finally { nativeTheme.recycle(); }
        }
    }

    @Test public void nativeFlatRowsKeepTheirActionsAndDestructiveColor() {
        try (var host = Robolectric.buildActivity(Activity.class).setup()) {
            LinearLayout root = new LinearLayout(host.get());
            TextView action = new TextView(host.get());
            action.setText("Report"); action.setTextColor(0xffcc1111);
            action.setPadding(17, 9, 17, 9);
            action.setForeground(new RippleDrawable(android.content.res.ColorStateList.valueOf(Color.RED), null, null));
            int[] clicks = {0}; action.setOnClickListener(v -> clicks[0]++);
            root.addView(action);
            PopupWindow popup = new PopupWindow(root, 320, 400);
            StyledPopupMenu.styleNative(popup);
            assertNull("no synthetic popup panel is installed", popup.getBackground());
            assertTrue(action.getBackground() instanceof android.graphics.drawable.InsetDrawable);
            assertNull("the old full-width foreground cannot paint over the inset ripple", action.getForeground());
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
            list.setDivider(new android.graphics.drawable.ColorDrawable(Color.RED));
            list.setDividerHeight(3);
            host.get().setContentView(list);
            host.visible();
            list.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY));
            list.layout(0, 0, 320, 400);
            long[] picked = {-1}; list.setOnItemClickListener((parent, view, position, id) -> picked[0] = id);
            PopupWindow popup = new PopupWindow(list, 320, 400);
            StyledPopupMenu.styleNative(popup);
            assertEquals("styling preserves native menu height", 3, list.getDividerHeight());
            assertNull("a native list's background is left as supplied", list.getBackground());
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
