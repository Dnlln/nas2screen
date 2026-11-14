package com.nas2screen.screensaver.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.nas2screen.screensaver.R;

/** A focusable TV settings row: title, optional subtitle and a value on the right. */
public final class Row extends LinearLayout {

    public final TextView title;
    public final TextView subtitle;
    public final TextView state;

    public Row(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackgroundResource(R.drawable.bg_row);
        setFocusable(true);
        setClickable(true);
        setPadding(dp(20), dp(14), dp(20), dp(14));

        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        setLayoutParams(params);

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(VERTICAL);

        title = new TextView(context);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        title.setTextColor(context.getColor(R.color.text_primary));
        column.addView(title);

        subtitle = new TextView(context);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        subtitle.setTextColor(context.getColor(R.color.text_secondary));
        subtitle.setVisibility(GONE);
        LayoutParams subtitleParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(4);
        column.addView(subtitle, subtitleParams);

        state = new TextView(context);
        state.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        state.setTextColor(context.getColor(R.color.accent));
        state.setTypeface(Typeface.DEFAULT_BOLD);
        state.setGravity(Gravity.END);

        addView(column, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        addView(state, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    }

    public static Row create(Context context, CharSequence titleText, CharSequence subtitleText) {
        Row row = new Row(context);
        row.title.setText(titleText);
        row.setSubtitle(subtitleText);
        return row;
    }

    public static Row action(Context context, CharSequence titleText, CharSequence subtitleText,
                             Runnable onClick) {
        Row row = create(context, titleText, subtitleText);
        row.state.setText("›");
        row.setOnClickListener(v -> onClick.run());
        return row;
    }

    public Row setSubtitle(CharSequence text) {
        if (text == null || text.length() == 0) {
            subtitle.setVisibility(GONE);
        } else {
            subtitle.setText(text);
            subtitle.setVisibility(VISIBLE);
        }
        return this;
    }

    public Row setState(CharSequence text) {
        state.setText(text == null ? "" : text);
        return this;
    }

    public Row accentState() {
        state.setTextColor(getContext().getColor(R.color.accent));
        return this;
    }

    public Row neutralState() {
        state.setTextColor(getContext().getColor(R.color.text_secondary));
        return this;
    }

    /** Toggles the string and returns the new value. */
    public static boolean toggleText(Row row, boolean checked, CharSequence on, CharSequence off) {
        boolean next = !checked;
        row.state.setText(next ? on : off);
        row.accentState();
        return next;
    }

    public static ColorStateList accentTint(Context context) {
        return ColorStateList.valueOf(context.getColor(R.color.accent));
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }

    public static void fixFocusOrder(View... views) {
        for (int i = 0; i < views.length - 1; i++) {
            views[i].setNextFocusDownId(views[i + 1].getId());
        }
    }
}
