/*
 * Copyright 2026 Hushfacebook contributors
 * https://github.com/SysAdminDoc/Hushfacebook
 *
 * Modified for HushGram (Instagram), 2026.
 */
package app.hushgram.extension.instagram.settings;

import android.content.res.ColorStateList;
import android.database.DataSetObserver;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckedTextView;
import android.widget.ListAdapter;
import android.widget.TextView;

/** Styles the framework's real list rows without replacing selection or checkbox behavior. */
final class DialogChoices extends BaseAdapter {
    private final ListAdapter source;
    private final ScreenColors colors;

    DialogChoices(ListAdapter source, ScreenColors colors) {
        this.source = source;
        this.colors = colors;
    }

    @Override public int getCount() { return source.getCount(); }
    @Override public Object getItem(int position) { return source.getItem(position); }
    @Override public long getItemId(int position) { return source.getItemId(position); }
    @Override public boolean hasStableIds() { return source.hasStableIds(); }
    @Override public boolean isEnabled(int position) { return source.isEnabled(position); }
    @Override public boolean areAllItemsEnabled() { return source.areAllItemsEnabled(); }
    @Override public int getItemViewType(int position) { return source.getItemViewType(position); }
    @Override public int getViewTypeCount() { return source.getViewTypeCount(); }
    @Override public void registerDataSetObserver(DataSetObserver observer) { source.registerDataSetObserver(observer); }
    @Override public void unregisterDataSetObserver(DataSetObserver observer) { source.unregisterDataSetObserver(observer); }

    @Override public View getView(int position, View recycled, ViewGroup parent) {
        View row = source.getView(position, recycled, parent);
        if (row instanceof TextView) {
            TextView choice = (TextView) row;
            float density = row.getResources().getDisplayMetrics().density;
            choice.setTextColor(ScreenColors.dimmedWhenDisabled(colors.title));
            choice.setTextSize(16);
            choice.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            choice.setSingleLine(false);
            choice.setMaxLines(Integer.MAX_VALUE);
            boolean multiple = parent instanceof android.widget.ListView
                    && ((android.widget.ListView) parent).getChoiceMode() == android.widget.ListView.CHOICE_MODE_MULTIPLE;
            if (choice instanceof CheckedTextView) {
                CheckedTextView checked = (CheckedTextView) choice;
                checked.setCheckMarkTintList(null);
                checked.setCompoundDrawablesRelative(null, null, null, null);
                checked.setCheckMarkDrawable(new SelectionIndicator(choice.getContext(), colors, multiple));
            }
            int gap = Math.round(3 * density);
            int side = Math.round(18 * density);
            choice.setMinHeight(Math.round(64 * density) + gap);
            choice.setMinimumHeight(Math.round(64 * density) + gap);
            choice.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
            GradientDrawable surface = new GradientDrawable();
            surface.setColor(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
                    new int[]{colors.choiceSelected(), colors.iconSurface}));
            float top = Math.round((position == 0 ? 12 : 4) * density);
            float bottom = Math.round((position == getCount() - 1 ? 12 : 4) * density);
            surface.setCornerRadii(new float[]{top, top, top, top, bottom, bottom, bottom, bottom});
            InsetDrawable inset = new InsetDrawable(surface, side, 0, side, gap);
            InsetDrawable mask = new InsetDrawable(surface.getConstantState().newDrawable().mutate(), side, 0, side, gap);
            choice.setBackground(ScreenColors.boundedRipple(ScreenColors.interactionRipple(colors.heading), inset, mask).mutate());
            choice.setPaddingRelative(side + Math.round(20 * density), Math.round(14 * density),
                    side + Math.round(16 * density), Math.round(14 * density) + gap);
        }
        return row;
    }
}
