package io.nyx.app.hook;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * The info sheet opened from a story or a media long-press.
 *
 * Built from framework views only, so it inflates on any Instagram build and
 * cannot pull in a missing style. Every row is copyable by long-press.
 */
final class InfoPanel {

    private InfoPanel() {}

    static void show(Activity activity, List<String> lines) {
        try {
            if (activity == null || activity.isFinishing()) return;
            if (lines == null || lines.isEmpty()) return;

            LinearLayout column = new LinearLayout(activity);
            column.setOrientation(LinearLayout.VERTICAL);
            int pad = dp(activity, 18);
            column.setPadding(pad, pad, pad, pad);

            TextView title = new TextView(activity);
            title.setText("NYX INFO");
            title.setTextSize(10);
            title.setLetterSpacing(0.22f);
            title.setTextColor(0xFF8A93AD);
            column.addView(title);

            for (String line : lines) {
                TextView row = new TextView(activity);
                row.setText(line);
                row.setTextSize(13.5f);
                row.setTextColor(0xFFF4F6FF);
                row.setPadding(0, dp(activity, 10), 0, 0);
                row.setTextIsSelectable(true);
                column.addView(row);
            }

            TextView footer = new TextView(activity);
            footer.setText("Long-press any line to copy");
            footer.setTextSize(10.5f);
            footer.setTypeface(Typeface.DEFAULT_BOLD);
            footer.setTextColor(0xFF7C5CFF);
            footer.setPadding(0, dp(activity, 16), 0, 0);
            column.addView(footer);

            ScrollView scroll = new ScrollView(activity);
            scroll.addView(column, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setView(scroll)
                    .setPositiveButton("Close", (DialogInterface.OnClickListener) null)
                    .create();
            dialog.show();
        } catch (Throwable t) {
            HookBridge.warn("info panel", t);
        }
    }

    private static int dp(Activity activity, float value) {
        try {
            return (int) (value * activity.getResources().getDisplayMetrics().density);
        } catch (Throwable t) {
            return (int) value;
        }
    }
}
