/*
 * Copyright 2026 Hushfacebook contributors
 * https://github.com/SysAdminDoc/Hushfacebook
 *
 * Modified for HushGram (Instagram), 2026.
 */
package app.hushgram.extension.instagram.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.preference.TwoStatePreference;
import android.text.TextUtils;
import android.util.SparseBooleanArray;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.Nullable;

import app.hushgram.extension.shared.settings.preference.ImmediateAction;

/** Wallpaper-derived Material tonal surfaces for settings and runtime controls. */
public final class ScreenColors {
    public final boolean light;
    /** The page, behind the title bar and every row. */
    public final int background;
    /** A dialog's surface. */
    public final int dialog;
    /** Row and dialog titles, the title bar and its back arrow. */
    public final int title;
    /** Row summaries and a dialog's message. */
    public final int summary;
    /** Section titles. */
    public final int heading;
    /** A switch that's on, and the buttons of a dialog. */
    public final int accent;
    /** A switch that's off. */
    public final int switchOff;
    /** Grouped preference surfaces and control outlines. */
    public final int card;
    public final int outline;
    /** Tonal surface used by secondary controls. */
    public final int iconSurface;
    /** Text on the filled primary dialog action. */
    public final int onAccent;
    /** Calm validation feedback on the charcoal dialog surface. */
    public final int error;

    /** The colours the screen on show was built with, or null for the black page. */
    @Nullable
    static volatile ScreenColors shown;
    public static final ScreenColors DEFAULT = new ScreenColors();

    int choiceSelected() {
        return android.graphics.Color.rgb(
                (Color.red(iconSurface) * 9 + Color.red(accent)) / 10,
                (Color.green(iconSurface) * 9 + Color.green(accent)) / 10,
                (Color.blue(iconSurface) * 9 + Color.blue(accent)) / 10);
    }

    static ScreenColors current() {
        return shown == null ? DEFAULT : shown;
    }

    private ScreenColors() {
        this(null);
    }

    private ScreenColors(@Nullable Context context) {
        light = false;
        // System tonal palettes follow the wallpaper without requesting wallpaper access.
        background = tone(context, "system_neutral1_1000", Color.BLACK);
        dialog = tone(context, "system_neutral1_900", 0xFF211F26);
        title = tone(context, "system_neutral1_100", 0xFFE6E0E9);
        summary = tone(context, "system_neutral2_200", 0xFFCAC4D0);
        heading = tone(context, "system_accent1_200", 0xFFD0BCFF);
        accent = heading;
        switchOff = tone(context, "system_neutral2_700", 0xFF49454F);
        card = tone(context, "system_neutral1_900", 0xFF211F26);
        outline = tone(context, "system_neutral2_500", 0xFF79747E);
        iconSurface = tone(context, "system_neutral2_800", 0xFF322F35);
        onAccent = tone(context, "system_accent1_800", 0xFF381E72);
        error = 0xFFFFB4AB;
    }

    private static int tone(@Nullable Context context, String name, int fallback) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return fallback;
        int id = context.getResources().getIdentifier(name, "color", "android");
        return id == 0 ? fallback : context.getColor(id);
    }

    /** The unchecked thumb uses the on-surface token. */
    int offThumb() {
        return title;
    }

    /**
     * [color], at Material's 38% on a disabled row. A plain colour replaced the theme's own
     * disabled state, so a row that ignored taps, such as Import while an export runs, looked
     * the same as one that would take them.
     */
    static ColorStateList dimmedWhenDisabled(int color) {
        int dimmed = (color & 0x00FFFFFF) | 0x61000000;
        return new ColorStateList(new int[][]{{-android.R.attr.state_enabled}, {}}, new int[]{dimmed, color});
    }

    /** Native list navigation uses selected/activated as well as focused. Keep each visible. */
    ColorStateList interactionOutline(int normal) {
        return interactionOutline(normal, heading);
    }

    private ColorStateList interactionOutline(int normal, int focused) {
        return new ColorStateList(new int[][]{
                {-android.R.attr.state_enabled}, {android.R.attr.state_focused},
                {android.R.attr.state_hovered}, {android.R.attr.state_selected},
                {android.R.attr.state_activated}, {android.R.attr.state_checked}, {}},
                new int[]{(normal & 0x00FFFFFF) | (Math.round(Color.alpha(normal) * 0.38f) << 24),
                        focused, focused, focused, focused, focused, normal});
    }

    /** Content and mask share bounds; nesting would apply an inset twice to the ripple mask. */
    static RippleDrawable boundedRipple(ColorStateList colors, Drawable content, Drawable mask) {
        RippleDrawable ripple = new RippleDrawable(colors, content, mask);
        ripple.setPaddingMode(android.graphics.drawable.LayerDrawable.PADDING_MODE_STACK);
        return ripple;
    }

    static ColorStateList interactionRipple(int color) {
        return new ColorStateList(new int[][]{{-android.R.attr.state_enabled}, {}},
                new int[]{Color.TRANSPARENT, (color & 0x00FFFFFF) | 0x33000000});
    }

    ColorStateList choiceFill(int normal) {
        return new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{(accent & 0x00FFFFFF) | 0x22000000, normal});
    }

    /** Secondary actions use the primary text token. */
    int secondaryActionText() {
        return heading;
    }

    /** Wallpaper-derived Material tones, refreshed when opening the screen. */
    @Nullable
    static ScreenColors forScreen(Context context) {
        return new ScreenColors(context);
    }

    /** The framework theme rows and dialogs are built with, so what this class doesn't paint still reads. */
    int theme() {
        return light ? android.R.style.Theme_Material_Light_NoActionBar : android.R.style.Theme_Material_NoActionBar;
    }

    /** {@link #theme()} of {@link #forScreen}, or the dark Material theme the black page has always had. */
    static int themeFor(Context context) {
        ScreenColors colors = forScreen(context);
        return colors == null ? android.R.style.Theme_Material_NoActionBar : colors.theme();
    }

    /** A row's title and summary, its switch if it has one, and its chevron if a tap opens something. */
    void paintRow(View row, Preference preference) {
        boolean overview = preference.getParent() instanceof PreferenceScreen;
        TextView title = row.findViewById(android.R.id.title);
        if (title != null) {
            title.setTextColor(dimmedWhenDisabled(this.title));
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP,
                    "hushgram_status".equals(preference.getKey()) ? 14 : 16);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            if (title.getParent() instanceof View) {
                View text = (View) title.getParent();
                text.setPadding(0, 0, 0, 0);
                text.setMinimumHeight(0);
            }
        }
        TextView summary = row.findViewById(android.R.id.summary);
        if (summary != null) {
            summary.setTextColor(dimmedWhenDisabled(this.summary));
            summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            summary.setLineSpacing(0, 1);
            summary.setMaxLines(Integer.MAX_VALUE);
            summary.setEllipsize(null);
        }
        View widget = row.findViewById(android.R.id.switch_widget);
        if (widget instanceof Switch) {
            if (widget instanceof AnimatedSettingsSwitch) {
                ((AnimatedSettingsSwitch) widget).setColors(this);
            }

        }
        paintChevron(row, preference);
        ImageView icon = row.findViewById(android.R.id.icon);
        // Decoration: the row's title already says what it is, so a screen reader skips the icon.
        if (icon != null) icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (icon != null && preference.getIcon() != null) {
            ViewGroup.LayoutParams imageSize = icon.getLayoutParams();
            imageSize.width = dp(row, 24);
            imageSize.height = dp(row, 24);
            icon.setLayoutParams(imageSize);
            icon.setPadding(0, 0, 0, 0);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            icon.setImageTintList(dimmedWhenDisabled(heading));
            icon.setBackground(null);
            if (icon.getParent() instanceof ViewGroup) {
                ViewGroup frame = (ViewGroup) icon.getParent();
                frame.setPaddingRelative(0, 0, dp(row, 14), 0);
                ViewGroup.LayoutParams size = frame.getLayoutParams();
                size.width = dp(row, 38);
                frame.setLayoutParams(size);
            }
        }
        paintSurface(row, preference, opensCard(preference), closesCard(preference));
    }

    /**
     * Whether [preference] is the top row of the card it sits in. A section's rows share one card.
     * On the page itself, the rows that open each category's page share one too, and Report a
     * problem and Support HushGram join the About row they follow; every other row there is a card
     * of its own.
     */
    static boolean opensCard(Preference preference) {
        PreferenceGroup parent = preference.getParent();
        if (parent == null) return true;
        int at = indexIn(parent, preference);
        if (at <= 0) return true;
        return !(parent instanceof PreferenceCategory) && !sharesCard(parent.getPreference(at - 1), preference);
    }

    /** Whether [preference] is the bottom row of its card. */
    static boolean closesCard(Preference preference) {
        PreferenceGroup parent = preference.getParent();
        if (parent == null) return true;
        int at = indexIn(parent, preference);
        if (at < 0 || at == parent.getPreferenceCount() - 1) return true;
        return !(parent instanceof PreferenceCategory) && !sharesCard(preference, parent.getPreference(at + 1));
    }

    /**
     * Two neighbours on the page itself that draw as one card: both open a page, or one of the two
     * last rows (Report a problem, Support HushGram) follows a page's row or the other one.
     */
    private static boolean sharesCard(Preference above, Preference below) {
        if (isTopControl(above) || isTopControl(below)) return false;
        return true;
    }

    private static boolean isTopControl(Preference row) {
        String key = row.getKey();
        return "hushgram_settings_search".equals(key) || HushgramPreferenceFragment.ENABLE_ALL_KEY.equals(key)
                || "hushgram_status".equals(key)
                || HushgramPreferenceFragment.SIGN_IN_NOTICE_KEY.equals(key);
    }

    /** Report a problem and Support HushGram, which end the page. */
    private static boolean closesPage(Preference preference) {
        String key = preference.getKey();
        return HushgramPreferenceFragment.REPORT_ROW_KEY.equals(key) || HushgramPreferenceFragment.SUPPORT_ROW_KEY.equals(key);
    }

    private static boolean opensPage(Preference preference) {
        String key = preference.getKey();
        return key != null && key.startsWith(HushgramPreferenceFragment.CATEGORY_ROW_KEY);
    }

    /** Where [preference] stands among the rows [group] shows, which it keeps in their order. */
    private static int indexIn(PreferenceGroup group, Preference preference) {
        for (int i = 0; i < group.getPreferenceCount(); i++) if (group.getPreference(i) == preference) return i;
        return -1;
    }

    /**
     * Group boundaries follow the visible rows, including a filtered search result. The card's
     * height is the row's: its text wraps at any font size and the row grows to fit.
     */
    void paintSurface(View row, Preference preference, boolean first, boolean last) {
        boolean overview = preference.getParent() instanceof PreferenceScreen;
        boolean category = preference instanceof HushgramPreferenceFragment.Heading;
        boolean expanded = category && ((HushgramPreferenceFragment.Heading) preference).expanded;
        float top = dp(row, category || first ? 12 : 4);
        float bottom = dp(row, category || last ? 12 : 4);
        GradientDrawable surface = new GradientDrawable();
        surface.setColor(expanded ? iconSurface : card);
        surface.setCornerRadii(new float[]{top, top, top, top, bottom, bottom, bottom, bottom});
        int side = dp(row, 18);
        boolean afterSearch = false;
        PreferenceGroup parent = preference.getParent();
        if (parent != null) {
            int at = indexIn(parent, preference);
            afterSearch = at > 0 && ("hushgram_settings_search".equals(parent.getPreference(at - 1).getKey())
                    || HushgramPreferenceFragment.ENABLE_ALL_KEY.equals(parent.getPreference(at - 1).getKey()));
        }
        int above = category || afterSearch ? dp(row, 16) : 0;
        Drawable inset = new InsetDrawable(surface, side, above, side, dp(row, 3));
        Drawable mask = new InsetDrawable(surface.getConstantState().newDrawable().mutate(),
                side, above, side, dp(row, 3));
        row.setBackground(preference.isSelectable()
                ? boundedRipple(interactionRipple(heading), inset, mask).mutate() : inset);
        // Explicit padding after the inset background prevents its padding from moving the text.
        int horizontal = 36;
        int vertical = 12;
        row.setPaddingRelative(dp(row, horizontal), dp(row, vertical) + above,
                dp(row, horizontal), dp(row, vertical + 3));
        row.setMinimumHeight(dp(row, "hushgram_view_toggle".equals(preference.getKey()) ? 48
                : "hushgram_status".equals(preference.getKey()) ? 56 : opensPage(preference) ? 76 : 68));
    }

    /** A section title. */
    void paintHeading(View row) {
        TextView title = row.findViewById(android.R.id.title);
        if (title != null) {
            title.setTextColor(heading);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            title.setLetterSpacing(0.055f);
            title.setAllCaps(true);
        }
        row.setPaddingRelative(dp(row, 22), dp(row, 20), dp(row, 22), dp(row, 8));
    }

    /** Marks the chevron a row was given, so a recycled row's can be found and taken off. */
    static final String CHEVRON = "hushgram_chevron";

    /**
     * Whether a tap on [preference] opens something: a dialog, a file picker, the browser. A
     * switch says what a tap does with its switch, and a row that acts the moment it's tapped
     * goes without a chevron, as the shared ImmediateAction asks, so the chevron keeps one meaning.
     */
    static boolean opensSomething(Preference preference) {
        return preference.isSelectable() && !(preference instanceof TwoStatePreference)
                && !(preference instanceof ImmediateAction && ((ImmediateAction) preference).actsOnTap());
    }

    /**
     * The chevron at the end of every row a tap opens something from. Only its title's colour set
     * such a row apart before, which is how "Licenses" and "Version" looked alike (WCAG 1.4.1). It
     * sits in the row's widget frame, where a switch row keeps its switch. Rows are recycled, and
     * one Row class draws plain lines too, so a row that shouldn't have one has it taken off.
     */
    private void paintChevron(View row, Preference preference) {
        View found = row.findViewById(android.R.id.widget_frame);
        if (!(found instanceof ViewGroup)) return;
        ViewGroup frame = (ViewGroup) found;
        View chevron = frame.findViewWithTag(CHEVRON);
        if (!opensSomething(preference)) {
            if (chevron != null) {
                frame.removeView(chevron);
                // Preference hides an empty frame only when it builds the row, which a recycled row
                // skips, and an empty frame still takes its padding's width from the text.
                if (frame.getChildCount() == 0) frame.setVisibility(View.GONE);
            }
            return;
        }
        ImageView image;
        if (chevron instanceof ImageView) {
            image = (ImageView) chevron;
        } else {
            image = new ImageView(row.getContext());
            image.setTag(CHEVRON);
            // The row already tells a screen reader it's a button; the shape adds nothing to hear.
            image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            frame.addView(image, new LinearLayout.LayoutParams(dp(row, 16), dp(row, 24)));
        }
        Drawable drawn = image.getDrawable();
        int direction = preference instanceof HushgramPreferenceFragment.Heading
                ? ((HushgramPreferenceFragment.Heading) preference).expanded ? -1 : 1 : 0;
        if (!(drawn instanceof Chevron) || ((Chevron) drawn).color != summary
                || ((Chevron) drawn).direction != direction) {
            image.setImageDrawable(new Chevron(summary, dp(row, 2), direction));
        }
        image.setEnabled(preference.isEnabled());
        frame.setVisibility(View.VISIBLE);
    }

    /**
     * A chevron drawn in code, since Instagram's APK gives the patch no resource for one. It points the way
     * the row reads, so it mirrors in a right-to-left layout, and it dims with its row.
     */
    static final class Chevron extends Drawable {
        /** The colour it's drawn in on a row that can be tapped now. */
        final int color;
        /** Zero follows reading direction; positive opens down, negative collapses up. */
        final int direction;
        private final ColorStateList colors;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        Chevron(int color, float stroke) {
            this(color, stroke, 0);
        }

        Chevron(int color, float stroke, int direction) {
            this.color = color;
            this.direction = direction;
            colors = dimmedWhenDisabled(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(color);
        }

        /**
         * One arm's end, the tip and the other arm's end, as x and y pairs. The tip is at the end
         * the row reads towards: the right, or the left in a right-to-left layout. Material's own
         * chevron is 4.6 dp wide and 9.2 dp tall in a 24 dp icon, and this keeps that shape.
         */
        float[] points() {
            Rect bounds = getBounds();
            float half = bounds.height() * 0.19f;
            float reach = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? -half / 2 : half / 2;
            float x = bounds.exactCenterX();
            float y = bounds.exactCenterY();
            if (direction != 0) {
                float tip = half / 2 * direction;
                return new float[]{x - half, y - tip, x, y + tip, x + half, y - tip};
            }
            return new float[]{x - reach, y - half, x + reach, y, x - reach, y + half};
        }

        /** The colour it draws in now, which follows its row's state. */
        int currentColor() {
            return paint.getColor();
        }

        @Override
        public void draw(Canvas canvas) {
            float[] at = points();
            path.rewind();
            path.moveTo(at[0], at[1]);
            path.lineTo(at[2], at[3]);
            path.lineTo(at[4], at[5]);
            canvas.drawPath(path, paint);
        }

        @Override
        public boolean isAutoMirrored() {
            return true;
        }

        @Override
        public boolean onLayoutDirectionChanged(int layoutDirection) {
            invalidateSelf();
            return true;
        }

        @Override
        public boolean isStateful() {
            return true;
        }

        @Override
        protected boolean onStateChange(int[] state) {
            int now = colors.getColorForState(state, color);
            if (now == paint.getColor()) return false;
            paint.setColor(now);
            invalidateSelf();
            return true;
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter filter) {
            paint.setColorFilter(filter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** Recovery is a choice, not another pair of settings rows. */
    private void paintRecoveryAction(View row, boolean primary) {
        TextView title = row.findViewById(android.R.id.title);
        if (title != null) {
            title.setTextColor(dimmedWhenDisabled(primary ? onAccent : secondaryActionText()));
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setGravity(android.view.Gravity.CENTER);
            // Centred by gravity, which a start alignment from showAllText would override.
            title.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
            ViewGroup.LayoutParams size = title.getLayoutParams();
            size.width = ViewGroup.LayoutParams.MATCH_PARENT;
            title.setLayoutParams(size);
        }
        GradientDrawable surface = new GradientDrawable();
        surface.setColor(primary ? dimmedWhenDisabled(accent) : ColorStateList.valueOf(Color.TRANSPARENT));
        surface.setCornerRadius(dp(row, 12));
        surface.setStroke(dp(row, 2), interactionOutline(primary ? accent : outline,
                primary ? onAccent : heading));
        row.setBackground(boundedRipple(interactionRipple(primary ? onAccent : heading),
                new InsetDrawable(surface, dp(row, 18), dp(row, 4), dp(row, 18), dp(row, 4)), null).mutate());
        row.setMinimumHeight(dp(row, 56));
        row.setPadding(dp(row, 32), dp(row, 8), dp(row, 32), dp(row, 8));
    }

    static void recoveryMessage(View row) {
        ScreenColors palette = forScreen(row.getContext());
        if (palette == null) palette = DEFAULT;
        HushgramPreferenceFragment.showAllText(row);
        TextView title = row.findViewById(android.R.id.title);
        TextView summary = row.findViewById(android.R.id.summary);
        // Centred across the row: text wrapped to its own width has nothing to centre in.
        if (title != null) {
            fillWidth(title);
            title.setGravity(android.view.Gravity.CENTER);
            // Centred by gravity, which a start alignment from showAllText would override.
            title.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
            title.setTextSize(22);
            title.setTextColor(palette.title);
            title.setTypeface(Typeface.create("sans-serif-medium", 0));
            Drawable icon = SettingsIcons.icon(row.getContext(), SettingsIcons.ABOUT, palette.heading);
            icon.setBounds(0, 0, dp(row, 56), dp(row, 56));
            title.setCompoundDrawablesRelative(null, icon, null, null);
            title.setCompoundDrawablePadding(dp(row, 24));
        }
        if (summary != null) {
            fillWidth(summary);
            summary.setGravity(android.view.Gravity.CENTER);
            summary.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
            summary.setTextColor(palette.summary);
            summary.setTextSize(16);
            summary.setPadding(0, dp(row, 12), 0, 0);
        }
        row.setPadding(dp(row, 32), dp(row, 100), dp(row, 32), dp(row, 56));
    }

    private static void fillWidth(View text) {
        ViewGroup.LayoutParams size = text.getLayoutParams();
        if (size == null) return;
        size.width = ViewGroup.LayoutParams.MATCH_PARENT;
        text.setLayoutParams(size);
    }

    /** A dialog on show: its surface, title, message, real list choices, buttons and text field. */
    void paint(@Nullable AlertDialog dialog) {
        if (dialog == null) return;
        Window window = dialog.getWindow();
        if (window != null) {
            GradientDrawable panel = new GradientDrawable();
            panel.setColor(this.dialog);
            panel.setCornerRadius(dp(window.getDecorView(), 12));
            window.setBackgroundDrawable(panel);
            int width = Math.min(dialog.getContext().getResources().getDisplayMetrics().widthPixels
                    - dp(window.getDecorView(), 32), dp(window.getDecorView(), 560));
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        @SuppressWarnings("DiscouragedApi")
        int titleId = dialog.getContext().getResources().getIdentifier("alertTitle", "id", "android");
        TextView title = titleId == 0 ? null : dialog.findViewById(titleId);
        if (title != null) {
            // In the gradient, as every HushGram title is.
            title.setTextColor(this.title);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            title.setSingleLine(false);
            title.setMaxLines(Integer.MAX_VALUE);
        }
        // AlertDialog's own message, and the one a preference's dialog layout brings, which is the
        // one on show there: AlertDialog's own sits GONE above it, and findViewById finds that first.
        if (window != null) paintMessages(window.getDecorView());
        android.widget.ListView choices = dialog.getListView();
        if (choices != null && choices.getAdapter() != null && !(choices.getAdapter() instanceof DialogChoices)) {
            SparseBooleanArray checked = choices.getCheckedItemPositions();
            checked = checked == null ? null : checked.clone();
            choices.setAdapter(new DialogChoices(choices.getAdapter(), this));
            if (checked != null) {
                for (int index = 0; index < checked.size(); index++) {
                    if (checked.valueAt(index)) choices.setItemChecked(checked.keyAt(index), true);
                }
            }
            choices.setDivider(null);
        }
        if (choices != null) {
            choices.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            choices.setDivider(null);
            choices.setDividerHeight(0);
        }
        stackDialogActions(dialog);
        View field = dialog.findViewById(android.R.id.edit);
        if (field instanceof EditText) paintField((EditText) field);
    }

    /** Reuse the framework buttons to preserve listeners, enabled states and dismissal. */
    private void stackDialogActions(AlertDialog dialog) {
        int panelId = dialog.getContext().getResources().getIdentifier("buttonPanel", "id", "android");
        View panel = panelId == 0 ? null : dialog.findViewById(panelId);
        if (!(panel instanceof ViewGroup)) return;
        ViewGroup host = (ViewGroup) panel;
        LinearLayout column = new LinearLayout(dialog.getContext());
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(host, 20), dp(host, 8), dp(host, 20), dp(host, 16));
        for (int which : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEUTRAL, AlertDialog.BUTTON_NEGATIVE}) {
            Button button = dialog.getButton(which);
            if (button == null || button.getVisibility() == View.GONE) continue;
            if (button.getParent() instanceof ViewGroup) ((ViewGroup) button.getParent()).removeView(button);
            paintAction(button, which == AlertDialog.BUTTON_POSITIVE);
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            size.topMargin = column.getChildCount() == 0 ? 0 : dp(host, 8);
            column.addView(button, size);
        }
        host.removeAllViews();
        host.setPadding(0, 0, 0, 0);
        if (host instanceof LinearLayout) ((LinearLayout) host).setOrientation(LinearLayout.VERTICAL);
        host.addView(column, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /**
     * A dialog's text field. The framework theme draws its underline, cursor and selection in
     * the framework's teal, so they take the accent the buttons have.
     */
    void paintField(EditText field) {
        field.setHintTextColor(dimmedWhenDisabled(summary));
        GradientDrawable outline = new GradientDrawable();
        outline.setColor(iconSurface);
        outline.setCornerRadius(dp(field, 12));
        field.setBackgroundTintList(null);
        field.setBackground(outline);
        field.setPaddingRelative(dp(field, 12), dp(field, 10), dp(field, 12), dp(field, 10));
        field.setTextColor(dimmedWhenDisabled(title));
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        field.setMinHeight(dp(field, 48));
        field.setHighlightColor(half(accent));
        // Android 9 has no setters for the cursor and handles, so they keep the framework's colour.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        Drawable cursor = field.getTextCursorDrawable();
        if (cursor != null) {
            cursor = cursor.mutate();
            cursor.setTint(accent);
            field.setTextCursorDrawable(cursor);
        }
        Drawable handle = field.getTextSelectHandle();
        if (handle != null) {
            handle = handle.mutate();
            handle.setTint(accent);
            field.setTextSelectHandle(handle);
        }
        Drawable left = field.getTextSelectHandleLeft();
        if (left != null) {
            left = left.mutate();
            left.setTint(accent);
            field.setTextSelectHandleLeft(left);
        }
        Drawable right = field.getTextSelectHandleRight();
        if (right != null) {
            right = right.mutate();
            right.setTint(accent);
            field.setTextSelectHandleRight(right);
        }
    }

    private void paintMessages(View view) {
        if (view instanceof TextView && view.getId() == android.R.id.message) {
            TextView message = (TextView) view;
            message.setTextColor(dimmedWhenDisabled(summary));
            message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            message.setLineSpacing(dp(view, 2), 1);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) paintMessages(group.getChildAt(index));
        }
    }

    /** Paints a row with the colours on show, if there are any. */
    static void row(View row, Preference preference) {
        ScreenColors colors = shown;
        ScreenColors used = colors == null ? DEFAULT : colors;
        used.paintRow(row, preference);
        NewRowDots.paint(row, preference, used.accent);
    }

    /** Paints a section title with the colours on show, if there are any. */
    static void heading(View row) {
        ScreenColors colors = shown;
        (colors == null ? DEFAULT : colors).paintHeading(row);
    }

    /** Paints both recovery actions even if the ordinary settings page failed to initialize. */
    static void recoveryAction(View row, boolean primary) {
        ScreenColors colors = forScreen(row.getContext());
        (colors == null ? DEFAULT : colors).paintRecoveryAction(row, primary);
    }

    /** Paints a dialog with the colours on show, if there are any. */
    public static void dialog(@Nullable AlertDialog dialog) {
        ScreenColors colors = shown;
        (colors == null ? DEFAULT : colors).paint(dialog);
    }

    /** Styles a runtime dialog's own input without changing its validation or input type. */
    public static void styleField(EditText field) {
        ScreenColors colors = shown;
        (colors == null ? DEFAULT : colors).paintField(field);
    }

    /** Styles an existing action without replacing its content, listeners or accessibility delegate. */
    public static void styleAction(TextView action, boolean primary) {
        ScreenColors colors = shown;
        (colors == null ? DEFAULT : colors).paintAction(action, primary);
    }

    void paintAction(TextView action, boolean primary) {
        GradientDrawable surface = new GradientDrawable();
        surface.setColor(dimmedWhenDisabled(primary ? accent : card));
        surface.setCornerRadius(dp(action, 12));
        action.setBackgroundTintList(null);
        // View's tint pass mutates the background after setting its state. Mutate first so
        // that pass cannot replace enabled children with fresh, empty-state copies.
        action.setBackground(boundedRipple(
                interactionRipple(primary ? onAccent : heading), surface,
                surface.getConstantState().newDrawable().mutate()).mutate());
        action.setTextColor(dimmedWhenDisabled(primary ? onAccent : secondaryActionText()));
        action.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        action.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        action.setAllCaps(false);
        action.setSingleLine(false);
        action.setMaxLines(Integer.MAX_VALUE);
        action.setEllipsize(null);
        action.setHorizontallyScrolling(false);
        action.setGravity(android.view.Gravity.CENTER);
        action.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        action.setMinHeight(dp(action, 48));
        action.setMinimumHeight(dp(action, 48));
        action.setMinWidth(dp(action, 64));
        action.setMinimumWidth(dp(action, 64));
        action.setPaddingRelative(dp(action, 16), dp(action, 12), dp(action, 16), dp(action, 12));
    }

    /** Compact actions retain the shared interaction states without becoming filled buttons. */
    public static void styleIconAction(View action) {
        ScreenColors colors = shown == null ? DEFAULT : shown;
        GradientDrawable surface = new GradientDrawable();
        surface.setColor(Color.TRANSPARENT);
        surface.setCornerRadius(dp(action, 8));
        surface.setStroke(dp(action, 2), colors.interactionOutline(Color.TRANSPARENT));
        action.setBackgroundTintList(null);
        action.setBackground(boundedRipple(interactionRipple(colors.heading), surface, null).mutate());
    }

    private static int dp(View view, int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                view.getResources().getDisplayMetrics()));
    }

    /** A switch's track: the thumb's colour at half strength, as Material's own switch draws it. */
    public static int half(int color) {
        return (color & 0x00FFFFFF) | 0x80000000;
    }
}
