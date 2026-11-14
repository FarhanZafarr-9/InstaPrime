package app.hushgram.extension.instagram.misc;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Render the actual drawable template written by the resource patch, without packaging an APK. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {31, 37})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LaunchArtworkTest {
    private static Drawable artwork() throws Exception {
        android.content.Context context = RuntimeEnvironment.getApplication();
        int id = context.getResources().getIdentifier("instaprime_splash_test", "drawable", context.getPackageName());
        assertNotEquals("compiled test artwork", 0, id);
        return context.getResources().getDrawable(id, context.getTheme());
    }

    @Test public void accentShowsThroughEveryPixelOutsideTheBrandTile() throws Exception {
        for (int accent : new int[] {0xff0095f6, 0xff7755bb, 0xff2a6f68, Color.BLACK, Color.WHITE}) {
            Drawable icon = artwork();
            Bitmap image = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888);
            image.eraseColor(accent);
            icon.setBounds(0, 0, 160, 160);
            icon.draw(new Canvas(image));
            for (int y = 0; y < 160; y++) for (int x = 0; x < 160; x++) {
                if (x < 48 || x >= 112 || y < 48 || y >= 112) assertEquals("no outer card at " + x + "," + y,
                        accent, image.getPixel(x, y));
            }
            assertEquals("white brand tile", Color.WHITE, image.getPixel(50, 80));
            assertEquals("black camera outline", Color.BLACK, image.getPixel(96, 80));
            image.recycle();
        }
    }

    @Test public void outerCanvasAndRoundedCornersRemainTransparent() throws Exception {
        Bitmap image = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888);
        Drawable icon = artwork();
        icon.setBounds(0, 0, 160, 160);
        icon.draw(new Canvas(image));
        assertEquals(0, Color.alpha(image.getPixel(0, 0)));
        assertEquals(0, Color.alpha(image.getPixel(48, 48)));
        assertEquals(255, Color.alpha(image.getPixel(80, 80)));
        image.recycle();
    }
}
