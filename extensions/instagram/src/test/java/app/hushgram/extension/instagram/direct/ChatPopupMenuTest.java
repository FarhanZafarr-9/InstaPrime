/*
 * Copyright 2026 InstaPrime contributors
 * HushGram foundation: https://github.com/SysAdminDoc/HushGram
 */
package app.hushgram.extension.instagram.direct;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.LinearLayout;
import android.widget.ListView;
import org.junit.Test;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;
import app.hushgram.extension.shared.SettingsContextRule;

/** Real anchored lists: checked choices, dismissal, wrapping and rounded clipping on both API floors. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 37}, qualifiers = "w320dp-h640dp-xhdpi")
public class ChatPopupMenuTest {
    @Rule public final SettingsContextRule settings = new SettingsContextRule();

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void longProfileOptionsUseAvailableSpaceAndHighlightedRowsDoNotReachTheWalls() {
        try (ActivityController<Activity> activity = Robolectric.buildActivity(Activity.class).setup().visible()) {
            Button anchor = new Button(activity.get());
            activity.get().setContentView(anchor);
            ChatPopupMenu popup = new ChatPopupMenu(activity.get(), anchor);
            popup.getMenu().add("Copy the full profile picture address for this account");
            popup.show();
            ListView list = popup.windowForTests().getListView();
            int width = popup.windowForTests().getWidth();
            float density = list.getResources().getDisplayMetrics().density;
            android.graphics.Rect frame = new android.graphics.Rect();
            anchor.getWindowVisibleDisplayFrame(frame);
            int available = frame.width() > 0 ? frame.width() : list.getResources().getDisplayMetrics().widthPixels;
            assertTrue(width <= available);
            assertTrue("long options are not restricted to the former 240dp cap", width > Math.round(240 * density));
            int height = Math.round(160 * density);
            list.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            list.layout(0, 0, width, height);
            View row = list.getChildAt(0);
            assertNotNull(row);
            assertTrue(row.getLeft() >= list.getPaddingLeft());
            assertTrue(row.getRight() <= width - list.getPaddingRight());
            row.setSelected(true);
            row.jumpDrawablesToCurrentState();
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            list.draw(new Canvas(bitmap));
            int y = row.getTop() + row.getHeight() / 2;
            int panel = ((GradientDrawable) list.getBackground()).getColor().getDefaultColor();
            assertEquals(panel, bitmap.getPixel(list.getPaddingLeft() / 2, y));
            assertEquals(panel, bitmap.getPixel(width - list.getPaddingRight() / 2 - 1, y));
            bitmap.recycle();
        } finally { ChatPopupMenu.resetForTests(); }
    }

    @Test public void shortMenusUseContentWidthInsteadOfTheMaximum() {
        try (ActivityController<Activity> activity = Robolectric.buildActivity(Activity.class).setup().visible()) {
            Button anchor = new Button(activity.get());
            activity.get().setContentView(anchor);
            ChatPopupMenu popup = new ChatPopupMenu(activity.get(), anchor);
            popup.getMenu().add("Reply");
            popup.show();
            int shortWidth = popup.windowForTests().getWidth();
            popup.dismiss();
            GradientDrawable icon = new GradientDrawable();
            int size = Math.round(24 * anchor.getResources().getDisplayMetrics().density);
            icon.setSize(size, size);
            icon.setColor(Color.WHITE);
            popup.getMenu().getItem(0).setIcon(icon);
            assertNotNull(popup.getMenu().getItem(0).getIcon());
            popup.show();
            int iconWidth = popup.windowForTests().getWidth();
            assertTrue("the icon and its spacing count toward width: plain=" + shortWidth + ", icon=" + iconWidth,
                    iconWidth > shortWidth);
            popup.dismiss();
            popup.getMenu().getItem(0).setCheckable(true);
            popup.show();
            assertTrue("the checkmark slot also counts toward width", popup.windowForTests().getWidth() > iconWidth);
            popup.dismiss();
            popup.getMenu().clear();
            popup.getMenu().add("A much longer message action label");
            popup.show();
            assertTrue(popup.windowForTests().getWidth() > shortWidth);
        } finally { ChatPopupMenu.resetForTests(); }
    }

    @Test public void choosingARowRunsItsNativeMenuActionAndDismissesOnlyOnce() {
        try (ActivityController<Activity> activity = Robolectric.buildActivity(Activity.class).setup().visible()) {
            Button anchor = new Button(activity.get());
            activity.get().setContentView(anchor);
            ChatPopupMenu popup = new ChatPopupMenu(activity.get(), anchor);
            popup.getMenu().add(1, 4, 0, "Hold Seen in this chat").setCheckable(true).setChecked(true);
            int[] picked = {-1}, dismissed = {0};
            popup.setOnMenuItemClickListener(item -> {
                assertFalse("the menu must be gone before its action opens another sheet", popup.windowForTests().isShowing());
                picked[0] = item.getItemId(); return true;
            });
            popup.setOnDismissListener(menu -> dismissed[0]++);
            popup.show();
            assertTrue(popup.windowForTests().isShowing());
            ListView list = popup.windowForTests().getListView();
            View entry = list.getAdapter().getView(0, null, list);
            CheckedTextView row = entry.findViewById(android.R.id.text1);
            assertTrue(row.isChecked());
            assertNotNull(row.getCheckMarkDrawable());
            android.view.accessibility.AccessibilityNodeInfo info = entry.createAccessibilityNodeInfo();
            assertTrue(info.isCheckable());
            assertEquals(CheckedTextView.class.getName(), info.getClassName());
            list.performItemClick(entry, 0, 4);
            assertEquals(4, picked[0]);
            assertFalse(popup.windowForTests().isShowing());
            popup.dismiss();
            assertEquals(1, dismissed[0]);
        } finally { ChatPopupMenu.resetForTests(); }
    }

    @Test public void leavingTheAnchorClosesTheMenuAndDisabledRowsStayDisabled() {
        try (ActivityController<Activity> activity = Robolectric.buildActivity(Activity.class).setup().visible()) {
            LinearLayout root = new LinearLayout(activity.get());
            Button anchor = new Button(activity.get());
            root.addView(anchor);
            activity.get().setContentView(root);
            anchor.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            ChatPopupMenu popup = new ChatPopupMenu(activity.get(), anchor);
            popup.getMenu().add(Menu.NONE, 1, 0, "Export chat").setEnabled(false);
            popup.show();
            assertEquals(View.LAYOUT_DIRECTION_RTL, popup.windowForTests().getListView().getLayoutDirection());
            assertFalse(popup.windowForTests().getListView().getAdapter().isEnabled(0));
            ListView list = popup.windowForTests().getListView();
            View row = list.getAdapter().getView(0, null, list);
            assertFalse(row.createAccessibilityNodeInfo().isCheckable());
            root.removeView(anchor);
            assertFalse(popup.windowForTests().isShowing());
        } finally { ChatPopupMenu.resetForTests(); }
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void lightAndDarkMenusClipTheirCornersAndWrapLongChoices() throws Exception {
        for (int theme : new int[]{android.R.style.Theme_Material_Light_NoActionBar,
                android.R.style.Theme_Material_NoActionBar}) {
            ActivityController<Activity> activity = Robolectric.buildActivity(Activity.class);
            activity.get().setTheme(theme);
            try {
                activity.setup().visible();
                Button anchor = new Button(activity.get());
                android.widget.FrameLayout chat = new android.widget.FrameLayout(activity.get());
                chat.addView(anchor, new android.widget.FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Math.round(48 * anchor.getResources().getDisplayMetrics().density)));
                activity.get().setContentView(chat);
                ChatPopupMenu popup = new ChatPopupMenu(activity.get(), anchor);
                popup.getMenu().add(1, 0, 0, "Use the InstaPrime setting").setCheckable(true).setChecked(true);
                popup.getMenu().add(1, 1, 1, "Hold Seen in this chat until I choose to mark it as read").setCheckable(true);
                popup.getMenu().add(1, 2, 2, "Send Seen in this chat").setCheckable(true);
                popup.show();
                ListView list = popup.windowForTests().getListView();
                assertTrue(list.getClipToOutline());
                GradientDrawable background = (GradientDrawable) list.getBackground();
                app.hushgram.extension.instagram.settings.ScreenColors palette =
                        app.hushgram.extension.instagram.settings.ScreenColors.forScreen(activity.get());
                android.content.res.TypedArray nativeTheme = activity.get().obtainStyledAttributes(new int[]{android.R.attr.colorBackgroundFloating});
                try { assertEquals("without an override, use the native popup surface", nativeTheme.getColor(0, palette.dialog),
                        background.getColor().getDefaultColor()); }
                finally { nativeTheme.recycle(); }
                assertEquals(16 * list.getResources().getDisplayMetrics().density, background.getCornerRadius(), 0.1f);
                View large = list.getAdapter().getView(1, null, list);
                assertTrue("the list reserves a real gutter beside every option", list.getPaddingLeft() > 0);
                android.graphics.drawable.RippleDrawable option = (android.graphics.drawable.RippleDrawable)
                        large.getBackground();
                GradientDrawable mask = (GradientDrawable) option.findDrawableByLayerId(android.R.id.mask);
                float inner = 12 * list.getResources().getDisplayMetrics().density;
                assertArrayEquals("all options have the same rounded ripple boundary",
                        new float[]{inner, inner, inner, inner, inner, inner, inner, inner}, mask.getCornerRadii(), 0.1f);
                for (int at = 0; at < list.getAdapter().getCount(); at++) {
                    View entry = list.getAdapter().getView(at, null, list);
                    android.graphics.drawable.RippleDrawable ripple = (android.graphics.drawable.RippleDrawable)
                            entry.getBackground();
                    assertEquals(Color.TRANSPARENT, ((GradientDrawable) ripple.getDrawable(0)).getColor().getDefaultColor());
                    assertArrayEquals(mask.getCornerRadii(), ((GradientDrawable)
                            ripple.findDrawableByLayerId(android.R.id.mask)).getCornerRadii(), 0.1f);
                }
                CheckedTextView row = large.findViewById(android.R.id.text1);
                row.setTextSize(32);
                int width = popup.windowForTests().getWidth();
                large.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                large.layout(0, 0, width, large.getMeasuredHeight());
                assertTrue(row.getLayout().getLineCount() > 1);
                for (int line = 0; line < row.getLayout().getLineCount(); line++) assertEquals(0, row.getLayout().getEllipsisCount(line));
                int height = list.getPaddingTop() + list.getPaddingBottom();
                for (int i = 0; i < list.getAdapter().getCount(); i++) {
                    View entry = list.getAdapter().getView(i, null, list);
                    entry.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                    height += entry.getMeasuredHeight();
                }
                assertTrue("the popup reserves the complete measured options height", popup.windowForTests().getHeight() >= height);
                list.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
                list.layout(0, 0, width, list.getMeasuredHeight());
                CheckedTextView wrapped = list.getChildAt(1).findViewById(android.R.id.text1);
                assertNotNull(wrapped);
                assertTrue("long menu choices wrap beside the indicator", wrapped.getLayout().getLineCount() > 1);
                assertTrue("all wrapped lines fit inside their row", wrapped.getLayout().getHeight()
                        <= wrapped.getHeight() - wrapped.getPaddingTop() - wrapped.getPaddingBottom());
                Bitmap bitmap = Bitmap.createBitmap(width, list.getHeight(), Bitmap.Config.ARGB_8888);
                list.draw(new Canvas(bitmap));
                assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)));
                java.io.File file = new java.io.File("build/chat-popup-" + android.os.Build.VERSION.SDK_INT + "-" + theme + ".png");
                file.getParentFile().mkdirs();
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); }
                bitmap.recycle();
                popup.dismiss();
            } finally { activity.close(); ChatPopupMenu.resetForTests(); }
        }
    }
}
