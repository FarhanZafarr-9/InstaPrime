/* Copyright 2026 HushGram and InstaPrime contributors. */
package app.hushgram.extension.instagram.settings;

import static org.junit.Assert.*;

import android.app.Activity;
import android.content.SharedPreferences;
import android.preference.*;
import android.view.View;
import android.widget.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import app.hushgram.extension.shared.SettingsContextRule;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 37})
public class NewRowDotsTest {
    @Rule public final SettingsContextRule settings = new SettingsContextRule();
    ActivityController<Activity> controller;
    SharedPreferences store;
    Preference a, b;
    PreferenceCategory group;
    Map<PreferenceCategory, List<Preference>> pages;

    @Before public void setup() {
        controller = Robolectric.buildActivity(Activity.class).setup();
        store = controller.get().getSharedPreferences(NewRowDots.FILE, 0);
        store.edit().clear().commit();
        group = new PreferenceCategory(controller.get());
        a = HushgramPreferenceFragment.toggle(controller.get(), Settings.HIDE_ADS, "Hide ads", "Description");
        b = HushgramPreferenceFragment.toggle(controller.get(), Settings.START_ON_FOLLOWING, "Following feed", "Description");
        HushgramPreferenceFragment.mark(a, SettingsIcons.SETTINGS);
        HushgramPreferenceFragment.mark(b, SettingsIcons.SETTINGS);
        pages = new LinkedHashMap<>();
        pages.put(group, Arrays.asList(a, b));
    }

    @After public void close() {
        NewRowDots.stop();
        Settings.DESCRIPTIONS_BEHIND_INFO.resetToDefault();
        controller.close();
        ScreenColors.shown = null;
    }
    void unseen() {
        store.edit().putStringSet(NewRowDots.SEEN, new HashSet<>()).commit();
        NewRowDots.start(controller.get(), pages);
    }
    Preference opener(SettingsNavigation.Node node) {
        Preference p = new HushgramPreferenceFragment.Row(controller.get());
        p.setTitle(node.title);
        HushgramPreferenceFragment.mark(p, SettingsIcons.SETTINGS);
        NewRowDots.opens(p, node);
        return p;
    }
    View render(Preference p) {
        return p.getView(null, new FrameLayout(controller.get()));
    }

    @Test public void firstOpenSeedsSeenKeys() {
        NewRowDots.start(controller.get(), pages);
        assertFalse(NewRowDots.freshRow(a));
        assertFalse(NewRowDots.freshRow(b));
        assertTrue(store.getStringSet(NewRowDots.SEEN, null).contains(a.getKey()));
    }

    @Test public void ancestorsAggregateOnlyTheirOwnDescendants() {
        unseen();
        SettingsNavigation.Node root = new SettingsNavigation.Node("root", null, group, "Root", SettingsIcons.SETTINGS);
        SettingsNavigation.Node one = new SettingsNavigation.Node("one", root, group, "One", SettingsIcons.SETTINGS);
        SettingsNavigation.Node two = new SettingsNavigation.Node("two", root, group, "Two", SettingsIcons.SETTINGS);
        one.rows.add(a);
        two.rows.add(b);
        root.children.add(one);
        root.children.add(two);
        Preference r = opener(root), x = opener(one), y = opener(two);
        assertTrue(NewRowDots.freshRow(r));
        assertTrue(NewRowDots.freshRow(x));
        assertTrue(NewRowDots.freshRow(y));
        NewRowDots.leftRows(root.rows);
        assertTrue(NewRowDots.freshRow(x));
        NewRowDots.leftRows(one.rows);
        assertFalse(NewRowDots.freshRow(x));
        assertTrue(NewRowDots.freshRow(y));
        assertTrue(NewRowDots.freshRow(r));
        NewRowDots.leftRows(two.rows);
        assertFalse(NewRowDots.freshRow(r));
    }

    @Test public void badgeIsOnIconAndCoexistsWithInfoButton() {
        unseen();
        Settings.DESCRIPTIONS_BEHIND_INFO.save(true);
        View row = render(a);
        ImageView icon = row.findViewById(android.R.id.icon);
        assertEquals(NewRowDots.TAG, icon.getTag());
        assertTrue(icon.getDrawable() instanceof NewRowDots.BadgedIcon);
        assertNotNull(row.findViewWithTag(DescriptionInfo.TAG));
        assertNull(((TextView)row.findViewById(android.R.id.title)).getCompoundDrawablesRelative()[2]);
        NewRowDots.leftRows(Arrays.asList(a));
        row = render(a);
        icon = row.findViewById(android.R.id.icon);
        assertNull(icon.getTag());
        assertFalse(icon.getDrawable() instanceof NewRowDots.BadgedIcon);
        assertNotNull(row.findViewWithTag(DescriptionInfo.TAG));
    }

    @Test public void seenKeysSurviveReopening() {
        unseen();
        NewRowDots.leftRows(Arrays.asList(a));
        NewRowDots.stop();
        NewRowDots.start(controller.get(), pages);
        assertFalse(NewRowDots.freshRow(a));
        assertTrue(NewRowDots.freshRow(b));
    }

    @Test public void searchesNeitherDotNorMarkUnvisitedRowsSeen() {
        unseen();
        NewRowDots.searching(true);
        assertFalse(NewRowDots.freshRow(a));
        NewRowDots.leftRows(Arrays.asList(a));
        NewRowDots.searching(false);
        assertTrue(NewRowDots.freshRow(a));
    }

    @Test public void corruptSeenValueRecoversAndSeenSetStaysOutOfBackups() {
        store.edit().putString(NewRowDots.SEEN, "bad").commit();
        NewRowDots.start(controller.get(), pages);
        assertFalse(NewRowDots.freshRow(a));
        for(String key:ConfigurationBackup.eligible().keySet())assertFalse(key.contains(NewRowDots.FILE)||key.equals(NewRowDots.SEEN));
    }

    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void unseenHighlightClearsWithBadgeAndBadgeDrawsAtTopLeft() {
        unseen();
        View row = render(a);
        android.graphics.drawable.RippleDrawable ripple = (android.graphics.drawable.RippleDrawable)row.getBackground();
        android.graphics.drawable.GradientDrawable surface = (android.graphics.drawable.GradientDrawable)((android.graphics.drawable.InsetDrawable)ripple.getDrawable(0)).getDrawable();
        int freshColor = surface.getColor().getDefaultColor();
        NewRowDots.BadgedIcon badge = (NewRowDots.BadgedIcon)((ImageView)row.findViewById(android.R.id.icon)).getDrawable();
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(24, 24, android.graphics.Bitmap.Config.ARGB_8888);
        badge.setBounds(0, 0, 24, 24);
        badge.draw(new android.graphics.Canvas(bitmap));
        assertEquals(ScreenColors.current().accent, bitmap.getPixel(4, 4));
        bitmap.recycle();
        NewRowDots.leftRows(Arrays.asList(a));
        row = render(a);
        ripple = (android.graphics.drawable.RippleDrawable)row.getBackground();
        surface = (android.graphics.drawable.GradientDrawable)((android.graphics.drawable.InsetDrawable)ripple.getDrawable(0)).getDrawable();
        assertNotEquals(freshColor, surface.getColor().getDefaultColor());
        assertEquals(ScreenColors.current().card, surface.getColor().getDefaultColor());
    }
}
