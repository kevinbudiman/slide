package trikita.slide.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;

import trikita.slide.R;
import trikita.slide.State;

// Grid of slide thumbnails; tapping one jumps to that slide.
public final class OverviewDialog {

    public interface OnPagePicked {
        void onPagePicked(int page);
    }

    private OverviewDialog() {}

    public static void show(Context c, State state, OnPagePicked listener) {
        int count = state.slides().size();
        int spacing = Style.dip(c, 8);

        GridView grid = new GridView(c);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(Style.dip(c, 140));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setHorizontalSpacing(spacing);
        grid.setVerticalSpacing(spacing);
        grid.setPadding(spacing * 2, spacing, spacing * 2, spacing);
        grid.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return count; }
            @Override public Object getItem(int position) { return position; }
            @Override public long getItemId(int position) { return position; }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                LinearLayout item = (LinearLayout) convertView;
                if (item == null) {
                    item = new LinearLayout(c);
                    item.setOrientation(LinearLayout.VERTICAL);
                    Preview preview = new Preview(c);
                    preview.setState(state);
                    item.addView(preview, new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                    TextView number = new TextView(c);
                    number.setGravity(Gravity.CENTER);
                    item.addView(number);
                }
                Preview preview = (Preview) item.getChildAt(0);
                preview.setFixedPage(position);
                TextView number = (TextView) item.getChildAt(1);
                number.setText(String.valueOf(position + 1));
                number.setTypeface(null, position == state.page()
                        ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
                item.setContentDescription(c.getString(R.string.cd_slide_number, position + 1));
                return item;
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(c)
                .setTitle(R.string.om_overview)
                .setView(grid)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        grid.setOnItemClickListener((parent, view, position, id) -> {
            dialog.dismiss();
            listener.onPagePicked(position);
        });
        dialog.show();
        grid.setSelection(Math.max(0, Math.min(state.page(), count - 1)));
    }
}
