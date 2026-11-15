/*
 * Copyright 2026 HushGram contributors
 * https://github.com/SysAdminDoc/HushGram
 */
package app.hushgram.extension.instagram.direct;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import app.hushgram.extension.instagram.settings.Settings;
import app.hushgram.extension.shared.SettingsContextRule;
import app.hushgram.extension.shared.settings.BaseSettings;
import app.hushgram.extension.shared.settings.HushgramPause;
import app.hushgram.extension.shared.settings.PauseForTests;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 37})
public class MessageGesturesTest {
    @Test public void floatingMenusKeepTheirRightGapWithoutMovingInsetMenus() {
        assertEquals(772, MessageMenuPosition.insetRight(780, 300, 1080, 8));
        assertEquals(40, MessageMenuPosition.insetRight(40, 300, 1080, 8));
        assertEquals(700, MessageMenuPosition.insetRight(700, 300, 1080, 8));
        assertEquals(0, MessageMenuPosition.insetRight(0, 1080, 1080, 8));
        assertEquals(540, MessageMenuPosition.insetRight(548, 300, 856, 16));
    }

    @Test public void moreKeepsTheOriginalMenusAlignedEdgeWhenItsWidthChanges() {
        android.graphics.Rect frame = new android.graphics.Rect(0, 0, 1080, 1920);
        android.graphics.Rect outgoing = new android.graphics.Rect(756, 400, 1056, 800);
        assertEquals(656, MessageMenuPosition.submenuLeft(outgoing, frame, 400, 12));
        assertEquals(856, MessageMenuPosition.submenuLeft(outgoing, frame, 200, 12));
        android.graphics.Rect incoming = new android.graphics.Rect(24, 400, 324, 800);
        assertEquals(24, MessageMenuPosition.submenuLeft(incoming, frame, 400, 12));
        android.graphics.Rect touching = new android.graphics.Rect(780, 400, 1080, 800);
        assertEquals(668, MessageMenuPosition.submenuLeft(touching, frame, 400, 12));
    }

    @Rule public final SettingsContextRule settings = new SettingsContextRule();
    private final List<String> actions = Arrays.asList("Reply", "More");

    @Before public void before() {
        BaseSettings.PAUSED.save(false);
        PauseForTests.resume();
        Settings.MESSAGE_TAP_ACTIONS.resetToDefault();
        Settings.MESSAGE_TAP_REACTIONS.resetToDefault();
        MessageGestures.inBuildForTests = true;
    }

    @After public void after() {
        Settings.MESSAGE_TAP_ACTIONS.resetToDefault();
        Settings.MESSAGE_TAP_REACTIONS.resetToDefault();
        BaseSettings.PAUSED.save(false);
        PauseForTests.resume();
        MessageGestures.inBuildForTests = null;
        MessageGestures.bridgeForTests = null;
    }

    @Test public void defaultsAndPauseLeaveNativeTapAlone() {
        AtomicInteger calls = new AtomicInteger();
        MessageGestures.bridgeForTests = delegate -> { calls.incrementAndGet(); return true; };
        assertFalse(MessageGestures.tap(new Object()));
        Settings.MESSAGE_TAP_ACTIONS.save(true);
        BaseSettings.PAUSED.save(true);
        PauseForTests.pause(HushgramPause.Reason.SWITCH);
        assertFalse(MessageGestures.tap(new Object()));
        BaseSettings.PAUSED.save(false);
        MessageGestures.inBuildForTests = false;
        assertFalse(MessageGestures.tap(new Object()));
        assertEquals(0, calls.get());
    }

    @Test public void sharedMediaDoesNotTakeTheTextMessageTapShortcut() {
        assertTrue(MessageGestures.intercepts("text"));
        assertFalse(MessageGestures.intercepts("clip"));
        assertFalse(MessageGestures.intercepts("media_share"));
        assertFalse(MessageGestures.intercepts(null));
    }

    @Test public void fasterSingleTapKeepsEnabledDoubleTapsAndMediaRecognition() {
        assertTrue(MessageGestures.keepDoubleTap(true, true, true));
        Settings.MESSAGE_TAP_REACTIONS.save(true);
        assertFalse(MessageGestures.keepDoubleTap(true, true, true));
        assertTrue(MessageGestures.keepDoubleTap(true, true, false));
        assertTrue(MessageGestures.keepDoubleTap(true, false, true));
        assertFalse(MessageGestures.keepDoubleTap(false, true, false));
    }

    @Test public void reactionsWithoutAvailabilityDoNotOpenAnEmptyBackdrop() {
        Settings.MESSAGE_TAP_REACTIONS.save(true);
        AtomicBoolean launched = new AtomicBoolean();
        MessageGestures.bridgeForTests = delegate -> {
            MessageGestures.actions(actions);
            MessageGestures.reactions(false);
            MessageGestures.requireVisible();
            launched.set(true);
            return true;
        };
        assertTrue(MessageGestures.tap(new Object()));
        assertFalse(launched.get());
        assertSame(actions, MessageGestures.actions(actions));
    }

    @Test public void nativeReactionAnchorUsesTheActualTapPosition() {
        Settings.MESSAGE_TAP_REACTIONS.save(true);
        android.view.MotionEvent down = android.view.MotionEvent.obtain(1, 1, android.view.MotionEvent.ACTION_DOWN, 80, 240, 0);
        try { MessageGestures.observe(down); } finally { down.recycle(); }
        android.view.MotionEvent up = android.view.MotionEvent.obtain(1, 2, android.view.MotionEvent.ACTION_UP, 80, 240, 0);
        try { MessageGestures.observe(up); } finally { up.recycle(); }
        MessageGestures.bridgeForTests = delegate -> {
            assertEquals(80f, MessageGestures.x(), 0f);
            assertEquals(240f, MessageGestures.y(), 0f);
            MessageGestures.reactions(true);
            MessageGestures.requireVisible();
            return true;
        };
        assertTrue(MessageGestures.tap(new Object()));
    }

    @Test public void tapSectionsAreIndependentAndNativeHoldIsUnchanged() {
        for (int mode = 1; mode <= 3; mode++) {
            Settings.MESSAGE_TAP_ACTIONS.save((mode & 1) != 0);
            Settings.MESSAGE_TAP_REACTIONS.save((mode & 2) != 0);
            final int selected = mode;
            MessageGestures.bridgeForTests = delegate -> {
                if ((selected & 1) != 0) assertSame(actions, MessageGestures.actions(actions));
                else assertTrue(MessageGestures.actions(actions).isEmpty());
                assertEquals((selected & 2) != 0, MessageGestures.reactions(true));
                assertFalse(MessageGestures.reactions(false));
                assertEquals("only reaction-only mode removes action-pane compensation", selected != 2,
                        MessageGestures.floatingActions(true));
                assertFalse(MessageGestures.floatingActions(false));
                return true;
            };
            assertTrue(MessageGestures.tap(new Object()));
            assertTrue("native holds keep their layout flag", MessageGestures.floatingActions(true));
            assertSame(actions, MessageGestures.actions(actions));
            assertTrue(MessageGestures.reactions(true));
        }
    }

    @Test public void unsupportedAndThrowingMenuFallBackAndClearScope() {
        Settings.MESSAGE_TAP_REACTIONS.save(true);
        MessageGestures.bridgeForTests = delegate -> true;
        assertFalse("eligibility alone must not swallow a tap if no native model was built", MessageGestures.tap(new Object()));
        MessageGestures.bridgeForTests = delegate -> false;
        assertFalse(MessageGestures.tap(new Object()));
        MessageGestures.bridgeForTests = delegate -> { throw new IllegalStateException("native failed"); };
        assertFalse(MessageGestures.tap(new Object()));
        assertSame(actions, MessageGestures.actions(actions));
        assertTrue(MessageGestures.reactions(true));
    }

    @Test public void menuScopeDoesNotLeakAcrossThreads() throws Exception {
        Settings.MESSAGE_TAP_REACTIONS.save(true);
        AtomicBoolean otherThreadNative = new AtomicBoolean();
        MessageGestures.bridgeForTests = delegate -> {
            Thread thread = new Thread(() -> otherThreadNative.set(MessageGestures.actions(actions) == actions));
            thread.start();
            try { thread.join(); } catch (InterruptedException e) { throw new AssertionError(e); }
            assertTrue(MessageGestures.actions(actions).isEmpty());
            return true;
        };
        assertTrue(MessageGestures.tap(new Object()));
        assertTrue(otherThreadNative.get());
    }
}
