package app.hushgram.extension.instagram.misc;

import static org.junit.Assert.*;
import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 37})
public class ProfileStatusBarTest {
    ActivityController<Activity> host;
    View profile, decor;
    ProfileStatusBar.Cover cover;

    @Before public void open() {
        host = Robolectric.buildActivity(Activity.class).setup().visible();
        FrameLayout content = new FrameLayout(host.get());
        profile = new View(host.get());
        profile.setId(0x7f0b7001);
        content.addView(profile, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 80));
        host.get().setContentView(content);
        decor = host.get().getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, 1080, 1920);
        Object attach = ReflectionHelpers.getField(decor, "mAttachInfo");
        ReflectionHelpers.setField(attach, "mHasWindowFocus", true);
        ProfileStatusBar.profileIdForTests = profile.getId();
        ProfileStatusBar.heightForTests = 48;
        ProfileStatusBar.colorForTests = 0xff223344;
        host.get().getWindow().setStatusBarColor(Color.RED);
        if (Build.VERSION.SDK_INT >= 29) host.get().getWindow().setStatusBarContrastEnforced(true);
        cover = new ProfileStatusBar.Cover(host.get());
    }

    @After public void close() {
        cover.hide(host.get().getWindow());
        host.close();
        ProfileStatusBar.profileIdForTests = ProfileStatusBar.searchIdForTests =
                ProfileStatusBar.heightForTests = ProfileStatusBar.colorForTests = null;
    }

    @Test public void reusedActivityFollowsProfileArrivalAndDeparture() {
        profile.setVisibility(View.GONE);
        cover.update();
        assertFalse(cover.shown);
        profile.setVisibility(View.VISIBLE);
        cover.update();
        assertTrue(cover.shown);
        if (Build.VERSION.SDK_INT >= 29) assertFalse(host.get().getWindow().isStatusBarContrastEnforced());
        if (Build.VERSION.SDK_INT >= 35) assertEquals(48, cover.strip.getBounds().height());
        else assertEquals(0xff223344, host.get().getWindow().getStatusBarColor());
        profile.setVisibility(View.GONE);
        cover.update();
        assertFalse(cover.shown);
        if (Build.VERSION.SDK_INT < 35) assertEquals(Color.RED, host.get().getWindow().getStatusBarColor());
        if (Build.VERSION.SDK_INT >= 29) assertTrue(host.get().getWindow().isStatusBarContrastEnforced());
    }

    @Test public void disabledAccentAndHiddenStatusBarRemoveTheCover() {
        cover.update();
        assertTrue(cover.shown);
        ProfileStatusBar.colorForTests = Color.TRANSPARENT;
        cover.update();
        assertFalse(cover.shown);
        ProfileStatusBar.colorForTests = 0xff223344;
        ProfileStatusBar.heightForTests = 0;
        cover.update();
        assertFalse(cover.shown);
    }

    @Test public void popupFocusDoesNotRemoveTheProfileAccent() {
        cover.update();
        Object attach = ReflectionHelpers.getField(decor, "mAttachInfo");
        ReflectionHelpers.setField(attach, "mHasWindowFocus", false);
        cover.onWindowFocusChanged(false);
        assertTrue("a popup does not leave the profile", cover.shown);
        if (Build.VERSION.SDK_INT < 35) assertEquals(0xff223344, host.get().getWindow().getStatusBarColor());
        profile.setVisibility(View.GONE);
        cover.onGlobalLayout();
        assertFalse("actual navigation still removes the accent", cover.shown);
    }

    @Test public void anotherScreensNewStatusColorIsNotRestoredOver() {
        cover.update();
        host.get().getWindow().setStatusBarColor(Color.BLUE);
        profile.setVisibility(View.GONE);
        cover.update();
        if (Build.VERSION.SDK_INT < 35) assertEquals(Color.BLUE, host.get().getWindow().getStatusBarColor());
    }

    @Test public void searchHeaderKeepsAccentUntilSearchIsLeft() {
        // Reuse the visible fixture header as Search while the profile header is absent.
        ProfileStatusBar.profileIdForTests = 0;
        ProfileStatusBar.searchIdForTests = profile.getId();
        cover.update();
        assertTrue("Search gets the same status inset color", cover.shown);
        if (Build.VERSION.SDK_INT >= 35) assertEquals(0xff223344, cover.strip.getColor());
        else assertEquals(0xff223344, host.get().getWindow().getStatusBarColor());
        profile.setVisibility(View.GONE);
        cover.update();
        assertFalse("leaving Search removes the override", cover.shown);
        if (Build.VERSION.SDK_INT < 35) assertEquals(Color.RED, host.get().getWindow().getStatusBarColor());
    }
}
