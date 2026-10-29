/*
 * Copyright 2026 HushGram contributors
 * https://github.com/SysAdminDoc/HushGram
 */
package app.hushgram.extension.instagram.settings;

import static org.junit.Assert.*;
import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;
import app.hushgram.extension.shared.SettingsContextRule;
import app.hushgram.extension.shared.Utils;
import app.hushgram.extension.shared.settings.BaseSettings;
import app.hushgram.extension.shared.settings.PauseForTests;

/** Production navigation, real preference objects and native dialog choice behavior. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 37}, qualifiers = "w320dp-h640dp-xhdpi")
@SuppressWarnings("deprecation")
public class SettingsCardsTest {
    @Rule public final SettingsContextRule settings = new SettingsContextRule();
    private ActivityController<Activity> controller;
    private HushgramPreferenceFragment page;

    @Before public void prepare() {
        RuntimeEnvironment.getApplication().getApplicationInfo().targetSdkVersion = 36;
        BaseSettings.PAUSED.save(false);
        PauseForTests.resume();
        BaseSettings.SAFE_MODE.save(false);
        Settings.SIGN_IN_NOTICE_HIDDEN.save(true);
    }

    @After public void close() throws Exception {
        if (controller != null) controller.close();
        Utils.awaitBackgroundTasksForTests();
        PatchFamily.inBuildForTests = null;
        ScreenColors.shown = null;
        Settings.CATEGORY_PAGES.resetToDefault();
        Settings.SIGN_IN_NOTICE_HIDDEN.resetToDefault();
        Settings.START_ON_FOLLOWING.resetToDefault();
        Settings.ONLY_FOLLOWING.resetToDefault();
        Settings.LOGO_ON_FOLLOWING.resetToDefault();
        Settings.ACCENT_COLOR.resetToDefault();
        Settings.ACCENT_BADGES.resetToDefault();
        RuntimeEnvironment.setFontScale(1f);
        SettingsEntry.onClosedByUser();
    }

    private void open(EnumSet<PatchFamily> build) throws Exception {
        PatchFamily.inBuildForTests = build;
        controller = Robolectric.buildActivity(Activity.class).setup();
        page = new HushgramPreferenceFragment();
        controller.get().getFragmentManager().beginTransaction().add(android.R.id.content, page, "settings").commitNow();
        Utils.awaitBackgroundTasksForTests();
        ShadowLooper.idleMainLooper();
    }

    private SettingsNavigation model() throws Exception {
        Field field = HushgramPreferenceFragment.class.getDeclaredField("navigation");
        field.setAccessible(true);
        return (SettingsNavigation) field.get(page);
    }

    private SettingsNavigation.Node category(String title) throws Exception {
        for (SettingsNavigation.Node node : model().categories) if (title.contentEquals(node.title)) return node;
        throw new AssertionError("Missing category: " + title);
    }

    private void tap(Preference row) {
        assertNotNull(row);
        assertTrue(row.getOnPreferenceClickListener().onPreferenceClick(row));
        ShadowLooper.idleMainLooper();
    }

    private View render(Preference preference) {
        View row = preference.getView(null, new FrameLayout(controller.get()));
        int width = Math.round(320 * row.getResources().getDisplayMetrics().density);
        row.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        row.layout(0, 0, width, row.getMeasuredHeight());
        return row;
    }

    @Test public void homeUsesStandardNavigationRowsAndNoExpandableHeadings() throws Exception {
        Settings.CATEGORY_PAGES.save(false);
        open(EnumSet.allOf(PatchFamily.class));
        assertEquals(23, model().categories.size());
        PreferenceScreen screen = page.getPreferenceScreen();
        for (int i = 0; i < screen.getPreferenceCount(); i++) assertFalse(screen.getPreference(i) instanceof PreferenceCategory);
        for (SettingsNavigation.Node node : model().categories) {
            assertSame(node.opener, screen.findPreference(node.opener.getKey()));
            assertTrue(node.opener instanceof HushgramPreferenceFragment.Row);
            View row = render(node.opener);
            assertNotNull(row.findViewWithTag(ScreenColors.CHEVRON));
            assertTrue(row.getHeight() >= 76 * row.getResources().getDisplayMetrics().density);
        }
        assertNull(screen.findPreference("hushgram_view_toggle"));
        assertNull(screen.findPreference(Settings.CATEGORY_PAGES.key));
        assertEquals("", page.currentPage());
    }

    @Test public void smallCategoryOpensItsSettingsWithoutAnotherLevel() throws Exception {
        open(EnumSet.of(PatchFamily.FOLLOWING_FEED));
        SettingsNavigation.Node feed = category("Feed");
        assertTrue(feed.children.isEmpty());
        tap(feed.opener);
        assertEquals(feed.id, page.currentPage());
        assertNotNull(page.getPreferenceScreen().findPreference(Settings.START_ON_FOLLOWING.key));
        assertNull(page.getPreferenceScreen().findPreference(category("Fork info").opener.getKey()));
        assertTrue(page.closeCategory());
        assertEquals("", page.currentPage());
        assertFalse(page.closeCategory());
    }

    @Test public void largeCategoriesOpenSubpagesAndBackReturnsOneLevel() throws Exception {
        open(EnumSet.allOf(PatchFamily.class));
        for (String title : new String[]{"Feed", "Reels", "Stories", "Tab bar", "Downloads", "Messages", "Profiles"}) {
            SettingsNavigation.Node category = category(title);
            assertTrue(title, category.children.size() >= 2);
            tap(category.opener);
            assertNull("No bulk toggle on a page of subcategories", page.getPreferenceScreen().findPreference(HushgramPreferenceFragment.ENABLE_ALL_KEY));
            SettingsNavigation.Node leaf = category.children.get(0);
            assertNotNull(page.getPreferenceScreen().findPreference(leaf.opener.getKey()));
            assertNull(page.getPreferenceScreen().findPreference(leaf.rows.get(0).getKey()));
            tap(leaf.opener);
            if (!leaf.toggles().isEmpty()) assertNotNull(page.getPreferenceScreen().findPreference(HushgramPreferenceFragment.ENABLE_ALL_KEY));
            assertEquals(leaf.id, page.currentPage());
            assertSame(leaf.rows.get(0), page.getPreferenceScreen().findPreference(leaf.rows.get(0).getKey()));
            assertTrue(page.closeCategory());
            assertEquals(category.id, page.currentPage());
            assertTrue(page.closeCategory());
            assertEquals("", page.currentPage());
        }
    }

    @Test public void everySettingHasExactlyOneDestinationAndKeepsItsObject() throws Exception {
        open(EnumSet.allOf(PatchFamily.class));
        Set<Preference> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        int count = 0;
        for (SettingsNavigation.Node category : model().categories) {
            count += category.count();
            visit(category, visited);
        }
        assertTrue(count > 300);
        assertEquals(count, visited.size());
        for (Preference row : visited) {
            assertNotNull(model().destination(row));
            if (row.getKey() != null) assertSame(row, page.findPreference(row.getKey()));
        }
    }

    private void visit(SettingsNavigation.Node node, Set<Preference> visited) {
        assertTrue(node.rows.isEmpty() || node.children.isEmpty());
        for (Preference row : node.rows) assertTrue("Duplicate row: " + row.getKey(), visited.add(row));
        for (SettingsNavigation.Node child : node.children) { assertSame(node, child.parent); visit(child, visited); }
    }

    @Test public void searchResultOpensTheRightLeafWithoutChangingTheSetting() throws Exception {
        open(EnumSet.allOf(PatchFamily.class));
        boolean before = Settings.START_ON_FOLLOWING.savedValue();
        Preference setting = page.findPreference(Settings.START_ON_FOLLOWING.key);
        SettingsNavigation.Node destination = model().destination(setting);
        page.searchSettings("Start Home on Following");
        Preference result = page.getPreferenceScreen().findPreference("hushgram_search_result_" + setting.getKey());
        assertNotNull(result);
        assertTrue(result.getSummary().toString().contains("Feed"));
        tap(result);
        assertEquals(destination.id, page.currentPage());
        assertSame(setting, page.getPreferenceScreen().findPreference(setting.getKey()));
        assertEquals(before, Settings.START_ON_FOLLOWING.savedValue());
        assertTrue(page.closeCategory());
        assertEquals(destination.parent.id, page.currentPage());
    }

    @Test public void backClearsSearchBeforeLeavingItsSourcePage() throws Exception {
        open(EnumSet.allOf(PatchFamily.class));
        SettingsNavigation.Node feed = category("Feed");
        tap(feed.opener);
        page.searchSettings("Hide ads");
        assertNotNull(page.getPreferenceScreen().findPreference("hushgram_search_result_" + Settings.HIDE_ADS.key));
        assertTrue(page.closeCategory());
        assertEquals(feed.id, page.currentPage());
        assertNotNull(page.getPreferenceScreen().findPreference(feed.children.get(0).opener.getKey()));
        assertTrue(page.closeCategory());
        assertEquals("", page.currentPage());
    }

    @Test public void nestedPageRestoresAfterActivityRecreation() throws Exception {
        open(EnumSet.allOf(PatchFamily.class));
        SettingsNavigation.Node feed = category("Feed");
        tap(feed.opener);
        SettingsNavigation.Node leaf = feed.children.get(0);
        tap(leaf.opener);
        String id = page.currentPage();
        String key = leaf.rows.get(0).getKey();
        controller.recreate();
        ShadowLooper.idleMainLooper();
        page = (HushgramPreferenceFragment) controller.get().getFragmentManager().findFragmentByTag("settings");
        assertEquals(id, page.currentPage());
        assertNotNull(page.getPreferenceScreen().findPreference(key));
        assertTrue(page.closeCategory());
        assertEquals(feed.id, page.currentPage());
    }

    @Test public void rippleIsMaskedAndThumbHasBreathingRoom() throws Exception {
        open(EnumSet.of(PatchFamily.HIDE_ADS));
        tap(category("Ads and privacy").opener);
        View row = render(page.findPreference(Settings.HIDE_ADS.key));
        RippleDrawable ripple = (RippleDrawable) row.getBackground();
        assertNotNull(ripple.findDrawableByLayerId(android.R.id.mask));
        Switch toggle = row.findViewById(android.R.id.switch_widget);
        InsetDrawable thumb = (InsetDrawable) toggle.getThumbDrawable();
        assertTrue(thumb.getDrawable() instanceof SwitchThumb);
        float density = row.getResources().getDisplayMetrics().density;
        assertEquals(Math.round(20 * density), thumb.getDrawable().getIntrinsicHeight());
        assertEquals(Math.round(32 * density), toggle.getTrackDrawable().getIntrinsicHeight());
        Rect padding = new Rect();
        toggle.getTrackDrawable().getPadding(padding);
        assertEquals(Math.round(6 * density), padding.left);
        assertEquals(padding.left, padding.right);
        assertEquals(padding.left, padding.top);
        assertEquals(padding.top, padding.bottom);
    }

    @Test public void singleAndMultipleChoicesKeepNativeSelectionAndUseRoundedRows() throws Exception {
        open(EnumSet.of(PatchFamily.HIDE_ADS));
        for (boolean multiple : new boolean[]{false, true}) {
            boolean[] clicked = {false};
            AlertDialog.Builder builder = new AlertDialog.Builder(controller.get()).setTitle("Choose");
            if (multiple) builder.setMultiChoiceItems(new String[]{"First", "Second"}, new boolean[]{false, false},
                    (dialog, which, checked) -> clicked[0] = which == 1 && checked);
            else builder.setSingleChoiceItems(new String[]{"First", "Second"}, 0,
                    (dialog, which) -> clicked[0] = which == 1);
            AlertDialog dialog = builder.create();
            dialog.show();
            ScreenColors.current().paint(dialog);
            ListView list = dialog.getListView();
            View row = list.getAdapter().getView(1, null, list);
            assertTrue(row instanceof CheckedTextView);
            InsetDrawable background = (InsetDrawable) ((RippleDrawable) row.getBackground()).getDrawable(0);
            GradientDrawable surface = (GradientDrawable) background.getDrawable();
            assertEquals(ScreenColors.current().iconSurface, surface.getColor().getDefaultColor());
            assertEquals(ScreenColors.current().choiceSelected(), surface.getColor().getColorForState(
                    new int[]{android.R.attr.state_checked}, 0));
            assertNotEquals(ScreenColors.current().dialog, surface.getColor().getDefaultColor());
            assertTrue(((CheckedTextView) row).getCheckMarkDrawable() instanceof SelectionIndicator);
            assertNotNull(((RippleDrawable) row.getBackground()).findDrawableByLayerId(android.R.id.mask));
            list.performItemClick(row, 1, list.getAdapter().getItemId(1));
            assertTrue(clicked[0]);
            assertTrue(list.isItemChecked(1));
            dialog.dismiss();
        }
    }

    @Test public void dialogButtonsStackFullWidthAndPreserveDismissal() throws Exception {
        open(EnumSet.of(PatchFamily.HIDE_ADS));
        boolean[] clicked = {false};
        AlertDialog dialog = new AlertDialog.Builder(controller.get()).setTitle("Actions")
                .setPositiveButton("Save", (d, which) -> clicked[0] = true)
                .setNegativeButton("Cancel", null).setNeutralButton("Reset", null).create();
        dialog.show();
        ScreenColors.current().paint(dialog);
        ScreenColors.current().paint(dialog);
        Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        LinearLayout column = (LinearLayout) save.getParent();
        assertEquals(LinearLayout.VERTICAL, column.getOrientation());
        assertSame(column, dialog.getButton(AlertDialog.BUTTON_NEGATIVE).getParent());
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, save.getLayoutParams().width);
        assertEquals(3, column.getChildCount());
        save.performClick();
        ShadowLooper.idleMainLooper();
        assertTrue(clicked[0]);
        assertFalse(dialog.isShowing());
    }

    @Test public void dependentSwitchRetainsItsChoiceWhenTheParentDisablesIt() throws Exception {
        Settings.START_ON_FOLLOWING.save(true);
        Settings.ONLY_FOLLOWING.save(true);
        open(EnumSet.of(PatchFamily.FOLLOWING_FEED));
        Preference child = page.findPreference(Settings.ONLY_FOLLOWING.key);
        Settings.START_ON_FOLLOWING.save(false);
        page.updateUIAvailability();
        Switch toggle = render(child).findViewById(android.R.id.switch_widget);
        assertFalse(toggle.isEnabled());
        assertTrue(toggle.isChecked());
        assertTrue(Settings.ONLY_FOLLOWING.savedValue());
        Settings.START_ON_FOLLOWING.save(true);
        page.updateUIAvailability();
        Switch restored = render(child).findViewById(android.R.id.switch_widget);
        assertTrue(restored.isEnabled());
        assertTrue(restored.isChecked());
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void disabledSwitchThumbStaysDistinctForBothSavedStates() throws Exception {
        open(EnumSet.of(PatchFamily.HIDE_ADS));
        for (boolean checked : new boolean[]{false, true}) {
            AnimatedSettingsSwitch toggle = new AnimatedSettingsSwitch(controller.get());
            toggle.setChecked(checked);
            toggle.setEnabled(false);
            float density = toggle.getResources().getDisplayMetrics().density;
            int width = Math.round(52 * density), height = Math.round(32 * density);
            toggle.setPadding(0, 0, 0, 0);
            toggle.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            toggle.layout(0, 0, width, height);
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(width, height,
                    android.graphics.Bitmap.Config.ARGB_8888);
            toggle.draw(new android.graphics.Canvas(bitmap));
            int thumbX = Math.round((checked ? 36 : 16) * density);
            int thumbColor = bitmap.getPixel(thumbX, Math.round(10 * density));
            int trackColor = bitmap.getPixel(Math.round(26 * density), Math.round(4 * density));
            assertEquals(ScreenColors.current().title, thumbColor);
            assertEquals(ScreenColors.current().switchOff, trackColor);
            assertTrue((android.graphics.Color.luminance(thumbColor) + 0.05f)
                    / (android.graphics.Color.luminance(trackColor) + 0.05f) >= 3);
            assertEquals(checked, toggle.isChecked());
        }
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void navigationAndDescriptionsFitAtDoubleFontSize() throws Exception {
        RuntimeEnvironment.setFontScale(2f);
        open(EnumSet.allOf(PatchFamily.class));
        SettingsNavigation.Node feed = category("Feed");
        for (Preference preference : new Preference[]{feed.opener, feed.children.get(0).opener,
                page.findPreference(Settings.START_ON_FOLLOWING.key)}) {
            View row = render(preference);
            TextView title = row.findViewById(android.R.id.title);
            TextView summary = row.findViewById(android.R.id.summary);
            assertNotNull(title.getLayout());
            for (int i = 0; i < title.getLayout().getLineCount(); i++) assertEquals(0, title.getLayout().getEllipsisCount(i));
            assertEquals(Integer.MAX_VALUE, summary.getMaxLines());
            assertNull(summary.getEllipsize());
            assertTrue(summary.getLayout().getHeight() <= summary.getHeight());
            assertTrue(title.getHeight() + row.getPaddingTop() + row.getPaddingBottom() <= row.getHeight());
        }
    }

    @Test public void forkInfoIsFirstAndContainsForkUpstreamAndSupportLinks() throws Exception {
        open(EnumSet.allOf(PatchFamily.class));
        SettingsNavigation.Node fork = category("Fork info");
        assertSame(fork, model().categories.get(0));
        assertNull(page.getPreferenceScreen().findPreference(HushgramPreferenceFragment.REPORT_ROW_KEY));
        assertNull(page.getPreferenceScreen().findPreference(HushgramPreferenceFragment.SUPPORT_ROW_KEY));
        tap(fork.opener);
        assertEquals(8, fork.rows.size());
        assertNotNull(page.getPreferenceScreen().findPreference(HushgramPreferenceFragment.REPORT_ROW_KEY));
        assertNotNull(page.getPreferenceScreen().findPreference(HushgramPreferenceFragment.SUPPORT_ROW_KEY));
        assertNull(page.getPreferenceScreen().findPreference(HushgramPreferenceFragment.ENABLE_ALL_KEY));
        tap(page.findPreference("primegram_developer"));
        assertEquals("https://github.com/FarhanZafarr-9", org.robolectric.Shadows.shadowOf(controller.get()).getNextStartedActivity().getDataString());
        tap(page.findPreference("primegram_source"));
        assertEquals("https://github.com/FarhanZafarr-9/InstaPrime", org.robolectric.Shadows.shadowOf(controller.get()).getNextStartedActivity().getDataString());
        assertTrue(page.findPreference("primegram_upstream_developer").getSummary().toString().contains("HushGram"));
        assertTrue(page.findPreference("primegram_version").getSummary().toString().startsWith("InstaPrime"));
        assertEquals("InstaPrime settings", app.hushgram.extension.shared.L10n.t("HushGram settings"));
    }

    @Test public void spacingAndRippleMaskShareTheSameInsetBounds() throws Exception {
        open(EnumSet.allOf(PatchFamily.class));
        View row = render(category("Fork info").opener);
        RippleDrawable ripple = (RippleDrawable) row.getBackground();
        ripple.setBounds(0, 0, row.getWidth(), row.getHeight());
        InsetDrawable content = (InsetDrawable) ripple.getDrawable(0);
        InsetDrawable mask = (InsetDrawable) ripple.findDrawableByLayerId(android.R.id.mask);
        assertEquals(android.graphics.drawable.LayerDrawable.PADDING_MODE_STACK, ripple.getPaddingMode());
        assertEquals(content.getDrawable().getBounds(), mask.getDrawable().getBounds());
        float density = row.getResources().getDisplayMetrics().density;
        assertEquals(Math.round(18 * density), content.getDrawable().getBounds().left);
        assertEquals(row.getWidth() - Math.round(18 * density), content.getDrawable().getBounds().right);
        assertEquals(Math.round(16 * density), content.getDrawable().getBounds().top);
        ListView list = page.getView().findViewById(android.R.id.list);
        assertEquals(Math.round(32 * density), list.getPaddingBottom());
        assertFalse(list.getClipToPadding());
    }

    @Test public void enableAllWorksOnANestedPageWithoutChangingOtherCategories() throws Exception {
        open(EnumSet.allOf(PatchFamily.class));
        Preference target = page.findPreference(Settings.START_ON_FOLLOWING.key);
        SettingsNavigation.Node leaf = model().destination(target);
        tap(leaf.opener);
        android.preference.TwoStatePreference all = (android.preference.TwoStatePreference)
                page.getPreferenceScreen().findPreference(HushgramPreferenceFragment.ENABLE_ALL_KEY);
        assertNotNull(all);
        assertFalse(all.isPersistent());
        boolean adsBefore = Settings.HIDE_ADS.savedValue();
        assertFalse(all.getOnPreferenceChangeListener().onPreferenceChange(all, true));
        for (android.preference.TwoStatePreference row : leaf.toggles()) assertTrue(row.getTitle().toString(), row.isChecked());
        assertEquals(adsBefore, Settings.HIDE_ADS.savedValue());
        all.getOnPreferenceChangeListener().onPreferenceChange(all, false);
        for (android.preference.TwoStatePreference row : leaf.toggles()) assertFalse(row.getTitle().toString(), row.isChecked());
    }

    @Test public void enableAllLeavesChoiceValuesAloneAndRespectsRejectedChanges() throws Exception {
        open(EnumSet.of(PatchFamily.ACCENT_COLOR));
        SettingsNavigation.Node layout = category("Layout");
        tap(layout.opener);
        Object hueBefore = Settings.ACCENT_HUE.savedValue();
        android.preference.TwoStatePreference refused = layout.toggles().get(0);
        refused.setChecked(false);
        refused.setOnPreferenceChangeListener((row, on) -> false);
        Preference all = page.getPreferenceScreen().findPreference(HushgramPreferenceFragment.ENABLE_ALL_KEY);
        all.getOnPreferenceChangeListener().onPreferenceChange(all, true);
        assertFalse(refused.isChecked());
        assertEquals(hueBefore, Settings.ACCENT_HUE.savedValue());
    }

    @Test public void settingsEntryUsesTheSuppliedInstaPrimeIcon() throws Exception {
        open(EnumSet.of(PatchFamily.HIDE_ADS));
        LinearLayout wrapper = (LinearLayout) SettingsScreenRow.above(new View(controller.get()));
        LinearLayout entry = (LinearLayout) wrapper.getChildAt(0);
        android.widget.ImageView image = (android.widget.ImageView) entry.getChildAt(0);
        assertSame(InstaPrimeIcon.bitmap(), ((android.graphics.drawable.BitmapDrawable) image.getDrawable()).getBitmap());
        assertEquals(512, InstaPrimeIcon.bitmap().getWidth());
        assertEquals(512, InstaPrimeIcon.bitmap().getHeight());
        assertTrue(((TextView) entry.getChildAt(1)).getText().toString().contains("InstaPrime"));
    }

    @Test public void restartNoticeHasADedicatedFullWidthAction() throws Exception {
        open(EnumSet.of(PatchFamily.HIDE_ADS));
        SettingsDialog dialog = new SettingsDialog();
        dialog.show(controller.get().getFragmentManager(), "restart_notice_test");
        controller.get().getFragmentManager().executePendingTransactions();
        ShadowLooper.idleMainLooper();
        dialog.showRestart(true);
        LinearLayout card = dialog.getView().findViewById(SettingsDialog.RESTART_ID);
        assertNotNull(card);
        assertEquals(View.VISIBLE, card.getVisibility());
        assertEquals("Restart to apply changes", ((TextView) card.getChildAt(0)).getText().toString());
        assertNull(((TextView) card.getChildAt(0)).getPaint().getShader());
        Button action = card.findViewById(SettingsDialog.RESTART_ACTION_ID);
        assertEquals("Restart now", action.getText().toString());
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, action.getLayoutParams().width);
        assertTrue(action.hasOnClickListeners());
        dialog.showRestart(false);
        assertEquals(View.GONE, card.getVisibility());
        dialog.dismiss();
    }

    @Test public void switchAnimationSurvivesPreferenceRebindingAndReplacement() throws Exception {
        open(EnumSet.of(PatchFamily.HIDE_ADS));
        HushgramPreferenceFragment.Toggle preference = (HushgramPreferenceFragment.Toggle)
                page.findPreference(Settings.HIDE_ADS.key);
        View row = render(preference);
        boolean before = preference.isChecked();
        assertEquals(before ? 1f : 0f, preference.motion.progress, 0.001f);
        // A row tap changes the preference; Android can discard its view during dependency refresh.
        preference.setChecked(!before);
        View replacement = render(preference);
        assertNotSame(row, replacement);
        Switch toggle = replacement.findViewById(android.R.id.switch_widget);
        assertTrue(toggle instanceof AnimatedSettingsSwitch);
        toggle.jumpDrawablesToCurrentState();
        android.animation.ValueAnimator animation = preference.motion.transition;
        if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
            assertNotNull(animation);
            assertEquals(before ? 1f : 0f, preference.motion.progress, 0.001f);
            animation.setCurrentPlayTime(110);
            float halfway = preference.motion.progress;
            assertTrue(halfway > 0f && halfway < 1f);
            render(preference); // Another replacement must retain both animator and halfway state.
            assertSame(animation, preference.motion.transition);
            assertEquals(halfway, preference.motion.progress, 0.001f);
            // Rapid reversal starts at the current position, without a jump to either endpoint.
            preference.setChecked(before);
            render(preference);
            assertEquals(halfway, preference.motion.progress, 0.001f);
            preference.motion.transition.end();
            assertEquals(before ? 1f : 0f, preference.motion.progress, 0.001f);
        }
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void instaPrimeMarkHasGentlyRoundedTransparentCorners() throws Exception {
        open(EnumSet.of(PatchFamily.HIDE_ADS));
        android.graphics.Bitmap icon = InstaPrimeIcon.bitmap();
        assertEquals(0, android.graphics.Color.alpha(icon.getPixel(0, 0)));
        assertEquals(0, android.graphics.Color.alpha(icon.getPixel(511, 511)));
        assertEquals(0, android.graphics.Color.alpha(icon.getPixel(10, 10)));
        assertEquals(255, android.graphics.Color.alpha(icon.getPixel(256, 256)));
        assertEquals(android.graphics.Color.WHITE, icon.getPixel(256, 20));
        java.io.File preview = new java.io.File("build/instaprime-icon-preview-" + Build.VERSION.SDK_INT + ".png");
        preview.getParentFile().mkdirs();
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(preview)) {
            assertTrue(icon.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output));
        }
    }

    @Test public void wallpaperTokensFollowTheSystemPalette() throws Exception {
        open(EnumSet.of(PatchFamily.HIDE_ADS));
        ScreenColors colors = ScreenColors.current();
        if (Build.VERSION.SDK_INT >= 31) {
            assertEquals(controller.get().getColor(android.R.color.system_accent1_200), colors.accent);
            assertEquals(controller.get().getColor(android.R.color.system_neutral1_900), colors.card);
        } else assertEquals(ScreenColors.DEFAULT.card, colors.card);
    }
}
