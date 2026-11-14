/* Copyright 2026 InstaPrime contributors */
package app.hushgram.extension.instagram.settings;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import app.hushgram.extension.shared.Logger;
import app.hushgram.extension.shared.Utils;

/** Styles the native snackbar surface, retaining its content, actions, animation and placement. */
public final class NativeSnackbar {
    private NativeSnackbar() { }

    public static void style(View surface) {
        try {
            if (surface == null) return;
            style(surface, SettingsStatus.pureBlack(), Utils.settingsReady() && Settings.THEME_NATIVE_POPUPS.get());
        } catch (Throwable failure) {
            Logger.printException(() -> "Could not style native snackbar", failure);
        }
    }

    static void style(View surface, boolean black, boolean accent) {
        ScreenColors colors = ScreenColors.forScreen(surface.getContext());
        Integer fill = null;
        if (accent) fill = colors.dialog;
        else if (black) fill = Color.BLACK;
        Drawable background = surface.getBackground();
        if (background != null) {
            shape(background.mutate(), 8 * surface.getResources().getDisplayMetrics().density, fill);
            if (fill != null) surface.setBackgroundTintList(null);
        }
        if (fill != null) text(surface, accent ? colors.title : ScreenColors.BLACK.title);
    }

    private static void shape(Drawable background, float radius, Integer fill) {
        if (background instanceof GradientDrawable) {
            GradientDrawable gradient = (GradientDrawable) background;
            gradient.setCornerRadius(radius);
            if (fill != null) gradient.setColor(fill);
        } else if (background instanceof InsetDrawable) {
            shape(((InsetDrawable) background).getDrawable(), radius, fill);
        } else if (background instanceof LayerDrawable) {
            LayerDrawable layers = (LayerDrawable) background;
            for (int i = 0; i < layers.getNumberOfLayers(); i++) shape(layers.getDrawable(i), radius, fill);
        } else if (background instanceof ColorDrawable && fill != null) {
            ((ColorDrawable) background).setColor(fill);
        }
    }

    private static void text(View view, int color) {
        if (view instanceof TextView) ((TextView) view).setTextColor(ScreenColors.dimmedWhenDisabled(color));
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) text(group.getChildAt(i), color);
        }
    }
}
