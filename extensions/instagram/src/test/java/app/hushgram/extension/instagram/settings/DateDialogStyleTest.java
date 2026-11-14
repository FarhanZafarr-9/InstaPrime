package app.hushgram.extension.instagram.settings;

import static org.junit.Assert.*;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.widget.DatePicker;
import android.view.ViewGroup;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import app.hushgram.extension.shared.SettingsContextRule;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 37})
public class DateDialogStyleTest {
    @Rule public final SettingsContextRule settings = new SettingsContextRule();
    @Test public void stylingTheShellLeavesTheNativePickerAndSelectionUntouched() {
        try (var host = Robolectric.buildActivity(Activity.class).setup()) {
            DatePickerDialog dialog = new DatePickerDialog(host.get(), (picker, year, month, day) -> { }, 2026, 10, 14);
            dialog.show();
            DatePicker picker = dialog.getDatePicker();
            long minimum = picker.getMinDate(), maximum = picker.getMaxDate();
            ScreenColors.dialog(dialog);
            assertSame(picker, dialog.getDatePicker());
            assertEquals(2026, picker.getYear());
            assertEquals(10, picker.getMonth());
            assertEquals(14, picker.getDayOfMonth());
            assertEquals(minimum, picker.getMinDate());
            assertEquals(maximum, picker.getMaxDate());
            assertEquals(ViewGroup.LayoutParams.MATCH_PARENT,
                    dialog.getButton(DialogInterface.BUTTON_POSITIVE).getLayoutParams().width);
            dialog.dismiss();
        }
    }
}
