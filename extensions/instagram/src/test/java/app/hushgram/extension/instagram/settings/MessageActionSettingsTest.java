package app.hushgram.extension.instagram.settings;

import static org.junit.Assert.*;

import android.app.Activity;
import android.app.AlertDialog;
import android.preference.Preference;
import android.widget.ListView;
import java.util.EnumSet;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowLooper;
import app.hushgram.extension.instagram.direct.MessageActions;
import app.hushgram.extension.shared.SettingsContextRule;
import app.hushgram.extension.shared.Utils;
import app.hushgram.extension.shared.settings.BaseSettings;
import app.hushgram.extension.shared.settings.PauseForTests;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 37})
@SuppressWarnings("deprecation")
public class MessageActionSettingsTest {
    @Rule public final SettingsContextRule settings = new SettingsContextRule();
    private ActivityController<Activity> controller;
    private HushgramPreferenceFragment page;

    @Before public void prepare() {
        Settings.HIDE_MESSAGE_ACTIONS.resetToDefault();
        Settings.MORE_MESSAGE_ACTIONS.resetToDefault();
        Settings.MESSAGE_TAP_ACTIONS.resetToDefault();
        Settings.MESSAGE_TAP_REACTIONS.resetToDefault();
        BaseSettings.PAUSED.save(false);
        PauseForTests.resume();
        BaseSettings.SAFE_MODE.save(false);
        Settings.SIGN_IN_NOTICE_HIDDEN.save(true);
    }

    @After public void close() throws Exception {
        if (controller != null) controller.close();
        Utils.awaitBackgroundTasksForTests();
        PatchFamily.inBuildForTests = null;
        prepare();
        Settings.SIGN_IN_NOTICE_HIDDEN.resetToDefault();
    }

    private void open(boolean installed) throws Exception {
        PatchFamily.inBuildForTests = installed ? EnumSet.of(PatchFamily.MESSAGE_ACTIONS, PatchFamily.MESSAGE_GESTURES)
                : EnumSet.noneOf(PatchFamily.class);
        controller = Robolectric.buildActivity(Activity.class).setup();
        page = new HushgramPreferenceFragment();
        controller.get().getFragmentManager().beginTransaction().add(android.R.id.content, page).commitNow();
        Utils.awaitBackgroundTasksForTests();
    }

    @Test public void controlsAreGatedByInstalledPatchesAndStartOff() throws Exception {
        open(false);
        assertNull(page.findPreference(Settings.HIDE_MESSAGE_ACTIONS.key));
        assertNull(page.findPreference(Settings.MESSAGE_TAP_ACTIONS.key));
        assertFalse(Settings.MESSAGE_TAP_ACTIONS.savedValue());
        assertFalse(Settings.MESSAGE_TAP_REACTIONS.savedValue());
    }

    @Test public void checkboxDialogsKeepSelectionsExclusiveAndMoreReachable() throws Exception {
        open(true);
        Settings.MORE_MESSAGE_ACTIONS.save("REPLY,FORWARD");
        Preference row = page.findPreference(Settings.HIDE_MESSAGE_ACTIONS.key);
        row.getOnPreferenceClickListener().onPreferenceClick(row);
        ShadowLooper.idleMainLooper();
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        ListView list = dialog.getListView();
        int reply = MessageActions.OFFERED.indexOf("REPLY");
        assertTrue(list.performItemClick(list.getAdapter().getView(reply, null, list), reply, reply));
        assertEquals("REPLY", Settings.HIDE_MESSAGE_ACTIONS.savedValue());
        assertEquals("FORWARD", Settings.MORE_MESSAGE_ACTIONS.savedValue());
        int more = MessageActions.OFFERED.indexOf("MORE");
        assertTrue(list.performItemClick(list.getAdapter().getView(more, null, list), more, more));
        assertEquals("", Settings.MORE_MESSAGE_ACTIONS.savedValue());
        dialog.dismiss();
        Preference moved = page.findPreference(Settings.MORE_MESSAGE_ACTIONS.key);
        moved.getOnPreferenceClickListener().onPreferenceClick(moved);
        ShadowLooper.idleMainLooper();
        list = ShadowAlertDialog.getLatestAlertDialog().getListView();
        assertEquals(MessageActions.OFFERED.size() - 1, list.getCount());
        assertTrue(list.performItemClick(list.getAdapter().getView(reply, null, list), reply, reply));
        assertEquals("", Settings.HIDE_MESSAGE_ACTIONS.savedValue());
        assertEquals("REPLY", Settings.MORE_MESSAGE_ACTIONS.savedValue());
        assertTrue(ConfigurationBackup.eligible().containsKey(Settings.HIDE_MESSAGE_ACTIONS.key));
        assertTrue(ConfigurationBackup.eligible().containsKey(Settings.MORE_MESSAGE_ACTIONS.key));
    }

    @Test public void everyActionChoiceFitsTheBackupAndRestores() throws Exception {
        open(true);
        String all = MessageActions.join(new java.util.LinkedHashSet<>(MessageActions.OFFERED));
        Settings.HIDE_MESSAGE_ACTIONS.save(all);
        byte[] backup = ConfigurationBackup.export();
        Settings.HIDE_MESSAGE_ACTIONS.resetToDefault();
        assertEquals(0, ConfigurationBackup.restore(backup).skipped);
        assertEquals(all, Settings.HIDE_MESSAGE_ACTIONS.savedValue());
        assertFalse(ConfigurationBackup.textAllowed(Settings.MORE_MESSAGE_ACTIONS, "MORE"));
    }
}
