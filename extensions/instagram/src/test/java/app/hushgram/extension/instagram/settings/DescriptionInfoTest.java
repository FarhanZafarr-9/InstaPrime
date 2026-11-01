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
}
