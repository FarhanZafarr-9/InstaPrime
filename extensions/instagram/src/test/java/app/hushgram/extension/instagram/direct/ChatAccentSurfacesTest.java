package app.hushgram.extension.instagram.direct;

import static org.junit.Assert.assertSame;
import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ChatAccentSurfacesTest {
    @Test public void visibleEmptyImageDoesNotHideTheActualComposerFill() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout bar = new FrameLayout(activity);
        ImageView image = new ImageView(activity);
        View row = new View(activity);
        row.setBackgroundColor(Color.BLACK);
        bar.addView(image, new FrameLayout.LayoutParams(500, 80));
        bar.addView(row, new FrameLayout.LayoutParams(500, 80));
        activity.setContentView(bar);
        bar.layout(0, 0, 500, 80);
        image.layout(0, 0, 500, 80);
        row.layout(0, 0, 500, 80);
        assertSame(row, ChatAccentSurfaces.inputSurface(image));
        row.setBackground(null);
        assertSame(image, ChatAccentSurfaces.inputSurface(image));
        activity.finish();
    }
}
