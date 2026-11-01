/* Copyright 2026 HushGram and InstaPrime contributors. */
package app.hushgram.extension.instagram.settings;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.AlertDialog;
import android.preference.SwitchPreference;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowAlertDialog;
import app.hushgram.extension.shared.SettingsContextRule;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 37}, qualifiers = "w320dp-h480dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DescriptionInfoTest {
    @Rule public final SettingsContextRule settings = new SettingsContextRule();
    ActivityController<Activity> controller;
    static final String DESCRIPTION = "Keep this complete description available without changing the saved choice.";

    @Before public void setup() {
        controller = Robolectric.buildActivity(Activity.class).setup();
        Settings.DESCRIPTIONS_BEHIND_INFO.save(true);
        DescriptionInfo.searching = false;
    }

    @After public void close() {
        controller.close();
        Settings.DESCRIPTIONS_BEHIND_INFO.resetToDefault();
        DescriptionInfo.searching = false;
        ScreenColors.shown = null;
    }
    SwitchPreference preference() {
        SwitchPreference p = HushgramPreferenceFragment.toggle(controller.get(), Settings.HIDE_MENU_ROW, "Hide the menu row", DESCRIPTION);
        p.setPersistent(false);
        HushgramPreferenceFragment.mark(p, SettingsIcons.SETTINGS);
        return p;
    }
    View bind(SwitchPreference p, View old) {
        View v = p.getView(old, new FrameLayout(controller.get()));
        v.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        v.layout(0, 0, 320, v.getMeasuredHeight());
        return v;
    }

    @Test public void buttonPrecedesSwitchAndOpensDescriptionWithoutToggling() {
        SwitchPreference p = preference();
        boolean before = p.isChecked();
        View v = bind(p, null);
        ImageButton b = v.findViewWithTag(DescriptionInfo.TAG);
        assertNotNull(b);
        ViewGroup f = (ViewGroup)b.getParent();
        assertTrue(f.indexOfChild(b)<f.indexOfChild(v.findViewById(android.R.id.switch_widget)));
        assertEquals(32, b.getHeight());
        assertEquals(View.GONE, v.findViewById(android.R.id.summary).getVisibility());
        assertNull(((TextView)v.findViewById(android.R.id.title)).getCompoundDrawablesRelative()[2]);
        b.performClick();
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertEquals(DESCRIPTION, shadowOf(dialog).getMessage().toString());
        assertEquals(before, p.isChecked());
        dialog.dismiss();
    }

    @Test public void infoButtonAndSwitchFitNarrowRowAtLargeFont() {
        RuntimeEnvironment.setFontScale(2f);
        try {
            View v = bind(preference(), null);
            ImageButton b = v.findViewWithTag(DescriptionInfo.TAG);
            View control = v.findViewById(android.R.id.switch_widget);
            assertTrue(b.getWidth()>0);
            ViewGroup f = (ViewGroup)b.getParent();
            assertTrue(control.getRight()<=f.getWidth());
            assertTrue(control.getBottom()<=f.getHeight());
            assertTrue(b.getRight()<=control.getLeft());
            assertTrue(f.getHeight()>=32);
        }
        finally {
            RuntimeEnvironment.setFontScale(1f);
        }
    }

    @Test public void recycledRowsRestoreDescriptionsAndRemoveButton() {
        SwitchPreference p = preference();
        View v = bind(p, null);
        Settings.DESCRIPTIONS_BEHIND_INFO.save(false);
        v = bind(p, v);
        assertNull(v.findViewWithTag(DescriptionInfo.TAG));
        assertEquals(View.VISIBLE, v.findViewById(android.R.id.summary).getVisibility());
    }

    @Test public void disabledRowsRetainExplanation() {
        SwitchPreference p = preference();
        p.setEnabled(false);
        View v = bind(p, null);
        assertNull(v.findViewWithTag(DescriptionInfo.TAG));
        assertEquals(View.VISIBLE, v.findViewById(android.R.id.summary).getVisibility());
    }

    @Test public void searchingKeepsDescriptions() {
        DescriptionInfo.searching = true;
        View v = bind(preference(), null);
        assertNull(v.findViewWithTag(DescriptionInfo.TAG));
        assertEquals(View.VISIBLE, v.findViewById(android.R.id.summary).getVisibility());
    }

    @Test public void buttonBeforeChevronAndRtlLayout() {
        HushgramPreferenceFragment.Row p = new HushgramPreferenceFragment.Row(controller.get());
        p.setTitle("Choose an option");
        p.setSummary(DESCRIPTION);
        View v = p.getView(null, new FrameLayout(controller.get()));
        v.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        v.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        v.layout(0, 0, 320, v.getMeasuredHeight());
        View b = v.findViewWithTag(DescriptionInfo.TAG);
        assertNotNull(b);
        View c = v.findViewWithTag(ScreenColors.CHEVRON);
        assertNotNull(c);
        assertSame(b.getParent(), c.getParent());
        assertTrue(b.getLeft()>=c.getRight());
    }

    @Test public void screenReaderKeepsFullDescription() {
        shadowOf(controller.get().getSystemService(android.view.accessibility.AccessibilityManager.class)).setTouchExplorationEnabled(true);
        View v = bind(preference(), null);
        assertNull(v.findViewWithTag(DescriptionInfo.TAG));
        assertEquals(View.VISIBLE, v.findViewById(android.R.id.summary).getVisibility());
    }

    @Test public void choiceTravelsInBackup() {
        assertTrue(ConfigurationBackup.eligible().containsKey(Settings.DESCRIPTIONS_BEHIND_INFO.key));
    }

    public static class TestPreferenceFragment extends android.preference.PreferenceFragment { }

    private ListView attach(android.preference.Preference... rows) {
        android.preference.PreferenceFragment fragment = new TestPreferenceFragment();
        controller.get().getFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment).commitNow();
        android.preference.PreferenceScreen screen = fragment.getPreferenceManager().createPreferenceScreen(controller.get());
        for (android.preference.Preference row : rows) screen.addPreference(row);
        fragment.setPreferenceScreen(screen);
        controller.visible();
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        View decor = controller.get().getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, 320, 480);
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        ListView list = fragment.getView().findViewById(android.R.id.list);
        list.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY));
        list.layout(0, 0, 320, 400);
        return list;
    }

    private void touch(ListView list, View target) {
        android.graphics.Rect rect = new android.graphics.Rect();
        target.getDrawingRect(rect);
        list.offsetDescendantRectToMyCoords(target, rect);
        long now = android.os.SystemClock.uptimeMillis();
        android.view.MotionEvent down = android.view.MotionEvent.obtain(now, now,
                android.view.MotionEvent.ACTION_DOWN, rect.exactCenterX(), rect.exactCenterY(), 0);
        android.view.MotionEvent up = android.view.MotionEvent.obtain(now, now + 60,
                android.view.MotionEvent.ACTION_UP, rect.exactCenterX(), rect.exactCenterY(), 0);
        list.dispatchTouchEvent(down);
        list.dispatchTouchEvent(up);
        down.recycle(); up.recycle();
        shadowOf(android.os.Looper.getMainLooper()).idleFor(1, java.util.concurrent.TimeUnit.SECONDS);
    }

    @Test public void realListTouchTogglesRowWhileInfoOnlyShowsDescription() {
        SwitchPreference preference = preference();
        preference.setChecked(false);
        ListView list = attach(preference);
        View row = list.getChildAt(0);
        assertNotNull(row);
        assertFalse("A secondary action must not disable native ListView item clicks", row.hasFocusable());
        View info = row.findViewWithTag(DescriptionInfo.TAG);
        assertNotNull(info);
        touch(list, info);
        AlertDialog description = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(description);
        assertEquals(DESCRIPTION, shadowOf(description).getMessage().toString());
        assertFalse(preference.isChecked());
        description.dismiss();
        touch(list, row.findViewById(android.R.id.title));
        assertTrue("Touching the label still flips the switch", preference.isChecked());
        touch(list, list.getChildAt(0).findViewById(android.R.id.switch_widget));
        assertFalse("Touching the switch still flips it", preference.isChecked());
    }

    @Test public void customChoiceRowHidesDescriptionAndRealTouchStillOpensPicker() {
        HushgramPreferenceFragment.ScrollBreakRow choice = new HushgramPreferenceFragment.ScrollBreakRow(controller.get());
        choice.setPersistent(false);
        choice.setTitle("Scroll break");
        choice.setEntries(new String[]{"Off", "Ten minutes"});
        choice.setEntryValues(new String[]{"OFF", "TEN_MINUTES"});
        choice.setValue("OFF");
        choice.setSummary(DESCRIPTION);
        ListView list = attach(choice);
        View row = list.getChildAt(0);
        assertFalse(row.hasFocusable());
        assertEquals(View.GONE, row.findViewById(android.R.id.summary).getVisibility());
        View info = row.findViewWithTag(DescriptionInfo.TAG);
        assertNotNull(info);
        assertNotNull(row.findViewWithTag(ScreenColors.CHEVRON));
        touch(list, info);
        AlertDialog description = ShadowAlertDialog.getLatestAlertDialog();
        assertEquals(DESCRIPTION, shadowOf(description).getMessage().toString());
        description.dismiss();
        touch(list, row.findViewById(android.R.id.title));
        assertTrue("Row touch opens the actual choice picker", choice.getDialog().isShowing());
        AlertDialog picker = (AlertDialog) choice.getDialog();
        ListView options = picker.getListView();
        options.performItemClick(options.getAdapter().getView(1, null, options), 1, options.getAdapter().getItemId(1));
        shadowOf(android.os.Looper.getMainLooper()).idleFor(1, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals("TEN_MINUTES", choice.getValue());
    }
}
